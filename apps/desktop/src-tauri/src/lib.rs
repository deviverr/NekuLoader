mod folder_icons;
mod artwork;
mod devices;
mod downloader;
mod library;
mod models;
mod playback;
mod provider;
mod resolver;
mod share;
mod storage;
mod tagging;
mod tooling;

use std::{
    collections::{HashMap, VecDeque},
    future::Future,
    path::PathBuf,
    pin::Pin,
    sync::Arc,
};

use chrono::Utc;
use models::{
    AndroidDevice, AppSettings, BootstrapResponse, CustomThemeColors, DownloadJob,
    DownloadRequest, JobEvent, JobStatus, LibraryTrack, MediaPreview, PlaybackState,
    SourceDetection, ToolInfo,
};
use share::{ShareInfo, ShareManager};
use storage::Database;
use tauri::{AppHandle, Emitter, Manager, State};
use tokio::sync::Mutex;
use tokio_util::sync::CancellationToken;
use uuid::Uuid;

/// Raw downloader output retained per job for the queue terminal panel. Capped
/// so a chatty subprocess cannot grow memory without bound.
pub const MAX_JOB_LOG_LINES: usize = 2_000;

#[derive(Clone)]
struct JobControl {
    token: CancellationToken,
    paused: Arc<Mutex<bool>>,
}

struct AppState {
    database: Database,
    app_data: PathBuf,
    controls: Arc<Mutex<HashMap<String, JobControl>>>,
    /// Jobs the user started while every download slot was busy, in order.
    pending: Arc<Mutex<VecDeque<String>>>,
    share: Arc<ShareManager>,
    legacy_imported: bool,
}

impl AppState {
    fn settings(&self) -> Result<AppSettings, String> {
        self.database
            .load_settings(AppSettings::defaults(&home_directory()))
    }

    fn artwork_cache(&self) -> PathBuf {
        self.app_data.join("artwork-cache")
    }
}

#[tauri::command]
async fn bootstrap(state: State<'_, AppState>) -> Result<BootstrapResponse, String> {
    let home = home_directory();
    let settings = state.database.load_settings(AppSettings::defaults(&home))?;
    Ok(BootstrapResponse {
        settings,
        jobs: state.database.list_jobs()?,
        history: state.database.list_history()?,
        tools: tooling::inspect_quick(&state.app_data),
        legacy_imported: state.legacy_imported,
        version: env!("CARGO_PKG_VERSION").into(),
        is_windows: cfg!(windows),
        playback: state.database.load_playback_state()?,
        custom_themes: state.database.list_custom_themes()?,
    })
}

#[tauri::command]
async fn detect_source(url: String) -> Result<SourceDetection, String> {
    provider::detect_resolved(&url).await
}

#[tauri::command]
async fn inspect_link(url: String, state: State<'_, AppState>) -> Result<MediaPreview, String> {
    downloader::inspect_link(&url, &state.app_data, &state.settings()?).await
}

#[tauri::command]
fn save_settings(
    settings: AppSettings,
    app: AppHandle,
    state: State<'_, AppState>,
) -> Result<AppSettings, String> {
    validate_settings(&settings)?;
    if let Some(image) = settings.background_image_path.as_deref().map(PathBuf::from)
        && image.is_file()
    {
        allow_asset(&app, &image)?;
    }
    state.database.save_settings(&settings)?;
    Ok(settings)
}

#[tauri::command]
async fn refresh_tools(state: State<'_, AppState>) -> Result<Vec<ToolInfo>, String> {
    Ok(tooling::inspect(&state.app_data).await)
}

/// Every folder the player should look in, in priority order.
///
/// The download folder is always included; the rest are opt-in so a first run
/// never sweeps the whole home directory without being asked.
fn music_roots(settings: &AppSettings) -> Vec<PathBuf> {
    let home = home_directory();
    let mut roots = vec![PathBuf::from(&settings.output_dir)];

    for folder in &settings.music_folders {
        roots.push(PathBuf::from(folder));
    }

    if settings.scan_system_music {
        roots.push(PathBuf::from(&home).join("Music"));
    }

    if settings.scan_everything {
        roots.push(PathBuf::from(&home));
    }

    // A nested root would only re-walk files the outer one already covered.
    roots.retain(|root| root.is_dir());
    roots.dedup();
    roots
}

