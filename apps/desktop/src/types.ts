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
  theme: string;
  concurrentDownloads: number;
  checkToolUpdates: boolean;
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

export interface BootstrapResponse {
  settings: AppSettings;
  jobs: DownloadJob[];
  history: HistoryEntry[];
  tools: ToolInfo[];
  legacyImported: boolean;
  version: string;
}
