package com.taras.autoflow.workers

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.taras.autoflow.engine.ExecutionContext
import com.taras.autoflow.engine.NodeExecutionResult
import com.taras.autoflow.service.NodeWorker
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class HeadlessWebViewWorker(private val appContext: Context) : NodeWorker {
    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun execute(parameters: Map<String, String>, context: ExecutionContext): NodeExecutionResult =
        suspendCancellableCoroutine { cont ->
            val url = parameters["url"]?.takeIf { it.isNotBlank() } ?: run {
                cont.resume(NodeExecutionResult.Failure("URL kosong"))
                return@suspendCancellableCoroutine
            }
            val jsExtract = parameters["js_extract"]?.takeIf { it.isNotBlank() } ?: "document.title"
            val waitMs = parameters["wait_ms"]?.toLongOrNull()?.coerceIn(0L, 15_000L) ?: 1200L
            val timeoutMs = parameters["timeout_ms"]?.toLongOrNull()?.coerceIn(3000L, 60_000L) ?: 25_000L

            val mainHandler = Handler(Looper.getMainLooper())
            val completed = AtomicBoolean(false)

            mainHandler.post {
                val webView = WebView(appContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                }

                fun finishOnce(result: NodeExecutionResult) {
                    if (completed.compareAndSet(false, true)) {
                        mainHandler.removeCallbacksAndMessages(null)
                        mainHandler.post {
                            webView.stopLoading()
                            webView.destroy()
                        }
                        if (cont.isActive) {
                            cont.resume(result)
                        }
                    }
                }

                val timeoutRunnable = Runnable {
                    finishOnce(NodeExecutionResult.Failure("WebView timeout setelah ${timeoutMs}ms", isRecoverable = true))
                }
                mainHandler.postDelayed(timeoutRunnable, timeoutMs)

                cont.invokeOnCancellation {
                    completed.set(true)
                    mainHandler.post {
                        mainHandler.removeCallbacksAndMessages(null)
                        webView.stopLoading()
                        webView.destroy()
                    }
                }

                class WebScraperBridge {
                    @JavascriptInterface
                    fun onDataReceived(data: String) {
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.flush()
                        val cookies = cookieManager.getCookie(url) ?: ""
                        finishOnce(
                            NodeExecutionResult.Success(
                                mapOf("content" to data, "cookies" to cookies, "url" to url)
                            )
                        )
                    }
                }

                webView.addJavascriptInterface(WebScraperBridge(), "NativeBridge")
                webView.webViewClient = object : WebViewClient() {
                    private var extractionScheduled = false

                    override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                        super.onPageFinished(view, loadedUrl)
                        if (extractionScheduled || completed.get()) return
                        extractionScheduled = true
                        mainHandler.postDelayed({
                            if (!completed.get()) {
                                view?.evaluateJavascript(
                                    "try { window.NativeBridge.onDataReceived(String(($jsExtract))); } catch(e) { window.NativeBridge.onDataReceived('JS_ERROR: ' + e.message); }",
                                    null
                                )
                            }
                        }, waitMs)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request?.isForMainFrame == true) {
                            finishOnce(
                                NodeExecutionResult.Failure(
                                    "WebView error: ${error?.description ?: "Unknown"}",
                                    isRecoverable = true
                                )
                            )
                        }
                    }
                }
                webView.loadUrl(url)
            }
        }
}
