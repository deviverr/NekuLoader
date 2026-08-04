import { invoke } from "@tauri-apps/api/core";
import { listen, type UnlistenFn } from "@tauri-apps/api/event";
import { open } from "@tauri-apps/plugin-dialog";
import { revealItemInDir } from "@tauri-apps/plugin-opener";
import type {
  AppSettings,
  BootstrapResponse,
  DownloadJob,
  DownloadRequest,
  MediaKind,
  MediaPreview,
  Provider,
  SourceDetection,
  ToolInfo,
} from "../types";

export interface LibraryTrack {
  id: string;
  title: string;
  artist: string;
  album: string;
  path: string;
  format: string;
  sizeBytes: number;
  modifiedAt: string;
}

export interface ProviderCapability {
  canDownload: boolean;
  label: "Direct download" | "Metadata matching" | "Recognition only" | "Unsupported";
  description: string;
}

declare global {
  interface Window {
    __TAURI_INTERNALS__?: unknown;
  }
}

export const isTauri = () => Boolean(window.__TAURI_INTERNALS__);
export const releasesUrl = "https://github.com/deviverr/NekuLoader/releases";

const browserDefaults: BootstrapResponse = {
  settings: {
    outputDir: "~/Music/Downloads",
    defaultFormat: "mp3",
    defaultQuality: "320",
    trackNumbers: true,
    embedMetadata: true,
    embedArtwork: true,
    soundEnabled: true,
    soundVolume: 0.55,
    animateBackground: true,
    reducedMotion: false,
    language: "English",
    theme: "Classic Legacy",
    concurrentDownloads: 1,
    checkToolUpdates: true,
  },
  jobs: [],
  history: [],
  tools: [
    { name: "yt-dlp", required: true, installed: false, status: "Desktop backend required" },
    { name: "FFmpeg", required: true, installed: false, status: "Desktop backend required" },
    { name: "JavaScript runtime", required: true, installed: false, status: "Desktop backend required" },
  ],
  legacyImported: false,
  version: "3.2.0-alpha.1",
};

export async function bootstrap(): Promise<BootstrapResponse> {
  return isTauri() ? invoke("bootstrap") : browserDefaults;
}

export async function detectSource(url: string): Promise<SourceDetection> {
  if (isTauri()) return invoke("detect_source", { url });
  return detectInBrowser(url);
}

export async function inspectLink(url: string): Promise<MediaPreview> {
  if (isTauri()) return invoke("inspect_link", { url });
  const detection = detectInBrowser(url);
  if (!detection.supported) throw new Error(detection.message);
  return {
    url: detection.normalizedUrl,
    title: `${providerLabel(detection.provider)} ${kindLabel(detection.mediaKind)}`,
    artist: "Desktop preview mode",
    album: "",
    provider: detection.provider,
    mediaKind: detection.mediaKind,
    warning: providerCapability(detection.provider).canDownload
      ? "Run the installed desktop app to resolve live metadata."
      : "This alpha recognizes the link, but its metadata-matching download workflow is not implemented yet.",
  };
}

export async function queueDownload(request: DownloadRequest): Promise<DownloadJob> {
  if (isTauri()) return invoke("queue_download", { request });
  const now = new Date().toISOString();
  return {
    id: crypto.randomUUID(),
    request,
    title: request.title || "Preview download",
    artist: request.artist || "",
    status: "queued",
    progress: 0,
    speed: "",
    eta: "",
    stage: "Waiting",
    createdAt: now,
    updatedAt: now,
  };
}

export const startJob = (jobId: string) => (isTauri() ? invoke<void>("start_job", { jobId }) : Promise.resolve());
export const pauseJob = (jobId: string) => (isTauri() ? invoke<void>("pause_job", { jobId }) : Promise.resolve());
export const cancelJob = (jobId: string) => (isTauri() ? invoke<void>("cancel_job", { jobId }) : Promise.resolve());

export async function saveSettings(settings: AppSettings): Promise<AppSettings> {
  return isTauri() ? invoke("save_settings", { settings }) : settings;
}

