# Samsung frozen widget reproduction — October 26, 2026

Status: OPEN. Optimized-mode functional sign-off withdrawn.

With the widget visible and YouTube Music reporting PLAYING at speed 1.0,
three passive physical screenshots showed elapsed 0:04 and remaining -3:51.
Session position extrapolated using device uptime was 11.51, 22.74 and 34.00
seconds respectively. The playback indicator remained Pause. The title animation
moved offscreen and its delayed reset did not execute during the samples.
Samsung Freecess logs explicitly report background freezing of OnCue's process.
Earlier process snapshots showing isFrozen=false did not rule out this separate
Samsung freezing mechanism. Charging and a bound notification listener did not
prevent the observed failure.

The current implementation depends on an app-process Handler for progress and
title-reset callbacks. Those callbacks cannot execute while the process is frozen.
A further periodic callback inside the same process cannot solve that condition.
The title grace-period change did not replace the existing scheduling architecture.

Brave captures earlier in this investigation reported PAUSED and subsequently its
session disappeared; those captures were excluded from advancing-timer assertions.
The successful reproduction above uses the independent YouTube Music session.

Evidence remains private under ignored artifacts/brave-return-investigation/.
No device identifiers or personal media titles are included in this report.
No timing-runtime workaround has been applied or pushed during this investigation.
