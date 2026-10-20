package com.example.mediawidget

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

    private fun v5(context: Context, id: Int) = Build.VERSION.SDK_INT >= 31 && !compact(context, id)
    private fun layout(context: Context, id: Int) = if (compact(context, id)) R.layout.widget_media_compact
        else if (v5(context, id)) R.layout.widget_media_v5 else R.layout.widget_media_control

    fun renderAll(context: Context, controller: MediaController?) {
        selectedToken = controller?.sessionToken
        val ids = widgetIds(context)
        if (ids.isEmpty()) return
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
            val views = createViews(context, id, controller, artwork)
            manager.updateAppWidget(id, views)
        }
    }

    /** The same RemoteViews used by the launcher and native-resolution debug host. */
    fun createViews(context: Context, id: Int, controller: MediaController?, artwork: Bitmap? = null,
                    widthOverride: Float? = null): RemoteViews {
        val manager = AppWidgetManager.getInstance(context)
        val views = RemoteViews(context.packageName, layout(context, id))
        val hasMedia = controller != null
        if (v5(context, id) && Build.VERSION.SDK_INT >= 31) {
            val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val widthDp = manager.getAppWidgetOptions(id).getInt(
                if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 374)
            val state=controller?.playbackState
            val actions=state?.actions ?: 0L
            val enabledMask=(if(actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L)1 else 0) or
                (if(canToggle(state))2 else 0) or
                (if(actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L)4 else 0)
            V5Artwork.bind(context, views, widthOverride ?: widthDp.toFloat(), hasMedia, hasAccess(context),isPlaybackActive(state),enabledMask)
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
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
                ?: description?.title?.toString()?.takeIf { it.isNotBlank() } ?: context.getString(R.string.unknown_title)
            val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
                ?: description?.subtitle?.toString()?.takeIf { it.isNotBlank() } ?: appLabel(context, controller.packageName)
            views.setTextViewText(R.id.track_title, title)
            views.setTextViewText(R.id.track_artist, artist)
            views.setContentDescription(R.id.album_art, context.getString(R.string.open_current_track, title))
            val displayArt = if (v5(context, id)) rawAlbumArt(controller)?.let { V5Artwork.expandedArtwork(it) } else artwork
            if (displayArt != null) views.setImageViewBitmap(R.id.album_art, displayArt)
            else if (v5(context, id)) views.setImageViewBitmap(R.id.album_art, V5Artwork.placeholder(context))
            else views.setImageViewResource(R.id.album_art, R.drawable.ic_album_placeholder)
            val playing = isPlaybackActive(controller.playbackState)
            if (!v5(context, id)) views.setImageViewResource(R.id.btn_play, if (playing) R.drawable.ic_pause else R.drawable.ic_play)
            views.setContentDescription(R.id.btn_play, context.getString(if (playing) R.string.pause_playback else R.string.start_playback))
            val actions = controller.playbackState?.actions ?: 0L
            button(context, views, R.id.btn_play, canToggle(controller.playbackState), MediaWidgetProvider.ACTION_PLAY_PAUSE, controller)
            button(context, views, R.id.btn_prev, actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L, MediaWidgetProvider.ACTION_PREV, controller)
            button(context, views, R.id.btn_next, actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L, MediaWidgetProvider.ACTION_NEXT, controller)
            val openIntent = controller.sessionActivity ?: playerIntent(context, controller.packageName)
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
        val state = controller.playbackState
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val height = AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
            .getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 96)
        val visible = duration > 0 && (state?.position ?: -1) >= 0 && height >= if (compact(context, id)) 120 else 96
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
