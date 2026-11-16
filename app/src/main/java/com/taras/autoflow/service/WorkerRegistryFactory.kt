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
        val pythonWorker = PythonComputeWorker(appContext)
        val httpWorker = NetworkHttpWorker(appContext)
        val webViewWorker = HeadlessWebViewWorker(appContext)
        val shizukuWorker = ShizukuShellWorker()
        val computeWorker = KotlinComputeWorker(appContext)
        val geminiWorker = GeminiAiWorker(appContext)
        val uiWorker = AccessibilityServiceWorker()
        val delayWorker = DelayWorker()

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

            // Alias Kompatibilitas
            register("PYTHON", pythonWorker)
            register("HTTP", httpWorker)
            register("OFFSCREEN_WEB", webViewWorker)
            register("SHELL", shizukuWorker)
            register("COMPUTE", computeWorker)
            register("DEDUPLICATE", computeWorker)
        }
    }
}
