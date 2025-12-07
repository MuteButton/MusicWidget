# Music Widget

An Android widget that displays currently playing media with album art and playback controls.

## Features

- 🎵 Displays current track info (title, artist, album art)
- 🎨 Dynamic background color extracted from album art
- ⏯️ Playback controls (play/pause, next, previous)
- 🖼️ Click album art to open the active media app
- 🌐 Opens YouTube Music in browser when no media is playing
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

- Android 8.0 (API 26) or higher
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
