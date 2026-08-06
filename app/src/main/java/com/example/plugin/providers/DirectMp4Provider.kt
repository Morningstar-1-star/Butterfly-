package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DirectMp4Provider : ContentProviderApi {

    override val providerId: String = "direct_mp4"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val sampleList = listOf(
            PluginVideoItem(
                id = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                title = "Big Buck Bunny (Direct MP4)",
                uploaderName = "Blender Foundation",
                thumbnailUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=500",
                providerId = providerId
            ),
            PluginVideoItem(
                id = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                title = "Elephant's Dream (Direct MP4)",
                uploaderName = "Blender Foundation",
                thumbnailUrl = "https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?w=500",
                providerId = providerId
            ),
            PluginVideoItem(
                id = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                title = "For Bigger Blazes (Direct MP4)",
                uploaderName = "Google Chromecast",
                thumbnailUrl = "https://images.unsplash.com/photo-1518173946687-a4c8a383392e?w=500",
                providerId = providerId
            )
        )
        PagedResult(items = sampleList)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        if (query.startsWith("http") && query.contains(".mp4")) {
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
            home(pageToken)
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
        PluginStreamInfo(
            id = idOrUrl,
            url = idOrUrl,
            title = idOrUrl.substringAfterLast("/"),
            channelName = "Direct Stream",
            videoStreams = listOf(
                PluginVideoStream(
                    url = idOrUrl,
                    qualityLabel = "1080p",
                    format = "mp4",
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
        PluginChannel(id = "direct", name = "Direct Stream Source")
    }

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist = withContext(Dispatchers.IO) {
        PluginPlaylist(id = playlistIdOrUrl, title = "Direct Stream List", uploaderName = "Direct MP4")
    }

    override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> = withContext(Dispatchers.IO) {
        home().items
    }
}
