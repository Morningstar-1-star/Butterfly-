package com.example.remote

import android.net.Uri
import android.util.Log
import com.example.resolver.SourceCandidate
import com.example.resolver.SourceStreamType
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
 * MediaFlow Proxy & MediaFlow-Proxy-Light Integration Helper.
 * (Official API client for mhdzumair/mediaflow-proxy & mhdzumair/mediaflow-proxy-light)
 *
 * Provides backend streaming middleware to proxy HLS (.m3u8), DASH (.mpd), and direct HTTP/HTTPS
 * streams that require custom Referer, User-Agent, Origin, or Cookie headers.
 *
 * Supports:
 * - HLS manifest proxying and dynamic rewriting via `/proxy/hls/manifest.m3u8`
 * - MPEG-DASH manifest proxying via `/proxy/mpd/manifest.mpd`
 * - Generic HTTP/HTTPS stream proxying via `/proxy/stream`
 * - Transparent forward proxying via `/proxy/forward` for API/metadata requests
 * - Header forwarding using both JSON payload (`headers`/`request_headers`) and `h_<key>` query parameters for MediaFlow Light (Rust)
 * - Safe credential management without logging passwords or sensitive tokens
 */
object MediaFlowProxyHelper {

    private const val TAG = "MediaFlowProxyHelper"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    data class HealthStatus(
        val isOnline: Boolean,
        val isAuthError: Boolean = false,
        val serverVersion: String? = null,
        val latencyMs: Long = 0L,
        val message: String = ""
    )

    /**
     * Checks if MediaFlow Proxy is enabled in settings and has a valid configured URL.
     */
    fun isMediaFlowEnabled(): Boolean {
        val serverUrl = AppConfig.getMediaFlowServerUrl().trim()
        return AppConfig.isMediaFlowEnabled() && serverUrl.isNotBlank()
    }

    /**
     * Determines whether a given [SourceCandidate] should be routed through MediaFlow Proxy.
     */
    fun shouldProxy(candidate: SourceCandidate): Boolean {
        if (!isMediaFlowEnabled()) return false

        // Never proxy BitTorrent swarms, local files, or WebView embeds
        if (candidate.isTorrent) return false
        if (candidate.type == SourceStreamType.EMBED_WEBVIEW || candidate.type == SourceStreamType.LOCAL) return false

        val url = candidate.urlOrMagnet.trim()
        if (url.isBlank() || url.startsWith("file://", ignoreCase = true) || url.startsWith("content://", ignoreCase = true)) {
            return false
        }

        // If user configured to proxy all remote streams
        if (AppConfig.isMediaFlowProxyAllStreams()) {
            return true
        }

        // If candidate explicitly requested proxying
        if (candidate.extraData["needs_proxy"] == "true" || candidate.extraData["needsProxy"] == "true") {
            return true
        }

        // If candidate requires custom anti-leech headers (Referer, Origin, Cookie, custom UA)
        if (candidate.headers.isNotEmpty()) {
            val hasSpecialHeaders = candidate.headers.any { (k, _) ->
                k.equals("Referer", ignoreCase = true) ||
                k.equals("Origin", ignoreCase = true) ||
                k.equals("Cookie", ignoreCase = true) ||
                k.equals("User-Agent", ignoreCase = true) ||
                k.equals("Authorization", ignoreCase = true)
            }
            if (hasSpecialHeaders) return true
        }

        return false
    }

