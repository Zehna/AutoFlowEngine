package com.taras.autoflow.engine

import android.content.Context
import com.taras.autoflow.service.WorkerRegistryFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

sealed interface OrchestratorState {
    data object Idle : OrchestratorState
    data class Running(
        val workflowId: String,
        val workflowName: String,
        val cycle: Int,
        val stepIndex: Int,
        val totalSteps: Int,
        val currentNodeId: String,
        val currentNodeName: String
    ) : OrchestratorState

    data class Paused(
        val workflowId: String,
        val workflowName: String,
        val cycle: Int,
        val stepIndex: Int,
        val totalSteps: Int,
        val currentNodeId: String,
        val currentNodeName: String
    ) : OrchestratorState

    data class Completed(
        val workflowId: String,
        val workflowName: String,
        val totalCycles: Int
    ) : OrchestratorState

    data class Failed(
        val workflowId: String,
        val workflowName: String,
        val reason: String
    ) : OrchestratorState
}

object AutoFlowRuntime {
    @Volatile
    private var orchestratorInstance: WorkflowOrchestrator? = null

    fun getOrchestrator(context: Context): WorkflowOrchestrator {
        return orchestratorInstance ?: synchronized(this) {
            orchestratorInstance ?: WorkflowOrchestrator(
                context.applicationContext,
                WorkerRegistryFactory.create(context.applicationContext)
            ).also { orchestratorInstance = it }
        }
    }

    private val _isEngineActive = MutableStateFlow(false)
    val isEngineActive = _isEngineActive.asStateFlow()

    private val _runningNodeId = MutableStateFlow<String?>(null)
    val runningNodeId = _runningNodeId.asStateFlow()

    private val _consoleLogs = MutableStateFlow<List<String>>(emptyList())
    val consoleLogs = _consoleLogs.asStateFlow()

    @Volatile
    private var emergencyStopTriggered = false

    @Volatile
    private var paused = false

    fun markStarted(nodeId: String? = null) {
        emergencyStopTriggered = false
        paused = false
        _isEngineActive.value = true
        _runningNodeId.value = nodeId
    }

    fun updateCurrentNode(nodeId: String) {
        _runningNodeId.value = nodeId
    }

    fun pause() {
        paused = true
        appendLog("⏸ Eksekusi dijeda pengguna")
    }

    fun resume() {
        paused = false
        appendLog("▶ Eksekusi dilanjutkan")
    }

    fun isPaused(): Boolean = paused

    fun appendLog(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val entry = "[$time] $message"
        _consoleLogs.value = (_consoleLogs.value + entry).takeLast(150)
    }

    fun clearConsole() {
        _consoleLogs.value = emptyList()
    }

    fun markStopped() {
        _isEngineActive.value = false
        _runningNodeId.value = null
        paused = false
    }

    fun triggerEmergencyStop() {
        emergencyStopTriggered = true
        paused = false
        appendLog("■ Emergency STOP diminta pengguna")
        markStopped()
    }

    fun isEmergencyStopActive(): Boolean = emergencyStopTriggered
}
