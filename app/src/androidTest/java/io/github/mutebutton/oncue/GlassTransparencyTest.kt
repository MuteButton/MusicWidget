package io.github.mutebutton.oncue

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlassTransparencyTest {
    @Test fun allRenderedGlassStatesHaveTransparentExterior() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun check(bitmap: Bitmap) {
            for (x in 0 until bitmap.width) {
                assertEquals(0, Color.alpha(bitmap.getPixel(x, 0)))
                assertEquals(0, Color.alpha(bitmap.getPixel(x, bitmap.height - 1)))
            }
            for (y in 0 until bitmap.height) {
                assertEquals(0, Color.alpha(bitmap.getPixel(0, y)))
                assertEquals(0, Color.alpha(bitmap.getPixel(bitmap.width - 1, y)))
            }
            assertEquals(0, Color.alpha(bitmap.getPixel(25, 25)))
            assertEquals(255, Color.alpha(bitmap.getPixel(514, 112)))
        }
        check(V5Artwork.idle(context, true))
        check(V5Artwork.idle(context, false))
        for (active in listOf(false, true)) for (enabled in 0..7) {
            val surface = V5Artwork.playbackSurface(context, active, enabled)
            check(surface)
            for (hue in listOf(0f, 120f, 240f)) {
                check(ArtworkColors.glass(surface, ArtworkColors.Palette(hue, .8f), enabled or if (active) 8 else 0))
            }
        }
    }
}
