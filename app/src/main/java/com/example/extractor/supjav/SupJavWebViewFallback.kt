package com.example.extractor.supjav

import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Headless WebView Fallback for SupJav.
 *
 * Triggered as an automated fallback when native HTTP parsing fails due to
 * Cloudflare anti-bot verification or dynamic JavaScript embed rendering.
 */
object SupJavWebViewFallback {

    private const val TAG = "SupJavWebViewFallback"
    private const val FALLBACK_TIMEOUT_MS = 14000L

    /**
     * Resolves a direct playable video source by loading the page or embed headlessly
     * and capturing network requests for .m3u8 / .mp4 media files.
     */
    suspend fun resolveWithFallback(
        context: Context,
        targetUrl: String,
        timeoutMs: Long = FALLBACK_TIMEOUT_MS
    ): SupJavSource? = withContext(Dispatchers.Main) {
        Log.i(TAG, "SupJav fallback: Initiating headless WebView media capture for $targetUrl")
        try {
            withTimeoutOrNull(timeoutMs) {
                runHeadlessMediaCapture(context, targetUrl)
            }
        } catch (e: Exception) {
            Log.w(TAG, "SupJav fallback: WebView capture timed out or failed: ${e.message}")
            null
        }
    }

    /**
     * Fetches catalog HTML through headless WebView when anti-bot challenge is encountered.
     */
    suspend fun fetchCatalogWithFallback(
        context: Context,
        targetUrl: String,
        timeoutMs: Long = FALLBACK_TIMEOUT_MS
    ): List<VideoItem> = withContext(Dispatchers.Main) {
        Log.i(TAG, "SupJav fallback: Initiating headless WebView catalog extraction for $targetUrl")
        try {
            val html = withTimeoutOrNull(timeoutMs) {
                runHeadlessHtmlCapture(context, targetUrl)
            }
            if (!html.isNullOrBlank()) {
                SupJavParser.parseVideoCards(html, targetUrl)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "SupJav fallback: Catalog capture failed: ${e.message}")
            emptyList()
        }
    }

