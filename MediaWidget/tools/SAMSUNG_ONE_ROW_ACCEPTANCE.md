# Samsung one-row and timer verification — 2026-10-25

Local OnCue 1.2.1 candidate, version code 4. Not pushed or published.

APK: `artifacts/samsung-one-row/build/OnCue-1.2.1.apk`
SHA-256: `a1e2b509879d32d70b757899cdfedb01ff1862b0f5c55f0c6738aab3ca67eeee`

## Findings and changes

- The published provider requested two rows and a 111dp minimum resize height. Android 12+ now requests one row with a 40dp minimum. Older Android provider metadata remains unchanged.
- Android 12+ retains the existing glass design at narrow widths and scales the entire pill proportionally when host height is insufficient. Internal element positions are unchanged relative to the pill. Short hosts retain progress rather than hiding it below 96dp.
- The physical Samsung logged `FreecessHandler: freeze io.github.mutebutton.oncue` after backgrounding, while its notification-listener service remained bound. No recorded app crash was found. Per-second updates cannot run in a frozen process.
- Changed only OnCue's per-app battery mode from Optimized to Unrestricted on the connected phone. The existing 1.2 APK then advanced from 2:15 to 2:39 over four fresh UI samples. No timer implementation change was needed. Setup now offers Samsung-specific explanation and an app-settings shortcut; it does not silently change battery policy.
- Release packaging derives the artifact filename from versionName instead of hardcoding 1.2.

## Verification

- Signed release assemble/bundle: passed. Full lint: 0 errors, 72 warnings.
- JVM: 15/15 passed, including proportional sizing across 250–500dp widths and 40–111dp heights.
- Signed APK emulator regression at one-row size: 36/36 passed. Includes transport, timestamps, seek, session selection, navigation, artwork modes, restart, screen wake, permissions, and idle recovery.
- Physical Samsung: installed APK hash matches the candidate. Launcher reports `semAppWidgetRowSpan=1`, with height reduced from 218dp to 96dp. Pill bounds fit inside the one-row host.
- YouTube Music on final APK: timestamps 3:13, 3:21, 3:29, 3:37; progress and remaining time update consistently. Pill stays within the host throughout.
- Brave/YouTube on final APK: timestamps 0:59, 1:07, 1:15, 1:23, with matching progress updates across 24 seconds.
- Temporary USB stay-awake setting is restored to its original value after each phone test. The OnCue-only Unrestricted battery setting intentionally remains enabled.

## Evidence and limits

Evidence is under ignored `artifacts/samsung-one-row/`. Phone diagnostics and screenshots are local only and contain personal device/media context; do not commit or publish them. Selected non-personal findings are recorded here instead.

UIAutomator initially failed during a track change; stale output from that attempt is not accepted. Subsequent checks delete the prior remote capture and require a fresh successful dump before reading timestamps.

This verifies the reported four-second freeze with the per-app exception and one-row behavior on the connected Samsung. It does not claim all Samsung firmware or overnight battery behavior has been tested. The rejected accessibility redesign is absent.

