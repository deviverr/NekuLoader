import { invoke } from "@tauri-apps/api/core";
import { listen, type UnlistenFn } from "@tauri-apps/api/event";
import { readText, writeText } from "@tauri-apps/plugin-clipboard-manager";
import { open } from "@tauri-apps/plugin-dialog";
import { revealItemInDir } from "@tauri-apps/plugin-opener";
import { convertFileSrc } from "@tauri-apps/api/core";
import type {
  AndroidDevice,
  AppSettings,
  BootstrapResponse,
  CustomThemeColors,
  DownloadJob,
  DownloadRequest,
  LibraryTrack,
  MediaKind,
  MediaPreview,
  PlaybackState,
  Provider,
  SourceDetection,
  ToolInfo,
} from "../types";

export type { LibraryTrack } from "../types";

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
    themeMode: "mono",
    catMode: false,
    logTextColor: "green",
    backgroundImagePath: null,
    backgroundImageRecent: [],
    snowSpeed: 1,
    snowBoldness: 1,
    snowTransparency: 0.55,
    starAngle: 180,

    frameTransparency: 1,
    musicFolders: [],
    scanSystemMusic: false,
    scanEverything: false,
    cookieMode: "none",
    cookieBrowser: "chrome",
    cookieFile: null,
    customFolderIcons: false,
    parseMode: "automatic",
    trayNotifications: true,
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
  isWindows: false,
  playback: {
    queueTrackIds: [],
    currentIndex: null,
    positionSeconds: 0,
    shuffle: false,
    repeatMode: "off",
    volume: 0.8,
  },
  customThemes: [],
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
      : "This source is not supported.",
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
  if (isTauri()) return invoke("scan_library");
  return import.meta.env.DEV ? demoLibrary() : [];
}

