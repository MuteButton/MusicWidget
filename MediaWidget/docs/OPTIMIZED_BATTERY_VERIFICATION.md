# Optimized battery verification — October 26, 2026

## Decision — reopened after physical regression

The earlier functional sign-off is withdrawn: subsequent physical testing found Samsung background-process freezes coinciding with stopped timestamp updates. Sleep/wake and return-from-app reliability remain open. The observations below are historical passes, not proof of sustained reliability.

Earlier assessment: functional sign-off for the tested OnCue 1.2.4-preview8 behavior on the Samsung
under Optimized battery mode, with user acceptance of the phone behavior.
No runtime changes were required for this investigation. This is a bounded
functional sign-off, not certification of every device or overnight battery drain.

Candidate commit: a266c6a (previously pushed).
APK SHA-256: 1427289a51081ecbd8769509eca235d0591af68c7001ed8090f64ed3df50942d.

## Physical Samsung

Optimized mode was selected and verified in Settings. While charging:

- YouTube Music: six playing samples over approximately 2.5 minutes; sampled
  elapsed and progress descriptions agreed, and interval progression differed
  from wall time by at most 0.89 seconds. Pause, resume, return from another app,
  and an earlier unlock recovery were observed successfully.
- Widget Next changed the actual media item and metadata; title tap opened the
  active player. A paused seek to 103,658ms produced elapsed 1:43 and a matching
  progress description.
- Brave: six samples advanced from 1:11 to 3:12 with matching progress
  descriptions and interval error below 0.89 seconds. Pause and fresh resume
  checks passed. With YouTube Music paused, the widget selected playing Brave.
  Subsequent-track metadata updated. Return from Settings was verified using a
  fresh screenshot and session snapshot, rather than the failed hierarchy capture.

The user was asked to perform a five-minute unplugged check including another
app and lock/unlock, and replied: "im satisified this works". This is recorded as
user acceptance, not as independently measured evidence that every requested
step or duration was completed. No instrumented unplugged trace was captured.

USB/AC stay-awake was enabled at the user's request during testing. The phone
reports AC charging on its cable connection. It is currently disconnected, so
restoring the original stay-awake setting remains pending when reconnected.

## Emulator: exact signed APK

- Core suite: 41 checks passed.
- Background suite: 55 checks passed in approximately 338 seconds, including
  three forced deep-Doze cycles, pause/resume recovery, injected process crash
  and listener rebind, explicit reopen after force-stop, and no-media recovery.
- Locked-screen suite: 5 checks passed, including low CPU while awake/locked and
  advancing timestamps after unlock.
- Continuous screen recording directly verified the playing-title marquee:
  sparse prefix moved left, distant dense suffix became visible, and the title
  returned to its initial ellipsized prefix while timestamps and progress advanced.
  Two prior screenshot-based suffix assertions failed. A timing diagnostic found
  a screenshot operation taking 15.63 seconds, invalidating that diagnostic's
  sampling window. The recording supplies separate visual evidence; the failed
  automated assertions are not relabeled as passes.
- Fifteen-second CPU observations: 2.10% of one core during screen-off Doze and
  0% during idle/no-media. No app-owned wake lock was observed in snapshots.
  Background-suite memory growth stayed within its 32 MiB threshold, including
  intentional process replacement. These bounded samples cannot prove absence
  of leaks or quantify overnight battery drain.

## Limitations and excluded results

- Forced standby did not make the bound-widget app inactive (bucket stayed
  ACTIVE); restrictive app-standby coverage is not claimed.
- Emulator Chrome first timed out loading the local page, then on retry suffered
  an input-dispatch ANR attributed to Chrome. That suite remains blocked; physical
  Brave supplied the separate real-browser playback coverage above.
- Failed UIAutomator captures and an initial stale-XML resume assertion were
  excluded. Fresh evidence was used for the physical pause/resume findings.
- No overnight, multi-day, unplugged battery-drain or physical process-kill
  certification; no guarantee for other OEMs, launcher versions, or players.
- The runtime gates periodic updates on screen availability, not actual launcher
  widget visibility. It can still update while another app covers the widget.
  Optimized-mode success here does not establish a visibility-only architecture.
- Previously documented accessibility findings remain separate open release
  concerns. This battery investigation does not close them.

Private evidence is stored only under ignored artifacts/optimized-regression/.
Phone media titles, captures, device identifiers and signing secrets are not
included here. No experimental runtime changes or new release were pushed.
