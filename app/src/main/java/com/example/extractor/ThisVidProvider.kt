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

object ThisVidProvider {
    private const val TAG = "ThisVidProvider"
    const val PROVIDER_ID = "thisvid"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val BASE_URL = "https://thisvid.com"

    private val defaultHeaders = mapOf(
        "User-Agent" to DEFAULT_UA,
        "Referer" to "$BASE_URL/",
        "Origin" to BASE_URL,
        "Cookie" to "age_verified=1; platform=pc"
    )

    fun getHome(limit: Int = 20, page: Int = 1): List<VideoItem> {
        val safePage = if (page < 1) 1 else page
        val urls = listOf(
            "$BASE_URL/latest-updates/$safePage/",
            "$BASE_URL/most-popular/$safePage/",
            "$BASE_URL/top-rated/$safePage/"
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

        // Handle playlist: thisvid:playlist:<id> or thisvid:playlist
        if (clean.startsWith("thisvid:playlist:", ignoreCase = true)) {
            val plId = clean.substringAfter("thisvid:playlist:").trim()
            val plUrl = "$BASE_URL/playlists/$plId/"
            val list = parseHtml(plUrl, limit)
            if (list.isNotEmpty()) return list
        }

        val q = clean.replace(Regex("(?i)^(thisvid:playlist:|thisvid:)?"), "").trim()
        val encoded = URLEncoder.encode(q, "UTF-8")
        val searchUrl = "$BASE_URL/search/$encoded/$safePage/"
        val list = parseHtml(searchUrl, limit)
        if (list.isNotEmpty()) return list

        val searchUrl2 = "$BASE_URL/search/videos/$encoded/$safePage/"
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
            val cards = doc.select(".item, .video-item, .thumb, .item-video, div[data-video-id]")
            for (card in cards) {
                if (list.size >= limit) break
                val linkEl = card.select("a").firstOrNull {
                    val href = it.attr("href")
                    href.contains("/videos/") || href.contains("/watch/") || href.contains("/video/")
                } ?: card.select("a").firstOrNull() ?: continue

                var href = linkEl.attr("href")
                if (href.isBlank()) continue
                if (!href.startsWith("http")) href = "$BASE_URL$href"

                if (seen.contains(href)) continue
                seen.add(href)

                val title = card.select(".title, .item-title, a[title], h4, .thumb-title").text().trim().ifBlank {
                    card.select("img").attr("alt").ifBlank { "ThisVid Video" }
                }

                var thumb = card.select("img").attr("data-src").ifBlank {
                    card.select("img").attr("data-original")
                }.ifBlank {
                    card.select("img").attr("src")
                }
                if (thumb.startsWith("//")) thumb = "https:$thumb"

                val durText = card.select(".duration, .item-duration, .time").text().trim()
                val durSec = parseDuration(durText)
                val uploader = card.select(".username, .item-user, .author").text().trim().ifBlank { "ThisVid" }

                val isPlaylist = href.contains("/playlists/") || url.contains("/playlists/")
                val prefix = if (isPlaylist) "thisvid:playlist:" else ""

                list.add(
                    VideoItem(
                        id = "$prefix$href",
                        title = title,
                        uploaderName = uploader,
                        uploaderUrl = "$BASE_URL/members/$uploader",
                        thumbnailUrl = thumb,
                        providerId = PROVIDER_ID,
                        durationSeconds = durSec,
                        uploadDate = "ThisVid",
                        description = title
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "ThisVid parseHtml error: ${e.message}")
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
        val targetUrl = when {
            clean.startsWith("http") -> clean
            clean.startsWith("thisvid:playlist:") -> "$BASE_URL/playlists/${clean.substringAfter("thisvid:playlist:")}"
            clean.startsWith("thisvid:") -> "$BASE_URL/videos/${clean.substringAfter("thisvid:")}"
            else -> "$BASE_URL/videos/$clean"
        }

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
                Log.w(TAG, "yt-dlp ThisVid extraction: ${e.message}")
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
                val title = doc.select("title, h1, .video-title").firstOrNull()?.text()?.trim() ?: "ThisVid Video"
                val thumb = doc.select("meta[property=og:image]").attr("content")

                val videoSources = mutableListOf<PlayableStreamOption>()
                val videoUrlMatcher = Pattern.compile("""(?:video_url|video_alt_url|file|src|source)\s*:\s*["'](https?:\\?/\\?/[^"']+\.(?:mp4|m3u8)[^"']*)["']""")
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
                        channelName = "ThisVid",
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
            Log.w(TAG, "Direct ThisVid extraction error: ${e.message}")
        }

        null
    }
}
