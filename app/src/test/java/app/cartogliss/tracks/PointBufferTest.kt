package app.cartogliss.tracks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PointBufferTest {
    private fun p(t: Long) = TrackPoint(t, 0.0, 0.0, null, null, 0)

    @Test
    fun flushesWhenFull() {
        val b = PointBuffer({ 3 })
        assertFalse(b.add(p(1), 0))
        assertFalse(b.add(p(2), 1000))
        assertTrue(b.add(p(3), 2000))
        assertEquals(listOf(1L, 2L, 3L), b.drain().map { it.timeMs })
        assertEquals(0, b.size)
        assertFalse(b.due(10_000_000))
    }

    @Test
    fun flushesWhenOldestWaitedTooLong() {
        val b = PointBuffer({ 100 }, maxAgeMs = 60_000)
        b.add(p(1), 0)
        assertFalse(b.due(59_999))
        assertTrue(b.due(60_000))
    }

    @Test
    fun sizeOneWritesEveryPoint() {
        val b = PointBuffer({ 0 }) // 0 is treated as 1
        assertTrue(b.add(p(1), 0))
    }
}
