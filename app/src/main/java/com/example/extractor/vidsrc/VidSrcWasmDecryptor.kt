package com.example.extractor.vidsrc

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.example.util.NetworkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * On-device WebAssembly decryptor for VidSrc and Decryptor stream URLs.
 *
 * Executes the transparent per-window WebAssembly decryption module (vsdec)
 * natively within a headless WebView JavaScript environment without any external network calls in JS.
 */
object VidSrcWasmDecryptor {

    private const val TAG = "VidSrcWasmDecryptor"
    private const val TIMEOUT_MS = 6500L
    private const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    // Cache downloaded wasm bytecode by URL so subsequent requests avoid network roundtrips
    private val wasmCache = ConcurrentHashMap<String, ByteArray>()

    /**
     * Decrypts obfuscated ChaCha20 base64 stream URLs using the provided WebAssembly binary.
     * Returns the list of decrypted master M3U8 URLs.
     */
    suspend fun decrypt(
        context: Context,
        wasmUrl: String,
        encryptedBase64: String
    ): List<String> {
        val cleanEnc = encryptedBase64.trim()
        val cleanWasmUrl = wasmUrl.trim()
        if (cleanEnc.isBlank() || cleanWasmUrl.isBlank()) return emptyList()

        // 1. Download WASM bytecode on IO thread
        val wasmBytes = withContext(Dispatchers.IO) {
            wasmCache[cleanWasmUrl] ?: try {
                val req = Request.Builder()
                    .url(cleanWasmUrl)
                    .header("Referer", "https://cloudorchestranova.com/")
                    .header("Origin", "https://cloudorchestranova.com")
                    .header("User-Agent", DEFAULT_UA)
                    .build()
                val resp = httpClient.newCall(req).execute()
                val bytes = resp.body?.bytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    wasmCache[cleanWasmUrl] = bytes
                    bytes
                } else null
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch WASM bytecode: ${e.message}")
                null
            }
        } ?: return emptyList()

        val wasmBase64 = Base64.encodeToString(wasmBytes, Base64.NO_WRAP)

        // 2. Execute WebAssembly instantiation and decryption in headless WebView
        val decryptedText = withContext(Dispatchers.Main) {
            try {
                withTimeoutOrNull(TIMEOUT_MS) {
                    runWasmInWebView(context, wasmBase64, cleanEnc)
                }
            } catch (e: Exception) {
                Log.w(TAG, "WASM execution timeout/error: ${e.message}")
                null
            }
        }

        if (decryptedText.isNullOrBlank()) {
            Log.w(TAG, "WASM decryption returned empty result")
            return emptyList()
        }

        val rawList = decryptedText.split("\n")
            .map { it.trim() }
            .filter { it.startsWith("http") }

        Log.i(TAG, "Successfully decrypted ${rawList.size} master M3U8 stream URLs via WASM")
        return rawList
    }

    private class DecryptBridge(
        private val onResult: (String?) -> Unit
    ) {
        @JavascriptInterface
        fun onDecrypted(result: String) {
            onResult(result)
        }

        @JavascriptInterface
        fun onError(err: String) {
            Log.w(TAG, "WebAssembly JS execution error: $err")
            onResult(null)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun runWasmInWebView(
        context: Context,
        wasmBase64: String,
        encBase64: String
    ): String? = suspendCancellableCoroutine { cont ->
        var resumed = false
        fun finish(res: String?) {
            if (!resumed) {
                resumed = true
                cont.resume(res)
            }
        }

        var webView: WebView? = null
        try {
            val appCtx = context.applicationContext ?: context
            webView = WebView(appCtx)
            webView.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
            val settings = webView.settings
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.blockNetworkImage = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false

            webView.webViewClient = object : android.webkit.WebViewClient() {
                override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                    Log.w(TAG, "VidSrc WASM WebView renderer process gone")
                    finish(null)
                    return true
                }
            }

            val bridge = DecryptBridge { res ->
                finish(res)
                Handler(Looper.getMainLooper()).post {
                    try { webView?.destroy() } catch (_: Exception) {}
                }
            }
            webView.addJavascriptInterface(bridge, "VidSrcBridge")

            val html = """
                <!DOCTYPE html>
                <html>
                <head><meta charset="utf-8"></head>
                <body>
                <script>
                (function() {
                    try {
                        var wasmStr = "$wasmBase64";
                        var encStr = "$encBase64";
                        var wasmBin = Uint8Array.from(atob(wasmStr), function(c) { return c.charCodeAt(0); });
                        WebAssembly.instantiate(wasmBin, {}).then(function(inst) {
                            var ex = inst.exports;
                            var encBin = Uint8Array.from(atob(encStr), function(c) { return c.charCodeAt(0); });
                            var ptr = ex.alloc(encBin.length);
                            new Uint8Array(ex.memory.buffer, ptr, encBin.length).set(encBin);
                            var outLen = ex.decrypt(ptr, encBin.length);
                            var decBytes = new Uint8Array(ex.memory.buffer, ptr + 12, outLen);
                            var res = new TextDecoder().decode(decBytes);
                            window.VidSrcBridge.onDecrypted(res);
                        }).catch(function(err) {
                            window.VidSrcBridge.onError(String(err));
                        });
                    } catch(e) {
                        window.VidSrcBridge.onError(String(e));
                    }
                })();
                </script>
                </body>
                </html>
            """.trimIndent()

            webView.loadDataWithBaseURL("https://cloudorchestranova.com/", html, "text/html", "UTF-8", null)

        } catch (e: Exception) {
            Log.e(TAG, "WebView instantiation failure: ${e.message}", e)
            try { webView?.destroy() } catch (_: Exception) {}
            finish(null)
        }

        cont.invokeOnCancellation {
            try { webView?.destroy() } catch (_: Exception) {}
        }
    }
}
