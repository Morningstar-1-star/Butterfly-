package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * TorBox Debrid API Provider Plugin
 */
class TorBoxProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "torbox"
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsSearch = true,
        supportsMovie = true,
        supportsSeries = true,
        supportsTorrent = true
    )

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = search("popular", pageToken)

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        PagedResult(listOf(PluginVideoItem(id = "torbox_$query", title = "TorBox: $query", uploaderName = "TorBox Debrid API", providerId = providerId)))
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        PluginVideoItem(id = idOrUrl, title = "TorBox $idOrUrl", uploaderName = "TorBox", providerId = providerId)
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val streams = listOf(
            PluginVideoStream(
                url = "https://api.torbox.app/v1/api/torrents/requestdl?hash=$idOrUrl",
                qualityLabel = "TorBox High Speed Direct Cache 1080p",
                format = "mp4",
                height = 1080,
                codec = "HEVC"
            )
        )
        PluginStreamInfo(id = idOrUrl, url = streams.first().url, title = "TorBox Debrid Stream", channelName = "TorBox", videoStreams = streams)
    }
}

/**
 * EasyDebrid API Provider Plugin
 */
class EasyDebridProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "easydebrid"
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsSearch = true,
        supportsMovie = true,
        supportsSeries = true,
        supportsTorrent = true
    )

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = search("popular", pageToken)

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        PagedResult(listOf(PluginVideoItem(id = "easy_$query", title = "EasyDebrid: $query", uploaderName = "EasyDebrid Cloud", providerId = providerId)))
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        PluginVideoItem(id = idOrUrl, title = "EasyDebrid $idOrUrl", uploaderName = "EasyDebrid", providerId = providerId)
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val streams = listOf(
            PluginVideoStream(
                url = "https://easydebrid.com/api/v1/link/unrestrict?link=$idOrUrl",
                qualityLabel = "EasyDebrid Instant Cloud 1080p",
                format = "mp4",
                height = 1080,
                codec = "H264"
            )
        )
        PluginStreamInfo(id = idOrUrl, url = streams.first().url, title = "EasyDebrid Stream", channelName = "EasyDebrid", videoStreams = streams)
    }
}

/**
 * Jackett & Prowlarr Local / Remote Scraper Provider Plugin
 */
class JackettProwlarrProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "jackett_prowlarr"
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsSearch = true,
        supportsMovie = true,
        supportsSeries = true,
        supportsAnime = true,
        supportsTorrent = true
    )

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = search("latest", pageToken)

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        PagedResult(listOf(PluginVideoItem(id = "jp_$query", title = "Prowlarr Indexer: $query", uploaderName = "Jackett/Prowlarr", providerId = providerId)))
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        PluginVideoItem(id = idOrUrl, title = "Prowlarr $idOrUrl", uploaderName = "Prowlarr Indexer", providerId = providerId)
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val streams = listOf(
            PluginVideoStream(
                url = "magnet:?xt=urn:btih:$idOrUrl&dn=ProwlarrResult",
                qualityLabel = "Prowlarr Multitracker Magnet 1080p",
                format = "magnet",
                height = 1080,
                codec = "HEVC"
            )
        )
        PluginStreamInfo(id = idOrUrl, url = streams.first().url, title = "Prowlarr Magnet Stream", channelName = "Jackett/Prowlarr", videoStreams = streams)
    }
}
