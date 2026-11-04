#[cfg(unix)]
use std::io;
use std::{
    collections::VecDeque,
    env,
    ffi::OsString,
    path::{Path, PathBuf},
    process::Stdio,
    sync::Arc,
    time::Duration,
};

use chrono::Utc;
use serde_json::Value;
use tauri::{AppHandle, Emitter};
#[cfg(unix)]
use tokio::time::{Instant, sleep};
use tokio::{
    io::{AsyncBufReadExt, AsyncReadExt, BufReader},
    process::{Child, Command},
    sync::{Mutex, mpsc},
    time::timeout,
};
use tokio_util::sync::CancellationToken;

use crate::{
    models::{
        AppSettings, DownloadJob, JobEvent, JobLogEvent, JobStatus, MediaKind, MediaPreview,
        Provider,
    },
    provider, resolver,
    storage::Database,
    tagging,
    tooling::{self, Toolchain},
};

pub async fn inspect_link(
    url: &str,
    app_data: &Path,
    settings: &AppSettings,
) -> Result<MediaPreview, String> {
    let detection = provider::detect_resolved(url).await?;
    if !detection.supported {
        return Err(detection.message);
    }

    let toolchain = tooling::discover(app_data);
    if matches!(detection.provider, Provider::Deezer | Provider::AppleMusic)
        || (detection.provider == Provider::Spotify && toolchain.spotdl.is_none()) {
        let catalog = resolver::fetch(&detection.normalized_url, &detection.provider).await?;
        let warning = if toolchain.yt_dlp.is_none() || toolchain.ffmpeg.is_none() {
            Some("yt-dlp and FFmpeg are required. Open Settings → Components to finish setup.".into())
        } else {
            Some(format!(
                "{} does not serve downloadable audio. Each track is matched by artist, title, and length to an accessible source; quality depends on that source.",
                detection.provider.label()
            ))
        };
        return Ok(MediaPreview {
            url: detection.normalized_url,
            title: catalog.title,
            artist: catalog.artist,
            album: String::new(),
            thumbnail: catalog.thumbnail,
            duration_seconds: (catalog.tracks.len() == 1)
                .then(|| catalog.tracks[0].duration_seconds)
                .flatten(),
            track_count: Some(catalog.tracks.len()),
            provider: detection.provider,
            media_kind: catalog.kind,
            extractor: None,
            warning,
        });
    }
    let uses_ytdlp = matches!(
        detection.provider,
        Provider::YouTube | Provider::YouTubeMusic | Provider::SoundCloud
    );
    if !uses_ytdlp || toolchain.yt_dlp.is_none() {
        let warning = match detection.provider {
            Provider::Spotify if toolchain.spotdl.is_none() => Some(
                "SpotDL is required for Spotify downloads. Install it, then refresh Settings → Components."
                    .into(),
            ),
            Provider::Spotify if toolchain.ffmpeg.is_none() => Some(
                "FFmpeg is required for Spotify downloads. Open Settings → Components to finish setup."
                    .into(),
            ),
            Provider::Spotify => Some(
                "SpotDL will match the Spotify metadata to an audio source when the job starts."
                    .into(),
            ),
            _ if toolchain.yt_dlp.is_none() => Some(
                "yt-dlp is not installed yet. Open Settings → Components to finish setup."
                    .into(),
            ),
            _ => None,
        };
        return Ok(MediaPreview {
            url: detection.normalized_url,
            title: format!(
                "{} {}",
                detection.provider.label(),
                detection.media_kind.label()
            ),
            artist: "Ready to resolve when queued".into(),
            album: String::new(),
            thumbnail: None,
            duration_seconds: None,
            track_count: None,
            provider: detection.provider,
            media_kind: detection.media_kind,
            extractor: None,
            warning,
        });
    }

    let yt_dlp = toolchain.yt_dlp.as_deref().expect("checked above");
    let mut command = Command::new(yt_dlp);
    command.args([
        "--ignore-config",
        "--no-plugin-dirs",
        "--no-exec",
        "--no-update",
        "--skip-download",
        "--dump-single-json",
        "--no-warnings",
    ]);
    // Collections list their entries without resolving each one; a full
    // resolve of a large playlist would run into the inspection timeout.
    if matches!(detection.media_kind, MediaKind::Album | MediaKind::Playlist) {
        command.arg("--flat-playlist");
    } else {
        command.arg("--no-playlist");
    }
    command.args(cookie_args(settings));
    if let Some((name, path)) = &toolchain.js_runtime {
        command
            .arg("--js-runtimes")
            .arg(format!("{name}:{}", path.to_string_lossy()));
    }
    command.arg("--").arg(&detection.normalized_url);
    command
        .env("PATH", managed_path(&toolchain, app_data)?)
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    configure_process_tree(&mut command);

    let mut child = command
        .spawn()
        .map_err(|error| format!("Could not start yt-dlp: {error}"))?;
    let stdout = match child.stdout.take() {
        Some(stdout) => stdout,
        None => {
            terminate_process_tree(&mut child).await;
            return Err("yt-dlp metadata output was unavailable".into());
        }
    };
    let stderr = match child.stderr.take() {
        Some(stderr) => stderr,
        None => {
            terminate_process_tree(&mut child).await;
            return Err("yt-dlp metadata error stream was unavailable".into());
        }
    };
    let stdout_task = tokio::spawn(read_bounded_complete(stdout, MAX_METADATA_OUTPUT_BYTES));
    let stderr_task = tokio::spawn(read_bounded_tail(stderr, MAX_CAPTURED_LOG_BYTES));

    let status = match timeout(METADATA_INSPECTION_TIMEOUT, child.wait()).await {
        Ok(Ok(status)) => status,
        Ok(Err(error)) => {
            terminate_process_tree(&mut child).await;
            let _ = stdout_task.await;
            let _ = stderr_task.await;
            return Err(format!("Could not wait for yt-dlp metadata: {error}"));
        }
        Err(_) => {
            terminate_process_tree(&mut child).await;
            let _ = stdout_task.await;
            let _ = stderr_task.await;
            return Err("Metadata inspection timed out after 45 seconds".into());
        }
    };
    let stdout = stdout_task
        .await
        .map_err(|error| format!("Could not collect yt-dlp metadata: {error}"))??;
    let stderr = stderr_task.await.unwrap_or_default();
    if !status.success() {
        return Err(clean_error(&stderr));
    }
    let data: Value = serde_json::from_slice(&stdout)
        .map_err(|error| format!("The provider returned invalid metadata: {error}"))?;
    let track_count = data
        .get("playlist_count")
        .and_then(Value::as_u64)
        .map(|count| count as usize)
        .or_else(|| data.get("entries").and_then(Value::as_array).map(Vec::len));
    let media_kind = if track_count.unwrap_or(0) > 1 && detection.media_kind == MediaKind::Track {
        MediaKind::Playlist
    } else {
        detection.media_kind
    };
    Ok(MediaPreview {
        url: detection.normalized_url,
        title: string_field(&data, &["title", "track"]).unwrap_or_else(|| "Untitled media".into()),
        artist: preview_artist(&data).unwrap_or_else(|| "Unknown artist".into()),
        album: string_field(&data, &["album", "playlist_title"]).unwrap_or_default(),
        thumbnail: string_field(&data, &["thumbnail"]),
        duration_seconds: data
            .get("duration")
            .and_then(Value::as_f64)
            .map(|value| value.max(0.0) as u64),
        track_count,
        provider: detection.provider,
        media_kind,
        extractor: string_field(&data, &["extractor_key", "extractor"]),
        warning: None,
    })
}

