package app.cartogliss.geo

import app.cartogliss.settings.AngleFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Coordinate formatting in the three angle formats. Output uses ASCII ' and " (the same characters
 * the input fields produce), with the hemisphere letter first:
 *  - DD:  N 45.12346°
 *  - DDM: N 45°07.408'
 *  - DMS: N 45°07'24.5"
 */
object Coords {
    fun formatLat(lat: Double, f: AngleFormat): String = (if (lat < 0) "S " else "N ") + angle(abs(lat), f, 2)
    fun formatLon(lon: Double, f: AngleFormat): String = (if (lon < 0) "W " else "E ") + angle(abs(lon), f, 3)

    fun format(lat: Double, lon: Double, f: AngleFormat): String = "${formatLat(lat, f)}  ${formatLon(lon, f)}"

    private fun angle(v: Double, f: AngleFormat, degDigits: Int): String = when (f) {
        AngleFormat.DD -> String.format(Locale.ROOT, "%.5f°", v)
        AngleFormat.DDM -> {
            // Work in integer thousandths of a minute so 59.9996' rounds up into the next degree.
            val total = (v * 60_000).roundToLong()
            val deg = total / 60_000
            val m = (total % 60_000) / 1000.0
            String.format(Locale.ROOT, "%0${degDigits}d°%06.3f'", deg, m)
        }
        AngleFormat.DMS -> {
            val total = (v * 36_000).roundToLong() // tenths of a second
            val deg = total / 36_000
            val min = (total % 36_000) / 600
            val sec = (total % 600) / 10.0
            String.format(Locale.ROOT, "%0${degDigits}d°%02d'%04.1f\"", deg, min, sec)
        }
    }
}
