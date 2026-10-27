# OnCue 1.2.4 beta 1

This beta refines the approved One UI layout without changing the widget's core
playback behavior. Previous and Next have equal, centered touch areas; all three
controls sit slightly farther from the right rim. Title space decreases by 3.7%.
The idle glass now fills the left interior cleanly without the cyan crescent.
Full-title accessibility labels are retained and duplicate decorative focus
targets are removed.

Removed the obsolete square-cover/edge-blur calibration experiment and its
bitmap cache. Android 7–11 compact rendering is retained because Android 7
(API 24) remains the minimum supported OS. No historical-user migration is
required by this release, and no minimum-OS change is included.

## Installation

Install the signed `OnCue-1.2.4-beta1.apk` (version code 15). Existing testers
can update in place using the same signing identity. Enable notification access
and add the home-screen widget. On Samsung, Unrestricted battery mode is
recommended for reliable live progress and marquee; Optimized can freeze updates.

## Known limitations

- Some transport touch widths remain below 48dp to preserve the approved layout.
- Widget text does not enlarge with system font scaling.
- Full TalkBack gesture traversal and spoken-output verification remain open.
- Launcher and OEM behavior varies; this is a public beta, not universal-device
  or full accessibility certification.

## Verification

The signed APK is generated from the signed release bundle. Final APK SHA-256:
`66020491c77ba483f61137b05a8393a3bb8b8324868741703dd1a5a6747243b7`.

Release build, debug/test compilation and 25 JVM tests pass. Release lint reports
0 errors and 77 visible warnings (mostly Kotlin conveniences, compatibility
attributes and the documented layout/accessibility limitations). Repository
hygiene checks exclude credentials, personal home paths and generated artifacts.
Physical Samsung preview checks are documented in
[the layout review](ACCESSIBILITY_CONSERVATIVE_REVIEW.md). Final-APK device
regression and CI outcomes are recorded separately before publication.
