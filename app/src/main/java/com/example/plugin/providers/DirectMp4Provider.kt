package com.example.plugin.providers

import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DirectMp4Provider : ContentProviderApi {

    override val providerId: String = "direct_mp4"

    override fun getProviderConfig(context: android.content.Context?): ProviderConfig {
        return ProviderConfig(
            id = providerId,
            name = "Direct MP4 Stream Loader",
            enabled = true,
            supportsDirectStreams = true,
            healthStatus = ProviderHealthStatus.READY
        )
    }

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        PagedResult(items = emptyList())
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        if (query.startsWith("http", ignoreCase = true) && query.contains(".mp4", ignoreCase = true)) {
            PagedResult(
                items = listOf(
                    PluginVideoItem(
                        id = query,
                        title = query.substringAfterLast("/"),
                        uploaderName = "Direct Stream URL",
                        providerId = providerId
                    )
                )
            )
        } else {
            PagedResult(items = emptyList())
        }
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        PluginVideoItem(
            id = idOrUrl,
            title = idOrUrl.substringAfterLast("/"),
            uploaderName = "Direct MP4 Link",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val isValidUrl = idOrUrl.startsWith("http", ignoreCase = true)
        val streams = if (isValidUrl) {
            listOf(
                PluginVideoStream(
                    url = idOrUrl,
                    qualityLabel = "Direct MP4",
                    format = "mp4",
                    isMuxed = true
                )
            )
        } else emptyList()

        PluginStreamInfo(
            id = idOrUrl,
            url = streams.firstOrNull()?.url ?: "",
            title = idOrUrl.substringAfterLast("/"),
            channelName = "Direct Stream",
            videoStreams = streams
        )
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> = withContext(Dispatchers.IO) {
        PagedResult(items = emptyList())
    }

    override suspend fun getSubtitles(idOrUrl: String): List<PluginSubtitle> = withContext(Dispatchers.IO) {
        emptyList()
    }

    override suspend fun getChannel(channelIdOrUrl: String): PluginChannel = withContext(Dispatchers.IO) {
        PluginChannel(id = "direct", name = "Direct Stream Source")
    }

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist = withContext(Dispatchers.IO) {
        PluginPlaylist(id = playlistIdOrUrl, title = "Direct Stream List", uploaderName = "Direct MP4")
    }

    override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> = withContext(Dispatchers.IO) {
        emptyList()
    }
}
