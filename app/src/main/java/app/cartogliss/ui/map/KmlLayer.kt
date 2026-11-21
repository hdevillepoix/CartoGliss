package app.cartogliss.ui.map

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cartogliss.kml.KmlCollection
import app.cartogliss.kml.KmlGeometry
import app.cartogliss.kml.LatLon
import app.cartogliss.kml.Waypoint
import app.cartogliss.map.MapViewport
import app.cartogliss.map.TileMath
import kotlin.math.max
import kotlin.math.round

/** App defaults when a KML style doesn't say. */
private const val DEFAULT_LINE = 0xFFD81B60.toInt()
private const val DEFAULT_FILL = 0x33D81B60
private const val DEFAULT_POINT = 0xFFFF6F00.toInt()

/** Above this many visible waypoints, names are not drawn (it would be unreadable anyway). */
private const val MAX_LABELS = 300

/** Geometry pre-projected to normalized Web Mercator, with a bounding box for culling. */
class Ring(val xs: DoubleArray, val ys: DoubleArray) {
    val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
}

class PreparedLine(val ring: Ring, val color: Int, val widthDp: Float)
class PreparedArea(val rings: List<Ring>, val fill: Int?, val outline: Int?, val widthDp: Float)
class PreparedPoint(val x: Double, val y: Double, val color: Int, val waypoint: Waypoint)

class KmlLayerData(
    val lines: List<PreparedLine> = emptyList(),
    val areas: List<PreparedArea> = emptyList(),
    val points: List<PreparedPoint> = emptyList(),
) {
    companion object {
        val EMPTY = KmlLayerData()

        fun from(collections: List<KmlCollection>): KmlLayerData {
            val lines = ArrayList<PreparedLine>()
            val areas = ArrayList<PreparedArea>()
            val points = ArrayList<PreparedPoint>()
            for (c in collections) {
                if (!c.active) continue
                val doc = c.doc ?: continue
                for (pm in doc.placemarks) {
                    val st = pm.style
                    for (g in pm.geometries) when (g) {
                        is KmlGeometry.Line -> lines += PreparedLine(ring(g.points), st.lineColor ?: DEFAULT_LINE, st.lineWidth ?: 2.5f)
                        is KmlGeometry.Polygon -> areas += PreparedArea(
                            rings = listOf(ring(g.outer)) + g.holes.map(::ring),
                            fill = if (st.fill) st.polyColor ?: DEFAULT_FILL else null,
                            outline = if (st.outline) st.lineColor ?: DEFAULT_LINE else null,
                            widthDp = st.lineWidth ?: 1.5f,
                        )
                        is KmlGeometry.Point -> points += PreparedPoint(
                            TileMath.lonToX(g.at.lon), TileMath.latToY(g.at.lat), st.iconColor ?: DEFAULT_POINT,
                            Waypoint(pm.name ?: "(unnamed)", g.at.lat, g.at.lon, pm.description, c.id, c.title),
                        )
                    }
                }
            }
            return KmlLayerData(lines, areas, points)
        }

        private fun ring(pts: List<LatLon>) = Ring(
            DoubleArray(pts.size) { TileMath.lonToX(pts[it].lon) },
            DoubleArray(pts.size) { TileMath.latToY(pts[it].lat) },
        )
    }
}

/** Shift (in whole worlds) that brings normalized x nearest to the view centre. */
private fun worldShift(x: Double, mx: Double) = round(mx - x)

private class Projector(vp: MapViewport, tilePx: Float, w: Float, h: Float) {
    val s = vp.scale(tilePx)
    val ox = w / 2.0 - vp.mx * s
    val oy = h / 2.0 - vp.my * s
    fun x(nx: Double, shift: Double) = ((nx + shift) * s + ox).toFloat()
    fun y(ny: Double) = (ny * s + oy).toFloat()
}

