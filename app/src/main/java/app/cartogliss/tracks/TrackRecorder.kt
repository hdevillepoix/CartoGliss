package app.cartogliss.tracks

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.cartogliss.location.Fix
import app.cartogliss.location.LocationTracker
import app.cartogliss.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class RecordingState(
    val trackId: Long,
    val startedAtMs: Long,
    val distanceM: Double,
    /** Kept points, for drawing the live track. */
    val points: List<TrackPoint>,
)

/**
 * Track recording. [start] creates a journal entry and starts [RecordingService], which keeps the
 * process alive and GPS on (foreground service). Fixes are filtered (DESIGN.md: interval, distance,
 * accuracy) and journaled; [stop] exports the GPX file.
 */
class TrackRecorder(
    private val journal: TrackJournal,
    private val location: LocationTracker,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    val tracksDir: File,
    /** Called after a GPX file is written (from the app or from the notification's Stop). */
    private val onSaved: suspend (File) -> Unit = {},
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<RecordingState?>(null)
    val state: StateFlow<RecordingState?> = _state

    private var collector: Job? = null
    private var lastFixNanos = 0L
    private var currentSeg = 0

    /** True when the journal holds an unfinished track (e.g. the app was killed while recording). */
    suspend fun hasOpenTrack(): Boolean = withContext(Dispatchers.IO) { journal.openTrack() != null }

    fun start(context: Context) {
        scope.launch {
            mutex.withLock {
                if (_state.value == null && withContext(Dispatchers.IO) { journal.openTrack() } == null) {
                    val now = System.currentTimeMillis()
                    val id = withContext(Dispatchers.IO) { journal.start(now) }
                    _state.value = RecordingState(id, now, 0.0, emptyList())
                }
            }
            ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java))
        }
    }

    /** Resume an interrupted recording (from the app, in the foreground). */
    fun resume(context: Context) {
        ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java))
    }

    /** Called by the service once it is in the foreground. Loads an interrupted track if needed. */
    internal fun attach() {
        if (collector?.isActive == true) return
        collector = scope.launch {
            mutex.withLock {
                if (_state.value == null) {
                    val open = withContext(Dispatchers.IO) { journal.openTrack() } ?: return@launch
                    val pts = withContext(Dispatchers.IO) { journal.points(open.id) }
                    // A resumed recording continues in a new GPX segment.
                    val nextSeg = (pts.maxOfOrNull { it.seg } ?: -1) + 1
                    currentSeg = nextSeg
                    _state.value = RecordingState(open.id, open.startedAtMs, segmentedDistance(pts), pts)
                }
            }
            location.fix.filterNotNull().collect(::onFix)
        }
    }

    internal fun detach() {
        collector?.cancel()
        collector = null
    }

    private suspend fun onFix(fix: Fix) {
        if (fix.elapsedNanos == lastFixNanos) return // the same fix re-emitted
        lastFixNanos = fix.elapsedNanos
        // The location flow replays its last value, which may be old: only record live fixes.
        if (fix.ageMs() > Fix.RECENT_MS) return
        val filter = TrackFilter(settings.settings.first().track)
        mutex.withLock {
            val s = _state.value ?: return
            val p = TrackPoint(fix.timeMs, fix.lat, fix.lon, fix.altitudeM, fix.accuracyM, currentSeg)
            val last = s.points.lastOrNull()
            // A new segment always takes its first point, without the distance/time test against the old segment.
            if (!filter.accept(last?.takeIf { it.seg == currentSeg }, p)) return
            withContext(Dispatchers.IO) { journal.add(s.trackId, p) }
            val added = if (last != null && last.seg == p.seg) TrackStats.distance(listOf(last, p)) else 0.0
            _state.value = s.copy(distanceM = s.distanceM + added, points = s.points + p)
        }
    }

    /** Stop recording and write the GPX file. Returns it, or null if nothing was recorded. */
    suspend fun stop(context: Context): File? {
        context.stopService(Intent(context, RecordingService::class.java))
        detach()
        return mutex.withLock {
            val s = _state.value
            val open = withContext(Dispatchers.IO) { journal.openTrack() }
            val id = s?.trackId ?: open?.id ?: return@withLock null
            val started = s?.startedAtMs ?: open?.startedAtMs ?: System.currentTimeMillis()
            val file = withContext(Dispatchers.IO) {
                val pts = journal.points(id)
                val f = if (pts.isEmpty()) null else export(started, pts)
                journal.delete(id)
                f
            }
            _state.value = null
            currentSeg = 0
            file
        }?.also { onSaved(it) }
    }

    private fun export(startedAtMs: Long, pts: List<TrackPoint>): File {
        tracksDir.mkdirs()
        val local = Instant.ofEpochMilli(startedAtMs).atZone(ZoneId.systemDefault())
        val base = FILE_TIME.format(local)
        var file = File(tracksDir, "$base.gpx")
        var i = 2
        while (file.exists()) file = File(tracksDir, "$base-${i++}.gpx")
        val tmp = File(file.path + ".tmp")
        tmp.bufferedWriter().use { GpxWriter.write("Track ${NAME_TIME.format(local)}", pts, it) }
        check(tmp.renameTo(file)) { "Could not save $file" }
        return file
    }

    private fun segmentedDistance(pts: List<TrackPoint>) = pts.groupBy { it.seg }.values.sumOf(TrackStats::distance)

    companion object {
        private val FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")
        private val NAME_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
