package com.taras.autoflow.engine

import kotlinx.serialization.json.Json
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object WorkflowBundleImporter {
    private val json = Json { ignoreUnknownKeys = true }

    fun unpackBundle(zipFile: File, destinationDir: File): WorkflowDefinition? {
        val canonDestPath = destinationDir.canonicalPath
        var extractedDefinition: WorkflowDefinition? = null

        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val targetFile = File(destinationDir, entry.name)
                if (!targetFile.canonicalPath.startsWith(canonDestPath)) {
                    throw SecurityException("Zip Slip terdeteksi pada berkas: ${entry.name}")
                }

                if (entry.isDirectory) {
                    targetFile.mkdirs()
                } else {
                    targetFile.parentFile?.mkdirs()
                    val bytes = zis.readBytes()
                    if (entry.name == "workflow.json") {
                        extractedDefinition = json.decodeFromString<WorkflowDefinition>(String(bytes, Charsets.UTF_8))
                    }
                    FileOutputStream(targetFile).use { it.write(bytes) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return extractedDefinition
    }
}
