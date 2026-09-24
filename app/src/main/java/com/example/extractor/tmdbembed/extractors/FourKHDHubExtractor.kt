package com.example.extractor.tmdbembed.extractors

import android.util.Log
import com.example.extractor.tmdbembed.ExtractedStream
import com.example.extractor.tmdbembed.TMDBEmbedSource
import com.example.extractor.tmdbembed.TMDBMediaRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object FourKHDHubExtractor {
    private const val TAG = "FourKHDHubExtractor"
    private const val DOMAINS_URL = "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/domains.json"
    private const val FALLBACK_DOMAIN = "https://4khdhub.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private suspend fun getActiveDomain(): String = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(DOMAINS_URL).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext FALLBACK_DOMAIN
                val body = resp.body?.string() ?: return@withContext FALLBACK_DOMAIN
                val arr = JSONArray(body)
                if (arr.length() > 0) arr.getString(0) else FALLBACK_DOMAIN
            }
        } catch (e: Exception) {
            FALLBACK_DOMAIN
        }
    }

    suspend fun extract(request: TMDBMediaRequest): List<ExtractedStream> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<ExtractedStream>()
        try {
            val domain = getActiveDomain()
            val cleanTitle = request.title.replace(":", "").trim()
            val enc = URLEncoder.encode(cleanTitle, "UTF-8")
            val searchUrl = "$domain/?s=$enc"

            val searchReq = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "$domain/")
                .build()

            val html = client.newCall(searchReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string() ?: ""
            }

            val linkPattern = Pattern.compile("href=[\"'](https?://[^\"']+/video/[^\"']+)[\"']")
            val matcher = linkPattern.matcher(html)
            var count = 0
            while (matcher.find() && count < 3) {
                val streamUrl = matcher.group(1) ?: continue
                if (!streamUrl.contains("r2.dev")) {
                    streams.add(
                        ExtractedStream(
                            title = "${request.title} [4KHDHub • Stream ${count + 1}]",
                            url = streamUrl,
                            quality = if (streamUrl.contains("4k") || streamUrl.contains("2160")) "4K" else "1080p",
                            source = TMDBEmbedSource.FOUR_K_HD_HUB,
                            isHls = streamUrl.contains(".m3u8"),
                            headers = mapOf(
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                                "Referer" to "$domain/"
                            )
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "4KHDHub extraction failed: ${e.message}", e)
        }
        streams
    }
}
