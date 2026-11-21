package app.cartogliss.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import app.cartogliss.kml.KmlParser
import app.cartogliss.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Where tracks and waypoints are kept (DESIGN.md "Storage"): a folder the user picked (sub-folders
 * tracks/ and waypoints/), or app storage until one is picked or when access to it was lost.
 */
class Storage(private val context: Context, private val settings: SettingsRepository, scope: CoroutineScope) {
    data class Dirs(
        val tracks: DocDir,
        val waypoints: DocDir,
        /** The picked folder's URI, or null for app storage. */
        val tree: Uri?,
        /** Set when a folder was picked but its access permission is gone (e.g. after a reinstall). */
        val lost: Boolean = false,
    )

    private val privateTracks = FileDocDir(File(context.filesDir, "tracks"))
    private val privateWaypoints = FileDocDir(File(context.filesDir, "kml"))

    val dirs: StateFlow<Dirs?> = settings.settings
        .map { it.storageTree }
        .distinctUntilChanged()
        .map { resolve(it) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun current(): Dirs = dirs.filterNotNull().first()

    private fun resolve(tree: String?): Dirs {
        val app = Dirs(privateTracks, privateWaypoints, null)
        if (tree == null) return app
        val uri = tree.toUri()
        val granted = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission }
        if (!granted) return app.copy(lost = true)
        val r = context.contentResolver
        return Dirs(SafDocDir(r, uri, TRACKS), SafDocDir(r, uri, WAYPOINTS), uri)
    }

    /**
     * Use the folder [tree] from now on. Files in app storage are moved into it; files already there
     * are kept. Returns the renamed files (old name → new name).
     */
    suspend fun choose(tree: Uri): Map<String, String> = withContext(Dispatchers.IO) {
        val r = context.contentResolver
        r.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val renamed = HashMap<String, String>()
        renamed += Migration.move(privateTracks, SafDocDir(r, tree, TRACKS))
        renamed += Migration.move(privateWaypoints, SafDocDir(r, tree, WAYPOINTS)) { _, bytes -> isEmptyKml(bytes) }
        val old = current().tree
        settings.setStorageTree(tree.toString())
        if (old != null && old != tree) release(old)
        renamed
    }

    /** Go back to app storage. The files stay in the folder; they are not copied back. */
    suspend fun useAppStorage() {
        current().tree?.let(::release)
        settings.setStorageTree(null)
    }

    private fun release(uri: Uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }

    /** A collection without placemarks, e.g. the "My waypoints" created on first launch. */
    private fun isEmptyKml(bytes: ByteArray) =
        runCatching { bytes.inputStream().use(KmlParser::parse).placemarks.isEmpty() }.getOrDefault(false)

    /** Content URI of a file, for sharing with another app. */
    fun shareUri(dir: DocDir, name: String): Uri? = when (dir) {
        is FileDocDir -> FileProvider.getUriForFile(context, "app.cartogliss.files", dir.file(name))
        is SafDocDir -> dir.uriOf(name)
        else -> null
    }

    /** Human-readable location of the picked folder, e.g. "primary:CartoGliss". */
    fun describe(d: Dirs): String = when {
        d.tree == null -> "App storage (deleted with the app)"
        else -> runCatching { android.provider.DocumentsContract.getTreeDocumentId(d.tree) }.getOrDefault(d.tree.toString())
            .replace("primary:", "Internal storage/")
    }

    companion object {
        const val TRACKS = "tracks"
        const val WAYPOINTS = "waypoints"
    }
}
