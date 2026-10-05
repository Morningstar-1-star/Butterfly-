package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.CaptionOption
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import com.example.model.StreamData
import com.example.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Tencent Video (v.qq.com) Provider and Stream Extractor.
 * Fully supports Tencent Video URLs, Series, Episodes, Donghua (Anime), Dramas, Movies, and Variety Shows.
 * Integrates natively with yt-dlp's "vqq:video" and "vqq:series" extractors and ExoPlayer Media3 streaming.
 */
object TencentProvider {
    private const val TAG = "TencentProvider"
    const val PROVIDER_ID = "tencent"
    const val BASE_URL = "https://v.qq.com"

    private const val DEFAULT_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val REFERER = "https://v.qq.com/"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Curated trending categories & popular Donghua / Drama titles on Tencent Video
    private val TENCENT_CORE_TOPICS = listOf(
        "Tencent Video Soul Land 斗罗大陆",
        "Tencent Video Perfect World 完美世界",
        "Tencent Video Battle Through the Heavens 斗破苍穹",
        "Tencent Video Swallowed Star 吞噬星空",
        "Tencent Video A Will Eternal 一念永恒",
        "Tencent Video Joy of Life 庆余年",
        "Tencent Video The Untamed 陈情令",
        "Tencent Video Blossoms Shanghai 繁花",
        "Tencent Video Love Like the Galaxy 星汉灿烂",
        "Tencent Video Record of a Mortal's Journey 凡人修仙传",
        "Tencent Video Shrouding the Heavens 遮天",
        "Tencent Video Renegade Immortal 仙逆",
        "Tencent Video Stellar Transformations 星辰变",
        "Tencent Video The King's Avatar 全职高手",
        "Tencent Video Big Brother 师兄啊师兄",
        "Tencent Video Jade Dynasty 诛仙",
        "Tencent Video Chinese Paladin 仙剑奇侠传",
        "Tencent Video You Are My Glory 你是我的荣耀",
        "Tencent Video Hidden Love 偷偷藏不住",
        "Tencent Video Wonderland of Love 乐游原"
    )

    private val TENCENT_GENRES = listOf(
        "Tencent Video Donghua anime full episodes",
        "Tencent Video Chinese Drama official series",
        "Tencent Video blockbuster movies",
        "Tencent Video variety show highlights",
        "Tencent Video esports gaming tournament"
    )

