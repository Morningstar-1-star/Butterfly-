package com.example.extractor.sextb

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.regex.Pattern

/**
 * StreamTape Extractor for SEXТB embed streams.
 */
object StreamTapeExtractor {

    private const val TAG = "StreamTapeExtractor"

    private val httpClient get() = SextbNetwork.httpClient

    private val TOKEN_REGEX = Pattern.compile(
        """document\.getElementById\(['"](?:robotlink|videolink)['"]\)\.innerHTML\s*=\s*['"]([^'"]+)['"]\s*\+\s*\(['"]([^'"]+)['"]\)\.substring\((\d+)\)"""
    )

    fun canHandle(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("streamtape.com") || lower.contains("streamtape.net") || lower.contains("streamtape.to")
    }

    suspend fun extractStream(
        url: String,
        referer: String? = "https://sextb.net/",
        userAgent: String = SextbResolver.DEFAULT_UA
    ): List<VideoSource> = withContext(Dispatchers.IO) {
        val sources = mutableListOf<VideoSource>()
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Referer", referer ?: "https://sextb.net/")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                Log.w(TAG, "HTTP ${resp.code} fetching StreamTape embed at $url")
                return@withContext emptyList()
            }

            val html = resp.body?.string() ?: ""
            val extracted = extractFromHtml(html, url, userAgent)
            sources.addAll(extracted)
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving StreamTape stream for $url: ${e.message}")
        }
        sources
    }

    fun extractFromHtml(
        html: String,
        embedUrl: String,
        userAgent: String = SextbResolver.DEFAULT_UA
    ): List<VideoSource> {
        val sources = mutableListOf<VideoSource>()

        // 1. Script substring concatenation pattern
        val matcher = TOKEN_REGEX.matcher(html)
        if (matcher.find()) {
            val part1 = matcher.group(1) ?: ""
            val part2 = matcher.group(2) ?: ""
            val substrIndex = matcher.group(3)?.toIntOrNull() ?: 0
            val suffix = if (substrIndex < part2.length) part2.substring(substrIndex) else part2
            val fullUrl = StbturboExtractor.httpsify(part1 + suffix)
            val streamUrl = if (!fullUrl.contains("&stream=1")) "$fullUrl&stream=1" else fullUrl

            sources.add(
                VideoSource(
                    url = streamUrl,
                    mimeType = "video/mp4",
                    quality = "1080p",
                    isHls = false,
                    headers = mapOf(
                        "User-Agent" to userAgent,
                        "Referer" to embedUrl
                    ),
                    sourceName = "StreamTape"
                )
            )
            Log.i(TAG, "Resolved StreamTape stream: $streamUrl")
            return sources
        }

        // 2. Direct element innerHTML fallback
        val doc = Jsoup.parse(html, embedUrl)
        val robotLink = doc.selectFirst("#robotlink, #videolink")?.text()?.trim()
        if (!robotLink.isNullOrBlank()) {
            val fullUrl = StbturboExtractor.httpsify(robotLink)
            val streamUrl = if (!fullUrl.contains("&stream=1")) "$fullUrl&stream=1" else fullUrl
            sources.add(
                VideoSource(
                    url = streamUrl,
                    mimeType = "video/mp4",
                    quality = "1080p",
                    isHls = false,
                    headers = mapOf(
                        "User-Agent" to userAgent,
                        "Referer" to embedUrl
                    ),
                    sourceName = "StreamTape"
                )
            )
        }

        return sources
    }
}
