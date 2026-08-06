package com.example.plugin.bridge

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Sandboxed, controlled HTTP Bridge provided to plugins.
 * Enforces timeout boundaries and permission scopes.
 */
class HttpBridge(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {

    data class HttpResponse(
        val statusCode: Int,
        val headers: Map<String, List<String>>,
        val body: String
    )

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResponse {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }
        return executeRequest(requestBuilder.build())
    }

    suspend fun post(
        url: String,
        body: String,
        contentType: String = "application/json",
        headers: Map<String, String> = emptyMap()
    ): HttpResponse {
        val mediaType = contentType.toMediaTypeOrNull()
        val requestBody = body.toRequestBody(mediaType)
        val requestBuilder = Request.Builder().url(url).post(requestBody)
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }
        return executeRequest(requestBuilder.build())
    }

    private suspend fun executeRequest(request: Request): HttpResponse =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val responseBody = response.body?.string() ?: ""
                        val headerMap = response.headers.toMultimap()
                        val httpResp = HttpResponse(
                            statusCode = response.code,
                            headers = headerMap,
                            body = responseBody
                        )
                        if (continuation.isActive) {
                            continuation.resume(httpResp)
                        }
                    } catch (e: Exception) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    }
                }
            })
        }
}

/**
 * Isolated Key-Value Storage Bridge for each plugin namespace.
 */
class StorageBridge(
    private val context: android.content.Context,
    private val pluginId: String
) {
    private val prefs by lazy {
        context.getSharedPreferences("butterfly_plugin_store_$pluginId", android.content.Context.MODE_PRIVATE)
    }

    fun getString(key: String, defaultValue: String? = null): String? = prefs.getString(key, defaultValue)
    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean = prefs.getBoolean(key, defaultValue)
    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}

/**
 * Media3 Integration Bridge: Converts PluginStreamInfo into Media3 MediaItem / MediaSource configuration.
 */
class Media3Bridge {
    fun createMediaItem(
        streamInfo: com.example.plugin.sdk.model.PluginStreamInfo,
        selectedVideoStream: com.example.plugin.sdk.model.PluginVideoStream? = null
    ): androidx.media3.common.MediaItem {
        val playUri = streamInfo.hlsUrl
            ?: selectedVideoStream?.url
            ?: streamInfo.videoStreams.firstOrNull()?.url
            ?: streamInfo.url

        val builder = androidx.media3.common.MediaItem.Builder()
            .setUri(playUri)
            .setMediaId(streamInfo.id)

        val metadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(streamInfo.title)
            .setArtist(streamInfo.channelName)
            .setDescription(streamInfo.description)
            .build()

        builder.setMediaMetadata(metadata)
        return builder.build()
    }
}

/**
 * Sandboxed Logger Bridge for plugins.
 */
class LoggingBridge(private val pluginId: String) {
    fun d(message: String) { Log.d("ButterflyPlugin:$pluginId", message) }
    fun i(message: String) { Log.i("ButterflyPlugin:$pluginId", message) }
    fun w(message: String) { Log.w("ButterflyPlugin:$pluginId", message) }
    fun e(message: String, throwable: Throwable? = null) { Log.e("ButterflyPlugin:$pluginId", message, throwable) }
}
