package com.example.plugin.providers

import android.content.Context
import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import com.example.util.DebridSettingsManager
import com.example.utils.TorrentUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    override fun getProviderConfig(context: Context?): ProviderConfig {
        return ProviderConfig(
            id = providerId,
            name = "EasyDebrid Cloud",
            enabled = true,
            endpoint = "https://easydebrid.com",
            requiresApiKey = true,
            supportsDirectStreams = true,
            supportsTorrents = true,
            supportsDebrid = true,
            healthStatus = ProviderHealthStatus.CONFIGURATION_REQUIRED
        )
    }

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = search("popular", pageToken)

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        PagedResult(emptyList())
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        PluginVideoItem(id = idOrUrl, title = "EasyDebrid $idOrUrl", uploaderName = "EasyDebrid", providerId = providerId)
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        PluginStreamInfo(id = idOrUrl, url = "", title = "EasyDebrid Stream", channelName = "EasyDebrid", videoStreams = emptyList())
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

    override fun getProviderConfig(context: Context?): ProviderConfig {
        return ProviderConfig(
            id = providerId,
            name = "Jackett / Prowlarr Self-Hosted Indexer",
            enabled = true,
            endpoint = "http://localhost:9696",
            requiresApiKey = true,
            supportsDirectStreams = false,
            supportsTorrents = true,
            healthStatus = ProviderHealthStatus.CONFIGURATION_REQUIRED
        )
    }

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = search("latest", pageToken)

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        PagedResult(emptyList())
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        PluginVideoItem(id = idOrUrl, title = "Prowlarr $idOrUrl", uploaderName = "Prowlarr Indexer", providerId = providerId)
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        PluginStreamInfo(id = idOrUrl, url = "", title = "Prowlarr Magnet Stream", channelName = "Jackett/Prowlarr", videoStreams = emptyList())
    }
}
