package com.taras.autoflow.engine

import org.json.JSONObject

class ExecutionContext(
    val workflowId: String,
    private val outputsMap: MutableMap<String, JSONObject> = mutableMapOf()
) {
    fun saveNodeOutput(nodeId: String, data: Map<String, Any?>) {
        outputsMap[nodeId] = JSONObject(data)
    }

    fun getNodeOutput(nodeId: String): JSONObject? = outputsMap[nodeId]

    fun resolvePlaceholders(template: String): String {
        val pattern = Regex("\\{\\{([a-zA-Z0-9_-]+)\\.([a-zA-Z0-9_.-]+)\\}\\}")
        return pattern.replace(template) { match ->
            val targetNode = match.groupValues[1]
            val path = match.groupValues[2]

            val nodeJson = outputsMap[targetNode] ?: return@replace ""
            evaluateJsonPath(nodeJson, path)
        }
    }

    private fun evaluateJsonPath(root: JSONObject, path: String): String {
        val tokens = path.split(".")
        var current: Any = root

        for (token in tokens) {
            when (current) {
                is JSONObject -> current = current.opt(token) ?: return ""
                else -> return ""
            }
        }
        return current.toString()
    }
}
