# NekuLoader simulated user review — 2026-11-10

Scope: the user-selected Mac checkout at `/Users/dev/Projects/NekuLoader/recovered-src`, `apps/desktop` version 3.2.0-alpha.2. Initial investigation and fixes also covered the older KINGSTON checkout; the user then selected the newer Mac copy as the project base. Player changes were ported selectively to preserve its existing improvements. Existing work in progress was preserved.

## Method and evidence

An agent walked through first-time downloader, keyboard-only user, and regular music-listener scenarios by inspecting the interface and native command code. Automated component tests simulate clicks, keyboard focus, native API replies, and audio events in jsdom. These are **simulated user scenarios**, not human usability research, real browser/audio playback, installed-app testing, or OS/device smoke tests.

Final frontend verification ran directly in the Mac checkout with its existing dependencies: `npm run check` passed TypeScript and **23 tests across 3 files**; `npm run build` passed TypeScript and produced the Vite production bundle. Four new playback regression cases cover clearing/removing the last track, seek/pause checkpointing, and active-row pause/resume. No media was downloaded and no phone was modified by these tests.

Historical drive verification: an exact-lock temporary QA copy passed TypeScript, 16 tests and the frontend production build. AppleDouble `._*` metadata files were excluded from that temporary QA copy because they are not source code. These results describe the older checkout and are not the final project validation.

## Scenarios

| Persona and action | Finding and resulting behavior | Verification |
| --- | --- | --- |
| New user starts without downloader tools | Settings originally opened General, hiding the install action. Missing tools now lead directly to Components. | App component tests with simulated tool replies |
| Keyboard user opens Settings or Queue | Focus originally escaped behind dialogs. Dialog now receives and contains focus, then returns focus when closed. | Overlay component test |
| Listener clears queue or removes its last playing track | Audio continued after the interface cleared. The player now pauses and unloads the audio source and resets the position. | Two playback regression cases with simulated audio element |
| Listener seeks, pauses, then expects the position to be saved | Position was saved only after structural changes. Explicit seeks and pause events now checkpoint the current position. The Mac base's existing restored-position behavior after metadata loads is preserved. | Playback regression case for seek and pause save calls |
| Listener clicks the pause icon on the active library row | Row previously replaced the playback queue. The active row now pauses/resumes at its existing position; its accessible label matches the action. | Playback regression case checking pause/resume without resetting time |
| Downloader edits a link while its previous preview is visible | Submission could use old metadata during a new resolve. Preview invalidation and submission gating now track resolution. | Existing App/interface coverage plus source review; no live provider request |

## Remaining release gaps

The older drive checkout lacked the newer native queue scheduler and device-transfer implementation. The selected Mac base contains those implementations. Their presence and passing interface tests do not establish actual native download/device functionality; installed-app and real-device checks remain necessary.

No clean-machine Windows, macOS Intel, Linux installer test, signed/notarized installation test, actual iPhone/Android transfer, audible codec playback, or live provider audio download was performed in this review. Project licensing and release-signing decisions remain owner decisions. Native Rust test results are recorded separately by the coordinating agent.

Unsaved General settings can still be lost when Settings is dismissed. Browser/native error handling and real playback across codec/OS combinations need installed-app testing. Passing these checks does not establish that NekuLoader is finished or ready for a stable public launch.

## Installed-app checks completed on 2026-11-10

Mounted the generated DMG read-only, copied its app into an isolated QA folder,
and launched the native packaged app. This uses the existing Mac profile; it is
not a clean-machine installation or a notarized/quarantine test.

The native interface resolved and downloaded Blender Foundation's official
Big Buck Bunny film (https://www.youtube.com/watch?v=aqz-KE-bpKQ), which is
licensed CC BY 3.0 (https://peach.blender.org/about/). Attribution: (c) 2008
Blender Foundation / www.bigbuckbunny.org. The test is a private extracted-audio
copy, not a redistributed website asset. Output stays in
`/Users/dev/Projects/NekuLoader/release-qa/media`.

The download completed at 100%; FFprobe confirmed MP3 audio, embedded artwork,
and 634.578146 seconds. Native Library found the file automatically. Play, seek,
pause, app quit/reopen and restored paused position were observed through the
native interface. Clearing the test playback queue reset the player.

This run caught a second album-row issue: the active row restarted instead of
pausing and displayed reversed play/pause glyphs. Both album/queue active rows
now toggle existing playback. A fifth playback regression brings frontend
coverage to **24 passing tests**. The Mac Rust suite passed **52 tests** with
seven opt-in tests ignored; separately executed integration checks are listed in
`releases/local-20261110/VALIDATION.md`.

A real alpha.3 Cosmic screenshot is used by the website. The original Mono
appearance was restored after capture. The test destination remains the isolated
QA media folder. No existing music or user queue/history records were deleted.
