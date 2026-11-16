package com.taras.autoflow.data

import android.content.Context
import com.taras.autoflow.engine.WorkflowDefinition
import kotlinx.serialization.json.Json
import java.io.File

class WorkflowFileRepository(private val context: Context) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val workflowsDir: File
        get() = File(context.filesDir, "workflows").apply { mkdirs() }

    val pythonModulesDir: File
        get() = File(context.filesDir, "python_modules").apply { mkdirs() }

    private val db = AutoFlowDatabase.getInstance(context)

    suspend fun initializeAndSync() {
        val existingFiles = workflowsDir.listFiles { f -> f.isFile && f.extension.equals("json", ignoreCase = true) }
            ?.toList().orEmpty()

        if (existingFiles.isEmpty() && db.workflowDao().getWorkflowCount() == 0) {
            val defaultWorkflow = BuiltInWorkflowSeeds.getStarterWorkflows().first()
            saveWorkflow(defaultWorkflow)
        } else {
            // Sinkronkan setiap .json dari filesDir/workflows ke Room DB
            existingFiles.forEach { file ->
                runCatching {
                    val def = json.decodeFromString(WorkflowDefinition.serializer(), file.readText(Charsets.UTF_8))
                    val existingEntity = db.workflowDao().getWorkflowById(def.id)
                    db.workflowDao().upsertWorkflow(
                        WorkflowEntity.fromDefinition(
                            definition = def,
                            isScheduled = existingEntity?.isScheduled ?: false,
                            scheduleIntervalMinutes = existingEntity?.scheduleIntervalMinutes ?: 15L,
                            lastRunAt = existingEntity?.lastRunAt,
                            lastRunStatus = existingEntity?.lastRunStatus
                        )
                    )
                }
            }
        }
    }

    suspend fun saveWorkflow(definition: WorkflowDefinition) {
        val file = File(workflowsDir, "${sanitizeFileName(definition.id)}.json")
        file.writeText(json.encodeToString(WorkflowDefinition.serializer(), definition), Charsets.UTF_8)

        val existingEntity = db.workflowDao().getWorkflowById(definition.id)
        db.workflowDao().upsertWorkflow(
            WorkflowEntity.fromDefinition(
                definition = definition,
                isScheduled = existingEntity?.isScheduled ?: false,
                scheduleIntervalMinutes = existingEntity?.scheduleIntervalMinutes ?: 15L,
                lastRunAt = existingEntity?.lastRunAt,
                lastRunStatus = existingEntity?.lastRunStatus
            )
        )
    }

    suspend fun deleteWorkflow(workflowId: String) {
        val file = File(workflowsDir, "${sanitizeFileName(workflowId)}.json")
        if (file.exists()) file.delete()
        db.workflowDao().deleteWorkflowById(workflowId)
    }

    fun listPythonModules(): List<String> {
        return pythonModulesDir.listFiles { f -> f.isFile && f.extension.equals("py", ignoreCase = true) }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()
    }

    private fun sanitizeFileName(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "workflow" }
}
