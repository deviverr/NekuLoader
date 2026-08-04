use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub enum Provider {
    YouTube,
    YouTubeMusic,
    Spotify,
    SoundCloud,
    Deezer,
    AppleMusic,
    Unknown,
}

impl Provider {
    pub fn label(&self) -> &'static str {
        match self {
            Self::YouTube => "YouTube",
            Self::YouTubeMusic => "YouTube Music",
            Self::Spotify => "Spotify",
            Self::SoundCloud => "SoundCloud",
            Self::Deezer => "Deezer",
            Self::AppleMusic => "Apple Music",
            Self::Unknown => "Unknown",
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub enum MediaKind {
    Track,
    Album,
    Playlist,
    Unknown,
}

impl MediaKind {
    pub fn label(&self) -> &'static str {
        match self {
            Self::Track => "Track",
            Self::Album => "Album",
            Self::Playlist => "Playlist",
            Self::Unknown => "Media",
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SourceDetection {
    pub provider: Provider,
    pub media_kind: MediaKind,
    pub host: String,
    pub supported: bool,
    pub normalized_url: String,
    pub message: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MediaPreview {
    pub url: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub thumbnail: Option<String>,
    pub duration_seconds: Option<u64>,
    pub track_count: Option<usize>,
    pub provider: Provider,
    pub media_kind: MediaKind,
    pub extractor: Option<String>,
    pub warning: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DownloadRequest {
    pub url: String,
    pub title: Option<String>,
    pub artist: Option<String>,
    pub provider: Provider,
    pub media_kind: MediaKind,
    pub format: String,
    pub quality: String,
    pub output_dir: String,
    pub track_numbers: bool,
    pub embed_metadata: bool,
    pub embed_artwork: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub enum JobStatus {
    Queued,
    Preparing,
    Downloading,
    Converting,
    Completed,
    Paused,
    Cancelled,
    Failed,
    Interrupted,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DownloadJob {
    pub id: String,
    pub request: DownloadRequest,
    pub title: String,
    pub artist: String,
    pub status: JobStatus,
    pub progress: f64,
    pub speed: String,
    pub eta: String,
    pub stage: String,
    pub error: Option<String>,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AppSettings {
    pub output_dir: String,
    pub default_format: String,
    pub default_quality: String,
    pub track_numbers: bool,
    pub embed_metadata: bool,
    pub embed_artwork: bool,
    pub sound_enabled: bool,
    pub sound_volume: f64,
    pub animate_background: bool,
    pub reduced_motion: bool,
    pub language: String,
    pub theme: String,
    pub concurrent_downloads: u8,
    pub check_tool_updates: bool,
}

impl AppSettings {
    pub fn defaults(home: &str) -> Self {
        let output_dir = std::path::Path::new(home)
            .join("Music")
            .join("Downloads")
            .to_string_lossy()
            .into_owned();
        Self {
            output_dir,
            default_format: "mp3".into(),
            default_quality: "320".into(),
            track_numbers: true,
            embed_metadata: true,
            embed_artwork: true,
            sound_enabled: true,
            sound_volume: 0.55,
            animate_background: true,
            reduced_motion: false,
            language: "English".into(),
            theme: "Classic Legacy".into(),
            concurrent_downloads: 1,
            check_tool_updates: true,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ToolInfo {
    pub name: String,
    pub required: bool,
    pub installed: bool,
    pub path: Option<String>,
    pub version: Option<String>,
    pub status: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct HistoryEntry {
    pub id: i64,
    pub legacy_text: Option<String>,
    pub job: Option<DownloadJob>,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LibraryTrack {
    pub id: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub path: String,
    pub format: String,
    pub size_bytes: u64,
    pub modified_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BootstrapResponse {
    pub settings: AppSettings,
    pub jobs: Vec<DownloadJob>,
    pub history: Vec<HistoryEntry>,
    pub tools: Vec<ToolInfo>,
    pub legacy_imported: bool,
    pub version: String,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct JobEvent {
    pub job: DownloadJob,
}
