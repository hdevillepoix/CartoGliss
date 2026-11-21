package app.cartogliss

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import app.cartogliss.location.HeadingTracker
import app.cartogliss.location.LocationTracker
import app.cartogliss.settings.KeyProvider
import app.cartogliss.settings.SettingsRepository
import app.cartogliss.tiles.BuiltInSources
import app.cartogliss.tiles.SourceCatalog
import app.cartogliss.tiles.SourceRegistry
import app.cartogliss.tiles.TileFetcher
import app.cartogliss.tiles.TileLoader
import app.cartogliss.tiles.TileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private val Context.settingsStore by preferencesDataStore(name = "settings")

/** Holds app-wide singletons (manual dependency injection). */
class AppContainer(context: Context) {
    /** Outlives screens; used for work that must finish (saving state, tile loads). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings = SettingsRepository(context.settingsStore)

    val apiKeys: StateFlow<Map<KeyProvider, String>> = settings.settings
        .map { it.apiKeys }
        .distinctUntilChanged()
        .stateIn(appScope, SharingStarted.Eagerly, emptyMap())

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val tileStore = TileStore(context)

    val location = LocationTracker(context)
    val heading = HeadingTracker(context)

    val sources = SourceRegistry(
        builtIns = runCatching {
            SourceCatalog.parseBuiltIn(context.assets.open("sources.json").bufferedReader().use { it.readText() })
        }.getOrElse { listOf(BuiltInSources.OSM_STANDARD) },
        file = File(context.filesDir, "sources_user.json"),
    )

    val fetcher = TileFetcher(http) { apiKeys.value[it] }

    val tileLoader = TileLoader(tileStore, fetcher, appScope)

    init {
        // A newly entered key should take effect without waiting for the retry delay.
        appScope.launch { apiKeys.collect { tileLoader.clearFailures() } }
    }
}

class CartoGlissApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as CartoGlissApp).container