    private suspend fun runHeadlessMediaCapture(
        context: Context,
        targetUrl: String
    ): SupJavSource? = suspendCancellableCoroutine { continuation ->
        val isResolved = AtomicBoolean(false)
        var webView: WebView? = null

        fun cleanup() {
            try {
                webView?.let { wv ->
                    wv.stopLoading()
                    wv.webViewClient = object : WebViewClient() {}
                    wv.loadUrl("about:blank")
                    wv.clearHistory()
                    wv.destroy()
                }
            } catch (e: Exception) {
                Log.w(TAG, "SupJav fallback: Error disposing WebView: ${e.message}")
            } finally {
                webView = null
            }
        }

        continuation.invokeOnCancellation {
            Handler(Looper.getMainLooper()).post { cleanup() }
        }

        try {
            val wv = WebView(context.applicationContext)
            webView = wv
            wv.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)

            val settings = wv.settings
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.blockNetworkImage = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString = SupJavNetwork.DEFAULT_USER_AGENT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.cacheMode = WebSettings.LOAD_DEFAULT

            wv.webViewClient = object : WebViewClient() {
                override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                    Log.w(TAG, "SupJav fallback: Renderer process gone, cleaning up")
                    cleanup()
                    if (continuation.isActive) continuation.resume(null)
                    return true
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val reqUrl = request?.url?.toString() ?: return null
                    val lower = reqUrl.lowercase()

                    // Check for video streams
                    if ((lower.contains(".m3u8") || lower.contains(".mp4")) &&
                        !lower.contains("thumb") && !lower.contains("preview") && !lower.contains("poster")
                    ) {
                        if (isResolved.compareAndSet(false, true)) {
                            Log.i(TAG, "SupJav fallback: Captured video stream via network interception: $reqUrl")
                            val isHls = lower.contains(".m3u8")
                            val source = SupJavSource(
                                url = reqUrl,
                                mimeType = if (isHls) "application/x-mpegURL" else "video/mp4",
                                quality = if (isHls) "1080p FHD • SupJav Stream" else "720p HD",
                                isHls = isHls,
                                headers = mapOf(
                                    "User-Agent" to SupJavNetwork.DEFAULT_USER_AGENT,
                                    "Referer" to targetUrl
                                ),
                                sourceName = "SupJav (Interception Stream)"
                            )

                            Handler(Looper.getMainLooper()).post {
                                cleanup()
                                if (continuation.isActive) {
                                    continuation.resume(source)
                                }
                            }
                        }
                    }

                    // Block tracking & ad bloat
                    if (lower.contains("google-analytics") || lower.contains("doubleclick") ||
                        lower.contains("popads") || lower.contains("exoclick") || lower.contains("adtrue")
                    ) {
                        return WebResourceResponse("text/plain", "utf-8", null)
                    }

                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (isResolved.get()) return

                    // Inject JS to trigger any video element or extract iframe/video src
                    view?.evaluateJavascript(
                        """
                        (function() {
                            try {
                                var videos = document.querySelectorAll('video, source');
                                for (var i = 0; i < videos.length; i++) {
                                    var src = videos[i].src || videos[i].currentSrc;
                                    if (src && (src.indexOf('.m3u8') !== -1 || src.indexOf('.mp4') !== -1)) {
                                        return src;
                                    }
                                }
                                var iframes = document.querySelectorAll('iframe');
                                for (var j = 0; j < iframes.length; j++) {
                                    var isrc = iframes[j].src;
                                    if (isrc && isrc.indexOf('http') === 0) {
                                        // found iframe
                                    }
                                }
                            } catch(e) {}
                            return '';
                        })();
                        """.trimIndent()
                    ) { result ->
                        val clean = result?.trim('"', '\'') ?: ""
                        if (clean.startsWith("http") && (clean.contains(".m3u8") || clean.contains(".mp4"))) {
                            if (isResolved.compareAndSet(false, true)) {
                                val isHls = clean.contains(".m3u8")
                                val source = SupJavSource(
                                    url = clean,
                                    mimeType = if (isHls) "application/x-mpegURL" else "video/mp4",
                                    quality = if (isHls) "1080p FHD" else "720p HD",
                                    isHls = isHls,
                                    headers = mapOf(
                                        "User-Agent" to SupJavNetwork.DEFAULT_USER_AGENT,
                                        "Referer" to targetUrl
                                    ),
                                    sourceName = "SupJav (DOM Stream)"
                                )
                                cleanup()
                                if (continuation.isActive) {
                                    continuation.resume(source)
                                }
                            }
                        }
                    }
                }

                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    handler?.proceed()
                }
            }

            wv.loadUrl(targetUrl)
        } catch (e: Exception) {
            Log.e(TAG, "SupJav fallback: Exception initializing WebView: ${e.message}")
            cleanup()
            if (continuation.isActive) continuation.resume(null)
        }
    }

    private suspend fun runHeadlessHtmlCapture(
        context: Context,
        targetUrl: String
    ): String? = suspendCancellableCoroutine { continuation ->
        var webView: WebView? = null
        val isFinished = AtomicBoolean(false)

        fun cleanup() {
            try {
                webView?.let { wv ->
                    wv.stopLoading()
                    wv.webViewClient = object : WebViewClient() {}
                    wv.loadUrl("about:blank")
                    wv.destroy()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning up html capture webview: ${e.message}")
            } finally {
                webView = null
            }
        }

        continuation.invokeOnCancellation {
            Handler(Looper.getMainLooper()).post { cleanup() }
        }

        try {
            val wv = WebView(context.applicationContext)
            webView = wv
            wv.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)

            val settings = wv.settings
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.blockNetworkImage = true
            settings.userAgentString = SupJavNetwork.DEFAULT_USER_AGENT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            wv.webViewClient = object : WebViewClient() {
                override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                    Log.w(TAG, "SupJav HTML fallback: Renderer process gone, cleaning up")
                    cleanup()
                    if (continuation.isActive) continuation.resume(null)
                    return true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (isFinished.compareAndSet(false, true)) {
                            view?.evaluateJavascript(
                                "(function() { return document.documentElement.outerHTML; })();"
                            ) { html ->
                                val unescaped = unescapeJsString(html ?: "")
                                cleanup()
                                if (continuation.isActive) continuation.resume(unescaped)
                            }
                        }
                    }, 2000L)
                }

                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    handler?.proceed()
                }
            }

            wv.loadUrl(targetUrl)
        } catch (e: Exception) {
            cleanup()
            if (continuation.isActive) continuation.resume(null)
        }
    }

    private fun unescapeJsString(str: String): String {
        var s = str
        if (s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length - 1)
        }
        return s.replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
    }
}
