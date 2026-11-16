package com.taras.autoflow.workers

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.taras.autoflow.data.WorkflowFileRepository
import com.taras.autoflow.engine.ExecutionContext
import com.taras.autoflow.engine.NodeExecutionResult
import com.taras.autoflow.service.NodeWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class PythonComputeWorker(private val appContext: Context) : NodeWorker {
    init {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(appContext))
        }
    }

    override suspend fun execute(parameters: Map<String, String>, context: ExecutionContext): NodeExecutionResult =
        withContext(Dispatchers.IO) {
            val action = parameters["action"]?.uppercase()?.ifBlank { null }
                ?: if (parameters.containsKey("module")) "CALL_MODULE" else "RUN_INLINE"
            val inputJson = parameters["input_json"] ?: "{}"
            val contextJson = context.getAllOutputsJson()

            try {
                val py = Python.getInstance()
                val bridge = py.getModule("workflow_bridge")

                val pyResponse = when (action) {
                    "CALL_MODULE" -> {
                        val moduleName = parameters["module"]?.takeIf { it.isNotBlank() }
                            ?: return@withContext NodeExecutionResult.Failure("Parameter 'module' wajib diisi untuk CALL_MODULE")
                        val funcName = parameters["function"]?.ifBlank { "run" } ?: "run"
                        val modulesDir = WorkflowFileRepository(appContext).pythonModulesDir.absolutePath
                        bridge.callAttr("call_module_function", modulesDir, moduleName, funcName, inputJson, contextJson).toString()
                    }
                    else -> {
                        val script = parameters["script"]
                            ?: return@withContext NodeExecutionResult.Failure("Parameter 'script' kosong untuk RUN_INLINE")
                        bridge.callAttr("execute_inline_script", script, inputJson, contextJson).toString()
                    }
                }

                val json = JSONObject(pyResponse)
                if (json.optBoolean("is_success")) {
                    val outputsObj = json.optJSONObject("outputs") ?: JSONObject()
                    val resultMap = mutableMapOf<String, Any?>()
                    outputsObj.keys().forEach { key -> resultMap[key] = outputsObj.get(key) }
                    resultMap["stdout"] = json.optString("stdout")
                    NodeExecutionResult.Success(resultMap)
                } else {
                    NodeExecutionResult.Failure(json.optString("error_message", "Python error"))
                }
            } catch (e: Exception) {
                NodeExecutionResult.Failure("Eksekusi Python gagal: ${e.message}")
            }
        }
}
