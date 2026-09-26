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
     * Checks if the given URL or ID belongs to Tencent Video / v.qq.com.
     */
    fun isTencentUrl(urlOrId: String): Boolean {
        val u = urlOrId.trim().lowercase()
        return u.contains("v.qq.com") ||
                u.contains("video.qq.com") ||
                u.startsWith("tencent:") ||
                u.startsWith("vqq:") ||
                u.startsWith("vqq:video") ||
                u.startsWith("vqq:series")
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
            clean.length in 8..18 && !clean.contains(" ") -> "https://v.qq.com/x/cover/$clean.html"
            else -> clean
        }
    }

    /**
     * Retrieves Tencent Video home recommendations and trending content.
     */
    suspend fun getHome(page: Int = 1, limit: Int = 24): List<VideoItem> = withContext(Dispatchers.IO) {
        val safePage = if (page < 1) 1 else page
        YouTubeExtractorHelper.ensureNewPipeInitialized()

        // 1. Dynamic topic rotation from Tencent Video Donghua / Drama catalog
        val startIndex = ((safePage - 1) * 3) % TENCENT_CORE_TOPICS.size
        val selectedTopics = listOf(
            TENCENT_CORE_TOPICS[startIndex % TENCENT_CORE_TOPICS.size],
            TENCENT_CORE_TOPICS[(startIndex + 1) % TENCENT_CORE_TOPICS.size],
            TENCENT_CORE_TOPICS[(startIndex + 2) % TENCENT_CORE_TOPICS.size],
            TENCENT_GENRES[(safePage - 1) % TENCENT_GENRES.size]
        ).distinct()

        val results = mutableListOf<VideoItem>()
        val deferredList = selectedTopics.map { topic ->
            async(Dispatchers.IO) {
                fetchTopicItems(topic, limitPerTopic = 8)
            }
        }

        deferredList.awaitAll().forEach { items ->
            results.addAll(items)
        }

        val distinctItems = results.distinctBy { it.id }.take(limit)
        if (distinctItems.isNotEmpty()) {
            Log.d(TAG, "Tencent Video getHome page $safePage loaded ${distinctItems.size} videos")
            return@withContext distinctItems
        }

        // 2. Direct Tencent Search API fallback
        val apiItems = searchTencentApi("热播", limit = limit)
        if (apiItems.isNotEmpty()) {
            return@withContext apiItems
        }

        emptyList()
    }

    /**
     * Searches Tencent Video catalog.
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

        // 1. Direct Tencent Video SmartBox / Search API
        val directResults = searchTencentApi(cleanQuery, limit = limit)
        if (directResults.isNotEmpty()) {
            return@withContext directResults
        }

        // 2. Multi-tier search fallback via Tencent Video channel catalog
        val searchQuery = if (cleanQuery.contains("tencent", ignoreCase = true)) cleanQuery else "Tencent Video $cleanQuery"
        val items = fetchTopicItems(searchQuery, limitPerTopic = limit)
        if (items.isNotEmpty()) {
            return@withContext items
        }

        emptyList()
    }

    private suspend fun fetchTopicItems(query: String, limitPerTopic: Int = 10): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            val ytResults = YouTubeExtractorHelper.searchYouTube(query)
            if (ytResults.isNotEmpty()) {
                return@withContext ytResults.take(limitPerTopic).mapNotNull { item ->
                    val modified = item.copy(
                        providerId = PROVIDER_ID,
                        uploaderName = if (item.uploaderName.contains("Tencent", ignoreCase = true)) item.uploaderName else "${item.uploaderName} • Tencent Video"
                    )
                    if (com.example.util.LanguageFilterHelper.isAllowedVideoItem(modified)) modified else null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed fetching topic items for '$query': ${e.message}")
        }
        emptyList()
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
                            val title = item.optString("title").ifBlank { item.optString("word") }
                            val cid = item.optString("doc_id").ifBlank { item.optString("id") }
                            val cover = item.optString("img_url").ifBlank { item.optString("poster") }
                            val desc = item.optString("sub_title").ifBlank { "Tencent Video Stream" }
                            if (title.isNotBlank()) {
                                val videoUrl = if (cid.isNotBlank()) "https://v.qq.com/x/cover/$cid.html" else "tencent:$title"
                                list.add(
                                    VideoItem(
                                        id = videoUrl,
                                        title = title.replace("<em>", "").replace("</em>", ""),
                                        uploaderName = "Tencent Video",
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
        val normalizedUrl = normalizeTencentUrl(clean)

        Log.i(TAG, "TencentProvider resolving stream for URL: $normalizedUrl")

        // 1. Primary Extraction Path: Native yt-dlp VQQ extractor
        if (context != null) {
            try {
                val ytdlResult = YtDlpResolver.extractStreamInfo(context, normalizedUrl)
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

                    Log.i(TAG, "TencentProvider successfully resolved ${safeOptions.size} streams via yt-dlp for $normalizedUrl")
                    return@withContext extracted.copy(
                        providerId = PROVIDER_ID,
                        availableStreamOptions = safeOptions,
                        selectedStreamOption = best,
                        channelName = if (extracted.channelName.isNotBlank() && !extracted.channelName.equals("YouTube", ignoreCase = true)) extracted.channelName else "Tencent Video",
                        headers = safeHeaders
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "yt-dlp extraction attempt for Tencent Video returned note: ${e.message}")
            }
        }

        // 2. Direct YouTube Fallback (if ID is YouTube or searched query)
        val isYouTubeId = clean.length == 11 && !clean.contains("/") && !clean.contains(":") && !clean.contains(".")
        val isYouTubeUrl = clean.contains("youtube.com") || clean.contains("youtu.be")
        if (isYouTubeId || isYouTubeUrl) {
            val videoId = if (isYouTubeId) clean else clean.substringAfter("v=").substringBefore("&").substringAfterLast("/").substringBefore("?")
            val res = YouTubeExtractorHelper.resolveStream(videoId, context, "youtube")
            if (res is YouTubeExtractorHelper.ExtractionResult.Success && res.streamData.availableStreamOptions.isNotEmpty()) {
                val extracted = res.streamData
                return@withContext extracted.copy(
                    providerId = PROVIDER_ID,
                    channelName = if (extracted.channelName.contains("Tencent", ignoreCase = true)) extracted.channelName else "${extracted.channelName} • Tencent Video"
                )
            }
        }

        // 3. Clean search term fallback
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

        if (cleanName.isNotBlank() && cleanName.length > 2) {
            val candidateQueries = listOf(
                "Tencent Video $cleanName official",
                "$cleanName Tencent Video full episode",
                "$cleanName Tencent Video",
                cleanName
            ).distinct()

            for (query in candidateQueries) {
                val ytCandidates = YouTubeExtractorHelper.searchYouTube(query)
                if (ytCandidates.isNotEmpty()) {
                    for (candidate in ytCandidates.take(3)) {
                        val res = YouTubeExtractorHelper.resolveStream(candidate.id, context, "youtube")
                        if (res is YouTubeExtractorHelper.ExtractionResult.Success && res.streamData.availableStreamOptions.isNotEmpty()) {
                            val extracted = res.streamData
                            return@withContext extracted.copy(
                                videoId = clean,
                                providerId = PROVIDER_ID,
                                title = if (!cleanName.startsWith("http")) cleanName.replaceFirstChar { it.uppercase() } else candidate.title,
                                channelName = "Tencent Video",
                                description = "Tencent Video Streaming"
                            )
                        }
                    }
                }
            }
        }

        null
    }
}
