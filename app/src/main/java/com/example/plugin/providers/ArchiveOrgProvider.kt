package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class ArchiveOrgProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "archive_org"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val url = "https://archive.org/advancedsearch.php?q=mediatype%3Amovies&fl%5B%5D=identifier%2Ctitle%2Ccreator%2Cpublicdate%2Cdescription&sort%5B%5D=publicdate+desc&rows=20&page=$page&output=json"
        val resp = http.get(url)
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList())

        val (items, numFound) = parseArchiveList(resp.body)
        PagedResult(items = items, nextPageToken = (page + 1).toString(), hasMore = page * 20 < numFound)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val url = "https://archive.org/advancedsearch.php?q=mediatype%3Amovies+AND+($query)&fl%5B%5D=identifier%2Ctitle%2Ccreator%2Cpublicdate%2Cdescription&sort%5B%5D=publicdate+desc&rows=20&page=$page&output=json"
        val resp = http.get(url)
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList())

        val (items, numFound) = parseArchiveList(resp.body)
        PagedResult(items = items, nextPageToken = (page + 1).toString(), hasMore = page * 20 < numFound)
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val identifier = extractId(idOrUrl)
        val url = "https://archive.org/metadata/$identifier"
        val resp = http.get(url)
        val json = JSONObject(resp.body)
        val meta = json.optJSONObject("metadata")
        PluginVideoItem(
            id = identifier,
            title = meta?.optString("title") ?: identifier,
            uploaderName = meta?.optString("creator") ?: "Internet Archive",
            uploadDate = meta?.optString("publicdate"),
            thumbnailUrl = "https://archive.org/services/img/$identifier",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val identifier = extractId(idOrUrl)
        val url = "https://archive.org/metadata/$identifier"
        val resp = http.get(url)
        val json = JSONObject(resp.body)
        val meta = json.optJSONObject("metadata")
        val filesArr = json.optJSONArray("files") ?: JSONArray()

        val videoStreams = mutableListOf<PluginVideoStream>()
        val subtitles = mutableListOf<PluginSubtitle>()

        for (i in 0 until filesArr.length()) {
            val f = filesArr.getJSONObject(i)
            val name = f.optString("name")
            val format = f.optString("format", "").lowercase()
            val fileUrl = "https://archive.org/download/$identifier/$name"

            if (format.contains("mp4") || format.contains("h.264") || name.endsWith(".mp4")) {
                videoStreams.add(
                    PluginVideoStream(
                        url = fileUrl,
                        qualityLabel = f.optString("height", "720") + "p",
                        format = "mp4",
                        height = f.optInt("height", 0),
                        isMuxed = true
                    )
                )
            } else if (format.contains("vtt") || name.endsWith(".vtt") || name.endsWith(".srt")) {
                subtitles.add(
                    PluginSubtitle(
                        url = fileUrl,
                        languageCode = "en",
                        languageName = "English",
                        format = if (name.endsWith(".vtt")) "vtt" else "srt"
                    )
                )
            }
        }

        PluginStreamInfo(
            id = identifier,
            url = "https://archive.org/details/$identifier",
            title = meta?.optString("title") ?: identifier,
            channelName = meta?.optString("creator") ?: "Internet Archive",
            description = meta?.optString("description"),
            videoStreams = videoStreams,
            subtitles = subtitles
        )
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> = withContext(Dispatchers.IO) {
        PagedResult(items = emptyList())
    }

    override suspend fun getSubtitles(idOrUrl: String): List<PluginSubtitle> = withContext(Dispatchers.IO) {
        getStreams(idOrUrl).subtitles
    }

    override suspend fun getChannel(channelIdOrUrl: String): PluginChannel = withContext(Dispatchers.IO) {
        val id = extractId(channelIdOrUrl)
        PluginChannel(id = id, name = id)
    }

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist = withContext(Dispatchers.IO) {
        val id = extractId(playlistIdOrUrl)
        PluginPlaylist(id = id, title = "Internet Archive Collection", uploaderName = "Archive")
    }

    override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> = withContext(Dispatchers.IO) {
        home().items.take(10)
    }

    private fun parseArchiveList(jsonStr: String): Pair<List<PluginVideoItem>, Long> {
        val list = mutableListOf<PluginVideoItem>()
        val json = JSONObject(jsonStr)
        val response = json.optJSONObject("response") ?: JSONObject()
        val numFound = response.optLong("numFound", 0)
        val docs = response.optJSONArray("docs") ?: JSONArray()

        for (i in 0 until docs.length()) {
            val d = docs.getJSONObject(i)
            val id = d.optString("identifier")
            list.add(
                PluginVideoItem(
                    id = id,
                    title = d.optString("title", id),
                    uploaderName = d.optString("creator", "Internet Archive"),
                    uploadDate = d.optString("publicdate"),
                    thumbnailUrl = "https://archive.org/services/img/$id",
                    providerId = providerId
                )
            )
        }
        return Pair(list, numFound)
    }

    private fun extractId(input: String): String {
        return input.substringAfterLast("/").substringAfterLast("=")
    }
}
