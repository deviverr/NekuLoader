import type { AppSettings, BootstrapResponse, LibraryTrack } from "../types";

/**
 * Bootstrap fixtures. Tests override only the fields they care about, so adding
 * a settings field does not mean editing every test.
 */
export function settingsFixture(overrides: Partial<AppSettings> = {}): AppSettings {
  return {
    outputDir: "/Music",
    defaultFormat: "mp3",
    defaultQuality: "320",
    trackNumbers: true,
    embedMetadata: true,
    embedArtwork: true,
    soundEnabled: false,
    soundVolume: 0.5,
    animateBackground: false,
    // Tests run with motion held so frame timers never fire mid-assertion.
    reducedMotion: true,
    language: "English",
    theme: "Classic Legacy",
    concurrentDownloads: 1,
    checkToolUpdates: false,
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
    ...overrides,
  };
}

export function bootstrapFixture(
  overrides: Partial<BootstrapResponse> = {},
): BootstrapResponse {
  return {
    settings: settingsFixture(overrides.settings),
    jobs: [],
    history: [],
    tools: [],
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
    ...overrides,
  };
}

export function trackFixture(overrides: Partial<LibraryTrack> = {}): LibraryTrack {
  return {
    id: "/Music/Asterism/Mycelium Eyes.flac",
    title: "Mycelium Eyes",
    artist: "Sub Urban",
    album: "Asterism",
    path: "/Music/Asterism/Mycelium Eyes.flac",
    format: "FLAC",
    sizeBytes: 42_000_000,
    modifiedAt: "2026-08-04T00:00:00Z",
    durationSeconds: 214,
    trackNumber: 1,
    hasArtwork: true,
    ...overrides,
  };
}
