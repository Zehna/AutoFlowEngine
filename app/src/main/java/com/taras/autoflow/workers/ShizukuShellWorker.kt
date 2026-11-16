package com.taras.autoflow.workers

import com.taras.autoflow.engine.ExecutionContext
import com.taras.autoflow.engine.NodeExecutionResult
import com.taras.autoflow.service.NodeWorker
import com.taras.autoflow.util.ShizukuHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class ShizukuShellWorker : NodeWorker {
    override suspend fun execute(parameters: Map<String, String>, context: ExecutionContext): NodeExecutionResult =
        withContext(Dispatchers.IO) {
            val command = parameters["command"] ?: return@withContext NodeExecutionResult.Failure("Perintah shell kosong")

            if (!ShizukuHelper.hasPermission()) {
                return@withContext NodeExecutionResult.Failure("Izin Shizuku belum diberikan")
            }

            try {
                val method = Shizuku::class.java.getMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process

                val stdout = process.inputStream.bufferedReader().readText()
                val stderr = process.errorStream.bufferedReader().readText()
                val exitCode = process.waitFor()

                if (exitCode == 0) {
                    NodeExecutionResult.Success(mapOf("stdout" to stdout))
                } else {
                    NodeExecutionResult.Failure("Shell exit $exitCode: $stderr")
                }
            } catch (e: Exception) {
                NodeExecutionResult.Failure("Eksekusi Shizuku gagal: ${e.message}")
            }
        }
}
