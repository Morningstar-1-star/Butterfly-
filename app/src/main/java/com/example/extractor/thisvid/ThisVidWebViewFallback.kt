package com.example.extractor.thisvid

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * High-performance headless WebView stream sniffer for ThisVid.
 * Executes kt_player.js within an isolated Android WebView context to capture the real,
 * decrypted .mp4 / .m3u8 media stream directly before playback.
 */
object ThisVidWebViewFallback {
    private const val TAG = "ThisVidWebViewFallback"
    private const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val TIMEOUT_MS = 6000L

    suspend fun resolveStream(
        context: Context,
        pageUrl: String,
        embedUrl: String? = null
    ): PlayableStreamOption? = withContext(Dispatchers.Main) {
        val targetUrl = embedUrl?.takeIf { it.isNotBlank() } ?: pageUrl
        Log.i(TAG, "Initiating ThisVid headless stream capture for $targetUrl")

        try {
            withTimeoutOrNull(TIMEOUT_MS) {
                runHeadlessCapture(context, targetUrl)
            }
        } catch (e: Exception) {
            Log.w(TAG, "ThisVid headless capture error: ${e.message}")
            null
        }
    }

    private class ThisVidBridge(
        private val onMedia: (String) -> Unit
    ) {
        @JavascriptInterface
        fun onMediaFound(url: String) {
            onMedia(url)
        }
    }

    private suspend fun runHeadlessCapture(
        context: Context,
        targetUrl: String
    ): PlayableStreamOption? = suspendCancellableCoroutine { continuation ->
        val isResolved = AtomicBoolean(false)
        var webView: WebView? = null

        fun cleanupWebView() {
            try {
                webView?.let { wv ->
                    wv.stopLoading()
                    wv.webViewClient = object : WebViewClient() {}
                    wv.loadUrl("about:blank")
                    wv.clearHistory()
                    wv.destroy()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning up ThisVid WebView: ${e.message}")
            } finally {
                webView = null
            }
        }

        try {
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
            }

            webView = WebView(context.applicationContext).apply {
                setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    blockNetworkImage = true
                    allowFileAccess = false
                    allowContentAccess = false
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    userAgentString = DEFAULT_UA
                    cacheMode = WebSettings.LOAD_NO_CACHE
                }

                addJavascriptInterface(
                    ThisVidBridge(
                        onMedia = { mediaUrl ->
                            if (isValidMediaUrl(mediaUrl) && isResolved.compareAndSet(false, true)) {
                                Log.i(TAG, "Captured media via JS bridge: $mediaUrl")
                                val isHls = mediaUrl.contains(".m3u8")
                                val option = PlayableStreamOption(
                                    qualityLabel = if (isHls) "1080p HLS" else "1080p HD Direct",
                                    format = if (isHls) "m3u8" else "mp4",
                                    isMuxed = true,
                                    videoUrl = mediaUrl,
                                    providerType = ProviderType.DIRECT,
                                    headers = mapOf(
                                        "Referer" to "https://thisvid.com/",
                                        "Origin" to "https://thisvid.com",
                                        "User-Agent" to DEFAULT_UA
                                    ),
                                    sourceName = "ThisVid Direct"
                                )
                                Handler(Looper.getMainLooper()).post {
                                    cleanupWebView()
                                    if (continuation.isActive) continuation.resume(option)
                                }
                            }
                        }
                    ),
                    "ThisVidBridge"
                )

                webViewClient = object : WebViewClient() {
                    override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                        Log.w(TAG, "ThisVid WebView renderer process gone, cleaning up")
                        Handler(Looper.getMainLooper()).post {
                            cleanupWebView()
                            if (continuation.isActive) continuation.resume(null)
                        }
                        return true
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val uriStr = request?.url?.toString() ?: return null
                        if (isValidMediaUrl(uriStr) && isResolved.compareAndSet(false, true)) {
                            Log.i(TAG, "Captured media stream via network intercept: $uriStr")
                            val isHls = uriStr.contains(".m3u8")
                            val option = PlayableStreamOption(
                                qualityLabel = if (isHls) "1080p HLS" else "1080p HD Direct",
                                format = if (isHls) "m3u8" else "mp4",
                                isMuxed = true,
                                videoUrl = uriStr,
                                providerType = ProviderType.DIRECT,
                                headers = mapOf(
                                    "Referer" to "https://thisvid.com/",
                                    "Origin" to "https://thisvid.com",
                                    "User-Agent" to DEFAULT_UA
                                ),
                                sourceName = "ThisVid Direct"
                            )
                            Handler(Looper.getMainLooper()).post {
                                cleanupWebView()
                                if (continuation.isActive) continuation.resume(option)
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Inject video element watcher and player inspector
                        val js = """
                            (function() {
                                function checkMedia() {
                                    var v = document.querySelector('video');
                                    if (v && v.src && v.src.indexOf('http') === 0 && (v.src.indexOf('.mp4') !== -1 || v.src.indexOf('.m3u8') !== -1)) {
                                        window.ThisVidBridge.onMediaFound(v.src);
                                        return;
                                    }
                                    var sources = document.querySelectorAll('video source');
                                    for (var i = 0; i < sources.length; i++) {
                                        if (sources[i].src && sources[i].src.indexOf('http') === 0 && (sources[i].src.indexOf('.mp4') !== -1 || sources[i].src.indexOf('.m3u8') !== -1)) {
                                            window.ThisVidBridge.onMediaFound(sources[i].src);
                                            return;
                                        }
                                    }
                                    if (window.flowplayer) {
                                        try {
                                            var fp = window.flowplayer();
                                            if (fp && fp.video && fp.video.src && fp.video.src.indexOf('http') === 0) {
                                                window.ThisVidBridge.onMediaFound(fp.video.src);
                                                return;
                                            }
                                        } catch (e) {}
                                    }
                                }
                                checkMedia();
                                var interval = setInterval(checkMedia, 300);
                                setTimeout(function() { clearInterval(interval); }, 5000);
                            })();
                        """.trimIndent()
                        view?.evaluateJavascript(js, null)
                    }
                }

                loadUrl(targetUrl)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed initializing ThisVid WebView: ${e.message}")
            cleanupWebView()
            if (continuation.isActive) continuation.resume(null)
        }

        continuation.invokeOnCancellation {
            Handler(Looper.getMainLooper()).post { cleanupWebView() }
        }
    }

    private fun isValidMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.contains("preview") || lower.contains("poster") || lower.contains("thumb") ||
            lower.contains(".jpg") || lower.contains(".png") || lower.contains(".gif") ||
            lower.contains(".css") || lower.contains(".js") || lower.contains("blank") ||
            lower.contains("tracking") || lower.contains("event_reporting") || lower.contains("event_") ||
            lower.contains("analytics") || lower.contains("pixel") || lower.contains("log") ||
            lower.contains("ads") || lower.contains("count")
        ) {
            return false
        }
        return (lower.contains(".mp4") || lower.contains(".m3u8")) && lower.startsWith("http")
    }
}
