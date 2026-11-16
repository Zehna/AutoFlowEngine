package com.taras.autoflow.engine

import android.content.Context
import kotlinx.serialization.json.Json
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
        packBundle(definition, outFile)
        return outFile
    }

    fun packBundle(definition: WorkflowDefinition, outputFile: File) {
        val sanitizedNodes = definition.nodes.map { node ->
            val cleanParams = node.parameters.mapValues { (k, v) ->
                if (sensitiveKeys.any { k.contains(it, ignoreCase = true) }) "{{ENV_SECRET}}" else v
            }
            node.copy(parameters = cleanParams)
        }
        val sanitizedVars = definition.variables.mapValues { (k, v) ->
            if (sensitiveKeys.any { k.contains(it, ignoreCase = true) }) "{{ENV_SECRET}}" else v
        }
        val cleanDefinition = definition.copy(variables = sanitizedVars, nodes = sanitizedNodes)
        val jsonString = json.encodeToString(WorkflowDefinition.serializer(), cleanDefinition)

        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            val entry = ZipEntry("workflow.json")
            zos.putNextEntry(entry)
            zos.write(jsonString.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
    }
}
