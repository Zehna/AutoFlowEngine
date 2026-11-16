package com.taras.autoflow.workers

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
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
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class NetworkHttpWorker(private val appContext: Context? = null) : NodeWorker {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun execute(parameters: Map<String, String>, context: ExecutionContext): NodeExecutionResult =
        withContext(Dispatchers.IO) {
            val url = parameters["url"]?.takeIf { it.isNotBlank() }
                ?: return@withContext NodeExecutionResult.Failure("URL wajib diisi")
            val method = (parameters["action"]?.takeIf { it.isNotBlank() } ?: parameters["method"])
                ?.uppercase() ?: "GET"
            val bodyPayload = parameters["body"] ?: ""
            val headersJson = parameters["headers_json"]
            val useSavedCookies = parameters["use_saved_cookies"]?.equals("true", ignoreCase = true) == true

            try {
                val requestBuilder = Request.Builder().url(url)

                if (!headersJson.isNullOrBlank()) {
                    runCatching { JSONObject(headersJson) }.getOrNull()?.let { hdr ->
                        hdr.keys().forEach { key ->
                            requestBuilder.header(key, hdr.optString(key))
                        }
                    }
                }

                if (useSavedCookies) {
                    val webCookie = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
                    val kvCookie = if (appContext != null) {
                        val host = runCatching { Uri.parse(url).host }.getOrNull()
                        if (!host.isNullOrBlank()) {
                            AutoFlowDatabase.getInstance(appContext).stateDao().getValueAnyNamespace("cookie_$host")
                        } else null
                    } else null

                    val cookieHeader = webCookie?.takeIf { it.isNotBlank() } ?: kvCookie
                    if (!cookieHeader.isNullOrBlank()) {
                        requestBuilder.header("Cookie", cookieHeader)
                    }
                }

                val jsonMediaType = "application/json; charset=utf-8".toMediaType()
                when (method) {
                    "POST", "POST_JSON" -> requestBuilder.post(bodyPayload.toRequestBody(jsonMediaType))
                    "PUT", "PUT_JSON" -> requestBuilder.put(bodyPayload.toRequestBody(jsonMediaType))
                    "PATCH" -> requestBuilder.patch(bodyPayload.toRequestBody(jsonMediaType))
                    "DELETE" -> if (bodyPayload.isNotEmpty()) {
                        requestBuilder.delete(bodyPayload.toRequestBody(jsonMediaType))
                    } else {
                        requestBuilder.delete()
                    }
                    else -> requestBuilder.get()
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    val respString = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        NodeExecutionResult.Success(
                            mapOf("code" to response.code, "body" to respString)
                        )
                    } else {
                        NodeExecutionResult.Failure(
                            "HTTP ${response.code}: $respString",
                            isRecoverable = response.code >= 500 || response.code == 429
                        )
                    }
                }
            } catch (e: Exception) {
                NodeExecutionResult.Failure("Koneksi gagal: ${e.message}", isRecoverable = true)
            }
        }
}
