package app.cartogliss.kml

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import app.cartogliss.settings.Settings
import app.cartogliss.settings.SettingsRepository
import app.cartogliss.storage.DocDir
import app.cartogliss.storage.Storage
import app.cartogliss.storage.uniqueName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.filterNotNull

data class KmlCollection(
    /** File name in the waypoints folder, e.g. "My waypoints.kml". Used as the id. */
    val id: String,
    val title: String,
    val active: Boolean,
    val doc: KmlDocument?,
    val error: String?,
) {
    val waypointCount get() = doc?.points?.size ?: 0
    val lineCount get() = doc?.placemarks?.sumOf { p -> p.geometries.count { it is KmlGeometry.Line } } ?: 0
    val areaCount get() = doc?.placemarks?.sumOf { p -> p.geometries.count { it is KmlGeometry.Polygon } } ?: 0
}

/** Identifies a placemark in a collection file, for deleting it. [name] is the raw name (null if unnamed). */
data class PlacemarkRef(val collectionId: String, val index: Int, val name: String?)

/** One waypoint of an active collection, for Browse and map taps. */
data class Waypoint(
    val name: String,
    val lat: Double,
    val lon: Double,
    val description: String?,
    val collectionId: String,
    val collectionTitle: String,
    val ref: PlacemarkRef = PlacemarkRef(collectionId, -1, null),
)

/**
 * KML collections. The files in the waypoints folder are the source of truth (DESIGN.md); this keeps a
 * parsed copy per file, re-parsed when the file changes. Files dropped into the folder by other apps
 * appear on the next [refresh].
 */
class KmlRepository(
    private val storage: Storage,
    private val settings: SettingsRepository,
    scope: CoroutineScope,
) {
    private class Parsed(val stamp: Pair<Long, Long>, val doc: KmlDocument?, val error: String?)

    private val mutex = Mutex()
    private val parsed = MutableStateFlow<Map<String, Parsed>>(emptyMap())
    private var parsedDirId: String? = null

    private suspend fun dir(): DocDir = storage.current().waypoints

    val collections: StateFlow<List<KmlCollection>> =
        combine(parsed, settings.settings.map { it.activeKml }) { files, active ->
            files.entries.sortedBy { it.key.lowercase() }.map { (id, p) ->
                KmlCollection(id, p.doc?.name ?: id.removeSuffix(".kml"), id in active, p.doc, p.error)
            }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val activeWaypoints: StateFlow<List<Waypoint>> = collections.map { list ->
        list.filter { it.active }.flatMap { c ->
            c.doc?.points.orEmpty().map { (pm, at) -> Waypoint(pm.name ?: "(unnamed)", at.lat, at.lon, pm.description, c.id, c.title, PlacemarkRef(c.id, pm.index, pm.name)) }
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        // (Re)load whenever the storage location changes, including at startup.
        scope.launch {
            storage.dirs.filterNotNull().collect { d ->
                mutex.withLock {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            if (d.waypoints.list().none { it.name.endsWith(".kml", ignoreCase = true) }) {
                                d.waypoints.write(Settings.DEFAULT_COLLECTION) { KmlWriter.write(KmlWriter.newDocument("My waypoints"), it, indent = true) }
                            }
                        }
                    }
                    refreshLocked()
                }
            }
        }
    }

    /** Rescan the folder and re-parse changed files. */
    suspend fun refresh() = mutex.withLock { refreshLocked() }

    private suspend fun refreshLocked() = withContext(Dispatchers.IO) {
        val dir = dir()
        val old = if (parsedDirId == dir.id) parsed.value else emptyMap()
        val files = runCatching { dir.list() }.getOrElse { emptyList() }.filter { it.name.endsWith(".kml", ignoreCase = true) }
        parsed.value = files.associate { f ->
            val stamp = f.modified to f.size
            val prev = old[f.name]
            f.name to if (prev != null && prev.stamp == stamp) prev else {
                try {
                    Parsed(stamp, dir.read(f.name, KmlParser::parse), null)
                } catch (e: Exception) {
                    Parsed(stamp, null, e.message ?: e.javaClass.simpleName)
                }
            }
        }
        parsedDirId = dir.id
    }

    /** Create an empty collection; returns its id. */
    suspend fun create(title: String): String = mutex.withLock {
        val dir = dir()
        val name = withContext(Dispatchers.IO) {
            uniqueName(dir, safeName(title)).also { n -> dir.write(n) { KmlWriter.write(KmlWriter.newDocument(title), it, indent = true) } }
        }
        settings.setKmlActive(name, true)
        refreshLocked()
        name
    }

    /** Import a .kml or .kmz picked by the user. Returns the new collection id. */
    suspend fun import(resolver: ContentResolver, uri: Uri): String = mutex.withLock {
        val (bytes, displayName) = withContext(Dispatchers.IO) {
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
            val data = resolver.openInputStream(uri)?.use { input ->
                val buf = input.readBytes()
                require(buf.size <= MAX_IMPORT_BYTES) { "File is larger than ${MAX_IMPORT_BYTES / 1_000_000} MB" }
                buf
            } ?: error("Could not open the file")
            data to (name ?: "Imported")
        }
        val kml = if (Kmz.isZip(bytes)) Kmz.extractKml(bytes) ?: error("No .kml inside this KMZ") else bytes
        // Validate before keeping it.
        val doc = withContext(Dispatchers.IO) { kml.inputStream().use(KmlParser::parse) }
        val base = displayName.substringBeforeLast('.').ifBlank { doc.name ?: "Imported" }
        val dir = dir()
        val name = withContext(Dispatchers.IO) {
            uniqueName(dir, safeName(base)).also { n -> dir.write(n) { it.write(kml) } }
        }
        settings.setKmlActive(name, true)
        refreshLocked()
        name
    }

    suspend fun delete(id: String) = mutex.withLock {
        val dir = dir()
        withContext(Dispatchers.IO) { dir.delete(id) }
        settings.setKmlActive(id, false)
        refreshLocked()
    }

    suspend fun setActive(id: String, active: Boolean) = settings.setKmlActive(id, active)

    /** Add a Point placemark to collection [id], editing its file in place. */
    suspend fun addWaypoint(id: String, name: String, lat: Double, lon: Double) = mutex.withLock {
        val dir = dir()
        withContext(Dispatchers.IO) {
            val doc = dir.read(id) { Xml.builder().parse(it) }
            KmlWriter.addPlacemark(doc, name, lat, lon)
            dir.write(id) { KmlWriter.write(doc, it) }
        }
        refreshLocked()
    }

    /**
     * Delete a placemark (all its geometries) from its file, in place. Returns false when the file
     * changed and the placemark is no longer where it was (nothing is changed then).
     */
    suspend fun deletePlacemark(ref: PlacemarkRef): Boolean = mutex.withLock {
        val dir = dir()
        val ok = withContext(Dispatchers.IO) {
            val doc = dir.read(ref.collectionId) { Xml.builder().parse(it) }
            KmlWriter.removePlacemark(doc, ref.index, ref.name).also { removed ->
                if (removed) dir.write(ref.collectionId) { KmlWriter.write(doc, it) }
            }
        }
        refreshLocked()
        ok
    }

    private fun safeName(title: String): String =
        title.trim().replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1f]"), "_").take(80).ifBlank { "Collection" }

    private fun uniqueName(dir: DocDir, base: String) = dir.uniqueName(base, "kml") { " ($it)" }

    companion object {
        private const val MAX_IMPORT_BYTES = 50_000_000
    }
}
