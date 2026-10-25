# Tap routing - OnCue 1.2.3

Local candidate; no push or publication.

APK SHA-256: 328f591731fc1c1246ee0226690c554da0ce229a3e8b95c3671389f25c39e8d5

Changes:
- Non-control pill space now has the same media destination as artwork/text.
  This includes timeline and timestamp taps. Playback buttons retain their actions.
- OPEN_PLAYER resolves the selected active session before any preferred player
  or saved browser URL. It tries session activity, notification destination,
  then the active package's launcher activity.
- If that active session has no usable destination, show an error and finish,
  rather than selecting a different app or leaving OnCue setup visible.
- Incoming OPEN_PLAYER intents and activity recreation are handled.
- Forwarded media PendingIntents explicitly opt into sender launch privileges on Android 14+; Android 16 uses ALLOW_IF_VISIBLE. The emulator caught and verified this requirement rather than treating send() returning as proof of launch.
- No battery architecture, geometry, artwork or typography changes.

The direct session PendingIntent path is retained to preserve player-provided
destinations, including browser media-tab restoration. No guarantee is made
that every player supplies an exact content/tab destination.

Build, 15 unit tests and release lint pass (0 errors, 72 existing warnings).
The final signed APK passed all 65 emulator checks:
- Core release suite: 41/41, including title, artist, artwork, progress bar,
  both timestamps, blank pill space, active-session fallback and transport controls.
- Browser/default routing: 13/13, including active-session priority over a saved
  Chrome URL and correct default browser/URL launch after the final session ends.
- Real Chrome: 11/11, including restoring the playing tab from an unrelated tab
  and restoring it after OnCue's process is stopped and reopened.
Final-APK emulator routing checks are recorded under ignored
artifacts/tap-routing-fix/. The Samsung subsequently connected and was updated
in place to version 1.2.3 (code 6). Its installed APK SHA-256 matches the tested
artifact above; USB stay-awake was confirmed at its original value 0.
Physical Samsung tap-behavior verification remains pending; installation and
artifact identity alone are not a functional test.
