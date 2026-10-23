package io.github.mutebutton.musicwidget

import android.graphics.*
import android.util.LruCache

/** Locally rendered glass layers. No wallpaper capture, screenshot surface, or per-tick rendering. */
internal object GlassArtwork {
    private val cache = LruCache<String, Bitmap>(8)
    private fun color(value: String) = Color.parseColor(value)

    fun surface(width: Int, height: Int, idle: Boolean): Bitmap {
        val boundedWidth = width.coerceIn(320, 1400)
        val boundedHeight = height.coerceIn(80, 600)
        val key = "surface:$boundedWidth:$boundedHeight:$idle"
        cache.get(key)?.let { return it }
        val h = if (idle) 235f else 257f
        val bitmap = Bitmap.createBitmap(boundedWidth, boundedHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(boundedWidth / 865f, boundedHeight / h)
        val rect = RectF(1f, 1f, 864f, h - 1)
        val radius = 107f
        val path = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.save()
        canvas.clipPath(path)
        paint.shader = LinearGradient(0f, 0f, 865f, h * .65f,
            if (idle) intArrayOf(color("#719DD8"), color("#314079"), color("#644A76"))
            else intArrayOf(color("#4465A9"), color("#202D50"), color("#55416B")),
            floatArrayOf(0f, .53f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)
        fun wash(x: Float, y: Float, r: Float, tint: String, verticalScale: Float = 1f) {
            paint.shader = RadialGradient(x, y, r, color(tint), Color.TRANSPARENT, Shader.TileMode.CLAMP).apply { setLocalMatrix(Matrix().apply { setScale(1f, verticalScale, x, y) }) }
            canvas.drawPath(path, paint)
        }
        if (idle) {
            wash(100f, -30f, 800f, "#E675B0FF", .3f)
            wash(20f, -10f, 230f, "#BFDEFFFF", .4f)
        } else {
            wash(100f, -30f, 480f, "#BF7CA9FF", .45f)
        }
        wash(820f, 10f, 250f, if (idle) "#70C871E0" else "#90D481CD", .65f)
        wash(150f, h + 20f, 315f, "#9A7186FF", .45f)
        wash(810f, h + 10f, 180f, "#997C9DFA", .6f)
        wash(600f, h + 65f, 290f, "#735B47BB", .5f)
        // Blur a colored stroke into the surface to give the rim depth, rather than a flat outline.
        paint.shader = LinearGradient(0f, h * .35f, 865f, h * .65f,
            intArrayOf(color("#99E5FF"), color("#6489F4"), color("#B874F2"), color("#F5B3F4")),
            floatArrayOf(0f, .38f, .68f, 1f), Shader.TileMode.CLAMP)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 13f
        paint.maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)
        paint.alpha = 220
        canvas.drawPath(path, paint)
        paint.strokeWidth = 5f
        paint.maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
        paint.alpha = 235
        canvas.drawPath(path, paint)
        // Idle's lower corners have different lighting from its upper rim.
        if (idle) {
            paint.alpha = 255
            paint.strokeWidth = 14f
            paint.maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
            paint.shader = RadialGradient(100f, h - 10f, 230f, color("#B8717FFF"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawPath(path, paint)
            paint.shader = RadialGradient(865f, h, 180f, color("#FF514E90"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawPath(path, paint)
        }
        paint.maskFilter = null
        paint.alpha = 255
        paint.strokeWidth = if (idle) 3.2f else 1.7f
        paint.shader = LinearGradient(0f, 0f, 865f, h,
            intArrayOf(color("#EEFCFF"), color("#99BEFF"), color("#CDAAFF"), color("#FCE1FF")),
            floatArrayOf(0f, .4f, .67f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)
        canvas.restore()
        cache.put(key, bitmap)
        return bitmap
    }

    fun control(primary: Boolean, next: Boolean): Bitmap {
        val key = "control:$primary:$next"
        cache.get(key)?.let { return it }
        val bitmap = Bitmap.createBitmap(138, 144, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = if (primary) 64f else 50f
        val rect = RectF(69f-radius, 72f-radius, 69f+radius, 72f+radius)
        if (primary) {
            paint.color = color("#BFAD80FF")
            paint.maskFilter = BlurMaskFilter(9f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawOval(rect, paint)
            paint.maskFilter = null
        }
        paint.shader = if (primary) LinearGradient(15f, 125f, 120f, 20f,
            intArrayOf(color("#659FF6"), color("#7265C6"), color("#D785DE")), null, Shader.TileMode.CLAMP)
        else LinearGradient(20f, 20f, 110f, 125f,
            color(if (next) "#997A519D" else "#99748DBD"), color(if (next) "#99784F8E" else "#99424E75"), Shader.TileMode.CLAMP)
        canvas.drawOval(rect, paint)
        if (primary) {
            canvas.save()
            canvas.clipPath(Path().apply { addOval(rect, Path.Direction.CW) })
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 8f
            paint.maskFilter = BlurMaskFilter(7f, BlurMaskFilter.Blur.NORMAL)
            paint.shader = LinearGradient(20f, 120f, 110f, 20f, color("#C9F8FF"), color("#FFC0FA"), Shader.TileMode.CLAMP)
            canvas.drawOval(rect, paint)
            canvas.restore()
        }
        paint.maskFilter = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (primary) 2f else 1.4f
        paint.shader = LinearGradient(20f, 120f, 120f, 20f,
            color(if (primary) "#D5FAFF" else "#556A86AF"), color(if (primary) "#FFD0FC" else "#668C8CC2"), Shader.TileMode.CLAMP)
        canvas.drawOval(rect, paint)
        cache.put(key, bitmap)
        return bitmap
    }

    fun idleOrb(context: android.content.Context): Bitmap {
        cache.get("orb")?.let { return it }
        val bitmap = Bitmap.createBitmap(216, 216, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rect = RectF(2f, 2f, 214f, 214f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(30f, 5f, 160f, 216f,
                intArrayOf(color("#ABB7F3"), color("#687EBE"), color("#384E80")), null, Shader.TileMode.CLAMP)
        }
        canvas.drawOval(rect, paint)
        canvas.save()
        canvas.clipPath(Path().apply { addOval(rect, Path.Direction.CW) })
        paint.shader = LinearGradient(20f, 160f, 180f, 40f,
            intArrayOf(color("#FBA4FF"), color("#A9CDFF"), color("#CCBBFF")), null, Shader.TileMode.CLAMP)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 10f
        paint.maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawOval(rect, paint)
        paint.maskFilter = null
        paint.strokeWidth = 1.8f
        canvas.drawOval(rect, paint)
        canvas.restore()
        context.getDrawable(R.drawable.ic_widget_music)?.apply {
            setBounds(61, 53, 155, 155)
            draw(canvas)
        }
        cache.put("orb", bitmap)
        return bitmap
    }

    fun openButton(): Bitmap {
        cache.get("open")?.let { return it }
        val bitmap = Bitmap.createBitmap(315, 120, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rect = RectF(1f, 1f, 314f, 119f)
        val path = Path().apply { addRoundRect(rect, 59f, 59f, Path.Direction.CW) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 315f, 100f,
                intArrayOf(color("#456DD0"), color("#503788"), color("#B261AF")), null, Shader.TileMode.CLAMP)
        }
        canvas.drawPath(path, paint)
        canvas.save()
        canvas.clipPath(path)
        paint.style = Paint.Style.STROKE
        paint.shader = LinearGradient(0f, 120f, 315f, 0f, color("#779CFF"), color("#FFB3F8"), Shader.TileMode.CLAMP)
        paint.strokeWidth = 10f
        paint.maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(path, paint)
        paint.maskFilter = null
        paint.strokeWidth = 1.5f
        canvas.drawPath(path, paint)
        canvas.restore()
        cache.put("open", bitmap)
        return bitmap
    }
}