#[tauri::command]
async fn scan_library(state: State<'_, AppState>) -> Result<Vec<LibraryTrack>, String> {
    let settings = state.settings()?;
    let roots = music_roots(&settings);
    if roots.is_empty() {
        return Err(format!(
            "The music folder {} is not available. Connect the drive, or choose another folder in Settings.",
            settings.output_dir
        ));
    }
    tokio::task::spawn_blocking(move || library::scan_all(&roots))
        .await
        .map_err(|error| error.to_string())?
}

#[tauri::command]
async fn install_components(state: State<'_, AppState>) -> Result<Vec<ToolInfo>, String> {
    // Hold the scheduling lock for the entire promotion, so a new job cannot
    // start between the idle check and replacing its downloader executable.
    let controls = state.controls.lock().await;
    if !controls.is_empty() {
        return Err("Pause active downloads before updating components".into());
    }
    let result = tooling::install_or_update(&state.app_data).await;
    drop(controls);
    result
}

#[tauri::command]
fn queue_download(
    request: DownloadRequest,
    state: State<'_, AppState>,
) -> Result<DownloadJob, String> {
    validate_request(&request)?;
    let now = Utc::now();
    let job = DownloadJob {
        id: Uuid::new_v4().to_string(),
        title: request
            .title
            .clone()
            .filter(|value| !value.is_empty())
            .unwrap_or_else(|| "Resolving media".into()),
        artist: request.artist.clone().unwrap_or_default(),
        request,
        status: JobStatus::Queued,
        progress: 0.0,
        speed: String::new(),
        eta: String::new(),
        stage: "Waiting".into(),
        error: None,
        created_at: now,
        updated_at: now,
        recent_log: Vec::new(),
            unavailable_tracks: 0,
    };
    state.database.save_job(&job)?;
    Ok(job)
}

#[tauri::command]
fn save_playback_state(
    playback: PlaybackState,
    state: State<'_, AppState>,
) -> Result<(), String> {
    if !(0.0..=1.0).contains(&playback.volume) {
        return Err("Playback volume must be between 0 and 1".into());
    }
    state.database.save_playback_state(&playback)
}

#[tauri::command]
fn load_playback_state(state: State<'_, AppState>) -> Result<PlaybackState, String> {
    state.database.load_playback_state()
}

/// Confirms a track may be handed to the webview's `<audio>` element. The
/// frontend passes the returned path through `convertFileSrc`.
#[tauri::command]
fn resolve_playable_url(
    path: String,
    app: AppHandle,
    state: State<'_, AppState>,
) -> Result<String, String> {
    let roots = music_roots(&state.settings()?);
    let resolved = playback::authorize_path(&PathBuf::from(path), &roots)?;
    // The static asset scope only covers $HOME and app data; music on an
    // external drive (/Volumes, D:\) is admitted file by file once
    // `authorize_path` has confirmed it lives under a configured music root.
    allow_asset(&app, &resolved)?;
    Ok(resolved.to_string_lossy().into_owned())
}

fn allow_asset(app: &AppHandle, path: &std::path::Path) -> Result<(), String> {
    app.asset_protocol_scope()
        .allow_file(path)
        .map_err(|error| format!("Could not open {}: {error}", path.display()))
}

/// Returns a cached cover-art file path for a library track, or `None` when the
/// track has no embedded picture.
#[tauri::command]
async fn get_track_artwork(
    path: String,
    app: AppHandle,
    state: State<'_, AppState>,
) -> Result<Option<String>, String> {
    let roots = music_roots(&state.settings()?);
    let track = playback::authorize_path(&PathBuf::from(path), &roots)?;
    let cache = state.artwork_cache();
    let cached = tokio::task::spawn_blocking(move || tagging::cache_artwork(&track, &cache))
        .await
        .map_err(|error| error.to_string())?;
    // A folder cover.jpg is served from the music folder, not the cache.
    if let Some(path) = &cached {
        allow_asset(&app, path)?;
    }
    Ok(cached.map(|value| value.to_string_lossy().into_owned()))
}

