package app.cartogliss.tiles

import app.cartogliss.settings.KeyProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TileUrlTest {
    private fun src(template: String, subdomains: List<String> = emptyList(), key: KeyProvider? = null) =
        TileSource("t", "T", "General", template, subdomains = subdomains, keyProvider = key)

    @Test
    fun xyz() {
        assertEquals(
            "https://tile.openstreetmap.org/10/511/340.png",
            TileUrl.build(BuiltInSources.OSM_STANDARD, 10, 511, 340, null),
        )
    }

    @Test
    fun wmtsOrder_zyx() {
        assertEquals("https://h/5/7/3.png", TileUrl.build(src("https://h/{z}/{y}/{x}.png"), 5, 3, 7, null))
    }

    @Test
    fun tmsFlippedY() {
        assertEquals("https://h/3/1/2.png", TileUrl.build(src("https://h/{z}/{x}/{-y}.png"), 3, 1, 5, null))
    }

    @Test
    fun quadkeyAndSubdomain() {
        val s = src("https://ecn.t{s}.tiles.virtualearth.net/tiles/a{q}.jpeg?g=1", listOf("0", "1", "2", "3"))
        assertEquals("https://ecn.t0.tiles.virtualearth.net/tiles/a213.jpeg?g=1", TileUrl.build(s, 3, 3, 5, null))
    }

    @Test
    fun keyRequired() {
        val s = src("https://h/{z}/{x}/{y}.png?apikey={key}", key = KeyProvider.THUNDERFOREST)
        assertNull(TileUrl.build(s, 1, 0, 0, null))
        assertNull(TileUrl.build(s, 1, 0, 0, " "))
        assertEquals("https://h/1/0/0.png?apikey=a%2Bb", TileUrl.build(s, 1, 0, 0, "a+b"))
    }

    @Test
    fun headerKeySubstitution() {
        val s = src("https://h/{z}/{x}/{y}", key = KeyProvider.AZURE).copy(headers = mapOf("subscription-key" to "{key}"))
        assertEquals(mapOf("subscription-key" to "secret"), TileUrl.headers(s, "secret"))
    }
}
