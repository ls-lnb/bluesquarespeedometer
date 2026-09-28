package net.nhiroki.bluesquarespeedometer.overlay

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.preference.PreferenceManager
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import net.nhiroki.bluesquarespeedometer.DisplayFormat
import net.nhiroki.bluesquarespeedometer.MainActivity
import net.nhiroki.bluesquarespeedometer.R
import kotlin.math.abs

/**
 * Foreground service that shows a small ("stamp sized") speedometer overlay on
 * top of other apps, e.g. on top of a navigation app while driving.
 *
 * Requires the SYSTEM_ALERT_WINDOW ("Display over other apps") permission and a
 * location foreground service.
 */
class OverlaySpeedometerService : android.app.Service() {
    companion object {
        const val ACTION_STOP: String = "net.nhiroki.bluesquarespeedometer.overlay.action.STOP"
        const val ACTION_OPEN_APP: String = "net.nhiroki.bluesquarespeedometer.overlay.action.OPEN_APP"

        private const val NOTIFICATION_ID: Int = 42142
        private const val CHANNEL_ID: String = "mini_speedometer_overlay"
        private const val PREF_KEY_OVERLAY_X: String = "overlay_window_x"
        private const val PREF_KEY_OVERLAY_Y: String = "overlay_window_y"

        @Volatile
        private var _running: Boolean = false

        fun isRunning(): Boolean = _running
    }

    private var _windowManager: WindowManager? = null
    private var _locationManager: LocationManager? = null
    private var _overlayView: View? = null
    private var _windowParams: WindowManager.LayoutParams? = null
    private var _locationListener: LocationListener? = null
    private var _touchSlop: Int = 0

    // Dragging state
    private var _dragStartX: Int = 0
    private var _dragStartY: Int = 0
    private var _dragStartRawX: Float = 0f
    private var _dragStartRawY: Float = 0f
    private var _dragging: Boolean = false

    class MyLocationListener : LocationListener {
        val service: OverlaySpeedometerService

        constructor(service: OverlaySpeedometerService) {
            this.service = service
        }

        override fun onLocationChanged(location: Location) {
            service.updateDisplayedValues(location)
        }

