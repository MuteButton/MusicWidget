package com.example.mediawidget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.Log
import android.util.LruCache
import android.widget.RemoteViews
import androidx.core.graphics.toColorInt
import androidx.palette.graphics.Palette
import java.util.concurrent.Executors

/**
 * NotificationListenerService that monitors active media sessions and updates the media widget.
 * Provides playback controls and displays currently playing track information.
 */
class MediaListenerService : NotificationListenerService(),
    MediaSessionManager.OnActiveSessionsChangedListener {

    private var activeController: MediaController? = null
    private lateinit var mediaSessionManager: MediaSessionManager
    private val pendingIntentCache = mutableMapOf<String, PendingIntent>()
    private val bitmapCache = LruCache<String, Bitmap>(BITMAP_CACHE_SIZE)
    private val paletteCache = mutableMapOf<String, Int>()
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastMetadataHash: Int? = null

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let { handleCommand(it) }
        }
    }

    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateWidget()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateWidget()
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_NEXT)
            addAction(ACTION_PREV)
            addAction(ACTION_OPEN_APP)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(commandReceiver, filter)
        }

        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        try {
            updateActiveSession()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating session", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(commandReceiver)
        activeController?.unregisterCallback(mediaCallback)
        executor.shutdown()
        bitmapCache.evictAll()
        paletteCache.clear()
        pendingIntentCache.clear()
        try {
            mediaSessionManager.removeOnActiveSessionsChangedListener(this)
        } catch (_: SecurityException) {
            Log.w(TAG, "Permission not granted")
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(
                this,
                ComponentName(this, MediaListenerService::class.java)
            )
            updateActiveSession()
        } catch (_: SecurityException) {
            Log.w(TAG, "Permission not granted")
        }
    }

    override fun onActiveSessionsChanged(controllers: List<MediaController>?) {
        updateActiveSession(controllers)
    }

    private fun updateActiveSession(controllers: List<MediaController>? = null) {
        val currentControllers = controllers ?: try {
            mediaSessionManager.getActiveSessions(ComponentName(this, MediaListenerService::class.java))
        } catch (_: SecurityException) {
            Log.w(TAG, "Permission not granted")
            return
        }

        activeController?.unregisterCallback(mediaCallback)
        activeController = currentControllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: currentControllers.firstOrNull()
        activeController?.registerCallback(mediaCallback)

        updateWidget()
    }

    private fun updateWidget() {
        val controller = activeController
        val views = RemoteViews(packageName, R.layout.widget_media_control)
        val defaultBackgroundColor = DEFAULT_BACKGROUND_COLOR.toColorInt()

        if (controller != null) {
            val metadata = controller.metadata
            val state = controller.playbackState
            
            val currentHash = metadata.hashCode()
            val metadataChanged = currentHash != lastMetadataHash
            lastMetadataHash = currentHash
            
            val albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)

            views.setTextViewText(
                R.id.track_title,
                metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown Title"
            )
            views.setTextViewText(
                R.id.track_artist,
                metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown Artist"
            )

            if (albumArt != null) {
                val cacheKey = "${albumArt.width}x${albumArt.height}_${albumArt.hashCode()}"
                
                val roundedArt = bitmapCache.get(cacheKey) ?: getLeftRoundedBitmap(albumArt).also {
                    bitmapCache.put(cacheKey, it)
                }
                views.setImageViewBitmap(R.id.album_art, roundedArt)

                val cachedColor = paletteCache[cacheKey]
                if (cachedColor != null) {
                    views.setInt(R.id.right_container, "setBackgroundColor", cachedColor)
                } else if (metadataChanged) {
                    executor.execute {
                        val palette = Palette.from(albumArt).generate()
                        val swatch = palette.darkVibrantSwatch ?: palette.vibrantSwatch
                            ?: palette.darkMutedSwatch ?: palette.mutedSwatch
                        val backgroundColor = swatch?.rgb ?: defaultBackgroundColor
                        paletteCache[cacheKey] = backgroundColor
                        
                        mainHandler.post {
                            views.setInt(R.id.right_container, "setBackgroundColor", backgroundColor)
                            pushUpdate(views)
                        }
                    }
                    return
                }
            } else {
                views.setImageViewResource(R.id.album_art, R.drawable.ic_album_placeholder)
                views.setInt(R.id.right_container, "setBackgroundColor", defaultBackgroundColor)
            }

            val isPlaying = state?.state == PlaybackState.STATE_PLAYING
            views.setImageViewResource(R.id.btn_play, if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        } else {
            lastMetadataHash = null
            views.setTextViewText(R.id.track_title, "No Media Playing")
            views.setTextViewText(R.id.track_artist, "Open a media app")
            views.setImageViewResource(R.id.album_art, R.drawable.ic_album_placeholder)
            views.setInt(R.id.right_container, "setBackgroundColor", defaultBackgroundColor)
            views.setImageViewResource(R.id.btn_play, R.drawable.ic_play)
        }

        views.setOnClickPendingIntent(R.id.btn_play, getCachedPendingIntent(ACTION_PLAY_PAUSE))
        views.setOnClickPendingIntent(R.id.btn_next, getCachedPendingIntent(ACTION_NEXT))
        views.setOnClickPendingIntent(R.id.btn_prev, getCachedPendingIntent(ACTION_PREV))
        views.setOnClickPendingIntent(R.id.album_art, getCachedPendingIntent(ACTION_OPEN_APP))

        pushUpdate(views)
    }

    private fun pushUpdate(views: RemoteViews) {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val thisWidget = ComponentName(this, MediaWidgetProvider::class.java)
        appWidgetManager.updateAppWidget(thisWidget, views)
    }

    private fun getLeftRoundedBitmap(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())

        val path = Path()
        path.addRoundRect(
            rect,
            floatArrayOf(CORNER_RADIUS, CORNER_RADIUS, 0f, 0f, 0f, 0f, CORNER_RADIUS, CORNER_RADIUS),
            Path.Direction.CW
        )
        canvas.clipPath(path)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }


    private fun getCachedPendingIntent(action: String): PendingIntent {
        return pendingIntentCache.getOrPut(action) {
            val intent = Intent(action)
            intent.setPackage(packageName)
            val requestCode = when (action) {
                ACTION_PLAY_PAUSE -> 1
                ACTION_NEXT -> 2
                ACTION_PREV -> 3
                ACTION_OPEN_APP -> 4
                else -> 0
            }
            PendingIntent.getBroadcast(
                this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    private fun handleCommand(intent: Intent) {
        when (intent.action) {
            ACTION_OPEN_APP -> handleOpenApp()
            else -> handleMediaControl(intent)
        }
    }

    private fun handleOpenApp() {
        val controller = activeController
        if (controller != null) {
            openMediaApp(controller.packageName)
        } else {
            openYouTubeMusic()
        }
    }

    private fun openMediaApp(packageName: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            launchIntent?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(this)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening media app", e)
        }
    }

    private fun openYouTubeMusic() {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(YOUTUBE_MUSIC_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            val browsers = packageManager.queryIntentActivities(browserIntent, 0)
                .filter { 
                    val pkg = it.activityInfo.packageName
                    !pkg.contains("music", ignoreCase = true) && 
                    !pkg.contains("youtube", ignoreCase = true)
                }
            
            if (browsers.isNotEmpty()) {
                browserIntent.setPackage(browsers.first().activityInfo.packageName)
                startActivity(browserIntent)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Could not open YouTube Music in browser")
        }
    }

    private fun handleMediaControl(intent: Intent) {
        val controller = activeController ?: run {
            updateActiveSession()
            activeController
        } ?: return

        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
                    controller.transportControls.pause()
                } else {
                    controller.transportControls.play()
                }
            }
            ACTION_NEXT -> controller.transportControls.skipToNext()
            ACTION_PREV -> controller.transportControls.skipToPrevious()
        }
    }

    companion object {
        private const val TAG = "MediaWidget"
        private const val BITMAP_CACHE_SIZE = 4 * 1024 * 1024
        private const val DEFAULT_BACKGROUND_COLOR = "#2D2D2D"
        private const val YOUTUBE_MUSIC_URL = "https://music.youtube.com"
        private const val CORNER_RADIUS = 28f
        
        const val ACTION_PLAY_PAUSE = "com.example.mediawidget.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.mediawidget.ACTION_NEXT"
        const val ACTION_PREV = "com.example.mediawidget.ACTION_PREV"
        const val ACTION_OPEN_APP = "com.example.mediawidget.ACTION_OPEN_APP"
    }
}
