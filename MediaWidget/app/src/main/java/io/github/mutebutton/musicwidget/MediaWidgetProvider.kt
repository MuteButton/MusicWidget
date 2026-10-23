package io.github.mutebutton.musicwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle

class MediaWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = refresh(context)
    override fun onDeleted(context: Context, ids: IntArray) = refresh(context)
    override fun onDisabled(context: Context) = refresh(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action !in setOf(ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREV)) return
        @Suppress("DEPRECATION")
        val token = intent.getParcelableExtra<MediaSession.Token>(EXTRA_SESSION)
        // A delayed tap must never control a different app after its session disappears.
        val controller = WidgetRenderer.controllers(context).firstOrNull { it.sessionToken == token }
        if (controller != null) {
            val state = controller.playbackState
            val actions = state?.actions ?: 0L
            val controls = controller.transportControls
            when (intent.action) {
                ACTION_PLAY_PAUSE -> if (WidgetRenderer.canToggle(state)) {
                    if (WidgetRenderer.isPlaybackActive(state)) controls.pause() else controls.play()
                }
                ACTION_NEXT -> if (actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L) controls.skipToNext()
                ACTION_PREV -> if (actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L) controls.skipToPrevious()
            }
        }
        refresh(context)
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "io.github.mutebutton.musicwidget.PLAY_PAUSE"
        const val ACTION_NEXT = "io.github.mutebutton.musicwidget.NEXT"
        const val ACTION_PREV = "io.github.mutebutton.musicwidget.PREVIOUS"
        const val EXTRA_SESSION = "session"
        fun refresh(context: Context) {
            val service = MediaListenerService.instance
            if (service != null) service.refresh()
            else WidgetRenderer.renderAll(context, WidgetRenderer.chooseController(WidgetRenderer.controllers(context)))
        }
    }
}
