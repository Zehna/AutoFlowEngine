package com.taras.autoflow.engine

import android.content.Context
import android.net.Uri
import kotlinx.serialization.json.Json
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object WorkflowBundleImporter {
    private val json = Json { ignoreUnknownKeys = true }

    fun importFromUri(context: Context, uri: Uri): WorkflowDefinition {
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Gagal membuka berkas yang dipilih")

        // Deteksi header PK (\x50\x4b) untuk membedakan arsip ZIP dengan JSON mentah
        val isZip = rawBytes.size >= 2 && rawBytes[0] == 0x50.toByte() && rawBytes[1] == 0x4B.toByte()
        return if (isZip) {
            val extractDir = File(context.cacheDir, "imported_bundle_${System.currentTimeMillis()}").apply { mkdirs() }
            try {
                unpackStream(ByteArrayInputStream(rawBytes), extractDir)
                    ?: throw IllegalArgumentException("Berkas workflow.json tidak ditemukan di dalam arsip ZIP")
            } finally {
                extractDir.deleteRecursively()
            }
        } else {
            val content = String(rawBytes, Charsets.UTF_8).trim()
            runCatching {
                json.decodeFromString(WorkflowBundle.serializer(), content).workflow
            }.getOrElse {
                json.decodeFromString(WorkflowDefinition.serializer(), content)
            }
        }
    }

    fun unpackBundle(zipFile: File, destinationDir: File): WorkflowDefinition? {
        return FileInputStream(zipFile).use { unpackStream(it, destinationDir) }
    }

    private fun unpackStream(inputStream: InputStream, destinationDir: File): WorkflowDefinition? {
        val canonDestPath = destinationDir.canonicalPath + File.separator
        var extractedDefinition: WorkflowDefinition? = null

        ZipInputStream(inputStream).use { zis ->
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
                    if (entry.name == "workflow.json" || entry.name.endsWith("/workflow.json")) {
                        val raw = String(bytes, Charsets.UTF_8)
                        extractedDefinition = runCatching {
                            json.decodeFromString(WorkflowBundle.serializer(), raw).workflow
                        }.getOrElse {
                            json.decodeFromString(WorkflowDefinition.serializer(), raw)
                        }
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
