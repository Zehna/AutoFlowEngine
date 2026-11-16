package com.taras.autoflow.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

object AutoFlowRuntime {
    private val _isEngineActive = MutableStateFlow(false)
    val isEngineActive = _isEngineActive.asStateFlow()

    private val _runningNodeId = MutableStateFlow<String?>(null)
    val runningNodeId = _runningNodeId.asStateFlow()

    private val _activeWorkflowName = MutableStateFlow<String?>(null)
    val activeWorkflowName = _activeWorkflowName.asStateFlow()

    private val _progressText = MutableStateFlow<String?>(null)
    val progressText = _progressText.asStateFlow()

    private val _consoleLogs = MutableStateFlow<List<String>>(emptyList())
    val consoleLogs = _consoleLogs.asStateFlow()

    @Volatile
    private var emergencyStopTriggered = false

    fun markStarted(nodeId: String? = null, workflowName: String? = null) {
        emergencyStopTriggered = false
        _isEngineActive.value = true
        _runningNodeId.value = nodeId
        _activeWorkflowName.value = workflowName
        _progressText.value = null
        if (workflowName != null) {
            appendLog("▶ Memulai workflow: $workflowName")
        }
    }

    fun updateCurrentNode(nodeId: String, stepIndex: Int = 0, totalSteps: Int = 0) {
        _runningNodeId.value = nodeId
        _progressText.value = if (totalSteps > 0) "Langkah $stepIndex/$totalSteps" else null
    }

    fun appendLog(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val entry = "[$time] $message"
        _consoleLogs.value = (_consoleLogs.value + entry).takeLast(120)
    }

    fun clearConsole() {
        _consoleLogs.value = emptyList()
    }

    fun markStopped() {
        _isEngineActive.value = false
        _runningNodeId.value = null
        _activeWorkflowName.value = null
        _progressText.value = null
    }

    fun triggerEmergencyStop() {
        emergencyStopTriggered = true
        appendLog("■ Emergency STOP diminta pengguna")
        markStopped()
    }

    fun isEmergencyStopActive(): Boolean = emergencyStopTriggered
}
