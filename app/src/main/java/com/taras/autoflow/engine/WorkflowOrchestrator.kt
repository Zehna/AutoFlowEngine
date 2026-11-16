package com.taras.autoflow.engine

import android.content.Context
import com.taras.autoflow.data.AutoFlowDatabase
import com.taras.autoflow.data.NodeExecutionLogEntity
import com.taras.autoflow.service.WorkerRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

class WorkflowOrchestrator(
    private val context: Context,
    private val registry: WorkerRegistry
) {
    private val database = AutoFlowDatabase.getInstance(context)

    private val _state = MutableStateFlow<OrchestratorState>(OrchestratorState.Idle)
    val state: StateFlow<OrchestratorState> = _state.asStateFlow()

    val logs: StateFlow<List<String>> = AutoFlowRuntime.consoleLogs

    fun pause() {
        val cur = _state.value
        if (cur is OrchestratorState.Running) {
            AutoFlowRuntime.pause()
            _state.value = OrchestratorState.Paused(
                workflowId = cur.workflowId,
                workflowName = cur.workflowName,
                cycle = cur.cycle,
                stepIndex = cur.stepIndex,
                totalSteps = cur.totalSteps,
                currentNodeId = cur.currentNodeId,
                currentNodeName = cur.currentNodeName
            )
        }
    }

    fun resume() {
        val cur = _state.value
        if (cur is OrchestratorState.Paused) {
            AutoFlowRuntime.resume()
            _state.value = OrchestratorState.Running(
                workflowId = cur.workflowId,
                workflowName = cur.workflowName,
                cycle = cur.cycle,
                stepIndex = cur.stepIndex,
                totalSteps = cur.totalSteps,
                currentNodeId = cur.currentNodeId,
                currentNodeName = cur.currentNodeName
            )
        }
    }

    fun stop() {
        AutoFlowRuntime.triggerEmergencyStop()
    }

    fun resetToIdle() {
        if (!AutoFlowRuntime.isEngineActive.value) {
            _state.value = OrchestratorState.Idle
        }
    }

    private suspend fun awaitIfPaused() {
        while (AutoFlowRuntime.isPaused() && !AutoFlowRuntime.isEmergencyStopActive()) {
            coroutineContext.ensureActive()
            delay(150L)
        }
    }

    suspend fun run(definition: WorkflowDefinition): Boolean {
        if (definition.nodes.isEmpty()) {
            val reason = "Workflow '${definition.name}' tidak memiliki node untuk dijalankan."
            AutoFlowRuntime.appendLog("✕ $reason")
            _state.value = OrchestratorState.Failed(definition.id, definition.name, reason)
            return false
        }

        AutoFlowRuntime.markStarted()
        AutoFlowRuntime.appendLog("▶ Memulai workflow: ${definition.name} (${definition.executionPolicy.mode})")

        var currentCycle = 0
        var finalSuccess = true
        var failureReason = ""

        try {
            val policy = definition.executionPolicy
            val maxCycles = if (policy.mode == ExecutionMode.LOOP) {
                if (policy.maxIterations <= 0) Int.MAX_VALUE else policy.maxIterations
            } else 1

            while (currentCycle < maxCycles) {
                coroutineContext.ensureActive()
                if (AutoFlowRuntime.isEmergencyStopActive()) {
                    finalSuccess = false
                    failureReason = "Dihentikan pengguna"
                    break
                }

                currentCycle++
                if (policy.mode == ExecutionMode.LOOP) {
                    AutoFlowRuntime.appendLog("↻ Siklus Loop #$currentCycle")
                }

                val cycleResult = runSingleCycle(definition, currentCycle)
                if (!cycleResult.first) {
                    finalSuccess = false
                    failureReason = cycleResult.second
                    break
                }

                if (currentCycle < maxCycles && !AutoFlowRuntime.isEmergencyStopActive()) {
                    var waited = 0L
                    val delayTarget = policy.loopDelayMs.coerceAtLeast(100L)
                    while (waited < delayTarget && !AutoFlowRuntime.isEmergencyStopActive()) {
                        awaitIfPaused()
                        val chunk = minOf(150L, delayTarget - waited)
                        delay(chunk)
                        waited += chunk
                    }
                }
            }

            if (AutoFlowRuntime.isEmergencyStopActive()) {
                _state.value = OrchestratorState.Failed(definition.id, definition.name, "Dihentikan pengguna")
                return false
            }

            if (finalSuccess) {
                AutoFlowRuntime.appendLog("★ Workflow '${definition.name}' selesai ($currentCycle siklus).")
                _state.value = OrchestratorState.Completed(definition.id, definition.name, currentCycle)
            } else {
                _state.value = OrchestratorState.Failed(definition.id, definition.name, failureReason)
            }
            return finalSuccess
        } catch (cancel: CancellationException) {
            AutoFlowRuntime.appendLog("■ Workflow dibatalkan.")
            _state.value = OrchestratorState.Failed(definition.id, definition.name, "Dibatalkan")
            throw cancel
        } finally {
            val statusStr = when (_state.value) {
                is OrchestratorState.Completed -> "SUCCESS"
                is OrchestratorState.Failed -> if (AutoFlowRuntime.isEmergencyStopActive()) "ABORTED" else "FAILED"
                else -> "ABORTED"
            }
            runBlocking(Dispatchers.IO) {
                database.workflowDao().updateRunStatus(
                    id = definition.id,
                    runAt = System.currentTimeMillis(),
                    status = statusStr
                )
            }
            AutoFlowRuntime.markStopped()
        }
    }

    private suspend fun runSingleCycle(definition: WorkflowDefinition, cycle: Int): Pair<Boolean, String> {
        val ctx = ExecutionContext(
            workflowId = definition.id,
            cycle = cycle,
            variables = definition.variables,
            kvLookup = { key ->
                runBlocking(Dispatchers.IO) {
                    database.stateDao().getValueAnyNamespace(key)
                }
            }
        )

        val nodes = definition.nodes
        val nodeIndexById = nodes.withIndex().associate { it.value.id to it.index }
        var currentIndex = 0
        var executedStepsInCycle = 0
        val maxStepsGuard = nodes.size * 5

        while (currentIndex in nodes.indices && executedStepsInCycle < maxStepsGuard) {
            coroutineContext.ensureActive()
            awaitIfPaused()

            val node = nodes[currentIndex]
            if (AutoFlowRuntime.isEmergencyStopActive()) {
                recordLog(definition, node, "ABORTED", "{}", "{}", 0, "Emergency Stop")
                return false to "Emergency Stop"
            }

            executedStepsInCycle++
            val nodeDisplayName = node.name.ifBlank { node.id }
            AutoFlowRuntime.updateCurrentNode(node.id)
            _state.value = OrchestratorState.Running(
                workflowId = definition.id,
                workflowName = definition.name,
                cycle = cycle,
                stepIndex = currentIndex + 1,
                totalSteps = nodes.size,
                currentNodeId = node.id,
                currentNodeName = nodeDisplayName
            )

            if (!ctx.evaluateCondition(node.condition)) {
                AutoFlowRuntime.appendLog("↷ [${node.id}] Dilewati (kondisi tidak terpenuhi)")
                recordLog(definition, node, "SKIPPED", "{}", "{}", 0, "Condition false: ${node.condition}")
                currentIndex = resolveNextIndex(node, currentIndex, nodeIndexById) ?: break
                continue
            }

            var attempts = 0
            var isNodeSuccess = false
            var lastError = "Unknown error"

            while (attempts <= node.retry.maxAttempts && !isNodeSuccess) {
                coroutineContext.ensureActive()
                awaitIfPaused()
                if (AutoFlowRuntime.isEmergencyStopActive()) return false to "Emergency Stop"

                val start = System.currentTimeMillis()
                val resolvedParams = node.params.mapValues { (_, elem) ->
                    ctx.resolvePlaceholders(elem.asPlainString())
                }.toMutableMap()
                if (node.action.isNotBlank() && !resolvedParams.containsKey("action")) {
                    resolvedParams["action"] = node.action
                }

                val inputPayload = JSONObject(resolvedParams).toString()
                AutoFlowRuntime.appendLog("⚙ [${node.id}] ${node.worker}${if (node.action.isNotBlank()) "/${node.action}" else ""}...")

                val worker = registry.resolveWorker(node.worker)
                val result = worker?.execute(resolvedParams, ctx)
                    ?: NodeExecutionResult.Failure("Worker '${node.worker}' tidak terdaftar")

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
                        lastError = result.message
                        if (attempts <= node.retry.maxAttempts && result.isRecoverable) {
                            AutoFlowRuntime.appendLog("↻ [${node.id}] Retry $attempts/${node.retry.maxAttempts}: ${result.message}")
                            recordLog(
                                definition,
                                node,
                                "RETRYING",
                                inputPayload,
                                "{}",
                                duration,
                                "Percobaan $attempts gagal: ${result.message}"
                            )
                            delay(node.retry.delayMs)
                        } else {
                            AutoFlowRuntime.appendLog("✕ [${node.id}] Gagal: ${result.message}")
                            recordLog(definition, node, "FAILED", inputPayload, "{}", duration, result.message)
                        }
                    }
                }
            }

            if (!isNodeSuccess && !node.continueOnError) {
                return false to lastError
            }

            currentIndex = resolveNextIndex(node, currentIndex, nodeIndexById) ?: break
        }
        return true to ""
    }

    private fun resolveNextIndex(
        node: WorkflowNode,
        currentIndex: Int,
        nodeIndexById: Map<String, Int>
    ): Int? {
        val nextId = node.next?.trim()
        return when {
            nextId.isNullOrEmpty() -> currentIndex + 1
            nextId.equals("END", ignoreCase = true) -> null
            else -> nodeIndexById[nextId]
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
                nodeType = if (node.action.isNotBlank()) "${node.worker}:${node.action}" else node.worker,
                status = status,
                inputPayload = input,
                outputPayload = output,
                executionDurationMs = duration,
                errorTrace = error
            )
        )
    }
}
