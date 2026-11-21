package app.cartogliss.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Device heading from the rotation vector sensor.
 * [magneticDeg]: azimuth of the device's natural "up" (top edge), clockwise from magnetic north.
 * [tiltDeg]: angle between the screen normal and vertical (0 = lying flat, 90 = upright).
 */
data class Heading(val magneticDeg: Float, val tiltDeg: Float) {
    companion object {
        /** DESIGN.md: hide the view cone when the phone is tilted more than this from flat. */
        const val MAX_TILT_DEG = 60f
    }
}

class HeadingTracker(context: Context) {
    private val sm = context.applicationContext.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _heading = MutableStateFlow<Heading?>(null)
    val heading: StateFlow<Heading?> = _heading

    val available: Boolean get() = sensor != null

    private var users = 0
    private val r = FloatArray(9)
    private val o = FloatArray(3)

    // Low-pass filter on the unit vector, so 359° → 1° doesn't swing through 180°.
    private var sx = 0.0
    private var cx = 0.0
    private var primed = false

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(r, e.values)
            SensorManager.getOrientation(r, o)
            val az = o[0].toDouble()
            if (!primed) {
                sx = sin(az); cx = cos(az); primed = true
            } else {
                sx += SMOOTHING * (sin(az) - sx)
                cx += SMOOTHING * (cos(az) - cx)
            }
            val deg = ((Math.toDegrees(atan2(sx, cx)) + 360.0) % 360.0).toFloat()
            // r[8] is the world-Z component of the device Z axis: cos(tilt).
            val tilt = Math.toDegrees(acos(r[8].toDouble().coerceIn(-1.0, 1.0))).toFloat()
            _heading.value = Heading(deg, tilt)
        }

        override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
    }

    fun acquire() {
        if (users++ == 0 && sensor != null) {
            primed = false
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun release() {
        if (users == 0) return
        if (--users == 0) {
            sm?.unregisterListener(listener)
            _heading.value = null
        }
    }

    companion object {
        private const val SMOOTHING = 0.25
    }
}
