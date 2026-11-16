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
import com.taras.autoflow.data.WorkflowStateEntity
import com.taras.autoflow.engine.AutoFlowRuntime
import com.taras.autoflow.engine.WorkflowBundleExporter
import com.taras.autoflow.engine.WorkflowBundleImporter
import com.taras.autoflow.engine.WorkflowDefinition
import com.taras.autoflow.engine.WorkflowOrchestrator
import com.taras.autoflow.service.WorkerRegistryFactory
import com.taras.autoflow.service.WorkflowForegroundService
import com.taras.autoflow.worker.WorkflowTriggerScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class AutoFlowViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AutoFlowDatabase.getInstance(application)
    private val registry = WorkerRegistryFactory.create(application)
    private val orchestrator = WorkflowOrchestrator(application, registry)

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

    private var activeExecutionJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            if (db.workflowDao().getWorkflowCount() == 0) {
                val seeds = BuiltInWorkflowSeeds.getStarterWorkflows().map { WorkflowEntity.fromDefinition(it) }
                db.workflowDao().insertAllIgnore(seeds)
            }
        }
    }

    fun createNewWorkflowTemplate(): WorkflowDefinition {
        val shortId = UUID.randomUUID().toString().take(6)
        return WorkflowDefinition(
            id = "wf_$shortId",
            name = "Workflow Baru ($shortId)",
            description = "Alur kerja modular kustom",
            nodes = listOf(WorkflowNodePresets.createPythonScraperPreset().copy(id = "step_1"))
        )
    }

    fun startWorkflow(definition: WorkflowDefinition) {
        if (AutoFlowRuntime.isEngineActive.value) {
            _events.tryEmit("Engine masih berjalan. Hentikan dulu sebelum memulai workflow baru.")
            return
        }
        val app = getApplication<Application>()
        runCatching {
            val serviceIntent = Intent(app, WorkflowForegroundService::class.java)
            ContextCompat.startForegroundService(app, serviceIntent)
        }

        activeExecutionJob = viewModelScope.launch(Dispatchers.Default) {
            orchestrator.run(definition)
        }
    }

    fun stopExecution() {
        AutoFlowRuntime.triggerEmergencyStop()
        activeExecutionJob?.cancel()
        activeExecutionJob = null
        val app = getApplication<Application>()
        runCatching {
            val stopIntent = Intent(app, WorkflowForegroundService::class.java).apply {
                action = WorkflowForegroundService.ACTION_STOP
            }
            app.startService(stopIntent)
        }
    }

    fun saveWorkflow(definition: WorkflowDefinition) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = db.workflowDao().getWorkflowById(definition.id)
            val entity = WorkflowEntity.fromDefinition(
                definition = definition,
                isScheduled = existing?.isScheduled ?: false,
                scheduleIntervalMinutes = existing?.scheduleIntervalMinutes ?: 15L,
                lastRunAt = existing?.lastRunAt,
                lastRunStatus = existing?.lastRunStatus
            )
            db.workflowDao().upsertWorkflow(entity)
            _events.tryEmit("Workflow '${definition.name}' disimpan")
        }
    }

    fun duplicateWorkflow(entity: WorkflowEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val orig = runCatching { entity.toDefinition() }.getOrNull() ?: return@launch
            val copyDef = orig.copy(
                id = "wf_${UUID.randomUUID().toString().take(6)}",
                name = "${orig.name} (Salinan)"
            )
            db.workflowDao().upsertWorkflow(WorkflowEntity.fromDefinition(copyDef))
            _events.tryEmit("Workflow diduplikasi")
        }
    }

    fun deleteWorkflow(workflowId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            WorkflowTriggerScheduler.cancelScheduledWorkflow(getApplication(), workflowId)
            db.workflowDao().deleteWorkflowById(workflowId)
            _events.tryEmit("Workflow dihapus")
        }
    }

    fun setWorkflowSchedule(entity: WorkflowEntity, enabled: Boolean, intervalMinutes: Long) {
        val safeInterval = intervalMinutes.coerceAtLeast(15L)
        viewModelScope.launch(Dispatchers.IO) {
            if (enabled) {
                WorkflowTriggerScheduler.schedulePeriodicWorkflow(getApplication(), entity.id, safeInterval)
                _events.tryEmit("Jadwal otomatis diaktifkan (tiap $safeInterval menit)")
            } else {
                WorkflowTriggerScheduler.cancelScheduledWorkflow(getApplication(), entity.id)
                _events.tryEmit("Jadwal otomatis dimatikan")
            }
            db.workflowDao().updateSchedule(entity.id, enabled, safeInterval)
        }
    }

    fun restoreStarterWorkflows() {
        viewModelScope.launch(Dispatchers.IO) {
            val seeds = BuiltInWorkflowSeeds.getStarterWorkflows().map { WorkflowEntity.fromDefinition(it) }
            db.workflowDao().insertAllIgnore(seeds)
            _events.tryEmit("Preset starter workflow ditambahkan")
        }
    }

    fun importWorkflowFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val imported = WorkflowBundleImporter.importFromUri(getApplication(), uri)
                val finalDef = if (db.workflowDao().getWorkflowById(imported.id) != null) {
                    imported.copy(id = "${imported.id}_${UUID.randomUUID().toString().take(4)}")
                } else {
                    imported
                }
                db.workflowDao().upsertWorkflow(WorkflowEntity.fromDefinition(finalDef))
                _events.tryEmit("Berhasil mengimpor '${finalDef.name}'")
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
            _events.tryEmit("Cookie untuk $host disimpan ke KV Vault ($key)")
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
