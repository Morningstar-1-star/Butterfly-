package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Unified Torrent Aggregator Provider that merges, deduplicates, and ranks
 * results across all independent torrent/debrid plugins (Torrentio, MediaFusion, Orion,
 * Comet, KnightCrawler, Zilean, VidSrc, TorBox, EasyDebrid, Jackett/Prowlarr, YTS, EZTV, Nyaa, TMDB).
 */
class UnifiedTorrentProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "unified_torrents"
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsSearch = true,
        supportsMovie = true,
        supportsSeries = true,
        supportsAnime = true,
        supportsTorrent = true,
        supportsSubtitles = true
    )

    private val subProviders: List<ContentProviderApi> by lazy {
        listOf(
            TorrentioAggregatorProvider(http),
            MediaFusionProvider(http),
            CometProvider(http),
            ZileanProvider(http),
            VidSrcProvider(http),
            OrionProvider(http),
            TorBoxProvider(http),
            EasyDebridProvider(http),
            JackettProwlarrProvider(http),
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
                        withTimeoutOrNull(6000L) {
                            provider.home(pageToken).items
                        } ?: emptyList()
                    } catch (e: Exception) {
                        emptyList()
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
                        withTimeoutOrNull(6000L) {
                            provider.search(query, pageToken).items
                        } ?: emptyList()
                    } catch (e: Exception) {
                        emptyList()
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
            try {
                withTimeoutOrNull(3000L) { it.getVideo(idOrUrl) }
            } catch (e: Exception) { null }
        }
        firstResult ?: PluginVideoItem(
            id = idOrUrl,
            title = "Unified Stream $idOrUrl",
            uploaderName = "Unified Torrents Engine",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        coroutineScope {
            val jobs = subProviders.map { provider ->
                async {
                    try {
                        withTimeoutOrNull(7000L) {
                            provider.getStreams(idOrUrl)
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            val streamInfos = jobs.awaitAll().filterNotNull()

            val combinedVideoStreams = mutableListOf<PluginVideoStream>()
            val combinedAudioStreams = mutableListOf<PluginAudioStream>()
            val combinedSubtitles = mutableListOf<PluginSubtitle>()
            var mainTitle = "Unified Stream"
            var desc: String? = null
            var channel: String = "Unified Torrents Aggregator"
            var avatar: String? = null
            var thumb: String? = null

            streamInfos.forEach { info ->
                if (mainTitle == "Unified Stream" && info.title.isNotBlank()) mainTitle = info.title
                if (desc.isNullOrBlank()) desc = info.description
                if (channel == "Unified Torrents Aggregator" && info.channelName.isNotBlank()) channel = info.channelName
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

            // Deduplicate streams strictly by InfoHash and Normalized URL
            val seenHashes = mutableSetOf<String>()
            val seenUrls = mutableSetOf<String>()
            val deduplicatedList = mutableListOf<PluginVideoStream>()

            for (stream in combinedVideoStreams) {
                val url = stream.url ?: continue
                var hash: String? = null
                if (url.contains("xt=urn:btih:", ignoreCase = true)) {
                    val match = Regex("xt=urn:btih:([a-fA-F0-9]{40}|[a-zA-Z2-7]{32})", RegexOption.IGNORE_CASE).find(url)
                    hash = match?.groupValues?.get(1)?.lowercase()
                }

                if (hash != null) {
                    if (seenHashes.contains(hash)) continue
                    seenHashes.add(hash)
                    deduplicatedList.add(stream)
                } else {
                    val norm = url.trim().replace(Regex("([?&])(utm_[^&]+|_t=[^&]+|ref=[^&]+)"), "")
                    if (seenUrls.contains(norm)) continue
                    seenUrls.add(norm)
                    deduplicatedList.add(stream)
                }
            }

            // Rank streams using Codec Preference (AV1 -> HEVC -> H.264) + Resolution + Quality
            val rankedStreams = deduplicatedList.sortedByDescending { stream ->
                var score = 0
                // Codec score
                when {
                    stream.codec.equals("AV1", ignoreCase = true) || stream.qualityLabel.contains("AV1", ignoreCase = true) -> score += 100
                    stream.codec.equals("HEVC", ignoreCase = true) || stream.qualityLabel.contains("HEVC", ignoreCase = true) || stream.qualityLabel.contains("H265", ignoreCase = true) -> score += 70
                    stream.codec.equals("H264", ignoreCase = true) || stream.qualityLabel.contains("H264", ignoreCase = true) || stream.qualityLabel.contains("AVC", ignoreCase = true) -> score += 40
                    else -> score += 20
                }
                // Resolution score
                if (stream.qualityLabel.contains("4K", ignoreCase = true) || stream.height >= 2160) score += 50
                else if (stream.qualityLabel.contains("1080", ignoreCase = true) || stream.height >= 1080) score += 30
                else if (stream.qualityLabel.contains("720", ignoreCase = true) || stream.height >= 720) score += 15

                score
            }

            PluginStreamInfo(
                id = idOrUrl,
                url = idOrUrl,
                title = mainTitle,
                channelName = channel,
                channelAvatarUrl = avatar,
                description = desc,
                videoStreams = rankedStreams,
                audioStreams = combinedAudioStreams.distinctBy { it.url },
                subtitles = combinedSubtitles.distinctBy { it.url },
                thumbnailUrl = thumb
            )
        }
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> = PagedResult(emptyList())

    override suspend fun getSubtitles(idOrUrl: String): List<PluginSubtitle> = emptyList()

    override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> = withContext(Dispatchers.IO) {
        subProviders.firstOrNull()?.getRecommendations(idOrUrl) ?: emptyList()
    }

    override suspend fun getChannel(channelIdOrUrl: String): PluginChannel = withContext(Dispatchers.IO) {
        PluginChannel(id = "unified_torrents", name = "Unified Torrents Aggregator")
    }

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist = withContext(Dispatchers.IO) {
        PluginPlaylist(id = "unified_torrents", title = "Unified Torrents Collection", uploaderName = "Aggregator Engine")
    }
}