pub async fn run_job(
    mut job: DownloadJob,
    database: Database,
    app: AppHandle,
    app_data: std::path::PathBuf,
    token: CancellationToken,
    paused: Arc<Mutex<bool>>,
) {
    let result = run_job_inner(&mut job, &database, &app, &app_data, &token).await;
    match result {
        Ok(note) => {
            #[cfg(windows)]
            if database.load_settings(AppSettings::defaults(&home_directory()))
                .map(|settings| settings.custom_folder_icons).unwrap_or(false)
                && let Some(ffmpeg) = tooling::discover(&app_data).ffmpeg
                && let Err(error) = crate::folder_icons::update(Path::new(&job.request.output_dir), &ffmpeg).await
            {
                log::warn!("Album folder icon update failed: {error}");
                job.recent_log.push(format!("Folder icons: {error}"));
            }
            job.status = JobStatus::Completed;
            job.progress = 100.0;
            job.stage = note.unwrap_or_else(|| "Complete".into());
            job.speed.clear();
            job.eta.clear();
            job.updated_at = Utc::now();
            let _ = database.save_job(&job);
            let _ = database.add_history_job(&job);
            let _ = app.emit("job:update", JobEvent { job });
        }
        Err(error) => {
            let was_paused = *paused.lock().await;
            job.status = if was_paused {
                JobStatus::Paused
            } else if token.is_cancelled() {
                JobStatus::Cancelled
            } else {
                JobStatus::Failed
            };
            job.stage = if was_paused {
                "Paused"
            } else if token.is_cancelled() {
                "Cancelled"
            } else {
                "Failed"
            }
            .into();
            job.error = if was_paused || token.is_cancelled() {
                None
            } else {
                Some(error)
            };
            job.updated_at = Utc::now();
            let _ = database.save_job(&job);
            let _ = app.emit("job:update", JobEvent { job });
        }
    }
}

async fn run_job_inner(
    job: &mut DownloadJob,
    database: &Database,
    app: &AppHandle,
    app_data: &Path,
    token: &CancellationToken,
) -> Result<Option<String>, String> {
    // Re-detect at execution time instead of trusting the provider supplied by
    // the webview. This is the routing boundary that guarantees Spotify,
    // Apple Music, and Deezer URLs can never fall through to raw yt-dlp.
    let detection = provider::detect_resolved(&job.request.url).await?;
    job.request.url = detection.normalized_url;
    job.request.provider = detection.provider.clone();
    if detection.media_kind != MediaKind::Unknown {
        job.request.media_kind = detection.media_kind;
    }

    let tools = tooling::discover(app_data);
    let settings = database
        .load_settings(AppSettings::defaults(&home_directory()))
        .unwrap_or_else(|_| AppSettings::defaults(&home_directory()));
    std::fs::create_dir_all(&job.request.output_dir)
        .map_err(|error| format!("Cannot create output folder: {error}"))?;

    let backend = backend_for_provider(&job.request.provider)?;
    set_stage(job, database, app, JobStatus::Preparing, backend.preparing_stage())?;
    if backend == DownloadBackend::Catalog
        || (backend == DownloadBackend::SpotDl && tools.spotdl.is_none()) {
        return run_catalog_job(job, database, app, app_data, token, &tools, &settings).await;
    }

    let collection = is_collection(job);
    let started_at = std::time::SystemTime::now() - Duration::from_secs(2);
    let mut attempt = 0;
    let outcome = loop {
        attempt += 1;
        let (command, backend) =
            build_download_command(job, &job.request.provider, &tools, app_data, &settings)?;
        set_stage(job, database, app, JobStatus::Downloading, backend.running_stage())?;
        let mut item = (0_usize, 0_usize);
        let outcome = run_streaming(command, backend.label(), job, database, app, token, |job, line| {
            if let Some(position) = parse_item(line) {
                item = position;
                job.stage = format!("Track {} of {}", position.0, position.1);
                return true;
            }
            let Some((percent, speed, eta)) = parse_progress(line) else {
                return false;
            };
            let (index, total) = item;
            job.progress = if total > 1 && index > 0 {
                ((index - 1) as f64 + percent / 100.0) / total as f64 * 100.0
            } else {
                percent
            }
            .min(99.9);
            job.speed = speed;
            job.eta = eta;
            let converting = percent >= 99.5 && total <= 1;
            job.status = if converting { JobStatus::Converting } else { JobStatus::Downloading };
            if total <= 1 {
                job.stage = if converting { "Converting and tagging" } else { "Downloading" }.into();
            }
            true
        })
        .await?;
        if attempt >= 2 || outcome.success || !is_transient_refusal(&outcome.tail) {
            break outcome;
        }
        retry_pause(job, database, app, token).await?;
    };

    if job.request.embed_artwork {
        let root = PathBuf::from(&job.request.output_dir);
        let _ = tokio::task::spawn_blocking(move || finalize_artwork(&root, started_at)).await;
    }
    if outcome.success && outcome.errors == 0 {
        return Ok(None);
    }
    // A collection with some unavailable entries (removed, region-locked,
    // private) still delivered the rest; report it instead of failing it all.
    if collection && outcome.produced > 0 {
        job.unavailable_tracks = outcome.errors.max(1);
        return Ok(Some(format!(
            "Complete · {} unavailable",
            outcome.errors.max(1)
        )));
    }
    if backend == DownloadBackend::SpotDl {
        set_stage(job, database, app, JobStatus::Preparing, "SpotDL failed; trying public catalog matching")?;
        return run_catalog_job(job, database, app, app_data, token, &tools, &settings).await;
    }
    Err(friendly_error(&outcome.tail))
}

fn set_stage(
    job: &mut DownloadJob,
    database: &Database,
    app: &AppHandle,
    status: JobStatus,
    stage: &str,
) -> Result<(), String> {
    job.status = status;
    job.stage = stage.into();
    job.updated_at = Utc::now();
    database.save_job(job)?;
    app.emit("job:update", JobEvent { job: job.clone() })
        .map_err(|error| error.to_string())
}

/// Lines kept on the job record for the queue terminal on a cold load.
const RECENT_LOG_LINES: usize = 200;
const FLUSH_INTERVAL: Duration = Duration::from_millis(250);

struct StreamOutcome {
    success: bool,
    /// Last stderr lines, for error reporting.
    tail: String,
    /// Files the downloader finished writing.
    produced: usize,
    /// `ERROR:` lines seen.
    errors: usize,
}

/// Runs a downloader process, streaming stdout and stderr into the job log
/// and handing stdout lines to `on_stdout`, which returns true when it changed
/// the job. Database writes and events are throttled to [`FLUSH_INTERVAL`].
async fn run_streaming<R: tauri::Runtime, F>(
    mut command: Command,
    label: &str,
    job: &mut DownloadJob,
    database: &Database,
    app: &AppHandle<R>,
    token: &CancellationToken,
    mut on_stdout: F,
) -> Result<StreamOutcome, String>
where
    F: FnMut(&mut DownloadJob, &str) -> bool,
{
    command
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    configure_process_tree(&mut command);
    let mut child = command
        .spawn()
        .map_err(|error| format!("Could not start {label}: {error}"))?;
    let (Some(stdout), Some(stderr)) = (child.stdout.take(), child.stderr.take()) else {
        terminate_process_tree(&mut child).await;
        return Err("Downloader output was unavailable".into());
    };

    let (sender, mut receiver) = mpsc::channel::<(bool, String)>(512);
    let out_sender = sender.clone();
    tokio::spawn(async move {
        let mut lines = BufReader::new(stdout).lines();
        while let Ok(Some(line)) = lines.next_line().await {
            if out_sender.send((false, line)).await.is_err() {
                break;
            }
        }
    });
    tokio::spawn(async move {
        let mut lines = BufReader::new(stderr).lines();
        while let Ok(Some(line)) = lines.next_line().await {
            if sender.send((true, line)).await.is_err() {
                break;
            }
        }
    });

    let mut tail = VecDeque::<String>::new();
    let invocation = std::iter::once(command.as_std().get_program())
        .chain(command.as_std().get_args())
        .map(|part| {
            let text = part.to_string_lossy();
            if text.contains(' ') { format!("\"{text}\"") } else { text.into_owned() }
        })
        .collect::<Vec<_>>()
        .join(" ");
    let mut pending_log = vec![redact_home(&format!("$ {invocation}"))];
    let mut dirty = false;
    let mut last_flush = std::time::Instant::now();
    let mut produced = 0;
    let mut errors = 0;

    loop {
        tokio::select! {
            _ = token.cancelled() => {
                terminate_process_tree(&mut child).await;
                return Err("Download stopped".into());
            }
            message = receiver.recv() => {
                let Some((is_stderr, line)) = message else { break };
                let line = line.trim_end().to_string();
                if line.is_empty() {
                    continue;
                }
                let is_progress = !is_stderr && line.starts_with(PROGRESS_MARKER);
                if !is_stderr && on_stdout(job, &line) {
                    dirty = true;
                }
                if line.starts_with("neku-file|")
                    || line.starts_with("[ExtractAudio] Destination:")
                    || line.contains("has already been recorded in the archive")
                    || line.contains("has already been downloaded")
                    || line.contains("Downloaded \"")
                {
                    produced += 1;
                }
                if line.starts_with("ERROR:") {
                    errors += 1;
                }
                if is_stderr {
                    tail.push_back(line.clone());
                    if tail.len() > 40 {
                        tail.pop_front();
                    }
                }
                if !is_progress {
                    pending_log.push(redact_home(&line));
                }
                if last_flush.elapsed() >= FLUSH_INTERVAL {
                    flush(job, database, app, &mut pending_log, &mut dirty);
                    last_flush = std::time::Instant::now();
                }
            }
        }
    }
    flush(job, database, app, &mut pending_log, &mut dirty);

    let status = tokio::select! {
        _ = token.cancelled() => {
            terminate_process_tree(&mut child).await;
            return Err("Download stopped".into());
        }
        status = child.wait() => status.map_err(|error| error.to_string())?,
    };
    Ok(StreamOutcome {
        // 101 is yt-dlp's "--max-downloads reached", which is the goal.
        success: status.success() || status.code() == Some(101),
        tail: tail.into_iter().collect::<Vec<_>>().join("\n"),
        produced,
        errors,
    })
}

