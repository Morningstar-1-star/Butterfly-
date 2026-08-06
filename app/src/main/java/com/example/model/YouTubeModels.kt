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
    val thumbnailUrl: String? = null
) {
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
    val audioStream: AudioStream? = null
)

data class CaptionOption(
    val languageName: String,
    val languageCode: String,
    val format: String,
    val url: String
)

data class StreamData(
    val videoId: String,
    val videoUrl: String,
    val title: String,
    val channelName: String,
    val channelAvatarUrl: String?,
    val subscriberCountText: String?,
    val viewCount: Long,
    val likeCount: Long,
    val uploadDate: String?,
    val description: String?,
    val progressiveStreams: List<VideoStream>,
    val videoOnlyStreams: List<VideoStream>,
    val audioStreams: List<AudioStream>,
    val captionOptions: List<CaptionOption>,
    val availableStreamOptions: List<PlayableStreamOption>,
    val selectedStreamOption: PlayableStreamOption?,
    val hlsUrl: String?,
    val relatedVideos: List<VideoItem>
)
