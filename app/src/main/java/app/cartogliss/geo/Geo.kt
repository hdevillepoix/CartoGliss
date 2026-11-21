package app.cartogliss.geo

import kotlin.math.PI
import kotlin.math.cos

/** WGS84 equatorial radius used by Web Mercator. */
const val EARTH_RADIUS_M = 6_378_137.0
const val EARTH_CIRCUMFERENCE_M = 2 * PI * EARTH_RADIUS_M

object Geo {
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
