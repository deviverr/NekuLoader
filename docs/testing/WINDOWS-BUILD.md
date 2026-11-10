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

## Completed alpha.3 build

The final build used `XWIN_CROSS_COMPILER=clang` and a dedicated
`XWIN_CACHE_DIR`, with `CARGO_BUILD_JOBS=2`. LLVM 14 needs the compiler wrapper
in `scripts/windows-cross/clang-wrapper.py` installed as `clang` earlier on PATH:
it moves MSVC fallback intrinsic headers after Clang's intrinsic headers and
removes link-search flags from compile-only invocations. No application or
cryptography source was changed. The extracted SDK also needed the case alias
`Windows.h -> windows.h` in its include directory on the Linux filesystem.

The Windows-targeted test binary was built with `cargo xwin test --release
--locked --target x86_64-pc-windows-msvc --manifest-path src-tauri/Cargo.toml
--no-run`, then executed in an isolated Wine 11.10 Staging win64 prefix under
Xvfb. Result: 49 passed, 0 failed, 7 ignored. Earlier Wine 8 was incompatible
with the Rust runtime; only the Wine 11.10 result is used.

NSIS packaging completed successfully. The shipped app is PE32+ x86-64; the
NSIS bootstrapper is PE32. The installer is unsigned and remains a testing
candidate pending native Windows installation, WebView2 and UI feedback.
Installer SHA-256:
`0e4ecfe44c7eb3ecd4f701364b111447a7a591d64eee2b4b5f68b7f15f28c3b7`.
