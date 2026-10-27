package io.github.mutebutton.oncue

import org.junit.Assert.assertTrue
import org.junit.Test

class RowLayoutTest {
    @Test fun controlsRemainSeparatedFromEachOtherTextAndTimeline() {
        for (height in 225..WidgetSizing.MAX_ROW_HEIGHT) {
            val boxes = RowLayout.controls(height.toFloat())
            assertTrue(RowLayout.CONTENT_LEFT + RowLayout.TITLE_WIDTH < boxes.first().x)
            boxes.zipWithNext().forEach { (a, b) -> assertTrue(a.x + a.width < b.x) }
            boxes.forEach {
                assertTrue(it.y >= 0)
                assertTrue(it.x + it.width <= 1030)
                assertTrue(it.y + it.height < height - 73)
                assertTrue(it.width == it.height)
            }
            assertTrue(RowLayout.CONTENT_LEFT + RowLayout.PROGRESS_WIDTH <= 1030)
        }
    }
}