#[tauri::command]
fn save_custom_theme(theme: CustomThemeColors, state: State<'_, AppState>) -> Result<(), String> {
    if theme.name.trim().is_empty() {
        return Err("Name the theme before saving it".into());
    }
    state.database.save_custom_theme(&theme)
}

#[tauri::command]
fn list_custom_themes(state: State<'_, AppState>) -> Result<Vec<CustomThemeColors>, String> {
    state.database.list_custom_themes()
}

#[tauri::command]
fn delete_custom_theme(name: String, state: State<'_, AppState>) -> Result<(), String> {
    state.database.delete_custom_theme(&name)
}

#[tauri::command(rename_all = "camelCase")]
async fn start_job(job_id: String, app: AppHandle) -> Result<(), String> {
    launch_or_wait(app, job_id).await
}

fn concurrency_limit(state: &AppState) -> Result<usize, String> {
    Ok(state.settings()?.concurrent_downloads.max(1) as usize)
}

fn emit_job(app: &AppHandle, job: &DownloadJob) {
    let _ = app.emit("job:update", JobEvent { job: job.clone() });
}

/// Starts the job now when a download slot is free, otherwise parks it in the
/// pending queue. Finishing jobs pull the next pending one via [`pump_queue`].
async fn launch_or_wait(app: AppHandle, job_id: String) -> Result<(), String> {
    let state = app.state::<AppState>();
    let mut controls = state.controls.lock().await;
    if controls.contains_key(&job_id) {
        return Ok(());
    }
    let mut job = state
        .database
        .get_job(&job_id)?
        .ok_or_else(|| "Download job not found".to_string())?;
    if job.status == JobStatus::Completed && job.unavailable_tracks == 0 {
        return Err("This job is already complete".into());
    }
    job.status = JobStatus::Queued;
    job.error = None;
    job.unavailable_tracks = 0;
    job.progress = 0.0;
    job.speed.clear();
    job.eta.clear();
    job.updated_at = Utc::now();

    if controls.len() >= concurrency_limit(&state)? {
        let mut pending = state.pending.lock().await;
        if !pending.contains(&job_id) {
            pending.push_back(job_id.clone());
        }
        job.stage = "Waiting for a free slot".into();
        state.database.save_job(&job)?;
        emit_job(&app, &job);
        return Ok(());
    }
    state.pending.lock().await.retain(|id| id != &job_id);

    job.stage = "Starting".into();
    state.database.save_job(&job)?;
    emit_job(&app, &job);

    let token = CancellationToken::new();
    let paused = Arc::new(Mutex::new(false));
    controls.insert(
        job_id.clone(),
        JobControl {
            token: token.clone(),
            paused: paused.clone(),
        },
    );
    drop(controls);

    let database = state.database.clone();
    let app_data = state.app_data.clone();
    let control_map = state.controls.clone();
    let worker_app = app.clone();
    tauri::async_runtime::spawn(async move {
        downloader::run_job(job, database, worker_app.clone(), app_data, token, paused).await;
        control_map.lock().await.remove(&job_id);
        pump_queue(worker_app).await;
    });
    Ok(())
}

/// Fills free download slots from the pending queue. Boxed because it and
/// [`launch_or_wait`] reach each other through the spawned worker.
fn pump_queue(app: AppHandle) -> Pin<Box<dyn Future<Output = ()> + Send>> {
    Box::pin(async move {
        loop {
            let next = {
                let state = app.state::<AppState>();
                let Ok(limit) = concurrency_limit(&state) else { return };
                if state.controls.lock().await.len() >= limit {
                    return;
                }
                state.pending.lock().await.pop_front()
            };
            let Some(job_id) = next else { return };
            if let Err(error) = launch_or_wait(app.clone(), job_id.clone()).await {
                log::warn!("Could not start queued job {job_id}: {error}");
            }
        }
    })
}

