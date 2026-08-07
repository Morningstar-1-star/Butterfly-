package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Unified Torrent Provider that consolidates all torrent indexers (Torrentio, YTS, EZTV, 1337x, Nyaa, PirateBay, TMDB)
 * into a single unified stream source with high-speed mirror auto-scanning.
 */
class UnifiedTorrentProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "unified_torrents"

    private val subProviders: List<ContentProviderApi> by lazy {
        listOf(
            TorrentioAggregatorProvider(http),
            TorrentApiMultiProvider(http),
            YtsTorrentProvider(http),
            EztvTorrentProvider(http),
            NyaaAnimeProvider(http),
            TmdbTorrentProvider(http)
        )
    }

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        coroutineScope {
            val jobs = subProviders.map { provider ->
                async {
                    try {
                        provider.home(pageToken).items
                    } catch (e: Exception) {
                        emptyList<PluginVideoItem>()
                    }
                }
            }
            val results = jobs.awaitAll().flatten()
            val deduplicated = results.distinctBy { it.id.lowercase() }.map { it.copy(providerId = providerId) }
            PagedResult(
                items = deduplicated,
                nextPageToken = pageToken,
                hasMore = deduplicated.isNotEmpty()
            )
        }
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        coroutineScope {
            val jobs = subProviders.map { provider ->
                async {
                    try {
                        provider.search(query, pageToken).items
                    } catch (e: Exception) {
                        emptyList<PluginVideoItem>()
                    }
                }
            }
            val results = jobs.awaitAll().flatten()
            val deduplicated = results.distinctBy { it.id.lowercase() }.map { it.copy(providerId = providerId) }
            PagedResult(
                items = deduplicated,
                nextPageToken = pageToken,
                hasMore = deduplicated.isNotEmpty()
            )
        }
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val firstResult = subProviders.firstNotNullOfOrNull {
            try { it.getVideo(idOrUrl) } catch (e: Exception) { null }
        }
        firstResult ?: PluginVideoItem(
            id = idOrUrl,
            title = "Unified Stream $idOrUrl",
            uploaderName = "Unified Torrents (Auto Scanner)",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        coroutineScope {
            val jobs = subProviders.map { provider ->
                async {
                    try {
                        provider.getStreams(idOrUrl)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            val streamInfos = jobs.awaitAll().filterNotNull()

            val combinedVideoStreams = mutableListOf<PluginVideoStream>()
            val combinedAudioStreams = mutableListOf<PluginAudioStream>()
            val combinedSubtitles = mutableListOf<PluginSubtitle>()
            var mainTitle = "Unified Torrent Stream"
            var desc: String? = null
            var channel: String = "Unified Torrents Engine"
            var avatar: String? = null
            var thumb: String? = null

            val serverNamePrefixes = listOf(
                "Mhl-ply", "Ophm", "VidStpm", "VidHndi", "VidEnd", 
                "Torrentio", "VidFast", "PeerStream", "Nitro-Mux", "Lolly"
            )

            var idx = 0
            streamInfos.forEach { info ->
                if (mainTitle == "Unified Torrent Stream" && info.title.isNotBlank()) {
                    mainTitle = info.title
                }
                if (desc.isNullOrBlank()) desc = info.description
                if (channel == "Unified Torrents Engine" && info.channelName.isNotBlank()) channel = info.channelName
                if (avatar.isNullOrBlank()) avatar = info.channelAvatarUrl
                if (thumb.isNullOrBlank()) thumb = info.thumbnailUrl

                combinedSubtitles.addAll(info.subtitles)
                combinedAudioStreams.addAll(info.audioStreams)

                info.videoStreams.forEach { stream ->
                    val url = stream.url ?: ""
                    val formattedUrl = if (url.startsWith("magnet:") || url.contains("xt=urn:btih:")) {
                        com.example.utils.TorrentUtils.formatMagnetUrl(url, mainTitle)
                    } else {
                        url
                    }
                    val cleanLabel = com.example.utils.TorrentUtils.formatCleanQualityLabel(stream.qualityLabel)
                    combinedVideoStreams.add(stream.copy(url = formattedUrl, qualityLabel = cleanLabel))
                }
            }

            // Deduplicate streams by clean quality label or URL
            val formattedServerStreams = combinedVideoStreams.distinctBy { (it.url ?: "") + "_" + it.qualityLabel }

            PluginStreamInfo(
                id = idOrUrl,
                url = idOrUrl,
                title = mainTitle,
                channelName = channel,
                channelAvatarUrl = avatar,
                description = desc,
                videoStreams = formattedServerStreams,
                audioStreams = combinedAudioStreams.distinctBy { it.url },
                subtitles = combinedSubtitles.distinctBy { it.url },
                thumbnailUrl = thumb
            )
        }
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> {
        return PagedResult(emptyList())
    }

    override suspend fun getSubtitles(idOrUrl: String): List<PluginSubtitle> {
        return emptyList()
    }

    override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> = withContext(Dispatchers.IO) {
        subProviders.firstOrNull()?.getRecommendations(idOrUrl) ?: emptyList()
    }

    override suspend fun getChannel(channelIdOrUrl: String): PluginChannel = withContext(Dispatchers.IO) {
        PluginChannel(id = "unified_torrents", name = "Unified Torrents (Auto Scanner)")
    }

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist = withContext(Dispatchers.IO) {
        PluginPlaylist(id = "unified_torrents", title = "Unified Torrents Collection", uploaderName = "Auto Scanner")
    }
}
