# Nonfunctional verification

Scope: API 36.1 S25 Edge approximation emulator, debug APK SHA-256 `95bf5ffb2df2764a6dbcfaf4f9e4cb569974846929024e9b3f58e7f3c21811af`. Emulator CPU values are guest process CPU, not physical-device battery or latency claims.

## Repeatable profiling

Run `Test-WidgetNfr.ps1` on the dedicated emulator. It enables adaptive glass, warms all artwork/playback caches, measures process CPU over 20-second windows, and performs 30 cycles of track change, pause/play, missing/restored artwork, and creation/removal of a competing session. Every ten cycles it forces GC with Android's supported `am dumpheap -g`, saves the HPROF and meminfo, and records thread/file-descriptor counts. Preferences and wake state are restored afterward.

Screening budgets, chosen before the run: playing CPU <=5% of one guest core; paused, idle and screen-off CPU <=1%; post-warm-up Java+native allocation growth <=8 MiB, PSS growth <=24 MiB, thread increase <=3 and descriptor increase <=4. These are regression bounds, not product guarantees. Actual observations are reported alongside them.

`Summarize-AndroidHeap.cjs` reads Android HPROF instance records to compare surviving app services, media controllers, destinations and bitmaps. It is a retention trend tool, not a GC-root/dominator analyzer. It rejects unknown record formats rather than silently skipping them.

## Results

First run `artifacts/nfr-chrome-fix`: CPU 0.249% playing, 0% paused, 0% screen-off playback, 0% idle. Across 30 cycles, post-GC Java+native allocation grew 268 KiB, PSS fell 253 KiB, threads stayed 25 and descriptors stayed 112. One listener service survived throughout; controllers remained two during playback and fell to zero at idle; notification destinations remained one during playback and fell to zero at idle. Bitmap counts stabilized at 20 after the initial 19. No accumulating object/resource trend was observed.

The final UI assertion in that run checked the wrong launcher page after wake and failed. The measurements are retained; the command is not reported as wholly passing. Explicit page restoration was added, and `artifacts/nfr-visible-widget` is the corrected complete run: all 11 checks passed, exit code 0.

## Resource and safety review

- Media controller callbacks are unregistered when sessions disappear and on listener disconnect; session maps clear on disconnect.
- Progress callbacks are removed on disconnect and screen-off. Ticking requires an interactive display, known duration, advancing playback and at least one widget.
- Delayed marquee work is cancelled on session/track changes, widget removal and listener disconnect; emulator tests cover stale work after screen-off and session removal.
- Adaptive surfaces use a four-entry LRU; legacy glass uses an eight-entry LRU. Source artwork references are weak; derived covers are single cached outputs. Native v5 assets use a finite resource set and two idle states.
- Navigation destinations are keyed by notification and removed on notification removal/disconnect. Selection requires both package and exact media-session token; the app does not read arbitrary notification text to infer destinations.
- Production manifest has no Internet permission, the control receiver is not exported, and the notification service requires the platform notification-listener binding permission. Browser URLs are validated and dispatched to the selected browser. Debug playback fixtures are debug-source-only.
- Functional resilience, disabled actions, permission recovery, large-font layout and semantic control labels are exercised by the emulator suites. A complete TalkBack/contrast/localization audit is not claimed; the fixed reference design also constrains touch-target sizing.

## Limits

No finite run proves that memory leaks are impossible. These tests detect sustained retention/resource growth for the exercised scenarios. Physical Samsung battery/thermal behavior, overnight soak, other Android versions, and exhaustive accessibility/security assessment require separate environments and tests. Do not label those as passed based on this emulator run.

## Corrected complete run and responsiveness

In nfr-visible-widget, CPU was 0.348% playing and 0% paused, screen-off playing and idle. Post-GC Java allocation was 3,411 KiB at both baseline and cycle 30; native allocation was 26,659 -> 26,952 KiB (+293 KiB); PSS was 118,494 -> 119,393 KiB (+899 KiB). Threads stayed 25 and descriptors 112. Heap counts stabilized: listener services fell from a transient two at baseline to one; media controllers stayed two then fell to zero at idle; destinations stayed one then fell to zero; bitmaps stabilized at 20. No sustained retention growth was observed over the two 30-cycle runs.

Test-WidgetResponsiveness.ps1 passed in nfr-responsiveness. Ten actual Next taps reached the player callback in median 133.7 ms, maximum 271.2 ms, including ADB and polling overhead. This measures callback responsiveness, not physical display frame latency. Recorded process exit history shows expected test force-stops/SIGKILL; the stress process survived without restart.
