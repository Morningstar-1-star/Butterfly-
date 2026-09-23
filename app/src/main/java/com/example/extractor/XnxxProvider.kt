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
 * XNXX Provider & High-Performance Stream Extractor.
 * Supported by yt-dlp 'XNXX' extractor.
 */
object XnxxProvider {
    private const val TAG = "XnxxProvider"
    const val PROVIDER_ID = "xnxx"
    private const val BASE_URL = "https://www.xnxx.com"

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
        val target = if (page <= 1) "$BASE_URL/new/1" else "$BASE_URL/new/$page"
        parseXnxxHtml(target, limit)
    }

    suspend fun search(query: String, limit: Int = 20, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val target = if (page <= 1) "$BASE_URL/search/$encoded" else "$BASE_URL/search/$encoded/$page"
        parseXnxxHtml(target, limit)
    }

    private fun parseXnxxHtml(targetUrl: String, limit: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        val seenUrls = mutableSetOf<String>()

        try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Cookie", "age_verified=1; platform=pc; has_consent=1")
                .header("Referer", "$BASE_URL/")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val doc = Jsoup.parse(html, BASE_URL)
            val cards = doc.select(".thumb-block, div[id^=video_], div[data-id], .mozaique > div")

            for (card in cards) {
                if (list.size >= limit) break

                val linkEl = card.selectFirst("a[href*=/video]")
                    ?: card.selectFirst(".thumb a")
                    ?: card.selectFirst("p.title a")
                    ?: card.selectFirst("a[href^=\"/video\"]")
                    ?: continue

                var href = linkEl.attr("href").trim()
                if (href.isBlank() || href == "#" || href.contains("/channels/") || href.contains("/tags/") || href.contains("/profiles/") || href.contains("/categories/")) {
                    continue
                }

                if (!href.startsWith("http")) {
                    href = if (href.startsWith("/")) "$BASE_URL$href" else "$BASE_URL/$href"
                }

                if (seenUrls.contains(href)) continue
                seenUrls.add(href)

                val videoId = card.attr("data-id").ifBlank {
                    card.attr("id").removePrefix("video_").ifBlank {
                        Regex("""/video(?:\.?\w+)?/([0-9a-zA-Z_-]+)""").find(href)?.groupValues?.get(1) ?: href
                    }
                }

                var title = ""
                val titleEl = card.selectFirst("p.title a") ?: card.selectFirst(".title a") ?: card.selectFirst(".title")
                if (titleEl != null) {
                    title = titleEl.attr("title").ifBlank { titleEl.text() }
                }
                if (title.isBlank()) {
                    title = linkEl.attr("title").ifBlank { linkEl.text() }
                }
                if (title.isBlank()) {
                    title = card.selectFirst("img")?.attr("alt") ?: ""
                }

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

                val durText = card.selectFirst(".duration, .video-duration, .video-length")?.text()?.trim() ?: ""
                val durSec = com.example.model.parseDurationToSeconds(durText)

                val uploader = card.selectFirst(".name, .uploader, .channel, span.name")?.text()?.trim() ?: "XNXX"
                val viewsText = card.selectFirst(".metadata, .views, span[class*='views']")?.text()?.trim() ?: ""
                val views = Regex("""(\d+(?:\.\d+)?)\s*([kKmM]?)""").find(viewsText)?.let { m ->
                    val num = m.groupValues[1].toDoubleOrNull() ?: 0.0
                    when (m.groupValues[2].lowercase()) {
                        "k" -> (num * 1000).toLong()
                        "m" -> (num * 1000000).toLong()
                        else -> num.toLong()
                    }
                } ?: 0L

                list.add(
                    VideoItem(
                        id = href,
                        title = title.ifBlank { "XNXX Video" },
                        uploaderName = uploader,
                        uploaderUrl = BASE_URL,
                        thumbnailUrl = cleanThumb.takeIf { it.isNotBlank() },
                        providerId = PROVIDER_ID,
                        durationSeconds = durSec,
                        viewCount = views,
                        uploadDate = "XNXX"
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "parseXnxxHtml error: ${e.message}")
        }

        return list
    }

    suspend fun getStreamData(urlOrId: String, context: Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val targetUrl = when {
            urlOrId.startsWith("http://") || urlOrId.startsWith("https://") -> urlOrId
            urlOrId.startsWith("xnxx:", ignoreCase = true) -> {
                val id = urlOrId.substringAfter(":").trim('/')
                if (id.startsWith("http")) id else "$BASE_URL/video-$id/"
            }
            else -> "$BASE_URL/video-$urlOrId/"
        }

        // 1. Direct regex extraction of html5player parameters
        try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Cookie", "age_verified=1; platform=pc; has_consent=1")
                .header("Referer", "$BASE_URL/")
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!html.isNullOrBlank()) {
                val title = Regex("""html5player\.setVideoTitle\('([^']+)'\)""").find(html)?.groupValues?.get(1)
                    ?: Jsoup.parse(html).selectFirst("h2.page-title, .video-title, title")?.text()?.replace(" - XNXX.COM", "")?.trim()
                    ?: "XNXX Video"

                val thumb = Regex("""html5player\.setThumbUrl\('([^']+)'\)""").find(html)?.groupValues?.get(1)
                    ?: Regex("""html5player\.setThumbUrl169\('([^']+)'\)""").find(html)?.groupValues?.get(1)

                val hlsUrl = Regex("""html5player\.setVideoHLS\('([^']+)'\)""").find(html)?.groupValues?.get(1)
                val urlHigh = Regex("""html5player\.setVideoUrlHigh\('([^']+)'\)""").find(html)?.groupValues?.get(1)
                val urlLow = Regex("""html5player\.setVideoUrlLow\('([^']+)'\)""").find(html)?.groupValues?.get(1)

                val streamOptions = mutableListOf<PlayableStreamOption>()

                if (!hlsUrl.isNullOrBlank()) {
                    streamOptions.add(
                        PlayableStreamOption(
                            qualityLabel = "1080p (Auto HLS)",
                            format = "m3u8",
                            isMuxed = true,
                            videoUrl = hlsUrl,
                            providerType = ProviderType.DIRECT,
                            headers = mapOf("User-Agent" to DEFAULT_USER_AGENT, "Referer" to "$BASE_URL/")
                        )
                    )
                }

                if (!urlHigh.isNullOrBlank()) {
                    streamOptions.add(
                        PlayableStreamOption(
                            qualityLabel = "720p HD MP4",
                            format = "mp4",
                            isMuxed = true,
                            videoUrl = urlHigh,
                            providerType = ProviderType.DIRECT,
                            headers = mapOf("User-Agent" to DEFAULT_USER_AGENT, "Referer" to "$BASE_URL/")
                        )
                    )
                }

                if (!urlLow.isNullOrBlank()) {
                    streamOptions.add(
                        PlayableStreamOption(
                            qualityLabel = "360p SD MP4",
                            format = "mp4",
                            isMuxed = true,
                            videoUrl = urlLow,
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
                        channelName = "XNXX",
                        channelAvatarUrl = null,
                        description = "XNXX High Speed Stream",
                        thumbnailUrl = thumb,
                        providerId = PROVIDER_ID,
                        providerType = ProviderType.DIRECT,
                        availableStreamOptions = streamOptions,
                        selectedStreamOption = best
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct XNXX extraction note: ${e.message}")
        }

        // 2. yt-dlp fallback
        if (context != null) {
            try {
                val result = YtDlpResolver.extractStreamInfo(context, targetUrl)
                if (result is YouTubeExtractorHelper.ExtractionResult.Success) {
                    return@withContext result.streamData.copy(providerId = PROVIDER_ID)
                }
            } catch (e: Exception) {
                Log.w(TAG, "YtDlpResolver extraction failed for XNXX: ${e.message}")
            }
        }

        // 3. Fallback to XNXX Web Embed Player
        val cleanId = targetUrl.substringAfter("/video-").substringBefore("/").substringBefore("?")
        val embedUrl = if (cleanId.isNotBlank() && !cleanId.startsWith("http")) {
            "$BASE_URL/embedframe/$cleanId"
        } else if (targetUrl.contains("/embedframe/")) {
            targetUrl
        } else {
            targetUrl
        }

        val embedOption = PlayableStreamOption(
            qualityLabel = "XNXX Web Player (HD)",
            format = "embed",
            isMuxed = true,
            videoUrl = embedUrl,
            providerType = ProviderType.EMBED,
            headers = mapOf("User-Agent" to DEFAULT_USER_AGENT, "Referer" to "$BASE_URL/")
        )

        return@withContext StreamData(
            videoId = targetUrl,
            videoUrl = embedUrl,
            title = "XNXX Video Stream",
            channelName = "XNXX",
            description = "XNXX Player Stream",
            thumbnailUrl = null,
            providerId = PROVIDER_ID,
            providerType = ProviderType.EMBED,
            availableStreamOptions = listOf(embedOption),
            selectedStreamOption = embedOption
        )
    }
}
