package app.cartogliss.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One GPS fix. [elapsedNanos] is on the [SystemClock.elapsedRealtimeNanos] clock. */
data class Fix(
    val lat: Double,
    val lon: Double,
    val altitudeM: Double?,
    val accuracyM: Float?,
    val speedMps: Float?,
    val bearingDeg: Float?,
    val timeMs: Long,
    val elapsedNanos: Long,
) {
    fun ageMs(nowNanos: Long = SystemClock.elapsedRealtimeNanos()): Long = (nowNanos - elapsedNanos) / 1_000_000

    companion object {
        /** DESIGN.md: a fix is "recent" (blue point) when younger than this. */
        const val RECENT_MS = 10_000L

        fun from(l: Location) = Fix(
            lat = l.latitude,
            lon = l.longitude,
            altitudeM = if (l.hasAltitude()) l.altitude else null,
            accuracyM = if (l.hasAccuracy()) l.accuracy else null,
            speedMps = if (l.hasSpeed()) l.speed else null,
            bearingDeg = if (l.hasBearing()) l.bearing else null,
            timeMs = l.time,
            elapsedNanos = l.elapsedRealtimeNanos,
        )
    }
}

data class GnssInfo(val used: Int = 0, val visible: Int = 0)

/**
 * GPS via the platform [LocationManager] (no Google Play Services).
 * Users call [acquire]/[release]; updates run while at least one user holds it and GPS is enabled.
 */
class LocationTracker(context: Context) {
    private val app = context.applicationContext
    private val lm = app.getSystemService(LocationManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private val _fix = MutableStateFlow<Fix?>(null)
    val fix: StateFlow<Fix?> = _fix

    private val _gnss = MutableStateFlow(GnssInfo())
    val gnss: StateFlow<GnssInfo> = _gnss

    private val _enabled = MutableStateFlow(true)
    /** The user's GPS on/off toggle (state bar satellite icon). */
    val enabled: StateFlow<Boolean> = _enabled

    private var users = 0
    private var running = false

    private val listener = LocationListener { _fix.value = Fix.from(it) }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            for (i in 0 until status.satelliteCount) if (status.usedInFix(i)) used++
            _gnss.value = GnssInfo(used = used, visible = status.satelliteCount)
        }

        override fun onStopped() {
            _gnss.value = GnssInfo()
        }
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun acquire() {
        users++
        update()
    }

    fun release() {
        users = (users - 1).coerceAtLeast(0)
        update()
    }

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        update()
    }

    /** Re-check after the permission is granted. */
    fun onPermissionChanged() = update()

    @SuppressLint("MissingPermission")
    private fun update() {
        val shouldRun = users > 0 && _enabled.value && hasPermission() && lm != null
        if (shouldRun == running) return
        if (shouldRun) {
            try {
                if (_fix.value == null) {
                    lm!!.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { _fix.value = Fix.from(it) }
                }
                lm!!.requestLocationUpdates(LocationManager.GPS_PROVIDER, UPDATE_MS, 0f, listener, Looper.getMainLooper())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    lm.registerGnssStatusCallback(ContextCompat.getMainExecutor(app), gnssCallback)
                } else {
                    @Suppress("DEPRECATION")
                    lm.registerGnssStatusCallback(gnssCallback, main)
                }
                running = true
            } catch (e: SecurityException) {
                running = false
            }
        } else {
            lm?.removeUpdates(listener)
            lm?.unregisterGnssStatusCallback(gnssCallback)
            _gnss.value = GnssInfo()
            running = false
        }
    }

    companion object {
        private const val UPDATE_MS = 1000L
    }
}
