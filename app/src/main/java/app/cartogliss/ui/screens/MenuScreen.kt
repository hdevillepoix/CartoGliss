package app.cartogliss.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cartogliss.appContainer
import app.cartogliss.tiles.TileSource
import app.cartogliss.ui.nav.Routes
import kotlinx.coroutines.launch

@Composable
fun MenuScreen(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val sources by container.sources.sources.collectAsStateWithLifecycle()
    val keys by container.apiKeys.collectAsStateWithLifecycle()
    val s = settings ?: return

    ScreenScaffold("Menu", onBack) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader("Maps")
            NavItem("Download maps") { onNavigate(Routes.DOWNLOAD) }
            sources.filter { it.id in s.starredSources && !container.sources.isHidden(it.id) }.forEach { src ->
                SourceMenuItem(src, selected = src.id == s.viewport.sourceId, missingKey = src.missingKey(keys)?.label) {
                    scope.launch {
                        container.settings.setSource(src.id)
                        onBack()
                    }
                }
            }
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

/** A starred source in the menu. Greyed out (not selectable) while its API key is missing. */
@Composable
fun SourceMenuItem(src: TileSource, selected: Boolean, missingKey: String?, onSelect: () -> Unit) {
    val enabled = missingKey == null
    ListItem(
        supportingContent = missingKey?.let { { Text("Needs a $it key (Settings → Map server keys)") } },
        trailingContent = if (selected) ({ Icon(Icons.Filled.Check, contentDescription = "Current map") }) else null,
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onSelect),
    ) {
        Text(
            src.name,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        )
    }
}
