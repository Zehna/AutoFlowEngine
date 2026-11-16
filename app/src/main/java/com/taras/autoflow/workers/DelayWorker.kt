package com.taras.autoflow.workers

import com.taras.autoflow.engine.AutoFlowRuntime
import com.taras.autoflow.engine.ExecutionContext
import com.taras.autoflow.engine.NodeExecutionResult
import com.taras.autoflow.service.NodeWorker
import kotlinx.coroutines.delay

class DelayWorker : NodeWorker {
    override suspend fun execute(parameters: Map<String, String>, context: ExecutionContext): NodeExecutionResult {
        val totalMs = parameters["duration_ms"]?.toLongOrNull()?.coerceIn(0L, 300_000L) ?: 1000L
        var elapsed = 0L
        val step = 100L

        while (elapsed < totalMs) {
            if (AutoFlowRuntime.isEmergencyStopActive()) {
                return NodeExecutionResult.Failure("Delay dihentikan oleh Emergency Stop")
            }
            val chunk = minOf(step, totalMs - elapsed)
            delay(chunk)
            elapsed += chunk
        }
        return NodeExecutionResult.Success(mapOf("waited_ms" to totalMs))
    }
}
