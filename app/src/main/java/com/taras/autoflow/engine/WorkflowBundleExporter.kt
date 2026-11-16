package com.taras.autoflow.engine

import android.content.Context
import com.taras.autoflow.data.WorkflowFileRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object WorkflowBundleExporter {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    private val sensitiveKeys = listOf("api_key", "token", "secret", "password", "cookie")

    fun exportBundleToCache(context: Context, definition: WorkflowDefinition): File {
        val exportDir = File(context.cacheDir, "shared_bundles").apply { mkdirs() }
        val safeName = definition.name.lowercase().replace(Regex("[^a-z0-9_-]+"), "_").trim('_')
            .ifEmpty { definition.id }
        val outFile = File(exportDir, "${safeName}.autoflow.zip")
        val pyDir = WorkflowFileRepository(context).pythonModulesDir
        packBundle(definition, outFile, pyDir)
        return outFile
    }

    fun packBundle(definition: WorkflowDefinition, outputFile: File, pythonModulesDir: File? = null) {
        val sanitizedNodes = definition.nodes.map { node ->
            val cleanParams = node.params.mapValues { (k, v) ->
                if (sensitiveKeys.any { k.contains(it, ignoreCase = true) }) JsonPrimitive("{{ENV_SECRET}}") else v
            }
            node.copy(params = cleanParams)
        }
        val sanitizedVars = definition.variables.mapValues { (k, v) ->
            if (sensitiveKeys.any { k.contains(it, ignoreCase = true) }) JsonPrimitive("{{ENV_SECRET}}") else v
        }
        val cleanDefinition = definition.copy(variables = sanitizedVars, nodes = sanitizedNodes)
        val jsonString = json.encodeToString(WorkflowDefinition.serializer(), cleanDefinition)

        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            zos.putNextEntry(ZipEntry("workflow.json"))
            zos.write(jsonString.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            if (pythonModulesDir != null && pythonModulesDir.exists()) {
                val referencedModules = definition.nodes
                    .filter { it.worker.equals("PYTHON_COMPUTE", ignoreCase = true) }
                    .mapNotNull { it.params["module"]?.asPlainString()?.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()

                referencedModules.forEach { modName ->
                    val fileName = if (modName.endsWith(".py")) modName else "$modName.py"
                    val pyFile = File(pythonModulesDir, fileName)
                    if (pyFile.exists() && pyFile.isFile) {
                        zos.putNextEntry(ZipEntry("python_modules/$fileName"))
                        zos.write(pyFile.readBytes())
                        zos.closeEntry()
                    }
                }
            }
        }
    }
}
