package io.github.mutebutton.musicwidget.debug

import android.app.Activity
import android.graphics.*
import android.os.Bundle
import android.os.Build
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import android.util.TypedValue
import io.github.mutebutton.musicwidget.R
import io.github.mutebutton.musicwidget.V5Artwork
import org.json.JSONObject
import java.io.File

/** Offline typography measurement. Never used by production rendering or acceptance captures. */
class VisualCalibrationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < 31) { finish(); return }
        Thread {
            val reference = BitmapFactory.decodeResource(resources, R.drawable.v5_playing_skin_source)
            val result = JSONObject()
            if (!intent.getBooleanExtra("timeOnly", false)) {
            result.put("title", calibrate(reference, "Afterglow", R.id.track_title, 307, 48, 291, 42,
                75..81, 29..34, intArrayOf(400,500,600,700)))
            result.put("artist", calibrate(reference, "The Midnight", R.id.track_artist, 307, 90, 298, 43,
                111..119, 24..28, intArrayOf(400,500)))
            }
            result.put("elapsed", calibrate(reference, "1:24", R.id.elapsed_time, 307, 170, 105, 39,
                187..193, 18..23, intArrayOf(400,500)))
            File(filesDir, "visual-calibration.json").writeText(result.toString(2))
            runOnUiThread { finish() }
        }.start()
    }

    @androidx.annotation.RequiresApi(31)
    private fun calibrate(reference: Bitmap, text: String, id: Int, left: Int, top: Int, width: Int, height: Int,
                          baselines: IntRange, sizes: IntRange, weights: IntArray): JSONObject {
        // Use actual production RemoteViews/TextViews at the measured launcher size.
        // Paint-only calibration does not reproduce TextView hinting/layout.
        val scale = 979f / 1030f
        val host = FrameLayout(this)
        val rv = RemoteViews(packageName, R.layout.widget_media_v5)
        V5Artwork.bind(this, rv, 979f/resources.displayMetrics.density, true, true)
        rv.setViewVisibility(R.id.media_state, View.VISIBLE)
        rv.setViewVisibility(R.id.idle_state, View.GONE)
        val root = rv.apply(this, host)
        val label = root.findViewById<TextView>(id)
        label.text = text
        val bitmap = Bitmap.createBitmap(979,214,Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val expected = Bitmap.createScaledBitmap(reference,979,214,true)
        val rx = (left*scale).toInt(); val ry=(top*scale).toInt()
        val rw=(width*scale).toInt(); val rh=(height*scale).toInt()
        val target=IntArray(rw*rh); val pixels=IntArray(rw*rh)
        expected.getPixels(target,0,rw,rx,ry,rw,rh)
        val ws=View.MeasureSpec.makeMeasureSpec(979,View.MeasureSpec.EXACTLY)
        val hs=View.MeasureSpec.makeMeasureSpec(214,View.MeasureSpec.EXACTLY)
        var best = Double.MAX_VALUE
        var record = JSONObject()
        var tested = 0
        for (family in arrayOf("sans-serif","sans-serif-condensed")) for (weight in weights) {
            label.typeface = Typeface.create(Typeface.create(family, Typeface.NORMAL), weight, false)
            for (sizeStep in sizes.first*2..sizes.last*2) for (scaleStep in 49..(if(id==R.id.elapsed_time)63 else 57)) for (baseline in baselines) {
                label.setTextSize(TypedValue.COMPLEX_UNIT_PX,sizeStep / 2f * scale)
                label.textScaleX = scaleStep / 50f
                root.measure(ws,hs); root.layout(0,0,979,214)
                val relativeTop: Int
                if(id == R.id.elapsed_time) {
                    val row=root.findViewById<View>(R.id.time_row)
                    val params=row.layoutParams as android.widget.LinearLayout.LayoutParams
                    relativeTop=(baseline*scale+0.5f).toInt()-root.findViewById<View>(R.id.progress_container).top-root.findViewById<View>(R.id.progress_bar).height-label.baseline
                    params.topMargin=relativeTop
                    row.layoutParams=params
                } else {
                    val params=label.layoutParams as FrameLayout.LayoutParams
                    relativeTop=(baseline*scale+0.5f).toInt() - root.findViewById<View>(R.id.text_info).top - label.baseline
                    params.topMargin=relativeTop
                    label.layoutParams=params
                }
                root.measure(ws,hs); root.layout(0,0,979,214)
                root.draw(canvas)
                bitmap.getPixels(pixels,0,rw,rx,ry,rw,rh)
                var error = 0.0
                for (i in pixels.indices) {
                    val a=pixels[i]; val b=target[i]
                    error += kotlin.math.abs(((a shr 16) and 255)-((b shr 16) and 255)) +
                        kotlin.math.abs(((a shr 8) and 255)-((b shr 8) and 255)) +
                        kotlin.math.abs((a and 255)-(b and 255))
                }
                tested++
                if (error < best) {
                    best=error
                    record=JSONObject().put("family",family).put("weight",weight).put("size",sizeStep/2f)
                        .put("scaleX",label.textScaleX).put("baseline",baseline)
                        .put("relativeTop",relativeTop/scale)
                        .put("meanRgbError",error/(pixels.size*3))
                }
            }
        }
        return record.put("candidates",tested)
    }
}
