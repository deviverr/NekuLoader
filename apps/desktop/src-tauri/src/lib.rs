mod downloader;
mod library;
mod models;
mod provider;
mod storage;
mod tooling;

use std::{collections::HashMap, path::PathBuf, sync::Arc};

use chrono::Utc;
use models::{
    AppSettings, BootstrapResponse, DownloadJob, DownloadRequest, JobStatus, LibraryTrack,
    MediaPreview, SourceDetection, ToolInfo,
};
use storage::Database;
use tauri::{Manager, State};
use tokio::sync::Mutex;
use tokio_util::sync::CancellationToken;
use uuid::Uuid;

#[derive(Clone)]
struct JobControl {
    token: CancellationToken,
    paused: Arc<Mutex<bool>>,
}

struct AppState {
    database: Database,
    app_data: PathBuf,
    controls: Arc<Mutex<HashMap<String, JobControl>>>,
    legacy_imported: bool,
}

#[tauri::command]
async fn bootstrap(state: State<'_, AppState>) -> Result<BootstrapResponse, String> {
    let home = home_directory();
    let settings = state.database.load_settings(AppSettings::defaults(&home))?;
    Ok(BootstrapResponse {
        settings,
        jobs: state.database.list_jobs()?,
        history: state.database.list_history()?,
        tools: tooling::inspect(&state.app_data).await,
        legacy_imported: state.legacy_imported,
        version: env!("CARGO_PKG_VERSION").into(),
    })
}

#[tauri::command]
fn detect_source(url: String) -> Result<SourceDetection, String> {
    provider::detect(&url)
}

#[tauri::command]
async fn inspect_link(url: String, state: State<'_, AppState>) -> Result<MediaPreview, String> {
    downloader::inspect_link(&url, &state.app_data).await
}

#[tauri::command]
fn save_settings(settings: AppSettings, state: State<'_, AppState>) -> Result<AppSettings, String> {
    validate_settings(&settings)?;
    state.database.save_settings(&settings)?;
    Ok(settings)
}

#[tauri::command]
async fn refresh_tools(state: State<'_, AppState>) -> Result<Vec<ToolInfo>, String> {
    Ok(tooling::inspect(&state.app_data).await)
}

#[tauri::command]
async fn scan_library(state: State<'_, AppState>) -> Result<Vec<LibraryTrack>, String> {
    let settings = state
        .database
        .load_settings(AppSettings::defaults(&home_directory()))?;
    let root = PathBuf::from(settings.output_dir);
    tokio::task::spawn_blocking(move || library::scan(&root))
        .await
        .map_err(|error| error.to_string())?
}

#[tauri::command]
async fn install_components(state: State<'_, AppState>) -> Result<Vec<ToolInfo>, String> {
    if !state.controls.lock().await.is_empty() {
        return Err("Pause active downloads before updating components".into());
    }
    tooling::install_or_update(&state.app_data).await
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
    };
    state.database.save_job(&job)?;
    Ok(job)
}

#[tauri::command(rename_all = "camelCase")]
async fn start_job(
    job_id: String,
    app: tauri::AppHandle,
    state: State<'_, AppState>,
) -> Result<(), String> {
    let mut controls = state.controls.lock().await;
    if controls.contains_key(&job_id) {
        return Ok(());
    }
    let limit = state
        .database
        .load_settings(AppSettings::defaults(&home_directory()))?
        .concurrent_downloads as usize;
    if controls.len() >= limit {
        return Err(format!(
            "The concurrent download limit is {limit}; wait for an active job or raise it in Settings"
        ));
    }
    let mut job = state
        .database
        .get_job(&job_id)?
        .ok_or_else(|| "Download job not found".to_string())?;
    if job.status == JobStatus::Completed {
        return Err("This job is already complete".into());
    }
    job.status = JobStatus::Queued;
    job.stage = "Starting".into();
    job.error = None;
    job.updated_at = Utc::now();
    state.database.save_job(&job)?;

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
    tauri::async_runtime::spawn(async move {
        downloader::run_job(job, database, app, app_data, token, paused).await;
        control_map.lock().await.remove(&job_id);
    });
    Ok(())
}

#[tauri::command(rename_all = "camelCase")]
async fn pause_job(job_id: String, state: State<'_, AppState>) -> Result<(), String> {
    let control = state.controls.lock().await.get(&job_id).cloned();
    if let Some(control) = control {
        *control.paused.lock().await = true;
        control.token.cancel();
    }
    Ok(())
}

#[tauri::command(rename_all = "camelCase")]
async fn cancel_job(job_id: String, state: State<'_, AppState>) -> Result<(), String> {
    let control = state.controls.lock().await.get(&job_id).cloned();
    if let Some(control) = control {
        *control.paused.lock().await = false;
        control.token.cancel();
    } else if let Some(mut job) = state.database.get_job(&job_id)?
        && job.status != JobStatus::Completed
    {
        job.status = JobStatus::Cancelled;
        job.stage = "Cancelled".into();
        job.updated_at = Utc::now();
        state.database.save_job(&job)?;
    }
    Ok(())
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
            app.manage(AppState {
                database,
                app_data,
                controls: Arc::new(Mutex::new(HashMap::new())),
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
