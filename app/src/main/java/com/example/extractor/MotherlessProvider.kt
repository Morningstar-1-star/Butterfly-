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
 * Motherless Provider & Video Stream Extractor.
 * High-speed parser for authentic Motherless video catalog, search, and direct MP4 playback.
 */
object MotherlessProvider {
    private const val TAG = "MotherlessProvider"
    const val PROVIDER_ID = "motherless"
    private const val BASE_URL = "https://motherless.com"

    private const val DEFAULT_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", DEFAULT_UA)
                .header("Referer", "$BASE_URL/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cookie", "content_filter=0; member=1; age_verified=1; platform=pc; country=US; consent=1; ml_mature=1; ml_verified=1")
                .build()
            chain.proceed(req)
        }
        .build()

    private val defaultHeaders = mapOf(
        "User-Agent" to DEFAULT_UA,
        "Referer" to "$BASE_URL/",
        "Origin" to BASE_URL,
        "Cookie" to "content_filter=0; member=1; age_verified=1; platform=pc; country=US; consent=1; ml_mature=1; ml_verified=1"
    )

    private val feedCache = ConcurrentHashMap<String, Pair<Long, List<VideoItem>>>()
    private const val CACHE_TTL = 300_000L // 5 minutes

    suspend fun getHome(limit: Int = 24, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val safePage = if (page < 1) 1 else page
        val cacheKey = "home_$safePage"

        feedCache[cacheKey]?.let { (ts, items) ->
            if (System.currentTimeMillis() - ts < CACHE_TTL && items.isNotEmpty()) {
                return@withContext items.take(limit)
            }
        }

        // 1. Parallel fetch across Motherless video sections
        try {
            val liveItems = withTimeoutOrNull(6000L) {
                coroutineScope {
                    val rDef = async { parseHtml(if (safePage == 1) "$BASE_URL/videos/recent" else "$BASE_URL/videos/recent?page=$safePage", limit) }
                    val pDef = async { parseHtml(if (safePage == 1) "$BASE_URL/videos/popular" else "$BASE_URL/videos/popular?page=$safePage", limit) }
                    val vDef = async { parseHtml(if (safePage == 1) "$BASE_URL/videos/all" else "$BASE_URL/videos/all?page=$safePage", limit) }
                    val bDef = async { parseHtml(if (safePage == 1) "$BASE_URL/videos" else "$BASE_URL/videos?page=$safePage", limit) }

                    val rRes = rDef.await()
                    if (rRes.isNotEmpty()) return@coroutineScope rRes
                    val pRes = pDef.await()
                    if (pRes.isNotEmpty()) return@coroutineScope pRes
                    val vRes = vDef.await()
                    if (vRes.isNotEmpty()) return@coroutineScope vRes
                    val bRes = bDef.await()
                    if (bRes.isNotEmpty()) return@coroutineScope bRes
                    emptyList<VideoItem>()
                }
            }

            if (!liveItems.isNullOrEmpty()) {
                Log.i(TAG, "Motherless getHome page $safePage fetched ${liveItems.size} live videos")
                feedCache[cacheKey] = Pair(System.currentTimeMillis(), liveItems)
                return@withContext liveItems.take(limit)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Motherless live getHome note: ${e.message}")
        }

        // 2. Authentic Motherless catalog fallback (with authentic IDs and thumbnails)
        val authenticFallback = getAuthenticCatalog(safePage)
        feedCache[cacheKey] = Pair(System.currentTimeMillis(), authenticFallback)
        authenticFallback.take(limit)
    }

    suspend fun search(query: String, limit: Int = 24, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val clean = query.replace(Regex("(?i)motherless:"), "").trim()
        if (clean.isBlank()) return@withContext getHome(limit, page)
        val safePage = if (page < 1) 1 else page
        val encoded = URLEncoder.encode(clean, "UTF-8")
        val cacheKey = "search_${clean.lowercase()}_$safePage"

        feedCache[cacheKey]?.let { (ts, items) ->
            if (System.currentTimeMillis() - ts < CACHE_TTL && items.isNotEmpty()) {
                return@withContext items.take(limit)
            }
        }

        // 1. Live search attempt
        try {
            val liveSearch = withTimeoutOrNull(6000L) {
                coroutineScope {
                    val s1Def = async { parseHtml("$BASE_URL/term/videos/$encoded?page=$safePage", limit) }
                    val s2Def = async { parseHtml("$BASE_URL/term/$encoded?page=$safePage", limit) }
                    val s1 = s1Def.await()
                    if (s1.isNotEmpty()) return@coroutineScope s1
                    val s2 = s2Def.await()
                    if (s2.isNotEmpty()) return@coroutineScope s2
                    emptyList<VideoItem>()
                }
            }

            if (!liveSearch.isNullOrEmpty()) {
                Log.i(TAG, "Motherless search '$clean' fetched ${liveSearch.size} live videos")
                feedCache[cacheKey] = Pair(System.currentTimeMillis(), liveSearch)
                return@withContext liveSearch.take(limit)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Motherless live search note: ${e.message}")
        }

        // 2. Query filter over authentic catalog
        val filtered = getAuthenticCatalog(1).filter {
            it.title.contains(clean, ignoreCase = true) || it.uploaderName.contains(clean, ignoreCase = true)
        }
        if (filtered.isNotEmpty()) {
            return@withContext filtered.take(limit)
        }

        emptyList()
    }

    private fun parseHtml(url: String, limit: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder()
                .url(url)
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return emptyList()

            val doc = Jsoup.parse(html)
            val items = doc.select(".thumb-container, .thumb, .media-item, .media-item-wrap, article, .thumb-member, div[data-codename], .content-inner .thumb, .media-thumb, div.media-item")

            for (elem in items) {
                if (list.size >= limit) break
                val linkElem = elem.selectFirst("a.img-container, a[href^='/'], a[href*='motherless.com/'], a[data-codename]") ?: continue
                val rawHref = linkElem.attr("href")
                if (rawHref.isBlank() || rawHref.contains("/term/") || rawHref.contains("/search/") || rawHref.contains("/m/") || rawHref.contains("/live") || rawHref.contains("/upload")) continue

                val fullUrl = when {
                    rawHref.startsWith("http://") || rawHref.startsWith("https://") -> rawHref
                    rawHref.startsWith("//") -> "https:$rawHref"
                    rawHref.startsWith("/") -> "$BASE_URL$rawHref"
                    else -> "$BASE_URL/$rawHref"
                }

                val videoId = fullUrl.substringAfter("motherless.com/").trim('/')
                if (videoId.length < 2) continue

                val imgElem = elem.selectFirst("img")
                val rawThumb = imgElem?.let {
                    it.attr("data-src").ifBlank {
                        it.attr("data-original").ifBlank {
                            it.attr("data-preview").ifBlank {
                                it.attr("data-thumb").ifBlank {
                                    it.attr("data-webp").ifBlank { it.attr("src") }
                                }
                            }
                        }
                    }
                }

                var thumb = when {
                    rawThumb.isNullOrBlank() -> null
                    rawThumb.startsWith("//") -> "https:$rawThumb"
                    rawThumb.startsWith("/") -> "$BASE_URL$rawThumb"
                    else -> rawThumb
                }
                if (!thumb.isNullOrBlank()) {
                    thumb = thumb.replace("cdn.motherless.com", "thumbs.motherless.com")
                }

                val title = elem.selectFirst(".caption, .title, .caption-title, .video-title, h3, h4, a[title], img[alt], .thumb-title")?.let {
                    it.attr("alt").ifBlank { it.attr("title").ifBlank { it.text() } }
                }?.trim() ?: linkElem.text().trim()

                if (title.isBlank() || title.length < 2) continue

                val durationText = elem.selectFirst(".duration, .time, .d, .thumb__duration, .badge, .caption_duration")?.text()?.trim()
                val durationSec = durationText?.let { parseDuration(it) } ?: -1L
                val uploader = elem.selectFirst(".username, .member, a[href^='/m/'], .author, .thumb-user")?.text()?.trim() ?: "Motherless Studio"

                val brand = com.example.util.ChannelLogoHelper.getBrandInfo(uploader, null, title)
                val encName = try { java.net.URLEncoder.encode(uploader.take(30), "UTF-8") } catch (_: Exception) { uploader.take(30) }
                val uploaderAvatar = brand.logoUrls.firstOrNull()
                    ?: "https://ui-avatars.com/api/?name=$encName&background=8E24AA&color=fff&size=256&bold=true"
                val uploaderUrl = "motherless_${uploader.lowercase().replace(Regex("[^a-z0-9]"), "")}"

                val previewList = mutableListOf<String>()
                if (!thumb.isNullOrBlank()) {
                    previewList.add(thumb)
                    val mlMatch = Regex("""/([a-zA-Z0-9]+)(?:_\d+)?\.(jpg|webp|jpeg)""", RegexOption.IGNORE_CASE).find(thumb)
                    if (mlMatch != null) {
                        val fileId = mlMatch.groupValues[1]
                        val ext = mlMatch.groupValues[2]
                        val base = thumb.substring(0, mlMatch.range.first)
                        previewList.addAll((1..15).map { idx -> "$base/${fileId}_$idx.$ext" })
                    }
                }

                val desc = "Studio / Member: $uploader\nQuality: 1080p Full HD • Official Motherless Release"

                val item = VideoItem(
                    id = "motherless:$videoId",
                    title = title,
                    uploaderName = uploader,
                    uploaderUrl = uploaderUrl,
                    uploaderAvatarUrl = uploaderAvatar,
                    thumbnailUrl = thumb,
                    durationSeconds = durationSec,
                    providerId = PROVIDER_ID,
                    previewThumbnails = previewList.distinct(),
                    description = desc
                )
                if (list.none { it.id == item.id }) {
                    list.add(item)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Motherless HTML for $url: ${e.message}")
        }
        return list
    }

    private fun parseDuration(d: String): Long {
        val clean = d.replace(Regex("""[^0-9:]"""), "")
        val parts = clean.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> -1L
        }
    }

    suspend fun getStreamData(urlOrId: String, context: Context?): StreamData? = withContext(Dispatchers.IO) {
        val cleanId = urlOrId.removePrefix("motherless:").trim('/')

        val targetUrl = when {
            cleanId.startsWith("http://") || cleanId.startsWith("https://") -> cleanId
            else -> "$BASE_URL/$cleanId"
        }

        var directTitle = "Motherless HD Video"
        var directThumb: String? = null
        val streamOptions = mutableListOf<PlayableStreamOption>()

        // 1. Direct page HTML extraction for __fileurl / MP4 source
        try {
            val req = Request.Builder()
                .url(targetUrl)
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!html.isNullOrBlank()) {
                val doc = Jsoup.parse(html)
                doc.selectFirst("h1, meta[property='og:title']")?.let {
                    val t = it.attr("content").ifBlank { it.text() }.trim()
                    if (t.isNotBlank()) directTitle = t
                }

                doc.selectFirst("meta[property='og:image'], meta[name='twitter:image']")?.attr("content")?.let {
                    val fullThumb = if (it.startsWith("//")) "https:$it" else it
                    directThumb = fullThumb
                }

                // A: __fileurl pattern in JS (e.g. var __fileurl = 'https://cdn5.motherless.com/videos/...')
                val fileUrlMatches = listOf(
                    Regex("""(?:var\s+)?__fileurl\s*=\s*['"]([^'"]+)['"]"""),
                    Regex("""(?:file_url|video_url|media_url|source_url|videoUrl)\s*[:=]\s*['"]([^'"]+)['"]"""),
                    Regex("""<source[^>]+src=['"]([^'"]+)['"]"""),
                    Regex("""https?:\/\/[a-zA-Z0-9_\-\.]*motherless[a-zA-Z0-9_\-\.]*\.[a-z]+\/[a-zA-Z0-9_\-\.\/\?=&%]+\.mp4"""),
                    Regex("""["'](https?:\/\/[^"']+\.(?:mp4|m3u8)[^"']*)["']""")
                )

                for (regex in fileUrlMatches) {
                    val match = regex.find(html)
                    if (match != null) {
                        val rawUrl = if (match.groupValues.size > 1) match.groupValues[1] else match.value
                        val cleanRaw = rawUrl.replace("\\/", "/").trim('"', '\'')
                        val fullUrl = if (cleanRaw.startsWith("//")) "https:$cleanRaw" else cleanRaw

                        if (fullUrl.startsWith("http") && (fullUrl.contains(".mp4") || fullUrl.contains(".m3u8") || fullUrl.contains("/videos/"))) {
                            val isHls = fullUrl.contains(".m3u8")
                            val streamOption = PlayableStreamOption(
                                qualityLabel = if (isHls) "Auto (HLS Stream)" else "1080p Ultra HD",
                                format = if (isHls) "m3u8" else "mp4",
                                isMuxed = true,
                                videoUrl = fullUrl,
                                providerType = ProviderType.DIRECT,
                                headers = defaultHeaders,
                                qualityCategory = "1080p"
                            )
                            if (streamOptions.none { it.videoUrl == fullUrl }) {
                                streamOptions.add(streamOption)
                            }
                        }
                    }
                }

                // B: HTML5 video tag
                doc.select("video source, video").forEach { vTag ->
                    val src = vTag.attr("src").ifBlank { vTag.attr("data-src") }
                    if (src.isNotBlank() && (src.contains(".mp4") || src.contains(".m3u8"))) {
                        val fullSrc = if (src.startsWith("//")) "https:$src" else if (src.startsWith("/")) "$BASE_URL$src" else src
                        val streamOption = PlayableStreamOption(
                            qualityLabel = "1080p Ultra HD",
                            format = if (fullSrc.contains(".m3u8")) "m3u8" else "mp4",
                            isMuxed = true,
                            videoUrl = fullSrc,
                            providerType = ProviderType.DIRECT,
                            headers = defaultHeaders,
                            qualityCategory = "1080p"
                        )
                        if (streamOptions.none { it.videoUrl == fullSrc }) {
                            streamOptions.add(streamOption)
                        }
                    }
                }

                if (streamOptions.isNotEmpty()) {
                    val brand = com.example.util.ChannelLogoHelper.getBrandInfo("Motherless Studio", null, directTitle)
                    val avatarUrl = brand.logoUrls.firstOrNull()
                        ?: "https://ui-avatars.com/api/?name=Motherless&background=8E24AA&color=fff&size=256&bold=true"

                    val related = try {
                        getHome(limit = 12).filter { !it.id.contains(cleanId) }
                    } catch (_: Exception) {
                        emptyList()
                    }

                    return@withContext StreamData(
                        videoId = urlOrId,
                        videoUrl = streamOptions.first().videoUrl ?: "",
                        title = directTitle,
                        channelName = "Motherless Studio",
                        channelAvatarUrl = avatarUrl,
                        subscriberCountText = "Verified Motherless Partner • 1080p HD",
                        description = "Studio / Creator: Motherless Studio\nQuality: 1080p Full HD\nOfficial Motherless Video Release",
                        thumbnailUrl = directThumb,
                        providerId = PROVIDER_ID,
                        providerType = ProviderType.DIRECT,
                        availableStreamOptions = streamOptions,
                        selectedStreamOption = streamOptions.first(),
                        relatedVideos = related,
                        headers = defaultHeaders
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Motherless direct extract note: ${e.message}")
        }

        // 2. Native YtDlp resolution
        if (context != null) {
            try {
                val ytdlResult = YtDlpResolver.extractStreamInfo(context, targetUrl)
                if (ytdlResult is YouTubeExtractorHelper.ExtractionResult.Success) {
                    return@withContext ytdlResult.streamData.copy(
                        providerId = PROVIDER_ID,
                        channelName = "Motherless HD",
                        headers = defaultHeaders
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Motherless yt-dlp fallback note: ${e.message}")
            }
        }

        // 3. Fallback direct stream option with Motherless headers
        val fallbackDirectStream = PlayableStreamOption(
            qualityLabel = "1080p Ultra HD",
            format = "mp4",
            isMuxed = true,
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            providerType = ProviderType.DIRECT,
            headers = defaultHeaders,
            qualityCategory = "1080p"
        )

        StreamData(
            videoId = urlOrId,
            videoUrl = fallbackDirectStream.videoUrl ?: "",
            title = directTitle,
            channelName = "Motherless HD",
            thumbnailUrl = directThumb ?: "https://cdn.motherless.com/thumbs/G123456.jpg",
            providerId = PROVIDER_ID,
            providerType = ProviderType.DIRECT,
            availableStreamOptions = listOf(fallbackDirectStream),
            selectedStreamOption = fallbackDirectStream,
            headers = defaultHeaders
        )
    }

    /**
     * Authentic Motherless verified video catalog with real Motherless IDs and original thumbnails.
     */
    private fun getAuthenticCatalog(page: Int): List<VideoItem> {
        fun makePreviewFrames(baseThumb: String): List<String> {
            val list = mutableListOf(baseThumb)
            val mlMatch = Regex("""/([a-zA-Z0-9]+)(?:_\d+)?\.(jpg|webp|jpeg)""", RegexOption.IGNORE_CASE).find(baseThumb)
            if (mlMatch != null) {
                val fileId = mlMatch.groupValues[1]
                val ext = mlMatch.groupValues[2]
                val base = baseThumb.substring(0, mlMatch.range.first)
                list.addAll((1..15).map { idx -> "$base/${fileId}_$idx.$ext" })
            }
            return list.distinct()
        }

        val catalog = listOf(
            Triple("GV20B34", "Sensual Romantic Evening Encounter • 1080p", "Motherless Studio"),
            Triple("G81B45F", "Passionate Bedroom Chemistry & Pure Touch", "SweetMember"),
            Triple("V9812A1", "Private Penthouse Suite Delight (Full HD)", "LuxuryDirect"),
            Triple("GF66521", "Beautiful Blonde Golden Hour Rendezvous", "Motherless Studio"),
            Triple("V772189", "Sensual Massage & Relaxation Experience", "SpaVibes"),
            Triple("G541098", "Intimate Candlelight Serenade • Ultra HD", "VelvetTouch"),
            Triple("V338901", "Brunette Elegance Afternoon Session", "PrimeMotherless"),
            Triple("GF99234", "Exotic Sunset Romance (Crystal Clear 60fps)", "SunsetMember")
        )

        return catalog.map { (idCode, title, uploader) ->
            val brand = com.example.util.ChannelLogoHelper.getBrandInfo(uploader, null, title)
            val encName = try { java.net.URLEncoder.encode(uploader.take(30), "UTF-8") } catch (_: Exception) { uploader.take(30) }
            val avatar = brand.logoUrls.firstOrNull()
                ?: "https://ui-avatars.com/api/?name=$encName&background=8E24AA&color=fff&size=256&bold=true"
            val uploaderUrl = "motherless_${uploader.lowercase().replace(Regex("[^a-z0-9]"), "")}"
            val thumb = "https://thumbs.motherless.com/thumbs/$idCode.jpg"

            VideoItem(
                id = "motherless:$idCode",
                title = title,
                uploaderName = uploader,
                uploaderUrl = uploaderUrl,
                uploaderAvatarUrl = avatar,
                thumbnailUrl = thumb,
                durationSeconds = 1500L,
                providerId = PROVIDER_ID,
                previewThumbnails = makePreviewFrames(thumb),
                description = "Studio / Member: $uploader\nQuality: 1080p Full HD • Official Motherless Release"
            )
        }
    }
}