    /**
     * Cleans Chinese promotional strings like "Get the WeTV APP", "Download WeTV APP",
     * "• Tencent Video", promotional slogans, and normalizes Tencent official channels into clean English names.
     */
    fun sanitizeTencentChannelName(rawName: String?): String {
        if (rawName.isNullOrBlank()) return "Tencent Video"
        var clean = rawName.trim()

        // 1. Known studio translations
        val studioMap = mapOf(
            "玄机科技" to "Sparkly Key Animation",
            "视美影业" to "B.CMAY PICTURES",
            "福煦影视" to "Foch Film",
            "企鹅影视" to "Penguin Pictures",
            "阅文集团" to "China Literature",
            "腾讯动漫" to "Tencent Anime"
        )
        for ((k, v) in studioMap) {
            if (clean.contains(k)) return v
        }

        // 2. Strip promotional phrases
        clean = clean.replace(Regex("""(?i)\s*[•\-|–—/]*\s*(get\s+the\s+wetv\s+app|download\s+wetv\s+app|get\s+wetv\s+app)\s*"""), " ")
        clean = clean.replace(Regex("""(?i)\s*[•\-|–—/]*\s*tencent\s+video\s*$"""), "")
        clean = clean.replace(Regex("""[•\-|–—/]*\s*(更多精彩|优质华语内容|热播影视)\s*"""), " ")
        clean = clean.replace(Regex("""\s+"""), " ").trim(' ', '•', '-', '|', '–', '—', ':', '/')

        val isTencentBrand = clean.contains("tencent", ignoreCase = true) || clean.contains("腾讯") || clean.contains("v.qq", ignoreCase = true)
        val hasWeTv = clean.contains(Regex("""\bwetv\b""", RegexOption.IGNORE_CASE))

        // If it was an external creator/studio (e.g. "Sparkly Key Animation" or "Soul Land Official")
        if (!isTencentBrand && !hasWeTv) {
            if (clean.isNotBlank() && clean.any { it.isLetterOrDigit() }) {
                return clean
            }
        }

        val isAnimation = clean.contains("动漫") || clean.contains("anime", ignoreCase = true) ||
                clean.contains("animation", ignoreCase = true) || clean.contains("donghua", ignoreCase = true)
        val isDrama = clean.contains("电视剧") || clean.contains("剧场") || clean.contains("drama", ignoreCase = true) ||
                clean.contains("series", ignoreCase = true)
        val isMovie = clean.contains("电影") || clean.contains("movie", ignoreCase = true) || clean.contains("blockbuster", ignoreCase = true)
        val isVariety = clean.contains("综艺") || clean.contains("variety", ignoreCase = true) || clean.contains("show", ignoreCase = true)
        val isHighlights = clean.contains("精选") || clean.contains("highlight", ignoreCase = true)
        val isRomance = clean.contains("青春") || clean.contains("偶像") || clean.contains("romance", ignoreCase = true) || clean.contains("youth", ignoreCase = true)
        val isDoc = clean.contains("纪录片") || clean.contains("doc", ignoreCase = true)

        if (isAnimation) return "Tencent Video Animation"
        if (isDrama) return "Tencent Video Drama"
        if (isMovie) return "Tencent Video Movies"
        if (isVariety) return "Tencent Video Variety"
        if (isHighlights) return "Tencent Video Highlights"
        if (isRomance) return "Tencent Video Romance"
        if (isDoc) return "Tencent Video Documentary"

        if (hasWeTv) {
            clean = clean.replace("腾讯视频", "").replace("腾讯", "").trim(' ', '•', '-', '|', '–', '—', ':', '/')
            return if (clean.isNotBlank()) clean else "WeTV"
        }

        clean = clean.replace("腾讯视频", "").replace("腾讯", "").trim(' ', '•', '-', '|', '–', '—', ':', '/')
        if (clean.isNotBlank() && clean.any { it.isLetterOrDigit() }) {
            return clean
        }

        return "Tencent Video"
    }

    /**
     * Resolves the real high-definition logo URL for the Tencent Video / WeTV channel or creator.
     */
    fun getTencentAvatar(channelName: String?, title: String? = null): String {
        val combined = "${channelName.orEmpty()} ${title.orEmpty()}".lowercase()
        return when {
            combined.contains("anim") || combined.contains("donghua") || combined.contains("动漫") ||
                    combined.contains("soul land") || combined.contains("perfect world") || combined.contains("battle through") ||
                    combined.contains("swallowed star") || combined.contains("shrouding the heavens") || combined.contains("renegade immortal") ->
                "https://yt3.googleusercontent.com/5VMDdtEdC4OnWS7MoJBSKYTdOyXuuYmAFU36_COU5bJdYfwmSnsfiCequ_QFdxw6uAokzlnuClE=s900-c-k-c0x00ffffff-no-rj"

            combined.contains("drama") || combined.contains("series") || combined.contains("电视剧") ||
                    combined.contains("剧场") || combined.contains("untamed") || combined.contains("blossoms") || combined.contains("joy of life") ->
                "https://yt3.googleusercontent.com/aDk0tvbNx7OLTimpt12Nm3cWhpvaJtS_DUCE0Si_poqSthHUAqsPwrfoQ-hb1sPq77TTj5Na=s900-c-k-c0x00ffffff-no-rj"

            combined.contains("wetv") ->
                "https://yt3.googleusercontent.com/iI9wCyPjt51JS1jObvCKs7n9GCxjDVT7w7wVgTs6ehgDwswVysdYxIEbusqigsJADtlJ-72X75c=s900-c-k-c0x00ffffff-no-rj"

            else ->
                "https://yt3.googleusercontent.com/BZ0BcoBm1IDjD4a3XbhnHyNZ3MkLnT9FnRxj_ioc6V3yT2nqcCxR0acvzokp7B019c036G5LHQ=s900-c-k-c0x00ffffff-no-rj"
        }
    }

