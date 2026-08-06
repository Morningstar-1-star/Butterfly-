package com.example.verification

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.extractor.YouTubeExtractorHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VerificationStatus(
    val isAttempting: Boolean = false,
    val isSuccess: Boolean = false,
    val visitorData: String? = null,
    val poToken: String? = null,
    val cookies: String? = null,
    val logs: List<String> = emptyList(),
    val failureReason: String? = null
)

class LocalBotGuardVerifier(private val context: Context) : YouTubeExtractorHelper.CustomPoTokenProvider {

    private val _status = MutableStateFlow(VerificationStatus())
    val status: StateFlow<VerificationStatus> = _status.asStateFlow()

    private var webView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun addLog(msg: String) {
        Log.d("LocalBotGuardVerifier", msg)
        val currentLogs = _status.value.logs + "[${System.currentTimeMillis() % 100000}] $msg"
        _status.value = _status.value.copy(logs = currentLogs)
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun startVerification(targetVideoId: String = "dQw4w9WgXcQ") {
        mainHandler.post {
            try {
                addLog("Starting hidden WebView BotGuard verification attempt for videoId: $targetVideoId...")
                _status.value = VerificationStatus(isAttempting = true, logs = _status.value.logs + "Starting WebView...")

                val wv = WebView(context)
                webView = wv

                wv.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
                }

                wv.addJavascriptInterface(JSBridge(), "AndroidBotGuardBridge")

                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        addLog("Page loaded: $url")
                        
                        // Inject script to observe window.ytcfg, ytInitialData, or botguard objects
                        val script = """
                            (function() {
                                try {
                                    var visitorData = null;
                                    var poToken = null;
                                    
                                    if (window.ytcfg && window.ytcfg.get) {
                                        visitorData = window.ytcfg.get('VISITOR_DATA');
                                        poToken = window.ytcfg.get('PO_TOKEN') || window.ytcfg.get('BOTGUARD_TOKEN');
                                    }
                                    
                                    var cookieStr = document.cookie;
                                    
                                    window.AndroidBotGuardBridge.onVerificationData(
                                        visitorData || "none",
                                        poToken || "none",
                                        cookieStr || "none"
                                    );
                                } catch(e) {
                                    window.AndroidBotGuardBridge.onError(e.toString());
                                }
                            })();
                        """.trimIndent()

                        wv.evaluateJavascript(script, null)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        addLog("WebView Error ($errorCode): $description on $failingUrl")
                    }
                }

                val targetUrl = "https://www.youtube.com/embed/$targetVideoId"
                addLog("Loading YouTube embed page: $targetUrl")
                wv.loadUrl(targetUrl)

            } catch (e: Exception) {
                addLog("Failed to initialize WebView: ${e.message}")
                _status.value = _status.value.copy(
                    isAttempting = false,
                    isSuccess = false,
                    failureReason = "WebView Initialization Error: ${e.message}"
                )
            }
        }
    }

    private inner class JSBridge {
        @JavascriptInterface
        fun onVerificationData(visitorData: String, poToken: String, cookies: String) {
            mainHandler.post {
                addLog("JS Callback Received - visitorData: $visitorData, poToken: $poToken, cookies: $cookies")
                
                val vData = if (visitorData != "none") visitorData else null
                val pToken = if (poToken != "none") poToken else null
                val cStr = if (cookies != "none") cookies else null

                if (pToken != null || vData != null) {
                    addLog("Successfully extracted verification parameters!")
                    _status.value = _status.value.copy(
                        isAttempting = false,
                        isSuccess = true,
                        visitorData = vData,
                        poToken = pToken,
                        cookies = cStr
                    )
                } else {
                    addLog("BotGuard JS loaded but window.ytcfg did not yield poToken or visitorData in standard embed layout.")
                    _status.value = _status.value.copy(
                        isAttempting = false,
                        isSuccess = false,
                        cookies = cStr,
                        failureReason = "BotGuard/ytcfg did not produce poToken or visitorData directly in DOM. YouTube requires full DroidGuard attestation or proprietary Web-JS Botguard challenge execution."
                    )
                }
            }
        }

        @JavascriptInterface
        fun onError(error: String) {
            mainHandler.post {
                addLog("JS Error: $error")
                _status.value = _status.value.copy(
                    isAttempting = false,
                    isSuccess = false,
                    failureReason = "JavaScript Execution Error: $error"
                )
            }
        }
    }

    override fun getPoToken(visitorData: String?): String? {
        return _status.value.poToken
    }
}
