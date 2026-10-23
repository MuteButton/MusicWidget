package com.example.mediawidget.debug

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** ADB-only entry point. This entire source set is omitted from release builds. */
class DebugMediaActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val command = intent.getStringExtra("command") ?: "show"
        if (command == "pin") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AppWidgetManager.getInstance(this).requestPinAppWidget(
                    ComponentName("com.example.mediawidget", "com.example.mediawidget.MediaWidgetProvider"), null, null
                )
            }
        } else if (command != "show") {
            val serviceIntent = Intent(this, DebugMediaService::class.java)
            intent.extras?.let(serviceIntent::putExtras)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
            else startService(serviceIntent)
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            setBackgroundColor(Color.rgb(14, 18, 38))
        }
        layout.addView(TextView(this).apply {
            text = "Media widget test player\n\nDebug build only · silent playback\n\n" +
                "Artwork is synthetic test artwork.\nUse the home-screen widget to test controls."
            textSize = 22f
            setTextColor(Color.WHITE)
        })
        listOf("play", "pause", "next", "previous", "stop").forEach { action ->
            layout.addView(Button(this).apply {
                text = action.replaceFirstChar(Char::uppercase)
                setOnClickListener {
                    startService(Intent(this@DebugMediaActivity, DebugMediaService::class.java)
                        .putExtra("command", action))
                }
            })
        }
        setContentView(layout)
        // Opening the artwork/session intent keeps this visible; ADB setup returns to launcher.
        if (command != "show" && command != "pin" && intent.getBooleanExtra("finish", true)) finish()
    }
}
