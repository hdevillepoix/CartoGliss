package app.cartogliss.tracks

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.cartogliss.location.Fix
import app.cartogliss.location.LocationTracker
import app.cartogliss.settings.SettingsRepository
import app.cartogliss.storage.Storage
import app.cartogliss.storage.uniqueName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class RecordingState(
    val trackId: Long,
    val startedAtMs: Long,
    val distanceM: Double,
    /** Kept points (written or still buffered), for drawing the live track. */
    val points: List<TrackPoint>,
    /** Paused: GPS released, the track stays open until it is continued or finished. */
    val paused: Boolean = false,
)

/**
 * Track recording (DESIGN.md "Track recording"). [start] opens a journal entry and starts
 * [RecordingService], which keeps the process alive and GPS on. Fixes are filtered (interval,
 * distance, accuracy), buffered in memory and written to the journal in batches. [pause] stops the
 * service but keeps the track open; [continueTrack] resumes it in a new GPX segment; [finish] writes
 * the GPX file into the tracks folder.
 */
class TrackRecorder(
    private val journal: TrackJournal,
    private val location: LocationTracker,
    private val settings: SettingsRepository,
    private val storage: Storage,
    private val scope: CoroutineScope,
    /** Called with the file name after a GPX file is written (from the app or the notification). */
    private val onSaved: suspend (String) -> Unit = {},
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<RecordingState?>(null)
    val state: StateFlow<RecordingState?> = _state

    private val bufferSize = settings.settings.map { it.track.bufferPoints }.stateIn(scope, SharingStarted.Eagerly, 10)
    private val buffer = PointBuffer({ bufferSize.value })

    private var collector: Job? = null
    private var lastFixNanos = 0L
    private var currentSeg = 0

    /**
     * Called when the app opens: loads an open track from the journal. A paused one stays paused; one
     * that was recording when the app was killed is resumed (in a new segment).
     */
    suspend fun restore(context: Context) {
        val resume = mutex.withLock {
            if (_state.value != null) return@withLock false
            val open = withContext(Dispatchers.IO) { journal.openTrack() } ?: return@withLock false
            load(open)
            !open.paused
        }
        if (resume) startService(context)
    }

    fun start(context: Context) {
        scope.launch {
            mutex.withLock {
                if (_state.value == null && withContext(Dispatchers.IO) { journal.openTrack() } == null) {
                    val now = System.currentTimeMillis()
                    val id = withContext(Dispatchers.IO) { journal.start(now) }
                    currentSeg = 0
                    _state.value = RecordingState(id, now, 0.0, emptyList())
                }
            }
            startService(context)
        }
    }

    /** Pause: write the buffer, release GPS, keep the track open. */
    suspend fun pause(context: Context) {
        context.stopService(Intent(context, RecordingService::class.java))
        detach()
        mutex.withLock {
            val s = _state.value ?: return
            withContext(Dispatchers.IO) { journal.setPaused(s.trackId, true) }
            _state.value = s.copy(paused = true)
        }
    }

    /** Continue a paused track, in a new GPX segment. */
    suspend fun continueTrack(context: Context) {
        mutex.withLock {
            val s = _state.value ?: return
            if (!s.paused) return
            withContext(Dispatchers.IO) { journal.setPaused(s.trackId, false) }
            currentSeg = nextSeg(s.points)
            _state.value = s.copy(paused = false)
        }
        startService(context)
    }

    private fun startService(context: Context) {
        ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java))
    }

    private fun nextSeg(pts: List<TrackPoint>) = (pts.maxOfOrNull { it.seg } ?: -1) + 1

    private suspend fun load(open: TrackJournal.OpenTrack) {
        val pts = withContext(Dispatchers.IO) { journal.points(open.id) }
        currentSeg = nextSeg(pts)
        _state.value = RecordingState(open.id, open.startedAtMs, segmentedDistance(pts), pts, open.paused)
    }

    /**
     * Called by the service once it is in the foreground. Returns false when there is nothing to
     * record (no open track, or it is paused) and the service should stop.
     */
    internal suspend fun attach(): Boolean {
        val ok = mutex.withLock {
            if (_state.value == null) {
                // Restarted by Android after the process was killed: continue in a new segment.
                val open = withContext(Dispatchers.IO) { journal.openTrack() } ?: return@withLock false
                load(open)
            }
            _state.value?.paused == false
        }
        if (!ok) return false
        if (collector?.isActive == true) return true
        collector = scope.launch {
            launch {
                // Write a batch that has waited too long even when no new point arrives.
                while (true) {
                    delay(30_000)
                    mutex.withLock { if (buffer.due(System.currentTimeMillis())) flushLocked() }
                }
            }
            location.fix.filterNotNull().collect(::onFix)
        }
        return true
    }

    /** Called when the service stops: writes what is buffered. */
    internal fun detach() {
        collector?.cancel()
        collector = null
        scope.launch { withContext(NonCancellable) { mutex.withLock { flushLocked() } } }
    }

    private suspend fun flushLocked() {
        val id = _state.value?.trackId ?: return
        val batch = buffer.drain()
        if (batch.isNotEmpty()) withContext(Dispatchers.IO) { journal.addAll(id, batch) }
    }

    private suspend fun onFix(fix: Fix) {
        if (fix.elapsedNanos == lastFixNanos) return // the same fix re-emitted
        lastFixNanos = fix.elapsedNanos
        // The location flow replays its last value, which may be old: only record live fixes.
        if (fix.ageMs() > Fix.RECENT_MS) return
        val filter = TrackFilter(settings.settings.first().track)
        mutex.withLock {
            val s = _state.value ?: return
            if (s.paused) return
            val p = TrackPoint(fix.timeMs, fix.lat, fix.lon, fix.altitudeM, fix.accuracyM, currentSeg)
            val last = s.points.lastOrNull()
            // A new segment always takes its first point, without the distance/time test against the old segment.
            if (!filter.accept(last?.takeIf { it.seg == currentSeg }, p)) return
            val added = if (last != null && last.seg == p.seg) TrackStats.distance(listOf(last, p)) else 0.0
            _state.value = s.copy(distanceM = s.distanceM + added, points = s.points + p)
            if (buffer.add(p, System.currentTimeMillis())) flushLocked()
        }
    }

    /** Finish the track and write the GPX file. Returns its name, or null if nothing was recorded. */
    suspend fun finish(context: Context): String? {
        context.stopService(Intent(context, RecordingService::class.java))
        collector?.cancel()
        collector = null
        return mutex.withLock {
            flushLocked()
            val s = _state.value
            val open = withContext(Dispatchers.IO) { journal.openTrack() }
            val id = s?.trackId ?: open?.id ?: return@withLock null
            val started = s?.startedAtMs ?: open?.startedAtMs ?: System.currentTimeMillis()
            val pts = withContext(Dispatchers.IO) { journal.points(id) }
            // If the file can't be written (e.g. folder access lost), the journal is kept for a retry.
            val name = if (pts.isEmpty()) null else export(started, pts)
            withContext(Dispatchers.IO) { journal.delete(id) }
            _state.value = null
            currentSeg = 0
            name
        }?.also { onSaved(it) }
    }

    /** Finish the paused track, then start a new one. */
    suspend fun finishAndStartNew(context: Context): String? = finish(context).also { start(context) }

    private suspend fun export(startedAtMs: Long, pts: List<TrackPoint>): String {
        val dir = storage.current().tracks
        val local = Instant.ofEpochMilli(startedAtMs).atZone(ZoneId.systemDefault())
        return withContext(Dispatchers.IO) {
            val name = dir.uniqueName(FILE_TIME.format(local), "gpx") { "-$it" }
            dir.write(name) { out -> out.bufferedWriter().let { w -> GpxWriter.write("Track ${NAME_TIME.format(local)}", pts, w); w.flush() } }
            name
        }
    }

    private fun segmentedDistance(pts: List<TrackPoint>) = pts.groupBy { it.seg }.values.sumOf(TrackStats::distance)

    companion object {
        private val FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")
        private val NAME_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
