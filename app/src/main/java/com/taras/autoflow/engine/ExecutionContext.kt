package com.taras.autoflow.engine

import org.json.JSONArray
import org.json.JSONObject

class ExecutionContext(
    val workflowId: String,
    private val variables: Map<String, String> = emptyMap(),
    private val kvLookup: (String) -> String? = { null },
    private val outputsMap: MutableMap<String, JSONObject> = mutableMapOf()
) {
    fun saveNodeOutput(nodeId: String, data: Map<String, Any?>) {
        outputsMap[nodeId] = JSONObject(data)
    }

    fun getNodeOutput(nodeId: String): JSONObject? = outputsMap[nodeId]

    fun getAllOutputsJson(): String {
        val root = JSONObject()
        root.put("vars", JSONObject(variables))
        outputsMap.forEach { (nodeId, jsonObj) ->
            root.put(nodeId, jsonObj)
        }
        return root.toString()
    }

    fun resolvePlaceholders(template: String): String {
        val pattern = Regex("\\{\\{([a-zA-Z0-9_-]+)\\.([a-zA-Z0-9_.-]+)\\}\\}")
        return pattern.replace(template) { match ->
            val rootKey = match.groupValues[1]
            val path = match.groupValues[2]

            when (rootKey) {
                "vars" -> variables[path] ?: ""
                "kv" -> kvLookup(path) ?: ""
                else -> {
                    val nodeJson = outputsMap[rootKey] ?: return@replace ""
                    evaluateJsonPath(nodeJson, path)
                }
            }
        }
    }

    fun evaluateCondition(condition: String?): Boolean {
        if (condition.isNullOrBlank()) return true
        val resolved = resolvePlaceholders(condition).trim()
        if (resolved.isEmpty()) return false

        return when {
            resolved.startsWith("not_empty ") -> resolved.removePrefix("not_empty ").trim().isNotEmpty()
            resolved.startsWith("is_empty ") -> resolved.removePrefix("is_empty ").trim().isEmpty()
            resolved.contains(" != ") -> {
                val parts = resolved.split(" != ", limit = 2)
                parts[0].trim() != parts[1].trim()
            }
            resolved.contains(" == ") -> {
                val parts = resolved.split(" == ", limit = 2)
                parts[0].trim() == parts[1].trim()
            }
            resolved.contains(" !contains ") -> {
                val parts = resolved.split(" !contains ", limit = 2)
                !parts[0].trim().contains(parts[1].trim(), ignoreCase = true)
            }
            resolved.contains(" contains ") -> {
                val parts = resolved.split(" contains ", limit = 2)
                parts[0].trim().contains(parts[1].trim(), ignoreCase = true)
            }
            resolved.contains(" > ") -> {
                val parts = resolved.split(" > ", limit = 2)
                (parts[0].trim().toDoubleOrNull() ?: 0.0) > (parts[1].trim().toDoubleOrNull() ?: 0.0)
            }
            resolved.contains(" < ") -> {
                val parts = resolved.split(" < ", limit = 2)
                (parts[0].trim().toDoubleOrNull() ?: 0.0) < (parts[1].trim().toDoubleOrNull() ?: 0.0)
            }
            else -> !resolved.equals("false", ignoreCase = true) && resolved != "0"
        }
    }

    private fun evaluateJsonPath(root: JSONObject, path: String): String {
        val tokens = path.split(".")
        var current: Any? = root

        for (token in tokens) {
            // Jika nilai saat ini berupa string JSON, parse otomatis agar akses field dalam JSON body berjalan mulus
            if (current is String) {
                val trimmed = current.trim()
                current = when {
                    trimmed.startsWith("{") -> runCatching { JSONObject(trimmed) }.getOrNull()
                    trimmed.startsWith("[") -> runCatching { JSONArray(trimmed) }.getOrNull()
                    else -> null
                } ?: return ""
            }

            current = when (current) {
                is JSONObject -> current.opt(token)
                is JSONArray -> {
                    val idx = token.toIntOrNull() ?: return ""
                    if (idx in 0 until current.length()) current.opt(idx) else return ""
                }
                else -> return ""
            } ?: return ""
        }
        return if (current == JSONObject.NULL) "" else current.toString()
    }
}
