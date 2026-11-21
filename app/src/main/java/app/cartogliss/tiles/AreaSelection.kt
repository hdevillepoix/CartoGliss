package app.cartogliss.tiles

import kotlinx.serialization.Serializable

@Serializable
data class Cell(val z: Int, val x: Int, val y: Int) {
    fun parent(): Cell = Cell(z - 1, x shr 1, y shr 1)

    /** True when [other] is this cell or lies inside it. */
    fun contains(other: Cell): Boolean =
        other.z >= z && (other.x shr (other.z - z)) == x && (other.y shr (other.z - z)) == y

    fun children(): List<Cell> = listOf(
        Cell(z + 1, 2 * x, 2 * y), Cell(z + 1, 2 * x + 1, 2 * y),
        Cell(z + 1, 2 * x, 2 * y + 1), Cell(z + 1, 2 * x + 1, 2 * y + 1),
    )
}

/**
 * The area painted on the download screen, as tile-grid cells of mixed levels (a quadtree cover).
 * Invariant: no cell contains another, so tile counts at any level are exact.
 * Independent of the tile source: the same area can be downloaded from any source.
 */
class AreaSelection(initial: Collection<Cell> = emptyList()) {
    private val cells = HashSet<Cell>()

    init {
        initial.forEach(::add)
    }

    val size: Int get() = cells.size
    fun isEmpty() = cells.isEmpty()
    fun cells(): Set<Cell> = cells.toSet()

    fun clear() = cells.clear()

    /** True when [c] is entirely selected. */
    fun covers(c: Cell): Boolean {
        var a: Cell? = c
        while (a != null) {
            if (a in cells) return true
            a = if (a.z > 0) a.parent() else null
        }
        return false
    }

    /** Select cell [c]. Returns false if it was already selected. */
    fun add(c: Cell): Boolean {
        if (covers(c)) return false
        cells.removeAll { c.contains(it) }
        cells += c
        return true
    }

    /** Deselect cell [c], splitting a larger selected cell around it if needed. Returns false if nothing changed. */
    fun remove(c: Cell): Boolean {
        val removedInside = cells.removeAll { c.contains(it) }
        // Find a selected ancestor and split it down to c's level, keeping everything except c.
        var anc: Cell? = if (c.z > 0) c.parent() else null
        while (anc != null && anc !in cells) anc = if (anc.z > 0) anc.parent() else null
        if (anc == null) return removedInside
        cells -= anc
        var cur: Cell = anc
        while (cur.z < c.z) {
            val next = cur.children().first { it.contains(c) }
            cur.children().filter { it != next }.forEach { cells += it }
            cur = next
        }
        return true
    }

    /** Exact number of level-[z] tiles touching the selection. */
    fun tileCount(z: Int): Long {
        var n = 0L
        val parents = HashSet<Long>()
        for (c in cells) {
            if (c.z <= z) n += 1L shl (2 * (z - c.z))
            else parents += key(c.x shr (c.z - z), c.y shr (c.z - z))
        }
        return n + parents.size
    }

    /** The level-[z] tiles touching the selection, each once. */
    fun tiles(z: Int): Sequence<Pair<Int, Int>> = sequence {
        val parents = LinkedHashSet<Long>()
        for (c in cells.toList()) {
            if (c.z <= z) {
                val d = z - c.z
                val x0 = c.x shl d
                val y0 = c.y shl d
                for (y in y0 until y0 + (1 shl d)) for (x in x0 until x0 + (1 shl d)) yield(x to y)
            } else {
                parents += key(c.x shr (c.z - z), c.y shr (c.z - z))
            }
        }
        for (k in parents) yield((k ushr 32).toInt() to (k and 0xFFFFFFFFL).toInt())
    }

    private fun key(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)
}
