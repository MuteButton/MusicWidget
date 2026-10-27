package io.github.mutebutton.oncue

import org.junit.Assert.assertEquals
import org.junit.Test

class MarqueeTimingTest {
    @Test fun travelTimeScalesWithLengthNotSpeed() {
        assertEquals(10700L, MarqueeTiming.restoreDelay(300f))
        assertEquals(20700L, MarqueeTiming.restoreDelay(600f))
        assertEquals(700L, MarqueeTiming.restoreDelay(0f))
    }
}
