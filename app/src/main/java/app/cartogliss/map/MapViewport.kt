package app.cartogliss.map

import app.cartogliss.settings.Viewport
import kotlin.math.floor
import kotlin.math.roundToInt

/** A tile to draw, with its integer screen rectangle (pixel-snapped so neighbours never leave seams). */
data class TileOnScreen(val z: Int, val x: Int, val y: Int, val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * Discrete map viewport: centre in normalized Web Mercator coordinates, integer zoom level N,
 * and overzoom factor M ∈ {1, 2, 4, 8}.
 *
 * `tilePx` is the on-screen size, in pixels, of one tile at M = 1 (256 dp × density).
 */
data class MapViewport(
    val mx: Double,
    val my: Double,
    val zoom: Int,
    val overzoom: Int = 1,
) {
    val lat: Double get() = TileMath.yToLat(my)
    val lon: Double get() = TileMath.xToLon(mx)

    /** Screen pixels per normalized unit. */
    fun scale(tilePx: Float): Double = (1L shl zoom).toDouble() * overzoom * tilePx

    /** Drag by (dx, dy) screen pixels: the map follows the finger 1:1. */
    fun panned(dx: Float, dy: Float, tilePx: Float): MapViewport {
        val s = scale(tilePx)
        return copy(mx = TileMath.wrapX(mx - dx / s), my = (my - dy / s).coerceIn(0.0, 1.0))
    }

    /**
     * Apply [steps] zoom steps (or overzoom steps in overzoom mode), keeping the point at screen
     * offset (fx, fy) from the centre fixed. Zoom is clamped to [minZoom, maxZoom]; overzoom to {1,2,4,8}.
     */
    fun stepped(
        steps: Int,
        overzoomMode: Boolean,
        tilePx: Float,
        minZoom: Int,
        maxZoom: Int,
        fx: Float = 0f,
        fy: Float = 0f,
    ): MapViewport {
        val next = if (overzoomMode) {
            val i = (OVERZOOMS.indexOf(overzoom).coerceAtLeast(0) + steps).coerceIn(0, OVERZOOMS.lastIndex)
            copy(overzoom = OVERZOOMS[i])
        } else {
            copy(zoom = (zoom + steps).coerceIn(minZoom, maxZoom))
        }
        if (next == this) return this
        val s1 = scale(tilePx)
        val px = mx + fx / s1
        val py = my + fy / s1
        val s2 = next.scale(tilePx)
        return next.copy(mx = TileMath.wrapX(px - fx / s2), my = (py - fy / s2).coerceIn(0.0, 1.0))
    }

    fun canStep(steps: Int, overzoomMode: Boolean, minZoom: Int, maxZoom: Int): Boolean =
        if (overzoomMode) {
            val i = OVERZOOMS.indexOf(overzoom) + steps
            i in OVERZOOMS.indices
        } else {
            zoom + steps in minZoom..maxZoom
        }

    fun clampZoom(minZoom: Int, maxZoom: Int): MapViewport = copy(zoom = zoom.coerceIn(minZoom, maxZoom))

    /** Tiles of level [zoom] intersecting a [width]×[height] px screen, nearest to the centre first. */
    fun visibleTiles(width: Int, height: Int, tilePx: Float): List<TileOnScreen> {
        if (width <= 0 || height <= 0) return emptyList()
        val n = 1 shl zoom
        val s = scale(tilePx)
        val halfW = width / 2.0
        val halfH = height / 2.0
        val x0 = floor((mx - halfW / s) * n).toInt()
        val x1 = floor((mx + halfW / s) * n).toInt()
        val y0 = floor((my - halfH / s) * n).toInt().coerceAtLeast(0)
        val y1 = floor((my + halfH / s) * n).toInt().coerceAtMost(n - 1)
        val out = ArrayList<TileOnScreen>()
        for (ty in y0..y1) {
            for (tx in x0..x1) {
                val l = ((tx.toDouble() / n - mx) * s + halfW)
                val t = ((ty.toDouble() / n - my) * s + halfH)
                val r = (((tx + 1).toDouble() / n - mx) * s + halfW)
                val b = (((ty + 1).toDouble() / n - my) * s + halfH)
                out += TileOnScreen(
                    z = zoom,
                    x = ((tx % n) + n) % n,
                    y = ty,
                    left = l.roundToInt(), top = t.roundToInt(), right = r.roundToInt(), bottom = b.roundToInt(),
                )
            }
        }
        val cx = width / 2.0
        val cy = height / 2.0
        out.sortBy { val dx = (it.left + it.right) / 2.0 - cx; val dy = (it.top + it.bottom) / 2.0 - cy; dx * dx + dy * dy }
        return out
    }

    fun toViewport(sourceId: String) = Viewport(lat = lat, lon = lon, zoom = zoom, overzoom = overzoom, sourceId = sourceId)

    companion object {
        val OVERZOOMS = Viewport.ALLOWED_OVERZOOM

        fun from(v: Viewport) = MapViewport(
            mx = TileMath.wrapX(TileMath.lonToX(v.lon)),
            my = TileMath.latToY(v.lat),
            zoom = v.zoom,
            overzoom = v.overzoom.takeIf { it in OVERZOOMS } ?: 1,
        )
    }
}
