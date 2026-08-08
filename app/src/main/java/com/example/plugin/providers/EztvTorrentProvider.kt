package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class EztvTorrentProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    companion object {
        const val BASE_URL = "https://eztv.re/api"
    }

    override val providerId: String = "eztv_torrents"

    override val capabilities: ProviderCapabilities = ProviderCapabilities(supportsTorrent = true)

    private val tmdbProvider by lazy { TmdbTorrentProvider(http) }

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        tmdbProvider.home(pageToken)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        tmdbProvider.search(query, pageToken)
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val cleanId = extractId(idOrUrl)
        PluginVideoItem(
            id = cleanId,
            title = "EZTV TV Release $cleanId",
            uploaderName = "EZTV TV Series",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val cleanId = extractId(idOrUrl)
        val videoStreams = mutableListOf<PluginVideoStream>()

        // Magnet / Torrent stream
        if (cleanId.startsWith("magnet:") || cleanId.length > 20) {
            val rawMagnet = if (cleanId.startsWith("magnet:")) cleanId else "magnet:?xt=urn:btih:$cleanId"
            val magnetUrl = com.example.utils.TorrentUtils.formatMagnetUrl(rawMagnet)
            videoStreams.add(
                PluginVideoStream(
                    url = magnetUrl,
                    qualityLabel = "720p HDTV • EZTV",
                    format = "torrent",
                    isMuxed = true
                )
            )
        }

        PluginStreamInfo(
            id = cleanId,
            url = videoStreams.firstOrNull()?.url ?: "https://eztv.re",
            title = "EZTV Torrent Stream",
            channelName = "EZTV Torrents",
            videoStreams = videoStreams
        )
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> = withContext(Dispatchers.IO) {
        PagedResult(emptyList())
    }

    override suspend fun getSubtitles(idOrUrl: String): List<PluginSubtitle> = withContext(Dispatchers.IO) {
        emptyList()
    }

    override suspend fun getChannel(channelIdOrUrl: String): PluginChannel = withContext(Dispatchers.IO) {
        PluginChannel(id = "eztv", name = "EZTV Series")
    }

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist = withContext(Dispatchers.IO) {
        PluginPlaylist(id = "eztv", title = "EZTV Releases", uploaderName = "EZTV")
    }

    override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> = withContext(Dispatchers.IO) {
        home().items.take(10)
    }

    private fun parseEztvList(jsonStr: String): Pair<List<PluginVideoItem>, Boolean> {
        val list = mutableListOf<PluginVideoItem>()
        val json = JSONObject(jsonStr)
        val torrents = json.optJSONArray("torrents") ?: JSONArray()

        for (i in 0 until torrents.length()) {
            val tor = torrents.getJSONObject(i)
            val id = tor.optInt("id", 0)
            val title = tor.optString("title", "TV Release")
            val magnet = tor.optString("magnet_url", "")
            val hash = tor.optString("hash", "")
            val seeds = tor.optInt("seeds", 0)

            val torrentId = if (magnet.isNotEmpty()) magnet else hash.ifEmpty { id.toString() }

            list.add(
                PluginVideoItem(
                    id = torrentId,
                    title = title,
                    uploaderName = "EZTV ($seeds Seeds)",
                    thumbnailUrl = null,
                    providerId = providerId
                )
            )
        }
        return Pair(list, torrents.length() >= 30)
    }

    private fun extractId(input: String): String {
        return input.substringAfterLast("/").substringAfterLast("=")
    }
}
