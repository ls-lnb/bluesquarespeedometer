package net.nhiroki.bluesquarespeedometer

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.location.LocationManager
import android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatActivity

/**
 * Shows the location provider selection used by both the main view (first run
 * prompt) and the settings screen.
 */
fun showLocationProviderDialog(activity: AppCompatActivity, onProviderChanged: () -> Unit) {
    val locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val locationProviders: List<String> = locationManager.getProviders(false)

    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    val currentPreferenceLocationProvider: String = prefs.getString(MainActivity.PREFERENCE_KEY_LOCATION_PROVIDER, "")!!

    var checkedItem: Int = -1

    val candidates: Array<CharSequence> = Array(locationProviders.size, {
        val ret: String = locationProviders.get(it)
        if (currentPreferenceLocationProvider.equals(ret)) {
            checkedItem = it
        }
        ret
    })

    AlertDialog.Builder(activity)
        .setTitle(R.string.dialog_select_location_provider)
        .setSingleChoiceItems(candidates, checkedItem, DialogInterface.OnClickListener { dialog, which ->
            prefs.edit()
                .putString(MainActivity.PREFERENCE_KEY_LOCATION_PROVIDER, locationProviders.get(which))
                .apply()
            dialog.cancel()
            onProviderChanged()
        })
        .create()
        .show()
}