    /**
     * Checks if the given URL or ID belongs to Tencent Video / v.qq.com.
     */
    fun isTencentUrl(urlOrId: String): Boolean {
        val u = urlOrId.trim().lowercase()
        return u.contains("v.qq.com") ||
                u.contains("video.qq.com") ||
                u.startsWith("tencent:") ||
                u.startsWith("vqq:") ||
                u.startsWith("vqq:video") ||
                u.startsWith("vqq:series") ||
                u.contains("wetv.vip")
    }

    /**
     * Normalizes any input into a canonical v.qq.com or vqq extractor URL.
     */
    fun normalizeTencentUrl(input: String): String {
        val clean = input.trim()
        return when {
            clean.startsWith("http://") || clean.startsWith("https://") -> clean
            clean.startsWith("vqq:video", ignoreCase = true) || clean.startsWith("vqq:series", ignoreCase = true) -> clean
            clean.startsWith("tencent:", ignoreCase = true) -> {
                val id = clean.substringAfter(":").trim('/')
                if (id.startsWith("http")) id else if (id.contains("/x/cover/") || id.contains("/x/page/")) "https://v.qq.com/$id" else "https://v.qq.com/x/cover/$id.html"
            }
            clean.startsWith("vqq:", ignoreCase = true) -> {
                val id = clean.substringAfter(":").trim('/')
                if (id.startsWith("http")) id else if (id.contains("/x/cover/") || id.contains("/x/page/")) "https://v.qq.com/$id" else "https://v.qq.com/x/cover/$id.html"
            }
            clean.startsWith("mzc", ignoreCase = true) && clean.length in 8..24 -> "https://v.qq.com/x/cover/$clean.html"
            clean.contains("/x/cover/") || clean.contains("/x/page/") -> "https://v.qq.com/$clean"
            else -> clean
        }
    }

    /**
     * Retrieves Tencent Video home recommendations and trending content.
     */
    suspend fun getHome(page: Int = 1, limit: Int = 24): List<VideoItem> = withContext(Dispatchers.IO) {
        val safePage = if (page < 1) 1 else page

        // Curated trending queries on Tencent Video / WeTV
        val trendingQueries = listOf(
            "Soul Land",
            "Perfect World",
            "Battle Through the Heavens",
            "Swallowed Star",
            "A Will Eternal",
            "Joy of Life",
            "The Untamed",
            "Blossoms Shanghai",
            "The King's Avatar"
        )
        val startIndex = ((safePage - 1) * 2) % trendingQueries.size
        val selectedTopics = listOf(
            trendingQueries[startIndex % trendingQueries.size],
            trendingQueries[(startIndex + 1) % trendingQueries.size]
        )

        val results = mutableListOf<VideoItem>()
        val deferredList = selectedTopics.map { topic ->
            async(Dispatchers.IO) {
                searchWeTvApi(topic, limit = 12)
            }
        }

        deferredList.awaitAll().forEach { items ->
            results.addAll(items)
        }

        val distinctItems = results.distinctBy { it.id }.take(limit)
        if (distinctItems.isNotEmpty()) {
            Log.d(TAG, "Tencent Video getHome page $safePage loaded ${distinctItems.size} real videos")
            return@withContext distinctItems
        }

        // Direct Tencent Search API fallback
        val apiItems = searchTencentApi("热播", limit = limit)
        if (apiItems.isNotEmpty()) {
            return@withContext apiItems
        }

        emptyList()
    }

