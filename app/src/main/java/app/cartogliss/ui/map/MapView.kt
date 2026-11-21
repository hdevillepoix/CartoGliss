package app.cartogliss.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.cartogliss.map.MapViewport
import app.cartogliss.map.TileOnScreen
import app.cartogliss.tiles.TileKey
import app.cartogliss.tiles.TileLoader
import app.cartogliss.tiles.TileSource
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.max

/** Screen size of one tile at overzoom 1, in dp (see DESIGN.md: always 256 dp × M). */
val TILE_DP = 256.dp

/** Pinch ratio beyond which a pinch counts as one zoom step on release. */
const val PINCH_STEP_RATIO = 1.4f

private val VOID = Color(0xFFBDBDBD)
private val PLACEHOLDER = Color(0xFFE6E6E6)
private val PLACEHOLDER_GRID = Color(0xFFD2D2D2)

/**
 * Discrete raster map. Draws only tiles of the current zoom level; anything not loaded is a
 * neutral placeholder. Gestures: 1:1 drag (no fling), pinch = ±1 step on release, double-tap = +1.
 *
 * @param onPinchPending reports the step a pinch would apply if released now (−1, 0, +1).
 */
@Composable
fun MapView(
    viewport: MapViewport,
    source: TileSource,
    loader: TileLoader,
    onPan: (dx: Float, dy: Float) -> Unit,
    onStep: (steps: Int, focusFromCentre: Offset) -> Unit,
    onPinchPending: (Int) -> Unit,
    overlay: MapOverlay,
    modifier: Modifier = Modifier,
) {
    val tilePx = with(LocalDensity.current) { TILE_DP.toPx() }
    val doubleTapSlop = with(LocalDensity.current) { 48.dp.toPx() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val version by loader.version.collectAsState()

    val tiles = remember(viewport, size, tilePx) { viewport.visibleTiles(size.width, size.height, tilePx) }
    val keys = remember(tiles, source) {
        tiles.filter { source.covers(it.z, it.x, it.y) }.map { TileKey(source.id, it.z, it.x, it.y) }
    }

    LaunchedEffect(keys, source) {
        // Request now, then keep retrying failed tiles while this view stays put.
        while (true) {
            loader.request(source, keys)
            delay(TileLoader.RETRY_AFTER_MS)
        }
    }

    val pan by rememberUpdatedState(onPan)
    val step by rememberUpdatedState(onStep)
    val pending by rememberUpdatedState(onPinchPending)

    Canvas(
        modifier
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                val tapSlop = viewConfiguration.touchSlop
                val doubleTapMs = viewConfiguration.doubleTapTimeoutMillis
                var lastTapTime = 0L
                var lastTapPos = Offset.Zero
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var moved = 0f
                    var pinch = 1f
                    var maxPointers = 1
                    var lastCount = 0
                    var lastCentroid = Offset.Unspecified
                    var lastSpan = 0f
                    var upTime = down.uptimeMillis
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pressed = event.changes.filter { it.pressed }
                        event.changes.forEach { it.consume() }
                        if (pressed.isEmpty()) {
                            upTime = event.changes.maxOf { it.uptimeMillis }
                            break
                        }
                        val centroid = Offset(
                            pressed.sumOf { it.position.x.toDouble() }.toFloat() / pressed.size,
                            pressed.sumOf { it.position.y.toDouble() }.toFloat() / pressed.size,
                        )
                        val span = if (pressed.size >= 2) {
                            pressed.sumOf { hypot(it.position.x - centroid.x, it.position.y - centroid.y).toDouble() }.toFloat() / pressed.size
                        } else 0f
                        // Only compare frames with the same set of fingers, otherwise the centroid jumps.
                        if (pressed.size == lastCount && lastCentroid.isSpecified) {
                            val d = centroid - lastCentroid
                            moved += d.getDistance()
                            if (d != Offset.Zero) pan(d.x, d.y)
                            if (pressed.size >= 2 && lastSpan > 0f && span > 0f) {
                                pinch *= span / lastSpan
                                pending(pinchStep(pinch))
                            }
                        }
                        lastCount = pressed.size
                        lastCentroid = centroid
                        lastSpan = span
                        maxPointers = max(maxPointers, pressed.size)
                    }
                    val centre = Offset(size.width / 2f, size.height / 2f)
                    if (maxPointers >= 2) {
                        pending(0)
                        val s = pinchStep(pinch)
                        if (s != 0) step(s, lastCentroid - centre)
                        lastTapTime = 0L
                    } else if (moved < tapSlop) {
                        val isDouble = upTime - lastTapTime < doubleTapMs &&
                            (down.position - lastTapPos).getDistance() < doubleTapSlop
                        if (isDouble) {
                            step(1, down.position - centre)
                            lastTapTime = 0L
                        } else {
                            lastTapTime = upTime
                            lastTapPos = down.position
                        }
                    }
                }
            },
    ) {
        @Suppress("UNUSED_EXPRESSION") version // redraw when tiles arrive
        drawRect(VOID)
        for (t in tiles) {
            val img = loader.get(TileKey(source.id, t.z, t.x, t.y))
            if (img != null) {
                drawImage(
                    image = img,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(img.width, img.height),
                    dstOffset = IntOffset(t.left, t.top),
                    dstSize = IntSize(t.right - t.left, t.bottom - t.top),
                    filterQuality = FilterQuality.Low,
                )
            } else if (!source.covers(t.z, t.x, t.y)) {
                drawOutOfCoverage(t)
            } else {
                drawPlaceholder(t)
            }
        }
        drawOverlays(viewport, tilePx, overlay)
    }
}

private fun pinchStep(ratio: Float): Int = when {
    ratio >= PINCH_STEP_RATIO -> 1
    ratio <= 1f / PINCH_STEP_RATIO -> -1
    else -> 0
}

/** Light grey square with a fine 4×4 grid: "not loaded", never a guess from another level. */
private fun DrawScope.drawPlaceholder(t: TileOnScreen) {
    val w = (t.right - t.left).toFloat()
    val h = (t.bottom - t.top).toFloat()
    drawRect(PLACEHOLDER, topLeft = Offset(t.left.toFloat(), t.top.toFloat()), size = Size(w, h))
    val stroke = 1f
    for (i in 0..3) {
        val x = t.left + w * i / 4f
        val y = t.top + h * i / 4f
        drawLine(PLACEHOLDER_GRID, Offset(x, t.top.toFloat()), Offset(x, t.bottom.toFloat()), stroke)
        drawLine(PLACEHOLDER_GRID, Offset(t.left.toFloat(), y), Offset(t.right.toFloat(), y), stroke)
    }
}

private val OUT_OF_COVERAGE = Color(0xFFF2F2F2)
private val OUT_OF_COVERAGE_HATCH = Color(0xFFDADADA)

/** Outside the source's coverage box: pale with diagonal hatching, distinct from "not loaded yet". */
private fun DrawScope.drawOutOfCoverage(t: TileOnScreen) {
    val l = t.left.toFloat()
    val tp = t.top.toFloat()
    val w = (t.right - t.left).toFloat()
    val h = (t.bottom - t.top).toFloat()
    drawRect(OUT_OF_COVERAGE, topLeft = Offset(l, tp), size = Size(w, h))
    clipRect(l, tp, l + w, tp + h) {
        val step = w / 8f
        var d = -h
        while (d < w) {
            drawLine(OUT_OF_COVERAGE_HATCH, Offset(l + d, tp + h), Offset(l + d + h, tp), 1f)
            d += step
        }
    }
}
