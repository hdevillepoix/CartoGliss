package app.cartogliss.kml

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import app.cartogliss.settings.Settings
import app.cartogliss.settings.SettingsRepository
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
import java.io.File

data class KmlCollection(
    /** File name in app storage, e.g. "My waypoints.kml". Used as the id. */
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

/** One waypoint of an active collection, for Browse and map taps. */
data class Waypoint(val name: String, val lat: Double, val lon: Double, val description: String?, val collectionId: String, val collectionTitle: String)

/**
 * KML collections. The files in app storage are the source of truth (DESIGN.md); this keeps a parsed
 * copy per file, re-parsed when the file changes.
 */
class KmlRepository(
    private val dir: File,
    private val settings: SettingsRepository,
    scope: CoroutineScope,
) {
    private class Parsed(val modified: Long, val doc: KmlDocument?, val error: String?)

    private val mutex = Mutex()
    private val parsed = MutableStateFlow<Map<String, Parsed>>(emptyMap())

    val collections: StateFlow<List<KmlCollection>> =
        combine(parsed, settings.settings.map { it.activeKml }) { files, active ->
            files.entries.sortedBy { it.key.lowercase() }.map { (id, p) ->
                KmlCollection(id, p.doc?.name ?: id.removeSuffix(".kml"), id in active, p.doc, p.error)
            }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val activeWaypoints: StateFlow<List<Waypoint>> = collections.map { list ->
        list.filter { it.active }.flatMap { c ->
            c.doc?.points.orEmpty().map { (pm, at) -> Waypoint(pm.name ?: "(unnamed)", at.lat, at.lon, pm.description, c.id, c.title) }
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        scope.launch {
            withContext(Dispatchers.IO) {
                dir.mkdirs()
                if (dir.listFiles { f -> f.name.endsWith(".kml") }.isNullOrEmpty()) {
                    KmlWriter.writeAtomically(KmlWriter.newDocument("My waypoints"), File(dir, Settings.DEFAULT_COLLECTION), indent = true)
                }
            }
            refresh()
        }
    }

    /** Rescan the directory and re-parse changed files. */
    suspend fun refresh() = mutex.withLock { refreshLocked() }

    private suspend fun refreshLocked() = withContext(Dispatchers.IO) {
        val old = parsed.value
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".kml", ignoreCase = true) }.orEmpty()
        parsed.value = files.associate { f ->
            val prev = old[f.name]
            f.name to if (prev != null && prev.modified == f.lastModified()) prev else {
                try {
                    Parsed(f.lastModified(), f.inputStream().use(KmlParser::parse), null)
                } catch (e: Exception) {
                    Parsed(f.lastModified(), null, e.message ?: e.javaClass.simpleName)
                }
            }
        }
    }

    /** Create an empty collection; returns its id. */
    suspend fun create(title: String): String = mutex.withLock {
        val file = uniqueFile(safeName(title))
        withContext(Dispatchers.IO) { KmlWriter.writeAtomically(KmlWriter.newDocument(title), file, indent = true) }
        settings.setKmlActive(file.name, true)
        refreshLocked()
        file.name
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
        val file = uniqueFile(safeName(base))
        withContext(Dispatchers.IO) {
            val tmp = File(file.path + ".tmp")
            tmp.writeBytes(kml)
            check(tmp.renameTo(file)) { "Could not save $file" }
        }
        settings.setKmlActive(file.name, true)
        refreshLocked()
        file.name
    }

    suspend fun delete(id: String) = mutex.withLock {
        withContext(Dispatchers.IO) { File(dir, id).delete() }
        settings.setKmlActive(id, false)
        refreshLocked()
    }

    suspend fun setActive(id: String, active: Boolean) = settings.setKmlActive(id, active)

    /** Add a Point placemark to collection [id], editing its file in place. */
    suspend fun addWaypoint(id: String, name: String, lat: Double, lon: Double) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val file = File(dir, id)
            val doc = file.inputStream().use { Xml.builder().parse(it) }
            KmlWriter.addPlacemark(doc, name, lat, lon)
            KmlWriter.writeAtomically(doc, file)
        }
        refreshLocked()
    }

    private fun safeName(title: String): String =
        title.trim().replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1f]"), "_").take(80).ifBlank { "Collection" }

    private fun uniqueFile(base: String): File {
        var f = File(dir, "$base.kml")
        var i = 2
        while (f.exists()) f = File(dir, "$base ($i).kml").also { i++ }
        return f
    }

    companion object {
        private const val MAX_IMPORT_BYTES = 50_000_000
    }
}
