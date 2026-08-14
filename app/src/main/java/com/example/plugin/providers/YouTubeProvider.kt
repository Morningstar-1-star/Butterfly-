package com.example.plugin.providers

import com.example.extractor.YouTubeExtractorHelper
import com.example.model.FeedResult
import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import com.example.util.YouTubeApiHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class YouTubeProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "youtube"

    // Primary extraction path uses NewPipeExtractor + fallback to Piped / Invidious instances
    private val pipedInstances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://api.piped.private.coffee",
        "https://pipedapi.mha.fi"
    )

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val apiItems = YouTubeApiHelper.fetchPopularVideos(25, pageToken)
        if (!apiItems.isNullOrEmpty()) {
            return@withContext PagedResult(items = apiItems, hasMore = true)
        }

        when (val result = YouTubeExtractorHelper.fetchTrendingVideos()) {
            is FeedResult.Success -> {
                val items = result.items.map { item ->
                    PluginVideoItem(
                        id = item.id,
                        title = item.title,
                        uploaderName = item.uploaderName,
                        uploaderUrl = item.uploaderUrl,
                        uploaderAvatarUrl = item.uploaderAvatarUrl,
                        viewCount = item.viewCount,
                        durationSeconds = item.durationSeconds,
                        uploadDate = item.uploadDate,
                        thumbnailUrl = item.thumbnailUrl,
                        providerId = providerId
                    )
                }
                PagedResult(items = items, hasMore = false)
            }
            is FeedResult.Error -> {
                fetchHomeFromPiped()
            }
        }
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val apiSearchResult = YouTubeApiHelper.search(query, 25, pageToken)
        if (apiSearchResult != null && apiSearchResult.videoItems.isNotEmpty()) {
            return@withContext PagedResult(items = apiSearchResult.videoItems, hasMore = true)
        }

        when (val result = YouTubeExtractorHelper.searchVideos(query)) {
            is FeedResult.Success -> {
                val items = result.items.map { item ->
                    PluginVideoItem(
                        id = item.id,
                        title = item.title,
                        uploaderName = item.uploaderName,
                        uploaderUrl = item.uploaderUrl,
                        uploaderAvatarUrl = item.uploaderAvatarUrl,
                        viewCount = item.viewCount,
                        durationSeconds = item.durationSeconds,
                        uploadDate = item.uploadDate,
                        thumbnailUrl = item.thumbnailUrl,
                        providerId = providerId
                    )
                }
                PagedResult(items = items, hasMore = false)
            }
            is FeedResult.Error -> {
                fetchSearchFromPiped(query)
            }
        }
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val streamInfo = getStreams(idOrUrl)
        PluginVideoItem(
            id = streamInfo.id,
            title = streamInfo.title,
            uploaderName = streamInfo.channelName,
            uploaderAvatarUrl = streamInfo.channelAvatarUrl,
            viewCount = streamInfo.viewCount,
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        when (val res = YouTubeExtractorHelper.fetchStreamData(idOrUrl)) {
            is YouTubeExtractorHelper.ExtractionResult.Success -> {
                val sd = res.streamData
                val videoStreams = mutableListOf<PluginVideoStream>()

                if (sd.availableStreamOptions.isNotEmpty()) {
                    sd.availableStreamOptions.forEach { opt ->
                        videoStreams.add(
                            PluginVideoStream(
                                url = opt.videoUrl ?: "",
                                qualityLabel = opt.qualityLabel,
                                format = opt.format,
                                isMuxed = opt.isMuxed,
                                audioUrl = opt.audioUrl,
                                headers = opt.headers
                            )
                        )
                    }
                } else {
                    sd.progressiveStreams.forEach { vs ->
                        videoStreams.add(
                            PluginVideoStream(
                                url = vs.url ?: "",
                                qualityLabel = vs.resolution ?: "720p",
                                format = vs.format?.name ?: "mp4",
                                height = vs.height,
                                fps = vs.fps,
                                isMuxed = true,
                                headers = sd.headers
                            )
                        )
                    }

                    val bestAudioUrl = sd.audioStreams.maxByOrNull { it.averageBitrate }?.url
                    sd.videoOnlyStreams.forEach { vo ->
                        videoStreams.add(
                            PluginVideoStream(
                                url = vo.url ?: "",
                                qualityLabel = vo.resolution ?: "1080p",
                                format = vo.format?.name ?: "mp4",
                                height = vo.height,
                                fps = vo.fps,
                                isMuxed = false,
                                audioUrl = bestAudioUrl,
                                headers = sd.headers
                            )
                        )
                    }
                }

                val audioStreams = sd.audioStreams.map { audio ->
                    PluginAudioStream(
                        url = audio.url ?: "",
                        qualityLabel = "${audio.averageBitrate} kbps",
                        format = audio.format?.name ?: "m4a",
                        bitrate = audio.averageBitrate.toLong(),
                        headers = sd.headers
                    )
                }

                val subtitles = sd.captionOptions.map { cap ->
                    PluginSubtitle(
                        url = cap.url,
                        languageCode = cap.languageCode,
                        languageName = cap.languageName,
                        format = cap.format
                    )
                }

                PluginStreamInfo(
                    id = sd.videoId,
                    url = "https://www.youtube.com/watch?v=${sd.videoId}",
                    title = sd.title,
                    channelName = sd.channelName,
                    channelAvatarUrl = sd.channelAvatarUrl,
                    viewCount = sd.viewCount,
                    likeCount = sd.likeCount,
                    uploadDate = sd.uploadDate,
                    description = sd.description,
                    videoStreams = videoStreams,
                    audioStreams = audioStreams,
                    subtitles = subtitles,
                    hlsUrl = sd.hlsUrl,
                    httpHeaders = sd.headers,
                    thumbnailUrl = sd.thumbnailUrl
                )
            }
            is YouTubeExtractorHelper.ExtractionResult.Error -> {
                fetchStreamsFromPiped(idOrUrl)
            }
        }
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(idOrUrl)
        for (instance in pipedInstances) {
            try {
                val url = "$instance/comments/$videoId"
                val resp = http.get(url)
                if (resp.statusCode == 200) {
                    val json = JSONObject(resp.body)
                    val commentsArr = json.optJSONArray("comments") ?: JSONArray()
                    val list = mutableListOf<PluginComment>()
                    for (i in 0 until commentsArr.length()) {
                        val c = commentsArr.getJSONObject(i)
                        list.add(
                            PluginComment(
                                id = c.optString("commentId"),
                                authorName = c.optString("author"),
                                authorAvatarUrl = c.optString("thumbnail"),
                                content = c.optString("commentText"),
                                publishedTime = c.optString("commentedText"),
                                likeCount = c.optLong("likeCount", 0)
                            )
                        )
                    }
                    return@withContext PagedResult(items = list, nextPageToken = json.optString("nextpage"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        PagedResult(items = emptyList())
    }

    override suspend fun getSubtitles(idOrUrl: String): List<PluginSubtitle> = withContext(Dispatchers.IO) {
        getStreams(idOrUrl).subtitles
    }

    override suspend fun getChannel(channelIdOrUrl: String): PluginChannel = withContext(Dispatchers.IO) {
        val channelId = channelIdOrUrl.substringAfterLast("/")
        val apiChannel = YouTubeApiHelper.getChannelDetails(channelId)
        if (apiChannel != null) {
            return@withContext apiChannel
        }
        PluginChannel(
            id = channelId,
            name = "YouTube Channel ($channelId)"
        )
    }

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist = withContext(Dispatchers.IO) {
        val playlistId = playlistIdOrUrl.substringAfterLast("/")
        PluginPlaylist(
            id = playlistId,
            title = "YouTube Playlist",
            uploaderName = "YouTube"
        )
    }

    override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> = withContext(Dispatchers.IO) {
        home().items.take(10)
    }

    private suspend fun fetchHomeFromPiped(): PagedResult<PluginVideoItem> {
        for (instance in pipedInstances) {
            try {
                val url = "$instance/trending?region=US"
                val resp = http.get(url)
                if (resp.statusCode == 200) {
                    val array = JSONArray(resp.body)
                    val list = mutableListOf<PluginVideoItem>()
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val id = item.optString("url").substringAfter("v=")
                        list.add(
                            PluginVideoItem(
                                id = id,
                                title = item.optString("title"),
                                uploaderName = item.optString("uploaderName"),
                                uploaderAvatarUrl = item.optString("uploaderAvatar"),
                                viewCount = item.optLong("views", 0),
                                durationSeconds = item.optLong("duration", 0),
                                uploadDate = item.optString("uploadedDate"),
                                thumbnailUrl = item.optString("thumbnail"),
                                providerId = providerId
                            )
                        )
                    }
                    return PagedResult(items = list)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return PagedResult(items = emptyList())
    }

    private suspend fun fetchSearchFromPiped(query: String): PagedResult<PluginVideoItem> {
        for (instance in pipedInstances) {
            try {
                val url = "$instance/search?q=$query&filter=all"
                val resp = http.get(url)
                if (resp.statusCode == 200) {
                    val json = JSONObject(resp.body)
                    val items = json.optJSONArray("items") ?: JSONArray()
                    val list = mutableListOf<PluginVideoItem>()
                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        if (item.optString("type") == "stream") {
                            val id = item.optString("url").substringAfter("v=")
                            list.add(
                                PluginVideoItem(
                                    id = id,
                                    title = item.optString("title"),
                                    uploaderName = item.optString("uploaderName"),
                                    uploaderAvatarUrl = item.optString("uploaderAvatar"),
                                    viewCount = item.optLong("views", 0),
                                    durationSeconds = item.optLong("duration", 0),
                                    uploadDate = item.optString("uploadedDate"),
                                    thumbnailUrl = item.optString("thumbnail"),
                                    providerId = providerId
                                )
                            )
                        }
                    }
                    return PagedResult(items = list)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return PagedResult(items = emptyList())
    }

    private suspend fun fetchStreamsFromPiped(idOrUrl: String): PluginStreamInfo {
        val videoId = extractVideoId(idOrUrl)
        for (instance in pipedInstances) {
            try {
                val url = "$instance/streams/$videoId"
                val resp = http.get(url)
                if (resp.statusCode == 200) {
                    val json = JSONObject(resp.body)
                    val videoStreamsArr = json.optJSONArray("videoStreams") ?: JSONArray()
                    val audioStreamsArr = json.optJSONArray("audioStreams") ?: JSONArray()
                    val subtitlesArr = json.optJSONArray("subtitles") ?: JSONArray()

                    val videoStreams = mutableListOf<PluginVideoStream>()
                    for (i in 0 until videoStreamsArr.length()) {
                        val v = videoStreamsArr.getJSONObject(i)
                        videoStreams.add(
                            PluginVideoStream(
                                url = v.optString("url"),
                                qualityLabel = v.optString("quality"),
                                format = v.optString("format"),
                                height = v.optInt("height", 0),
                                fps = v.optInt("fps", 30),
                                isMuxed = v.optBoolean("videoOnly", false).not()
                            )
                        )
                    }

                    val audioStreams = mutableListOf<PluginAudioStream>()
                    for (i in 0 until audioStreamsArr.length()) {
                        val a = audioStreamsArr.getJSONObject(i)
                        audioStreams.add(
                            PluginAudioStream(
                                url = a.optString("url"),
                                qualityLabel = a.optString("quality"),
                                format = a.optString("format"),
                                bitrate = a.optLong("bitrate", 0)
                            )
                        )
                    }

                    val subtitles = mutableListOf<PluginSubtitle>()
                    for (i in 0 until subtitlesArr.length()) {
                        val s = subtitlesArr.getJSONObject(i)
                        subtitles.add(
                            PluginSubtitle(
                                url = s.optString("url"),
                                languageCode = s.optString("code"),
                                languageName = s.optString("name"),
                                format = s.optString("mimeType", "vtt")
                            )
                        )
                    }

                    return PluginStreamInfo(
                        id = videoId,
                        url = "https://www.youtube.com/watch?v=$videoId",
                        title = json.optString("title"),
                        channelName = json.optString("uploader"),
                        channelAvatarUrl = json.optString("uploaderAvatar"),
                        viewCount = json.optLong("views", 0),
                        likeCount = json.optLong("likes", 0),
                        description = json.optString("description"),
                        videoStreams = videoStreams,
                        audioStreams = audioStreams,
                        subtitles = subtitles,
                        hlsUrl = if (json.has("hls") && !json.isNull("hls")) json.optString("hls") else null
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return PluginStreamInfo(
            id = videoId,
            url = "https://www.youtube.com/watch?v=$videoId",
            title = "YouTube Video $videoId",
            channelName = "YouTube Creator"
        )
    }

    private fun extractVideoId(input: String): String {
        return when (val res = YouTubeExtractorHelper.parseYouTubeInput(input)) {
            is YouTubeExtractorHelper.UrlParseResult.ValidVideoId -> res.videoId
            else -> input.trim()
        }
    }
}
