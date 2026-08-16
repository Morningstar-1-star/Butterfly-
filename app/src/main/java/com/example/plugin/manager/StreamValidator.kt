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

    suspend fun validateStream(url: String, headers: Map<String, String> = emptyMap()): StreamValidationResult = withContext(Dispatchers.IO) {
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
            val reqBuilder = Request.Builder().url(cleanUrl)
            
            val ua = headers["User-Agent"] ?: headers["user-agent"] ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
            reqBuilder.header("User-Agent", ua)
            reqBuilder.header("Range", "bytes=0-1024")

            val isBilibili = cleanUrl.contains("bilibili.com", ignoreCase = true) || cleanUrl.contains("bilivideo.com", ignoreCase = true) || cleanUrl.contains("hdslb.com", ignoreCase = true)
            if (isBilibili && !headers.containsKey("Referer") && !headers.containsKey("referer")) {
                reqBuilder.header("Referer", "https://www.bilibili.com/")
            }

            headers.forEach { (k, v) ->
                if (!k.equals("User-Agent", ignoreCase = true) && !k.equals("Range", ignoreCase = true) && v.isNotBlank()) {
                    reqBuilder.header(k, v)
                }
            }

            val request = reqBuilder.head().build()

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
            val bodyPeek = try { response.body?.source()?.peek()?.readUtf8(100) ?: "" } catch (_: Exception) { "" }
            response.close()

            val isSuccessCode = code in 200..299 || code == 206

            if (!isSuccessCode) {
                val failureReason = when (code) {
                    404 -> StreamFailureReason.HTTP_404_NOT_FOUND
                    403 -> StreamFailureReason.HTTP_403_FORBIDDEN
                    401 -> StreamFailureReason.HTTP_403_FORBIDDEN
                    in 500..599 -> StreamFailureReason.NETWORK_ERROR
                    else -> StreamFailureReason.NETWORK_ERROR
                }
                return@withContext StreamValidationResult(
                    isValid = false,
                    url = cleanUrl,
                    streamType = streamType,
                    failureReason = failureReason,
                    httpCode = code,
                    latencyMs = latency
                )
            }

            val isPlayableMediaType = contentType.contains("video/") ||
                    contentType.contains("audio/") ||
                    contentType.contains("mpegurl") ||
                    contentType.contains("dash+xml") ||
                    contentType.contains("octet-stream") ||
                    bodyPeek.contains("#EXTM3U") ||
                    bodyPeek.contains("ftyp") ||
                    bodyPeek.contains("<?xml")

            if (contentType.contains("html") && !bodyPeek.contains("#EXTM3U") && !bodyPeek.contains("ftyp") && streamType != StreamType.EMBED_PAGE) {
                return@withContext StreamValidationResult(
                    isValid = false,
                    url = cleanUrl,
                    streamType = streamType,
                    failureReason = StreamFailureReason.INVALID_CONTENT_TYPE,
                    httpCode = code,
                    latencyMs = latency,
                    contentType = contentType
                )
            }

            if (isPlayableMediaType || isSuccessCode) {
                return@withContext StreamValidationResult(
                    isValid = true,
                    url = cleanUrl,
                    streamType = streamType,
                    httpCode = code,
                    latencyMs = latency,
                    contentType = contentType
                )
            } else {
                return@withContext StreamValidationResult(
                    isValid = false,
                    url = cleanUrl,
                    streamType = streamType,
                    failureReason = StreamFailureReason.INVALID_CONTENT_TYPE,
                    httpCode = code,
                    latencyMs = latency,
                    contentType = contentType
                )
            }
        } catch (e: Exception) {
            Log.w("StreamValidator", "Failed to validate stream $cleanUrl: ${e.message}")
            StreamValidationResult(
                isValid = false,
                url = cleanUrl,
                streamType = streamType,
                failureReason = StreamFailureReason.NETWORK_ERROR,
                latencyMs = System.currentTimeMillis() - startTime
            )
        }
    }
}
