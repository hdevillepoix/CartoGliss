package app.cartogliss.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.cartogliss.ui.screens.CacheScreen
import app.cartogliss.ui.screens.KeysScreen
import app.cartogliss.ui.screens.MapScreen
import app.cartogliss.ui.screens.MenuScreen
import app.cartogliss.ui.screens.PlaceholderScreen
import app.cartogliss.ui.screens.SettingsScreen
import app.cartogliss.ui.screens.TrackSettingsScreen
import app.cartogliss.ui.screens.UnitsScreen

object Routes {
    const val MAP = "map"
    const val MENU = "menu"
    const val DOWNLOAD = "maps/download"
    const val SOURCES = "maps/sources"
    const val WAYPOINTS_BROWSE = "waypoints/browse"
    const val WAYPOINTS_COLLECTIONS = "waypoints/collections"
    const val WAYPOINTS_ADD = "waypoints/add"
    const val WAYPOINTS_ADD_PATTERN = "waypoints/add?lat={lat}&lon={lon}"
    fun addWaypoint(lat: Double, lon: Double) = "waypoints/add?lat=$lat&lon=$lon"
    const val TRACKS_BROWSE = "tracks/browse"
    const val SETTINGS = "settings"
    const val SETTINGS_KEYS = "settings/keys"
    const val SETTINGS_UNITS = "settings/units"
    const val SETTINGS_TRACK = "settings/track"
    const val SETTINGS_CACHE = "settings/cache"
}

@Composable
fun CartoGlissNavHost() {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }
    val go: (String) -> Unit = { nav.navigate(it) }

    NavHost(navController = nav, startDestination = Routes.MAP) {
        composable(Routes.MAP) {
            MapScreen(
                onOpenMenu = { go(Routes.MENU) },
                onAddWaypoint = { lat, lon -> go(Routes.addWaypoint(lat, lon)) },
            )
        }
        composable(Routes.MENU) { MenuScreen(onNavigate = go, onBack = back) }

        composable(Routes.DOWNLOAD) { PlaceholderScreen("Download maps", "Milestone 7", back) }
        composable(Routes.SOURCES) { PlaceholderScreen("More maps", "Milestone 4", back) }
        composable(Routes.WAYPOINTS_BROWSE) { PlaceholderScreen("Waypoints", "Milestone 5", back) }
        composable(Routes.WAYPOINTS_COLLECTIONS) { PlaceholderScreen("Collections", "Milestone 5", back) }
        composable(
            Routes.WAYPOINTS_ADD_PATTERN,
            arguments = listOf(
                navArgument("lat") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("lon") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { PlaceholderScreen("Add waypoint", "Milestone 5", back) }
        composable(Routes.TRACKS_BROWSE) { PlaceholderScreen("Tracks", "Milestone 6", back) }

        composable(Routes.SETTINGS) { SettingsScreen(onNavigate = go, onBack = back) }
        composable(Routes.SETTINGS_KEYS) { KeysScreen(onBack = back) }
        composable(Routes.SETTINGS_UNITS) { UnitsScreen(onBack = back) }
        composable(Routes.SETTINGS_TRACK) { TrackSettingsScreen(onBack = back) }
        composable(Routes.SETTINGS_CACHE) { CacheScreen(onBack = back) }
    }
}
