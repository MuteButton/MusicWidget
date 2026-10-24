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
            if (connected) {
                c.drawBitmap(asset(context, R.drawable.v5_idle_title), 298f, 62f, null)
                c.drawBitmap(asset(context, R.drawable.v5_idle_subtitle), 298f, 119f, null)
                c.drawBitmap(asset(context, R.drawable.v5_idle_open_app_cta), 722f, 54f, null)
            }
        }
        }
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

    fun placeholder(context: Context): Bitmap {
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
        return expandedArtwork(source)
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
             active: Boolean = true, enabledMask: Int = 7, palette: ArtworkColors.Palette? = null) {
        val scale = widthDp * context.resources.displayMetrics.density / 1030f
        fun size(id: Int, width: Float, height: Float) {
            views.setViewLayoutWidth(id, width * scale, TypedValue.COMPLEX_UNIT_PX)
            views.setViewLayoutHeight(id, height * scale, TypedValue.COMPLEX_UNIT_PX)
        }
        fun place(id: Int, x: Float, y: Float, width: Float, height: Float) {
            size(id, width, height)
            views.setViewLayoutMargin(id, RemoteViews.MARGIN_LEFT, x * scale, TypedValue.COMPLEX_UNIT_PX)
            views.setViewLayoutMargin(id, RemoteViews.MARGIN_TOP, y * scale, TypedValue.COMPLEX_UNIT_PX)
        }
        listOf(R.id.glass_surface, R.id.idle_state, R.id.media_state).forEach { size(it, 1030f, 225f) }
        val base = if (hasMedia) playbackSurface(context, active, enabledMask) else idle(context, connected)
        views.setImageViewBitmap(R.id.glass_surface, if (hasMedia && palette != null)
            ArtworkColors.glass(base, palette, (enabledMask and 7) or (if (active) 8 else 0)) else base)
        views.setTextColor(R.id.track_artist, palette?.caption ?: 0xff9897ff.toInt())
        views.setColorStateList(R.id.progress_bar, "setProgressTintList",
            palette?.let { android.content.res.ColorStateList.valueOf(it.accent) })
        views.setColorStateList(R.id.progress_bar, "setProgressBackgroundTintList",
            palette?.let { android.content.res.ColorStateList.valueOf((it.accent and 0x00ffffff) or 0x50000000) })
        place(R.id.idle_title, 310f, 80f, 390f, 40f)
        place(R.id.idle_subtitle, 310f, 125f, 390f, 40f)
        place(R.id.btn_open, 722f, 54f, 260f, 117f)
        for (id in listOf(R.id.idle_title, R.id.idle_subtitle, R.id.btn_open)) {
            views.setTextColor(id, if (connected) Color.TRANSPARENT else Color.WHITE)
        }
        if (!connected) {
            views.setTextColor(R.id.idle_subtitle,0xff87b5ff.toInt())
        }
        views.setViewPadding(R.id.btn_open,0,if (connected) 0 else (10*scale).toInt(),0,0)
        place(R.id.album_art, 0f, 0f, 307f, 225f)
        place(R.id.text_info, 314f, 49f, 325f, 84f)
        place(R.id.title_container, 0f, 0f, 325f, 42f)
        place(R.id.track_artist, 0f, 42f, 325f, 42f)
        place(R.id.btn_prev, 650f, 40f, 102f, 104f)
        place(R.id.btn_play, 754f, 32f, 121f, 114f)
        place(R.id.btn_next, 877f, 41f, 94f, 103f)
        place(R.id.progress_container, 314f, 148f, 650f, 57f)
        size(R.id.progress_bar, 650f, 11f)
        size(R.id.time_row, 650f, 37f)
        views.setViewLayoutMargin(R.id.time_row, RemoteViews.MARGIN_TOP, 13.7f * scale, TypedValue.COMPLEX_UNIT_PX)
        views.setTextViewTextSize(R.id.track_title, TypedValue.COMPLEX_UNIT_PX, 32f * scale)
        views.setTextViewTextSize(R.id.track_artist, TypedValue.COMPLEX_UNIT_PX, 24.5f * scale)
        views.setTextViewTextSize(R.id.elapsed_time, TypedValue.COMPLEX_UNIT_PX, 19.5f * scale)
        views.setTextViewTextSize(R.id.remaining_time, TypedValue.COMPLEX_UNIT_PX, 19.5f * scale)
        views.setTextViewTextSize(R.id.idle_title, TypedValue.COMPLEX_UNIT_PX, 32f * scale)
        views.setTextViewTextSize(R.id.idle_subtitle, TypedValue.COMPLEX_UNIT_PX, 25f * scale)
        views.setTextViewTextSize(R.id.btn_open, TypedValue.COMPLEX_UNIT_PX, 27f * scale)
        // Visuals share one scaling operation; these remain independent live hit targets.
        for (id in listOf(R.id.btn_prev,R.id.btn_play,R.id.btn_next)) views.setImageViewResource(id,0)
    }

    private fun playbackSurface(context: Context, active: Boolean, enabledMask: Int): Bitmap {
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
    fun expandedArtwork(source: Bitmap): Bitmap {
        if(expandedSource.get() === source && expandedGeneration == source.generationId) return expandedCover!!
        val bitmap=Bitmap.createBitmap(307,225,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        val scale=maxOf(307f/source.width,225f/source.height)
        val shader=BitmapShader(source,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply {
                setScale(scale,scale)
                postTranslate((307-source.width*scale)/2f,(225-source.height*scale)/2f)
            })
        }
        val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader=shader }
        // The raster rim has 80px corners, not a capsule's half-height radius.
        // Keep only the thin rim inset; a larger radius exposes a crescent of glass.
        canvas.clipPath(Path().apply { addRoundRect(RectF(14f,5f,1015f,220f),80f,80f,Path.Direction.CW) })
        canvas.drawRect(0f,0f,307f,225f,paint)
        paint.shader=LinearGradient(215f,0f,307f,0f,Color.WHITE,Color.TRANSPARENT,Shader.TileMode.CLAMP)
        paint.xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        canvas.drawRect(0f,0f,307f,225f,paint)
        expandedSource=java.lang.ref.WeakReference(source)
        expandedGeneration=source.generationId
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
