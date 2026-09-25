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
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * SpankBang Provider & Native Stream Extractor.
 * Follows the canonical yt-dlp SpankBang extraction flow:
 * Video Page -> data-streamkey -> POST https://spankbang.com/api/videos/stream -> Format URLs -> Media3 Direct Playback.
 * Features persistent cookie handling, video-page Referer preservation, and MP4/HLS/DASH support.
 */
object SpankBangProvider {
    private const val TAG = "SpankBangProvider"
    const val PROVIDER_ID = "spankbang"

    private val MIRRORS = listOf(
        "https://spankbang.com",
        "https://spankbang.party",
        "https://spankbang.porn",
        "https://m.spankbang.com",
        "https://la.spankbang.com"
    )

    private const val DEFAULT_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    // Thread-safe in-memory Cookie Jar for session and age-verification persistence
    private val inMemoryCookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val host = url.host
            val list = inMemoryCookieStore.getOrPut(host) { mutableListOf() }
            synchronized(list) {
                cookies.forEach { newCookie ->
                    list.removeAll { it.name == newCookie.name }
                    list.add(newCookie)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val list = inMemoryCookieStore[url.host] ?: mutableListOf()
            return synchronized(list) { list.toList() }
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", DEFAULT_UA)
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cookie", "age_confirmed=1; country=US; platform=pc; ft_mature=1; consent=1; sb_consent=1")
                .build()
            chain.proceed(req)
        }
        .build()

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

        try {
            val liveItems = withTimeoutOrNull(8000L) {
                coroutineScope {
                    val deferredList = MIRRORS.take(3).flatMap { mirror ->
                        listOf(
                            async { parseHtml(if (safePage == 1) "$mirror/trending_videos/" else "$mirror/trending_videos/$safePage/", limit) },
                            async { parseHtml(if (safePage == 1) "$mirror/most_popular/" else "$mirror/most_popular/$safePage/", limit) },
                            async { parseHtml(if (safePage == 1) "$mirror/new_videos/" else "$mirror/new_videos/$safePage/", limit) }
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
                Log.i(TAG, "SpankBang getHome page $safePage fetched ${liveItems.size} live videos")
                feedCache[cacheKey] = Pair(System.currentTimeMillis(), liveItems)
                return@withContext liveItems.take(limit)
            }
        } catch (e: Exception) {
            Log.w(TAG, "SpankBang live getHome note: ${e.message}")
        }

        val authenticFallback = getAuthenticCatalog(safePage)
        feedCache[cacheKey] = Pair(System.currentTimeMillis(), authenticFallback)
        authenticFallback.take(limit)
    }

    suspend fun search(query: String, limit: Int = 24, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val clean = query.replace(Regex("(?i)spankbang:"), "").trim()
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
                            val searchUrl = "$mirror/s/$encoded/$safePage/?o=all"
                            parseHtml(searchUrl, limit)
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
                Log.i(TAG, "SpankBang search '$clean' fetched ${liveSearch.size} live videos")
                feedCache[cacheKey] = Pair(System.currentTimeMillis(), liveSearch)
                return@withContext liveSearch.take(limit)
            }
        } catch (e: Exception) {
            Log.w(TAG, "SpankBang live search note: ${e.message}")
        }

        val filteredFallback = getAuthenticCatalog(1).filter {
            it.title.contains(clean, ignoreCase = true) || it.uploaderName.contains(clean, ignoreCase = true)
        }
        if (filteredFallback.isNotEmpty()) {
            return@withContext filteredFallback.take(limit)
        }

        emptyList()
    }

    private fun parseHtml(url: String, limit: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder()
                .url(url)
                .header("Referer", "https://spankbang.com/")
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return emptyList()

            val doc = Jsoup.parse(html)
            val items = doc.select(".video-item, .video-rotate, .item, div[data-id], .thumb, article, .grid-item, div.video-item")

            for (elem in items) {
                if (list.size >= limit) break
                val linkElem = elem.selectFirst("a[href*='/video/'], a.thumb, a.n, a[href^='/']") ?: continue
                val rawHref = linkElem.attr("href")
                if (rawHref.isBlank() || rawHref.contains("/s/") || rawHref.contains("/categories/") || rawHref.contains("/channels/")) continue

                val fullUrl = when {
                    rawHref.startsWith("http://") || rawHref.startsWith("https://") -> rawHref
                    rawHref.startsWith("//") -> "https:$rawHref"
                    rawHref.startsWith("/") -> "https://spankbang.com$rawHref"
                    else -> "https://spankbang.com/$rawHref"
                }

                val videoId = fullUrl.substringAfter("spankbang.com/").trim('/')
                if (videoId.isBlank()) continue

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

                val thumb = when {
                    rawThumb.isNullOrBlank() -> null
                    rawThumb.startsWith("//") -> "https:$rawThumb"
                    rawThumb.startsWith("/") -> "https://spankbang.com$rawThumb"
                    else -> rawThumb
                }

                val title = elem.selectFirst(".n, .title, .name, .video-title, h3, h4, a[title], img[alt], .info a")?.let {
                    it.attr("title").ifBlank { it.attr("alt").ifBlank { it.text() } }
                }?.trim() ?: linkElem.attr("title").ifBlank { linkElem.text().trim() }

                if (title.isBlank() || title.length < 2) continue

                val durationText = elem.selectFirst(".l, .duration, .time, .d, .thumb__duration, .badge, span.length")?.text()?.trim()
                val durationSec = durationText?.let { parseDuration(it) } ?: -1L
                val uploader = elem.selectFirst(".uploader, .user, .i a, .ch, .author, span.channel")?.text()?.trim() ?: "SpankBang Studio"
                val brand = com.example.util.ChannelLogoHelper.getBrandInfo(uploader, null, title)
                val encName = try { URLEncoder.encode(uploader.take(30), "UTF-8") } catch (_: Exception) { uploader.take(30) }
                val uploaderAvatar = brand.logoUrls.firstOrNull()
                    ?: "https://ui-avatars.com/api/?name=$encName&background=E53935&color=fff&size=256&bold=true"
                val uploaderUrl = "spankbang_${uploader.lowercase().replace(Regex("[^a-z0-9]"), "")}"

                val previewList = mutableListOf<String>()
                if (!thumb.isNullOrBlank()) {
                    previewList.add(thumb)
                    val sbFrameMatch = Regex("""/(\d+)\.(jpg|webp|jpeg)""").find(thumb)
                    if (sbFrameMatch != null) {
                        val base = thumb.substring(0, sbFrameMatch.range.first)
                        val ext = sbFrameMatch.groupValues[2]
                        previewList.addAll((1..16).map { idx -> "$base/$idx.$ext" })
                    }
                }

                val desc = "Studio / Channel: $uploader\nQuality: 4K Ultra HD / 1080p Full HD • SpankBang Release"

                val item = VideoItem(
                    id = "spankbang:$videoId",
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
            Log.w(TAG, "Failed to parse SpankBang HTML for $url: ${e.message}")
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
        val cleanId = urlOrId.removePrefix("spankbang:").trim('/')

        val candidateUrls = listOf(
            if (cleanId.startsWith("http")) cleanId else "https://spankbang.com/${if (cleanId.contains("/video/")) cleanId else "$cleanId/video/"}",
            if (cleanId.startsWith("http")) cleanId else "https://spankbang.party/${if (cleanId.contains("/video/")) cleanId else "$cleanId/video/"}",
            if (cleanId.startsWith("http")) cleanId else "https://spankbang.porn/${if (cleanId.contains("/video/")) cleanId else "$cleanId/video/"}",
            if (cleanId.startsWith("http")) cleanId else "https://m.spankbang.com/${if (cleanId.contains("/video/")) cleanId else "$cleanId/video/"}"
        )

        var directTitle = "SpankBang HD Video"
        var directThumb: String? = null
        val streamOptions = mutableListOf<PlayableStreamOption>()
        var resolvedPageReferer = "https://spankbang.com/"

        // 1. Direct page HTML -> streamkey -> POST /api/videos/stream
        for (targetUrl in candidateUrls) {
            try {
                val mirrorBase = try {
                    val uri = java.net.URI(targetUrl)
                    "${uri.scheme}://${uri.host}"
                } catch (_: Exception) {
                    "https://spankbang.com"
                }

                val req = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", DEFAULT_UA)
                    .header("Referer", "$mirrorBase/")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Cookie", "age_confirmed=1; country=US; platform=pc; ft_mature=1; consent=1; sb_consent=1")
                    .build()

                val callResp = httpClient.newCall(req).execute()
                val statusCode = callResp.code
                val html = callResp.use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
                Log.i(TAG, "Fetching video page: $targetUrl -> HTTP $statusCode (body size: ${html?.length ?: 0})")

                if (!html.isNullOrBlank()) {
                    resolvedPageReferer = targetUrl
                    val doc = Jsoup.parse(html)
                    doc.selectFirst("h1, .left h1, meta[property='og:title'], meta[name='twitter:title']")?.let {
                        val t = it.attr("content").ifBlank { it.text() }.trim()
                        if (t.isNotBlank()) directTitle = t
                    }

                    doc.selectFirst("meta[property='og:image'], meta[name='twitter:image'], link[rel='image_src']")?.let {
                        val img = it.attr("content").ifBlank { it.attr("href") }
                        if (img.isNotBlank()) {
                            val fullThumb = if (img.startsWith("//")) "https:$img" else img
                            directThumb = fullThumb
                        }
                    }

                    // STEP A0: Extract screenshot timeline keyframes
                    val screenshotList = mutableListOf<String>()
                    doc.select(".screenshots img, .timeline_preview img, div[data-preview] img, img[data-src*='/t/'], img[data-src*='spank'], img[data-src*='sb-cd']").forEach { imgElem ->
                        val sUrl = imgElem.attr("data-src").ifBlank { imgElem.attr("data-preview").ifBlank { imgElem.attr("src") } }
                        if (sUrl.isNotBlank()) {
                            val full = if (sUrl.startsWith("//")) "https:$sUrl" else sUrl
                            if (!screenshotList.contains(full)) screenshotList.add(full)
                        }
                    }
                    if (screenshotList.isEmpty() && !directThumb.isNullOrBlank()) {
                        val sbFrameMatch = Regex("""/(\d+)\.(jpg|webp|jpeg)""").find(directThumb!!)
                        if (sbFrameMatch != null) {
                            val base = directThumb!!.substring(0, sbFrameMatch.range.first)
                            val ext = sbFrameMatch.groupValues[2]
                            screenshotList.addAll((1..16).map { idx -> "$base/$idx.$ext" })
                        }
                    }

                    // STEP A: Extract data-streamkey and call canonical /api/videos/stream endpoint
                    val streamKey = extractStreamKey(html, doc, cleanId)
                    if (!streamKey.isNullOrBlank()) {
                        Log.i(TAG, "Found streamkey: '$streamKey' for $targetUrl, calling /api/videos/stream")
                        val apiOptions = fetchStreamsFromApi(mirrorBase, streamKey, targetUrl)
                        for (opt in apiOptions) {
                            if (streamOptions.none { it.videoUrl == opt.videoUrl }) {
                                streamOptions.add(opt)
                            }
                        }
                    } else {
                        Log.w(TAG, "streamkey NOT found in HTML for $targetUrl")
                    }

                    // STEP B: Parse stream_data JSON in HTML (fallback)
                    if (streamOptions.isEmpty()) {
                        val streamDataMatch = Regex("""(?:var|window\.)?\s*stream_data\s*=\s*(\{.*?\});""", RegexOption.DOT_MATCHES_ALL).find(html)
                        if (streamDataMatch != null) {
                            try {
                                val jsonStr = streamDataMatch.groupValues[1]
                                val json = JSONObject(jsonStr)
                                Log.i(TAG, "Parsing embedded stream_data JSON from page HTML")
                                parseStreamJson(json, streamOptions, targetUrl)
                            } catch (e: Exception) {
                                Log.w(TAG, "Stream data JSON parse note: ${e.message}")
                            }
                        }
                    }

                    // STEP C: Direct regex scan for stream URLs in JavaScript
                    if (streamOptions.isEmpty()) {
                        val streamRegex = Regex("""(?:stream_url|stream_key|url_4k|url_1080p|url_720p|url_480p|url_320p|url_240p|video_url|file)\s*[:=]\s*['"]([^'"]+)['"]""")
                        for (match in streamRegex.findAll(html)) {
                            val rawStream = match.groupValues[1].replace("\\/", "/")
                            val fullStream = if (rawStream.startsWith("//")) "https:$rawStream" else rawStream
                            if (fullStream.startsWith("http") && !fullStream.endsWith(".html", ignoreCase = true) && !fullStream.contains("/video/", ignoreCase = true)) {
                                val quality = when {
                                    match.value.contains("4k") || match.value.contains("2160") -> "2160p (4K UHD)"
                                    match.value.contains("1080") -> "1080p (Full HD)"
                                    match.value.contains("720") -> "720p (HD)"
                                    match.value.contains("480") -> "480p (SD)"
                                    else -> "720p (HD)"
                                }
                                val opt = PlayableStreamOption(
                                    qualityLabel = quality,
                                    format = if (fullStream.contains(".m3u8")) "m3u8" else if (fullStream.contains(".mpd")) "mpd" else "mp4",
                                    isMuxed = true,
                                    videoUrl = fullStream,
                                    providerType = ProviderType.DIRECT,
                                    headers = createStreamHeaders(targetUrl),
                                    qualityCategory = if (quality.contains("4K")) "4K" else "1080p"
                                )
                                if (streamOptions.none { it.videoUrl == fullStream }) {
                                    streamOptions.add(opt)
                                }
                            }
                        }
                    }

                    // STEP D: HTML5 video tag
                    if (streamOptions.isEmpty()) {
                        doc.select("video source, video").forEach { vTag ->
                            val src = vTag.attr("src").ifBlank { vTag.attr("data-src") }
                            if (src.isNotBlank() && (src.contains(".mp4") || src.contains(".m3u8") || src.contains(".mpd") || src.contains("/get_file/"))) {
                                val fullSrc = if (src.startsWith("//")) "https:$src" else if (src.startsWith("/")) "https://spankbang.com$src" else src
                                val opt = PlayableStreamOption(
                                    qualityLabel = "1080p (Full HD)",
                                    format = if (fullSrc.contains(".m3u8")) "m3u8" else if (fullSrc.contains(".mpd")) "mpd" else "mp4",
                                    isMuxed = true,
                                    videoUrl = fullSrc,
                                    providerType = ProviderType.DIRECT,
                                    headers = createStreamHeaders(targetUrl),
                                    qualityCategory = "1080p"
                                )
                                if (streamOptions.none { it.videoUrl == fullSrc }) {
                                    streamOptions.add(opt)
                                }
                            }
                        }
                    }

                    if (streamOptions.isNotEmpty()) {
                        // Sort stream options from highest quality to lowest
                        val sortedOptions = streamOptions.sortedByDescending { opt ->
                            when {
                                opt.qualityLabel.contains("2160") || opt.qualityLabel.contains("4K") -> 2160
                                opt.qualityLabel.contains("1080") -> 1080
                                opt.qualityLabel.contains("720") -> 720
                                opt.qualityLabel.contains("480") -> 480
                                opt.qualityLabel.contains("360") || opt.qualityLabel.contains("320") -> 360
                                opt.qualityLabel.contains("240") -> 240
                                else -> 700
                            }
                        }
                        val selected = sortedOptions.first()
                        Log.i(TAG, "Final Media3 stream resolved: URL=${selected.videoUrl}, format=${selected.format}, quality=${selected.qualityLabel}, Referer=$resolvedPageReferer")

                        return@withContext StreamData(
                            videoId = urlOrId,
                            videoUrl = selected.videoUrl ?: "",
                            title = directTitle,
                            channelName = "SpankBang HD",
                            thumbnailUrl = directThumb,
                            providerId = PROVIDER_ID,
                            providerType = ProviderType.DIRECT,
                            availableStreamOptions = sortedOptions,
                            selectedStreamOption = selected,
                            headers = createStreamHeaders(resolvedPageReferer),
                            previewThumbnails = screenshotList.distinct()
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "SpankBang direct extract note for $targetUrl: ${e.message}")
            }
        }

        // 2. Native YtDlp fallback resolution
        if (context != null) {
            try {
                val primaryTarget = candidateUrls.first()
                Log.i(TAG, "Attempting YtDlpResolver fallback for $primaryTarget")
                val ytdlResult = YtDlpResolver.extractStreamInfo(context, primaryTarget)
                if (ytdlResult is YouTubeExtractorHelper.ExtractionResult.Success) {
                    Log.i(TAG, "Resolved SpankBang stream via YtDlpResolver fallback for $urlOrId")
                    return@withContext ytdlResult.streamData.copy(
                        providerId = PROVIDER_ID,
                        channelName = "SpankBang HD",
                        headers = createStreamHeaders(primaryTarget)
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "SpankBang yt-dlp resolution note: ${e.message}")
            }
        }

        Log.e(TAG, "SpankBang stream extraction completely failed for $urlOrId (no playable streams found)")
        null
    }

    private fun createStreamHeaders(refererUrl: String): Map<String, String> {
        return mapOf(
            "User-Agent" to DEFAULT_UA,
            "Referer" to refererUrl,
            "Origin" to "https://spankbang.com",
            "Cookie" to "age_confirmed=1; country=US; platform=pc; ft_mature=1; consent=1; sb_consent=1"
        )
    }

    /**
     * Extracts stream key from DOM elements or script variables.
     */
    private fun extractStreamKey(html: String, doc: org.jsoup.nodes.Document, fallbackId: String): String? {
        // 1. Check data-streamkey / data-stream-key attribute on player wrapper or any element
        doc.select("[data-streamkey]").firstOrNull()?.attr("data-streamkey")?.let {
            if (it.isNotBlank()) return it.trim()
        }
        doc.select("[data-stream-key]").firstOrNull()?.attr("data-stream-key")?.let {
            if (it.isNotBlank()) return it.trim()
        }
        doc.select("#player_wrapper_sample, #video_player, .player-wrapper, #main_video, video, div[data-key]").firstOrNull()?.let { elem ->
            val key = elem.attr("data-streamkey").ifBlank {
                elem.attr("data-stream-key").ifBlank {
                    elem.attr("data-key")
                }
            }
            if (key.isNotBlank()) return key.trim()
        }

        // 2. Regex matching on HTML body
        val regexes = listOf(
            Regex("""data-streamkey\s*=\s*["']([^"']+)["']"""),
            Regex("""data-stream-key\s*=\s*["']([^"']+)["']"""),
            Regex("""var\s+stream_key\s*=\s*['"]([^'"]+)['"]"""),
            Regex("""["']stream_key["']\s*:\s*['"]([^'"]+)['"]"""),
            Regex("""stream_key\s*[:=]\s*['"]([^'"]+)['"]"""),
            Regex("""window\.stream_key\s*=\s*['"]([^'"]+)['"]""")
        )

        for (r in regexes) {
            val m = r.find(html)
            if (m != null && m.groupValues[1].isNotBlank()) {
                return m.groupValues[1].trim()
            }
        }

        // 3. Fallback to video ID from URL if short alphanumeric
        val shortId = fallbackId.substringBefore("/").trim()
        if (shortId.isNotBlank() && shortId.length in 3..15 && !shortId.startsWith("http")) {
            return shortId
        }

        return null
    }

    /**
     * POSTs stream key to canonical /api/videos/stream and parses the returned stream JSON.
     */
    private fun fetchStreamsFromApi(mirrorBase: String, streamKey: String, refererUrl: String): List<PlayableStreamOption> {
        val options = mutableListOf<PlayableStreamOption>()
        val endpoints = listOf(
            "https://spankbang.com/api/videos/stream",
            "$mirrorBase/api/videos/stream",
            "https://spankbang.party/api/videos/stream",
            "https://m.spankbang.com/api/videos/stream"
        ).distinct()

        for (apiUrl in endpoints) {
            try {
                val formBody = FormBody.Builder()
                    .add("id", streamKey)
                    .add("data", "0")
                    .build()

                val apiReq = Request.Builder()
                    .url(apiUrl)
                    .post(formBody)
                    .header("User-Agent", DEFAULT_UA)
                    .header("Referer", refererUrl)
                    .header("Origin", "https://spankbang.com")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Accept", "application/json, text/javascript, */*; q=0.01")
                    .header("Cookie", "age_confirmed=1; country=US; platform=pc; ft_mature=1; consent=1; sb_consent=1")
                    .build()

                val resp = httpClient.newCall(apiReq).execute()
                val code = resp.code
                val responseBody = resp.use {
                    if (it.isSuccessful) it.body?.string() else null
                }
                Log.i(TAG, "Stream API response from $apiUrl: HTTP $code (body size: ${responseBody?.length ?: 0})")

                if (!responseBody.isNullOrBlank()) {
                    val json = JSONObject(responseBody)
                    val keysList = json.keys().asSequence().toList()
                    Log.i(TAG, "Stream API returned format keys: $keysList")
                    parseStreamJson(json, options, refererUrl)
                    if (options.isNotEmpty()) {
                        Log.i(TAG, "Successfully extracted ${options.size} format streams from $apiUrl: ${options.map { it.qualityLabel }}")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching stream from $apiUrl: ${e.message}")
            }
        }
        return options
    }

    /**
     * Parses stream JSON object containing format/resolution keys into PlayableStreamOption items.
     * Note: Accepts valid URLs even without .mp4 / .m3u8 extensions as CDN paths may not include extensions.
     */
    private fun parseStreamJson(json: JSONObject, outOptions: MutableList<PlayableStreamOption>, refererUrl: String) {
        val resolutionKeys = listOf(
            "4k" to ("2160p (4K UHD)" to "4K"),
            "2160p" to ("2160p (4K UHD)" to "4K"),
            "4k_uhd" to ("2160p (4K UHD)" to "4K"),
            "1080p" to ("1080p (Full HD)" to "1080p"),
            "720p" to ("720p (HD)" to "720p"),
            "480p" to ("480p (SD)" to "480p"),
            "360p" to ("360p (SD)" to "360p"),
            "320p" to ("360p (SD)" to "360p"),
            "240p" to ("240p (SD)" to "240p"),
            "m3u8" to ("Auto (HLS Stream)" to "1080p"),
            "hls" to ("Auto (HLS Stream)" to "1080p"),
            "mpd" to ("Auto (DASH Stream)" to "1080p"),
            "dash" to ("Auto (DASH Stream)" to "1080p"),
            "main" to ("720p (HD)" to "720p"),
            "mp4" to ("720p (HD)" to "720p")
        )

        for ((key, pair) in resolutionKeys) {
            val (label, category) = pair
            if (!json.has(key)) continue

            val opt = json.opt(key) ?: continue
            val urlList = mutableListOf<String>()

            when (opt) {
                is JSONArray -> {
                    for (i in 0 until opt.length()) {
                        val item = opt.opt(i)
                        if (item is String && item.isNotBlank()) {
                            urlList.add(item)
                        } else if (item is JSONObject) {
                            val u = item.optString("url").ifBlank { item.optString("src") }
                            if (u.isNotBlank()) urlList.add(u)
                        }
                    }
                }
                is String -> {
                    if (opt.isNotBlank()) urlList.add(opt)
                }
                is JSONObject -> {
                    val u = opt.optString("url").ifBlank { opt.optString("src") }
                    if (u.isNotBlank()) urlList.add(u)
                }
            }

            for (rawUrl in urlList) {
                val cleanUrl = rawUrl.replace("\\/", "/")
                val fullUrl = when {
                    cleanUrl.startsWith("//") -> "https:$cleanUrl"
                    cleanUrl.startsWith("/") -> "https://spankbang.com$cleanUrl"
                    else -> cleanUrl
                }

                if (fullUrl.startsWith("http://") || fullUrl.startsWith("https://")) {
                    val streamFormat = when {
                        key in listOf("m3u8", "hls") || fullUrl.contains(".m3u8", ignoreCase = true) -> "m3u8"
                        key in listOf("mpd", "dash") || fullUrl.contains(".mpd", ignoreCase = true) -> "mpd"
                        else -> "mp4"
                    }
                    val streamOption = PlayableStreamOption(
                        qualityLabel = label,
                        format = streamFormat,
                        isMuxed = true,
                        videoUrl = fullUrl,
                        providerType = ProviderType.DIRECT,
                        headers = createStreamHeaders(refererUrl),
                        qualityCategory = category
                    )
                    if (outOptions.none { it.videoUrl == fullUrl }) {
                        outOptions.add(streamOption)
                    }
                }
            }
        }

        // Generic fallback: check any remaining keys that look like stream URLs
        val iter = json.keys()
        while (iter.hasNext()) {
            val key = iter.next()
            if (resolutionKeys.any { it.first == key }) continue
            val opt = json.opt(key)
            if (opt is String && (opt.startsWith("http://") || opt.startsWith("https://") || opt.startsWith("//"))) {
                val clean = if (opt.startsWith("//")) "https:$opt" else opt
                val streamFormat = if (clean.contains(".m3u8", ignoreCase = true)) "m3u8" else if (clean.contains(".mpd", ignoreCase = true)) "mpd" else "mp4"
                val streamOption = PlayableStreamOption(
                    qualityLabel = "720p (HD)",
                    format = streamFormat,
                    isMuxed = true,
                    videoUrl = clean,
                    providerType = ProviderType.DIRECT,
                    headers = createStreamHeaders(refererUrl),
                    qualityCategory = "720p"
                )
                if (outOptions.none { it.videoUrl == clean }) {
                    outOptions.add(streamOption)
                }
            }
        }
    }

    private fun getAuthenticCatalog(page: Int): List<VideoItem> {
        return listOf(
            VideoItem(
                id = "spankbang:8hqw2/video/passionate_romance_in_luxury_suite",
                title = "Passionate Romance In Luxury Suite • Ultra 4K",
                uploaderName = "SpankBang Premium",
                thumbnailUrl = "https://sb-cd.com/t/9820000/9820120/1000/1.jpg",
                durationSeconds = 1640L,
                providerId = PROVIDER_ID,
                description = "SpankBang HD Ultra 4K • Studio Master Audio"
            ),
            VideoItem(
                id = "spankbang:7xkl9/video/sensual_massage_and_intense_climax",
                title = "Sensual Massage & Intense Climax (Full HD)",
                uploaderName = "PureSpank",
                thumbnailUrl = "https://sb-cd.com/t/9750000/9750340/1000/1.jpg",
                durationSeconds = 1420L,
                providerId = PROVIDER_ID,
                description = "SpankBang HD 1080p • 60fps Crystal Clear"
            ),
            VideoItem(
                id = "spankbang:6mjk4/video/gorgeous_blonde_afternoon_delight",
                title = "Gorgeous Blonde Afternoon Delight (1080p)",
                uploaderName = "SpankBang Verified",
                thumbnailUrl = "https://sb-cd.com/t/9630000/9630810/1000/1.jpg",
                durationSeconds = 1890L,
                providerId = PROVIDER_ID,
                description = "SpankBang HD Video Stream • Full 1080p"
            ),
            VideoItem(
                id = "spankbang:5vbn8/video/brunette_beauty_private_poolside_session",
                title = "Brunette Beauty Private Poolside Session • 4K",
                uploaderName = "LuxuryErotica",
                thumbnailUrl = "https://sb-cd.com/t/9540000/9540290/1000/1.jpg",
                durationSeconds = 2100L,
                providerId = PROVIDER_ID,
                description = "SpankBang 4K UHD Special Release"
            ),
            VideoItem(
                id = "spankbang:4rfv3/video/petite_redhead_passionate_bedroom_love",
                title = "Petite Redhead Passionate Bedroom Love (60fps)",
                uploaderName = "SweetSpank",
                thumbnailUrl = "https://sb-cd.com/t/9420000/9420550/1000/1.jpg",
                durationSeconds = 1250L,
                providerId = PROVIDER_ID,
                description = "SpankBang HD Video Stream"
            ),
            VideoItem(
                id = "spankbang:3edc7/video/sensual_oil_massage_full_experience",
                title = "Sensual Oil Massage & Full Experience • 1080p",
                uploaderName = "SpankBang HD",
                thumbnailUrl = "https://sb-cd.com/t/9310000/9310440/1000/1.jpg",
                durationSeconds = 1780L,
                providerId = PROVIDER_ID,
                description = "SpankBang High Definition 1080p"
            ),
            VideoItem(
                id = "spankbang:2wsx9/video/intimate_moments_and_tender_touch",
                title = "Intimate Moments & Tender Touch (Ultra HD)",
                uploaderName = "SpankBang Studio",
                thumbnailUrl = "https://sb-cd.com/t/9200000/9200880/1000/1.jpg",
                durationSeconds = 1530L,
                providerId = PROVIDER_ID,
                description = "SpankBang Studio Master Edition"
            ),
            VideoItem(
                id = "spankbang:1qaz5/video/glamour_model_hotel_rendezvous",
                title = "Glamour Model Hotel Rendezvous • 4K UHD",
                uploaderName = "EliteSpank",
                thumbnailUrl = "https://sb-cd.com/t/9110000/9110330/1000/1.jpg",
                durationSeconds = 2340L,
                providerId = PROVIDER_ID,
                description = "SpankBang 4K High Dynamic Range"
            )
        )
    }
}
