# Feature acceptance ledger

OnCue 1.2.3 tap-routing changes and final-APK checks are tracked in
[tap-routing acceptance](TAP_ROUTING_ACCEPTANCE.md).

The local OnCue 1.2.2 playing-title, Samsung and lock/unlock checks are documented
separately in [playing-title acceptance](PLAYING_TITLE_ACCEPTANCE.md). Older results
below retain their original APK scope.

Verified on 2026-10-22 using API 36.1 and the S25 Edge approximation AVD. Debug regression APK SHA-256: `95bf5ffb2df2764a6dbcfaf4f9e4cb569974846929024e9b3f58e7f3c21811af`. The Chrome playing-tab navigation fix is included.

The signed release is verified separately in [release acceptance](RELEASE_ACCEPTANCE.md); the results below retain their original APK scope.

## Automated emulator results

All ten core groups passed together in **full-regression-chrome-fix: 106 distinct checks**, aggregate exit code 0. Earlier failed and interrupted runs remain preserved. Real Chrome integration is also tested separately below.

| Group | Checks | Evidence under repository artifacts/ |
| --- | ---: | --- |
| Core: metadata, timestamps/progress, transport callbacks, seek, missing data, competing sessions, access recovery | 26 | full-regression-chrome-fix/core |
| Lifecycle: buffering, connecting, clamp, process death, sleep/wake, large font | 10 | full-regression-chrome-fix/lifecycle |
| Automatic marquee: motion, one pass, restoration, short titles, duplicate/resume suppression, navigation | 11 | full-regression-chrome-fix/marquee |
| Browser URL: browser-only setting, validation, persistence, active-session precedence, URL dispatch/clear | 12 | full-regression-chrome-fix/browser |
| Artist navigation, second-session callback isolation, marquee interrupted by sleep/session removal | 8 | full-regression-chrome-fix/additional_scenarios |
| Idle preferred/remembered player, missing preferred app, chooser | 8 | full-regression-chrome-fix/idle_routing |
| Adaptive colors: default, palettes, artwork preservation, missing-art fallback, persistence | 21 | full-regression-chrome-fix/colors |
| Launcher glass: geometry, edge hit areas, controls, idle navigation | 6 | full-regression-chrome-fix/launcher_glass |
| Native idle exact reference | 2 | full-regression-chrome-fix/native_idle |
| Native playing reference measurement | 2 | full-regression-chrome-fix/native_playing |

The initial colors failure detected 54 of 9,025 cover pixels differing by at most one channel level (mean RGB error 0.001995), consistent with compositor rounding. The corrected assertion permits at most one channel level and mean error below 0.02; it still rejects recoloring. The focused retest passed all 21 checks. Marquee movement uses a measured glyph mask, not PNG-byte identity; see REGRESSION_COVERAGE.md.

## Real-player and launcher evidence

| Scenario | Result | Evidence under artifacts/ |
| --- | --- | --- |
| Real YouTube elapsed/remaining/progress advance, freeze on pause, resume | PASS, 12 checks | current-youtube-progress |
| Title opens actual YouTube player; paused timestamp matches player exactly (2:12) | PASS, 2 checks | current-youtube-navigation |
| Artwork opens actual playing YouTube session | PASS, 1 check | current-youtube-artwork-navigation |
| Add/pin second widget; both instances reflect playback and controls | PASS, 4 checks | current-launcher-manual |
| Compact horizontal drag resize, play callback, long-title layout and navigation | PASS, 4 checks | current-launcher-compact-checks |
| Expand width/height, contained controls, Next callback after resize | PASS, 4 checks | current-launcher-expanded-checks |
| Landscape metadata and Pause callback, verified portrait restoration | PASS | current-launcher-rotation and current-launcher-rotation-restore |
| Remove extra test widget, retain original, restore preferences | PASS, 2 checks | current-launcher-cleanup |

The real video was "Key & Peele - Substitute Teacher 1&2 (cut)", duration 6:05. YouTube picture-in-picture was temporarily enabled for Home playback and restored OFF afterward (current-youtube-manual/youtube-pip-restored-off.png). Stopped YouTube correctly produces idle. No fixture result is presented as real YouTube evidence.

## Visual acceptance and remaining scope

Native idle matched all 231,750 reference pixels exactly. Classic live playing measured mean RGB error 3.3413; live media text, artwork and accurate progress are intentionally retained as requested and are not pixel-identical to a fixed raster mockup. Adaptive full glass is tested; compact uses its existing static styling.

This verifies the listed scenarios on this emulator, not every possible device/player combination. YouTube Music, Samsung One UI/physical hardware, other Android APIs, and physical battery/thermal or overnight soak behavior remain unverified. CPU and bounded memory-retention profiling are now verified; see NFR_VERIFICATION.md. Exact-tab restoration is now verified for real Chrome; other browsers remain unverified. Browser URL dispatch and fixture active-session precedence are verified separately. No universal 100% coverage claim is made.

## Infrastructure history

Earlier interrupted and failed runs are retained. The emulator issue was isolated to a host GLES DLL crash; the workspace copy of official emulator 37.2.12 now loads its own bundled SwiftShader DLLs. The installed SDK and Windows DLLs were not replaced. See EMULATOR_RECOVERY.md. UI probes reject ANR/crash dialogs and have bounded timeouts; infrastructure failures are never counted as feature passes.
The final cold boot encountered a System UI ANR (current-launcher-final/latest-ui.xml). After restarting System UI and waking/unlocking the guest, launcher checks completed. The first rotation return assertion checked widget presence only; its label was too broad. Actual portrait dimensions were subsequently verified independently in current-launcher-rotation-restore. Launcher landscape, system rotation, font scale and saved widget preferences were restored, and the fixture was stopped.

## Chrome regression found and fixed

The original APK failed to return to a playing Chrome tab after another tab was selected (current-real-browser-tabs). Chrome published no session launch intent, but its media notification supplied a content intent with the matching session token. Navigation now prefers the session activity, then the exact package/token-matched media notification, then the normal app fallback. Notification changes refresh navigation only when the destination changes.

The new APK passed assembleDebug, all 11 unit tests, lintDebug, and the complete 106-check core emulator run. All nine initial real Chrome integration checks passed in real-chrome-fixed, including switching back to the playing tab. The repeatable test is Test-RealChrome.ps1 with browser-media-server.cjs and is included in the runner as real_chrome. Its expanded process-recovery scenario passed all 11 checks in real-chrome-process-recovery, including exact-tab restoration after killing and restarting the widget process. Total current-APK functional coverage is 117 checks across eleven groups.

Real YouTube and manual resize/multiple-widget/rotation results above were recorded on the preceding APK (6d738bed62f8161fc685682d4bc7ce4e5dd07db009cf2c0eb10c999a1009a59d). They are retained baseline evidence, not relabeled as tests of the new binary. The new change affects navigation fallback only; the full core rerun verifies unchanged playback, lifecycle, layout, colors and routing on the new APK.
## NFR results

The corrected 30-cycle profiling run passed all 11 checks in nfr-visible-widget. Playback CPU was 0.348% of one guest core; paused, idle and screen-off CPU were 0%. Post-GC Java allocation was flat, native allocation grew 293 KiB then stabilized, and threads/descriptors stayed flat. Controllers and notification destinations were released on idle. Responsiveness passed both checks in nfr-responsiveness: ten actual taps reached the player callback with median 134 ms and maximum 271 ms, including ADB overhead. See NFR_VERIFICATION.md for methodology and limits.
