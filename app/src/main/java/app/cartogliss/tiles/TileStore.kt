package app.cartogliss.tiles

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent tile cache: one SQLite file per source id (MBTiles-like schema).
 * Tiles are kept forever; the only removal is an explicit per-source clear.
 */
class TileStore(private val context: Context) {

    private val dbs = ConcurrentHashMap<String, TileDb>()

    private fun db(sourceId: String): TileDb = dbs.computeIfAbsent(sourceId) { TileDb(context, fileName(it)) }

    fun get(sourceId: String, z: Int, x: Int, y: Int): ByteArray? =
        db(sourceId).readableDatabase.rawQuery(
            "SELECT data FROM tiles WHERE z = ? AND x = ? AND y = ?",
            arrayOf(z.toString(), x.toString(), y.toString()),
        ).use { c -> if (c.moveToFirst()) c.getBlob(0) else null }

    fun put(sourceId: String, z: Int, x: Int, y: Int, data: ByteArray) {
        val values = ContentValues().apply {
            put("z", z); put("x", x); put("y", y)
            put("data", data)
            put("fetched_at", System.currentTimeMillis())
        }
        db(sourceId).writableDatabase.insertWithOnConflict("tiles", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun tileCount(sourceId: String): Long {
        if (!dbFile(sourceId).exists()) return 0
        return db(sourceId).readableDatabase.rawQuery("SELECT COUNT(*) FROM tiles", null)
            .use { c -> if (c.moveToFirst()) c.getLong(0) else 0 }
    }

    /** Size on disk, including SQLite's WAL side files. */
    fun sizeBytes(sourceId: String): Long {
        val f = dbFile(sourceId)
        return listOf(f, File(f.path + "-wal"), File(f.path + "-shm")).sumOf { if (it.exists()) it.length() else 0L }
    }

    fun clear(sourceId: String) {
        if (!dbFile(sourceId).exists()) return
        val w = db(sourceId).writableDatabase
        w.delete("tiles", null, null)
        w.execSQL("VACUUM")
    }

    /** Ids of every source with a cache file on disk (including retired sources). */
    fun storedSourceIds(): List<String> {
        val dir = context.getDatabasePath("x").parentFile ?: return emptyList()
        return dir.listFiles().orEmpty()
            .map { it.name }
            .filter { it.startsWith("tiles-") && it.endsWith(".db") }
            .map { it.removePrefix("tiles-").removeSuffix(".db") }
    }

    private fun dbFile(sourceId: String): File = context.getDatabasePath(fileName(sourceId))

    private fun fileName(sourceId: String) = "tiles-" + sourceId.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".db"

    private class TileDb(context: Context, name: String) : SQLiteOpenHelper(context, name, null, 1) {
        init {
            setWriteAheadLoggingEnabled(true)
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE tiles (
                    z INTEGER NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    data BLOB NOT NULL,
                    fetched_at INTEGER NOT NULL,
                    PRIMARY KEY (z, x, y)
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}
