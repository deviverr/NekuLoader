export type Provider =
  | "youTube"
  | "youTubeMusic"
  | "spotify"
  | "soundCloud"
  | "deezer"
  | "appleMusic"
  | "unknown";

export type MediaKind = "track" | "album" | "playlist" | "unknown";
export type JobStatus =
  | "queued"
  | "preparing"
  | "downloading"
  | "converting"
  | "completed"
  | "paused"
  | "cancelled"
  | "failed"
  | "interrupted";

export interface SourceDetection {
  provider: Provider;
  mediaKind: MediaKind;
  host: string;
  supported: boolean;
  normalizedUrl: string;
  message: string;
}

export interface MediaPreview {
  url: string;
  title: string;
  artist: string;
  album: string;
  thumbnail?: string;
  durationSeconds?: number;
  trackCount?: number;
  provider: Provider;
  mediaKind: MediaKind;
  extractor?: string;
  warning?: string;
}

/** How Deezer and Apple Music links are re-searched against a downloadable source. */
export type ParseMode = "automatic" | "spotifyMetadata" | "youTubeMusicSearch";

/**
 * `mono` (the default), `cosmic`, and `legacy` are built in; user themes are
 * stored as `custom:<name>` and layer their colours over the monochrome base.
 */
export type ThemeMode = "mono" | "cosmic" | "legacy" | `custom:${string}` | `preset:${string}`;

export type LogTextColor =
  | "green"
  | "blue"
  | "cyan"
  | "yellow"
  | "orange"
  | "pink"
  | "red"
  | "white";

export interface AppSettings {
  outputDir: string;
  defaultFormat: string;
  defaultQuality: string;
  trackNumbers: boolean;
  embedMetadata: boolean;
  embedArtwork: boolean;
  soundEnabled: boolean;
  soundVolume: number;
  animateBackground: boolean;
  reducedMotion: boolean;
  language: string;
  /** Legacy 3.1 theme name, preserved on import; `themeMode` drives rendering. */
  theme: string;
  concurrentDownloads: number;
  checkToolUpdates: boolean;

  themeMode: ThemeMode;
  catMode: boolean;
  logTextColor: LogTextColor;
  backgroundImagePath: string | null;
  backgroundImageRecent: string[];
  snowSpeed: number;
  snowBoldness: number;
  snowTransparency: number;
  /** Degrees the starfield drifts toward; 180 is straight down. */
  starAngle: number;

  frameTransparency: number;

  /** Extra folders the player searches, added by the user. */
  musicFolders: string[];
  /** Also scan the platform's standard music location. */
  scanSystemMusic: boolean;
  /** Opt-in whole-home sweep. Slow, so never on by default. */
  scanEverything: boolean;

  /**
   * YouTube answers many anonymous requests with a bot check, and cookies are
   * the only supported way through. Opt-in: "none" | "browser" | "file".
   */
  cookieMode: "none" | "browser" | "file";
  cookieBrowser: string;
  cookieFile: string | null;

  customFolderIcons: boolean;
  parseMode: ParseMode;
  trayNotifications: boolean;
}

/** One named user theme, mirroring legacy 3.1's Customize Theme dialog. */
export interface CustomThemeColors {
  name: string;
  bgStart: string;
  bgEnd: string;
  titleColor: string;
  textColor: string;
  logBgColor: string;
  logTextColor: string;
  buttonColor: string;
  starColor: string;
  starAlpha: number;
  menuBarColor: string;
}

export type RepeatMode = "off" | "one" | "all";

export interface PlaybackState {
  /** Ordered LibraryTrack ids, which are absolute paths. */
  queueTrackIds: string[];
  currentIndex: number | null;
  positionSeconds: number;
  shuffle: boolean;
  repeatMode: RepeatMode;
  volume: number;
}

export interface AndroidDevice {
  id: string;
  model: string;
  /** Raw `adb devices` state: "device", "unauthorized", "offline", … */
  state: string;
}

export interface DownloadRequest {
  url: string;
  title?: string;
  artist?: string;
  provider: Provider;
  mediaKind: MediaKind;
  format: string;
  quality: string;
  outputDir: string;
  trackNumbers: boolean;
  embedMetadata: boolean;
  embedArtwork: boolean;
}

export interface DownloadJob {
  id: string;
  request: DownloadRequest;
  title: string;
  artist: string;
  status: JobStatus;
  progress: number;
  speed: string;
  eta: string;
  stage: string;
  error?: string;
  createdAt: string;
  updatedAt: string;
  /** Tail of raw downloader output, for the queue terminal panel on cold load. */
  recentLog?: string[];
  unavailableTracks?: number;
}

export interface ToolInfo {
  name: string;
  required: boolean;
  installed: boolean;
  path?: string;
  version?: string;
  status: string;
}

export interface HistoryEntry {
  id: number;
  legacyText?: string;
  job?: DownloadJob;
  createdAt: string;
}

/** A local audio file discovered by a library scan. */
export interface LibraryTrack {
  id: string;
  title: string;
  artist: string;
  album: string;
  path: string;
  format: string;
  sizeBytes: number;
  modifiedAt: string;
  /** From embedded tags; absent when the file carries no readable tag block. */
  durationSeconds?: number | null;
  trackNumber?: number | null;
  hasArtwork?: boolean;
}

export interface BootstrapResponse {
  settings: AppSettings;
  jobs: DownloadJob[];
  history: HistoryEntry[];
  tools: ToolInfo[];
  legacyImported: boolean;
  version: string;
  /** Lets Settings explain, rather than hide, the Windows-only folder icons. */
  isWindows: boolean;
  playback: PlaybackState;
  customThemes: CustomThemeColors[];
}
