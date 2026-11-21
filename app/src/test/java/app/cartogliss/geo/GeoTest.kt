package app.cartogliss.geo

import app.cartogliss.map.MapViewport
import app.cartogliss.map.TileMath
import app.cartogliss.settings.DistanceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GeoTest {
    @Test
    fun metersPerPixel_z0_equator() {
        // Classic Web Mercator figure: 156 543.03 m/px at z0 with 256 px tiles.
        assertEquals(156_543.03, Geo.metersPerPixel(0.0, 256.0), 0.01)
        assertEquals(156_543.03 / 2, Geo.metersPerPixel(60.0, 256.0), 0.01)
    }

    @Test
    fun clip_inside_unchanged() {
        val s = Segment(10f, 10f, 90f, 50f)
        assertEquals(s, clipSegment(s, Rect2(0f, 0f, 100f, 100f)))
    }

    @Test
    fun clip_farPoint_isCutAtEdge() {
        val c = clipSegment(Segment(50f, 50f, 1e9f, 50f), Rect2(0f, 0f, 100f, 100f))
        assertNotNull(c)
        assertEquals(50f, c!!.x0)
        assertEquals(100f, c.x1, 1e-3f)
        assertEquals(50f, c.y1)
    }

    @Test
    fun clip_bothOutside_crossing() {
        val c = clipSegment(Segment(-50f, 50f, 150f, 50f), Rect2(0f, 0f, 100f, 100f))!!
        assertEquals(0f, c.x0, 1e-3f)
        assertEquals(100f, c.x1, 1e-3f)
    }

    @Test
    fun clip_miss() {
        assertNull(clipSegment(Segment(-50f, -10f, 150f, -10f), Rect2(0f, 0f, 100f, 100f)))
    }

    @Test
    fun scaleLabels() {
        assertEquals("87 m", Units.scaleLabel(87.3, DistanceUnit.METRIC))
        assertEquals("0.53 m", Units.scaleLabel(0.531, DistanceUnit.METRIC))
        assertEquals("1.2 km", Units.scaleLabel(1234.0, DistanceUnit.METRIC))
        assertEquals("150 km", Units.scaleLabel(152_000.0, DistanceUnit.METRIC))
        assertEquals("110 yd", Units.scaleLabel(100.0, DistanceUnit.MILES))
        assertEquals("1500 yd", Units.scaleLabel(1400.0, DistanceUnit.MILES))
        assertEquals("1500 m", Units.scaleLabel(1500.0, DistanceUnit.NAUTICAL))
        assertEquals("1.2 mi", Units.scaleLabel(2000.0, DistanceUnit.MILES))
        assertEquals("93 m", Units.scaleLabel(93.0, DistanceUnit.NAUTICAL))
        assertEquals("2.7 NM", Units.scaleLabel(5000.0, DistanceUnit.NAUTICAL))
    }

    @Test
    fun toScreen_centreIsScreenCentre() {
        val v = MapViewport.from(app.cartogliss.settings.Viewport(lat = 45.0, lon = 6.0, zoom = 12))
        val (x, y) = v.toScreen(45.0, 6.0, 1000, 2000, 256f)
        assertEquals(500.0, x, 1e-6)
        assertEquals(1000.0, y, 1e-6)
    }

    @Test
    fun toScreen_takesShortWayAcrossAntimeridian() {
        val v = MapViewport(TileMath.lonToX(179.0), 0.5, zoom = 4)
        val (x, _) = v.toScreen(0.0, -179.0, 1000, 1000, 256f)
        // 2° east of centre, not 358° west.
        val expected = 500 + (2.0 / 360.0) * v.scale(256f)
        assertEquals(expected, x, 1e-6)
    }
}
