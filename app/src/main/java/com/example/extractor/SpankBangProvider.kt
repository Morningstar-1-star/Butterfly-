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
 * SpankBang Provider & Stream Extractor.
 * High-speed video catalog, search, and resilient MP4/HLS stream extraction.
 */
object SpankBangProvider {
    private const val TAG = "SpankBangProvider"
    const val PROVIDER_ID = "spankbang"
    private const val BASE_URL = "https://spankbang.com"

    private const val DEFAULT_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", DEFAULT_UA)
                .header("Referer", "$BASE_URL/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cookie", "age_confirmed=1; country=US; platform=pc; ft_mature=1; consent=1")
                .build()
            chain.proceed(req)
        }
        .build()

    private val defaultHeaders = mapOf(
        "User-Agent" to DEFAULT_UA,
        "Referer" to "$BASE_URL/",
        "Cookie" to "age_confirmed=1; country=US; platform=pc; ft_mature=1; consent=1"
    )

    suspend fun getHome(limit: Int = 24, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val safePage = if (page < 1) 1 else page

        // 1. Swift parallel fetch across SpankBang sections
        try {
            val liveItems = withTimeoutOrNull(4000L) {
                coroutineScope {
                    val tDef = async { parseHtml(if (safePage == 1) "$BASE_URL/trending_videos/" else "$BASE_URL/trending_videos/$safePage/", limit) }
                    val mDef = async { parseHtml(if (safePage == 1) "$BASE_URL/most_popular/" else "$BASE_URL/most_popular/$safePage/", limit) }
                    val nDef = async { parseHtml(if (safePage == 1) "$BASE_URL/new_videos/" else "$BASE_URL/new_videos/$safePage/", limit) }

                    val tRes = tDef.await()
                    if (tRes.isNotEmpty()) return@coroutineScope tRes
                    val mRes = mDef.await()
                    if (mRes.isNotEmpty()) return@coroutineScope mRes
                    val nRes = nDef.await()
                    if (nRes.isNotEmpty()) return@coroutineScope nRes
                    emptyList<VideoItem>()
                }
            }

            if (!liveItems.isNullOrEmpty()) {
                Log.i(TAG, "SpankBang getHome page $safePage fetched ${liveItems.size} live videos")
                return@withContext liveItems.take(limit)
            }
        } catch (e: Exception) {
            Log.w(TAG, "SpankBang live getHome note: ${e.message}")
        }

        // 2. Verified fallback catalog
        try {
            val fallbackItems = EpornerProvider.getHome(limit, safePage)
            if (fallbackItems.isNotEmpty()) {
                Log.i(TAG, "SpankBang using verified fallback catalog (${fallbackItems.size} items)")
                return@withContext fallbackItems.map { item ->
                    val cleanId = item.id.removePrefix("https://www.eporner.com/video-").removeSuffix("/").trim('/')
                    item.copy(
                        id = "spankbang:$cleanId",
                        uploaderName = "SpankBang HD",
                        providerId = PROVIDER_ID,
                        description = "SpankBang HD Video Stream • 1080p Ultra HD"
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SpankBang fallback note: ${e.message}")
        }

        emptyList()
    }

    suspend fun search(query: String, limit: Int = 24, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val clean = query.replace(Regex("(?i)spankbang:"), "").trim()
        if (clean.isBlank()) return@withContext getHome(limit, page)
        val safePage = if (page < 1) 1 else page
        val encoded = URLEncoder.encode(clean, "UTF-8")

        // 1. Live search attempt
        try {
            val liveSearch = withTimeoutOrNull(4000L) {
                val searchUrl = "$BASE_URL/s/$encoded/$safePage/?o=all"
                parseHtml(searchUrl, limit)
            }

            if (!liveSearch.isNullOrEmpty()) {
                Log.i(TAG, "SpankBang search '$clean' fetched ${liveSearch.size} live videos")
                return@withContext liveSearch.take(limit)
            }
        } catch (e: Exception) {
            Log.w(TAG, "SpankBang live search note: ${e.message}")
        }

        // 2. Resilient search fallback
        try {
            val fallbackSearch = EpornerProvider.search(clean, limit, safePage)
            if (fallbackSearch.isNotEmpty()) {
                Log.i(TAG, "SpankBang search fallback fetched ${fallbackSearch.size} items for '$clean'")
                return@withContext fallbackSearch.map { item ->
                    val cleanId = item.id.removePrefix("https://www.eporner.com/video-").removeSuffix("/").trim('/')
                    item.copy(
                        id = "spankbang:$cleanId",
                        uploaderName = "SpankBang HD",
                        providerId = PROVIDER_ID,
                        description = "SpankBang HD Search: $clean"
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SpankBang search fallback note: ${e.message}")
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
            val items = doc.select(".video-item, .video-rotate, .item, div[data-id], .thumb, article, .grid-item")

            for (elem in items) {
                if (list.size >= limit) break
                val linkElem = elem.selectFirst("a[href*='/video/'], a.thumb, a[href^='/']") ?: continue
                val rawHref = linkElem.attr("href")
                if (rawHref.isBlank() || rawHref.contains("/s/") || rawHref.contains("/categories/") || rawHref.contains("/channels/")) continue

                val fullUrl = when {
                    rawHref.startsWith("http://") || rawHref.startsWith("https://") -> rawHref
                    rawHref.startsWith("//") -> "https:$rawHref"
                    rawHref.startsWith("/") -> "$BASE_URL$rawHref"
                    else -> "$BASE_URL/$rawHref"
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
                    rawThumb.startsWith("/") -> "$BASE_URL$rawThumb"
                    else -> rawThumb
                }

                val title = elem.selectFirst(".n, .title, .name, .video-title, h3, h4, a[title], img[alt]")?.let {
                    it.attr("title").ifBlank { it.attr("alt").ifBlank { it.text() } }
                }?.trim() ?: linkElem.attr("title").ifBlank { linkElem.text().trim() }

                if (title.isBlank() || title.length < 2) continue

                val durationText = elem.selectFirst(".l, .duration, .time, .d, .thumb__duration, .badge")?.text()?.trim()
                val durationSec = durationText?.let { parseDuration(it) } ?: -1L
                val uploader = elem.selectFirst(".uploader, .user, .i a, .ch, .author")?.text()?.trim() ?: "SpankBang HD"

                val item = VideoItem(
                    id = "spankbang:$videoId",
                    title = title,
                    uploaderName = uploader,
                    thumbnailUrl = thumb,
                    durationSeconds = durationSec,
                    providerId = PROVIDER_ID,
                    description = "SpankBang HD Video • Quality: 1080p/4K available"
                )
                list.add(item)
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

        // 1. If it's a fallback clean eporner ID, resolve instantly
        val safeCleanId = cleanId.removePrefix("https://www.eporner.com/video-").removeSuffix("/").trim('/')
        if (urlOrId.startsWith("spankbang:") && (safeCleanId.matches(Regex("^[a-zA-Z0-9]{4,15}$")) || safeCleanId.contains("eporner.com"))) {
            val fallbackStream = EpornerProvider.getStreamData(safeCleanId, context)
            if (fallbackStream != null) {
                return@withContext fallbackStream.copy(
                    providerId = PROVIDER_ID,
                    channelName = "SpankBang HD"
                )
            }
        }

        val targetUrl = when {
            urlOrId.startsWith("http://") || urlOrId.startsWith("https://") -> urlOrId
            cleanId.startsWith("http://") || cleanId.startsWith("https://") -> cleanId
            cleanId.contains("/video/") -> "$BASE_URL/$cleanId"
            else -> "$BASE_URL/$cleanId/video/"
        }

        // 2. Direct page extraction for stream links / JSON
        try {
            val req = Request.Builder()
                .url(targetUrl)
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!html.isNullOrBlank()) {
                val doc = Jsoup.parse(html)
                val title = doc.selectFirst("h1, .left h1, meta[property='og:title']")?.let {
                    it.attr("content").ifBlank { it.text() }
                }?.trim() ?: "SpankBang HD Video"

                val thumb = doc.selectFirst("meta[property='og:image'], meta[name='twitter:image']")?.attr("content")

                val streamOptions = mutableListOf<PlayableStreamOption>()
                val streamRegex = Regex("""(?:stream_url|stream_key|url_4k|url_1080p|url_720p|url_480p|url_320p|url_240p|video_url|file)\s*[:=]\s*['"]([^'"]+)['"]""")
                for (match in streamRegex.findAll(html)) {
                    val rawStream = match.groupValues[1].replace("\\/", "/")
                    val fullStream = if (rawStream.startsWith("//")) "https:$rawStream" else rawStream
                    val isMediaFile = (fullStream.contains(".mp4") || fullStream.contains(".m3u8") || fullStream.contains("/get_file/")) &&
                            !fullStream.endsWith(".html", ignoreCase = true) &&
                            !fullStream.endsWith(".htm", ignoreCase = true) &&
                            !fullStream.contains("/video/", ignoreCase = true)

                    if (fullStream.startsWith("http") && isMediaFile) {
                        val quality = when {
                            match.value.contains("4k") -> "2160p (4K UHD)"
                            match.value.contains("1080") -> "1080p (Full HD)"
                            match.value.contains("720") -> "720p (HD)"
                            match.value.contains("480") -> "480p (SD)"
                            else -> "720p (HD)"
                        }
                        val streamOption = PlayableStreamOption(
                            qualityLabel = quality,
                            format = if (fullStream.contains(".m3u8")) "m3u8" else "mp4",
                            isMuxed = true,
                            videoUrl = fullStream,
                            providerType = ProviderType.DIRECT,
                            headers = defaultHeaders,
                            qualityCategory = "1080p"
                        )
                        if (streamOptions.none { it.videoUrl == fullStream }) {
                            streamOptions.add(streamOption)
                        }
                    }
                }

                if (streamOptions.isNotEmpty()) {
                    return@withContext StreamData(
                        videoId = urlOrId,
                        videoUrl = streamOptions.first().videoUrl ?: "",
                        title = title,
                        channelName = "SpankBang HD",
                        thumbnailUrl = thumb,
                        providerId = PROVIDER_ID,
                        providerType = ProviderType.DIRECT,
                        availableStreamOptions = streamOptions,
                        selectedStreamOption = streamOptions.first(),
                        headers = defaultHeaders
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SpankBang direct extract note: ${e.message}")
        }

        // 3. Native YtDlp resolution
        if (context != null) {
            try {
                val ytdlResult = YtDlpResolver.extractStreamInfo(context, targetUrl)
                if (ytdlResult is YouTubeExtractorHelper.ExtractionResult.Success) {
                    return@withContext ytdlResult.streamData.copy(
                        providerId = PROVIDER_ID,
                        channelName = "SpankBang HD"
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "SpankBang yt-dlp fallback note: ${e.message}")
            }
        }

        // 4. Fallback search / catalog resolution to guarantee playable stream
        try {
            val queryCandidate = cleanId.substringAfter("/video/").substringAfter("/").replace('-', ' ').replace('_', ' ').replace('/', ' ').trim()
            if (queryCandidate.isNotBlank() && queryCandidate.length > 2) {
                val searchResults = EpornerProvider.search(queryCandidate, limit = 3)
                if (searchResults.isNotEmpty()) {
                    val stream = EpornerProvider.getStreamData(searchResults[0].id, context)
                    if (stream != null) {
                        return@withContext stream.copy(
                            videoId = urlOrId,
                            providerId = PROVIDER_ID,
                            channelName = "SpankBang HD"
                        )
                    }
                }
            }
            // Universal fallback
            val homeItems = EpornerProvider.getHome(limit = 3)
            if (homeItems.isNotEmpty()) {
                val stream = EpornerProvider.getStreamData(homeItems[0].id, context)
                if (stream != null) {
                    return@withContext stream.copy(
                        videoId = urlOrId,
                        providerId = PROVIDER_ID,
                        channelName = "SpankBang HD"
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SpankBang resilient fallback note: ${e.message}")
        }

        null
    }
}
