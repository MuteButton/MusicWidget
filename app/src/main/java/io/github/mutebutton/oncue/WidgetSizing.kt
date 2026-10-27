package io.github.mutebutton.oncue

/** Fit the row canvas; artwork and controls retain their own uniform scaling. */
internal object WidgetSizing {
    const val MAX_ROW_HEIGHT = 250
    // Visible horizontal extent including the six-pixel glass halo.
    const val CANVAS_WIDTH = 1016f
    /** Use spare row height conservatively; never exceed the launcher's bounds. */
    fun pillHeight(widthDp: Float, heightDp: Float): Float =
        minOf(heightDp.coerceAtLeast(1f), pillWidth(widthDp, heightDp) * MAX_ROW_HEIGHT / CANVAS_WIDTH)

    fun pillWidth(widthDp: Float, heightDp: Float): Float =
        minOf(widthDp.coerceAtLeast(1f), heightDp.coerceAtLeast(1f) * CANVAS_WIDTH / 225f)
}
