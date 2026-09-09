package com.example.extractor.sextb

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    private class FallbackBridge(
        private val onMedia: (String) -> Unit,
        private val onIframe: (String) -> Unit
    ) {
        @android.webkit.JavascriptInterface
        fun onMediaFound(url: String) {
            onMedia(url)
        }

        @android.webkit.JavascriptInterface
        fun onIframeFound(url: String) {
            onIframe(url)
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
            android.webkit.CookieManager.getInstance().apply {
                setAcceptCookie(true)
            }

            webView = WebView(context.applicationContext).apply {
                android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    javaScriptCanOpenWindowsAutomatically = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    userAgentString = SextbResolver.DEFAULT_UA
                    cacheMode = WebSettings.LOAD_NO_CACHE
                }

                addJavascriptInterface(
                    FallbackBridge(
                        onMedia = { mediaUrl ->
                            if (isResolved.compareAndSet(false, true)) {
                                Log.i(TAG, "SEXТB fallback: Found media stream via JS Bridge: $mediaUrl")
                                val isHls = mediaUrl.contains(".m3u8") || !mediaUrl.contains(".mp4")
                                val clean = StbturboExtractor.httpsify(mediaUrl)
                                val streamHost = StbturboExtractor.extractHost(clean)
                                val playbackHost = if (streamHost.isNotBlank()) streamHost else "stbturbo.xyz"
                                val source = VideoSource(
                                    url = clean,
                                    mimeType = if (isHls) "application/x-mpegURL" else "video/mp4",
                                    quality = "1080p",
                                    isHls = isHls,
                                    headers = mapOf(
                                        "Referer" to "https://$playbackHost/",
                                        "User-Agent" to SextbResolver.DEFAULT_UA,
                                        "Origin" to "https://$playbackHost"
                                    ),
                                    sourceName = "SEXТB Direct"
                                )
                                Handler(Looper.getMainLooper()).post {
                                    cleanupWebView()
                                    if (continuation.isActive) {
                                        continuation.resume(source)
                                    }
                                }
                            }
                        },
                        onIframe = { iframeUrl ->
                            if (!isResolved.get()) {
                                val cleanIframe = SextbResolver.cleanIframeUrl(iframeUrl)
                                Log.i(TAG, "SEXТB fallback: Detected player iframe via JS Bridge: $cleanIframe")
                                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                    val extracted = SextbResolver.resolveIframeEmbed(cleanIframe, targetUrl)
                                    if (extracted.isNotEmpty() && isResolved.compareAndSet(false, true)) {
                                        Handler(Looper.getMainLooper()).post {
                                            cleanupWebView()
                                            if (continuation.isActive) {
                                                continuation.resume(extracted.first())
                                            }
                                        }
                                    } else if (!isResolved.get()) {
                                        // Load the iframe directly into the WebView so its player initializes in the top window
                                        Handler(Looper.getMainLooper()).post {
                                            if (!isResolved.get()) {
                                                webView?.loadUrl(cleanIframe)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    ),
                    "SextbBridge"
                )

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val reqUrl = request?.url?.toString() ?: return null

                        // 1. Direct media stream interception (.m3u8, .mp4)
                        if (isMediaStreamUrl(reqUrl) && isResolved.compareAndSet(false, true)) {
                            Log.i(TAG, "SEXТB fallback: Detected playable stream via request interception: ${sanitizeLogUrl(reqUrl)}")

                            val streamHost = StbturboExtractor.extractHost(reqUrl)
                            val playbackHost = if (streamHost.isNotBlank()) streamHost else "stbturbo.xyz"
                            val reqHeaders = mutableMapOf(
                                "User-Agent" to SextbResolver.DEFAULT_UA,
                                "Referer" to "https://$playbackHost/",
                                "Origin" to "https://$playbackHost"
                            )

                            val isHls = reqUrl.contains(".m3u8")
                            val source = VideoSource(
                                url = reqUrl,
                                mimeType = if (isHls) "application/x-mpegURL" else "video/mp4",
                                quality = "1080p",
                                headers = reqHeaders,
                                sourceName = "SEXТB Stream"
                            )

                            Handler(Looper.getMainLooper()).post {
                                cleanupWebView()
                                if (continuation.isActive) {
                                    continuation.resume(source)
                                }
                            }
                        }

                        // 2. Iframe player embed URL interception
                        if (SextbResolver.isEmbedPlayerUrl(reqUrl) && !isResolved.get()) {
                            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                val streams = SextbResolver.resolveIframeEmbed(reqUrl, targetUrl)
                                if (streams.isNotEmpty() && isResolved.compareAndSet(false, true)) {
                                    Handler(Looper.getMainLooper()).post {
                                        cleanupWebView()
                                        if (continuation.isActive) {
                                            continuation.resume(streams.first())
                                        }
                                    }
                                }
                            }
                        }

                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?
                    ) {
                        Log.d(TAG, "SEXТB fallback: Proceeding through SSL certificate warning in fallback WebView")
                        handler?.proceed()
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (isResolved.get()) return

                        val jsExtract = """
                            (function() {
                                try {
                                    var origFetch = window.fetch;
                                    if (origFetch) {
                                        window.fetch = function() {
                                            return origFetch.apply(this, arguments).then(function(res) {
                                                try {
                                                    var cln = res.clone();
                                                    cln.text().then(function(txt) {
                                                        if (window.SextbBridge) {
                                                            var matches = txt.match(/https?:\/\/[^\s"'<>\\]+/g);
                                                            if (matches) {
                                                                for (var i = 0; i < matches.length; i++) {
                                                                    var m = matches[i].replace(/\\\//g, '/');
                                                                    if (m.indexOf('.m3u8') !== -1 || m.indexOf('.mp4') !== -1) {
                                                                        window.SextbBridge.onMediaFound(m);
                                                                    } else if (m.indexOf('stbturbo') !== -1 || m.indexOf('streamtb') !== -1 || m.indexOf('streamtape') !== -1) {
                                                                        window.SextbBridge.onIframeFound(m);
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }).catch(function(){});
                                                } catch(e) {}
                                                return res;
                                            });
                                        };
                                    }
                                } catch(e) {}

                                try {
                                    var origOpen = XMLHttpRequest.prototype.open;
                                    var origSend = XMLHttpRequest.prototype.send;
                                    XMLHttpRequest.prototype.open = function(method, url) {
                                        this._url = url;
                                        return origOpen.apply(this, arguments);
                                    };
                                    XMLHttpRequest.prototype.send = function(data) {
                                        this.addEventListener('load', function() {
                                            try {
                                                if (this.responseText && window.SextbBridge) {
                                                    var matches = this.responseText.match(/https?:\/\/[^\s"'<>\\]+/g);
                                                    if (matches) {
                                                        for (var i = 0; i < matches.length; i++) {
                                                            var m = matches[i].replace(/\\\//g, '/');
                                                            if (m.indexOf('.m3u8') !== -1 || m.indexOf('.mp4') !== -1) {
                                                                window.SextbBridge.onMediaFound(m);
                                                            } else if (m.indexOf('stbturbo') !== -1 || m.indexOf('streamtb') !== -1 || m.indexOf('streamtape') !== -1) {
                                                                window.SextbBridge.onIframeFound(m);
                                                            }
                                                        }
                                                    }
                                                }
                                            } catch(e) {}
                                        });
                                        return origSend.apply(this, arguments);
                                    };
                                } catch(e) {}

                                function scan() {
                                    try {
                                        var vp = document.querySelector('#video_player') || document.querySelector('[data-hash]') || document.querySelector('.player-wrapper');
                                        if (vp) {
                                            var hash = vp.getAttribute('data-hash') || vp.getAttribute('data-src');
                                            if (hash && hash.length > 5) {
                                                window.SextbBridge.onMediaFound(hash);
                                                return true;
                                            }
                                        }
                                        var v = document.querySelector('video');
                                        if (v) {
                                            if (v.src && v.src.indexOf('http') === 0) {
                                                window.SextbBridge.onMediaFound(v.src);
                                                return true;
                                            }
                                            if (v.currentSrc && v.currentSrc.indexOf('http') === 0) {
                                                window.SextbBridge.onMediaFound(v.currentSrc);
                                                return true;
                                            }
                                        }
                                        var ifrs = document.querySelectorAll('iframe');
                                        for (var j = 0; j < ifrs.length; j++) {
                                            var src = ifrs[j].src || ifrs[j].getAttribute('data-src') || '';
                                            if (src && src.indexOf('http') === 0 && !src.includes('google') && !src.includes('ads') && !src.includes('analytics')) {
                                                window.SextbBridge.onIframeFound(src);
                                                return true;
                                            }
                                        }
                                    } catch(e) {}
                                    return false;
                                }

                                if (!scan()) {
                                    var btns = document.querySelectorAll('.episode-list .btn-player, .btn-player, .play-btn, .btn-play, #btn-player, .server-btn, [data-source]');
                                    for (var b = 0; b < btns.length; b++) {
                                        try { btns[b].click(); } catch(e) {}
                                    }
                                    var attempts = 0;
                                    var timer = setInterval(function() {
                                        attempts++;
                                        if (scan() || attempts > 35) {
                                            clearInterval(timer);
                                        }
                                    }, 250);
                                }
                            })();
                        """.trimIndent()

                        view?.evaluateJavascript(jsExtract, null)
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
                                                var nodes = document.querySelectorAll('.tray-item, .video-item, .item, .thumb-block, a[href*="/video/"]');
                                                for (var i = 0; i < nodes.length && items.length < 30; i++) {
                                                    var el = nodes[i];
                                                    var a = el.querySelector('a:nth-of-type(1)') || el.querySelector('a[href*="/video/"]') || (el.tagName.toLowerCase() === 'a' ? el : null);
                                                    if (!a || !a.href) continue;
                                                    var titleEl = el.querySelector('.tray-item-title') || el.querySelector('.title, h2, h3, h4');
                                                    var title = titleEl ? titleEl.innerText.trim() : (a.getAttribute('title') || a.innerText.trim());
                                                    var img = el.querySelector('.tray-item-thumbnail') || el.querySelector('img');
                                                    var thumb = img ? (img.getAttribute('data-src') || img.getAttribute('src') || '') : '';
                                                    var durEl = el.querySelector('.tray-film-views, .duration, .time');
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

                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: SslErrorHandler?,
                                    error: SslError?
                                ) {
                                    handler?.proceed()
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
