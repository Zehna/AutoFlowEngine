package com.taras.autoflow.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.taras.autoflow.MainActivity
import com.taras.autoflow.engine.AutoFlowRuntime
import com.taras.autoflow.engine.OrchestratorState
import com.taras.autoflow.engine.WorkflowDefinition
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json

class WorkflowForegroundService : Service() {
    companion object {
        const val ACTION_START = "com.taras.autoflow.ACTION_START"
        const val ACTION_PAUSE = "com.taras.autoflow.ACTION_PAUSE"
        const val ACTION_RESUME = "com.taras.autoflow.ACTION_RESUME"
        const val ACTION_STOP = "com.taras.autoflow.ACTION_STOP"
        const val EXTRA_WORKFLOW_JSON = "EXTRA_WORKFLOW_JSON"
        private const val NOTIFICATION_ID = 1001
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val channelId = "autoflow_engine_channel"
    private var isObserving = false
    private var executionJob: Job? = null
    private val json = Json { ignoreUnknownKeys = true }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val orchestrator = AutoFlowRuntime.getOrchestrator(applicationContext)

        when (intent?.action) {
            ACTION_PAUSE -> {
                orchestrator.pause()
                return START_NOT_STICKY
            }
            ACTION_RESUME -> {
                orchestrator.resume()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                orchestrator.stop()
                executionJob?.cancel()
                executionJob = null
                PlatformAutomation.onWorkflowFinished()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val initialNotif = buildNotification("AutoFlow Engine Aktif", "Menyiapkan eksekusi workflow...", isPaused = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, initialNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, initialNotif)
        }

        if (!isObserving) {
            isObserving = true
            serviceScope.launch {
                val manager = getSystemService(NotificationManager::class.java)
                orchestrator.state.collect { state ->
                    when (state) {
                        is OrchestratorState.Running -> {
                            val label = "${state.currentNodeName} (${state.stepIndex}/${state.totalSteps})"
                            PlatformAutomation.onNodeRunning(this@WorkflowForegroundService, label)
                            val updated = buildNotification(
                                "AutoFlow: ${state.workflowName} (#${state.cycle})",
                                "Menjalankan: $label",
                                isPaused = false
                            )
                            manager.notify(NOTIFICATION_ID, updated)
                        }
                        is OrchestratorState.Paused -> {
                            PlatformAutomation.onNodeRunning(this@WorkflowForegroundService, "DIJEDA")
                            val updated = buildNotification(
                                "AutoFlow Dijeda: ${state.workflowName}",
                                "Node tertunda: ${state.currentNodeName}",
                                isPaused = true
                            )
                            manager.notify(NOTIFICATION_ID, updated)
                        }
                        is OrchestratorState.Completed,
                        is OrchestratorState.Failed -> {
                            PlatformAutomation.onWorkflowFinished()
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                        OrchestratorState.Idle -> {
                            PlatformAutomation.onWorkflowFinished()
                        }
                    }
                }
            }
        }

        val workflowJson = intent?.getStringExtra(EXTRA_WORKFLOW_JSON)
        if (!workflowJson.isNullOrBlank()) {
            val definition = runCatching {
                json.decodeFromString(WorkflowDefinition.serializer(), workflowJson)
            }.getOrNull()

            if (definition != null) {
                executionJob?.cancel()
                executionJob = serviceScope.launch(Dispatchers.Default) {
                    orchestrator.run(definition)
                }
            }
        }

        return START_STICKY
    }

    private fun buildNotification(title: String, content: String, isPaused: Boolean): Notification {
        val mainIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val pauseOrResumeIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, WorkflowForegroundService::class.java).apply {
                action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, WorkflowForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(mainIntent)
            .addAction(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (isPaused) "Resume" else "Pause",
                pauseOrResumeIntent
            )
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
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
