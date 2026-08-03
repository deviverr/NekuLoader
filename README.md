# NekuLoader 3.1

NekuLoader is a Java desktop download manager and local music library/player for macOS and Windows. This source tree was recovered from the creator's packaged JAR and is being restored into a maintainable release.

Use NekuLoader only for media you own or are authorized to download. Provider support does not grant rights to copy protected material.

## Features

- YouTube, YouTube Music, and SoundCloud extraction through yt-dlp
- Spotify metadata matching through SpotDL providers
- Deezer and Apple Music album/track metadata parsing followed by audio matching
- MP3, FLAC, Opus, M4A/AAC, WAV, ALAC, and OGG Vorbis output where supported
- Sequential URL queue, duplicate archive, metadata, artwork, and persistent history
- Local recursive music library with artwork, tags, play/pause, seek, volume, and previous/next
- Android ADB transfer with album folders and media-library refresh
- Finder/Apple Devices/File Sharing export for iPhone, iPad, and iPod
- Legacy and Modern appearances

Apple Music playlist parsing is gated because it requires authenticated Apple Music API access. Spotify matching currently excludes ALAC; use FLAC or M4A instead.

## Build And Run

Requirements: JDK 17 or newer, curl, yt-dlp, SpotDL, and FFmpeg/FFprobe. Android transfer additionally requires ADB.

```bash
./build.sh
./run.sh
```

On macOS, install the command-line dependencies with:

```bash
./setup-mac-tools.sh
```

The Java build downloads FlatLaf `3.7.2`, org.json `20260522`, and JLayer `1.0.1` into `lib/`, then creates `build/NekuLoader-3.1.0.jar`.

## Tests

```bash
./test.sh
RUN_AUDIO_PLAYBACK_TEST=1 ./test.sh
```

The suite covers provider capability gates, persistent history, FFprobe metadata, FFmpeg playback, and Android transfer through a fake ADB integration fixture.

## Native Packages

Native packages must be built on their target operating system with a full JDK 21 containing `jpackage`:

```bash
./package.sh dmg       # macOS
./package.sh exe       # Windows under Git Bash
./package.sh deb       # Debian/Ubuntu
```

The package includes a Java runtime and the NekuLoader application. Version 3.1 discovers yt-dlp, SpotDL, FFmpeg, Node/Deno, and ADB from the host `PATH`, common macOS installation folders, or supported app-adjacent folders. This also works when macOS launches the app with Finder's restricted `PATH`. Advanced users can override discovery with `NEKULOADER_YT_DLP`, `NEKULOADER_PYTHON`, `NEKULOADER_SPOTDL`, `NEKULOADER_FFMPEG`, `NEKULOADER_FFPROBE`, and `NEKULOADER_ADB`. The Packager does not claim those tools are bundled.

The current macOS artifact is ad-hoc signed. Public distribution still requires an Apple Developer ID certificate and notarization. Windows release testing and code signing must be performed on Windows.

## Project Status

The original activation/license gate has been removed. This recovered code is suitable for continued development, but a public source release still needs a chosen project license, a Windows test matrix, signed release artifacts, and confirmation that all redistributed third-party binaries and assets have compatible notices.

See `THIRD_PARTY_NOTICES.md` for dependency information.
