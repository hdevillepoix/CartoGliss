package app.cartogliss.tracks

/**
 * Recorded points kept in memory until a batch is due for writing: when [maxPoints] are waiting, or
 * the oldest has waited [maxAgeMs]. The recorder also drains it on pause, finish and service stop.
 */
class PointBuffer(private val maxPoints: () -> Int, private val maxAgeMs: Long = MAX_AGE_MS) {
    private val pending = ArrayList<TrackPoint>()
    private var firstAtMs = 0L

    val size get() = pending.size

    /** Adds [p]; true when the buffer should now be written. */
    fun add(p: TrackPoint, nowMs: Long): Boolean {
        if (pending.isEmpty()) firstAtMs = nowMs
        pending += p
        return due(nowMs)
    }

    fun due(nowMs: Long): Boolean =
        pending.isNotEmpty() && (pending.size >= maxPoints().coerceAtLeast(1) || nowMs - firstAtMs >= maxAgeMs)

    fun drain(): List<TrackPoint> = pending.toList().also { pending.clear() }

    companion object {
        const val MAX_AGE_MS = 120_000L
    }
}
