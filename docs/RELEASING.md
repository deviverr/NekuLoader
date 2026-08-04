# Releasing NekuLoader 3.2

NekuLoader 3.2 is currently an alpha. Releases should remain draft prereleases until every target artifact has been smoke-tested. The current alpha policy intentionally uses no project signing credentials.

## What the current workflow does

`.github/workflows/release-3.2.yml` runs only for `v3.2.0-alpha.*` tags. Its platform matrix requests:

| Runner | Requested bundles |
| --- | --- |
| macOS 14 (Apple silicon) | `.app` and `.dmg` |
| macOS 15 Intel | `.app` and `.dmg` |
| Ubuntu 22.04 | AppImage, `.deb`, and `.rpm` |
| Windows 2022 | NSIS and `.msi` |

The Tauri action creates a draft prerelease. It does not request updater metadata or consume repository signing secrets. `createUpdaterArtifacts` is disabled, the updater plugin is not included, and application updates are manual links to GitHub Releases.

The workflow also does not use Apple Developer ID signing/notarization or Windows Authenticode. Do not add signing keys, certificates, or passwords from a chat transcript. Any future signing policy requires the release owner's explicit approval and a fresh credential delivered through a dedicated secrets channel.

## Preflight

From `apps/desktop`:

```bash
npm ci
npm run check
npm run build
cargo test --locked --manifest-path src-tauri/Cargo.toml
```

Also verify:

- the version matches in `package.json`, `src-tauri/Cargo.toml`, and `src-tauri/tauri.conf.json`;
- `tauri.conf.json` has updater artifacts disabled and contains no updater public key;
- the release workflow has no updater, Apple, or Windows signing-secret references;
- the original NekuLoader icon appears correctly in each target package;
- Settings can install/update required components on each operating system;
- component installation is blocked while a download is active;
- missing required components open Settings on first run without starting an automatic download;
- missing or mismatched SHA-256 asset metadata stops installation;
- SpotDL installs on macOS arm64, Linux x86_64, and Windows x86_64, while unsupported targets clearly describe manual discovery;
- an optional SpotDL failure does not prevent verified required components from becoming available;
- Settings links to GitHub Releases for manual application updates;
- a clean machine can download an accessible YouTube, YouTube Music, and SoundCloud test item;
- a Spotify test link starts the SpotDL metadata-matching path on a supported test target, and ALAC is rejected before queueing;
- playlist naming, metadata, artwork, resume, cancel, and persistent queue behavior are exercised;
- the Library scan finds supported local audio, skips symlinks, searches correctly, and reveals a file;
- the browser preview never attempts native installation or filesystem mutation.

Only use test media that the release team owns or is authorized to download.

## Honest release notes

Every 3.2 alpha release should state these limits unless implementation has changed and tests prove otherwise:

- Direct download is implemented for accessible YouTube, YouTube Music, and SoundCloud links through yt-dlp.
- Spotify download uses SpotDL metadata matching; the matched audio source and quality can differ from Spotify's catalog stream, and ALAC output is not supported by this path.
- Deezer and Apple Music are recognition-only; metadata matching and audio download are not implemented yet.
- Output format conversion does not improve source quality.
- SpotDL is optional to the required direct-provider toolchain. The component action attempts a managed install only where a supported SpotDL build is published and detects manual installations elsewhere.
- The local Library scan reads filenames, parent folders, size, format, and modified time; embedded tag/artwork indexing is not yet claimed.
- The Java 3.1 edition remains the stable historical reference while 3.2 reaches parity.

Suggested opening:

> NekuLoader 3.2 is a native Rust/Tauri rewrite with automatic link recognition, a persistent queue, explicit component management, and the refined legacy interface. This is a testing prerelease and is not yet the stable replacement for NekuLoader 3.1.

## Artifact review

For each draft artifact:

1. Install on a clean machine or VM.
2. Confirm the application name and native window title are exactly `NekuLoader`.
3. Confirm no Java runtime is required by 3.2.
4. Record operating-system security warnings accurately.
5. Run component installation, one authorized direct-provider download, and a Spotify metadata-matching test where SpotDL is supported.
6. Quit/reopen and verify settings, queue, and history persistence.
7. Check uninstall behavior and retained user data.
8. Verify that no updater metadata or project-signing credential was added to the release.

Do not call an artifact signed, notarized, or production-ready unless the corresponding target-specific verification was completed.

## Licensing checkpoint

The repository does not currently declare a NekuLoader project license. Before public redistribution, review `THIRD_PARTY_NOTICES.md`, generate complete npm and Cargo license reports, audit the original icon and other assets, and preserve all obligations for runtime-downloaded helper binaries.
