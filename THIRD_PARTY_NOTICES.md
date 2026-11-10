# Third-Party Notices

NekuLoader is distributed as a free proprietary alpha under `LICENSE.txt`. These notices describe third-party components; their licenses govern those components independently.

## NekuLoader 3.1 legacy Java edition

The recovered Java source depends on these libraries at build/runtime:

- FlatLaf 3.7.2, Apache License 2.0
- org.json 20260522, JSON License
- JLayer 1.0.1, LGPL

The Java application also shells out to user-installed tools for download, conversion, and device workflows, including yt-dlp, SpotDL, FFmpeg, ADB, and libimobiledevice utilities. If you bundle any of those tools in a release, include the exact licenses and notices for the specific binaries and build configurations you distribute.

Do not publish the original packaged Windows runtime folders (`jre/`, `python/`, `ffmpeg/`) or installer/runtime binaries without auditing their redistribution terms.

## NekuLoader 3.2 native edition

The 3.2 desktop source in `apps/desktop` uses Tauri 2 and its plugins, React 19, React DOM 19, Lucide React, TypeScript, Vite, Vitest, and their transitive npm and Cargo dependencies. The exact resolved versions are recorded in:

- `apps/desktop/package-lock.json`
- `apps/desktop/src-tauri/Cargo.lock`

Tauri and its official JavaScript packages are dual-licensed under MIT or Apache-2.0 terms. React and React DOM are MIT-licensed. Lucide is ISC-licensed. Build and release processes should generate a complete version-specific license report for all transitive npm and Cargo dependencies rather than treating this short list as exhaustive.

## Components downloaded at runtime by 3.2

The managed component action downloads tools into the user's application-data directory; they are not committed to this source tree:

- [yt-dlp](https://github.com/yt-dlp/yt-dlp) — downloaded from the official yt-dlp GitHub release channel. Although the source is Unlicense, its PyInstaller executables include GPLv3+ code and the combined executables are GPLv3+; upstream provides a version-specific `THIRD_PARTY_LICENSES.txt`.
- [Deno](https://github.com/denoland/deno) — downloaded from the official Deno GitHub release channel under the MIT license, with additional notices for its bundled dependencies.
- FFmpeg and FFprobe — macOS uses the GPL-3.0 [`eugeneware/ffmpeg-static`](https://github.com/eugeneware/ffmpeg-static) assets; Windows and Linux use the `*-gpl` assets from [`yt-dlp/FFmpeg-Builds`](https://github.com/yt-dlp/FFmpeg-Builds). See [FFmpeg's legal guidance](https://ffmpeg.org/legal.html). Redistributing these binaries requires the corresponding GPL license, notices, and source-code obligations for the exact build.
- [SpotDL](https://github.com/spotDL/spotify-downloader) — the upstream project is MIT-licensed and its executable contains bundled dependencies with their own terms. It is downloaded as an optional managed executable on macOS arm64, Linux x86_64, and Windows x86_64; compatible system installations are discovered on other targets.

Runtime downloads are integrity-checked against SHA-256 data published in GitHub release metadata and health-checked before promotion. These checks do not establish redistribution rights. A distributor must preserve the upstream notices, source-offer obligations, and other terms applicable to the exact helper binaries it ships or causes the application to download.

Component checksum verification is an integrity mechanism; it does not replace third-party license compliance, Apple notarization, Windows code signing, or a project license. The current 3.2 alpha release workflow intentionally uses no project signing credentials.
