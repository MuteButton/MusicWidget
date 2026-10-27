# OnCue 2.0

OnCue 2.0 promotes the approved glass widget to `main` and `release/2.0`.
It includes artwork-adaptive colors, live media controls and progress, automatic
long-title scrolling, browser/player routing, One UI sizing, equal skip-button
touch areas, the refined control inset, and the corrected idle rim.

The code matches the verified 1.2.4 beta candidate except for version metadata
and support links. Version code 16 permits existing OnCue testers to update.
The application ID and signing identity are unchanged.

## Release organization

- Repository: https://github.com/MuteButton/OnCue
- `main` and `release/2.0`: current OnCue 2.0, tag `oncue-v2.0.0`.
- `release/1.0`: preserved former main at `1731bfc`, tag `oncue-v1.0.0`.
  This historical source already declares version 1.0 and uses the original
  `com.example.mediawidget` application ID. It is a separate app from OnCue 2.0,
  not an in-place upgrade path. Its source has not been retroactively redesigned.
- Superseded GitHub release entries are removed after local archival. Their
  original Git tags remain historical references.

## Known limitations

Some touch widths remain below 48dp, widget text does not scale with system font
size, and complete TalkBack traversal/spoken output remains unverified. Samsung
Optimized battery mode can freeze updates; Unrestricted is recommended for
reliable live progress. These limitations are not removed by the version rename.

See the release assets for APK checksums and the final verification summary.
