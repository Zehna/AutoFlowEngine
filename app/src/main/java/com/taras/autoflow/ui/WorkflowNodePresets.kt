package com.taras.autoflow.ui

import com.taras.autoflow.engine.WorkflowNode

data class NodePresetMeta(
    val type: String,
    val title: String,
    val summary: String,
    val createDefault: (nodeId: String) -> WorkflowNode
)

object WorkflowNodePresets {
    val allPresets: List<NodePresetMeta> = listOf(
        NodePresetMeta(
            type = "PYTHON",
            title = "Python 3.11 Script",
            summary = "Jalankan skrip CPython lokal dengan requests, bs4, & akses ke context",
            createDefault = { id -> createPythonScraperPreset(id) }
        ),
        NodePresetMeta(
            type = "HTTP",
            title = "HTTP REST Client",
            summary = "Panggil API eksternal (GET/POST/PUT) dengan header & cookie tersimpan",
            createDefault = { id -> createHttpRequestPreset(id) }
        ),
        NodePresetMeta(
            type = "OFFSCREEN_WEB",
            title = "Headless WebView",
            summary = "Buka halaman web SPA di latar belakang dan ekstrak data DOM via JS",
            createDefault = { id -> createWebViewPreset(id) }
        ),
        NodePresetMeta(
            type = "UI_ACTION",
            title = "UI Action (Shizuku / A11y)",
            summary = "Tap, swipe, ketik teks, atau buka aplikasi target otomatis",
            createDefault = { id -> createUiClickPreset(id) }
        ),
        NodePresetMeta(
            type = "SHELL",
            title = "Shizuku Shell Command",
            summary = "Jalankan perintah ADB shell tanpa root melalui Shizuku",
            createDefault = { id -> createShellPreset(id) }
        ),
        NodePresetMeta(
            type = "GEMINI_AI",
            title = "Gemini AI Processor",
            summary = "Analisis teks atau gambar lokal menggunakan model Gemini",
            createDefault = { id -> createGeminiPreset(id) }
        ),
        NodePresetMeta(
            type = "COMPUTE",
            title = "Local Compute & KV Store",
            summary = "KV_GET, KV_SET, JSON_EXTRACT, REGEX_MATCH, atau TEXT_TEMPLATE",
            createDefault = { id -> createComputePreset(id) }
        ),
        NodePresetMeta(
            type = "DEDUPLICATE",
            title = "Deduplicate Guard",
            summary = "Cegah pemrosesan ulang data yang sama berdasarkan key unik",
            createDefault = { id -> createDeduplicatePreset(id) }
        ),
        NodePresetMeta(
            type = "DELAY",
            title = "Delay / Wait",
            summary = "Jeda eksekusi beberapa milidetik antar langkah",
            createDefault = { id -> createDelayPreset(id) }
        )
    )

    fun createPythonScraperPreset(id: String = "py_step"): WorkflowNode = WorkflowNode(
        id = id,
        label = "Skrip Python",
        type = "PYTHON",
        parameters = mapOf(
            "script" to "import json\noutputs['status'] = 'OK'\noutputs['keys'] = list(context.keys())",
            "input_json" to "{}"
        )
    )

    fun createHttpRequestPreset(id: String = "http_step"): WorkflowNode = WorkflowNode(
        id = id,
        label = "HTTP Request",
        type = "HTTP",
        parameters = mapOf(
            "url" to "https://httpbin.org/get",
            "method" to "GET",
            "headers_json" to "{}",
            "use_saved_cookies" to "false"
        )
    )

    fun createWebViewPreset(id: String = "web_step"): WorkflowNode = WorkflowNode(
        id = id,
        label = "WebView Scraper",
        type = "OFFSCREEN_WEB",
        parameters = mapOf(
            "url" to "https://example.com",
            "wait_ms" to "1200",
            "js_extract" to "document.title"
        )
    )

    fun createUiClickPreset(id: String = "ui_step"): WorkflowNode = WorkflowNode(
        id = id,
        label = "UI Tap / Launch",
        type = "UI_ACTION",
        parameters = mapOf(
            "action" to "CLICK",
            "x" to "540",
            "y" to "960"
        )
    )

    fun createShellPreset(id: String = "shell_step"): WorkflowNode = WorkflowNode(
        id = id,
        label = "ADB Shell",
        type = "SHELL",
        parameters = mapOf(
            "command" to "dumpsys battery | grep level"
        )
    )

    fun createGeminiPreset(id: String = "ai_step"): WorkflowNode = WorkflowNode(
        id = id,
        label = "Gemini AI",
        type = "GEMINI_AI",
        parameters = mapOf(
            "model" to "gemini-2.5-flash",
            "api_key" to "{{kv.gemini_api_key}}",
            "prompt" to "Analisis teks berikut:\n{{prev.body}}"
        )
    )

    fun createComputePreset(id: String = "compute_step"): WorkflowNode = WorkflowNode(
        id = id,
        label = "KV & Transform",
        type = "COMPUTE",
        parameters = mapOf(
            "operation" to "KV_SET",
            "key" to "sample_key",
            "value" to "sample_value"
        )
    )

    fun createDeduplicatePreset(id: String = "dedup_step"): WorkflowNode = WorkflowNode(
        id = id,
        label = "Cek Duplikat",
        type = "DEDUPLICATE",
        parameters = mapOf(
            "operation" to "DEDUPLICATE",
            "key" to "item_unique_id",
            "fail_on_duplicate" to "true"
        )
    )

    fun createDelayPreset(id: String = "delay_step"): WorkflowNode = WorkflowNode(
        id = id,
        label = "Jeda Waktu",
        type = "DELAY",
        parameters = mapOf(
            "duration_ms" to "1500"
        )
    )
}
