package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class JsonProvider(
    private val jsonUrl: String = "",
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "json_provider"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        if (jsonUrl.isBlank()) return@withContext PagedResult(emptyList())
        val resp = http.get(jsonUrl)
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList())

        val list = parseJsonFeed(resp.body)
        PagedResult(items = list)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        if (query.startsWith("http") && query.endsWith(".json")) {
            val custom = JsonProvider(query, http)
            custom.home(pageToken)
        } else {
            val list = home().items.filter { it.title.contains(query, ignoreCase = true) }
            PagedResult(items = list)
        }
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val items = home().items
        items.find { it.id == idOrUrl } ?: PluginVideoItem(
            id = idOrUrl,
            title = "JSON Video",
            uploaderName = "JSON Feed",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val isHls = idOrUrl.contains(".m3u8")
        PluginStreamInfo(
            id = idOrUrl,
            url = idOrUrl,
            title = idOrUrl.substringAfterLast("/"),
            channelName = "JSON Feed Provider",
            hlsUrl = if (isHls) idOrUrl else null,
            videoStreams = if (!isHls) listOf(
                PluginVideoStream(
                    url = idOrUrl,
                    qualityLabel = "1080p",
                    format = "mp4",
                    isMuxed = true
                )
            ) else emptyList()
        )
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> = withContext(Dispatchers.IO) {
        PagedResult(items = emptyList())
    }

    override suspend fun getSubtitles(idOrUrl: String): List<PluginSubtitle> = withContext(Dispatchers.IO) {
        emptyList()
    }

    override suspend fun getChannel(channelIdOrUrl: String): PluginChannel = withContext(Dispatchers.IO) {
        PluginChannel(id = "json", name = "JSON Channel")
    }

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist = withContext(Dispatchers.IO) {
        PluginPlaylist(id = playlistIdOrUrl, title = "JSON Feed Playlist", uploaderName = "JSON")
    }

    override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> = withContext(Dispatchers.IO) {
        home().items
    }

    private fun parseJsonFeed(jsonStr: String): List<PluginVideoItem> {
        val list = mutableListOf<PluginVideoItem>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    PluginVideoItem(
                        id = obj.optString("url", obj.optString("id")),
                        title = obj.optString("title"),
                        uploaderName = obj.optString("uploader", "JSON Creator"),
                        thumbnailUrl = obj.optString("thumbnail"),
                        durationSeconds = obj.optLong("duration", 0),
                        providerId = providerId
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
