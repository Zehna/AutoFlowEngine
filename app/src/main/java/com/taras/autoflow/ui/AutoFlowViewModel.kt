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
import com.taras.autoflow.engine.WorkflowNode
import com.taras.autoflow.n8n.N8nClient
import com.taras.autoflow.n8n.N8nConfig
import com.taras.autoflow.n8n.N8nConfigStore
import com.taras.autoflow.n8n.N8nExecutionInfo
import com.taras.autoflow.n8n.N8nServerStatus
import com.taras.autoflow.n8n.N8nWorkflowDetail
import com.taras.autoflow.n8n.N8nWorkflowSummary
import com.taras.autoflow.service.WorkflowForegroundService
import com.taras.autoflow.worker.WorkflowTriggerScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.util.UUID

class AutoFlowViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AutoFlowDatabase.getInstance(application)
    private val fileRepo = WorkflowFileRepository(application)
    private val orchestrator = AutoFlowRuntime.getOrchestrator(application)
    private val n8nStore = N8nConfigStore(application)
    private val n8nClient = N8nClient(application)

    val orchestratorState: StateFlow<OrchestratorState> = orchestrator.state
    val consoleLogs: StateFlow<List<String>> = orchestrator.logs

    private val _n8nConfig = MutableStateFlow(N8nConfig())
    val n8nConfig = _n8nConfig.asStateFlow()

    private val _n8nStatus = MutableStateFlow(N8nServerStatus())
    val n8nStatus = _n8nStatus.asStateFlow()

    private val _n8nWorkflows = MutableStateFlow<List<N8nWorkflowSummary>>(emptyList())
    val n8nWorkflows = _n8nWorkflows.asStateFlow()

    private val _n8nExecutions = MutableStateFlow<List<N8nExecutionInfo>>(emptyList())
    val n8nExecutions = _n8nExecutions.asStateFlow()

    private val _n8nSelectedDetail = MutableStateFlow<N8nWorkflowDetail?>(null)
    val n8nSelectedDetail = _n8nSelectedDetail.asStateFlow()

    private val _n8nSelectedExec = MutableStateFlow<N8nExecutionInfo?>(null)
    val n8nSelectedExec = _n8nSelectedExec.asStateFlow()

    private val _n8nBusyMessage = MutableStateFlow<String?>(null)
    val n8nBusyMessage = _n8nBusyMessage.asStateFlow()

    private val _n8nLastOutcome = MutableStateFlow<String?>(null)
    val n8nLastOutcome = _n8nLastOutcome.asStateFlow()

    private val _n8nDiagnostics = MutableStateFlow<String?>(null)
    val n8nDiagnostics = _n8nDiagnostics.asStateFlow()

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
            val cfg = n8nStore.loadConfig()
            _n8nConfig.value = cfg
            refreshN8nStatusInternal(cfg)
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

    // --- Integrasi n8n Localhost & Auto-Start Bridge ---

    fun refreshN8nAll() {
        viewModelScope.launch(Dispatchers.IO) {
            val cfg = n8nStore.loadConfig()
            _n8nConfig.value = cfg
            refreshN8nStatusInternal(cfg)
            n8nClient.listWorkflows(cfg).onSuccess { _n8nWorkflows.value = it }
            n8nClient.listExecutions(cfg, limit = 25).onSuccess { _n8nExecutions.value = it }
        }
    }

    private suspend fun refreshN8nStatusInternal(cfg: N8nConfig) {
        val status = n8nClient.probeServerStatus(cfg)
        _n8nStatus.value = status
        if (status.bridgeAvailable || status.n8nRunning) {
            n8nClient.listWorkflows(cfg).onSuccess { _n8nWorkflows.value = it }
        }
    }

    fun saveN8nConfig(config: N8nConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            n8nStore.saveConfig(config)
            _n8nConfig.value = config
            _events.tryEmit("Konfigurasi n8n disimpan")
            refreshN8nStatusInternal(config)
            n8nClient.listWorkflows(config).onSuccess { _n8nWorkflows.value = it }
        }
    }

    fun startN8nServerAndRefresh(onReady: (() -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val cfg = n8nStore.loadConfig()
            _n8nConfig.value = cfg
            _n8nBusyMessage.value = "Menghubungkan / menghidupkan server n8n..."
            val res = n8nClient.ensureServerRunning(cfg) { progress ->
                _n8nBusyMessage.value = progress
                AutoFlowRuntime.appendLog("🔌 $progress")
            }
            res.fold(
                onSuccess = { status ->
                    _n8nStatus.value = status
                    n8nClient.listWorkflows(cfg).onSuccess { _n8nWorkflows.value = it }
                    n8nClient.listExecutions(cfg, limit = 25).onSuccess { _n8nExecutions.value = it }
                    _n8nBusyMessage.value = null
                    _events.tryEmit("n8n siap di ${cfg.normalizedBaseUrl()}")
                    withContext(Dispatchers.Main) { onReady?.invoke() }
                },
                onFailure = { err ->
                    _n8nBusyMessage.value = null
                    refreshN8nStatusInternal(cfg)
                    val msg = "n8n gagal start: ${err.message}"
                    _n8nLastOutcome.value = msg
                    _events.tryEmit(msg)
                }
            )
        }
    }

    fun stopN8nServer() {
        viewModelScope.launch(Dispatchers.IO) {
            val cfg = n8nStore.loadConfig()
            _n8nBusyMessage.value = "Menghentikan server n8n..."
            n8nClient.stopServer(cfg).fold(
                onSuccess = { msg ->
                    _events.tryEmit(msg)
                    refreshN8nStatusInternal(cfg)
                },
                onFailure = { err ->
                    _events.tryEmit(err.message ?: "Gagal menghentikan n8n")
                }
            )
            _n8nBusyMessage.value = null
        }
    }

    fun inspectN8nWorkflow(workflowId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cfg = n8nStore.loadConfig()
            _n8nBusyMessage.value = "Menginspeksi workflow $workflowId..."
            n8nClient.inspectWorkflow(cfg, workflowId).fold(
                onSuccess = { detail ->
                    _n8nSelectedDetail.value = detail
                    n8nClient.listExecutions(cfg, workflowId = workflowId, limit = 15)
                        .onSuccess { _n8nExecutions.value = it }
                },
                onFailure = { err ->
                    _events.tryEmit("Inspeksi gagal: ${err.message}")
                }
            )
            _n8nBusyMessage.value = null
        }
    }

    fun clearSelectedN8nWorkflow() {
        _n8nSelectedDetail.value = null
    }

    fun inspectN8nExecution(execId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cfg = n8nStore.loadConfig()
            n8nClient.inspectExecution(cfg, execId).fold(
                onSuccess = { _n8nSelectedExec.value = it },
                onFailure = { err -> _events.tryEmit("Gagal memuat eksekusi #$execId: ${err.message}") }
            )
        }
    }

    fun clearSelectedN8nExecution() {
        _n8nSelectedExec.value = null
    }

    fun toggleN8nWorkflowActive(workflowId: String, active: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val cfg = n8nStore.loadConfig()
            n8nClient.setWorkflowActive(cfg, workflowId, active).fold(
                onSuccess = {
                    _events.tryEmit(if (active) "Workflow diaktifkan" else "Workflow dinonaktifkan")
                    n8nClient.listWorkflows(cfg).onSuccess { _n8nWorkflows.value = it }
                    if (_n8nSelectedDetail.value?.id == workflowId) {
                        inspectN8nWorkflow(workflowId)
                    }
                },
                onFailure = { err -> _events.tryEmit(err.message ?: "Gagal mengubah status workflow") }
            )
        }
    }

    fun executeN8nWorkflow(workflowId: String, webhookPath: String? = null, payloadJson: String = "{}") {
        viewModelScope.launch(Dispatchers.IO) {
            val cfg = n8nStore.loadConfig()
            _n8nBusyMessage.value = "Memulai & menjalankan workflow $workflowId..."
            val res = n8nClient.executeWorkflow(cfg, workflowId, webhookPath, payloadJson) { progress ->
                _n8nBusyMessage.value = progress
                AutoFlowRuntime.appendLog("▶ n8n: $progress")
            }
            res.fold(
                onSuccess = { outcome ->
                    _n8nLastOutcome.value = outcome.message
                    _events.tryEmit(outcome.message)
                    if (outcome.executionDetail != null) {
                        _n8nSelectedExec.value = outcome.executionDetail
                    }
                    refreshN8nStatusInternal(cfg)
                    n8nClient.listExecutions(cfg, limit = 25).onSuccess { _n8nExecutions.value = it }
                },
                onFailure = { err ->
                    _n8nLastOutcome.value = "Error: ${err.message}"
                    _events.tryEmit("Eksekusi gagal: ${err.message}")
                }
            )
            _n8nBusyMessage.value = null
        }
    }

    fun loginAndGenerateN8nApiKey(config: N8nConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            n8nStore.saveConfig(config)
            _n8nConfig.value = config
            _n8nBusyMessage.value = "Login ke n8n & membuat API Key..."
            n8nClient.ensureServerRunning(config) { _n8nBusyMessage.value = it }.onFailure { err ->
                _n8nBusyMessage.value = null
                _events.tryEmit("n8n belum aktif: ${err.message}")
                return@launch
            }
            n8nClient.loginAndCreateApiKey(config).fold(
                onSuccess = { key ->
                    val updated = config.copy(apiKey = key)
                    n8nStore.saveConfig(updated)
                    _n8nConfig.value = updated
                    _events.tryEmit("Login berhasil & API Key otomatis tersimpan!")
                    refreshN8nStatusInternal(updated)
                },
                onFailure = { err ->
                    _events.tryEmit("Login / API Key gagal: ${err.message}")
                }
            )
            _n8nBusyMessage.value = null
        }
    }

    fun pinN8nWorkflowToDashboard(wf: N8nWorkflowSummary) {
        viewModelScope.launch(Dispatchers.IO) {
            val safeId = "wf_n8n_${wf.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")}"
            val webhookPath = wf.triggers.firstOrNull { it.webhookPath.isNotBlank() }?.webhookPath.orEmpty()
            val def = WorkflowDefinition(
                id = safeId,
                name = "n8n ▶ ${wf.name}",
                description = "Auto-start server n8n lokal, jalankan workflow '${wf.name}' (${wf.id}), lalu inspeksi hasilnya.",
                variables = mapOf(
                    "n8n_workflow_id" to JsonPrimitive(wf.id),
                    "webhook_path" to JsonPrimitive(webhookPath),
                    "payload_json" to JsonPrimitive("{}")
                ),
                nodes = listOf(
                    WorkflowNode(
                        id = "n8n_boot",
                        name = "Auto-Start Server n8n",
                        worker = "N8N_BRIDGE",
                        action = "ENSURE_SERVER",
                        next = "n8n_exec"
                    ),
                    WorkflowNode(
                        id = "n8n_exec",
                        name = "Jalankan ${wf.name}",
                        worker = "N8N_BRIDGE",
                        action = "EXECUTE_WORKFLOW",
                        params = mapOf(
                            "workflow_id" to JsonPrimitive("{{variables.n8n_workflow_id}}"),
                            "webhook_path" to JsonPrimitive("{{variables.webhook_path}}"),
                            "payload_json" to JsonPrimitive("{{variables.payload_json}}")
                        ),
                        next = "n8n_check"
                    ),
                    WorkflowNode(
                        id = "n8n_check",
                        name = "Inspeksi Log Eksekusi",
                        worker = "N8N_BRIDGE",
                        action = "LIST_EXECUTIONS",
                        params = mapOf(
                            "workflow_id" to JsonPrimitive("{{variables.n8n_workflow_id}}"),
                            "limit" to JsonPrimitive(5)
                        ),
                        next = "END"
                    )
                )
            )
            fileRepo.saveWorkflow(def)
            _events.tryEmit("Workflow n8n disematkan ke Dashboard!")
        }
    }

    fun clearN8nOutcome() {
        _n8nLastOutcome.value = null
    }

    fun runN8nDiagnostics() {
        viewModelScope.launch(Dispatchers.IO) {
            val cfg = n8nStore.loadConfig()
            _n8nBusyMessage.value = "Menguji koneksi localhost (:5678 & :15678)..."
            val report = n8nClient.runConnectionDiagnostics(cfg)
            _n8nDiagnostics.value = report
            refreshN8nStatusInternal(cfg)
            _n8nBusyMessage.value = null
        }
    }

    fun clearN8nDiagnostics() {
        _n8nDiagnostics.value = null
    }
}
