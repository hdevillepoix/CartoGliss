package app.cartogliss.tiles

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.cartogliss.BuildConfig
import app.cartogliss.settings.KeyProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

data class TileKey(val sourceId: String, val z: Int, val x: Int, val y: Int)

/**
 * Loads tiles: memory (LRU) → disk ([TileStore]) → network.
 *
 * Only exact (source, z, x, y) tiles are ever returned; a tile is never stood in for by another
 * zoom level. Network fetches for tiles that scrolled off screen before their turn are skipped.
 */
class TileLoader(
    private val store: TileStore,
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
    private val apiKey: (KeyProvider) -> String?,
) {
    private class Entry(val bitmap: Bitmap) {
        val image: ImageBitmap = bitmap.asImageBitmap()
    }

    private val memory = object : LruCache<TileKey, Entry>(memoryBudgetBytes()) {
        override fun sizeOf(key: TileKey, value: Entry): Int = value.bitmap.allocationByteCount
    }
    private val inFlight: MutableSet<TileKey> = ConcurrentHashMap.newKeySet()
    private val failedAt = ConcurrentHashMap<TileKey, Long>()
    private val permits = ConcurrentHashMap<String, Semaphore>()

    @Volatile
    private var wanted: Set<TileKey> = emptySet()

    private val _version = MutableStateFlow(0L)

    /** Bumped whenever a tile becomes available; observe it to redraw. */
    val version: StateFlow<Long> = _version

    fun get(key: TileKey): ImageBitmap? = memory.get(key)?.image

    /** Declare the tiles currently on screen (centre first) and start loading missing ones. */
    fun request(source: TileSource, keys: List<TileKey>) {
        wanted = keys.toHashSet()
        val now = SystemClock.elapsedRealtime()
        for (k in keys) {
            if (memory.get(k) != null) continue
            val failed = failedAt[k]
            if (failed != null && now - failed < RETRY_AFTER_MS) continue
            if (!inFlight.add(k)) continue
            scope.launch {
                try {
                    load(source, k)
                } finally {
                    inFlight.remove(k)
                }
            }
        }
    }

    /** Forget everything held in memory for a source (after its disk cache is cleared). */
    fun evict(sourceId: String) {
        memory.snapshot().keys.filter { it.sourceId == sourceId }.forEach { memory.remove(it) }
        failedAt.keys.removeAll { it.sourceId == sourceId }
        _version.update { it + 1 }
    }

    private suspend fun load(source: TileSource, k: TileKey) {
        store.get(k.sourceId, k.z, k.x, k.y)?.let { bytes ->
            if (publish(k, bytes)) return
        }
        if (k !in wanted) return
        val semaphore = permits.computeIfAbsent(source.id) { Semaphore(NETWORK_CONCURRENCY) }
        semaphore.withPermit {
            if (k !in wanted) return
            val key = source.keyProvider?.let(apiKey)
            val url = TileUrl.build(source, k.z, k.x, k.y, key)
            if (url == null) {
                markFailed(k, permanent = false)
                return
            }
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).apply {
                TileUrl.headers(source, key).forEach { (n, v) -> header(n, v) }
            }.build()
            try {
                http.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "HTTP ${resp.code} for ${source.id} ${k.z}/${k.x}/${k.y}")
                        markFailed(k, permanent = resp.code == 404)
                        return
                    }
                    val bytes = resp.body.bytes()
                    if (publish(k, bytes)) {
                        store.put(k.sourceId, k.z, k.x, k.y, bytes)
                    } else {
                        markFailed(k, permanent = false)
                    }
                }
            } catch (e: IOException) {
                markFailed(k, permanent = false)
            }
        }
    }

    private fun publish(k: TileKey, bytes: ByteArray): Boolean {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
        memory.put(k, Entry(bmp))
        failedAt.remove(k)
        _version.update { it + 1 }
        return true
    }

    private fun markFailed(k: TileKey, permanent: Boolean) {
        failedAt[k] = if (permanent) Long.MAX_VALUE / 2 else SystemClock.elapsedRealtime()
    }

    companion object {
        private const val TAG = "TileLoader"
        const val RETRY_AFTER_MS = 15_000L

        /** Identifying User-Agent (required by the OSM tile policy). */
        val USER_AGENT = "CartoGliss/${BuildConfig.VERSION_NAME} (Android; personal use)"
        private const val NETWORK_CONCURRENCY = 2

        private fun memoryBudgetBytes(): Int =
            (Runtime.getRuntime().maxMemory() / 6).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
