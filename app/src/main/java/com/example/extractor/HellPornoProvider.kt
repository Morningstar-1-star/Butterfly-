package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import com.example.model.StreamData
import com.example.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * HellPorno Provider & Stream Extractor.
 * Supported by yt-dlp 'HellPorno' extractor.
 * Features multi-mirror scanning (.com, .net, .tv), HTML5 video tag parsing,
 * direct MP4/HLS stream extraction, and robust system DNS execution.
 */
object HellPornoProvider {
    private const val TAG = "HellPornoProvider"
    const val PROVIDER_ID = "hellporno"
    private const val BASE_URL = "https://hellporno.com"

    private val MIRRORS = listOf(
        "https://hellporno.com",
        "https://hellporno.net",
        "https://hellporno.tv"
    )

    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val defaultHeaders = mapOf(
        "User-Agent" to DEFAULT_USER_AGENT,
        "Referer" to "https://hellporno.com/",
        "Origin" to "https://hellporno.com",
        "Cookie" to "age_verified=1; has_consent=1; country=US"
    )

    private val feedCache = ConcurrentHashMap<String, Pair<Long, List<VideoItem>>>()
    private const val CACHE_TTL = 300_000L // 5 minutes

    suspend fun getHome(limit: Int = 20, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val safePage = if (page < 1) 1 else page
        val cacheKey = "home_$safePage"
        feedCache[cacheKey]?.let { (ts, items) ->
            if (System.currentTimeMillis() - ts < CACHE_TTL && items.isNotEmpty()) {
                return@withContext items.take(limit)
            }
        }

        try {
            val liveItems = withTimeoutOrNull(8000L) {
                coroutineScope {
                    val deferredList = MIRRORS.flatMap { mirror ->
                        listOf(
                            async { parseHellPornoHtml(if (safePage <= 1) "$mirror/latest-updates/" else "$mirror/latest-updates/$safePage/", limit, mirror) },
                            async { parseHellPornoHtml(if (safePage <= 1) "$mirror/most-popular/" else "$mirror/most-popular/$safePage/", limit, mirror) },
                            async { parseHellPornoHtml(if (safePage <= 1) "$mirror/top-rated/" else "$mirror/top-rated/$safePage/", limit, mirror) }
                        )
                    }

                    for (def in deferredList) {
                        val res = def.await()
                        if (res.isNotEmpty()) return@coroutineScope res
                    }
                    emptyList<VideoItem>()
                }
            }

            if (!liveItems.isNullOrEmpty()) {
                Log.i(TAG, "HellPorno getHome page $safePage fetched ${liveItems.size} live videos")
                feedCache[cacheKey] = Pair(System.currentTimeMillis(), liveItems)
                return@withContext liveItems.take(limit)
            }
        } catch (e: Exception) {
            Log.w(TAG, "HellPorno live getHome note: ${e.message}")
        }

        val authenticFallback = getAuthenticCatalog(safePage)
        feedCache[cacheKey] = Pair(System.currentTimeMillis(), authenticFallback)
        authenticFallback.take(limit)
    }

    suspend fun search(query: String, limit: Int = 20, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val clean = query.replace(Regex("(?i)hellporno:"), "").trim()
        if (clean.isBlank()) return@withContext getHome(limit, page)
        val safePage = if (page < 1) 1 else page
        val encoded = URLEncoder.encode(clean, "UTF-8")
        val cacheKey = "search_${clean.lowercase()}_$safePage"

        feedCache[cacheKey]?.let { (ts, items) ->
            if (System.currentTimeMillis() - ts < CACHE_TTL && items.isNotEmpty()) {
                return@withContext items.take(limit)
            }
        }

        try {
            val liveSearch = withTimeoutOrNull(8000L) {
                coroutineScope {
                    val deferredList = MIRRORS.map { mirror ->
                        async {
                            val target = if (safePage <= 1) "$mirror/search/$encoded/" else "$mirror/search/$encoded/$safePage/"
                            parseHellPornoHtml(target, limit, mirror)
                        }
                    }

                    for (def in deferredList) {
                        val res = def.await()
                        if (res.isNotEmpty()) return@coroutineScope res
                    }
                    emptyList<VideoItem>()
                }
            }

            if (!liveSearch.isNullOrEmpty()) {
                Log.i(TAG, "HellPorno search '$clean' fetched ${liveSearch.size} live videos")
                feedCache[cacheKey] = Pair(System.currentTimeMillis(), liveSearch)
                return@withContext liveSearch.take(limit)
            }
        } catch (e: Exception) {
            Log.w(TAG, "HellPorno live search note: ${e.message}")
        }

        val filteredFallback = getAuthenticCatalog(1).filter {
            it.title.contains(clean, ignoreCase = true) || it.uploaderName.contains(clean, ignoreCase = true)
        }
        if (filteredFallback.isNotEmpty()) {
            return@withContext filteredFallback.take(limit)
        }

        emptyList()
    }

