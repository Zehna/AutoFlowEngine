package com.taras.autoflow.n8n

enum class N8nAutoStartMode(val label: String) {
    AUTO("Otomatis (Bridge → Shizuku → Termux)"),
    BRIDGE("Local Bridge (:15678 / Omalauncher)"),
    SHIZUKU("Shizuku Shell"),
    TERMUX("Termux RUN_COMMAND"),
    MANUAL("Manual (Jangan auto-start)");

    companion object {
        fun fromString(raw: String?): N8nAutoStartMode =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: AUTO
    }
}

data class N8nConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val bridgeUrl: String = DEFAULT_BRIDGE_URL,
    val bridgeToken: String = "",
    val apiKey: String = "",
    val email: String = "",
    val password: String = "",
    val autoStartMode: N8nAutoStartMode = N8nAutoStartMode.AUTO,
    val shizukuStartCommand: String = "",
    val termuxCommand: String = "n8n start",
    val bootTimeoutSeconds: Int = 150
) {
    companion object {
        const val DEFAULT_BASE_URL = "http://127.0.0.1:5678"
        const val DEFAULT_BRIDGE_URL = "http://127.0.0.1:15678"
    }

    fun normalizedBaseUrl(): String = baseUrl.trim().trimEnd('/').ifBlank { DEFAULT_BASE_URL }
    fun normalizedBridgeUrl(): String = bridgeUrl.trim().trimEnd('/').ifBlank { DEFAULT_BRIDGE_URL }
}

data class N8nServerStatus(
    val n8nRunning: Boolean = false,
    val n8nBooting: Boolean = false,
    val bridgeAvailable: Boolean = false,
    val n8nPid: Int? = null,
    val bootElapsedSeconds: Int = 0,
    val workflowCount: Int = 0,
    val bridgeVersion: String = "",
    val message: String = "Belum diperiksa",
    val bridgeError: String? = null,
    val logTail: List<String> = emptyList()
)

data class N8nTriggerInfo(
    val name: String,
    val type: String,
    val webhookPath: String = ""
)

data class N8nWorkflowSummary(
    val id: String,
    val name: String,
    val description: String = "",
    val active: Boolean = false,
    val nodeCount: Int = 0,
    val edgeCount: Int = 0,
    val nodeTypes: List<String> = emptyList(),
    val triggers: List<N8nTriggerInfo> = emptyList(),
    val pipelinePreview: String = "",
    val tags: List<String> = emptyList(),
    val updatedAt: String = "",
    val source: String = "api"
)

data class N8nNodeInfo(
    val id: String,
    val name: String,
    val type: String,
    val typeVersion: String = "1",
    val disabled: Boolean = false,
    val parametersJson: String = "{}",
    val credentialTypes: List<String> = emptyList(),
    val notes: String = "",
    val isTrigger: Boolean = false
)

data class N8nEdgeInfo(
    val from: String,
    val to: String,
    val type: String = "main",
    val branch: Int = 0
)

data class N8nStickyNote(
    val name: String,
    val content: String
)

data class N8nWorkflowDetail(
    val id: String,
    val name: String,
    val description: String = "",
    val active: Boolean = false,
    val updatedAt: String = "",
    val nodes: List<N8nNodeInfo> = emptyList(),
    val edges: List<N8nEdgeInfo> = emptyList(),
    val triggers: List<N8nTriggerInfo> = emptyList(),
    val stickyNotes: List<N8nStickyNote> = emptyList(),
    val settingsJson: String = "{}",
    val rawJson: String = "{}",
    val source: String = "api"
)

data class N8nExecutionNodeSummary(
    val nodeName: String,
    val status: String,
    val executionTimeMs: Long = 0L,
    val itemCount: Int = 0,
    val outputPreview: String = "",
    val error: String? = null
)

data class N8nExecutionInfo(
    val id: String,
    val workflowId: String,
    val workflowName: String = "",
    val status: String = "",
    val mode: String = "",
    val finished: Boolean = false,
    val startedAt: String = "",
    val stoppedAt: String = "",
    val lastNodeExecuted: String? = null,
    val errorMessage: String? = null,
    val nodes: List<N8nExecutionNodeSummary> = emptyList(),
    val source: String = "api"
)

data class N8nExecuteOutcome(
    val success: Boolean,
    val executionId: String? = null,
    val status: String = "",
    val method: String = "",
    val message: String = "",
    val outputJson: String = "{}",
    val executionDetail: N8nExecutionInfo? = null
)