    /**
     * Builds a proxied playback URL for Media3 / ExoPlayer according to the official MediaFlow API.
     *
     * @param originalUrl The raw media URL (HLS, DASH, or direct MP4/MKV stream).
     * @param headers Optional request headers (e.g. Referer, User-Agent, Origin, Cookie).
     * @param isHls True if the stream is an HLS m3u8 playlist.
     * @param isDash True if the stream is a DASH mpd manifest.
     * @param customPassword Optional password override (defaults to AppConfig).
     * @param customServerUrl Optional server URL override (defaults to AppConfig).
     * @param isLightModeOverride Optional light mode override (defaults to AppConfig).
     * @return Proxied URL string if MediaFlow is configured/enabled; otherwise returns the original URL.
     */
    fun buildProxiedUrl(
        originalUrl: String,
        headers: Map<String, String> = emptyMap(),
        isHls: Boolean = originalUrl.contains(".m3u8", ignoreCase = true),
        isDash: Boolean = originalUrl.contains(".mpd", ignoreCase = true),
        customPassword: String? = null,
        customServerUrl: String? = null,
        isLightModeOverride: Boolean? = null
    ): String {
        if (customServerUrl == null && !isMediaFlowEnabled()) {
            return originalUrl
        }
        val serverUrl = (customServerUrl ?: AppConfig.getMediaFlowServerUrl()).trim().trimEnd('/')
        if (serverUrl.isBlank() || originalUrl.isBlank() ||
            originalUrl.startsWith("file://", ignoreCase = true) ||
            originalUrl.startsWith("content://", ignoreCase = true)
        ) {
            return originalUrl
        }

        val apiPassword = (customPassword ?: AppConfig.getMediaFlowApiPassword()).trim()
        val isLightMode = isLightModeOverride ?: AppConfig.isMediaFlowLightMode()

        return try {
            val encodedDestination = URLEncoder.encode(originalUrl, StandardCharsets.UTF_8.name())
            val path = when {
                isHls -> "/proxy/hls/manifest.m3u8"
                isDash -> "/proxy/mpd/manifest.mpd"
                else -> "/proxy/stream"
            }

            val queryBuilder = StringBuilder("$serverUrl$path?d=$encodedDestination")

            if (apiPassword.isNotBlank()) {
                queryBuilder.append("&api_password=").append(URLEncoder.encode(apiPassword, StandardCharsets.UTF_8.name()))
            }

            if (headers.isNotEmpty()) {
                val headersJson = JSONObject()
                headers.forEach { (k, v) ->
                    headersJson.put(k, v)
                    // Also append as h_<Header> query parameter for MediaFlow Light (Rust) fast parser
                    val paramKey = "h_" + k.replace(" ", "-")
                    val encodedVal = URLEncoder.encode(v, StandardCharsets.UTF_8.name())
                    queryBuilder.append("&").append(paramKey).append("=").append(encodedVal)
                }

                val encodedHeadersJson = URLEncoder.encode(headersJson.toString(), StandardCharsets.UTF_8.name())
                queryBuilder.append("&headers=").append(encodedHeadersJson)
                queryBuilder.append("&request_headers=").append(encodedHeadersJson)
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
     * Builds a transparent forward proxy URL via `/proxy/forward` for arbitrary HTTP API requests.
     */
    fun buildForwardProxyUrl(
        targetApiUrl: String,
        headers: Map<String, String> = emptyMap(),
        customPassword: String? = null,
        customServerUrl: String? = null
    ): String {
        val serverUrl = (customServerUrl ?: AppConfig.getMediaFlowServerUrl()).trim().trimEnd('/')
        if (serverUrl.isBlank() || targetApiUrl.isBlank()) return targetApiUrl

        val apiPassword = (customPassword ?: AppConfig.getMediaFlowApiPassword()).trim()

        return try {
            val encodedDestination = URLEncoder.encode(targetApiUrl, StandardCharsets.UTF_8.name())
            val queryBuilder = StringBuilder("$serverUrl/proxy/forward?d=$encodedDestination")

            if (apiPassword.isNotBlank()) {
                queryBuilder.append("&api_password=").append(URLEncoder.encode(apiPassword, StandardCharsets.UTF_8.name()))
            }

            if (headers.isNotEmpty()) {
                val headersJson = JSONObject()
                headers.forEach { (k, v) -> headersJson.put(k, v) }
                val encodedHeaders = URLEncoder.encode(headersJson.toString(), StandardCharsets.UTF_8.name())
                queryBuilder.append("&headers=").append(encodedHeaders)
            }

            queryBuilder.toString()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to construct MediaFlow forward proxy URL: ${e.message}")
            targetApiUrl
        }
    }

    /**
     * Tests connectivity, latency, authentication, and health of a MediaFlow Proxy server instance.
     */
    suspend fun testHealth(
        customUrl: String? = null,
        customPassword: String? = null
    ): HealthStatus = withContext(Dispatchers.IO) {
        val serverUrl = (customUrl ?: AppConfig.getMediaFlowServerUrl()).trim().trimEnd('/')
        if (serverUrl.isBlank()) {
            return@withContext HealthStatus(
                isOnline = false,
                message = "MediaFlow Server URL is not configured."
            )
        }

        val password = (customPassword ?: AppConfig.getMediaFlowApiPassword()).trim()
        val startTime = System.currentTimeMillis()

        try {
            val testEndpoint = if (password.isNotBlank()) {
                "$serverUrl/health?api_password=${URLEncoder.encode(password, StandardCharsets.UTF_8.name())}"
            } else {
                "$serverUrl/health"
            }

            val request = Request.Builder()
                .url(testEndpoint)
                .header("User-Agent", "Butterfly/1.0 MediaFlowClient")
                .build()

            val response = try {
                httpClient.newCall(request).execute()
            } catch (e: Exception) {
                // If /health directly failed with connection error, try root /
                val rootReq = Request.Builder()
                    .url(serverUrl)
                    .header("User-Agent", "Butterfly/1.0 MediaFlowClient")
                    .build()
                httpClient.newCall(rootReq).execute()
            }

            val latency = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)

            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: "{}"
                val json = try { JSONObject(bodyStr) } catch (_: Exception) { JSONObject() }
                val version = json.optString("version", json.optString("status", "online"))
                val flavor = if (version.contains("light", ignoreCase = true) || bodyStr.contains("light", ignoreCase = true)) {
                    "MediaFlow Light"
                } else {
                    "MediaFlow"
                }

                HealthStatus(
                    isOnline = true,
                    serverVersion = "$flavor $version".trim(),
                    latencyMs = latency,
                    message = "Connected ($flavor, ${latency}ms latency)"
                )
            } else if (response.code in listOf(401, 403)) {
                HealthStatus(
                    isOnline = false,
                    isAuthError = true,
                    latencyMs = latency,
                    message = "Authentication failed: Invalid API password (${response.code})"
                )
            } else if (response.code == 404) {
                // Try pinging the root endpoint /
                val rootReq = Request.Builder()
                    .url(if (password.isNotBlank()) "$serverUrl/?api_password=${URLEncoder.encode(password, "UTF-8")}" else serverUrl)
                    .header("User-Agent", "Butterfly/1.0 MediaFlowClient")
                    .build()
                val rootResp = httpClient.newCall(rootReq).execute()
                val rootLatency = System.currentTimeMillis() - startTime
                if (rootResp.isSuccessful || rootResp.code in 200..399) {
                    HealthStatus(
                        isOnline = true,
                        serverVersion = "MediaFlow Light",
                        latencyMs = rootLatency,
                        message = "Connected to MediaFlow Light (${rootLatency}ms latency)"
                    )
                } else if (rootResp.code in listOf(401, 403)) {
                    HealthStatus(
                        isOnline = false,
                        isAuthError = true,
                        latencyMs = rootLatency,
                        message = "Authentication failed: Invalid API password (${rootResp.code})"
                    )
                } else {
                    HealthStatus(
                        isOnline = false,
                        latencyMs = rootLatency,
                        message = "HTTP ${rootResp.code}: ${rootResp.message}"
                    )
                }
            } else {
                HealthStatus(
                    isOnline = false,
                    latencyMs = latency,
                    message = "HTTP ${response.code}: ${response.message}"
                )
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            val cleanErr = e.localizedMessage ?: e.message ?: "Connection refused"
            HealthStatus(
                isOnline = false,
                latencyMs = latency,
                message = "Connection failed: $cleanErr"
            )
        }
    }

    /**
     * Sanitizes a media URL for safe logging, stripping passwords, bearer tokens, and cookies.
     */
    fun sanitizeUrlForLogging(url: String): String {
        return try {
            var sanitized = url
            sanitized = sanitized.replace(Regex("api_password=[^&]+"), "api_password=***")
            sanitized = sanitized.replace(Regex("Cookie=[^&]+", RegexOption.IGNORE_CASE), "Cookie=***")
            sanitized = sanitized.replace(Regex("token=[^&]+", RegexOption.IGNORE_CASE), "token=***")
            sanitized = sanitized.replace(Regex("key=[^&]+", RegexOption.IGNORE_CASE), "key=***")
            sanitized
        } catch (_: Exception) {
            "URL[redacted]"
        }
    }
}
