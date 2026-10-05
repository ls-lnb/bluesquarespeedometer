package net.nhiroki.bluesquarespeedometer

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import net.nhiroki.bluesquarespeedometer.overlay.OverlaySpeedometerService
import net.nhiroki.bluesquarespeedometer.viewers.DigitalSpeedometer1Activity
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.max


class MainActivity : AppCompatActivity() {
    companion object {
        const val PREFERENCE_KEY_LOCATION_PROVIDER:String = "preference_location_provider"

        const val PREFERENCE_KEY_SPEED_UNIT:String = "preference_speed_unit"
        const val PREFERENCE_VAL_SPEED_UNIT_DEFAULT:Int = 0
        const val PREFERENCE_VAL_SPEED_UNIT_KM_H:Int = 0
        const val PREFERENCE_VAL_SPEED_UNIT_KNOT:Int = 1
        const val PREFERENCE_VAL_SPEED_UNIT_M_S:Int = 2
        const val PREFERENCE_VAL_SPEED_UNIT_MPH:Int = 3

        const val PREFERENCE_KEY_ALTITUDE_UNIT:String = "preference_altitude_unit"
        const val PREFERENCE_VAL_ALTITUDE_DEFAULT:Int = 0
        const val PREFERENCE_VAL_ALTITUDE_METERS:Int = 0
        const val PREFERENCE_VAL_ALTITUDE_FEET:Int = 1

        // Whether the regular (non fullscreen) view keeps the display on
        const val PREFERENCE_KEY_KEEP_SCREEN_ON:String = "preference_keep_screen_on"
        const val PREFERENCE_VAL_KEEP_SCREEN_ON_DEFAULT:Boolean = true

        // Whether the mini overlay (floating speedometer) should be shown
        const val PREFERENCE_KEY_OVERLAY_ENABLED:String = "preference_overlay_enabled"

        // The big digits fill this share of their (screen filling) section,
        // so large sections do not end up with small numbers in them
        private const val DIGIT_SECTION_FILL_RATIO:Float = 0.5f
        private const val UNIT_SIZE_RATIO:Float = 0.4f
        private const val MIN_DIGIT_SIZE_PX:Float = 14f
        private const val MIN_UNIT_SIZE_PX:Float = 9f
    }

    class MyLocationListener : LocationListener {
        val mainActivity:MainActivity

        constructor(mainActivity: MainActivity) {
            this.mainActivity = mainActivity
        }

        override fun onLocationChanged(location: Location) {
            mainActivity.updateLocation(location)
        }

