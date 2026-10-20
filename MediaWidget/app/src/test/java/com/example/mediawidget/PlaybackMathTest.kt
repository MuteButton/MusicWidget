package com.example.mediawidget

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackMathTest {
    @Test fun pausedPositionDoesNotDrift() = assertEquals(84_000, PlaybackMath.position(84_000, 1000, 9000, 1f, false, 282_000))
    @Test fun playingPositionUsesSpeedAndMonotonicTime() = assertEquals(100_000, PlaybackMath.position(84_000, 1000, 9000, 2f, true, 282_000))
    @Test fun positionIsClampedToDuration() = assertEquals(90_000, PlaybackMath.position(84_000, 1000, 9000, 1f, true, 90_000))
    @Test fun unknownAndFutureTimestampsDoNotJump() {
        assertEquals(84_000, PlaybackMath.position(84_000, 0, 9000, 1f, true, 282_000))
        assertEquals(84_000, PlaybackMath.position(84_000, 10_000, 9000, 1f, true, 282_000))
        assertEquals(0, PlaybackMath.position(-1, 1000, 9000, 1f, true, 282_000))
    }
    @Test fun formatsPodcastsAndTrackTimes() {
        assertEquals("1:24", PlaybackMath.time(84_000))
        assertEquals("1:01:01", PlaybackMath.time(3_661_000))
        assertEquals("0:00", PlaybackMath.time(-1))
    }
    @Test fun countdownKeepsTheLastPartialSecondVisible() {
        assertEquals("3:18", PlaybackMath.time(197_999, roundUp = true))
        assertEquals("0:01", PlaybackMath.time(1, roundUp = true))
        assertEquals("0:00", PlaybackMath.time(0, roundUp = true))
    }
}
