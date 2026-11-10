# NekuLoader

NekuLoader is a cross-platform desktop music and playlist downloader. The repository now contains two generations:

- **3.2 alpha** — the new Tauri 2, Rust, React, and TypeScript application in `apps/desktop`.
- **3.1 legacy** — the recovered Java application under `src`, preserved for reference and migration.

NekuLoader is for media you own or are authorized to download. Recognizing a provider link does not grant permission to copy its content or bypass its access controls.

## NekuLoader 3.2 alpha

The 3.2 rewrite is a native app rather than a Java JAR. It currently includes automatic link recognition, a persistent download queue, local history, a searchable music-folder scan, managed downloader components, the original NekuLoader icon, and a themed interface with six languages, a local player, Android USB and tokenized Wi‑Fi transfer.

### Current provider behavior

| Provider | 3.2 alpha behavior |
| --- | --- |
| YouTube / YouTube Music | Direct extraction through yt-dlp when the media is accessible |
| SoundCloud | Direct extraction through yt-dlp when the media is accessible |
| Spotify | SpotDL metadata matching, with public-catalog fallback for tracks and albums; matched audio and quality can differ from Spotify |
| Deezer | Public catalog metadata → duration-checked audio matching (tracks, albums, and playlists) |
| Apple Music | Public catalog metadata → duration-checked audio matching (songs and albums; Apple playlists unavailable through the public API) |

Choosing FLAC, WAV, or another output format does not improve the source. A lossless output can avoid an additional lossy encode, but it cannot recover detail that was not present in the downloaded audio.

### Managed components

Settings → Components & updates provides an explicit **Install required components** or **Update required components** action. NekuLoader does not silently download the toolchain at startup.

The action installs or updates yt-dlp, recommended Deno, FFmpeg, and FFprobe in the app-data directory. It also attempts to install optional SpotDL where upstream publishes a managed build for the current platform; manual system installations are detected on other platforms. Release assets are SHA-256 verified against their published GitHub metadata, downloaded to staging, and health-checked before promotion. An optional SpotDL failure does not block the required toolchain.

On first run, missing required components open Settings with an actionable notice. Installation still requires a click; nothing is downloaded silently.

Application updates are manual for this alpha. Settings links to GitHub Releases; no updater or platform-signing credential is embedded in the app or release workflow. Component installation and updates remain independently integrity-checked and available from Settings.

### Develop and test 3.2

Requirements:

- Node.js 24
- Rust toolchain from `apps/desktop/rust-toolchain.toml`
- Tauri 2 platform prerequisites for the current operating system

```bash
cd apps/desktop
npm ci
npm run check
npm run build
npm run tauri dev
```

`npm run dev` runs a browser-only interface preview. Filesystem scans, component installation, and native downloads are intentionally unavailable there.

See [`apps/desktop/README.md`](apps/desktop/README.md) for implementation details and [`docs/RELEASING.md`](docs/RELEASING.md) for the release checklist and current signing limitations.

## NekuLoader 3.1 legacy

The Java edition supports its historical provider workflows, multiple output formats, a sequential queue, metadata and artwork, a local library/player, and device-transfer helpers. Its source was recovered from the packaged application and remains available while 3.2 reaches parity.

Requirements: JDK 17 or newer, curl, yt-dlp, SpotDL, and FFmpeg/FFprobe. Android transfer additionally requires ADB.

```bash
./build.sh
./test.sh
./run.sh
```

Native Java packages require a full JDK 21 containing `jpackage`:

```bash
./package.sh dmg       # macOS
./package.sh exe       # Windows under Git Bash
./package.sh deb       # Debian/Ubuntu
```

The 3.1 package discovers tools from the host `PATH`, common installation folders, or supported app-adjacent folders. It does not bundle those tools through the maintained source build.

## Release status

NekuLoader 3.2 is a prerelease and is not yet the stable replacement for 3.1. The release workflow builds draft platform artifacts without project signing credentials. The public alpha currently offers a locally tested, unsigned and unnotarized macOS Apple silicon installer at https://nekudev.com/nekuloader/. An experimental Debian 12 x64 package is also available after isolated build, installation and headless checks. Windows and Intel Mac downloads are unavailable while GitHub Actions is blocked by an account billing issue; alpha updates are downloaded manually from GitHub Releases.

NekuLoader is a free proprietary alpha: official binaries may be downloaded and used free of charge; source code and original asset rights remain reserved under `LICENSE.txt`. See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) before redistributing source, assets, helper tools, or installers.
