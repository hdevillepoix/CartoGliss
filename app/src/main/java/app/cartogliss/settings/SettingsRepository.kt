package app.cartogliss.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Typed access to the app's persisted settings (DataStore Preferences). */
class SettingsRepository(private val store: DataStore<Preferences>) {

    val settings: Flow<Settings> = store.data.map(::read)

    suspend fun setDistanceUnit(unit: DistanceUnit) = store.edit { it[K.distanceUnit] = unit.name }
    suspend fun setSpeedUnit(unit: SpeedUnit) = store.edit { it[K.speedUnit] = unit.name }
    suspend fun setAngleFormat(format: AngleFormat) = store.edit { it[K.angleFormat] = format.name }

    suspend fun setApiKey(provider: KeyProvider, key: String) = store.edit {
        val k = K.apiKey(provider)
        if (key.isBlank()) it.remove(k) else it[k] = key.trim()
    }

    suspend fun setStarred(sourceId: String, starred: Boolean) = store.edit {
        val current = it[K.starred] ?: Settings.DEFAULT_STARRED
        it[K.starred] = if (starred) current + sourceId else current - sourceId
    }

    suspend fun setTrackSettings(track: TrackSettings) = store.edit {
        it[K.trackInterval] = track.minIntervalS
        it[K.trackDistance] = track.minDistanceM
        it[K.trackAccuracy] = track.maxAccuracyM
    }

    suspend fun setTarget(target: Target?) = store.edit {
        if (target == null) {
            it.remove(K.targetLat); it.remove(K.targetLon); it.remove(K.targetName)
        } else {
            it[K.targetLat] = target.lat
            it[K.targetLon] = target.lon
            if (target.name == null) it.remove(K.targetName) else it[K.targetName] = target.name
        }
    }

    suspend fun setViewport(v: Viewport) = store.edit { it.writeViewport(v) }

    private fun MutablePreferences.writeViewport(v: Viewport) {
        this[K.vpLat] = v.lat
        this[K.vpLon] = v.lon
        this[K.vpZoom] = v.zoom
        this[K.vpOverzoom] = v.overzoom
        this[K.vpSource] = v.sourceId
    }

    private fun read(p: Preferences): Settings {
        val d = Settings()
        val lat = p[K.targetLat]
        val lon = p[K.targetLon]
        val dv = d.viewport
        val overzoom = p[K.vpOverzoom]?.takeIf { it in Viewport.ALLOWED_OVERZOOM } ?: dv.overzoom
        return Settings(
            distanceUnit = enumOr(p[K.distanceUnit], d.distanceUnit),
            speedUnit = enumOr(p[K.speedUnit], d.speedUnit),
            angleFormat = enumOr(p[K.angleFormat], d.angleFormat),
            apiKeys = KeyProvider.entries.mapNotNull { kp -> p[K.apiKey(kp)]?.let { kp to it } }.toMap(),
            starredSources = p[K.starred] ?: d.starredSources,
            track = TrackSettings(
                minIntervalS = p[K.trackInterval] ?: d.track.minIntervalS,
                minDistanceM = p[K.trackDistance] ?: d.track.minDistanceM,
                maxAccuracyM = p[K.trackAccuracy] ?: d.track.maxAccuracyM,
            ),
            target = if (lat != null && lon != null) Target(lat, lon, p[K.targetName]) else null,
            viewport = Viewport(
                lat = p[K.vpLat] ?: dv.lat,
                lon = p[K.vpLon] ?: dv.lon,
                zoom = p[K.vpZoom] ?: dv.zoom,
                overzoom = overzoom,
                sourceId = p[K.vpSource] ?: dv.sourceId,
            ),
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

    private object K {
        val distanceUnit = stringPreferencesKey("units.distance")
        val speedUnit = stringPreferencesKey("units.speed")
        val angleFormat = stringPreferencesKey("units.angle")
        fun apiKey(p: KeyProvider) = stringPreferencesKey("key.${p.name.lowercase()}")
        val starred = stringSetPreferencesKey("sources.starred")
        val trackInterval = intPreferencesKey("track.minIntervalS")
        val trackDistance = intPreferencesKey("track.minDistanceM")
        val trackAccuracy = intPreferencesKey("track.maxAccuracyM")
        val targetLat = doublePreferencesKey("target.lat")
        val targetLon = doublePreferencesKey("target.lon")
        val targetName = stringPreferencesKey("target.name")
        val vpLat = doublePreferencesKey("viewport.lat")
        val vpLon = doublePreferencesKey("viewport.lon")
        val vpZoom = intPreferencesKey("viewport.zoom")
        val vpOverzoom = intPreferencesKey("viewport.overzoom")
        val vpSource = stringPreferencesKey("viewport.source")
    }
}
