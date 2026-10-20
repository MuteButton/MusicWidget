package com.example.mediawidget

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.content.ContextCompat

/** Observes sessions; commands use a receiver so they also work after process death. */
class MediaListenerService : NotificationListenerService(),
    MediaSessionManager.OnActiveSessionsChangedListener {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var sessions: MediaSessionManager
    private var active: MediaController? = null
    private var connected = false
    private val callbacks = mutableMapOf<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private val tick = object : Runnable {
        override fun run() {
            if (!shouldTick()) return
            active?.let { WidgetRenderer.updateProgress(this@MediaListenerService, it) }
            if (shouldTick()) handler.postDelayed(this, nextTickDelay())
        }
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                handler.removeCallbacks(tick)
                active?.let { WidgetRenderer.updateProgress(this@MediaListenerService, it) }
            }
            else refresh()
        }
    }

    override fun onCreate() {
        super.onCreate()
        sessions = getSystemService(MediaSessionManager::class.java)
        ContextCompat.registerReceiver(this, screenReceiver,
            IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) },
            ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        instance = this
        try {
            sessions.addOnActiveSessionsChangedListener(this,
                ComponentName(this, MediaListenerService::class.java), handler)
            refresh()
        } catch (error: SecurityException) {
            Log.w(TAG, "Notification access unavailable", error)
            disconnect()
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        disconnect()
        WidgetRenderer.renderAll(this, null)
        if (WidgetRenderer.hasAccess(this)) requestRebind(ComponentName(this, MediaListenerService::class.java))
    }

    override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) = refresh(controllers)

    internal fun refresh(controllers: List<MediaController>? = null, destroyed: MediaSession.Token? = null) {
        if (!connected) return
        val current = (controllers ?: WidgetRenderer.controllers(this)).filter { it.sessionToken != destroyed }
        val tokens = current.map { it.sessionToken }.toSet()
        callbacks.keys.filter { it !in tokens }.forEach { token ->
            callbacks.remove(token)?.let { (controller, callback) -> controller.unregisterCallback(callback) }
        }
        current.forEach { controller ->
            if (controller.sessionToken !in callbacks) {
                val callback = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: android.media.MediaMetadata?) = refresh()
                    // Position-only changes (seeks) must update even if state/actions are unchanged.
                    override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) = refresh()
                    override fun onSessionDestroyed() = refresh(destroyed = controller.sessionToken)
                }
                controller.registerCallback(callback, handler)
                callbacks[controller.sessionToken] = controller to callback
            }
        }
        active = WidgetRenderer.chooseController(current, active?.sessionToken)
        WidgetRenderer.renderAll(this, active)
        handler.removeCallbacks(tick)
        if (shouldTick()) handler.postDelayed(tick, nextTickDelay())
    }

    private fun shouldTick(): Boolean = connected && WidgetRenderer.isAdvancing(active?.playbackState) &&
        (active?.metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: 0) > 0 &&
        getSystemService(PowerManager::class.java).isInteractive && WidgetRenderer.widgetIds(this).isNotEmpty()

    private fun nextTickDelay(): Long {
        val state = active?.playbackState ?: return 1_000
        val duration = active?.metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: return 1_000
        val position = PlaybackMath.position(state.position, state.lastPositionUpdateTime,
            SystemClock.elapsedRealtime(), state.playbackSpeed, WidgetRenderer.isAdvancing(state), duration)
        return if (position < duration && state.playbackSpeed > 0)
            ((duration - position) / state.playbackSpeed).toLong().coerceIn(100, 1_000) else 1_000
    }

    private fun disconnect() {
        connected = false
        if (instance === this) instance = null
        handler.removeCallbacksAndMessages(null)
        callbacks.values.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
        callbacks.clear()
        active = null
        try { sessions.removeOnActiveSessionsChangedListener(this) } catch (_: SecurityException) { }
    }

    override fun onDestroy() {
        disconnect()
        unregisterReceiver(screenReceiver)
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MediaListenerService"
        internal var instance: MediaListenerService? = null
            private set
    }
}
