package com.example.plugin.providers

import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DirectHlsProvider : ContentProviderApi {

    override val providerId: String = "direct_hls"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val sampleList = listOf(
            PluginVideoItem(
                id = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                title = "Big Buck Bunny (HLS Stream)",
                uploaderName = "Mux Test Streams",
                thumbnailUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=500",
                providerId = providerId
            ),
            PluginVideoItem(
                id = "https://playertest.longtailvideo.com/adaptive/bipbop/gear4/prog_index.m3u8",
                title = "Apple BipBop Adaptive HLS Test",
                uploaderName = "JWPlayer / Apple",
                thumbnailUrl = "https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?w=500",
                providerId = providerId
            )
        )
        PagedResult(items = sampleList)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        if (query.startsWith("http") && (query.contains(".m3u8") || query.contains(".m3u"))) {
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
            home(pageToken)
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
        PluginStreamInfo(
            id = idOrUrl,
            url = idOrUrl,
            title = idOrUrl.substringAfterLast("/"),
            channelName = "Live HLS Stream",
            hlsUrl = idOrUrl,
            videoStreams = listOf(
                PluginVideoStream(
                    url = idOrUrl,
                    qualityLabel = "Adaptive HLS",
                    format = "m3u8",
                    isMuxed = true
                )
            )
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
        home().items
    }
}
