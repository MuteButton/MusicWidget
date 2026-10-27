# Optimized-mode launcher-clock experiment — October 26, 2026

Historical experiment. Its build flag, runtime branches and experimental animation resources were removed after user review. The findings below are retained to explain why it was rejected.
No experimental changes have been pushed or released.

## Observed results

- Physical Samsung: Freecess froze the experimental app process, yet launcher
  timestamps advanced through 01:52, 02:01 and 02:10. The progress bar remained
  at the last app-delivered position. This verifies clocks surviving a process
  freeze, not the complete media UI remaining synchronized.
- Emulator: clocks advance with the application ticker disabled. Once Pause
  was confirmed delivered to the launcher, the elapsed clock stayed stopped.
  An earlier immediate pause assertion failed and was excluded as unsettled;
  the explicit state-wait follow-up passed.
- Emulator, no final player callback: a 4:42 track showed elapsed 04:47 and
  remaining --00:06, while bar description stayed 4:41 of 4:42. Native clocks
  alone do not clamp to media duration. This is a blocking defect, not a pass.
- Experimental title animation with fillAfter=false returns to its initial
  position inside the launcher even while OnCue is frozen. A Samsung capture
  after 24 seconds confirms the prefix is visible. It is clipped rather than
  ellipsized until the app can restore the static title, so fidelity is not final.
- Native clock formatting uses padded minutes (01:52). Non-1x playback retains
  the existing application ticker; it has not gained freeze resilience.

## Decision

Do not promote this prototype: continuous bar synchronization, duration clamping,
non-1x speed and exact title restoration remain unresolved. Sleep/wake and
return-from-app coverage for the prototype are not claimed from these tests.
A foreground-service experiment is a separate possible approach; it would need
an explicit ongoing notification, suitable service type, user-driven startup,
recovery handling and physical battery/functional validation. The user rejected an ongoing notification; do not implement that approach.

Evidence is private under ignored artifacts/host-clock-experiment/. Android's
public RemoteViews API supplies native clocks but no automatic binding between
those clocks and a ProgressBar:
https://developer.android.com/reference/android/widget/RemoteViews

The previous title-fix candidate is restored to the phone after this experiment;
battery mode remains unchanged. The experiment does not close the Samsung freeze
regression or restore the withdrawn Optimized-mode sign-off.