fn flush<R: tauri::Runtime>(
    job: &mut DownloadJob,
    database: &Database,
    app: &AppHandle<R>,
    pending_log: &mut Vec<String>,
    dirty: &mut bool,
) {
    if !pending_log.is_empty() {
        job.recent_log.extend(pending_log.iter().cloned());
        let overflow = job.recent_log.len().saturating_sub(RECENT_LOG_LINES);
        job.recent_log.drain(..overflow);
        let _ = app.emit(
            "job:log",
            JobLogEvent {
                job_id: job.id.clone(),
                lines: std::mem::take(pending_log),
            },
        );
        *dirty = true;
    }
    if *dirty {
        job.updated_at = Utc::now();
        let _ = database.save_job(job);
        let _ = app.emit("job:update", JobEvent { job: job.clone() });
        *dirty = false;
    }
}

fn redact_home(line: &str) -> String {
    match env::var_os("HOME").or_else(|| env::var_os("USERPROFILE")) {
        Some(home) if !home.is_empty() => line.replace(&*home.to_string_lossy(), "~"),
        _ => line.to_string(),
    }
}

/// `[download] Downloading item 3 of 12` → (3, 12).
fn parse_item(line: &str) -> Option<(usize, usize)> {
    let rest = line.strip_prefix("[download] Downloading item ")?;
    let (index, total) = rest.split_once(" of ")?;
    Some((index.trim().parse().ok()?, total.trim().parse().ok()?))
}

/// Downloads a Deezer or Apple Music link: catalog metadata, then one search
/// per track, then catalog tags written over the match.
async fn run_catalog_job(
    job: &mut DownloadJob,
    database: &Database,
    app: &AppHandle,
    app_data: &Path,
    token: &CancellationToken,
    tools: &Toolchain,
    settings: &AppSettings,
) -> Result<Option<String>, String> {
    let yt_dlp = tools
        .yt_dlp
        .clone()
        .ok_or_else(|| "yt-dlp is missing. Open Settings → Components.".to_string())?;
    let ffmpeg = tools
        .ffmpeg
        .clone()
        .ok_or_else(|| "FFmpeg is missing. Open Settings → Components.".to_string())?;

    set_stage(job, database, app, JobStatus::Preparing, "Reading catalog")?;
    let catalog = resolver::fetch(&job.request.url, &job.request.provider).await?;
    if catalog.tracks.is_empty() {
        return Err("The catalog returned no tracks for this link".into());
    }
    if job.title.is_empty() || job.title == "Resolving media" || job.request.provider == Provider::Spotify {
        job.title = catalog.title.clone();
        job.artist = catalog.artist.clone();
    }
    let collection = matches!(catalog.kind, MediaKind::Album | MediaKind::Playlist);
    let folder = if collection {
        Path::new(&job.request.output_dir).join(sanitize_file_name(&catalog.title))
    } else {
        PathBuf::from(&job.request.output_dir)
    };
    std::fs::create_dir_all(&folder).map_err(|error| format!("Cannot create folder: {error}"))?;
    let extension = output_extension(&job.request.format).to_string();
    let total = catalog.tracks.len();
    let path_env = managed_path(tools, app_data)?;
    let mut missing = Vec::<String>::new();
    let mut last_failure = String::new();
    let catalog_started = std::time::SystemTime::now() - Duration::from_secs(2);

    for (index, track) in catalog.tracks.iter().enumerate() {
        if token.is_cancelled() {
            return Err("Download stopped".into());
        }
        let position = track.track_number.unwrap_or(index as u32 + 1);
        let base = if collection && job.request.track_numbers {
            format!("{position:02} - {} - {}", track.artist, track.title)
        } else {
            format!("{} - {}", track.artist, track.title)
        };
        let base = sanitize_file_name(&base);
        let target = folder.join(format!("{base}.{extension}"));
        job.stage = format!("Track {} of {total}: {}", index + 1, track.title);
        job.progress = index as f64 / total as f64 * 100.0;
        set_stage(job, database, app, JobStatus::Downloading, &job.stage.clone())?;
        if target.is_file() {
            continue;
        }

        // Never silently relax a known catalog duration: an unrelated live
        // version or remix must not be tagged as the requested recording.
        let mut attempts = VecDeque::from([(track.duration_seconds.filter(|d| *d > 0), false)]);
        while let Some((duration, retried)) = attempts.pop_front() {
            let mut command = Command::new(&yt_dlp);
            command.env("PATH", &path_env);
            command.args(cookie_args(settings));
            command.args([
                "--ignore-config",
                "--no-plugin-dirs",
                "--no-exec",
                "--no-update",
                "--no-playlist",
                "--windows-filenames",
                "--socket-timeout",
                "30",
                "--retries",
                "5",
                "--max-downloads",
                "1",
                "-x",
                "--audio-format",
                &job.request.format,
                "--audio-quality",
                &job.request.quality,
                "--newline",
                "--progress-template",
                PROGRESS_TEMPLATE,
            ]);
            command
                .arg("--ffmpeg-location")
                .arg(ffmpeg.parent().unwrap_or(Path::new(".")));
            if let Some((name, path)) = &tools.js_runtime {
                command
                    .arg("--js-runtimes")
                    .arg(format!("{name}:{}", path.to_string_lossy()));
            }
            if job.request.embed_metadata {
                command.arg("--embed-metadata");
            }
            if job.request.embed_artwork {
                command.args(artwork_args(&job.request.format));
            }
            let query = if let Some(duration) = duration {
                command.arg("--match-filter").arg(format!(
                    "duration >= {} & duration <= {}",
                    duration.saturating_sub(15),
                    duration + 15
                ));
                format!("ytsearch5:{}", track.search_query())
            } else {
                format!("ytsearch1:{}", track.search_query())
            };
            command
                .arg("-o")
                .arg(folder.join(format!("{}.%(ext)s", base.replace('%', "%%"))))
                .arg("--")
                .arg(query);

            let slice = 100.0 / total as f64;
            let offset = index as f64 * slice;
            let outcome = run_streaming(command, "yt-dlp", job, database, app, token, |job, line| {
                let Some((percent, speed, eta)) = parse_progress(line) else {
                    return false;
                };
                job.progress = (offset + percent / 100.0 * slice).min(99.9);
                job.speed = speed;
                job.eta = eta;
                true
            })
            .await?;
            if target.is_file() {
                break;
            }
            remove_leftovers(&folder, &base, &extension);
            if !outcome.tail.trim().is_empty() {
                last_failure = outcome.tail.clone();
            }
            if outcome.tail.contains("Sign in to confirm") {
                return Err(friendly_error(&outcome.tail));
            }
            if !retried && is_transient_refusal(&outcome.tail) {
                retry_pause(job, database, app, token).await?;
                attempts.push_front((duration, true));
            }
        }

        if !target.is_file() {
            missing.push(track.search_query());
            continue;
        }
        if job.request.embed_metadata {
            let tags = tagging::CatalogTags {
                title: track.title.clone(),
                artist: track.artist.clone(),
                album: if track.album.is_empty() && collection {
                    catalog.title.clone()
                } else {
                    track.album.clone()
                },
                album_artist: if catalog.kind == MediaKind::Album {
                    catalog.artist.clone()
                } else {
                    String::new()
                },
                track_number: (catalog.kind == MediaKind::Album).then_some(position),
            };
            let path = target.clone();
            if let Ok(Err(error)) =
                tokio::task::spawn_blocking(move || tagging::write_catalog_tags(&path, &tags)).await
            {
                log::warn!("Tagging {} failed: {error}", target.display());
            }
        }
    }

    if job.request.embed_artwork {
        let root = PathBuf::from(&job.request.output_dir);
        let since = catalog_started;
        let _ = tokio::task::spawn_blocking(move || finalize_artwork(&root, since)).await;
    }
    if missing.len() == total {
        return Err(if last_failure.contains("ERROR:") {
            friendly_error(&last_failure)
        } else {
            "No matching audio was found for any track in this link".into()
        });
    }
    if missing.is_empty() {
        Ok(None)
    } else {
        job.unavailable_tracks = missing.len();
        job.recent_log.push(format!("Not found: {}", missing.join("; ")));
        Ok(Some(format!("Complete · {} not found", missing.len())))
    }
}

