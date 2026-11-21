package app.cartogliss.map

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Web Mercator (EPSG:3857) helpers.
 *
 * "Normalized" coordinates are zoom-independent: x and y both run 0..1 across the world,
 * x eastwards from 180°W, y southwards from ~85.05°N. Tile (z, x, y) covers
 * [x / 2^z, (x+1) / 2^z) horizontally.
 */
object TileMath {
    const val MAX_LAT = 85.05112877980659

    fun lonToX(lon: Double): Double = (lon + 180.0) / 360.0

    fun latToY(lat: Double): Double {
        val r = Math.toRadians(lat.coerceIn(-MAX_LAT, MAX_LAT))
        return (1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0
    }

    fun xToLon(x: Double): Double = x * 360.0 - 180.0

    fun yToLat(y: Double): Double = Math.toDegrees(atan(kotlin.math.sinh(PI * (1.0 - 2.0 * y))))

    /** Tile index containing the point at zoom [z]. */
    fun tileX(lon: Double, z: Int): Int = floor(lonToX(lon) * (1 shl z)).toInt().coerceIn(0, (1 shl z) - 1)
    fun tileY(lat: Double, z: Int): Int = floor(latToY(lat) * (1 shl z)).toInt().coerceIn(0, (1 shl z) - 1)

    /** Bing Maps quadkey for tile (z, x, y). */
    fun quadkey(z: Int, x: Int, y: Int): String {
        val sb = StringBuilder(z)
        for (i in z downTo 1) {
            val mask = 1 shl (i - 1)
            var digit = 0
            if (x and mask != 0) digit += 1
            if (y and mask != 0) digit += 2
            sb.append(digit)
        }
        return sb.toString()
    }

    /** Wrap a normalized x into [0, 1). */
    fun wrapX(x: Double): Double = x - floor(x)
}
