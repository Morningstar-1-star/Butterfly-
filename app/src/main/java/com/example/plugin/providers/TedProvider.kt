package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class TedProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "ted"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val url = "https://www.ted.com/talks.json?page=${pageToken ?: "1"}"
        val resp = http.get(url)
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList())

        val list = parseTedList(resp.body)
        val nextPage = ((pageToken?.toIntOrNull() ?: 1) + 1).toString()
        PagedResult(items = list, nextPageToken = nextPage, hasMore = list.isNotEmpty())
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val url = "https://www.ted.com/talks.json?q=$query&page=${pageToken ?: "1"}"
        val resp = http.get(url)
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList())

        val list = parseTedList(resp.body)
        PagedResult(items = list, nextPageToken = pageToken, hasMore = false)
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val slug = extractId(idOrUrl)
        PluginVideoItem(
            id = slug,
            title = slug.replace("_", " ").capitalize(),
            uploaderName = "TED Talks",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val slug = extractId(idOrUrl)
        val url = if (idOrUrl.startsWith("http")) idOrUrl else "https://www.ted.com/talks/$slug"
        val resp = http.get(url)
        val html = resp.body

        // Extract __NEXT_DATA__ JSON script from page
        var hlsUrl: String? = null
        val mp4Streams = mutableListOf<PluginVideoStream>()

        val jsonMatch = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""").find(html)
        if (jsonMatch != null) {
            try {
                val jsonStr = jsonMatch.groupValues[1]
                val root = JSONObject(jsonStr)
                val talkObj = root.optJSONObject("props")?.optJSONObject("pageProps")?.optJSONObject("videoData")
                val downloads = talkObj?.optJSONObject("downloads")
                val nativeDownloads = downloads?.optJSONArray("nativeDownloads") ?: JSONArray()

                for (i in 0 until nativeDownloads.length()) {
                    val d = nativeDownloads.getJSONObject(i)
                    mp4Streams.add(
                        PluginVideoStream(
                            url = d.optString("url"),
                            qualityLabel = d.optString("quality", "720p"),
                            format = "mp4",
                            isMuxed = true
                        )
                    )
                }

                hlsUrl = talkObj?.optJSONObject("playerData")?.optString("hls")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        PluginStreamInfo(
            id = slug,
            url = url,
            title = slug.replace("_", " "),
            channelName = "TED",
            videoStreams = mp4Streams,
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
        PluginChannel(id = "ted", name = "TED Talks")
    }

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist = withContext(Dispatchers.IO) {
        PluginPlaylist(id = playlistIdOrUrl, title = "TED Playlist", uploaderName = "TED")
    }

    override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> = withContext(Dispatchers.IO) {
        home().items.take(10)
    }

    private fun parseTedList(jsonStr: String): List<PluginVideoItem> {
        val list = mutableListOf<PluginVideoItem>()
        try {
            val root = JSONObject(jsonStr)
            val results = root.optJSONArray("results") ?: JSONArray()
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val slug = item.optString("slug")
                list.add(
                    PluginVideoItem(
                        id = slug,
                        title = item.optString("title"),
                        uploaderName = item.optString("speaker_name", "TED Speaker"),
                        durationSeconds = item.optLong("duration", 0),
                        thumbnailUrl = item.optString("image_url"),
                        providerId = providerId
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun extractId(input: String): String {
        return input.substringAfterLast("/").substringBefore("?")
    }
}
