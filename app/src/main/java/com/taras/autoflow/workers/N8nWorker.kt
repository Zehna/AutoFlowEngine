package com.taras.autoflow.workers

import android.content.Context
import com.taras.autoflow.engine.AutoFlowRuntime
import com.taras.autoflow.engine.ExecutionContext
import com.taras.autoflow.engine.NodeExecutionResult
import com.taras.autoflow.n8n.N8nClient
import com.taras.autoflow.n8n.N8nConfig
import com.taras.autoflow.n8n.N8nConfigStore
import com.taras.autoflow.service.NodeWorker
import org.json.JSONArray
import org.json.JSONObject

class N8nWorker(private val appContext: Context) : NodeWorker {
    private val configStore = N8nConfigStore(appContext)
    private val client = N8nClient(appContext)

    override suspend fun execute(parameters: Map<String, String>, context: ExecutionContext): NodeExecutionResult {
        val action = (parameters["action"]?.takeIf { it.isNotBlank() } ?: "LIST_WORKFLOWS").uppercase()
        val stored = configStore.loadConfig()
        val config = mergeOverrides(stored, parameters)

        return when (action) {
            "ENSURE_SERVER", "START_SERVER" -> {
                val t0 = System.currentTimeMillis()
                val res = client.ensureServerRunning(config) { msg ->
                    AutoFlowRuntime.appendLog("  ↳ n8n: $msg")
                }
                res.fold(
                    onSuccess = { st ->
                        NodeExecutionResult.Success(
                            mapOf(
                                "status" to if (st.n8nRunning) "RUNNING" else "OFFLINE",
                                "url" to config.normalizedBaseUrl(),
                                "bridge_available" to st.bridgeAvailable,
                                "workflow_count" to st.workflowCount,
                                "pid" to (st.n8nPid ?: 0),
                                "elapsed_ms" to (System.currentTimeMillis() - t0),
                                "message" to st.message
                            )
                        )
                    },
                    onFailure = { err ->
                        NodeExecutionResult.Failure(err.message ?: "Gagal menjalankan server n8n", isRecoverable = true)
                    }
                )
            }

            "STOP_SERVER" -> {
                client.stopServer(config).fold(
                    onSuccess = { msg -> NodeExecutionResult.Success(mapOf("status" to "STOPPED", "message" to msg)) },
                    onFailure = { err -> NodeExecutionResult.Failure(err.message ?: "Gagal menghentikan n8n") }
                )
            }

            "LIST_WORKFLOWS" -> {
                if (!parameters["auto_start"].equals("false", ignoreCase = true)) {
                    client.ensureServerRunning(config) { msg ->
                        AutoFlowRuntime.appendLog("  ↳ n8n: $msg")
                    }.onFailure { err ->
                        AutoFlowRuntime.appendLog("  ⚠ Auto-start warning: ${err.message}")
                    }
                }
                client.listWorkflows(config).fold(
                    onSuccess = { list ->
                        val arr = JSONArray()
                        list.forEach { wf ->
                            arr.put(
                                JSONObject().apply {
                                    put("id", wf.id)
                                    put("name", wf.name)
                                    put("active", wf.active)
                                    put("nodeCount", wf.nodeCount)
                                    put("pipeline", wf.pipelinePreview)
                                    put("source", wf.source)
                                }
                            )
                        }
                        val summary = if (list.isEmpty()) {
                            "0 workflow ditemukan di n8n lokal"
                        } else {
                            list.joinToString("\n") { wf ->
                                "• [${if (wf.active) "AKTIF" else "NONAKTIF"}] ${wf.name} (id=${wf.id}, ${wf.nodeCount} node)"
                            }
                        }
                        val first = list.firstOrNull()
                        NodeExecutionResult.Success(
                            mapOf(
                                "count" to list.size,
                                "first_id" to (first?.id ?: ""),
                                "first_name" to (first?.name ?: ""),
                                "summary" to summary,
                                "workflows_json" to arr.toString()
                            )
                        )
                    },
                    onFailure = { err ->
                        NodeExecutionResult.Failure(err.message ?: "Gagal mengambil daftar workflow n8n", isRecoverable = true)
                    }
                )
            }

            "INSPECT_WORKFLOW" -> {
                val targetId = resolveTargetWorkflowId(config, parameters["workflow_id"])
                    ?: return NodeExecutionResult.Failure("Tidak ada workflow ditemukan di n8n lokal untuk diinspeksi")

                client.inspectWorkflow(config, targetId).fold(
                    onSuccess = { detail ->
                        val nodesArr = JSONArray()
                        detail.nodes.forEach { n ->
                            nodesArr.put(
                                JSONObject().apply {
                                    put("id", n.id)
                                    put("name", n.name)
                                    put("type", n.type)
                                    put("disabled", n.disabled)
                                    put("isTrigger", n.isTrigger)
                                }
                            )
                        }
                        val edgesArr = JSONArray()
                        detail.edges.forEach { e ->
                            edgesArr.put(
                                JSONObject().apply {
                                    put("from", e.from)
                                    put("to", e.to)
                                    put("type", e.type)
                                }
                            )
                        }
                        val pipeline = if (detail.edges.isNotEmpty()) {
                            detail.edges.joinToString(" | ") { "${it.from} → ${it.to}" }
                        } else {
                            detail.nodes.joinToString(" → ") { it.name }
                        }
                        val triggerList = detail.triggers.joinToString(", ") { "${it.name} (${it.type})" }
                        val notesText = detail.stickyNotes.joinToString("\n---\n") { "${it.name}: ${it.content}" }

                        NodeExecutionResult.Success(
                            mapOf(
                                "id" to detail.id,
                                "name" to detail.name,
                                "active" to detail.active,
                                "node_count" to detail.nodes.size,
                                "edge_count" to detail.edges.size,
                                "trigger_nodes" to triggerList,
                                "pipeline_text" to pipeline,
                                "sticky_notes" to notesText,
                                "nodes_json" to nodesArr.toString(),
                                "connections_json" to edgesArr.toString(),
                                "workflow_json" to detail.rawJson,
                                "source" to detail.source
                            )
                        )
                    },
                    onFailure = { err ->
                        NodeExecutionResult.Failure(err.message ?: "Gagal menginspeksi workflow '$targetId'")
                    }
                )
            }

            "LIST_EXECUTIONS" -> {
                val wfId = parameters["workflow_id"]?.trim()?.takeIf { it.isNotEmpty() }
                val limit = parameters["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 10
                client.listExecutions(config, wfId, limit).fold(
                    onSuccess = { execs ->
                        val arr = JSONArray()
                        execs.forEach { ex ->
                            arr.put(
                                JSONObject().apply {
                                    put("id", ex.id)
                                    put("workflowId", ex.workflowId)
                                    put("workflowName", ex.workflowName)
                                    put("status", ex.status)
                                    put("startedAt", ex.startedAt)
                                    put("lastNode", ex.lastNodeExecuted ?: "")
                                    put("error", ex.errorMessage ?: "")
                                }
                            )
                        }
                        val latest = execs.firstOrNull()
                        val summary = if (execs.isEmpty()) {
                            "Belum ada riwayat eksekusi di n8n lokal"
                        } else {
                            execs.take(5).joinToString("\n") { ex ->
                                "#${ex.id} [${ex.status}] ${ex.workflowName} (${ex.mode})"
                            }
                        }
                        NodeExecutionResult.Success(
                            mapOf(
                                "count" to execs.size,
                                "latest_id" to (latest?.id ?: ""),
                                "latest_status" to (latest?.status ?: ""),
                                "summary" to summary,
                                "executions_json" to arr.toString()
                            )
                        )
                    },
                    onFailure = { err ->
                        NodeExecutionResult.Failure(err.message ?: "Gagal mengambil riwayat eksekusi")
                    }
                )
            }

            "EXECUTE_WORKFLOW", "RUN_WORKFLOW" -> {
                val webhookPath = parameters["webhook_path"]?.trim()?.takeIf { it.isNotEmpty() }
                val targetId = resolveTargetWorkflowId(config, parameters["workflow_id"])
                    ?: return NodeExecutionResult.Failure("Parameter 'workflow_id' kosong dan tidak ada workflow lokal")
                val payload = parameters["payload_json"] ?: "{}"

                val res = client.executeWorkflow(
                    config = config,
                    workflowId = targetId,
                    webhookPath = webhookPath,
                    payloadJson = payload
                ) { msg ->
                    AutoFlowRuntime.appendLog("  ↳ n8n: $msg")
                }

                res.fold(
                    onSuccess = { outcome ->
                        if (outcome.success) {
                            NodeExecutionResult.Success(
                                mapOf(
                                    "execution_id" to (outcome.executionId ?: ""),
                                    "status" to outcome.status,
                                    "method" to outcome.method,
                                    "message" to outcome.message,
                                    "output_json" to outcome.outputJson
                                )
                            )
                        } else {
                            NodeExecutionResult.Failure(outcome.message)
                        }
                    },
                    onFailure = { err ->
                        NodeExecutionResult.Failure(err.message ?: "Eksekusi n8n gagal", isRecoverable = true)
                    }
                )
            }

            "ACTIVATE_WORKFLOW", "DEACTIVATE_WORKFLOW" -> {
                val targetId = parameters["workflow_id"]?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return NodeExecutionResult.Failure("Parameter 'workflow_id' wajib diisi")
                val active = action == "ACTIVATE_WORKFLOW"
                client.setWorkflowActive(config, targetId, active).fold(
                    onSuccess = {
                        NodeExecutionResult.Success(mapOf("workflow_id" to targetId, "active" to active))
                    },
                    onFailure = { err ->
                        NodeExecutionResult.Failure(err.message ?: "Gagal mengubah status aktif")
                    }
                )
            }

            else -> NodeExecutionResult.Failure("Aksi N8N_BRIDGE '$action' tidak dikenal")
        }
    }

    private suspend fun resolveTargetWorkflowId(config: N8nConfig, explicitId: String?): String? {
        val cleaned = explicitId?.trim()?.takeIf { it.isNotEmpty() }
        if (cleaned != null) return cleaned
        return client.listWorkflows(config).getOrNull()?.firstOrNull()?.id
    }

    private fun mergeOverrides(base: N8nConfig, params: Map<String, String>): N8nConfig {
        return base.copy(
            baseUrl = params["base_url"]?.takeIf { it.isNotBlank() } ?: base.baseUrl,
            bridgeUrl = params["bridge_url"]?.takeIf { it.isNotBlank() } ?: base.bridgeUrl,
            bridgeToken = params["bridge_token"]?.takeIf { it.isNotBlank() } ?: base.bridgeToken,
            apiKey = params["api_key"]?.takeIf { it.isNotBlank() } ?: base.apiKey,
            email = params["email"]?.takeIf { it.isNotBlank() } ?: base.email,
            password = params["password"]?.takeIf { it.isNotBlank() } ?: base.password
        )
    }
}
