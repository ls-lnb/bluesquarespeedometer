package net.nhiroki.bluesquarespeedometer

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import android.preference.PreferenceManager
import android.provider.Settings
import android.view.View
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.TimeZone


@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    var context: Context? = null

    @Before
    fun setUp() {
        this.context = InstrumentationRegistry.getInstrumentation().targetContext

        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context!!.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList(Locale("en", "US"))
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(Locale("en", "US")))
        }
        Locale.setDefault(Locale("en", "US"))
    }

    @Test
    fun testJustStartingUp() {
        // Just test app starts without problems loading libraries
        ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(3000)

        // Not sure why failing...
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 24)

        Espresso.onView(ViewMatchers.withText("Speed"))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun testKeepScreenOnOption() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(3000)

        Assume.assumeTrue(Build.VERSION.SDK_INT >= 24)

        // The screen on CI is small; scroll the config section into view
        scrollMainViewToBottom(scenario)

        // Default of the option is "On", matching the historic behavior
        Espresso.onView(ViewMatchers.withId(R.id.main_activity_config_keep_screen_on_textview))
            .check(ViewAssertions.matches(ViewMatchers.withText("On")))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))

        Espresso.onView(ViewMatchers.withId(R.id.main_activity_keep_screen_on_button))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
            .perform(ViewActions.click())

        Espresso.onView(ViewMatchers.withId(R.id.main_activity_config_keep_screen_on_textview))
            .check(ViewAssertions.matches(ViewMatchers.withText("Off")))

        scenario.onActivity { activity ->
            assertFalse(activity.findViewById<View>(R.id.main).keepScreenOn)
        }

        // Toggle back so that the default state is kept for other tests
        Espresso.onView(ViewMatchers.withId(R.id.main_activity_keep_screen_on_button))
            .perform(ViewActions.click())

        Espresso.onView(ViewMatchers.withId(R.id.main_activity_config_keep_screen_on_textview))
            .check(ViewAssertions.matches(ViewMatchers.withText("On")))

        scenario.onActivity { activity ->
            assertTrue(activity.findViewById<View>(R.id.main).keepScreenOn)
        }

        scenario.close()
    }

    @Test
    fun testOverlayButtonShown() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(3000)

        Assume.assumeTrue(Build.VERSION.SDK_INT >= 24)
        // This test is about asking for the "Display over other apps" permission
        Assume.assumeFalse(Settings.canDrawOverlays(this.context))

        scrollMainViewToBottom(scenario)

        // The overlay button is always shown, in its "show" state initially
        Espresso.onView(ViewMatchers.withId(R.id.main_activity_overlay_button))
            .check(ViewAssertions.matches(ViewMatchers.withText("Show mini overlay")))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))

        // Tapping it explains and requests the overlay permission, but does not
        // enable the overlay itself while the permission is missing
        Espresso.onView(ViewMatchers.withId(R.id.main_activity_overlay_button))
            .perform(ViewActions.click())

        Espresso.onView(ViewMatchers.withText(R.string.overlay_permission_description))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))

        scenario.onActivity { activity ->
            assertFalse(isOverlayPreferenceEnabled(activity))
        }

        Espresso.pressBack()
        scenario.close()
    }

    private fun isOverlayPreferenceEnabled(activity: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(activity)
            .getBoolean(MainActivity.PREFERENCE_KEY_OVERLAY_ENABLED, false)
    }

    private fun scrollMainViewToBottom(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { activity ->
            val scrollView = activity.findViewById<ScrollView>(R.id.main)
            // ScrollView#scrollTo clamps to the content size
            scrollView.scrollTo(0, Int.MAX_VALUE / 2)
        }
        Thread.sleep(500)
    }

    @Test
    fun testJustStartingUpJapanese() {
        // Looks like the way of setting language has changed in SDK24 (N).
        // Older version does not change output even if we run the following language setting code.
        // Therefore, in this case, skipping test for non-English version.
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context!!.getSystemService<LocaleManager>(LocaleManager::class.java)
                .setApplicationLocales(
                    LocaleList(
                        Locale("ja", "JP")
                    )
                )
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(Locale("ja", "JP")))
        }
        Locale.setDefault(Locale("ja", "JP"))

        // Just test app starts without problems loading libraries
        ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(3000)

        Espresso.onView(ViewMatchers.withText("速度"))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }
}
