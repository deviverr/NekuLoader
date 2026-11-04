# NekuLoader 3.2 desktop

This directory contains the native NekuLoader 3.2 alpha application:

- Tauri 2 application shell
- Rust backend for provider detection, the persistent queue, component management, and library scanning
- React and TypeScript interface
- SQLite application state in the platform app-data directory

## Commands

```bash
npm ci
npm run check     # TypeScript plus Vitest
npm run build     # TypeScript plus production Vite bundle
npm run tauri dev
npm run tauri build
```

The browser-only `npm run dev` mode is useful for interface work. It intentionally does not install components, scan the real music folder, perform native downloads, or reveal files.

## Downloader components

The installed app exposes component management in Settings. Installation is always initiated by the user; startup checks do not download anything.

The managed action downloads:

- yt-dlp from the current official GitHub release
- Deno from the current official GitHub release
- FFmpeg/FFprobe from `eugeneware/ffmpeg-static` on macOS or `yt-dlp/FFmpeg-Builds` on Windows and Linux
- recommended Deno (failure does not block the required direct-provider components)
- optional SpotDL on macOS arm64, Linux x86_64, and Windows x86_64; system-installed SpotDL is detected on other targets

Every managed release asset must have published SHA-256 metadata. Downloads are size-limited, staged, verified, health-checked, and only then promoted into the app tool directory. A SpotDL failure is reported but does not block the required components. Component updates are blocked while downloads are active. First run opens this Settings section when required components are missing, but installation remains an explicit user action.

## Application updates and signing

Application updates are manual for this alpha. Settings links to GitHub Releases, and the repository does not contain an updater public key or request updater artifacts. The release workflow does not consume Tauri updater, Apple Developer ID, notarization, or Windows Authenticode credentials.

This does not affect managed downloader-component updates: their assets are still downloaded explicitly, checked against upstream SHA-256 metadata, health-checked, and promoted transactionally. See `../../docs/RELEASING.md` before changing the application-signing policy.

## Provider and quality limits

Direct download is implemented for accessible YouTube, YouTube Music, and SoundCloud URLs through yt-dlp. Spotify downloads use SpotDL to match Spotify metadata to another accessible audio source, so the matched source and quality can differ from Spotify's catalog stream; ALAC output is not supported by this path. Deezer tracks, albums, and playlists and Apple Music songs and albums use public catalog metadata followed by artist/title search with a duration tolerance of 15 seconds when a catalog duration is available. Apple Music playlists are not exposed by the public catalog API. Spotify tracks and albums can fall back to public embed metadata when SpotDL is absent or fails; playlist fallback is refused because the complete list cannot be verified. Partial collections expose a retry action and preserve already downloaded files.

Audio format selection controls output encoding. Converting a lossy source to FLAC or WAV does not restore lost detail.
