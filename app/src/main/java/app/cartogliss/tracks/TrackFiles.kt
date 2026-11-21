package app.cartogliss.tracks

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class TrackFile(
    /** File name, used as the id. */
    val id: String,
    val file: File,
    val name: String,
    val track: GpxTrack?,
    val error: String?,
)

/** Saved GPX tracks in app storage, parsed once per file version. */
class TrackFiles(private val dir: File) {
    private val mutex = Mutex()
    private val cache = HashMap<String, Pair<Long, TrackFile>>()
    private val _tracks = MutableStateFlow<List<TrackFile>>(emptyList())

    /** Newest first. */
    val tracks: StateFlow<List<TrackFile>> = _tracks

    suspend fun refresh() = mutex.withLock {
        withContext(Dispatchers.IO) {
            val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".gpx", ignoreCase = true) }.orEmpty()
            val list = files.map { f ->
                val hit = cache[f.name]
                if (hit != null && hit.first == f.lastModified()) hit.second else {
                    val tf = try {
                        val g = f.inputStream().use(GpxParser::parse)
                        TrackFile(f.name, f, g.name ?: f.nameWithoutExtension, g, null)
                    } catch (e: Exception) {
                        TrackFile(f.name, f, f.nameWithoutExtension, null, e.message ?: e.javaClass.simpleName)
                    }
                    cache[f.name] = f.lastModified() to tf
                    tf
                }
            }
            cache.keys.retainAll(files.map { it.name }.toSet())
            _tracks.value = list.sortedByDescending { it.track?.startMs ?: it.file.lastModified() }
        }
    }

    suspend fun delete(id: String) {
        withContext(Dispatchers.IO) { File(dir, id).delete() }
        refresh()
    }
}
