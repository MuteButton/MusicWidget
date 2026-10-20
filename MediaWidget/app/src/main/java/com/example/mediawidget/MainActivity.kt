package com.example.mediawidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.grant_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<Button>(R.id.add_widget_button).setOnClickListener {
            val manager = getSystemService(AppWidgetManager::class.java)
            if (Build.VERSION.SDK_INT >= 26 && manager.isRequestPinAppWidgetSupported) {
                manager.requestPinAppWidget(ComponentName(this, MediaWidgetProvider::class.java), null, null)
                Toast.makeText(this, R.string.setup_pin_requested, Toast.LENGTH_LONG).show()
            } else Toast.makeText(this, R.string.setup_manual_widget, Toast.LENGTH_LONG).show()
        }
        findViewById<Button>(R.id.choose_player_button).setOnClickListener { choosePlayer(false) }
        findViewById<Button>(R.id.color_mode_button).setOnClickListener {
            val preferences = getSharedPreferences(WidgetRenderer.PREFS, MODE_PRIVATE)
            AlertDialog.Builder(this).setTitle(R.string.glass_colors)
                .setSingleChoiceItems(arrayOf(getString(R.string.classic_glass), getString(R.string.artwork_glass)),
                    if (preferences.getBoolean(ArtworkColors.PREFERENCE, false)) 1 else 0) { dialog, choice ->
                    preferences.edit().putBoolean(ArtworkColors.PREFERENCE, choice == 1).apply()
                    updateUi()
                    MediaWidgetProvider.refresh(this)
                    dialog.dismiss()
                }.setNegativeButton(android.R.string.cancel, null).show()
        }
        if (savedInstanceState == null && intent.action == ACTION_OPEN_PLAYER) openPlayer()
    }

    override fun onResume() {
        super.onResume()
        updateUi()
        MediaWidgetProvider.refresh(this)
    }

    private fun updateUi() {
        val access = WidgetRenderer.hasAccess(this)
        findViewById<TextView>(R.id.status_text).setText(if (access) R.string.setup_ready else R.string.setup_permission)
        findViewById<Button>(R.id.grant_button).setText(if (access) R.string.setup_manage_access else R.string.grant_permission)
        findViewById<Button>(R.id.add_widget_button).isEnabled = access
        val preferences = getSharedPreferences(WidgetRenderer.PREFS, MODE_PRIVATE)
        val selected = preferences.getString(WidgetRenderer.PREFERRED_PLAYER, null)
        findViewById<Button>(R.id.color_mode_button).text = getString(R.string.glass_colors_selected,
            getString(if (preferences.getBoolean(ArtworkColors.PREFERENCE, false)) R.string.artwork_glass else R.string.classic_glass))
        findViewById<TextView>(R.id.player_status).text = if (selected == null) getString(R.string.setup_player_automatic)
            else getString(R.string.setup_player_selected, WidgetRenderer.appLabel(this, selected))
    }

    private fun openPlayer() {
        val preferences = getSharedPreferences(WidgetRenderer.PREFS, MODE_PRIVATE)
        val packages = listOfNotNull(preferences.getString(WidgetRenderer.PREFERRED_PLAYER, null),
            preferences.getString(WidgetRenderer.LAST_PLAYER, null))
        for (pkg in packages.distinct().filter { it != packageName }) {
            val launch = packageManager.getLaunchIntentForPackage(pkg) ?: continue
            try {
                startActivity(launch)
                finish()
                return
            } catch (_: android.content.ActivityNotFoundException) { }
        }
        choosePlayer(true)
    }

    private fun choosePlayer(openAfterSelection: Boolean) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        if (apps.isEmpty()) {
            Toast.makeText(this, R.string.setup_no_players, Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.setup_choose_player)
            .setItems(apps.map { it.loadLabel(packageManager).toString() }.toTypedArray()) { _, index ->
                val pkg = apps[index].activityInfo.packageName
                getSharedPreferences(WidgetRenderer.PREFS, MODE_PRIVATE).edit()
                    .putString(WidgetRenderer.PREFERRED_PLAYER, pkg).apply()
                updateUi()
                MediaWidgetProvider.refresh(this)
                if (openAfterSelection) openPlayer()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object { const val ACTION_OPEN_PLAYER = "com.example.mediawidget.OPEN_PLAYER" }
}
