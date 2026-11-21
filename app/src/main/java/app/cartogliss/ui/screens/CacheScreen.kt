package app.cartogliss.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cartogliss.appContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private data class CacheInfo(val tiles: Long, val bytes: Long)
private data class CacheRow(val id: String, val name: String)

@Composable
fun CacheScreen(onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val scope = rememberCoroutineScope()
    val sources by container.sources.sources.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    var info by remember { mutableStateOf<Map<String, CacheInfo>>(emptyMap()) }
    var retired by remember { mutableStateOf<List<String>>(emptyList()) }
    var confirm by remember { mutableStateOf<CacheRow?>(null) }

    LaunchedEffect(refresh, sources) {
        withContext(Dispatchers.IO) {
            val stored = container.tileStore.storedSourceIds()
            val known = sources.map { it.id }.toSet()
            retired = stored.filter { it !in known }.sorted()
            info = (sources.map { it.id } + retired).associateWith {
                CacheInfo(container.tileStore.tileCount(it), container.tileStore.sizeBytes(it))
            }
        }
    }

    ScreenScaffold("Tile cache", onBack) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            val total = info.values.sumOf { it.bytes }
            Text("Total: ${formatBytes(total)}", modifier = Modifier.padding(16.dp))
            sources.forEach { src -> Row(CacheRow(src.id, src.name), info[src.id]) { confirm = it } }
            if (retired.isNotEmpty()) {
                SectionHeader("Retired")
                retired.forEach { id -> Row(CacheRow(id, id), info[id]) { confirm = it } }
            }
        }
    }

    confirm?.let { row ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Clear ${row.name}?") },
            text = { Text("All cached tiles for this source are deleted, including downloaded areas.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    scope.launch {
                        withContext(Dispatchers.IO) { container.tileStore.clear(row.id) }
                        container.tileLoader.evict(row.id)
                        refresh++
                    }
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Row(row: CacheRow, i: CacheInfo?, onClear: (CacheRow) -> Unit) {
    ListItem(
        supportingContent = { Text(if (i == null) "…" else "${i.tiles} tiles · ${formatBytes(i.bytes)}") },
        trailingContent = {
            TextButton(onClick = { onClear(row) }, enabled = (i?.tiles ?: 0) > 0) { Text("Clear") }
        },
    ) { Text(row.name) }
}

fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> String.format(Locale.ROOT, "%.2f GB", b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> String.format(Locale.ROOT, "%.1f MB", b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> String.format(Locale.ROOT, "%.0f kB", b / (1L shl 10).toDouble())
    else -> "$b B"
}
