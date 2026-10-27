package io.github.mutebutton.musicwidget

import org.junit.Assert.*
import org.junit.Test

class TouchGeometryTest {
    @Test fun expandedControlsDoNotStealAdjacentContentAtSupportedSizes() {
        for (height in listOf(225f, 250f)) for (width in listOf(280f, 320f, 374f, 420f, 500f)) {
            val visual = RowLayout.controls(height)
            val targets = RowLayout.touchControls(height, WidgetSizing.CANVAS_WIDTH / width)
            assertEquals(targets[0].width, targets[2].width, .001f)
            assertEquals(targets[0].height, targets[2].height, .001f)
            for (i in listOf(0, 2)) {
                assertEquals(visual[i].x + visual[i].width / 2f,
                    targets[i].x + targets[i].width / 2f, .001f)
                assertEquals(visual[i].y + visual[i].height / 2f,
                    targets[i].y + targets[i].height / 2f, .001f)
            }
            targets.forEachIndexed { i, box ->
                assertTrue(box.x >= RowLayout.CONTENT_LEFT + RowLayout.TITLE_WIDTH)
                assertTrue(box.y >= 0)
                assertTrue(box.y + box.height <= height - 73f + .001f)
                assertTrue(box.width >= visual[i].width)
                assertTrue(box.height >= visual[i].height)
                assertTrue(box.x <= visual[i].x + visual[i].width / 2)
                assertTrue(box.x + box.width >= visual[i].x + visual[i].width / 2)
                if (i < 2) assertTrue(box.x + box.width <= targets[i + 1].x + .001f)
            }
        }
    }
}