/// Square-crops thumbnails (YouTube serves album art letterboxed in a 16:9
/// frame) and keeps them on disk for [`finalize_artwork`]. WAV cannot take an
/// embedded picture from yt-dlp, so it is embedded afterwards instead.
fn artwork_args(format: &str) -> Vec<&'static str> {
    let mut args = vec![
        "--write-thumbnail",
        "--convert-thumbnails",
        "jpg",
        "--ppa",
        "ThumbnailsConvertor+ffmpeg_o:-c:v mjpeg -qmin 1 -qscale:v 2 -vf crop=\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\"",
        "-o",
        "pl_thumbnail:",
    ];
    if format != "wav" {
        args.insert(0, "--embed-thumbnail");
    }
    args
}

/// Embeds kept thumbnails where yt-dlp could not (WAV), promotes one per album
/// folder to `cover.jpg` for players that read folder art, and removes the
/// rest. Only images written since `since` are touched.
fn finalize_artwork(root: &Path, since: std::time::SystemTime) {
    let mut folders = vec![root.to_path_buf()];
    if let Ok(entries) = std::fs::read_dir(root) {
        folders.extend(
            entries
                .flatten()
                .filter(|entry| entry.file_type().is_ok_and(|kind| kind.is_dir()))
                .map(|entry| entry.path()),
        );
    }
    for folder in folders {
        let Ok(entries) = std::fs::read_dir(&folder) else {
            continue;
        };
        let images: Vec<PathBuf> = entries
            .flatten()
            .map(|entry| entry.path())
            .filter(|path| {
                path.extension().is_some_and(|ext| ext.eq_ignore_ascii_case("jpg"))
                    && !path.file_name().is_some_and(|name| name.to_string_lossy().starts_with('.'))
                    && std::fs::metadata(path)
                        .and_then(|meta| meta.modified())
                        .is_ok_and(|modified| modified >= since)
            })
            .collect();
        let is_album_folder = folder != root;
        for image in images {
            let stem = image.with_extension("");
            let audio = AUDIO_OUTPUTS
                .iter()
                .map(|ext| stem.with_extension(ext))
                .find(|candidate| candidate.is_file());
            let Some(audio) = audio else {
                continue;
            };
            if audio.extension().is_some_and(|ext| ext == "wav")
                && let Ok(bytes) = std::fs::read(&image)
                && let Err(error) = tagging::embed_cover(&audio, bytes)
            {
                log::warn!("Embedding artwork into {} failed: {error}", audio.display());
            }
            let cover = folder.join("cover.jpg");
            if is_album_folder && !cover.exists() {
                let _ = std::fs::rename(&image, &cover);
            } else {
                let _ = std::fs::remove_file(&image);
            }
        }
    }
}

const AUDIO_OUTPUTS: &[&str] = &["mp3", "flac", "opus", "m4a", "wav", "ogg"];

/// YouTube intermittently refuses media requests with 403 even for a healthy
/// toolchain; one delayed retry clears most of them.
fn is_transient_refusal(tail: &str) -> bool {
    let lowered = tail.to_ascii_lowercase();
    lowered.contains("http error 403") || lowered.contains("403: forbidden")
}

async fn retry_pause(
    job: &mut DownloadJob,
    database: &Database,
    app: &AppHandle,
    token: &CancellationToken,
) -> Result<(), String> {
    job.recent_log.push("Source refused the request (HTTP 403); retrying in 3 s".into());
    set_stage(job, database, app, JobStatus::Downloading, "Retrying")?;
    tokio::select! {
        _ = token.cancelled() => Err("Download stopped".into()),
        _ = tokio::time::sleep(Duration::from_secs(3)) => Ok(()),
    }
}

/// Removes thumbnails and partial downloads a failed attempt left next to the
/// target, so a failed match does not litter the music folder.
fn remove_leftovers(folder: &Path, base: &str, final_extension: &str) {
    let Ok(entries) = std::fs::read_dir(folder) else {
        return;
    };
    let prefix = format!("{base}.");
    for entry in entries.flatten() {
        let name = entry.file_name().to_string_lossy().into_owned();
        if let Some(rest) = name.strip_prefix(&prefix)
            && rest != final_extension
        {
            let _ = std::fs::remove_file(entry.path());
        }
    }
}

fn output_extension(format: &str) -> &str {
    match format {
        "alac" => "m4a",
        "vorbis" => "ogg",
        other => other,
    }
}

/// Makes a single path component that is valid on Windows, macOS, and Linux.
fn sanitize_file_name(raw: &str) -> String {
    let cleaned: String = raw
        .chars()
        .map(|c| match c {
            '<' | '>' | ':' | '"' | '/' | '\\' | '|' | '?' | '*' => '_',
            c if c.is_control() => '_',
            c => c,
        })
        .collect();
    let trimmed = cleaned.trim().trim_end_matches(['.', ' ']);
    let mut name: String = trimmed.chars().take(150).collect();
    if name.is_empty() {
        name = "Untitled".into();
    }
    let stem = name.split('.').next().unwrap_or_default().to_ascii_uppercase();
    let reserved = matches!(stem.as_str(), "CON" | "PRN" | "AUX" | "NUL")
        || ((stem.starts_with("COM") || stem.starts_with("LPT"))
            && stem.len() == 4
            && stem.as_bytes()[3].is_ascii_digit());
    if reserved {
        name.insert(0, '_');
    }
    name
}

