# NekuLoader: 30-day launch plan

Prepared 2026-11-10 for the selected Mac checkout, 3.2.0-alpha.3. Dates below are a proposed sequence after launch preparation begins, not scheduled jobs or completed results.

## Product and decision

NekuLoader is a native desktop home for authorized audio: persistent downloading, local playback, a searchable library, themes, six languages and phone transfer. The identity is already distinctive: the original Neku cat, Pixelify Sans, pixel controls, starfield and cosmic colors. The website must look like the actual app. Use real screenshots and owned recordings in demonstrations.

The owner chose a **free proprietary alpha**: downloads are allowed under the project license, source code rights are reserved. Do not call it open source or assume permission to redistribute or modify code. Follow the repository LICENSE, third-party notices and exact helper-binary obligations.

Provider truth: YouTube/YouTube Music/SoundCloud use yt-dlp. Spotify uses SpotDL with public-catalog fallback for tracks/albums; full playlist fallback is unavailable. Deezer tracks/albums/playlists and Apple songs/albums use duration-checked catalog matching. Apple playlists are unavailable through the public API. Matched audio can differ from the catalog stream; FLAC conversion cannot restore missing detail.

## Days 1–3: a trustworthy first download

- Complete checks against the exact candidate commit; record TypeScript, production build and Rust test results separately from human or installer testing.
- Publish only an artifact that has a real file, SHA-256, release notes and explicit platform/signing status. macOS Apple silicon is the only launch artifact currently validated. GitHub cross-platform CI is blocked by account billing; Windows and Linux remain unavailable until real builds and clean-machine checks pass.
- On a clean Mac, install, explicitly install components, download one owned recording, play it, restart and confirm queue/history persistence. Test a failed item and retry. A development-machine launch is not a clean-machine pass.
- Preserve the owner's unsigned Windows policy. Mac signing/notarization remains opt-in after the selected Apple team and encrypted credentials are configured. Do not label an unsigned build signed or notarized.
- Review dependency notices and original icon rights. Current GPL helper binaries require exact-build license/source-obligation review; the project's proprietary license does not replace their terms.

**Exit evidence:** candidate version + checksum; recorded environment; successful core journey; truthful known-issues list. Do not invent a test pass if a machine is unavailable.

## Days 4–7: pilot with actual users

Recruit 8–12 volunteers, including nontechnical music listeners and local-library collectors. Start on the verified platform, then expand to Windows/Linux candidates only when artifacts are tested. Let users install, set up components, download an authorized item and find/play it without coaching. Then try phone transfer on a real Android device and an iPhone browser. Record task completion, time and exact points of confusion.

Agents should independently run the same tasks to identify confusion and bugs. Label those sessions simulated user reviews; they are not replacements for human feedback or real devices.

**Proposed threshold:** at least 80% complete the core journey without coaching, no unresolved data loss, and no blocker preventing first-run setup. If fewer than five testers participate, report the small sample rather than treating percentages as validated demand.

## Days 8–14: a quiet public alpha

- Deploy the authentic pixel/cosmic page at nekudev.com/nekuloader on the user's VPS. Keep the existing homepage and other routes intact. Until a reachable server address, SSH username and existing server configuration are known, deployment is pending.
- Serve downloads/manifest.json with an explicit available flag, version, local installer URL and SHA-256. The site shows a Mac button only when the manifest and installer availability checks succeed. Never publish a placeholder installer or checksum.
- Publish a 30–45-second walkthrough using owned audio: link → format → queue → playback → phone. Show real alpha behavior and any setup step users encounter.
- Add concise bug-report prompts: OS, architecture, app version, expected behavior and reproducible steps. Ask users to remove cookies, tokens and personal paths from logs.
- Post once to an existing personal audience and one relevant desktop/music-organization community whose rules allow promotion. Ask for a specific task test; do not bulk-message strangers or buy ads.

**Evidence to collect:** working HTTPS page/download, five useful feedback reports, and the three most common setup or reliability problems.

## Days 15–21: fix what users actually hit

Resolve the three most frequent pilot issues before adding providers. Publish a small patch and changelog that names the behavior changed and how it was checked. Ask original reporters to retry the precise failing task. Test at least one Windows and Linux candidate on clean systems before adding their direct buttons. Translate stable onboarding/help text and verify major tasks in each advertised language.

Publish two useful guides with real screenshots: what Spotify matching means, and how to move local music to a phone. Avoid misleading search phrases such as guaranteed Spotify FLAC or unrestricted premium downloads.

**Proposed threshold:** five people return voluntarily within a week, and previously blocked reporters can finish their task. These are targets, not forecasts.

## Days 22–30: decide whether to expand or monetize

Interview ten returning users if the audience is large enough. Ask which workflow saves time, what remains annoying, and what they already pay for. Summarize answers verbatim with consent, then group repeated needs. Do not treat download counts or GitHub stars as proof of retention.

Keep the promised free alpha free. Test optional support only after choosing a payment route for which the owner is eligible. GitHub Sponsors supports one-time/monthly tiers but requires recipient residence in a supported region; Ukraine was absent from its published list when checked on 2026-11-10. Timezone alone does not establish eligibility. Do not display an unconfigured payment button.

Potential paid future workflows: metadata cleanup, duplicate review, saved organization recipes and convenient device-sync profiles. These are concepts, not shipped features. Test willingness to pay before building them. A one-time purchase or optional support is a better first hypothesis than a subscription for this local desktop scope. Never sell supposed access to provider content or guaranteed extraction.

**Day-30 decision:** expand distribution if core-task completion and return use hold up; spend the next iteration on reliability if they do not. Start a paid-feature experiment only when repeated user requests justify it. Do not manufacture urgency or change the free alpha terms without an explicit owner decision.

## Measurement and sources

Use voluntary interviews and issue reports initially. Record aggregate install/task success and support burden without hidden analytics or collecting source links/cookies. Track human and agent sessions separately.

Primary sources checked for distribution guidance:

- Apple Developer ID signing and notarization: https://developer.apple.com/developer-id/
- GitHub Releases and tagged downloadable artifacts: https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases
- GitHub Sponsors eligibility and supported recipient regions: https://docs.github.com/en/sponsors/getting-started-with-github-sponsors/about-github-sponsors
- FFmpeg license differences by build configuration: https://ffmpeg.org/legal.html

These sources support specific distribution/payment/license facts, not the proposed audience, prices, conversion rates or commercial success.
