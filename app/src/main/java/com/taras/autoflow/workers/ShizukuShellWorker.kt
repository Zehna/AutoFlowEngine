package com.taras.autoflow.workers

import com.taras.autoflow.engine.ExecutionContext
import com.taras.autoflow.engine.NodeExecutionResult
import com.taras.autoflow.service.NodeWorker
import com.taras.autoflow.util.ShizukuHelper
import kotlin.random.Random

class ShizukuShellWorker : NodeWorker {
    override suspend fun execute(parameters: Map<String, String>, context: ExecutionContext): NodeExecutionResult {
        val command = parameters["command"]
            ?: return NodeExecutionResult.Failure("Perintah shell kosong")

        val result = ShizukuHelper.runShell(command)
        return result.fold(
            onSuccess = { out ->
                if (out.exitCode == 0) {
                    NodeExecutionResult.Success(mapOf("stdout" to out.stdout))
                } else {
                    NodeExecutionResult.Failure("Shell exit ${out.exitCode}: ${out.stderr}")
                }
            },
            onFailure = { err ->
                NodeExecutionResult.Failure("Eksekusi Shizuku gagal: ${err.message}")
            }
        )
    }

    companion object {
        suspend fun executeUiViaShizuku(parameters: Map<String, String>): NodeExecutionResult {
            val action = parameters["action"]?.uppercase() ?: "CLICK"
            val jitter = { (Random.nextFloat() - 0.5f) * 6f }

            val cmd = when (action) {
                "CLICK" -> {
                    val x = (parameters["x"]?.toFloatOrNull() ?: 500f) + jitter()
                    val y = (parameters["y"]?.toFloatOrNull() ?: 1000f) + jitter()
                    "input tap ${x.toInt()} ${y.toInt()}"
                }
                "SWIPE" -> {
                    val x1 = (parameters["x"]?.toFloatOrNull() ?: 500f) + jitter()
                    val y1 = (parameters["y"]?.toFloatOrNull() ?: 1000f) + jitter()
                    val x2 = (parameters["end_x"]?.toFloatOrNull() ?: x1) + jitter()
                    val y2 = (parameters["end_y"]?.toFloatOrNull() ?: (y1 - 600f)) + jitter()
                    val duration = parameters["duration_ms"]?.toLongOrNull() ?: 350L
                    "input swipe ${x1.toInt()} ${y1.toInt()} ${x2.toInt()} ${y2.toInt()} $duration"
                }
                "TEXT" -> {
                    val text = parameters["text"]?.replace("'", "'\\''") ?: ""
                    "input text '$text'"
                }
                "LAUNCH_APP" -> {
                    val pkg = parameters["package"] ?: return NodeExecutionResult.Failure("Parameter 'package' kosong")
                    "monkey -p $pkg -c android.intent.category.LAUNCHER 1"
                }
                else -> return NodeExecutionResult.Failure("Aksi UI '$action' tidak dikenal")
            }

            return ShizukuHelper.runShell(cmd).fold(
                onSuccess = { out ->
                    if (out.exitCode == 0) {
                        NodeExecutionResult.Success(mapOf("action" to action, "backend" to "shizuku", "stdout" to out.stdout))
                    } else {
                        NodeExecutionResult.Failure("Shizuku UI gagal (${out.exitCode}): ${out.stderr}")
                    }
                },
                onFailure = { err ->
                    NodeExecutionResult.Failure("UI Action butuh Shizuku aktif: ${err.message}")
                }
            )
        }
    }
}
