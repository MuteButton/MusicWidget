package io.github.mutebutton.musicwidget

/** Shared design coordinates for live views, raster controls and title measurement. */
internal object RowLayout {
    const val TITLE_SIZE = 34f
    const val TITLE_WIDTH = 328f
    const val CONTENT_LEFT = 310f
    const val PROGRESS_WIDTH = 684f
    data class Box(val x: Float, val y: Float, val width: Float, val height: Float)
    fun controls(height: Float): List<Box> {
        val centerY = height * .38f
        return listOf(Box(650f, centerY - 54f, 108f, 108f),
            Box(766f, centerY - 64f, 128f, 128f),
            Box(902f, centerY - 54f, 108f, 108f))
    }
}
