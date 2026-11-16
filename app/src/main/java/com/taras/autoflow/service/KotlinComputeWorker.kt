package com.taras.autoflow.service

import android.content.Context
import com.taras.autoflow.data.AutoFlowDatabase
import com.taras.autoflow.data.WorkflowStateEntity
import com.taras.autoflow.engine.ExecutionContext
import com.taras.autoflow.engine.NodeExecutionResult
import org.json.JSONArray
import org.json.JSONObject

class KotlinComputeWorker(private val context: Context) : NodeWorker {
    private val db = AutoFlowDatabase.getInstance(context)

    override suspend fun execute(parameters: Map<String, String>, context: ExecutionContext): NodeExecutionResult {
        val op = parameters["operation"]?.uppercase()
            ?: if (parameters.containsKey("dedup_key")) "DEDUPLICATE" else "TEXT_TEMPLATE"
        val namespace = parameters["namespace"]?.ifBlank { "default" } ?: "default"

        return when (op) {
            "KV_GET" -> {
                val key = parameters["key"] ?: return NodeExecutionResult.Failure("Key kosong")
                val value = db.stateDao().getValue(key, namespace)
                NodeExecutionResult.Success(mapOf("key" to key, "value" to (value ?: ""), "found" to (value != null)))
            }
            "KV_SET" -> {
                val key = parameters["key"] ?: return NodeExecutionResult.Failure("Key kosong")
                val value = parameters["value"] ?: ""
                db.stateDao().putValue(WorkflowStateEntity(key = key, value = value, namespace = namespace))
                NodeExecutionResult.Success(mapOf("status" to "SAVED", "key" to key, "value" to value))
            }
            "DEDUPLICATE" -> {
                val checkKey = parameters["key"] ?: parameters["dedup_key"]
                    ?: return NodeExecutionResult.Failure("Parameter 'key' kosong")
                val failOnDuplicate = parameters["fail_on_duplicate"]?.equals("false", ignoreCase = true) != true
                val exists = db.stateDao().getValue(checkKey, namespace) != null
                if (exists) {
                    if (failOnDuplicate) {
                        NodeExecutionResult.Failure("Data duplikat terdeteksi untuk key '$checkKey'", isRecoverable = false)
                    } else {
                        NodeExecutionResult.Success(mapOf("duplicate" to true, "key" to checkKey))
                    }
                } else {
                    db.stateDao().putValue(WorkflowStateEntity(key = checkKey, value = "SEEN", namespace = namespace))
                    NodeExecutionResult.Success(mapOf("duplicate" to false, "key" to checkKey))
                }
            }
            "JSON_EXTRACT" -> {
                val jsonRaw = parameters["json_input"] ?: "{}"
                val path = parameters["path"] ?: ""
                val extracted = extractJson(jsonRaw, path)
                NodeExecutionResult.Success(mapOf("value" to extracted))
            }
            "REGEX_MATCH" -> {
                val input = parameters["input"] ?: ""
                val pattern = parameters["pattern"] ?: return NodeExecutionResult.Failure("Pattern regex kosong")
                val match = Regex(pattern).find(input)
                val firstGroup = match?.groupValues?.getOrNull(1) ?: match?.value ?: ""
                NodeExecutionResult.Success(mapOf("matched" to (match != null), "value" to firstGroup))
            }
            "TEXT_TEMPLATE" -> {
                val text = parameters["template"] ?: parameters["value"] ?: ""
                NodeExecutionResult.Success(mapOf("result" to text))
            }
            else -> NodeExecutionResult.Failure("Operasi '$op' tidak didukung")
        }
    }

    private fun extractJson(raw: String, path: String): String {
        if (path.isBlank()) return raw
        var current: Any? = runCatching {
            val trimmed = raw.trim()
            if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed)
        }.getOrNull() ?: return ""

        for (seg in path.split(".")) {
            current = when (current) {
                is JSONObject -> current.opt(seg)
                is JSONArray -> seg.toIntOrNull()?.let { idx -> if (idx in 0 until current.length()) current.opt(idx) else null }
                else -> null
            } ?: return ""
        }
        return if (current == JSONObject.NULL) "" else current.toString()
    }
}
