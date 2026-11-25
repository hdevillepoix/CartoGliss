package app.cartogliss.tiles

import app.cartogliss.map.TileMath
import app.cartogliss.settings.KeyProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CatalogTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val builtIns = SourceCatalog.parseBuiltIn(File("src/main/assets/sources.json").readText())

    @Test
    fun bundledCatalog_isValid() {
        assertEquals(13, builtIns.size)
        assertEquals(builtIns.size, builtIns.map { it.id }.toSet().size)
        for (s in builtIns) {
            assertTrue("${s.id}: ${TileUrl.validate(s.urlTemplate)}", TileUrl.validate(s.urlTemplate).isEmpty())
            assertTrue(s.id, s.minZoom <= s.maxZoom)
            assertTrue(s.id, s.category in listOf("General", "Hiking", "Biking", "Local"))
            if ("{key}" in s.urlTemplate || s.headers.values.any { "{key}" in it }) assertNotNull(s.id, s.keyProvider)
            if ("{s}" in s.urlTemplate) assertTrue(s.id, s.subdomains.isNotEmpty())
            assertNotNull(s.id, TileUrl.build(s, 5, 3, 7, "k"))
        }
        val osm = builtIns.first { it.id == BuiltInSources.DEFAULT_ID }
        assertEquals(BuiltInSources.OSM_STANDARD, osm)
        assertFalse(osm.bulkDownloadAllowed)
        assertEquals(KeyProvider.AZURE, builtIns.first { it.id == "azure-imagery" }.keyProvider)
    }

    @Test
    fun gaUrl_usesRowThenColumn() {
        val ga = builtIns.first { it.id == "ga-topo-au" }
        assertTrue(TileUrl.build(ga, 10, 900, 600, null)!!.endsWith("/GoogleMapsCompatible/10/600/900.png"))
    }

    @Test
    fun gaNatmap_rowThenColumn_maxZoom12() {
        val nm = builtIns.first { it.id == "ga-natmap-250k" }
        assertEquals(12, nm.maxZoom)
        assertTrue(TileUrl.build(nm, 12, 3505, 2417, null)!!.endsWith("/GoogleMapsCompatible/12/2417/3505"))
    }

    @Test
    fun coverage() {
        val os = builtIns.first { it.id == "os-outdoor" }
        val z = 10
        assertTrue(os.covers(z, TileMath.tileX(-1.5, z), TileMath.tileY(53.0, z))) // Peak District
        assertFalse(os.covers(z, TileMath.tileX(2.35, z), TileMath.tileY(48.86, z))) // Paris
        assertTrue(builtIns.first { it.id == "osm-standard" }.covers(0, 0, 0))
    }

    @Test
    fun missingKey() {
        val tf = builtIns.first { it.id == "tf-outdoors" }
        assertEquals(KeyProvider.THUNDERFOREST, tf.missingKey(emptyMap()))
        assertNull(tf.missingKey(mapOf(KeyProvider.THUNDERFOREST to "x")))
        assertNull(tf.copy(customKey = "own").missingKey(emptyMap()))
    }

    @Test
    fun registry_customSources_overrides_persist() {
        val file = File(tmp.root, "sources_user.json")
        val r = SourceRegistry(builtIns, file)
        val custom = builtIns.first { it.id == "os-outdoor" }.copy(id = r.newCustomId(), name = "OS Road", urlTemplate = "https://api.os.uk/maps/raster/v1/zxy/Road_3857/{z}/{x}/{y}.png?key={key}", builtIn = false)
        r.saveCustom(custom)
        r.setMaxZoomOverride("os-outdoor", 20)
        r.setHidden("ms-satellite", true)

        val reloaded = SourceRegistry(builtIns, file)
        assertEquals(custom, reloaded.byId(custom.id))
        assertEquals(20, reloaded.byId("os-outdoor")!!.maxZoom)
        assertTrue(reloaded.isHidden("ms-satellite"))
        assertEquals(builtIns.size + 1, reloaded.sources.value.size)

        reloaded.deleteCustom(custom.id)
        reloaded.setMaxZoomOverride("os-outdoor", null)
        assertNull(reloaded.byId(custom.id))
        assertEquals(16, reloaded.byId("os-outdoor")!!.maxZoom)
        assertEquals(BuiltInSources.DEFAULT_ID, reloaded.resolve("no-such-source").id)
    }

    @Test
    fun registry_customCannotShadowBuiltIn() {
        val r = SourceRegistry(builtIns, File(tmp.root, "u.json"))
        r.saveCustom(builtIns.first().copy(name = "Impostor"))
        assertEquals(builtIns.first().name, r.byId(builtIns.first().id)!!.name)
    }
}