#[tauri::command(rename_all = "camelCase")]
async fn pause_job(
    job_id: String,
    app: AppHandle,
    state: State<'_, AppState>,
) -> Result<(), String> {
    let control = state.controls.lock().await.get(&job_id).cloned();
    if let Some(control) = control {
        *control.paused.lock().await = true;
        control.token.cancel();
    } else if remove_pending(&state, &job_id).await {
        set_idle_status(&app, &state, &job_id, JobStatus::Paused, "Paused")?;
    }
    Ok(())
}

async fn remove_pending(state: &AppState, job_id: &str) -> bool {
    let mut pending = state.pending.lock().await;
    let before = pending.len();
    pending.retain(|id| id != job_id);
    pending.len() != before
}

fn set_idle_status(
    app: &AppHandle,
    state: &AppState,
    job_id: &str,
    status: JobStatus,
    stage: &str,
) -> Result<(), String> {
    if let Some(mut job) = state.database.get_job(job_id)?
        && job.status != JobStatus::Completed
    {
        job.status = status;
        job.stage = stage.into();
        job.updated_at = Utc::now();
        state.database.save_job(&job)?;
        emit_job(app, &job);
    }
    Ok(())
}

#[tauri::command(rename_all = "camelCase")]
async fn cancel_job(
    job_id: String,
    app: AppHandle,
    state: State<'_, AppState>,
) -> Result<(), String> {
    let control = state.controls.lock().await.get(&job_id).cloned();
    if let Some(control) = control {
        *control.paused.lock().await = false;
        control.token.cancel();
    } else {
        remove_pending(&state, &job_id).await;
        set_idle_status(&app, &state, &job_id, JobStatus::Cancelled, "Cancelled")?;
    }
    Ok(())
}

// --- devices ----------------------------------------------------------------

fn authorize_files(state: &AppState, files: &[String]) -> Result<Vec<PathBuf>, String> {
    let roots = music_roots(&state.settings()?);
    files
        .iter()
        .map(|file| playback::authorize_path(&PathBuf::from(file), &roots))
        .collect()
}

#[tauri::command]
async fn list_android_devices(state: State<'_, AppState>) -> Result<Vec<AndroidDevice>, String> {
    devices::list_devices(&state.app_data).await
}

#[tauri::command(rename_all = "camelCase")]
async fn push_to_android(
    device_id: String,
    files: Vec<String>,
    destination: String,
    app: AppHandle,
    state: State<'_, AppState>,
) -> Result<(), String> {
    let files = authorize_files(&state, &files)?;
    devices::push(&app, &state.app_data, &device_id, &files, &destination).await
}

/// Re-fetches square cover art for library tracks from the source page their
/// tags record. Returns how many tracks were updated.
#[tauri::command]
async fn repair_artwork(
    files: Vec<String>,
    app: AppHandle,
    state: State<'_, AppState>,
) -> Result<usize, String> {
    let files = authorize_files(&state, &files)?;
    artwork::repair(&app, &state.app_data, files).await
}

#[tauri::command]
async fn start_wifi_share(
    files: Vec<String>,
    state: State<'_, AppState>,
) -> Result<ShareInfo, String> {
    let files = authorize_files(&state, &files)?;
    state
        .share
        .start(files, state.app_data.join("share-cache"))
        .await
}

#[tauri::command]
async fn stop_wifi_share(state: State<'_, AppState>) -> Result<(), String> {
    state.share.stop().await;
    Ok(())
}

#[tauri::command]
async fn current_wifi_share(state: State<'_, AppState>) -> Result<Option<ShareInfo>, String> {
    Ok(state.share.current().await)
}

fn validate_settings(settings: &AppSettings) -> Result<(), String> {
    if settings.output_dir.trim().is_empty() {
        return Err("Choose an output folder".into());
    }
    if !(0.0..=1.0).contains(&settings.sound_volume) {
        return Err("Sound volume must be between 0 and 1".into());
    }
    if !(1..=4).contains(&settings.concurrent_downloads) {
        return Err("Concurrent downloads must be between 1 and 4".into());
    }
    Ok(())
}

