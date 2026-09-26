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

object FourKHDHubExtractor {
    private const val TAG = "FourKHDHubExtractor"
    private val KNOWN_DOMAINS = listOf(
        "https://4khdhub.com",
        "https://4khdhub.dad",
        "https://4khdhub.skin",
        "https://4khdhub.org"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun extract(request: TMDBMediaRequest): List<ExtractedStream> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<ExtractedStream>()
        for (domain in KNOWN_DOMAINS) {
            try {
                val cleanTitle = request.title.replace(":", "").trim()
                val enc = URLEncoder.encode(cleanTitle, "UTF-8")
                val searchUrl = "$domain/?s=$enc"

                val searchReq = Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "$domain/")
                    .build()

                val html = client.newCall(searchReq).execute().use { resp ->
                    if (!resp.isSuccessful) "" else resp.body?.string() ?: ""
                }

                if (html.isNotBlank()) {
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
                    if (streams.isNotEmpty()) break
                }
            } catch (e: Exception) {
                Log.w(TAG, "4KHDHub ($domain) note: ${e.message}")
            }
        }
        streams
    }
}

