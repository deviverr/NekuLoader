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
    /// Tail of the raw downloader stdout/stderr, snapshotted from the in-memory
    /// ring buffer so the queue terminal panel has content on a cold load.
    #[serde(default)]
    pub recent_log: Vec<String>,
    /// Missing entries in a partially completed collection; old jobs default to zero.
    #[serde(default)]
    pub unavailable_tracks: usize,
}

/// How Deezer and Apple Music links are re-searched against a downloadable
/// source, mirroring legacy 3.1's "Parse:" combo.
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub enum ParseMode {
    /// Try Spotify first, fall back to YouTube Music (legacy "dual").
    Automatic,
    SpotifyMetadata,
    YouTubeMusicSearch,
}

impl Default for ParseMode {
    fn default() -> Self {
        Self::Automatic
    }
}

fn default_theme_mode() -> String {
    "mono".into()
}

/// Degrees the starfield drifts toward; 180 is straight down.
fn default_star_angle() -> f64 {
    180.0
}

fn default_cookie_browser() -> String {
    "chrome".into()
}

fn default_log_text_color() -> String {
    "green".into()
}

fn default_snow_speed() -> f64 {
    1.0
}

fn default_snow_boldness() -> f64 {
    1.0
}

fn default_snow_transparency() -> f64 {
    0.55
}

fn default_frame_transparency() -> f64 {
    1.0
}

fn default_tray_notifications() -> bool {
    true
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
    /// Legacy 3.1 theme name, retained so an imported profile keeps its value.
    /// `theme_mode` is what the 3.2 interface actually renders from.
    pub theme: String,
    pub concurrent_downloads: u8,
    pub check_tool_updates: bool,

    // --- 3.2 rework: appearance -------------------------------------------
    /// "cosmic" | "legacy" | "custom:<name>"
    #[serde(default = "default_theme_mode")]
    pub theme_mode: String,
    #[serde(default)]
    pub cat_mode: bool,
    #[serde(default = "default_log_text_color")]
    pub log_text_color: String,
    #[serde(default)]
    pub background_image_path: Option<String>,
    #[serde(default)]
    pub background_image_recent: Vec<String>,
    #[serde(default = "default_snow_speed")]
    pub snow_speed: f64,
    #[serde(default = "default_snow_boldness")]
    pub snow_boldness: f64,
    #[serde(default = "default_snow_transparency")]
    pub snow_transparency: f64,
    #[serde(default = "default_star_angle")]
    pub star_angle: f64,
    #[serde(default = "default_frame_transparency")]
    pub frame_transparency: f64,

    // --- 3.2 rework: where the player looks for music ----------------------
    /// Extra folders the user added by hand.
    #[serde(default)]
    pub music_folders: Vec<String>,
    /// Also scan the platform's standard music location (~/Music and friends).
    #[serde(default)]
    pub scan_system_music: bool,
    /// Opt-in whole-home sweep. Slow and noisy, so it is never the default.
    #[serde(default)]
    pub scan_everything: bool,

    // --- 3.2 rework: authentication ----------------------------------------
    /// "none" | "browser" | "file". YouTube increasingly refuses anonymous
    /// requests with a bot check, and cookies are the only supported answer.
    #[serde(default)]
    pub cookie_mode: String,
    /// Browser yt-dlp reads cookies from when `cookie_mode` is "browser".
    #[serde(default = "default_cookie_browser")]
    pub cookie_browser: String,
    /// Netscape-format cookies.txt when `cookie_mode` is "file".
    #[serde(default)]
    pub cookie_file: Option<String>,

    // --- 3.2 rework: behavior ---------------------------------------------
    #[serde(default)]
    pub custom_folder_icons: bool,
    #[serde(default)]
    pub parse_mode: ParseMode,
    #[serde(default = "default_tray_notifications")]
    pub tray_notifications: bool,
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
            theme_mode: default_theme_mode(),
            cat_mode: false,
            log_text_color: default_log_text_color(),
            background_image_path: None,
            background_image_recent: Vec::new(),
            snow_speed: default_snow_speed(),
            snow_boldness: default_snow_boldness(),
            snow_transparency: default_snow_transparency(),
            star_angle: default_star_angle(),
            frame_transparency: default_frame_transparency(),
            music_folders: Vec::new(),
            scan_system_music: false,
            scan_everything: false,
            cookie_mode: "none".into(),
            cookie_browser: default_cookie_browser(),
            cookie_file: None,
            custom_folder_icons: false,
            parse_mode: ParseMode::default(),
            tray_notifications: default_tray_notifications(),
        }
    }
}

/// One named user theme, mirroring legacy 3.1's `CustomThemeDialog` color set.
/// Colors are `#rrggbb` strings; the frontend maps them onto CSS custom
/// properties, so no color parsing happens in Rust.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CustomThemeColors {
    pub name: String,
    pub bg_start: String,
    pub bg_end: String,
    pub title_color: String,
    pub text_color: String,
    pub log_bg_color: String,
    pub log_text_color: String,
    pub button_color: String,
    pub star_color: String,
    #[serde(default = "default_star_alpha")]
    pub star_alpha: f64,
    pub menu_bar_color: String,
}

fn default_star_alpha() -> f64 {
    0.47
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub enum RepeatMode {
    Off,
    One,
    All,
}

impl Default for RepeatMode {
    fn default() -> Self {
        Self::Off
    }
}

/// Persisted player state so the app resumes its queue and position on restart.
/// Audio output itself lives in the webview (`<audio>` + `convertFileSrc`);
/// Rust only stores this.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PlaybackState {
    /// Ordered `LibraryTrack.id` values (which are absolute paths).
    #[serde(default)]
    pub queue_track_ids: Vec<String>,
    #[serde(default)]
    pub current_index: Option<usize>,
    #[serde(default)]
    pub position_seconds: f64,
    #[serde(default)]
    pub shuffle: bool,
    #[serde(default)]
    pub repeat_mode: RepeatMode,
    #[serde(default = "default_volume")]
    pub volume: f64,
}

fn default_volume() -> f64 {
    0.8
}

impl Default for PlaybackState {
    fn default() -> Self {
        Self {
            queue_track_ids: Vec::new(),
            current_index: None,
            position_seconds: 0.0,
            shuffle: false,
            repeat_mode: RepeatMode::Off,
            volume: default_volume(),
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
    /// From embedded tags when readable; `None` when the file has no usable
    /// tag block and the title/album fell back to path heuristics.
    #[serde(default)]
    pub duration_seconds: Option<u64>,
    #[serde(default)]
    pub track_number: Option<u32>,
    #[serde(default)]
    pub has_artwork: bool,
}

/// An ADB-visible Android device, mirroring legacy 3.1's `DeviceTransferManager`.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AndroidDevice {
    pub id: String,
    pub model: String,
    /// Raw `adb devices` state: "device", "unauthorized", "offline", …
    pub state: String,
}

/// Progress for a multi-file push to a device.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct TransferEvent {
    pub device_id: String,
    pub file: String,
    pub completed: usize,
    pub total: usize,
    pub error: Option<String>,
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
    /// Lets the interface explain, rather than silently hide, the Windows-only
    /// album folder icon setting on other platforms.
    #[serde(default)]
    pub is_windows: bool,
    #[serde(default)]
    pub playback: PlaybackState,
    #[serde(default)]
    pub custom_themes: Vec<CustomThemeColors>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct JobEvent {
    pub job: DownloadJob,
}

/// A batch of raw downloader output lines for the queue terminal panel.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct JobLogEvent {
    pub job_id: String,
    pub lines: Vec<String>,
}
