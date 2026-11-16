package com.taras.autoflow.engine

import kotlinx.serialization.Serializable

@Serializable
data class WorkflowBundle(
    val formatVersion: Int = 1,
    val workflow: WorkflowDefinition,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class WorkflowDefinition(
    val id: String,
    val name: String,
    val description: String = "",
    val triggers: List<TriggerConfig> = emptyList(),
    val nodes: List<WorkflowNode>
)

@Serializable
data class TriggerConfig(
    val type: String, // MANUAL, PERIODIC, NOTIFICATION
    val options: Map<String, String> = emptyMap()
)

@Serializable
data class WorkflowNode(
    val id: String,
    val type: String, // HTTP, PYTHON, UI_ACTION, OFFSCREEN_WEB, SHELL, DEDUPLICATE, GEMINI_AI
    val parameters: Map<String, String> = emptyMap(),
    val maxRetries: Int = 0,
    val retryDelayMs: Long = 1000L,
    val continueOnError: Boolean = false
)

sealed interface NodeExecutionResult {
    data class Success(val output: Map<String, Any?>) : NodeExecutionResult
    data class Failure(val message: String, val isRecoverable: Boolean = false) : NodeExecutionResult
}
