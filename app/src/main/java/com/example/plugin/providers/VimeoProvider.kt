package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class VimeoProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "vimeo"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val url = "https://vimeo.com/api/v2/channel/staffpicks/videos.json?page=$page"
        val resp = http.get(url)
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList())

        val items = parseVimeoList(resp.body)
        PagedResult(items = items, nextPageToken = (page + 1).toString(), hasMore = items.isNotEmpty())
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        home(pageToken)
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val id = extractId(idOrUrl)
        val url = "https://vimeo.com/api/v2/video/$id.json"
        val resp = http.get(url)
        val jsonArray = JSONArray(resp.body)
        val json = jsonArray.getJSONObject(0)
        PluginVideoItem(
            id = json.optString("id", id),
            title = json.optString("title", "Vimeo Video"),
            uploaderName = json.optString("user_name", "Vimeo Creator"),
            uploaderUrl = json.optString("user_url"),
            uploaderAvatarUrl = json.optString("user_portrait_large"),
            viewCount = json.optLong("stats_number_of_plays", 0),
            durationSeconds = json.optLong("duration", 0),
            uploadDate = json.optString("upload_date"),
            thumbnailUrl = json.optString("thumbnail_large"),
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val id = extractId(idOrUrl)
        val configUrl = "https://player.vimeo.com/video/$id/config"
        val resp = http.get(configUrl)
        val json = JSONObject(resp.body)

        val requestObj = json.optJSONObject("request")
        val filesObj = requestObj?.optJSONObject("files")
        val progressiveArr = filesObj?.optJSONArray("progressive") ?: JSONArray()

        val videoStreams = mutableListOf<PluginVideoStream>()
        for (i in 0 until progressiveArr.length()) {
            val f = progressiveArr.getJSONObject(i)
            videoStreams.add(
                PluginVideoStream(
                    url = f.optString("url"),
                    qualityLabel = f.optString("quality", "720p"),
                    format = "mp4",
                    width = f.optInt("width", 0),
                    height = f.optInt("height", 0),
                    fps = f.optInt("fps", 30),
                    isMuxed = true
                )
            )
        }

        val hlsUrl = filesObj?.optJSONObject("hls")?.optJSONObject("cdns")?.let { cdns ->
            val keys = cdns.keys()
            if (keys.hasNext()) cdns.getJSONObject(keys.next()).optString("url") else null
        }

        val videoMeta = json.optJSONObject("video")
        PluginStreamInfo(
            id = id,
            url = "https://vimeo.com/$id",
            title = videoMeta?.optString("title") ?: "Vimeo Video",
            channelName = videoMeta?.optJSONObject("owner")?.optString("name") ?: "Vimeo",
            videoStreams = videoStreams,
            hlsUrl = hlsUrl
        )
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> = withContext(Dispatchers.IO) {
        PagedResult(items = emptyList())
    }

    override suspend fun getSubtitles(idOrUrl: String): List<PluginSubtitle> = withContext(Dispatchers.IO) {
        emptyList()
    }

    override suspend fun getChannel(channelIdOrUrl: String): PluginChannel = withContext(Dispatchers.IO) {
        val id = extractId(channelIdOrUrl)
        PluginChannel(id = id, name = id)
    }

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist = withContext(Dispatchers.IO) {
        val id = extractId(playlistIdOrUrl)
        PluginPlaylist(id = id, title = "Vimeo Album", uploaderName = "Vimeo")
    }

    override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> = withContext(Dispatchers.IO) {
        home().items.take(10)
    }

    private fun parseVimeoList(jsonStr: String): List<PluginVideoItem> {
        val list = mutableListOf<PluginVideoItem>()
        val jsonArray = JSONArray(jsonStr)
        for (i in 0 until jsonArray.length()) {
            val v = jsonArray.getJSONObject(i)
            list.add(
                PluginVideoItem(
                    id = v.optString("id"),
                    title = v.optString("title"),
                    uploaderName = v.optString("user_name", "Vimeo Creator"),
                    uploaderUrl = v.optString("user_url"),
                    uploaderAvatarUrl = v.optString("user_portrait_large"),
                    viewCount = v.optLong("stats_number_of_plays", 0),
                    durationSeconds = v.optLong("duration", 0),
                    uploadDate = v.optString("upload_date"),
                    thumbnailUrl = v.optString("thumbnail_large"),
                    providerId = providerId
                )
            )
        }
        return list
    }

    private fun extractId(input: String): String {
        return input.substringAfterLast("/").substringAfterLast("=")
    }
}
