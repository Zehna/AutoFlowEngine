package com.taras.autoflow.worker

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.taras.autoflow.service.WorkflowForegroundService

class WorkflowScheduleWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val intent = Intent(appContext, WorkflowForegroundService::class.java)
        ContextCompat.startForegroundService(appContext, intent)
        return Result.success()
    }
}
