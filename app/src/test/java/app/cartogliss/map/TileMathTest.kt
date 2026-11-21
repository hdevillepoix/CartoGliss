package app.cartogliss.map

import org.junit.Assert.assertEquals
import org.junit.Test

class TileMathTest {
    @Test
    fun origin_isWorldCentre() {
        assertEquals(0.5, TileMath.lonToX(0.0), 1e-12)
        assertEquals(0.5, TileMath.latToY(0.0), 1e-12)
    }

    @Test
    fun london_z10() {
        assertEquals(511, TileMath.tileX(-0.1278, 10))
        assertEquals(340, TileMath.tileY(51.5074, 10))
    }

    @Test
    fun roundTrip() {
        for ((lat, lon) in listOf(51.5 to -0.12, -33.86 to 151.21, 0.0 to 179.99, 84.9 to -179.0)) {
            assertEquals(lat, TileMath.yToLat(TileMath.latToY(lat)), 1e-9)
            assertEquals(lon, TileMath.xToLon(TileMath.lonToX(lon)), 1e-9)
        }
    }

    @Test
    fun latitude_isClampedToMercatorLimit() {
        assertEquals(0.0, TileMath.latToY(90.0), 1e-9)
        assertEquals(1.0, TileMath.latToY(-90.0), 1e-9)
    }

    @Test
    fun quadkey_bingExample() {
        // From Microsoft's "Bing Maps Tile System" article: tile (3, 5) at level 3 → "213".
        assertEquals("213", TileMath.quadkey(3, 3, 5))
        assertEquals("", TileMath.quadkey(0, 0, 0))
    }

    @Test
    fun wrapX() {
        assertEquals(0.25, TileMath.wrapX(1.25), 1e-12)
        assertEquals(0.75, TileMath.wrapX(-0.25), 1e-12)
    }
}
