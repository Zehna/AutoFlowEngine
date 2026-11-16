package com.taras.autoflow.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.taras.autoflow.workers.HeadlessWebViewWorker

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewLoginScreen(
    initialUrl: String = "https://github.com/login",
    onBack: () -> Unit,
    onCookieCaptured: (url: String, cookies: String) -> Unit
) {
    var addressText by remember { mutableStateOf(initialUrl) }
    var activeUrl by remember { mutableStateOf(initialUrl) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session WebView Login", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val target = webViewRef?.url ?: activeUrl
                            val cookieManager = CookieManager.getInstance()
                            cookieManager.flush()
                            val cookies = cookieManager.getCookie(target) ?: ""
                            onCookieCaptured(target, cookies)
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Simpan Sesi")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = addressText,
                    onValueChange = { addressText = it },
                    label = { Text("URL Login") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                FilledIconButton(onClick = {
                    val normalized = if (addressText.startsWith("http://") || addressText.startsWith("https://")) {
                        addressText
                    } else {
                        "https://$addressText"
                    }
                    addressText = normalized
                    activeUrl = normalized
                    webViewRef?.loadUrl(normalized)
                }) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Buka URL")
                }
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    val cookieManager = CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                    }
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = HeadlessWebViewWorker.SHARED_USER_AGENT
                        cookieManager.setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                if (!url.isNullOrBlank()) {
                                    activeUrl = url
                                    addressText = url
                                }
                            }
                        }
                        loadUrl(initialUrl)
                        webViewRef = this
                    }
                }
            )
        }
    }
}
