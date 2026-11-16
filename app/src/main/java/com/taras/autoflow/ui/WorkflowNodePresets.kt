package com.taras.autoflow.ui

import com.taras.autoflow.engine.WorkflowNode

object WorkflowNodePresets {
    fun createPythonScraperPreset(): WorkflowNode = WorkflowNode(
        id = "py_scraper",
        type = "PYTHON",
        parameters = mapOf(
            "script" to "import requests\nfrom bs4 import BeautifulSoup\noutputs['status'] = 'OK'",
            "input_json" to "{}"
        )
    )

    fun createHttpRequestPreset(): WorkflowNode = WorkflowNode(
        id = "http_request",
        type = "HTTP",
        parameters = mapOf(
            "url" to "https://api.github.com",
            "method" to "GET"
        )
    )

    fun createUiClickPreset(): WorkflowNode = WorkflowNode(
        id = "ui_tap",
        type = "UI_ACTION",
        parameters = mapOf(
            "action" to "CLICK",
            "x" to "540",
            "y" to "960"
        )
    )
}