export async function refreshTools(): Promise<ToolInfo[]> {
  return isTauri() ? invoke("refresh_tools") : browserDefaults.tools;
}

export async function scanLibrary(): Promise<LibraryTrack[]> {
  return isTauri() ? invoke("scan_library") : [];
}

export async function installComponents(): Promise<ToolInfo[]> {
  if (!isTauri()) {
    throw new Error("Component installation is available in the installed desktop app.");
  }
  return invoke("install_components");
}

export async function chooseFolder(): Promise<string | null> {
  if (!isTauri()) return null;
  const result = await open({ directory: true, multiple: false, title: "Choose download folder" });
  return typeof result === "string" ? result : null;
}

export async function reveal(path: string): Promise<void> {
  if (isTauri()) await revealItemInDir(path);
}

export async function onJobUpdate(callback: (job: DownloadJob) => void): Promise<UnlistenFn> {
  if (!isTauri()) return () => undefined;
  return listen<{ job: DownloadJob }>("job:update", ({ payload }) => callback(payload.job));
}

function detectInBrowser(raw: string): SourceDetection {
  const parsed = new URL(raw.trim());
  const host = parsed.hostname.toLowerCase();
  let provider: Provider = "unknown";
  if (host === "music.youtube.com") provider = "youTubeMusic";
  else if (host === "youtu.be" || host === "youtube.com" || host.endsWith(".youtube.com")) provider = "youTube";
  else if (host === "spotify.link" || host === "spotify.com" || host.endsWith(".spotify.com")) provider = "spotify";
  else if (host === "on.soundcloud.com" || host === "soundcloud.com" || host.endsWith(".soundcloud.com")) provider = "soundCloud";
  else if (host === "deezer.page.link" || host === "deezer.com" || host.endsWith(".deezer.com")) provider = "deezer";
  else if (host === "music.apple.com" || host === "itunes.apple.com") provider = "appleMusic";
  let mediaKind: MediaKind = "unknown";
  if (/\/(track|watch|shorts)\b/.test(parsed.pathname) || host === "youtu.be" || parsed.searchParams.has("i")) mediaKind = "track";
  else if (/\/album\b|\/browse\//.test(parsed.pathname)) mediaKind = "album";
  else if (/\/playlist\b|\/sets\//.test(parsed.pathname)) mediaKind = "playlist";
  const supported = provider !== "unknown";
  return {
    provider,
    mediaKind,
    host,
    supported,
    normalizedUrl: parsed.toString(),
    message: supported ? `${providerLabel(provider)} ${kindLabel(mediaKind)} detected` : "This source is not supported yet",
  };
}

export function providerLabel(provider: Provider): string {
  return ({
    youTube: "YouTube",
    youTubeMusic: "YouTube Music",
    spotify: "Spotify",
    soundCloud: "SoundCloud",
    deezer: "Deezer",
    appleMusic: "Apple Music",
    unknown: "Unknown",
  })[provider];
}

export function kindLabel(kind: MediaKind): string {
  return kind === "unknown" ? "media" : kind;
}

export function providerCapability(provider: Provider): ProviderCapability {
  if (["youTube", "youTubeMusic", "soundCloud"].includes(provider)) {
    return {
      canDownload: true,
      label: "Direct download",
      description: "Downloads accessible audio through yt-dlp, then converts it with FFmpeg.",
    };
  }
  if (provider === "spotify") {
    return {
      canDownload: true,
      label: "Metadata matching",
      description: "Uses SpotDL to match Spotify metadata to an accessible audio source. The matched source and quality can differ from Spotify's catalog stream.",
    };
  }
  if (["deezer", "appleMusic"].includes(provider)) {
    return {
      canDownload: false,
      label: "Recognition only",
      description: "The link is recognized, but metadata matching and audio download are not implemented in this alpha.",
    };
  }
  return {
    canDownload: false,
    label: "Unsupported",
    description: "This source is not supported.",
  };
}