fun DrawScope.drawKml(vp: MapViewport, tilePx: Float, data: KmlLayerData) {
    if (data.lines.isEmpty() && data.areas.isEmpty() && data.points.isEmpty()) return
    val p = Projector(vp, tilePx, size.width, size.height)
    // Visible normalized window, for culling.
    val halfW = size.width / 2.0 / p.s
    val halfH = size.height / 2.0 / p.s
    fun visible(r: Ring, shift: Double) =
        r.maxX + shift >= vp.mx - halfW && r.minX + shift <= vp.mx + halfW &&
            r.maxY >= vp.my - halfH && r.minY <= vp.my + halfH

    for (a in data.areas) {
        val outer = a.rings.first()
        val shift = worldShift((outer.minX + outer.maxX) / 2, vp.mx)
        if (!visible(outer, shift)) continue
        val path = Path().apply {
            fillType = PathFillType.EvenOdd
            for (r in a.rings) addRing(r, shift, p, close = true)
        }
        a.fill?.let { drawPath(path, Color(it)) }
        a.outline?.let { drawPath(path, Color(it), style = Stroke(a.widthDp.dp.toPx(), join = StrokeJoin.Round)) }
    }
    for (l in data.lines) {
        val shift = worldShift((l.ring.minX + l.ring.maxX) / 2, vp.mx)
        if (!visible(l.ring, shift)) continue
        val path = Path().apply { addRing(l.ring, shift, p, close = false) }
        drawPath(path, Color.White.copy(alpha = 0.7f), style = Stroke((l.widthDp + 2f).dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, Color(l.color), style = Stroke(l.widthDp.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    val shown = ArrayList<Pair<Offset, PreparedPoint>>()
    for (pt in data.points) {
        val sx = p.x(pt.x, worldShift(pt.x, vp.mx))
        val sy = p.y(pt.y)
        if (sx < -50 || sy < -50 || sx > size.width + 50 || sy > size.height + 50) continue
        shown += Offset(sx, sy) to pt
    }
    val r = 5.dp.toPx()
    for ((o, pt) in shown) {
        drawCircle(Color.White, r + 1.5.dp.toPx(), o)
        drawCircle(Color(pt.color), r, o)
    }
    if (shown.size <= MAX_LABELS) {
        val textPx = 12.sp.toPx()
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = textPx; color = android.graphics.Color.BLACK; typeface = Typeface.DEFAULT_BOLD }
        val halo = Paint(fill).apply { color = android.graphics.Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3.dp.toPx(); strokeJoin = Paint.Join.ROUND }
        drawIntoCanvas { c ->
            for ((o, pt) in shown) {
                val x = o.x + r + 3.dp.toPx()
                val y = o.y + textPx * 0.35f
                c.nativeCanvas.drawText(pt.waypoint.name, x, y, halo)
                c.nativeCanvas.drawText(pt.waypoint.name, x, y, fill)
            }
        }
    }
}

private fun Path.addRing(r: Ring, shift: Double, p: Projector, close: Boolean) {
    // Skip points closer than ~1 px to the previous one: long tracks stay cheap when zoomed out.
    var lastX = p.x(r.xs[0], shift)
    var lastY = p.y(r.ys[0])
    moveTo(lastX, lastY)
    for (i in 1 until r.xs.size) {
        val x = p.x(r.xs[i], shift)
        val y = p.y(r.ys[i])
        if (i < r.xs.size - 1 && max(kotlin.math.abs(x - lastX), kotlin.math.abs(y - lastY)) < 1f) continue
        lineTo(x, y)
        lastX = x; lastY = y
    }
    if (close) close()
}

/** The waypoint nearest to a screen point, within [radiusPx]. */
fun KmlLayerData.hitTest(vp: MapViewport, tilePx: Float, width: Int, height: Int, at: Offset, radiusPx: Float): Waypoint? {
    val p = Projector(vp, tilePx, width.toFloat(), height.toFloat())
    var best: Waypoint? = null
    var bestD = radiusPx * radiusPx
    for (pt in points) {
        val dx = p.x(pt.x, worldShift(pt.x, vp.mx)) - at.x
        val dy = p.y(pt.y) - at.y
        val d = dx * dx + dy * dy
        if (d <= bestD) { bestD = d; best = pt.waypoint }
    }
    return best
}

private const val TRACK_SAVED = 0xFF1565C0.toInt()
private const val TRACK_LIVE = 0xFFD32F2F.toInt()

/** GPX tracks for the map: saved ones in blue, the one being recorded in red. */
object TrackLines {
    fun fromSegments(segments: List<List<app.cartogliss.tracks.TrackPoint>>, live: Boolean): List<PreparedLine> =
        segments.filter { it.size >= 2 }.map { seg ->
            PreparedLine(
                Ring(DoubleArray(seg.size) { TileMath.lonToX(seg[it].lon) }, DoubleArray(seg.size) { TileMath.latToY(seg[it].lat) }),
                if (live) TRACK_LIVE else TRACK_SAVED,
                3f,
            )
        }
}

fun DrawScope.drawTrackLines(vp: MapViewport, tilePx: Float, lines: List<PreparedLine>) =
    drawKml(vp, tilePx, KmlLayerData(lines = lines))
