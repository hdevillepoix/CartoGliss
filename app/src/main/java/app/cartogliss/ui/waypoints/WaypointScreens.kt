package app.cartogliss.ui.waypoints

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cartogliss.appContainer
import app.cartogliss.geo.CoordInput
import app.cartogliss.geo.Coords
import app.cartogliss.geo.Geo
import app.cartogliss.geo.Units
import app.cartogliss.kml.Waypoint
import app.cartogliss.settings.Settings
import app.cartogliss.settings.Target
import app.cartogliss.ui.screens.ScreenScaffold
import kotlinx.coroutines.launch

private val STAR = Color(0xFFF9A825)

/** Waypoints of the active collections; tap for Go To / Show on map. */
@Composable
fun WaypointBrowseScreen(onToMap: () -> Unit, onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val scope = rememberCoroutineScope()
    val waypoints by container.kml.activeWaypoints.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val fix by container.location.fix.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Waypoint?>(null) }
    val s = settings ?: return

    val shown = remember(waypoints, query, fix) {
        val f = fix
        val filtered = waypoints.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        if (f == null) filtered.sortedBy { it.name.lowercase() } else filtered.sortedBy { Geo.distance(f.lat, f.lon, it.lat, it.lon) }
    }

    ScreenScaffold("Waypoints", onBack) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (waypoints.isEmpty()) {
                Text(
                    "No waypoints in the active collections. Activate or import a KML in Waypoints → Collections.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            LazyColumn {
                items(shown, key = { "${it.collectionId}/${it.name}/${it.lat}/${it.lon}" }) { wp ->
                    val f = fix
                    ListItem(
                        supportingContent = { Text(wp.collectionTitle, fontSize = 12.sp) },
                        trailingContent = f?.let { { Text(Units.distance(Geo.distance(it.lat, it.lon, wp.lat, wp.lon), s.distanceUnit)) } },
                        modifier = Modifier.clickable { selected = wp },
                    ) { Text(wp.name) }
                }
            }
        }
    }

    selected?.let { wp ->
        WaypointDialog(
            wp = wp,
            fix = fix,
            angleFormat = s.angleFormat,
            distanceUnit = s.distanceUnit,
            onGoTo = {
                selected = null
                scope.launch {
                    container.settings.setTarget(Target(wp.lat, wp.lon, wp.name))
                    onToMap()
                }
            },
            onShowOnMap = {
                selected = null
                scope.launch {
                    container.settings.setCentre(wp.lat, wp.lon)
                    onToMap()
                }
            },
            onDismiss = { selected = null },
        )
    }
}

/** KML collections: activate (star), import, create, delete. */
@Composable
fun CollectionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    val collections by container.kml.collections.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val id = container.kml.import(context.contentResolver, uri)
                Toast.makeText(context, "Imported $id", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    ScreenScaffold("Collections", onBack) { padding ->
        Column(Modifier.padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Many file managers report KML/KMZ as octet-stream, so accept anything and validate on import.
                Button(onClick = { importer.launch(arrayOf("*/*")) }) { Text("Import KML / KMZ") }
                TextButton(onClick = { creating = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("New")
                }
            }
            LazyColumn {
                items(collections, key = { it.id }) { c ->
                    ListItem(
                        leadingContent = {
                            IconButton(onClick = { scope.launch { container.kml.setActive(c.id, !c.active) } }) {
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = if (c.active) "Deactivate" else "Activate",
                                    tint = if (c.active) STAR else MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        },
                        supportingContent = {
                            Text(
                                c.error?.let { "Can't read: $it" }
                                    ?: listOfNotNull(
                                        "${c.waypointCount} waypoints",
                                        c.lineCount.takeIf { it > 0 }?.let { "$it lines" },
                                        c.areaCount.takeIf { it > 0 }?.let { "$it areas" },
                                    ).joinToString(" · ") + "  ·  ${c.id}",
                                fontSize = 12.sp,
                                color = if (c.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { deleting = c.id }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                        },
                    ) { Text(c.title) }
                }
            }
        }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("New collection") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    creating = false
                    scope.launch { container.kml.create(name.trim()) }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } },
        )
    }
    deleting?.let { id ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete $id?") },
            text = { Text("The KML file and all its waypoints are deleted from the app.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch { container.kml.delete(id) }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

/** Manual waypoint entry. Accepts DD, DDM or DMS; the space bar inserts the next separator. */
@Composable
fun AddWaypointScreen(initialLat: Double?, initialLon: Double?, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val collections by container.kml.collections.collectAsStateWithLifecycle()
    val s = settings ?: return

    var name by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf(TextFieldValue(initialLat?.let { Coords.formatLat(it, s.angleFormat) } ?: "")) }
    var lon by remember { mutableStateOf(TextFieldValue(initialLon?.let { Coords.formatLon(it, s.angleFormat) } ?: "")) }
    var collectionId by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val chosen = collections.firstOrNull { it.id == collectionId }
        ?: collections.firstOrNull { it.id == Settings.DEFAULT_COLLECTION }
        ?: collections.firstOrNull()
    val latValue = CoordInput.parse(lat.text, isLat = true)
    val lonValue = CoordInput.parse(lon.text, isLat = false)

    ScreenScaffold("Add waypoint", onBack) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            CoordField("Latitude", lat, latValue != null || lat.text.isBlank()) { lat = it }
            CoordField("Longitude", lon, lonValue != null || lon.text.isBlank()) { lon = it }
            Text(
                "Decimal degrees, degrees + decimal minutes or degrees + minutes + seconds. N/S/E/W or a minus sign. Space inserts ° ' \" in turn.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            if (latValue != null && lonValue != null) {
                Text("= ${Coords.format(latValue, lonValue, s.angleFormat)}", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            }
            Box {
                OutlinedTextField(
                    value = chosen?.title ?: "No collection",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Save in") },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                // Transparent layer over the read-only field to open the menu.
                Box(Modifier.matchParentSize().clickable { menuOpen = true })
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    collections.filter { it.doc != null }.forEach { c ->
                        DropdownMenuItem(text = { Text(c.title) }, onClick = { collectionId = c.id; menuOpen = false })
                    }
                }
            }
            Button(
                enabled = name.isNotBlank() && latValue != null && lonValue != null && chosen != null,
                onClick = {
                    val target = chosen ?: return@Button
                    scope.launch {
                        try {
                            container.kml.addWaypoint(target.id, name.trim(), latValue!!, lonValue!!)
                            if (!target.active) container.kml.setActive(target.id, true)
                            Toast.makeText(context, "Saved in ${target.title}", Toast.LENGTH_SHORT).show()
                            onBack()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not save: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }
}

@Composable
private fun CoordField(label: String, value: TextFieldValue, valid: Boolean, onChange: (TextFieldValue) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { nv ->
            val (text, cursor) = CoordInput.applySpace(value.text, nv.text, nv.selection.end)
            onChange(if (text == nv.text) nv else TextFieldValue(text, TextRange(cursor)))
        },
        label = { Text(label) },
        isError = !valid,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}
