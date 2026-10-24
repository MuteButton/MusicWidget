package io.github.mutebutton.musicwidget

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Alpha outside the supplied 1030 x 225 glass rims; never changes interior RGB. */
internal object GlassSilhouette {
    fun alpha(x: Int, y: Int, playing: Boolean): Int {
        val top = if (playing) 3f else 12f
        val bottom = if (playing) 221f else 219f
        val radius = if (playing) 88f else 96f
        val qx = abs(x + .5f - 514f) - (502f - radius)
        val qy = abs(y + .5f - (top + bottom) / 2f) - ((bottom - top) / 2f - radius)
        val distance = hypot(maxOf(qx, 0f), maxOf(qy, 0f)) + minOf(maxOf(qx, qy), 0f) - radius
        // Keep the bright rim intact, then fade the narrow outer halo to zero.
        val t = (distance / 6f).coerceIn(0f, 1f)
        val halo = 1f - t * t * (3f - 2f * t)
        // The supplied crop cuts through the halo at its top/bottom. Fade those
        // last two pixels too, rather than exposing another rectangular seam.
        val edge = (minOf(x, 1029 - x, y, 224 - y) / 2f).coerceIn(0f, 1f)
        return (255f * halo * edge).roundToInt()
    }
}
