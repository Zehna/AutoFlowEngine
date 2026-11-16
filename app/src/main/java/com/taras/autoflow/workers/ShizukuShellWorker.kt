package com.taras.autoflow.workers

import com.taras.autoflow.engine.ExecutionContext
import com.taras.autoflow.engine.NodeExecutionResult
import com.taras.autoflow.service.NodeWorker
import com.taras.autoflow.util.ShizukuHelper
import kotlin.random.Random

class ShizukuShellWorker : NodeWorker {
    override suspend fun execute(parameters: Map<String, String>, context: ExecutionContext): NodeExecutionResult {
        val action = parameters["action"]?.uppercase()?.ifBlank { null }
            ?: if (parameters.containsKey("command")) "EXEC" else "EXEC"

        val command = when (action) {
            "EXEC" -> {
                parameters["command"]?.takeIf { it.isNotBlank() }
                    ?: return NodeExecutionResult.Failure("Parameter 'command' kosong")
            }
            "LAUNCH_COMPONENT", "LAUNCH_APP" -> {
                val component = parameters["component"]?.trim()
                val pkg = parameters["package"]?.trim()
                when {
                    !component.isNullOrEmpty() -> "am start -n $component"
                    !pkg.isNullOrEmpty() -> "monkey -p $pkg -c android.intent.category.LAUNCHER 1"
                    else -> return NodeExecutionResult.Failure("Parameter 'component' atau 'package' wajib diisi")
                }
            }
            "FORCE_STOP" -> {
                val pkg = parameters["package"]?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return NodeExecutionResult.Failure("Parameter 'package' wajib diisi untuk FORCE_STOP")
                "am force-stop $pkg"
            }
            "PUT_SETTING" -> {
                val ns = parameters["namespace"]?.lowercase()?.takeIf { it in listOf("system", "secure", "global") } ?: "system"
                val key = parameters["key"]?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return NodeExecutionResult.Failure("Parameter 'key' wajib diisi untuk PUT_SETTING")
                val value = parameters["value"]?.replace("'", "'\\''") ?: ""
                "settings put $ns $key '$value'"
            }
            "TAP", "CLICK" -> {
                val jitter = { (Random.nextFloat() - 0.5f) * 4f }
                val x = (parameters["x"]?.toFloatOrNull() ?: 540f) + jitter()
                val y = (parameters["y"]?.toFloatOrNull() ?: 960f) + jitter()
                "input tap ${x.toInt()} ${y.toInt()}"
            }
            "SWIPE" -> {
                val x1 = parameters["x"]?.toFloatOrNull() ?: parameters["x1"]?.toFloatOrNull() ?: 540f
                val y1 = parameters["y"]?.toFloatOrNull() ?: parameters["y1"]?.toFloatOrNull() ?: 1200f
                val x2 = parameters["end_x"]?.toFloatOrNull() ?: parameters["x2"]?.toFloatOrNull() ?: x1
                val y2 = parameters["end_y"]?.toFloatOrNull() ?: parameters["y2"]?.toFloatOrNull() ?: (y1 - 600f)
                val duration = parameters["duration_ms"]?.toLongOrNull() ?: 350L
                "input swipe ${x1.toInt()} ${y1.toInt()} ${x2.toInt()} ${y2.toInt()} $duration"
            }
            "TEXT" -> {
                val text = parameters["text"]?.replace("'", "'\\''") ?: ""
                "input text '$text'"
            }
            "KEYEVENT" -> {
                val code = parameters["keycode"]?.trim()?.takeIf { it.isNotEmpty() } ?: "4"
                "input keyevent $code"
            }
            else -> return NodeExecutionResult.Failure("Aksi SHIZUKU_SHELL '$action' tidak dikenal")
        }

        return ShizukuHelper.runShell(command).fold(
            onSuccess = { out ->
                if (out.exitCode == 0) {
                    NodeExecutionResult.Success(
                        mapOf("action" to action, "command" to command, "stdout" to out.stdout.trim())
                    )
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
            val worker = ShizukuShellWorker()
            return worker.execute(parameters, ExecutionContext("ui_shizuku"))
        }
    }
}
