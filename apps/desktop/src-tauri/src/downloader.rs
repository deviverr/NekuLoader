#[cfg(unix)]
use std::io;
use std::{
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
    sync::Mutex,
    time::timeout,
};
use tokio_util::sync::CancellationToken;

use crate::{
    models::{DownloadJob, JobEvent, JobStatus, MediaKind, MediaPreview, Provider},
    provider,
    storage::Database,
    tooling::{self, Toolchain},
};

pub async fn inspect_link(url: &str, app_data: &Path) -> Result<MediaPreview, String> {
    let detection = provider::detect(url)?;
    if !detection.supported {
        return Err(detection.message);
    }

    let toolchain = tooling::discover(app_data);
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
            Provider::Deezer => Some(
                "Deezer metadata matching is not available in this 3.2 alpha yet.".into(),
            ),
            Provider::AppleMusic => Some(
                "Apple Music metadata matching is not available in this 3.2 alpha yet.".into(),
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
        "--no-playlist",
    ]);
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
    let track_count = data.get("entries").and_then(Value::as_array).map(Vec::len);
    let media_kind = if track_count.unwrap_or(0) > 1 && detection.media_kind == MediaKind::Track {
        MediaKind::Playlist
    } else {
        detection.media_kind
    };
    Ok(MediaPreview {
        url: detection.normalized_url,
        title: string_field(&data, &["title", "track"]).unwrap_or_else(|| "Untitled media".into()),
        artist: string_field(&data, &["artist", "uploader", "channel"])
            .unwrap_or_else(|| "Unknown artist".into()),
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
        Ok(()) => {
            job.status = JobStatus::Completed;
            job.progress = 100.0;
            job.stage = "Complete".into();
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
) -> Result<(), String> {
    // Re-detect at execution time instead of trusting the provider supplied by
    // the webview. This is the routing boundary that guarantees Spotify,
    // Apple Music, and Deezer URLs can never fall through to raw yt-dlp.
    let detection = provider::detect(&job.request.url)?;
    job.request.url = detection.normalized_url;
    job.request.provider = detection.provider.clone();
    if detection.media_kind != MediaKind::Unknown {
        job.request.media_kind = detection.media_kind;
    }

    let tools = tooling::discover(app_data);
    let (mut command, backend) =
        build_download_command(job, &job.request.provider, &tools, app_data)?;
    std::fs::create_dir_all(&job.request.output_dir)
        .map_err(|error| format!("Cannot create output folder: {error}"))?;

    job.status = JobStatus::Preparing;
    job.stage = backend.preparing_stage().into();
    job.updated_at = Utc::now();
    database.save_job(job)?;
    app.emit("job:update", JobEvent { job: job.clone() })
        .map_err(|error| error.to_string())?;

    command
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    configure_process_tree(&mut command);

    let mut child = command
        .spawn()
        .map_err(|error| format!("Could not start {}: {error}", backend.label()))?;
    let stdout = match child.stdout.take() {
        Some(stdout) => stdout,
        None => {
            terminate_process_tree(&mut child).await;
            return Err("Downloader output was unavailable".into());
        }
    };
    let stderr = match child.stderr.take() {
        Some(stderr) => stderr,
        None => {
            terminate_process_tree(&mut child).await;
            return Err("Downloader error stream was unavailable".into());
        }
    };
    let mut lines = BufReader::new(stdout).lines();
    let stderr_task = tokio::spawn(read_bounded_tail(stderr, MAX_CAPTURED_LOG_BYTES));

    job.status = JobStatus::Downloading;
    job.stage = backend.running_stage().into();
    job.updated_at = Utc::now();
    if let Err(error) = database.save_job(job) {
        terminate_process_tree(&mut child).await;
        let _ = stderr_task.await;
        return Err(error);
    }
    if let Err(error) = app.emit("job:update", JobEvent { job: job.clone() }) {
        terminate_process_tree(&mut child).await;
        let _ = stderr_task.await;
        return Err(error.to_string());
    }

    loop {
        tokio::select! {
            _ = token.cancelled() => {
                terminate_process_tree(&mut child).await;
                let _ = stderr_task.await;
                return Err("Download stopped".into());
            }
            line = lines.next_line() => {
                match line {
                    Ok(Some(line)) => {
                        if let Some((percent, speed, eta)) = parse_progress(&line) {
                            job.progress = percent;
                            job.speed = speed;
                            job.eta = eta;
                            job.stage = if percent >= 99.5 { "Converting and tagging" } else { "Downloading" }.into();
                            job.status = if percent >= 99.5 { JobStatus::Converting } else { JobStatus::Downloading };
                            job.updated_at = Utc::now();
                            if let Err(error) = database.save_job(job) {
                                terminate_process_tree(&mut child).await;
                                let _ = stderr_task.await;
                                return Err(error);
                            }
                            if let Err(error) = app.emit("job:update", JobEvent { job: job.clone() }) {
                                terminate_process_tree(&mut child).await;
                                let _ = stderr_task.await;
                                return Err(error.to_string());
                            }
                        }
                    }
                    Ok(None) => break,
                    Err(error) => {
                        terminate_process_tree(&mut child).await;
                        let _ = stderr_task.await;
                        return Err(format!("Could not read downloader output: {error}"));
                    }
                }
            }
        }
    }

    let status = tokio::select! {
        _ = token.cancelled() => {
            terminate_process_tree(&mut child).await;
            let _ = stderr_task.await;
            return Err("Download stopped".into());
        }
        status = child.wait() => status.map_err(|error| error.to_string())?,
    };
    let stderr = stderr_task.await.unwrap_or_default();
    if !status.success() {
        return Err(clean_error(&stderr));
    }
    Ok(())
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum DownloadBackend {
    YtDlp,
    SpotDl,
}

impl DownloadBackend {
    fn label(self) -> &'static str {
        match self {
            Self::YtDlp => "yt-dlp",
            Self::SpotDl => "SpotDL",
        }
    }

    fn preparing_stage(self) -> &'static str {
        match self {
            Self::YtDlp => "Preparing tools",
            Self::SpotDl => "Preparing Spotify match",
        }
    }

    fn running_stage(self) -> &'static str {
        match self {
            Self::YtDlp => "Downloading",
            Self::SpotDl => "Matching and downloading",
        }
    }
}

