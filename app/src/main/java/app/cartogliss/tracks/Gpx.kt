package app.cartogliss.tracks

import app.cartogliss.geo.Geo
import app.cartogliss.kml.Xml
import app.cartogliss.kml.child
import app.cartogliss.kml.children
import app.cartogliss.kml.descendants
import app.cartogliss.kml.local
import app.cartogliss.kml.text
import app.cartogliss.settings.TrackSettings
import java.io.InputStream
import java.io.Writer
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale

data class TrackPoint(
    val timeMs: Long,
    val lat: Double,
    val lon: Double,
    val eleM: Double? = null,
    val accuracyM: Float? = null,
    /** Segment number: a new segment starts when recording resumes after an interruption. */
    val seg: Int = 0,
)

data class GpxTrack(val name: String?, val segments: List<List<TrackPoint>>) {
    val points: List<TrackPoint> get() = segments.flatten()
    val distanceM: Double get() = segments.sumOf(TrackStats::distance)
    val startMs: Long? get() = points.firstOrNull { it.timeMs > 0 }?.timeMs
    val endMs: Long? get() = points.lastOrNull { it.timeMs > 0 }?.timeMs
}

object TrackStats {
    fun distance(points: List<TrackPoint>): Double =
        points.zipWithNext().sumOf { (a, b) -> Geo.distance(a.lat, a.lon, b.lat, b.lon) }

    /** "0:42" (m:ss) under an hour, "1:05:10" (h:mm:ss) above. */
    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) else String.format(Locale.ROOT, "%d:%02d", m, sec)
    }
}

/**
 * Track recording settings as a filter (DESIGN.md): drop fixes worse than the accuracy limit, then keep a
 * point only when both the minimum time and minimum distance since the last kept point have passed.
 */
class TrackFilter(private val s: TrackSettings) {
    fun accept(last: TrackPoint?, p: TrackPoint): Boolean {
        val acc = p.accuracyM
        if (acc != null && acc > s.maxAccuracyM) return false
        if (last == null) return true
        if (p.timeMs - last.timeMs < s.minIntervalS * 1000L) return false
        return Geo.distance(last.lat, last.lon, p.lat, p.lon) >= s.minDistanceM
    }
}

object GpxWriter {
    fun write(name: String, points: List<TrackPoint>, out: Writer) {
        out.write("""<?xml version="1.0" encoding="UTF-8"?>""" + "\n")
        out.write("""<gpx version="1.1" creator="CartoGliss" xmlns="http://www.topografix.com/GPX/1/1">""" + "\n")
        out.write("  <metadata><name>${esc(name)}</name>")
        points.firstOrNull()?.let { out.write("<time>${Instant.ofEpochMilli(it.timeMs)}</time>") }
        out.write("</metadata>\n")
        out.write("  <trk>\n    <name>${esc(name)}</name>\n")
        for ((_, seg) in points.groupBy { it.seg }.toSortedMap()) {
            out.write("    <trkseg>\n")
            for (p in seg) {
                out.write("      <trkpt lat=\"${f7(p.lat)}\" lon=\"${f7(p.lon)}\">")
                p.eleM?.let { out.write("<ele>${String.format(Locale.ROOT, "%.1f", it)}</ele>") }
                out.write("<time>${Instant.ofEpochMilli(p.timeMs)}</time>")
                p.accuracyM?.let { out.write("<hdop>${String.format(Locale.ROOT, "%.1f", it / 5f)}</hdop>") }
                out.write("</trkpt>\n")
            }
            out.write("    </trkseg>\n")
        }
        out.write("  </trk>\n</gpx>\n")
    }

    private fun f7(v: Double) = String.format(Locale.ROOT, "%.7f", v)

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

object GpxParser {
    /** Reads trk/trkseg/trkpt (and rte/rtept as one segment). */
    fun parse(input: InputStream): GpxTrack {
        val root = Xml.builder().parse(input).documentElement
        val segments = mutableListOf<List<TrackPoint>>()
        for (trk in root.descendants().filter { it.local == "trk" }) {
            for (seg in trk.children("trkseg")) segments += seg.children("trkpt").mapNotNull(::point).toList()
        }
        for (rte in root.descendants().filter { it.local == "rte" }) segments += rte.children("rtept").mapNotNull(::point).toList()
        val name = root.descendants().firstOrNull { it.local == "trk" }?.child("name")?.text()?.trim()
            ?: root.child("metadata")?.child("name")?.text()?.trim()
        return GpxTrack(name?.takeIf { it.isNotEmpty() }, segments.filter { it.isNotEmpty() })
    }

    private fun point(e: org.w3c.dom.Element): TrackPoint? {
        val lat = e.getAttribute("lat").toDoubleOrNull() ?: return null
        val lon = e.getAttribute("lon").toDoubleOrNull() ?: return null
        val time = e.child("time")?.text()?.trim()?.let {
            try { Instant.parse(it).toEpochMilli() } catch (x: DateTimeParseException) { null }
        } ?: 0L
        return TrackPoint(time, lat, lon, e.child("ele")?.text()?.trim()?.toDoubleOrNull())
    }
}
