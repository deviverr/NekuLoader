# NekuLoader launch and revenue plan

Updated 2026-11-10. Working plan, not a claim of traction or promised revenue.

## Decision

Launch the current free proprietary alpha to a small audience now. Lead with
Mac Apple silicon, where the shipped app is signed, Apple-notarized and tested.
Windows x64 is an unsigned testing candidate with 49 Rust tests passed under
Wine; native installation/UI feedback is pending. Debian 12 x64 has container
and headless checks; normal desktop testing is pending. Physical-phone testing
and clean-machine coverage remain open. The product is ready for a pilot, not a
claim that every platform or feature is finished.

The live page is https://nekudev.com/nekuloader/ . Keep its minimal black design.
The root homepage stays image-only. Keep technical detail in release notes.

## Audience and promise

First audience hypothesis: people who already keep a local music/audio library
and want downloading, artwork, organization and playback in one desktop app.
Recruit Mac users first, then Windows friends as explicitly identified testers.

Working description: **A pixel-styled desktop app for saving authorized audio
and playing your local library.** The recognizable black/pixel interface is the
visual hook; the complete save → library → playback journey is the practical
reason to try it. Test this positioning against actual user replies.

Do not market catalog matching as downloading the original Spotify/Deezer/Apple
stream. Do not promise lossless source quality, bypassing restrictions, working
phone transfer on every device, or general Windows/Linux reliability. The
current alpha is proprietary, not open source.

## Revenue model to test

1. **Now: optional support**, with suggested one-time amounts of $5, $15 and $25.
   These are proposed amounts, not active products. Support funds development;
   it buys no unshipped feature, guaranteed response time or future entitlement.
   Current alpha downloads stay free. Put one small Support link near Feedback
   once a real owner-controlled checkout exists. Do not add an account system,
   fake checkout, artificial download limit or intrusive payment prompt.
2. **After repeat use: test a $19 one-time v1 license hypothesis**, explicitly
   limited to the released major version if implemented. Ask users about a
   concrete workflow before coding a paywall. Candidate future paid workflows:
   metadata repair, duplicate review, saved organization recipes or device-sync
   profiles. None of these is being sold or promised now. Existing alpha
   functionality is not being removed by this plan.
3. **Subscriptions later only if recurring value is demonstrated.** Local-only
   convenience alone is not evidence that people want another subscription.
   Avoid unlimited lifetime-update promises and cloud processing costs during
   the pilot.

The first commercial target is five voluntary supporters. It is a test target,
not an expected outcome. At a future $19 price, 30 purchases produce $570 gross
one-time sales before fees, refunds, taxes and support. That arithmetic is not a
forecast and is not monthly recurring revenue.

A payment route is not selected yet. The owner was asked for an existing
supporter/payment page. Before enabling it, confirm ownership, payout eligibility
and acceptance of this software category with the chosen provider. No money is
being collected or payment-provider contract accepted by this plan.

## First 7 days

| Sequence | Work | Evidence / target |
| --- | --- | --- |
| 1 | Invite friends and existing contacts with the prepared tester message. | 10 consenting human testers; aim for 5 Mac and 5 Windows. Sample size is explicit. |
| 2 | Observe first-run setup, authorized download, library discovery and playback without coaching. | Record each failed step, elapsed time and environment. Aim for 8 of 10 to complete the core journey. |
| 3 | Fix the three most frequent or severe failures; prioritize data loss and inability to start. | Reproduction and reporter retest, not just an automated pass. |
| 4 | Record a real 30-second walkthrough using owned or licensed media. | Show required component setup honestly; use the storyboard in LAUNCH-KIT.md. |
| 5 | Publish one demonstration to the owner's existing audience. | Actual visits/downloads and useful replies; log the channel. |
| 6 | Share a tailored feedback post in one relevant community after checking its current rules. | Five useful task reports, not an upvote target. |
| 7 | Add the optional Support link when checkout is ready. | Working mobile/desktop checkout; correct recipient and currency; no invented benefits. |

These are work items, not calendar bookings or scheduled posts. No promotional
messages have been sent. Drafts are prepared for review; posting requires an
explicit destination and instruction to publish.

## Days 8–30

Ask testers after seven days whether they used the app again, what task they did,
and what still required help. Record nonresponses separately from negative
answers. A proposed signal to continue is five of the first ten participants
reporting voluntary repeat use. This tiny sample is directional, not market
validation.

Publish a short update showing a real user-reported fix. Make two focused clips:
"link to local library" and "play your existing local files." Mention matching
limits wherever a catalog provider is demonstrated. Use screenshots from the
current version; no simulated download success or fictional testimonial.

Interview returning users about the last time library organization cost them
time. Show one specific future paid workflow and a proposed price; distinguish
"sounds nice" from a concrete request to purchase after release. Build the paid
workflow only if repeated needs and willingness to pay justify it. Do not take
preorders for an undefined feature.

Consider Product Hunt once the core journey succeeds for pilot users and native
Windows feedback is incorporated. It is a distribution experiment, not the main
business. Its current guidance requires a personal maker account, product URL,
name, tagline, description, launch tag, logo and gallery image. Ask people to try
and comment; never buy or request upvotes.

## Measurement and stop rules

Use the [scorecard](launch/SCORECARD.md) and [prepared launch copy](launch/LAUNCH-KIT.md). Distinguish download requests from
people, installs from downloads, and return use from first-run success. Count
human sessions separately from simulated agent reviews. Start with voluntary
reports; do not collect source URLs, cookies, audio files or background telemetry.

No ad spend in this pilot. Reconsider paid acquisition only after repeat use,
purchase conversion, refund/support costs and a positive contribution margin
have actually been measured. If users cannot get through setup, spend the next
iteration fixing setup. If they install but do not return, investigate the use
case before building a paid tier.

## Comparable products and sources

Checked 2026-11-10. Competitor pricing is evidence of available business models,
not proof NekuLoader users will pay the same amount.

- [Stacher](https://stacher.io/): free core; optional paid library/themes/support.
  [Official Patreon membership](https://www.patreon.com/cw/stacher/membership)
  lists Premium at $5/month.
- [4K Video Downloader Plus](https://www.4kdownload.com/buy/videodownloader-8?source=4k-video-downloader):
  page lists Lite $15/year, Personal $25 and Pro $45 lifetime product licenses.
  Prices/taxes may vary. NekuLoader's proposed $19 is an untested hypothesis.
- [cobalt donations](https://cobalt.tools/donate): an example of voluntary funding
  alongside a free product, not evidence of donation conversion or profitability.
- [Product Hunt submission guidance](https://help.producthunt.com/en/articles/479557-how-to-post-a-product),
  [launch FAQ](https://help.producthunt.com/en/articles/17734646-8-launch-faq-and-myths),
  [page requirements](https://help.producthunt.com/en/articles/17834537-4-build-your-product-page).
- [Current NekuLoader release](https://github.com/deviverr/NekuLoader/releases/tag/v3.2.0-alpha.3),
  docs/testing/MAC-NOTARIZATION.md and docs/testing/WINDOWS-FRIEND-TEST.md are the
  basis for its distribution/test claims.
