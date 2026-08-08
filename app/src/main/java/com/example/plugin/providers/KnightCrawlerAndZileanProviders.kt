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

    private fun getBaseUrl(context: Context?): String {
        return if (context != null) {
            DebridSettingsManager.getZileanEndpoint(context)
        } else {
            "https://zilean.elfhosted.com"
        }
    }

    override fun getProviderConfig(context: Context?): ProviderConfig {
        val endpoint = getBaseUrl(context)
        return ProviderConfig(
            id = providerId,
            name = "Zilean DMM Indexer",
            enabled = true,
            endpoint = endpoint,
            requiresApiKey = false,
            supportedMediaTypes = listOf("movie", "series"),
            supportsDirectStreams = true,
            supportsTorrents = true,
            healthStatus = if (endpoint.isNotBlank()) ProviderHealthStatus.READY else ProviderHealthStatus.CONFIGURATION_REQUIRED
        )
    }

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = search("trending", pageToken)

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        PagedResult(emptyList())
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        PluginVideoItem(id = idOrUrl, title = "Zilean $idOrUrl", uploaderName = "Zilean", providerId = providerId)
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val identity = MediaIdResolver.resolve(idOrUrl)
        val stremioId = identity.toStremioImdbId()
        val videoStreams = mutableListOf<PluginVideoStream>()

        if (stremioId != null) {
            val isTv = identity.mediaType == com.example.model.MediaType.TV
            val baseUrl = getBaseUrl(null)
            val streamUrl = if (isTv) "$baseUrl/stream/series/$stremioId.json" else "$baseUrl/stream/movie/$stremioId.json"
            Log.d("ZileanProvider", "Requesting Zilean streams from real endpoint: $streamUrl")

            try {
                val response = http.get(streamUrl)
                if (response.statusCode == 200 && response.body.isNotBlank()) {
                    val json = JSONObject(response.body)
                    val streamArr = json.optJSONArray("streams") ?: JSONArray()
                    for (i in 0 until streamArr.length()) {
                        val st = streamArr.getJSONObject(i)
                        val title = st.optString("title", "Zilean Stream ${i + 1}")
                        val name = st.optString("name", "Zilean DMM")
                        val url = st.optString("url")
                        val infoHash = st.optString("infoHash")

                        val cleanLabel = TorrentUtils.formatCleanQualityLabel("$name $title", "Zilean")
                        if (url.isNotEmpty()) {
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
                }
            } catch (e: Exception) {
                Log.e("ZileanProvider", "Network request failed for Zilean endpoint: ${e.message}")
            }
        }

        PluginStreamInfo(
            id = idOrUrl,
            url = videoStreams.firstOrNull()?.url ?: "",
            title = "Zilean DMM Stream",
            channelName = "Zilean DMM",
            videoStreams = videoStreams
        )
    }
}
