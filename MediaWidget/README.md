# Music Widget

**Your music. Your artwork. Your home screen.**

A glass-style Android widget with artwork-adaptive colors, live track progress, and playback controls. Tap through to your player, let long titles scroll into view, and choose a favorite music app or browser destination for when playback stops.

![Music Widget with blue, green, and pink artwork-inspired glass.](docs/images/artwork-glass-showcase.png)

*Actual emulator captures using demo tracks and test artwork. Full glass layout on Android 12+.*

**[Explore the widget and get started](../README.md)**

## Work on the app

This folder is the Android Studio project. From here, build and run the local checks:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

- [Development guide](docs/DEVELOPMENT.md): setup, architecture, and behavior details.
- [Feature acceptance](tools/FEATURE_ACCEPTANCE.md): verified scenarios and outstanding coverage.
- [Emulator regression](tools/REGRESSION_COVERAGE.md): actual widget UI and media-session tests.
- [Performance and memory](tools/NFR_VERIFICATION.md): measurements, stress tests, and limits.
- [Debug media fixture](tools/EMULATOR_WIDGET_TESTS.md): repeatable test playback.

[MIT licensed](LICENSE).
