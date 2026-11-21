package app.cartogliss.tracks

import app.cartogliss.storage.DocDir
import app.cartogliss.storage.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class TrackFile(
    /** File name, used as the id. */
    val id: String,
    val modified: Long,
    val name: String,
    val track: GpxTrack?,
    val error: String?,
)

/** Saved GPX tracks in the tracks folder, parsed once per file version. */
class TrackFiles(private val storage: Storage, scope: CoroutineScope) {
    private val mutex = Mutex()
    private val cache = HashMap<String, Pair<Pair<Long, Long>, TrackFile>>()
    private var cacheDirId: String? = null
    private val _tracks = MutableStateFlow<List<TrackFile>>(emptyList())

    /** Newest first. */
    val tracks: StateFlow<List<TrackFile>> = _tracks

    init {
        scope.launch { storage.dirs.filterNotNull().collect { refresh() } }
    }

    suspend fun dir(): DocDir = storage.current().tracks

    suspend fun refresh() = mutex.withLock {
        val dir = dir()
        withContext(Dispatchers.IO) {
            if (cacheDirId != dir.id) cache.clear()
            cacheDirId = dir.id
            val files = runCatching { dir.list() }.getOrElse { emptyList() }.filter { it.name.endsWith(".gpx", ignoreCase = true) }
            val list = files.map { f ->
                val stamp = f.modified to f.size
                val hit = cache[f.name]
                if (hit != null && hit.first == stamp) hit.second else {
                    val base = f.name.substringBeforeLast('.')
                    val tf = try {
                        val g = dir.read(f.name, GpxParser::parse)
                        TrackFile(f.name, f.modified, g.name ?: base, g, null)
                    } catch (e: Exception) {
                        TrackFile(f.name, f.modified, base, null, e.message ?: e.javaClass.simpleName)
                    }
                    cache[f.name] = stamp to tf
                    tf
                }
            }
            cache.keys.retainAll(files.map { it.name }.toSet())
            _tracks.value = list.sortedByDescending { it.track?.startMs ?: it.modified }
        }
    }

    suspend fun delete(id: String) {
        val dir = dir()
        withContext(Dispatchers.IO) { dir.delete(id) }
        refresh()
    }
}
