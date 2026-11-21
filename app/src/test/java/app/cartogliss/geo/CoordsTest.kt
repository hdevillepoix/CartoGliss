package app.cartogliss.geo

import app.cartogliss.settings.AngleFormat
import app.cartogliss.settings.DistanceUnit
import app.cartogliss.settings.SpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class CoordsTest {
    @Test
    fun formats() {
        val lat = 45.123456
        val lon = -6.5
        assertEquals("N 45.12346°", Coords.formatLat(lat, AngleFormat.DD))
        assertEquals("N 45°07.407'", Coords.formatLat(lat, AngleFormat.DDM))
        assertEquals("N 45°07'24.4\"", Coords.formatLat(lat, AngleFormat.DMS))
        assertEquals("W 006°30.000'", Coords.formatLon(lon, AngleFormat.DDM))
        assertEquals("S 33.86000°", Coords.formatLat(-33.86, AngleFormat.DD))
    }

    @Test
    fun rounding_carriesIntoDegrees() {
        assertEquals("N 46°00.000'", Coords.formatLat(45.9999999, AngleFormat.DDM))
        assertEquals("N 46°00'00.0\"", Coords.formatLat(45.9999999, AngleFormat.DMS))
    }

    @Test
    fun distanceAndBearing() {
        // London → Paris ≈ 343.5 km, initial bearing ≈ 148°
        val d = Geo.distance(51.5074, -0.1278, 48.8566, 2.3522)
        assertEquals(343_500.0, d, 1500.0)
        assertEquals(148.0, Geo.bearing(51.5074, -0.1278, 48.8566, 2.3522), 1.0)
        assertEquals(90.0, Geo.bearing(0.0, 0.0, 0.0, 1.0), 1e-9)
    }

    @Test
    fun compass() {
        assertEquals("N", Geo.compass16(0.0))
        assertEquals("N", Geo.compass16(359.0))
        assertEquals("ESE", Geo.compass16(112.5))
        assertEquals("SW", Geo.compass16(225.0))
        assertEquals("NNW", Geo.compass16(-22.5))
    }

    @Test
    fun unitLabels() {
        assertEquals("850 m", Units.distance(850.0, DistanceUnit.METRIC))
        assertEquals("1.23 km", Units.distance(1234.0, DistanceUnit.METRIC))
        assertEquals("12.3 km", Units.distance(12_340.0, DistanceUnit.METRIC))
        assertEquals("123 km", Units.distance(123_400.0, DistanceUnit.METRIC))
        assertEquals("547 yd", Units.distance(500.0, DistanceUnit.MILES))
        assertEquals("1.00 mi", Units.distance(1609.4, DistanceUnit.MILES))
        assertEquals("1800 m", Units.distance(1800.0, DistanceUnit.NAUTICAL))
        assertEquals("1.08 NM", Units.distance(2000.0, DistanceUnit.NAUTICAL))
        assertEquals("36.0 km/h", Units.speed(10.0, SpeedUnit.KMH))
        assertEquals("19.4 kt", Units.speed(10.0, SpeedUnit.KNOTS))
        assertEquals("3281 ft", Units.altitude(1000.0, DistanceUnit.MILES))
    }

    @Test
    fun signedDecimalIsTheDefault() {
        assertEquals(AngleFormat.SIGNED, app.cartogliss.settings.Settings().angleFormat)
        assertEquals("45.12346", Coords.formatLat(45.123456, AngleFormat.SIGNED))
        assertEquals("-6.50000", Coords.formatLon(-6.5, AngleFormat.SIGNED))
        assertEquals("45.12346, -6.50000", Coords.format(45.123456, -6.5, AngleFormat.SIGNED))
        assertEquals("0.00000", Coords.formatLat(-0.000001, AngleFormat.SIGNED))
        // What is displayed can be typed back.
        assertEquals(-6.5, CoordInput.parse(Coords.formatLon(-6.5, AngleFormat.SIGNED), isLat = false)!!, 1e-9)
    }
}
