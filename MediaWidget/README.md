# Music Widget

An Android widget that displays currently playing media with album art and playback controls.

## Features

- ðŸŽµ Displays current track info (title, artist, album art)
- ðŸŽ¨ Blue-purple glass design with compact and full layouts
- â¯ï¸ Playback controls (play/pause, next, previous)
- ðŸ–¼ï¸ Click album art to open the active media app
- ðŸŒ Open App remembers your last player or uses your chosen default
- âš¡ Optimized with intelligent caching for smooth performance

## Installation

### From Releases (Recommended)

1. Download the latest `app-release.apk` from [Releases](https://github.com/MuteButton/MusicWidget/releases)
2. On your Android device, enable "Install from Unknown Sources":
   - Go to **Settings** â†’ **Security** (or **Apps**)
   - Enable **Install unknown apps** and allow your browser/file manager
3. Open the downloaded APK file and tap **Install**
4. After installation, grant **Notification Access** permission:
   - Go to **Settings** â†’ **Apps** â†’ **Music Widget** â†’ **Permissions**
   - Or: **Settings** â†’ **Apps** â†’ **Special Access** â†’ **Notification Access**
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

Notification access is required. The app reads media sessions locally; it does not upload media information. `Open App` uses the preferred player chosen in setup, or remembers the last external player. Artwork, artist text, and title taps open the active session's player. In the Android 12+ full layout, a newly displayed track with a long title waits briefly, scrolls left once automatically, and restores the original ellipsis. Short titles remain stationary. Compact and older layouts retain static titles and tap-to-open navigation.

Title motion uses the launcher's native TextView marquee, with no per-frame bitmap or widget updates. Two delayed updates start and restore the title. Track changes and screen-off cancel pending title work. Duplicate metadata, pause/resume, seeks, and progress ticks do not restart the animation; refreshes during the pass preserve its title view. A newly created app process may show one fresh pass for the current track. Marquee behavior is verified on the API 36.1 emulator's launcher; other launchers may handle focus differently. The one-pass timing follows [Android's TextView implementation](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/widget/TextView.java).

**Artwork glass** is the default color mode. **Classic glass** remains available under **Colors** in setup, and explicitly saved selections are preserved. For the Android 12+ full v5 layout, Artwork glass derives a hue from a 48 x 48 cover sample, recolors the glass/rim/control surfaces, and coordinates the artist caption and progress accent. White highlights and glyphs are preserved; luminance compensation prevents green hues from washing out controls. Covers and live track data are unchanged. Missing or neutral artwork falls back to Classic; idle remains Classic. Palette extraction is cached by source bitmap/generation, with at most four recolored surfaces cached, and progress-only ticks do not sample or recolor. `tools/Preview-ArtworkColors.ps1` captures paired emulator examples through the real settings UI.

Rendering caches bounded artwork and a fixed set of glass surfaces and control skins. The v5 full card preserves the supplied 1030:225 aspect ratio in both states, centered in its launcher allocation. Its coordinates and typography scale together; the launcher controls outer placement. While playback advances, progress-only updates run once per second, using consistent unpadded minute formatting. Timers stop while the screen is off, playback is paused, or no widget exists. Session/state changes update immediately.

V5 controls share one rendered surface to avoid separate scaling seams, with independent accessible click targets and supported-action states. Missing artwork uses the same crop/fade geometry. The renderer caches one bounded cover, weakly references its source, and avoids generating the legacy cover when only v5 widgets exist. Native-resolution idle is verified RGB-identical to the reference; live playback remains an approximation. See the fidelity report for measured differences and emulator evidence.

Build and check with `gradlew assembleDebug testDebugUnitTest lintDebug`. The debug APK includes a deterministic silent media player for emulator testing; the release source set excludes it. See [emulator test instructions](tools/EMULATOR_WIDGET_TESTS.md), the reproducible [acceptance script](tools/Test-WidgetEmulator.ps1), and [reference fidelity status](tools/REFERENCE_FIDELITY.md). Debug fixture screenshots use test metadata, a reference cover crop for the first track, and synthetic artwork for the other tracks; they do not show a third-party music service.

When a browser is selected under **Choose media app**, setup shows **Open URL (optional)**. Enter a web address (bare domains default to HTTPS), or clear it to launch the browser normally. The address is saved separately for each browser and used by the idle widget's Open App action in that browser, without redirecting into another installed app. Non-browser selections hide the setting. Active-session artwork/artist taps still use the media session's own destination. Browser detection uses generic web intent filters without host restrictions, not a list of browser names. Run `tools/Test-BrowserUrl.ps1` on the prepared emulator for UI and launch checks; the script restores the previous preferences.

For consolidated emulator regression, run tools/Test-AllEmulator.ps1 from the repository root. This exercises actual widget UI and real Android MediaSession callbacks, including marquee, browser URLs, adaptive colors, lifecycle recovery, and visual checks. See [coverage and explicit limits](tools/REGRESSION_COVERAGE.md); real YouTube integration and other launchers are not implied by a fixture pass.
