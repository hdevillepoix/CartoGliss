package app.cartogliss.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.cartogliss.appContainer
import app.cartogliss.settings.AngleFormat
import app.cartogliss.settings.DistanceUnit
import app.cartogliss.settings.KeyProvider
import app.cartogliss.settings.Settings
import app.cartogliss.settings.SpeedUnit
import app.cartogliss.settings.TrackSettings
import app.cartogliss.ui.nav.Routes
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    ScreenScaffold("Settings", onBack) { padding ->
        Column(Modifier.padding(padding)) {
            NavItem("Map server keys", "Thunderforest, Mapbox, Tracestrack, OS, Azure Maps") {
                onNavigate(Routes.SETTINGS_KEYS)
            }
            NavItem("Units", "Distance, speed, angle format") { onNavigate(Routes.SETTINGS_UNITS) }
            NavItem("Track recording", "Interval, distance, accuracy filter") { onNavigate(Routes.SETTINGS_TRACK) }
            NavItem("Tile cache", "Size and clearing per source") { onNavigate(Routes.SETTINGS_CACHE) }
        }
    }
}

/** Renders [content] once settings have loaded, so text fields start from stored values. */
@Composable
private fun WithSettings(content: @Composable (Settings) -> Unit) {
    val repo = LocalContext.current.appContainer.settings
    val settings by repo.settings.collectAsState(initial = null)
    settings?.let { content(it) }
}

@Composable
fun KeysScreen(onBack: () -> Unit) {
    val repo = LocalContext.current.appContainer.settings
    val scope = rememberCoroutineScope()
    ScreenScaffold("Map server keys", onBack) { padding ->
        WithSettings { s ->
            Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
                KeyProvider.entries.forEach { provider ->
                    var text by remember { mutableStateOf(s.apiKeys[provider].orEmpty()) }
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            scope.launch { repo.setApiKey(provider, it) }
                        },
                        label = { Text(provider.label) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun UnitsScreen(onBack: () -> Unit) {
    val repo = LocalContext.current.appContainer.settings
    val scope = rememberCoroutineScope()
    ScreenScaffold("Units", onBack) { padding ->
        WithSettings { s ->
            Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
                SectionHeader("Distance")
                RadioGroup(DistanceUnit.entries, s.distanceUnit, { it.label }) {
                    scope.launch { repo.setDistanceUnit(it) }
                }
                SectionHeader("Speed")
                RadioGroup(SpeedUnit.entries, s.speedUnit, { it.label }) {
                    scope.launch { repo.setSpeedUnit(it) }
                }
                SectionHeader("Angle format")
                RadioGroup(AngleFormat.entries, s.angleFormat, { it.label }) {
                    scope.launch { repo.setAngleFormat(it) }
                }
            }
        }
    }
}

@Composable
private fun <T> RadioGroup(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    options.forEach { option ->
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(selected = option == selected, onClick = { onSelect(option) })
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = option == selected, onClick = null)
            Text(label(option), modifier = Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
fun TrackSettingsScreen(onBack: () -> Unit) {
    val repo = LocalContext.current.appContainer.settings
    val scope = rememberCoroutineScope()
    ScreenScaffold("Track recording", onBack) { padding ->
        WithSettings { s ->
            var track by remember { mutableStateOf(s.track) }
            val save: (TrackSettings) -> Unit = { track = it; scope.launch { repo.setTrackSettings(it) } }
            Column(Modifier.padding(padding).padding(16.dp)) {
                IntField("Minimum time between points (s)", track.minIntervalS) { save(track.copy(minIntervalS = it)) }
                IntField("Minimum distance between points (m)", track.minDistanceM) { save(track.copy(minDistanceM = it)) }
                IntField("Drop fixes worse than (m accuracy)", track.maxAccuracyM) { save(track.copy(maxAccuracyM = it)) }
            }
        }
    }
}

/** Integer field: keeps the raw text locally, saves only when it parses as a non-negative integer. */
@Composable
private fun IntField(label: String, value: Int, onValid: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    val valid = text.toIntOrNull()?.let { it >= 0 } == true
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t
            t.toIntOrNull()?.takeIf { it >= 0 }?.let(onValid)
        },
        label = { Text(label) },
        isError = !valid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    )
}
