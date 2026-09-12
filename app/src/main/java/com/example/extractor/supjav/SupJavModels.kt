package com.example.extractor.supjav

import androidx.annotation.Keep
import com.example.model.VideoItem

/**
 * Playable video source model for SupJav streams.
 */
@Keep
data class SupJavSource(
    val url: String,
    val mimeType: String = "application/x-mpegURL",
    val quality: String = "1080p",
    val isHls: Boolean = true,
    val headers: Map<String, String> = emptyMap(),
    val sourceName: String = "SupJav HLS"
)

/**
 * Server embed model found on SupJav detail pages.
 */
@Keep
data class SupJavServerEmbed(
    val serverName: String,
    val embedUrl: String,
    val serverType: String = "unknown"
)

/**
 * Parsed metadata details for a SupJav post/video.
 */
@Keep
data class SupJavVideoDetails(
    val id: String,
    val title: String,
    val code: String = "",
    val uploaderUrl: String,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long = 0L,
    val viewCount: Long = 0L,
    val tags: List<String> = emptyList(),
    val actresses: List<String> = emptyList(),
    val releaseDate: String? = null,
    val videoSources: List<SupJavSource> = emptyList(),
    val embedSources: List<SupJavServerEmbed> = emptyList()
) {
    fun toVideoItem(): VideoItem {
        return VideoItem(
            id = "supjav_${id.ifBlank { code.lowercase() }}",
            title = title,
            uploaderName = "SupJav • ${code.ifBlank { "JAV" }}",
            uploaderUrl = uploaderUrl,
            thumbnailUrl = thumbnailUrl,
            durationSeconds = durationSeconds,
            viewCount = viewCount,
            providerId = "supjav",
            description = buildString {
                if (code.isNotBlank()) append("Code: $code • ")
                if (actresses.isNotEmpty()) append("Cast: ${actresses.joinToString(", ")} • ")
                if (releaseDate != null) append("Released: $releaseDate • ")
                append("SupJav Direct Video Source")
            }
        )
    }
}

class SupJavException(
    val error: SupJavError,
    message: String? = null,
    cause: Throwable? = null
) : Exception(message ?: error.defaultMessage, cause)

enum class SupJavError(val defaultMessage: String) {
    NETWORK_FAILURE("Network request to SupJav failed or timed out"),
    CLOUDFLARE_BLOCK("SupJav endpoint is protected by Cloudflare bot challenge"),
    PARSING_ERROR("Failed to parse SupJav HTML structure"),
    NO_STREAMS_FOUND("No playable video streams or server embeds could be extracted"),
    EMBED_EXTRACTION_FAILED("Failed to resolve video stream from SupJav embed player"),
    UNKNOWN("An unknown error occurred in SupJav provider")
}
