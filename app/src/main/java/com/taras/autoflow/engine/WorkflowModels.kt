package com.taras.autoflow.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

@Serializable
enum class ExecutionMode {
    ONCE,
    LOOP
}

@Serializable
data class ExecutionPolicy(
    val mode: ExecutionMode = ExecutionMode.ONCE,
    val maxIterations: Int = 1, // <= 0 berarti loop tak terbatas hingga dihentikan pengguna
    val loopDelayMs: Long = 2000L
)

@Serializable
data class RetryPolicy(
    val maxAttempts: Int = 0,
    val delayMs: Long = 1000L
)

@Serializable
data class WorkflowBundle(
    val formatVersion: Int = 2,
    val workflow: WorkflowDefinition,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class WorkflowDefinition(
    val id: String,
    val name: String,
    val description: String = "",
    val variables: Map<String, JsonElement> = emptyMap(),
    val triggers: List<TriggerConfig> = emptyList(),
    val executionPolicy: ExecutionPolicy = ExecutionPolicy(),
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
    val name: String = "",
    val worker: String, // SHIZUKU_SHELL, WEBVIEW_HEADLESS, NETWORK_HTTP, COMPUTE_KOTLIN, PYTHON_COMPUTE, GEMINI_AI, UI_ACTION, DELAY
    val action: String = "",
    val condition: String? = null,
    val params: Map<String, JsonElement> = emptyMap(),
    val retry: RetryPolicy = RetryPolicy(),
    val next: String? = null,
    val continueOnError: Boolean = false
)

sealed interface NodeExecutionResult {
    data class Success(val output: Map<String, Any?>) : NodeExecutionResult
    data class Failure(val message: String, val isRecoverable: Boolean = false) : NodeExecutionResult
}

enum class VariableValueType {
    STRING,
    NUMBER,
    BOOLEAN,
    JSON
}

fun JsonElement.asPlainString(): String = when (this) {
    is JsonNull -> ""
    is JsonPrimitive -> content
    is JsonObject, is JsonArray -> toString()
}

fun JsonElement.detectType(): VariableValueType = when (this) {
    is JsonObject, is JsonArray -> VariableValueType.JSON
    is JsonPrimitive -> when {
        isString -> VariableValueType.STRING
        booleanOrNull != null -> VariableValueType.BOOLEAN
        longOrNull != null || doubleOrNull != null -> VariableValueType.NUMBER
        else -> VariableValueType.STRING
    }
    is JsonNull -> VariableValueType.STRING
}

fun stringToTypedJsonElement(raw: String, type: VariableValueType): JsonElement = when (type) {
    VariableValueType.BOOLEAN -> JsonPrimitive(raw.trim().equals("true", ignoreCase = true))
    VariableValueType.NUMBER -> {
        val trimmed = raw.trim()
        val l = trimmed.toLongOrNull()
        if (l != null) JsonPrimitive(l)
        else {
            val d = trimmed.toDoubleOrNull()
            if (d != null) JsonPrimitive(d) else JsonPrimitive(0)
        }
    }
    VariableValueType.JSON -> {
        runCatching { kotlinx.serialization.json.Json.parseToJsonElement(raw) }
            .getOrElse { JsonPrimitive(raw) }
    }
    VariableValueType.STRING -> JsonPrimitive(raw)
}
