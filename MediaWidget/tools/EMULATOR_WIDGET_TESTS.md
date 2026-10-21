# Debug media fixture and emulator checks

## Automatic title scrolling

`Test-WidgetMarquee.ps1` exercises the Android 12+ full layout on the actual launcher. It captures and hashes title pixels during and after automatic scrolling; checks one-pass restoration, no restart on duplicate metadata or resume, stationary short titles, title tap navigation, track-change cancellation, and advancing playback timestamps. Screenshots/results are written to `artifacts/auto-marquee-acceptance`. The native animation runs in the launcher, not in the app's tick handler. Run the core and extended suites as regression checks after changing title rendering.

`app/src/debug` is merged only into debug builds. It creates a real Android `MediaSession`, a media-style foreground notification, and silent looping audio. It does not require a music account or network. The first cover is the full supplied v5 artwork sprite, including its precomposed glass continuation; it is not an original clean album cover. Subsequent covers are generated square test artwork. Production rendering uses an aspect-preserving crop and real adjacent pixels for the fade. No test player or exported control entry point ships in release builds.

Use the installed SDK's `adb` and the emulator's serial. Examples below omit `-s emulator-5554` when only one device is connected.

```powershell
$adb = 'C:\Users\David\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$fixture = 'com.example.mediawidget/.debug.DebugMediaActivity'
& $adb shell am start -n $fixture --es command reset
& $adb shell input keyevent KEYCODE_HOME
& $adb shell run-as com.example.mediawidget cat files/debug-media-state.json
& $adb shell dumpsys media_session
```

The fixture starts on **Afterglow Ã¢â‚¬â€ The Midnight**, at approximately **1:24 / 4:42**. It intentionally includes silent media playback; hearing no sound is expected. State snapshots include transport callback counters, artwork state, advertised actions, and session status. Widget taps must increment the corresponding counters; this proves the widget used `MediaController.TransportControls`.

## ADB control

All commands use `adb shell am start -n com.example.mediawidget/.debug.DebugMediaActivity --es command COMMAND`. ADB setup commands immediately finish the fixture activity. Launching its session activity through the widget keeps the test-player screen visible, making open-player navigation verifiable. Add `--ez finish false` to keep a command's activity visible.

| Command | Effect |
| --- | --- |
| `reset` | Playing, artwork, full controls, Afterglow, counters cleared, 1:24 / 4:42 |
| `play`, `pause`, `next`, `previous` | Invoke actual session transport callbacks |
| `seek --el position 120000` | Seek through the transport callback to 2:00 |
| `no-art`, `art` | Remove/restore album artwork |
| `limited` | Advertise play/pause only; previous/next must be disabled |
| `unsupported` | Advertise no transport actions; no control should dispatch |
| `all-actions` | Restore full controls |
| `buffering`, `connecting` | Freeze elapsed time and advertise PAUSE only; widget must show enabled Pause and increment pauseCount when tapped |
| `near-end --el remaining 3500` | Play 3.5 seconds before duration; terminal display must settle at 4:42 / -0:00 without negative remaining time |
| `metadata --es title TITLE --es artist ARTIST --el duration 282000 --el position 84000` | Override metadata and position |
| `missing-metadata` | Publish a null metadata object to exercise fallbacks |
| `rapid` | Publish 24 artwork/metadata changes then settle on Afterglow with blue/purple artwork |
| `snapshot` | Write the current elapsed position and counters to the JSON state file |
| `stop` | Release/destroy the session, stop silent audio and remove notification |
| `show` | Open fixture screen without changing session state |
| `pin` | Request the launcher's native Add Widget confirmation (API 26+) |

For commands with extra parameters, place the parameters after the command, e.g.:

```powershell
& $adb shell am start -n $fixture --es command seek --el position 120000
& $adb shell am start -n $fixture --es command metadata --es title 'Long_Track_Name_For_Overflow_Testing' --es artist 'Long_Artist_Name_For_Overflow_Testing' --el duration 0
```

Read current state without opening another app:

```powershell
& $adb shell run-as com.example.mediawidget cat files/debug-media-state.json
& $adb logcat -d -s MWFixture:I MediaListenerService:E AndroidRuntime:E
```

After the initial `reset` Activity command starts the foreground fixture, subsequent commands can be sent without changing the foreground app:

```powershell
& $adb shell am broadcast -n com.example.mediawidget/.debug.DebugMediaReceiver --es command pause
& $adb shell am broadcast -n com.example.mediawidget/.debug.DebugMediaReceiver --es command seek --el position 120000
```

The receiver intentionally cannot start a stopped service from the background; start it through the Activity again after `stop`. These are debug-only exported controls.

`second-play`, `second-pause`, and `second-stop` create/control an independent second `MediaSession` in the same fixture process. It has distinct artwork, title, token, and transport counters (`secondPlayCount`, etc.). To test arbitration, start primary playback, send `second-play` (primary should remain selected while still playing), pause primary (second should become selected), pause second (keep second selected), then play primary (switch to primary). Tapping controls on the second widget session should change second counters only. Both sessions intentionally share a package; this verifies token-based selection rather than package-name comparisons.

## Acceptance checklist

1. Stop fixture. Verify empty state and its Open App action. With notification access disabled, verify permission/setup state and action. Restore permission and return home.
2. Reset fixture. Verify title, artist, rounded artwork, visible progress, elapsed/remaining labels and active pause icon. Add another widget while playing; it must populate immediately.
3. Tap widget Pause. `pauseCount` increases, `playing` becomes false, play icon appears, progress stops. Tap Play; `playCount` increases, playback/progress resume.
4. Tap Next then Previous. Verify titles change to Emerald Skies then Afterglow; respective counters increment. Tap artwork; fixture player opens.
5. Seek through fixture command to 2:00 without changing playing state. Verify elapsed/progress updates. Set duration 0 and verify safe unknown-duration rendering.
6. Set limited actions and verify previous/next are visibly disabled. Taps must leave next/previous counters unchanged. Set unsupported and verify play/pause is also disabled. Restore all-actions.
7. Remove artwork, then send rapid changes. Verify fallback artwork is clean, no previous artwork flashes after settling, and final track is Afterglow. Null metadata and long strings must not break layout.
8. Test compact and expanded launcher sizes (including minimum width) and a font scale of 1.3. Verify no overlapping controls/text. Restore emulator font scale after testing.
9. Pause fixture, wait, and ensure progress remains unchanged; turn display off/on and ensure rendering resumes correctly without unnecessary background widget updates.
10. Stop fixture. Verify empty state returns immediately. Check `AndroidRuntime`/widget logs for crashes, RemoteViews exceptions, and binder bitmap failures.

Keep screenshot names explicit about **debug fixture playback**; these are UI/functionality tests, not evidence of integration with Spotify, Samsung Music, or Samsung One UI.
