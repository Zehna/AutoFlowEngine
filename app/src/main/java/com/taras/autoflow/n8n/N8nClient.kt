package com.taras.autoflow.n8n

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.taras.autoflow.engine.AutoFlowRuntime
import com.taras.autoflow.util.ShizukuHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class N8nClient(private val context: Context) {
    private val appContext = context.applicationContext
    private val configStore = N8nConfigStore(appContext)

    private val shortClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val apiClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()

    private val execClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(165, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun probeServerStatus(config: N8nConfig): N8nServerStatus = withContext(Dispatchers.IO) {
        val baseUrl = config.normalizedBaseUrl()
        val bridgeUrl = config.normalizedBridgeUrl()

        val directUp = isHealthzUp(baseUrl)
        val bridgeJson = fetchBridgeHealth(bridgeUrl, config.bridgeToken)

        if (bridgeJson != null) {
            val running = directUp || bridgeJson.optBoolean("n8nRunning", false)
            val booting = !running && bridgeJson.optBoolean("n8nBooting", false)
            val pid = if (bridgeJson.isNull("n8nPid")) null else bridgeJson.optInt("n8nPid")
            val elapsed = bridgeJson.optInt("bootElapsedSeconds", 0)
            val wfCount = bridgeJson.optInt("workflowCount", 0)
            val logs = if (booting) fetchBridgeLogs(config, 8) else emptyList()
            val msg = when {
                running -> "Server n8n aktif di $baseUrl (Bridge terhubung)"
                booting -> "Server n8n sedang startup (${elapsed}s)..."
                else -> "Bridge aktif (:15678), n8n standby — siap auto-start"
            }
            return@withContext N8nServerStatus(
                n8nRunning = running,
                n8nBooting = booting,
                bridgeAvailable = true,
                n8nPid = pid,
                bootElapsedSeconds = elapsed,
                workflowCount = wfCount,
                bridgeVersion = bridgeJson.optString("version", "1.0.0"),
                message = msg,
                logTail = logs
            )
        }

        val msg = if (directUp) {
            "Server n8n aktif di $baseUrl (Koneksi langsung)"
        } else {
            "Server n8n offline di $baseUrl • Bridge lokal belum terdeteksi"
        }
        N8nServerStatus(
            n8nRunning = directUp,
            n8nBooting = false,
            bridgeAvailable = false,
            message = msg
        )
    }

    suspend fun ensureServerRunning(
        config: N8nConfig,
        onProgress: (String) -> Unit = {}
    ): Result<N8nServerStatus> = withContext(Dispatchers.IO) {
        val baseUrl = config.normalizedBaseUrl()
        if (isHealthzUp(baseUrl)) {
            val st = probeServerStatus(config)
            return@withContext Result.success(st)
        }

        if (config.autoStartMode == N8nAutoStartMode.MANUAL) {
            return@withContext Result.failure(
                IllegalStateException("n8n di $baseUrl sedang offline dan Auto-Start disetel Manual.")
            )
        }

        val startedVia = triggerAutoStart(config, onProgress)
            ?: return@withContext Result.failure(
                IllegalStateException(
                    "n8n offline & tidak ada auto-starter aktif. Pasang Bridge lokal (:15678), aktifkan Shizuku, atau jalankan 'n8n start' di terminal."
                )
            )

        onProgress("Auto-start dipicu via $startedVia, menunggu /healthz siap...")

        val maxSeconds = config.bootTimeoutSeconds.coerceIn(20, 300)
        val startedAtMs = System.currentTimeMillis()
        var lastLog = ""
        var sawBooting = false

        while (true) {
            currentCoroutineContext().ensureActive()
            if (AutoFlowRuntime.isEmergencyStopActive()) {
                return@withContext Result.failure(IllegalStateException("Dihentikan pengguna saat menunggu n8n boot"))
            }

            if (isHealthzUp(baseUrl)) {
                val st = probeServerStatus(config)
                onProgress("Server n8n siap di $baseUrl!")
                return@withContext Result.success(st)
            }

            val elapsedSec = ((System.currentTimeMillis() - startedAtMs) / 1000L).toInt()
            if (elapsedSec >= maxSeconds) {
                val tail = fetchBridgeLogs(config, 10).joinToString(" | ")
                return@withContext Result.failure(
                    IllegalStateException("Timeout menunggu n8n boot setelah ${maxSeconds}s. Log: $tail")
                )
            }

            val bridgeHealth = fetchBridgeHealth(config.normalizedBridgeUrl(), config.bridgeToken)
            if (bridgeHealth != null) {
                val booting = bridgeHealth.optBoolean("n8nBooting", false)
                if (booting) sawBooting = true
                if (sawBooting && !booting && !bridgeHealth.optBoolean("n8nRunning", false) && elapsedSec > 3) {
                    val tail = fetchBridgeLogs(config, 8).joinToString(" | ")
                    return@withContext Result.failure(
                        IllegalStateException("Proses n8n berhenti sebelum siap: $tail")
                    )
                }
            }

            val tailLines = fetchBridgeLogs(config, 3)
            val newestLine = tailLines.lastOrNull()?.trim().orEmpty()
            if (newestLine.isNotEmpty() && newestLine != lastLog) {
                lastLog = newestLine
                onProgress("Booting n8n (${elapsedSec}s): $newestLine")
            } else if (elapsedSec % 5 == 0) {
                onProgress("Menunggu server n8n (${elapsedSec}s/${maxSeconds}s)...")
            }

            delay(1500L)
        }
        @Suppress("UNREACHABLE_CODE")
        Result.failure(IllegalStateException("Unexpected exit"))
    }

    private suspend fun triggerAutoStart(
        config: N8nConfig,
        onProgress: (String) -> Unit
    ): String? {
        val mode = config.autoStartMode

        if (mode == N8nAutoStartMode.AUTO || mode == N8nAutoStartMode.BRIDGE) {
            val bridgeUrl = config.normalizedBridgeUrl()
            if (fetchBridgeHealth(bridgeUrl, config.bridgeToken) != null) {
                val resp = postBridge(bridgeUrl, "/n8n/start", config.bridgeToken)
                if (resp.first in 200..299) {
                    return "Local Bridge (:15678)"
                } else if (mode == N8nAutoStartMode.BRIDGE) {
                    onProgress("Bridge menolak /n8n/start (HTTP ${resp.first}): ${resp.second}")
                    return null
                }
            } else if (mode == N8nAutoStartMode.BRIDGE) {
                return null
            }
        }

        if (mode == N8nAutoStartMode.AUTO || mode == N8nAutoStartMode.SHIZUKU) {
            val cmd = config.shizukuStartCommand.trim()
            if (ShizukuHelper.hasPermission() && cmd.isNotEmpty()) {
                val shellRes = ShizukuHelper.runShell("nohup $cmd >/dev/null 2>&1 &")
                if (shellRes.isSuccess) {
                    return "Shizuku Shell"
                }
            } else if (mode == N8nAutoStartMode.SHIZUKU) {
                return null
            }
        }

        if (mode == N8nAutoStartMode.AUTO || mode == N8nAutoStartMode.TERMUX) {
            if (isPackageInstalled("com.termux")) {
                val ok = launchTermuxCommand(config.termuxCommand)
                if (ok) return "Termux RUN_COMMAND"
            }
        }

        return null
    }

    suspend fun stopServer(config: N8nConfig): Result<String> = withContext(Dispatchers.IO) {
        val bridgeUrl = config.normalizedBridgeUrl()
        val res = postBridge(bridgeUrl, "/n8n/stop", config.bridgeToken)
        if (res.first in 200..299) {
            Result.success("Server n8n dihentikan")
        } else {
            Result.failure(IllegalStateException("Gagal menghentikan via Bridge (HTTP ${res.first}): ${res.second}"))
        }
    }

    suspend fun fetchBridgeLogs(config: N8nConfig, lines: Int = 40): List<String> = withContext(Dispatchers.IO) {
        val bridgeUrl = config.normalizedBridgeUrl()
        val reqBuilder = Request.Builder().url("$bridgeUrl/n8n/logs?lines=$lines").get()
        if (config.bridgeToken.isNotBlank()) {
            reqBuilder.header("X-Bridge-Token", config.bridgeToken)
        }
        runCatching {
            shortClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val body = resp.body?.string().orEmpty()
                val arr = JSONObject(body).optJSONArray("lines") ?: return@use emptyList()
                List(arr.length()) { idx -> arr.optString(idx) }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun loginAndCreateApiKey(config: N8nConfig): Result<String> = withContext(Dispatchers.IO) {
        val baseUrl = config.normalizedBaseUrl()
        val cookie = loginWithEmailPassword(config).getOrElse { return@withContext Result.failure(it) }

        val scopesReq = Request.Builder()
            .url("$baseUrl/rest/api-keys/scopes")
            .header("Cookie", cookie)
            .get()
            .build()

        val scopesArr = runCatching {
            apiClient.newCall(scopesReq).execute().use { resp ->
                if (!resp.isSuccessful) return@use JSONArray()
                val raw = resp.body?.string().orEmpty()
                val root = JSONObject(raw)
                root.optJSONArray("data") ?: JSONArray()
            }
        }.getOrElse { JSONArray() }

        val payload = JSONObject().apply {
            put("label", "AutoFlow Engine (${System.currentTimeMillis() / 1000})")
            put("scopes", scopesArr)
            put("expiresAt", JSONObject.NULL)
        }
        val createReq = Request.Builder()
            .url("$baseUrl/rest/api-keys")
            .header("Cookie", cookie)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            apiClient.newCall(createReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("HTTP ${resp.code}: $raw"))
                }
                val root = JSONObject(raw)
                val data = root.optJSONObject("data") ?: root
                val rawKey = data.optString("rawApiKey").takeIf { it.isNotBlank() }
                    ?: return@withContext Result.failure(IllegalStateException("rawApiKey tidak ditemukan di respons"))
                configStore.saveApiKey(rawKey)
                Result.success(rawKey)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listWorkflows(config: N8nConfig): Result<List<N8nWorkflowSummary>> = withContext(Dispatchers.IO) {
        val baseUrl = config.normalizedBaseUrl()
        val n8nUp = isHealthzUp(baseUrl)

        if (n8nUp && config.apiKey.isNotBlank()) {
            val res = requestN8n(
                url = "$baseUrl/api/v1/workflows?limit=100",
                apiKey = config.apiKey
            )
            if (res.isSuccess) {
                val parsed = parseWorkflowListJson(res.getOrThrow(), source = "public_api")
                return@withContext Result.success(parsed)
            }
        }

        if (n8nUp) {
            val cookie = resolveOrLoginSessionCookie(config)
            if (!cookie.isNullOrBlank()) {
                val res = requestN8n(url = "$baseUrl/rest/workflows", cookie = cookie)
                if (res.isSuccess) {
                    val parsed = parseWorkflowListJson(res.getOrThrow(), source = "rest_session")
                    return@withContext Result.success(parsed)
                }
            }
        }

        val bridgeRes = getBridge(config.normalizedBridgeUrl(), "/n8n/inspect/workflows", config.bridgeToken)
        if (bridgeRes.first in 200..299) {
            val parsed = parseWorkflowListJson(bridgeRes.second, source = "bridge_sqlite")
            return@withContext Result.success(parsed)
        }

        Result.failure(
            IllegalStateException(
                if (!n8nUp) "n8n offline dan Bridge (:15678) tidak aktif."
                else "Gagal mengakses daftar workflow. Isi API Key / Email+Password n8n atau aktifkan Bridge lokal."
            )
        )
    }

    suspend fun inspectWorkflow(config: N8nConfig, workflowId: String): Result<N8nWorkflowDetail> = withContext(Dispatchers.IO) {
        val baseUrl = config.normalizedBaseUrl()
        val n8nUp = isHealthzUp(baseUrl)

        if (n8nUp && config.apiKey.isNotBlank()) {
            val res = requestN8n(url = "$baseUrl/api/v1/workflows/$workflowId", apiKey = config.apiKey)
            if (res.isSuccess) {
                return@withContext Result.success(parseWorkflowDetailJson(res.getOrThrow(), source = "public_api"))
            }
        }

        if (n8nUp) {
            val cookie = resolveOrLoginSessionCookie(config)
            if (!cookie.isNullOrBlank()) {
                val res = requestN8n(url = "$baseUrl/rest/workflows/$workflowId", cookie = cookie)
                if (res.isSuccess) {
                    return@withContext Result.success(parseWorkflowDetailJson(res.getOrThrow(), source = "rest_session"))
                }
            }
        }

        val bridgeRes = getBridge(config.normalizedBridgeUrl(), "/n8n/inspect/workflows/$workflowId", config.bridgeToken)
        if (bridgeRes.first in 200..299) {
            return@withContext Result.success(parseWorkflowDetailJson(bridgeRes.second, source = "bridge_sqlite"))
        }

        Result.failure(IllegalStateException("Gagal menginspeksi workflow '$workflowId' (HTTP ${bridgeRes.first})"))
    }

    suspend fun listExecutions(
        config: N8nConfig,
        workflowId: String? = null,
        limit: Int = 20
    ): Result<List<N8nExecutionInfo>> = withContext(Dispatchers.IO) {
        val bridgePath = buildString {
            append("/n8n/inspect/executions?limit=$limit")
            if (!workflowId.isNullOrBlank()) append("&workflowId=$workflowId")
        }
        val bridgeRes = getBridge(config.normalizedBridgeUrl(), bridgePath, config.bridgeToken)
        if (bridgeRes.first in 200..299) {
            return@withContext Result.success(parseExecutionsListJson(bridgeRes.second, source = "bridge_sqlite"))
        }

        val baseUrl = config.normalizedBaseUrl()
        if (isHealthzUp(baseUrl)) {
            if (config.apiKey.isNotBlank()) {
                val q = buildString {
                    append("$baseUrl/api/v1/executions?limit=$limit&includeData=true")
                    if (!workflowId.isNullOrBlank()) append("&workflowId=$workflowId")
                }
                val res = requestN8n(url = q, apiKey = config.apiKey)
                if (res.isSuccess) {
                    return@withContext Result.success(parseExecutionsListJson(res.getOrThrow(), source = "public_api"))
                }
            }
            val cookie = resolveOrLoginSessionCookie(config)
            if (!cookie.isNullOrBlank()) {
                val q = buildString {
                    append("$baseUrl/rest/executions?limit=$limit")
                    if (!workflowId.isNullOrBlank()) append("&workflowId=$workflowId")
                }
                val res = requestN8n(url = q, cookie = cookie)
                if (res.isSuccess) {
                    return@withContext Result.success(parseExecutionsListJson(res.getOrThrow(), source = "rest_session"))
                }
            }
        }

        Result.failure(IllegalStateException("Gagal mengambil riwayat eksekusi n8n"))
    }

    suspend fun inspectExecution(config: N8nConfig, executionId: String): Result<N8nExecutionInfo> = withContext(Dispatchers.IO) {
        val bridgeRes = getBridge(config.normalizedBridgeUrl(), "/n8n/inspect/executions/$executionId", config.bridgeToken)
        if (bridgeRes.first in 200..299) {
            val obj = JSONObject(bridgeRes.second)
            return@withContext Result.success(parseExecutionObject(obj, "bridge_sqlite"))
        }

        val baseUrl = config.normalizedBaseUrl()
        if (isHealthzUp(baseUrl) && config.apiKey.isNotBlank()) {
            val res = requestN8n("$baseUrl/api/v1/executions/$executionId?includeData=true", apiKey = config.apiKey)
            if (res.isSuccess) {
                val root = JSONObject(res.getOrThrow())
                val obj = root.optJSONObject("data") ?: root
                return@withContext Result.success(parseExecutionObject(obj, "public_api"))
            }
        }

        Result.failure(IllegalStateException("Gagal membaca detail eksekusi #$executionId"))
    }

    suspend fun setWorkflowActive(config: N8nConfig, workflowId: String, active: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        val baseUrl = config.normalizedBaseUrl()
        val action = if (active) "activate" else "deactivate"

        if (config.apiKey.isNotBlank()) {
            val res = requestN8n("$baseUrl/api/v1/workflows/$workflowId/$action", method = "POST", body = "{}", apiKey = config.apiKey)
            if (res.isSuccess) return@withContext Result.success(active)
        }

        val cookie = resolveOrLoginSessionCookie(config)
        if (!cookie.isNullOrBlank()) {
            val res = requestN8n("$baseUrl/rest/workflows/$workflowId/$action", method = "POST", body = "{}", cookie = cookie)
            if (res.isSuccess) return@withContext Result.success(active)
        }

        Result.failure(IllegalStateException("Butuh API Key atau Login Sesi n8n untuk mengubah status aktif workflow."))
    }

    suspend fun executeWorkflow(
        config: N8nConfig,
        workflowId: String,
        webhookPath: String? = null,
        payloadJson: String = "{}",
        onProgress: (String) -> Unit = {}
    ): Result<N8nExecuteOutcome> = withContext(Dispatchers.IO) {
        ensureServerRunning(config, onProgress).onFailure {
            return@withContext Result.failure(it)
        }

        val baseUrl = config.normalizedBaseUrl()
        val detail = inspectWorkflow(config, workflowId).getOrNull()

        val resolvedWebhook = webhookPath?.trim()?.trimStart('/')?.takeIf { it.isNotEmpty() }
            ?: detail?.triggers?.firstOrNull { it.webhookPath.isNotBlank() }?.webhookPath?.trimStart('/')

        if (!resolvedWebhook.isNullOrBlank() && (detail?.active == true || !webhookPath.isNullOrBlank())) {
            onProgress("Memicu Webhook n8n: /webhook/$resolvedWebhook")
            val hookRes = requestN8n(
                url = "$baseUrl/webhook/$resolvedWebhook",
                method = "POST",
                body = payloadJson.ifBlank { "{}" }
            )
            if (hookRes.isSuccess) {
                val body = hookRes.getOrThrow()
                return@withContext Result.success(
                    N8nExecuteOutcome(
                        success = true,
                        status = "success",
                        method = "webhook",
                        message = "Webhook /$resolvedWebhook berhasil dieksekusi",
                        outputJson = body
                    )
                )
            }
        }

        val cookie = resolveOrLoginSessionCookie(config)
        if (!cookie.isNullOrBlank()) {
            val triggerName = detail?.triggers?.firstOrNull()?.name
                ?: detail?.nodes?.firstOrNull { !it.disabled }?.name
            val runBody = JSONObject().apply {
                if (!triggerName.isNullOrBlank()) {
                    put("triggerToStartFrom", JSONObject().put("name", triggerName))
                } else {
                    val lastNode = detail?.nodes?.lastOrNull()?.name ?: "Start"
                    put("destinationNode", JSONObject().apply {
                        put("nodeName", lastNode)
                        put("mode", "inclusive")
                    })
                }
            }
            onProgress("Menjalankan workflow '$workflowId' via /rest/workflows/$workflowId/run...")
            val runRes = requestN8n(
                url = "$baseUrl/rest/workflows/$workflowId/run",
                method = "POST",
                body = runBody.toString(),
                cookie = cookie
            )
            if (runRes.isSuccess) {
                val raw = runRes.getOrThrow()
                val root = runCatching { JSONObject(raw) }.getOrNull()
                val dataObj = root?.optJSONObject("data") ?: root
                val execId = dataObj?.optString("executionId")?.takeIf { it.isNotBlank() }

                var finalDetail: N8nExecutionInfo? = null
                if (execId != null) {
                    onProgress("Eksekusi dimulai (ID: $execId), memantau status...")
                    for (attempt in 1..40) {
                        currentCoroutineContext().ensureActive()
                        delay(1500L)
                        val ex = inspectExecution(config, execId).getOrNull()
                        if (ex != null) {
                            finalDetail = ex
                            if (ex.finished || ex.status in listOf("success", "error", "crashed", "canceled")) {
                                break
                            }
                        }
                    }
                }
                val isOk = finalDetail?.status != "error" && finalDetail?.status != "crashed"
                return@withContext Result.success(
                    N8nExecuteOutcome(
                        success = isOk,
                        executionId = execId,
                        status = finalDetail?.status ?: "triggered",
                        method = "rest_manual_run",
                        message = if (isOk) "Workflow dieksekusi (ID: ${execId ?: "-"})"
                        else "Eksekusi error: ${finalDetail?.errorMessage ?: "Unknown"}",
                        outputJson = raw,
                        executionDetail = finalDetail
                    )
                )
            }
        }

        val bridgeUrl = config.normalizedBridgeUrl()
        if (fetchBridgeHealth(bridgeUrl, config.bridgeToken) != null) {
            onProgress("Menjalankan via Bridge CLI (n8n execute --id=$workflowId)...")
            val reqBuilder = Request.Builder()
                .url("$bridgeUrl/n8n/workflows/$workflowId/execute")
                .post("{}".toRequestBody(jsonMediaType))
            if (config.bridgeToken.isNotBlank()) {
                reqBuilder.header("X-Bridge-Token", config.bridgeToken)
            }
            val execOutcome = runCatching {
                execClient.newCall(reqBuilder.build()).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    val obj = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()
                    val latestObj = obj.optJSONObject("latestExecution")
                    val latestExec = latestObj?.let { parseExecutionObject(it, "bridge_cli") }
                    val ok = resp.isSuccessful && obj.optInt("exitCode", 1) == 0
                    N8nExecuteOutcome(
                        success = ok,
                        executionId = latestExec?.id,
                        status = latestExec?.status ?: if (ok) "success" else "error",
                        method = "bridge_cli",
                        message = if (ok) "Dieksekusi via Bridge CLI"
                        else obj.optString("error").ifBlank { obj.optString("stderr").take(240) },
                        outputJson = raw,
                        executionDetail = latestExec
                    )
                }
            }
            return@withContext execOutcome
        }

        Result.failure(
            IllegalStateException("Tidak ada jalur eksekusi tersedia. Isi Email/Password n8n, Webhook aktif, atau jalankan Local Bridge.")
        )
    }

    private suspend fun resolveOrLoginSessionCookie(config: N8nConfig): String? {
        val baseUrl = config.normalizedBaseUrl()
        val cached = configStore.getCachedSessionCookie(baseUrl)
        if (!cached.isNullOrBlank()) return cached
        if (config.email.isNotBlank() && config.password.isNotBlank()) {
            return loginWithEmailPassword(config).getOrNull()
        }
        return null
    }

    private suspend fun loginWithEmailPassword(config: N8nConfig): Result<String> {
        val baseUrl = config.normalizedBaseUrl()
        if (config.email.isBlank() || config.password.isBlank()) {
            return Result.failure(IllegalStateException("Email dan Password n8n wajib diisi"))
        }
        val body = JSONObject().apply {
            put("emailOrLdapLoginId", config.email.trim())
            put("password", config.password)
        }
        val req = Request.Builder()
            .url("$baseUrl/rest/login")
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        return try {
            apiClient.newCall(req).execute().use { resp ->
                val respStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return Result.failure(IllegalStateException("Login n8n gagal (HTTP ${resp.code}): $respStr"))
                }
                val cookies = resp.headers("Set-Cookie")
                    .mapNotNull { it.substringBefore(";").trim().takeIf { c -> c.isNotEmpty() } }
                val cookieHeader = cookies.joinToString("; ")
                if (cookieHeader.isBlank()) {
                    return Result.failure(IllegalStateException("Login berhasil tetapi cookie sesi n8n-auth tidak diterima"))
                }
                configStore.saveSessionCookie(cookieHeader)
                Result.success(cookieHeader)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isHealthzUp(baseUrl: String): Boolean {
        val req = Request.Builder().url("$baseUrl/healthz").get().build()
        return runCatching {
            shortClient.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private fun fetchBridgeHealth(bridgeUrl: String, token: String): JSONObject? {
        val reqBuilder = Request.Builder().url("$bridgeUrl/health").get()
        if (token.isNotBlank()) reqBuilder.header("X-Bridge-Token", token)
        return runCatching {
            shortClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                JSONObject(resp.body?.string().orEmpty())
            }
        }.getOrNull()
    }

    private fun getBridge(bridgeUrl: String, path: String, token: String): Pair<Int, String> {
        val reqBuilder = Request.Builder().url("$bridgeUrl$path").get()
        if (token.isNotBlank()) reqBuilder.header("X-Bridge-Token", token)
        return runCatching {
            apiClient.newCall(reqBuilder.build()).execute().use { resp ->
                resp.code to (resp.body?.string().orEmpty())
            }
        }.getOrElse { -1 to (it.message ?: "Connection failed") }
    }

    private fun postBridge(bridgeUrl: String, path: String, token: String): Pair<Int, String> {
        val reqBuilder = Request.Builder()
            .url("$bridgeUrl$path")
            .post("{}".toRequestBody(jsonMediaType))
        if (token.isNotBlank()) reqBuilder.header("X-Bridge-Token", token)
        return runCatching {
            apiClient.newCall(reqBuilder.build()).execute().use { resp ->
                resp.code to (resp.body?.string().orEmpty())
            }
        }.getOrElse { -1 to (it.message ?: "Connection failed") }
    }

    private fun requestN8n(
        url: String,
        method: String = "GET",
        body: String? = null,
        apiKey: String? = null,
        cookie: String? = null
    ): Result<String> {
        val builder = Request.Builder().url(url)
        if (!apiKey.isNullOrBlank()) builder.header("X-N8N-API-KEY", apiKey)
        if (!cookie.isNullOrBlank()) builder.header("Cookie", cookie)

        when (method.uppercase()) {
            "POST" -> builder.post((body ?: "{}").toRequestBody(jsonMediaType))
            "PUT" -> builder.put((body ?: "{}").toRequestBody(jsonMediaType))
            "PATCH" -> builder.patch((body ?: "{}").toRequestBody(jsonMediaType))
            "DELETE" -> builder.delete()
            else -> builder.get()
        }

        return try {
            apiClient.newCall(builder.build()).execute().use { resp ->
                val str = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    Result.success(str)
                } else {
                    Result.failure(IllegalStateException("HTTP ${resp.code}: $str"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isPackageInstalled(pkg: String): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageInfo(pkg, 0)
            }
            true
        }.getOrDefault(false)
    }

    private fun launchTermuxCommand(command: String): Boolean {
        return runCatching {
            val intent = Intent("com.termux.RUN_COMMAND").apply {
                setClassName("com.termux", "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", "nohup $command >/tmp/n8n.log 2>&1 &"))
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
            true
        }.getOrDefault(false)
    }

    private fun parseWorkflowListJson(raw: String, source: String): List<N8nWorkflowSummary> {
        val trimmed = raw.trim()
        val array = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> JSONObject(trimmed).optJSONArray("data") ?: JSONArray()
        }
        val list = mutableListOf<N8nWorkflowSummary>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optBoolean("isArchived", false)) continue

            val rawNodes = obj.optJSONArray("nodes")
            val nonNoteNodes = mutableListOf<JSONObject>()
            val triggers = mutableListOf<N8nTriggerInfo>()

            if (rawNodes != null) {
                for (nIdx in 0 until rawNodes.length()) {
                    val n = rawNodes.optJSONObject(nIdx) ?: continue
                    val nType = n.optString("type")
                    if (nType.contains("stickyNote", ignoreCase = true)) continue
                    nonNoteNodes.add(n)
                    if (isTriggerType(nType)) {
                        triggers.add(
                            N8nTriggerInfo(
                                name = n.optString("name"),
                                type = nType,
                                webhookPath = n.optJSONObject("parameters")?.optString("path").orEmpty()
                            )
                        )
                    }
                }
            } else {
                val trigArr = obj.optJSONArray("triggers")
                if (trigArr != null) {
                    for (tIdx in 0 until trigArr.length()) {
                        val t = trigArr.optJSONObject(tIdx) ?: continue
                        triggers.add(
                            N8nTriggerInfo(
                                name = t.optString("name"),
                                type = t.optString("type"),
                                webhookPath = t.optString("webhookPath")
                            )
                        )
                    }
                }
            }

            val nodeTypes = if (rawNodes != null) {
                nonNoteNodes.map { it.optString("type") }
            } else {
                jsonArrayToStrings(obj.optJSONArray("nodeTypes"))
            }

            val pipeline = obj.optString("pipelinePreview").ifBlank {
                nonNoteNodes.take(8).joinToString(" → ") { it.optString("name") }
            }

            val tagsArr = obj.optJSONArray("tags")
            val tags = mutableListOf<String>()
            if (tagsArr != null) {
                for (tIdx in 0 until tagsArr.length()) {
                    val item = tagsArr.opt(tIdx)
                    when (item) {
                        is JSONObject -> tags.add(item.optString("name"))
                        is String -> tags.add(item)
                    }
                }
            }

            list.add(
                N8nWorkflowSummary(
                    id = obj.optString("id"),
                    name = obj.optString("name", "Untitled"),
                    description = obj.optString("description"),
                    active = obj.optBoolean("active", false),
                    nodeCount = obj.optInt("nodeCount", nonNoteNodes.size),
                    edgeCount = obj.optInt("edgeCount", 0),
                    nodeTypes = nodeTypes,
                    triggers = triggers,
                    pipelinePreview = pipeline,
                    tags = tags.filter { it.isNotBlank() },
                    updatedAt = obj.optString("updatedAt"),
                    source = obj.optString("source").ifBlank { source }
                )
            )
        }
        return list
    }

    private fun parseWorkflowDetailJson(raw: String, source: String): N8nWorkflowDetail {
        val root = JSONObject(raw)
        val obj = if (root.has("data") && root.optJSONObject("data") != null && !root.has("nodes")) {
            root.getJSONObject("data")
        } else {
            root
        }

        val nodes = mutableListOf<N8nNodeInfo>()
        val triggers = mutableListOf<N8nTriggerInfo>()
        val notes = mutableListOf<N8nStickyNote>()

        val rawNodes = obj.optJSONArray("nodes") ?: JSONArray()
        for (i in 0 until rawNodes.length()) {
            val n = rawNodes.optJSONObject(i) ?: continue
            val nType = n.optString("type")
            val paramsObj = n.optJSONObject("parameters") ?: JSONObject()

            if (nType.contains("stickyNote", ignoreCase = true)) {
                notes.add(
                    N8nStickyNote(
                        name = n.optString("name", "Catatan"),
                        content = paramsObj.optString("content")
                    )
                )
                continue
            }

            val isTrig = n.optBoolean("isTrigger", isTriggerType(nType))
            val credTypes = mutableListOf<String>()
            val credArr = n.optJSONArray("credentialTypes")
            if (credArr != null) {
                credTypes.addAll(jsonArrayToStrings(credArr))
            } else {
                n.optJSONObject("credentials")?.keys()?.forEach { credTypes.add(it) }
            }

            val info = N8nNodeInfo(
                id = n.optString("id"),
                name = n.optString("name"),
                type = nType,
                typeVersion = n.opt("typeVersion")?.toString() ?: "1",
                disabled = n.optBoolean("disabled", false),
                parametersJson = paramsObj.toString(2),
                credentialTypes = credTypes,
                notes = n.optString("notes"),
                isTrigger = isTrig
            )
            nodes.add(info)
            if (isTrig) {
                triggers.add(
                    N8nTriggerInfo(
                        name = info.name,
                        type = nType,
                        webhookPath = paramsObj.optString("path")
                    )
                )
            }
        }

        val edges = mutableListOf<N8nEdgeInfo>()
        val edgesArr = obj.optJSONArray("edges")
        if (edgesArr != null) {
            for (i in 0 until edgesArr.length()) {
                val e = edgesArr.optJSONObject(i) ?: continue
                edges.add(
                    N8nEdgeInfo(
                        from = e.optString("from"),
                        to = e.optString("to"),
                        type = e.optString("type", "main"),
                        branch = e.optInt("branch", 0)
                    )
                )
            }
        } else {
            val conns = obj.optJSONObject("connections") ?: JSONObject()
            conns.keys().forEach { srcName ->
                val byType = conns.optJSONObject(srcName) ?: return@forEach
                byType.keys().forEach { connType ->
                    val branches = byType.optJSONArray(connType) ?: return@forEach
                    for (bIdx in 0 until branches.length()) {
                        val targets = branches.optJSONArray(bIdx) ?: continue
                        for (tIdx in 0 until targets.length()) {
                            val tgt = targets.optJSONObject(tIdx) ?: continue
                            val targetNode = tgt.optString("node")
                            if (targetNode.isNotBlank()) {
                                edges.add(N8nEdgeInfo(srcName, targetNode, connType, bIdx))
                            }
                        }
                    }
                }
            }
        }

        return N8nWorkflowDetail(
            id = obj.optString("id"),
            name = obj.optString("name", "Untitled"),
            description = obj.optString("description"),
            active = obj.optBoolean("active", false),
            updatedAt = obj.optString("updatedAt"),
            nodes = nodes,
            edges = edges,
            triggers = triggers,
            stickyNotes = notes,
            settingsJson = obj.optJSONObject("settings")?.toString(2) ?: "{}",
            rawJson = obj.toString(2),
            source = obj.optString("source").ifBlank { source }
        )
    }

    private fun parseExecutionsListJson(raw: String, source: String): List<N8nExecutionInfo> {
        val trimmed = raw.trim()
        val arr = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> {
                val root = JSONObject(trimmed)
                val dataObj = root.optJSONObject("data")
                dataObj?.optJSONArray("results")
                    ?: root.optJSONArray("data")
                    ?: root.optJSONArray("results")
                    ?: JSONArray()
            }
        }
        val list = mutableListOf<N8nExecutionInfo>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            list.add(parseExecutionObject(obj, source))
        }
        return list
    }

    private fun parseExecutionObject(obj: JSONObject, source: String): N8nExecutionInfo {
        val nodeSummaries = mutableListOf<N8nExecutionNodeSummary>()
        val preParsedNodes = obj.optJSONArray("nodes")
        if (preParsedNodes != null) {
            for (i in 0 until preParsedNodes.length()) {
                val n = preParsedNodes.optJSONObject(i) ?: continue
                nodeSummaries.add(
                    N8nExecutionNodeSummary(
                        nodeName = n.optString("nodeName"),
                        status = n.optString("status", "success"),
                        executionTimeMs = n.optLong("executionTimeMs", 0L),
                        itemCount = n.optInt("itemCount", 0),
                        outputPreview = n.optString("outputPreview"),
                        error = n.optString("error").takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
        } else {
            val runData = obj.optJSONObject("data")
                ?.optJSONObject("resultData")
                ?.optJSONObject("runData")
            if (runData != null) {
                runData.keys().forEach { nodeName ->
                    val runs = runData.optJSONArray(nodeName) ?: return@forEach
                    if (runs.length() == 0) return@forEach
                    val last = runs.optJSONObject(runs.length() - 1) ?: return@forEach
                    val err = last.optJSONObject("error")?.optString("message")
                    val mainArr = last.optJSONObject("data")?.optJSONArray("main")?.optJSONArray(0)
                    val preview = if (mainArr != null && mainArr.length() > 0) {
                        mainArr.opt(0)?.toString()?.take(400).orEmpty()
                    } else ""
                    nodeSummaries.add(
                        N8nExecutionNodeSummary(
                            nodeName = nodeName,
                            status = last.optString("executionStatus", if (err != null) "error" else "success"),
                            executionTimeMs = last.optLong("executionTime", 0L),
                            itemCount = mainArr?.length() ?: 0,
                            outputPreview = preview,
                            error = err
                        )
                    )
                }
            }
        }

        val wfName = obj.optString("workflowName").ifBlank {
            obj.optJSONObject("workflowData")?.optString("name").orEmpty()
        }

        return N8nExecutionInfo(
            id = obj.optString("id"),
            workflowId = obj.optString("workflowId"),
            workflowName = wfName,
            status = obj.optString("status", "unknown"),
            mode = obj.optString("mode"),
            finished = obj.optBoolean("finished", false),
            startedAt = obj.optString("startedAt"),
            stoppedAt = obj.optString("stoppedAt"),
            lastNodeExecuted = obj.optString("lastNodeExecuted").takeIf { it.isNotBlank() && it != "null" },
            errorMessage = obj.optString("errorMessage").takeIf { it.isNotBlank() && it != "null" },
            nodes = nodeSummaries,
            source = obj.optString("source").ifBlank { source }
        )
    }

    private fun jsonArrayToStrings(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return List(arr.length()) { idx -> arr.optString(idx) }.filter { it.isNotBlank() }
    }

    private fun isTriggerType(type: String): Boolean {
        val low = type.lowercase()
        return low.contains("trigger") || low.contains("webhook")
    }
}
