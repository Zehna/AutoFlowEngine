package com.taras.autoflow.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.taras.autoflow.MainActivity
import com.taras.autoflow.engine.AutoFlowRuntime
import kotlinx.coroutines.*

class WorkflowForegroundService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private val channelId = "autoflow_engine_channel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("AutoFlow Engine Aktif", "Menunggu trigger alur kerja...")
        startForeground(1001, notification)

        serviceScope.launch {
            AutoFlowRuntime.runningNodeId.collect { activeNode ->
                val content = if (activeNode != null) "Memproses Node: $activeNode" else "Alur kerja siaga"
                val updatedNotif = buildNotification("AutoFlow Orchestrator", content)
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(1001, updatedNotif)
            }
        }
        return START_STICKY
    }

    private fun buildNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
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
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
