package com.taras.autoflow.worker

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.taras.autoflow.data.AutoFlowDatabase
import com.taras.autoflow.engine.AutoFlowRuntime
import com.taras.autoflow.engine.WorkflowOrchestrator
import com.taras.autoflow.service.WorkerRegistryFactory
import com.taras.autoflow.service.WorkflowForegroundService

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

        runCatching {
            val intent = Intent(appContext, WorkflowForegroundService::class.java)
            ContextCompat.startForegroundService(appContext, intent)
        }

        AutoFlowRuntime.appendLog("⏰ Trigger terjadwal menjalankan '${definition.name}'")
        val orchestrator = WorkflowOrchestrator(appContext, WorkerRegistryFactory.create(appContext))
        val success = orchestrator.run(definition)
        return if (success) Result.success() else Result.retry()
    }
}