/// Turns the downloader's last error lines into something a user can act on.
fn friendly_error(tail: &str) -> String {
    let lowered = tail.to_ascii_lowercase();
    if lowered.contains("sign in to confirm") || lowered.contains("not a bot") {
        return "YouTube asked for a sign-in check. In Settings → General, choose a browser to read cookies from (Firefox is the most reliable) or a cookies.txt file, then retry.".into();
    }
    if lowered.contains("http error 429") || lowered.contains("too many requests") {
        return "The source is rate-limiting this connection (HTTP 429). Wait a few minutes or lower parallel downloads, then retry.".into();
    }
    if lowered.contains("http error 403") || lowered.contains("403: forbidden") {
        return "The source refused the download (HTTP 403). Update components in Settings → Components, then retry.".into();
    }
    if lowered.contains("private video") || lowered.contains("this video is private") {
        return "This item is private.".into();
    }
    if lowered.contains("not available in your country") || lowered.contains("geo") && lowered.contains("restrict") {
        return "This item is not available in your region.".into();
    }
    if lowered.contains("video unavailable") || lowered.contains("has been removed") {
        return "This item is unavailable or was removed by the uploader.".into();
    }
    if lowered.contains("ffmpeg") && lowered.contains("not found") {
        return "FFmpeg is missing. Open Settings → Components.".into();
    }
    if lowered.contains("could not copy") && lowered.contains("cookie") {
        return "The browser's cookie database is locked or encrypted. Close the browser, or use Firefox or a cookies.txt file instead.".into();
    }
    clean_error(tail)
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum DownloadBackend {
    YtDlp,
    SpotDl,
    /// Deezer and Apple Music: catalog metadata, then a search per track.
    Catalog,
}

impl DownloadBackend {
    fn label(self) -> &'static str {
        match self {
            Self::YtDlp | Self::Catalog => "yt-dlp",
            Self::SpotDl => "SpotDL",
        }
    }

    fn preparing_stage(self) -> &'static str {
        match self {
            Self::YtDlp => "Preparing tools",
            Self::SpotDl => "Preparing Spotify match",
            Self::Catalog => "Reading catalog",
        }
    }

    fn running_stage(self) -> &'static str {
        match self {
            Self::YtDlp => "Downloading",
            Self::SpotDl | Self::Catalog => "Matching and downloading",
        }
    }
}

fn backend_for_provider(provider: &Provider) -> Result<DownloadBackend, String> {
    match provider {
        Provider::YouTube | Provider::YouTubeMusic | Provider::SoundCloud => {
            Ok(DownloadBackend::YtDlp)
        }
        Provider::Spotify => Ok(DownloadBackend::SpotDl),
        Provider::Deezer | Provider::AppleMusic => Ok(DownloadBackend::Catalog),
        Provider::Unknown => Err("This source is not supported yet".into()),
    }
}

fn home_directory() -> String {
    env::var("HOME")
        .or_else(|_| env::var("USERPROFILE"))
        .unwrap_or_else(|_| ".".into())
}

/// Browsers yt-dlp can read a cookie jar from. Anything else is rejected rather
/// than passed through, so a stored setting can never inject a flag.
const COOKIE_BROWSERS: &[&str] = &[
    "brave", "chrome", "chromium", "edge", "firefox", "opera", "safari", "vivaldi", "whale",
];

/// Cookie arguments for yt-dlp.
///
/// YouTube now answers a large share of anonymous requests with "Sign in to
/// confirm you're not a bot", and cookies are the only supported way through.
/// This stays opt-in: reading a browser's cookie jar is sensitive, so nothing
/// happens unless the user picked a source in Settings.
fn cookie_args(settings: &AppSettings) -> Vec<OsString> {
    match settings.cookie_mode.as_str() {
        "browser" => {
            let browser = settings.cookie_browser.to_ascii_lowercase();
            if !COOKIE_BROWSERS.contains(&browser.as_str()) {
                return Vec::new();
            }
            vec![OsString::from("--cookies-from-browser"), OsString::from(browser)]
        }
        "file" => match settings.cookie_file.as_deref() {
            Some(path) if !path.trim().is_empty() => {
                vec![OsString::from("--cookies"), OsString::from(path)]
            }
            _ => Vec::new(),
        },
        _ => Vec::new(),
    }
}

fn build_download_command(
    job: &DownloadJob,
    provider: &Provider,
    tools: &Toolchain,
    app_data: &Path,
    settings: &AppSettings,
) -> Result<(Command, DownloadBackend), String> {
    let backend = backend_for_provider(provider)?;
    let mut command = match backend {
        DownloadBackend::YtDlp => build_ytdlp_command(job, tools, settings)?,
        DownloadBackend::SpotDl => build_spotdl_command(job, tools, settings)?,
        DownloadBackend::Catalog => {
            return Err("Catalog providers are downloaded track by track".into());
        }
    };
    command.env("PATH", managed_path(tools, app_data)?);
    Ok((command, backend))
}

fn build_ytdlp_command(
    job: &DownloadJob,
    tools: &Toolchain,
    settings: &AppSettings,
) -> Result<Command, String> {
    let yt_dlp = tools
        .yt_dlp
        .as_deref()
        .ok_or_else(|| "yt-dlp is missing. Open Settings → Components.".to_string())?;
    let ffmpeg = tools
        .ffmpeg
        .as_deref()
        .ok_or_else(|| "FFmpeg is missing. Open Settings → Components.".to_string())?;
    let collection = is_collection(job);
    let template = if collection {
        if job.request.track_numbers {
            "%(playlist_title,album)s/%(playlist_index,track_number)03d - %(title,track)s.%(ext)s"
        } else {
            "%(playlist_title,album)s/%(title,track)s.%(ext)s"
        }
    } else {
        "%(title,track)s.%(ext)s"
    };
    let output_template = Path::new(&job.request.output_dir).join(template);
    let ffmpeg_dir = ffmpeg.parent().unwrap_or(Path::new("."));

    let mut command = Command::new(yt_dlp);
    command.args(cookie_args(settings));
    command.args([
        "--ignore-config",
        "--no-plugin-dirs",
        "--no-exec",
        "--no-update",
        "--continue",
        "--no-overwrites",
        "--windows-filenames",
        "--socket-timeout",
        "30",
        "--retries",
        "10",
        "--fragment-retries",
        "10",
    ]);
    // No download archive: it remembered tracks the user had since deleted
    // and silently skipped them. With a fixed --audio-format yt-dlp already
    // recognises an existing converted file and skips it without downloading.
    command.args([
        "-x",
        "--audio-format",
        &job.request.format,
        "--audio-quality",
        &job.request.quality,
    ]);
    command.arg("--ffmpeg-location").arg(ffmpeg_dir);
    if let Some((name, path)) = &tools.js_runtime {
        command
            .arg("--js-runtimes")
            .arg(format!("{name}:{}", path.to_string_lossy()));
    }
    if job.request.embed_metadata {
        command.arg("--embed-metadata");
    }
    if job.request.embed_artwork {
        command.args(artwork_args(&job.request.format));
    }
    command.args([
        "--newline",
        "--progress-template",
        PROGRESS_TEMPLATE,
    ]);
    command.args(["--progress", "--no-quiet", "--print", "after_move:neku-file|%(filepath)s"]);
    if collection {
        command.args(["--yes-playlist", "--ignore-errors"]);
        // Album order survives in players even without numbered filenames,
        // and YouTube Music's "Album - " playlist prefix stays out of folders.
        command.args([
            "--parse-metadata",
            "playlist_index:%(track_number)s",
            "--replace-in-metadata",
            "playlist_title",
            "^Album - ",
            "",
        ]);
    } else {
        command.arg("--no-playlist");
    }
    command
        .arg("-o")
        .arg(output_template)
        .arg("--")
        .arg(&job.request.url);
    Ok(command)
}

fn build_spotdl_command(
    job: &DownloadJob,
    tools: &Toolchain,
    settings: &AppSettings,
) -> Result<Command, String> {
    let spotdl = tools.spotdl.as_deref().ok_or_else(|| {
        "SpotDL is required for Spotify downloads and was not found. Install SpotDL, then refresh Settings → Components."
            .to_string()
    })?;
    let ffmpeg = tools.ffmpeg.as_deref().ok_or_else(|| {
        "FFmpeg is required for Spotify downloads. Open Settings → Components.".to_string()
    })?;

    let mut command = Command::new(spotdl);
    command.args(spotdl_args(job, ffmpeg)?);
    // SpotDL understands an explicit cookie file only, not a browser jar.
    if settings.cookie_mode == "file"
        && let Some(path) = settings.cookie_file.as_deref().filter(|v| !v.trim().is_empty())
    {
        command.arg("--cookie-file").arg(path);
    }
    Ok(command)
}

