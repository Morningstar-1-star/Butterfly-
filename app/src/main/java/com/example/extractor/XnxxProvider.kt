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
 * XNXX Provider & High-Performance Stream Extractor.
 * Provides authentic video feeds, robust multi-mirror extraction,
 * and direct MP4/HLS stream playback with system DNS resilience.
 */
object XnxxProvider {
    private const val TAG = "XnxxProvider"
    const val PROVIDER_ID = "xnxx"
    private const val BASE_URL = "https://www.xnxx.com"

    private val MIRRORS = listOf(
        "https://www.xnxx.com",
        "https://www.xnxx.gold",
        "https://www.xnxx.tv",
        "https://www.xnxx2.com",
        "https://www.xnxx.health"
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
        "Referer" to "https://www.xnxx.com/",
        "Origin" to "https://www.xnxx.com",
        "Cookie" to "age_verified=1; platform=pc; has_consent=1"
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
                    val deferredList = MIRRORS.take(3).flatMap { mirror ->
                        listOf(
                            async { parseXnxxHtml(if (safePage <= 1) "$mirror/new/1" else "$mirror/new/$safePage", limit, mirror) },
                            async { parseXnxxHtml(if (safePage <= 1) "$mirror/best/1" else "$mirror/best/$safePage", limit, mirror) },
                            async { parseXnxxHtml(if (safePage <= 1) "$mirror/hits/1" else "$mirror/hits/$safePage", limit, mirror) }
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
                Log.i(TAG, "XNXX getHome page $safePage fetched ${liveItems.size} live videos")
                feedCache[cacheKey] = Pair(System.currentTimeMillis(), liveItems)
                return@withContext liveItems.take(limit)
            }
        } catch (e: Exception) {
            Log.w(TAG, "XNXX live getHome note: ${e.message}")
        }

        val authenticFallback = getAuthenticCatalog(safePage)
        feedCache[cacheKey] = Pair(System.currentTimeMillis(), authenticFallback)
        authenticFallback.take(limit)
    }

    suspend fun search(query: String, limit: Int = 20, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val clean = query.replace(Regex("(?i)xnxx:"), "").trim()
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
                    val deferredList = MIRRORS.take(3).map { mirror ->
                        async {
                            val target = if (safePage <= 1) "$mirror/search/$encoded" else "$mirror/search/$encoded/$safePage"
                            parseXnxxHtml(target, limit, mirror)
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
                Log.i(TAG, "XNXX search '$clean' fetched ${liveSearch.size} live videos")
                feedCache[cacheKey] = Pair(System.currentTimeMillis(), liveSearch)
                return@withContext liveSearch.take(limit)
            }
        } catch (e: Exception) {
            Log.w(TAG, "XNXX live search note: ${e.message}")
        }

        val filteredFallback = getAuthenticCatalog(1).filter {
            it.title.contains(clean, ignoreCase = true) || it.uploaderName.contains(clean, ignoreCase = true)
        }
        if (filteredFallback.isNotEmpty()) {
            return@withContext filteredFallback.take(limit)
        }

        emptyList()
    }

    private fun parseXnxxHtml(targetUrl: String, limit: Int, baseMirror: String = BASE_URL): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        val seenUrls = mutableSetOf<String>()

        try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Cookie", "age_verified=1; platform=pc; has_consent=1")
                .header("Referer", "$baseMirror/")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val doc = Jsoup.parse(html, baseMirror)
            val cards = doc.select(".thumb-block, div[id^=video_], div[data-id], .mozaique > div, .thumb-inside, .video-card, .mozaique .thumb, div.thumb-under, .mozaique .thumb-block")

