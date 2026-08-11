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

class BilibiliProvider(
    private val context: Context? = null,
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "bilibili"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val url = "https://api.bilibili.com/x/web-interface/popular?ps=25&pn=$page"
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
            "Referer" to "https://www.bilibili.com/"
        )
        val resp = http.get(url, headers)
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList(), hasMore = false)

        try {
            val root = JSONObject(resp.body)
            if (root.optInt("code", -1) != 0) return@withContext PagedResult(emptyList(), hasMore = false)

            val dataObj = root.optJSONObject("data") ?: return@withContext PagedResult(emptyList(), hasMore = false)
            val listArr = dataObj.optJSONArray("list") ?: JSONArray()

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
                val ownerName = ownerObj?.optString("name") ?: "Bilibili Uploader"
                var ownerFace = ownerObj?.optString("face")
                if (ownerFace?.startsWith("//") == true) ownerFace = "https:$ownerFace"
                val ownerMid = ownerObj?.optLong("mid", 0L) ?: 0L

                val statObj = itemObj.optJSONObject("stat")
                val viewCount = statObj?.optLong("view", 0L) ?: 0L

                val durationSec = itemObj.optLong("duration", 0L)

                items.add(
                    PluginVideoItem(
                        id = bvid,
                        title = cleanTitle.ifBlank { "Bilibili Video ($bvid)" },
                        uploaderName = ownerName,
                        uploaderUrl = if (ownerMid > 0) "https://space.bilibili.com/$ownerMid" else null,
                        uploaderAvatarUrl = ownerFace,
                        viewCount = viewCount,
                        durationSeconds = durationSec,
                        thumbnailUrl = pic.takeIf { it.isNotBlank() },
                        providerId = providerId
                    )
                )
            }

            PagedResult(
                items = items,
                nextPageToken = (page + 1).toString(),
                hasMore = items.isNotEmpty()
            )
        } catch (e: Exception) {
            Log.e("BilibiliProvider", "Error parsing home feed: ${e.message}", e)
            PagedResult(emptyList(), hasMore = false)
        }
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext PagedResult(emptyList(), hasMore = false)
        val page = pageToken?.toIntOrNull() ?: 1
        val encodedQuery = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        val url = "https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword=$encodedQuery&page=$page"
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
            "Referer" to "https://www.bilibili.com/"
        )
        val resp = http.get(url, headers)
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList(), hasMore = false)

        try {
            val root = JSONObject(resp.body)
            if (root.optInt("code", -1) != 0) return@withContext PagedResult(emptyList(), hasMore = false)

            val dataObj = root.optJSONObject("data") ?: return@withContext PagedResult(emptyList(), hasMore = false)
            val resultArr = dataObj.optJSONArray("result") ?: JSONArray()

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

            PagedResult(
                items = items,
                nextPageToken = (page + 1).toString(),
                hasMore = items.isNotEmpty()
            )
        } catch (e: Exception) {
            Log.e("BilibiliProvider", "Error parsing search results for '$query': ${e.message}", e)
            PagedResult(emptyList(), hasMore = false)
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
