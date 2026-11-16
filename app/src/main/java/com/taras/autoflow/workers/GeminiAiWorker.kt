package com.taras.autoflow.workers

import android.content.Context
import android.util.Base64
import com.taras.autoflow.data.AutoFlowDatabase
import com.taras.autoflow.engine.ExecutionContext
import com.taras.autoflow.engine.NodeExecutionResult
import com.taras.autoflow.service.NodeWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class GeminiAiWorker(private val appContext: Context? = null) : NodeWorker {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun execute(parameters: Map<String, String>, context: ExecutionContext): NodeExecutionResult =
        withContext(Dispatchers.IO) {
            var apiKey = parameters["api_key"]?.trim().orEmpty()
            if ((apiKey.isEmpty() || apiKey == "{{ENV_SECRET}}") && appContext != null) {
                apiKey = AutoFlowDatabase.getInstance(appContext).stateDao()
                    .getValueAnyNamespace("gemini_api_key").orEmpty()
            }
            if (apiKey.isEmpty() || apiKey == "{{ENV_SECRET}}") {
                return@withContext NodeExecutionResult.Failure(
                    "Gemini API Key belum diatur. Isi parameter 'api_key' atau simpan 'gemini_api_key' di KV Vault."
                )
            }

            val prompt = parameters["prompt"]?.takeIf { it.isNotBlank() }
                ?: return@withContext NodeExecutionResult.Failure("Prompt kosong")
            val model = parameters["model"]?.takeIf { it.isNotBlank() } ?: "gemini-2.5-flash"
            val action = parameters["action"]?.uppercase().orEmpty()
            val expectJson = action == "ANALYZE_JSON" ||
                parameters["response_mime_type"] == "application/json" ||
                parameters["json_mode"]?.equals("true", ignoreCase = true) == true
            val systemInstruction = parameters["system_instruction"]
            val imagePath = parameters["image_path"]

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

            val partsArray = JSONArray().apply {
                put(JSONObject().put("text", prompt))
                if (!imagePath.isNullOrBlank()) {
                    val imgFile = File(imagePath)
                    if (imgFile.exists() && imgFile.isFile) {
                        val mime = when (imgFile.extension.lowercase()) {
                            "png" -> "image/png"
                            "webp" -> "image/webp"
                            else -> "image/jpeg"
                        }
                        val b64 = Base64.encodeToString(imgFile.readBytes(), Base64.NO_WRAP)
                        put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", mime)
                                put("data", b64)
                            })
                        })
                    }
                }
            }

            val jsonBody = JSONObject().apply {
                if (!systemInstruction.isNullOrBlank()) {
                    put("system_instruction", JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
                    })
                }
                if (expectJson) {
                    put("generationConfig", JSONObject().apply {
                        put("responseMimeType", "application/json")
                    })
                }
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", partsArray)
                    })
                })
            }

            try {
                val req = Request.Builder()
                    .url(url)
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(req).execute().use { resp ->
                    val respStr = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        val respObj = JSONObject(respStr)
                        val text = respObj.optJSONArray("candidates")
                            ?.optJSONObject(0)
                            ?.optJSONObject("content")
                            ?.optJSONArray("parts")
                            ?.optJSONObject(0)
                            ?.optString("text") ?: ""

                        val output = mutableMapOf<String, Any?>("reply" to text, "model" to model)
                        if (expectJson) {
                            runCatching { JSONObject(text) }.getOrNull()?.let { parsed ->
                                parsed.keys().forEach { k -> output[k] = parsed.get(k) }
                            }
                        }
                        NodeExecutionResult.Success(output)
                    } else {
                        NodeExecutionResult.Failure(
                            "Gemini API Error (${resp.code}): $respStr",
                            isRecoverable = resp.code >= 500 || resp.code == 429
                        )
                    }
                }
            } catch (e: Exception) {
                NodeExecutionResult.Failure("Panggilan Gemini gagal: ${e.message}", isRecoverable = true)
            }
        }
}
