# Conservative accessibility candidate ? October 27, 2026

Review candidate on experiment/accessibility-preserve-layout; not pushed.
Default raster layout, text sizes, positions, and colors remain unchanged.

Changes:
- Separate invisible transport hit rectangles from raster control geometry.
  Grow toward 48dp, bounded by the title edge, inter-button gaps, canvas and
  timeline. Geometry tests cover widths 280/320/374/420/500dp and heights 225/250
  design units, retaining original control centers and non-overlap.
- Mark duplicate root/state/text containers and artwork unimportant for
  accessibility without hiding descendants. Full title remains an actionable
  focus target, with an explicit open-player description. Artist, transport
  buttons and progress retain their accessibility nodes and actions.
- Test-only UI probe now preserves accessibility services during inspection and
  records importance and accessibility focus. Production app gains no permission.

Verification on API 36.1 launcher, density 420dpi:

| Target | Before pixels | After pixels | After dp |
| --- | --- | --- | --- |
| Previous | 104 x 104 | 119 x 126 | 45.3 x 48 |
| Play | 124 x 124 | 126 x 126 | 48 x 48 |
| Next | 104 x 104 | 114 x 126 | 43.4 x 48 |

The approved paused widget and settled candidate crops have exactly zero
changed RGB pixels. Initial capture during installation showed wallpaper and
was excluded; the recapture is the actual settled widget. Title/artwork bounds
remain unchanged. Evidence: ignored artifacts/accessibility-conservative/.

Release build and 25 JVM tests pass. All 41 signed-release acceptance checks pass.
Four focused device assertions pass: edge taps dispatch Previous/Play/Next once
and the long title's full open-player description is retained. The script is
Test-WidgetAccessibility.ps1; normal setup/fixture is required.

System font scale 150% and 200% was inspected. The widget retains its original
text sizing; the setting does not produce accessible enlarged text. This is an
OPEN limitation, not a passed large-font adaptation. No partial/capped font
scaling was introduced or presented as full support.

TalkBack was enabled on the emulator. Automated swipes navigated outside the
widget, so the run does NOT establish full focus order or spoken-label quality.
The node checks pass, but actual TalkBack traversal/activation remains OPEN.
The original font scale (1.0) and disabled TalkBack state were restored. The
Samsung was disconnected; no physical One UI verification is claimed here.

Recommendation: review the invisible targets and semantics as an incremental
improvement. Do not close the 48dp-all-targets, font scaling, or full TalkBack
findings. No visible redesign or extra app mode was introduced.


## Physical One UI and regression follow-up

The Samsung was subsequently reconnected. The same candidate APK was installed
and its SHA-256 verified against both Samsung and emulator installations. Actual
One UI bounds: glass stays [94,594][1347,897]; Previous expands from
[887,642][1020,775] to [878,633][1025,783]; Next expands from
[1197,642][1331,775] to [1193,633][1339,783]. Play is unchanged at
[1030,630][1188,788], already larger than 48dp at the phone's density.
Before/after screenshot differences were confined to the moving marquee area;
this physical pair is not claimed as a whole-widget zero-pixel comparison.

All five physical focused assertions pass: selected fixture, full-title label,
and newly added edge taps for Previous, Next and Pause. The test now verifies
source selection and checks Pause last: a competing Brave session kept reporting
PLAYING despite pause requests, so pausing the fixture legitimately switched to
Brave and invalidated earlier fixture-counter assertions. These setup failures
are retained in private evidence, not counted as passing app tests.

TalkBack was enabled on the actual Samsung. All six targets accepted direct
accessibility focus and exposed the expected labels: full title, artist, Previous,
Pause, Next and playback progress. Direct focus initially failed because the
probe's framework ID search did not resolve embedded widget nodes; recursive
node lookup fixed the test probe. Gesture/keyboard traversal attempts did not
establish sequential TalkBack navigation. Full spoken output, gesture traversal
and activation by a TalkBack user therefore remain OPEN. No full accessibility
certification is claimed. TalkBack, font scale and stay-awake were restored to
their original values after testing; the temporary player was stopped.

Timing recovery (13), title transition (9), and browser URL routing (13) passed
on the candidate emulator. Earlier reruns were interrupted by emulator shutdown
and a test-player foreground-service-start exception, not an OnCue exception.
Starting the test-player activity visibly resolved the service-start problem.
Core signed-release (41), focused emulator edge checks (4) and 25 JVM tests
remain the completed checks on the unchanged candidate. Evidence is under
ignored artifacts/accessibility-final/ and artifacts/accessibility-conservative/.

Final uninterrupted marquee rerun: all 13 assertions passed on the candidate.

## Equal skip-target refinement

Previous and Next now use the same logical width and height, centered on the
visible controls. Both use the smaller symmetric clearance so neither target
overlaps its neighbor, title or timeline. The earlier bounds above describe
the preceding candidate, not this refinement. Visible drawing is unchanged.

The refined release build and all 25 JVM tests pass, including equal dimensions,
center alignment and non-overlap across the supported test sizes. All five
focused Samsung assertions pass on the installed refined release APK.
Current Samsung bounds: Previous [882,633][1025,783], Next
[1193,633][1337,783], Play [1030,630][1188,788]. The skip targets are logically
equal; conversion to integer screen coordinates yields 143 versus 144 pixels
in width, with both 150 pixels high. This is rounding, not asymmetric geometry.

Before/after Samsung crops show zero changed pixels in either visible skip
button. The full-widget pair differs in the Play/Pause glyph during state
settling and is not claimed as pixel-identical. Private evidence and candidate
APK: artifacts/accessibility-equal/. This refinement remains unpushed; the
font scaling, minimum-width and full TalkBack limitations above still apply.

## Idle rim repair

Physical idle inspection exposed source-art residue between the legacy circular
backing repair and the current rounded rim. The idle row now fills its left
interior to the same contour used by live artwork before compositing the note.
Playing rendering and touch geometry are unchanged. The temporary backing is
recycled, and the completed surface uses the existing bounded cache.

Release build and 25 JVM tests pass. Installed Samsung before/after screenshots
confirm removal of the broad cyan crescent; differences are confined to the
left artwork region. The remainder of the widget is pixel-identical. Evidence:
artifacts/idle-rim-fix/comparison.json and after.png. An additional idle-button
check could not run because the phone had locked; this is not a passing action
test. No phone settings were changed. Candidate remains unpushed.

## Modest transport inset preview

Transport controls now move left together by 12 design units; visible sizes and
inter-button spacing are unchanged. Title/artist width decreases from 328 to
316 units (3.7%) so text cannot overlap the controls. Marquee measurement uses
the same shared width. Timeline, timestamps, artwork and idle layout do not move.

Release build and geometry/unit checks pass. Installed Samsung bounds confirm
approximately 15 physical pixels of leftward movement: Previous
[867,633][1011,783], Play [1015,630][1173,788], Next [1178,633][1322,783].
Title viewport is [467,654][857,708], previously ending at x=872. Screenshot
review confirms the added right-side breathing room. Evidence and APK:
artifacts/controls-inset/. This is a visual preview pending user review, not pushed.
