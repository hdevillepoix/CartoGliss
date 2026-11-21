package app.cartogliss.geo

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** WGS84 equatorial radius used by Web Mercator. */
const val EARTH_RADIUS_M = 6_378_137.0
const val EARTH_CIRCUMFERENCE_M = 2 * PI * EARTH_RADIUS_M

/** Mean Earth radius, for great-circle distances. */
const val MEAN_EARTH_RADIUS_M = 6_371_008.8

object Geo {
    /** Great-circle (haversine) distance in metres. */
    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2).let { it * it } + cos(p1) * cos(p2) * sin(dl / 2).let { it * it }
        return 2 * MEAN_EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /** Initial true bearing from point 1 to point 2, degrees in [0, 360). */
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    private val COMPASS16 = listOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
    )

    /** 16-point compass name (N, NNE … ESE …). */
    fun compass16(deg: Double): String = COMPASS16[((((deg % 360) + 360) % 360) / 22.5).roundToInt() % 16]

    /** Ground metres per screen pixel at [lat] for a map drawn at [pxPerWorld] pixels per world width. */
    fun metersPerPixel(lat: Double, pxPerWorld: Double): Double =
        cos(Math.toRadians(lat)) * EARTH_CIRCUMFERENCE_M / pxPerWorld
}

/** Axis-aligned rectangle in screen pixels. */
data class Rect2(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** A segment between two screen points. */
data class Segment(val x0: Float, val y0: Float, val x1: Float, val y1: Float)

/**
 * Liang–Barsky clipping of a segment to [r]. Returns null when the segment misses the rectangle.
 * Used so dashed lines to far-away points are never stroked over millions of off-screen pixels.
 */
fun clipSegment(s: Segment, r: Rect2): Segment? {
    val dx = (s.x1 - s.x0).toDouble()
    val dy = (s.y1 - s.y0).toDouble()
    var t0 = 0.0
    var t1 = 1.0
    val p = doubleArrayOf(-dx, dx, -dy, dy)
    val q = doubleArrayOf(
        (s.x0 - r.left).toDouble(), (r.right - s.x0).toDouble(),
        (s.y0 - r.top).toDouble(), (r.bottom - s.y0).toDouble(),
    )
    for (i in 0..3) {
        if (p[i] == 0.0) {
            if (q[i] < 0) return null
        } else {
            val t = q[i] / p[i]
            if (p[i] < 0) {
                if (t > t1) return null
                if (t > t0) t0 = t
            } else {
                if (t < t0) return null
                if (t < t1) t1 = t
            }
        }
    }
    return Segment(
        (s.x0 + t0 * dx).toFloat(), (s.y0 + t0 * dy).toFloat(),
        (s.x0 + t1 * dx).toFloat(), (s.y0 + t1 * dy).toFloat(),
    )
}
