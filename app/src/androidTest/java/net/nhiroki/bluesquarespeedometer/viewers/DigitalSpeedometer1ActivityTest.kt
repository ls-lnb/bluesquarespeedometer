package net.nhiroki.bluesquarespeedometer.viewers

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.nhiroki.bluesquarespeedometer.R
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class DigitalSpeedometer1ActivityTest {
    @Before
    fun setUp() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(Locale("en", "US")))
        Locale.setDefault(Locale("en", "US"))
    }

    @Test
    fun testFullscreenViewShowsSpeedAndAltitude() {
        // Just test the fullscreen view starts and shows speed and altitude rows
        ActivityScenario.launch(DigitalSpeedometer1Activity::class.java)
        Thread.sleep(3000)

        Espresso.onView(ViewMatchers.withId(R.id.digital_meter1_textview))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))

        // Altitude is displayed at the bottom of the fullscreen view,
        // with the default unit being meters
        Espresso.onView(ViewMatchers.withId(R.id.digital_meter1_altitude_textview))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(ViewMatchers.withId(R.id.digital_meter1_altitude_unit_textview))
            .check(ViewAssertions.matches(ViewMatchers.withText("m")))
    }
}
