package com.taras.autoflow.workers

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.taras.autoflow.engine.ExecutionContext
import com.taras.autoflow.engine.NodeExecutionResult
import com.taras.autoflow.service.NodeWorker
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class HeadlessWebViewWorker(private val context: Context) : NodeWorker {
    override suspend fun execute(parameters: Map<String, String>, context: ExecutionContext): NodeExecutionResult =
        suspendCancellableCoroutine { cont ->
            val url = parameters["url"] ?: run {
                cont.resume(NodeExecutionResult.Failure("URL kosong"))
                return@suspendCancellableCoroutine
            }
            val jsExtract = parameters["js_extract"] ?: "document.documentElement.outerHTML"

            Handler(Looper.getMainLooper()).post {
                val webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                }

                class WebScraperBridge {
                    @JavascriptInterface
                    fun onDataReceived(data: String) {
                        CookieManager.getInstance().flush()
                        cont.resume(NodeExecutionResult.Success(mapOf("content" to data)))
                    }
                }

                webView.addJavascriptInterface(WebScraperBridge(), "NativeBridge")
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript(
                            "window.NativeBridge.onDataReceived(($jsExtract));",
                            null
                        )
                    }
                }
                webView.loadUrl(url)
            }
        }
}
