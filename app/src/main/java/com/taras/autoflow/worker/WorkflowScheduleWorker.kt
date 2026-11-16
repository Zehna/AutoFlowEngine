package com.taras.autoflow.worker

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.taras.autoflow.data.AutoFlowDatabase
import com.taras.autoflow.engine.AutoFlowRuntime
import com.taras.autoflow.engine.WorkflowDefinition
import com.taras.autoflow.service.WorkflowForegroundService
import kotlinx.serialization.json.Json

class WorkflowScheduleWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val workflowId = inputData.getString("WORKFLOW_ID")
            ?: return Result.failure()

        val db = AutoFlowDatabase.getInstance(appContext)
        val entity = db.workflowDao().getWorkflowById(workflowId)
            ?: return Result.failure()

        val definition = runCatching { entity.toDefinition() }.getOrNull()
            ?: return Result.failure()

        AutoFlowRuntime.appendLog("⏰ Trigger terjadwal menjalankan '${definition.name}'")

        val startedAsFgs = runCatching {
            val serialized = Json.encodeToString(WorkflowDefinition.serializer(), definition)
            val intent = Intent(appContext, WorkflowForegroundService::class.java).apply {
                action = WorkflowForegroundService.ACTION_START
                putExtra(WorkflowForegroundService.EXTRA_WORKFLOW_JSON, serialized)
            }
            ContextCompat.startForegroundService(appContext, intent)
            true
        }.getOrDefault(false)

        if (startedAsFgs) {
            return Result.success()
        }

        // Fallback jika Android membatasi startForegroundService dari background: jalankan langsung di CoroutineWorker
        val orchestrator = AutoFlowRuntime.getOrchestrator(appContext)
        val success = orchestrator.run(definition)
        return if (success) Result.success() else Result.retry()
    }
}
