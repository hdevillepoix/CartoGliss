package app.cartogliss.tiles

import app.cartogliss.BuildConfig
import app.cartogliss.settings.KeyProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

sealed interface FetchResult {
    class Ok(val bytes: ByteArray) : FetchResult
    data class Http(val code: Int) : FetchResult
    data object NoKey : FetchResult
    data class Failed(val message: String) : FetchResult
}

/** One HTTP tile request, shared by the map, the "Test" button and the area downloader. */
class TileFetcher(
    private val http: OkHttpClient,
    private val apiKey: (KeyProvider) -> String?,
) {
    fun keyFor(source: TileSource): String? = source.customKey ?: source.keyProvider?.let(apiKey)

    suspend fun fetch(source: TileSource, z: Int, x: Int, y: Int): FetchResult = withContext(Dispatchers.IO) {
        val key = keyFor(source)
        val url = TileUrl.build(source, z, x, y, key) ?: return@withContext FetchResult.NoKey
        val request = try {
            Request.Builder().url(url).header("User-Agent", USER_AGENT).apply {
                TileUrl.headers(source, key).forEach { (n, v) -> header(n, v) }
            }.build()
        } catch (e: IllegalArgumentException) {
            return@withContext FetchResult.Failed("Invalid URL: $url")
        }
        try {
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) FetchResult.Http(resp.code) else FetchResult.Ok(resp.body.bytes())
            }
        } catch (e: IOException) {
            FetchResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    companion object {
        /** Identifying User-Agent (required by the OSM tile policy). */
        val USER_AGENT = "CartoGliss/${BuildConfig.VERSION_NAME} (Android; personal use)"
    }
}
