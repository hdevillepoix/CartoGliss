package app.cartogliss.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cartogliss.geo.Geo
import app.cartogliss.geo.Units
import app.cartogliss.map.MapViewport
import app.cartogliss.settings.DistanceUnit

val OVERZOOM_RED = Color(0xFFD32F2F)
val RECORD_RED = Color(0xFFD32F2F)

/** Scale bar length on screen: 2 cm ≈ 126 dp. */
private val SCALE_BAR_DP = 126.dp

/** + / NxM / − stack. The NxM box toggles overzoom mode (red when on). */
@Composable
fun ZoomControls(
    vp: MapViewport,
    overzoomMode: Boolean,
    canIn: Boolean,
    canOut: Boolean,
    onIn: () -> Unit,
    onOut: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 2.dp,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TextButton(onClick = onIn, enabled = canIn, modifier = Modifier.size(56.dp)) {
                Text("+", fontSize = 26.sp)
            }
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .background(
                        if (overzoomMode) OVERZOOM_RED else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(4.dp),
                    ),
            ) {
                TextButton(onClick = onToggle, modifier = Modifier.size(width = 56.dp, height = 36.dp)) {
                    Text(
                        "${vp.zoom}x${vp.overzoom}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (overzoomMode) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            TextButton(onClick = onOut, enabled = canOut, modifier = Modifier.size(56.dp)) {
                Text("−", fontSize = 26.sp)
            }
        }
    }
}

/** "Centre on my position" button. */
@Composable
fun CentreOnGpsButton(enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 2.dp,
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(56.dp)) {
            TargetIcon(tint, Modifier.semantics { contentDescription = "Centre on my position" })
        }
    }
}

/** Fixed-length scale bar; its label changes with zoom and latitude (2 significant figures). */
@Composable
fun ScaleBar(vp: MapViewport, tilePx: Float, unit: DistanceUnit, modifier: Modifier = Modifier) {
    val barPx = with(LocalDensity.current) { SCALE_BAR_DP.toPx() }
    val meters = Geo.metersPerPixel(vp.lat, vp.scale(tilePx)) * barPx
    val halo = Shadow(Color.White, Offset.Zero, blurRadius = 6f)
    Column(modifier.width(SCALE_BAR_DP), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            Units.scaleLabel(meters, unit),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
            style = TextStyle(shadow = halo),
        )
        Canvas(Modifier.width(SCALE_BAR_DP).height(8.dp)) {
            val y = size.height - 1.5.dp.toPx()
            val tick = size.height
            for ((color, sw) in listOf(Color.White to 4.dp.toPx(), Color.Black to 2.dp.toPx())) {
                drawLine(color, Offset(0f, y), Offset(size.width, y), sw, cap = StrokeCap.Square)
                drawLine(color, Offset(0f, y), Offset(0f, y - tick), sw, cap = StrokeCap.Square)
                drawLine(color, Offset(size.width, y), Offset(size.width, y - tick), sw, cap = StrokeCap.Square)
            }
        }
    }
}

// ---- Small hand-drawn icons (the core icon set has none of these) ----

@Composable
fun TargetIcon(tint: Color, modifier: Modifier = Modifier, size: Dp = 26.dp) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension * 0.32f
        val sw = 2.dp.toPx()
        drawCircle(tint, r, style = Stroke(sw))
        drawCircle(tint, 3.dp.toPx())
        val c = center
        val outer = this.size.minDimension / 2f
        drawLine(tint, Offset(c.x, c.y - outer), Offset(c.x, c.y - r), sw)
        drawLine(tint, Offset(c.x, c.y + r), Offset(c.x, c.y + outer), sw)
        drawLine(tint, Offset(c.x - outer, c.y), Offset(c.x - r, c.y), sw)
        drawLine(tint, Offset(c.x + r, c.y), Offset(c.x + outer, c.y), sw)
    }
}

/** Three bars: "stats". */
@Composable
fun StatsIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val bw = w * 0.2f
        listOf(0.55f, 0.25f, 0.4f).forEachIndexed { i, top ->
            val x = w * (0.12f + i * 0.3f)
            drawRect(tint, Offset(x, h * top), Size(bw, h * (0.9f - top)))
        }
    }
}

/** Folded map: "map view". */
@Composable
fun MapIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(w * 0.08f, h * 0.2f); lineTo(w * 0.36f, h * 0.1f); lineTo(w * 0.64f, h * 0.2f); lineTo(w * 0.92f, h * 0.1f)
            lineTo(w * 0.92f, h * 0.8f); lineTo(w * 0.64f, h * 0.9f); lineTo(w * 0.36f, h * 0.8f); lineTo(w * 0.08f, h * 0.9f); close()
        }
        drawPath(p, tint, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
        drawLine(tint, Offset(w * 0.36f, h * 0.1f), Offset(w * 0.36f, h * 0.8f), 1.5.dp.toPx())
        drawLine(tint, Offset(w * 0.64f, h * 0.2f), Offset(w * 0.64f, h * 0.9f), 1.5.dp.toPx())
    }
}

/** Satellite: body with two panels. Crossed out when GPS is off. */
@Composable
fun SatelliteIcon(tint: Color, off: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val sw = 1.8.dp.toPx()
        // Body (diamond) and two panels along the diagonal.
        val body = Path().apply {
            moveTo(w * 0.5f, h * 0.34f); lineTo(w * 0.66f, h * 0.5f); lineTo(w * 0.5f, h * 0.66f); lineTo(w * 0.34f, h * 0.5f); close()
        }
        drawPath(body, tint)
        fun panel(cx: Float, cy: Float) {
            val p = Path().apply {
                moveTo(cx - w * 0.12f, cy - h * 0.02f); lineTo(cx - w * 0.02f, cy - h * 0.12f)
                lineTo(cx + w * 0.12f, cy + h * 0.02f); lineTo(cx + w * 0.02f, cy + h * 0.12f); close()
            }
            drawPath(p, tint, style = Stroke(sw, join = StrokeJoin.Round))
        }
        panel(w * 0.24f, h * 0.24f)
        panel(w * 0.76f, h * 0.76f)
        if (off) drawLine(OVERZOOM_RED, Offset(w * 0.1f, h * 0.9f), Offset(w * 0.9f, h * 0.1f), 2.5.dp.toPx())
    }
}

enum class RecordMode { OFF, RECORDING, PAUSED }

/** Record: red disc when recording, red pause bars in a red ring when paused, outlined circle otherwise. */
@Composable
fun RecordIcon(mode: RecordMode, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp)) {
        val r = size.minDimension * 0.4f
        when (mode) {
            RecordMode.RECORDING -> drawCircle(RECORD_RED, r)
            RecordMode.OFF -> drawCircle(tint, r, style = Stroke(2.dp.toPx()))
            RecordMode.PAUSED -> {
                drawCircle(RECORD_RED, r, style = Stroke(2.dp.toPx()))
                val bw = r * 0.28f
                val bh = r * 0.9f
                val cx = size.width / 2
                val cy = size.height / 2
                drawRect(RECORD_RED, Offset(cx - bw * 1.6f, cy - bh / 2), androidx.compose.ui.geometry.Size(bw, bh))
                drawRect(RECORD_RED, Offset(cx + bw * 0.6f, cy - bh / 2), androidx.compose.ui.geometry.Size(bw, bh))
            }
        }
    }
}
