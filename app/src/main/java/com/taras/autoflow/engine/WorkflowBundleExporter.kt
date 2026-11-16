package com.taras.autoflow.engine

import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object WorkflowBundleExporter {
    private val json = Json { prettyPrint = true }

    fun packBundle(definition: WorkflowDefinition, outputFile: File) {
        val sanitizedNodes = definition.nodes.map { node ->
            val cleanParams = node.parameters.mapValues { (k, v) ->
                if (k.contains("api_key", ignoreCase = true) || k.contains("token", ignoreCase = true)) {
                    "{{ENV_SECRET}}"
                } else v
            }
            node.copy(parameters = cleanParams)
        }
        val cleanDefinition = definition.copy(nodes = sanitizedNodes)
        val jsonString = json.encodeToString(WorkflowDefinition.serializer(), cleanDefinition)

        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            val entry = ZipEntry("workflow.json")
            zos.putNextEntry(entry)
            zos.write(jsonString.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
    }
}
