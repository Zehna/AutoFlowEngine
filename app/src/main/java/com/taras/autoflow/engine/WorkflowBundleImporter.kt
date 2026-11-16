package com.taras.autoflow.engine

import android.content.Context
import android.net.Uri
import com.taras.autoflow.data.WorkflowFileRepository
import kotlinx.serialization.json.Json
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class ImportedBundleResult(
    val workflows: List<WorkflowDefinition>,
    val pythonModulesExtracted: List<String>
)

object WorkflowBundleImporter {
    private val json = Json { ignoreUnknownKeys = true }

    fun importFromUri(context: Context, uri: Uri): ImportedBundleResult {
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Gagal membuka berkas yang dipilih")

        val repo = WorkflowFileRepository(context)
        val isZip = rawBytes.size >= 2 && rawBytes[0] == 0x50.toByte() && rawBytes[1] == 0x4B.toByte()

        return if (isZip) {
            val tempDir = File(context.cacheDir, "import_stage_${System.currentTimeMillis()}").apply { mkdirs() }
            try {
                unpackStream(
                    inputStream = ByteArrayInputStream(rawBytes),
                    stageDir = tempDir,
                    pythonModulesDir = repo.pythonModulesDir
                )
            } finally {
                tempDir.deleteRecursively()
            }
        } else {
            val content = String(rawBytes, Charsets.UTF_8).trim()
            val def = decodeWorkflowJson(content)
            ImportedBundleResult(workflows = listOf(def), pythonModulesExtracted = emptyList())
        }
    }

    fun unpackBundle(zipFile: File, destinationDir: File): WorkflowDefinition? {
        return FileInputStream(zipFile).use {
            unpackStream(it, destinationDir, File(destinationDir, "python_modules").apply { mkdirs() })
                .workflows.firstOrNull()
        }
    }

    private fun unpackStream(
        inputStream: InputStream,
        stageDir: File,
        pythonModulesDir: File
    ): ImportedBundleResult {
        val canonStagePath = stageDir.canonicalPath + File.separator
        val canonPythonPath = pythonModulesDir.canonicalPath + File.separator
        val extractedWorkflows = mutableListOf<WorkflowDefinition>()
        val extractedPyModules = mutableListOf<String>()

        ZipInputStream(inputStream).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name
                val targetFile = File(stageDir, entryName)
                if (!targetFile.canonicalPath.startsWith(canonStagePath)) {
                    throw SecurityException("Zip Slip terdeteksi pada berkas: $entryName")
                }

                if (!entry.isDirectory) {
                    val bytes = zis.readBytes()
                    val fileName = File(entryName).name

                    if (fileName.endsWith(".json", ignoreCase = true)) {
                        runCatching {
                            decodeWorkflowJson(String(bytes, Charsets.UTF_8))
                        }.onSuccess { def ->
                            extractedWorkflows.add(def)
                        }
                    } else if (fileName.endsWith(".py", ignoreCase = true)) {
                        val safePyFile = File(pythonModulesDir, fileName)
                        if (!safePyFile.canonicalPath.startsWith(canonPythonPath)) {
                            throw SecurityException("Zip Slip terdeteksi pada modul Python: $entryName")
                        }
                        safePyFile.parentFile?.mkdirs()
                        FileOutputStream(safePyFile).use { it.write(bytes) }
                        extractedPyModules.add(fileName)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        if (extractedWorkflows.isEmpty()) {
            throw IllegalArgumentException("Tidak ditemukan definisi workflow (.json) yang valid di dalam berkas.")
        }
        return ImportedBundleResult(extractedWorkflows, extractedPyModules)
    }

    private fun decodeWorkflowJson(raw: String): WorkflowDefinition {
        return runCatching {
            json.decodeFromString(WorkflowBundle.serializer(), raw).workflow
        }.getOrElse {
            json.decodeFromString(WorkflowDefinition.serializer(), raw)
        }
    }
}
