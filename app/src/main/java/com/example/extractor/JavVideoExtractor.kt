package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.extractor.supjav.JavEnglishTitleHelper
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import com.example.model.StreamData
import com.example.model.VideoItem
import com.example.model.parseDurationToSeconds
import com.example.util.StreamCategorizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * High-performance extractor & aggregator for JAV (Japanese Adult Video) providers:
 * - SupJav
 * - Sextb / JAV HD
 * - 123AV / JAVPlayer
 * - Javtiful
 * - High-speed JAV catalog & CDN streams
 */
object JavVideoExtractor {
    private const val TAG = "JavVideoExtractor"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9,ja;q=0.8")
                .build()
            chain.proceed(req)
        }
        .build()

    suspend fun getHome(providerId: String, limit: Int = 20, page: Int = 1, context: Context? = null): List<VideoItem> = withContext(Dispatchers.IO) {
        val pid = providerId.lowercase().trim()
        when (pid) {
            "supjav" -> {
                val list = SupJavProvider.getHome(limit, page, context)
                if (list.isNotEmpty()) list else get123AvHome(limit, page).ifEmpty { getJavtifulHome(limit, page) }
            }
            "123av", "javplayer" -> {
                val list = get123AvHome(limit, page)
                if (list.isNotEmpty()) list else getJavtifulHome(limit, page).ifEmpty { getAllJavHome(limit, page, context) }
            }
            "javtiful" -> {
                val list = getJavtifulHome(limit, page)
                if (list.isNotEmpty()) list else get123AvHome(limit, page).ifEmpty { getAllJavHome(limit, page, context) }
            }
            "jav_all", "all_jav" -> getAllJavHome(limit, page, context)
            else -> getAllJavHome(limit, page, context)
        }
    }

    suspend fun search(providerId: String, query: String, limit: Int = 20, page: Int = 1, context: Context? = null): List<VideoItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext getHome(providerId, limit, page, context)
        val pid = providerId.lowercase().trim()

        when (pid) {
            "supjav" -> {
                val res = SupJavProvider.search(cleanQuery, limit, page, context)
                if (res.isNotEmpty()) res else searchAllJav(cleanQuery, limit, page, context)
            }
            "123av", "javplayer" -> {
                val res = search123Av(cleanQuery, limit, page)
                if (res.isNotEmpty()) res else searchAllJav(cleanQuery, limit, page, context)
            }
            "javtiful" -> {
                val res = searchJavtiful(cleanQuery, limit, page)
                if (res.isNotEmpty()) res else searchAllJav(cleanQuery, limit, page, context)
            }
            "jav_all", "all_jav" -> searchAllJav(cleanQuery, limit, page, context)
            else -> searchAllJav(cleanQuery, limit, page, context)
        }
    }

    // ----------------------------------------------------
    // 123AV Extractor
    // ----------------------------------------------------
    fun get123AvHome(limit: Int, page: Int): List<VideoItem> {
        val urls = listOf(
            if (page <= 1) "https://123av.com/en" else "https://123av.com/en/new?page=$page",
            if (page <= 1) "https://123av.com/en/ranking" else "https://123av.com/en/ranking?page=$page"
        )
        for (u in urls) {
            val list = parse123AvPage(u, limit)
            if (list.isNotEmpty()) return list
        }
        return emptyList()
    }

    fun search123Av(query: String, limit: Int, page: Int): List<VideoItem> {
        val encoded = try { URLEncoder.encode(query, "UTF-8") } catch (e: Exception) { query }
        val url = if (page <= 1) {
            "https://123av.com/en/search?keyword=$encoded"
        } else {
            "https://123av.com/en/search?keyword=$encoded&page=$page"
        }
        return parse123AvPage(url, limit)
    }

    private fun parse123AvPage(url: String, limit: Int): List<VideoItem> {
        val items = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder().url(url).build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return emptyList()

            val html = resp.body?.string() ?: return emptyList()
            val doc = Jsoup.parse(html)
            val cards = doc.select(".card, article, .video-card, .thumb-block, .item")

            for (card in cards) {
                if (items.size >= limit) break
                val linkEl = card.select("a.card__link, a.card__cover, a[href*='/en/v/'], a[href*='/v/']").firstOrNull() ?: continue
                val href = linkEl.attr("href")
                if (href.isBlank()) continue

                val codeMatch = Regex("/(?:en/)?v/([^/\\?]+)").find(href)
                val rawId = codeMatch?.groupValues?.get(1) ?: href.substringAfterLast("/")

                val rawTitle = card.select(".card__title, .title, h3").text().trim().ifBlank {
                    linkEl.attr("title").ifBlank { rawId.uppercase() }
                }

                val title = JavEnglishTitleHelper.toEnglishTitle(rawTitle, rawId)
                val code = JavEnglishTitleHelper.extractJavCode(title).ifBlank { rawId.uppercase() }

                val imgEl = card.select("img.card__img, img").firstOrNull()
                var thumb = imgEl?.attr("src")?.ifBlank { null } ?: imgEl?.attr("data-src")?.ifBlank { null }
                if (thumb != null && thumb.startsWith("//")) thumb = "https:$thumb"

                val durText = card.select(".card__dur, .duration, span.badge").text().trim()
                val durationSec = parseDurationToSeconds(durText)

                val viewsText = card.select(".card__views, .views").text().trim()
                val views = parseViewsCount(viewsText)

                items.add(
                    VideoItem(
                        id = "123av_$rawId",
                        title = title,
                        uploaderName = if (code.isNotBlank()) "123AV • $code" else "123AV Studio",
                        thumbnailUrl = thumb,
                        durationSeconds = durationSec,
                        viewCount = views,
                        providerId = "123av",
                        description = "123AV JAV Direct Stream • Code: $code"
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "123AV parsing error for $url: ${e.message}")
        }
        return items
    }

    // ----------------------------------------------------
    // Javtiful Extractor
    // ----------------------------------------------------
    fun getJavtifulHome(limit: Int, page: Int): List<VideoItem> {
        val urls = listOf(
            if (page <= 1) "https://javtiful.com" else "https://javtiful.com/trending?page=$page",
            if (page <= 1) "https://javtiful.com/recent" else "https://javtiful.com/recent?page=$page"
        )
        for (u in urls) {
            val list = parseJavtifulPage(u, limit)
            if (list.isNotEmpty()) return list
        }
        return emptyList()
    }

    fun searchJavtiful(query: String, limit: Int, page: Int): List<VideoItem> {
        val encoded = try { URLEncoder.encode(query, "UTF-8") } catch (e: Exception) { query }
        val url = if (page <= 1) {
            "https://javtiful.com/search?q=$encoded"
        } else {
            "https://javtiful.com/search?q=$encoded&page=$page"
        }
        return parseJavtifulPage(url, limit)
    }

    private fun parseJavtifulPage(url: String, limit: Int): List<VideoItem> {
        val items = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder().url(url).build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return emptyList()

            val html = resp.body?.string() ?: return emptyList()
            val doc = Jsoup.parse(html)
            val cards = doc.select(".front-video-card, article.front-video-card, .video-item, .item")

            for (card in cards) {
                if (items.size >= limit) break
                val linkEl = card.select("a.front-video-title, a.front-video-thumb, a[href*='/video/']").firstOrNull() ?: continue
                val href = linkEl.attr("href")
                if (href.isBlank()) continue

                val idMatch = Regex("/video/(\\d+)(?:/([^/?]+))?").find(href)
                val rawId = if (idMatch != null) {
                    val numId = idMatch.groupValues[1]
                    val slug = idMatch.groupValues.getOrNull(2) ?: ""
                    if (slug.isNotBlank()) "${numId}_$slug" else numId
                } else {
                    href.substringAfterLast("/")
                }

                val rawTitle = card.select(".front-video-title, .title").text().trim().ifBlank {
                    linkEl.text().trim().ifBlank { rawId.uppercase() }
                }

                val title = JavEnglishTitleHelper.toEnglishTitle(rawTitle, rawId)
                val code = JavEnglishTitleHelper.extractJavCode(title).ifBlank { JavEnglishTitleHelper.extractJavCode(rawId) }

                val imgEl = card.select("img").firstOrNull()
                var thumb = imgEl?.attr("data-front-lazy-src")?.ifBlank { null }
                    ?: imgEl?.attr("src")?.ifBlank { null }
                if (thumb != null && thumb.startsWith("/")) thumb = "https://javtiful.com$thumb"
                if (thumb != null && thumb.startsWith("//")) thumb = "https:$thumb"

                val durText = card.select(".front-duration-tag, .duration").text().trim()
                val durationSec = parseDurationToSeconds(durText)

                val statText = card.select(".front-video-stat, .views").text().trim()
                val views = parseViewsCount(statText)

                items.add(
                    VideoItem(
                        id = "javtiful_$rawId",
                        title = title,
                        uploaderName = if (code.isNotBlank()) "Javtiful • $code" else "Javtiful Studio",
                        thumbnailUrl = thumb,
                        durationSeconds = durationSec,
                        viewCount = views,
                        providerId = "javtiful",
                        description = "Javtiful High Speed Stream • $title"
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Javtiful parsing error for $url: ${e.message}")
        }
        return items
    }

    // ----------------------------------------------------
    // Aggregator (All JAV Sources)
    // ----------------------------------------------------
    suspend fun getAllJavHome(limit: Int, page: Int, context: Context?): List<VideoItem> = coroutineScope {
        val d0 = async { SupJavProvider.getHome(limit, page, context) }
        val d1 = async { SextbProvider.getHome(limit, page, context) }
        val d2 = async { get123AvHome(limit, page) }
        val d3 = async { getJavtifulHome(limit, page) }
        val d4 = async {
            try {
                EpornerProvider.search("JAV", limit = limit, page = page).map {
                    it.copy(
                        title = JavEnglishTitleHelper.toEnglishTitle(it.title, it.id),
                        uploaderName = if (it.uploaderName?.contains("JAV", ignoreCase = true) == true) it.uploaderName else "JAV HD • ${it.uploaderName ?: "Japan"}",
                        providerId = "jav_all"
                    )
                }
            } catch (_: Exception) { emptyList<VideoItem>() }
        }

        val res0 = try { d0.await() } catch (_: Exception) { emptyList() }
        val res1 = try { d1.await() } catch (_: Exception) { emptyList() }
        val res2 = try { d2.await() } catch (_: Exception) { emptyList() }
        val res3 = try { d3.await() } catch (_: Exception) { emptyList() }
        val res4 = try { d4.await() } catch (_: Exception) { emptyList() }

        val combined = mutableListOf<VideoItem>()
        val maxLen = maxOf(res0.size, res1.size, res2.size, res3.size, res4.size)
        for (i in 0 until maxLen) {
            if (i < res0.size) combined.add(res0[i])
            if (i < res1.size) combined.add(res1[i])
            if (i < res2.size) combined.add(res2[i])
            if (i < res3.size) combined.add(res3[i])
            if (i < res4.size) combined.add(res4[i])
        }

        val distinct = combined.distinctBy { it.id }
        if (distinct.isNotEmpty()) {
            distinct.take(limit)
        } else {
            emptyList()
        }
    }

    suspend fun searchAllJav(query: String, limit: Int, page: Int, context: Context?): List<VideoItem> = coroutineScope {
        val d0 = async { SupJavProvider.search(query, limit, page, context) }
        val d1 = async { SextbProvider.search(query, limit, page, context) }
        val d2 = async { search123Av(query, limit, page) }
        val d3 = async { searchJavtiful(query, limit, page) }
        val d4 = async {
            try {
                EpornerProvider.search(if (query.contains("jav", ignoreCase = true)) query else "JAV $query", limit = limit, page = page).map {
                    it.copy(
                        title = JavEnglishTitleHelper.toEnglishTitle(it.title, it.id),
                        uploaderName = "JAV HD • ${it.uploaderName ?: "Japanese Studio"}"
                    )
                }
            } catch (_: Exception) { emptyList<VideoItem>() }
        }

        val res0 = try { d0.await() } catch (_: Exception) { emptyList() }
        val res1 = try { d1.await() } catch (_: Exception) { emptyList() }
        val res2 = try { d2.await() } catch (_: Exception) { emptyList() }
        val res3 = try { d3.await() } catch (_: Exception) { emptyList() }
        val res4 = try { d4.await() } catch (_: Exception) { emptyList() }

        val combined = (res0 + res1 + res2 + res3 + res4).distinctBy { it.id }
        if (combined.isNotEmpty()) {
            combined.take(limit)
        } else {
            emptyList()
        }
    }

    private fun parseViewsCount(raw: String): Long {
        if (raw.isBlank()) return 0L
        val clean = raw.lowercase().replace(",", "").trim()
        return try {
            when {
                clean.endsWith("m") -> (clean.removeSuffix("m").toDouble() * 1_000_000).toLong()
                clean.endsWith("k") -> (clean.removeSuffix("k").toDouble() * 1_000).toLong()
                else -> clean.filter { it.isDigit() }.toLongOrNull() ?: 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Directly resolves stream URLs for SupJav, 123AV, Javtiful, and Sextb videos.
     */
    suspend fun extractStream(videoIdOrUrl: String, context: Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        val lower = videoIdOrUrl.lowercase()

        // 0. SupJav Stream Extraction
        if (lower.startsWith("supjav_") || lower.startsWith("supjav:") || lower.contains("supjav.com") || lower.contains("supjav.mom") || lower.contains("supjav.biz") || lower.contains("tvlogy")) {
            val sjStream = SupJavProvider.getStreamData(videoIdOrUrl, context)
            if (sjStream != null && sjStream.availableStreamOptions.isNotEmpty()) return@withContext sjStream
        }

        // 1. Sextb Stream Extraction
        if (lower.startsWith("sextb_") || lower.startsWith("sextb:") || lower.contains("sextb.net")) {
            val sexStream = SextbProvider.getStreamData(videoIdOrUrl, context)
            if (sexStream != null && sexStream.availableStreamOptions.isNotEmpty()) return@withContext sexStream
        }

        // 2. 123AV Direct Stream Extraction
        if (lower.startsWith("123av_") || lower.contains("123av.com") || lower.contains("javplayer.cc")) {
            try {
                val slug = videoIdOrUrl.removePrefix("123av_")
                    .substringAfter("/v/")
                    .substringBefore("?")
                    .trim('/')
                val targetUrl = "https://123av.com/en/v/$slug"

                val req = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", userAgent)
                    .header("Referer", "https://123av.com/en")
                    .build()

                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    val doc = Jsoup.parse(html)
                    val rawTitle = doc.select("h1, .watch__title, .vdetail__title, title").firstOrNull()?.text()?.trim() ?: "[$slug] 123AV JAV Stream"
                    val title = JavEnglishTitleHelper.toEnglishTitle(rawTitle, slug)

                    val embedMatch = Regex("""javplayer\.cc[\\/]+e[\\/]+([a-zA-Z0-9_-]+)""").find(html)
                        ?: Regex("""javplayer\.cc.*?/e.*?/([a-zA-Z0-9_-]+)""").find(html)
                        ?: Regex("""https?://javplayer\.cc/e/([a-zA-Z0-9_-]+)""").find(html)

                    val embedId = embedMatch?.groupValues?.get(1)
                    if (!embedId.isNullOrBlank()) {
                        val streamApiUrl = "https://javplayer.cc/stream?id=$embedId"
                        val apiReq = Request.Builder()
                            .url(streamApiUrl)
                            .header("User-Agent", userAgent)
                            .header("Referer", "https://javplayer.cc/e/$embedId")
                            .header("Origin", "https://javplayer.cc")
                            .build()

                        val apiResp = httpClient.newCall(apiReq).execute()
                        if (apiResp.isSuccessful) {
                            val apiJson = org.json.JSONObject(apiResp.body?.string() ?: "")
                            if (apiJson.optString("status") == "ok") {
                                val m3u8 = apiJson.optJSONObject("media")?.optString("stream")
                                if (!m3u8.isNullOrBlank()) {
                                    val headers = mapOf(
                                        "Referer" to "https://javplayer.cc/",
                                        "Origin" to "https://javplayer.cc",
                                        "User-Agent" to userAgent
                                    )
                                    val opt = PlayableStreamOption(
                                        qualityLabel = "1080p FHD • JAVPlayer CDN",
                                        format = "m3u8",
                                        isMuxed = true,
                                        videoUrl = m3u8,
                                        audioUrl = null,
                                        providerType = ProviderType.DIRECT,
                                        headers = headers,
                                        sourceName = "123AV / JAVPlayer",
                                        qualityCategory = StreamCategorizer.detectQualityFromText("1080p", false, false)
                                    )
                                    return@withContext StreamData(
                                        videoId = videoIdOrUrl,
                                        title = title,
                                        channelName = "123AV / JAVPlayer",
                                        channelAvatarUrl = null,
                                        description = "123AV Direct Stream • Code: ${slug.uppercase()}",
                                        availableStreamOptions = listOf(opt),
                                        selectedStreamOption = opt,
                                        providerId = "123av",
                                        providerType = ProviderType.DIRECT,
                                        headers = headers
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct 123av extract failed for $videoIdOrUrl: ${e.message}")
            }
        }

        // 3. Javtiful Direct Stream Extraction
        if (lower.startsWith("javtiful_") || lower.contains("javtiful.com")) {
            try {
                val rawSlug = videoIdOrUrl.removePrefix("javtiful_")
                    .substringAfter("/video/")
                    .substringBefore("?")
                    .trim('/')
                val numId = Regex("""\b(\d+)\b""").find(rawSlug)?.groupValues?.get(1) ?: rawSlug

                val urlsToTry = mutableListOf(
                    "https://javtiful.com/video/${rawSlug.replace('_', '/')}",
                    "https://javtiful.com/video/$numId",
                    "https://javtiful.com/embed/$numId"
                )

                for (targetUrl in urlsToTry) {
                    val req = Request.Builder()
                        .url(targetUrl)
                        .header("User-Agent", userAgent)
                        .header("Referer", "https://javtiful.com/")
                        .build()

                    val resp = httpClient.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val html = resp.body?.string() ?: ""
                        val doc = Jsoup.parse(html)
                        val rawTitle = doc.select("h1, .video-title, .title, title").firstOrNull()?.text()?.trim() ?: "[$rawSlug] Javtiful Stream"
                        val title = JavEnglishTitleHelper.toEnglishTitle(rawTitle, rawSlug)

                        val streamUrl = Regex("""(https://fast-stream\.jav\.si/p/[a-zA-Z0-9_-]+)""").find(html)?.groupValues?.get(1)
                            ?: Regex("""(https://[^"'\s<>]+\.mp4[^"'\s<>]*)""").find(html)?.groupValues?.get(1)
                            ?: Regex("""(https://[^"'\s<>]+\.m3u8[^"'\s<>]*)""").find(html)?.groupValues?.get(1)

                        if (!streamUrl.isNullOrBlank()) {
                            val isHls = streamUrl.contains(".m3u8")
                            val headers = mapOf(
                                "Referer" to "https://javtiful.com/",
                                "Origin" to "https://javtiful.com",
                                "User-Agent" to userAgent
                            )
                            val opt = PlayableStreamOption(
                                qualityLabel = if (isHls) "1080p FHD • Javtiful HLS" else "720p HD • Javtiful Fast Stream",
                                format = if (isHls) "m3u8" else "mp4",
                                isMuxed = true,
                                videoUrl = streamUrl,
                                audioUrl = null,
                                providerType = ProviderType.DIRECT,
                                headers = headers,
                                sourceName = "Javtiful",
                                qualityCategory = StreamCategorizer.detectQualityFromText(if (isHls) "1080p" else "720p", false, false)
                            )
                            return@withContext StreamData(
                                videoId = videoIdOrUrl,
                                title = title,
                                channelName = "Javtiful",
                                channelAvatarUrl = null,
                                description = "Javtiful Direct Fast Stream • $title",
                                availableStreamOptions = listOf(opt),
                                selectedStreamOption = opt,
                                providerId = "javtiful",
                                providerType = ProviderType.DIRECT,
                                headers = headers
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct javtiful extract failed for $videoIdOrUrl: ${e.message}")
            }
        }

        // 4. Direct Eporner ID check if prefixed with 123av_ or javtiful_
        val strippedId = videoIdOrUrl.removePrefix("123av_").removePrefix("javtiful_").removePrefix("supjav_").removePrefix("sextb_").trim()
        if (strippedId.matches(Regex("^[a-zA-Z0-9]{5,15}$")) && !strippedId.contains("-")) {
            try {
                val epDirect = EpornerProvider.getStreamData(strippedId, context)
                if (epDirect != null && epDirect.availableStreamOptions.isNotEmpty()) {
                    val detectedProvider = when {
                        lower.startsWith("123av_") -> "123av"
                        lower.startsWith("javtiful_") -> "javtiful"
                        lower.startsWith("supjav_") -> "supjav"
                        lower.startsWith("sextb_") -> "sextb"
                        else -> "jav_all"
                    }
                    return@withContext epDirect.copy(
                        videoId = videoIdOrUrl,
                        title = JavEnglishTitleHelper.toEnglishTitle(epDirect.title, strippedId),
                        providerId = detectedProvider
                    )
                }
            } catch (_: Exception) {}
        }

        // 5. Cross-Provider JAV Code Matching & Resilient 1080p CDN Fallback
        val javCode = JavEnglishTitleHelper.extractJavCode(videoIdOrUrl)
        val searchQ = if (javCode.isNotBlank()) "JAV $javCode" else "JAV ${strippedId.replace("_", " ").replace("-", " ").take(25)}"
        try {
            val epSearch = EpornerProvider.search(searchQ, limit = 4, page = 1).ifEmpty {
                EpornerProvider.search("JAV Uncensored HD", limit = 4, page = 1)
            }
            for (item in epSearch) {
                val epStream = EpornerProvider.getStreamData(item.id, context)
                if (epStream != null && epStream.availableStreamOptions.isNotEmpty()) {
                    return@withContext epStream.copy(
                        videoId = videoIdOrUrl,
                        title = JavEnglishTitleHelper.toEnglishTitle(epStream.title, javCode),
                        channelName = if (javCode.isNotBlank()) "JAV HD Studio • $javCode" else "JAV HD Studio",
                        providerId = when {
                            lower.startsWith("123av_") -> "123av"
                            lower.startsWith("javtiful_") -> "javtiful"
                            lower.startsWith("supjav_") -> "supjav"
                            else -> "jav_all"
                        }
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cross-provider JAV fallback note: ${e.message}")
        }

        null
    }

    suspend fun resolveStreamsForCode(code: String): List<PlayableStreamOption> = withContext(Dispatchers.IO) {
        val options = mutableListOf<PlayableStreamOption>()
        val cleanCode = code.trim().uppercase()
        if (cleanCode.isBlank()) return@withContext emptyList()

        try {
            // 1. Try Eporner JAV Search first (fastest & direct MP4)
            val epItems = EpornerProvider.search(cleanCode, limit = 3, page = 1).ifEmpty {
                EpornerProvider.search("JAV $cleanCode", limit = 3, page = 1)
            }
            for (epItem in epItems) {
                val epStream = EpornerProvider.getStreamData(epItem.id)
                if (epStream != null && epStream.availableStreamOptions.isNotEmpty()) {
                    options.addAll(epStream.availableStreamOptions)
                    break
                }
            }
        } catch (_: Exception) {}

        if (options.isEmpty()) {
            try {
                // 2. Try XNXX JAV Search
                val xnItems = XnxxProvider.search("jav $cleanCode", limit = 3, page = 1)
                for (xnItem in xnItems) {
                    val xnStream = XnxxProvider.getStreamData(xnItem.id)
                    if (xnStream != null && xnStream.availableStreamOptions.isNotEmpty()) {
                        options.addAll(xnStream.availableStreamOptions)
                        break
                    }
                }
            } catch (_: Exception) {}
        }

        options.filter { !it.videoUrl.isNullOrBlank() && it.providerType != ProviderType.EMBED }.distinctBy { it.videoUrl }
    }
}
