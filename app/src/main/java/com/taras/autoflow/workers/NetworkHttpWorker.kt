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
import java.util.concurrent.TimeUnit

class NetworkHttpWorker : NodeWorker {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun execute(parameters: Map<String, String>, context: ExecutionContext): NodeExecutionResult =
        withContext(Dispatchers.IO) {
            val url = parameters["url"] ?: return@withContext NodeExecutionResult.Failure("URL wajib diisi")
            val method = parameters["method"]?.uppercase() ?: "GET"
            val bodyPayload = parameters["body"] ?: ""

            try {
                val requestBuilder = Request.Builder().url(url)
                if (method == "POST" || method == "PUT") {
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val reqBody = bodyPayload.toRequestBody(mediaType)
                    if (method == "POST") requestBuilder.post(reqBody) else requestBuilder.put(reqBody)
                } else {
                    requestBuilder.get()
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    val respString = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        NodeExecutionResult.Success(
                            mapOf("code" to response.code, "body" to respString)
                        )
                    } else {
                        NodeExecutionResult.Failure("HTTP ${response.code}: $respString", isRecoverable = response.code >= 500)
                    }
                }
            } catch (e: Exception) {
                NodeExecutionResult.Failure("Koneksi gagal: ${e.message}", isRecoverable = true)
            }
        }
}
