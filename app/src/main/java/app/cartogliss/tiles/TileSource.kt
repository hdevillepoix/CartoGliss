package app.cartogliss.tiles

import app.cartogliss.map.TileMath
import app.cartogliss.settings.KeyProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder

@Serializable
data class LatLonBox(val south: Double, val west: Double, val north: Double, val east: Double)

/** One raster XYZ tile source. Same schema for built-in and custom sources. See DESIGN.md "Catalog format". */
data class TileSource(
    val id: String,
    val name: String,
    val category: String,
    val urlTemplate: String,
    val subdomains: List<String> = emptyList(),
    val tileSize: Int = 256,
    val minZoom: Int = 0,
    val maxZoom: Int = 19,
    /** Known key provider (key from Settings → Map server keys); null = none. */
    val keyProvider: KeyProvider? = null,
    /** A key stored with this source ("custom" key). */
    val customKey: String? = null,
    val attribution: String = "",
    val bulkDownloadAllowed: Boolean = true,
    val coverage: LatLonBox? = null,
    /** Extra HTTP headers; `{key}` is substituted. */
    val headers: Map<String, String> = emptyMap(),
    val builtIn: Boolean = false,
    val notes: String = "",
    /** Short label for the map's layers popup; falls back to [name]. */
    val shortName: String? = null,
) {
    val label: String get() = shortName?.takeIf { it.isNotBlank() } ?: name

    /** True when tile (z, x, y) intersects [coverage] (always true without a coverage box). */
    fun covers(z: Int, x: Int, y: Int): Boolean {
        val c = coverage ?: return true
        val n = (1 shl z).toDouble()
        val west = TileMath.xToLon(x / n)
        val east = TileMath.xToLon((x + 1) / n)
        val north = TileMath.yToLat(y / n)
        val south = TileMath.yToLat((y + 1) / n)
        return east > c.west && west < c.east && north > c.south && south < c.north
    }

    /** The key provider whose key is missing, if any. */
    fun missingKey(keys: Map<KeyProvider, String>): KeyProvider? =
        keyProvider?.takeIf { customKey.isNullOrBlank() && keys[it].isNullOrBlank() }
}

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

    /** Problems with a URL template, empty when usable. */
    fun validate(template: String): List<String> {
        val problems = mutableListOf<String>()
        if (!template.startsWith("https://") && !template.startsWith("http://")) problems += "must start with https:// or http://"
        val hasXyz = "{z}" in template && "{x}" in template && ("{y}" in template || "{-y}" in template)
        if (!hasXyz && "{q}" !in template) problems += "needs {z}, {x} and {y} (or {-y}), or {q}"
        return problems
    }
}

/** JSON form of a source, used by the bundled `sources.json` and the user's custom sources file. */
@Serializable
data class SourceDto(
    val id: String,
    val name: String,
    val category: String,
    val urlTemplate: String,
    val subdomains: List<String> = emptyList(),
    val tileSize: Int = 256,
    val minZoom: Int = 0,
    val maxZoom: Int = 19,
    /** thunderforest / mapbox / tracestrack / os / azure, or absent. */
    val keyProvider: String? = null,
    val customKey: String? = null,
    val attribution: String = "",
    val bulkDownloadAllowed: Boolean = true,
    val coverage: LatLonBox? = null,
    val headers: Map<String, String> = emptyMap(),
    val notes: String = "",
    val shortName: String? = null,
) {
    fun toSource(builtIn: Boolean) = TileSource(
        id = id, name = name, category = category, urlTemplate = urlTemplate, subdomains = subdomains,
        tileSize = tileSize, minZoom = minZoom, maxZoom = maxZoom,
        keyProvider = keyProvider?.let { kp -> KeyProvider.entries.first { it.name.equals(kp, ignoreCase = true) } },
        customKey = customKey, attribution = attribution, bulkDownloadAllowed = bulkDownloadAllowed,
        coverage = coverage, headers = headers, builtIn = builtIn, notes = notes, shortName = shortName,
    )

    companion object {
        fun from(s: TileSource) = SourceDto(
            id = s.id, name = s.name, category = s.category, urlTemplate = s.urlTemplate, subdomains = s.subdomains,
            tileSize = s.tileSize, minZoom = s.minZoom, maxZoom = s.maxZoom,
            keyProvider = s.keyProvider?.name?.lowercase(), customKey = s.customKey, attribution = s.attribution,
            bulkDownloadAllowed = s.bulkDownloadAllowed, coverage = s.coverage, headers = s.headers, notes = s.notes,
            shortName = s.shortName,
        )
    }
}

@Serializable
data class CatalogFile(val sources: List<SourceDto>)

object SourceCatalog {
    val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = false }

    fun parseBuiltIn(text: String): List<TileSource> = json.decodeFromString<CatalogFile>(text).sources.map { it.toSource(builtIn = true) }
}

object BuiltInSources {
    const val DEFAULT_ID = "osm-standard"

    /** Fallback used if the catalog can't be read; identical to the catalog's entry. */
    val OSM_STANDARD = TileSource(
        id = DEFAULT_ID,
        name = "Standard OSM",
        category = "General",
        urlTemplate = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
        tileSize = 256,
        minZoom = 0,
        maxZoom = 19,
        attribution = "© OpenStreetMap contributors",
        bulkDownloadAllowed = false,
        builtIn = true,
        notes = "OSMF tile usage policy: no bulk downloading.",
        shortName = "OSM",
    )
}
