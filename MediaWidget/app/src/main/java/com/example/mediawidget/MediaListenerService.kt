package com.example.mediawidget

import android.app.Notification
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.toColorInt
import androidx.palette.graphics.Palette
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MediaListenerService : NotificationListenerService(),
    MediaSessionManager.OnActiveSessionsChangedListener {

    private var activeController: MediaController? = null
    private lateinit var mediaSessionManager: MediaSessionManager
    private val serviceComponent by lazy { ComponentName(this, MediaListenerService::class.java) }
    private val appWidgetManager by lazy { AppWidgetManager.getInstance(this) }
    
    private val paletteExecutor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    
    private var cachedRoundedBitmap: Bitmap? = null
    private var lastAlbumArtId: Int? = null
    private var currentBackgroundColor: Int = DEFAULT_BACKGROUND_COLOR.toColorInt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    private val controllerCallbacks = mutableMapOf<android.media.session.MediaSession.Token, Pair<MediaController, MediaController.Callback>>()

    private inner class MediaControllerCallback(private val controller: MediaController) : MediaController.Callback() {
        private var lastState: Int? = null
        private var lastActions: Long? = null

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val newState = state?.state
            val newActions = state?.actions
            if (newState == lastState && newActions == lastActions) return
            
            lastState = newState
            lastActions = newActions

            // If this controller started playing, or if it's the active one, re-evaluate everything
            if (newState == PlaybackState.STATE_PLAYING || controller.sessionToken == activeController?.sessionToken) {
                updateActiveSession()
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            if (controller.sessionToken == activeController?.sessionToken) {
                updateWidget(reprocessImages = true)
            }
        }
        
        override fun onSessionDestroyed() = updateActiveSession()
    }

    override fun onCreate() {
        super.onCreate()
        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { handleCommand(it) }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        controllerCallbacks.values.forEach { (controller, callback) ->
            controller.unregisterCallback(callback)
        }
        controllerCallbacks.clear()
        paletteExecutor.shutdown()
        try {
            mediaSessionManager.removeOnActiveSessionsChangedListener(this)
        } catch (e: SecurityException) { Log.e(TAG, "Failed to remove listener", e) }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(this, serviceComponent)
            updateActiveSession()
        } catch (e: SecurityException) { Log.e(TAG, "Failed to add listener", e) }
    }
    
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        try {
            mediaSessionManager.removeOnActiveSessionsChangedListener(this)
        } catch (e: SecurityException) { Log.e(TAG, "Failed to remove listener on disconnect", e) }
    }

    override fun onActiveSessionsChanged(controllers: List<MediaController>?) = updateActiveSession(controllers)

    private fun updateActiveSession(controllers: List<MediaController>? = null) {
        val currentControllers = controllers ?: try { 
            mediaSessionManager.getActiveSessions(serviceComponent) 
        } catch (e: SecurityException) { emptyList() }

        // 1. Manage Callbacks: Listen to ALL active sessions
        registerCallbacks(currentControllers)

        // 2. Find the best controller to display (Prioritize Playing)
        val newController = currentControllers?.firstOrNull { 
            it.playbackState?.state == PlaybackState.STATE_PLAYING 
        } ?: currentControllers?.firstOrNull()

        if (newController?.sessionToken == activeController?.sessionToken) {
            // Even if controller is same, state might have changed (e.g. Paused), so update widget
            updateWidget(reprocessImages = false)
            return
        }

        activeController = newController
        
        // Clear cache when switching sessions to prevent stale art
        cachedRoundedBitmap?.recycle()
        cachedRoundedBitmap = null
        lastAlbumArtId = null
        
        updateWidget(reprocessImages = true)
    }

    private fun registerCallbacks(controllers: List<MediaController>?) {
        if (controllers == null) return
        
        val newTokens = controllers.map { it.sessionToken }.toSet()
        
        // Remove old callbacks
        val iterator = controllerCallbacks.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in newTokens) {
                entry.value.first.unregisterCallback(entry.value.second)
                iterator.remove()
            }
        }

        // Add new callbacks
        controllers.forEach { controller ->
            if (!controllerCallbacks.containsKey(controller.sessionToken)) {
                val callback = MediaControllerCallback(controller)
                controller.registerCallback(callback, handler)
                controllerCallbacks[controller.sessionToken] = Pair(controller, callback)
            }
        }
    }

    private fun updateWidget(reprocessImages: Boolean = true) {
        val views = RemoteViews(packageName, R.layout.widget_media_control)
        val controller = activeController

        // --- Update UI State ---
        if (controller != null) {
            updateWidgetWithMedia(views, controller, reprocessImages)
        } else {
            updateWidgetNoMedia(views)
        }
        
        // --- Update Controls & Background ---
        updateControls(views, controller)
        views.setInt(R.id.right_container, "setBackgroundColor", currentBackgroundColor)

        try {
            appWidgetManager.updateAppWidget(ComponentName(this, MediaWidgetProvider::class.java), views)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating widget", e)
        }
    }

    private fun updateWidgetWithMedia(views: RemoteViews, controller: MediaController, reprocessImages: Boolean) {
        val metadata = controller.metadata
        val state = controller.playbackState
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        views.setTextViewText(R.id.track_title, title ?: "Unknown Title")
        views.setTextViewText(R.id.track_artist, metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown Artist")
        
        views.setImageViewResource(R.id.btn_play, if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)

        // Control wave animation based on playback state
        if (isPlaying) {
            views.setViewVisibility(R.id.wave_animating, View.VISIBLE)
            views.setViewVisibility(R.id.wave_static, View.GONE)
        } else {
            views.setViewVisibility(R.id.wave_animating, View.GONE)
            views.setViewVisibility(R.id.wave_static, View.VISIBLE)
        }

        val albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        if (reprocessImages) {
            if (albumArt != null) {
                updateAlbumArt(views, albumArt)
            } else {
                setDefaultAlbumArt(views)
            }
        } else {
            if (cachedRoundedBitmap != null) {
                views.setImageViewBitmap(R.id.album_art, cachedRoundedBitmap)
            } else {
                setDefaultAlbumArt(views)
            }
        }
    }

    private fun updateWidgetNoMedia(views: RemoteViews) {
        setDefaultAlbumArt(views)
        currentBackgroundColor = DEFAULT_BACKGROUND_COLOR.toColorInt()
        
        // Hide wave animation when no media is playing
        views.setViewVisibility(R.id.wave_animating, View.GONE)
        views.setViewVisibility(R.id.wave_static, View.INVISIBLE)
        
        views.setTextViewText(R.id.track_title, "No Media Playing")
        views.setTextViewText(R.id.track_artist, "Open a media app")
        views.setImageViewResource(R.id.btn_play, R.drawable.ic_play)
    }

    private fun setDefaultAlbumArt(views: RemoteViews) {
        cachedRoundedBitmap?.recycle()
        cachedRoundedBitmap = null
        lastAlbumArtId = null
        views.setImageViewResource(R.id.album_art, R.drawable.ic_album_placeholder)
    }

    private fun updateAlbumArt(views: RemoteViews, albumArt: Bitmap) {
        val albumArtId = albumArt.generationId
        if (lastAlbumArtId == albumArtId && cachedRoundedBitmap != null) {
            views.setImageViewBitmap(R.id.album_art, cachedRoundedBitmap)
            return
        }

        val scaledArt = scaleBitmapIfNeeded(albumArt)
        cachedRoundedBitmap?.recycle()
        cachedRoundedBitmap = getLeftRoundedBitmap(scaledArt)
        lastAlbumArtId = albumArtId
        views.setImageViewBitmap(R.id.album_art, cachedRoundedBitmap)
        extractAndApplyPalette(scaledArt)
        if (scaledArt != albumArt) scaledArt.recycle()
    }

    private fun extractAndApplyPalette(bitmap: Bitmap) {
        // Create a safe copy for the background thread to own and recycle
        val scaled = Bitmap.createScaledBitmap(bitmap, 100, 100, true)
        val paletteBitmap = if (scaled == bitmap) {
            bitmap.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            scaled
        }

        paletteExecutor.execute {
            val palette = Palette.from(paletteBitmap).generate()
            paletteBitmap.recycle()
            val newColor = palette.darkVibrantSwatch?.rgb ?: palette.vibrantSwatch?.rgb ?: DEFAULT_BACKGROUND_COLOR.toColorInt()
            if (newColor != currentBackgroundColor) {
                handler.post {
                    currentBackgroundColor = newColor
                    updateWidget(reprocessImages = false)
                }
            }
        }
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= MAX_BITMAP_SIZE && bitmap.height <= MAX_BITMAP_SIZE) return bitmap
        val ratio = bitmap.width.toFloat() / bitmap.height
        val (newWidth, newHeight) = if (ratio > 1) MAX_BITMAP_SIZE to (MAX_BITMAP_SIZE / ratio).roundToInt() else (MAX_BITMAP_SIZE * ratio).roundToInt() to MAX_BITMAP_SIZE
        return Bitmap.createScaledBitmap(bitmap, newWidth.coerceAtLeast(1), newHeight.coerceAtLeast(1), true)
    }

    private fun getLeftRoundedBitmap(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val path = Path().apply {
            addRoundRect(
                RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()),
                floatArrayOf(CORNER_RADIUS, CORNER_RADIUS, 0f, 0f, 0f, 0f, CORNER_RADIUS, CORNER_RADIUS),
                Path.Direction.CW
            )
        }
        canvas.clipPath(path)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }

    private fun updateControls(views: RemoteViews, controller: MediaController?) {
        views.setOnClickPendingIntent(R.id.btn_play, getServicePendingIntent(ACTION_PLAY_PAUSE))
        
        val state = controller?.playbackState
        val actions = state?.actions ?: 0L
        val supportsPrev = (actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L
        val supportsNext = (actions and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L

        updateButton(views, R.id.btn_prev, supportsPrev, ACTION_PREV)
        updateButton(views, R.id.btn_next, supportsNext, ACTION_NEXT)
        
        val openAppIntent = getOpenAppIntent(controller)
        views.setOnClickPendingIntent(R.id.album_art, openAppIntent)
    }

    private fun updateButton(views: RemoteViews, viewId: Int, enabled: Boolean, action: String) {
        views.setViewVisibility(viewId, View.VISIBLE)
        views.setInt(viewId, "setImageAlpha", if (enabled) 255 else 77)
        views.setBoolean(viewId, "setEnabled", enabled)
        views.setOnClickPendingIntent(viewId, if (enabled) getServicePendingIntent(action) else null)
    }
    
    private fun getOpenAppIntent(controller: MediaController?): PendingIntent? {
        if (controller == null) return null
        val targetPkg = controller.packageName
        val title = controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)

        // 1. Try to find the app's notification intent (most reliable)
        try {
            val notifications = activeNotifications?.filter { it.packageName == targetPkg }
            val notification = notifications?.find { 
                title != null && it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() == title 
            } ?: notifications?.firstOrNull()
            
            notification?.notification?.contentIntent?.let { return it }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding notification intent", e)
        }

        // 2. Fallback to sessionActivity
        if (controller.sessionActivity != null) {
            return controller.sessionActivity
        }
        
        // 3. Last resort: create a generic launch intent for the app
        val launchIntent = packageManager.getLaunchIntentForPackage(targetPkg)
        return if (launchIntent != null) {
            PendingIntent.getActivity(this, 0, launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            null
        }
    }

    private fun getServicePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MediaListenerService::class.java).setAction(action)
        return PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun handleCommand(action: String) {
        val controller = activeController ?: return
        when (action) {
            ACTION_PLAY_PAUSE -> togglePlayback(controller)
            ACTION_NEXT -> controller.transportControls.skipToNext()
            ACTION_PREV -> controller.transportControls.skipToPrevious()
        }
    }
    
    private fun togglePlayback(controller: MediaController) {
        val controls = controller.transportControls
        if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) controls.pause() else controls.play()
    }

    companion object {
        private const val TAG = "MediaListenerService"
        private const val DEFAULT_BACKGROUND_COLOR = "#2D2D2D"
        private const val CORNER_RADIUS = 28f
        private const val MAX_BITMAP_SIZE = 512
        
        const val ACTION_PLAY_PAUSE = "com.example.mediawidget.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.mediawidget.ACTION_NEXT"
        const val ACTION_PREV = "com.example.mediawidget.ACTION_PREV"
    }
}
