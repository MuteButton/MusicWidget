package io.github.mutebutton.musicwidget

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.annotation.RequiresApi

/** Supplied v5 raster skins; metadata, artwork and transport state remain live. */
internal object V5Artwork {
    private val assets = mutableMapOf<Int, Bitmap>()
    private val surfaces = mutableMapOf<Boolean, Bitmap>()
    private var artSource = java.lang.ref.WeakReference<Bitmap>(null)
    private var artGeneration = 0
    private var artTransition = -1
    private var artBlur = -1
    private var artFadeStart = -1
    private var artMirror = false
    private var artExtent = -1
    const val ART_EXTENT = 280
    private var fadedArt: Bitmap? = null
    private var playingSkin: Bitmap? = null
    private var playSkin: Bitmap? = null
    private var missingCover: Bitmap? = null
    private var playbackSurface: Bitmap? = null
    private var playbackSurfaceKey = -1
    private var expandedSource = java.lang.ref.WeakReference<Bitmap>(null)
    private var expandedGeneration = -1
    private var expandedCover: Bitmap? = null
    private var expandedHeight = 225
    private val rowSurfaces = android.util.LruCache<String, Bitmap>(4)
    private val rowButtons = mutableMapOf<Int, Bitmap>()
    private fun asset(context: Context, id: Int) = assets.getOrPut(id) {
        BitmapFactory.decodeResource(context.resources, id, BitmapFactory.Options().apply { inScaled = false })
    }

    fun idle(context: Context, connected: Boolean): Bitmap = surfaces.getOrPut(connected) {
        if (!connected) {
            // Use the fully restored shell, then remove only its static captions.
            // The supplied bare underpaint has visible polygonal retouch scars.
            idle(context, true).copy(Bitmap.Config.ARGB_8888, true).also {
                interpolateRows(it, 298, 77, 640, 118)
                interpolateRows(it, 298, 125, 640, 157)
                interpolateRows(it, 772, 101, 901, 142)
            }
        } else {
            asset(context, R.drawable.v5_idle_glass_underlay).copy(Bitmap.Config.ARGB_8888, true).also {
                val c = Canvas(it)
                c.drawBitmap(asset(context, R.drawable.v5_idle_note), 104f, 68f, null)
                c.drawBitmap(asset(context, R.drawable.v5_idle_title), 298f, 62f, null)
                c.drawBitmap(asset(context, R.drawable.v5_idle_subtitle), 298f, 119f, null)
                c.drawBitmap(asset(context, R.drawable.v5_idle_open_app_cta), 722f, 54f, null)
                applySilhouette(it, playing = false)
            }
        }
    }

