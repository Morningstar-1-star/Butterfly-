package com.example.plugin.runtime

import android.content.Context
import com.example.plugin.bridge.HttpBridge
import com.example.plugin.bridge.LoggingBridge
import com.example.plugin.bridge.StorageBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.javascript.ScriptableObject
import java.io.File

class NativeHttpHelper(private val httpBridge: HttpBridge) {
    fun get(url: String, headersJson: String?): String {
        return runBlocking {
            try {
                val headerMap = if (!headersJson.isNullOrEmpty()) {
                    val json = JSONObject(headersJson)
                    val map = mutableMapOf<String, String>()
                    json.keys().forEach { key -> map[key] = json.getString(key) }
                    map
                } else emptyMap()

                val resp = httpBridge.get(url, headerMap)
                val obj = JSONObject()
                obj.put("status", resp.statusCode)
                obj.put("body", resp.body)
                obj.toString()
            } catch (e: Exception) {
                val err = JSONObject()
                err.put("error", e.localizedMessage ?: "HTTP Error")
                err.put("status", 500)
                err.toString()
            }
        }
    }

    fun post(url: String, body: String, contentType: String?, headersJson: String?): String {
        return runBlocking {
            try {
                val headerMap = if (!headersJson.isNullOrEmpty()) {
                    val json = JSONObject(headersJson)
                    val map = mutableMapOf<String, String>()
                    json.keys().forEach { key -> map[key] = json.getString(key) }
                    map
                } else emptyMap()

                val resp = httpBridge.post(url, body, contentType ?: "application/json", headerMap)
                val obj = JSONObject()
                obj.put("status", resp.statusCode)
                obj.put("body", resp.body)
                obj.toString()
            } catch (e: Exception) {
                val err = JSONObject()
                err.put("error", e.localizedMessage ?: "HTTP Error")
                err.put("status", 500)
                err.toString()
            }
        }
    }
}

class NativeStorageHelper(private val storageBridge: StorageBridge) {
    fun getItem(key: String): String? = storageBridge.getString(key)
    fun setItem(key: String, value: String) = storageBridge.putString(key, value)
    fun removeItem(key: String) = storageBridge.remove(key)
}

