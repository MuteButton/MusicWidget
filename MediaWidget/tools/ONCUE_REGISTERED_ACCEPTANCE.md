# Registered OnCue: transparent glass acceptance

Verified October 24, 2026 on the API 36.1 emulator.

The application ID is `io.github.mutebutton.oncue` (version 1.2, code 3). The release signing certificate SHA-256 remains `35162d5dd3ff1b970a23bd5cf0cbb0fa90df265a44babf6dc722ad39e3a70f15`, matching the certificate registered in Android Developer Console. Earlier `io.github.mutebutton.musicwidget` builds install separately; settings and notification access do not migrate.

## Transparency correction

The supplied glass PNGs contain opaque pixels outside their rounded rims. The renderer now applies a rounded alpha mask after composing each cached surface. It preserves interior RGB, fades a narrow outer halo, and makes the rectangular image edges transparent. Both idle permission states, playing/paused controls and adaptive colors are covered. Source artwork and live hit targets remain unchanged.

## Verification

- Signed release APK/AAB build and bundle validation passed.
- 13 JVM tests passed, including silhouette edge, rim and halo checks.
- Four Android instrumentation tests passed on the emulator. The transparency test checks actual rendered bitmaps for both idle states and all 16 playback/control combinations, including three adaptive hues per combination.
- All 36 integration assertions passed on the byte-identical signed APK: metadata, elapsed/progress updates, pause/resume, next/previous, seeking, action availability, buffering, competing sessions, navigation, colors, restart, screen wake, notification-permission restoration and final return to idle.
- The preceding pre-transparency APK run passed 35 checks but failed final idle recovery. That failure is retained in `artifacts/oncue-registered-release/acceptance/`; no listener code was changed and a root cause was not established. The fresh final-build run passed the full sequence; this is not a claim that an intermittent listener issue was fixed.
- Actual home-screen screenshots are saved in `artifacts/oncue-transparent-release/final/`. Historical builds and evidence were not overwritten.

## Deliverables

Local, Git-ignored output: `artifacts/oncue-transparent-release/`.

| File | SHA-256 |
| --- | --- |
| OnCue-1.2.apk | `38c54124e141a238850a0be487955c54a708a35ce33fdf200b8cb6bb9c2684ee` |
| OnCue-1.2.aab | `af721611cffc0e4811d33b4f6cce55fb2b0953063462884ed70ac9f784ff1d88` |

Physical-phone authorization and installation remain with the owner. This report does not certify a real Samsung device or overnight battery behavior. Other historical emulator scripts still default to the old package; `Test-ReleaseApk.ps1` targets the registered package and passes it to the shared UI helpers.