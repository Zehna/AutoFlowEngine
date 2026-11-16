package com.taras.autoflow.worker

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object WorkflowTriggerScheduler {
    private fun uniqueWorkName(workflowId: String) = "schedule_$workflowId"

    fun schedulePeriodicWorkflow(context: Context, workflowId: String, intervalMinutes: Long) {
        val safeInterval = intervalMinutes.coerceAtLeast(15L)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<WorkflowScheduleWorker>(
            safeInterval, TimeUnit.MINUTES
        )
            .setInputData(workDataOf("WORKFLOW_ID" to workflowId))
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            uniqueWorkName(workflowId),
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    fun cancelScheduledWorkflow(context: Context, workflowId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(workflowId))
    }

    fun runOnceInBackground(context: Context, workflowId: String) {
        val workRequest = OneTimeWorkRequestBuilder<WorkflowScheduleWorker>()
            .setInputData(workDataOf("WORKFLOW_ID" to workflowId))
            .build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
