package com.example.plugin.providers

import android.content.Context
import com.example.extractor.YtDlpResolver
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BilibiliProvider(
    private val context: Context? = null
) : ContentProviderApi {

    override val providerId: String = "bilibili"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        PagedResult(items = emptyList(), hasMore = false)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        PagedResult(items = emptyList(), hasMore = false)
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val streams = getStreams(idOrUrl)
        PluginVideoItem(
            id = streams.id,
            title = streams.title,
            uploaderName = streams.channelName,
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val ctx = context ?: ArchiveOrgProvider.contextRef
        if (ctx != null) {
            when (val res = YtDlpResolver.extractStreamInfo(ctx, idOrUrl)) {
                is YtDlpResolver.ExtractionResult.Success -> {
                    val sd = res.streamData
                    val videoStreams = res.playableOptions.map { opt ->
                        PluginVideoStream(
                            url = opt.videoUrl ?: "",
                            qualityLabel = opt.qualityLabel,
                            format = opt.format,
                            height = 0,
                            fps = 30,
                            isMuxed = opt.isMuxed
                        )
                    }

                    return@withContext PluginStreamInfo(
                        id = sd.videoId,
                        url = YtDlpResolver.normalizeUrl(idOrUrl),
                        title = sd.title,
                        channelName = sd.channelName,
                        description = sd.description,
                        videoStreams = videoStreams,
                        thumbnailUrl = sd.thumbnailUrl
                    )
                }
                is YtDlpResolver.ExtractionResult.Error -> {
                    // Fallback
                }
            }
        }

        val cleanUrl = YtDlpResolver.normalizeUrl(idOrUrl)
        PluginStreamInfo(
            id = idOrUrl,
            url = cleanUrl,
            title = "Bilibili Video",
            channelName = "Bilibili Uploader"
        )
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> =
        PagedResult(items = emptyList())

    override suspend fun getSubtitles(idOrUrl: String): List<PluginSubtitle> = emptyList()

    override suspend fun getChannel(channelIdOrUrl: String): PluginChannel =
        PluginChannel(id = channelIdOrUrl, name = "Bilibili Channel")

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist =
        PluginPlaylist(id = playlistIdOrUrl, title = "Bilibili Playlist", uploaderName = "Bilibili")

    override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> = emptyList()
}
