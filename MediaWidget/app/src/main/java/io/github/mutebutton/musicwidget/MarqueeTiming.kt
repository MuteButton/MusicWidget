package io.github.mutebutton.musicwidget

import kotlin.math.ceil

internal object MarqueeTiming {
    const val DP_PER_SECOND = 30f // Keep in sync with widget_title_slide.xml.
    fun restoreDelay(textWidthDp: Float): Long =
        600L + ceil(textWidthDp.coerceIn(0f, 30000f) / DP_PER_SECOND * 1000).toLong() + 100L
}
