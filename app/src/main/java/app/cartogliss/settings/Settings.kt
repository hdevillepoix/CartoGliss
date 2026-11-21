package app.cartogliss.settings

enum class DistanceUnit(val label: String) {
    METRIC("m / km"),
    MILES("miles"),
    NAUTICAL("nautical miles"),
}

enum class SpeedUnit(val label: String) {
    KMH("km/h"),
    MPH("mph"),
    KNOTS("kt"),
    MPS("m/s"),
}

enum class AngleFormat(val label: String) {
    /** Default: 45.12346, -6.12345 */
    SIGNED("Signed decimal degrees (45.12346, -6.12345)"),
    DD("Decimal degrees with N/S/E/W"),
    DDM("Degrees + decimal minutes"),
    DMS("Degrees + minutes + decimal seconds"),
}

/** Services whose API key is entered in Settings → Map server keys. */
enum class KeyProvider(val label: String) {
    THUNDERFOREST("Thunderforest"),
    MAPBOX("Mapbox"),
    TRACESTRACK("Tracestrack"),
    OS("Ordnance Survey (OS Maps API)"),
    AZURE("Azure Maps"),
}

data class TrackSettings(
    /** Minimum time between recorded points, in seconds. */
    val minIntervalS: Int = 5,
    /** Minimum distance between recorded points, in metres. */
    val minDistanceM: Int = 5,
    /** Fixes with a worse accuracy than this (metres) are dropped. */
    val maxAccuracyM: Int = 30,
    /** Points kept in memory before they are written to storage (1 = write every point). */
    val bufferPoints: Int = 10,
)

data class Target(
    val lat: Double,
    val lon: Double,
    /** Set when the target comes from a KML waypoint. */
    val name: String? = null,
)

data class Viewport(
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val zoom: Int = 2,
    /** Overzoom factor M ∈ {0.5, 1, 2, 4, 8} (0.5 draws the tiles at half size). */
    val overzoom: Double = 1.0,
    val sourceId: String = DEFAULT_SOURCE_ID,
) {
    companion object {
        const val DEFAULT_SOURCE_ID = "osm-standard"
        val ALLOWED_OVERZOOM = listOf(0.5, 1.0, 2.0, 4.0, 8.0)
    }
}

data class Settings(
    val distanceUnit: DistanceUnit = DistanceUnit.METRIC,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val angleFormat: AngleFormat = AngleFormat.SIGNED,
    /** Keys in use: typed in Settings, else built into the APK (keys.env). */
    val apiKeys: Map<KeyProvider, String> = emptyMap(),
    /** Keys typed in Settings only. */
    val userKeys: Map<KeyProvider, String> = emptyMap(),
    val starredSources: Set<String> = DEFAULT_STARRED,
    val track: TrackSettings = TrackSettings(),
    val target: Target? = null,
    /** First launch: whole world, OSM Standard, z2. Afterwards, the last viewport. */
    val viewport: Viewport = Viewport(),
    /** File names of the KML collections shown on the map. */
    val activeKml: Set<String> = DEFAULT_ACTIVE_KML,
    /** File names of saved GPX tracks drawn on the map. */
    val shownTracks: Set<String> = emptySet(),
    /** Shared folder (SAF tree URI) holding tracks/ and waypoints/; null = app storage. */
    val storageTree: String? = null,
    /** The first-launch "choose a folder" question was answered. */
    val storagePrompted: Boolean = false,
) {
    companion object {
        val DEFAULT_STARRED = setOf("osm-standard", "opentopomap")
        const val DEFAULT_COLLECTION = "My waypoints.kml"
        val DEFAULT_ACTIVE_KML = setOf(DEFAULT_COLLECTION)
    }
}
