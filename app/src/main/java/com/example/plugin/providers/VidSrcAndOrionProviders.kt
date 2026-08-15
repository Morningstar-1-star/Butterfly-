package com.example.plugin.providers

import android.content.Context
import android.util.Log
import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import com.example.util.DebridSettingsManager
import com.example.util.MediaIdResolver
import com.example.utils.TorrentUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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

    override fun getProviderConfig(context: Context?): ProviderConfig {
        return ProviderConfig(
            id = providerId,
            name = "VidSrc Embed Engine",
            enabled = true,
            endpoint = baseUrl,
            requiresApiKey = false,
            supportsDirectStreams = true,
            supportsWebView = true,
            healthStatus = ProviderHealthStatus.READY
        )
    }

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = search("top", pageToken)

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        PagedResult(emptyList())
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        PluginVideoItem(id = idOrUrl, title = "VidSrc $idOrUrl", uploaderName = "VidSrc", providerId = providerId)
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val identity = MediaIdResolver.resolve(idOrUrl)
        val tmdbOrImdb = identity.tmdbId ?: identity.imdbId
        val streams = mutableListOf<PluginVideoStream>()
        
        if (!tmdbOrImdb.isNullOrEmpty()) {
            val isTv = identity.mediaType == com.example.model.MediaType.TV
            val path = if (isTv) "embed/tv/$tmdbOrImdb/${identity.season ?: 1}/${identity.episode ?: 1}" else "embed/movie/$tmdbOrImdb"
            val embedUrl = "$baseUrl/$path"
            streams.add(
                PluginVideoStream(
                    url = embedUrl,
                    qualityLabel = "VidSrc Web Embed 1080p",
                    format = "embed",
                    height = 1080,
                    codec = "H264"
                )
            )
        }
        PluginStreamInfo(id = idOrUrl, url = streams.firstOrNull()?.url ?: "", title = "VidSrc Embed Stream", channelName = "VidSrc", videoStreams = streams)
    }
}

/**
 * Orion Indexer Provider Plugin
 * Real Indexer Integration (no fake direct-stream URLs)
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

    override fun getProviderConfig(context: Context?): ProviderConfig {
        val apiKey = if (context != null) DebridSettingsManager.getOrionApiKey(context) else ""
        val hasConfig = apiKey.isNotBlank()
        return ProviderConfig(
            id = providerId,
            name = "Orion Indexer Engine",
            enabled = true,
            endpoint = "https://stremio.orionoid.com",
            requiresApiKey = true,
            apiKey = if (hasConfig) apiKey else null,
            supportsDirectStreams = false,
            supportsTorrents = true,
            supportsDebrid = true,
            healthStatus = if (hasConfig) ProviderHealthStatus.READY else ProviderHealthStatus.CONFIGURATION_REQUIRED
        )
    }

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = search("popular", pageToken)

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        PagedResult(emptyList())
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        PluginVideoItem(id = idOrUrl, title = "Orion $idOrUrl", uploaderName = "Orionoid", providerId = providerId)
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val identity = MediaIdResolver.resolve(idOrUrl)
        val stremioId = identity.toStremioImdbId()
        val videoStreams = mutableListOf<PluginVideoStream>()

        if (stremioId != null) {
            val isTv = identity.mediaType == com.example.model.MediaType.TV
            // Orion Stremio index endpoint (or custom tokenized endpoint if user configured token)
            val streamUrl = if (isTv) {
                "https://stremio.orionoid.com/stremio/v1/stream/series/$stremioId.json"
            } else {
                "https://stremio.orionoid.com/stremio/v1/stream/movie/$stremioId.json"
            }
            Log.d("OrionProvider", "Requesting real Orion index stream from: $streamUrl")

            try {
                val response = http.get(streamUrl)
                val bodyTrimmed = response.body.trim()
                if (response.statusCode == 200 && bodyTrimmed.startsWith("{")) {
                    val json = JSONObject(response.body)
                    val streamArr = json.optJSONArray("streams") ?: JSONArray()
                    for (i in 0 until streamArr.length()) {
                        val st = streamArr.getJSONObject(i)
                        val title = st.optString("title", "Orion Stream ${i + 1}")
                        val name = st.optString("name", "Orionoid")
                        val url = st.optString("url")
                        val infoHash = st.optString("infoHash")

                        val cleanLabel = TorrentUtils.formatCleanQualityLabel("$name $title", "Orion")
                        if (url.isNotEmpty() && !url.contains("api.orionoid.com/v1/download/stream")) {
                            videoStreams.add(
                                PluginVideoStream(
                                    url = url,
                                    qualityLabel = cleanLabel,
                                    format = if (url.contains(".m3u8")) "hls" else "mp4",
                                    isMuxed = true
                                )
                            )
                        } else if (infoHash.isNotEmpty()) {
                            val magnet = TorrentUtils.formatMagnetUrl(infoHash, title)
                            videoStreams.add(
                                PluginVideoStream(
                                    url = magnet,
                                    qualityLabel = cleanLabel,
                                    format = "torrent",
                                    isMuxed = true
                                )
                            )
                        }
                    }
                } else {
                    Log.w("OrionProvider", "Orion indexer returned non-JSON or HTML response (status: ${response.statusCode}), ensure API key / endpoint is configured.")
                }
            } catch (e: Exception) {
                Log.e("OrionProvider", "Network error calling Orion indexer: ${e.message}")
            }
        }

        PluginStreamInfo(
            id = idOrUrl,
            url = videoStreams.firstOrNull()?.url ?: "",
            title = "Orion Indexer Stream",
            channelName = "Orionoid Indexer",
            videoStreams = videoStreams
        )
    }
}
