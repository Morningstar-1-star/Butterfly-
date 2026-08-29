package com.example.remote

import android.util.Base64
import android.util.Log
import com.example.util.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * MediaFlow Proxy & MediaFlow Proxy Light Integration Helper.
 * (Adapted from mhdzumair/mediaflow-proxy & mhdzumair/MediaFlow-Proxy-Light)
 *
 * Provides backend streaming middleware to proxy HLS (.m3u8), DASH (.mpd), and direct HTTP
 * streams that require custom Referer, User-Agent, Origin, or Cookie headers.
 * Converts complex protected web streams into clean, standard playable URLs for ExoPlayer / Media3.
 */
object MediaFlowProxyHelper {

    private const val TAG = "MediaFlowProxyHelper"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    data class HealthStatus(
        val isOnline: Boolean,
        val serverVersion: String? = null,
        val latencyMs: Long = 0L,
        val message: String = ""
    )

    /**
     * Checks if MediaFlow Proxy is enabled in settings and has a valid configured URL.
     */
    fun isMediaFlowEnabled(): Boolean {
        return AppConfig.isMediaFlowEnabled() && AppConfig.getMediaFlowServerUrl().isNotBlank()
    }

    /**
     * Builds a proxied playback URL for Media3 / ExoPlayer.
     *
     * @param originalUrl The raw media URL (HLS, DASH, or direct MP4/stream).
     * @param headers Optional request headers (e.g. Referer, User-Agent, Origin, Cookie).
     * @param isHls True if the stream is an HLS m3u8 playlist.
     * @param isDash True if the stream is a DASH mpd manifest.
     * @return Proxied URL string if MediaFlow is configured/enabled; otherwise returns the original URL.
     */
    fun buildProxiedUrl(
        originalUrl: String,
        headers: Map<String, String> = emptyMap(),
        isHls: Boolean = originalUrl.contains(".m3u8", ignoreCase = true),
        isDash: Boolean = originalUrl.contains(".mpd", ignoreCase = true)
    ): String {
        if (!isMediaFlowEnabled()) return originalUrl
        if (originalUrl.isBlank() || originalUrl.startsWith("file://") || originalUrl.startsWith("content://")) {
            return originalUrl
        }

        val baseUrl = AppConfig.getMediaFlowServerUrl().trim().trimEnd('/')
        val apiPassword = AppConfig.getMediaFlowApiPassword().trim()
        val isLightMode = AppConfig.isMediaFlowLightMode()

        return try {
            val encodedDestination = URLEncoder.encode(originalUrl, StandardCharsets.UTF_8.name())
            val path = when {
                isHls -> "/proxy/hls/manifest.m3u8"
                isDash -> "/proxy/mpd/manifest.mpd"
                else -> "/proxy/stream"
            }

            val queryBuilder = StringBuilder("$baseUrl$path?d=$encodedDestination")

            if (apiPassword.isNotBlank()) {
                queryBuilder.append("&api_password=").append(URLEncoder.encode(apiPassword, StandardCharsets.UTF_8.name()))
            }

            // Encode headers as JSON or base64
            if (headers.isNotEmpty()) {
                val headersJson = JSONObject()
                headers.forEach { (k, v) -> headersJson.put(k, v) }
                val encodedHeaders = URLEncoder.encode(headersJson.toString(), StandardCharsets.UTF_8.name())
                queryBuilder.append("&request_headers=").append(encodedHeaders)
            }

            if (isLightMode) {
                queryBuilder.append("&mode=light")
            }

            queryBuilder.toString()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to construct MediaFlow proxy URL: ${e.message}")
            originalUrl
        }
    }

    /**
     * Tests connectivity, latency, and health of a MediaFlow Proxy server instance.
     */
    suspend fun testHealth(
        customUrl: String? = null,
        customPassword: String? = null
    ): HealthStatus = withContext(Dispatchers.IO) {
        val serverUrl = (customUrl ?: AppConfig.getMediaFlowServerUrl()).trim().trimEnd('/')
        if (serverUrl.isBlank()) {
            return@withContext HealthStatus(isOnline = false, message = "MediaFlow URL is not set.")
        }

        val password = customPassword ?: AppConfig.getMediaFlowApiPassword()
        val startTime = System.currentTimeMillis()

        try {
            val testEndpoint = if (password.isNotBlank()) {
                "$serverUrl/health?api_password=${URLEncoder.encode(password, "UTF-8")}"
            } else {
                "$serverUrl/health"
            }

            val request = Request.Builder()
                .url(testEndpoint)
                .header("User-Agent", "Butterfly/1.0 MediaFlowClient")
                .build()

            val response = httpClient.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime

            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: "{}"
                val json = try { JSONObject(bodyStr) } catch (_: Exception) { JSONObject() }
                val version = json.optString("version", json.optString("status", "online"))
                HealthStatus(
                    isOnline = true,
                    serverVersion = version,
                    latencyMs = latency,
                    message = "MediaFlow Proxy is online ($latency ms latency)"
                )
            } else {
                // Fallback check on root /
                val rootReq = Request.Builder().url(serverUrl).build()
                val rootResp = httpClient.newCall(rootReq).execute()
                val rootLatency = System.currentTimeMillis() - startTime
                if (rootResp.isSuccessful || rootResp.code in 401..403) {
                    HealthStatus(
                        isOnline = true,
                        serverVersion = "MediaFlow Light",
                        latencyMs = rootLatency,
                        message = "MediaFlow Proxy reached ($rootLatency ms)"
                    )
                } else {
                    HealthStatus(
                        isOnline = false,
                        latencyMs = latency,
                        message = "HTTP ${response.code}: ${response.message}"
                    )
                }
            }
        } catch (e: Exception) {
            HealthStatus(
                isOnline = false,
                latencyMs = System.currentTimeMillis() - startTime,
                message = "Connection failed: ${e.localizedMessage ?: e.message}"
            )
        }
    }
}
