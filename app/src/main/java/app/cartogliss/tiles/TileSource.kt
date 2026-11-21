package app.cartogliss.tiles

import app.cartogliss.map.TileMath
import app.cartogliss.settings.KeyProvider
import java.net.URLEncoder

data class LatLonBox(val south: Double, val west: Double, val north: Double, val east: Double)

/** One raster XYZ tile source. Same schema for built-in and (later) custom sources. See DESIGN.md "Catalog format". */
data class TileSource(
    val id: String,
    val name: String,
    val category: String,
    val urlTemplate: String,
    val subdomains: List<String> = emptyList(),
    val tileSize: Int = 256,
    val minZoom: Int = 0,
    val maxZoom: Int = 19,
    /** null = no key needed. */
    val keyProvider: KeyProvider? = null,
    val attribution: String = "",
    val bulkDownloadAllowed: Boolean = true,
    val coverage: LatLonBox? = null,
    /** Extra HTTP headers; `{key}` is substituted. */
    val headers: Map<String, String> = emptyMap(),
)

/** Builds request URLs and headers from a source's templates. */
object TileUrl {
    /** Returns null when the source needs a key and none is set. */
    fun build(source: TileSource, z: Int, x: Int, y: Int, key: String?): String? {
        if (source.keyProvider != null && key.isNullOrBlank()) return null
        var url = source.urlTemplate
            .replace("{z}", z.toString())
            .replace("{x}", x.toString())
            .replace("{-y}", ((1 shl z) - 1 - y).toString())
            .replace("{y}", y.toString())
        if ("{q}" in url) url = url.replace("{q}", TileMath.quadkey(z, x, y))
        if ("{s}" in url && source.subdomains.isNotEmpty()) {
            // Deterministic choice so the same tile always hits the same host (better HTTP caching).
            url = url.replace("{s}", source.subdomains[Math.floorMod(x + y, source.subdomains.size)])
        }
        if ("{key}" in url) url = url.replace("{key}", URLEncoder.encode(key.orEmpty(), "UTF-8"))
        return url
    }

    fun headers(source: TileSource, key: String?): Map<String, String> =
        source.headers.mapValues { (_, v) -> v.replace("{key}", key.orEmpty()) }
}

object BuiltInSources {
    val OSM_STANDARD = TileSource(
        id = "osm-standard",
        name = "Standard OSM",
        category = "General",
        urlTemplate = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
        tileSize = 256,
        minZoom = 0,
        maxZoom = 19,
        attribution = "© OpenStreetMap contributors",
        bulkDownloadAllowed = false,
    )

    // The full catalog (sources.json) arrives in milestone 4.
    val all: List<TileSource> = listOf(OSM_STANDARD)

    fun byId(id: String): TileSource = all.firstOrNull { it.id == id } ?: OSM_STANDARD
}
