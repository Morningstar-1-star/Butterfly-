package com.example.extractor.tmdbembed.extractors

import android.util.Log
import com.example.extractor.tmdbembed.ExtractedStream
import com.example.extractor.tmdbembed.TMDBEmbedSource
import com.example.extractor.tmdbembed.TMDBMediaRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object DahmerMoviesExtractor {
    private const val TAG = "DahmerMoviesExtractor"
    private const val BASE_URL = "https://a.111477.xyz"
    private const val REFERER = "https://a.111477.xyz/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun extract(request: TMDBMediaRequest): List<ExtractedStream> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<ExtractedStream>()
        try {
            val cleanTitle = request.title.replace(":", "").trim()
            val encTitle = URLEncoder.encode(cleanTitle, "UTF-8")
            val targetPath = if (request.isTv) {
                val sStr = if (request.season < 10) "0${request.season}" else "${request.season}"
                "/tvs/$encTitle/Season%20$sStr/"
            } else {
                "/movies/$encTitle%20(${request.year})/"
            }

            val req = Request.Builder()
                .url("$BASE_URL$targetPath")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", REFERER)
                .build()

            val html = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string() ?: ""
            }

            if (html.isBlank()) return@withContext emptyList()

            // Regex parse directory links
            val linkRegex = Pattern.compile("<a[^>]*href=[\"']([^\"']*)[\"'][^>]*>([^<]*)</a>", Pattern.CASE_INSENSITIVE)
            val matcher = linkRegex.matcher(html)

            while (matcher.find()) {
                val href = matcher.group(1) ?: continue
                val text = matcher.group(2)?.trim() ?: ""

                if (text.endsWith(".mp4", ignoreCase = true) ||
                    text.endsWith(".mkv", ignoreCase = true) ||
                    text.endsWith(".m3u8", ignoreCase = true)
                ) {
                    val fullUrl = if (href.startsWith("http")) href else "$BASE_URL$targetPath$href"
                    val qual = when {
                        text.contains("2160") || text.contains("4k", ignoreCase = true) -> "4K"
                        text.contains("1080") -> "1080p"
                        text.contains("720") -> "720p"
                        else -> "1080p"
                    }

                    streams.add(
                        ExtractedStream(
                            title = "${request.title} [DahmerMovies • $qual]",
                            url = fullUrl,
                            quality = qual,
                            source = TMDBEmbedSource.DAHMER_MOVIES,
                            isHls = fullUrl.contains(".m3u8"),
                            headers = mapOf(
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                                "Referer" to REFERER
                            )
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "DahmerMovies extraction failed: ${e.message}", e)
        }
        streams
    }
}
