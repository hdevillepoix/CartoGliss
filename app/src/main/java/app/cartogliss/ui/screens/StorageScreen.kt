package app.cartogliss.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cartogliss.appContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Launches the system folder picker; the picked folder becomes the storage folder (files moved in).
 * [onDone] gets whether a folder was taken into use.
 */
@Composable
fun rememberStorageFolderPicker(onDone: (Boolean) -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) {
            onDone(false)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            try {
                val renamed = container.storage.choose(uri)
                // Keep "shown"/"active" on files that had to be renamed.
                val s = container.settings.settings.first()
                for ((old, new) in renamed) {
                    if (old in s.activeKml) container.settings.setKmlActive(new, true)
                    if (old in s.shownTracks) container.settings.setTrackShown(new, true)
                }
                Toast.makeText(context, "Tracks and waypoints are now kept in the chosen folder", Toast.LENGTH_SHORT).show()
                onDone(true)
            } catch (e: Exception) {
                Toast.makeText(context, "Can't use this folder: ${e.message}", Toast.LENGTH_LONG).show()
                onDone(false)
            }
        }
    }
    return { launcher.launch(null) }
}

@Composable
fun StorageScreen(onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val scope = rememberCoroutineScope()
    val dirs by container.storage.dirs.collectAsStateWithLifecycle()
    val pick = rememberStorageFolderPicker()

    ScreenScaffold("Storage folder", onBack) { padding ->
        val d = dirs ?: return@ScreenScaffold
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Tracks and waypoints are kept in:", style = MaterialTheme.typography.bodyMedium)
            Text(container.storage.describe(d), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
            if (d.lost) {
                Text(
                    "Access to the chosen folder was lost (for example after reinstalling). Choose it again to get your files back.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Text(
                "A folder you choose holds two sub-folders, tracks/ (GPX) and waypoints/ (KML). Other apps can open them, " +
                    "and they stay when the app is uninstalled. Create a folder such as \"CartoGliss\" in the picker. " +
                    "Files in app storage are moved into it; files already there are kept.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Button(onClick = pick, modifier = Modifier.padding(top = 16.dp)) {
                Text(if (d.tree == null) "Choose a folder" else "Choose another folder")
            }
            if (d.tree != null || d.lost) {
                OutlinedButton(onClick = { scope.launch { container.storage.useAppStorage() } }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Use app storage instead")
                }
                Text(
                    "The files stay in the folder; they are not copied back.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/** First launch (or folder access lost): ask where tracks and waypoints should be kept. */
@Composable
fun StoragePromptDialog(lost: Boolean = false) {
    val container = LocalContext.current.appContainer
    val scope = rememberCoroutineScope()
    // Marked as answered only once the picker returns: the dialog (and the picker's launcher) must
    // stay in composition until then.
    val pick = rememberStorageFolderPicker { scope.launch { container.settings.setStoragePrompted() } }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(if (lost) "Storage folder not accessible" else "Where to keep tracks and waypoints?") },
        text = {
            if (lost) Text(
                "The app lost access to the folder holding your tracks and waypoints (for example after reinstalling). " +
                    "Choose it again to get them back, or use app storage.",
            ) else Text(
                "Choose a folder (for example a new \"CartoGliss\" folder) to keep GPX tracks and KML waypoints where other apps " +
                    "can see them and where they survive uninstalling the app. You can change this later in Settings → Storage folder.",
            )
        },
        confirmButton = {
            TextButton(onClick = pick) { Text("Choose folder") }
        },
        dismissButton = {
            TextButton(onClick = {
                scope.launch { if (lost) container.storage.useAppStorage() else container.settings.setStoragePrompted() }
            }) { Text("App storage") }
        },
    )
}
