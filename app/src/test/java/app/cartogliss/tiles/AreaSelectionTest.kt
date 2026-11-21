package app.cartogliss.tiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AreaSelectionTest {
    @Test
    fun singleCell_countsAtAllLevels() {
        val s = AreaSelection(listOf(Cell(10, 500, 300)))
        assertEquals(1L, s.tileCount(10))
        assertEquals(4L, s.tileCount(11))
        assertEquals(1L shl 18, s.tileCount(19)) // 4^9
        assertEquals(1L, s.tileCount(5))
        assertEquals(s.tileCount(12).toInt(), s.tiles(12).count())
    }

    @Test
    fun addingParent_absorbsChildren_andChildOfSelected_isNoop() {
        val s = AreaSelection()
        Cell(10, 500, 300).children().forEach { s.add(it) }
        assertEquals(4, s.size)
        assertTrue(s.add(Cell(10, 500, 300)))
        assertEquals(1, s.size)
        assertFalse(s.add(Cell(12, 2001, 1201)))
        assertEquals(1, s.size)
    }

    @Test
    fun removeInsideLargerCell_splits() {
        val s = AreaSelection(listOf(Cell(10, 500, 300)))
        assertTrue(s.remove(Cell(12, 2000, 1200)))
        // 16 level-12 tiles minus 1.
        assertEquals(15L, s.tileCount(12))
        assertFalse(s.covers(Cell(12, 2000, 1200)))
        assertTrue(s.covers(Cell(12, 2003, 1203)))
        // Split representation: 3 cells at z11 + 3 at z12.
        assertEquals(6, s.size)
        assertFalse(s.remove(Cell(12, 2000, 1200)))
    }

    @Test
    fun fineCells_dedupeToParentsAtCoarseLevels() {
        val s = AreaSelection(listOf(Cell(14, 0, 0), Cell(14, 1, 1), Cell(14, 4, 4)))
        assertEquals(2L, s.tileCount(12)) // (0,0) and (1,1) share a z12 parent; (4,4) has another
        assertEquals(2, s.tiles(12).toSet().size)
        assertEquals(3L, s.tileCount(14))
        assertEquals(12L, s.tileCount(15))
    }

    @Test
    fun tiles_enumerationMatchesCount_mixed() {
        val s = AreaSelection(listOf(Cell(8, 10, 10), Cell(11, 100, 100), Cell(11, 101, 100), Cell(12, 300, 50)))
        for (z in 6..13) assertEquals("z$z", s.tileCount(z).toInt(), s.tiles(z).toSet().size)
    }
}
