package com.taras.autoflow.n8n

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import com.taras.autoflow.data.AutoFlowDatabase
import com.taras.autoflow.data.WorkflowStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class N8nConfigStore(context: Context) {
    private val appContext = context.applicationContext
    private val dao = AutoFlowDatabase.getInstance(appContext).stateDao()

    companion object {
        const val NAMESPACE = "n8n"
        private const val KEY_BASE_URL = "n8n_base_url"
        private const val KEY_BRIDGE_URL = "n8n_bridge_url"
        private const val KEY_BRIDGE_TOKEN = "n8n_bridge_token"
        private const val KEY_API_KEY = "n8n_api_key"
        private const val KEY_EMAIL = "n8n_email"
        private const val KEY_PASSWORD = "n8n_password"
        private const val KEY_AUTO_START_MODE = "n8n_auto_start_mode"
        private const val KEY_SHIZUKU_CMD = "n8n_shizuku_cmd"
        private const val KEY_TERMUX_CMD = "n8n_termux_cmd"
        private const val KEY_BOOT_TIMEOUT = "n8n_boot_timeout_sec"
        private const val KEY_SESSION_COOKIE = "n8n_session_cookie"
    }

    suspend fun loadConfig(): N8nConfig = withContext(Dispatchers.IO) {
        N8nConfig(
            baseUrl = dao.getValue(KEY_BASE_URL, NAMESPACE)?.ifBlank { null } ?: N8nConfig.DEFAULT_BASE_URL,
            bridgeUrl = dao.getValue(KEY_BRIDGE_URL, NAMESPACE)?.ifBlank { null } ?: N8nConfig.DEFAULT_BRIDGE_URL,
            bridgeToken = dao.getValue(KEY_BRIDGE_TOKEN, NAMESPACE).orEmpty(),
            apiKey = dao.getValue(KEY_API_KEY, NAMESPACE)
                ?: dao.getValueAnyNamespace(KEY_API_KEY).orEmpty(),
            email = dao.getValue(KEY_EMAIL, NAMESPACE).orEmpty(),
            password = dao.getValue(KEY_PASSWORD, NAMESPACE).orEmpty(),
            autoStartMode = N8nAutoStartMode.fromString(dao.getValue(KEY_AUTO_START_MODE, NAMESPACE)),
            shizukuStartCommand = dao.getValue(KEY_SHIZUKU_CMD, NAMESPACE).orEmpty(),
            termuxCommand = dao.getValue(KEY_TERMUX_CMD, NAMESPACE)?.ifBlank { null } ?: "n8n start",
            bootTimeoutSeconds = dao.getValue(KEY_BOOT_TIMEOUT, NAMESPACE)?.toIntOrNull()?.coerceIn(15, 300) ?: 150
        )
    }

    suspend fun saveConfig(config: N8nConfig) = withContext(Dispatchers.IO) {
        put(KEY_BASE_URL, config.normalizedBaseUrl())
        put(KEY_BRIDGE_URL, config.normalizedBridgeUrl())
        put(KEY_BRIDGE_TOKEN, config.bridgeToken.trim())
        put(KEY_API_KEY, config.apiKey.trim())
        put(KEY_EMAIL, config.email.trim())
        put(KEY_PASSWORD, config.password)
        put(KEY_AUTO_START_MODE, config.autoStartMode.name)
        put(KEY_SHIZUKU_CMD, config.shizukuStartCommand.trim())
        put(KEY_TERMUX_CMD, config.termuxCommand.trim().ifBlank { "n8n start" })
        put(KEY_BOOT_TIMEOUT, config.bootTimeoutSeconds.coerceIn(15, 300).toString())
    }

    suspend fun getCachedSessionCookie(baseUrl: String): String? = withContext(Dispatchers.IO) {
        val stored = dao.getValue(KEY_SESSION_COOKIE, NAMESPACE)?.takeIf { it.isNotBlank() }
        if (stored != null) return@withContext stored

        val webCookie = runCatching { CookieManager.getInstance().getCookie(baseUrl) }.getOrNull()
            ?.takeIf { it.contains("n8n-auth", ignoreCase = true) }
        if (webCookie != null) return@withContext webCookie

        val host = runCatching { Uri.parse(baseUrl).host }.getOrNull()
        if (!host.isNullOrBlank()) {
            val hostCookie = dao.getValueAnyNamespace("cookie_$host")?.takeIf { it.isNotBlank() }
            if (hostCookie != null) return@withContext hostCookie
        }
        null
    }

    suspend fun saveSessionCookie(cookie: String) = withContext(Dispatchers.IO) {
        put(KEY_SESSION_COOKIE, cookie)
    }

    suspend fun clearSessionCookie() = withContext(Dispatchers.IO) {
        dao.deleteKey(KEY_SESSION_COOKIE, NAMESPACE)
    }

    suspend fun saveApiKey(apiKey: String) = withContext(Dispatchers.IO) {
        put(KEY_API_KEY, apiKey.trim())
    }

    private suspend fun put(key: String, value: String) {
        dao.putValue(
            WorkflowStateEntity(
                key = key,
                value = value,
                namespace = NAMESPACE,
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}
