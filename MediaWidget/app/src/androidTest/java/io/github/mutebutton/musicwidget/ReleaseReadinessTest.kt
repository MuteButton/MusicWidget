package io.github.mutebutton.musicwidget

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
        assertEquals("io.github.mutebutton.musicwidget", context.packageName)
        assertEquals(36, context.applicationInfo.targetSdkVersion)
        assertEquals(24, context.applicationInfo.minSdkVersion)
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty()
        assertFalse("Widget must not gain network access silently", permissions.contains("android.permission.INTERNET"))
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
        val settings = "sharedpref" to "media_widget.xml"
        assertEquals(listOf(settings), includes(R.xml.backup_rules))
        assertEquals(listOf(settings, settings), includes(R.xml.data_extraction_rules))
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
            onView(withId(R.id.privacy_button)).check(matches(isDisplayed())).perform(click())
            onView(withId(android.R.id.message)).check(matches(withText(containsString("Developer: MuteButton"))))
            onView(withId(android.R.id.button1)).check(matches(isDisplayed())).perform(click())
        }
    }
}
