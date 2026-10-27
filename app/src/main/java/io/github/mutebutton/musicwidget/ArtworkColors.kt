package io.github.mutebutton.musicwidget

import android.graphics.Bitmap
import android.graphics.Color
import android.util.LruCache
import java.lang.ref.WeakReference
import kotlin.math.pow
import kotlin.math.roundToInt

/** Small artwork sample; recolor only the glass, never the cover or live white text. */
internal object ArtworkColors {
    const val PREFERENCE = "artwork_glass"
    data class Palette(val hue: Float, val saturation: Float) {
        val accent: Int get() = Color.HSVToColor(floatArrayOf(hue, .38f, 1f))
        val caption: Int get() = Color.HSVToColor(floatArrayOf(hue, .20f, .96f))
    }
    private var source = WeakReference<Bitmap>(null)
    private var generation = -1
    private var cached: Palette? = null
    private val surfaces = LruCache<String, Bitmap>(4)
    private val linear = FloatArray(256) { i ->
        val v = i / 255f
        if (v <= .04045f) v / 12.92f else ((v + .055f) / 1.055f).pow(2.4f)
    }
    private val encoded = IntArray(4097) { i ->
        val v = i / 4096f
        ((if (v <= .0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - .055f) * 255).roundToInt()
    }
    private fun luminance(color: Int) = .2126f * linear[Color.red(color)] +
        .7152f * linear[Color.green(color)] + .0722f * linear[Color.blue(color)]

    fun from(art: Bitmap?): Palette? {
        if (art == null || art.isRecycled) return null
        if (source.get() === art && generation == art.generationId) return cached
        val sample = Bitmap.createScaledBitmap(art, 48, 48, true)
        val pixels = IntArray(48 * 48)
        sample.getPixels(pixels, 0, 48, 0, 0, 48, 48)
        if (sample !== art) sample.recycle()
        val weights = FloatArray(24)
        val saturation = FloatArray(24)
        val hsv = FloatArray(3)
        for (pixel in pixels) {
            if (Color.alpha(pixel) < 192) continue
            Color.colorToHSV(pixel, hsv)
            // Ignore black borders, white lettering, and neutral covers.
            if (hsv[1] < .20f || hsv[2] < .25f) continue
            val bucket = (hsv[0] / 15f).toInt().coerceIn(0, 23)
            val weight = hsv[1] * hsv[2] * hsv[2]
            weights[bucket] += weight
            saturation[bucket] += weight * hsv[1]
        }
        val best = weights.indices.maxByOrNull { weights[it] } ?: 0
        cached = if (weights[best] < pixels.size * .015f) null
            else Palette(best * 15f + 7.5f, saturation[best] / weights[best])
        source = WeakReference(art)
        generation = art.generationId
        return cached
    }

    fun glass(base: Bitmap, palette: Palette, stateKey: Int): Bitmap {
        val key = "${base.generationId}:$stateKey:${palette.hue}:${palette.saturation}"
        surfaces.get(key)?.let { return it }
        val pixels = IntArray(base.width * base.height)
        base.getPixels(pixels, 0, base.width, 0, 0, base.width, base.height)
        val hsv = FloatArray(3)
        for (i in pixels.indices) {
            val original = pixels[i]
            Color.colorToHSV(original, hsv)
            // Keep neutral white reflections, glyphs, and dark exterior pixels intact.
            if (hsv[1] < .10f || hsv[2] < .08f) continue
            val offset = ((hsv[0] - 255f + 540f) % 360f - 180f).coerceIn(-45f, 45f)
            hsv[0] = (palette.hue + offset * .65f + 360f) % 360f
            hsv[1] = (hsv[1] * (.65f + palette.saturation * .25f)).coerceAtMost(.78f)
            // Green appears much brighter than blue at equal HSV value. Preserve
            // relative luminance so artwork hue cannot wash out white controls.
            val tinted = Color.HSVToColor(hsv)
            val ratio = luminance(original) / luminance(tinted).coerceAtLeast(.0001f)
            fun channel(value: Int) = encoded[(linear[value] * ratio * 4096).roundToInt().coerceIn(0, 4096)]
            pixels[i] = Color.argb(Color.alpha(original), channel(Color.red(tinted)),
                channel(Color.green(tinted)), channel(Color.blue(tinted)))
        }
        return Bitmap.createBitmap(pixels, base.width, base.height, Bitmap.Config.ARGB_8888)
            .also { surfaces.put(key, it) }
    }
}
