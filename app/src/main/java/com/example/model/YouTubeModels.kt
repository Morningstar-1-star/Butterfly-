package com.example.model

import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream

data class VideoItem(
    val id: String,
    val title: String,
    val uploaderName: String,
    val uploaderUrl: String? = null,
    val uploaderAvatarUrl: String? = null,
    val viewCount: Long = -1,
    val durationSeconds: Long = -1,
    val uploadDate: String? = null,
    val thumbnailUrl: String? = null,
    val providerId: String? = null,
    val tags: List<String> = emptyList(),
    val description: String? = null,
    val previewThumbnails: List<String> = emptyList(),
    val previewClipUrl: String? = null
) {
    val cleanTags: List<String>
        get() {
            val listTags = tags.map { it.replace("#", "").trim() }.filter { it.isNotEmpty() }
            if (listTags.isNotEmpty()) return listTags.distinct().take(5)

            val stopWords = setOf("with", "from", "that", "this", "what", "video", "official", "full", "hd", "4k", "2024", "2025", "2026", "the", "and", "for", "you", "about", "are", "have", "more")
            val extracted = (title + " " + uploaderName)
                .split(" ", "-", "_", "|", "/", ":", ",", "[", "]", "(", ")")
                .map { it.replace("#", "").trim() }
                .filter { word -> word.length >= 3 && word.lowercase() !in stopWords && word.any { c -> c.isLetter() } }
                .distinctBy { it.lowercase() }

            return extracted.take(5)
        }

    val formattedDuration: String
        get() {
            if (durationSeconds <= 0) return ""
            val hours = durationSeconds / 3600
            val minutes = (durationSeconds % 3600) / 60
            val seconds = durationSeconds % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

    val displayDuration: String
        get() {
            if (durationSeconds > 0) return formattedDuration
            return ""
        }

    val formattedViews: String
        get() {
            if (viewCount < 0) return ""
            return when {
                viewCount >= 1_000_000 -> String.format("%.1fM views", viewCount / 1_000_000.0)
                viewCount >= 1_000 -> String.format("%.1fK views", viewCount / 1_000.0)
                else -> "$viewCount views"
            }
        }
}

enum class ExtractorErrorType {
    PO_TOKEN_REQUIRED,
    SABR_PROTECTION,
    SIGNATURE_DECRYPTION_FAILED,
    RECAPTCHA_REQUIRED,
    AGE_RESTRICTED,
    GEO_RESTRICTED,
    NETWORK_ERROR,
    NO_PLAYABLE_STREAMS,
    UNAVAILABLE,
    YOUTUBE_IP_BLOCKED,
    UNKNOWN
}

data class ExtractorErrorDetails(
    val errorType: ExtractorErrorType,
    val message: String,
    val rawExceptionName: String,
    val fullStackTrace: String,
    val urlOrId: String,
    val causeInfo: String? = null,
    val technicalFixSuggestion: String? = null
)

data class FeedErrorDetails(
    val rawExceptionName: String,
    val message: String,
    val fullStackTrace: String,
    val causeInfo: String? = null,
    val urlOrQuery: String? = null
)

sealed class FeedResult {
    data class Success(val items: List<VideoItem>) : FeedResult()
    data class Error(val errorDetails: FeedErrorDetails) : FeedResult()
}

data class PlayableStreamOption(
    val qualityLabel: String,
    val format: String,
    val isMuxed: Boolean, // Video + Audio combined
    val videoStream: VideoStream? = null,
    val audioStream: AudioStream? = null,
    val videoUrl: String? = null,
    val audioUrl: String? = null,
    val providerType: ProviderType = ProviderType.OTHER,
    val headers: Map<String, String> = emptyMap(),
    val audioHeaders: Map<String, String> = emptyMap()
)

data class CaptionOption(
    val languageName: String,
    val languageCode: String,
    val format: String,
    val url: String
)

data class StreamData(
    val videoId: String,
    val videoUrl: String = "",
    val title: String,
    val channelName: String,
    val channelAvatarUrl: String? = null,
    val subscriberCountText: String? = null,
    val viewCount: Long = -1,
    val likeCount: Long = -1,
    val uploadDate: String? = null,
    val description: String? = null,
    val progressiveStreams: List<VideoStream> = emptyList(),
    val videoOnlyStreams: List<VideoStream> = emptyList(),
    val audioStreams: List<AudioStream> = emptyList(),
    val captionOptions: List<CaptionOption> = emptyList(),
    val availableStreamOptions: List<PlayableStreamOption> = emptyList(),
    val selectedStreamOption: PlayableStreamOption? = null,
    val hlsUrl: String? = null,
    val relatedVideos: List<VideoItem> = emptyList(),
    val providerId: String? = null,
    val thumbnailUrl: String? = null,
    val providerType: ProviderType = ProviderType.OTHER,
    val headers: Map<String, String> = emptyMap()
) {
    val effectiveThumbnailUrl: String?
        get() {
            if (!thumbnailUrl.isNullOrEmpty()) return thumbnailUrl
            if (videoId.isNotEmpty() && !videoId.startsWith("http")) {
                return "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            }
            if (!channelAvatarUrl.isNullOrEmpty()) return channelAvatarUrl
            return null
        }
}

fun parseDurationToSeconds(raw: String?): Long {
    if (raw.isNullOrBlank()) return -1L
    val trimmed = raw.trim()
    if (trimmed.all { it.isDigit() }) {
        return trimmed.toLongOrNull() ?: -1L
    }
    val asFloat = trimmed.toDoubleOrNull()
    if (asFloat != null) {
        return asFloat.toLong()
    }
    val parts = trimmed.split(":")
    return when (parts.size) {
        2 -> (parts[0].toLongOrNull() ?: 0L) * 60L + (parts[1].toLongOrNull() ?: 0L)
        3 -> (parts[0].toLongOrNull() ?: 0L) * 3600L + (parts[1].toLongOrNull() ?: 0L) * 60L + (parts[2].toLongOrNull() ?: 0L)
        else -> -1L
    }
}

