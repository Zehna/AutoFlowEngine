package com.taras.autoflow.data

import com.taras.autoflow.engine.TriggerConfig
import com.taras.autoflow.engine.WorkflowDefinition
import com.taras.autoflow.engine.WorkflowNode

object BuiltInWorkflowSeeds {
    fun getStarterWorkflows(): List<WorkflowDefinition> = listOf(
        WorkflowDefinition(
            id = "wf_api_python_pipeline",
            name = "API Fetch → Python → KV Vault",
            description = "Mengambil metadata repo dari GitHub API, memprosesnya dengan Python 3.11, lalu menyimpan ringkasan ke Room KV Store.",
            variables = mapOf("repo" to "Zehna/AutoFlowEngine"),
            triggers = listOf(TriggerConfig(type = "MANUAL")),
            nodes = listOf(
                WorkflowNode(
                    id = "fetch_repo",
                    label = "Ambil Data GitHub API",
                    type = "HTTP",
                    parameters = mapOf(
                        "url" to "https://api.github.com/repos/{{vars.repo}}",
                        "method" to "GET"
                    ),
                    maxRetries = 1
                ),
                WorkflowNode(
                    id = "parse_stats",
                    label = "Olahan Data Python",
                    type = "PYTHON",
                    condition = "not_empty {{fetch_repo.body}}",
                    parameters = mapOf(
                        "script" to """
                            import json
                            raw = context.get("fetch_repo", {}).get("body", "{}")
                            data = json.loads(raw)
                            outputs["full_name"] = data.get("full_name", "unknown")
                            outputs["stars"] = data.get("stargazers_count", 0)
                            outputs["lang"] = data.get("language", "Kotlin")
                            outputs["summary"] = f"{outputs['full_name']} ({outputs['lang']}) - {outputs['stars']} stars"
                        """.trimIndent(),
                        "input_json" to "{}"
                    )
                ),
                WorkflowNode(
                    id = "save_state",
                    label = "Simpan ke KV Vault",
                    type = "COMPUTE",
                    parameters = mapOf(
                        "operation" to "KV_SET",
                        "key" to "last_repo_summary",
                        "value" to "{{parse_stats.summary}}"
                    )
                )
            )
        ),
        WorkflowDefinition(
            id = "wf_shizuku_device_audit",
            name = "Shizuku Shell → Python Diagnostik",
            description = "Membaca status baterai & sistem perangkat lewat Shizuku ADB Shell lalu memformat laporan dengan Python.",
            triggers = listOf(TriggerConfig(type = "MANUAL")),
            nodes = listOf(
                WorkflowNode(
                    id = "shell_battery",
                    label = "Baca Status Baterai (Shizuku)",
                    type = "SHELL",
                    parameters = mapOf(
                        "command" to "dumpsys battery | grep -E 'level|temperature|AC powered|USB powered'"
                    )
                ),
                WorkflowNode(
                    id = "py_format",
                    label = "Format Laporan Baterai",
                    type = "PYTHON",
                    parameters = mapOf(
                        "script" to """
                            raw = context.get("shell_battery", {}).get("stdout", "")
                            lines = [line.strip() for line in raw.splitlines() if line.strip()]
                            outputs["metrics"] = lines
                            outputs["count"] = len(lines)
                        """.trimIndent(),
                        "input_json" to "{}"
                    )
                )
            )
        ),
        WorkflowDefinition(
            id = "wf_webview_dom_extract",
            name = "Headless WebView DOM Scraper",
            description = "Memuat halaman web SPA secara offscreen di WebView, mengekstrak DOM & cookie, lalu memproses teks dengan Python.",
            triggers = listOf(TriggerConfig(type = "MANUAL")),
            nodes = listOf(
                WorkflowNode(
                    id = "web_scrape",
                    label = "Ekstrak DOM Offscreen",
                    type = "OFFSCREEN_WEB",
                    parameters = mapOf(
                        "url" to "https://example.com",
                        "wait_ms" to "1000",
                        "js_extract" to "document.title + ' | ' + (document.querySelector('h1')?.innerText || '')"
                    )
                ),
                WorkflowNode(
                    id = "py_clean",
                    label = "Normalisasi Teks",
                    type = "PYTHON",
                    parameters = mapOf(
                        "script" to """
                            text = context.get("web_scrape", {}).get("content", "")
                            outputs["cleaned"] = text.strip().upper()
                        """.trimIndent(),
                        "input_json" to "{}"
                    )
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
                    label = "Ambil Sampel JSON",
                    type = "HTTP",
                    parameters = mapOf(
                        "url" to "https://httpbin.org/json",
                        "method" to "GET"
                    )
                ),
                WorkflowNode(
                    id = "ai_summary",
                    label = "Ringkas via Gemini AI",
                    type = "GEMINI_AI",
                    parameters = mapOf(
                        "model" to "gemini-2.5-flash",
                        "api_key" to "{{kv.gemini_api_key}}",
                        "prompt" to "Ringkas isi JSON berikut dalam 2 kalimat bahasa Indonesia:\n{{fetch_sample.body}}"
                    )
                )
            )
        )
    )
}
