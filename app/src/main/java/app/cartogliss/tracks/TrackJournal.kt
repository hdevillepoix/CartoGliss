package app.cartogliss.tracks

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Points of the track being recorded, written as they arrive so a crash or a killed process loses
 * nothing. Exported to GPX when recording stops, then deleted.
 */
class TrackJournal(context: Context) : SQLiteOpenHelper(context, "track_journal.db", null, 1) {
    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE track (id INTEGER PRIMARY KEY AUTOINCREMENT, started_at INTEGER NOT NULL)")
        db.execSQL(
            "CREATE TABLE point (track_id INTEGER NOT NULL, seg INTEGER NOT NULL, t INTEGER NOT NULL, " +
                "lat REAL NOT NULL, lon REAL NOT NULL, ele REAL, acc REAL)",
        )
        db.execSQL("CREATE INDEX point_track ON point(track_id, t)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    data class OpenTrack(val id: Long, val startedAtMs: Long)

    fun start(startedAtMs: Long): Long =
        writableDatabase.insertOrThrow("track", null, ContentValues().apply { put("started_at", startedAtMs) })

    /** The unfinished track, if recording was interrupted. */
    fun openTrack(): OpenTrack? = readableDatabase.rawQuery("SELECT id, started_at FROM track ORDER BY id DESC LIMIT 1", null).use { c ->
        if (c.moveToFirst()) OpenTrack(c.getLong(0), c.getLong(1)) else null
    }

    fun add(trackId: Long, p: TrackPoint) {
        writableDatabase.insertOrThrow("point", null, ContentValues().apply {
            put("track_id", trackId)
            put("seg", p.seg)
            put("t", p.timeMs)
            put("lat", p.lat)
            put("lon", p.lon)
            p.eleM?.let { put("ele", it) }
            p.accuracyM?.let { put("acc", it) }
        })
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
