package com.taras.autoflow.engine

import android.content.Context
import com.taras.autoflow.data.AutoFlowDatabase
import com.taras.autoflow.data.NodeExecutionLogEntity
import com.taras.autoflow.service.WorkerRegistry
import kotlinx.coroutines.delay
import org.json.JSONObject

class WorkflowOrchestrator(
    private val context: Context,
    private val registry: WorkerRegistry
) {
    private val database = AutoFlowDatabase.getInstance(context)

    suspend fun run(definition: WorkflowDefinition): Boolean {
        val ctx = ExecutionContext(definition.id)
        AutoFlowRuntime.markStarted()

        try {
            for (node in definition.nodes) {
                if (AutoFlowRuntime.isEmergencyStopActive()) {
                    recordLog(definition, node, "ABORTED", "{}", "{}", 0, "Emergency Stop triggered")
                    return false
                }

                AutoFlowRuntime.updateCurrentNode(node.id)
                var attempts = 0
                var isNodeSuccess = false

                while (attempts <= node.maxRetries && !isNodeSuccess) {
                    if (AutoFlowRuntime.isEmergencyStopActive()) return false

                    val start = System.currentTimeMillis()
                    val resolvedParams = node.parameters.mapValues { ctx.resolvePlaceholders(it.value) }
                    val inputPayload = JSONObject(resolvedParams).toString()

                    val worker = registry.resolveWorker(node.type)
                    val result = worker?.execute(resolvedParams, ctx)
                        ?: NodeExecutionResult.Failure("Tipe worker '${node.type}' tidak terdaftar")

                    val duration = System.currentTimeMillis() - start

                    when (result) {
                        is NodeExecutionResult.Success -> {
                            ctx.saveNodeOutput(node.id, result.output)
                            recordLog(definition, node, "SUCCESS", inputPayload, JSONObject(result.output).toString(), duration, null)
                            isNodeSuccess = true
                        }
                        is NodeExecutionResult.Failure -> {
                            attempts++
                            if (attempts <= node.maxRetries && result.isRecoverable) {
                                recordLog(definition, node, "RETRYING", inputPayload, "{}", duration, "Percobaan $attempts gagal: ${result.message}")
                                delay(node.retryDelayMs)
                            } else {
                                recordLog(definition, node, "FAILED", inputPayload, "{}", duration, result.message)
                            }
                        }
                    }
                }

                if (!isNodeSuccess && !node.continueOnError) {
                    return false
                }
            }
            return true
        } finally {
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
