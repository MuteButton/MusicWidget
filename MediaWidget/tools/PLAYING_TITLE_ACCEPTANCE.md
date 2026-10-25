# Playing-title and battery regression - OnCue 1.2.2

Local candidate only; not pushed or published.

APK: OnCue-1.2.2.apk, version code 5.
SHA-256: dc83661491aec53225e0e372ab663211a7d23c77676245e1678f8c38b04ae9ed

## Change and cause

Progress-only RemoteViews updates reapply the cached title actions. Recreating
the TextView resets native marquee; retaining the view with addStableView alone
also fails because reapplying setText restarts native marquee.

The Android 12+ layout now retains a stable title container and uses a one-shot
layout translation independent of TextView marquee. It holds briefly, slides
the full title over 16 seconds, and restores the static ellipsis. Progress and
timestamps continue throughout. Animation frames run in the launcher; OnCue
schedules only the existing start/restore callbacks. Title clicks retain the
active-player destination. The accepted geometry and one-row sizing are unchanged.

Periodic timer updates now also stop while keyguard is showing and at the
reported end of a track. ACTION_USER_PRESENT refreshes/resumes on unlock. The
last scheduled end-of-track update renders the final clamped timestamp before
stopping. Screen-off, paused, unknown-duration and no-widget suppression remain.

A standard AppWidgetProvider has no reliable callback for visibility of its
specific launcher page. Unlocked playback can still generate updates while
another app is foreground. No usage-history/accessibility permission, polling
of foreground apps, foreground service or wake lock was added. Samsung's
per-app Unrestricted battery exception remains necessary on the tested phone.

## Verified evidence

- Release APK/AAB build: PASS; 15 JVM tests; lint 0 errors, 72 existing warnings.
- Final signed APK core emulator suite: 36/36.
- Final signed APK marquee emulator suite: 13/13. Includes a new distant-suffix check during
  continuous playback, not just a difference between two nearby frames.
  Prefix/suffix glyph pixels were 348/1557, while elapsed time advanced.
  Also covers paused slide, seek, restoration, duplicate metadata, resume,
  title navigation, short titles, and cancellation on track change.
- Physical Samsung One UI: pre-unlock-correction candidate: a real mixed CJK/Latin title
  scrolled through its ending and restored without restarting; elapsed time
  advanced 4:40, 4:44, 4:48, 4:52, 4:56, 5:00, 5:04 during the captures.
  Widget retains semAppWidgetRowSpan=1. Stay-awake was confirmed restored after that capture. The final corrected APK was installed with matching hash; after the user locked/unlocked it, captures show 0:29, 0:35, 0:41, 0:47. The capture script restores stay-awake in finally; the phone disconnected before a separate final readback.
- Final APK background/lifecycle suite: 55/55 across three forced Doze cycles, process-crash recovery, force-stop/reopen and idle. Screen-off Doze and no-media samples observed no app-owned wake lock. Final PSS growth was 15,793 KiB, below the 32 MiB test threshold; this includes deliberate process replacement and does not prove absence of leaks.
- Awake lock screen: keyguard confirmed showing; 0% app CPU in a 15.17-second
  sample. Unlock immediately refreshed current time and the live timer resumed (5/5 checks on the final APK).

Evidence is under ignored artifacts/marquee-playing-fix/. Personal phone
screenshots and logs are local only and must not be committed or published.
The screenshot sequence is sampled visual verification, not frame-time profiling.

## Reproduction

Use a dedicated configured emulator and set ANDROID_SERIAL explicitly.
Run Test-ReleaseApk.ps1 with the exact APK, Test-WidgetMarquee.ps1,
Test-WidgetLocked.ps1 and Test-ReleaseBackground.ps1. The background and locked
suites reject physical devices and restore the temporary settings they change.

These results cover the named builds/scenarios, not every launcher or firmware,
overnight battery drain, or proof of absence of all memory leaks.
