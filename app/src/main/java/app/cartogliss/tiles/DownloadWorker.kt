package app.cartogliss.tiles

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.cartogliss.R
import app.cartogliss.appContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@Serializable
data class DownloadJob(val sourceId: String, val sourceName: String, val levels: List<Int>, val cells: List<Cell>)

/**
 * Downloads every tile of the selected levels that touches the painted area, skipping tiles already
 * cached. Runs as a foreground WorkManager job (notification with progress and Cancel); jobs queue up.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        val jobFile = File(inputData.getString(KEY_JOB) ?: return Result.failure())
        val job = runCatching {
            SourceCatalog.json.decodeFromString(DownloadJob.serializer(), jobFile.readText())
        }.getOrElse { return Result.failure() }
        val source = container.sources.byId(job.sourceId) ?: return Result.failure()
        if (!source.bulkDownloadAllowed) return Result.failure(workDataOf(KEY_ERROR to "Bulk download is not allowed for ${source.name}"))

        val selection = AreaSelection(job.cells)
        val levels = job.levels.filter { it in source.minZoom..source.maxZoom }.sorted()
        val total = levels.sumOf { selection.tileCount(it) }.toInt()
        val done = AtomicInteger(0)
        val fetched = AtomicInteger(0)
        val failed = AtomicInteger(0)

        setForeground(foregroundInfo(job.sourceName, 0, total))

        val sem = Semaphore(TileLoader.NETWORK_CONCURRENCY)
        try {
            coroutineScope {
                for (z in levels) {
                    for ((x, y) in selection.tiles(z)) {
                        ensureActive()
                        if (!source.covers(z, x, y) || withContext(Dispatchers.IO) { container.tileStore.has(source.id, z, x, y) }) {
                            tick(done, total, fetched, failed, job.sourceName)
                            continue
                        }
                        sem.acquire()
                        launch {
                            try {
                                if (fetchOne(source, z, x, y)) fetched.incrementAndGet() else failed.incrementAndGet()
                            } finally {
                                sem.release()
                                tick(done, total, fetched, failed, job.sourceName)
                            }
                        }
                    }
                }
            }
        } finally {
            jobFile.delete()
        }
        return Result.success(progressData(done.get(), total, fetched.get(), failed.get()))
    }

    /** One tile, with a single polite retry on 429/503. */
    private suspend fun fetchOne(source: TileSource, z: Int, x: Int, y: Int): Boolean {
        val container = applicationContext.appContainer
        repeat(2) { attempt ->
            when (val r = container.fetcher.fetch(source, z, x, y)) {
                is FetchResult.Ok -> {
                    withContext(Dispatchers.IO) { container.tileStore.put(source.id, z, x, y, r.bytes) }
                    return true
                }
                is FetchResult.Http -> if ((r.code == 429 || r.code == 503) && attempt == 0) delay(10_000) else return false
                else -> return false
            }
        }
        return false
    }

    private var lastReport = 0L

    private suspend fun tick(done: AtomicInteger, total: Int, fetched: AtomicInteger, failed: AtomicInteger, name: String) {
        val d = done.incrementAndGet()
        val now = System.currentTimeMillis()
        if (d == total || now - lastReport > 700) {
            lastReport = now
            setProgress(progressData(d, total, fetched.get(), failed.get()))
            notificationManager().notify(NOTIFICATION_ID, notification(name, d, total))
        }
    }

    private fun progressData(done: Int, total: Int, fetched: Int, failed: Int): Data =
        workDataOf(KEY_DONE to done, KEY_TOTAL to total, KEY_FETCHED to fetched, KEY_FAILED to failed)

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo("tiles", 0, 0)

    private fun foregroundInfo(name: String, done: Int, total: Int): ForegroundInfo {
        createChannel()
        val n = notification(name, done, total)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, n)
        }
    }

    private fun notification(name: String, done: Int, total: Int) =
        NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Downloading $name")
            .setContentText(if (total > 0) "$done / $total tiles" else "Preparing…")
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Cancel", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id))
            .build()

    private fun createChannel() {
        val nm = notificationManager()
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Map downloads", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notificationManager() = applicationContext.getSystemService(NotificationManager::class.java)

    companion object {
        const val UNIQUE_NAME = "tile-download"
        const val KEY_JOB = "job"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_FETCHED = "fetched"
        const val KEY_FAILED = "failed"
        const val KEY_ERROR = "error"
        private const val CHANNEL = "downloads"
        private const val NOTIFICATION_ID = 2

        /** Queue a download (jobs run one after the other). */
        fun enqueue(context: Context, job: DownloadJob, wifiOnly: Boolean) {
            val dir = File(context.filesDir, "downloads").apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.json")
            file.writeText(SourceCatalog.json.encodeToString(DownloadJob.serializer(), job))
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(KEY_JOB to file.path))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
                .addTag(job.sourceName)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
