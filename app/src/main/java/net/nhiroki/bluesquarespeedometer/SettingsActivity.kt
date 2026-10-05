package net.nhiroki.bluesquarespeedometer

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.location.LocationManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.max

/**
 * Settings screen: location provider, units, license information and the app
 * version, moved out of the main view to keep it minimal.
 */
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        // Keep the layout's own padding (curved display edges) and add
        // the system bar / display cutout insets to it
        val insetTarget = findViewById<View>(R.id.settings)
        val baseLeft = insetTarget.paddingLeft
        val baseTop = insetTarget.paddingTop
        val baseRight = insetTarget.paddingRight
        val baseBottom = insetTarget.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(
            insetTarget,
            OnApplyWindowInsetsListener { v: View?, insets: WindowInsetsCompat? ->
                val systemBars = insets!!.getInsets(WindowInsetsCompat.Type.systemBars())
                val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
                v!!.setPadding(
                    baseLeft + max(systemBars.left, cutout.left),
                    baseTop + max(systemBars.top, cutout.top),
                    baseRight + max(systemBars.right, cutout.right),
                    baseBottom + max(systemBars.bottom, cutout.bottom))
                insets
            })

        this.findViewById<Button>(R.id.settings_change_provider_button).setOnClickListener {
            this.changeProviderButtonClicked()
        }
        this.findViewById<Button>(R.id.settings_speed_unit_button).setOnClickListener {
            this.changeSpeedUnitButtonClicked()
        }
        this.findViewById<Button>(R.id.settings_altitude_unit_button).setOnClickListener {
            this.changeAltitudeUnitButtonClicked()
        }
        this.findViewById<Button>(R.id.settings_licensing_information_button).setOnClickListener {
            startActivity(Intent(this, LicensingInformationActivity::class.java))
        }

        findViewById<TextView>(R.id.settings_version_info_footer).setText(getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME)
    }

    override fun onResume() {
        super.onResume()
        this.updateValuesShown()
    }

    private fun updateValuesShown() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        val currentPreferenceLocationProvider:String = prefs.getString(MainActivity.PREFERENCE_KEY_LOCATION_PROVIDER, "")!!
        findViewById<TextView>(R.id.settings_config_location_provider_textview).setText(if (currentPreferenceLocationProvider.isEmpty()) getText(R.string.general_caption_unset) else currentPreferenceLocationProvider)

        val speedUnit:Int = prefs.getInt(MainActivity.PREFERENCE_KEY_SPEED_UNIT, MainActivity.PREFERENCE_VAL_SPEED_UNIT_DEFAULT)
        findViewById<TextView>(R.id.settings_config_speed_unit_textview).setText(DisplayFormat.speedUnitName(this, speedUnit))

        val altitudeUnit:Int = prefs.getInt(MainActivity.PREFERENCE_KEY_ALTITUDE_UNIT, MainActivity.PREFERENCE_VAL_ALTITUDE_DEFAULT)
        findViewById<TextView>(R.id.settings_config_altitude_unit_textview).setText(DisplayFormat.altitudeUnitName(this, altitudeUnit))
    }

    private fun changeProviderButtonClicked() {
        val locationManager = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val locationProviders:List<String> = locationManager.getProviders(false)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val currentPreferenceLocationProvider:String = prefs.getString(MainActivity.PREFERENCE_KEY_LOCATION_PROVIDER, "")!!

        var checkedItem:Int = -1

        var candidates:Array<CharSequence> = Array(locationProviders.size, {
            val ret:String = locationProviders.get(it)
            if (currentPreferenceLocationProvider.equals(ret)) {
                checkedItem = it
            }
            ret
        })

        AlertDialog.Builder(this).setTitle(R.string.dialog_select_location_provider).setSingleChoiceItems(candidates, checkedItem, DialogInterface.OnClickListener {
                dialog, which ->
            prefs.edit()
                .putString(MainActivity.PREFERENCE_KEY_LOCATION_PROVIDER, locationProviders.get(which))
                .apply()
            dialog.cancel()
            this.updateValuesShown()
            // MainActivity re-registers its location updates with the new
            // provider when it resumes, right after this screen closes.

        }).create().show()
    }

    private fun changeSpeedUnitButtonClicked() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val currentSpeedUnit:Int = prefs.getInt(MainActivity.PREFERENCE_KEY_SPEED_UNIT, MainActivity.PREFERENCE_VAL_SPEED_UNIT_DEFAULT)

        val candidates:Array<CharSequence> = Array(4, {
            DisplayFormat.speedUnitName(this, it)
        })
        AlertDialog.Builder(this).setTitle(R.string.dialog_select_speed_unit).setSingleChoiceItems(candidates, currentSpeedUnit, DialogInterface.OnClickListener {
                dialog, which ->
            prefs.edit()
                .putInt(MainActivity.PREFERENCE_KEY_SPEED_UNIT, which)
                .apply()
            dialog.cancel()
            this.updateValuesShown()
        }).create().show()
    }

    private fun changeAltitudeUnitButtonClicked() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val currentAltitudeUnit:Int = prefs.getInt(MainActivity.PREFERENCE_KEY_ALTITUDE_UNIT, MainActivity.PREFERENCE_VAL_ALTITUDE_DEFAULT)

        val candidates:Array<CharSequence> = Array(2, {
            DisplayFormat.altitudeUnitName(this, it)
        })
        AlertDialog.Builder(this).setTitle(R.string.dialog_select_altitude_unit).setSingleChoiceItems(candidates, currentAltitudeUnit, DialogInterface.OnClickListener {
            dialog, which ->
                prefs.edit()
                    .putInt(MainActivity.PREFERENCE_KEY_ALTITUDE_UNIT, which)
                    .apply()
                dialog.cancel()
                this.updateValuesShown()
        }).create().show()
    }
}
