package com.example.extractor.tmdbembed.extractors

import android.util.Log
import com.example.extractor.tmdbembed.ExtractedStream
import com.example.extractor.tmdbembed.TMDBEmbedSource
import com.example.extractor.tmdbembed.TMDBMediaRequest
import com.example.model.CaptionOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object VixSrcExtractor {
    private const val TAG = "VixSrcExtractor"
    private const val BASE_URL = "https://vixsrc.to"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun extract(request: TMDBMediaRequest): List<ExtractedStream> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<ExtractedStream>()
        try {
            val apiUrl = if (request.isTv) {
                "$BASE_URL/api/tv/${request.tmdbId}/${request.season}/${request.episode}"
            } else {
                "$BASE_URL/api/movie/${request.tmdbId}"
            }

            val apiReq = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "$BASE_URL/")
                .build()

            val apiBody = client.newCall(apiReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string() ?: ""
            }

            if (apiBody.isBlank()) return@withContext emptyList()
            val json = JSONObject(apiBody)
            val src = json.optString("src", "")
            if (src.isBlank()) return@withContext emptyList()

            val embedUrl = if (src.startsWith("http")) src else "$BASE_URL$src"
            val embedReq = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "$BASE_URL/")
                .build()

            val embedHtml = client.newCall(embedReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string() ?: ""
            }

            // Extract token, expires, playlist from HTML
            val tokenPattern = Pattern.compile("['\"]?token['\"]?\\s*[:=]\\s*['\"]([^'\"]+)['\"]")
            val expiresPattern = Pattern.compile("['\"]?expires['\"]?\\s*[:=]\\s*['\"]([^'\"]+)['\"]")
            val playlistPattern = Pattern.compile("['\"]?playlist['\"]?\\s*[:=]\\s*['\"]([^'\"]+)['\"]")
            val directHlsPattern = Pattern.compile("https?://[^'\"\\s]+\\.m3u8[^'\"\\s]*")

            val tokenMatch = tokenPattern.matcher(embedHtml)
            val expiresMatch = expiresPattern.matcher(embedHtml)
            val playlistMatch = playlistPattern.matcher(embedHtml)
            val directHlsMatch = directHlsPattern.matcher(embedHtml)

            val token = if (tokenMatch.find()) tokenMatch.group(1) else null
            val expires = if (expiresMatch.find()) expiresMatch.group(1) else null
            val playlist = if (playlistMatch.find()) playlistMatch.group(1) else null

            var masterUrl: String? = null
            if (!playlist.isNullOrBlank() && !token.isNullOrBlank() && !expires.isNullOrBlank()) {
                masterUrl = if (playlist.contains("?")) {
                    "$playlist&token=$token&expires=$expires&h=1"
                } else {
                    "$playlist?token=$token&expires=$expires&h=1"
                }
            } else if (directHlsMatch.find()) {
                masterUrl = directHlsMatch.group()
            }

            if (masterUrl != null) {
                val headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    "Referer" to "$BASE_URL/",
                    "Origin" to BASE_URL
                )

                streams.add(
                    ExtractedStream(
                        title = "${request.title} [VixSrc • Auto HLS]",
                        url = masterUrl,
                        quality = "1080p",
                        source = TMDBEmbedSource.VIXSRC,
                        isHls = true,
                        headers = headers
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "VixSrc extraction failed: ${e.message}", e)
        }
        streams
    }
}
