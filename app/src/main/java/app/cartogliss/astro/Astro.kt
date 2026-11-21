package app.cartogliss.astro

import org.shredzone.commons.suncalc.MoonTimes
import org.shredzone.commons.suncalc.SunPosition
import org.shredzone.commons.suncalc.SunTimes
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

object Uv {
    /**
     * Clear-sky UV index from sun elevation and altitude (DESIGN.md):
     * UVI = 12.5 · cos(SZA)^2.42 · (1 + 0.10 · alt_km). Ozone fixed at 300 DU. 0 when the sun is down.
     */
    fun index(sunElevationDeg: Double, altitudeM: Double): Double {
        if (sunElevationDeg <= 0.0) return 0.0
        val cosSza = sin(Math.toRadians(sunElevationDeg))
        return 12.5 * cosSza.pow(2.42) * (1.0 + 0.10 * max(0.0, altitudeM) / 1000.0)
    }
}

data class Crossing(val time: Instant, val rising: Boolean)

/**
 * First time in [from, until] where f crosses [level], scanning in [step] increments and refining by
 * bisection to about a second.
 */
fun findCrossing(from: Instant, until: Instant, step: Duration, level: Double, f: (Instant) -> Double): Crossing? {
    var t = from
    var v = f(t) - level
    while (t < until) {
        val t2 = minOf(t + step, until)
        val v2 = f(t2) - level
        if ((v < 0 && v2 >= 0) || (v > 0 && v2 <= 0)) {
            var lo = t
            var hi = t2
            val loNeg = v < 0
            while (Duration.between(lo, hi) > Duration.ofSeconds(1)) {
                val mid = lo + Duration.between(lo, hi).dividedBy(2)
                val vm = f(mid) - level
                if ((vm < 0) == loNeg) lo = mid else hi = mid
            }
            return Crossing(hi, rising = loNeg)
        }
        t = t2
        v = v2
    }
    return null
}

/** Rise/set times are null when there is none that day (polar day/night). */
data class AstroInfo(
    val sunRise: ZonedDateTime?,
    val sunSet: ZonedDateTime?,
    val sunAlwaysUp: Boolean,
    val sunAlwaysDown: Boolean,
    val moonRise: ZonedDateTime?,
    val moonSet: ZonedDateTime?,
    val sunElevationDeg: Double,
    val uvNow: Double,
    /** Next UVI = 3 crossing before the next sunset; null if there is none. */
    val nextUv3: Crossing?,
)

object Astro {
    const val UV_LEVEL = 3.0

    fun sunElevation(t: Instant, lat: Double, lon: Double): Double =
        SunPosition.compute().on(t).at(lat, lon).execute().trueAltitude

    fun compute(lat: Double, lon: Double, altitudeM: Double, now: ZonedDateTime): AstroInfo {
        val dayStart = now.toLocalDate().atStartOfDay(now.zone)
        val sun = SunTimes.compute().on(dayStart).at(lat, lon).limit(Duration.ofDays(1)).execute()
        val moon = MoonTimes.compute().on(dayStart).at(lat, lon).limit(Duration.ofDays(1)).execute()

        val elevation = sunElevation(now.toInstant(), lat, lon)
        val uv = Uv.index(elevation, altitudeM)

        val nextSet = SunTimes.compute().on(now).at(lat, lon).limit(Duration.ofDays(2)).execute().set
        val until = (nextSet ?: now.plusDays(1)).toInstant()
        val crossing = findCrossing(now.toInstant(), until, Duration.ofMinutes(1), UV_LEVEL) {
            Uv.index(sunElevation(it, lat, lon), altitudeM)
        }

        return AstroInfo(
            sunRise = sun.rise,
            sunSet = sun.set,
            sunAlwaysUp = sun.isAlwaysUp,
            sunAlwaysDown = sun.isAlwaysDown,
            moonRise = moon.rise,
            moonSet = moon.set,
            sunElevationDeg = elevation,
            uvNow = uv,
            nextUv3 = crossing,
        )
    }
}
