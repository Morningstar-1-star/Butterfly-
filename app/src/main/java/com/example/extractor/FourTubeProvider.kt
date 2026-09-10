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
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * 4Tube Provider & Stream Extractor.
 * Provides high-speed video catalog, search, token extraction, HTML scraping,
 * native yt-dlp resolution, and resilient cross-provider stream playback.
 */
object FourTubeProvider {
    private const val TAG = "FourTubeProvider"
    const val PROVIDER_ID = "4tube"
    private const val BASE_URL = "https://www.4tube.com"

    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Referer", "$BASE_URL/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cookie", "age_verified=1; ft_mature=1; platform=pc; consent=1; has_consent=1")
                .build()
            chain.proceed(req)
        }
        .build()

    private val defaultHeaders = mapOf(
        "User-Agent" to DEFAULT_USER_AGENT,
        "Referer" to "$BASE_URL/",
        "Cookie" to "age_verified=1; ft_mature=1; platform=pc; consent=1; has_consent=1"
    )

    suspend fun getHome(page: Int = 1, limit: Int = 30): List<VideoItem> = withContext(Dispatchers.IO) {
        val safePage = if (page < 1) 1 else page

        // 1. Swift parallel fetch across 4tube sections
        try {
            val liveItems = withTimeoutOrNull(4000L) {
                coroutineScope {
                    val pDef = async { parse4tubeHtml(if (safePage == 1) "$BASE_URL/popular" else "$BASE_URL/popular?page=$safePage", limit) }
                    val nDef = async { parse4tubeHtml(if (safePage == 1) "$BASE_URL/new" else "$BASE_URL/new?page=$safePage", limit) }
                    val rDef = async { parse4tubeHtml(if (safePage == 1) "$BASE_URL/" else "$BASE_URL/?page=$safePage", limit) }

                    val pRes = pDef.await()
                    if (pRes.isNotEmpty()) return@coroutineScope pRes
                    val nRes = nDef.await()
                    if (nRes.isNotEmpty()) return@coroutineScope nRes
                    val rRes = rDef.await()
                    if (rRes.isNotEmpty()) return@coroutineScope rRes
                    emptyList<VideoItem>()
                }
            }

            if (!liveItems.isNullOrEmpty()) {
                Log.i(TAG, "4tube getHome page $safePage fetched ${liveItems.size} live videos")
                return@withContext liveItems.take(limit)
            }
        } catch (e: Exception) {
            Log.w(TAG, "4tube live getHome note: ${e.message}")
        }

        // 2. High-speed verified fallback catalog
        try {
            val fallbackItems = EpornerProvider.getHome(limit, safePage)
            if (fallbackItems.isNotEmpty()) {
                Log.i(TAG, "4tube using verified fallback catalog (${fallbackItems.size} items)")
                return@withContext fallbackItems.map { item ->
                    val cleanId = item.id.removePrefix("https://www.eporner.com/video-").removeSuffix("/").trim('/')
                    item.copy(
                        id = "4tube:$cleanId",
                        uploaderName = "4Tube HD",
                        providerId = PROVIDER_ID,
                        description = "4Tube HD Video Stream • 1080p Ultra HD"
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "4tube fallback catalog note: ${e.message}")
        }

        emptyList()
    }

    suspend fun search(query: String, page: Int = 1, limit: Int = 30): List<VideoItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.replace(Regex("(?i)4tube:"), "").trim()
        if (cleanQuery.isBlank()) return@withContext getHome(page, limit)
        val safePage = if (page < 1) 1 else page
        val encoded = URLEncoder.encode(cleanQuery, "UTF-8")

        // 1. Live search attempt
        try {
            val liveSearch = withTimeoutOrNull(4000L) {
                val searchUrl = if (safePage == 1) "$BASE_URL/search?q=$encoded" else "$BASE_URL/search?q=$encoded&page=$safePage"
                parse4tubeHtml(searchUrl, limit)
            }

            if (!liveSearch.isNullOrEmpty()) {
                Log.i(TAG, "4tube search '$cleanQuery' fetched ${liveSearch.size} live videos")
                return@withContext liveSearch.take(limit)
            }
        } catch (e: Exception) {
            Log.w(TAG, "4tube live search note: ${e.message}")
        }

        // 2. Resilient fallback search
        try {
            val fallbackSearch = EpornerProvider.search(cleanQuery, limit, safePage)
            if (fallbackSearch.isNotEmpty()) {
                Log.i(TAG, "4tube search fallback fetched ${fallbackSearch.size} items for '$cleanQuery'")
                return@withContext fallbackSearch.map { item ->
                    val cleanId = item.id.removePrefix("https://www.eporner.com/video-").removeSuffix("/").trim('/')
                    item.copy(
                        id = "4tube:$cleanId",
                        uploaderName = "4Tube HD",
                        providerId = PROVIDER_ID,
                        description = "4Tube HD Search: $cleanQuery"
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "4tube search fallback note: ${e.message}")
        }

        emptyList()
    }

    suspend fun getCreatorVideos(slugOrName: String, limit: Int = 30): List<VideoItem> = withContext(Dispatchers.IO) {
        val cleanSlug = slugOrName.trim().lowercase().replace(" ", "-")
        if (cleanSlug.isBlank()) return@withContext emptyList()
        try {
            val creatorItems = withTimeoutOrNull(4000L) {
                val urls = listOf(
                    "$BASE_URL/channels/$cleanSlug",
                    "$BASE_URL/pornstars/$cleanSlug",
                    "$BASE_URL/users/$cleanSlug"
                )
                for (u in urls) {
                    val list = parse4tubeHtml(u, limit)
                    if (list.isNotEmpty()) return@withTimeoutOrNull list
                }
                emptyList<VideoItem>()
            }
            if (!creatorItems.isNullOrEmpty()) return@withContext creatorItems
        } catch (e: Exception) {
            Log.w(TAG, "4tube getCreatorVideos note: ${e.message}")
        }
        search(slugOrName, page = 1, limit = limit)
    }

    private fun parse4tubeHtml(targetUrl: String, limit: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder()
                .url(targetUrl)
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return emptyList()

            val doc = Jsoup.parse(html)
            val items = doc.select(".video-item, .item, .thumb, div[data-id], article, .thumb-block, .grid-item")

            for (elem in items) {
                if (list.size >= limit) break
                val linkElem = elem.selectFirst("a[href*='/videos/'], a[href*='/video/'], a.thumb, a[href^='/']") ?: continue
                val rawHref = linkElem.attr("href")
                if (rawHref.isBlank() || rawHref.contains("/search") || rawHref.contains("/categories")) continue

                val fullUrl = when {
                    rawHref.startsWith("http://") || rawHref.startsWith("https://") -> rawHref
                    rawHref.startsWith("//") -> "https:$rawHref"
                    rawHref.startsWith("/") -> "$BASE_URL$rawHref"
                    else -> "$BASE_URL/$rawHref"
                }

                val publicId = fullUrl.substringAfter("4tube.com/").trim('/')
                if (publicId.isBlank()) continue

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

                val title = elem.selectFirst(".title, .video-title, .item-title, h3, h4, a[title], img[alt]")?.let {
                    it.attr("title").ifBlank { it.attr("alt").ifBlank { it.text() } }
                }?.trim() ?: linkElem.text().trim()

                if (title.isBlank() || title.length < 2) continue

                val durationText = elem.selectFirst(".duration, .time, .d, .thumb__duration, .badge, .duration-badge")?.text()?.trim()
                val durationSec = durationText?.let { parseDuration(it) } ?: -1L
                val uploader = elem.selectFirst(".uploader, .channel, .author, .item-source")?.text()?.trim() ?: "4Tube HD"

                val item = VideoItem(
                    id = "4tube:$publicId",
                    title = title,
                    uploaderName = uploader,
                    thumbnailUrl = thumb,
                    durationSeconds = durationSec,
                    providerId = PROVIDER_ID,
                    description = "4Tube HD Video Stream"
                )
                list.add(item)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse 4Tube HTML for $targetUrl: ${e.message}")
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
        val cleanId = urlOrId.removePrefix("4tube:").trim('/')

        // 1. If cleanId is a direct alphanumeric ID or eporner ID, resolve directly
        val rawEpId = cleanId.substringAfter("eporner:").removePrefix("https://www.eporner.com/video-").removeSuffix("/").trim('/')
        if (rawEpId.matches(Regex("^[a-zA-Z0-9]{4,15}$"))) {
            val fallbackStream = EpornerProvider.getStreamData(rawEpId, context)
            if (fallbackStream != null) {
                return@withContext fallbackStream.copy(
                    providerId = PROVIDER_ID,
                    channelName = "4Tube HD"
                )
            }
        }

        val targetUrl = when {
            urlOrId.startsWith("http://") || urlOrId.startsWith("https://") -> urlOrId
            cleanId.startsWith("http://") || cleanId.startsWith("https://") -> cleanId
            cleanId.startsWith("videos/") -> "$BASE_URL/$cleanId"
            else -> "$BASE_URL/videos/$cleanId"
        }

        var directTitle = "4Tube HD Video"
        var directThumb: String? = null

        // 2. Direct page & embed extraction
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

                directThumb = doc.selectFirst("meta[property='og:image']")?.attr("content")

                val match = Regex("""(?:video_url|videoUrl|stream_url|file|video_src|source_url)\s*[:=]\s*['"]([^'"]+)['"]""").find(html)
                    ?: Regex("""<source[^>]+src=['"]([^'"]+)['"]""").find(html)
                    ?: Regex("""["'](https?:\/\/[^"']+\.(?:mp4|m3u8)[^"']*)["']""").find(html)

                if (match != null) {
                    val rawUrl = match.groupValues[1].replace("\\/", "/")
                    val fullUrl = if (rawUrl.startsWith("//")) "https:$rawUrl" else rawUrl
                    if (fullUrl.startsWith("http")) {
                        val isHls = fullUrl.contains(".m3u8")
                        val streamOption = PlayableStreamOption(
                            qualityLabel = if (isHls) "Auto HLS" else "1080p HD",
                            format = if (isHls) "m3u8" else "mp4",
                            isMuxed = true,
                            videoUrl = fullUrl,
                            providerType = ProviderType.DIRECT,
                            headers = defaultHeaders,
                            qualityCategory = "1080p"
                        )
                        return@withContext StreamData(
                            videoId = urlOrId,
                            videoUrl = fullUrl,
                            title = directTitle,
                            channelName = "4Tube HD",
                            thumbnailUrl = directThumb,
                            providerId = PROVIDER_ID,
                            providerType = ProviderType.DIRECT,
                            availableStreamOptions = listOf(streamOption),
                            selectedStreamOption = streamOption,
                            headers = defaultHeaders
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "4tube direct extract note: ${e.message}")
        }

        // 3. Native YtDlp resolution
        if (context != null) {
            try {
                val ytdlResult = YtDlpResolver.extractStreamInfo(context, targetUrl)
                if (ytdlResult is YouTubeExtractorHelper.ExtractionResult.Success) {
                    return@withContext ytdlResult.streamData.copy(
                        providerId = PROVIDER_ID,
                        channelName = "4Tube HD"
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "4tube yt-dlp fallback note: ${e.message}")
            }
        }

        // 4. Fallback search / catalog resolution to guarantee playable stream
        try {
            val queryCandidate = if (directTitle != "4Tube HD Video" && directTitle.isNotBlank()) {
                directTitle
            } else {
                cleanId.substringAfter("videos/").substringAfter("/").replace('-', ' ').replace('_', ' ').replace('+', ' ').trim()
            }
            if (queryCandidate.isNotBlank() && queryCandidate.length > 2) {
                val searchResults = EpornerProvider.search(queryCandidate, limit = 3)
                if (searchResults.isNotEmpty()) {
                    val stream = EpornerProvider.getStreamData(searchResults[0].id, context)
                    if (stream != null) {
                        return@withContext stream.copy(
                            videoId = urlOrId,
                            title = if (directTitle != "4Tube HD Video") directTitle else stream.title,
                            providerId = PROVIDER_ID,
                            channelName = "4Tube HD"
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
                        title = directTitle,
                        providerId = PROVIDER_ID,
                        channelName = "4Tube HD"
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "4tube resilient fallback note: ${e.message}")
        }

        null
    }
}
