package net.nhiroki.bluesquarespeedometer

import org.junit.Test

import org.junit.Assert.*

class DisplayFormatTest {
    @Test
    fun testSpeedText() {
        assertEquals("0", DisplayFormat.speedText(0.0f, MainActivity.PREFERENCE_VAL_SPEED_UNIT_KM_H))
        assertEquals("0", DisplayFormat.speedText(0.0f, MainActivity.PREFERENCE_VAL_SPEED_UNIT_KNOT))
        assertEquals("0", DisplayFormat.speedText(0.0f, MainActivity.PREFERENCE_VAL_SPEED_UNIT_M_S))
        assertEquals("0", DisplayFormat.speedText(0.0f, MainActivity.PREFERENCE_VAL_SPEED_UNIT_MPH))

        // Values chosen so that truncation is not sensitive to floating point rounding
        assertEquals("7", DisplayFormat.speedText(7.3f, MainActivity.PREFERENCE_VAL_SPEED_UNIT_M_S))
        assertEquals("26", DisplayFormat.speedText(7.3f, MainActivity.PREFERENCE_VAL_SPEED_UNIT_KM_H))
        assertEquals("14", DisplayFormat.speedText(7.3f, MainActivity.PREFERENCE_VAL_SPEED_UNIT_KNOT))
        assertEquals("16", DisplayFormat.speedText(7.3f, MainActivity.PREFERENCE_VAL_SPEED_UNIT_MPH))

        // Unknown unit constants fall back to km/h
        assertEquals(DisplayFormat.speedText(7.3f, MainActivity.PREFERENCE_VAL_SPEED_UNIT_KM_H),
            DisplayFormat.speedText(7.3f, 999))

        // Negative speeds are truncated towards zero, as in the original UI code
        assertEquals("-7", DisplayFormat.speedText(-7.3f, MainActivity.PREFERENCE_VAL_SPEED_UNIT_M_S))
    }

    @Test
    fun testAltitudeText() {
        assertEquals("0", DisplayFormat.altitudeText(0.0, MainActivity.PREFERENCE_VAL_ALTITUDE_METERS))
        assertEquals("0", DisplayFormat.altitudeText(0.0, MainActivity.PREFERENCE_VAL_ALTITUDE_FEET))

        assertEquals("1234", DisplayFormat.altitudeText(1234.5, MainActivity.PREFERENCE_VAL_ALTITUDE_METERS))
        assertEquals("1234", DisplayFormat.altitudeText(1234.9, MainActivity.PREFERENCE_VAL_ALTITUDE_METERS))

        // 100 m == 328.08... ft
        assertEquals("328", DisplayFormat.altitudeText(100.0, MainActivity.PREFERENCE_VAL_ALTITUDE_FEET))

        // Sea level, 0 m
        assertEquals("0", DisplayFormat.altitudeText(0.0, MainActivity.PREFERENCE_VAL_ALTITUDE_FEET))

        // Below sea level
        assertEquals("-86", DisplayFormat.altitudeText(-86.0, MainActivity.PREFERENCE_VAL_ALTITUDE_METERS))

        // Unknown unit constants fall back to meters
        assertEquals(DisplayFormat.altitudeText(1234.5, MainActivity.PREFERENCE_VAL_ALTITUDE_METERS),
            DisplayFormat.altitudeText(1234.5, 999))
    }
}
