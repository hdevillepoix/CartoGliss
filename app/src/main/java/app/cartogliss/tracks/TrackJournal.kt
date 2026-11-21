package app.cartogliss.tracks

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction

/**
 * Points of the track being recorded, written in batches ([PointBuffer]), so a killed process loses at
 * most the unwritten batch. Exported to GPX when the track is finished, then deleted.
 */
class TrackJournal(context: Context) : SQLiteOpenHelper(context, "track_journal.db", null, 2) {
    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE track (id INTEGER PRIMARY KEY AUTOINCREMENT, started_at INTEGER NOT NULL, paused INTEGER NOT NULL DEFAULT 0)")
        db.execSQL(
            "CREATE TABLE point (track_id INTEGER NOT NULL, seg INTEGER NOT NULL, t INTEGER NOT NULL, " +
                "lat REAL NOT NULL, lon REAL NOT NULL, ele REAL, acc REAL)",
        )
        db.execSQL("CREATE INDEX point_track ON point(track_id, t)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE track ADD COLUMN paused INTEGER NOT NULL DEFAULT 0")
    }

    data class OpenTrack(val id: Long, val startedAtMs: Long, val paused: Boolean)

    fun start(startedAtMs: Long): Long =
        writableDatabase.insertOrThrow("track", null, ContentValues().apply { put("started_at", startedAtMs) })

    /** The unfinished track, if recording was interrupted. */
    fun openTrack(): OpenTrack? = readableDatabase.rawQuery("SELECT id, started_at, paused FROM track ORDER BY id DESC LIMIT 1", null).use { c ->
        if (c.moveToFirst()) OpenTrack(c.getLong(0), c.getLong(1), c.getInt(2) != 0) else null
    }

    fun setPaused(trackId: Long, paused: Boolean) {
        writableDatabase.update("track", ContentValues().apply { put("paused", if (paused) 1 else 0) }, "id = ?", arrayOf(trackId.toString()))
    }

    /** One transaction per batch. */
    fun addAll(trackId: Long, points: List<TrackPoint>) {
        if (points.isEmpty()) return
        writableDatabase.transaction {
            for (p in points) {
                insertOrThrow("point", null, ContentValues().apply {
                    put("track_id", trackId)
                    put("seg", p.seg)
                    put("t", p.timeMs)
                    put("lat", p.lat)
                    put("lon", p.lon)
                    p.eleM?.let { put("ele", it) }
                    p.accuracyM?.let { put("acc", it) }
                })
            }
        }
    }

    fun points(trackId: Long): List<TrackPoint> =
        readableDatabase.rawQuery("SELECT seg, t, lat, lon, ele, acc FROM point WHERE track_id = ? ORDER BY t", arrayOf(trackId.toString())).use { c ->
            val out = ArrayList<TrackPoint>(c.count)
            while (c.moveToNext()) {
                out += TrackPoint(
                    timeMs = c.getLong(1), lat = c.getDouble(2), lon = c.getDouble(3),
                    eleM = if (c.isNull(4)) null else c.getDouble(4),
                    accuracyM = if (c.isNull(5)) null else c.getFloat(5),
                    seg = c.getInt(0),
                )
            }
            out
        }

    fun delete(trackId: Long) {
        writableDatabase.delete("point", "track_id = ?", arrayOf(trackId.toString()))
        writableDatabase.delete("track", "id = ?", arrayOf(trackId.toString()))
    }
}