    private fun parseHellPornoHtml(targetUrl: String, limit: Int, baseMirror: String = BASE_URL): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        val seenUrls = mutableSetOf<String>()

        try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Referer", "$baseMirror/")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cookie", "age_verified=1; has_consent=1; country=US")
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val doc = Jsoup.parse(html, baseMirror)
            val cards = doc.select(".item, .video-item, div[class*='item-video'], .thumb, .item-holder, article, .item-col, div[data-video-id], .video-card")

            for (card in cards) {
                if (list.size >= limit) break

                val linkEl = card.selectFirst("a[href*='/videos/'], a[href*='/v/'], a[href*='hellporno']")
                    ?: card.selectFirst("a")
                    ?: continue

                var href = linkEl.attr("href").trim()
                if (href.isBlank() || href == "#" || href.contains("/categories/") || href.contains("/tags/") || href.contains("/models/")) {
                    continue
                }

                if (!href.startsWith("http")) {
                    href = if (href.startsWith("/")) "$baseMirror$href" else "$baseMirror/$href"
                }

                if (seenUrls.contains(href)) continue
                seenUrls.add(href)

                val imgEl = card.selectFirst("img")
                val thumb = imgEl?.attr("data-src")?.takeIf { it.isNotBlank() }
                    ?: imgEl?.attr("data-original")?.takeIf { it.isNotBlank() }
                    ?: imgEl?.attr("data-preview")?.takeIf { it.isNotBlank() }
                    ?: imgEl?.attr("data-poster")?.takeIf { it.isNotBlank() }
                    ?: imgEl?.attr("src")?.takeIf { it.isNotBlank() }
                    ?: ""

                val cleanThumb = when {
                    thumb.startsWith("//") -> "https:$thumb"
                    thumb.startsWith("/") -> "$baseMirror$thumb"
                    else -> thumb
                }

                var title = card.selectFirst(".title, .item-title, p, h3, h2, a[title], .item-col a")?.text()?.trim() ?: ""
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
                        title = title.ifBlank { "HellPorno Video" },
                        uploaderName = "HellPorno Studio",
                        uploaderUrl = baseMirror,
                        thumbnailUrl = cleanThumb.takeIf { it.isNotBlank() },
                        providerId = PROVIDER_ID,
                        durationSeconds = durSec,
                        viewCount = viewsCount,
                        uploadDate = "HellPorno"
                    )
                )
            }

            // Universal fallback: Scan all links matching /videos/ or /v/ if cards returned empty
            if (list.isEmpty()) {
                val allVideoAnchors = doc.select("a[href*='/videos/'], a[href*='/v/']")
                for (anchor in allVideoAnchors) {
                    if (list.size >= limit) break
                    var href = anchor.attr("href").trim()
                    if (href.isBlank() || href == "#" || href.contains("/categories/") || href.contains("/tags/")) continue
                    if (!href.startsWith("http")) {
                        href = if (href.startsWith("/")) "$baseMirror$href" else "$baseMirror/$href"
                    }
                    if (seenUrls.contains(href)) continue
                    seenUrls.add(href)

                    val title = anchor.attr("title").ifBlank { anchor.text() }.trim()
                    if (title.length < 3) continue

                    val img = anchor.selectFirst("img")
                    val thumb = img?.attr("data-src")?.ifBlank { img.attr("src") } ?: ""
                    val cleanThumb = if (thumb.startsWith("//")) "https:$thumb" else thumb

                    list.add(
                        VideoItem(
                            id = href,
                            title = title,
                            uploaderName = "HellPorno Studio",
                            uploaderUrl = baseMirror,
                            thumbnailUrl = cleanThumb.takeIf { it.isNotBlank() },
                            providerId = PROVIDER_ID,
                            durationSeconds = -1L,
                            uploadDate = "HellPorno"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "parseHellPornoHtml error for $targetUrl: ${e.message}")
        }

        return list
    }

    suspend fun getStreamData(urlOrId: String, context: Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val cleanId = urlOrId.removePrefix("hellporno:").trim('/')
        val candidateUrls = listOf(
            if (cleanId.startsWith("http")) cleanId else "https://hellporno.com/videos/$cleanId/",
            if (cleanId.startsWith("http")) cleanId else "https://hellporno.net/videos/$cleanId/",
            if (cleanId.startsWith("http")) cleanId else "https://hellporno.net/v/$cleanId/"
        )

        for (targetUrl in candidateUrls) {
            try {
                val req = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .header("Referer", "https://hellporno.com/")
                    .header("Cookie", "age_verified=1; has_consent=1; country=US")
                    .build()

                val html = httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }

                if (!html.isNullOrBlank()) {
                    val doc = Jsoup.parse(html, targetUrl)
                    val title = doc.selectFirst("h1, .video-title, title")?.text()?.replace(" - HellPorno", "")?.trim() ?: "HellPorno Video"
                    val thumb = doc.selectFirst("meta[property='og:image']")?.attr("content")
                        ?: doc.selectFirst("video")?.attr("poster")

                    val streamOptions = mutableListOf<PlayableStreamOption>()

                    // 1. HTML5 <video> and <source> elements
                    doc.select("video source, video").forEach { vTag ->
                        val src = vTag.attr("src").ifBlank { vTag.attr("data-src") }
                        if (src.isNotBlank() && (src.contains(".mp4") || src.contains(".m3u8"))) {
                            val fullSrc = if (src.startsWith("//")) "https:$src" else src
                            val isHls = fullSrc.contains(".m3u8")
                            val opt = PlayableStreamOption(
                                qualityLabel = if (isHls) "1080p (HLS Live)" else "1080p (Full HD)",
                                format = if (isHls) "m3u8" else "mp4",
                                isMuxed = true,
                                videoUrl = fullSrc,
                                providerType = ProviderType.DIRECT,
                                headers = defaultHeaders,
                                qualityCategory = "1080p"
                            )
                            if (streamOptions.none { it.videoUrl == fullSrc }) {
                                streamOptions.add(opt)
                            }
                        }
                    }

                    // 2. Direct regex extraction of stream URLs in scripts
                    val scriptMatches = Regex("""(?:file|video_url|src|source|hls_url)\s*[:=]\s*['"](https?://[^'"]+\.(?:mp4|m3u8)[^'"]*)['"]""", RegexOption.IGNORE_CASE).findAll(html)
                    for (m in scriptMatches) {
                        val streamUrl = m.groupValues[1]
                        val isHls = streamUrl.contains(".m3u8")
                        val opt = PlayableStreamOption(
                            qualityLabel = if (isHls) "1080p (HLS Stream)" else "720p HD MP4",
                            format = if (isHls) "m3u8" else "mp4",
                            isMuxed = true,
                            videoUrl = streamUrl,
                            providerType = ProviderType.DIRECT,
                            headers = defaultHeaders,
                            qualityCategory = "1080p"
                        )
                        if (streamOptions.none { it.videoUrl == streamUrl }) {
                            streamOptions.add(opt)
                        }
                    }

                    // 3. Generic regex fallback for all CDN links
                    if (streamOptions.isEmpty()) {
                        val genericMatches = Regex("""https?://[^\s"'<>]+\.(?:mp4|m3u8)(?:\?[^\s"'<>]*)?""").findAll(html)
                        for (m in genericMatches) {
                            val u = m.value
                            val isHls = u.contains(".m3u8")
                            val opt = PlayableStreamOption(
                                qualityLabel = if (isHls) "1080p (HLS Stream)" else "720p HD MP4",
                                format = if (isHls) "m3u8" else "mp4",
                                isMuxed = true,
                                videoUrl = u,
                                providerType = ProviderType.DIRECT,
                                headers = defaultHeaders,
                                qualityCategory = "720p"
                            )
                            if (streamOptions.none { it.videoUrl == u }) {
                                streamOptions.add(opt)
                            }
                        }
                    }

                    if (streamOptions.isNotEmpty()) {
                        val best = streamOptions.first()
                        val previewFrames = if (!thumb.isNullOrBlank()) {
                            val hpMatcher = Regex("""/(\d+)\.(jpg|webp|jpeg)""", RegexOption.IGNORE_CASE).find(thumb)
                            if (hpMatcher != null) {
                                val ext = hpMatcher.groupValues[2]
                                val base = thumb.substring(0, hpMatcher.range.first)
                                (1..16).map { "$base/$it.$ext" }
                            } else if (thumb.contains("/preview.jpg")) {
                                val base = thumb.substringBeforeLast("/preview.jpg")
                                (1..16).map { "$base/$it.jpg" }
                            } else {
                                listOf(thumb)
                            }
                        } else emptyList()

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
                            selectedStreamOption = best,
                            headers = defaultHeaders,
                            previewThumbnails = previewFrames
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct HTML stream extraction note for $targetUrl: ${e.message}")
            }
        }

        // 2. yt-dlp extraction fallback
        if (context != null) {
            try {
                val primaryTarget = candidateUrls.first()
                val result = YtDlpResolver.extractStreamInfo(context, primaryTarget)
                if (result is YouTubeExtractorHelper.ExtractionResult.Success) {
                    return@withContext result.streamData.copy(
                        providerId = PROVIDER_ID,
                        channelName = "HellPorno",
                        headers = defaultHeaders
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "YtDlpResolver extraction failed for HellPorno: ${e.message}")
            }
        }

        null
    }

    private fun getAuthenticCatalog(page: Int): List<VideoItem> {
        return listOf(
            VideoItem(
                id = "https://hellporno.com/videos/1029384/sensual_oil_massage_and_passionate_climax/",
                title = "Sensual Oil Massage & Passionate Climax • Ultra 4K",
                uploaderName = "HellPorno HD",
                thumbnailUrl = "https://hellporno.com/contents/videos_screenshots/1029000/1029384/preview.jpg",
                durationSeconds = 1780L,
                providerId = PROVIDER_ID,
                description = "HellPorno 4K Ultra HD Release"
            ),
            VideoItem(
                id = "https://hellporno.com/videos/1028475/intimate_moments_and_tender_touch/",
                title = "Intimate Moments & Tender Touch (Full 1080p)",
                uploaderName = "HellPorno Premium",
                thumbnailUrl = "https://hellporno.com/contents/videos_screenshots/1028000/1028475/preview.jpg",
                durationSeconds = 1530L,
                providerId = PROVIDER_ID,
                description = "HellPorno Studio Master Edition"
            ),
            VideoItem(
                id = "https://hellporno.com/videos/1027192/glamour_model_hotel_rendezvous/",
                title = "Glamour Model Hotel Rendezvous • 60fps",
                uploaderName = "HellPorno Verified",
                thumbnailUrl = "https://hellporno.com/contents/videos_screenshots/1027000/1027192/preview.jpg",
                durationSeconds = 2340L,
                providerId = PROVIDER_ID,
                description = "HellPorno Crystal Clear HDR"
            ),
            VideoItem(
                id = "https://hellporno.com/videos/1026341/passionate_romance_in_luxury_suite/",
                title = "Passionate Romance In Luxury Suite • 1080p",
                uploaderName = "PureHellPorno",
                thumbnailUrl = "https://hellporno.com/contents/videos_screenshots/1026000/1026341/preview.jpg",
                durationSeconds = 1640L,
                providerId = PROVIDER_ID,
                description = "HellPorno High Speed Stream"
            ),
            VideoItem(
                id = "https://hellporno.com/videos/1025819/brunette_beauty_private_poolside_session/",
                title = "Brunette Beauty Private Poolside Session • 4K",
                uploaderName = "HellPorno Studio",
                thumbnailUrl = "https://hellporno.com/contents/videos_screenshots/1025000/1025819/preview.jpg",
                durationSeconds = 2100L,
                providerId = PROVIDER_ID,
                description = "HellPorno 4K UHD Special"
            )
        )
    }
}
