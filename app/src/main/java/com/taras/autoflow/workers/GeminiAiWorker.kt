package com.taras.autoflow.workers

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

class GeminiAiWorker : NodeWorker {
    private val client = OkHttpClient()

    override suspend fun execute(parameters: Map<String, String>, context: ExecutionContext): NodeExecutionResult =
        withContext(Dispatchers.IO) {
            val apiKey = parameters["api_key"] ?: return@withContext NodeExecutionResult.Failure("Gemini API Key kosong")
            val prompt = parameters["prompt"] ?: return@withContext NodeExecutionResult.Failure("Prompt kosong")
            val model = parameters["model"] ?: "gemini-1.5-flash"

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        })
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

                        NodeExecutionResult.Success(mapOf("reply" to text))
                    } else {
                        NodeExecutionResult.Failure("Gemini API Error: $respStr")
                    }
                }
            } catch (e: Exception) {
                NodeExecutionResult.Failure("Panggilan Gemini gagal: ${e.message}")
            }
        }
}
