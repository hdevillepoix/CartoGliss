package app.cartogliss.tracks

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.cartogliss.MainActivity
import app.cartogliss.R
import app.cartogliss.appContainer
import app.cartogliss.geo.Units
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground service (type location) that keeps GPS on and the process alive while a track is
 * recorded, with a persistent notification. Started while the app is in the foreground, so the
 * "while in use" location permission is enough.
 */
class RecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var holdingLocation = false
    private var notifying = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = appContainer
        when (intent?.action) {
            ACTION_FINISH -> {
                container.appScope.launch { runCatching { container.recorder.finish(applicationContext) } }
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                container.appScope.launch { container.recorder.pause(applicationContext) }
                return START_NOT_STICKY
            }
        }
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification("Starting…"), type)
        } catch (e: Exception) {
            // e.g. location permission revoked: can't record.
            stopSelf()
            return START_NOT_STICKY
        }
        if (!holdingLocation) {
            container.location.acquire()
            holdingLocation = true
        }
        scope.launch { if (!container.recorder.attach()) stopSelf() }
        if (!notifying) notifying = true else return START_STICKY
        scope.launch {
            combine(container.recorder.state, container.settings.settings) { s, set -> s to set }.collect { (s, set) ->
                if (s != null && !s.paused) {
                    val text = "${Units.distance(s.distanceM, set.distanceUnit)} · ${TrackStats.duration(System.currentTimeMillis() - s.startedAtMs)}"
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
                }
            }
        }
        // If Android kills and restarts us, attach() resumes the journaled track in a new segment
        // (the points still buffered in memory at that moment are lost).
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        if (holdingLocation) {
            appContainer.location.release()
            holdingLocation = false
        }
        appContainer.recorder.detach()
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Track recording", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val pause = PendingIntent.getService(
            this, 2, Intent(this, RecordingService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val finish = PendingIntent.getService(
            this, 1, Intent(this, RecordingService::class.java).setAction(ACTION_FINISH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_record)
            .setContentTitle("Recording track")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Pause", pause)
            .addAction(0, "Finish and save", finish)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val ACTION_PAUSE = "app.cartogliss.PAUSE_RECORDING"
        const val ACTION_FINISH = "app.cartogliss.FINISH_RECORDING"
        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 1
    }
}
