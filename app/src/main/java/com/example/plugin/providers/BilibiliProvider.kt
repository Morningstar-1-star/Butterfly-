package com.example.plugin.providers

import android.content.Context
import android.util.Log
import com.example.extractor.YtDlpResolver
import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

class BilibiliProvider(
    private val context: Context? = null,
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "bilibili"

    @Volatile
    private var cachedBuvid3: String? = null

    private suspend fun getBuvid3(): String {
        cachedBuvid3?.let { if (it.isNotBlank()) return it }
        return try {
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                "Referer" to "https://www.bilibili.com/"
            )
            val resp = http.get("https://api.bilibili.com/x/frontend/finger/spi", headers)
            if (resp.statusCode == 200) {
                val root = JSONObject(resp.body)
                val b3 = root.optJSONObject("data")?.optString("b_3") ?: ""
                if (b3.isNotBlank()) {
                    cachedBuvid3 = b3
                    b3
                } else ""
            } else ""
        } catch (e: Exception) {
            Log.w("BilibiliProvider", "Failed to fetch buvid3 cookie: ${e.message}")
            ""
        }
    }

    private suspend fun buildRequestHeaders(): Map<String, String> {
        val buvid3 = getBuvid3()
        val headers = mutableMapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
            "Referer" to "https://www.bilibili.com/",
            "Accept" to "application/json, text/plain, */*",
            "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8"
        )
        if (buvid3.isNotBlank()) {
            headers["Cookie"] = "buvid3=$buvid3; buvid4=$buvid3"
        }
        return headers
    }

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val headers = buildRequestHeaders()

        // Attempt 1: Popular API
        val popularUrl = "https://api.bilibili.com/x/web-interface/popular?ps=25&pn=$page"
        var items = parseBilibiliListApi(popularUrl, headers)

        // Attempt 2: Ranking API
        if (items.isEmpty()) {
            val rankingUrl = "https://api.bilibili.com/x/web-interface/ranking/v2?rid=0&type=all"
            items = parseBilibiliListApi(rankingUrl, headers)
        }

        // Attempt 3: Recommended Feed API
        if (items.isEmpty()) {
            val rcmdUrl = "https://api.bilibili.com/x/web-interface/index/top/feed/rcmd?ps=25"
            items = parseBilibiliListApi(rcmdUrl, headers)
        }

        PagedResult(
            items = items,
            nextPageToken = (page + 1).toString(),
            hasMore = items.isNotEmpty()
        )
    }

    private suspend fun parseBilibiliListApi(url: String, headers: Map<String, String>): List<PluginVideoItem> {
        return try {
            val resp = http.get(url, headers)
            if (resp.statusCode != 200) return emptyList()

            val root = JSONObject(resp.body)
            if (root.optInt("code", -1) != 0) return emptyList()

            val dataObj = root.optJSONObject("data") ?: return emptyList()
            val listArr = dataObj.optJSONArray("list") ?: dataObj.optJSONArray("item") ?: JSONArray()

            val items = mutableListOf<PluginVideoItem>()
            for (i in 0 until listArr.length()) {
                val itemObj = listArr.getJSONObject(i)
                val bvid = itemObj.optString("bvid").ifBlank {
                    val aid = itemObj.optLong("aid", 0L)
                    if (aid > 0) "av$aid" else ""
                }
                if (bvid.isBlank()) continue

                val rawTitle = itemObj.optString("title")
                val cleanTitle = rawTitle.replace(Regex("<[^>]*>"), "").trim()

                var pic = itemObj.optString("pic")
                if (pic.startsWith("//")) pic = "https:$pic"

                val ownerObj = itemObj.optJSONObject("owner")
                val ownerName = ownerObj?.optString("name") ?: itemObj.optString("author", "Bilibili Uploader")
                var ownerFace = ownerObj?.optString("face")
                if (ownerFace?.startsWith("//") == true) ownerFace = "https:$ownerFace"
                val ownerMid = ownerObj?.optLong("mid", 0L) ?: 0L

                val statObj = itemObj.optJSONObject("stat")
                val viewCount = statObj?.optLong("view", 0L) ?: itemObj.optLong("play", 0L)

                val durationSec = itemObj.optLong("duration", 0L)

                items.add(
                    PluginVideoItem(
                        id = bvid,
                        title = cleanTitle.ifBlank { "Bilibili Video ($bvid)" },
                        uploaderName = ownerName.ifBlank { "Bilibili Uploader" },
                        uploaderUrl = if (ownerMid > 0) "https://space.bilibili.com/$ownerMid" else null,
                        uploaderAvatarUrl = ownerFace,
                        viewCount = viewCount,
                        durationSeconds = durationSec,
                        thumbnailUrl = pic.takeIf { it.isNotBlank() },
                        providerId = providerId
                    )
                )
            }
            items
        } catch (e: Exception) {
            Log.e("BilibiliProvider", "Error in parseBilibiliListApi for $url: ${e.message}")
            emptyList()
        }
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext PagedResult(emptyList(), hasMore = false)
        val page = pageToken?.toIntOrNull() ?: 1
        val encodedQuery = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        val headers = buildRequestHeaders()

        // Attempt 1: Search type API
        val searchTypeUrl = "https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword=$encodedQuery&page=$page"
        var items = parseBilibiliSearchApi(searchTypeUrl, headers)

        // Attempt 2: Search all API
        if (items.isEmpty()) {
            val searchAllUrl = "https://api.bilibili.com/x/web-interface/search/all/v2?keyword=$encodedQuery&page=$page"
            items = parseBilibiliSearchApi(searchAllUrl, headers)
        }

        // Attempt 3: HTML Web Search Scraping
        if (items.isEmpty()) {
            items = parseBilibiliHtmlSearch(query, page)
        }

        PagedResult(
            items = items,
            nextPageToken = (page + 1).toString(),
            hasMore = items.isNotEmpty()
        )
    }

    private suspend fun parseBilibiliSearchApi(url: String, headers: Map<String, String>): List<PluginVideoItem> {
        return try {
            val resp = http.get(url, headers)
            if (resp.statusCode != 200) return emptyList()

            val root = JSONObject(resp.body)
            if (root.optInt("code", -1) != 0) return emptyList()

            val dataObj = root.optJSONObject("data") ?: return emptyList()
            val resultArr = dataObj.optJSONArray("result")
                ?: dataObj.optJSONObject("result")?.optJSONArray("video")
                ?: JSONArray()

            val items = mutableListOf<PluginVideoItem>()
            for (i in 0 until resultArr.length()) {
                val itemObj = resultArr.getJSONObject(i)
                val bvid = itemObj.optString("bvid").ifBlank {
                    val aid = itemObj.optLong("aid", 0L)
                    if (aid > 0) "av$aid" else ""
                }
                if (bvid.isBlank()) continue

                val rawTitle = itemObj.optString("title")
                val cleanTitle = rawTitle.replace(Regex("<[^>]*>"), "").trim()

                var pic = itemObj.optString("pic")
                if (pic.startsWith("//")) pic = "https:$pic"

                val author = itemObj.optString("author").ifBlank { "Bilibili Uploader" }
                val viewCount = itemObj.optLong("play", 0L)
                val durationStr = itemObj.optString("duration")
                val durationSec = parseDurationSeconds(durationStr)

                items.add(
                    PluginVideoItem(
                        id = bvid,
                        title = cleanTitle.ifBlank { "Bilibili Video ($bvid)" },
                        uploaderName = author,
                        viewCount = viewCount,
                        durationSeconds = durationSec,
                        thumbnailUrl = pic.takeIf { it.isNotBlank() },
                        providerId = providerId
                    )
                )
            }
            items
        } catch (e: Exception) {
            Log.e("BilibiliProvider", "Error in parseBilibiliSearchApi for $url: ${e.message}")
            emptyList()
        }
    }

    private fun parseBilibiliHtmlSearch(query: String, page: Int): List<PluginVideoItem> {
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val htmlUrl = "https://search.bilibili.com/all?keyword=$encoded&page=$page"
            val doc = Jsoup.connect(htmlUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .referrer("https://www.bilibili.com/")
                .timeout(10000)
                .get()

            val cardElements = doc.select(".bili-video-card, .video-item, .search-card")
            val items = mutableListOf<PluginVideoItem>()

            for (card in cardElements) {
                val link = card.select("a[href*=/video/]").firstOrNull()?.attr("href") ?: ""
                val bvid = Regex("BV[a-zA-Z0-9]{10}", RegexOption.IGNORE_CASE).find(link)?.value
                    ?: Regex("av[0-9]+", RegexOption.IGNORE_CASE).find(link)?.value
                    ?: continue

                val title = card.select(".bili-video-card__info--tit, .title").text().trim()
                val author = card.select(".bili-video-card__info--author, .up-name").text().trim()
                var pic = card.select("img").attr("src").ifBlank { card.select("img").attr("data-src") }
                if (pic.startsWith("//")) pic = "https:$pic"

                items.add(
                    PluginVideoItem(
                        id = bvid,
                        title = title.ifBlank { "Bilibili Video ($bvid)" },
                        uploaderName = author.ifBlank { "Bilibili Uploader" },
                        thumbnailUrl = pic.takeIf { it.isNotBlank() },
                        providerId = providerId
                    )
                )
            }
            items
        } catch (e: Exception) {
            Log.e("BilibiliProvider", "Error parsing Bilibili HTML search: ${e.message}")
            emptyList()
        }
    }

    private fun parseDurationSeconds(durationStr: String?): Long {
        if (durationStr.isNullOrBlank()) return 0L
        return try {
            val parts = durationStr.split(":").map { it.trim().toLong() }
            when (parts.size) {
                1 -> parts[0]
                2 -> parts[0] * 60 + parts[1]
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                else -> 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val streams = getStreams(idOrUrl)
        PluginVideoItem(
            id = streams.id,
            title = streams.title,
            uploaderName = streams.channelName,
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val ctx = context ?: ArchiveOrgProvider.contextRef
        if (ctx != null) {
            when (val res = YtDlpResolver.extractStreamInfo(ctx, idOrUrl)) {
                is YtDlpResolver.ExtractionResult.Success -> {
                    val sd = res.streamData
                    val videoStreams = res.playableOptions.map { opt ->
                        PluginVideoStream(
                            url = opt.videoUrl ?: "",
                            qualityLabel = opt.qualityLabel,
                            format = opt.format,
                            height = 0,
                            fps = 30,
                            isMuxed = opt.isMuxed
                        )
                    }

                    return@withContext PluginStreamInfo(
                        id = sd.videoId,
                        url = YtDlpResolver.normalizeUrl(idOrUrl),
                        title = sd.title,
                        channelName = sd.channelName,
                        description = sd.description,
                        videoStreams = videoStreams,
                        thumbnailUrl = sd.thumbnailUrl
                    )
                }
                is YtDlpResolver.ExtractionResult.Error -> {
                    // Fallback
                }
            }
        }

        val cleanUrl = YtDlpResolver.normalizeUrl(idOrUrl)
        PluginStreamInfo(
            id = idOrUrl,
            url = cleanUrl,
            title = "Bilibili Video",
            channelName = "Bilibili Uploader"
        )
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> =
        PagedResult(items = emptyList())

    override suspend fun getSubtitles(idOrUrl: String): List<PluginSubtitle> = emptyList()

    override suspend fun getChannel(channelIdOrUrl: String): PluginChannel =
        PluginChannel(id = channelIdOrUrl, name = "Bilibili Channel")

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist =
        PluginPlaylist(id = playlistIdOrUrl, title = "Bilibili Playlist", uploaderName = "Bilibili")

    override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> = emptyList()
}
