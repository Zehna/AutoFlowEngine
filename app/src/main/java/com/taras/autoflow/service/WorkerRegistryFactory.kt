package com.taras.autoflow.service

import android.content.Context
import com.taras.autoflow.workers.AccessibilityServiceWorker
import com.taras.autoflow.workers.DelayWorker
import com.taras.autoflow.workers.GeminiAiWorker
import com.taras.autoflow.workers.HeadlessWebViewWorker
import com.taras.autoflow.workers.NetworkHttpWorker
import com.taras.autoflow.workers.PythonComputeWorker
import com.taras.autoflow.workers.ShizukuShellWorker

object WorkerRegistryFactory {
    fun create(context: Context): WorkerRegistry {
        val appContext = context.applicationContext
        val computeWorker = KotlinComputeWorker(appContext)
        return WorkerRegistry().apply {
            register("PYTHON", PythonComputeWorker(appContext))
            register("HTTP", NetworkHttpWorker(appContext))
            register("OFFSCREEN_WEB", HeadlessWebViewWorker(appContext))
            register("SHELL", ShizukuShellWorker())
            register("GEMINI_AI", GeminiAiWorker(appContext))
            register("UI_ACTION", AccessibilityServiceWorker())
            register("COMPUTE", computeWorker)
            register("DEDUPLICATE", computeWorker)
            register("DELAY", DelayWorker())
        }
    }
}
