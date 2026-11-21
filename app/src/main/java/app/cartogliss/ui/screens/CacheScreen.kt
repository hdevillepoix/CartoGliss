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
import app.cartogliss.appContainer
import app.cartogliss.tiles.BuiltInSources
import app.cartogliss.tiles.TileSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private data class CacheInfo(val tiles: Long, val bytes: Long)

@Composable
fun CacheScreen(onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var info by remember { mutableStateOf<Map<String, CacheInfo>>(emptyMap()) }
    var confirm by remember { mutableStateOf<TileSource?>(null) }

    LaunchedEffect(refresh) {
        info = withContext(Dispatchers.IO) {
            BuiltInSources.all.associate { it.id to CacheInfo(container.tileStore.tileCount(it.id), container.tileStore.sizeBytes(it.id)) }
        }
    }

    ScreenScaffold("Tile cache", onBack) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            BuiltInSources.all.forEach { src ->
                val i = info[src.id]
                ListItem(
                    supportingContent = { Text(if (i == null) "…" else "${i.tiles} tiles · ${formatBytes(i.bytes)}") },
                    trailingContent = {
                        TextButton(onClick = { confirm = src }, enabled = (i?.tiles ?: 0) > 0) { Text("Clear") }
                    },
                ) { Text(src.name) }
            }
        }
    }

    confirm?.let { src ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Clear ${src.name}?") },
            text = { Text("All cached tiles for this source are deleted, including downloaded areas.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    scope.launch {
                        withContext(Dispatchers.IO) { container.tileStore.clear(src.id) }
                        container.tileLoader.evict(src.id)
                        refresh++
                    }
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

private fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> String.format(Locale.ROOT, "%.2f GB", b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> String.format(Locale.ROOT, "%.1f MB", b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> String.format(Locale.ROOT, "%.0f kB", b / (1L shl 10).toDouble())
    else -> "$b B"
}