fn spotdl_args(job: &DownloadJob, ffmpeg: &Path) -> Result<Vec<OsString>, String> {
    let format = spotdl_format(&job.request.format)?;
    let bitrate = spotdl_bitrate(format, &job.request.quality)?;
    let template = if is_collection(job) {
        if job.request.track_numbers {
            "{list-name}/{list-position} - {title}.{output-ext}"
        } else {
            "{list-name}/{title}.{output-ext}"
        }
    } else {
        "{title}.{output-ext}"
    };
    let output_template = Path::new(&job.request.output_dir).join(template);

    let mut args = vec![
        OsString::from("download"),
        // SpotDL's parser requires the positional query immediately after the
        // operation. Detection guarantees this is a normalized http(s) URL,
        // so it cannot be mistaken for an option; it is never passed to a shell.
        OsString::from(&job.request.url),
        OsString::from("--format"),
        OsString::from(format),
        OsString::from("--bitrate"),
        OsString::from(bitrate),
        OsString::from("--ffmpeg"),
        ffmpeg.as_os_str().to_owned(),
        OsString::from("--yt-dlp-args"),
        OsString::from("--ignore-config --no-plugin-dirs --no-exec --no-update"),
        OsString::from("--output"),
        output_template.into_os_string(),
        OsString::from("--overwrite"),
        OsString::from("skip"),
        OsString::from("--simple-tui"),
        OsString::from("--log-level"),
        OsString::from("INFO"),
    ];
    if !job.request.embed_artwork {
        args.push(OsString::from("--skip-album-art"));
    }
    Ok(args)
}

fn spotdl_format(format: &str) -> Result<&'static str, String> {
    match format {
        "mp3" => Ok("mp3"),
        "flac" => Ok("flac"),
        "opus" => Ok("opus"),
        "m4a" => Ok("m4a"),
        "wav" => Ok("wav"),
        "vorbis" => Ok("ogg"),
        "alac" => Err(
            "ALAC output is not supported for Spotify downloads. Choose FLAC or M4A instead."
                .into(),
        ),
        _ => Err(format!("{format} output is not supported by SpotDL")),
    }
}

fn spotdl_bitrate(format: &str, quality: &str) -> Result<String, String> {
    if matches!(format, "flac" | "wav") {
        return Ok("disable".into());
    }

    let normalized = quality.trim().to_ascii_lowercase();
    if matches!(
        normalized.as_str(),
        "auto" | "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9"
    ) {
        return Ok(normalized);
    }
    let number = normalized
        .strip_suffix("kbps")
        .or_else(|| normalized.strip_suffix('k'))
        .unwrap_or(&normalized)
        .parse::<u16>()
        .map_err(|_| "Choose a valid Spotify output quality".to_string())?;
    if !matches!(
        number,
        8 | 16 | 24 | 32 | 40 | 48 | 64 | 80 | 96 | 112 | 128 | 160 | 192 | 224 | 256 | 320
    ) {
        return Err("Choose a SpotDL bitrate from 8 to 320 kbps".into());
    }
    Ok(format!("{number}k"))
}

fn is_collection(job: &DownloadJob) -> bool {
    matches!(
        job.request.media_kind,
        MediaKind::Album | MediaKind::Playlist
    )
}

fn managed_path(tools: &Toolchain, app_data: &Path) -> Result<OsString, String> {
    let mut directories = Vec::<PathBuf>::new();
    for executable in [
        tools.spotdl.as_deref(),
        tools.yt_dlp.as_deref(),
        tools.ffmpeg.as_deref(),
        tools.ffprobe.as_deref(),
    ]
    .into_iter()
    .flatten()
    {
        if let Some(parent) = executable.parent() {
            push_unique_path(&mut directories, parent.to_path_buf());
        }
    }
    if let Some((_, executable)) = &tools.js_runtime
        && let Some(parent) = executable.parent()
    {
        push_unique_path(&mut directories, parent.to_path_buf());
    }
    push_unique_path(&mut directories, app_data.join("tools").join("current"));
    push_unique_path(&mut directories, app_data.join("tools").join("bin"));
    if let Some(existing) = env::var_os("PATH") {
        for directory in env::split_paths(&existing) {
            push_unique_path(&mut directories, directory);
        }
    }
    env::join_paths(directories)
        .map_err(|error| format!("Could not construct the downloader PATH: {error}"))
}

fn push_unique_path(paths: &mut Vec<PathBuf>, candidate: PathBuf) {
    if !paths.iter().any(|path| path == &candidate) {
        paths.push(candidate);
    }
}

const MAX_CAPTURED_LOG_BYTES: usize = 1024 * 1024;
const MAX_METADATA_OUTPUT_BYTES: usize = 16 * 1024 * 1024;
const METADATA_INSPECTION_TIMEOUT: Duration = Duration::from_secs(45);

async fn read_bounded_complete<R>(mut reader: R, limit: usize) -> Result<Vec<u8>, String>
where
    R: tokio::io::AsyncRead + Unpin,
{
    let mut output = Vec::with_capacity(limit.min(64 * 1024));
    let mut exceeded = false;
    let mut chunk = [0_u8; 8 * 1024];
    loop {
        let count = reader
            .read(&mut chunk)
            .await
            .map_err(|error| format!("Could not read provider metadata: {error}"))?;
        if count == 0 {
            break;
        }
        if !exceeded && output.len().saturating_add(count) <= limit {
            output.extend_from_slice(&chunk[..count]);
        } else {
            // Continue draining the pipe so the child cannot deadlock while it
            // exits, but do not retain an incomplete JSON document.
            exceeded = true;
        }
    }
    if exceeded {
        Err(format!(
            "Provider metadata exceeded the {} MiB safety limit",
            limit / (1024 * 1024)
        ))
    } else {
        Ok(output)
    }
}

async fn read_bounded_tail<R>(mut reader: R, limit: usize) -> String
where
    R: tokio::io::AsyncRead + Unpin,
{
    let mut tail = Vec::with_capacity(limit.min(16 * 1024));
    let mut chunk = [0_u8; 8 * 1024];
    loop {
        match reader.read(&mut chunk).await {
            Ok(0) | Err(_) => break,
            Ok(count) => append_bounded_tail(&mut tail, &chunk[..count], limit),
        }
    }
    String::from_utf8_lossy(&tail).into_owned()
}

fn append_bounded_tail(tail: &mut Vec<u8>, chunk: &[u8], limit: usize) {
    if limit == 0 {
        tail.clear();
        return;
    }
    if chunk.len() >= limit {
        tail.clear();
        tail.extend_from_slice(&chunk[chunk.len() - limit..]);
        return;
    }
    let retained = limit.saturating_sub(chunk.len()).min(tail.len());
    if retained < tail.len() {
        let start = tail.len() - retained;
        tail.copy_within(start.., 0);
        tail.truncate(retained);
    }
    tail.extend_from_slice(chunk);
}

fn configure_process_tree(command: &mut Command) {
    // This protects against early returns after spawning. Explicit cancellation
    // still uses `terminate_process_tree` so descendants are stopped as well.
    command.kill_on_drop(true);

    #[cfg(unix)]
    command.process_group(0);
}

