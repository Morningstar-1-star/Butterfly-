package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class DailymotionProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "dailymotion"

    private val fields = "id,title,owner,owner.username,owner.avatar_360_url,views_total,created_time,thumbnail_720_url,duration"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val url = "https://api.dailymotion.com/videos?fields=$fields&limit=20&page=$page"
        val resp = http.get(url)
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList())

        val (items, hasMore) = parseVideoList(resp.body)
        PagedResult(items = items, nextPageToken = (page + 1).toString(), hasMore = hasMore)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val url = "https://api.dailymotion.com/videos?search=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=$fields&limit=20&page=$page"
        val resp = http.get(url)
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList())

        val (items, hasMore) = parseVideoList(resp.body)
        PagedResult(items = items, nextPageToken = (page + 1).toString(), hasMore = hasMore)
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val id = extractId(idOrUrl)
        val url = "https://api.dailymotion.com/video/$id?fields=$fields"
        val resp = http.get(url)
        val json = JSONObject(resp.body)
        val owner = json.optJSONObject("owner")
        PluginVideoItem(
            id = json.optString("id", id),
            title = json.optString("title", "Dailymotion Video"),
            uploaderName = owner?.optString("username") ?: "Dailymotion Creator",
            uploaderAvatarUrl = owner?.optString("avatar_360_url"),
            viewCount = json.optLong("views_total", 0),
            durationSeconds = json.optLong("duration", 0),
            uploadDate = json.optLong("created_time", 0).toString(),
            thumbnailUrl = json.optString("thumbnail_720_url"),
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val id = extractId(idOrUrl)
        val metaUrl = "https://api.dailymotion.com/video/$id?fields=title,description,views_total,bookmarks_total"
        val respMeta = http.get(metaUrl)
        val jsonMeta = JSONObject(respMeta.body)

        val embedUrl = "https://www.dailymotion.com/embed/video/$id?autoplay=1&ui-logo=0&ui-start-screen-info=0"

        PluginStreamInfo(
            id = id,
            url = embedUrl,
            title = jsonMeta.optString("title", "Dailymotion Video"),
            channelName = "Dailymotion",
            viewCount = jsonMeta.optLong("views_total", 0),
            likeCount = jsonMeta.optLong("bookmarks_total", 0),
            description = jsonMeta.optString("description", "Dailymotion video stream"),
            hlsUrl = null,
            videoStreams = listOf(
                PluginVideoStream(
                    url = embedUrl,
                    qualityLabel = "Embed Auto Play",
                    format = "embed",
                    isMuxed = true
                )
            )
        )
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> = withContext(Dispatchers.IO) {
        val id = extractId(idOrUrl)
        val page = pageToken?.toIntOrNull() ?: 1
        val url = "https://api.dailymotion.com/video/$id/comments?fields=id,message,owner.username,owner.avatar_360_url,created_time&limit=20&page=$page"
        val resp = http.get(url)
        val list = mutableListOf<PluginComment>()
        if (resp.statusCode == 200) {
            val json = JSONObject(resp.body)
            val listArr = json.optJSONArray("list") ?: JSONArray()
            for (i in 0 until listArr.length()) {
                val item = listArr.getJSONObject(i)
                val owner = item.optJSONObject("owner")
                list.add(
                    PluginComment(
                        id = item.optString("id"),
                        authorName = owner?.optString("username") ?: "User",
                        authorAvatarUrl = owner?.optString("avatar_360_url"),
                        content = item.optString("message"),
                        publishedTime = item.optLong("created_time", 0).toString()
                    )
                )
            }
        }
        PagedResult(items = list, nextPageToken = (page + 1).toString(), hasMore = list.isNotEmpty())
    }

    override suspend fun getSubtitles(idOrUrl: String): List<PluginSubtitle> = withContext(Dispatchers.IO) {
        val id = extractId(idOrUrl)
        val url = "https://api.dailymotion.com/video/$id/subtitles?fields=id,language,url"
        val resp = http.get(url)
        val list = mutableListOf<PluginSubtitle>()
        if (resp.statusCode == 200) {
            val json = JSONObject(resp.body)
            val listArr = json.optJSONArray("list") ?: JSONArray()
            for (i in 0 until listArr.length()) {
                val item = listArr.getJSONObject(i)
                val lang = item.optString("language", "en")
                list.add(
                    PluginSubtitle(
                        url = item.optString("url"),
                        languageCode = lang,
                        languageName = lang.uppercase(),
                        format = "vtt"
                    )
                )
            }
        }
        list
    }

    override suspend fun getChannel(channelIdOrUrl: String): PluginChannel = withContext(Dispatchers.IO) {
        val username = extractId(channelIdOrUrl)
        val url = "https://api.dailymotion.com/user/$username?fields=id,username,avatar_360_url,description"
        val resp = http.get(url)
        val json = JSONObject(resp.body)
        PluginChannel(
            id = json.optString("id", username),
            name = json.optString("username", username),
            avatarUrl = json.optString("avatar_360_url"),
            description = json.optString("description")
        )
    }

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist = withContext(Dispatchers.IO) {
        val id = extractId(playlistIdOrUrl)
        val url = "https://api.dailymotion.com/playlist/$id?fields=id,name,owner.username,videos_total"
        val resp = http.get(url)
        val json = JSONObject(resp.body)
        PluginPlaylist(
            id = json.optString("id", id),
            title = json.optString("name", "Dailymotion Playlist"),
            uploaderName = json.optJSONObject("owner")?.optString("username") ?: "Dailymotion",
            videoCount = json.optInt("videos_total", 0)
        )
    }

    override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> = withContext(Dispatchers.IO) {
        val id = extractId(idOrUrl)
        val url = "https://api.dailymotion.com/video/$id/related?fields=$fields&limit=10"
        val resp = http.get(url)
        if (resp.statusCode == 200) parseVideoList(resp.body).first else emptyList()
    }

    private fun parseVideoList(jsonStr: String): Pair<List<PluginVideoItem>, Boolean> {
        val list = mutableListOf<PluginVideoItem>()
        val json = JSONObject(jsonStr)
        val listArr = json.optJSONArray("list") ?: JSONArray()
        val hasMore = json.optBoolean("has_more", false)

        for (i in 0 until listArr.length()) {
            val v = listArr.getJSONObject(i)
            val owner = v.optJSONObject("owner")
            list.add(
                PluginVideoItem(
                    id = v.optString("id"),
                    title = v.optString("title"),
                    uploaderName = owner?.optString("username") ?: "Dailymotion Creator",
                    uploaderAvatarUrl = owner?.optString("avatar_360_url"),
                    viewCount = v.optLong("views_total", 0),
                    durationSeconds = v.optLong("duration", 0),
                    uploadDate = v.optLong("created_time", 0).toString(),
                    thumbnailUrl = v.optString("thumbnail_720_url"),
                    providerId = providerId
                )
            )
        }
        return Pair(list, hasMore)
    }

    private fun extractId(input: String): String {
        return input.substringAfterLast("/").substringAfterLast("=")
    }
}
