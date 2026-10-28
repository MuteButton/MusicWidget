# Current limitations

Applies to OnCue 2.0. See [release notes](RELEASE_2.0.md) and the
[development guide](DEVELOPMENT.md) for installation and test commands.

## Battery and synchronization

Samsung can freeze OnCue under Optimized battery mode, including after returning
from another app or unlocking. Unrestricted is recommended for reliable live
progress and marquee. It permits background execution, not unlimited resource use.
OnCue does not use an ongoing foreground-service notification.

Periodic progress work stops when the screen is unavailable, timing cannot
advance, or playback reaches an endpoint. Media callbacks refresh state promptly.
Artwork is cached and periodic updates avoid rebuilding the entire surface.
These measures do not establish a battery-drain guarantee. A standard widget
cannot reliably determine whether its home-screen page is visible.

A launcher-clock prototype was rejected: timestamps could advance while the app
was frozen, but overrun the duration while the progress bar stayed stale. It is
not shipped.

## Accessibility and layout

- Some transport touch widths remain below 48dp. Previous and Next use equal
  logical dimensions centered on the visible buttons, without overlapping actions.
- Full glass text retains its designed size rather than scaling with system fonts.
- Full-title labels and direct accessibility focus were checked. Complete TalkBack
  gesture traversal, spoken output and activation remain unverified.
- Live metadata, adaptive colors and launcher sizing are not pixel-identical to a
  static reference. Android 7–11 uses the compact/native layout; full glass requires
  Android 12 or newer.

## Verification scope

The final 4?1 signed 2.0 APK passed 41 core checks, 62 forced background/lifecycle
checks and 11 real-Chrome checks. Physical Samsung testing verified one-row
placement, YouTube Music pause/resume, timer progression, player routing,
return-home and unlock recovery. CI smoke checks cover API 24, 31 and 36.
The release verification attachment identifies the exact APK and focused-suite
results. These checks do not certify every device, launcher or player, full
TalkBack accessibility, or overnight physical-device battery consumption.

Controls, artwork, timing and browser destinations depend on information supplied
by the media player. A player may report incomplete or stale information.

Historical investigation reports remain in Git history. Private captures and
device logs stay outside tracked files. Signing recovery requires an encrypted,
owner-controlled off-device backup; another copy on the same PC is insufficient.
