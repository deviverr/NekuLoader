# Independent Windows build

No GitHub Actions is used. A dedicated resource-limited Docker build on the VPS
uses Rust 1.95.0, Node 24, LLVM/clang, NSIS and cargo-xwin 0.23.1 to target
`x86_64-pc-windows-msvc`. The cargo-xwin release binary is SHA-256 checked against
its upstream GitHub release metadata before installation. The owner authorized
this SDK route after the Microsoft license requirement was presented.

Source is archived from the release branch; no production service or database is
mounted into the build. Build/test containers have separate names and no public
ports. The Windows installer stays unsigned under the owner’s existing policy.
The owner chose to distribute an EXE testing candidate to friends for native
Windows feedback; Wine checks, if available, are reported separately.

Build command: `npm run tauri build -- --runner cargo-xwin --target x86_64-pc-windows-msvc --bundles nsis -- --locked`.

Tauri cross-build guide: https://v2.tauri.app/distribute/windows-installer/
cargo-xwin: https://github.com/rust-cross/cargo-xwin
Friend-test checklist: `WINDOWS-FRIEND-TEST.md`.

A compiler run alone does not establish native Windows compatibility. Publish
actual build/test results and checksums with the resulting candidate.
