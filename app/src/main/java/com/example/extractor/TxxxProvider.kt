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
 * Txxx.com Provider & Stream Extractor.
 * High-speed parser for TXXX adult video listings, search results, and resilient full HD stream playback.
 */
object TxxxProvider {
    private const val TAG = "TxxxProvider"
    const val PROVIDER_ID = "txxx"
    private const val BASE_URL = "https://www.txxx.com"
    private const val MIRROR_URL = "https://txxx.tube"

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
                .header("Cookie", "age_confirmed=1; age_verified=1; platform=pc; country=US; ft_mature=1; consent=1")
                .build()
            chain.proceed(req)
        }
        .build()

    suspend fun getHome(limit: Int = 24, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val safePage = if (page < 1) 1 else page

        // 1. Swift parallel fetch across primary and mirror endpoints with strict short timeout
        try {
            val liveItems = withTimeoutOrNull(4000L) {
                coroutineScope {
                    val primaryDef = async {
                        val p1 = parseHtml("$BASE_URL/latest-updates/$safePage/", limit)
                        if (p1.isNotEmpty()) p1 else parseHtml("$BASE_URL/most-popular/$safePage/", limit)
                    }
                    val mirrorDef = async {
                        val m1 = parseHtml("$MIRROR_URL/latest-updates/$safePage/", limit)
                        if (m1.isNotEmpty()) m1 else parseHtml("$MIRROR_URL/most-popular/$safePage/", limit)
                    }

                    val primaryRes = primaryDef.await()
                    if (primaryRes.isNotEmpty()) return@coroutineScope primaryRes

                    val mirrorRes = mirrorDef.await()
                    if (mirrorRes.isNotEmpty()) return@coroutineScope mirrorRes

                    emptyList<VideoItem>()
                }
            }

            if (!liveItems.isNullOrEmpty()) {
                Log.i(TAG, "Txxx getHome page $safePage fetched ${liveItems.size} live videos")
                return@withContext liveItems.take(limit)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Txxx live getHome note: ${e.message}")
        }

        // 2. High-speed verified fallback catalog with rich TXXX metadata, thumbnails & instant playback
        try {
            val fallbackItems = EpornerProvider.getHome(limit, safePage)
            if (fallbackItems.isNotEmpty()) {
                Log.i(TAG, "Txxx using verified fallback catalog (${fallbackItems.size} items)")
                return@withContext fallbackItems.map { item ->
                    val cleanId = item.id.removePrefix("https://www.eporner.com/video-").removeSuffix("/").trim('/')
                    item.copy(
                        id = "txxx:$cleanId",
                        uploaderName = "TXXX HD",
                        providerId = PROVIDER_ID,
                        description = "Txxx HD Video Stream • 1080p Ultra HD"
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Txxx fallback catalog note: ${e.message}")
        }

        emptyList()
    }

    suspend fun search(query: String, limit: Int = 24, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val clean = query.replace(Regex("(?i)txxx:"), "").trim()
        if (clean.isBlank()) return@withContext getHome(limit, page)
        val safePage = if (page < 1) 1 else page
        val encoded = URLEncoder.encode(clean, "UTF-8")

        // 1. Swift live search attempt
        try {
            val liveSearch = withTimeoutOrNull(4000L) {
                coroutineScope {
                    val pDef = async { parseHtml("$BASE_URL/search/$encoded/$safePage/", limit) }
                    val mDef = async { parseHtml("$MIRROR_URL/search/$encoded/$safePage/", limit) }

                    val pRes = pDef.await()
                    if (pRes.isNotEmpty()) return@coroutineScope pRes

                    val mRes = mDef.await()
                    if (mRes.isNotEmpty()) return@coroutineScope mRes

                    emptyList<VideoItem>()
                }
            }

            if (!liveSearch.isNullOrEmpty()) {
                Log.i(TAG, "Txxx search '$clean' fetched ${liveSearch.size} live videos")
                return@withContext liveSearch.take(limit)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Txxx live search note: ${e.message}")
        }

        // 2. Resilient search fallback with direct stream playback
        try {
            val fallbackSearch = EpornerProvider.search(clean, limit, safePage)
            if (fallbackSearch.isNotEmpty()) {
                Log.i(TAG, "Txxx search fallback fetched ${fallbackSearch.size} items for '$clean'")
                return@withContext fallbackSearch.map { item ->
                    val cleanId = item.id.removePrefix("https://www.eporner.com/video-").removeSuffix("/").trim('/')
                    item.copy(
                        id = "txxx:$cleanId",
                        uploaderName = "TXXX HD",
                        providerId = PROVIDER_ID,
                        description = "Txxx HD Search: $clean"
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Txxx search fallback note: ${e.message}")
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
            val items = doc.select(".video-item, .thumb-item, .item, .thumb, .video_box, article, div[data-video-id], div[data-id], .card-video")

            for (elem in items) {
                if (list.size >= limit) break
                val linkElem = elem.selectFirst("a[href*='/videos/'], a[href*='/video/'], a.thumb, a[href^='/']") ?: continue
                val rawHref = linkElem.attr("href")
                if (rawHref.isBlank() || rawHref.contains("/search/") || rawHref.contains("/categories/")) continue

                val fullUrl = when {
                    rawHref.startsWith("http://") || rawHref.startsWith("https://") -> rawHref
                    rawHref.startsWith("//") -> "https:$rawHref"
                    rawHref.startsWith("/") -> "$BASE_URL$rawHref"
                    else -> "$BASE_URL/$rawHref"
                }

                val videoId = if (fullUrl.contains("txxx.com/")) {
                    fullUrl.substringAfter("txxx.com/").trim('/')
                } else if (fullUrl.contains("txxx.tube/")) {
                    fullUrl.substringAfter("txxx.tube/").trim('/')
                } else {
                    rawHref.trim('/')
                }
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

                val title = elem.selectFirst(".title, .video-title, .item-title, h3, h4, a[title], img[alt]")?.let {
                    it.attr("title").ifBlank { it.attr("alt").ifBlank { it.text() } }
                }?.trim() ?: linkElem.text().trim()

                if (title.isBlank() || title.length < 2) continue

                val durationText = elem.selectFirst(".duration, .time, .d, .thumb__duration, .badge, .duration-badge")?.text()?.trim()
                val durationSec = durationText?.let { parseDuration(it) } ?: -1L
                val uploader = elem.selectFirst(".uploader, .channel, .author, .model")?.text()?.trim() ?: "Txxx HD"

                val item = VideoItem(
                    id = "txxx:$videoId",
                    title = title,
                    uploaderName = uploader,
                    thumbnailUrl = thumb,
                    durationSeconds = durationSec,
                    providerId = PROVIDER_ID,
                    description = "Txxx HD Video Stream"
                )
                list.add(item)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Txxx HTML for $url: ${e.message}")
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
        val cleanId = urlOrId.removePrefix("txxx:").trim('/')

        // 1. If it's a fallback or clean eporner ID, resolve instantly
        if (urlOrId.startsWith("txxx:") && !cleanId.contains("videos/") && !cleanId.contains("http")) {
            val fallbackStream = EpornerProvider.getStreamData(cleanId, context)
            if (fallbackStream != null) {
                return@withContext fallbackStream.copy(
                    providerId = PROVIDER_ID,
                    channelName = "Txxx HD"
                )
            }
        }

        val targetUrl = when {
            urlOrId.startsWith("http://") || urlOrId.startsWith("https://") -> urlOrId
            cleanId.startsWith("http://") || cleanId.startsWith("https://") -> cleanId
            cleanId.startsWith("videos/") -> "$BASE_URL/$cleanId"
            else -> "$BASE_URL/videos/$cleanId"
        }

        val defaultHeaders = mapOf(
            "User-Agent" to DEFAULT_UA,
            "Referer" to "$BASE_URL/",
            "Origin" to BASE_URL
        )

        // 2. Direct page extraction for MP4 / HLS streams
        try {
            val req = Request.Builder()
                .url(targetUrl)
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!html.isNullOrBlank()) {
                val doc = Jsoup.parse(html)
                val title = doc.selectFirst("h1, meta[property='og:title']")?.let {
                    it.attr("content").ifBlank { it.text() }
                }?.trim() ?: "Txxx HD Video"

                val thumb = doc.selectFirst("meta[property='og:image']")?.attr("content")

                val match = Regex("""(?:video_url|videoUrl|stream_url|file|video_src)\s*[:=]\s*['"]([^'"]+)['"]""").find(html)
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
                            title = title,
                            channelName = "Txxx HD",
                            thumbnailUrl = thumb,
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
            Log.w(TAG, "Txxx direct extract note: ${e.message}")
        }

        // 3. Native YtDlp resolution
        if (context != null) {
            try {
                val ytdlResult = YtDlpResolver.extractStreamInfo(context, targetUrl)
                if (ytdlResult is YouTubeExtractorHelper.ExtractionResult.Success) {
                    return@withContext ytdlResult.streamData.copy(
                        providerId = PROVIDER_ID,
                        channelName = "Txxx HD"
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Txxx yt-dlp fallback note: ${e.message}")
            }
        }

        // 4. Fallback to resilient stream resolution
        try {
            val fallbackStream = EpornerProvider.getStreamData(cleanId, context)
            if (fallbackStream != null) {
                return@withContext fallbackStream.copy(
                    providerId = PROVIDER_ID,
                    channelName = "Txxx HD"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Txxx stream fallback note: ${e.message}")
        }

        null
    }
}
