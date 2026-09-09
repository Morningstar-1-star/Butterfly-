package com.example.extractor.sextb

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.example.model.VideoItem
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Controlled, isolated Headless WebView Fallback for SEXТB.
 *
 * ONLY triggered when native HTTP parsing fails due to Cloudflare JS challenges
 * or dynamic client-side player initialization.
 *
 * Guarantees:
 * - Scoped network interception detecting real .m3u8 / .mp4 streams.
 * - Strict timeout (default 15s) preventing stalled executions.
 * - Immediate deterministic cleanup on detection, cancellation, or error.
 * - Low-RAM device protection: never leaves invisible WebViews running.
 */
object SextbWebViewFallback {

    private const val TAG = "SextbWebViewFallback"
    private const val FALLBACK_TIMEOUT_MS = 15000L

    /**
     * Attempts to resolve media URL by loading the page/player inside a headless WebView
     * and intercepting media requests or extracting video elements.
     */
    suspend fun resolveWithFallback(
        context: Context,
        targetUrl: String,
        timeoutMs: Long = FALLBACK_TIMEOUT_MS
    ): VideoSource? = withContext(Dispatchers.Main) {
        Log.i(TAG, "SEXТB fallback: Initiating controlled headless WebView fallback for $targetUrl")

        try {
            withTimeoutOrNull(timeoutMs) {
                runHeadlessCapture(context, targetUrl)
            }
        } catch (e: Exception) {
            Log.w(TAG, "SEXТB fallback: WebView extraction timed out or failed: ${e.message}")
            null
        }
    }

