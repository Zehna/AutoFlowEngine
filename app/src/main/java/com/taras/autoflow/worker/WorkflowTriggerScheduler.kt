package com.taras.autoflow.worker

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object WorkflowTriggerScheduler {
    fun schedulePeriodicWorkflow(context: Context, workflowId: String, intervalMinutes: Long) {
        val workRequest = PeriodicWorkRequestBuilder<WorkflowScheduleWorker>(
            intervalMinutes, TimeUnit.MINUTES
        ).setInputData(workDataOf("WORKFLOW_ID" to workflowId)).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "schedule_$workflowId",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
}
