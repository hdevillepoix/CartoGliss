package app.cartogliss.tracks

import app.cartogliss.settings.TrackSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter

class GpxTest {
    private val t0 = 1_780_000_000_000L

    @Test
    fun filter_accuracy_interval_distance() {
        val f = TrackFilter(TrackSettings(minIntervalS = 5, minDistanceM = 10, maxAccuracyM = 30))
        val a = TrackPoint(t0, 45.0, 6.0, accuracyM = 5f)
        assertTrue(f.accept(null, a))
        assertFalse(f.accept(null, a.copy(accuracyM = 50f)))                         // too inaccurate
        assertFalse(f.accept(a, a.copy(timeMs = t0 + 3000, lat = 45.001)))           // too soon
        assertFalse(f.accept(a, a.copy(timeMs = t0 + 6000, lat = 45.00005)))         // ~5.6 m: too close
        assertTrue(f.accept(a, a.copy(timeMs = t0 + 6000, lat = 45.0002)))           // ~22 m after 6 s
        assertTrue(f.accept(a, a.copy(timeMs = t0 + 6000, lat = 45.0002, accuracyM = null)))
    }

    @Test
    fun writeThenParse_roundTrip_withSegments() {
        val pts = listOf(
            TrackPoint(t0, 45.0, 6.0, 1000.0, 4f, seg = 0),
            TrackPoint(t0 + 10_000, 45.001, 6.001, 1010.0, 4f, seg = 0),
            TrackPoint(t0 + 60_000, 45.002, 6.002, null, null, seg = 1),
        )
        val out = StringWriter().also { GpxWriter.write("Morning <walk> & co", pts, it) }.toString()
        assertTrue(out.contains("&lt;walk&gt; &amp; co"))
        val g = GpxParser.parse(out.byteInputStream())
        assertEquals("Morning <walk> & co", g.name)
        assertEquals(2, g.segments.size)
        assertEquals(3, g.points.size)
        assertEquals(45.001, g.points[1].lat, 1e-9)
        assertEquals(1010.0, g.points[1].eleM!!, 1e-9)
        assertEquals(t0 + 10_000, g.points[1].timeMs)
        assertEquals(t0, g.startMs)
        assertEquals(t0 + 60_000, g.endMs)
        // Distance only within segments: the gap between segments doesn't count.
        assertEquals(TrackStats.distance(pts.take(2)), g.distanceM, 1e-6)
    }

    @Test
    fun parsesForeignGpx_routes_andMissingTimes() {
        val gpx = """<gpx xmlns="http://www.topografix.com/GPX/1/0"><rte><name>R</name>
            <rtept lat="1" lon="2"/><rtept lat="1.1" lon="2.1"><time>bad</time></rtept></rte></gpx>"""
        val g = GpxParser.parse(gpx.byteInputStream())
        assertEquals(1, g.segments.size)
        assertEquals(0L, g.points[1].timeMs)
    }

    @Test
    fun durationFormat() {
        assertEquals("0:42", TrackStats.duration(42_000))
        assertEquals("12:05", TrackStats.duration(725_000))
        assertEquals("1:05:10", TrackStats.duration(3_910_000))
    }
}