fn backend_for_provider(provider: &Provider) -> Result<DownloadBackend, String> {
    match provider {
        Provider::YouTube | Provider::YouTubeMusic | Provider::SoundCloud => {
            Ok(DownloadBackend::YtDlp)
        }
        Provider::Spotify => Ok(DownloadBackend::SpotDl),
        Provider::Deezer => Err(
            "Deezer downloads are not available in NekuLoader 3.2 alpha yet. A metadata-to-audio matching resolver is still required."
                .into(),
        ),
        Provider::AppleMusic => Err(
            "Apple Music downloads are not available in NekuLoader 3.2 alpha yet. A metadata-to-audio matching resolver is still required."
                .into(),
        ),
        Provider::Unknown => Err("This source is not supported yet".into()),
    }
}

fn build_download_command(
    job: &DownloadJob,
    provider: &Provider,
    tools: &Toolchain,
    app_data: &Path,
) -> Result<(Command, DownloadBackend), String> {
    let backend = backend_for_provider(provider)?;
    let mut command = match backend {
        DownloadBackend::YtDlp => build_ytdlp_command(job, tools)?,
        DownloadBackend::SpotDl => build_spotdl_command(job, tools)?,
    };
    command.env("PATH", managed_path(tools, app_data)?);
    Ok((command, backend))
}

fn build_ytdlp_command(job: &DownloadJob, tools: &Toolchain) -> Result<Command, String> {
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
    let archive = Path::new(&job.request.output_dir).join(".nekuloader-download-archive.txt");
    let ffmpeg_dir = ffmpeg.parent().unwrap_or(Path::new("."));

    let mut command = Command::new(yt_dlp);
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
        "--download-archive",
    ]);
    command.arg(archive);
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
    if job.request.embed_artwork && job.request.format != "wav" {
        command.args(["--embed-thumbnail", "--convert-thumbnails", "jpg"]);
    }
    command.args([
        "--newline",
        "--progress-template",
        "download:%(progress._percent_str)s|%(progress._speed_str)s|%(progress._eta_str)s",
    ]);
    command.arg(if collection {
        "--yes-playlist"
    } else {
        "--no-playlist"
    });
    command
        .arg("-o")
        .arg(output_template)
        .arg("--")
        .arg(&job.request.url);
    Ok(command)
}

fn build_spotdl_command(job: &DownloadJob, tools: &Toolchain) -> Result<Command, String> {
    let spotdl = tools.spotdl.as_deref().ok_or_else(|| {
        "SpotDL is required for Spotify downloads and was not found. Install SpotDL, then refresh Settings → Components."
            .to_string()
    })?;
    let ffmpeg = tools.ffmpeg.as_deref().ok_or_else(|| {
        "FFmpeg is required for Spotify downloads. Open Settings → Components.".to_string()
    })?;

    let mut command = Command::new(spotdl);
    command.args(spotdl_args(job, ffmpeg)?);
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
    let archive = Path::new(&job.request.output_dir).join(".nekuloader-spotdl-archive.json");

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
        OsString::from("--archive"),
        archive.into_os_string(),
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

fn parse_progress(line: &str) -> Option<(f64, String, String)> {
    let payload = line.strip_prefix("download:")?;
    let mut fields = payload.split('|');
    let percent = fields.next()?.trim().trim_end_matches('%').parse().ok()?;
    let speed = fields.next().unwrap_or_default().trim().to_string();
    let eta = fields.next().unwrap_or_default().trim().to_string();
    Some((percent, speed, eta))
}

fn string_field(value: &Value, keys: &[&str]) -> Option<String> {
    keys.iter()
        .find_map(|key| value.get(*key).and_then(Value::as_str))
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
    use super::*;
    use crate::models::DownloadRequest;

    #[test]
    fn parses_structured_progress() {
        let parsed = parse_progress("download: 42.5%|3.2MiB/s|00:12").unwrap();
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
        assert!(backend_for_provider(&Provider::AppleMusic).is_err());
        assert!(backend_for_provider(&Provider::Deezer).is_err());
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
            argument_value(&args, "--output"),
            Some("/tmp/Neku music;safe/{list-name}/{list-position} - {title}.{output-ext}")
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
