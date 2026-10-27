package io.github.mutebutton.musicwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassSilhouetteTest {
    @Test fun rectangleEdgesAndCornersAreTransparent() {
        for (playing in listOf(false, true)) {
            for (x in 0..1029) {
                assertEquals(0, GlassSilhouette.alpha(x, 0, playing))
                assertEquals(0, GlassSilhouette.alpha(x, 224, playing))
            }
            for (y in 0..224) {
                assertEquals(0, GlassSilhouette.alpha(0, y, playing))
                assertEquals(0, GlassSilhouette.alpha(1029, y, playing))
            }
            assertEquals(0, GlassSilhouette.alpha(25, 25, playing))
            assertEquals(255, GlassSilhouette.alpha(514, 112, playing))
            assertEquals(255, GlassSilhouette.alpha(13, 112, playing))
        }
    }

    @Test fun outerGlowFadesWithoutClippingTheRim() {
        for (playing in listOf(false, true)) {
            assertEquals(0, GlassSilhouette.alpha(5, 112, playing))
            val glow = GlassSilhouette.alpha(9, 112, playing)
            assertTrue(glow in 1..254)
            assertTrue(glow < GlassSilhouette.alpha(11, 112, playing))
            assertEquals(255, GlassSilhouette.alpha(12, 112, playing))
        }
    }
}
