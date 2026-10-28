package io.github.mutebutton.oncue

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.view.View
import android.text.InputType
import android.widget.EditText
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.setup_root)) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(findViewById(R.id.setup_root))
        findViewById<View>(R.id.battery_device_steps).visibility =
            if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.battery_settings_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.privacy_button).setOnClickListener {
            AlertDialog.Builder(this).setTitle(R.string.privacy_title)
                .setMessage(R.string.privacy_policy)
                .setPositiveButton(android.R.string.ok, null).show()
        }
        findViewById<Button>(R.id.grant_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<Button>(R.id.add_widget_button).setOnClickListener {
            val manager = getSystemService(AppWidgetManager::class.java)
            if (Build.VERSION.SDK_INT >= 26 && manager.isRequestPinAppWidgetSupported) {
                manager.requestPinAppWidget(ComponentName(this, OnCueWidgetProvider::class.java), null, null)
                Toast.makeText(this, R.string.setup_pin_requested, Toast.LENGTH_LONG).show()
            } else Toast.makeText(this, R.string.setup_manual_widget, Toast.LENGTH_LONG).show()
        }
        findViewById<Button>(R.id.choose_player_button).setOnClickListener { choosePlayer(false) }
        findViewById<Button>(R.id.browser_url_button).setOnClickListener { editBrowserUrl() }
        findViewById<Button>(R.id.color_mode_button).setOnClickListener {
            val preferences = getSharedPreferences(WidgetRenderer.PREFS, MODE_PRIVATE)
            AlertDialog.Builder(this).setTitle(R.string.glass_colors)
                .setSingleChoiceItems(arrayOf(getString(R.string.classic_glass), getString(R.string.artwork_glass)),
                    if (preferences.getBoolean(ArtworkColors.PREFERENCE, true)) 1 else 0) { dialog, choice ->
                    preferences.edit().putBoolean(ArtworkColors.PREFERENCE, choice == 1).apply()
                    updateUi()
                    OnCueWidgetProvider.refresh(this)
                    dialog.dismiss()
                }.setNegativeButton(android.R.string.cancel, null).show()
        }
        if (intent.action == ACTION_OPEN_PLAYER) openPlayer()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_OPEN_PLAYER) openPlayer()
    }

    override fun onResume() {
        super.onResume()
        updateUi()
        OnCueWidgetProvider.refresh(this)
    }

    private fun updateUi() {
        val access = WidgetRenderer.hasAccess(this)
        findViewById<TextView>(R.id.status_text).setText(if (access) R.string.setup_ready else R.string.setup_permission)
        findViewById<Button>(R.id.grant_button).setText(if (access) R.string.setup_manage_access else R.string.grant_permission)
        findViewById<Button>(R.id.add_widget_button).isEnabled = access
        val preferences = getSharedPreferences(WidgetRenderer.PREFS, MODE_PRIVATE)
        val selected = preferences.getString(WidgetRenderer.PREFERRED_PLAYER, null)
        val browser = selected != null && isBrowser(selected)
        findViewById<Button>(R.id.browser_url_button).visibility = if (browser) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.browser_url_status).apply {
            visibility = if (browser) View.VISIBLE else View.GONE
            text = selected?.let { preferences.getString(BrowserUrl.key(it), null) }
                ?.takeIf { it.isNotEmpty() } ?: getString(R.string.browser_url_default)
        }
        findViewById<Button>(R.id.color_mode_button).text = getString(R.string.glass_colors_selected,
            getString(if (preferences.getBoolean(ArtworkColors.PREFERENCE, true)) R.string.artwork_glass else R.string.classic_glass))
        findViewById<TextView>(R.id.player_status).text = if (selected == null) getString(R.string.setup_player_automatic)
            else getString(R.string.setup_player_selected, WidgetRenderer.appLabel(this, selected))
    }

    private fun openPlayer() {
        // A cached idle click or an active-player launch fallback can arrive here.
        // Resolve the current session before considering any saved default/URL.
        val active = WidgetRenderer.chooseController(WidgetRenderer.controllers(this))
        if (active != null) {
            val destinations = listOfNotNull(active.sessionActivity,
                MediaListenerService.instance?.mediaDestination(active)).distinct()
            for (destination in destinations) {
                try {
                    val options = android.app.ActivityOptions.makeBasic()
                    if (Build.VERSION.SDK_INT >= 34) {
                        options.setPendingIntentBackgroundActivityStartMode(
                            if (Build.VERSION.SDK_INT >= 36)
                                android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                            else android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    }
                    destination.send(this, 0, null, null, null, null, options.toBundle())
                    finish()
                    return
                } catch (_: android.app.PendingIntent.CanceledException) { }
                  catch (_: SecurityException) { }
            }
            val launch = packageManager.getLaunchIntentForPackage(active.packageName)
            if (launch != null) {
                try {
                    startActivity(launch)
                    finish()
                    return
                } catch (_: android.content.ActivityNotFoundException) { }
                  catch (_: SecurityException) { }
            }
            // Never substitute a different player or leave setup on screen when
            // the selected session cannot provide a launchable destination.
            Toast.makeText(this, R.string.active_player_unavailable, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val preferences = getSharedPreferences(WidgetRenderer.PREFS, MODE_PRIVATE)
        val packages = listOfNotNull(preferences.getString(WidgetRenderer.PREFERRED_PLAYER, null),
            preferences.getString(WidgetRenderer.LAST_PLAYER, null))
        for (pkg in packages.distinct().filter { it != packageName }) {
            val url = preferences.getString(BrowserUrl.key(pkg), null)?.let(BrowserUrl::normalize)
            if (!url.isNullOrEmpty() && isBrowser(pkg)) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addCategory(Intent.CATEGORY_BROWSABLE).setPackage(pkg))
                    finish()
                    return
                } catch (_: android.content.ActivityNotFoundException) { }
                  catch (_: SecurityException) { }
            }
            val launch = packageManager.getLaunchIntentForPackage(pkg) ?: continue
            try {
                startActivity(launch)
                finish()
                return
            } catch (_: android.content.ActivityNotFoundException) { }
        }
        choosePlayer(true)
    }

    private fun isBrowser(pkg: String): Boolean = packageManager.queryIntentActivities(
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE).setPackage(pkg),
        android.content.pm.PackageManager.MATCH_ALL or android.content.pm.PackageManager.GET_RESOLVED_FILTER
    ).any {
        // A browser handles arbitrary web hosts, unlike apps with domain-specific deep links.
        it.activityInfo != null && it.filter?.let { filter ->
            filter.hasCategory(Intent.CATEGORY_BROWSABLE) && filter.countDataAuthorities() == 0
        } == true
    }

    private fun editBrowserUrl() {
        val preferences = getSharedPreferences(WidgetRenderer.PREFS, MODE_PRIVATE)
        val pkg = preferences.getString(WidgetRenderer.PREFERRED_PLAYER, null) ?: return
        if (!isBrowser(pkg)) return
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            hint = "https://music.youtube.com"
            setSingleLine(true)
            setText(preferences.getString(BrowserUrl.key(pkg), ""))
        }
        val dialog = AlertDialog.Builder(this).setTitle(R.string.browser_url_title)
            .setMessage(R.string.browser_url_help).setView(input)
            .setPositiveButton(R.string.browser_url_save, null)
            .setNegativeButton(android.R.string.cancel, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val url = BrowserUrl.normalize(input.text.toString())
                if (url == null) {
                    input.error = getString(R.string.browser_url_invalid)
                } else {
                    preferences.edit().apply {
                        if (url.isEmpty()) remove(BrowserUrl.key(pkg)) else putString(BrowserUrl.key(pkg), url)
                    }.apply()
                    updateUi()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
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
                OnCueWidgetProvider.refresh(this)
                if (openAfterSelection) openPlayer()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object { const val ACTION_OPEN_PLAYER = "io.github.mutebutton.oncue.OPEN_PLAYER" }
}
