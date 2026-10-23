# Signed release acceptance

This is the historical prototype report. See [neutral signing release acceptance](NEUTRAL_RELEASE_ACCEPTANCE.md) for Music Widget 1.1.

Release candidate: Music Widget 1.0, tested 2026-10-23 on the API 36.1 S25 Edge approximation emulator. This is an Android emulator, not a physical Samsung device or One UI.

## Artifact identity

- Local deliverable: `artifacts/final-release/MusicWidget-1.0-release.apk` (2,575,612 bytes).
- SHA-256: `b61e54a4a9fbd8db5d918ef36826f5625f6858fe67e44ca52cd5d559b212e75a`.
- Release minification and resource shrinking enabled; APK signature verification passed (v2).
- Manifest inspection found no debug fixture/calibration components or debuggable flag.
- Emulator-installed APK SHA-256 matched the deliverable before acceptance testing.
- Shipping application source is unchanged from `d1bc5ae`; this cleanup adds portable test tooling, a separate acceptance player, documentation, and repository hygiene rules.

## Build validation

`:app:assembleRelease`, `:app:testDebugUnitTest`, `:app:lintRelease`, and `:test-player:assembleDebug` completed successfully. All 11 JVM tests passed. Unit tests run in the debug variant; the emulator checks below exercise the signed, minified release itself.

Release lint: **0 errors, 135 warnings**. Warnings include unused resources, Kotlin extension suggestions, vector compatibility, dependency/target SDK maintenance, and small text. This is not a warning-free lint sign-off.

## Emulator evidence

Results and screenshots remain locally under `artifacts/final-release/`, excluded from Git. The separate `com.example.mediawidget.testplayer` application publishes real media sessions and records transport callbacks without making the shipping app debuggable.

| Group | Passed checks | Local evidence directory |
| --- | ---: | --- |
| Signed artifact identity, metadata, transport, timestamps, seek, competing sessions, recovery, colors and navigation | 36 | acceptance |
| Automatic title motion, restoration, duplicate suppression and title taps | 11 | marquee |
| Real Chrome playback, playing-tab selection and app restart recovery | 11 | chrome |
| Browser-only URL settings, validation, persistence, active-session precedence and idle dispatch | 12 | browser |
| Callback responsiveness (10 actual taps) | 2 | responsiveness |
| 30-cycle release stress: process survival, memory threshold and final control state | 3 | stress |

**Total: 75 release emulator checks passed.** Memory PSS was 29,838 KiB after warmup, peaked at 43,554 KiB in the samples, and ended at 38,712 KiB (+8,874 KiB without forced GC), within the 32 MiB growth threshold. The process did not restart and remained responsive.

Callback latency including host ADB and polling overhead: median **765.4 ms**, maximum **1,157.9 ms** across ten taps, below the 2,000 ms acceptance threshold. This measures end-to-end callback observation, not display frame latency.

The first browser attempt stopped because the setup launch reused an earlier Open App action. The test now requests a fresh MAIN setup activity explicitly. The complete retry passed; the earlier evidence is preserved in `browser-first-attempt`.

## Scope and limits

The earlier broad debug regression and retained-object/CPU analysis remain in [FEATURE_ACCEPTANCE.md](FEATURE_ACCEPTANCE.md) and [NFR_VERIFICATION.md](NFR_VERIFICATION.md), with their original APK identities. They are not relabeled as tests of this release binary. Real YouTube and manual launcher evidence in those reports is historical; this release run uses a separate media player and actual Chrome MediaSession playback.

Release stress uses unforced-GC process memory measurements, not heap-retention analysis. Passing it cannot prove the absence of every leak. Physical Samsung/One UI behavior, other Android versions, overnight operation, and a full accessibility audit remain outside this release run. No universal 100% coverage or pixel identity with live media is claimed.

## Repository privacy and cleanup

Machine-local Android Studio and VS Code settings are removed from Git tracking while preserved locally. An unused duplicate layout outside the Gradle source sets was archived locally; a redundant emulator download was removed after checking the extracted emulator. Generated artifacts, logs, build directories, signing stores/properties, and environment files are ignored. Tool defaults use environment-based SDK discovery and repository-relative output paths rather than a personal home directory.

`Test-RepositoryHygiene.ps1` scans tracked and candidate text for personal paths, common credential patterns, and accidental copies of local signing passwords without printing the values. It excludes generated/private files from the commit and does not rewrite historical commits or Git author metadata. Private evidence stays local; this report contains only sanitized measurements and artifact hashes.
