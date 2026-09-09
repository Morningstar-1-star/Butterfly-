package com.example.extractor.sextb

import com.example.model.CaptionOption
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import com.example.model.StreamData
import com.example.model.VideoItem

/**
 * Explicit error states for SEXТB provider per architecture guidelines.
 */
enum class SextbError(val code: String, val userMessage: String) {
    SEARCH_FAILED("SEARCH_FAILED", "SEXТB search query returned no response or failed to parse"),
    PAGE_NOT_FOUND("PAGE_NOT_FOUND", "SEXТB requested video page was not found or has been removed"),
    PLAYER_NOT_FOUND("PLAYER_NOT_FOUND", "Player configuration could not be located in SEXТB page"),
    SOURCE_NOT_FOUND("SOURCE_NOT_FOUND", "No playable media stream could be resolved for this title"),
    HTTP_BLOCKED("HTTP_BLOCKED", "HTTP request blocked by Cloudflare or anti-bot challenge"),
    JAVASCRIPT_REQUIRED("JAVASCRIPT_REQUIRED", "Page requires JavaScript execution to decrypt media sources"),
    STREAM_EXPIRED("STREAM_EXPIRED", "Stream token has expired; refreshing playback session"),
    UNSUPPORTED_PLAYER("UNSUPPORTED_PLAYER", "Encountered an unrecognized or unsupported embed player engine")
}

class SextbException(
    val error: SextbError,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Standardized Butterfly VideoSource contract for direct media streams.
 */
data class VideoSource(
    val url: String,
    val mimeType: String = if (url.contains(".m3u8")) "application/x-mpegURL" else "video/mp4",
    val quality: String = "1080p",
    val isHls: Boolean = url.contains(".m3u8") || mimeType.contains("mpegURL", ignoreCase = true),
    val headers: Map<String, String> = emptyMap(),
    val subtitleUrl: String? = null,
    val subtitles: List<CaptionOption> = emptyList(),
    val resolution: String? = null,
    val bitrate: Long = 0L,
    val sourceName: String = "SEXТB StreamTB",
    val sizeText: String = ""
) {
    fun toPlayableStreamOption(releaseTitle: String = ""): PlayableStreamOption {
        return PlayableStreamOption(
            qualityLabel = quality,
            format = if (isHls) "hls" else "mp4",
            isMuxed = true,
            videoUrl = url,
            audioUrl = null,
            providerType = ProviderType.DIRECT,
            headers = headers,
            audioHeaders = headers,
            sourceName = sourceName,
            qualityCategory = quality,
            sizeText = sizeText,
            releaseTitle = releaseTitle
        )
    }
}

data class SextbEpisode(
    val id: String,
    val episodeNumber: Int,
    val title: String,
    val pageUrl: String,
    val thumbnailUrl: String? = null
) {
    fun toVideoItem(seriesTitle: String): VideoItem {
        return VideoItem(
            id = id,
            title = if (title.isNotBlank()) "$seriesTitle - $title" else "$seriesTitle - Episode $episodeNumber",
            uploaderName = "SEXТB",
            uploaderUrl = pageUrl,
            thumbnailUrl = thumbnailUrl,
            providerId = "sextb",
            tags = listOf("SEXTB", "Episode $episodeNumber")
        )
    }
}

/**
 * Standardized media details extracted from SEXТB.
 */
data class SextbVideoDetails(
    val id: String,
    val pageUrl: String,
    val title: String,
    val originalTitle: String? = null,
    val thumbnailUrl: String? = null,
    val description: String? = null,
    val releaseDate: String? = null,
    val durationSeconds: Long = -1,
    val actors: List<String> = emptyList(),
    val studio: String? = null,
    val director: String? = null,
    val categories: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val episodes: List<SextbEpisode> = emptyList(),
    val availableSources: List<VideoSource> = emptyList()
) {
    fun toVideoItem(): VideoItem {
        val allTags = (tags + categories + listOfNotNull(studio, director) + actors).distinct()
        return VideoItem(
            id = id,
            title = title,
            originalTitle = originalTitle,
            uploaderName = studio ?: director ?: "SEXТB",
            uploaderUrl = pageUrl,
            uploaderAvatarUrl = thumbnailUrl,
            thumbnailUrl = thumbnailUrl,
            providerId = "sextb",
            tags = allTags,
            description = buildDescription(),
            durationSeconds = durationSeconds,
            uploadDate = releaseDate
        )
    }

    fun toStreamData(sources: List<VideoSource> = availableSources): StreamData {
        val streamOptions = sources.map { it.toPlayableStreamOption(title) }
        val primarySource = sources.firstOrNull()
        val hlsUrl = sources.firstOrNull { it.isHls }?.url ?: primarySource?.url
        val captions = sources.flatMap { it.subtitles }.distinctBy { it.url }

        return StreamData(
            videoId = id,
            videoUrl = pageUrl,
            title = title,
            channelName = studio ?: director ?: "SEXТB",
            channelAvatarUrl = thumbnailUrl,
            uploadDate = releaseDate,
            description = buildDescription(),
            captionOptions = captions,
            availableStreamOptions = streamOptions,
            selectedStreamOption = streamOptions.firstOrNull(),
            hlsUrl = hlsUrl,
            providerId = "sextb",
            thumbnailUrl = thumbnailUrl,
            providerType = ProviderType.DIRECT,
            headers = primarySource?.headers ?: emptyMap(),
            tags = tags + categories,
            originalTitle = originalTitle
        )
    }

    private fun buildDescription(): String {
        val parts = mutableListOf<String>()
        if (!description.isNullOrBlank()) parts.add(description)
        if (actors.isNotEmpty()) parts.add("Actors: ${actors.joinToString(", ")}")
        if (!studio.isNullOrBlank()) parts.add("Studio: $studio")
        if (!director.isNullOrBlank()) parts.add("Director: $director")
        if (!releaseDate.isNullOrBlank()) parts.add("Release Date: $releaseDate")
        return parts.joinToString("\n\n")
    }
}
