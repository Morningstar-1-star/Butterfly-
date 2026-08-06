package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class EpornerProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "eporner"
    private val baseUrl = "https://www.eporner.com/api/v2/web/search"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val url = "$baseUrl/?query=hd&per_page=20&page=$page&thumbsize=big&order=top-weekly"
        val resp = http.get(url)
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList())

        val (items, hasMore) = parseVideoList(resp.body)
        PagedResult(items = items, nextPageToken = (page + 1).toString(), hasMore = hasMore)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl/?query=$encodedQuery&per_page=20&page=$page&thumbsize=big"
        val resp = http.get(url)
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList())

        val (items, hasMore) = parseVideoList(resp.body)
        PagedResult(items = items, nextPageToken = (page + 1).toString(), hasMore = hasMore)
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val id = extractId(idOrUrl)
        val url = "$baseUrl/?id=$id&thumbsize=big"
        val resp = http.get(url)
        if (resp.statusCode == 200) {
            val (items, _) = parseVideoList(resp.body)
            if (items.isNotEmpty()) return@withContext items[0]
        }
        PluginVideoItem(
            id = id,
            title = "Eporner Video #$id",
            uploaderName = "Eporner",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val id = extractId(idOrUrl)
        val embedUrl = "https://www.eporner.com/embed/$id/"
        var title = "Eporner Video"
        var views = 0L

        val resp = http.get("$baseUrl/?id=$id&thumbsize=big")
        if (resp.statusCode == 200) {
            try {
                val json = JSONObject(resp.body)
                val arr = json.optJSONArray("videos") ?: JSONArray()
                if (arr.length() > 0) {
                    val v = arr.getJSONObject(0)
                    title = v.optString("title", title)
                    views = v.optLong("views", 0)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        PluginStreamInfo(
            id = id,
            url = embedUrl,
            title = title,
            channelName = "Eporner",
            viewCount = views,
            likeCount = 0,
            description = "Eporner Embedded HD Stream",
            hlsUrl = null,
            videoStreams = listOf(
                PluginVideoStream(
                    url = embedUrl,
                    qualityLabel = "Web Embed / Auto Play",
                    format = "embed",
                    isMuxed = true
                )
            )
        )
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> {
        return PagedResult(emptyList())
    }

    override suspend fun getSubtitles(idOrUrl: String): List<PluginSubtitle> {
        return emptyList()
    }

    override suspend fun getChannel(channelIdOrUrl: String): PluginChannel {
        return PluginChannel(id = channelIdOrUrl, name = "Eporner Studio")
    }

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist {
        return PluginPlaylist(id = playlistIdOrUrl, title = "Eporner Playlist", uploaderName = "Eporner")
    }

    override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> = withContext(Dispatchers.IO) {
        val resp = http.get("$baseUrl/?query=popular&per_page=10&thumbsize=big")
        if (resp.statusCode == 200) parseVideoList(resp.body).first else emptyList()
    }

    private fun parseVideoList(jsonStr: String): Pair<List<PluginVideoItem>, Boolean> {
        val list = mutableListOf<PluginVideoItem>()
        return try {
            val json = JSONObject(jsonStr)
            val arr = json.optJSONArray("videos") ?: JSONArray()
            val totalPages = json.optInt("total_pages", 1)
            val page = json.optInt("page", 1)

            for (i in 0 until arr.length()) {
                val v = arr.getJSONObject(i)
                list.add(
                    PluginVideoItem(
                        id = v.optString("id"),
                        title = v.optString("title"),
                        uploaderName = "Eporner",
                        uploaderAvatarUrl = null,
                        viewCount = v.optLong("views", 0),
                        durationSeconds = v.optLong("length_sec", 0),
                        uploadDate = v.optString("added"),
                        thumbnailUrl = v.optString("default_thumb")
                            .ifEmpty { v.optString("big_thumb") },
                        providerId = providerId
                    )
                )
            }
            Pair(list, page < totalPages)
        } catch (e: Exception) {
            Pair(emptyList(), false)
        }
    }

    private fun extractId(input: String): String {
        return input.substringAfterLast("/").substringAfterLast("=").substringBefore("-").substringBefore(".")
    }
}