    private fun applySilhouette(bitmap: Bitmap, playing: Boolean) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val i = y * bitmap.width + x
            val alpha = Color.alpha(pixels[i]) * GlassSilhouette.alpha(x, y, playing) / 255
            pixels[i] = if (alpha == 0) Color.TRANSPARENT else
                (pixels[i] and 0x00ffffff) or (alpha shl 24)
        }
        bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private fun interpolateRows(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int) {
        for (x in left until right) {
            val a=bitmap.getPixel(x,top-1); val b=bitmap.getPixel(x,bottom)
            for (y in top until bottom) {
                val t=(y-top+1f)/(bottom-top+1f)
                fun mix(first: Int,last: Int)=(first+(last-first)*t).toInt()
                bitmap.setPixel(x,y,Color.rgb(mix(Color.red(a),Color.red(b)),mix(Color.green(a),Color.green(b)),mix(Color.blue(a),Color.blue(b))))
            }
        }
    }

    fun placeholder(context: Context, height: Int = 225): Bitmap {
        val source=missingCover ?: Bitmap.createBitmap(307,225,Bitmap.Config.ARGB_8888).also {
            val canvas=Canvas(it)
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader=LinearGradient(0f,0f,215f,215f,0xff183978.toInt(),0xff35315e.toInt(),Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f,0f,307f,225f,paint)
            context.getDrawable(R.drawable.ic_widget_music)?.mutate()?.let { note ->
                note.setBounds(61,58,151,153); note.draw(canvas)
            }
            missingCover=it
        }
        return expandedArtwork(source, height)
    }

    /** Preserve approved rim pixels, remove sample metadata before drawing live views.
     * The supplied underpaint has polygonal repair scars, so interpolate the clean
     * rows immediately above/below each dynamic region instead. This is approximate.
     */
    private fun playing(context: Context): Bitmap {
        playingSkin?.let { return it }
        val bitmap = asset(context, R.drawable.v5_playing_skin_source).copy(Bitmap.Config.ARGB_8888, true)
        fun repair(left: Int, top: Int, right: Int, bottom: Int) {
            for (x in left until right) {
                val a = bitmap.getPixel(x, top - 1)
                val b = bitmap.getPixel(x, bottom)
                for (y in top until bottom) {
                    val t = (y - top + 1f) / (bottom - top + 1f)
                    fun channel(first: Int, last: Int) = (first + (last - first) * t).toInt()
                    bitmap.setPixel(x, y, Color.rgb(channel(Color.red(a), Color.red(b)),
                        channel(Color.green(a), Color.green(b)), channel(Color.blue(a), Color.blue(b))))
                }
            }
        }
        repair(307, 48, 610, 86)
        repair(307, 90, 610, 126)
        // Horizontal samples avoid smearing the pause button's bright lower rim
        // down through the progress track.
        for (y in 147 until 162) {
            val a = bitmap.getPixel(300, y)
            val b = bitmap.getPixel(984, y)
            for (x in 303 until 976) {
                val t = (x - 300) / 684f
                fun mix(first: Int, last: Int) = (first + (last - first) * t).toInt()
                bitmap.setPixel(x, y, Color.rgb(mix(Color.red(a), Color.red(b)), mix(Color.green(a), Color.green(b)), mix(Color.blue(a), Color.blue(b))))
            }
        }
        repair(307, 171, 416, 198)
        repair(893, 171, 982, 198)
        repair(650, 40, 752, 144)
        repair(754, 32, 875, 146)
        repair(877, 41, 971, 144)
        // Join the live artwork backing to the exact adjacent glass color on
        // every row, avoiding a vertical divider at the end of the artwork.
        for (y in 7 until 218) {
            val edge = bitmap.getPixel(307, y)
            for (x in 16 until 307) {
                val dx = minOf(x - 116, 0)
                val dy = y - 112
                if (dx * dx + dy * dy > 104 * 104) continue
                val t = x / 307f
                fun mix(first: Int, last: Int) = (first + (last - first) * t).toInt()
                bitmap.setPixel(x, y, Color.rgb(mix(23, Color.red(edge)), mix(66, Color.green(edge)), mix(130, Color.blue(edge))))
            }
        }
        playingSkin = bitmap
        return bitmap
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun bind(context: Context, views: RemoteViews, widthDp: Float, hasMedia: Boolean, connected: Boolean,
             active: Boolean = true, enabledMask: Int = 7, palette: ArtworkColors.Palette? = null,
             heightDp: Float = widthDp * 225f / 1030f, canvasWidth: Float = WidgetSizing.CANVAS_WIDTH) {
        val scale = widthDp * context.resources.displayMetrics.density / canvasWidth
        val rowHeight = kotlin.math.round(heightDp * canvasWidth / widthDp).toInt().coerceIn(225, WidgetSizing.MAX_ROW_HEIGHT)
        val verticalScale = scale
        fun size(id: Int, width: Float, height: Float) {
            views.setViewLayoutWidth(id, width * scale, TypedValue.COMPLEX_UNIT_PX)
            views.setViewLayoutHeight(id, height * verticalScale, TypedValue.COMPLEX_UNIT_PX)
        }
        fun place(id: Int, x: Float, y: Float, width: Float, height: Float) {
            size(id, width, height)
            views.setViewLayoutMargin(id, RemoteViews.MARGIN_LEFT, x * scale, TypedValue.COMPLEX_UNIT_PX)
            views.setViewLayoutMargin(id, RemoteViews.MARGIN_TOP, y * verticalScale, TypedValue.COMPLEX_UNIT_PX)
        }
        listOf(R.id.glass_surface, R.id.idle_state, R.id.media_state).forEach { size(it, 1030f, rowHeight.toFloat()) }
        val base = rowSurface(context, rowHeight, hasMedia, active, enabledMask)
        views.setImageViewBitmap(R.id.glass_surface, if (hasMedia && palette != null)
            ArtworkColors.glass(base, palette, (enabledMask and 7) or (if (active) 8 else 0)) else base)
        views.setTextColor(R.id.track_artist, palette?.caption ?: 0xff9897ff.toInt())
        views.setColorStateList(R.id.progress_bar, "setProgressTintList",
            palette?.let { android.content.res.ColorStateList.valueOf(it.accent) })
        views.setColorStateList(R.id.progress_bar, "setProgressBackgroundTintList",
            palette?.let { android.content.res.ColorStateList.valueOf((it.accent and 0x00ffffff) or 0x50000000) })
        place(R.id.idle_title, 298f, rowHeight / 2f - 42f, 410f, 44f)
        place(R.id.idle_subtitle, 298f, rowHeight / 2f + 5f, 410f, 40f)
        place(R.id.btn_open, 740f, rowHeight / 2f - 48f, 240f, 96f)
        for (id in listOf(R.id.idle_title, R.id.idle_subtitle, R.id.btn_open)) {
            views.setTextColor(id, Color.WHITE)
        }
        if (!connected) {
            views.setTextColor(R.id.idle_subtitle,0xff87b5ff.toInt())
        }
        views.setViewPadding(R.id.btn_open,0,0,0,0)
        place(R.id.album_art, 0f, 0f, 307f, rowHeight.toFloat())
        place(R.id.text_info, RowLayout.CONTENT_LEFT, rowHeight * .20f, RowLayout.TITLE_WIDTH, 90f)
        place(R.id.title_container, 0f, 0f, RowLayout.TITLE_WIDTH, 44f)
        place(R.id.track_artist, 0f, 46f, RowLayout.TITLE_WIDTH, 40f)
        listOf(R.id.btn_prev, R.id.btn_play, R.id.btn_next).zip(RowLayout.controls(rowHeight.toFloat())).forEach { (id, box) ->
            place(id, box.x, box.y, box.width, box.height)
        }
        place(R.id.progress_container, RowLayout.CONTENT_LEFT, rowHeight - 73f, RowLayout.PROGRESS_WIDTH, 61f)
        size(R.id.progress_bar, RowLayout.PROGRESS_WIDTH, 11f)
        size(R.id.time_row, RowLayout.PROGRESS_WIDTH, 37f)
        // The rim curves inward at the timestamps' baseline. Keep the remaining
        // label inside that curve without shortening the progress track.
        views.setViewPadding(R.id.time_row, 0, 0, kotlin.math.round(24f * scale).toInt(), 0)
        views.setViewLayoutMargin(R.id.time_row, RemoteViews.MARGIN_TOP, 12f * scale, TypedValue.COMPLEX_UNIT_PX)
        // The stable nested title owns its text and size. Parent-level title
        // actions are replayed with progress updates and disturb its animation.
        views.setTextViewTextSize(R.id.track_artist, TypedValue.COMPLEX_UNIT_PX, 26f * scale)
        views.setTextViewTextSize(R.id.elapsed_time, TypedValue.COMPLEX_UNIT_PX, 21f * scale)
        views.setTextViewTextSize(R.id.remaining_time, TypedValue.COMPLEX_UNIT_PX, 21f * scale)
        views.setTextViewTextSize(R.id.idle_title, TypedValue.COMPLEX_UNIT_PX, 34f * scale)
        views.setTextViewTextSize(R.id.idle_subtitle, TypedValue.COMPLEX_UNIT_PX, 26f * scale)
        views.setTextViewTextSize(R.id.btn_open, TypedValue.COMPLEX_UNIT_PX, 29f * scale)
        // Visuals share one scaling operation; these remain independent live hit targets.
        for (id in listOf(R.id.btn_prev,R.id.btn_play,R.id.btn_next)) views.setImageViewResource(id,0)
    }

    private fun rowSurface(context: Context, height: Int, media: Boolean, active: Boolean, enabledMask: Int): Bitmap {
        val key = "$height:$media:$active:$enabledMask"
        rowSurfaces.get(key)?.let { return it }
        // Resize only the glass shell. Composite controls afterward with uniform
        // scaling so the extra row height never stretches their circles or glyphs.
        val shell = playing(context).copy(Bitmap.Config.ARGB_8888, true)
        // Soften the old raster repairs before relocating controls. Blend only
        // the interior; the original rim and outer transparency remain exact.
        val stride = shell.width + 1
        val sums = Array(3) { LongArray(stride * (shell.height + 1)) }
        for (y in 0 until shell.height) for (x in 0 until shell.width) {
            val color = shell.getPixel(x, y)
            val values = intArrayOf(Color.red(color), Color.green(color), Color.blue(color))
            val i = (y + 1) * stride + x + 1
            for (c in 0..2) sums[c][i] = values[c] + sums[c][i - 1] + sums[c][i - stride] - sums[c][i - stride - 1]
        }
        for (y in 25 until 205) for (x in 300 until 990) {
            val blend = minOf(1f, (x - 300) / 24f, (990 - x) / 24f, (y - 25) / 20f, (205 - y) / 20f)
            val x0 = x - 18; val x1 = minOf(shell.width, x + 19)
            val y0 = maxOf(0, y - 18); val y1 = minOf(shell.height, y + 19)
            val color = shell.getPixel(x, y)
            val original = intArrayOf(Color.red(color), Color.green(color), Color.blue(color))
            val channels = IntArray(3) { c ->
                val sum = sums[c][y1 * stride + x1] - sums[c][y0 * stride + x1] -
                    sums[c][y1 * stride + x0] + sums[c][y0 * stride + x0]
                (original[c] * (1 - blend) + sum.toFloat() / ((x1 - x0) * (y1 - y0)) * blend).toInt().coerceIn(0, 255)
            }
            shell.setPixel(x, y, Color.rgb(channels[0], channels[1], channels[2]))
        }
        applySilhouette(shell, playing = true)
        val result = Bitmap.createBitmap(1030, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(shell, null, RectF(0f, 0f, 1030f, height.toFloat()), paint)
        shell.recycle()
        fun draw(image: Bitmap, box: RowLayout.Box, enabled: Boolean = true) {
            val factor = minOf(box.width / image.width, box.height / image.height)
            val w = image.width * factor
            val h = image.height * factor
            val left = box.x + (box.width - w) / 2f
            val top = box.y + (box.height - h) / 2f
            paint.alpha = if (enabled) 255 else 80
            canvas.drawBitmap(image, null, RectF(left, top, left + w, top + h), paint)
        }
        if (media) {
            val boxes = RowLayout.controls(height.toFloat())
            draw(rowButton(context, 0, active), boxes[0], enabledMask and 1 != 0)
            draw(rowButton(context, 1, active), boxes[1], enabledMask and 2 != 0)
            draw(rowButton(context, 2, active), boxes[2], enabledMask and 4 != 0)
        } else {
            paint.alpha = 255
            paint.shader = LinearGradient(60f, 40f, 230f, height - 35f,
                0xaa8abaff.toInt(), 0x997b48d0.toInt(), Shader.TileMode.CLAMP)
            canvas.drawCircle(155f, height / 2f, 83f, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = 0xffadbeff.toInt()
            canvas.drawCircle(155f, height / 2f, 83f, paint)
            paint.style = Paint.Style.FILL
            context.getDrawable(R.drawable.ic_widget_music)?.mutate()?.let {
                it.setTint(Color.WHITE)
                it.setBounds(112, height / 2 - 43, 198, height / 2 + 43)
                it.draw(canvas)
            }
            paint.color = 0x776b6dcd
            canvas.drawRoundRect(RectF(740f, height / 2f - 48f, 980f, height / 2f + 48f), 48f, 48f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = 0xffb9a2fa.toInt()
            canvas.drawRoundRect(RectF(740f, height / 2f - 48f, 980f, height / 2f + 48f), 48f, 48f, paint)
        }
        rowSurfaces.put(key, result)
        return result
    }

    private fun rowButton(context: Context, index: Int, active: Boolean): Bitmap {
        val key = if (index == 1 && !active) 3 else index
        return rowButtons.getOrPut(key) {
            val source = when (index) {
                0 -> asset(context, R.drawable.v5_playing_previous_button)
                1 -> transport(context, active)
                else -> asset(context, R.drawable.v5_playing_next_button)
            }
            val cx = if (index == 1) 61f else if (index == 0) 48f else 53f
            val cy = if (index == 1) 61f else if (index == 0) 53f else 52f
            val radius = if (index == 1) 48f else 39f
            val extent = kotlin.math.ceil(radius + 3f).toInt()
            val output = Bitmap.createBitmap(extent * 2, extent * 2, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            canvas.drawBitmap(source, extent - cx, extent - cy, Paint(Paint.FILTER_BITMAP_FLAG))
            val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(extent.toFloat(), extent.toFloat(), extent.toFloat(),
                    intArrayOf(Color.WHITE, Color.WHITE, Color.TRANSPARENT),
                    floatArrayOf(0f, radius / extent, 1f), Shader.TileMode.CLAMP)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            }
            canvas.drawRect(0f, 0f, output.width.toFloat(), output.height.toFloat(), mask)
            output
        }
    }

    internal fun playbackSurface(context: Context, active: Boolean, enabledMask: Int): Bitmap {
        val key=(enabledMask and 7) or (if(active)8 else 0)
        if(playbackSurfaceKey==key) playbackSurface?.let { return it }
        val bitmap=playing(context).copy(Bitmap.Config.ARGB_8888,true)
        val canvas=Canvas(bitmap)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        fun draw(image: Bitmap,x: Float,y: Float,bit: Int) {
            paint.alpha=if(enabledMask and bit != 0)255 else 80
            canvas.drawBitmap(image,x,y,paint)
        }
        draw(asset(context,R.drawable.v5_playing_previous_button),650f,40f,1)
        draw(transport(context,active),754f,32f,2)
        draw(asset(context,R.drawable.v5_playing_next_button),877f,41f,4)
        applySilhouette(bitmap, playing = true)
        playbackSurfaceKey=key
        playbackSurface=bitmap
        return bitmap
    }

    private fun transport(context: Context, active: Boolean): Bitmap {
        if (active) return asset(context, R.drawable.v5_playing_pause_button)
        return playSkin ?: asset(context, R.drawable.v5_playing_pause_button).copy(Bitmap.Config.ARGB_8888, true).also {
                for (x in 45..76) {
                    val a = it.getPixel(x, 41)
                    val b = it.getPixel(x, 82)
                    for (y in 42..81) {
                        val t = (y - 41) / 41f
                        fun mix(first: Int, last: Int) = (first + (last - first) * t).toInt()
                        it.setPixel(x, y, Color.rgb(mix(Color.red(a), Color.red(b)), mix(Color.green(a), Color.green(b)), mix(Color.blue(a), Color.blue(b))))
                    }
                }
                Canvas(it).drawPath(Path().apply { moveTo(49f, 43f); lineTo(77f, 61f); lineTo(49f, 79f); close() },
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xfffff7ff.toInt() })
                playSkin = it
            }
    }

    /** Crop uniformly to cover both the square visible area and its fade continuation.
     * Keeping actual adjacent image pixels avoids synthesizing horizontal edge streaks.
     */
    fun expandedArtwork(source: Bitmap, height: Int = 225): Bitmap {
        if(expandedSource.get() === source && expandedGeneration == source.generationId && expandedHeight == height) return expandedCover!!
        val bitmap=Bitmap.createBitmap(307,height,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        val scale=maxOf(307f/source.width,height.toFloat()/source.height)
        val shader=BitmapShader(source,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply {
                setScale(scale,scale)
                postTranslate((307-source.width*scale)/2f,(height-source.height*scale)/2f)
            })
        }
        val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader=shader }
        // The raster rim has 80px corners, not a capsule's half-height radius.
        // Keep only the thin rim inset; a larger radius exposes a crescent of glass.
        val ratio = height / 225f
        canvas.clipPath(Path().apply { addRoundRect(RectF(14f,5f * ratio,1015f,height - 5f * ratio),80f,80f * ratio,Path.Direction.CW) })
        canvas.drawRect(0f,0f,307f,height.toFloat(),paint)
        paint.shader=LinearGradient(215f,0f,307f,0f,Color.WHITE,Color.TRANSPARENT,Shader.TileMode.CLAMP)
        paint.xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        canvas.drawRect(0f,0f,307f,height.toFloat(),paint)
        expandedSource=java.lang.ref.WeakReference(source)
        expandedGeneration=source.generationId
        expandedHeight=height
        expandedCover=bitmap
        return bitmap
    }

    /** Legacy square-and-edge variants retained for reproducible debug calibration. */
    fun artwork(source: Bitmap, transitionWidth: Int = 0, blurRadius: Int = 0,
                fadeStart: Int = 215, mirrorEdge: Boolean = false, extent: Int = ART_EXTENT): Bitmap {
        require(transitionWidth in 0..100 && blurRadius in 0..100 && extent in 215..320 && fadeStart in 0 until extent)
        if (artSource.get() === source && artGeneration == source.generationId &&
            artTransition == transitionWidth && artBlur == blurRadius && artFadeStart == fadeStart && artMirror == mirrorEdge && artExtent == extent) return fadedArt!!
        val result = Bitmap.createBitmap(extent, 215, Bitmap.Config.ARGB_8888)
        val c = Canvas(result)
        val scale = 215f / minOf(source.width, source.height)
        val shader = BitmapShader(source, if (mirrorEdge) Shader.TileMode.MIRROR else Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        shader.setLocalMatrix(Matrix().apply {
            setScale(scale, scale)
            postTranslate((215 - source.width * scale) / 2f, (215 - source.height * scale) / 2f)
        })
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader }
        val path = Path().apply { addRoundRect(RectF(0f, 0f, extent.toFloat(), 215f), floatArrayOf(90f,90f,0f,0f,0f,0f,90f,90f), Path.Direction.CW) }
        c.clipPath(path)
        c.drawRect(0f, 0f, extent.toFloat(), 215f, p)
        // Optional edge treatments are used by the debug calibration. The measured
        // production defaults preserve the square cover, then fade its continuation.
        val edgeColumn = IntArray(215) { result.getPixel(214, it) }
        if (!mirrorEdge) for (y in 0 until 215) {
            var red = 0; var green = 0; var blue = 0; var samples = 0
            for (row in maxOf(0, y - blurRadius)..minOf(214, y + blurRadius)) {
                val edge = edgeColumn[row]
                red += Color.red(edge); green += Color.green(edge); blue += Color.blue(edge); samples++
            }
            val blurred = Color.rgb(red / samples, green / samples, blue / samples)
            for (x in (215-transitionWidth) until extent) {
                val original = result.getPixel(x, y)
                val blend = if (transitionWidth == 0) 1f else ((x - (215-transitionWidth)) / transitionWidth.toFloat()).coerceIn(0f, 1f)
                // Smoothstep gives the cover and continuation matching slopes.
                val t = blend * blend * (3 - 2 * blend)
                fun mix(a: Int, b: Int) = (a + (b - a) * t).toInt()
                result.setPixel(x, y, Color.rgb(mix(Color.red(original), Color.red(blurred)),
                    mix(Color.green(original), Color.green(blurred)), mix(Color.blue(original), Color.blue(blurred))))
            }
        }
        p.shader = LinearGradient(fadeStart.toFloat(), 0f, extent.toFloat(), 0f, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        c.drawRect(0f, 0f, extent.toFloat(), 215f, p)
        artSource = java.lang.ref.WeakReference(source)
        artGeneration = source.generationId
        artTransition = transitionWidth
        artBlur = blurRadius
        artFadeStart = fadeStart
        artMirror = mirrorEdge
        artExtent = extent
        fadedArt = result
        return result
    }
}
