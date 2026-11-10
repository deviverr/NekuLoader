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

The owner selected a free proprietary alpha on 2026-11-10; see `LICENSE.txt`. Earlier no-license notes below describe historical status. Before public redistribution, review `THIRD_PARTY_NOTICES.md`, generate complete npm and Cargo license reports, audit the original icon and other assets, and preserve all obligations for runtime-downloaded helper binaries.


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

## Local verification — 2026-11-10

Selected project base: `/Users/dev/Projects/NekuLoader/recovered-src` (alpha.2).
The older KINGSTON worktree is preserved separately. Added first-run Components
selection, dialog keyboard focus containment/restoration, audio stop/unload when
clearing/removing the final track, pause/seek position checkpoints, and active-row
pause/resume. TypeScript and 23 component tests passed; production frontend build
passed. Native Rust checks passed 52 tests with 7 opt-in integration tests ignored.
See `docs/USER-TEST-REPORT.md` for the distinction between simulated journeys and
real installed-app/device verification.

On this host, release compilation initially failed loading proc-macro crates.
A fresh target directory with `CARGO_PROFILE_RELEASE_BUILD_OVERRIDE_DEBUG=true`
compiled successfully. Packaging is a separate verification step; do not infer
signing, notarization, platform smoke tests, or public release from compilation.

The static landing page is in `apps/site`. Its uploaded staging copy at
`/home/nekudev/nekuloader-release-20261110` on the VPS has matching HTML SHA-256.
Activated on 2026-11-10 using the authorized administrator session. Public HTTPS
and direct-origin HTTPS return 200, the no-slash path redirects to /nekuloader/,
and the public HTML hash matches the local source. Let’s Encrypt renewal is enabled.
No project license was selected and no stable release was declared.

The refreshed Apple silicon DMG was packaged successfully and passed `hdiutil verify`.
Its checksum and installer are saved in `releases/local-20261110/`.

## Public alpha.3 preparation — 2026-11-10

The owner explicitly selected free proprietary binaries with source/original
asset rights reserved. `LICENSE.txt` preserves third-party license rights. The
app embeds the dependency notice ZIP and MPL-covered source archives from
`apps/desktop/src-tauri/resources/third-party-notices.zip`, which is tracked so
fresh source checkouts can reproduce packaging. The standalone archive is also
distributed with the installer. The runtime helper executables are not bundled.

Alpha.3 passed 24 frontend tests and 52 Rust tests. Native installed-app workflow
and the additional album-row fix were verified; see `docs/USER-TEST-REPORT.md`.
The final Mac artifact was generated with the selected 1.95 Rust toolchain and
`CARGO_PROFILE_RELEASE_BUILD_OVERRIDE_DEBUG=true npm run tauri build -- --bundles dmg -- --locked`.
Public release scope is the locally tested macOS arm64 alpha plus an experimental Debian 12 x64 package with container/headless verification. Broader platform
coverage remains explicit in `docs/releases/v3.2.0-alpha.3.md`. GitHub Actions is
blocked by an account billing lock; failed jobs did not execute tests.

Linux validation subsequently passed 24 frontend tests, 52 Rust tests, optimized
DEB build and installation, linked-library resolution, two 25-second Xvfb process
smokes, and managed required-component installation including fresh FFmpeg and
FFprobe. Interactive desktop and physical-phone testing are still open. Local
and Linux compile-input digests matched exactly. The site and GitHub prerelease
now offer both artifacts; Windows and Intel Mac remain unavailable. The download
UI passed 26 availability/error-path tests and both files have published SHA-256.