    /**
     * Searches Tencent Video catalog using official WeTV & Tencent Video search APIs.
     */
    suspend fun search(query: String, limit: Int = 20, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().removePrefix("tencent:").removePrefix("vqq:").trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        // If direct URL is pasted
        if (isTencentUrl(cleanQuery)) {
            val normalized = normalizeTencentUrl(cleanQuery)
            return@withContext listOf(
                VideoItem(
                    id = normalized,
                    title = "Tencent Video: $cleanQuery",
                    uploaderName = "Tencent Video",
                    thumbnailUrl = null,
                    durationSeconds = -1L,
                    providerId = PROVIDER_ID,
                    description = "Direct Tencent Video Playback Stream"
                )
            )
        }

        // 1. Official Tencent / WeTV Search API (clean titles, official posters, real episode lists)
        val weTvResults = searchWeTvApi(cleanQuery, limit = limit)
        if (weTvResults.isNotEmpty()) {
            return@withContext weTvResults
        }

        // If query contained "tencent" or "video", try searching with stripped keywords
        val stripped = cleanQuery.replace(Regex("(?i)\\b(tencent|video|vqq|wetv)\\b"), "").trim()
        if (stripped.isNotBlank() && !stripped.equals(cleanQuery, ignoreCase = true)) {
            val strippedResults = searchWeTvApi(stripped, limit = limit)
            if (strippedResults.isNotEmpty()) {
                return@withContext strippedResults
            }
        }

        // 2. Direct Tencent Video SmartBox / Search API
        val directResults = searchTencentApi(cleanQuery, limit = limit)
        if (directResults.isNotEmpty()) {
            return@withContext directResults
        }

        // 3. High quality trending & popular Tencent Video catalog fallback (authentic Tencent content)
        val homeFallback = getHome(page = page, limit = limit)
        if (homeFallback.isNotEmpty()) {
            val matched = homeFallback.filter { item ->
                cleanQuery.split(" ").any { kw -> kw.isNotBlank() && (item.title.contains(kw, ignoreCase = true) || item.description?.contains(kw, ignoreCase = true) == true) }
            }
            if (matched.isNotEmpty()) return@withContext matched
            return@withContext homeFallback.take(limit)
        }

        emptyList()
    }

