package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class NasaProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "nasa"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val url = "https://images-api.nasa.gov/search?media_type=video&page=$page"
        val resp = http.get(url)
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList())

        val list = parseNasaList(resp.body)
        PagedResult(items = list, nextPageToken = (page + 1).toString(), hasMore = list.isNotEmpty())
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val url = "https://images-api.nasa.gov/search?q=$query&media_type=video&page=$page"
        val resp = http.get(url)
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList())

        val list = parseNasaList(resp.body)
        PagedResult(items = list, nextPageToken = (page + 1).toString(), hasMore = list.isNotEmpty())
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val nasaId = extractId(idOrUrl)
        PluginVideoItem(
            id = nasaId,
            title = nasaId.replace("_", " "),
            uploaderName = "NASA",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val nasaId = extractId(idOrUrl)
        val collectionUrl = "https://images-api.nasa.gov/asset/$nasaId"
        val resp = http.get(collectionUrl)

        val videoStreams = mutableListOf<PluginVideoStream>()
        var hlsUrl: String? = null

        if (resp.statusCode == 200) {
            val json = JSONObject(resp.body)
            val items = json.optJSONObject("collection")?.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val itemUrl = items.getJSONObject(i).optString("href")
                if (itemUrl.endsWith(".mp4")) {
                    videoStreams.add(
                        PluginVideoStream(
                            url = itemUrl,
                            qualityLabel = if (itemUrl.contains("orig")) "1080p" else "720p",
                            format = "mp4",
                            isMuxed = true
                        )
                    )
                } else if (itemUrl.endsWith(".m3u8")) {
                    hlsUrl = itemUrl
                }
            }
        }

        PluginStreamInfo(
            id = nasaId,
            url = "https://images.nasa.gov/details-$nasaId",
            title = nasaId,
            channelName = "NASA",
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
        PluginChannel(id = "nasa", name = "NASA Video Library")
    }

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist = withContext(Dispatchers.IO) {
        PluginPlaylist(id = playlistIdOrUrl, title = "NASA Archives", uploaderName = "NASA")
    }

    override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> = withContext(Dispatchers.IO) {
        home().items.take(10)
    }

    private fun parseNasaList(jsonStr: String): List<PluginVideoItem> {
        val list = mutableListOf<PluginVideoItem>()
        try {
            val json = JSONObject(jsonStr)
            val items = json.optJSONObject("collection")?.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val dataArr = item.optJSONArray("data") ?: JSONArray()
                if (dataArr.length() > 0) {
                    val data = dataArr.getJSONObject(0)
                    val nasaId = data.optString("nasa_id")
                    val links = item.optJSONArray("links") ?: JSONArray()
                    var thumb: String? = null
                    for (j in 0 until links.length()) {
                        val l = links.getJSONObject(j)
                        if (l.optString("rel") == "preview") {
                            thumb = l.optString("href")
                            break
                        }
                    }
                    list.add(
                        PluginVideoItem(
                            id = nasaId,
                            title = data.optString("title", nasaId),
                            uploaderName = data.optString("center", "NASA"),
                            uploadDate = data.optString("date_created"),
                            thumbnailUrl = thumb,
                            providerId = providerId
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun extractId(input: String): String {
        return input.substringAfterLast("/").substringAfterLast("=")
    }
}
