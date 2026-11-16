package com.taras.autoflow.service

import com.taras.autoflow.engine.ExecutionContext
import com.taras.autoflow.engine.NodeExecutionResult

interface NodeWorker {
    suspend fun execute(parameters: Map<String, String>, context: ExecutionContext): NodeExecutionResult
}

class WorkerRegistry {
    private val workers = mutableMapOf<String, NodeWorker>()

    fun register(type: String, worker: NodeWorker) {
        workers[type] = worker
    }

    fun resolveWorker(type: String): NodeWorker? = workers[type]
}