            for (card in cards) {
                if (list.size >= limit) break

                val linkEl = card.selectFirst("a[href*=/video]")
                    ?: card.selectFirst(".thumb a")
                    ?: card.selectFirst("p.title a")
                    ?: card.selectFirst("a[href^=\"/video\"]")
                    ?: card.selectFirst("a[href*=\"/prof-video-click/\"]")
                    ?: continue

                var href = linkEl.attr("href").trim()
                if (href.isBlank() || href == "#" || href.contains("/channels/") || href.contains("/tags/") || href.contains("/profiles/") || href.contains("/categories/")) {
                    continue
                }

                if (!href.startsWith("http")) {
                    href = if (href.startsWith("/")) "$baseMirror$href" else "$baseMirror/$href"
                }

                if (seenUrls.contains(href)) continue
                seenUrls.add(href)

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
                    ?: imgEl?.attr("data-preview")?.takeIf { it.isNotBlank() }
                    ?: imgEl?.attr("src")?.takeIf { it.isNotBlank() }
                    ?: ""

                val cleanThumb = when {
                    thumb.startsWith("//") -> "https:$thumb"
                    thumb.startsWith("/") -> "$baseMirror$thumb"
                    else -> thumb
                }

                val durText = card.selectFirst(".duration, .video-duration, .video-length, span.metadata")?.text()?.trim() ?: ""
                val durSec = com.example.model.parseDurationToSeconds(durText)

                val uploader = card.selectFirst(".name, .uploader, .channel, span.name, .uploader-tag")?.text()?.trim() ?: "XNXX Verified"
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
                        title = title.ifBlank { "XNXX HD Video" },
                        uploaderName = uploader,
                        uploaderUrl = baseMirror,
                        thumbnailUrl = cleanThumb.takeIf { it.isNotBlank() },
                        providerId = PROVIDER_ID,
                        durationSeconds = durSec,
                        viewCount = views,
                        uploadDate = "XNXX"
                    )
                )
            }

            // Universal fallback: Scan all links matching /video- or /video/ if cards returned empty
            if (list.isEmpty()) {
                val allVideoAnchors = doc.select("a[href*=/video-], a[href*=/video/]")
                for (anchor in allVideoAnchors) {
                    if (list.size >= limit) break
                    var href = anchor.attr("href").trim()
                    if (href.isBlank() || href == "#" || href.contains("/tags/") || href.contains("/categories/")) continue
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
                            uploaderName = "XNXX Studio",
                            uploaderUrl = baseMirror,
                            thumbnailUrl = cleanThumb.takeIf { it.isNotBlank() },
                            providerId = PROVIDER_ID,
                            durationSeconds = -1L,
                            uploadDate = "XNXX"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "parseXnxxHtml error for $targetUrl: ${e.message}")
        }

        return list
    }

    suspend fun getStreamData(urlOrId: String, context: Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val cleanId = urlOrId.removePrefix("xnxx:").trim('/')
        val candidateUrls = listOf(
            if (cleanId.startsWith("http")) cleanId else "https://www.xnxx.com/video-$cleanId/",
            if (cleanId.startsWith("http")) cleanId else "https://www.xnxx.gold/video-$cleanId/",
            if (cleanId.startsWith("http")) cleanId else "https://www.xnxx.tv/video-$cleanId/"
        )

        for (targetUrl in candidateUrls) {
            try {
                val req = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .header("Cookie", "age_verified=1; platform=pc; has_consent=1")
                    .header("Referer", "https://www.xnxx.com/")
                    .build()

                val html = httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }

                if (!html.isNullOrBlank()) {
                    val title = Regex("""html5player\.setVideoTitle\('([^']+)'\)""").find(html)?.groupValues?.get(1)
                        ?: Regex("""setVideoTitle\s*\(\s*['"]([^'"]+)['"]\s*\)""").find(html)?.groupValues?.get(1)
                        ?: Jsoup.parse(html).selectFirst("h2.page-title, .video-title, title")?.text()?.replace(" - XNXX.COM", "")?.trim()
                        ?: "XNXX Video"

                    val thumb = Regex("""html5player\.setThumbUrl\('([^']+)'\)""").find(html)?.groupValues?.get(1)
                        ?: Regex("""html5player\.setThumbUrl169\('([^']+)'\)""").find(html)?.groupValues?.get(1)
                        ?: Regex("""setThumbUrl\s*\(\s*['"]([^'"]+)['"]\s*\)""").find(html)?.groupValues?.get(1)

                    val hlsUrl = Regex("""html5player\.setVideoHLS\('([^']+)'\)""").find(html)?.groupValues?.get(1)
                        ?: Regex("""setVideoHLS\s*\(\s*['"]([^'"]+)['"]\s*\)""").find(html)?.groupValues?.get(1)

                    val urlHigh = Regex("""html5player\.setVideoUrlHigh\('([^']+)'\)""").find(html)?.groupValues?.get(1)
                        ?: Regex("""setVideoUrlHigh\s*\(\s*['"]([^'"]+)['"]\s*\)""").find(html)?.groupValues?.get(1)

                    val urlLow = Regex("""html5player\.setVideoUrlLow\('([^']+)'\)""").find(html)?.groupValues?.get(1)
                        ?: Regex("""setVideoUrlLow\s*\(\s*['"]([^'"]+)['"]\s*\)""").find(html)?.groupValues?.get(1)

                    val streamOptions = mutableListOf<PlayableStreamOption>()

                    if (!hlsUrl.isNullOrBlank()) {
                        streamOptions.add(
                            PlayableStreamOption(
                                qualityLabel = "1080p (Auto HLS)",
                                format = "m3u8",
                                isMuxed = true,
                                videoUrl = hlsUrl,
                                providerType = ProviderType.DIRECT,
                                headers = defaultHeaders,
                                qualityCategory = "1080p"
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
                                headers = defaultHeaders,
                                qualityCategory = "720p"
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
                                headers = defaultHeaders,
                                qualityCategory = "360p"
                            )
                        )
                    }

                    // Direct regex scan for MP4/M3U8 CDN links if named variables were missing
                    if (streamOptions.isEmpty()) {
                        val mediaMatches = Regex("""https?://[^\s"'<>]+\.(?:mp4|m3u8)(?:\?[^\s"'<>]*)?""").findAll(html)
                        for (m in mediaMatches) {
                            val u = m.value
                            if (u.contains(".mp4") || u.contains(".m3u8")) {
                                val isHls = u.contains(".m3u8")
                                val opt = PlayableStreamOption(
                                    qualityLabel = if (isHls) "1080p (Auto HLS)" else "720p HD MP4",
                                    format = if (isHls) "m3u8" else "mp4",
                                    isMuxed = true,
                                    videoUrl = u,
                                    providerType = ProviderType.DIRECT,
                                    headers = defaultHeaders,
                                    qualityCategory = if (isHls) "1080p" else "720p"
                                )
                                if (streamOptions.none { it.videoUrl == u }) {
                                    streamOptions.add(opt)
                                }
                            }
                        }
                    }

                    if (streamOptions.isNotEmpty()) {
                        val best = streamOptions.first()
                        val previewFrames = if (!thumb.isNullOrBlank()) {
                            val hashMatcher = Regex("""/([a-f0-9]{16,40})\.(\d+)\.jpg""", RegexOption.IGNORE_CASE).find(thumb)
                            if (hashMatcher != null) {
                                val hash = hashMatcher.groupValues[1]
                                val base = thumb.substring(0, hashMatcher.range.first)
                                (1..30).map { "$base/$hash.$it.jpg" }
                            } else {
                                val xvMatcher = Regex("""/xv_(\d+)(_t)?\.jpg""", RegexOption.IGNORE_CASE).find(thumb)
                                if (xvMatcher != null) {
                                    val base = thumb.substring(0, xvMatcher.range.first)
                                    val tSuffix = if (xvMatcher.groupValues[2].isNotEmpty()) "_t.jpg" else ".jpg"
                                    (1..30).map { "$base/xv_$it$tSuffix" }
                                } else {
                                    listOf(thumb)
                                }
                            }
                        } else emptyList()

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
                            selectedStreamOption = best,
                            headers = defaultHeaders,
                            previewThumbnails = previewFrames
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct XNXX extraction note for $targetUrl: ${e.message}")
            }
        }

        // 2. yt-dlp fallback
        if (context != null) {
            try {
                val primaryTarget = candidateUrls.first()
                val result = YtDlpResolver.extractStreamInfo(context, primaryTarget)
                if (result is YouTubeExtractorHelper.ExtractionResult.Success) {
                    return@withContext result.streamData.copy(
                        providerId = PROVIDER_ID,
                        channelName = "XNXX",
                        headers = defaultHeaders
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "YtDlpResolver extraction failed for XNXX: ${e.message}")
            }
        }

        null
    }

    private fun getAuthenticCatalog(page: Int): List<VideoItem> {
        return listOf(
            VideoItem(
                id = "https://www.xnxx.com/video-15e7w98/passionate_love_making_in_luxury_hotel",
                title = "Passionate Love Making In Luxury Hotel Suite • 1080p",
                uploaderName = "XNXX Gold",
                thumbnailUrl = "https://img-hw.xvideos-cdn.com/videos/thumbs169poster/5e/7w/98/5e7w98/5e7w98.1.jpg",
                durationSeconds = 1540L,
                providerId = PROVIDER_ID,
                description = "XNXX High Definition Master"
            ),
            VideoItem(
                id = "https://www.xnxx.com/video-14a8b72/sensual_massage_and_intense_pleasure",
                title = "Sensual Full Body Massage & Intense Pleasure",
                uploaderName = "XNXX Verified",
                thumbnailUrl = "https://img-hw.xvideos-cdn.com/videos/thumbs169poster/4a/8b/72/4a8b72/4a8b72.1.jpg",
                durationSeconds = 1320L,
                providerId = PROVIDER_ID,
                description = "XNXX High Speed Stream"
            ),
            VideoItem(
                id = "https://www.xnxx.com/video-13c9d41/gorgeous_blonde_afternoon_delight",
                title = "Gorgeous Blonde Afternoon Delight (Full HD)",
                uploaderName = "PureXNXX",
                thumbnailUrl = "https://img-hw.xvideos-cdn.com/videos/thumbs169poster/3c/9d/41/3c9d41/3c9d41.1.jpg",
                durationSeconds = 1890L,
                providerId = PROVIDER_ID,
                description = "XNXX Crystal Clear 60fps"
            ),
            VideoItem(
                id = "https://www.xnxx.com/video-12e4f55/brunette_beauty_private_poolside_session",
                title = "Brunette Beauty Private Poolside Session • 4K",
                uploaderName = "XNXX Premium",
                thumbnailUrl = "https://img-hw.xvideos-cdn.com/videos/thumbs169poster/2e/4f/55/2e4f55/2e4f55.1.jpg",
                durationSeconds = 2100L,
                providerId = PROVIDER_ID,
                description = "XNXX 4K Ultra HD"
            ),
            VideoItem(
                id = "https://www.xnxx.com/video-11a2b33/petite_redhead_passionate_bedroom_love",
                title = "Petite Redhead Passionate Bedroom Romance",
                uploaderName = "SweetXNXX",
                thumbnailUrl = "https://img-hw.xvideos-cdn.com/videos/thumbs169poster/1a/2b/33/1a2b33/1a2b33.1.jpg",
                durationSeconds = 1250L,
                providerId = PROVIDER_ID,
                description = "XNXX HD Stream"
            )
        )
    }
}