async fn terminate_process_tree(child: &mut Child) {
    #[cfg(unix)]
    {
        if let Some(pid) = child.id()
            && signal_process_group(pid, SIGTERM).is_ok()
        {
            let deadline = Instant::now() + Duration::from_millis(1_500);
            loop {
                // Reap the direct child as soon as possible while checking
                // whether any descendant remains in its process group.
                let _ = child.try_wait();
                if !process_group_exists(pid) {
                    let _ = child.wait().await;
                    return;
                }
                if Instant::now() >= deadline {
                    break;
                }
                sleep(Duration::from_millis(50)).await;
            }

            let _ = signal_process_group(pid, SIGKILL);
            let _ = child.wait().await;
            return;
        }
    }

    #[cfg(windows)]
    {
        let mut taskkill_succeeded = false;
        if let Some(pid) = child.id() {
            let mut taskkill = Command::new(windows_taskkill_path());
            taskkill
                .args(["/PID", &pid.to_string(), "/T", "/F"])
                .stdin(Stdio::null())
                .stdout(Stdio::null())
                .stderr(Stdio::null())
                // Avoid opening a console window in the desktop application.
                .creation_flags(CREATE_NO_WINDOW);
            taskkill_succeeded = taskkill.status().await.is_ok_and(|status| status.success());
        }
        if taskkill_succeeded && timeout(Duration::from_secs(5), child.wait()).await.is_ok() {
            return;
        }
    }

    // Fallback for an already-exited process, a failed process-group signal,
    // or a platform without process-tree primitives.
    let _ = child.start_kill();
    let _ = child.wait().await;
}

#[cfg(unix)]
const SIGKILL: i32 = 9;
#[cfg(unix)]
const SIGTERM: i32 = 15;

#[cfg(windows)]
const CREATE_NO_WINDOW: u32 = 0x0800_0000;

#[cfg(windows)]
fn windows_taskkill_path() -> PathBuf {
    env::var_os("SystemRoot")
        .map(PathBuf::from)
        .filter(|root| root.is_absolute())
        .unwrap_or_else(|| PathBuf::from(r"C:\Windows"))
        .join("System32")
        .join("taskkill.exe")
}

#[cfg(unix)]
unsafe extern "C" {
    #[link_name = "kill"]
    fn posix_kill(pid: i32, signal: i32) -> i32;
}

#[cfg(unix)]
fn send_unix_signal(pid: i32, signal: i32) -> io::Result<()> {
    // SAFETY: POSIX `kill` only observes the integer pid/signal values. No
    // pointers or borrowed memory cross the FFI boundary.
    if unsafe { posix_kill(pid, signal) } == 0 {
        Ok(())
    } else {
        Err(io::Error::last_os_error())
    }
}

#[cfg(unix)]
fn signal_process_group(pid: u32, signal: i32) -> io::Result<()> {
    let process_group = i32::try_from(pid)
        .map_err(|_| io::Error::new(io::ErrorKind::InvalidInput, "process id is out of range"))?;
    send_unix_signal(-process_group, signal)
}

#[cfg(unix)]
fn process_group_exists(pid: u32) -> bool {
    signal_process_group(pid, 0).is_ok()
}

/// The `download:` part of a progress template selects the progress type and is
/// not printed, so the printed line carries its own marker.
const PROGRESS_MARKER: &str = "neku-progress|";
const PROGRESS_TEMPLATE: &str =
    "download:neku-progress|%(progress._percent_str)s|%(progress._speed_str)s|%(progress._eta_str)s";

fn parse_progress(line: &str) -> Option<(f64, String, String)> {
    let payload = line.trim_start().strip_prefix(PROGRESS_MARKER)?;
    let mut fields = payload.split('|');
    let percent = fields.next()?.trim().trim_end_matches('%').parse().ok()?;
    let speed = fields.next().unwrap_or_default().trim().to_string();
    let eta = fields.next().unwrap_or_default().trim().to_string();
    Some((percent, speed, eta))
}

fn preview_artist(data: &Value) -> Option<String> {
    string_field(data, &["artist", "uploader", "channel"]).or_else(|| {
        data.get("entries")?.as_array()?.iter().find_map(|entry| {
            string_field(entry, &["artist", "uploader", "channel"])
        })
    })
}

fn string_field(value: &Value, keys: &[&str]) -> Option<String> {
    keys.iter()
        .find_map(|key| value.get(*key).and_then(Value::as_str).filter(|text| !text.trim().is_empty()))
        .map(ToOwned::to_owned)
}

fn clean_error(raw: &str) -> String {
    let meaningful = raw
        .lines()
        .rev()
        .find(|line| !line.trim().is_empty())
        .unwrap_or("The downloader exited unexpectedly")
        .trim();
    let without_home = if let Some(home) = std::env::var_os("HOME") {
        meaningful.replace(&home.to_string_lossy().to_string(), "~")
    } else {
        meaningful.to_string()
    };
    without_home.chars().take(500).collect()
}

#[cfg(test)]
mod tests {
    #[cfg(unix)]
    #[tokio::test]
    async fn fake_downloader_streams_progress_errors_and_persistent_logs() {
        use std::os::unix::fs::PermissionsExt;
        let root = std::env::temp_dir().join(format!("neku-fake-ytdlp-{}", uuid::Uuid::new_v4()));
        std::fs::create_dir_all(&root).unwrap();
        let executable = root.join("fake yt-dlp");
        std::fs::write(&executable, "#!/bin/sh\nprintf '%s\\n' 'neku-progress|42.0%|1 MiB/s|00:03' 'neku-file|/music/test.mp3'\nprintf '%s\\n' 'ERROR: unavailable track' >&2\nexit 0\n").unwrap();
        std::fs::set_permissions(&executable, std::fs::Permissions::from_mode(0o755)).unwrap();
        let db = super::Database::new(root.join("jobs.db")).unwrap();
        let mut job = sample_spotify_job();
        let app = tauri::test::mock_app();
        let outcome = super::run_streaming(super::Command::new(&executable), "fake yt-dlp", &mut job,
            &db, app.handle(), &super::CancellationToken::new(), |job, line| {
                if let Some((percent, _, _)) = super::parse_progress(line) { job.progress = percent; true } else { false }
            }).await.unwrap();
        assert!(outcome.success); // yt-dlp can exit 0 despite an unavailable entry.
        assert_eq!(outcome.errors, 1);
        assert_eq!(outcome.produced, 1);
        assert_eq!(job.progress, 42.0);
        let saved = db.get_job(&job.id).unwrap().unwrap();
        assert!(saved.recent_log.iter().any(|line| line.contains("unavailable track")));
        assert!(!saved.recent_log.iter().any(|line| line.starts_with("neku-progress|")));
        let _ = std::fs::remove_dir_all(root);
    }

    #[test]
    fn album_preview_uses_entry_artist_when_collection_metadata_is_blank() {
        let data = serde_json::json!({
            "artist": " ", "uploader": null,
            "entries": [null, {"channel": "Album artist"}]
        });
        assert_eq!(super::preview_artist(&data).as_deref(), Some("Album artist"));
        let data = serde_json::json!({"artist": "Catalog artist", "entries": [{"artist": "Other"}]});
        assert_eq!(super::preview_artist(&data).as_deref(), Some("Catalog artist"));
        assert_eq!(super::preview_artist(&serde_json::json!({"entries": [null]})), None);
    }

    #[test]
    fn old_saved_jobs_load_without_partial_completion_metadata() {
        let mut value = serde_json::to_value(sample_spotify_job()).unwrap();
        value.as_object_mut().unwrap().remove("unavailableTracks");
        let job: super::DownloadJob = serde_json::from_value(value).unwrap();
        assert_eq!(job.unavailable_tracks, 0);
    }
    use super::*;
    use crate::models::DownloadRequest;

    #[test]
    fn parses_structured_progress() {
        let parsed = parse_progress("neku-progress| 42.5%|3.2MiB/s|00:12").unwrap();
        assert_eq!(parsed.0, 42.5);
        assert_eq!(parsed.1, "3.2MiB/s");
        assert_eq!(parsed.2, "00:12");
    }

    #[test]
    fn bounded_log_capture_preserves_only_the_meaningful_tail() {
        let mut tail = Vec::new();
        append_bounded_tail(&mut tail, b"012345", 8);
        append_bounded_tail(&mut tail, b"6789", 8);
        assert_eq!(tail, b"23456789");

        append_bounded_tail(&mut tail, b"abcdefghijkl", 8);
        assert_eq!(tail, b"efghijkl");
    }

