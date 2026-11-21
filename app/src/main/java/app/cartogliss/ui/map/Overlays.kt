package app.cartogliss.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.cartogliss.geo.Geo
import app.cartogliss.geo.Rect2
import app.cartogliss.geo.Segment
import app.cartogliss.geo.clipSegment
import app.cartogliss.location.Fix
import app.cartogliss.map.MapViewport
import app.cartogliss.settings.Target
import kotlin.math.cos
import kotlin.math.sin

/** Everything drawn on top of the tiles. */
data class MapOverlay(
    val fix: Fix? = null,
    val fixRecent: Boolean = false,
    /** True-north heading of the top of the screen; null hides the view cone. */
    val coneDeg: Float? = null,
    /** Direction of movement; null hides the arrow. */
    val movementDeg: Float? = null,
    val target: Target? = null,
)

private val GPS_BLUE = Color(0xFF1E88E5)
private val GPS_GREY = Color(0xFF9E9E9E)
private val CONE = Color(0x592196F3)
private val ARROW = Color(0xFF0D47A1)
private val TARGET_GREEN = Color(0xFF2E7D32)
private const val CONE_HALF_ANGLE = 20f

fun DrawScope.drawOverlays(vp: MapViewport, tilePx: Float, o: MapOverlay) {
    val w = size.width.toInt()
    val h = size.height.toInt()
    val fix = o.fix
    val pos = fix?.let { vp.toScreen(it.lat, it.lon, w, h, tilePx) }?.let { Offset(it.first.toFloat(), it.second.toFloat()) }

    // Target: green dashed line from the position, then the target marker.
    o.target?.let { t ->
        val refX = fix?.let { vp.nearestX(it.lon) } ?: vp.mx
        val (tx, ty) = vp.toScreen(t.lat, t.lon, w, h, tilePx, refX)
        val margin = 50f
        val screen = Rect2(-margin, -margin, size.width + margin, size.height + margin)
        if (pos != null) {
            clipSegment(Segment(pos.x, pos.y, tx.toFloat(), ty.toFloat()), screen)?.let { seg ->
                drawLine(
                    TARGET_GREEN,
                    Offset(seg.x0, seg.y0),
                    Offset(seg.x1, seg.y1),
                    strokeWidth = 3.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx())),
                    cap = StrokeCap.Butt,
                )
            }
        }
        if (tx in screen.left.toDouble()..screen.right.toDouble() && ty in screen.top.toDouble()..screen.bottom.toDouble()) {
            val c = Offset(tx.toFloat(), ty.toFloat())
            drawCircle(Color.White, 9.dp.toPx(), c, style = Stroke(5.dp.toPx()))
            drawCircle(TARGET_GREEN, 9.dp.toPx(), c, style = Stroke(3.dp.toPx()))
            drawCircle(TARGET_GREEN, 2.5.dp.toPx(), c)
        }
    }

    if (pos != null && fix != null) {
        val dotColor = if (o.fixRecent) GPS_BLUE else GPS_GREY

        // Accuracy disc, only when larger than the dot itself.
        fix.accuracyM?.let { acc ->
            val mpp = Geo.metersPerPixel(fix.lat, vp.scale(tilePx))
            val r = (acc / mpp).toFloat()
            if (r > 9.dp.toPx() && r < size.maxDimension * 4) drawCircle(dotColor.copy(alpha = 0.12f), r, pos)
        }

        // View cone, ±20°.
        o.coneDeg?.let { deg ->
            val r = 64.dp.toPx()
            drawArc(
                CONE,
                startAngle = deg - 90f - CONE_HALF_ANGLE,
                sweepAngle = 2 * CONE_HALF_ANGLE,
                useCenter = true,
                topLeft = Offset(pos.x - r, pos.y - r),
                size = Size(2 * r, 2 * r),
            )
        }

        // Direction of movement: unfilled triangle around the dot.
        o.movementDeg?.let { deg ->
            val a = Math.toRadians(deg.toDouble())
            val u = Offset(sin(a).toFloat(), -cos(a).toFloat()) // screen direction of the bearing (north-up)
            val n = Offset(-u.y, u.x)
            val tip = pos + u * 26.dp.toPx()
            val back = pos - u * 12.dp.toPx()
            val path = Path().apply {
                moveTo(tip.x, tip.y)
                val l = back + n * 13.dp.toPx()
                val r = back - n * 13.dp.toPx()
                lineTo(l.x, l.y)
                lineTo(r.x, r.y)
                close()
            }
            drawPath(path, Color.White, style = Stroke(5.dp.toPx(), join = StrokeJoin.Round))
            drawPath(path, ARROW, style = Stroke(2.5.dp.toPx(), join = StrokeJoin.Round))
        }

        drawCircle(Color.White, 9.dp.toPx(), pos)
        drawCircle(dotColor, 7.dp.toPx(), pos)
    }

    drawCrosshair()
}

/** Thin black crosshair at the map centre, with a small gap so the exact centre stays visible. */
private fun DrawScope.drawCrosshair() {
    val c = center
    val arm = 11.dp.toPx()
    val gap = 3.dp.toPx()
    val sw = 1.dp.toPx()
    drawLine(Color.Black, Offset(c.x - arm, c.y), Offset(c.x - gap, c.y), sw)
    drawLine(Color.Black, Offset(c.x + gap, c.y), Offset(c.x + arm, c.y), sw)
    drawLine(Color.Black, Offset(c.x, c.y - arm), Offset(c.x, c.y - gap), sw)
    drawLine(Color.Black, Offset(c.x, c.y + gap), Offset(c.x, c.y + arm), sw)
}