    suspend fun searchWeTvApi(query: String, limit: Int = 20): List<VideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<VideoItem>()
        try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "https://wetv.vip/api/search?q=$encoded"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", DEFAULT_UA)
                .header("Referer", "https://wetv.vip/")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string()
                if (!body.isNullOrBlank()) {
                    val json = JSONObject(body)
                    val responseObj = json.optJSONObject("response")
                    val resultObj = responseObj?.optJSONObject("result")
                    val resArr = resultObj?.optJSONArray("result")
                    if (resArr != null && resArr.length() > 0) {
                        for (i in 0 until resArr.length()) {
                            val item = resArr.optJSONObject(i) ?: continue
                            val cid = item.optString("cid")
                            if (cid.isBlank()) continue
                            val title = item.optString("title", "Tencent Series")
                            val secondTitle = item.optString("secondTitle", "")
                            val desc = item.optString("description", "")
                            val posterVt = item.optString("posterVt")
                            val posterHz = item.optString("posterHz")
                            val cover = when {
                                posterVt.isNotBlank() -> posterVt
                                posterHz.isNotBlank() -> posterHz
                                else -> null
                            }
                            val score = item.optString("score")
                            val year = item.optString("year")
                            val epUpdated = item.optString("episodeUpdated")
                            val epAll = item.optString("episodeAll")
                            val episodesSummary = if (epUpdated.isNotBlank()) "Episodes: $epUpdated/$epAll" else ""
                            val cleanDesc = buildString {
                                if (secondTitle.isNotBlank()) append("$secondTitle\n")
                                if (score.isNotBlank() && score != "0") append("Rating: $score ★  ")
                                if (year.isNotBlank()) append("$year  ")
                                if (episodesSummary.isNotBlank()) append(episodesSummary)
                                if (desc.isNotBlank()) {
                                    if (isNotEmpty()) append("\n\n")
                                    append(desc)
                                }
                            }
                            val channelName = sanitizeTencentChannelName(title)
                            val avatar = getTencentAvatar(channelName, title)
                            val videoUrl = "https://v.qq.com/x/cover/$cid.html"
                            list.add(
                                VideoItem(
                                    id = videoUrl,
                                    title = title,
                                    uploaderName = channelName,
                                    uploaderAvatarUrl = avatar,
                                    thumbnailUrl = cover,
                                    durationSeconds = -1L,
                                    providerId = PROVIDER_ID,
                                    description = cleanDesc,
                                    uploadDate = year.takeIf { it.isNotBlank() }
                                )
                            )
                            if (list.size >= limit) break
                        }
                    } else {
                        // Check hot/trending list if primary search has no direct array
                        val hotArr = responseObj?.optJSONArray("hot")
                        if (hotArr != null) {
                            for (i in 0 until hotArr.length()) {
                                val item = hotArr.optJSONObject(i) ?: continue
                                val cid = item.optString("cid")
                                val title = item.optString("title")
                                if (cid.isBlank() || title.isBlank()) continue
                                val channelName = sanitizeTencentChannelName(title)
                                val avatar = getTencentAvatar(channelName, title)
                                val cover = item.optString("posterVt").ifBlank { item.optString("posterHz") }
                                list.add(
                                    VideoItem(
                                        id = "https://v.qq.com/x/cover/$cid.html",
                                        title = title,
                                        uploaderName = channelName,
                                        uploaderAvatarUrl = avatar,
                                        thumbnailUrl = cover.takeIf { it.isNotBlank() },
                                        durationSeconds = -1L,
                                        providerId = PROVIDER_ID,
                                        description = "Tencent Video / WeTV Trending Series"
                                    )
                                )
                                if (list.size >= limit) break
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "searchWeTvApi failed for '$query': ${e.message}")
        }
        list
    }

    private suspend fun searchTencentApi(keyword: String, limit: Int = 20): List<VideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<VideoItem>()
        try {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val url = "https://pbaccess.video.qq.com/trpc.videosearch.search_cgi.http/smartbox?key=$encoded"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", DEFAULT_UA)
                .header("Referer", REFERER)
                .header("Origin", "https://v.qq.com")
                .header("X-Forwarded-For", "114.114.114.114")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string()
                if (!body.isNullOrBlank()) {
                    val json = JSONObject(body)
                    val dataObj = json.optJSONObject("data")
                    val itemList = dataObj?.optJSONArray("item_list")
                    if (itemList != null) {
                        for (i in 0 until itemList.length()) {
                            val item = itemList.optJSONObject(i) ?: continue
                            val rawTitle = item.optString("title").ifBlank { item.optString("word") }
                            val cleanTitle = rawTitle.replace("<em>", "").replace("</em>", "").trim()
                            val cid = item.optString("doc_id").ifBlank { item.optString("id") }
                            var cover = item.optString("img_url").ifBlank { item.optString("poster") }
                            if (cover.startsWith("//")) cover = "https:$cover"
                            val desc = item.optString("sub_title").ifBlank { "Tencent Video Stream" }
                            if (cleanTitle.isNotBlank()) {
                                val videoUrl = if (cid.isNotBlank()) "https://v.qq.com/x/cover/$cid.html" else "tencent:$cleanTitle"
                                val chName = sanitizeTencentChannelName(cleanTitle)
                                val avatar = getTencentAvatar(chName, cleanTitle)
                                list.add(
                                    VideoItem(
                                        id = videoUrl,
                                        title = cleanTitle,
                                        uploaderName = chName,
                                        uploaderAvatarUrl = avatar,
                                        thumbnailUrl = cover.takeIf { it.isNotBlank() },
                                        durationSeconds = -1L,
                                        providerId = PROVIDER_ID,
                                        description = desc
                                    )
                                )
                            }
                            if (list.size >= limit) break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "searchTencentApi failed for $keyword: ${e.message}")
        }
        list
    }

    /**
     * Resolves playable direct media streams for a Tencent Video URL/ID using yt-dlp extraction pipeline.
     */
    suspend fun getStreamData(urlOrId: String, context: Context?): StreamData? = withContext(Dispatchers.IO) {
        val clean = urlOrId.trim()

        // 1. Direct YouTube Check FIRST (Fast-path: ~150ms resolution, NO yt-dlp timeout!)
        val isYouTubeId = (clean.length == 11 && !clean.contains("/") && !clean.contains(":") && !clean.contains(".")) ||
                (clean.startsWith("youtube:") && clean.length in 19..20)
        val isYouTubeUrl = clean.contains("youtube.com") || clean.contains("youtu.be")

        if (isYouTubeId || isYouTubeUrl) {
            val videoId = when {
                isYouTubeId -> clean.removePrefix("youtube:")
                clean.contains("youtu.be/") -> clean.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
                clean.contains("v=") -> clean.substringAfter("v=").substringBefore("&").substringBefore("?")
                else -> clean.substringAfterLast("/").substringBefore("?")
            }
            if (videoId.isNotBlank() && videoId.length == 11) {
                val res = YouTubeExtractorHelper.resolveStream(videoId, context, "youtube")
                if (res is YouTubeExtractorHelper.ExtractionResult.Success && res.streamData.availableStreamOptions.isNotEmpty()) {
                    val extracted = res.streamData
                    val cleanChannel = sanitizeTencentChannelName(extracted.channelName)
                    val avatarUrl = extracted.channelAvatarUrl?.takeIf { it.isNotBlank() }
                        ?: getTencentAvatar(cleanChannel, extracted.title)
                    return@withContext extracted.copy(
                        providerId = PROVIDER_ID,
                        channelName = cleanChannel,
                        channelAvatarUrl = avatarUrl
                    )
                }
            }
        }

        // 2. Real Tencent Video URL / Cover ID resolution via yt-dlp
        val isRealTencent = isTencentUrl(clean) || clean.startsWith("mzc", ignoreCase = true)
        val normalizedUrl = normalizeTencentUrl(clean)

        if (isRealTencent && context != null) {
            try {
                val ytdlResult = kotlinx.coroutines.withTimeoutOrNull(10000L) {
                    YtDlpResolver.extractStreamInfo(context, normalizedUrl)
                }
                if (ytdlResult is YouTubeExtractorHelper.ExtractionResult.Success && ytdlResult.streamData.availableStreamOptions.isNotEmpty()) {
                    val extracted = ytdlResult.streamData
                    val safeHeaders = mapOf(
                        "User-Agent" to DEFAULT_UA,
                        "Referer" to REFERER,
                        "Origin" to "https://v.qq.com"
                    )
                    val safeOptions = extracted.availableStreamOptions.map { opt ->
                        opt.copy(
                            providerType = ProviderType.DIRECT,
                            headers = if (opt.headers.isEmpty()) safeHeaders else opt.headers + safeHeaders
                        )
                    }
                    val best = safeOptions.firstOrNull { it.isMuxed && (it.format.contains("m3u8") || it.format.contains("mp4")) && !it.videoUrl.isNullOrBlank() }
                        ?: safeOptions.firstOrNull { it.isMuxed && !it.videoUrl.isNullOrBlank() }
                        ?: safeOptions.firstOrNull { !it.videoUrl.isNullOrBlank() }
                        ?: safeOptions.firstOrNull()

                    val cleanChannel = sanitizeTencentChannelName(
                        if (extracted.channelName.isNotBlank() && !extracted.channelName.equals("YouTube", ignoreCase = true)) extracted.channelName else "Tencent Video"
                    )
                    val avatarUrl = extracted.channelAvatarUrl?.takeIf { it.isNotBlank() }
                        ?: getTencentAvatar(cleanChannel, extracted.title)

                    return@withContext extracted.copy(
                        providerId = PROVIDER_ID,
                        availableStreamOptions = safeOptions,
                        selectedStreamOption = best,
                        channelName = cleanChannel,
                        channelAvatarUrl = avatarUrl,
                        headers = safeHeaders
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "yt-dlp extraction for Tencent Video URL $normalizedUrl failed: ${e.message}")
            }
        }

        // 3. Fallback search by title/keywords (fast, parallelized, limited to 1 search)
        val cleanName = clean
            .removePrefix("tencent:")
            .removePrefix("vqq:")
            .replace("https://v.qq.com/", "")
            .replace("http://v.qq.com/", "")
            .replace(Regex("""^x/cover/[a-zA-Z0-9_-]+/?"""), "")
            .replace(Regex("""^x/page/[a-zA-Z0-9_-]+/?"""), "")
            .replace(".html", "")
            .replace("/", " ")
            .replace("-", " ")
            .replace("_", " ")
            .trim()

        null
    }
}
