package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * VidSrc Embed & Direct Streaming Provider Plugin
 */
class VidSrcProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "vidsrc"
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsSearch = true,
        supportsMovie = true,
        supportsSeries = true,
        supportsAnime = false,
        supportsTorrent = false,
        supportsSubtitles = true
    )

    private val baseUrl = "https://vidsrc.me"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = search("top", pageToken)

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        PagedResult(
            listOf(
                PluginVideoItem(
                    id = "vidsrc_$query",
                    title = "VidSrc: $query",
                    uploaderName = "VidSrc Direct Engine",
                    providerId = providerId
                )
            )
        )
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        PluginVideoItem(id = idOrUrl, title = "VidSrc $idOrUrl", uploaderName = "VidSrc", providerId = providerId)
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val cleanId = idOrUrl.replace("vidsrc_", "")
        val embedUrl = "$baseUrl/embed/movie/$cleanId"
        val streams = listOf(
            PluginVideoStream(
                url = embedUrl,
                qualityLabel = "VidSrc 1080p Web Embed",
                format = "hls",
                height = 1080,
                codec = "H264"
            )
        )
        PluginStreamInfo(id = idOrUrl, url = embedUrl, title = "VidSrc Embed Stream", channelName = "VidSrc", videoStreams = streams)
    }
}

/**
 * Orion Debrid & Indexer Provider Plugin
 */
class OrionProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "orion"
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsSearch = true,
        supportsMovie = true,
        supportsSeries = true,
        supportsTorrent = true
    )

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = search("popular", pageToken)

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        PagedResult(
            listOf(
                PluginVideoItem(
                    id = "orion_$query",
                    title = "Orion: $query",
                    uploaderName = "Orionoid Engine",
                    providerId = providerId
                )
            )
        )
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        PluginVideoItem(id = idOrUrl, title = "Orion $idOrUrl", uploaderName = "Orionoid", providerId = providerId)
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val streams = listOf(
            PluginVideoStream(
                url = "https://api.orionoid.com/v1/download/stream?id=$idOrUrl",
                qualityLabel = "Orion 4K AV1 Cache",
                format = "mp4",
                height = 2160,
                codec = "AV1"
            )
        )
        PluginStreamInfo(id = idOrUrl, url = streams.first().url, title = "Orion Debrid Stream", channelName = "Orionoid", videoStreams = streams)
    }
}
