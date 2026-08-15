package com.example.plugin.providers

import com.example.extractor.YouTubeExtractorHelper
import com.example.model.FeedResult
import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import com.example.util.YouTubeApiHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class YouTubeProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "youtube"

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
                PagedResult(items = emptyList())
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
                PagedResult(items = emptyList())
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
        val videoId = extractVideoId(idOrUrl)
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
                PluginStreamInfo(
                    id = videoId,
                    url = "https://www.youtube.com/watch?v=$videoId",
                    title = "YouTube Video $videoId",
                    channelName = "YouTube Creator"
                )
            }
        }
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> = withContext(Dispatchers.IO) {
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

    private fun extractVideoId(input: String): String {
        return when (val res = YouTubeExtractorHelper.parseYouTubeInput(input)) {
            is YouTubeExtractorHelper.UrlParseResult.ValidVideoId -> res.videoId
            else -> input.trim()
        }
    }
}