fn validate_request(request: &DownloadRequest) -> Result<(), String> {
    let detection = provider::detect(&request.url)?;
    if !detection.supported {
        return Err(detection.message);
    }
    if request.provider != detection.provider {
        return Err(
            "The detected provider changed; resolve the link again before downloading".into(),
        );
    }
    if request.output_dir.trim().is_empty() {
        return Err("Choose an output folder before downloading".into());
    }
    if !PathBuf::from(&request.output_dir).is_absolute() {
        return Err("The output folder must be an absolute path".into());
    }
    if !matches!(
        request.format.as_str(),
        "mp3" | "flac" | "opus" | "m4a" | "wav" | "alac" | "vorbis"
    ) {
        return Err("Unsupported audio format".into());
    }
    let expected_quality = match request.format.as_str() {
        "mp3" | "vorbis" => "320",
        "opus" | "m4a" => "256",
        "flac" | "wav" | "alac" => "0",
        _ => unreachable!("format was validated above"),
    };
    if request.quality != expected_quality {
        return Err("The selected quality does not match the audio format".into());
    }
    Ok(())
}

fn home_directory() -> String {
    std::env::var("HOME")
        .or_else(|_| std::env::var("USERPROFILE"))
        .unwrap_or_else(|_| ".".into())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_single_instance::init(|app, _, _| {
            if let Some(window) = app.get_webview_window("main") {
                let _ = window.show();
                let _ = window.set_focus();
            }
        }))
        .plugin(tauri_plugin_window_state::Builder::default().build())
        .plugin(tauri_plugin_log::Builder::default().build())
        .plugin(tauri_plugin_clipboard_manager::init())
        .plugin(tauri_plugin_notification::init())
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_opener::init())
        .setup(|app| {
            let app_data = app
                .path()
                .app_data_dir()
                .map_err(|error| error.to_string())?;
            let database = Database::new(app_data.join("nekuloader.db"))?;
            database.mark_running_jobs_interrupted()?;
            let legacy_imported = database.import_legacy(&home_directory())?;
            if let Some(image) = database
                .load_settings(AppSettings::defaults(&home_directory()))?
                .background_image_path
                .map(PathBuf::from)
                .filter(|image| image.is_file())
            {
                let _ = app.asset_protocol_scope().allow_file(&image);
            }
            app.manage(AppState {
                database,
                app_data,
                controls: Arc::new(Mutex::new(HashMap::new())),
                pending: Arc::new(Mutex::new(VecDeque::new())),
                share: Arc::new(ShareManager::default()),
                legacy_imported,
            });
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            bootstrap,
            detect_source,
            inspect_link,
            save_settings,
            refresh_tools,
            scan_library,
            install_components,
            queue_download,
            start_job,
            pause_job,
            cancel_job,
            save_playback_state,
            load_playback_state,
            resolve_playable_url,
            get_track_artwork,
            save_custom_theme,
            list_custom_themes,
            delete_custom_theme,
            list_android_devices,
            repair_artwork,
            push_to_android,
            start_wifi_share,
            stop_wifi_share,
            current_wifi_share,
        ])
        .run(tauri::generate_context!())
        .expect("error while running NekuLoader");
}

#[cfg(test)]
mod tests {
    use super::*;
    use models::{MediaKind, Provider};

    fn request() -> DownloadRequest {
        DownloadRequest {
            url: "https://music.youtube.com/watch?v=abc".into(),
            title: None,
            artist: None,
            provider: Provider::YouTubeMusic,
            media_kind: MediaKind::Track,
            format: "mp3".into(),
            quality: "320".into(),
            output_dir: std::env::current_dir()
                .unwrap()
                .join("music")
                .to_string_lossy()
                .into_owned(),
            track_numbers: true,
            embed_metadata: true,
            embed_artwork: true,
        }
    }

    #[test]
    fn accepts_a_valid_download_request() {
        assert!(validate_request(&request()).is_ok());
    }

    #[test]
    fn rejects_provider_and_quality_tampering() {
        let mut provider_changed = request();
        provider_changed.provider = Provider::Spotify;
        assert!(validate_request(&provider_changed).is_err());

        let mut quality_changed = request();
        quality_changed.quality = "999".into();
        assert!(validate_request(&quality_changed).is_err());
    }
}
