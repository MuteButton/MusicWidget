package io.github.mutebutton.musicwidget

import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.widget.RemoteViews

internal object WidgetRenderer {
    private val titleHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val titleTasks = mutableMapOf<Int, Runnable>()
    private data class TrackIdentity(val token: MediaSession.Token, val mediaId: String?,
                                     val title: String?, val artist: String?)
    private val titleTracks = mutableMapOf<Int, TrackIdentity>()
    private val transitionTitle = TransitionTitle()
    private var displayTitleToken: MediaSession.Token? = null
    private var displayTitleValue: String? = null
    private var titleExpiry: Runnable? = null

    private fun rawTitle(controller: MediaController): String? =
        controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
            ?: controller.metadata?.description?.title?.toString()

    private fun displayedTitle(context: Context, controller: MediaController): String =
        displayTitleValue?.takeIf { displayTitleToken == controller.sessionToken }
            ?: rawTitle(controller)?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.unknown_title)
    private var selectedToken: MediaSession.Token? = null
    private var artToken: MediaSession.Token? = null
    private var artGeneration: Int? = null
    private var roundedArt: Bitmap? = null
    private const val ART_SIZE = 256
    const val PREFS = "media_widget"
    const val LAST_PLAYER = "last_player"
    const val PREFERRED_PLAYER = "preferred_player"

    fun hasAccess(context: Context): Boolean {
        val component = ComponentName(context, MediaListenerService::class.java)
        return if (Build.VERSION.SDK_INT >= 27) {
            context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
        } else {
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                ?.split(':')?.any { ComponentName.unflattenFromString(it) == component } == true
        }
    }

    fun controllers(context: Context): List<MediaController> = try {
        if (hasAccess(context)) context.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(ComponentName(context, MediaListenerService::class.java)) else emptyList()
    } catch (_: SecurityException) { emptyList() }

    fun isPlaying(state: PlaybackState?): Boolean = state?.state == PlaybackState.STATE_PLAYING

    fun isPlaybackActive(state: PlaybackState?): Boolean = state?.state in setOf(
        PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING,
        PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING,
        PlaybackState.STATE_SKIPPING_TO_NEXT, PlaybackState.STATE_SKIPPING_TO_PREVIOUS)

    fun isAdvancing(state: PlaybackState?): Boolean = state?.state in setOf(
        PlaybackState.STATE_PLAYING, PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING)

    fun canToggle(state: PlaybackState?): Boolean {
        val required = if (isPlaybackActive(state)) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY
        return ((state?.actions ?: 0L) and (required or PlaybackState.ACTION_PLAY_PAUSE)) != 0L
    }

    fun chooseController(controllers: List<MediaController>, preferred: MediaSession.Token? = selectedToken): MediaController? {
        val candidates = controllers.filter { controller ->
            controller.playbackState?.state in setOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_PAUSED,
                PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING, PlaybackState.STATE_FAST_FORWARDING,
                PlaybackState.STATE_REWINDING, PlaybackState.STATE_SKIPPING_TO_NEXT, PlaybackState.STATE_SKIPPING_TO_PREVIOUS)
        }
        return candidates.firstOrNull { it.sessionToken == preferred && isPlaying(it.playbackState) }
            ?: candidates.firstOrNull { isPlaying(it.playbackState) }
            ?: candidates.firstOrNull { it.sessionToken == preferred }
            ?: candidates.firstOrNull()
    }

    fun widgetIds(context: Context): IntArray = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, MediaWidgetProvider::class.java))

    private fun compact(context: Context, id: Int): Boolean {
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
            .getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 374) < 360
    }

    private fun v5(context: Context, id: Int) = Build.VERSION.SDK_INT >= 31
    private fun layout(context: Context, id: Int) = if (v5(context, id)) R.layout.widget_media_v5
        else if (compact(context, id)) R.layout.widget_media_compact else R.layout.widget_media_control

    /** Prefer the precise canvas supplied by modern launchers over truncated dp. */
    @Suppress("DEPRECATION")
    private fun hostCanvas(context: Context, id: Int): android.util.SizeF {
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
        val width = options.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
            else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 374).coerceAtLeast(1).toFloat()
        val height = options.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
            else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 96).coerceAtLeast(1).toFloat()
        if (Build.VERSION.SDK_INT >= 31) {
            val exact = options.getParcelableArrayList<android.util.SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                ?.filter { it.width.isFinite() && it.height.isFinite() && it.width > 0 && it.height > 0 }
                ?.minByOrNull { kotlin.math.abs(it.width - width) + kotlin.math.abs(it.height - height) }
            // Match the current orientation; do not pick an unrelated fold/display size.
            if (exact != null && kotlin.math.abs(exact.width - width) < 2f &&
                kotlin.math.abs(exact.height - height) < 2f) return exact
        }
        return android.util.SizeF(width, height)
    }

    private fun pillWidth(context: Context, id: Int): Float = hostCanvas(context, id).let {
        WidgetSizing.pillWidth(it.width, it.height)
    }

    fun renderAll(context: Context, controller: MediaController?) {
        selectedToken = controller?.sessionToken
        val ids = widgetIds(context)
        val removed = titleTracks.keys.filter { it !in ids }
        removed.forEach { id ->
            titleTasks.remove(id)?.let { titleHandler.removeCallbacks(it) }
            titleTracks.remove(id)
        }
        titleExpiry?.let { titleHandler.removeCallbacks(it) }
        titleExpiry = null
        if (controller == null || ids.isEmpty()) {
            transitionTitle.reset()
            displayTitleToken = null
            displayTitleValue = null
        } else {
            val result = transitionTitle.resolve(controller.sessionToken, rawTitle(controller),
                context.getString(R.string.unknown_title), SystemClock.elapsedRealtime())
            displayTitleToken = controller.sessionToken
            displayTitleValue = result.title
            result.refreshAt?.let { deadline ->
                val app = context.applicationContext
                val token = controller.sessionToken
                val task = Runnable {
                    titleExpiry = null
                    val current = chooseController(controllers(app))
                    if (current?.sessionToken == token) renderAll(app, current)
                }
                titleExpiry = task
                titleHandler.postDelayed(task, (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1))
            }
        }
        if (ids.isEmpty()) return
        val interactive = isScreenAvailable(context)
        val track = controller?.let {
            TrackIdentity(it.sessionToken, it.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
                displayedTitle(context, it), it.metadata?.description?.subtitle?.toString())
        }
        if (controller != null && controller.packageName != context.packageName) {
            val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (preferences.getString(LAST_PLAYER, null) != controller.packageName) {
                preferences.edit().putString(LAST_PLAYER, controller.packageName).apply()
            }
        }
        // The v5 renderer has its own bounded crop; do not build an unused legacy cover.
        val artwork = if (ids.any { !v5(context, it) }) controller?.let { albumArt(it) } else null
        if (controller == null) { roundedArt = null; artGeneration = null; artToken = null }
        val manager = AppWidgetManager.getInstance(context)
        for (id in ids) {
            val changed = titleTracks[id] != track
            val preserveTitle = !changed && interactive && v5(context, id) && titleTasks.containsKey(id)
            if (!preserveTitle) titleTasks.remove(id)?.let { titleHandler.removeCallbacks(it) }
            if (track == null) titleTracks.remove(id) else titleTracks[id] = track
            val views = createViews(context, id, controller, artwork, preserveTitle = preserveTitle)
            if (preserveTitle) manager.partiallyUpdateAppWidget(id, views)
            else manager.updateAppWidget(id, views)
            if (changed && interactive && controller != null && v5(context, id)) scrollTitle(context, id, controller)
        }
    }

    /** The same RemoteViews used by the launcher and native-resolution debug host. */
    fun createViews(context: Context, id: Int, controller: MediaController?, artwork: Bitmap? = null,
                    widthOverride: Float? = null, preserveTitle: Boolean = false): RemoteViews {
        val manager = AppWidgetManager.getInstance(context)
        val views = RemoteViews(context.packageName, layout(context, id))
        val hasMedia = controller != null
        val destination = controller?.let { mediaIntent(context, it) }
            ?: setupIntent(context, if (hasAccess(context)) MainActivity.ACTION_OPEN_PLAYER else null)
        // Cover empty pill space and the timeline as well as artwork/text. Child
        // transport buttons retain their own actions (including disabled state).
        views.setOnClickPendingIntent(R.id.widget_container, destination)
        views.setOnClickPendingIntent(R.id.media_state, destination)
        views.setOnClickPendingIntent(R.id.glass_surface, destination)
        if (v5(context, id) && Build.VERSION.SDK_INT >= 31) {
            val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val widthDp = pillWidth(context, id)
            val state=controller?.playbackState
            val actions=state?.actions ?: 0L
            val enabledMask=(if(actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L)1 else 0) or
                (if(canToggle(state))2 else 0) or
                (if(actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L)4 else 0)
            val palette = if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getBoolean(ArtworkColors.PREFERENCE, true)) ArtworkColors.from(controller?.let { rawAlbumArt(it) }) else null
            val hostHeight = hostCanvas(context, id).height
            val height = if (widthOverride != null) widthOverride * 225f / 1030f
                else WidgetSizing.pillHeight(widthDp, hostHeight)
            V5Artwork.bind(context, views, widthOverride ?: widthDp.toFloat(), hasMedia, hasAccess(context),isPlaybackActive(state),enabledMask,palette,height, if (widthOverride != null) 1030f else WidgetSizing.CANVAS_WIDTH)
        } else if (!compact(context, id)) {
            val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val widthDp = manager.getAppWidgetOptions(id).getInt(
                if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 374)
            val widthPx = (widthDp * context.resources.displayMetrics.density).toInt()
            val heightPx = ((if (hasMedia) 111f else 101.5f) * context.resources.displayMetrics.density).toInt()
            views.setImageViewBitmap(R.id.glass_surface, GlassArtwork.surface(widthPx, heightPx, !hasMedia))
            views.setImageViewBitmap(R.id.skin_prev, GlassArtwork.control(primary = false, next = false))
            views.setImageViewBitmap(R.id.skin_play, GlassArtwork.control(primary = true, next = false))
            views.setImageViewBitmap(R.id.skin_next, GlassArtwork.control(primary = false, next = true))
            views.setImageViewBitmap(R.id.idle_art, GlassArtwork.idleOrb(context))
            views.setImageViewBitmap(R.id.skin_open, GlassArtwork.openButton())
        }
        views.setViewVisibility(R.id.idle_state, if (hasMedia) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.media_state, if (hasMedia) View.VISIBLE else View.GONE)
        // Hidden chronometers must also be stopped when a session ends.
        views.setChronometer(R.id.elapsed_time, SystemClock.elapsedRealtime(), null, false)
        views.setChronometer(R.id.remaining_time, SystemClock.elapsedRealtime(), "-%s", false)
        if (controller == null) {
            val access = hasAccess(context)
            views.setTextViewText(R.id.idle_title, context.getString(if (access) R.string.idle_no_media else R.string.idle_connect))
            views.setTextViewText(R.id.idle_subtitle, context.getString(if (access) R.string.idle_open_hint else R.string.idle_permission_hint))
            views.setTextViewText(R.id.btn_open, context.getString(if (access) R.string.open_player else R.string.connect_player))
            views.setOnClickPendingIntent(R.id.btn_open, setupIntent(context, if (access) MainActivity.ACTION_OPEN_PLAYER else null))
            views.setOnClickPendingIntent(R.id.idle_state, setupIntent(context, if (access) MainActivity.ACTION_OPEN_PLAYER else null))
        } else {
            val metadata = controller.metadata
            val description = metadata?.description
            val title = displayedTitle(context, controller)
            val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
                ?: description?.subtitle?.toString()?.takeIf { it.isNotBlank() } ?: appLabel(context, controller.packageName)
            if (!preserveTitle) {
                if (v5(context, id)) bindTitle(context, views, id, controller, title, false, widthOverride)
                else views.setTextViewText(R.id.track_title, title)
            }
            views.setTextViewText(R.id.track_artist, artist)
            views.setContentDescription(R.id.album_art, context.getString(R.string.open_current_track, title))
            val rowHeight = rowHeight(context, id, widthOverride)
            val displayArt = if (v5(context, id)) rawAlbumArt(controller)?.let { V5Artwork.expandedArtwork(it, rowHeight) } else artwork
            if (displayArt != null) views.setImageViewBitmap(R.id.album_art, displayArt)
            else if (v5(context, id)) views.setImageViewBitmap(R.id.album_art, V5Artwork.placeholder(context, rowHeight))
            else views.setImageViewResource(R.id.album_art, R.drawable.ic_album_placeholder)
            val playing = isPlaybackActive(controller.playbackState)
            if (!v5(context, id)) views.setImageViewResource(R.id.btn_play, if (playing) R.drawable.ic_pause else R.drawable.ic_play)
            views.setContentDescription(R.id.btn_play, context.getString(if (playing) R.string.pause_playback else R.string.start_playback))
            val actions = controller.playbackState?.actions ?: 0L
            button(context, views, R.id.btn_play, canToggle(controller.playbackState), MediaWidgetProvider.ACTION_PLAY_PAUSE, controller)
            button(context, views, R.id.btn_prev, actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L, MediaWidgetProvider.ACTION_PREV, controller)
            button(context, views, R.id.btn_next, actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L, MediaWidgetProvider.ACTION_NEXT, controller)
            val openIntent = mediaIntent(context, controller)
            views.setOnClickPendingIntent(R.id.album_art, openIntent)
            views.setOnClickPendingIntent(R.id.text_info, openIntent)
            progress(context, id, views, controller)
        }
        return views
    }

    private fun button(context: Context, views: RemoteViews, id: Int, enabled: Boolean, action: String, controller: MediaController) {
        views.setBoolean(id, "setEnabled", enabled)
        views.setInt(id, "setImageAlpha", if (enabled) 255 else 80)
        val intent = Intent(context, MediaWidgetProvider::class.java).setAction(action)
            .setData(Uri.Builder().scheme("mediawidget").authority("control")
                .appendPath(controller.sessionToken.hashCode().toString()).appendPath(action).build())
            .putExtra(MediaWidgetProvider.EXTRA_SESSION, controller.sessionToken)
        views.setOnClickPendingIntent(id, if (enabled) PendingIntent.getBroadcast(context, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) else null)
    }

    internal fun isScreenAvailable(context: Context): Boolean =
        context.getSystemService(android.os.PowerManager::class.java).isInteractive &&
            !context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked

    private fun rowHeight(context: Context, id: Int, widthOverride: Float?): Int {
        if (widthOverride != null) return 225
        val height = hostCanvas(context, id).height
        val width = pillWidth(context, id)
        return kotlin.math.round(WidgetSizing.pillHeight(width, height) * WidgetSizing.CANVAS_WIDTH / width).toInt().coerceIn(225, WidgetSizing.MAX_ROW_HEIGHT)
    }

    private fun bindTitle(context: Context, views: RemoteViews, id: Int, controller: MediaController,
                          title: String, scroll: Boolean, widthOverride: Float? = null) {
        if (Build.VERSION.SDK_INT < 31) return // Legacy layouts have no nested title.
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val width = widthOverride ?: pillWidth(context, id)
        val label = RemoteViews(context.packageName,
            if (scroll) R.layout.widget_title_marquee else R.layout.widget_title_static)
        label.setTextViewText(R.id.track_title, title)
        label.setTextViewTextSize(R.id.track_title, android.util.TypedValue.COMPLEX_UNIT_PX,
            RowLayout.TITLE_SIZE * width * context.resources.displayMetrics.density / (if (widthOverride != null) 1030f else WidgetSizing.CANVAS_WIDTH))
        if (scroll) {
            val paint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = RowLayout.TITLE_SIZE * width * context.resources.displayMetrics.density / (if (widthOverride != null) 1030f else WidgetSizing.CANVAS_WIDTH)
                textScaleX = 1.02f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            }
            label.setViewLayoutWidth(R.id.track_title, kotlin.math.ceil(android.text.Layout.getDesiredWidth(title, paint)),
                android.util.TypedValue.COMPLEX_UNIT_PX)
        }
        label.setOnClickPendingIntent(R.id.track_title,
            mediaIntent(context, controller))
        views.removeAllViews(R.id.title_container)
        // Reapplying setText restarts TextView's native marquee, even with stable
        // children. A one-shot layout animation survives text reapplication; the
        // stable child also prevents progress ticks from reinflating its container.
        views.addStableView(R.id.title_container, label, if (scroll) 1 else 0)
    }

    private fun mediaIntent(context: Context, controller: MediaController): PendingIntent =
        controller.sessionActivity ?: MediaListenerService.instance?.mediaDestination(controller)
        ?: playerIntent(context, controller.packageName)

    private fun scrollTitle(context: Context, id: Int, controller: MediaController) {
        if (id !in widgetIds(context) || !v5(context, id)) return
        val title = displayedTitle(context, controller)
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val landscape = app.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val widthDp = pillWidth(app, id)
        val density = app.resources.displayMetrics.density
        val scale = widthDp * density / WidgetSizing.CANVAS_WIDTH
        val paint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = RowLayout.TITLE_SIZE * scale
            textScaleX = 1.02f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        val lineWidth = android.text.Layout.getDesiredWidth(title.replace('\n', ' '), paint)
        val viewport = RowLayout.TITLE_WIDTH * scale
        titleTasks.remove(id)?.let { titleHandler.removeCallbacks(it) }
        fun display(scroll: Boolean) {
            val views = RemoteViews(app.packageName, R.layout.widget_media_v5)
            bindTitle(app, views, id, controller, title, scroll)
            manager.partiallyUpdateAppWidget(id, views)
        }
        display(false)
        if (lineWidth <= viewport) return
        // XML moves 30 dp/second, independent of title length. Replace the child
        // once its measured text has traversed, cancelling the long animation.
        // Only start/reset callbacks; the launcher owns every animation frame.
        val duration = MarqueeTiming.restoreDelay(lineWidth / density)
        val start = Runnable {
            display(true)
            val restore = Runnable {
                titleTasks.remove(id)
                display(false)
            }
            titleTasks[id] = restore
            titleHandler.postDelayed(restore, duration)
        }
        titleTasks[id] = start
        titleHandler.postDelayed(start, 600L)
    }

    fun cancelTitleScrolls() {
        titleExpiry?.let { titleHandler.removeCallbacks(it) }
        titleExpiry = null
        transitionTitle.reset()
        displayTitleToken = null
        displayTitleValue = null
        titleTasks.values.forEach { titleHandler.removeCallbacks(it) }
        titleTasks.clear()
    }

    fun updateProgress(context: Context, controller: MediaController) {
        // Partial updates exclude artwork and avoid re-inflating the entire widget every tick.
        widgetIds(context).forEach { id ->
            val views = RemoteViews(context.packageName, layout(context, id))
            progress(context, id, views, controller)
            AppWidgetManager.getInstance(context).partiallyUpdateAppWidget(id, views)
        }
    }

    private fun progress(context: Context, id: Int, views: RemoteViews, controller: MediaController) {
        val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val listener = MediaListenerService.instance
        val state = if (listener != null) listener.playbackState(controller) else controller.playbackState
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val height = AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
            .getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 96)
        val visible = duration > 0 && (state?.position ?: -1) >= 0 &&
            (v5(context, id) || height >= if (compact(context, id)) 120 else 96)
        views.setViewVisibility(R.id.progress_container, if (visible) View.VISIBLE else View.GONE)
        if (!visible) {
            views.setBoolean(R.id.elapsed_time, "setStarted", false)
            views.setBoolean(R.id.remaining_time, "setStarted", false)
            return
        }
        val now = SystemClock.elapsedRealtime()
        val position = PlaybackMath.position(state!!.position, state.lastPositionUpdateTime, now,
            state.playbackSpeed, isAdvancing(state), duration)
        views.setProgressBar(R.id.progress_bar, 1000, (position.toDouble() / duration * 1000).toInt(), false)
        views.setContentDescription(R.id.progress_bar, context.getString(R.string.progress_description,
            PlaybackMath.time(position), PlaybackMath.time(duration)))
        // Platform Chronometer pads minutes (01:24). Use the same m:ss formatter
        // for playing and paused states; the service sends small progress-only updates.
        views.setBoolean(R.id.elapsed_time, "setStarted", false)
        views.setBoolean(R.id.remaining_time, "setStarted", false)
        views.setTextViewText(R.id.elapsed_time, PlaybackMath.time(position))
        views.setTextViewText(R.id.remaining_time, "-" + PlaybackMath.time(duration - position, roundUp = true))
    }

    private fun rawAlbumArt(controller: MediaController): Bitmap? {
        val metadata = controller.metadata
        return (metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: metadata?.description?.iconBitmap
            )?.takeUnless { it.isRecycled }
    }

    private fun albumArt(controller: MediaController): Bitmap? {
        val source = rawAlbumArt(controller)
        if (source == null || source.isRecycled) {
            roundedArt = null; artGeneration = null; artToken = null
            return null
        }
        if (artToken == controller.sessionToken && artGeneration == source.generationId) return roundedArt
        // A bounded center crop avoids distortion, oversized Binder transactions and repeated image work.
        val output = Bitmap.createBitmap(ART_SIZE, ART_SIZE, Bitmap.Config.ARGB_8888)
        val scale = maxOf(ART_SIZE.toFloat() / source.width, ART_SIZE.toFloat() / source.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate((ART_SIZE - source.width * scale) / 2, (ART_SIZE - source.height * scale) / 2)
        }
        val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(matrix) }
        Canvas(output).drawRoundRect(0f, 0f, ART_SIZE.toFloat(), ART_SIZE.toFloat(), 42f, 42f,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader })
        artToken = controller.sessionToken
        artGeneration = source.generationId
        roundedArt = output // Let GC reclaim previous images; RemoteViews may still reference them.
        return output
    }

    fun appLabel(context: Context, packageName: String): String = try {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) { packageName }

    private fun playerIntent(context: Context, packageName: String): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
        return if (launch != null) PendingIntent.getActivity(context, packageName.hashCode(), launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        else setupIntent(context, MainActivity.ACTION_OPEN_PLAYER)
    }

    private fun setupIntent(context: Context, action: String?): PendingIntent = PendingIntent.getActivity(context,
        action?.hashCode() ?: 0, Intent(context, MainActivity::class.java).setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