        // called in some Android version and fails
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        }
    }

    var _locationManager:LocationManager? = null
    var _locationListener:LocationListener? = null
    var _displayedHeightM:Double = Double.NaN

    private var _pendingOverlayEnable:Boolean = false
    private lateinit var _locationPermissionRequest: ActivityResultLauncher<Array<String>>
    private lateinit var _overlayPermissionRequest: ActivityResultLauncher<Intent>
    private lateinit var _notificationPermissionRequest: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById<View?>(R.id.main),
            OnApplyWindowInsetsListener { v: View?, insets: WindowInsetsCompat? ->
                val systemBars = insets!!.getInsets(WindowInsetsCompat.Type.systemBars())
                v!!.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            })

        this._locationManager = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        this._locationListener = MyLocationListener(this)

        val locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            if (this._pendingOverlayEnable) {
                this.enableOverlay()
            }
        }
        this._locationPermissionRequest = locationPermissionRequest

        this._overlayPermissionRequest = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (Settings.canDrawOverlays(this)) {
                this.enableOverlay()
            } else {
                this._pendingOverlayEnable = false
                this.updateOverlayButton()
                Toast.makeText(this, R.string.overlay_permission_denied, Toast.LENGTH_LONG).show()
            }
        }

        this._notificationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            // Continue even when notification permission is denied; the overlay still works,
            // the ongoing notification then only shows up in the task manager.
            this.startOverlayService()
        }

        this.findViewById<Button>(R.id.main_activity_grant_location_button).setOnClickListener {
            locationPermissionRequest.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION))
        }

        this.findViewById<Button>(R.id.main_activity_meter_car1_button).setOnClickListener {
            startActivity(Intent(this, DigitalSpeedometer1Activity::class.java))
        }
        this.findViewById<Button>(R.id.main_activity_settings_button).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        this.findViewById<Button>(R.id.main_activity_keep_screen_on_button).setOnClickListener {
            changeKeepScreenOnButtonClicked()
        }
        this.findViewById<Button>(R.id.main_activity_overlay_button).setOnClickListener {
            if (this.isOverlayEnabled()) {
                this.disableOverlay()
            } else {
                this.enableOverlay()
            }
        }
        this.findViewById<Button>(R.id.main_activity_refresh_location_provider_button).setOnClickListener {
            updateLocationProvider()
        }

        this.applyKeepScreenOn()
        this.updateScreenOnButtonText()
        this.updateOverlayButton()
        this.setupSectionDigitSizes()
    }

    override fun onResume() {
        super.onResume()

        updateOptionsShown()

        this.applyKeepScreenOn()
        this.updateScreenOnButtonText()
        this.syncOverlayService()
        this.updateOverlayButton()

        val fineLocationPermission:Boolean = ActivityCompat.checkSelfPermission( this, Manifest.permission.ACCESS_FINE_LOCATION ) == PackageManager.PERMISSION_GRANTED
        val coarseLocationPermission:Boolean = ActivityCompat.checkSelfPermission( this, Manifest.permission.ACCESS_COARSE_LOCATION ) == PackageManager.PERMISSION_GRANTED

        findViewById<View>(R.id.main_activity_permission_location_not_granted).visibility = if (fineLocationPermission) View.GONE else View.VISIBLE

        if (fineLocationPermission) {
            findViewById<TextView>(R.id.main_activity_permission_status_textview).setText(R.string.permission_location_fine);
            this.updateLocationProvider()

            if (PreferenceManager.getDefaultSharedPreferences(this).getString(PREFERENCE_KEY_LOCATION_PROVIDER, "")!!.isEmpty()) {
                changeProviderButtonClicked()
            }

        } else {
            clearLocationDisplay()
            findViewById<TextView>(R.id.main_activity_permission_status_textview).setText(if (coarseLocationPermission) {R.string.permission_location_coarse} else {R.string.permission_location_no});
        }
    }

    override fun onStop() {
        this._locationManager!!.removeUpdates(this._locationListener!!)
        this._displayedHeightM = Double.NaN
        super.onStop()
    }

    private fun updateOptionsShown() {
        val speedUnit:Int = PreferenceManager.getDefaultSharedPreferences(this).getInt(PREFERENCE_KEY_SPEED_UNIT, PREFERENCE_VAL_SPEED_UNIT_DEFAULT)!!
        findViewById<TextView>(R.id.main_activity_speed_digits_textview).setText("-")
        findViewById<TextView>(R.id.main_activity_speed_unit_textview).setText(DisplayFormat.speedUnitName(this, speedUnit))

        val altitudeUnit:Int = PreferenceManager.getDefaultSharedPreferences(this).getInt(PREFERENCE_KEY_ALTITUDE_UNIT, PREFERENCE_VAL_ALTITUDE_DEFAULT)!!
        findViewById<TextView>(R.id.main_activity_altitude_digits_textview).setText("-")
        findViewById<TextView>(R.id.main_activity_altitude_unit_textview).setText(DisplayFormat.altitudeUnitName(this, altitudeUnit))
    }

    @SuppressLint("MissingPermission")
    private fun updateLocationProvider() {
        this._locationManager!!.removeUpdates(this._locationListener!!)
        clearLocationDisplay()

        val currentPreferenceLocationProvider:String = PreferenceManager.getDefaultSharedPreferences(this).getString(PREFERENCE_KEY_LOCATION_PROVIDER, "")!!

        if (Build.VERSION.SDK_INT >= 28) {
            if (!this._locationManager!!.isLocationEnabled()) {
                findViewById<View>(R.id.main_activity_location_not_enabled).visibility = View.VISIBLE
                findViewById<TextView>(R.id.main_activity_location_not_enabled_message).setText(R.string.error_soft_location_not_enabled)
                return
            }
        }

        if (currentPreferenceLocationProvider.isEmpty()) {
            return
        }

        if (!this._locationManager!!.isProviderEnabled(currentPreferenceLocationProvider)) {
            findViewById<View>(R.id.main_activity_location_not_enabled).visibility = View.VISIBLE
            findViewById<TextView>(R.id.main_activity_location_not_enabled_message).setText(getText(R.string.error_soft_location_provider_not_enabled).toString().format(currentPreferenceLocationProvider))
            return
        }

        findViewById<View>(R.id.main_activity_location_not_enabled).visibility = View.GONE

        this._locationManager!!.requestLocationUpdates(currentPreferenceLocationProvider, 0, 0.0f, this._locationListener!!)
    }

    private fun changeProviderButtonClicked() {
        val locationProviders:List<String> = _locationManager!!.getProviders(false)

        val currentPreferenceLocationProvider:String = PreferenceManager.getDefaultSharedPreferences(this).getString(PREFERENCE_KEY_LOCATION_PROVIDER, "")!!

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
            val prefEdit =
                PreferenceManager.getDefaultSharedPreferences(this).edit()
            prefEdit.putString(PREFERENCE_KEY_LOCATION_PROVIDER, locationProviders.get(which))
            prefEdit.apply()
            dialog.cancel()
            this.updateLocationProvider()

        }).create().show()
    }

    private fun isKeepScreenOnEnabled(): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREFERENCE_KEY_KEEP_SCREEN_ON, PREFERENCE_VAL_KEEP_SCREEN_ON_DEFAULT)
    }

    private fun applyKeepScreenOn() {
        findViewById<View>(R.id.main).keepScreenOn = this.isKeepScreenOnEnabled()
    }

    private fun updateScreenOnButtonText() {
        // One option now controls both the main view and the mini overlay;
        // the overlay service follows the same preference. The landscape
        // side pane uses the shorter label to fit the narrow button.
        findViewById<Button>(R.id.main_activity_keep_screen_on_button).setText(
            getString(R.string.option_state_format,
                getString(if (this.isLandscape()) R.string.main_button_screen_on_short else R.string.config_keep_screen_on),
                getString(if (this.isKeepScreenOnEnabled()) R.string.option_on else R.string.option_off)))
    }

    private fun changeKeepScreenOnButtonClicked() {
        PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putBoolean(PREFERENCE_KEY_KEEP_SCREEN_ON, !this.isKeepScreenOnEnabled())
            .apply()
        this.applyKeepScreenOn()
        this.updateScreenOnButtonText()
        // A running overlay service notices the change through its
        // OnSharedPreferenceChangeListener and updates its window flags
    }

    private fun isLandscape(): Boolean {
        return resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    /**
     * Sizes the big digits relative to their weighted, screen filling section
     * and keeps them fitted when the layout changes, e.g. when the accuracy
     * details grow or shrink, in portrait as well as in landscape.
     */
    private fun setupSectionDigitSizes() {
        this.fitDigitsToSection(R.id.main_activity_speed_section, R.id.main_activity_speed_digits_textview, R.id.main_activity_speed_unit_textview)
        this.fitDigitsToSection(R.id.main_activity_altitude_section, R.id.main_activity_altitude_digits_textview, R.id.main_activity_altitude_unit_textview)
    }

    private fun fitDigitsToSection(sectionResId: Int, digitsResId: Int, unitResId: Int) {
        val section = findViewById<View>(sectionResId)
        val digits = findViewById<TextView>(digitsResId)
        val unit = findViewById<TextView>(unitResId)

        val fit = {
            val sectionHeight = section.height
            if (sectionHeight > 0) {
                val digitSize = max(sectionHeight * DIGIT_SECTION_FILL_RATIO, MIN_DIGIT_SIZE_PX)
                val unitSize = max(digitSize * UNIT_SIZE_RATIO, MIN_UNIT_SIZE_PX)
                if (digits.textSize != digitSize) {
                    digits.setTextSize(TypedValue.COMPLEX_UNIT_PX, digitSize)
                }
                if (unit.textSize != unitSize) {
                    unit.setTextSize(TypedValue.COMPLEX_UNIT_PX, unitSize)
                }
            }
        }
        section.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fit() }
        section.post { fit() }
    }

    private fun hasLocationPermission(): Boolean {
        val fineLocationPermission:Boolean = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseLocationPermission:Boolean = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fineLocationPermission || coarseLocationPermission
    }

    private fun isOverlayEnabled(): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREFERENCE_KEY_OVERLAY_ENABLED, false)
    }

    /**
     * Starts the mini overlay, asking for the "Display over other apps"
     * permission, the location permission and the notification permission when
     * they are not granted yet.
     */
    private fun enableOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            this._pendingOverlayEnable = true
            AlertDialog.Builder(this)
                .setTitle(R.string.menu_this_app_overlay_show)
                .setMessage(R.string.overlay_permission_description)
                .setPositiveButton(R.string.overlay_permission_grant_prompt) { _, _ -> this.requestOverlayPermission() }
                .setNegativeButton(android.R.string.cancel) { _, _ -> this._pendingOverlayEnable = false }
                .setOnCancelListener { this._pendingOverlayEnable = false }
                .create().show()
            return
        }

        if (!this.hasLocationPermission()) {
            this._pendingOverlayEnable = true
            AlertDialog.Builder(this)
                .setTitle(R.string.menu_this_app_overlay_show)
                .setMessage(R.string.overlay_location_required_description)
                .setPositiveButton(R.string.permission_location_grant_prompt) { _, _ ->
                    this._locationPermissionRequest.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION))
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> this._pendingOverlayEnable = false }
                .setOnCancelListener { this._pendingOverlayEnable = false }
                .create().show()
            return
        }

        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            this._notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        this.startOverlayService()
    }

    private fun requestOverlayPermission() {
        val overlaySettingsIntent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + this.packageName)
        )
        try {
            this._overlayPermissionRequest.launch(overlaySettingsIntent)
        } catch (e:ActivityNotFoundException) {
            this._pendingOverlayEnable = false
            Toast.makeText(this, R.string.overlay_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    private fun startOverlayService() {
        this._pendingOverlayEnable = false
        val serviceIntent = Intent(this, OverlaySpeedometerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.startForegroundService(serviceIntent)
        } else {
            this.startService(serviceIntent)
        }
        PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putBoolean(PREFERENCE_KEY_OVERLAY_ENABLED, true)
            .apply()
        this.updateOverlayButton()
        // Shrink this app away, leaving only the small overlay on top of other apps
        this.moveTaskToBack(true)
    }

    private fun disableOverlay() {
        this.stopService(Intent(this, OverlaySpeedometerService::class.java))
        PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putBoolean(PREFERENCE_KEY_OVERLAY_ENABLED, false)
            .apply()
        this.updateOverlayButton()
    }

    private fun updateOverlayButton() {
        // The landscape side pane uses the short label with an On/Off state
        findViewById<Button>(R.id.main_activity_overlay_button).setText(
            if (this.isLandscape()) {
                getString(R.string.option_state_format,
                    getString(R.string.main_button_overlay_short),
                    getString(if (this.isOverlayEnabled()) R.string.option_on else R.string.option_off))
            } else if (this.isOverlayEnabled()) {
                getString(R.string.menu_this_app_overlay_hide)
            } else {
                getString(R.string.menu_this_app_overlay_show)
            })
    }

    /**
     * Keeps the overlay service and its preference in sync: restarts the
     * overlay after the process was killed and drops a stale preference when
     * the required permissions are gone.
     */
    private fun syncOverlayService() {
        if (this.isOverlayEnabled() && !OverlaySpeedometerService.isRunning()) {
            if (Settings.canDrawOverlays(this) && this.hasLocationPermission()) {
                val serviceIntent = Intent(this, OverlaySpeedometerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    this.startForegroundService(serviceIntent)
                } else {
                    this.startService(serviceIntent)
                }
            } else {
                PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putBoolean(PREFERENCE_KEY_OVERLAY_ENABLED, false)
                    .apply()
            }
        } else if (!this.isOverlayEnabled() && OverlaySpeedometerService.isRunning()) {
            this.stopService(Intent(this, OverlaySpeedometerService::class.java))
        }
    }

    private fun degreeToDisplayText(degree:Double, positiveAsix:String, negativeAxis:String): String {
        var degreeRemain:Double = degree
        var axisText = positiveAsix

        if (degreeRemain < 0) {
            degreeRemain = -degreeRemain
            axisText = negativeAxis
        }

        val degInt:Int = degreeRemain.toInt()
        degreeRemain -= degInt
        degreeRemain *= 60

        val degMinInt:Int = degreeRemain.toInt()
        degreeRemain -= degMinInt
        degreeRemain *= 60

        val degSecInt:Int = degreeRemain.toInt()
        degreeRemain -= degSecInt
        val degSubSecInt:Int = (degreeRemain * 10.0).toInt()

        return axisText + getText(R.string.unit_angle_deg).toString().format(degInt, degMinInt, degSecInt, degSubSecInt)
    }

    /*
     * Imperial units conversion
     *
     * 1 yard = 0.9144 m
     *
     * https://books.google.com/books?id=4aWN-VRV1AoC&pg=PA13  ([1] in Wikipedia link below on 2021/09/12)
     * > According to the agreement, the international yard equals 0.9144 meter and the international pound equals 0.453 592 37 kilogram.
     *
     * https://en.wikipedia.org/wiki/International_yard_and_pound
     * > The international yard and pound are two units of measurement that were the subject of an agreement among representatives of six nations signed on 1 July 1959; the United States, United Kingdom, Canada, Australia, New Zealand, and South Africa. The agreement defined the yard as exactly 0.9144 meters and the (avoirdupois) pound as exactly 0.45359237 kilograms.[1]
     *
     * https://www.legislation.gov.uk
     * yard
     * > 0.9144 metre
     *
     * https://elaws.e-gov.go.jp/document?lawid=404CO0000000357
     * ヤード
     * > メートルの〇・九一四四倍
     *
     *
     * 1 mile = 1760 yard
     *
     * https://en.wikipedia.org/wiki/Mile
     * >  The statute mile was standardised between the British Commonwealth and the United States by an international agreement in 1959, when it was formally redefined with respect to SI units as exactly 1,609.344 metres.
     *
     * https://www.legislation.gov.uk/uksi/1995/1804/schedule/made
     * mile
     * > 1.609344 kilometres
     *
     * https://elaws.e-gov.go.jp/document?lawid=404CO0000000357
     * マイル
     * > ヤードの千七百六十倍
     *
     * 1 nautical mile = 1852 m
     *
     * https://en.wikipedia.org/wiki/Nautical_mile
     * > Today the international nautical mile is defined as exactly 1852 metres (6076 ft; 1.151 mi). The derived unit of speed is the knot, one nautical mile per hour.
     *
     * https://elaws.e-gov.go.jp/document?lawid=404CO0000000357
     * ノット
     * > 一時間に千八百五十二メートルの速さ
     */
    fun updateLocation(location:Location) {
        val speedUnit:Int = PreferenceManager.getDefaultSharedPreferences(this).getInt(PREFERENCE_KEY_SPEED_UNIT, PREFERENCE_VAL_SPEED_UNIT_DEFAULT)!!
        when(speedUnit) {
            PREFERENCE_VAL_SPEED_UNIT_KM_H -> {
                findViewById<TextView>(R.id.main_activity_speed_digits_textview).setText((location.speed * 3.6).toInt().toString())
                findViewById<TextView>(R.id.main_activity_speed_unit_textview).setText(R.string.unit_km_per_hour)
            }
            PREFERENCE_VAL_SPEED_UNIT_KNOT -> {
                findViewById<TextView>(R.id.main_activity_speed_digits_textview).setText((location.speed * 3.6 / 1.852).toInt().toString())
                findViewById<TextView>(R.id.main_activity_speed_unit_textview).setText(R.string.unit_knot)
            }
            PREFERENCE_VAL_SPEED_UNIT_M_S -> {
                findViewById<TextView>(R.id.main_activity_speed_digits_textview).setText((location.speed).toInt().toString())
                findViewById<TextView>(R.id.main_activity_speed_unit_textview).setText(R.string.unit_meter_per_second)
            }
            PREFERENCE_VAL_SPEED_UNIT_MPH -> {
                findViewById<TextView>(R.id.main_activity_speed_digits_textview).setText((location.speed * 3.6 / 1.609344).toInt().toString())
                findViewById<TextView>(R.id.main_activity_speed_unit_textview).setText(R.string.unit_mile_per_hour)
            }
        }

        val altitudeReading:DisplayFormat.AltitudeReading = DisplayFormat.readAltitude(this, location)

        val altitudeUnit:Int = PreferenceManager.getDefaultSharedPreferences(this).getInt(PREFERENCE_KEY_ALTITUDE_UNIT, PREFERENCE_VAL_ALTITUDE_DEFAULT)!!
        val altitudeMeterToShow:Double = altitudeReading.meters
        findViewById<TextView>(R.id.main_activity_altitude_caption_textview).setText(if (altitudeReading.isMsl) R.string.metrics_msl_altitude else R.string.metrics_wgs84_altitude)
        when(altitudeUnit) {
            PREFERENCE_VAL_ALTITUDE_METERS -> {
                findViewById<TextView>(R.id.main_activity_altitude_digits_textview).setText(DisplayFormat.altitudeText(altitudeMeterToShow, altitudeUnit))
                findViewById<TextView>(R.id.main_activity_altitude_unit_textview).setText(R.string.unit_meter)
            }
            PREFERENCE_VAL_ALTITUDE_FEET -> {
                findViewById<TextView>(R.id.main_activity_altitude_digits_textview).setText(DisplayFormat.altitudeText(altitudeMeterToShow, altitudeUnit))
                findViewById<TextView>(R.id.main_activity_altitude_unit_textview).setText(R.string.unit_feet)
            }
        }
        this._displayedHeightM = altitudeMeterToShow

        var currentCordinateText:String = ""
        currentCordinateText += (degreeToDisplayText(location.longitude, getText(R.string.coordinate_display_east).toString(), getText(R.string.coordinate_display_west).toString()) + " " +
                degreeToDisplayText(location.latitude, getText(R.string.coordinate_display_north).toString(), getText(R.string.coordinate_display_south).toString()))
        if (Build.VERSION.SDK_INT >= 34 && location.hasMslAltitude()) {
            // Here also adds WGS84 altitude
            // https://developer.android.com/reference/android/location/Location#getAltitude()
            currentCordinateText += "\n" + getText(R.string.metrics_wgs84_altitude) + ": "
            when(altitudeUnit) {
                PREFERENCE_VAL_ALTITUDE_METERS -> {
                    currentCordinateText += location.altitude.toInt().toString() + " " + getText(R.string.unit_meter)
                }
                PREFERENCE_VAL_ALTITUDE_FEET -> {
                    currentCordinateText += ((location.altitude / 0.3048).toInt().toString()) + " " + getText(R.string.unit_feet)
                }
            }
        }
        findViewById<TextView>(R.id.main_activity_current_coordinate_textview).setText(currentCordinateText)

        var geolocationDetailText = ""
        geolocationDetailText += location.provider + "\n";
        geolocationDetailText += getText(R.string.metrics_accuracy).toString() + ": " + String.format("%.1f", location.accuracy) + " " + getText(R.string.unit_meter) +  "\n"
        if (Build.VERSION.SDK_INT >= 34 && location.hasMslAltitude()) {
            geolocationDetailText += getText(R.string.metrics_msl_altitude_accuracy).toString() + ": "
            when(altitudeUnit) {
                PREFERENCE_VAL_ALTITUDE_METERS -> {
                    geolocationDetailText += (location.mslAltitudeAccuracyMeters + 0.5).toInt().toString() + " " + getText(R.string.unit_meter)
                }
                PREFERENCE_VAL_ALTITUDE_FEET -> {
                    geolocationDetailText += ((location.mslAltitudeAccuracyMeters / 0.3048 + 0.5).toInt().toString()) + " " + getText(R.string.unit_feet)
                }
            }
            geolocationDetailText += "\n"
        }
        geolocationDetailText += getText(R.string.metrics_info_time).toString() + ": " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(location.time))

        findViewById<TextView>(R.id.main_activity_geolocation_detail_textview).setText(geolocationDetailText)

        findViewById<View>(R.id.main_activity_location_not_enabled).visibility = View.GONE
    }

    fun clearLocationDisplay() {
        findViewById<TextView>(R.id.main_activity_speed_digits_textview).setText("-")

        findViewById<TextView>(R.id.main_activity_altitude_digits_textview).setText("-")

        findViewById<TextView>(R.id.main_activity_current_coordinate_textview).setText("---")

        findViewById<TextView>(R.id.main_activity_geolocation_detail_textview).setText("")
    }
}
