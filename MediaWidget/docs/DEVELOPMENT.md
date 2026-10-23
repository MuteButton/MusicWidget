# Development guide

Music Widget is a native Android home-screen widget. The product overview and screenshots are in the [repository README](../../README.md).

## Build and install

Open `MediaWidget/` in Android Studio and let Gradle sync. The project uses its checked-in Gradle wrapper; `app/build.gradle` declares the SDK and dependency versions. Configure the Android SDK through Android Studio or your local `local.properties`.

From the repository root on Windows:

```powershell
cd MediaWidget
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On macOS/Linux, use `./gradlew` instead of `gradlew.bat`. The output is `MediaWidget/app/build/outputs/apk/debug/app-debug.apk` relative to the repository root.

After installation, open the app, grant notification access through its setup action, add the widget, and start a media app. The debug build includes a deterministic test player. The release source set excludes that fixture; release signing uses local keystore configuration and must not include secrets in Git.

## Architecture

| Component | Responsibility |
| --- | --- |
| `MediaListenerService` | Observe active sessions, register/unregister callbacks, schedule progress updates, and maintain session-matched notification destinations |
| `WidgetRenderer` | Select a player, build RemoteViews, route taps, update progress, and manage title scrolling |
| `V5Artwork` / `GlassArtwork` | Render the full Android 12+ glass surfaces and older-layout artwork |
| `ArtworkColors` | Derive a palette from cover art and cache recolored glass surfaces |
| `MainActivity` | Notification-access setup, preferred player, browser URL, and color choices |

## Playback and navigation

The widget switches between playback, idle, and permission-recovery states. Controls use the selected session's supported actions. Elapsed time and remaining time follow playback position; unknown-duration media hides progress. The bar is not a seek control.

Title, artist, and artwork taps prefer the session's own activity. If it is missing, the widget uses a media notification content intent only when both its package and media-session token match. Otherwise it falls back to opening the player app. The notification fallback fixes real Chrome sessions that omit a session activity but publish a destination for the playing tab.

Idle **Open App** uses the preferred player, then the remembered external player, then the chooser. Browser selections expose an optional URL saved separately for each browser. Bare domains normalize to HTTPS; invalid schemes are rejected. Active sessions take precedence over that saved idle destination.

## Rendering and title motion

The full Android 12+ layout uses v5 surfaces with live text, artwork, controls, and progress. The card maintains a 1030:225 aspect ratio, centered in the space allocated by the launcher. Narrow widgets use a compact layout; Android versions before 12 retain a native gradient layout. These surfaces do not sample or blur the wallpaper.

A newly displayed long title in the full layout waits briefly, scrolls once, and returns to its static ellipsis. The launcher performs the TextView animation; the app schedules only the start and restoration updates. Duplicate metadata, pause/resume, seeks, and progress ticks do not restart the pass. Track changes, screen-off, widget removal, and listener disconnect cancel stale work. Compact and older layouts retain static titles.

Artwork glass is the default for the full v5 layout. A 48 x 48 cover sample supplies the palette; the glass, caption, and progress accent change while artwork and white highlights are preserved. Missing or neutral artwork and the idle state use Classic. Classic remains selectable, and saved preferences are respected.

## Resource behavior

Progress-only updates run approximately once per second during advancing playback with known duration. They stop when the screen is off, playback is paused, or no widget exists. Session and state changes update independently.

Artwork sampling and derived covers are cached. Source bitmap references are weak; adaptive glass has a four-entry LRU and legacy surfaces have an eight-entry LRU. Media callbacks and notification destinations are removed when their sessions/notifications disappear and cleared on listener disconnect.

See [NFR verification](../tools/NFR_VERIFICATION.md) for measured CPU, memory retention, thread/descriptor counts, and callback latency. These emulator measurements are not physical-device battery guarantees.

## Test on an emulator

From the repository root, after installing the current debug APK and UI probe and adding a full-width widget:

```powershell
.\MediaWidget\tools\Test-AllEmulator.ps1
.\MediaWidget\tools\Test-WidgetNfr.ps1
.\MediaWidget\tools\Test-WidgetResponsiveness.ps1
```

Use the dedicated emulator setup described in [regression coverage](../tools/REGRESSION_COVERAGE.md). The real Chrome group requires Node.js, completed Chrome onboarding, and free localhost port 8765. Test helpers currently default to the original developer's Windows SDK/workspace paths; adjust those defaults for another machine. Tests interrupt playback and restore saved widget preferences where documented.

The suite taps actual widget controls, checks MediaSession callbacks, captures rendered UI, and compares visual evidence. The [acceptance ledger](../tools/FEATURE_ACCEPTANCE.md) distinguishes current-APK automated results from earlier real YouTube/manual evidence. Native idle has an exact reference assertion; live playing data and adaptive colors are not claimed to be pixel-identical to a static mockup.

## README image provenance

`images/artwork-glass-showcase.png` is an unchanged copy of `artifacts/artwork-glass-preview/artwork-examples.png`. It contains actual emulator widget crops with debug metadata, supplied reference artwork for the first track, and synthetic artwork for the other tracks. It is a feature illustration, not evidence of integration with a particular music service.
