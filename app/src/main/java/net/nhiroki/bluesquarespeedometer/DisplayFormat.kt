package net.nhiroki.bluesquarespeedometer

import android.content.Context
import android.location.Location
import android.location.altitude.AltitudeConverter
import android.os.Build
import java.io.IOException

/**
 * Formatting helpers shared by the regular UI, the fullscreen viewer and the
 * mini overlay.
 *
 * [speedText] and [altitudeText] are pure functions (no Android framework calls)
 * so that they can be covered by plain JVM unit tests.
 */
object DisplayFormat {
    data class AltitudeReading(val meters: Double, val isMsl: Boolean)

    // Confidence for each unit is on comment on MainActivity
    fun speedText(speedMps: Float, speedUnit: Int): String {
        return when (speedUnit) {
            MainActivity.PREFERENCE_VAL_SPEED_UNIT_KNOT -> (speedMps * 3.6 / 1.852).toInt().toString()
            MainActivity.PREFERENCE_VAL_SPEED_UNIT_M_S -> speedMps.toInt().toString()
            MainActivity.PREFERENCE_VAL_SPEED_UNIT_MPH -> (speedMps * 3.6 / 1.609344).toInt().toString()
            else -> (speedMps * 3.6).toInt().toString()
        }
    }

    // Confidence for each unit is on comment on MainActivity
    fun altitudeText(altitudeM: Double, altitudeUnit: Int): String {
        return when (altitudeUnit) {
            MainActivity.PREFERENCE_VAL_ALTITUDE_FEET -> (altitudeM / 0.3048).toInt().toString()
            else -> altitudeM.toInt().toString()
        }
    }

    /** Unit label for the speed, shared by the main view and the settings view. */
    fun speedUnitName(context: Context, speedUnit: Int): String {
        return when (speedUnit) {
            MainActivity.PREFERENCE_VAL_SPEED_UNIT_KNOT -> context.getText(R.string.unit_knot).toString()
            MainActivity.PREFERENCE_VAL_SPEED_UNIT_M_S -> context.getText(R.string.unit_meter_per_second).toString()
            MainActivity.PREFERENCE_VAL_SPEED_UNIT_MPH -> context.getText(R.string.unit_mile_per_hour).toString()
            MainActivity.PREFERENCE_VAL_SPEED_UNIT_KM_H -> context.getText(R.string.unit_km_per_hour).toString()
            else -> ""
        }
    }

    /** Unit label for the altitude, shared by the main view and the settings view. */
    fun altitudeUnitName(context: Context, altitudeUnit: Int): String {
        return when (altitudeUnit) {
            MainActivity.PREFERENCE_VAL_ALTITUDE_METERS -> context.getText(R.string.unit_meter).toString()
            MainActivity.PREFERENCE_VAL_ALTITUDE_FEET -> context.getText(R.string.unit_feet).toString()
            else -> ""
        }
    }

    /**
     * Resolves the altitude to display for a [Location], preferring mean sea
     * level (MSL) altitude when the platform is able to provide it (SDK34+).
     * On older SDKs, or when MSL is unavailable, the value is WGS84 based.
     */
    fun readAltitude(context: Context, location: Location): AltitudeReading {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !location.hasMslAltitude()) {
            // There are both cases that msl altitude is automatically added and not
            try {
                AltitudeConverter().addMslAltitudeToLocation(context, location)
            } catch (e: IllegalArgumentException) {
                // ignore, just continue without MSL altitude
                // There is a documented case
            } catch (e: IOException) {
                // IOException is also documented
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && location.hasMslAltitude()) {
            return AltitudeReading(location.mslAltitudeMeters, true)
        }
        // This case the height is WGS84 based
        // https://developer.android.com/reference/android/location/Location#getAltitude()
        return AltitudeReading(location.altitude, false)
    }
}
