package com.taras.autoflow.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object AutoFlowRuntime {
    private val _isEngineActive = MutableStateFlow(false)
    val isEngineActive = _isEngineActive.asStateFlow()

    private val _runningNodeId = MutableStateFlow<String?>(null)
    val runningNodeId = _runningNodeId.asStateFlow()

    private var emergencyStopTriggered = false

    fun markStarted(nodeId: String? = null) {
        emergencyStopTriggered = false
        _isEngineActive.value = true
        _runningNodeId.value = nodeId
    }

    fun updateCurrentNode(nodeId: String) {
        _runningNodeId.value = nodeId
    }

    fun markStopped() {
        _isEngineActive.value = false
        _runningNodeId.value = null
    }

    fun triggerEmergencyStop() {
        emergencyStopTriggered = true
        _isEngineActive.value = false
        _runningNodeId.value = null
    }

    fun isEmergencyStopActive(): Boolean = emergencyStopTriggered
}
