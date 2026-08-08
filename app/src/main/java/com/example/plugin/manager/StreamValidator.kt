package com.example.plugin.manager

import android.util.Log
import com.example.model.StreamFailureReason
import com.example.model.StreamType
import com.example.model.StreamValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class StreamValidator {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun validateStream(url: String): StreamValidationResult = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        val startTime = System.currentTimeMillis()

        if (cleanUrl.isBlank()) {
            return@withContext StreamValidationResult(
                isValid = false,
                url = cleanUrl,
                streamType = StreamType.UNKNOWN,
                failureReason = StreamFailureReason.NETWORK_ERROR
            )
        }

        // Magnet Link Handling
        if (cleanUrl.startsWith("magnet:", ignoreCase = true)) {
            val hasXt = cleanUrl.contains("xt=urn:btih:", ignoreCase = true)
            return@withContext if (hasXt) {
                // Magnet has valid infoHash metadata, but requires resolution before playback
                StreamValidationResult(
                    isValid = true,
                    url = cleanUrl,
                    streamType = StreamType.MAGNET,
                    latencyMs = System.currentTimeMillis() - startTime
                )
            } else {
                StreamValidationResult(
                    isValid = false,
                    url = cleanUrl,
                    streamType = StreamType.MAGNET,
                    failureReason = StreamFailureReason.INVALID_MAGNET
                )
            }
        }

        // Detect Stream Type by Extension / Pattern
        val streamType = when {
            cleanUrl.contains(".m3u8", ignoreCase = true) || cleanUrl.contains("/hls/", ignoreCase = true) -> StreamType.DIRECT_HLS
            cleanUrl.contains(".mp4", ignoreCase = true) || cleanUrl.contains(".mkv", ignoreCase = true) -> StreamType.DIRECT_MP4
            cleanUrl.contains(".mpd", ignoreCase = true) -> StreamType.DIRECT_DASH
            cleanUrl.contains("embed", ignoreCase = true) || cleanUrl.contains("vidsrc", ignoreCase = true) || cleanUrl.contains("player", ignoreCase = true) -> StreamType.EMBED_PAGE
            else -> StreamType.UNKNOWN
        }

        // Quick verification for Embed pages (Don't issue range requests to embed HTML pages)
        if (streamType == StreamType.EMBED_PAGE) {
            return@withContext StreamValidationResult(
                isValid = true,
                url = cleanUrl,
                streamType = StreamType.EMBED_PAGE,
                latencyMs = System.currentTimeMillis() - startTime
            )
        }

        // Probe HTTP Stream
        try {
            val request = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Range", "bytes=0-1024")
                .head() // Try HEAD first
                .build()

            var response = try { httpClient.newCall(request).execute() } catch (e: Exception) { null }

            if (response == null || !response.isSuccessful) {
                // Retry with lightweight GET
                val getReq = request.newBuilder().get().build()
                response = try { httpClient.newCall(getReq).execute() } catch (e: Exception) { null }
            }

            val latency = System.currentTimeMillis() - startTime
            if (response == null) {
                return@withContext StreamValidationResult(
                    isValid = false,
                    url = cleanUrl,
                    streamType = streamType,
                    failureReason = StreamFailureReason.NETWORK_ERROR,
                    latencyMs = latency
                )
            }

            val code = response.code
            val contentType = response.header("Content-Type")?.lowercase() ?: ""
            response.close()

            when {
                code == 404 -> StreamValidationResult(
                    isValid = false, url = cleanUrl, streamType = streamType,
                    failureReason = StreamFailureReason.HTTP_404_NOT_FOUND, httpCode = code, latencyMs = latency
                )
                code == 403 -> StreamValidationResult(
                    isValid = false, url = cleanUrl, streamType = streamType,
                    failureReason = StreamFailureReason.HTTP_403_FORBIDDEN, httpCode = code, latencyMs = latency
                )
                code in 500..599 -> StreamValidationResult(
                    isValid = false, url = cleanUrl, streamType = streamType,
                    failureReason = StreamFailureReason.NETWORK_ERROR, httpCode = code, latencyMs = latency
                )
                contentType.contains("html") && streamType != StreamType.EMBED_PAGE -> StreamValidationResult(
                    isValid = true, url = cleanUrl, streamType = StreamType.EMBED_PAGE,
                    httpCode = code, latencyMs = latency, contentType = contentType
                )
                else -> StreamValidationResult(
                    isValid = true, url = cleanUrl, streamType = streamType,
                    httpCode = code, latencyMs = latency, contentType = contentType
                )
            }
        } catch (e: Exception) {
            Log.w("StreamValidator", "Failed to validate stream $cleanUrl: ${e.message}")
            StreamValidationResult(
                isValid = true, // Graceful fallback if probe is blocked by server CORS/HEAD restrictions
                url = cleanUrl,
                streamType = streamType,
                latencyMs = System.currentTimeMillis() - startTime
            )
        }
    }
}
