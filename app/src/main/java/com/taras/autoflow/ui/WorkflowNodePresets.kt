package com.taras.autoflow.ui

import com.taras.autoflow.engine.WorkflowNode
import kotlinx.serialization.json.JsonPrimitive

data class NodePresetMeta(
    val worker: String,
    val action: String,
    val title: String,
    val summary: String,
    val createDefault: (nodeId: String) -> WorkflowNode
)

object WorkflowNodePresets {
    val allPresets: List<NodePresetMeta> = listOf(
        NodePresetMeta(
            worker = "N8N_BRIDGE",
            action = "ENSURE_SERVER",
            title = "n8n Auto-Start Server",
            summary = "Menyalakan server n8n lokal (:5678) otomatis jika belum berjalan",
            createDefault = { id ->
                WorkflowNode(
                    id = id,
                    name = "Start n8n Server",
                    worker = "N8N_BRIDGE",
                    action = "ENSURE_SERVER"
                )
            }
        ),
        NodePresetMeta(
            worker = "N8N_BRIDGE",
            action = "LIST_WORKFLOWS",
            title = "n8n List Workflows",
            summary = "Terhubung ke localhost n8n dan mengambil daftar seluruh workflow",
            createDefault = { id ->
                WorkflowNode(
                    id = id,
                    name = "Daftar Workflow n8n",
                    worker = "N8N_BRIDGE",
                    action = "LIST_WORKFLOWS",
                    params = mapOf("auto_start" to JsonPrimitive("true"))
                )
            }
        ),
        NodePresetMeta(
            worker = "N8N_BRIDGE",
            action = "INSPECT_WORKFLOW",
            title = "n8n Inspect Workflow",
            summary = "Inspeksi node, trigger, koneksi & sticky note di dalam workflow n8n",
            createDefault = { id ->
                WorkflowNode(
                    id = id,
                    name = "Inspeksi Workflow n8n",
                    worker = "N8N_BRIDGE",
                    action = "INSPECT_WORKFLOW",
                    params = mapOf("workflow_id" to JsonPrimitive(""))
                )
            }
        ),
        NodePresetMeta(
            worker = "N8N_BRIDGE",
            action = "EXECUTE_WORKFLOW",
            title = "n8n Execute Workflow",
            summary = "Auto-start server n8n lalu jalankan workflow n8n via REST/Webhook/Bridge",
            createDefault = { id ->
                WorkflowNode(
                    id = id,
                    name = "Jalankan Workflow n8n",
                    worker = "N8N_BRIDGE",
                    action = "EXECUTE_WORKFLOW",
                    params = mapOf(
                        "workflow_id" to JsonPrimitive(""),
                        "webhook_path" to JsonPrimitive(""),
                        "payload_json" to JsonPrimitive("{}")
                    )
                )
            }
        ),
        NodePresetMeta(
            worker = "PYTHON_COMPUTE",
            action = "RUN_INLINE",
            title = "Python Inline Script",
            summary = "Jalankan skrip CPython 3.11 lokal dengan requests, bs4, pillow & context",
            createDefault = { id -> createPythonScraperPreset(id) }
        ),
        NodePresetMeta(
            worker = "PYTHON_COMPUTE",
            action = "CALL_MODULE",
            title = "Python Module (.py)",
            summary = "Panggil fungsi di file .py dari direktori filesDir/python_modules",
            createDefault = { id ->
                WorkflowNode(
                    id = id,
                    name = "Panggil Modul Python",
                    worker = "PYTHON_COMPUTE",
                    action = "CALL_MODULE",
                    params = mapOf(
                        "module" to JsonPrimitive("custom_module"),
                        "function" to JsonPrimitive("run"),
                        "input_json" to JsonPrimitive("{}")
                    )
                )
            }
        ),
        NodePresetMeta(
            worker = "NETWORK_HTTP",
            action = "GET",
            title = "HTTP GET Request",
            summary = "Ambil data JSON/teks dari REST API dengan cookie & header kustom",
            createDefault = { id -> createHttpRequestPreset(id) }
        ),
        NodePresetMeta(
            worker = "NETWORK_HTTP",
            action = "POST_JSON",
            title = "HTTP POST JSON",
            summary = "Kirim payload JSON ke webhook atau REST API",
            createDefault = { id ->
                WorkflowNode(
                    id = id,
                    name = "HTTP POST JSON",
                    worker = "NETWORK_HTTP",
                    action = "POST_JSON",
                    params = mapOf(
                        "url" to JsonPrimitive("https://httpbin.org/post"),
                        "body" to JsonPrimitive("{\"message\":\"{{variables.msg}}\"}"),
                        "headers_json" to JsonPrimitive("{}"),
                        "use_saved_cookies" to JsonPrimitive(false)
                    )
                )
            }
        ),
        NodePresetMeta(
            worker = "WEBVIEW_HEADLESS",
            action = "EXTRACT_DOM",
            title = "Headless WebView (SPA)",
            summary = "Muat halaman web SPA, polling selector DOM, dan ekstrak via JS",
            createDefault = { id ->
                WorkflowNode(
                    id = id,
                    name = "WebView Scraper",
                    worker = "WEBVIEW_HEADLESS",
                    action = "EXTRACT_DOM",
                    params = mapOf(
                        "url" to JsonPrimitive("https://example.com"),
                        "wait_for_selector" to JsonPrimitive("h1"),
                        "js_extract" to JsonPrimitive("document.title"),
                        "wait_ms" to JsonPrimitive(1000)
                    )
                )
            }
        ),
        NodePresetMeta(
            worker = "SHIZUKU_SHELL",
            action = "EXEC",
            title = "Shizuku Shell (EXEC)",
            summary = "Jalankan perintah ADB shell tanpa root melalui Shizuku",
            createDefault = { id ->
                WorkflowNode(
                    id = id,
                    name = "Shizuku Shell",
                    worker = "SHIZUKU_SHELL",
                    action = "EXEC",
                    params = mapOf("command" to JsonPrimitive("dumpsys battery | grep level"))
                )
            }
        ),
        NodePresetMeta(
            worker = "SHIZUKU_SHELL",
            action = "TAP",
            title = "Shizuku Tap / Swipe",
            summary = "Kirim sentuhan koordinat layar (TAP) atau LAUNCH_COMPONENT / FORCE_STOP",
            createDefault = { id ->
                WorkflowNode(
                    id = id,
                    name = "Shizuku UI Tap",
                    worker = "SHIZUKU_SHELL",
                    action = "TAP",
                    params = mapOf("x" to JsonPrimitive(540), "y" to JsonPrimitive(960))
                )
            }
        ),
        NodePresetMeta(
            worker = "SHIZUKU_SHELL",
            action = "LAUNCH_COMPONENT",
            title = "Shizuku Launch App",
            summary = "Buka aplikasi atau Activity target via Shizuku",
            createDefault = { id ->
                WorkflowNode(
                    id = id,
                    name = "Buka Aplikasi",
                    worker = "SHIZUKU_SHELL",
                    action = "LAUNCH_COMPONENT",
                    params = mapOf("package" to JsonPrimitive("com.android.settings"))
                )
            }
        ),
        NodePresetMeta(
            worker = "COMPUTE_KOTLIN",
            action = "DEDUPLICATE",
            title = "Deduplicate (TTL Guard)",
            summary = "Cegah eksekusi duplikat menggunakan Room KV Store dengan TTL",
            createDefault = { id ->
                WorkflowNode(
                    id = id,
                    name = "Cek Duplikat TTL",
                    worker = "COMPUTE_KOTLIN",
                    action = "DEDUPLICATE",
                    params = mapOf(
                        "key" to JsonPrimitive("item_unique_id"),
                        "ttl_seconds" to JsonPrimitive(3600),
                        "fail_on_duplicate" to JsonPrimitive(true)
                    )
                )
            }
        ),
        NodePresetMeta(
            worker = "COMPUTE_KOTLIN",
            action = "KV_SET",
            title = "Kotlin Compute / KV Store",
            summary = "Simpan/ambil KV, FILTER_JSON, JSON_EXTRACT, atau REGEX_MATCH",
            createDefault = { id ->
                WorkflowNode(
                    id = id,
                    name = "Simpan KV",
                    worker = "COMPUTE_KOTLIN",
                    action = "KV_SET",
                    params = mapOf(
                        "key" to JsonPrimitive("sample_key"),
                        "value" to JsonPrimitive("sample_value")
                    )
                )
            }
        ),
        NodePresetMeta(
            worker = "GEMINI_AI",
            action = "ANALYZE_JSON",
            title = "Gemini AI Multimodal",
            summary = "Analisis teks atau foto lokal dengan output terstruktur JSON",
            createDefault = { id ->
                WorkflowNode(
                    id = id,
                    name = "Gemini AI",
                    worker = "GEMINI_AI",
                    action = "ANALYZE_JSON",
                    params = mapOf(
                        "model" to JsonPrimitive("gemini-2.5-flash"),
                        "api_key" to JsonPrimitive("{{kv.gemini_api_key}}"),
                        "prompt" to JsonPrimitive("Ringkas data berikut menjadi JSON {\"summary\": \"...\"}:\n{{prev.output.body}}")
                    )
                )
            }
        ),
        NodePresetMeta(
            worker = "DELAY",
            action = "WAIT",
            title = "Delay / Jeda",
            summary = "Jeda eksekusi beberapa milidetik antar langkah",
            createDefault = { id ->
                WorkflowNode(
                    id = id,
                    name = "Jeda Waktu",
                    worker = "DELAY",
                    action = "WAIT",
                    params = mapOf("duration_ms" to JsonPrimitive(1500))
                )
            }
        )
    )

    fun createPythonScraperPreset(id: String = "py_step"): WorkflowNode = WorkflowNode(
        id = id,
        name = "Skrip Python",
        worker = "PYTHON_COMPUTE",
        action = "RUN_INLINE",
        params = mapOf(
            "script" to JsonPrimitive("outputs['status'] = 'OK'\noutputs['keys'] = list(context.keys())"),
            "input_json" to JsonPrimitive("{}")
        )
    )

    fun createHttpRequestPreset(id: String = "http_step"): WorkflowNode = WorkflowNode(
        id = id,
        name = "HTTP Request",
        worker = "NETWORK_HTTP",
        action = "GET",
        params = mapOf(
            "url" to JsonPrimitive("https://httpbin.org/get"),
            "headers_json" to JsonPrimitive("{}"),
            "use_saved_cookies" to JsonPrimitive(false)
        )
    )
}
