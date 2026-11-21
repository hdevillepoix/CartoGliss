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
    DD("Decimal degrees"),
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
    /** Overzoom factor M ∈ {1, 2, 4, 8}. */
    val overzoom: Int = 1,
    val sourceId: String = DEFAULT_SOURCE_ID,
) {
    companion object {
        const val DEFAULT_SOURCE_ID = "osm-standard"
        val ALLOWED_OVERZOOM = listOf(1, 2, 4, 8)
    }
}

data class Settings(
    val distanceUnit: DistanceUnit = DistanceUnit.METRIC,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val angleFormat: AngleFormat = AngleFormat.DD,
    val apiKeys: Map<KeyProvider, String> = emptyMap(),
    val starredSources: Set<String> = DEFAULT_STARRED,
    val track: TrackSettings = TrackSettings(),
    val target: Target? = null,
    /** First launch: whole world, OSM Standard, z2. Afterwards, the last viewport. */
    val viewport: Viewport = Viewport(),
    /** File names of the KML collections shown on the map. */
    val activeKml: Set<String> = DEFAULT_ACTIVE_KML,
    /** File names of saved GPX tracks drawn on the map. */
    val shownTracks: Set<String> = emptySet(),
) {
    companion object {
        val DEFAULT_STARRED = setOf("osm-standard", "opentopomap")
        const val DEFAULT_COLLECTION = "My waypoints.kml"
        val DEFAULT_ACTIVE_KML = setOf(DEFAULT_COLLECTION)
    }
}
