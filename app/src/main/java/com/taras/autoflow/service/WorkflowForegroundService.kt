package com.taras.autoflow.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.taras.autoflow.MainActivity
import com.taras.autoflow.engine.AutoFlowRuntime
import kotlinx.coroutines.*

class WorkflowForegroundService : Service() {
    companion object {
        const val ACTION_STOP = "com.taras.autoflow.ACTION_STOP"
        private const val NOTIFICATION_ID = 1001
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val channelId = "autoflow_engine_channel"
    private var isObserving = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            AutoFlowRuntime.triggerEmergencyStop()
            PlatformAutomation.onWorkflowFinished()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification("AutoFlow Engine Aktif", "Menunggu trigger alur kerja...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (!isObserving) {
            isObserving = true
            serviceScope.launch {
                AutoFlowRuntime.runningNodeId.collect { activeNode ->
                    if (activeNode != null) {
                        PlatformAutomation.onNodeRunning(this@WorkflowForegroundService, activeNode)
                    } else {
                        PlatformAutomation.onWorkflowFinished()
                    }
                    val content = if (activeNode != null) "Memproses Node: $activeNode" else "Alur kerja siaga"
                    val updatedNotif = buildNotification("AutoFlow Orchestrator", content)
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.notify(NOTIFICATION_ID, updatedNotif)
                }
            }
        }
        return START_STICKY
    }

    private fun buildNotification(title: String, content: String): Notification {
        val mainIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, WorkflowForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(mainIntent)
            .addAction(android.R.drawable.ic_media_pause, "STOP", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "AutoFlow Active Engine",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        PlatformAutomation.onWorkflowFinished()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
