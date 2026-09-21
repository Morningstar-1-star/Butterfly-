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
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * HellPorno Provider & Stream Extractor.
 * Supported by yt-dlp 'HellPorno' extractor.
 */
object HellPornoProvider {
    private const val TAG = "HellPornoProvider"
    const val PROVIDER_ID = "hellporno"
    private const val BASE_URL = "https://hellporno.com"

    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun getHome(limit: Int = 20, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val target = if (page <= 1) "$BASE_URL/latest-updates/" else "$BASE_URL/latest-updates/$page/"
        parseHellPornoHtml(target, limit)
    }

    suspend fun search(query: String, limit: Int = 20, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val target = if (page <= 1) "$BASE_URL/search/$encoded/" else "$BASE_URL/search/$encoded/$page/"
        parseHellPornoHtml(target, limit)
    }

    private fun parseHellPornoHtml(targetUrl: String, limit: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        val seenUrls = mutableSetOf<String>()

        try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Referer", "$BASE_URL/")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val doc = Jsoup.parse(html, BASE_URL)
            val cards = doc.select(".item, .video-item, div[class*='item-video'], .thumb, .item-holder, article")

            for (card in cards) {
                if (list.size >= limit) break

                val linkEl = card.selectFirst("a[href*='/videos/'], a[href*='/video/'], a[href*='hellporno.com']")
                    ?: card.selectFirst("a")
                    ?: continue

                var href = linkEl.attr("href").trim()
                if (href.isBlank() || href == "#" || href.contains("/categories/") || href.contains("/tags/") || href.contains("/models/")) {
                    continue
                }

                if (!href.startsWith("http")) {
                    href = if (href.startsWith("/")) "$BASE_URL$href" else "$BASE_URL/$href"
                }

                if (seenUrls.contains(href)) continue
                seenUrls.add(href)

                val imgEl = card.selectFirst("img")
                val thumb = imgEl?.attr("data-src")?.takeIf { it.isNotBlank() }
                    ?: imgEl?.attr("data-original")?.takeIf { it.isNotBlank() }
                    ?: imgEl?.attr("src")?.takeIf { it.isNotBlank() }
                    ?: ""

                val cleanThumb = when {
                    thumb.startsWith("//") -> "https:$thumb"
                    thumb.startsWith("/") -> "$BASE_URL$thumb"
                    else -> thumb
                }

                var title = card.selectFirst(".title, .item-title, p, h3, h2, a[title]")?.text()?.trim() ?: ""
                if (title.isBlank()) {
                    title = linkEl.attr("title").ifBlank { imgEl?.attr("alt") ?: "HellPorno Video" }
                }

                val durationText = card.selectFirst(".duration, .item-duration, .time, span[class*='duration']")?.text()?.trim() ?: ""
                val durSec = com.example.model.parseDurationToSeconds(durationText)

                val viewsText = card.selectFirst(".views, .item-views, span[class*='views']")?.text()?.trim() ?: ""
                val viewsCount = Regex("""\d+""").find(viewsText.replace(",", ""))?.value?.toLongOrNull() ?: 0L

                list.add(
                    VideoItem(
                        id = href,
                        title = title,
                        uploaderName = "HellPorno",
                        uploaderUrl = BASE_URL,
                        thumbnailUrl = cleanThumb.takeIf { it.isNotBlank() },
                        providerId = PROVIDER_ID,
                        durationSeconds = durSec,
                        viewCount = viewsCount,
                        uploadDate = "HellPorno"
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "parseHellPornoHtml error: ${e.message}")
        }

        return list
    }

    suspend fun getStreamData(urlOrId: String, context: Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val targetUrl = when {
            urlOrId.startsWith("http://") || urlOrId.startsWith("https://") -> urlOrId
            urlOrId.startsWith("hellporno:", ignoreCase = true) -> {
                val id = urlOrId.substringAfter(":").trim('/')
                if (id.startsWith("http")) id else "$BASE_URL/videos/$id/"
            }
            else -> "$BASE_URL/videos/$urlOrId/"
        }

        // 1. Try direct HTML extraction of video source
        try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Referer", "$BASE_URL/")
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!html.isNullOrBlank()) {
                val doc = Jsoup.parse(html, BASE_URL)
                val title = doc.selectFirst("h1, .video-title, title")?.text()?.replace(" - HellPorno", "")?.trim() ?: "HellPorno Video"
                val thumb = doc.selectFirst("meta[property='og:image']")?.attr("content")
                    ?: doc.selectFirst("video")?.attr("poster")

                // Check for direct MP4 / HLS streams in HTML or JavaScript
                val mp4Matches = Regex("""(?:file|video_url|src|source)\s*:\s*['"](https?://[^'"]+\.mp4[^'"]*)['"]""", RegexOption.IGNORE_CASE).findAll(html).map { it.groupValues[1] }.toList()
                val hlsMatches = Regex("""(?:file|hls_url|src)\s*:\s*['"](https?://[^'"]+\.m3u8[^'"]*)['"]""", RegexOption.IGNORE_CASE).findAll(html).map { it.groupValues[1] }.toList()

                val streamOptions = mutableListOf<PlayableStreamOption>()
                hlsMatches.forEach { hls ->
                    streamOptions.add(
                        PlayableStreamOption(
                            qualityLabel = "1080p (HLS Live)",
                            format = "m3u8",
                            isMuxed = true,
                            videoUrl = hls,
                            providerType = ProviderType.DIRECT,
                            headers = mapOf("User-Agent" to DEFAULT_USER_AGENT, "Referer" to "$BASE_URL/")
                        )
                    )
                }

                mp4Matches.forEach { mp4 ->
                    streamOptions.add(
                        PlayableStreamOption(
                            qualityLabel = "720p HD MP4",
                            format = "mp4",
                            isMuxed = true,
                            videoUrl = mp4,
                            providerType = ProviderType.DIRECT,
                            headers = mapOf("User-Agent" to DEFAULT_USER_AGENT, "Referer" to "$BASE_URL/")
                        )
                    )
                }

                if (streamOptions.isNotEmpty()) {
                    val best = streamOptions.first()
                    return@withContext StreamData(
                        videoId = targetUrl,
                        videoUrl = best.videoUrl ?: "",
                        title = title,
                        channelName = "HellPorno",
                        channelAvatarUrl = null,
                        description = "HellPorno Ultra HD Stream",
                        thumbnailUrl = thumb,
                        providerId = PROVIDER_ID,
                        providerType = ProviderType.DIRECT,
                        availableStreamOptions = streamOptions,
                        selectedStreamOption = best
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct HTML stream extraction note: ${e.message}")
        }

        // 2. yt-dlp extraction fallback
        if (context != null) {
            try {
                val result = YtDlpResolver.extractStreamInfo(context, targetUrl)
                if (result is YouTubeExtractorHelper.ExtractionResult.Success) {
                    return@withContext result.streamData.copy(providerId = PROVIDER_ID)
                }
            } catch (e: Exception) {
                Log.w(TAG, "YtDlpResolver extraction failed for HellPorno: ${e.message}")
            }
        }

        return@withContext null
    }
}
