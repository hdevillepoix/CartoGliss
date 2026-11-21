package app.cartogliss.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class AstroTest {
    @Test
    fun uv_formula() {
        assertEquals(12.5, Uv.index(90.0, 0.0), 1e-9)
        assertEquals(15.0, Uv.index(90.0, 2000.0), 1e-9)
        assertEquals(0.0, Uv.index(-5.0, 0.0), 0.0)
        // cos(SZA)=0.5 at 30° elevation → 12.5·0.5^2.42
        assertEquals(12.5 * Math.pow(0.5, 2.42), Uv.index(30.0, 0.0), 1e-9)
    }

    @Test
    fun crossing_linearRamp() {
        val t0 = Instant.parse("2026-06-21T00:00:00Z")
        val f: (Instant) -> Double = { Duration.between(t0, it).toMinutes().toDouble() + Duration.between(t0, it).toSecondsPart() / 60.0 }
        val c = findCrossing(t0, t0.plusSeconds(3600), Duration.ofMinutes(1), 30.5, f)
        assertNotNull(c)
        assertTrue(c!!.rising)
        assertEquals(30 * 60 + 30.0, Duration.between(t0, c.time).seconds.toDouble(), 1.0)
    }

    @Test
    fun crossing_none() {
        val t0 = Instant.parse("2026-06-21T00:00:00Z")
        assertNull(findCrossing(t0, t0.plusSeconds(3600), Duration.ofMinutes(1), 5.0) { 1.0 })
    }

    @Test
    fun summerDay_inGeneva_reachesUv3_inTheMorning() {
        val zone = ZoneId.of("Europe/Zurich")
        val now = ZonedDateTime.of(2026, 6, 21, 6, 0, 0, 0, zone)
        val a = Astro.compute(46.2, 6.15, 400.0, now)
        assertNotNull(a.sunRise)
        assertNotNull(a.sunSet)
        assertTrue(a.sunRise!!.hour in 5..6)
        assertTrue(a.sunSet!!.hour in 21..21)
        val c = a.nextUv3!!
        assertTrue(c.rising)
        val local = c.time.atZone(zone)
        assertTrue("UV 3 at $local", local.hour in 8..10)
    }

    @Test
    fun winterEvening_noMoreUv3() {
        val zone = ZoneId.of("Europe/London")
        val now = ZonedDateTime.of(2026, 12, 21, 14, 0, 0, 0, zone)
        val a = Astro.compute(51.5, -0.12, 0.0, now)
        assertNull(a.nextUv3)
        assertTrue(a.uvNow < 3)
    }
}
