package app.cartogliss.ui.tracks

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cartogliss.appContainer
import app.cartogliss.geo.Units
import app.cartogliss.tracks.TrackFile
import app.cartogliss.tracks.TrackStats
import app.cartogliss.ui.screens.ScreenScaffold
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val WHEN = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm")

/** Saved GPX tracks: show on map (checkbox), open on the map (tap), share, delete. */
@Composable
fun TracksScreen(onToMap: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    val tracks by container.trackFiles.tracks.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val recording by container.recorder.state.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<TrackFile?>(null) }
    LaunchedEffect(Unit) { container.trackFiles.refresh() }
    val s = settings ?: return

    ScreenScaffold("Tracks", onBack) { padding ->
        Column(Modifier.padding(padding)) {
            recording?.let {
                Text(
                    "Recording: ${Units.distance(it.distanceM, s.distanceUnit)}, ${it.points.size} points",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (tracks.isEmpty()) {
                Text("No saved tracks yet. Use the record button in the state bar.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.outline)
            }
            LazyColumn {
                items(tracks, key = { it.id }) { tf ->
                    val shown = tf.id in s.shownTracks
                    val g = tf.track
                    ListItem(
                        leadingContent = {
                            Checkbox(checked = shown, onCheckedChange = { on -> scope.launch { container.settings.setTrackShown(tf.id, on) } })
                        },
                        supportingContent = {
                            Text(
                                when {
                                    g == null -> "Can't read: ${tf.error}"
                                    else -> listOfNotNull(
                                        g.startMs?.let { WHEN.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) },
                                        Units.distance(g.distanceM, s.distanceUnit),
                                        if (g.startMs != null && g.endMs != null) TrackStats.duration(g.endMs!! - g.startMs!!) else null,
                                        "${g.points.size} pts",
                                    ).joinToString(" · ")
                                },
                                fontSize = 12.sp,
                            )
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { share(context, tf) }) { Icon(Icons.Filled.Share, contentDescription = "Share") }
                                IconButton(onClick = { deleting = tf }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                            }
                        },
                        modifier = Modifier.clickable(enabled = g != null && g.points.isNotEmpty()) {
                            // Open on the map: switch it on and centre on its start.
                            val first = g!!.points.first()
                            scope.launch {
                                container.settings.setTrackShown(tf.id, true)
                                container.settings.setCentre(first.lat, first.lon)
                                onToMap()
                            }
                        },
                    ) { Text(tf.name) }
                }
            }
        }
    }

    deleting?.let { tf ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${tf.name}?") },
            text = { Text("The GPX file is deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        container.trackFiles.delete(tf.id)
                        container.settings.setTrackShown(tf.id, false)
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

private fun share(context: android.content.Context, tf: TrackFile) {
    try {
        val uri = FileProvider.getUriForFile(context, "app.cartogliss.files", tf.file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/gpx+xml")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, tf.name))
    } catch (e: Exception) {
        Toast.makeText(context, "Can't share: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
