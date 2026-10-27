package io.github.mutebutton.musicwidget.debug

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.RemoteViews
import io.github.mutebutton.musicwidget.R
import io.github.mutebutton.musicwidget.V5Artwork
import io.github.mutebutton.musicwidget.WidgetRenderer

/** Isolates native-resolution rasterization from the launcher's resizing.
 * This is a diagnostic RemoteViews host, not a home-screen acceptance capture.
 */
class IdleReferenceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < 31) { finish(); return }
        val host = FrameLayout(this)
        if (intent.getBooleanExtra("playing", false)) {
            val controller = WidgetRenderer.chooseController(WidgetRenderer.controllers(this))
                ?: error("Start the real media fixture before capturing playing state")
            val id = WidgetRenderer.widgetIds(this).first()
            val liveViews = WidgetRenderer.createViews(this, id, controller,
                widthOverride = 1030f / resources.displayMetrics.density)
            host.addView(liveViews.apply(this, host), FrameLayout.LayoutParams(1030,225,Gravity.CENTER))
            setContentView(host)
            return
        }
        val views = RemoteViews(packageName, R.layout.widget_media_v5)
        V5Artwork.bind(this, views, 1030f / resources.displayMetrics.density, false, true)
        views.setTextViewText(R.id.idle_title, "No Media Playing")
        views.setTextViewText(R.id.idle_subtitle, "Open a media app")
        views.setTextViewText(R.id.btn_open, "Open App")
        host.addView(views.apply(this, host), FrameLayout.LayoutParams(1030,225,Gravity.CENTER))
        setContentView(host)
    }
}
