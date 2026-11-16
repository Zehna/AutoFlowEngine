package com.taras.autoflow.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object CsvVaultExporter {
    fun exportToCsv(context: Context, logs: List<NodeExecutionLogEntity>): File {
        val targetDir = File(context.cacheDir, "shared_bundles").apply { mkdirs() }
        val dateStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val csvFile = File(targetDir, "vault_logs_$dateStamp.csv")

        FileOutputStream(csvFile).bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF")
            writer.write("ID,ExecutedAt,WorkflowID,WorkflowName,NodeID,NodeType,Status,DurationMs,Input,Output,ErrorTrace\r\n")

            logs.forEach { log ->
                val line = listOf(
                    log.id.toString(),
                    log.executedAt.toString(),
                    formatCsvField(log.workflowId),
                    formatCsvField(log.workflowName),
                    formatCsvField(log.nodeId),
                    formatCsvField(log.nodeType),
                    formatCsvField(log.status),
                    log.executionDurationMs.toString(),
                    formatCsvField(log.inputPayload),
                    formatCsvField(log.outputPayload),
                    formatCsvField(log.errorTrace ?: "")
                ).joinToString(",")
                writer.write("$line\r\n")
            }
        }
        return csvFile
    }

    private fun formatCsvField(text: String): String {
        val sanitized = text.replace("\"", "\"\"")
        return "\"$sanitized\""
    }
}
