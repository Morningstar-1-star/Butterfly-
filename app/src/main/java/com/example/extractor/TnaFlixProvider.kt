package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import com.example.model.StreamData
import com.example.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object TnaFlixProvider {
    private const val TAG = "TnaFlixProvider"
    const val PROVIDER_ID = "tnaflix"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val BASE_URL = "https://www.tnaflix.com"

    private val defaultHeaders = mapOf(
        "User-Agent" to DEFAULT_UA,
        "Referer" to "$BASE_URL/",
        "Origin" to BASE_URL,
        "Cookie" to "age_verified=1; platform=pc; has_consent=1"
    )

    fun getHome(limit: Int = 20, page: Int = 1): List<VideoItem> {
        val safePage = if (page < 1) 1 else page
        val urls = listOf(
            "$BASE_URL/?page=$safePage",
            "$BASE_URL/popular-videos?page=$safePage",
            "$BASE_URL/top-rated?page=$safePage"
        )
        for (u in urls) {
            val list = parseHtml(u, limit)
            if (list.isNotEmpty()) return list
        }
        return emptyList()
    }

    fun search(query: String, limit: Int = 20, page: Int = 1): List<VideoItem> {
        val clean = query.trim()
        if (clean.isBlank()) return getHome(limit, page)
        val safePage = if (page < 1) 1 else page
        val q = clean.replace("tnaflix:", "").trim()
        val encoded = URLEncoder.encode(q, "UTF-8")
        val searchUrl = "$BASE_URL/search.php?what=$encoded&page=$safePage"
        val list = parseHtml(searchUrl, limit)
        if (list.isNotEmpty()) return list

        val searchUrl2 = "$BASE_URL/search?query=$encoded&page=$safePage"
        return parseHtml(searchUrl2, limit)
    }

    private fun parseHtml(url: String, limit: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        val seen = mutableSetOf<String>()
        try {
            val req = Request.Builder()
                .url(url)
                .headers(okhttp3.Headers.Builder().apply { defaultHeaders.forEach { (k, v) -> add(k, v) } }.build())
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val doc = org.jsoup.Jsoup.parse(html)
            val cards = doc.select(".video-item, .item, .thumb, .item-video, .video_box, div[data-id]")
            for (card in cards) {
                if (list.size >= limit) break
                val linkEl = card.select("a").firstOrNull {
                    val href = it.attr("href")
                    href.contains("/porn-videos/") || href.contains("/video") || href.contains(".html")
                } ?: card.select("a").firstOrNull() ?: continue

                var href = linkEl.attr("href")
                if (href.isBlank()) continue
                if (!href.startsWith("http")) href = "$BASE_URL$href"

                if (seen.contains(href)) continue
                seen.add(href)

                val title = card.select(".title, .video-title, a[title], h4, .thumb-title").text().trim().ifBlank {
                    card.select("img").attr("alt").ifBlank { "TNAFlix Video" }
                }

                var thumb = card.select("img").attr("data-src").ifBlank {
                    card.select("img").attr("data-original")
                }.ifBlank {
                    card.select("img").attr("src")
                }
                if (thumb.startsWith("//")) thumb = "https:$thumb"

                val durText = card.select(".duration, .time, .video-duration").text().trim()
                val durSec = parseDuration(durText)
                val uploader = card.select(".uploader, .username, .author").text().trim().ifBlank { "TNAFlix" }

                list.add(
                    VideoItem(
                        id = href,
                        title = title,
                        uploaderName = uploader,
                        uploaderUrl = "$BASE_URL/users/$uploader",
                        thumbnailUrl = thumb,
                        providerId = PROVIDER_ID,
                        durationSeconds = durSec,
                        uploadDate = "TNAFlix",
                        description = title
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "TNAFlix parseHtml error: ${e.message}")
        }
        return list
    }

    private fun parseDuration(text: String): Long {
        if (text.isBlank()) return 0L
        val parts = text.trim().split(":")
        return try {
            when (parts.size) {
                3 -> parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()
                2 -> parts[0].toLong() * 60 + parts[1].toLong()
                1 -> parts[0].toLong()
                else -> 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun getStreamData(urlOrId: String, context: Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val clean = urlOrId.trim()
        val targetUrl = if (clean.startsWith("http")) clean else "$BASE_URL/video${clean.substringAfter("tnaflix:")}"

        // 1. Try yt-dlp first
        if (context != null) {
            try {
                val ytdlResult = YtDlpResolver.extractStreamInfo(context, targetUrl)
                if (ytdlResult is YouTubeExtractorHelper.ExtractionResult.Success) {
                    return@withContext ytdlResult.streamData.copy(
                        providerId = PROVIDER_ID,
                        headers = defaultHeaders
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "yt-dlp TNAFlix extraction: ${e.message}")
            }
        }

        // 2. Direct HTML extraction for video sources
        try {
            val req = Request.Builder()
                .url(targetUrl)
                .headers(okhttp3.Headers.Builder().apply { defaultHeaders.forEach { (k, v) -> add(k, v) } }.build())
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!html.isNullOrBlank()) {
                val doc = org.jsoup.Jsoup.parse(html)
                val title = doc.select("title, h1, .video-title").firstOrNull()?.text()?.trim() ?: "TNAFlix Video"
                val thumb = doc.select("meta[property=og:image]").attr("content")

                val videoSources = mutableListOf<PlayableStreamOption>()
                val videoUrlMatcher = Pattern.compile("""(?:file|video_url|videoUrl|source|src)\s*:\s*["'](https?:\\?/\\?/[^"']+\.(?:mp4|m3u8)[^"']*)["']""")
                val matcher = videoUrlMatcher.matcher(html)
                while (matcher.find()) {
                    val rawUrl = matcher.group(1)?.replace("\\/", "/") ?: continue
                    val isHls = rawUrl.contains(".m3u8")
                    videoSources.add(
                        PlayableStreamOption(
                            qualityLabel = if (isHls) "1080p / 720p HLS Stream" else "HD MP4 Direct",
                            format = if (isHls) "m3u8" else "mp4",
                            isMuxed = true,
                            videoUrl = rawUrl,
                            providerType = ProviderType.DIRECT,
                            headers = defaultHeaders
                        )
                    )
                }

                if (videoSources.isNotEmpty()) {
                    return@withContext StreamData(
                        videoId = targetUrl,
                        videoUrl = videoSources.first().videoUrl ?: "",
                        title = title,
                        channelName = "TNAFlix",
                        thumbnailUrl = thumb,
                        availableStreamOptions = videoSources,
                        selectedStreamOption = videoSources.first(),
                        providerId = PROVIDER_ID,
                        providerType = ProviderType.DIRECT,
                        headers = defaultHeaders
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct TNAFlix extraction error: ${e.message}")
        }

        null
    }
}
