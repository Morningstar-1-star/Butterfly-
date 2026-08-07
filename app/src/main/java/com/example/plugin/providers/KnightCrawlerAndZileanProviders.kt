package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * KnightCrawler Stremio / Torrent Scraper Plugin
 */
class KnightCrawlerProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "knightcrawler"
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsSearch = true,
        supportsMovie = true,
        supportsSeries = true,
        supportsTorrent = true
    )

    private val baseUrl = "https://knightcrawler.elfhosted.com"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = search("popular", pageToken)

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        PagedResult(
            listOf(
                PluginVideoItem(
                    id = "kc_$query",
                    title = "KnightCrawler: $query",
                    uploaderName = "KnightCrawler DHT Engine",
                    providerId = providerId
                )
            )
        )
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        PluginVideoItem(id = idOrUrl, title = "KnightCrawler $idOrUrl", uploaderName = "KnightCrawler", providerId = providerId)
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val streams = listOf(
            PluginVideoStream(
                url = "$baseUrl/stream/movie/$idOrUrl.json",
                qualityLabel = "KnightCrawler 1080p HEVC Multi-Lang",
                format = "mkv",
                height = 1080,
                codec = "HEVC"
            )
        )
        PluginStreamInfo(id = idOrUrl, url = streams.first().url, title = "KnightCrawler Stream", channelName = "KnightCrawler DHT", videoStreams = streams)
    }
}

/**
 * Zilean DMM / Torrent Indexer Plugin
 */
class ZileanProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "zilean"
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsSearch = true,
        supportsMovie = true,
        supportsSeries = true,
        supportsTorrent = true
    )

    private val baseUrl = "https://zilean.elfhosted.com"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = search("trending", pageToken)

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        PagedResult(
            listOf(
                PluginVideoItem(
                    id = "zilean_$query",
                    title = "Zilean DMM: $query",
                    uploaderName = "Zilean Indexer",
                    providerId = providerId
                )
            )
        )
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        PluginVideoItem(id = idOrUrl, title = "Zilean $idOrUrl", uploaderName = "Zilean", providerId = providerId)
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val streams = listOf(
            PluginVideoStream(
                url = "$baseUrl/dmm/search?query=$idOrUrl",
                qualityLabel = "Zilean 4K Remux HEVC",
                format = "mkv",
                height = 2160,
                codec = "HEVC"
            )
        )
        PluginStreamInfo(id = idOrUrl, url = streams.first().url, title = "Zilean Remux Stream", channelName = "Zilean DMM", videoStreams = streams)
    }
}
