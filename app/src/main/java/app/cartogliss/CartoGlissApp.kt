package app.cartogliss

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import app.cartogliss.settings.KeyProvider
import app.cartogliss.settings.SettingsRepository
import app.cartogliss.tiles.TileLoader
import app.cartogliss.tiles.TileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
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

    private val apiKeys = settings.settings
        .map { it.apiKeys }
        .stateIn(appScope, SharingStarted.Eagerly, emptyMap<KeyProvider, String>())

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val tileStore = TileStore(context)

    private val versionName: String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "dev"

    val tileLoader = TileLoader(tileStore, http, appScope, "CartoGliss/$versionName (Android; personal use)") {
        apiKeys.value[it]
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
