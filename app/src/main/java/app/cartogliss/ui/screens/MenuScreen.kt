package app.cartogliss.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cartogliss.ui.nav.Routes

@Composable
fun MenuScreen(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    ScreenScaffold("Menu", onBack) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader("Maps")
            NavItem("Download maps") { onNavigate(Routes.DOWNLOAD) }
            // Starred map sources will be listed here (milestone 4).
            NavItem("More maps") { onNavigate(Routes.SOURCES) }

            SectionHeader("Waypoints")
            NavItem("Browse") { onNavigate(Routes.WAYPOINTS_BROWSE) }
            NavItem("Collections") { onNavigate(Routes.WAYPOINTS_COLLECTIONS) }
            NavItem("Add manually") { onNavigate(Routes.WAYPOINTS_ADD) }

            SectionHeader("Tracks")
            NavItem("Browse") { onNavigate(Routes.TRACKS_BROWSE) }

            SectionHeader("Settings")
            NavItem("Settings") { onNavigate(Routes.SETTINGS) }
        }
    }
}
