package app.cartogliss.ui.nav

import android.net.Uri
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
import app.cartogliss.ui.sources.SourceDetailScreen
import app.cartogliss.ui.tracks.TracksScreen
import app.cartogliss.ui.waypoints.AddWaypointScreen
import app.cartogliss.ui.waypoints.CollectionsScreen
import app.cartogliss.ui.waypoints.WaypointBrowseScreen
import app.cartogliss.ui.sources.SourceEditScreen
import app.cartogliss.ui.sources.SourcesScreen

object Routes {
    const val MAP = "map"
    const val MENU = "menu"
    const val DOWNLOAD = "maps/download"
    const val SOURCES = "maps/sources"
    const val SOURCE_DETAIL = "maps/source/{id}"
    const val SOURCE_EDIT = "maps/source-edit?id={id}&dup={dup}"
    fun sourceDetail(id: String) = "maps/source/" + Uri.encode(id)
    fun sourceEdit(id: String?, dup: Boolean) = "maps/source-edit?dup=$dup" + (id?.let { "&id=" + Uri.encode(it) } ?: "")
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
    val toMap: () -> Unit = { nav.popBackStack(Routes.MAP, inclusive = false) }

    NavHost(navController = nav, startDestination = Routes.MAP) {
        composable(Routes.MAP) {
            MapScreen(
                onOpenMenu = { go(Routes.MENU) },
                onAddWaypoint = { lat, lon -> go(Routes.addWaypoint(lat, lon)) },
            )
        }
        composable(Routes.MENU) { MenuScreen(onNavigate = go, onBack = back) }

        composable(Routes.DOWNLOAD) { PlaceholderScreen("Download maps", "Milestone 7", back) }
        composable(Routes.SOURCES) {
            SourcesScreen(onOpen = { go(Routes.sourceDetail(it)) }, onAdd = { go(Routes.sourceEdit(null, false)) }, onBack = back)
        }
        composable(Routes.SOURCE_DETAIL, arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
            SourceDetailScreen(
                id = e.arguments?.getString("id").orEmpty(),
                onUse = toMap,
                onEdit = { id, dup -> go(Routes.sourceEdit(id, dup)) },
                onBack = back,
            )
        }
        composable(
            Routes.SOURCE_EDIT,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("dup") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { e ->
            SourceEditScreen(
                id = e.arguments?.getString("id"),
                duplicate = e.arguments?.getBoolean("dup") ?: false,
                onSaved = { id -> nav.popBackStack(); go(Routes.sourceDetail(id)) },
                onBack = back,
            )
        }
        composable(Routes.WAYPOINTS_BROWSE) { WaypointBrowseScreen(onToMap = toMap, onBack = back) }
        composable(Routes.WAYPOINTS_COLLECTIONS) { CollectionsScreen(onBack = back) }
        composable(
            Routes.WAYPOINTS_ADD_PATTERN,
            arguments = listOf(
                navArgument("lat") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("lon") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { e ->
            AddWaypointScreen(
                initialLat = e.arguments?.getString("lat")?.toDoubleOrNull(),
                initialLon = e.arguments?.getString("lon")?.toDoubleOrNull(),
                onBack = back,
            )
        }
        composable(Routes.TRACKS_BROWSE) { TracksScreen(onToMap = toMap, onBack = back) }

        composable(Routes.SETTINGS) { SettingsScreen(onNavigate = go, onBack = back) }
        composable(Routes.SETTINGS_KEYS) { KeysScreen(onBack = back) }
        composable(Routes.SETTINGS_UNITS) { UnitsScreen(onBack = back) }
        composable(Routes.SETTINGS_TRACK) { TrackSettingsScreen(onBack = back) }
        composable(Routes.SETTINGS_CACHE) { CacheScreen(onBack = back) }
    }
}
