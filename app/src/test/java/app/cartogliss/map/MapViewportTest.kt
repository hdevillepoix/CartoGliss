package app.cartogliss.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapViewportTest {
    private val tilePx = 256f

    @Test
    fun zoomSteps_clampToSourceRange() {
        val v = MapViewport(0.5, 0.5, zoom = 18)
        assertEquals(19, v.stepped(1, false, tilePx, 0, 19).zoom)
        assertEquals(19, v.stepped(5, false, tilePx, 0, 19).zoom)
        assertEquals(0, v.stepped(-30, false, tilePx, 0, 19).zoom)
    }

    @Test
    fun plusStopsAtMaxZoom_noAutomaticOverzoom() {
        val v = MapViewport(0.5, 0.5, zoom = 19)
        val next = v.stepped(1, overzoomMode = false, tilePx = tilePx, minZoom = 0, maxZoom = 19)
        assertEquals(v, next)
        assertFalse(v.canStep(1, false, 0, 19))
    }

    @Test
    fun overzoomMode_onlyChangesM_inPowersOfTwo() {
        var v = MapViewport(0.5, 0.5, zoom = 12)
        val seen = mutableListOf(v.overzoom)
        repeat(5) { v = v.stepped(1, true, tilePx, 0, 19); seen += v.overzoom }
        assertEquals(listOf(1.0, 2.0, 4.0, 8.0, 8.0, 8.0), seen)
        assertEquals(12, v.zoom)
        v = v.stepped(-1, true, tilePx, 0, 19)
        assertEquals(4.0, v.overzoom, 0.0)
        assertTrue(v.canStep(1, true, 0, 19))
    }

    @Test
    fun overzoomMode_goesDownToHalf() {
        var v = MapViewport(0.5, 0.5, zoom = 12)
        v = v.stepped(-1, true, tilePx, 0, 19)
        assertEquals(0.5, v.overzoom, 0.0)
        assertEquals(12, v.zoom)
        assertFalse(v.canStep(-1, true, 0, 19))
        assertEquals(v, v.stepped(-1, true, tilePx, 0, 19))
        // Half-size tiles: twice as many across the screen.
        val half = v.visibleTiles(1024, 1024, tilePx).map { it.x }.toSet().size
        val one = v.copy(overzoom = 1.0).visibleTiles(1024, 1024, tilePx).map { it.x }.toSet().size
        assertTrue(half >= 2 * one - 1)
        assertEquals((1L shl 12) * 0.5 * tilePx, v.scale(tilePx), 1e-6)
    }

    @Test
    fun pan_followsFinger1to1() {
        val v = MapViewport(0.5, 0.5, zoom = 2)
        val scale = v.scale(tilePx) // 1024 px per world
        val p = v.panned(dx = 256f, dy = -128f, tilePx = tilePx)
        assertEquals(0.5 - 256 / scale, p.mx, 1e-12)
        assertEquals(0.5 + 128 / scale, p.my, 1e-12)
    }

    @Test
    fun pan_wrapsAcrossAntimeridian() {
        val v = MapViewport(0.01, 0.5, zoom = 0)
        val p = v.panned(dx = 0.02f * 256f, dy = 0f, tilePx = tilePx)
        assertEquals(0.99, p.mx, 1e-9)
    }

    @Test
    fun step_keepsFocusPointFixed() {
        val v = MapViewport(0.3, 0.4, zoom = 10, overzoom = 1.0)
        val fx = 100f
        val fy = -50f
        val before = Pair(v.mx + fx / v.scale(tilePx), v.my + fy / v.scale(tilePx))
        val z = v.stepped(1, false, tilePx, 0, 19, fx, fy)
        val after = Pair(z.mx + fx / z.scale(tilePx), z.my + fy / z.scale(tilePx))
        assertEquals(before.first, after.first, 1e-12)
        assertEquals(before.second, after.second, 1e-12)
    }

    @Test
    fun visibleTiles_onlyCurrentLevel_coverScreen_withoutSeams() {
        val v = MapViewport(0.5, 0.5, zoom = 3, overzoom = 2.0)
        val tiles = v.visibleTiles(1080, 2000, tilePx)
        assertTrue(tiles.isNotEmpty())
        assertTrue(tiles.all { it.z == 3 })
        // Tiles are 512 px (256 × M=2); the screen centre sits on a tile corner.
        val first = tiles.first()
        assertEquals(512, first.right - first.left)
        // Neighbouring tiles share edges exactly.
        val byPos = tiles.associateBy { it.left to it.top }
        for (t in tiles) {
            byPos[t.right to t.top]?.let { assertEquals(t.right, it.left) }
        }
        // x indices are wrapped into [0, 2^z).
        assertTrue(tiles.all { it.x in 0 until 8 && it.y in 0 until 8 })
    }

    @Test
    fun visibleTiles_clipVertically_atWorldEdge() {
        val v = MapViewport(0.5, 0.0, zoom = 1)
        val tiles = v.visibleTiles(500, 1000, tilePx)
        assertTrue(tiles.all { it.y >= 0 })
    }
}
