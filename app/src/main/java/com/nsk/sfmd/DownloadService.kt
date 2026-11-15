package com.nsk.sfmd

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import com.nsk.sfmd.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@OptIn(kotlinx.coroutines.FlowPreview::class)
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = application as SfmdApp
    private var wakeLock: PowerManager.WakeLock? = null
    private var pendingStarts = 0
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("downloads", "Downloads", NotificationManager.IMPORTANCE_LOW))
        if (Build.VERSION.SDK_INT >= 29) startForeground(100, notification(null), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(100, notification(null))
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SFMD:downloads").apply { acquire(6 * 60 * 60 * 1000L) }
        scope.launch {
            combine(app.downloads.tasks, app.updates.state) { tasks, update -> tasks to update }.sample(700).collect { (tasks, update) ->
                val notice = if (update.phase == com.nsk.sfmd.updates.UpdatePhase.DOWNLOADING) NotificationCompat.Builder(this@DownloadService, "downloads")
                    .setSmallIcon(R.drawable.ic_sfmd).setContentTitle("Atualização do SFMD").setContentText("Baixando APK verificado pelo GitHub…")
                    .setOngoing(true).setOnlyAlertOnce(true).setProgress(100, (update.progress * 100).toInt(), false).build()
                    else notification(tasks.firstOrNull { it.state.active })
                getSystemService(NotificationManager::class.java).notify(100, notice)
            }
        }
    }
    private fun notification(task: DownloadTask?): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, "downloads").setSmallIcon(com.nsk.sfmd.R.drawable.ic_sfmd).setContentTitle(task?.title ?: "SFMD")
            .setContentText(task?.let { "${it.state.label} · ${(it.progress * 100).toInt()}%" } ?: "Preparando downloads…")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .apply { if (task != null) setProgress(100, (task.progress * 100).toInt(), task.total == 0L || task.state != TaskState.DOWNLOADING) }.build()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        pendingStarts++
        scope.launch { try { if (intent?.action == "SFMD_UPDATE") app.updates.awaitDownload() else app.downloads.runQueue() } finally { pendingStarts--; if (pendingStarts == 0) stopSelf() } }
        return START_NOT_STICKY
    }
    override fun onTimeout(startId: Int, fgsType: Int) {
        app.updates.cancel()
        scope.launch { app.downloads.interruptAll("O Android encerrou o tempo de transferência. Abra o app para tentar novamente."); stopSelf() }
    }
    override fun onDestroy() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        if (pendingStarts > 0) app.scope.launch { app.updates.cancel(); app.downloads.interruptAll("O serviço de transferência foi encerrado. Tente novamente pelo histórico.") }
        scope.cancel(); super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
}
