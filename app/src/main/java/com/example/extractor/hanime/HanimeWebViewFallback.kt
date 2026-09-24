package com.example.extractor.hanime

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import com.example.model.StreamData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Headless WebView media stream interceptor for Hanime sources.
 * Seamlessly resolves Cloudflare verification pages and captures
 * underlying direct HLS (.m3u8) or MP4 video streams.
 */
object HanimeWebViewFallback {
    private const val TAG = "HanimeWebViewFallback"
    private const val TIMEOUT_MS = 12000L

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun resolveStream(
        context: Context,
        targetUrl: String,
        videoId: String
    ): StreamData? = withContext(Dispatchers.Main) {
        val isResolved = AtomicBoolean(false)
        var webView: WebView? = null
        val handler = Handler(Looper.getMainLooper())

        val result = suspendCancellableCoroutine<PlayableStreamOption?> { continuation ->
            fun cleanup() {
                try {
                    webView?.stopLoading()
                    webView?.loadUrl("about:blank")
                    webView?.destroy()
                    webView = null
                } catch (e: Exception) {
                    Log.w(TAG, "WebView cleanup error: ${e.message}")
                }
            }

            val timeoutRunnable = Runnable {
                if (isResolved.compareAndSet(false, true)) {
                    Log.d(TAG, "Hanime WebView timeout reached ($TIMEOUT_MS ms)")
                    cleanup()
                    if (continuation.isActive) continuation.resume(null)
                }
            }
            handler.postDelayed(timeoutRunnable, TIMEOUT_MS)

            try {
                val wv = WebView(context.applicationContext)
                webView = wv

                val settings = wv.settings
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.userAgentString =
                    "Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

                wv.webViewClient = object : WebViewClient() {
                    override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                        handler.removeCallbacks(timeoutRunnable)
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

                        // Detect real video media streams (.m3u8, .mp4)
                        if ((lower.contains(".m3u8") || lower.contains(".mp4")) &&
                            !lower.contains("thumb") && !lower.contains("poster") && !lower.contains("preview") &&
                            !lower.contains(".png") && !lower.contains(".jpg") && !lower.contains(".webp")
                        ) {
                            if (isResolved.compareAndSet(false, true)) {
                                Log.i(TAG, "Hanime WebView captured video stream: $reqUrl")
                                handler.removeCallbacks(timeoutRunnable)

                                val isHls = lower.contains(".m3u8")
                                val option = PlayableStreamOption(
                                    qualityLabel = if (isHls) "720p HLS Stream" else "720p HD MP4",
                                    format = if (isHls) "m3u8" else "mp4",
                                    isMuxed = true,
                                    videoUrl = reqUrl,
                                    providerType = ProviderType.OTHER,
                                    headers = mapOf(
                                        "User-Agent" to settings.userAgentString,
                                        "Referer" to targetUrl
                                    )
                                )

                                Handler(Looper.getMainLooper()).post {
                                    cleanup()
                                    if (continuation.isActive) continuation.resume(option)
                                }
                            }
                        }

                        // Block advertising / tracker bloat
                        if (lower.contains("google-analytics") || lower.contains("doubleclick") ||
                            lower.contains("popads") || lower.contains("exoclick")
                        ) {
                            return WebResourceResponse("text/plain", "utf-8", null)
                        }

                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (isResolved.get()) return

                        // Check DOM for video or source elements
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
                                } catch(e) {}
                                return '';
                            })();
                            """.trimIndent()
                        ) { jsResult ->
                            val clean = jsResult?.trim('"', '\'') ?: ""
                            if (clean.startsWith("http") && (clean.contains(".m3u8") || clean.contains(".mp4"))) {
                                if (isResolved.compareAndSet(false, true)) {
                                    Log.i(TAG, "Hanime WebView DOM captured video stream: $clean")
                                    handler.removeCallbacks(timeoutRunnable)

                                    val isHls = clean.contains(".m3u8")
                                    val option = PlayableStreamOption(
                                        qualityLabel = if (isHls) "720p HLS (DOM)" else "720p HD (DOM)",
                                        format = if (isHls) "m3u8" else "mp4",
                                        isMuxed = true,
                                        videoUrl = clean,
                                        providerType = ProviderType.OTHER,
                                        headers = mapOf(
                                            "User-Agent" to settings.userAgentString,
                                            "Referer" to targetUrl
                                        )
                                    )
                                    cleanup()
                                    if (continuation.isActive) continuation.resume(option)
                                }
                            }
                        }
                    }
                }

                wv.loadUrl(targetUrl)
            } catch (e: Exception) {
                Log.w(TAG, "Error initializing WebView: ${e.message}")
                handler.removeCallbacks(timeoutRunnable)
                cleanup()
                if (continuation.isActive) continuation.resume(null)
            }

            continuation.invokeOnCancellation {
                handler.removeCallbacks(timeoutRunnable)
                cleanup()
            }
        }

        if (result != null) {
            StreamData(
                videoId = videoId,
                videoUrl = result.videoUrl ?: "",
                title = "Hanime Anime Stream",
                channelName = "Hanime",
                thumbnailUrl = null,
                availableStreamOptions = listOf(result),
                selectedStreamOption = result,
                providerId = "hanime1",
                headers = result.headers
            )
        } else {
            null
        }
    }
}
