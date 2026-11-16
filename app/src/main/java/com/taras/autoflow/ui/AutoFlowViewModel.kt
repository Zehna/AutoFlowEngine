package com.taras.autoflow.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.taras.autoflow.data.AutoFlowDatabase
import com.taras.autoflow.data.CsvVaultExporter
import com.taras.autoflow.engine.AutoFlowRuntime
import com.taras.autoflow.engine.WorkflowDefinition
import com.taras.autoflow.engine.WorkflowOrchestrator
import com.taras.autoflow.service.KotlinComputeWorker
import com.taras.autoflow.service.WorkerRegistry
import com.taras.autoflow.service.WorkflowForegroundService
import com.taras.autoflow.workers.AccessibilityServiceWorker
import com.taras.autoflow.workers.GeminiAiWorker
import com.taras.autoflow.workers.HeadlessWebViewWorker
import com.taras.autoflow.workers.NetworkHttpWorker
import com.taras.autoflow.workers.PythonComputeWorker
import com.taras.autoflow.workers.ShizukuShellWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class AutoFlowViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AutoFlowDatabase.getInstance(application)
    private val registry = WorkerRegistry().apply {
        register("PYTHON", PythonComputeWorker(application))
        register("HTTP", NetworkHttpWorker())
        register("OFFSCREEN_WEB", HeadlessWebViewWorker(application))
        register("SHELL", ShizukuShellWorker())
        register("GEMINI_AI", GeminiAiWorker())
        register("UI_ACTION", AccessibilityServiceWorker())
        register("COMPUTE", KotlinComputeWorker(application))
        register("DEDUPLICATE", KotlinComputeWorker(application))
    }
    private val orchestrator = WorkflowOrchestrator(application, registry)

    val logs = db.logDao().observeAllLogs().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    private val _workflows = MutableStateFlow<List<WorkflowDefinition>>(emptyList())
    val workflows = _workflows.asStateFlow()

    fun startWorkflow(definition: WorkflowDefinition) {
        val app = getApplication<Application>()
        val serviceIntent = Intent(app, WorkflowForegroundService::class.java)
        ContextCompat.startForegroundService(app, serviceIntent)

        viewModelScope.launch(Dispatchers.Default) {
            orchestrator.run(definition)
        }
    }

    fun stopExecution() {
        AutoFlowRuntime.triggerEmergencyStop()
    }

    fun exportVaultCsv(onExportReady: (File) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val snapshot = db.logDao().getFullLogsSnapshot()
            val file = CsvVaultExporter.exportToCsv(getApplication(), snapshot)
            viewModelScope.launch(Dispatchers.Main) {
                onExportReady(file)
            }
        }
    }

    fun clearVaultLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            db.logDao().purgeAllLogs()
        }
    }
}
