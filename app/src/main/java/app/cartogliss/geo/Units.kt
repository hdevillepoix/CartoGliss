package app.cartogliss.geo

import app.cartogliss.settings.DistanceUnit
import app.cartogliss.settings.SpeedUnit
import java.util.Locale
import java.math.BigDecimal
import java.math.MathContext

object Units {
    const val M_PER_MILE = 1609.344
    const val M_PER_FOOT = 0.3048
    const val M_PER_NM = 1852.0

    /** Round to [digits] significant figures and print without exponent or trailing zeros. */
    fun sigFig(value: Double, digits: Int = 2): String {
        if (value == 0.0 || !value.isFinite()) return "0"
        return BigDecimal(value).round(MathContext(digits)).stripTrailingZeros().toPlainString()
    }

    /**
     * Distance label for the scale bar, 2 significant figures.
     * Small distances switch to a smaller unit: m below 1 km; ft below 0.1 mi; m below 0.1 NM.
     */
    fun scaleLabel(meters: Double, unit: DistanceUnit): String = when (unit) {
        DistanceUnit.METRIC ->
            if (meters < 1000.0) "${sigFig(meters)} m" else "${sigFig(meters / 1000.0)} km"
        DistanceUnit.MILES ->
            if (meters < 0.1 * M_PER_MILE) "${sigFig(meters / M_PER_FOOT)} ft" else "${sigFig(meters / M_PER_MILE)} mi"
        DistanceUnit.NAUTICAL ->
            if (meters < 0.1 * M_PER_NM) "${sigFig(meters)} m" else "${sigFig(meters / M_PER_NM)} NM"
    }

    /** General distance label: m/ft/NM-metres below the small-unit threshold, then 2, 1 or 0 decimals. */
    fun distance(meters: Double, unit: DistanceUnit): String {
        fun big(v: Double, u: String) = when {
            v < 10 -> String.format(Locale.ROOT, "%.2f %s", v, u)
            v < 100 -> String.format(Locale.ROOT, "%.1f %s", v, u)
            else -> String.format(Locale.ROOT, "%.0f %s", v, u)
        }
        return when (unit) {
            DistanceUnit.METRIC -> if (meters < 1000) String.format(Locale.ROOT, "%.0f m", meters) else big(meters / 1000, "km")
            DistanceUnit.MILES ->
                if (meters < 0.1 * M_PER_MILE) String.format(Locale.ROOT, "%.0f ft", meters / M_PER_FOOT) else big(meters / M_PER_MILE, "mi")
            DistanceUnit.NAUTICAL ->
                if (meters < 0.1 * M_PER_NM) String.format(Locale.ROOT, "%.0f m", meters) else big(meters / M_PER_NM, "NM")
        }
    }

    fun speed(mps: Double, unit: SpeedUnit): String = when (unit) {
        SpeedUnit.KMH -> String.format(Locale.ROOT, "%.1f km/h", mps * 3.6)
        SpeedUnit.MPH -> String.format(Locale.ROOT, "%.1f mph", mps * 3600 / M_PER_MILE)
        SpeedUnit.KNOTS -> String.format(Locale.ROOT, "%.1f kt", mps * 3600 / M_PER_NM)
        SpeedUnit.MPS -> String.format(Locale.ROOT, "%.1f m/s", mps)
    }

    /** Altitude: feet when distances are in miles, metres otherwise. */
    fun altitude(meters: Double, unit: DistanceUnit): String =
        if (unit == DistanceUnit.MILES) String.format(Locale.ROOT, "%.0f ft", meters / M_PER_FOOT)
        else String.format(Locale.ROOT, "%.0f m", meters)
}
