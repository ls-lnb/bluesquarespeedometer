package net.nhiroki.bluesquarespeedometer

import android.app.LocaleManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.LocaleList
import android.preference.PreferenceManager
import android.provider.Settings
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.containsString
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
    fun testMainPageSectionsAndButtons() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(3000)

        Assume.assumeTrue(Build.VERSION.SDK_INT >= 24)

        // The metric sections are still on the main page. The coordinate view
        // always carries text ("---" until the first location fix), unlike the
        // accuracy details which are empty for the first seconds.
        Espresso.onView(ViewMatchers.withId(R.id.main_activity_speed_digits_textview))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(ViewMatchers.withId(R.id.main_activity_altitude_digits_textview))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(ViewMatchers.withId(R.id.main_activity_current_coordinate_textview))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))

        // The configuration sections are gone; four buttons remain
        scrollMainViewToBottom(scenario)
        for (buttonId in listOf(
                R.id.main_activity_meter_car1_button,
                R.id.main_activity_overlay_button,
                R.id.main_activity_keep_screen_on_button,
                R.id.main_activity_settings_button)) {
            Espresso.onView(ViewMatchers.withId(buttonId))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        }

        Espresso.onView(ViewMatchers.withId(R.id.main_activity_meter_car1_button))
            .check(ViewAssertions.matches(ViewMatchers.withText("Fullscreen")))
        Espresso.onView(ViewMatchers.withId(R.id.main_activity_settings_button))
            .check(ViewAssertions.matches(ViewMatchers.withText("Settings")))
        Espresso.onView(ViewMatchers.withId(R.id.main_activity_overlay_button))
            .check(ViewAssertions.matches(ViewMatchers.withText("Show mini overlay")))

        scenario.close()
    }

    @Test
    fun testKeepScreenOnOption() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(3000)

        Assume.assumeTrue(Build.VERSION.SDK_INT >= 24)

        scrollMainViewToBottom(scenario)

        // One option now controls the main view and the mini overlay together;
        // the default of the option is "On", matching the historic behavior
        val expectedOn = context!!.getString(R.string.config_keep_screen_on) + ": " +
            context!!.getString(R.string.option_on)
        val expectedOff = context!!.getString(R.string.config_keep_screen_on) + ": " +
            context!!.getString(R.string.option_off)

        Espresso.onView(ViewMatchers.withId(R.id.main_activity_keep_screen_on_button))
            .check(ViewAssertions.matches(ViewMatchers.withText(expectedOn)))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
            .perform(ViewActions.click())

        Espresso.onView(ViewMatchers.withId(R.id.main_activity_keep_screen_on_button))
            .check(ViewAssertions.matches(ViewMatchers.withText(expectedOff)))

        scenario.onActivity { activity ->
            assertFalse(isKeepScreenOnPreferenceEnabled(activity))
            assertFalse(activity.findViewById<View>(R.id.main).keepScreenOn)
        }

        // Toggle back so that the default state is kept for other tests
        Espresso.onView(ViewMatchers.withId(R.id.main_activity_keep_screen_on_button))
            .perform(ViewActions.click())

        Espresso.onView(ViewMatchers.withId(R.id.main_activity_keep_screen_on_button))
            .check(ViewAssertions.matches(ViewMatchers.withText(expectedOn)))

        scenario.onActivity { activity ->
            assertTrue(isKeepScreenOnPreferenceEnabled(activity))
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

    @Test
    fun testSettingsView() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(3000)

        Assume.assumeTrue(Build.VERSION.SDK_INT >= 24)

        scrollMainViewToBottom(scenario)
        Espresso.onView(ViewMatchers.withId(R.id.main_activity_settings_button))
            .perform(ViewActions.click())
        Thread.sleep(1000)

        // The settings view holds the provider and unit options
        Espresso.onView(ViewMatchers.withId(R.id.settings_change_provider_button))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(ViewMatchers.withId(R.id.settings_config_speed_unit_textview))
            .check(ViewAssertions.matches(ViewMatchers.withText("km/h")))
        Espresso.onView(ViewMatchers.withId(R.id.settings_config_altitude_unit_textview))
            .check(ViewAssertions.matches(ViewMatchers.withText("m")))

        // License information and the app version live here too
        scrollSettingsViewToBottom()
        Espresso.onView(ViewMatchers.withId(R.id.settings_licensing_information_button))
            .check(ViewAssertions.matches(ViewMatchers.withText("License information")))
        Espresso.onView(ViewMatchers.withId(R.id.settings_version_info_footer))
            .check(ViewAssertions.matches(ViewMatchers.withText(containsString(BuildConfig.VERSION_NAME))))

        Espresso.pressBack()
        Thread.sleep(500)
        Espresso.onView(ViewMatchers.withText("Speed"))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))

        scenario.close()
    }

    @Test
    fun testDigitsStayInsideTheirSection() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(3000)

        Assume.assumeTrue(Build.VERSION.SDK_INT >= 24)

        // A long reading is the case that used to overflow the section width
        scenario.onActivity { activity ->
            activity.findViewById<TextView>(R.id.main_activity_speed_digits_textview).setText("888")
            activity.findViewById<TextView>(R.id.main_activity_altitude_digits_textview).setText("8888")
        }
        Thread.sleep(500)

        scenario.onActivity { activity ->
            for (ids in listOf(
                    Pair(R.id.main_activity_speed_section, R.id.main_activity_speed_digits_textview),
                    Pair(R.id.main_activity_altitude_section, R.id.main_activity_altitude_digits_textview))) {
                val section = activity.findViewById<View>(ids.first)
                val digits = activity.findViewById<TextView>(ids.second)
                val unit = activity.findViewById<TextView>(
                    if (ids.first == R.id.main_activity_speed_section) R.id.main_activity_speed_unit_textview
                    else R.id.main_activity_altitude_unit_textview)

                assertTrue("digits must keep a readable size", digits.textSize > 0f)
                assertTrue(
                    "digits (${digits.width}px) and unit (${unit.width}px) must fit the section (${section.width}px)",
                    digits.width + unit.width <= section.width)
            }
        }

        scenario.close()
    }

    @Test
    fun testLandscapeSplitsIntoMetricsAndButtonPane() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(3000)

        Assume.assumeTrue(Build.VERSION.SDK_INT >= 24)

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        Thread.sleep(3000)

        // Every button is reachable without scrolling
        for (buttonId in listOf(
                R.id.main_activity_meter_car1_button,
                R.id.main_activity_overlay_button,
                R.id.main_activity_keep_screen_on_button,
                R.id.main_activity_settings_button)) {
            Espresso.onView(ViewMatchers.withId(buttonId))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        }

        // The metrics stay in the left pane, the buttons sit in the right pane
        scenario.onActivity { activity ->
            val section = activity.findViewById<View>(R.id.main_activity_speed_section)
            val button = activity.findViewById<View>(R.id.main_activity_settings_button)
            val sectionLocation = IntArray(2)
            val buttonLocation = IntArray(2)
            section.getLocationOnScreen(sectionLocation)
            button.getLocationOnScreen(buttonLocation)

            assertTrue(
                "buttons (x=${buttonLocation[0]}) must be beside the metrics (x=${sectionLocation[0]}, width=${section.width})",
                buttonLocation[0] >= sectionLocation[0] + section.width)
        }

        // Leave the device in portrait for the other tests
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        Thread.sleep(2000)

        scenario.close()
    }

    private fun isKeepScreenOnPreferenceEnabled(activity: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(activity)
            .getBoolean(MainActivity.PREFERENCE_KEY_KEEP_SCREEN_ON,
                MainActivity.PREFERENCE_VAL_KEEP_SCREEN_ON_DEFAULT)
    }

    private fun isOverlayPreferenceEnabled(activity: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(activity)
            .getBoolean(MainActivity.PREFERENCE_KEY_OVERLAY_ENABLED, false)
    }

    private fun scrollMainViewToBottom(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { activity ->
            val scrollView = activity.findViewById<View>(R.id.main) as? ScrollView
            // ScrollView#scrollTo clamps to the content size
            scrollView?.scrollTo(0, Int.MAX_VALUE / 2)
        }
        Thread.sleep(500)
    }

    private fun scrollSettingsViewToBottom() {
        // The settings screen fits a phone screen; on the small CI screen the
        // license button and version footer need one swipe.
        Espresso.onView(ViewMatchers.withId(R.id.settings))
            .perform(ViewActions.swipeUp())
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
