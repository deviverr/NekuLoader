# Releasing NekuLoader 3.2

NekuLoader 3.2 is currently an alpha. Releases should remain draft prereleases until every target artifact has been smoke-tested. Windows installers are currently unsigned. macOS signing/notarization is opt-in after the release owner has selected the Apple Developer team and configured fresh GitHub Actions secrets.

## What the current workflow does

`.github/workflows/release-3.2.yml` runs only for `v3.2.0-alpha.*` tags. Its platform matrix requests:

| Runner | Requested bundles |
| --- | --- |
| macOS 14 (Apple silicon) | `.app` and `.dmg` |
| macOS 15 Intel | `.app` and `.dmg` |
| Ubuntu 22.04 | AppImage, `.deb`, and `.rpm` |
| Windows 2022 | NSIS and `.msi` |

The Tauri action creates a draft prerelease. It does not request updater metadata or consume repository signing secrets. `createUpdaterArtifacts` is disabled, the updater plugin is not included, and application updates are manual links to GitHub Releases.

The workflow uses Apple Developer ID signing/notarization only when the `APPLE_SIGNING_ENABLED` repository variable is `true`. It does not use Windows Authenticode. Do not add signing keys, certificates, or passwords from a chat transcript. Any future signing policy requires the release owner's explicit approval and a fresh credential delivered through a dedicated secrets channel.

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
- Apple signing is enabled only after the selected team and all Apple secrets are configured; no updater or Windows signing secret is consumed;
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
- Spotify download uses metadata matching (SpotDL, with public-catalog fallback for tracks/albums); the matched audio source and quality can differ from Spotify's catalog stream, and ALAC output is not supported by this path.
- Deezer tracks/albums/playlists and Apple Music songs/albums use public catalog matching. Apple playlists are unavailable through the public API.
- Spotify tracks/albums have a public embed metadata fallback when SpotDL is absent or fails. Full Spotify playlist fallback remains unavailable because the public embed may truncate its track list.
- Output format conversion does not improve source quality.
- SpotDL is optional to the required direct-provider toolchain. The component action attempts a managed install only where a supported SpotDL build is published and detects manual installations elsewhere.
- The local Library scan reads embedded tags, durations, and artwork, supports configured folders and external drives, and refreshes after download completion.
- The Java 3.1 edition remains the stable historical reference while 3.2 reaches parity.

Suggested opening:

> NekuLoader 3.2 is a native Rust/Tauri rewrite with automatic link recognition, a persistent queue, explicit component management, and a themed six-language interface with local playback and device transfer. This is a testing prerelease and is not yet the stable replacement for NekuLoader 3.1.

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


## Local completion verification — 2026-11-04

Implemented collection retry and partial-success reporting, stale-preview prevention,
collection artist fallback, strict catalog duration matching, Spotify track/album
fallback, six-language settings/history/transfer/queue controls, native notification
support, optional runtime handling, and Windows folder-icon generation. Existing
Windows folder customizations are preserved. Component promotion holds the queue
scheduling lock to prevent a new job from starting midway through an update.

Verified on macOS arm64: TypeScript/Vitest; Rust unit and offline fake-downloader/
fake-ADB integration tests; live Spotify track/album metadata only; npm audit;
and local release build. Live metadata tests do not download audio. The fake ADB
test does not require or modify a phone. Native notifications require OS permission.

Public release remains gated by the existing project-license and signing decisions,
clean-machine Windows/macOS Intel/Linux smoke testing, and real-device transfer
verification. CI configuration is not evidence of a completed remote CI run.


## GitHub release setup (2026-11-04)

The release owner requested GitHub distribution for macOS, Windows, and Linux,
and explicitly selected unsigned Windows installers. The project license is
unchanged. Do not add or select a project license on the owner's behalf.

`desktop-ci.yml` builds candidate installers on release branches and manual runs,
then keeps them as workflow artifacts for 14 days. macOS Apple silicon/Intel,
Windows x64 (NSIS/MSI), and Linux x64 (AppImage/DEB/RPM) are covered. The tagged
release workflow uploads to a draft prerelease. A draft is not a public launch.

For signed macOS CI builds, configure these repository secrets through GitHub's
encrypted secret storage, then set repository variable `APPLE_SIGNING_ENABLED`
to `true`:

- `APPLE_CERTIFICATE`: base64-encoded Developer ID Application certificate and private key (.p12).
- `APPLE_CERTIFICATE_PASSWORD`: certificate export password.
- `APPLE_SIGNING_IDENTITY`: the authorized Developer ID Application identity.
- `APPLE_ID`, `APPLE_PASSWORD`, `APPLE_TEAM_ID`: notarization account, app-specific password, and team.

Never populate these from passwords or private keys pasted into chat. Signing
preflight fails if any required secret is missing, and signed builds must pass
`codesign --verify` and `stapler validate`. Signing remains disabled until the
owner confirms the intended Apple team. Do not reuse an unrelated identity merely
because it is present in the local Keychain. Xcode login alone does not configure
these GitHub-hosted runner secrets.
