package com.example.mediawidget

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
import android.widget.RemoteViews
import androidx.core.graphics.toColorInt
import androidx.palette.graphics.Palette
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * NotificationListenerService that monitors active media sessions and updates the media widget.
 * Provides playback controls and displays currently playing track information.
 */
class MediaListenerService : NotificationListenerService(),
    MediaSessionManager.OnActiveSessionsChangedListener {

    private var activeController: MediaController? = null
    private lateinit var mediaSessionManager: MediaSessionManager
    private val serviceComponent by lazy(LazyThreadSafetyMode.NONE) {
        ComponentName(this, MediaListenerService::class.java)
    }
    private val appWidgetManager by lazy(LazyThreadSafetyMode.NONE) {
        AppWidgetManager.getInstance(this)
    }
    
    private val paletteExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var cachedRoundedBitmap: Bitmap? = null
    private var lastAlbumArtId: Int? = null
    private var currentBackgroundColor: Int = DEFAULT_BACKGROUND_COLOR.toColorInt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = updateWidget(reprocessImages = false)
        override fun onMetadataChanged(metadata: MediaMetadata?) = updateWidget(reprocessImages = true)
    }

    override fun onCreate() {
        super.onCreate()
        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        updateActiveSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { handleCommand(it) }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSessionManager.removeOnActiveSessionsChangedListener(this)
        activeController?.unregisterCallback(mediaCallback)
        paletteExecutor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
        cachedRoundedBitmap?.recycle()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        runCatching {
            mediaSessionManager.addOnActiveSessionsChangedListener(this, serviceComponent)
        }.onFailure { Log.e(TAG, "Error adding session listener", it) }

        updateActiveSession()
    }

    override fun onActiveSessionsChanged(controllers: List<MediaController>?) {
        updateActiveSession(controllers)
    }

    private fun updateActiveSession(controllers: List<MediaController>? = null) {
        val currentControllers = controllers ?: mediaSessionManager.activeSessions() ?: return

        val newController = currentControllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: currentControllers.firstOrNull() ?: return

        if (activeController == newController) return

        activeController?.unregisterCallback(mediaCallback)
        activeController = newController.also { it.registerCallback(mediaCallback) }

        updateWidget(reprocessImages = true)
    }

    private fun updateWidget(reprocessImages: Boolean = true) {
        val views = RemoteViews(packageName, R.layout.widget_media_control)

        activeController?.let {
            updateWidgetWithMedia(views, it, reprocessImages)
        } ?: updateWidgetNoMedia(views)

        views.setInt(R.id.right_container, "setBackgroundColor", currentBackgroundColor)
        setupClickHandlers(views)
        
        appWidgetManager.updateAppWidget(ComponentName(this, MediaWidgetProvider::class.java), views)
    }

    private fun updateWidgetWithMedia(views: RemoteViews, controller: MediaController, reprocessImages: Boolean) {
        val metadata = controller.metadata
        val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING

        views.setTextViewText(
            R.id.track_title,
            metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown Title"
        )
        views.setTextViewText(
            R.id.track_artist,
            metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown Artist"
        )
        views.setImageViewResource(
            R.id.btn_play,
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )

        val albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        when {
            reprocessImages && albumArt != null -> updateAlbumArt(views, albumArt)
            !reprocessImages && cachedRoundedBitmap != null ->
                views.setImageViewBitmap(R.id.album_art, cachedRoundedBitmap)
            else -> setDefaultAlbumArt(views)
        }
    }

    private fun updateWidgetNoMedia(views: RemoteViews) {
        setDefaultAlbumArt(views)
        currentBackgroundColor = DEFAULT_BACKGROUND_COLOR.toColorInt()
        
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
        val albumArtId = albumArt.generationKey()

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
        val scaledForPalette = Bitmap.createScaledBitmap(bitmap, 100, 100, true)
        paletteExecutor.execute {
            if (paletteExecutor.isShutdown) {
                scaledForPalette.recycle()
                return@execute
            }

            val palette = Palette.from(scaledForPalette)
                .maximumColorCount(16)
                .generate()

            val newColor = palette.darkVibrantSwatch?.rgb
                ?: palette.vibrantSwatch?.rgb
                ?: palette.darkMutedSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: DEFAULT_BACKGROUND_COLOR.toColorInt()

            scaledForPalette.recycle()

            if (newColor != currentBackgroundColor) {
                mainHandler.post {
                    currentBackgroundColor = newColor
                    updateWidget(reprocessImages = false)
                }
            }
        }
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= MAX_BITMAP_SIZE && bitmap.height <= MAX_BITMAP_SIZE) return bitmap

        val ratio = bitmap.width.toFloat() / bitmap.height
        val (newWidth, newHeight) = if (bitmap.width > bitmap.height) {
            MAX_BITMAP_SIZE to (MAX_BITMAP_SIZE / ratio).roundToInt().coerceAtLeast(1)
        } else {
            (MAX_BITMAP_SIZE * ratio).roundToInt().coerceAtLeast(1) to MAX_BITMAP_SIZE
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
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

    private fun setupClickHandlers(views: RemoteViews) {
        views.setOnClickPendingIntent(R.id.btn_play, getPendingIntent(ACTION_PLAY_PAUSE))
        views.setOnClickPendingIntent(R.id.btn_next, getPendingIntent(ACTION_NEXT))
        views.setOnClickPendingIntent(R.id.btn_prev, getPendingIntent(ACTION_PREV))
        views.setOnClickPendingIntent(
            R.id.album_art,
            activeController?.let { getPendingIntent(ACTION_OPEN_APP) }
        )
    }

    private fun getPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MediaListenerService::class.java).setAction(action)
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun handleCommand(intent: Intent) {
        when (intent.action) {
            ACTION_OPEN_APP -> activeController?.packageName?.let { pkg ->
                packageManager.getLaunchIntentForPackage(pkg)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(this)
                }
            }
            else -> {
                val controller = controllerOrRefresh() ?: return
                when (intent.action) {
                    ACTION_PLAY_PAUSE -> togglePlayback(controller)
                    ACTION_NEXT -> controller.transportControls.skipToNext()
                    ACTION_PREV -> controller.transportControls.skipToPrevious()
                }
            }
        }
    }
    
    private fun Bitmap.generationKey(): Int = generationId

    private fun MediaSessionManager.activeSessions(): List<MediaController>? =
        runCatching { getActiveSessions(serviceComponent) }.getOrNull()

    private fun controllerOrRefresh(): MediaController? =
        activeController ?: run {
            updateActiveSession()
            activeController
        }

    private fun togglePlayback(controller: MediaController) {
        val controls = controller.transportControls
        if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
            controls.pause()
        } else {
            controls.play()
        }
    }

    companion object {
        private const val TAG = "MediaWidget"
        private const val DEFAULT_BACKGROUND_COLOR = "#2D2D2D"
        private const val CORNER_RADIUS = 28f
        private const val MAX_BITMAP_SIZE = 512
        
        const val ACTION_PLAY_PAUSE = "com.example.mediawidget.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.mediawidget.ACTION_NEXT"
        const val ACTION_PREV = "com.example.mediawidget.ACTION_PREV"
        const val ACTION_OPEN_APP = "com.example.mediawidget.ACTION_OPEN_APP"
    }
}
