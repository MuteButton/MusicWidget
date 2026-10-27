package io.github.mutebutton.musicwidget

/** Shared design coordinates for live views, raster controls and title measurement. */
internal object RowLayout {
    const val TITLE_SIZE = 34f
    // Small shared inset gives Next breathing room without changing button spacing.
    private const val CONTROL_INSET = 12f
    const val TITLE_WIDTH = 328f - CONTROL_INSET
    const val CONTENT_LEFT = 310f
    const val PROGRESS_WIDTH = 684f
    data class Box(val x: Float, val y: Float, val width: Float, val height: Float)
    fun controls(height: Float): List<Box> {
        val centerY = height * .38f
        return listOf(Box(650f - CONTROL_INSET, centerY - 54f, 108f, 108f),
            Box(766f - CONTROL_INSET, centerY - 64f, 128f, 128f),
            Box(902f - CONTROL_INSET, centerY - 54f, 108f, 108f))
    }

    /** Expand hit areas into spare space, never over text, neighbours or timeline. */
    fun touchControls(height: Float, unitsPerDp: Float): List<Box> {
        val visual = controls(height)
        val centers = visual.map { it.x + it.width / 2f }
        val edges = listOf(CONTENT_LEFT + TITLE_WIDTH,
            (visual[0].x + visual[0].width + visual[1].x) / 2f,
            (visual[1].x + visual[1].width + visual[2].x) / 2f, WidgetSizing.CANVAS_WIDTH)
        // Keep both skip targets equally sized and centered on their visible buttons.
        // The smaller symmetric clearance governs both, rather than clipping each side.
        val skipWidth = minOf(maxOf(visual[0].width, 48f * unitsPerDp),
            2f * minOf(centers[0] - edges[0], edges[1] - centers[0],
                centers[2] - edges[2], edges[3] - centers[2]))
        return visual.mapIndexed { index, box ->
            val targetWidth = if (index == 1) maxOf(box.width, 48f * unitsPerDp) else skipWidth
            val left = maxOf(edges[index], centers[index] - targetWidth / 2f)
            val right = minOf(edges[index + 1], centers[index] + targetWidth / 2f)
            val centerY = box.y + box.height / 2f
            val desiredHeight = maxOf(box.height, 48f * unitsPerDp)
            val targetHeight = if (index == 1) desiredHeight else
                minOf(desiredHeight, 2f * minOf(centerY, height - 73f - centerY))
            val top = maxOf(0f, centerY - targetHeight / 2f)
            val bottom = minOf(height - 73f, centerY + targetHeight / 2f)
            Box(left, top, right - left, bottom - top)
        }
    }
}
