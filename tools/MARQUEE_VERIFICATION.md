> Historical tap-to-scroll report. Superseded by automatic scrolling and tap-to-open in commit b278a7f. See [current regression coverage](REGRESSION_COVERAGE.md) and Test-WidgetMarquee.ps1.

# Title marquee verification - 2026-10-20

Implemented for the Android 12+ full v5 layout. Tap the title to wait 600 ms, scroll left once, and return to the original ellipsized text. Short titles stay still. Repeated taps restart; track/state changes and screen-off cancel pending work. Artwork and artist taps still open the player. Compact and legacy layouts keep tap-to-open behavior.

Tested on the API 36.1 emulator's actual launcher, using a real debug MediaSession. Native TextView animation runs in the launcher; the app sends no per-frame updates. Other launchers may handle focus differently and are not certified by this test.

- `Test-WidgetMarquee.ps1`: 14 checks passed, including real screenshot pixel changes during motion, exact pixel restoration, full accessible title, repeat taps, track-change cancellation, short titles, and advancing timestamps during animation.
- Core and extended regression suites: 26 + 10 checks passed.
- Final glass/transport/installed-APK checks: six passed. The Open App assertion now accepts the configured/remembered player as well as the chooser, matching production behavior after the YouTube test.
- Six unit tests passed. Debug build and lint passed; lint reported 134 warnings, no errors.
- Static playing comparison remains 3.9063603020/255 mean RGB error; idle remains 1.3165177994/255. The marquee change did not alter the normal reference appearance.

Before and restored title crops have identical SHA-256:
`5052A45460F97E76B9B23EF4BB74FDE3D6F5C43A2364B5A4F7E1CE0ED46C16BF`.
The moving crop differs:
`12BE502F5E4830DBDCC34E1C72523889A840B7D9346AB92E36F4E8BAA40827D9`.

Local artifacts: `../artifacts/marquee-acceptance/`, `marquee-regression/`, and `marquee-comparison/`. The before/moving/restored panel uses unmodified emulator card crops. Raw screenshots are retained alongside it.
