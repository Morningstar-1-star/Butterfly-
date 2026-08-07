package com.example.model

enum class StreamFailureReason(val code: String, val description: String) {
    NETWORK_ERROR("NET_ERR", "Network connectivity issue or host unreachable"),
    HTTP_404_NOT_FOUND("HTTP_404", "Stream resource not found on server (404)"),
    HTTP_403_FORBIDDEN("HTTP_403", "Access forbidden or hotlink protected (403)"),
    TIMEOUT("TIMEOUT", "Provider or stream verification timed out (>8s)"),
    CLOUDFLARE_BLOCKED("CF_BLOCKED", "Cloudflare anti-bot verification required"),
    DEAD_TORRENT("DEAD_TORRENT", "Torrent has 0 seeders / dead hash"),
    NO_PEERS("NO_PEERS", "No active BitTorrent peers found"),
    INVALID_MAGNET("BAD_MAGNET", "Malformed magnet URI or missing infohash"),
    INVALID_HLS("BAD_HLS", "Invalid M3U8 playlist or expired segment tokens"),
    INVALID_EMBED("BAD_EMBED", "Embed page failed to load or refused iframe connection"),
    PARSING_FAILED("PARSE_ERR", "Failed to parse stream metadata or API response"),
    TMDB_FAILED("TMDB_ERR", "TMDB metadata lookup or API resolution failed")
}

data class StreamValidationResult(
    val isValid: Boolean,
    val url: String,
    val streamType: StreamType,
    val failureReason: StreamFailureReason? = null,
    val httpCode: Int = 0,
    val latencyMs: Long = 0L,
    val contentType: String? = null
)

enum class StreamType {
    DIRECT_HLS,
    DIRECT_MP4,
    DIRECT_DASH,
    EMBED_PAGE,
    MAGNET,
    UNKNOWN
}
