package app.cartogliss

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import app.cartogliss.settings.SettingsRepository

private val Context.settingsStore by preferencesDataStore(name = "settings")

/** Holds app-wide singletons (manual dependency injection). */
class AppContainer(context: Context) {
    val settings = SettingsRepository(context.settingsStore)
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
