package com.example.resolver

enum class SourceStreamType {
    DIRECT,
    HLS,
    DASH,
    TORRENT,
    LOCAL
}

data class PlaybackCapabilities(
    val supportsSeeking: Boolean = true,
    val supportsTrackSelection: Boolean = true,
    val supportsSpeedChange: Boolean = true,
    val isLiveStream: Boolean = false
)

/**
 * Capability-rich unified media source representation.
 * Encapsulates both direct stream servers (Vega/HLS/MP4) and BitTorrent magnet sources
 * with complete technical telemetry (seeders, bitrate, resolution, headers, audio/sub tracks).
 */
data class SourceCandidate(
    val id: String,
    val providerId: String,
    val providerName: String,
    val serverName: String,
    val type: SourceStreamType,
    val title: String,
    val urlOrMagnet: String,
    val quality: String = "1080p",
    val qualityScore: Int = 1080,
    val format: String = "mp4",
    val sizeBytes: Long = 0L,
    val formattedSize: String = "",
    val seeders: Int = 0,
    val leechers: Int = 0,
    val headers: Map<String, String> = emptyMap(),
    val subtitleUrls: List<String> = emptyList(),
    val audioTracks: List<String> = emptyList(),
    val videoTracks: List<String> = emptyList(),
    val healthScore: Int = 100, // 0-100 score
    val isPlayable: Boolean = true,
    val capabilities: PlaybackCapabilities = PlaybackCapabilities(),
    val extraData: Map<String, String> = emptyMap()
) {
    val isTorrent: Boolean
        get() = type == SourceStreamType.TORRENT || urlOrMagnet.startsWith("magnet:", ignoreCase = true)

    val badgeLabel: String
        get() = when (type) {
            SourceStreamType.DIRECT -> "Direct"
            SourceStreamType.HLS -> "HLS"
            SourceStreamType.DASH -> "DASH"
            SourceStreamType.TORRENT -> if (seeders > 0) "Torrent • ${seeders}S" else "Torrent"
            SourceStreamType.LOCAL -> "Local"
        }
}
