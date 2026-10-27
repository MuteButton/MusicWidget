package io.github.mutebutton.musicwidget

import org.junit.Assert.*
import org.junit.Test

class WidgetSizingTest {
    @Test fun spareHeightIsUsedWithoutExceedingTheRowOrDesignLimit() {
        assertEquals(96f, WidgetSizing.pillHeight(401f, 96f), .001f)
        assertEquals(96.96f, WidgetSizing.pillHeight(401.28f, 96.96f), .001f)
        for (width in listOf(250f, 320f, 401f, 500f)) for (height in listOf(40f, 64f, 96f, 200f)) {
            val base = WidgetSizing.pillWidth(width, height) * 225f / WidgetSizing.CANVAS_WIDTH
            val fitted = WidgetSizing.pillHeight(width, height)
            assertTrue(fitted <= height + .001f)
            assertTrue(fitted >= base - .001f)
            assertTrue(fitted <= base * WidgetSizing.MAX_ROW_HEIGHT / 225f + .001f)
        }
    }
    @Test fun existingLargeHostsKeepTheirOriginalWidth() {
        assertEquals(374f, WidgetSizing.pillWidth(374f, 111f), .001f)
    }

    @Test fun shortAndNarrowHostsContainTheWholePill() {
        for (width in listOf(250f, 320f, 374f, 500f)) {
            for (height in listOf(40f, 50f, 64f, 82f, 111f)) {
                val fitted = WidgetSizing.pillWidth(width, height)
                assertTrue(fitted <= width)
                assertTrue(fitted * 225f / WidgetSizing.CANVAS_WIDTH <= height + .001f)
                assertEquals(minOf(width, height * WidgetSizing.CANVAS_WIDTH / 225f), fitted, .001f)
            }
        }
    }
}