/** A few fake tracks so the browser-only UI preview has something to show. */
function demoLibrary(): LibraryTrack[] {
  const now = new Date().toISOString();
  const album = (artist: string, title: string, names: string[], format: string) =>
    names.map((name, index) => ({
      id: `/demo/${title}/${name}.${format.toLowerCase()}`,
      path: `/demo/${title}/${name}.${format.toLowerCase()}`,
      title: name,
      artist,
      album: title,
      format,
      sizeBytes: 9_000_000,
      modifiedAt: now,
      durationSeconds: 180 + index * 17,
      trackNumber: index + 1,
      hasArtwork: false,
    }));
  return [
    ...album("Forss", "Soulhack", ["City Ports", "Soulhack", "Funk for Nerds", "Flickermood"], "MP3"),
    ...album("Demo Artist", "Pixel Nights", ["Starfall", "Neku Runs", "Cassette"], "FLAC"),
  ];
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

/** Raw downloader output for the queue terminal panel. */
export async function onJobLog(
  callback: (jobId: string, lines: string[]) => void,
): Promise<UnlistenFn> {
  if (!isTauri()) return () => undefined;
  return listen<{ jobId: string; lines: string[] }>("job:log", ({ payload }) =>
    callback(payload.jobId, payload.lines),
  );
}

// --- player ---------------------------------------------------------------

export async function savePlaybackState(playback: PlaybackState): Promise<void> {
  if (isTauri()) await invoke("save_playback_state", { playback });
}

export async function loadPlaybackState(): Promise<PlaybackState> {
  return isTauri() ? invoke("load_playback_state") : browserDefaults.playback;
}

/**
 * Turns a library path into something `<audio src>` can load. The Rust side
 * authorizes the path first; `convertFileSrc` then maps it onto the asset
 * protocol. Returns null in the browser preview, which has no file access.
 */
export async function playableUrl(path: string): Promise<string | null> {
  if (!isTauri()) return null;
  const resolved = await invoke<string>("resolve_playable_url", { path });
  return convertFileSrc(resolved);
}

export async function trackArtworkUrl(path: string): Promise<string | null> {
  if (!isTauri()) return null;
  const cached = await invoke<string | null>("get_track_artwork", { path });
  return cached ? convertFileSrc(cached) : null;
}

// --- themes ---------------------------------------------------------------

export async function saveCustomTheme(theme: CustomThemeColors): Promise<void> {
  if (isTauri()) await invoke("save_custom_theme", { theme });
}

export async function listCustomThemes(): Promise<CustomThemeColors[]> {
  return isTauri() ? invoke("list_custom_themes") : [];
}

export async function deleteCustomTheme(name: string): Promise<void> {
  if (isTauri()) await invoke("delete_custom_theme", { name });
}

// --- devices --------------------------------------------------------------

export async function listAndroidDevices(): Promise<AndroidDevice[]> {
  return isTauri() ? invoke("list_android_devices") : [];
}

export async function pushToAndroid(
  deviceId: string,
  files: string[],
  destination: string,
): Promise<void> {
  if (!isTauri()) throw new Error("Device transfer needs the installed desktop app.");
  await invoke("push_to_android", { deviceId, files, destination });
}

export async function onTransferProgress(
  callback: (event: { deviceId: string; file: string; completed: number; total: number; error?: string }) => void,
): Promise<UnlistenFn> {
  if (!isTauri()) return () => undefined;
  return listen<{ deviceId: string; file: string; completed: number; total: number; error?: string }>(
    "device:transfer",
    ({ payload }) => callback(payload),
  );
}

// --- artwork repair ----------------------------------------------------

/** Re-fetches square covers for tracks from the source page in their tags. */
export async function repairArtwork(files: string[]): Promise<number> {
  if (!isTauri()) throw new Error("Cover repair needs the installed desktop app.");
  return invoke("repair_artwork", { files });
}

export async function onArtworkRepair(
  callback: (event: { completed: number; total: number; fixed: number; file: string }) => void,
): Promise<UnlistenFn> {
  if (!isTauri()) return () => undefined;
  return listen<{ completed: number; total: number; fixed: number; file: string }>("artwork:repair", ({ payload }) =>
    callback(payload),
  );
}

// --- Wi-Fi transfer -------------------------------------------------------

export interface WifiShare {
  url: string;
  qrSvg: string;
  fileCount: number;
  totalBytes: number;
  expiresAt: string;
}

export async function startWifiShare(files: string[]): Promise<WifiShare> {
  if (!isTauri()) throw new Error("Wi-Fi transfer needs the installed desktop app.");
  return invoke("start_wifi_share", { files });
}

export async function stopWifiShare(): Promise<void> {
  if (isTauri()) await invoke("stop_wifi_share");
}

export async function currentWifiShare(): Promise<WifiShare | null> {
  return isTauri() ? invoke("current_wifi_share") : null;
}

/**
 * Reads the clipboard natively. The webview's own clipboard API makes macOS
 * show a "Paste" confirmation bubble on every press.
 */
export async function readClipboard(): Promise<string> {
  if (isTauri()) return (await readText()) ?? "";
  return navigator.clipboard.readText();
}

/** Picks an exported cookies.txt for the downloader. */
export async function chooseCookieFile(): Promise<string | null> {
  if (!isTauri()) return null;
  const result = await open({
    multiple: false,
    title: "Choose cookies.txt",
    filters: [{ name: "Cookies", extensions: ["txt"] }],
  });
  return typeof result === "string" ? result : null;
}

/** Picks a background image or GIF for the theme editor. */
export async function chooseImage(): Promise<string | null> {
  if (!isTauri()) return null;
  const result = await open({
    multiple: false,
    title: "Choose a background image",
    filters: [{ name: "Images", extensions: ["png", "jpg", "jpeg", "gif", "webp"] }],
  });
  return typeof result === "string" ? result : null;
}

/** Maps a user-chosen background image path onto the asset protocol. */
export function backgroundUrl(path: string | null): string | undefined {
  if (!path || !isTauri()) return undefined;
  return convertFileSrc(path);
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
      description: "Matches Spotify metadata to accessible audio using SpotDL, with public-catalog fallback for tracks and albums. The matched source and quality can differ from Spotify's catalog stream.",
    };
  }
  if (["deezer", "appleMusic"].includes(provider)) {
    return {
      canDownload: true,
      label: "Metadata matching",
      description: "Reads the track list from the public catalog, then matches each track by artist, title, and length to an accessible audio source and writes the catalog tags.",
    };
  }
  return {
    canDownload: false,
    label: "Unsupported",
    description: "This source is not supported.",
  };
}

export async function copyText(text: string): Promise<void> {
  if (isTauri()) return writeText(text);
  return navigator.clipboard.writeText(text);
}


/** Request OS permission only from the user's notification-setting action. */
export async function requestNotifications(): Promise<boolean> {
  if (!isTauri()) return false;
  const { isPermissionGranted, requestPermission } = await import("@tauri-apps/plugin-notification");
  return (await isPermissionGranted()) || (await requestPermission()) === "granted";
}

export async function notifyDownload(body: string): Promise<void> {
  if (!isTauri()) return;
  const { isPermissionGranted, sendNotification } = await import("@tauri-apps/plugin-notification");
  if (await isPermissionGranted()) sendNotification({ title: "NekuLoader", body });
}
