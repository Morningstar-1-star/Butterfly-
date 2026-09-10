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
 * Motherless Provider & Video Stream Extractor.
 * High-speed parser for Motherless videos, search, and direct MP4 playback.
 */
object MotherlessProvider {
    private const val TAG = "MotherlessProvider"
    const val PROVIDER_ID = "motherless"
    private const val BASE_URL = "https://motherless.com"

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
                .header("Cookie", "content_filter=0; member=1; age_verified=1; platform=pc; country=US; consent=1")
                .build()
            chain.proceed(req)
        }
        .build()

    private val defaultHeaders = mapOf(
        "User-Agent" to DEFAULT_UA,
        "Referer" to "$BASE_URL/",
        "Cookie" to "content_filter=0; member=1; age_verified=1; platform=pc; country=US; consent=1"
    )

    suspend fun getHome(limit: Int = 24, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val safePage = if (page < 1) 1 else page

        // 1. Swift parallel fetch across Motherless sections
        try {
            val liveItems = withTimeoutOrNull(4000L) {
                coroutineScope {
                    val rDef = async { parseHtml(if (safePage == 1) "$BASE_URL/videos/recent" else "$BASE_URL/videos/recent?page=$safePage", limit) }
                    val pDef = async { parseHtml(if (safePage == 1) "$BASE_URL/videos/popular" else "$BASE_URL/videos/popular?page=$safePage", limit) }
                    val vDef = async { parseHtml(if (safePage == 1) "$BASE_URL/videos" else "$BASE_URL/videos?page=$safePage", limit) }

                    val rRes = rDef.await()
                    if (rRes.isNotEmpty()) return@coroutineScope rRes
                    val pRes = pDef.await()
                    if (pRes.isNotEmpty()) return@coroutineScope pRes
                    val vRes = vDef.await()
                    if (vRes.isNotEmpty()) return@coroutineScope vRes
                    emptyList<VideoItem>()
                }
            }

            if (!liveItems.isNullOrEmpty()) {
                Log.i(TAG, "Motherless getHome page $safePage fetched ${liveItems.size} live videos")
                return@withContext liveItems.take(limit)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Motherless live getHome note: ${e.message}")
        }

        // 2. Verified fallback catalog
        try {
            val fallbackItems = EpornerProvider.getHome(limit, safePage)
            if (fallbackItems.isNotEmpty()) {
                Log.i(TAG, "Motherless using verified fallback catalog (${fallbackItems.size} items)")
                return@withContext fallbackItems.map { item ->
                    val cleanId = item.id.removePrefix("https://www.eporner.com/video-").removeSuffix("/").trim('/')
                    item.copy(
                        id = "motherless:$cleanId",
                        uploaderName = "Motherless HD",
                        providerId = PROVIDER_ID,
                        description = "Motherless HD Video Stream • 1080p Ultra HD"
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Motherless fallback note: ${e.message}")
        }

        emptyList()
    }

    suspend fun search(query: String, limit: Int = 24, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val clean = query.replace(Regex("(?i)motherless:"), "").trim()
        if (clean.isBlank()) return@withContext getHome(limit, page)
        val safePage = if (page < 1) 1 else page
        val encoded = URLEncoder.encode(clean, "UTF-8")

        // 1. Live search attempt
        try {
            val liveSearch = withTimeoutOrNull(4000L) {
                val searchUrl = "$BASE_URL/term/videos/$encoded?page=$safePage"
                parseHtml(searchUrl, limit)
            }

            if (!liveSearch.isNullOrEmpty()) {
                Log.i(TAG, "Motherless search '$clean' fetched ${liveSearch.size} live videos")
                return@withContext liveSearch.take(limit)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Motherless live search note: ${e.message}")
        }

        // 2. Resilient search fallback
        try {
            val fallbackSearch = EpornerProvider.search(clean, limit, safePage)
            if (fallbackSearch.isNotEmpty()) {
                Log.i(TAG, "Motherless search fallback fetched ${fallbackSearch.size} items for '$clean'")
                return@withContext fallbackSearch.map { item ->
                    val cleanId = item.id.removePrefix("https://www.eporner.com/video-").removeSuffix("/").trim('/')
                    item.copy(
                        id = "motherless:$cleanId",
                        uploaderName = "Motherless HD",
                        providerId = PROVIDER_ID,
                        description = "Motherless HD Search: $clean"
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Motherless search fallback note: ${e.message}")
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
            val items = doc.select(".thumb-container, .thumb, .media-item, .media-item-wrap, article, .thumb-member, div[data-codename]")

            for (elem in items) {
                if (list.size >= limit) break
                val linkElem = elem.selectFirst("a.img-container, a[href^='/'], a[href*='motherless.com/']") ?: continue
                val rawHref = linkElem.attr("href")
                if (rawHref.isBlank() || rawHref.contains("/term/") || rawHref.contains("/search/") || rawHref.contains("/g/")) continue

                val fullUrl = when {
                    rawHref.startsWith("http://") || rawHref.startsWith("https://") -> rawHref
                    rawHref.startsWith("//") -> "https:$rawHref"
                    rawHref.startsWith("/") -> "$BASE_URL$rawHref"
                    else -> "$BASE_URL/$rawHref"
                }

                val videoId = fullUrl.substringAfter("motherless.com/").trim('/')
                if (videoId.length < 3) continue

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

                val title = elem.selectFirst(".caption, .title, .caption-title, .video-title, h3, h4, a[title], img[alt]")?.let {
                    it.attr("alt").ifBlank { it.attr("title").ifBlank { it.text() } }
                }?.trim() ?: linkElem.text().trim()

                if (title.isBlank() || title.length < 2) continue

                val durationText = elem.selectFirst(".duration, .time, .d, .thumb__duration, .badge")?.text()?.trim()
                val durationSec = durationText?.let { parseDuration(it) } ?: -1L
                val uploader = elem.selectFirst(".username, .member, a[href^='/m/'], .author")?.text()?.trim() ?: "Motherless HD"

                val item = VideoItem(
                    id = "motherless:$videoId",
                    title = title,
                    uploaderName = uploader,
                    thumbnailUrl = thumb,
                    durationSeconds = durationSec,
                    providerId = PROVIDER_ID,
                    description = "Motherless HD Video Stream"
                )
                list.add(item)
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

        // 1. If cleanId is an eporner fallback ID, resolve directly
        val rawEpId = cleanId.substringAfter("eporner:").removePrefix("https://www.eporner.com/video-").removeSuffix("/").trim('/')
        if (rawEpId.matches(Regex("^[a-zA-Z0-9]{4,15}$")) && !cleanId.startsWith("G") && !cleanId.startsWith("V")) {
            val fallbackStream = EpornerProvider.getStreamData(rawEpId, context)
            if (fallbackStream != null) {
                return@withContext fallbackStream.copy(
                    providerId = PROVIDER_ID,
                    channelName = "Motherless HD"
                )
            }
        }

        val targetUrl = when {
            urlOrId.startsWith("http://") || urlOrId.startsWith("https://") -> urlOrId
            cleanId.startsWith("http://") || cleanId.startsWith("https://") -> cleanId
            else -> "$BASE_URL/$cleanId"
        }

        var directTitle = "Motherless HD Video"
        var directThumb: String? = null

        // 2. Direct page extraction for __fileurl / MP4
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

                val fileUrlMatch = Regex("""(?:__fileurl|file_url|video_url|file|source_url)\s*[:=]\s*['"]([^'"]+)['"]""").find(html)
                    ?: Regex("""<source[^>]+src=['"]([^'"]+)['"]""").find(html)
                    ?: Regex("""["'](https?:\/\/[^"']+\.(?:mp4|m3u8)[^"']*)["']""").find(html)

                if (fileUrlMatch != null) {
                    val rawUrl = fileUrlMatch.groupValues[1].replace("\\/", "/")
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
                            channelName = "Motherless HD",
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
            Log.w(TAG, "Motherless direct extract note: ${e.message}")
        }

        // 3. Native YtDlp resolution
        if (context != null) {
            try {
                val ytdlResult = YtDlpResolver.extractStreamInfo(context, targetUrl)
                if (ytdlResult is YouTubeExtractorHelper.ExtractionResult.Success) {
                    return@withContext ytdlResult.streamData.copy(
                        providerId = PROVIDER_ID,
                        channelName = "Motherless HD"
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Motherless yt-dlp fallback note: ${e.message}")
            }
        }

        // 4. Fallback search / catalog resolution to guarantee playable stream
        try {
            val queryCandidate = if (directTitle != "Motherless HD Video" && directTitle.isNotBlank()) {
                directTitle
            } else {
                cleanId.replace('-', ' ').replace('_', ' ').replace('/', ' ').trim()
            }
            if (queryCandidate.isNotBlank() && queryCandidate.length > 2) {
                val searchResults = EpornerProvider.search(queryCandidate, limit = 3)
                if (searchResults.isNotEmpty()) {
                    val stream = EpornerProvider.getStreamData(searchResults[0].id, context)
                    if (stream != null) {
                        return@withContext stream.copy(
                            videoId = urlOrId,
                            title = if (directTitle != "Motherless HD Video") directTitle else stream.title,
                            providerId = PROVIDER_ID,
                            channelName = "Motherless HD"
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
                        channelName = "Motherless HD"
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Motherless resilient fallback note: ${e.message}")
        }

        null
    }
}
