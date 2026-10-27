# Android 7 transparency regression

The android (24) job in run 40324481711 failed
`GlassTransparencyTest.allRenderedGlassStatesHaveTransparentExterior`: an exterior
pixel expected alpha 0 but had alpha 255. This is the test failure, independent
of the emulator shutdown warnings at the end of the job.

`V5Artwork.applySilhouette` now explicitly enables alpha before writing masked
pixels. An ARGB_8888 copy can retain the opaque bitmap flag; transparent pixel
values alone do not guarantee transparent drawing in that case. No assertion
was weakened and no API level was excluded.

Verification on October 26: release/debug/test APK builds, release lint and 24
unit tests passed. All four instrumentation tests passed on the local API 24
emulator, including the exact failing transparency test. Evidence is retained in
ignored `artifacts/api24-ci/final-instrumentation.txt` (OK, 4 tests).

Initial SDK emulator boots stalled because of the previously documented Windows
graphics-library problem. The isolated emulator configuration in
`tools/EMULATOR_RECOVERY.md` booted successfully. No system DLL was modified.
The correction remains local; a successful GitHub rerun is not yet claimed.