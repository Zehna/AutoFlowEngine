package com.taras.autoflow.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.taras.autoflow.data.AutoFlowDatabase
import com.taras.autoflow.data.BuiltInWorkflowSeeds
import com.taras.autoflow.data.CsvVaultExporter
import com.taras.autoflow.data.WorkflowEntity
import com.taras.autoflow.data.WorkflowFileRepository
import com.taras.autoflow.data.WorkflowStateEntity
import com.taras.autoflow.engine.AutoFlowRuntime
import com.taras.autoflow.engine.OrchestratorState
import com.taras.autoflow.engine.WorkflowBundleExporter
import com.taras.autoflow.engine.WorkflowBundleImporter
import com.taras.autoflow.engine.WorkflowDefinition
import com.taras.autoflow.service.WorkflowForegroundService
import com.taras.autoflow.worker.WorkflowTriggerScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class AutoFlowViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AutoFlowDatabase.getInstance(application)
    private val fileRepo = WorkflowFileRepository(application)
    private val orchestrator = AutoFlowRuntime.getOrchestrator(application)

    val orchestratorState: StateFlow<OrchestratorState> = orchestrator.state
    val consoleLogs: StateFlow<List<String>> = orchestrator.logs

    val workflows = db.workflowDao().observeAllWorkflows().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val logs = db.logDao().observeAllLogs().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val kvStates = db.stateDao().observeAllStates().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            fileRepo.initializeAndSync()
        }
    }

    fun createNewWorkflowTemplate(): WorkflowDefinition {
        val shortId = UUID.randomUUID().toString().take(6)
        return WorkflowDefinition(
            id = "wf_$shortId",
            name = "Workflow Baru ($shortId)",
            description = "Alur kerja modular kustom",
            nodes = listOf(WorkflowNodePresets.createPythonScraperPreset("step_1"))
        )
    }

    fun startWorkflow(definition: WorkflowDefinition) {
        val cur = orchestrator.state.value
        if (cur is OrchestratorState.Running || cur is OrchestratorState.Paused) {
            _events.tryEmit("Engine masih aktif. Hentikan dulu sebelum memulai workflow baru.")
            return
        }
        val app = getApplication<Application>()
        val serialized = Json.encodeToString(WorkflowDefinition.serializer(), definition)
        val started = runCatching {
            val serviceIntent = Intent(app, WorkflowForegroundService::class.java).apply {
                action = WorkflowForegroundService.ACTION_START
                putExtra(WorkflowForegroundService.EXTRA_WORKFLOW_JSON, serialized)
            }
            ContextCompat.startForegroundService(app, serviceIntent)
            true
        }.getOrDefault(false)

        if (!started) {
            viewModelScope.launch(Dispatchers.Default) {
                orchestrator.run(definition)
            }
        }
    }

    fun pauseExecution() {
        orchestrator.pause()
        sendServiceAction(WorkflowForegroundService.ACTION_PAUSE)
    }

    fun resumeExecution() {
        orchestrator.resume()
        sendServiceAction(WorkflowForegroundService.ACTION_RESUME)
    }

    fun stopExecution() {
        orchestrator.stop()
        sendServiceAction(WorkflowForegroundService.ACTION_STOP)
    }

    private fun sendServiceAction(actionName: String) {
        val app = getApplication<Application>()
        runCatching {
            val intent = Intent(app, WorkflowForegroundService::class.java).apply {
                action = actionName
            }
            app.startService(intent)
        }
    }

    fun saveWorkflow(definition: WorkflowDefinition) {
        viewModelScope.launch(Dispatchers.IO) {
            fileRepo.saveWorkflow(definition)
            _events.tryEmit("Workflow '${definition.name}' disimpan ke filesDir/workflows")
        }
    }

    fun duplicateWorkflow(entity: WorkflowEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val orig = runCatching { entity.toDefinition() }.getOrNull() ?: return@launch
            val copyDef = orig.copy(
                id = "wf_${UUID.randomUUID().toString().take(6)}",
                name = "${orig.name} (Salinan)"
            )
            fileRepo.saveWorkflow(copyDef)
            _events.tryEmit("Workflow diduplikasi")
        }
    }

    fun deleteWorkflow(workflowId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            WorkflowTriggerScheduler.cancelScheduledWorkflow(getApplication(), workflowId)
            fileRepo.deleteWorkflow(workflowId)
            _events.tryEmit("Workflow dihapus")
        }
    }

    fun setWorkflowSchedule(entity: WorkflowEntity, enabled: Boolean, intervalMinutes: Long) {
        val safeInterval = intervalMinutes.coerceAtLeast(15L)
        viewModelScope.launch(Dispatchers.IO) {
            if (enabled) {
                WorkflowTriggerScheduler.schedulePeriodicWorkflow(getApplication(), entity.id, safeInterval)
                _events.tryEmit("Jadwal otomatis aktif (tiap $safeInterval menit)")
            } else {
                WorkflowTriggerScheduler.cancelScheduledWorkflow(getApplication(), entity.id)
                _events.tryEmit("Jadwal otomatis dimatikan")
            }
            db.workflowDao().updateSchedule(entity.id, enabled, safeInterval)
        }
    }

    fun restoreStarterWorkflows() {
        viewModelScope.launch(Dispatchers.IO) {
            BuiltInWorkflowSeeds.getStarterWorkflows().forEach { def ->
                if (db.workflowDao().getWorkflowById(def.id) == null) {
                    fileRepo.saveWorkflow(def)
                }
            }
            _events.tryEmit("Preset starter workflow ditambahkan")
        }
    }

    fun importWorkflowFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val imported = WorkflowBundleImporter.importFromUri(getApplication(), uri)
                imported.workflows.forEach { wf ->
                    val finalDef = if (db.workflowDao().getWorkflowById(wf.id) != null) {
                        wf.copy(id = "${wf.id}_${UUID.randomUUID().toString().take(4)}")
                    } else {
                        wf
                    }
                    fileRepo.saveWorkflow(finalDef)
                }
                val pyMsg = if (imported.pythonModulesExtracted.isNotEmpty()) {
                    " + ${imported.pythonModulesExtracted.size} modul Python"
                } else ""
                _events.tryEmit("Berhasil mengimpor ${imported.workflows.size} workflow$pyMsg")
            }.onFailure { err ->
                _events.tryEmit("Gagal impor: ${err.message}")
            }
        }
    }

    fun exportWorkflowBundle(definition: WorkflowDefinition, onExportReady: (File) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = WorkflowBundleExporter.exportBundleToCache(getApplication(), definition)
            withContext(Dispatchers.Main) {
                onExportReady(file)
            }
        }
    }

    fun saveCookieForUrl(url: String, cookies: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val host = runCatching { Uri.parse(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: "default"
            val key = "cookie_$host"
            db.stateDao().putValue(WorkflowStateEntity(key = key, value = cookies, namespace = "cookies"))
            _events.tryEmit("Sesi & cookie untuk $host tersimpan ($key)")
        }
    }

    fun saveKvEntry(key: String, value: String, namespace: String = "default") {
        if (key.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            db.stateDao().putValue(WorkflowStateEntity(key = key.trim(), value = value, namespace = namespace.ifBlank { "default" }))
            _events.tryEmit("Variabel KV '${key.trim()}' disimpan")
        }
    }

    fun deleteKvEntry(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.stateDao().deleteByKey(key)
            _events.tryEmit("Variabel KV '$key' dihapus")
        }
    }

    fun exportVaultCsvToUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val snapshot = db.logDao().getFullLogsSnapshot()
                CsvVaultExporter.exportToUri(getApplication(), uri, snapshot)
                _events.tryEmit("CSV Vault berhasil disimpan (${snapshot.size} baris)")
            }.onFailure { err ->
                _events.tryEmit("Gagal ekspor CSV: ${err.message}")
            }
        }
    }

    fun exportVaultCsv(onExportReady: (File) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val snapshot = db.logDao().getFullLogsSnapshot()
            val file = CsvVaultExporter.exportToCsv(getApplication(), snapshot)
            withContext(Dispatchers.Main) {
                onExportReady(file)
            }
        }
    }

    fun clearVaultLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            db.logDao().purgeAllLogs()
            _events.tryEmit("Riwayat Vault dibersihkan")
        }
    }
}
