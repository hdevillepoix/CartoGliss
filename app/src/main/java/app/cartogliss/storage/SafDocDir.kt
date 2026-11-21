package app.cartogliss.storage

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.InputStream
import java.io.OutputStream

/**
 * Sub-folder [sub] (created on first use) of a folder the user picked with ACTION_OPEN_DOCUMENT_TREE.
 * Files live outside the app, so they are visible to other apps and survive uninstalling it.
 */
class SafDocDir(private val resolver: ContentResolver, private val tree: Uri, private val sub: String) : DocDir {
    override val id: String = "saf:$tree/$sub"

    private data class Child(val docId: String, val name: String, val isDir: Boolean, val modified: Long, val size: Long)

    private fun docUri(docId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)

    private fun children(parentId: String): List<Child> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val cols = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED, Document.COLUMN_SIZE,
        )
        return resolver.query(uri, cols, null, null, null)?.use { c ->
            val out = ArrayList<Child>(c.count)
            while (c.moveToNext()) {
                out += Child(
                    docId = c.getString(0),
                    name = c.getString(1) ?: continue,
                    isDir = c.getString(2) == Document.MIME_TYPE_DIR,
                    modified = if (c.isNull(3)) 0 else c.getLong(3),
                    size = if (c.isNull(4)) 0 else c.getLong(4),
                )
            }
            out
        } ?: error("Can't read the storage folder")
    }

    /** Document id of the sub-folder, created if missing. Not cached: the user may move or delete it. */
    private fun dirId(): String {
        val root = DocumentsContract.getTreeDocumentId(tree)
        children(root).firstOrNull { it.isDir && it.name == sub }?.let { return it.docId }
        val created = DocumentsContract.createDocument(resolver, docUri(root), Document.MIME_TYPE_DIR, sub)
            ?: error("Can't create the folder $sub")
        return DocumentsContract.getDocumentId(created)
    }

    private fun find(name: String): Child? = children(dirId()).firstOrNull { !it.isDir && it.name == name }

    // octet-stream keeps the name exactly as given (a typed MIME type may get an extension appended).
    private fun create(name: String): Uri =
        DocumentsContract.createDocument(resolver, docUri(dirId()), "application/octet-stream", name)
            ?: error("Can't create $name")

    override fun list(): List<DocEntry> =
        children(dirId()).filter { !it.isDir && !it.name.endsWith(TMP_SUFFIX) }.map { DocEntry(it.name, it.modified, it.size) }

    override fun exists(name: String) = find(name) != null

    override fun <T> read(name: String, block: (InputStream) -> T): T {
        val c = find(name) ?: error("$name not found")
        return (resolver.openInputStream(docUri(c.docId)) ?: error("Can't open $name")).use(block)
    }

    /** Content URI of [name], for sharing it with another app. */
    fun uriOf(name: String): Uri? = find(name)?.let { docUri(it.docId) }

    override fun write(name: String, block: (OutputStream) -> Unit) {
        val existing = find(name)
        if (existing == null) {
            val uri = create(name)
            writeTo(uri, block)
            return
        }
        // Replace: write a temp file, then swap it in, so a failed write keeps the old content.
        find(name + TMP_SUFFIX)?.let { DocumentsContract.deleteDocument(resolver, docUri(it.docId)) }
        val tmp = create(name + TMP_SUFFIX)
        writeTo(tmp, block)
        DocumentsContract.deleteDocument(resolver, docUri(existing.docId))
        val renamed = runCatching { DocumentsContract.renameDocument(resolver, tmp, name) }.getOrNull()
        if (renamed == null) {
            // Provider without rename: copy the temp file into place.
            val target = create(name)
            (resolver.openInputStream(tmp) ?: error("Can't open the temp file")).use { input ->
                writeTo(target) { input.copyTo(it) }
            }
            DocumentsContract.deleteDocument(resolver, tmp)
        }
    }

    private fun writeTo(uri: Uri, block: (OutputStream) -> Unit) {
        try {
            (resolver.openOutputStream(uri, "wt") ?: error("Can't write")).use(block)
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            throw e
        }
    }

    override fun delete(name: String): Boolean {
        val c = find(name) ?: return false
        return DocumentsContract.deleteDocument(resolver, docUri(c.docId))
    }
}