    #[tokio::test]
    async fn bounded_metadata_capture_rejects_incomplete_json() {
        assert_eq!(
            read_bounded_complete(&br#"{"ok":true}"#[..], 32)
                .await
                .unwrap(),
            br#"{"ok":true}"#
        );
        let error = read_bounded_complete(&b"0123456789"[..], 8)
            .await
            .unwrap_err();
        assert!(error.contains("safety limit"));
    }

    #[test]
    fn routes_only_direct_audio_providers_to_ytdlp() {
        for provider in [
            Provider::YouTube,
            Provider::YouTubeMusic,
            Provider::SoundCloud,
        ] {
            assert_eq!(
                backend_for_provider(&provider).unwrap(),
                DownloadBackend::YtDlp
            );
        }
        assert_eq!(
            backend_for_provider(&Provider::Spotify).unwrap(),
            DownloadBackend::SpotDl
        );
        assert_eq!(
            backend_for_provider(&Provider::AppleMusic).unwrap(),
            DownloadBackend::Catalog
        );
        assert_eq!(
            backend_for_provider(&Provider::Deezer).unwrap(),
            DownloadBackend::Catalog
        );
        assert!(backend_for_provider(&Provider::Unknown).is_err());
    }

    #[test]
    fn parses_playlist_position_and_sanitizes_names() {
        assert_eq!(parse_item("[download] Downloading item 3 of 12"), Some((3, 12)));
        assert_eq!(parse_item("[download] 50%"), None);
        assert_eq!(sanitize_file_name("AC/DC: Back in Black?"), "AC_DC_ Back in Black_");
        assert_eq!(sanitize_file_name("con"), "_con");
        assert_eq!(sanitize_file_name("  ...  "), "Untitled");
        assert_eq!(output_extension("vorbis"), "ogg");
        assert!(friendly_error("ERROR: Sign in to confirm you're not a bot").contains("cookies"));
        assert!(friendly_error("ERROR: unable to download video data: HTTP Error 403: Forbidden").contains("403"));
    }

    #[test]
    fn removes_failed_attempt_leftovers_only() {
        let dir = std::env::temp_dir().join(format!("neku-leftovers-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        for name in ["A - B.jpg", "A - B.webm.part", "A - B.mp3", "A - B C.jpg"] {
            std::fs::write(dir.join(name), b"x").unwrap();
        }
        remove_leftovers(&dir, "A - B", "mp3");
        let mut left: Vec<String> = std::fs::read_dir(&dir)
            .unwrap()
            .map(|entry| entry.unwrap().file_name().to_string_lossy().into_owned())
            .collect();
        left.sort();
        assert_eq!(left, ["A - B C.jpg", "A - B.mp3"]);
        let _ = std::fs::remove_dir_all(dir);
    }

    #[test]
    fn maps_spotify_formats_and_rejects_alac() {
        assert_eq!(spotdl_format("vorbis").unwrap(), "ogg");
        assert_eq!(spotdl_format("flac").unwrap(), "flac");
        assert!(spotdl_format("alac").unwrap_err().contains("ALAC"));
        assert_eq!(spotdl_bitrate("ogg", "320").unwrap(), "320k");
        assert_eq!(spotdl_bitrate("opus", "256kbps").unwrap(), "256k");
        assert_eq!(spotdl_bitrate("flac", "0").unwrap(), "disable");
    }

    #[test]
    fn constructs_spotdl_argv_without_shell_interpolation() {
        let mut job = sample_spotify_job();
        job.request.output_dir = "/tmp/Neku music;safe".into();
        job.request.media_kind = MediaKind::Playlist;
        job.request.format = "vorbis".into();
        job.request.quality = "320".into();
        let ffmpeg = Path::new("/opt/Neku Tools/ffmpeg");

        let args = spotdl_args(&job, ffmpeg).unwrap();
        let args: Vec<String> = args
            .iter()
            .map(|argument| argument.to_string_lossy().into_owned())
            .collect();

        assert_eq!(args.first().map(String::as_str), Some("download"));
        assert_eq!(args.get(1), Some(&job.request.url));
        assert_eq!(argument_value(&args, "--format"), Some("ogg"));
        assert_eq!(argument_value(&args, "--bitrate"), Some("320k"));
        assert_eq!(
            argument_value(&args, "--ffmpeg"),
            Some("/opt/Neku Tools/ffmpeg")
        );
        assert_eq!(
            argument_value(&args, "--yt-dlp-args"),
            Some("--ignore-config --no-plugin-dirs --no-exec --no-update")
        );
        assert_eq!(
            argument_value(&args, "--output").map(|value| value.replace('\\', "/")),
            Some("/tmp/Neku music;safe/{list-name}/{list-position} - {title}.{output-ext}".into())
        );
        assert!(!args.iter().any(|argument| argument == "--audio-format"));
    }

    #[test]
    fn rejects_alac_before_constructing_a_spotify_command() {
        let mut job = sample_spotify_job();
        job.request.format = "alac".into();
        let error = spotdl_args(&job, Path::new("/tools/ffmpeg")).unwrap_err();
        assert!(error.contains("ALAC"));
    }

    fn argument_value<'a>(args: &'a [String], option: &str) -> Option<&'a str> {
        args.iter()
            .position(|argument| argument == option)
            .and_then(|index| args.get(index + 1))
            .map(String::as_str)
    }

    fn sample_spotify_job() -> DownloadJob {
        let now = Utc::now();
        DownloadJob {
            id: "spotify-test".into(),
            request: DownloadRequest {
                url: "https://open.spotify.com/track/abc?si=def&value=safe".into(),
                title: None,
                artist: None,
                provider: Provider::Spotify,
                media_kind: MediaKind::Track,
                format: "mp3".into(),
                quality: "320".into(),
                output_dir: "/tmp/music".into(),
                track_numbers: true,
                embed_metadata: true,
                embed_artwork: true,
            },
            title: "Test track".into(),
            artist: "Test artist".into(),
            status: JobStatus::Queued,
            progress: 0.0,
            speed: String::new(),
            eta: String::new(),
            stage: "Queued".into(),
            error: None,
            created_at: now,
            updated_at: now,
            recent_log: Vec::new(),
            unavailable_tracks: 0,
        }
    }

    #[cfg(unix)]
    #[tokio::test]
    async fn cancellation_terminates_the_descendant_process_group() {
        let mut command = Command::new("/bin/sh");
        command
            .args(["-c", "sleep 30 & echo $!; wait"])
            .stdin(Stdio::null())
            .stdout(Stdio::piped())
            .stderr(Stdio::null());
        configure_process_tree(&mut command);

        let mut child = command.spawn().expect("spawn process tree fixture");
        let child_pid = child.id().expect("fixture child pid");
        let stdout = child.stdout.take().expect("fixture stdout");
        let mut lines = BufReader::new(stdout).lines();
        let descendant_pid: i32 = timeout(Duration::from_secs(2), lines.next_line())
            .await
            .expect("fixture printed descendant pid")
            .expect("read descendant pid")
            .expect("descendant pid line")
            .trim()
            .parse()
            .expect("numeric descendant pid");

        assert_ne!(descendant_pid, child_pid as i32);
        assert!(unix_process_exists(descendant_pid));

        terminate_process_tree(&mut child).await;

        for _ in 0..20 {
            if !unix_process_exists(descendant_pid) {
                return;
            }
            sleep(Duration::from_millis(25)).await;
        }
        assert!(
            !unix_process_exists(descendant_pid),
            "descendant process {descendant_pid} survived cancellation"
        );
    }

    #[cfg(unix)]
    fn unix_process_exists(pid: i32) -> bool {
        match send_unix_signal(pid, 0) {
            Ok(()) => true,
            // ESRCH is 3 on the Unix targets supported by Tauri (macOS and
            // Linux). Other failures, such as EPERM, still mean it exists.
            Err(error) => error.raw_os_error() != Some(3),
        }
    }
}