    private suspend fun runHeadlessCapture(
        context: Context,
        targetUrl: String
    ): VideoSource? = suspendCancellableCoroutine { continuation ->
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
                Log.w(TAG, "SEXТB fallback: Error disposing fallback WebView: ${e.message}")
            } finally {
                webView = null
            }
        }

        try {
            webView = WebView(context.applicationContext).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = false
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    userAgentString = SextbResolver.DEFAULT_UA
                    cacheMode = WebSettings.LOAD_NO_CACHE
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val reqUrl = request?.url?.toString() ?: return null

                        if (isMediaStreamUrl(reqUrl) && isResolved.compareAndSet(false, true)) {
                            Log.i(TAG, "SEXТB fallback: Detected playable stream via request interception: ${sanitizeLogUrl(reqUrl)}")

                            val reqHeaders = mutableMapOf<String, String>()
                            request.requestHeaders?.forEach { (k, v) ->
                                if (k.equals("Referer", ignoreCase = true) ||
                                    k.equals("Origin", ignoreCase = true) ||
                                    k.equals("User-Agent", ignoreCase = true)
                                ) {
                                    reqHeaders[k] = v
                                }
                            }
                            if (!reqHeaders.containsKey("Referer")) {
                                reqHeaders["Referer"] = targetUrl
                            }
                            if (!reqHeaders.containsKey("User-Agent")) {
                                reqHeaders["User-Agent"] = SextbResolver.DEFAULT_UA
                            }

                            val isHls = reqUrl.contains(".m3u8")
                            val source = VideoSource(
                                url = reqUrl,
                                mimeType = if (isHls) "application/x-mpegURL" else "video/mp4",
                                quality = "1080p",
                                headers = reqHeaders,
                                sourceName = "SEXТB WebView Fallback"
                            )

                            Handler(Looper.getMainLooper()).post {
                                cleanupWebView()
                                if (continuation.isActive) {
                                    continuation.resume(source)
                                }
                            }
                        }

                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (isResolved.get()) return

                        // Evaluate JS for HTML5 video element source or player config
                        val jsExtract = """
                            (function() {
                                var v = document.querySelector('video');
                                if (v && v.src && v.src.indexOf('http') === 0) return v.src;
                                if (v && v.currentSrc && v.currentSrc.indexOf('http') === 0) return v.currentSrc;
                                var s = document.querySelector('video source');
                                if (s && s.src && s.src.indexOf('http') === 0) return s.src;
                                return '';
                            })();
                        """.trimIndent()

                        view?.evaluateJavascript(jsExtract) { result ->
                            if (!isResolved.get()) {
                                val cleanUrl = result?.trim('"', '\'') ?: ""
                                if (isMediaStreamUrl(cleanUrl) && isResolved.compareAndSet(false, true)) {
                                    Log.i(TAG, "SEXТB fallback: Extracted media from DOM video element: ${sanitizeLogUrl(cleanUrl)}")
                                    val isHls = cleanUrl.contains(".m3u8")
                                    val source = VideoSource(
                                        url = cleanUrl,
                                        mimeType = if (isHls) "application/x-mpegURL" else "video/mp4",
                                        quality = "1080p",
                                        headers = mapOf(
                                            "Referer" to targetUrl,
                                            "User-Agent" to SextbResolver.DEFAULT_UA
                                        ),
                                        sourceName = "SEXТB WebView DOM"
                                    )

                                    cleanupWebView()
                                    if (continuation.isActive) {
                                        continuation.resume(source)
                                    }
                                }
                            }
                        }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        Log.w(TAG, "SEXТB fallback: WebView error ($errorCode): $description")
                    }
                }

                loadUrl(targetUrl)
            }
        } catch (e: Exception) {
            Log.e(TAG, "SEXТB fallback: Failed to initialize fallback WebView: ${e.message}")
            cleanupWebView()
            if (continuation.isActive) {
                continuation.resume(null)
            }
        }

        continuation.invokeOnCancellation {
            Handler(Looper.getMainLooper()).post {
                Log.d(TAG, "SEXТB fallback: Cancellation requested; cleaning up WebView")
                cleanupWebView()
            }
        }
    }

    private fun isMediaStreamUrl(url: String): Boolean {
        if (!url.startsWith("http")) return false
        val lower = url.lowercase()
        return lower.contains(".m3u8") ||
               (lower.contains(".mp4") && !lower.contains("preview") && !lower.contains("thumb")) ||
               lower.contains("master.m3u8") ||
               lower.contains("index.m3u8")
    }

    private fun sanitizeLogUrl(url: String): String {
        return try {
            val uri = java.net.URI(url)
            "${uri.scheme}://${uri.host}${uri.path}"
        } catch (_: Exception) {
            url.substringBefore("?")
        }
    }

    /**
     * Scrapes video items from a catalog page inside a headless WebView if direct HTTP is blocked.
     */
    suspend fun scrapeCatalog(
        context: Context,
        targetUrl: String,
        timeoutMs: Long = 8000L
    ): List<VideoItem> = withContext(Dispatchers.Main) {
        Log.i(TAG, "SEXТB fallback: Initiating headless catalog scraper for $targetUrl")
        try {
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<List<VideoItem>> { continuation ->
                    var webView: WebView? = null
                    val isDone = AtomicBoolean(false)

                    fun cleanup() {
                        try {
                            webView?.let { wv ->
                                wv.stopLoading()
                                wv.webViewClient = object : WebViewClient() {}
                                wv.loadUrl("about:blank")
                                wv.destroy()
                            }
                        } catch (_: Exception) {
                        } finally {
                            webView = null
                        }
                    }

                    try {
                        webView = WebView(context.applicationContext).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                userAgentString = SextbResolver.DEFAULT_UA
                                cacheMode = WebSettings.LOAD_DEFAULT
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    // Give DOM 1.2s to render dynamic video cards
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        if (isDone.get()) return@postDelayed

                                        val js = """
                                            (function() {
                                                var items = [];
                                                var nodes = document.querySelectorAll('.video-item, .item, .thumb-block, a[href*="/video/"]');
                                                for (var i = 0; i < nodes.length && items.length < 30; i++) {
                                                    var el = nodes[i];
                                                    var a = el.tagName.toLowerCase() === 'a' ? el : el.querySelector('a[href*="/video/"]');
                                                    if (!a || !a.href) continue;
                                                    var img = el.querySelector('img');
                                                    var title = a.getAttribute('title') || (img ? img.getAttribute('alt') : '') || a.innerText.trim();
                                                    var thumb = img ? (img.getAttribute('data-src') || img.getAttribute('src') || '') : '';
                                                    var durEl = el.querySelector('.duration, .time');
                                                    var dur = durEl ? durEl.innerText.trim() : '';
                                                    items.push({
                                                        href: a.href,
                                                        title: title,
                                                        thumb: thumb,
                                                        duration: dur
                                                    });
                                                }
                                                return JSON.stringify(items);
                                            })();
                                        """.trimIndent()

                                        view?.evaluateJavascript(js) { jsonStr ->
                                            if (isDone.compareAndSet(false, true)) {
                                                val videoItems = mutableListOf<VideoItem>()
                                                try {
                                                    val raw = jsonStr?.trim('"', '\\')?.replace("\\\"", "\"") ?: "[]"
                                                    val arr = JSONArray(raw)
                                                    for (i in 0 until arr.length()) {
                                                        val obj = arr.getJSONObject(i)
                                                        val href = obj.optString("href")
                                                        val title = obj.optString("title")
                                                        val thumb = obj.optString("thumb")
                                                        val dur = obj.optString("duration")
                                                        if (href.isNotBlank() && title.isNotBlank()) {
                                                            val vidId = SextbParser.extractVideoIdFromUrl(href)
                                                            videoItems.add(
                                                                VideoItem(
                                                                    id = vidId,
                                                                    title = title,
                                                                    uploaderName = "SEXТB",
                                                                    uploaderUrl = href,
                                                                    thumbnailUrl = if (thumb.startsWith("http")) thumb else if (thumb.startsWith("//")) "https:$thumb" else null,
                                                                    durationSeconds = com.example.model.parseDurationToSeconds(dur),
                                                                    providerId = "sextb"
                                                                )
                                                            )
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    Log.w(TAG, "Failed parsing WebView catalog json: ${e.message}")
                                                }

                                                cleanup()
                                                if (continuation.isActive) {
                                                    continuation.resume(videoItems)
                                                }
                                            }
                                        }
                                    }, 1200L)
                                }

                                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                                    Log.w(TAG, "Catalog scraper WebView error ($errorCode): $description")
                                }
                            }

                            loadUrl(targetUrl)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed initializing catalog WebView: ${e.message}")
                        cleanup()
                        if (continuation.isActive) continuation.resume(emptyList())
                    }

                    continuation.invokeOnCancellation {
                        Handler(Looper.getMainLooper()).post { cleanup() }
                    }
                }
            } ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Catalog WebView scraper timed out: ${e.message}")
            emptyList()
        }
    }
}
