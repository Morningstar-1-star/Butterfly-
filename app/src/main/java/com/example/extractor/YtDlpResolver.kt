package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.*
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object YtDlpResolver {
    private const val TAG = "YtDlpResolver"

    fun prewarm(context: Context) {
        try {
            YoutubeDL.getInstance().init(context)
        } catch (e: Throwable) {
            Log.w(TAG, "YoutubeDL prewarm init: ${e.message}")
        }
    }

    fun isYtDlpSupportedUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://") || url.contains("youtube.com") || url.contains("youtu.be") || url.length == 11
    }

    suspend fun extractStreamInfo(ctx: Context, targetUrl: String): YouTubeExtractorHelper.ExtractionResult = withContext(Dispatchers.IO) {
        try {
            val isYouTube = targetUrl.contains("youtube.com") || targetUrl.contains("youtu.be") || (targetUrl.length == 11 && !targetUrl.startsWith("http"))
            val videoUrl = if (targetUrl.startsWith("http")) {
                targetUrl
            } else if (targetUrl.length == 11) {
                "https://www.youtube.com/watch?v=$targetUrl"
            } else {
                targetUrl
            }

            Log.i(TAG, "Executing generic yt-dlp for video: $videoUrl")

            try {
                YoutubeDL.getInstance().init(ctx)
            } catch (e: Throwable) {
                Log.w(TAG, "YoutubeDL init note: ${e.message}")
            }

            val request = YoutubeDLRequest(videoUrl)
            request.addOption("-f", "best[ext=mp4]/best")
            request.addOption("--dump-json")
            request.addOption("--no-playlist")

            val response: YoutubeDLResponse = YoutubeDL.getInstance().execute(request)
            val jsonStr = response.out
            if (jsonStr.isBlank()) {
                throw IllegalStateException("yt-dlp returned empty output")
            }

            val json = JSONObject(jsonStr)
            val title = json.optString("title", "Video")
            val uploader = json.optString("uploader", json.optString("extractor", "Online Video"))
            val description = json.optString("description", "")
            val thumbnail = json.optString("thumbnail", "")

            val options = mutableListOf<PlayableStreamOption>()
            val headersMap = if (isYouTube) mapOf("Referer" to "https://www.youtube.com/") else emptyMap<String, String>()

            val formats = json.optJSONArray("formats")
            if (formats != null) {
                for (i in 0 until formats.length()) {
                    val fmt = formats.optJSONObject(i) ?: continue
                    val url = fmt.optString("url", "")
                    if (url.isBlank()) continue
                    val note = fmt.optString("format_note", "720p")
                    val ext = fmt.optString("ext", "mp4")
                    val acodec = fmt.optString("acodec", "none")
                    val vcodec = fmt.optString("vcodec", "none")
                    val isMuxed = acodec != "none" && vcodec != "none"

                    options.add(
                        PlayableStreamOption(
                            qualityLabel = "yt-dlp $note ($ext)",
                            format = ext,
                            isMuxed = isMuxed,
                            videoUrl = url,
                            providerType = ProviderType.DIRECT,
                            headers = headersMap
                        )
                    )
                }
            }

            if (options.isEmpty()) {
                val directUrl = json.optString("url", "")
                if (directUrl.isNotBlank()) {
                    options.add(
                        PlayableStreamOption(
                            qualityLabel = "yt-dlp Best Direct Stream",
                            format = "mp4",
                            isMuxed = true,
                            videoUrl = directUrl,
                            providerType = ProviderType.DIRECT,
                            headers = headersMap
                        )
                    )
                }
            }

            if (options.isEmpty()) {
                return@withContext YouTubeExtractorHelper.ExtractionResult.Error(
                    ExtractorErrorDetails(
                        errorType = ExtractorErrorType.NO_PLAYABLE_STREAMS,
                        message = "No streams found via yt-dlp",
                        rawExceptionName = "NoStreamsException",
                        fullStackTrace = "",
                        urlOrId = targetUrl,
                        causeInfo = "yt-dlp returned empty format array",
                        technicalFixSuggestion = "Check video URL or permissions."
                    )
                )
            }

            val bestOption = options.firstOrNull { it.qualityLabel.contains("1080p") } ?: options.first()
            val providerId = if (isYouTube) "youtube" else json.optString("extractor_key", "generic").lowercase()
            val streamData = StreamData(
                videoId = targetUrl,
                videoUrl = bestOption.videoUrl ?: "",
                title = title,
                channelName = uploader,
                description = description,
                thumbnailUrl = thumbnail,
                availableStreamOptions = options,
                selectedStreamOption = bestOption,
                providerId = providerId,
                providerType = ProviderType.DIRECT
            )
            Log.i(TAG, "yt-dlp success: extracted ${options.size} streams, selected: ${bestOption.qualityLabel}")
            YouTubeExtractorHelper.ExtractionResult.Success(streamData)
        } catch (e: Throwable) {
            Log.e(TAG, "yt-dlp execution failed: ${e.message}", e)
            YouTubeExtractorHelper.ExtractionResult.Error(
                ExtractorErrorDetails(
                    errorType = ExtractorErrorType.NETWORK_ERROR,
                    message = e.message ?: "yt-dlp failed",
                    rawExceptionName = e.javaClass.simpleName,
                    fullStackTrace = e.stackTraceToString(),
                    urlOrId = targetUrl,
                    causeInfo = e.message,
                    technicalFixSuggestion = "Verify connection or video status."
                )
            )
        }
    }
}
