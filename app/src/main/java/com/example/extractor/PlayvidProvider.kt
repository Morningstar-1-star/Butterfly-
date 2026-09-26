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
import java.util.concurrent.TimeUnit

/**
 * Playvid Provider & Real Stream Extractor.
 * High-speed live video catalog parsing, search, and native MP4/HLS stream extraction
 * via flashvars, KVS deobfuscation, iframe embeds, and yt-dlp fallback.
 */
object PlayvidProvider {
    private const val TAG = "PlayvidProvider"
    const val PROVIDER_ID = "playvid"
    private const val BASE_URL = "https://www.playvids.com"

    private const val DEFAULT_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", DEFAULT_UA)
                .header("Referer", "$BASE_URL/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cookie", "age_confirmed=1; platform=pc; country=US; ft_mature=1; consent=1")
                .build()
            chain.proceed(req)
        }
        .build()

    private val defaultHeaders = mapOf(
        "User-Agent" to DEFAULT_UA,
        "Referer" to "$BASE_URL/",
        "Cookie" to "age_confirmed=1; platform=pc; country=US; ft_mature=1; consent=1"
    )

    suspend fun getHome(limit: Int = 24, page: Int = 1, context: Context? = null): List<VideoItem> = withContext(Dispatchers.IO) {
        val safePage = if (page < 1) 1 else page

        try {
            val liveItems = withTimeoutOrNull(12000L) {
                coroutineScope {
                    val tDef = async { parseHtml(if (safePage == 1) "$BASE_URL/top-rated" else "$BASE_URL/top-rated?page=$safePage", limit) }
                    val pDef = async { parseHtml(if (safePage == 1) "$BASE_URL/most-popular" else "$BASE_URL/most-popular?page=$safePage", limit) }
                    val lDef = async { parseHtml(if (safePage == 1) "$BASE_URL/latest-updates" else "$BASE_URL/latest-updates?page=$safePage", limit) }
                    val vDef = async { parseHtml(if (safePage == 1) "$BASE_URL/videos" else "$BASE_URL/videos?page=$safePage", limit) }
                    val rDef = async { parseHtml(if (safePage == 1) "$BASE_URL/" else "$BASE_URL/?page=$safePage", limit) }

                    val tRes = tDef.await()
                    if (tRes.isNotEmpty()) return@coroutineScope tRes
                    val pRes = pDef.await()
                    if (pRes.isNotEmpty()) return@coroutineScope pRes
                    val lRes = lDef.await()
                    if (lRes.isNotEmpty()) return@coroutineScope lRes
                    val vRes = vDef.await()
                    if (vRes.isNotEmpty()) return@coroutineScope vRes
                    val rRes = rDef.await()
                    if (rRes.isNotEmpty()) return@coroutineScope rRes
                    emptyList<VideoItem>()
                }
            }

            if (!liveItems.isNullOrEmpty()) {
                Log.i(TAG, "Playvid getHome page $safePage fetched ${liveItems.size} live videos")
                return@withContext liveItems.take(limit)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Playvid live getHome error: ${e.message}")
        }

        emptyList()
    }

    suspend fun search(query: String, limit: Int = 24, page: Int = 1, context: Context? = null): List<VideoItem> = withContext(Dispatchers.IO) {
        val clean = query.replace(Regex("(?i)playvid:"), "").trim()
        if (clean.isBlank()) return@withContext getHome(limit, page, context)
        val safePage = if (page < 1) 1 else page
        val encoded = URLEncoder.encode(clean, "UTF-8")

        try {
            val liveSearch = withTimeoutOrNull(12000L) {
                val searchUrl = if (safePage == 1) "$BASE_URL/search?q=$encoded" else "$BASE_URL/search?q=$encoded&page=$safePage"
                val res = parseHtml(searchUrl, limit)
                if (res.isNotEmpty()) res else {
                    val altSearchUrl = if (safePage == 1) "$BASE_URL/search/video?q=$encoded" else "$BASE_URL/search/video?q=$encoded&page=$safePage"
                    parseHtml(altSearchUrl, limit)
                }
            }

            if (!liveSearch.isNullOrEmpty()) {
                Log.i(TAG, "Playvid search '$clean' fetched ${liveSearch.size} live videos")
                return@withContext liveSearch.take(limit)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Playvid live search error: ${e.message}")
        }

        emptyList()
    }

    private fun parseHtml(url: String, limit: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        val seenIds = mutableSetOf<String>()

        try {
            val req = Request.Builder()
                .url(url)
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return emptyList()

            val doc = Jsoup.parse(html)
            
            // Search broadly across all anchor tags linking to watch, video, v, or playvids pages
            val linkElems = doc.select("a[href*='/v/'], a[href*='/watch/'], a[href*='/video/'], a[href*='/videos/'], a[href*='playvids.com/'], a[href*='playvid.com/']")

            for (linkElem in linkElems) {
                if (list.size >= limit) break

                val rawHref = linkElem.attr("href").trim()
                if (rawHref.isBlank() || rawHref == "/" || rawHref.contains("/search") || rawHref.contains("/categories") || rawHref.contains("/channels") || rawHref.contains("/tags")) continue

                val fullUrl = when {
                    rawHref.startsWith("http://") || rawHref.startsWith("https://") -> rawHref
                    rawHref.startsWith("//") -> "https:$rawHref"
                    rawHref.startsWith("/") -> "$BASE_URL$rawHref"
                    else -> "$BASE_URL/$rawHref"
                }

                val videoId = fullUrl
                    .substringAfter("playvids.com/")
                    .substringAfter("playvid.com/")
                    .trim('/')
                if (videoId.isBlank() || seenIds.contains(videoId)) continue

                // Find parent block/card to extract rich thumbnail, duration, uploader
                val container = linkElem.parents().firstOrNull { p ->
                    p.hasClass("video-item") || p.hasClass("thumb-block") || p.hasClass("item") ||
                            p.hasClass("thumb_block") || p.tagName() == "article" || p.hasClass("grid-item") ||
                            p.hasAttr("data-video-id") || p.hasClass("video-card") || p.hasClass("box")
                } ?: linkElem.parent() ?: linkElem

                val imgElem = container.selectFirst("img") ?: linkElem.selectFirst("img")
                val rawThumb = imgElem?.let {
                    it.attr("data-src").ifBlank {
                        it.attr("data-original").ifBlank {
                            it.attr("data-preview").ifBlank {
                                it.attr("data-thumb").ifBlank {
                                    it.attr("data-poster").ifBlank {
                                        it.attr("data-webp").ifBlank {
                                            it.attr("data-lazy").ifBlank { it.attr("src") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val thumb = when {
                    rawThumb.isNullOrBlank() -> null
                    rawThumb.startsWith("//") -> "https:$rawThumb"
                    rawThumb.startsWith("/") -> "$BASE_URL$rawThumb"
                    else -> rawThumb
                }

                val title = container.selectFirst(".title, .video-title, .item-title, h3, h4, a[title], img[alt]")?.let {
                    it.attr("title").ifBlank { it.attr("alt").ifBlank { it.text() } }
                }?.trim()?.ifBlank { linkElem.attr("title").ifBlank { linkElem.text() } }?.trim() ?: "Playvid Video"

                if (title.isBlank() || title.length < 2 || title.equals("Playvid", ignoreCase = true) || title.equals("Playvids", ignoreCase = true)) continue

                val durationText = container.selectFirst(".duration, .time, .d, .thumb__duration, .badge, .duration-badge")?.text()?.trim()
                val durationSec = durationText?.let { parseDuration(it) } ?: -1L
                val uploader = container.selectFirst(".uploader, .channel, .author, .user")?.text()?.trim() ?: "Playvid HD"

                seenIds.add(videoId)

                val item = VideoItem(
                    id = "playvid:$videoId",
                    title = title,
                    uploaderName = uploader,
                    thumbnailUrl = thumb,
                    durationSeconds = durationSec,
                    providerId = PROVIDER_ID,
                    description = "Playvid HD Stream"
                )
                list.add(item)
            }

            // Regex fallback if Jsoup selection produced no items
            if (list.isEmpty()) {
                val hrefRegex = Regex("""href=["']((?:https?://(?:www\.)?playvid(?:s)?\.com)?/(?:v|watch|video|videos)/[^"']+)["']""", RegexOption.IGNORE_CASE)
                hrefRegex.findAll(html).forEach { match ->
                    if (list.size >= limit) return@forEach
                    val matchedHref = match.groupValues[1]
                    val fullUrl = when {
                        matchedHref.startsWith("http") -> matchedHref
                        matchedHref.startsWith("//") -> "https:$matchedHref"
                        else -> "$BASE_URL/${matchedHref.trimStart('/')}"
                    }
                    val videoId = fullUrl.substringAfter("playvids.com/").substringAfter("playvid.com/").trim('/')
                    if (videoId.isNotBlank() && seenIds.add(videoId)) {
                        list.add(
                            VideoItem(
                                id = "playvid:$videoId",
                                title = "Playvid Video HD",
                                uploaderName = "Playvid HD",
                                thumbnailUrl = null,
                                durationSeconds = -1L,
                                providerId = PROVIDER_ID,
                                description = "Playvid HD Stream"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Playvid HTML for $url: ${e.message}")
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
        val cleanId = urlOrId.removePrefix("playvid:").trim('/')

        val candidateUrls = mutableListOf<String>()
        if (urlOrId.startsWith("http://") || urlOrId.startsWith("https://")) {
            candidateUrls.add(urlOrId)
        } else if (cleanId.startsWith("http://") || cleanId.startsWith("https://")) {
            candidateUrls.add(cleanId)
        } else if (cleanId.startsWith("v/") || cleanId.startsWith("watch/") || cleanId.startsWith("video/") || cleanId.startsWith("videos/")) {
            candidateUrls.add("$BASE_URL/$cleanId")
            candidateUrls.add("https://www.playvid.com/$cleanId")
        } else {
            candidateUrls.add("$BASE_URL/v/$cleanId")
            candidateUrls.add("$BASE_URL/watch/$cleanId")
            candidateUrls.add("$BASE_URL/video/$cleanId")
            candidateUrls.add("$BASE_URL/$cleanId")
        }

        var targetUrl = candidateUrls.first()
        var directTitle = "Playvid HD Video"
        var directThumb: String? = null
        var iframeEmbedUrl: String? = null

        val availableOptions = mutableListOf<PlayableStreamOption>()

        // 1. Fetch main video page and extract flashvars & direct stream URLs
        for (candidate in candidateUrls) {
            try {
                val req = Request.Builder()
                    .url(candidate)
                    .build()

                val html = httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }

                if (!html.isNullOrBlank()) {
                    targetUrl = candidate
                    val doc = Jsoup.parse(html)
                    doc.selectFirst("h1, meta[property='og:title']")?.let {
                        val t = it.attr("content").ifBlank { it.text() }.trim()
                        if (t.isNotBlank()) directTitle = t
                    }

                    directThumb = doc.selectFirst("meta[property='og:image']")?.attr("content")?.let {
                        if (it.startsWith("//")) "https:$it" else if (it.startsWith("/")) "$BASE_URL$it" else it
                    }

                    // Check for iframe player URL in page
                    doc.selectFirst("iframe[src*='embed']")?.attr("src")?.let { embedSrc ->
                        iframeEmbedUrl = when {
                            embedSrc.startsWith("//") -> "https:$embedSrc"
                            embedSrc.startsWith("/") -> "$BASE_URL$embedSrc"
                            else -> embedSrc
                        }
                    }

                    // Extract flashvars parameters
                    val flashvars = KvsFlashvarsDecoder.parseFlashvars(html)
                    val licenseCode = flashvars["license_code"]

                    // Parse stream URLs from flashvars
                    val streamFromFV = extractStreamsFromFlashvars(flashvars, licenseCode)
                    availableOptions.addAll(streamFromFV)

                    // If flashvars didn't yield options, parse raw regex matches from page HTML
                    if (availableOptions.isEmpty()) {
                        val rawOptions = extractStreamsFromRawHtml(html, licenseCode)
                        availableOptions.addAll(rawOptions)
                    }

                    if (availableOptions.isNotEmpty() || !directTitle.contains("Playvid HD")) {
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Playvid candidate $candidate extract note: ${e.message}")
            }
        }

        // 2. Fetch iframe embed page if needed
        if (availableOptions.isEmpty()) {
            val embedUrl = iframeEmbedUrl ?: if (targetUrl.contains("/embed/")) targetUrl else "$BASE_URL/embed/$cleanId"
            try {
                val req = Request.Builder()
                    .url(embedUrl)
                    .build()

                val embedHtml = httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }

                if (!embedHtml.isNullOrBlank()) {
                    val embedFlashvars = KvsFlashvarsDecoder.parseFlashvars(embedHtml)
                    val licenseCode = embedFlashvars["license_code"]
                    val embedStreams = extractStreamsFromFlashvars(embedFlashvars, licenseCode)
                    availableOptions.addAll(embedStreams)

                    if (availableOptions.isEmpty()) {
                        val rawEmbedStreams = extractStreamsFromRawHtml(embedHtml, licenseCode)
                        availableOptions.addAll(rawEmbedStreams)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Playvid embed page extract note: ${e.message}")
            }
        }

        // 3. Return native streams if found
        if (availableOptions.isNotEmpty()) {
            val primaryOption = availableOptions.first()
            return@withContext StreamData(
                videoId = urlOrId,
                videoUrl = primaryOption.videoUrl ?: targetUrl,
                title = directTitle,
                channelName = "Playvid HD",
                thumbnailUrl = directThumb,
                providerId = PROVIDER_ID,
                providerType = primaryOption.providerType,
                availableStreamOptions = availableOptions,
                selectedStreamOption = primaryOption,
                headers = defaultHeaders
            )
        }

        // 4. Fallback to native yt-dlp resolution
        if (context != null) {
            try {
                val ytdlResult = YtDlpResolver.extractStreamInfo(context, targetUrl)
                if (ytdlResult is YouTubeExtractorHelper.ExtractionResult.Success) {
                    return@withContext ytdlResult.streamData.copy(
                        providerId = PROVIDER_ID,
                        title = directTitle.ifBlank { ytdlResult.streamData.title },
                        channelName = "Playvid HD",
                        thumbnailUrl = directThumb ?: ytdlResult.streamData.thumbnailUrl
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Playvid yt-dlp fallback note: ${e.message}")
            }
        }

        // 5. Fallback to Playvid Web Embed Player
        val embedUrl = iframeEmbedUrl ?: if (targetUrl.contains("/embed/")) targetUrl else "$BASE_URL/embed/$cleanId"
        val embedOption = PlayableStreamOption(
            qualityLabel = "Playvid Web Player (HD)",
            format = "embed",
            isMuxed = true,
            videoUrl = embedUrl,
            providerType = ProviderType.EMBED,
            headers = defaultHeaders,
            qualityCategory = "1080p"
        )

        StreamData(
            videoId = urlOrId,
            videoUrl = embedUrl,
            title = directTitle,
            channelName = "Playvid HD",
            thumbnailUrl = directThumb,
            providerId = PROVIDER_ID,
            providerType = ProviderType.EMBED,
            availableStreamOptions = listOf(embedOption),
            selectedStreamOption = embedOption,
            headers = defaultHeaders
        )
    }

    private fun extractStreamsFromFlashvars(flashvars: Map<String, String>, licenseCode: String?): List<PlayableStreamOption> {
        val options = mutableListOf<PlayableStreamOption>()
        val seenUrls = mutableSetOf<String>()

        // Check video_urls][1080p], video_urls][720p], etc.
        for ((key, value) in flashvars) {
            if (value.isBlank() || !value.contains("http") && !value.contains("function/")) continue

            val lowerKey = key.lowercase()
            if (lowerKey.contains("video_url") || lowerKey.contains("video_alt_url") || lowerKey.contains("file")) {
                val decodedUrl = KvsFlashvarsDecoder.decodeKvsUrl(value, licenseCode)
                val fullUrl = if (decodedUrl.startsWith("//")) "https:$decodedUrl" else decodedUrl

                if (fullUrl.startsWith("http") && !seenUrls.contains(fullUrl)) {
                    seenUrls.add(fullUrl)
                    val quality = when {
                        lowerKey.contains("1080") -> "1080p Full HD"
                        lowerKey.contains("720") -> "720p HD"
                        lowerKey.contains("480") -> "480p SD"
                        lowerKey.contains("360") -> "360p"
                        lowerKey.contains("alt") -> "720p HD (Alt)"
                        fullUrl.contains(".m3u8") -> "Auto HLS"
                        else -> "1080p HD"
                    }
                    val isHls = fullUrl.contains(".m3u8")

                    options.add(
                        PlayableStreamOption(
                            qualityLabel = quality,
                            format = if (isHls) "m3u8" else "mp4",
                            isMuxed = true,
                            videoUrl = fullUrl,
                            providerType = ProviderType.DIRECT,
                            headers = defaultHeaders,
                            sourceName = "Playvid Direct"
                        )
                    )
                }
            }
        }

        return options
    }

    private fun extractStreamsFromRawHtml(html: String, licenseCode: String?): List<PlayableStreamOption> {
        val options = mutableListOf<PlayableStreamOption>()
        val seenUrls = mutableSetOf<String>()

        val patterns = listOf(
            Regex("""(?:video_url|videoUrl|stream_url|file|video_src|source_url)\s*[:=]\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE),
            Regex("""<source[^>]+src=['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE),
            Regex("""["'](https?:\/\/[^"']+\.(?:mp4|m3u8)[^"']*)["']""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.findAll(html).forEach { match ->
                val rawUrl = match.groupValues[1]
                val decoded = KvsFlashvarsDecoder.decodeKvsUrl(rawUrl, licenseCode)
                val fullUrl = if (decoded.startsWith("//")) "https:$decoded" else decoded

                if (fullUrl.startsWith("http") && !seenUrls.contains(fullUrl)) {
                    val lower = fullUrl.lowercase()
                    if (lower.contains(".mp4") || lower.contains(".m3u8") || lower.contains("/get_file/")) {
                        seenUrls.add(fullUrl)
                        val isHls = lower.contains(".m3u8")
                        options.add(
                            PlayableStreamOption(
                                qualityLabel = if (isHls) "Auto HLS" else "1080p HD",
                                format = if (isHls) "m3u8" else "mp4",
                                isMuxed = true,
                                videoUrl = fullUrl,
                                providerType = ProviderType.DIRECT,
                                headers = defaultHeaders,
                                sourceName = "Playvid Direct"
                            )
                        )
                    }
                }
            }
        }

        return options
    }
}
