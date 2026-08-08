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
        val cleanId = idOrUrl.replace("comet_", "").replace("tt", "")
        val formattedImdb = if (cleanId.startsWith("tt")) cleanId else "tt$cleanId"
        val isTv = idOrUrl.contains("series") || idOrUrl.contains("tv_")
        val streamUrl = if (isTv) "$baseUrl/stream/series/$formattedImdb:1:1.json" else "$baseUrl/stream/movie/$formattedImdb.json"
        
        val streams = mutableListOf<PluginVideoStream>()

        try {
            val response = http.get(streamUrl)
            if (response.statusCode == 200 && response.body.isNotBlank()) {
                val json = org.json.JSONObject(response.body)
                val streamArr = json.optJSONArray("streams") ?: org.json.JSONArray()
                for (i in 0 until streamArr.length()) {
                    val st = streamArr.getJSONObject(i)
                    val title = st.optString("title", "Comet Stream ${i + 1}")
                    val name = st.optString("name", "Comet")
                    val url = st.optString("url")
                    val infoHash = st.optString("infoHash")

                    val cleanLabel = com.example.utils.TorrentUtils.formatCleanQualityLabel("$name $title", "Comet")
                    if (url.isNotEmpty()) {
                        streams.add(
                            PluginVideoStream(
                                url = url,
                                qualityLabel = cleanLabel,
                                format = if (url.contains(".m3u8")) "hls" else "mp4",
                                isMuxed = true
                            )
                        )
                    } else if (infoHash.isNotEmpty()) {
                        val magnet = com.example.utils.TorrentUtils.formatMagnetUrl(infoHash, title)
                        streams.add(
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
            e.printStackTrace()
        }

        PluginStreamInfo(
            id = idOrUrl,
            url = streams.firstOrNull()?.url ?: "",
            title = "Comet Stream",
            channelName = "Comet Fast Indexer",
            videoStreams = streams
        )
    }
}
