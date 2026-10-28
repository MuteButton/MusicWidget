package io.github.mutebutton.oncue

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.hamcrest.Matchers.containsString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/** Runs on real Android runtimes in CI; no accounts, media service or network required. */
@RunWith(AndroidJUnit4::class)
class ReleaseReadinessTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun identityAndPermissionsAreSuitableForDistribution() {
        assertEquals("io.github.mutebutton.oncue", context.packageName)
        assertEquals(36, context.applicationInfo.targetSdkVersion)
        assertEquals(24, context.applicationInfo.minSdkVersion)
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty()
        assertFalse("Widget must not gain network access silently", permissions.contains("android.permission.INTERNET"))
    }

    @Test fun launcherIconsResolveOnEverySupportedApi() {
        // loadIcon can silently return Android's default icon: resolve directly.
        val info = context.applicationInfo
        assertEquals("OnCue", context.packageManager.getApplicationLabel(info).toString())
        assertTrue(info.icon != 0)
        assertNotNull(context.getDrawable(info.icon))
        assertNotNull(context.getDrawable(R.mipmap.ic_launcher_round))
    }

    @Test fun renamedComponentsAreRegistered() {
        val manager = context.packageManager
        val listener = manager.getServiceInfo(
            android.content.ComponentName(context, MediaListenerService::class.java), 0)
        assertEquals("io.github.mutebutton.oncue.MediaListenerService", listener.name)
        assertEquals("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", listener.permission)
        val provider = manager.getReceiverInfo(
            android.content.ComponentName(context, OnCueWidgetProvider::class.java), PackageManager.GET_META_DATA)
        assertEquals("io.github.mutebutton.oncue.OnCueWidgetProvider", provider.name)
        assertEquals(R.xml.oncue_widget_info, provider.metaData.getInt("android.appwidget.provider"))
    }

    @Test fun modernWidgetDefaultsToFourByOne() {
        if (Build.VERSION.SDK_INT < 31) return
        context.resources.getXml(R.xml.oncue_widget_info).use { xml ->
            while (xml.eventType != XmlPullParser.START_TAG) xml.next()
            val android = "http://schemas.android.com/apk/res/android"
            assertEquals("4", xml.getAttributeValue(android, "targetCellWidth"))
            assertEquals("1", xml.getAttributeValue(android, "targetCellHeight"))
        }
    }

    @Test fun backupIncludesOnlySettingsOnOldAndNewAndroid() {
        fun includes(resource: Int): List<Pair<String?, String?>> {
            val result = mutableListOf<Pair<String?, String?>>()
            context.resources.getXml(resource).use { xml ->
                while (xml.eventType != XmlPullParser.END_DOCUMENT) {
                    if (xml.eventType == XmlPullParser.START_TAG && xml.name == "include")
                        result.add(xml.getAttributeValue(null, "domain") to xml.getAttributeValue(null, "path"))
                    xml.next()
                }
            }
            return result
        }
        val settings = "sharedpref" to "${WidgetRenderer.PREFS}.xml"
        assertEquals(listOf(settings), includes(R.xml.backup_rules))
        assertEquals(listOf(settings, settings), includes(R.xml.data_extraction_rules))
    }

    @Test fun setupExplainsAccessAndPlacesBatteryBeforeCustomization() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                val help = activity.findViewById<android.widget.TextView>(R.id.notification_help)
                assertEquals(View.VISIBLE, help.visibility)
                assertTrue(help.text.contains("does not save notification messages"))
                val battery = activity.findViewById<View>(R.id.battery_section)
                assertEquals(View.VISIBLE, battery.visibility)
                assertEquals(if (Build.MANUFACTURER.equals("samsung", true)) View.VISIBLE else View.GONE,
                    activity.findViewById<View>(R.id.battery_device_steps).visibility)
                val parent = battery.parent as android.view.ViewGroup
                assertTrue(parent.indexOfChild(battery) > parent.indexOfChild(activity.findViewById(R.id.grant_button)))
                assertTrue(parent.indexOfChild(battery) < parent.indexOfChild(activity.findViewById(R.id.add_widget_button)))
                assertTrue(parent.indexOfChild(battery) < parent.indexOfChild(activity.findViewById(R.id.color_mode_button)))
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            context.openFileOutput("setup-guidance.png", Context.MODE_PRIVATE).use {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            screenshot.recycle()
            onView(withId(R.id.setup_root)).perform(swipeUp())
            onView(withId(R.id.battery_settings_button)).check(matches(isDisplayed()))
        }
    }

    @Test fun setupButtonsOpenRealSystemSettings() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun activities(): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("dumpsys activity activities")
        ).bufferedReader().use { it.readText() }
        for (button in listOf(R.id.grant_button, R.id.battery_settings_button)) {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.onActivity { activity ->
                    activity.findViewById<Button>(button).performClick()
                }
                val deadline = android.os.SystemClock.elapsedRealtime() + 10000
                var dump = activities()
                while (!dump.lineSequence().any { it.contains("ResumedActivity") && it.contains("com.android.settings") }
                    && android.os.SystemClock.elapsedRealtime() < deadline) {
                    android.os.SystemClock.sleep(200)
                    dump = activities()
                }
                assertTrue("System settings must be foreground", dump.lineSequence().any {
                    it.contains("ResumedActivity") && it.contains("com.android.settings") })
                if (button == R.id.battery_settings_button) {
                    assertTrue("App details must target OnCue", dump.contains("package:${context.packageName}"))
                }
                // Settings is another UID: ordinary instrumentation key injection is
                // rejected on older Android. Use the UI automation shell instead.
                android.os.ParcelFileDescriptor.AutoCloseInputStream(
                    instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK")
                ).use { it.readBytes() }
                instrumentation.waitForIdleSync()
            }
        }
    }

    @Test fun privacyIsReachableAndSetupStaysInsideSystemInsets() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN)).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.findViewById<View>(R.id.setup_root)
                if (Build.VERSION.SDK_INT >= 35) {
                    val bars = root.rootWindowInsets.getInsets(android.view.WindowInsets.Type.systemBars())
                    assertTrue("Setup overlaps status bar", root.paddingTop >= bars.top)
                    assertTrue("Setup overlaps navigation bar", root.paddingBottom >= bars.bottom)
                }
                assertTrue(activity.findViewById<Button>(R.id.grant_button).isEnabled)
            }
            // Exercise a user gesture; requestRectangleOnScreen-based scrollTo can
            // stop underneath system insets on Android 16's edge-to-edge window.
            onView(withId(R.id.setup_root)).perform(swipeUp())
            scenario.onActivity { activity ->
                activity.findViewById<android.widget.ScrollView>(R.id.setup_root).fullScroll(View.FOCUS_DOWN)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            onView(withId(R.id.privacy_button)).check(matches(isDisplayed())).perform(click())
            onView(withId(android.R.id.message)).check(matches(withText(containsString("Developer: MuteButton"))))
            onView(withId(android.R.id.button1)).check(matches(isDisplayed())).perform(click())
        }
    }
}
