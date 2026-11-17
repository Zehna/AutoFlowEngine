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
            BuiltInWorkflowSeeds.getStarterWorkflows().take(2).forEach { def ->
                saveWorkflow(def)
            }
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

        // Pastikan preset n8n Localhost otomatis hadir sekali saat upgrade ke v2.1.0
        val prefs = context.getSharedPreferences("autoflow_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("n8n_starter_seeded_v21", false)) {
            val n8nStarter = BuiltInWorkflowSeeds.getStarterWorkflows().firstOrNull {
                it.id == "wf_n8n_localhost_autostart"
            }
            if (n8nStarter != null && db.workflowDao().getWorkflowById(n8nStarter.id) == null) {
                saveWorkflow(n8nStarter)
            }
            prefs.edit().putBoolean("n8n_starter_seeded_v21", true).apply()
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
