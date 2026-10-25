package io.github.mutebutton.musicwidget

import org.junit.Assert.*
import org.junit.Test

class WidgetSizingTest {
    @Test fun existingLargeHostsKeepTheirOriginalWidth() {
        assertEquals(374f, WidgetSizing.pillWidth(374f, 111f), .001f)
    }

    @Test fun shortAndNarrowHostsContainTheWholePill() {
        for (width in listOf(250f, 320f, 374f, 500f)) {
            for (height in listOf(40f, 50f, 64f, 82f, 111f)) {
                val fitted = WidgetSizing.pillWidth(width, height)
                assertTrue(fitted <= width)
                assertTrue(fitted * 225f / 1030f <= height + .001f)
                assertEquals(minOf(width, height * 1030f / 225f), fitted, .001f)
            }
        }
    }
}
