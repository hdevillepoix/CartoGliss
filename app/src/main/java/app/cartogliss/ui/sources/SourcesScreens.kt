package app.cartogliss.ui.sources

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cartogliss.appContainer
import app.cartogliss.map.TileMath
import app.cartogliss.settings.KeyProvider
import app.cartogliss.tiles.FetchResult
import app.cartogliss.tiles.LatLonBox
import app.cartogliss.tiles.TileSource
import app.cartogliss.tiles.TileUrl
import app.cartogliss.ui.screens.ScreenScaffold
import app.cartogliss.ui.screens.SectionHeader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val STAR = Color(0xFFF9A825)

/** "More maps": every source by category, with star toggles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(onOpen: (String) -> Unit, onAdd: () -> Unit, onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val scope = rememberCoroutineScope()
    val sources by container.sources.sources.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val keys by container.apiKeys.collectAsStateWithLifecycle()
    val s = settings ?: return
    val registry = container.sources

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("More maps") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "Add custom source") }
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            val visible = sources.filter { !registry.isHidden(it.id) }
            for (cat in registry.categories()) {
                val inCat = visible.filter { it.category == cat }
                if (inCat.isEmpty()) continue
                SectionHeader(cat)
                inCat.forEach { src ->
                    SourceRow(
                        src = src,
                        starred = src.id in s.starredSources,
                        current = src.id == s.viewport.sourceId,
                        missingKey = src.missingKey(keys),
                        onStar = { scope.launch { container.settings.setStarred(src.id, src.id !in s.starredSources) } },
                        onOpen = { onOpen(src.id) },
                    )
                }
            }
            val hidden = sources.filter { registry.isHidden(it.id) }
            if (hidden.isNotEmpty()) {
                SectionHeader("Hidden")
                hidden.forEach { src ->
                    ListItem(modifier = Modifier.clickable { onOpen(src.id) }) { Text(src.name, color = MaterialTheme.colorScheme.outline) }
                }
            }
            Spacer(Modifier.height(88.dp))
        }
    }
}

@Composable
private fun SourceRow(
    src: TileSource,
    starred: Boolean,
    current: Boolean,
    missingKey: KeyProvider?,
    onStar: () -> Unit,
    onOpen: () -> Unit,
) {
    ListItem(
        leadingContent = {
            IconButton(onClick = onStar) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = if (starred) "Unstar" else "Star",
                    tint = if (starred) STAR else MaterialTheme.colorScheme.outlineVariant,
                )
            }
        },
        supportingContent = {
            Column {
                if (src.attribution.isNotBlank()) Text(src.attribution, fontSize = 12.sp)
                missingKey?.let { Text("Needs a ${it.label} key", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            }
        },
        trailingContent = if (current) ({ Icon(Icons.Filled.Check, contentDescription = "Current map") }) else null,
        modifier = Modifier.clickable(onClick = onOpen),
    ) {
        Text(src.name, color = if (missingKey == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
    }
}

/** Details of one source, with its actions. */
@Composable
fun SourceDetailScreen(
    id: String,
    onUse: () -> Unit,
    onEdit: (id: String, duplicate: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val container = LocalContext.current.appContainer
    val scope = rememberCoroutineScope()
    val sources by container.sources.sources.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val keys by container.apiKeys.collectAsStateWithLifecycle()
    val src = sources.firstOrNull { it.id == id }
    val s = settings
    var confirmDelete by remember { mutableStateOf(false) }

    ScreenScaffold(src?.name ?: "Source", onBack) { padding ->
        if (src == null || s == null) return@ScreenScaffold
        val registry = container.sources
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Fact("Category", src.category)
            if (src.attribution.isNotBlank()) Fact("Attribution", src.attribution)
            Fact("Zoom", "${src.minZoom}–${src.maxZoom}")
            Fact("Tile size", "${src.tileSize} px")
            Fact("Bulk download", if (src.bulkDownloadAllowed) "allowed" else "not allowed")
            src.coverage?.let { c -> Fact("Coverage", "lat ${c.south} to ${c.north}, lon ${c.west} to ${c.east}") }
            Fact(
                "Key",
                when {
                    src.customKey != null -> "stored with this source"
                    src.keyProvider == null -> "none needed"
                    src.missingKey(keys) != null -> "${src.keyProvider.label} — missing"
                    else -> "${src.keyProvider.label} — set"
                },
            )
            if (src.notes.isNotBlank()) Fact("Notes", src.notes)
            Text("URL template", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            Text(src.urlTemplate, fontFamily = FontFamily.Monospace, fontSize = 12.sp)

            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                scope.launch { container.settings.setSource(src.id); onUse() }
            }, enabled = src.missingKey(keys) == null, modifier = Modifier.fillMaxWidth()) { Text("Use this map") }
            val starred = src.id in s.starredSources
            OutlinedButton(onClick = { scope.launch { container.settings.setStarred(src.id, !starred) } }, modifier = Modifier.fillMaxWidth()) {
                Text(if (starred) "Unstar (remove from menu)" else "Star (show in menu)")
            }
            OutlinedButton(onClick = { onEdit(src.id, true) }, modifier = Modifier.fillMaxWidth()) { Text("Duplicate as custom source") }

            if (src.builtIn) {
                MaxZoomOverride(src, registry.builtInDefault(src.id)?.maxZoom ?: src.maxZoom, registry.maxZoomOverride(src.id)) {
                    registry.setMaxZoomOverride(src.id, it)
                    container.tileLoader.clearFailures()
                }
                val hidden = registry.isHidden(src.id)
                OutlinedButton(onClick = { registry.setHidden(src.id, !hidden) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (hidden) "Unhide" else "Hide from the list")
                }
            } else {
                OutlinedButton(onClick = { onEdit(src.id, false) }, modifier = Modifier.fillMaxWidth()) { Text("Edit") }
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDelete && src != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${src.name}?") },
            text = { Text("Its cached tiles are kept and listed under Settings → Tile cache → Retired until cleared.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    container.sources.deleteCustom(src.id)
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MaxZoomOverride(src: TileSource, default: Int, override: Int?, onSet: (Int?) -> Unit) {
    var text by remember(src.id, override) { mutableStateOf((override ?: default).toString()) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Max zoom (default $default)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = {
            text.toIntOrNull()?.coerceIn(src.minZoom, 22)?.let { onSet(if (it == default) null else it) }
        }) { Text("Set") }
        if (override != null) TextButton(onClick = { onSet(null) }) { Text("Reset") }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, modifier = Modifier.weight(0.35f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(0.65f))
    }
}

private sealed interface KeyChoice {
    data object None : KeyChoice
    data class Provider(val p: KeyProvider) : KeyChoice
    data object Custom : KeyChoice
}

/** Add / edit / duplicate a custom source. [id] null = new; [duplicate] = start from [id] as a new source. */
@Composable
fun SourceEditScreen(id: String?, duplicate: Boolean, onSaved: (String) -> Unit, onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val scope = rememberCoroutineScope()
    val registry = container.sources
    val base = id?.let { registry.byId(it) }
    val editing = base != null && !duplicate && !base.builtIn
    val categories = registry.categories()

    var name by remember { mutableStateOf(base?.let { if (editing) it.name else "${it.name} (copy)" } ?: "") }
    var category by remember { mutableStateOf(base?.category ?: "Custom") }
    var template by remember { mutableStateOf(base?.urlTemplate ?: "https://") }
    var subdomains by remember { mutableStateOf(base?.subdomains?.joinToString(",") ?: "") }
    var headers by remember { mutableStateOf(base?.headers?.entries?.joinToString("\n") { "${it.key}: ${it.value}" } ?: "") }
    var tileSize by remember { mutableIntStateOf(base?.tileSize ?: 256) }
    var minZoom by remember { mutableStateOf((base?.minZoom ?: 0).toString()) }
    var maxZoom by remember { mutableStateOf((base?.maxZoom ?: 19).toString()) }
    var keyChoice by remember {
        mutableStateOf<KeyChoice>(
            when {
                base?.customKey != null -> KeyChoice.Custom
                base?.keyProvider != null -> KeyChoice.Provider(base.keyProvider)
                else -> KeyChoice.None
            },
        )
    }
    var customKey by remember { mutableStateOf(base?.customKey ?: "") }
    var attribution by remember { mutableStateOf(base?.attribution ?: "") }
    var bulk by remember { mutableStateOf(if (editing) base!!.bulkDownloadAllowed else false) }
    var south by remember { mutableStateOf(base?.coverage?.south?.toString() ?: "") }
    var west by remember { mutableStateOf(base?.coverage?.west?.toString() ?: "") }
    var north by remember { mutableStateOf(base?.coverage?.north?.toString() ?: "") }
    var east by remember { mutableStateOf(base?.coverage?.east?.toString() ?: "") }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testImage by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    fun build(): Pair<TileSource?, List<String>> {
        val errors = mutableListOf<String>()
        if (name.isBlank()) errors += "Name is required"
        errors += TileUrl.validate(template.trim()).map { "URL $it" }
        val zMin = minZoom.toIntOrNull()
        val zMax = maxZoom.toIntOrNull()
        if (zMin == null || zMax == null || zMin !in 0..22 || zMax !in 0..22 || zMin > zMax) errors += "Zoom range must be 0–22, min ≤ max"
        val box = listOf(south, west, north, east)
        val coverage = if (box.all { it.isBlank() }) null else {
            val v = box.map { it.trim().toDoubleOrNull() }
            if (v.any { it == null }) { errors += "Coverage needs all four numbers, or none"; null }
            else LatLonBox(v[0]!!, v[1]!!, v[2]!!, v[3]!!)
        }
        val hdrs = headers.lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val i = line.indexOf(':')
            if (i <= 0) { errors += "Header lines must look like Name: value"; null } else line.substring(0, i).trim() to line.substring(i + 1).trim()
        }.toMap()
        if (keyChoice == KeyChoice.Custom && customKey.isBlank()) errors += "Enter the custom key"
        if (errors.isNotEmpty()) return null to errors
        return TileSource(
            id = if (editing) base!!.id else registry.newCustomId(),
            name = name.trim(),
            category = category.trim().ifBlank { "Custom" },
            urlTemplate = template.trim(),
            subdomains = subdomains.split(',').map { it.trim() }.filter { it.isNotEmpty() },
            tileSize = tileSize,
            minZoom = zMin!!,
            maxZoom = zMax!!,
            keyProvider = (keyChoice as? KeyChoice.Provider)?.p,
            customKey = customKey.trim().takeIf { keyChoice == KeyChoice.Custom },
            attribution = attribution.trim(),
            bulkDownloadAllowed = bulk,
            coverage = coverage,
            headers = hdrs,
            builtIn = false,
        ) to emptyList()
    }

    var errors by remember { mutableStateOf<List<String>>(emptyList()) }

    ScreenScaffold(if (editing) "Edit source" else "Add custom source", onBack) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Name", name) { name = it }
            Field("Category", category) { category = it }
            Row { categories.take(6).forEach { c -> FilterChip(selected = c == category, onClick = { category = c }, label = { Text(c) }, modifier = Modifier.padding(end = 4.dp)) } }
            Field("URL template", template, mono = true, singleLine = false) { template = it }
            Text("Placeholders: {z} {x} {y} {-y} {q} {s} {key}", style = MaterialTheme.typography.bodySmall)
            Field("Subdomains (comma-separated, for {s})", subdomains) { subdomains = it }
            Field("Extra headers (one per line, Name: value)", headers, singleLine = false) { headers = it }
            Text("Tile size", style = MaterialTheme.typography.labelLarge)
            Row { listOf(256, 512).forEach { sz -> FilterChip(selected = tileSize == sz, onClick = { tileSize = sz }, label = { Text("$sz px") }, modifier = Modifier.padding(end = 8.dp)) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Min zoom", minZoom, number = true, modifier = Modifier.weight(1f)) { minZoom = it }
                Field("Max zoom", maxZoom, number = true, modifier = Modifier.weight(1f)) { maxZoom = it }
            }
            Text("API key", style = MaterialTheme.typography.labelLarge)
            val choices = listOf(KeyChoice.None) + KeyProvider.entries.map { KeyChoice.Provider(it) } + KeyChoice.Custom
            choices.forEach { c ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = c == keyChoice, onClick = { keyChoice = c }).padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = c == keyChoice, onClick = null)
                    Text(
                        when (c) {
                            KeyChoice.None -> "None"
                            KeyChoice.Custom -> "Custom key, stored with this source"
                            is KeyChoice.Provider -> c.p.label
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            if (keyChoice == KeyChoice.Custom) Field("Custom key", customKey) { customKey = it }
            Field("Attribution", attribution) { attribution = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Allow bulk download", modifier = Modifier.weight(1f))
                Switch(checked = bulk, onCheckedChange = { bulk = it })
            }
            Text("Coverage box (optional)", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("South", south, decimal = true, modifier = Modifier.weight(1f)) { south = it }
                Field("North", north, decimal = true, modifier = Modifier.weight(1f)) { north = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("West", west, decimal = true, modifier = Modifier.weight(1f)) { west = it }
                Field("East", east, decimal = true, modifier = Modifier.weight(1f)) { east = it }
            }

            errors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }

            OutlinedButton(onClick = {
                val (src, errs) = build()
                errors = errs
                if (src == null) return@OutlinedButton
                testResult = "Fetching…"
                testImage = null
                scope.launch {
                    val v = container.settings.settings.first().viewport
                    val z = v.zoom.coerceIn(src.minZoom, src.maxZoom)
                    val x = TileMath.tileX(v.lon, z)
                    val y = TileMath.tileY(v.lat, z)
                    val r = container.fetcher.fetch(src, z, x, y)
                    testResult = when (r) {
                        is FetchResult.Ok -> {
                            val bmp = BitmapFactory.decodeByteArray(r.bytes, 0, r.bytes.size)
                            testImage = bmp?.asImageBitmap()
                            if (bmp == null) "Got ${r.bytes.size} bytes, but not an image" else "OK: tile $z/$x/$y, ${bmp.width}×${bmp.height} px, ${r.bytes.size / 1024} kB"
                        }
                        is FetchResult.Http -> "HTTP ${r.code} for tile $z/$x/$y"
                        FetchResult.NoKey -> "No key set for ${src.keyProvider?.label}"
                        is FetchResult.Failed -> "Failed: ${r.message}"
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Test (tile under the map centre)") }
            testResult?.let { Text(it) }
            testImage?.let { Image(it, contentDescription = "Test tile", modifier = Modifier.size(192.dp)) }

            Button(onClick = {
                val (src, errs) = build()
                errors = errs
                if (src != null) {
                    registry.saveCustom(src)
                    container.tileLoader.evict(src.id)
                    onSaved(src.id)
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    singleLine: Boolean = true,
    number: Boolean = false,
    decimal: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        textStyle = if (mono) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                number -> KeyboardType.Number
                decimal -> KeyboardType.Decimal
                else -> KeyboardType.Text
            },
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
