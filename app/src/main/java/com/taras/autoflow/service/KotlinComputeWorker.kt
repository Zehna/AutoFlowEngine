package com.taras.autoflow.service

import android.content.Context
import com.taras.autoflow.data.AutoFlowDatabase
import com.taras.autoflow.data.WorkflowStateEntity
import com.taras.autoflow.engine.ExecutionContext
import com.taras.autoflow.engine.NodeExecutionResult

class KotlinComputeWorker(private val context: Context) : NodeWorker {
    private val db = AutoFlowDatabase.getInstance(context)

    override suspend fun execute(parameters: Map<String, String>, context: ExecutionContext): NodeExecutionResult {
        val op = parameters["operation"] ?: return NodeExecutionResult.Failure("Parameter 'operation' kosong")

        return when (op) {
            "KV_GET" -> {
                val key = parameters["key"] ?: return NodeExecutionResult.Failure("Key kosong")
                val value = db.stateDao().getValue(key)
                NodeExecutionResult.Success(mapOf("value" to value))
            }
            "KV_SET" -> {
                val key = parameters["key"] ?: return NodeExecutionResult.Failure("Key kosong")
                val value = parameters["value"] ?: ""
                db.stateDao().putValue(WorkflowStateEntity(key = key, value = value))
                NodeExecutionResult.Success(mapOf("status" to "SAVED"))
            }
            "DEDUPLICATE" -> {
                val checkKey = parameters["key"] ?: return NodeExecutionResult.Failure("Key kosong")
                val exists = db.stateDao().getValue(checkKey) != null
                if (exists) {
                    NodeExecutionResult.Failure("Data duplikat terdeteksi", isRecoverable = false)
                } else {
                    db.stateDao().putValue(WorkflowStateEntity(key = checkKey, value = "SEEN"))
                    NodeExecutionResult.Success(mapOf("duplicate" to false))
                }
            }
            else -> NodeExecutionResult.Failure("Operasi '$op' tidak didukung")
        }
    }
}
