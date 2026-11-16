package com.taras.autoflow.engine

import android.content.Context
import com.taras.autoflow.data.AutoFlowDatabase
import com.taras.autoflow.data.NodeExecutionLogEntity
import com.taras.autoflow.service.WorkerRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

class WorkflowOrchestrator(
    private val context: Context,
    private val registry: WorkerRegistry
) {
    private val database = AutoFlowDatabase.getInstance(context)

    suspend fun run(definition: WorkflowDefinition): Boolean {
        val ctx = ExecutionContext(
            workflowId = definition.id,
            variables = definition.variables,
            kvLookup = { key ->
                runBlocking(Dispatchers.IO) {
                    database.stateDao().getValueAnyNamespace(key)
                }
            }
        )
        AutoFlowRuntime.markStarted(workflowName = definition.name)
        var overallStatus = "SUCCESS"

        try {
            val total = definition.nodes.size
            for ((index, node) in definition.nodes.withIndex()) {
                coroutineContext.ensureActive()
                if (AutoFlowRuntime.isEmergencyStopActive()) {
                    recordLog(definition, node, "ABORTED", "{}", "{}", 0, "Emergency Stop diminta")
                    overallStatus = "ABORTED"
                    return false
                }

                AutoFlowRuntime.updateCurrentNode(node.id, index + 1, total)

                // Evaluasi kondisi cabang sebelum mengeksekusi node
                if (!ctx.evaluateCondition(node.condition)) {
                    AutoFlowRuntime.appendLog("↷ [${node.id}] Dilewati (kondisi tidak terpenuhi)")
                    recordLog(definition, node, "SKIPPED", "{}", "{}", 0, "Condition false: ${node.condition}")
                    continue
                }

                var attempts = 0
                var isNodeSuccess = false

                while (attempts <= node.maxRetries && !isNodeSuccess) {
                    coroutineContext.ensureActive()
                    if (AutoFlowRuntime.isEmergencyStopActive()) {
                        overallStatus = "ABORTED"
                        return false
                    }

                    val start = System.currentTimeMillis()
                    val resolvedParams = node.parameters.mapValues { ctx.resolvePlaceholders(it.value) }
                    val inputPayload = JSONObject(resolvedParams).toString()

                    AutoFlowRuntime.appendLog("⚙ [${node.id}] Menjalankan ${node.type}...")
                    val worker = registry.resolveWorker(node.type)
                    val result = worker?.execute(resolvedParams, ctx)
                        ?: NodeExecutionResult.Failure("Tipe worker '${node.type}' tidak terdaftar")

                    val duration = System.currentTimeMillis() - start

                    when (result) {
                        is NodeExecutionResult.Success -> {
                            ctx.saveNodeOutput(node.id, result.output)
                            recordLog(
                                definition,
                                node,
                                "SUCCESS",
                                inputPayload,
                                JSONObject(result.output).toString(),
                                duration,
                                null
                            )
                            AutoFlowRuntime.appendLog("✓ [${node.id}] Selesai (${duration}ms)")
                            isNodeSuccess = true
                        }
                        is NodeExecutionResult.Failure -> {
                            attempts++
                            if (attempts <= node.maxRetries && result.isRecoverable) {
                                AutoFlowRuntime.appendLog("↻ [${node.id}] Retry $attempts/${node.maxRetries}: ${result.message}")
                                recordLog(
                                    definition,
                                    node,
                                    "RETRYING",
                                    inputPayload,
                                    "{}",
                                    duration,
                                    "Percobaan $attempts gagal: ${result.message}"
                                )
                                delay(node.retryDelayMs)
                            } else {
                                AutoFlowRuntime.appendLog("✕ [${node.id}] Gagal: ${result.message}")
                                recordLog(definition, node, "FAILED", inputPayload, "{}", duration, result.message)
                            }
                        }
                    }
                }

                if (!isNodeSuccess && !node.continueOnError) {
                    overallStatus = "FAILED"
                    return false
                }
            }
            AutoFlowRuntime.appendLog("★ Workflow '${definition.name}' selesai sempurna.")
            return true
        } catch (cancel: CancellationException) {
            overallStatus = "ABORTED"
            AutoFlowRuntime.appendLog("■ Workflow dibatalkan.")
            throw cancel
        } finally {
            runBlocking(Dispatchers.IO) {
                database.workflowDao().updateRunStatus(
                    id = definition.id,
                    runAt = System.currentTimeMillis(),
                    status = overallStatus
                )
            }
            AutoFlowRuntime.markStopped()
        }
    }

    private suspend fun recordLog(
        workflow: WorkflowDefinition,
        node: WorkflowNode,
        status: String,
        input: String,
        output: String,
        duration: Long,
        error: String?
    ) {
        database.logDao().recordLog(
            NodeExecutionLogEntity(
                workflowId = workflow.id,
                workflowName = workflow.name,
                nodeId = node.id,
                nodeType = node.type,
                status = status,
                inputPayload = input,
                outputPayload = output,
                executionDurationMs = duration,
                errorTrace = error
            )
        )
    }
}
