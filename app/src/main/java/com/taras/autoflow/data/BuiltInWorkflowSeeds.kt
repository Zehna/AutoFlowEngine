package com.taras.autoflow.data

import com.taras.autoflow.engine.ExecutionMode
import com.taras.autoflow.engine.ExecutionPolicy
import com.taras.autoflow.engine.RetryPolicy
import com.taras.autoflow.engine.TriggerConfig
import com.taras.autoflow.engine.WorkflowDefinition
import com.taras.autoflow.engine.WorkflowNode
import kotlinx.serialization.json.JsonPrimitive

object BuiltInWorkflowSeeds {
    fun getStarterWorkflows(): List<WorkflowDefinition> = listOf(
        WorkflowDefinition(
            id = "wf_api_python_pipeline",
            name = "API Fetch → Python → KV Vault",
            description = "Mengambil metadata repo dari GitHub API, memprosesnya dengan Python 3.11, lalu menyimpan ringkasan ke Room KV Store.",
            variables = mapOf(
                "repo" to JsonPrimitive("Zehna/AutoFlowEngine"),
                "save_to_kv" to JsonPrimitive(true),
                "cycle_limit" to JsonPrimitive(1)
            ),
            triggers = listOf(TriggerConfig(type = "MANUAL")),
            executionPolicy = ExecutionPolicy(mode = ExecutionMode.ONCE, maxIterations = 1, loopDelayMs = 2000L),
            nodes = listOf(
                WorkflowNode(
                    id = "fetch_repo",
                    name = "Ambil Data GitHub API",
                    worker = "NETWORK_HTTP",
                    action = "GET",
                    params = mapOf(
                        "url" to JsonPrimitive("https://api.github.com/repos/{{variables.repo}}")
                    ),
                    retry = RetryPolicy(maxAttempts = 1, delayMs = 1000L),
                    next = "parse_stats"
                ),
                WorkflowNode(
                    id = "parse_stats",
                    name = "Olahan Data Python",
                    worker = "PYTHON_COMPUTE",
                    action = "RUN_INLINE",
                    condition = "not_empty {{fetch_repo.output.body}}",
                    params = mapOf(
                        "script" to JsonPrimitive(
                            """
                            import json
                            raw = context.get("fetch_repo", {}).get("body", "{}")
                            data = json.loads(raw)
                            outputs["full_name"] = data.get("full_name", "unknown")
                            outputs["stars"] = data.get("stargazers_count", 0)
                            outputs["lang"] = data.get("language", "Kotlin")
                            outputs["summary"] = f"{outputs['full_name']} ({outputs['lang']}) - {outputs['stars']} stars"
                            """.trimIndent()
                        ),
                        "input_json" to JsonPrimitive("{}")
                    ),
                    next = "save_state"
                ),
                WorkflowNode(
                    id = "save_state",
                    name = "Simpan ke KV Vault",
                    worker = "COMPUTE_KOTLIN",
                    action = "KV_SET",
                    condition = "{{variables.save_to_kv}} == true",
                    params = mapOf(
                        "key" to JsonPrimitive("last_repo_summary"),
                        "value" to JsonPrimitive("{{parse_stats.output.summary}}")
                    ),
                    next = "END"
                )
            )
        ),
        WorkflowDefinition(
            id = "wf_shizuku_device_audit",
            name = "Shizuku Shell → Python Diagnostik",
            description = "Membaca status baterai & sistem perangkat lewat Shizuku ADB Shell lalu memformat laporan dengan Python.",
            triggers = listOf(TriggerConfig(type = "MANUAL")),
            executionPolicy = ExecutionPolicy(mode = ExecutionMode.ONCE),
            nodes = listOf(
                WorkflowNode(
                    id = "shell_battery",
                    name = "Baca Status Baterai (Shizuku)",
                    worker = "SHIZUKU_SHELL",
                    action = "EXEC",
                    params = mapOf(
                        "command" to JsonPrimitive("dumpsys battery | grep -E 'level|temperature|AC powered|USB powered'")
                    ),
                    next = "py_format"
                ),
                WorkflowNode(
                    id = "py_format",
                    name = "Format Laporan Baterai",
                    worker = "PYTHON_COMPUTE",
                    action = "RUN_INLINE",
                    params = mapOf(
                        "script" to JsonPrimitive(
                            """
                            raw = context.get("shell_battery", {}).get("stdout", "")
                            lines = [line.strip() for line in raw.splitlines() if line.strip()]
                            outputs["metrics"] = lines
                            outputs["count"] = len(lines)
                            """.trimIndent()
                        ),
                        "input_json" to JsonPrimitive("{}")
                    ),
                    next = "END"
                )
            )
        ),
        WorkflowDefinition(
            id = "wf_webview_dom_extract",
            name = "Headless WebView DOM Scraper",
            description = "Memuat halaman web SPA di latar belakang, polling selector DOM, mengekstrak konten, lalu memproses teks dengan Python.",
            variables = mapOf(
                "target_url" to JsonPrimitive("https://example.com"),
                "selector" to JsonPrimitive("h1")
            ),
            triggers = listOf(TriggerConfig(type = "MANUAL")),
            nodes = listOf(
                WorkflowNode(
                    id = "web_scrape",
                    name = "Ekstrak DOM Offscreen",
                    worker = "WEBVIEW_HEADLESS",
                    action = "EXTRACT_DOM",
                    params = mapOf(
                        "url" to JsonPrimitive("{{variables.target_url}}"),
                        "wait_for_selector" to JsonPrimitive("{{variables.selector}}"),
                        "js_extract" to JsonPrimitive("document.title + ' | ' + (document.querySelector('h1')?.innerText || '')")
                    ),
                    next = "py_clean"
                ),
                WorkflowNode(
                    id = "py_clean",
                    name = "Normalisasi Teks",
                    worker = "PYTHON_COMPUTE",
                    action = "RUN_INLINE",
                    params = mapOf(
                        "script" to JsonPrimitive(
                            """
                            text = context.get("web_scrape", {}).get("content", "")
                            outputs["cleaned"] = text.strip().upper()
                            """.trimIndent()
                        ),
                        "input_json" to JsonPrimitive("{}")
                    ),
                    next = "END"
                )
            )
        ),
        WorkflowDefinition(
            id = "wf_gemini_assistant",
            name = "HTTP → Gemini AI Summarizer",
            description = "Mengambil payload JSON lalu meringkasnya menggunakan Gemini API (simpan key di KV Vault: gemini_api_key).",
            triggers = listOf(TriggerConfig(type = "MANUAL")),
            nodes = listOf(
                WorkflowNode(
                    id = "fetch_sample",
                    name = "Ambil Sampel JSON",
                    worker = "NETWORK_HTTP",
                    action = "GET",
                    params = mapOf(
                        "url" to JsonPrimitive("https://httpbin.org/json")
                    ),
                    next = "ai_summary"
                ),
                WorkflowNode(
                    id = "ai_summary",
                    name = "Ringkas via Gemini AI",
                    worker = "GEMINI_AI",
                    action = "ANALYZE",
                    params = mapOf(
                        "model" to JsonPrimitive("gemini-2.5-flash"),
                        "api_key" to JsonPrimitive("{{kv.gemini_api_key}}"),
                        "prompt" to JsonPrimitive("Ringkas isi JSON berikut dalam 2 kalimat bahasa Indonesia:\n{{fetch_sample.output.body}}")
                    ),
                    next = "END"
                )
            )
        )
    )
}