        // called in some Android version and fails
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        }
    }

    override fun onCreate() {
        super.onCreate()
        this._windowManager = this.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        this._locationManager = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        this._locationListener = MyLocationListener(this)
        this._touchSlop = ViewConfiguration.get(this).scaledTouchSlop
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_OPEN_APP -> {
                openApp()
                return START_NOT_STICKY
            }
        }

        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
        }

        if (Settings.canDrawOverlays(this)) {
            showOverlay()
            startLocationUpdates()
            _running = true
        } else {
            // Permission was revoked meanwhile; do not keep running.
            stopSelf()
        }

        // Restart with the overlay shown if the process is killed by the system
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        _running = false
        // The overlay is gone; do not restart it on the next app start
        PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putBoolean(MainActivity.PREFERENCE_KEY_OVERLAY_ENABLED, false)
            .apply()
        this._locationManager?.let { lm ->
            this._locationListener?.let { lm.removeUpdates(it) }
        }
        this._overlayView?.let { v ->
            try {
                this._windowManager?.removeView(v)
            } catch (e: IllegalArgumentException) {
                // already detached
            }
        }
        this._overlayView = null
        this._windowParams = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            channel.setShowBadge(false)
            val notificationManager = this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, OverlaySpeedometerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_overlay_notification)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.overlay_notification_action_stop), stopIntent)
            .build()
    }

    private fun showOverlay() {
        if (this._overlayView != null) {
            return
        }

        val overlayView: View = LayoutInflater.from(this).inflate(R.layout.overlay_speedometer, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        params.x = prefs.getInt(PREF_KEY_OVERLAY_X, dpToPx(12))
        params.y = prefs.getInt(PREF_KEY_OVERLAY_Y, dpToPx(120))

        overlayView.findViewById<TextView>(R.id.overlay_speedometer_close_textview).setOnClickListener {
            stopSelf()
        }
        // Drag anywhere on the window (children are not clickable, so presses
        // reach the root view which handles dragging and plain clicks).
        overlayView.setOnTouchListener { view, event -> handleDrag(view, event) }

        this._windowManager!!.addView(overlayView, params)
        this._overlayView = overlayView
        this._windowParams = params
    }

    private fun handleDrag(view: View, event: MotionEvent): Boolean {
        val params = this._windowParams ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                _dragStartX = params.x
                _dragStartY = params.y
                _dragStartRawX = event.rawX
                _dragStartRawY = event.rawY
                _dragging = false
                // Let the view handle the press, so that plain clicks still work.
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - _dragStartRawX
                val dy = event.rawY - _dragStartRawY
                if (!_dragging && (abs(dx) > _touchSlop || abs(dy) > _touchSlop)) {
                    _dragging = true
                }
                if (_dragging) {
                    params.x = _dragStartX + dx.toInt()
                    params.y = _dragStartY + dy.toInt()
                    this._windowManager?.updateViewLayout(view, params)
                    return true
                }
                return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (_dragging) {
                    savePosition(params)
                    _dragging = false
                    // Swallow the event so that a drag is not reported as a click
                    return true
                }
                return false
            }
        }
        return false
    }

    private fun savePosition(params: WindowManager.LayoutParams) {
        PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putInt(PREF_KEY_OVERLAY_X, params.x)
            .putInt(PREF_KEY_OVERLAY_Y, params.y)
            .apply()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        startActivity(intent)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationManager = this._locationManager ?: return

        locationManager.removeUpdates(this._locationListener!!)

        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            stopSelf()
            return
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val preferredProvider: String = prefs.getString(MainActivity.PREFERENCE_KEY_LOCATION_PROVIDER, "") ?: ""

        val enabledProviders: List<String> = locationManager.getProviders(true)
        val provider: String? = when {
            preferredProvider.isNotEmpty() && enabledProviders.contains(preferredProvider) -> preferredProvider
            enabledProviders.isNotEmpty() -> pickBestProvider(enabledProviders)
            else -> null
        }

        if (provider == null) {
            // Nothing to display right now; the service stays alive and shows "-"
            return
        }
        locationManager.requestLocationUpdates(provider, 0, 0.0f, this._locationListener!!)
    }

    private fun pickBestProvider(providers: List<String>): String {
        // Prefer a precise provider when available
        if (providers.contains(LocationManager.GPS_PROVIDER)) {
            return LocationManager.GPS_PROVIDER
        }
        return providers.first()
    }

    private fun updateDisplayedValues(location: Location) {
        val overlayView = this._overlayView ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        val speedUnit: Int = prefs.getInt(
            MainActivity.PREFERENCE_KEY_SPEED_UNIT,
            MainActivity.PREFERENCE_VAL_SPEED_UNIT_DEFAULT
        )
        val altitudeUnit: Int = prefs.getInt(
            MainActivity.PREFERENCE_KEY_ALTITUDE_UNIT,
            MainActivity.PREFERENCE_VAL_ALTITUDE_DEFAULT
        )

        overlayView.findViewById<TextView>(R.id.overlay_speedometer_speed_textview)
            .text = DisplayFormat.speedText(location.speed, speedUnit)
        overlayView.findViewById<TextView>(R.id.overlay_speedometer_unit_textview)
            .setText(speedUnitText(speedUnit))

        val altitudeReading = DisplayFormat.readAltitude(this, location)
        overlayView.findViewById<TextView>(R.id.overlay_speedometer_altitude_textview)
            .text = DisplayFormat.altitudeText(altitudeReading.meters, altitudeUnit)
        overlayView.findViewById<TextView>(R.id.overlay_speedometer_altitude_unit_textview)
            .setText(altitudeUnitText(altitudeUnit))
    }

    private fun speedUnitText(speedUnit: Int): Int {
        return when (speedUnit) {
            MainActivity.PREFERENCE_VAL_SPEED_UNIT_KNOT -> R.string.unit_knot
            MainActivity.PREFERENCE_VAL_SPEED_UNIT_M_S -> R.string.unit_meter_per_second
            MainActivity.PREFERENCE_VAL_SPEED_UNIT_MPH -> R.string.unit_mile_per_hour
            else -> R.string.unit_km_per_hour
        }
    }

    private fun altitudeUnitText(altitudeUnit: Int): Int {
        return when (altitudeUnit) {
            MainActivity.PREFERENCE_VAL_ALTITUDE_FEET -> R.string.unit_feet
            else -> R.string.unit_meter
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = this.resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }
}
