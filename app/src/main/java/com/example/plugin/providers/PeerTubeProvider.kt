package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PeerTubeProvider(
    private val serverUrl: String = "https://peertube.tv",
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "peertube"
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val start = pageToken?.toIntOrNull() ?: 0
        val url = "$serverUrl/api/v1/videos?start=$start&count=20&sort=-createdAt"
        val resp = http.get(url)
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList())

        val items = parseVideoList(resp.body)
        PagedResult(items = items, nextPageToken = (start + 20).toString(), hasMore = items.isNotEmpty())
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val start = pageToken?.toIntOrNull() ?: 0
        val url = "$serverUrl/api/v1/search/videos?search=$query&start=$start&count=20"
        val resp = http.get(url)
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList())

        val items = parseVideoList(resp.body)
        PagedResult(items = items, nextPageToken = (start + 20).toString(), hasMore = items.isNotEmpty())
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val videoId = extractId(idOrUrl)
        val url = "$serverUrl/api/v1/videos/$videoId"
        val resp = http.get(url)
        val json = org.json.JSONObject(resp.body)
        val channelObj = json.optJSONObject("channel")
        PluginVideoItem(
            id = json.optString("id", videoId),
            title = json.optString("name", "PeerTube Video"),
            uploaderName = channelObj?.optString("displayName") ?: "PeerTube Creator",
            uploaderUrl = channelObj?.optString("url"),
            uploaderAvatarUrl = channelObj?.optJSONObject("avatar")?.optString("path")?.let { "$serverUrl$it" },
            viewCount = json.optLong("views", 0),
            durationSeconds = json.optLong("duration", 0),
            uploadDate = json.optString("publishedAt"),
            thumbnailUrl = json.optString("thumbnailPath")?.let { "$serverUrl$it" },
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val videoId = extractId(idOrUrl)
        val url = "$serverUrl/api/v1/videos/$videoId"
        val resp = http.get(url)
        val json = org.json.JSONObject(resp.body)

        val filesArr = json.optJSONArray("files") ?: org.json.JSONArray()
        val videoStreams = mutableListOf<PluginVideoStream>()
        for (i in 0 until filesArr.length()) {
            val f = filesArr.getJSONObject(i)
            videoStreams.add(
                PluginVideoStream(
                    url = f.optString("fileUrl"),
                    qualityLabel = f.optJSONObject("resolution")?.optString("label") ?: "${f.optInt("height", 720)}p",
                    format = "mp4",
                    height = f.optInt("height", 0),
                    fps = f.optInt("fps", 30)
                )
            )
        }

        val streamingPlaylistsArr = json.optJSONArray("streamingPlaylists")
        val hls = if (streamingPlaylistsArr != null && streamingPlaylistsArr.length() > 0) {
            streamingPlaylistsArr.getJSONObject(0).optString("playlistUrl")
        } else null

        PluginStreamInfo(
            id = videoId,
            url = "$serverUrl/videos/watch/$videoId",
            title = json.optString("name", "PeerTube Video"),
            channelName = json.optJSONObject("channel")?.optString("displayName") ?: "PeerTube",
            viewCount = json.optLong("views", 0),
            likeCount = json.optLong("likes", 0),
            description = json.optString("description"),
            videoStreams = videoStreams,
            hlsUrl = hls
        )
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> = withContext(Dispatchers.IO) {
        val videoId = extractId(idOrUrl)
        val start = pageToken?.toIntOrNull() ?: 0
        val url = "$serverUrl/api/v1/videos/$videoId/comment-threads?start=$start&count=20"
        val resp = http.get(url)
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList())

        val list = mutableListOf<PluginComment>()
        val json = org.json.JSONObject(resp.body)
        val data = json.optJSONArray("data") ?: org.json.JSONArray()
        for (i in 0 until data.length()) {
            val item = data.getJSONObject(i)
            val thread = item.optJSONObject("threadMessage") ?: item
            val account = thread.optJSONObject("account")
            list.add(
                PluginComment(
                    id = thread.optString("id"),
                    authorName = account?.optString("displayName") ?: "User",
                    authorAvatarUrl = account?.optJSONObject("avatar")?.optString("path")?.let { "$serverUrl$it" },
                    content = thread.optString("text"),
                    publishedTime = thread.optString("createdAt")
                )
            )
        }
        PagedResult(items = list, nextPageToken = (start + 20).toString(), hasMore = list.isNotEmpty())
    }

    override suspend fun getSubtitles(idOrUrl: String): List<PluginSubtitle> = withContext(Dispatchers.IO) {
        val videoId = extractId(idOrUrl)
        val url = "$serverUrl/api/v1/videos/$videoId/captions"
        val resp = http.get(url)
        val list = mutableListOf<PluginSubtitle>()
        if (resp.statusCode == 200) {
            val json = org.json.JSONObject(resp.body)
            val data = json.optJSONArray("data") ?: org.json.JSONArray()
            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val langObj = item.optJSONObject("language")
                list.add(
                    PluginSubtitle(
                        url = "$serverUrl" + item.optString("captionPath"),
                        languageCode = langObj?.optString("id") ?: "en",
                        languageName = langObj?.optString("label") ?: "English",
                        format = "vtt"
                    )
                )
            }
        }
        list
    }

    override suspend fun getChannel(channelIdOrUrl: String): PluginChannel = withContext(Dispatchers.IO) {
        val channelId = extractId(channelIdOrUrl)
        val url = "$serverUrl/api/v1/video-channels/$channelId"
        val resp = http.get(url)
        val json = org.json.JSONObject(resp.body)
        PluginChannel(
            id = channelId,
            name = json.optString("displayName", channelId),
            avatarUrl = json.optJSONObject("avatar")?.optString("path")?.let { "$serverUrl$it" },
            description = json.optString("description")
        )
    }

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist = withContext(Dispatchers.IO) {
        val playlistId = extractId(playlistIdOrUrl)
        val url = "$serverUrl/api/v1/video-playlists/$playlistId"
        val resp = http.get(url)
        val json = org.json.JSONObject(resp.body)
        PluginPlaylist(
            id = playlistId,
            title = json.optString("displayName", "Playlist"),
            uploaderName = json.optJSONObject("ownerAccount")?.optString("displayName") ?: "PeerTube"
        )
    }

    override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> = withContext(Dispatchers.IO) {
        home().items.take(10)
    }

    private fun parseVideoList(jsonStr: String): List<PluginVideoItem> {
        val list = mutableListOf<PluginVideoItem>()
        val json = org.json.JSONObject(jsonStr)
        val data = json.optJSONArray("data") ?: org.json.JSONArray()
        for (i in 0 until data.length()) {
            val v = data.getJSONObject(i)
            val channel = v.optJSONObject("channel")
            list.add(
                PluginVideoItem(
                    id = v.optString("id"),
                    title = v.optString("name"),
                    uploaderName = channel?.optString("displayName") ?: "PeerTube Creator",
                    uploaderUrl = channel?.optString("url"),
                    uploaderAvatarUrl = channel?.optJSONObject("avatar")?.optString("path")?.let { "$serverUrl$it" },
                    viewCount = v.optLong("views", 0),
                    durationSeconds = v.optLong("duration", 0),
                    uploadDate = v.optString("publishedAt"),
                    thumbnailUrl = v.optString("thumbnailPath")?.let { "$serverUrl$it" },
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
