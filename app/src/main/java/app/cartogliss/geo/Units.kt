package app.cartogliss.geo

import app.cartogliss.settings.DistanceUnit
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
}
