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
        val op = (parameters["action"]?.takeIf { it.isNotBlank() } ?: parameters["operation"])
            ?.uppercase()
            ?: if (parameters.containsKey("dedup_key")) "DEDUPLICATE" else "TEXT_TEMPLATE"
        val namespace = parameters["namespace"]?.ifBlank { "default" } ?: "default"
        val now = System.currentTimeMillis()

        // Bersihkan entri kedaluwarsa secara berkala
        db.stateDao().purgeExpired(now)

        return when (op) {
            "KV_GET" -> {
                val key = parameters["key"] ?: return NodeExecutionResult.Failure("Key kosong")
                val value = db.stateDao().getValidValue(key, namespace, now)
                NodeExecutionResult.Success(mapOf("key" to key, "value" to (value ?: ""), "found" to (value != null)))
            }
            "KV_SET" -> {
                val key = parameters["key"] ?: return NodeExecutionResult.Failure("Key kosong")
                val value = parameters["value"] ?: ""
                val ttlMs = resolveTtlMs(parameters)
                val expiresAt = ttlMs?.let { now + it }
                db.stateDao().putValue(
                    WorkflowStateEntity(key = key, value = value, namespace = namespace, expiresAt = expiresAt, updatedAt = now)
                )
                NodeExecutionResult.Success(mapOf("status" to "SAVED", "key" to key, "value" to value))
            }
            "DEDUPLICATE" -> {
                val checkKey = parameters["key"] ?: parameters["dedup_key"]
                    ?: return NodeExecutionResult.Failure("Parameter 'key' kosong")
                val failOnDuplicate = parameters["fail_on_duplicate"]?.equals("false", ignoreCase = true) != true
                val ttlMs = resolveTtlMs(parameters)
                val expiresAt = ttlMs?.let { now + it }

                val existing = db.stateDao().getValidValue(checkKey, namespace, now)
                if (existing != null) {
                    if (failOnDuplicate) {
                        NodeExecutionResult.Failure("Data duplikat terdeteksi untuk key '$checkKey'", isRecoverable = false)
                    } else {
                        NodeExecutionResult.Success(mapOf("duplicate" to true, "key" to checkKey))
                    }
                } else {
                    db.stateDao().putValue(
                        WorkflowStateEntity(key = checkKey, value = "SEEN", namespace = namespace, expiresAt = expiresAt, updatedAt = now)
                    )
                    NodeExecutionResult.Success(mapOf("duplicate" to false, "key" to checkKey, "ttl_ms" to (ttlMs ?: -1L)))
                }
            }
            "FILTER_JSON" -> {
                val jsonRaw = parameters["json_input"] ?: "[]"
                val field = parameters["field"] ?: ""
                val equalsVal = parameters["equals"]
                val containsVal = parameters["contains"]
                val arr = runCatching { JSONArray(jsonRaw) }.getOrNull()
                    ?: return NodeExecutionResult.Failure("Input bukan JSON Array yang valid")

                val filtered = JSONArray()
                for (i in 0 until arr.length()) {
                    val item = arr.opt(i)
                    val targetStr = if (item is JSONObject && field.isNotBlank()) item.optString(field) else item?.toString().orEmpty()
                    val matchEquals = equalsVal == null || targetStr == equalsVal
                    val matchContains = containsVal == null || targetStr.contains(containsVal, ignoreCase = true)
                    if (matchEquals && matchContains) {
                        filtered.put(item)
                    }
                }
                NodeExecutionResult.Success(mapOf("items" to filtered.toString(), "count" to filtered.length()))
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

    private fun resolveTtlMs(parameters: Map<String, String>): Long? {
        val ms = parameters["ttl_ms"]?.toLongOrNull()
        if (ms != null && ms > 0) return ms
        val sec = parameters["ttl_seconds"]?.toLongOrNull()
        if (sec != null && sec > 0) return sec * 1000L
        return null
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