class JsProviderEngine(
    private val context: Context,
    val manifest: PluginManifest,
    private val pluginDir: File,
    private val httpBridge: HttpBridge = HttpBridge(),
    private val storageBridge: StorageBridge = StorageBridge(context, manifest.id),
    private val logger: LoggingBridge = LoggingBridge(manifest.id)
) : ContentProviderApi {

    override val providerId: String = manifest.id

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val jsFile: File? by lazy {
        findJsEntryFile()
    }

    private fun findJsEntryFile(): File? {
        val entryName = manifest.entryFile.takeIf { it.isNotEmpty() } ?: "main.js"
        val explicit = File(pluginDir, entryName)
        if (explicit.exists()) return explicit

        val distIndex = File(pluginDir, "dist/index.js")
        if (distIndex.exists()) return distIndex

        val indexJs = File(pluginDir, "index.js")
        if (indexJs.exists()) return indexJs

        val mainJs = File(pluginDir, "main.js")
        if (mainJs.exists()) return mainJs

        return pluginDir.listFiles { _, name -> name.endsWith(".js") }?.firstOrNull()
    }

    /**
     * Executes a JavaScript function in Rhino JS context and returns raw JSON string result.
     */
    private suspend fun evalJsFunction(functionName: String, vararg args: Any?): String? = withContext(Dispatchers.IO) {
        val file = jsFile ?: run {
            logger.e("No valid JavaScript entry file found in plugin directory: ${pluginDir.absolutePath}")
            return@withContext null
        }

        val rhinoCtx = org.mozilla.javascript.Context.enter()
        rhinoCtx.optimizationLevel = -1 // Interpreted mode required for Android DEX compatibility

        try {
            val scope = rhinoCtx.initStandardObjects()

            // Bind native HTTP and Storage helpers to JS scope via Context.javaToJS
            val httpHelper = NativeHttpHelper(httpBridge)
            val storageHelper = NativeStorageHelper(storageBridge)

            val jsHttpObj = org.mozilla.javascript.Context.javaToJS(httpHelper, scope)
            val jsStorageObj = org.mozilla.javascript.Context.javaToJS(storageHelper, scope)

            ScriptableObject.putProperty(scope, "__httpNative", jsHttpObj)
            ScriptableObject.putProperty(scope, "__storageNative", jsStorageObj)

            // Setup Console & Environment JS runtime code
            val setupJs = """
                var global = this;
                var globalThis = this;
                var window = this;
                var exports = {};
                var module = { exports: exports };

                var console = {
                    log: function() {
                        var msg = Array.prototype.slice.call(arguments).join(' ');
                        java.lang.System.out.println("[JS-LOG] " + msg);
                    },
                    error: function() {
                        var msg = Array.prototype.slice.call(arguments).join(' ');
                        java.lang.System.err.println("[JS-ERR] " + msg);
                    }
                };

                var ButterflyContext = {
                    baseUrl: "${manifest.repository ?: ""}",
                    headers: {},
                    cookies: {},
                    storage: {
                        getItem: function(k) { return __storageNative.getItem(k); },
                        setItem: function(k, v) { __storageNative.setItem(k, v); },
                        removeItem: function(k) { __storageNative.removeItem(k); }
                    },
                    http: {
                        get: function(url, headers) {
                            var h = headers ? JSON.stringify(headers) : "{}";
                            var raw = __httpNative.get(url, h);
                            return JSON.parse(raw);
                        },
                        post: function(url, body, contentType, headers) {
                            var h = headers ? JSON.stringify(headers) : "{}";
                            var raw = __httpNative.post(url, typeof body === 'string' ? body : JSON.stringify(body), contentType || "application/json", h);
                            return JSON.parse(raw);
                        }
                    }
                };

                // Simple axios/fetch polyfill for Vega providers
                var axios = {
                    get: function(url, config) {
                        var headers = (config && config.headers) ? config.headers : {};
                        var resp = ButterflyContext.http.get(url, headers);
                        return { data: resp.body ? (resp.body.startsWith('{') || resp.body.startsWith('[') ? JSON.parse(resp.body) : resp.body) : "", status: resp.status };
                    },
                    post: function(url, data, config) {
                        var headers = (config && config.headers) ? config.headers : {};
                        var resp = ButterflyContext.http.post(url, data, "application/json", headers);
                        return { data: resp.body ? (resp.body.startsWith('{') || resp.body.startsWith('[') ? JSON.parse(resp.body) : resp.body) : "", status: resp.status };
                    }
                };

                var fetch = function(url, opts) {
                    opts = opts || {};
                    var method = (opts.method || 'GET').toUpperCase();
                    if (method === 'POST') {
                        var resp = ButterflyContext.http.post(url, opts.body || '', opts.headers || {});
                        return Promise.resolve({
                            json: function() { return Promise.resolve(JSON.parse(resp.body)); },
                            text: function() { return Promise.resolve(resp.body); }
                        });
                    } else {
                        var resp = ButterflyContext.http.get(url, opts.headers || {});
                        return Promise.resolve({
                            json: function() { return Promise.resolve(JSON.parse(resp.body)); },
                            text: function() { return Promise.resolve(resp.body); }
                        });
                    }
                };
            """.trimIndent()

            rhinoCtx.evaluateString(scope, setupJs, "setup.js", 1, null)

            // Evaluate the Provider JS bundle
            val code = file.readText()
            rhinoCtx.evaluateString(scope, code, file.name, 1, null)

            // Construct function invocation runner
            val argsSerialized = args.joinToString(",") { arg ->
                when (arg) {
                    null -> "null"
                    is String -> JSONObject.quote(arg)
                    is Number, is Boolean -> arg.toString()
                    else -> JSONObject.quote(arg.toString())
                }
            }

            val runnerJs = """
                (function() {
                    try {
                        var fn = null;
                        if (typeof $functionName === 'function') {
                            fn = $functionName;
                        } else if (typeof exports !== 'undefined' && typeof exports.$functionName === 'function') {
                            fn = exports.$functionName;
                        } else if (typeof module !== 'undefined' && module.exports && typeof module.exports.$functionName === 'function') {
                            fn = module.exports.$functionName;
                        } else if (typeof module !== 'undefined' && module.exports && typeof module.exports.default === 'object' && typeof module.exports.default.$functionName === 'function') {
                            fn = module.exports.default.$functionName;
                        }

                        if (!fn) {
                            return JSON.stringify({ __error: "Function '$functionName' not found in provider script" });
                        }

                        var result = fn(ButterflyContext, $argsSerialized);
                        if (result && typeof result.then === 'function') {
                            var syncRes = null;
                            result.then(function(res) { syncRes = res; }).catch(function(err) { syncRes = { __error: String(err) }; });
                            return JSON.stringify(syncRes);
                        }
                        return JSON.stringify(result);
                    } catch(e) {
                        return JSON.stringify({ __error: e.toString() + (e.stack ? '\n' + e.stack : '') });
                    }
                })()
            """.trimIndent()

            val rawResult = rhinoCtx.evaluateString(scope, runnerJs, "runner.js", 1, null)
            rawResult?.toString()
        } catch (e: Exception) {
            logger.e("JS execution exception in $functionName", e)
            JSONObject().put("__error", e.localizedMessage ?: "JS Exception").toString()
        } finally {
            org.mozilla.javascript.Context.exit()
        }
    }

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val jsonStr = evalJsFunction("catalog", page) ?: evalJsFunction("getPosts", "popular", page)

        if (jsonStr == null) return@withContext PagedResult(emptyList())

        val items = parsePostsJson(jsonStr)
        PagedResult(items = items, nextPageToken = (page + 1).toString(), hasMore = items.isNotEmpty())
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val jsonStr = evalJsFunction("getSearchPosts", query, page) ?: evalJsFunction("search", query, page)

        if (jsonStr == null) return@withContext PagedResult(emptyList())

        val items = parsePostsJson(jsonStr)
        PagedResult(items = items, nextPageToken = (page + 1).toString(), hasMore = items.isNotEmpty())
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val jsonStr = evalJsFunction("getMeta", idOrUrl)
        if (jsonStr != null) {
            try {
                val obj = JSONObject(jsonStr)
                if (!obj.has("__error")) {
                    return@withContext PluginVideoItem(
                        id = obj.optString("id", idOrUrl),
                        title = obj.optString("title", "Video"),
                        uploaderName = obj.optString("type", manifest.name),
                        thumbnailUrl = obj.optString("poster", obj.optString("image", null)),
                        providerId = providerId
                    )
                }
            } catch (e: Exception) {
                logger.e("Error parsing getMeta JSON", e)
            }
        }
        PluginVideoItem(id = idOrUrl, title = idOrUrl, uploaderName = manifest.name, providerId = providerId)
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val jsonStr = evalJsFunction("getStream", idOrUrl)
        val videoStreams = mutableListOf<PluginVideoStream>()
        var hlsUrl: String? = null

        if (jsonStr != null) {
            try {
                if (jsonStr.trim().startsWith("[")) {
                    val arr = JSONArray(jsonStr)
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        val url = item.optString("url", "")
                        val quality = item.optString("quality", "720p")
                        val isHls = item.optBoolean("isHls", false) || url.contains(".m3u8")
                        if (url.isNotEmpty()) {
                            if (isHls && hlsUrl == null) hlsUrl = url
                            videoStreams.add(
                                PluginVideoStream(
                                    url = url,
                                    qualityLabel = quality,
                                    format = if (isHls) "hls" else "mp4"
                                )
                            )
                        }
                    }
                } else if (jsonStr.trim().startsWith("{")) {
                    val obj = JSONObject(jsonStr)
                    val url = obj.optString("url", "")
                    if (url.isNotEmpty()) {
                        val isHls = obj.optBoolean("isHls", false) || url.contains(".m3u8")
                        if (isHls) hlsUrl = url
                        videoStreams.add(
                            PluginVideoStream(
                                url = url,
                                qualityLabel = obj.optString("quality", "720p"),
                                format = if (isHls) "hls" else "mp4"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                logger.e("Error parsing getStream JSON", e)
            }
        }

        PluginStreamInfo(
            id = idOrUrl,
            url = videoStreams.firstOrNull()?.url ?: idOrUrl,
            title = "Provider Stream",
            channelName = manifest.name,
            videoStreams = videoStreams,
            hlsUrl = hlsUrl
        )
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> =
        PagedResult(emptyList())

    override suspend fun getSubtitles(idOrUrl: String): List<PluginSubtitle> =
        emptyList()

    override suspend fun getChannel(channelIdOrUrl: String): PluginChannel =
        PluginChannel(id = channelIdOrUrl, name = manifest.name)

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist =
        PluginPlaylist(id = playlistIdOrUrl, title = manifest.name, uploaderName = manifest.name)

    override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> =
        emptyList()

    private fun parsePostsJson(jsonStr: String): List<PluginVideoItem> {
        val list = mutableListOf<PluginVideoItem>()
        try {
            val json = jsonStr.trim()
            val array = if (json.startsWith("[")) {
                JSONArray(json)
            } else if (json.startsWith("{")) {
                val obj = JSONObject(json)
                obj.optJSONArray("data") ?: obj.optJSONArray("posts") ?: JSONArray()
            } else {
                JSONArray()
            }

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val id = item.optString("id", item.optString("link", item.optString("url", "item_$i")))
                val title = item.optString("title", item.optString("name", "Untitled"))
                val img = item.optString("image", item.optString("poster", item.optString("thumbnail", null)))
                list.add(
                    PluginVideoItem(
                        id = id,
                        title = title,
                        uploaderName = manifest.name,
                        thumbnailUrl = img,
                        providerId = providerId
                    )
                )
            }
        } catch (e: Exception) {
            logger.e("Failed to parse provider posts JSON", e)
        }
        return list
    }
}
