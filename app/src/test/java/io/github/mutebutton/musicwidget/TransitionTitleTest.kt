package io.github.mutebutton.musicwidget

import org.junit.Assert.*
import org.junit.Test

class TransitionTitleTest {
    @Test fun transientHostHoldsThenAcceptsRealTitleImmediately() {
        val p = TransitionTitle()
        p.resolve("session", "First song", "Brave", 0)
        assertEquals(TransitionTitle.Result("First song", 1100), p.resolve("session", "m.youtube.com", "Brave", 100))
        assertEquals(TransitionTitle.Result("Next song", null), p.resolve("session", "Next song", "Brave", 200))
        assertEquals("Next song", p.resolve("session", "Next song", "Brave", 2000).title)
    }
    @Test fun repeatedCallbacksDoNotExtendDeadline() {
        val p = TransitionTitle()
        p.resolve(1, "First", "Browser", 0)
        p.resolve(1, "m.youtube.com", "Browser", 100)
        assertEquals(1100L, p.resolve(1, "", "Browser", 999).refreshAt)
        assertEquals(TransitionTitle.Result("YouTube", null), p.resolve(1, "m.youtube.com", "Browser", 1100))
        assertEquals("YouTube", p.resolve(1, "m.youtube.com", "Browser", 5000).title)
    }
    @Test fun differentSessionNeverInheritsTitle() {
        val p = TransitionTitle()
        p.resolve(1, "Private song", "Browser", 0)
        assertEquals("YouTube", p.resolve(2, "https://music.youtube.com/", "Browser", 100).title)
        assertEquals("Player", p.resolve(3, null, "Player", 200).title)
    }
    @Test fun resetAndLegitimateTitles() {
        val p = TransitionTitle()
        assertEquals("A song about youtube.com", p.resolve(1, "A song about youtube.com", "Browser", 0).title)
        p.reset()
        assertEquals("Browser", p.resolve(1, "", "Browser", 1).title)
    }
}
