package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Comet Stremio / Torrent Indexer Provider Plugin
 */
class CometProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "comet"
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsSearch = true,
        supportsMovie = true,
        supportsSeries = true,
        supportsTorrent = true
    )

    private val baseUrl = "https://comet.elfhosted.com"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = search("popular", pageToken)

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val items = listOf(
            PluginVideoItem(
                id = "comet_${query.lowercase()}",
                title = "Comet Stream: $query",
                uploaderName = "Comet Stremio",
                providerId = providerId
            )
        )
        PagedResult(items)
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        PluginVideoItem(
            id = idOrUrl,
            title = "Comet Stream $idOrUrl",
            uploaderName = "Comet Indexer",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val streams = listOf(
            PluginVideoStream(
                url = "$baseUrl/stream/movie/$idOrUrl.json",
                qualityLabel = "Comet 4K AV1 Ultra HDR",
                format = "mp4",
                height = 2160,
                codec = "AV1"
            ),
            PluginVideoStream(
                url = "$baseUrl/stream/movie/${idOrUrl}_1080p.json",
                qualityLabel = "Comet 1080p HEVC Multi-Audio",
                format = "mp4",
                height = 1080,
                codec = "HEVC"
            )
        )

        PluginStreamInfo(
            id = idOrUrl,
            url = streams.first().url,
            title = "Comet High Speed Stream",
            channelName = "Comet Fast Indexer",
            videoStreams = streams
        )
    }
}
