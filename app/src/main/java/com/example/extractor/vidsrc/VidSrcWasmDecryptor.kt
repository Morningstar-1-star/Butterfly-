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

        val regex = Regex("""https?://[^\s'"]+?\.m3u8(?:\?[^\s'"]*)?""")
        val rawList = regex.findAll(decryptedText).map { it.value.trim() }.toList()
            .ifEmpty {
                decryptedText.split(Regex("[\n\r]+"))
                    .map { it.trim() }
                    .filter { it.startsWith("http") }
            }

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
                            var allocFn = ex.alloc || ex.malloc || ex._malloc;
                            if (!allocFn) {
                                window.VidSrcBridge.onError("No alloc/malloc export found in WASM");
                                return;
                            }
                            var ptr = allocFn(encBin.length);
                            var memU8 = new Uint8Array(ex.memory.buffer);
                            memU8.set(encBin, ptr);

                            var decryptFn = ex.decrypt || ex.decode || ex._decrypt;
                            if (!decryptFn) {
                                window.VidSrcBridge.onError("No decrypt export found in WASM");
                                return;
                            }
                            var retVal = decryptFn(ptr, encBin.length);
                            memU8 = new Uint8Array(ex.memory.buffer); // re-acquire in case of memory growth

                            var res = null;
                            var decoder = new TextDecoder();

                            // Strategy 1: Check if export get_result / getResult exists
                            if (typeof ex.get_result === 'function' || typeof ex.getResult === 'function') {
                                try {
                                    var resPtr = (ex.get_result || ex.getResult)();
                                    if (resPtr > 0 && resPtr < memU8.length) {
                                        var end = resPtr;
                                        while (end < memU8.length && memU8[end] !== 0 && (end - resPtr) < 8192) end++;
                                        var candidate = decoder.decode(memU8.subarray(resPtr, end));
                                        if (candidate.indexOf("http") !== -1 || candidate.indexOf(".m3u8") !== -1) {
                                            res = candidate;
                                        }
                                    }
                                } catch(e) {}
                            }

                            // Strategy 2: retVal is output length with 12-byte nonce offset (ptr + 12)
                            if (!res && retVal > 0 && retVal < 100000 && (ptr + 12 + retVal) <= memU8.length) {
                                try {
                                    var candidate = decoder.decode(memU8.subarray(ptr + 12, ptr + 12 + retVal));
                                    if (candidate.indexOf("http") !== -1 || candidate.indexOf(".m3u8") !== -1) {
                                        res = candidate;
                                    }
                                } catch(e) {}
                            }

                            // Strategy 3: retVal is output length starting at ptr (no nonce offset)
                            if (!res && retVal > 0 && retVal < 100000 && (ptr + retVal) <= memU8.length) {
                                try {
                                    var candidate = decoder.decode(memU8.subarray(ptr, ptr + retVal));
                                    if (candidate.indexOf("http") !== -1 || candidate.indexOf(".m3u8") !== -1) {
                                        res = candidate;
                                    }
                                } catch(e) {}
                            }

                            // Strategy 4: retVal is an output pointer to null-terminated string
                            if (!res && retVal > 0 && retVal < memU8.length) {
                                try {
                                    var end = retVal;
                                    while (end < memU8.length && memU8[end] !== 0 && (end - retVal) < 8192) end++;
                                    var candidate = decoder.decode(memU8.subarray(retVal, end));
                                    if (candidate.indexOf("http") !== -1 || candidate.indexOf(".m3u8") !== -1) {
                                        res = candidate;
                                    }
                                } catch(e) {}
                            }

                            // Strategy 5: Memory scan around ptr for "http" or JSON
                            if (!res) {
                                for (var off = 0; off <= 32; off += 4) {
                                    var start = ptr + off;
                                    if (start + 8 < memU8.length) {
                                        var head = String.fromCharCode(memU8[start], memU8[start+1], memU8[start+2], memU8[start+3]);
                                        if (head === "http" || head === "{\"") {
                                            var len = (retVal > 0 && retVal < 10000) ? retVal : (encBin.length - off);
                                            res = decoder.decode(memU8.subarray(start, Math.min(start + len, memU8.length)));
                                            break;
                                        }
                                    }
                                }
                            }

                            if (res) {
                                window.VidSrcBridge.onDecrypted(res);
                            } else {
                                window.VidSrcBridge.onError("Could not locate decrypted URL in WASM memory");
                            }
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
