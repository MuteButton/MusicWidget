# Music Widget

An Android widget that displays currently playing media with album art and playback controls.

## Features

- 🎵 Displays current track info (title, artist, album art)
- 🎨 Blue-purple glass design with compact and full layouts
- ⏯️ Playback controls (play/pause, next, previous)
- 🖼️ Click album art to open the active media app
- 🌐 Open App remembers your last player or uses your chosen default
- ⚡ Optimized with intelligent caching for smooth performance

## Installation

### From Releases (Recommended)

1. Download the latest `app-release.apk` from [Releases](https://github.com/MuteButton/MusicWidget/releases)
2. On your Android device, enable "Install from Unknown Sources":
   - Go to **Settings** → **Security** (or **Apps**)
   - Enable **Install unknown apps** and allow your browser/file manager
3. Open the downloaded APK file and tap **Install**
4. After installation, grant **Notification Access** permission:
   - Go to **Settings** → **Apps** → **Music Widget** → **Permissions**
   - Or: **Settings** → **Apps** → **Special Access** → **Notification Access**
   - Enable access for Music Widget

### Adding the Widget

1. Long-press on your home screen
2. Tap **Widgets**
3. Find **Music Widget** and drag it to your home screen
4. The widget will automatically display media from supported apps (Spotify, YouTube Music, Brave, Chrome, etc.)

## Requirements

- Android 7.0 (API 24) or higher
- Notification access permission (to read media playback info)

## Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/MuteButton/MusicWidget.git
   cd MusicWidget
   ```

2. Open in Android Studio

3. Build the project:
   ```bash
   ./gradlew assembleDebug
   ```

4. Find APK at: `app/build/outputs/apk/debug/app-debug.apk`

## Privacy

This app only accesses media session information to display currently playing tracks. No data is collected, stored, or transmitted to any external servers.

## License

This project is open source and available under the MIT License.

## Contributing

Contributions are welcome! Feel free to submit issues or pull requests.

## Support

If you encounter any issues or have suggestions, please open an issue on GitHub.

## Widget behavior and development checks

The widget switches between an actionable idle/setup state and a playback state with artwork, supported transport controls, and elapsed/remaining time. Playback progress is a display, not a seek slider. Unknown-duration media hides progress. A narrow widget uses a compact layout; progress is hidden below 120dp height in that layout. The full layout on Android 12+ uses the supplied v5 raster skins with live text and aspect-preserving artwork with a faded continuation. Older Android versions retain the native gradient layout. Neither captures the wallpaper.

Notification access is required. The app reads media sessions locally; it does not upload media information. `Open App` uses the preferred player chosen in setup, or remembers the last external player. Artwork and artist text open the active session's player. In the Android 12+ full layout, tapping a long title waits briefly, scrolls it left once, and restores the original ellipsis. Short titles remain stationary; another tap restarts the scroll. Compact and older layouts retain tap-to-open titles.

Title motion uses the launcher's native TextView marquee, with no per-frame bitmap or widget updates. Two delayed updates start and restore the title; track/state changes, widget refreshes, and screen-off cancel pending title work. Progress-only ticks leave the animation alone. Marquee behavior is verified on the API 36.1 emulator's launcher; other launchers may handle focus differently. The one-pass timing follows [Android's TextView implementation](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/widget/TextView.java).

The optional **Artwork glass** mode is available under **Colors** in setup. Classic glass remains the default. For the Android 12+ full v5 layout, Artwork glass derives a hue from a 48 x 48 cover sample, recolors the glass/rim/control surfaces, and coordinates the artist caption and progress accent. White highlights and glyphs are preserved; luminance compensation prevents green hues from washing out controls. Covers and live track data are unchanged. Missing or neutral artwork falls back to Classic; idle remains Classic. Palette extraction is cached by source bitmap/generation, with at most four recolored surfaces cached, and progress-only ticks do not sample or recolor. `tools/Preview-ArtworkColors.ps1` captures paired emulator examples through the real settings UI.

Rendering caches bounded artwork and a fixed set of glass surfaces and control skins. The v5 full card preserves the supplied 1030:225 aspect ratio in both states, centered in its launcher allocation. Its coordinates and typography scale together; the launcher controls outer placement. While playback advances, progress-only updates run once per second, using consistent unpadded minute formatting. Timers stop while the screen is off, playback is paused, or no widget exists. Session/state changes update immediately.

V5 controls share one rendered surface to avoid separate scaling seams, with independent accessible click targets and supported-action states. Missing artwork uses the same crop/fade geometry. The renderer caches one bounded cover, weakly references its source, and avoids generating the legacy cover when only v5 widgets exist. Native-resolution idle is verified RGB-identical to the reference; live playback remains an approximation. See the fidelity report for measured differences and emulator evidence.

Build and check with `gradlew assembleDebug testDebugUnitTest lintDebug`. The debug APK includes a deterministic silent media player for emulator testing; the release source set excludes it. See [emulator test instructions](tools/EMULATOR_WIDGET_TESTS.md), the reproducible [acceptance script](tools/Test-WidgetEmulator.ps1), and [reference fidelity status](tools/REFERENCE_FIDELITY.md). Debug fixture screenshots use test metadata, a reference cover crop for the first track, and synthetic artwork for the other tracks; they do not show a third-party music service.
