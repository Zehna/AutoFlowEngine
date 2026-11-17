package com.taras.autoflow.service

import android.content.Context
import com.taras.autoflow.workers.AccessibilityServiceWorker
import com.taras.autoflow.workers.DelayWorker
import com.taras.autoflow.workers.GeminiAiWorker
import com.taras.autoflow.workers.HeadlessWebViewWorker
import com.taras.autoflow.workers.N8nWorker
import com.taras.autoflow.workers.NetworkHttpWorker
import com.taras.autoflow.workers.PythonComputeWorker
import com.taras.autoflow.workers.ShizukuShellWorker

object WorkerRegistryFactory {
    fun create(context: Context): WorkerRegistry {
        val appContext = context.applicationContext
        val pythonWorker = PythonComputeWorker(appContext)
        val httpWorker = NetworkHttpWorker(appContext)
        val webViewWorker = HeadlessWebViewWorker(appContext)
        val shizukuWorker = ShizukuShellWorker()
        val computeWorker = KotlinComputeWorker(appContext)
        val geminiWorker = GeminiAiWorker(appContext)
        val uiWorker = AccessibilityServiceWorker()
        val delayWorker = DelayWorker()
        val n8nWorker = N8nWorker(appContext)

        return WorkerRegistry().apply {
            // Nama Worker Standar v2
            register("PYTHON_COMPUTE", pythonWorker)
            register("NETWORK_HTTP", httpWorker)
            register("WEBVIEW_HEADLESS", webViewWorker)
            register("SHIZUKU_SHELL", shizukuWorker)
            register("COMPUTE_KOTLIN", computeWorker)
            register("GEMINI_AI", geminiWorker)
            register("UI_ACTION", uiWorker)
            register("DELAY", delayWorker)
            register("N8N_BRIDGE", n8nWorker)

            // Alias Kompatibilitas
            register("PYTHON", pythonWorker)
            register("HTTP", httpWorker)
            register("OFFSCREEN_WEB", webViewWorker)
            register("SHELL", shizukuWorker)
            register("COMPUTE", computeWorker)
            register("DEDUPLICATE", computeWorker)
            register("N8N", n8nWorker)
        }
    }
}
