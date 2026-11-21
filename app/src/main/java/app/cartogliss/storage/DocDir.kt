package app.cartogliss.storage

import java.io.File
import java.io.InputStream
import java.io.OutputStream

data class DocEntry(val name: String, val modified: Long, val size: Long)

/**
 * A flat folder of user files (tracks or waypoints): app storage ([FileDocDir]) or a folder the user
 * picked through the Storage Access Framework ([SafDocDir]). Files are addressed by name.
 */
interface DocDir {
    /** Identifies the location, so caches can tell when the folder changed. */
    val id: String

    /** Files only (no sub-folders, no temp files). */
    fun list(): List<DocEntry>
    fun exists(name: String): Boolean
    fun <T> read(name: String, block: (InputStream) -> T): T

    /** Create or replace [name]. A failed write leaves the old content in place. */
    fun write(name: String, block: (OutputStream) -> Unit)
    fun delete(name: String): Boolean
}

/** "base.ext", or with [suffix] numbers (2, 3, …) until the name is free. */
fun DocDir.uniqueName(base: String, ext: String, suffix: (Int) -> String): String {
    val taken = list().map { it.name }.toHashSet()
    if ("$base.$ext" !in taken) return "$base.$ext"
    var i = 2
    while ("$base${suffix(i)}.$ext" in taken) i++
    return "$base${suffix(i)}.$ext"
}

const val TMP_SUFFIX = ".tmp"

class FileDocDir(val dir: File) : DocDir {
    override val id: String = "file:" + dir.absolutePath

    override fun list(): List<DocEntry> =
        dir.listFiles { f -> f.isFile && !f.name.endsWith(TMP_SUFFIX) }.orEmpty().map { DocEntry(it.name, it.lastModified(), it.length()) }

    override fun exists(name: String) = File(dir, name).isFile

    override fun <T> read(name: String, block: (InputStream) -> T): T = File(dir, name).inputStream().use(block)

    override fun write(name: String, block: (OutputStream) -> Unit) {
        dir.mkdirs()
        val file = File(dir, name)
        val tmp = File(dir, name + TMP_SUFFIX)
        try {
            tmp.outputStream().use(block)
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "Could not save $name" }
        }
    }

    override fun delete(name: String) = File(dir, name).delete()

    fun file(name: String) = File(dir, name)
}

object Migration {
    /**
     * Move every file of [from] into [to]. On a name clash, the source file is dropped when it is
     * identical or [discardOnClash] says it holds nothing worth keeping; otherwise it is kept under a
     * new name. Returns old name → new name for the files that were renamed.
     */
    fun move(
        from: DocDir,
        to: DocDir,
        discardOnClash: (name: String, bytes: ByteArray) -> Boolean = { _, _ -> false },
    ): Map<String, String> {
        val renamed = HashMap<String, String>()
        for (e in from.list()) {
            val bytes = from.read(e.name) { it.readBytes() }
            var name = e.name
            if (to.exists(name)) {
                val existing = to.read(name) { it.readBytes() }
                if (existing.contentEquals(bytes) || discardOnClash(name, bytes)) {
                    from.delete(e.name)
                    continue
                }
                val base = name.substringBeforeLast('.')
                val ext = name.substringAfterLast('.', "")
                name = to.uniqueName(base, ext) { " ($it)" }
                renamed[e.name] = name
            }
            to.write(name) { it.write(bytes) }
            from.delete(e.name)
        }
        return renamed
    }
}
