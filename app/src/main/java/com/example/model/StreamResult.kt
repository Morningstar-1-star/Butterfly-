package com.example.model

import com.example.resolver.SourceCandidate
import com.example.resolver.SourceStreamType

/**
 * Standardized Stream Result based on the AIOStreams unified architecture.
 * Represents a normalized, validated, and ranked media stream candidate
 * regardless of whether it originates from yt-dlp, NewPipe, direct HTTP/HLS/DASH scrapers,
 * BitTorrent swarm indexers, or Debrid providers.
 */
data class StreamResult(
    val id: String,
    val title: String,
    val providerId: String,
    val providerName: String,
    val streamType: SourceStreamType = SourceStreamType.DIRECT,
    val url: String,
    val hlsUrl: String? = null,
    val dashUrl: String? = null,
    val format: String = "mp4",
    val qualityLabel: String = "1080p",
    val qualityScore: Int = 1080,
    val bitrateBps: Long = 0L,
    val isMuxed: Boolean = true,
    val audioUrl: String? = null,
    val sizeBytes: Long = 0L,
    val formattedSize: String = "",
    val seeders: Int = 0,
    val leechers: Int = 0,
    val isTorrent: Boolean = false,
    val infoHash: String? = null,
    val fileIndex: Int? = null,
    val headers: Map<String, String> = emptyMap(),
    val audioHeaders: Map<String, String> = emptyMap(),
    val subtitleUrls: List<String> = emptyList(),
    val audioTracks: List<String> = emptyList(),
    val healthScore: Int = 100,
    val rankingScore: Int = 0,
    val isPlayable: Boolean = true,
    val isProxiedViaMediaFlow: Boolean = false,
    val mediaFlowProxyUrl: String? = null,
    val extraData: Map<String, String> = emptyMap()
) {
    /**
     * Returns the effective playback URL. If MediaFlow proxy is active and configured,
     * uses the proxied stream URL; otherwise returns the direct URL or HLS playlist.
     */
    val effectivePlayableUrl: String
        get() {
            if (isProxiedViaMediaFlow && !mediaFlowProxyUrl.isNullOrBlank()) {
                return mediaFlowProxyUrl
            }
            return hlsUrl ?: dashUrl ?: url
        }

    val displayBadge: String
        get() = when {
            isTorrent -> if (seeders > 0) "Torrent • ${seeders}S" else "Torrent"
            streamType == SourceStreamType.HLS -> "HLS • $qualityLabel"
            streamType == SourceStreamType.DASH -> "DASH • $qualityLabel"
            isProxiedViaMediaFlow -> "MediaFlow • $qualityLabel"
            else -> qualityLabel
        }

    fun toSourceCandidate(): SourceCandidate {
        return SourceCandidate(
            id = id,
            providerId = providerId,
            providerName = providerName,
            serverName = "$providerName ($qualityLabel)",
            type = streamType,
            title = title,
            urlOrMagnet = url,
            quality = qualityLabel,
            qualityScore = qualityScore,
            format = format,
            sizeBytes = sizeBytes,
            formattedSize = formattedSize,
            seeders = seeders,
            leechers = leechers,
            headers = headers,
            subtitleUrls = subtitleUrls,
            audioTracks = audioTracks,
            healthScore = healthScore,
            isPlayable = isPlayable,
            extraData = extraData
        )
    }

    fun toPlayableStreamOption(): PlayableStreamOption {
        return PlayableStreamOption(
            qualityLabel = "$qualityLabel - $providerName",
            format = format,
            isMuxed = isMuxed,
            videoUrl = effectivePlayableUrl,
            audioUrl = audioUrl,
            providerType = if (isTorrent) ProviderType.TORRENT else ProviderType.DIRECT,
            headers = headers,
            audioHeaders = audioHeaders
        )
    }
}
