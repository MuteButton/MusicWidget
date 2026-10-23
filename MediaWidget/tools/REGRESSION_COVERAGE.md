# Emulator regression coverage

Run from the repository root after installing the current debug APK and the UI probe, with exactly one running emulator and an existing full-width home-screen widget:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\MediaWidget\tools\Test-AllEmulator.ps1
```

This is an emulator UI/integration suite, not a wrapper around unit tests. It taps actual launcher widget controls, reads live MediaSession callback counters, inspects rendered UI, and captures/hashes screenshot pixels. The debug player publishes real Android media sessions and silent audio.

The runner checks installed APK identity, records commit/device/API/resolution/density, runs independent groups even when another group fails, and writes per-group logs, screenshots, acceptance results, and a combined summary.json. Any failing group fails the aggregate command. Preferences, font scale, and original notification-access state are restored afterward. This is for a dedicated emulator: tests interrupt media playback and stop/start the debug app.

| Group | Automated checks |
| --- | --- |
| Core | Metadata, elapsed/remaining/progress, pause/resume, next/previous, seek, disabled actions, rapid changes, missing artwork capture, unknown duration, null metadata, competing sessions, artwork navigation, idle, permission revoke/recovery, crash log |
| Lifecycle | Buffering/connecting cancellation, end-of-track clamping, main-process death/recovery, controls after restart, screen sleep/wake, large font and text/control separation |
| Marquee | Real moving title pixels, seek refresh, static restoration, no loop/restart on duplicate metadata/resume, advancing time, long/short title navigation, short-title stability, new-track scroll, cancellation of stale title work |
| Browser URL | Chrome vs YouTube visibility, HTTPS normalization, persistence across selection, invalid scheme rejection, active-session precedence, explicit browser URL dispatch, clearing |
| Artwork colors | Default when preference is absent, three palettes vs Classic pixels, cover pixel preservation, different covers producing different palettes, missing-art fallback, saved preference after process restart |
| Launcher glass | Installed APK hash, control hit areas/edge taps, playback controls, idle/open-app routing, screenshots and reference metrics |
| Native idle | Production glass at 1030x225, exact RGB match against v5 idle reference in diagnostic host |
| Native playing | Production RemoteViews at 1030x225 with live fixture metadata; records reference differences (does not claim exact identity) |

## Explicit coverage limits

Additional groups included in the runner (both passed in full-regression-expanded):

- `Test-WidgetScenarios.ps1`: artist navigation, isolated second-session transport callbacks, screen-off during marquee, and session disappearance during marquee.
- `Test-IdleRouting.ps1`: preferred native app, remembered native app, missing preferred app fallback, and empty-preference chooser.

UI-probe calls time out after 45 seconds instead of hanging indefinitely when the emulator compositor or accessibility instrumentation stalls. A timeout is a failure, never a pass.

Marquee comparisons use white-glyph masks with a 1% changed-pixel threshold, retaining original crops and `title-comparisons.json`. Whole-PNG identity is too strict for software-renderer text reinflation: an observed stationary redraw changed 0.324% of the glyph mask, while actual movement changed 20.566%. Static assertions include a second post-resume sample. This tolerance applies to motion detection only; it does not weaken the exact idle-reference RGB assertion.

A green aggregate result is scoped to the connected emulator/API/launcher. It does **not** prove every installed media app, Samsung One UI, physical-device battery restrictions, older Android releases, or all accessibility configurations work.

- **Real YouTube:** `Test-YouTubeProgress.ps1` is separate and requires a genuine video already playing with its widget visible (usually with picture-in-picture). It checks both timestamps and screenshot progress through play/pause/resume. Ads, sign-in, network, and account availability prevent unattended deterministic setup. Fixture passes are not reported as YouTube passes.
- **Launcher resizing and multiple widgets:** pin/add, simultaneous compact/full widgets, width/height drag resizing and transport/title interactions passed manual emulator checks. Landscape metadata/controls and verified portrait restoration also passed. See FEATURE_ACCEPTANCE.md for evidence. These checks are separate from the aggregate runner.
- **Other players/browsers:** Chrome and YouTube selection are covered. Native players and exact browser-tab restoration depend on each app's published session activity. No universal exact-tab guarantee is possible.
- **Visual fidelity:** idle has an exact native-reference assertion. Live text/artwork/progress and adaptive color modes intentionally differ from the static design. Screenshot capture alone is not a pixel-perfect assertion.
- **Performance:** bounded updates are exercised through playback/lifecycle tests, but these tests are not a CPU, battery, memory-leak, or long-duration benchmark.

## Latest verified results

All ten core groups passed in one run (106 checks) in full-regression-chrome-fix. The real_chrome group passed its extended 11 checks in real-chrome-process-recovery, giving 117 current-APK functional checks. Original aggregate failures are retained. Real YouTube and manual launcher results are recorded separately in FEATURE_ACCEPTANCE.md.

Cover preservation permits maximum channel error 1 and mean RGB error below 0.02 to accommodate measured compositor rounding; adaptive glass must differ by mean RGB more than 2. Original crops and color-pixel-comparisons.json are retained. Exact idle-reference RGB remains strict.

## Real Chrome tab regression

The runner now includes real_chrome (Test-RealChrome.ps1). It requires Node.js, Chrome with first-run onboarding completed, and free localhost port 8765. A local generated audio page publishes a real browser MediaSession; callbacks are recorded by the local server. The test checks metadata, play/pause, title/artwork navigation, and returning to the playing tab after selecting a different tab. It closes its server, removes its ADB reverse mapping, and stops Chrome afterward. This is deterministic browser integration, separate from the debug-player fixture.


NFR profiling and responsiveness are separate repeatable scripts: Test-WidgetNfr.ps1 and Test-WidgetResponsiveness.ps1. See NFR_VERIFICATION.md for passing results, heap analysis and explicit limits.
