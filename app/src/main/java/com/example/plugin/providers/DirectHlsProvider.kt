package com.example.plugin.providers

import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DirectHlsProvider : ContentProviderApi {

    override val providerId: String = "direct_hls"

    override fun getProviderConfig(context: android.content.Context?): ProviderConfig {
        return ProviderConfig(
            id = providerId,
            name = "Direct HLS / M3U8 Stream Loader",
            enabled = true,
            supportsDirectStreams = true,
            healthStatus = ProviderHealthStatus.READY
        )
    }

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        PagedResult(items = emptyList())
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        if (query.startsWith("http", ignoreCase = true) && (query.contains(".m3u8", ignoreCase = true) || query.contains(".m3u", ignoreCase = true))) {
            PagedResult(
                items = listOf(
                    PluginVideoItem(
                        id = query,
                        title = query.substringAfterLast("/"),
                        uploaderName = "Custom HLS Stream",
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
            uploaderName = "HLS Live Stream",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val isValidUrl = idOrUrl.startsWith("http", ignoreCase = true)
        val streams = if (isValidUrl) {
            listOf(
                PluginVideoStream(
                    url = idOrUrl,
                    qualityLabel = "Adaptive HLS",
                    format = "m3u8",
                    isMuxed = true
                )
            )
        } else emptyList()

        PluginStreamInfo(
            id = idOrUrl,
            url = streams.firstOrNull()?.url ?: "",
            title = idOrUrl.substringAfterLast("/"),
            channelName = "Live HLS Stream",
            hlsUrl = if (isValidUrl) idOrUrl else null,
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
        PluginChannel(id = "hls", name = "HLS Stream Source")
    }

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist = withContext(Dispatchers.IO) {
        PluginPlaylist(id = playlistIdOrUrl, title = "HLS Playlist", uploaderName = "HLS")
    }

    override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> = withContext(Dispatchers.IO) {
        emptyList()
    }
}
