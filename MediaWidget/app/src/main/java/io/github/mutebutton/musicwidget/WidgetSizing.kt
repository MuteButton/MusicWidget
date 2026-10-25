package io.github.mutebutton.musicwidget

/** Fit the approved 1030x225 artwork without cropping or changing its proportions. */
internal object WidgetSizing {
    fun pillWidth(widthDp: Float, heightDp: Float): Float =
        minOf(widthDp.coerceAtLeast(1f), heightDp.coerceAtLeast(1f) * 1030f / 225f)
}
