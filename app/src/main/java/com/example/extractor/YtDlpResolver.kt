package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.ExtractorErrorType
import com.example.model.PlayableStreamOption
import com.example.model.StreamData
import com.example.plugin.sdk.model.ProviderType
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object YtDlpResolver {

    private const val TAG = "YtDlpResolver"

    @Volatile
    private var isInitialized = false

    fun init(context: Context) {
        if (!isInitialized) {
            synchronized(this) {
                if (!isInitialized) {
                    try {
                        YoutubeDL.getInstance().init(context.applicationContext)
                        try {
                            FFmpeg.getInstance().init(context.applicationContext)
                        } catch (fe: Throwable) {
                            Log.w(TAG, "FFmpeg init warning: ${fe.message}")
                        }
                        isInitialized = true
                        Log.d(TAG, "YoutubeDL initialized successfully")
                    } catch (e: Throwable) {
                        Log.e(TAG, "Failed to initialize YoutubeDL", e)
                    }
                }
            }
        }
    }

    fun isYouTubeUrl(url: String): Boolean {
        val u = url.lowercase().trim()
        return u.contains("youtube.com") || u.contains("youtu.be") || u.contains("youtube-nocookie.com")
    }

    fun isBilibiliUrl(url: String): Boolean {
        val u = url.lowercase().trim()
        return u.contains("bilibili.com") || u.contains("b23.tv") || u.contains("bilibili.tv") ||
                u.startsWith("bv") || u.startsWith("av") || u.contains("/bv") || u.contains("/av")
    }

    fun isYtDlpSupportedUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return false

        if (isYouTubeUrl(trimmed) || isBilibiliUrl(trimmed)) return true

        // Raw 11-char YouTube ID or BV id
        if (Regex("^[a-zA-Z0-9_-]{11}$").matches(trimmed)) return true
        if (Regex("^BV[a-zA-Z0-9]{10}$", RegexOption.IGNORE_CASE).matches(trimmed)) return true
        if (Regex("^av[0-9]+$", RegexOption.IGNORE_CASE).matches(trimmed)) return true

        // Check common video hosts supported by yt-dlp
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            val u = trimmed.lowercase()
            return u.contains("dailymotion.com") || u.contains("vimeo.com") ||
                    u.contains("tiktok.com") || u.contains("twitter.com") || u.contains("x.com") ||
                    u.contains("peer.tube") || u.contains("nicovideo.jp") || u.contains("twitch.tv")
        }

        return false
    }

    fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return input

        // Handle Bilibili BV/AV IDs directly
        if (Regex("^BV[a-zA-Z0-9]{10}$", RegexOption.IGNORE_CASE).matches(trimmed)) {
            return "https://www.bilibili.com/video/$trimmed"
        }
        if (Regex("^av[0-9]+$", RegexOption.IGNORE_CASE).matches(trimmed)) {
            return "https://www.bilibili.com/video/$trimmed"
        }

        // Handle YouTube 11-char ID
        if (Regex("^[a-zA-Z0-9_-]{11}$").matches(trimmed)) {
            return "https://www.youtube.com/watch?v=$trimmed"
        }

        // Standardize URLs
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            if (isBilibiliUrl(trimmed)) {
                return "https://www.bilibili.com/video/$trimmed"
            }
            if (isYouTubeUrl(trimmed)) {
                return "https://www.youtube.com/watch?v=$trimmed"
            }
            return "https://$trimmed"
        }

        return trimmed
    }

    sealed class ExtractionResult {
        data class Success(
            val streamData: StreamData,
            val playableOptions: List<PlayableStreamOption>
        ) : ExtractionResult()

        data class Error(
            val errorType: ExtractorErrorType,
            val message: String,
            val causeMessage: String? = null
        ) : ExtractionResult()
    }

    suspend fun extractStreamInfo(
        context: Context,
        urlOrId: String
    ): ExtractionResult = withContext(Dispatchers.IO) {
        init(context)

        val fullUrl = normalizeUrl(urlOrId)
        val isBilibili = isBilibiliUrl(fullUrl)
        val isYouTube = isYouTubeUrl(fullUrl)

        Log.d(TAG, "Starting yt-dlp extraction for: $fullUrl (bilibili=$isBilibili, youtube=$isYouTube)")

        try {
            val request = YoutubeDLRequest(fullUrl)

            // Prefer Android-compatible H.264 (avc1) + AAC (m4a/mp4a) or best progressive mp4
            request.addOption("-f", "bestvideo[vcodec^=avc1]+bestaudio[acodec^=mp4a]/bestvideo[vcodec^=h264]+bestaudio[acodec^=aac]/best[vcodec^=avc1]/best[ext=mp4]/best")
            request.addOption("--no-playlist")
            request.addOption("--socket-timeout", "15")

            if (isBilibili) {
                request.addOption("--add-header", "Referer:https://www.bilibili.com/")
                request.addOption("--add-header", "User-Agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            }

            val videoInfo: VideoInfo = YoutubeDL.getInstance().getInfo(request)

            val title = videoInfo.title ?: "Extracted Video"
            val uploader = videoInfo.uploader ?: if (isBilibili) "Bilibili Uploader" else if (isYouTube) "YouTube Channel" else "Web Creator"
            val duration = videoInfo.duration.toLong()
            val thumbnail = videoInfo.thumbnail

            // Extract HTTP headers
            val extractedHeaders = mutableMapOf<String, String>()
            videoInfo.httpHeaders?.forEach { (k, v) ->
                if (!v.isNullOrBlank()) {
                    extractedHeaders[k] = v
                }
            }

            if (isBilibili) {
                if (!extractedHeaders.containsKey("Referer") && !extractedHeaders.containsKey("referer")) {
                    extractedHeaders["Referer"] = "https://www.bilibili.com/"
                }
                if (!extractedHeaders.containsKey("User-Agent") && !extractedHeaders.containsKey("user-agent")) {
                    extractedHeaders["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                }
            }

            val options = mutableListOf<PlayableStreamOption>()

            // First check formats
            val formats = videoInfo.formats
            if (!formats.isNullOrEmpty()) {
                // Find muxed formats (video + audio)
                val muxedFormats = formats.filter {
                    !it.url.isNullOrEmpty() &&
                            (it.vcodec != "none" && it.acodec != "none")
                }

                for (fmt in muxedFormats) {
                    val fmtHeaders = extractedHeaders.toMutableMap()
                    fmt.httpHeaders?.forEach { (k, v) -> if (!v.isNullOrBlank()) fmtHeaders[k] = v }

                    val qualityLabel = when {
                        fmt.height > 0 -> "${fmt.height}p (${fmt.ext ?: "mp4"})"
                        !fmt.format.isNullOrBlank() -> fmt.format!!
                        else -> "Standard Quality"
                    }

                    options.add(
                        PlayableStreamOption(
                            qualityLabel = qualityLabel,
                            format = fmt.ext ?: "mp4",
                            isMuxed = true,
                            videoUrl = fmt.url,
                            audioUrl = null,
                            headers = fmtHeaders,
                            providerType = ProviderType.OTHER
                        )
                    )
                }

                // If no muxed format found, pair best video with best audio
                if (options.isEmpty()) {
                    val videoFmts = formats.filter { !it.url.isNullOrEmpty() && it.vcodec != "none" && it.vcodec != null }
                    val audioFmts = formats.filter { !it.url.isNullOrEmpty() && it.acodec != "none" && it.acodec != null }

                    val bestVideo = videoFmts.maxByOrNull { it.height } ?: videoFmts.firstOrNull()
                    val bestAudio = audioFmts.maxByOrNull { it.tbr } ?: audioFmts.firstOrNull()

                    if (bestVideo != null) {
                        val fmtHeaders = extractedHeaders.toMutableMap()
                        bestVideo.httpHeaders?.forEach { (k, v) -> if (!v.isNullOrBlank()) fmtHeaders[k] = v }

                        options.add(
                            PlayableStreamOption(
                                qualityLabel = "${bestVideo.height.takeIf { it > 0 }?.let { "${it}p" } ?: "Adaptive"} (yt-dlp)",
                                format = bestVideo.ext ?: "mp4",
                                isMuxed = bestAudio == null,
                                videoUrl = bestVideo.url,
                                audioUrl = bestAudio?.url,
                                headers = fmtHeaders,
                                providerType = ProviderType.OTHER
                            )
                        )
                    }
                }
            }

            // Fallback to videoInfo.url if options list is still empty
            if (options.isEmpty() && !videoInfo.url.isNullOrEmpty()) {
                options.add(
                    PlayableStreamOption(
                        qualityLabel = "Direct Stream (yt-dlp)",
                        format = "mp4",
                        isMuxed = true,
                        videoUrl = videoInfo.url,
                        audioUrl = null,
                        headers = extractedHeaders,
                        providerType = ProviderType.OTHER
                    )
                )
            }

            if (options.isEmpty()) {
                return@withContext ExtractionResult.Error(
                    errorType = ExtractorErrorType.NO_PLAYABLE_STREAMS,
                    message = "No playable video formats found for this URL",
                    causeMessage = "yt-dlp completed but did not return playable stream URLs"
                )
            }

            val selectedOpt = options.first()

            val streamData = StreamData(
                videoId = videoInfo.id ?: urlOrId,
                videoUrl = selectedOpt.videoUrl ?: "",
                title = title,
                channelName = uploader,
                viewCount = 0L,
                uploadDate = null,
                description = videoInfo.description,
                availableStreamOptions = options,
                selectedStreamOption = selectedOpt,
                thumbnailUrl = thumbnail,
                headers = extractedHeaders,
                providerId = if (isBilibili) "bilibili" else if (isYouTube) "youtube" else "ytdlp"
            )

            Log.d(TAG, "yt-dlp Extraction successful: title='$title', options=${options.size}, headers=${extractedHeaders.size}")

            ExtractionResult.Success(
                streamData = streamData,
                playableOptions = options
            )

        } catch (e: YoutubeDLException) {
            val msg = e.localizedMessage ?: "yt-dlp extraction failed"
            Log.e(TAG, "YoutubeDLException: $msg", e)

            val errorType = when {
                msg.contains("Geoblocking", ignoreCase = true) || msg.contains("region", ignoreCase = true) || msg.contains("not available in your country", ignoreCase = true) ->
                    ExtractorErrorType.GEO_RESTRICTED
                msg.contains("Private video", ignoreCase = true) || msg.contains("Unavailable", ignoreCase = true) || msg.contains("Video unavailable", ignoreCase = true) || msg.contains("404", ignoreCase = true) ->
                    ExtractorErrorType.UNAVAILABLE
                msg.contains("Sign in", ignoreCase = true) || msg.contains("Age-restricted", ignoreCase = true) ->
                    ExtractorErrorType.AGE_RESTRICTED
                msg.contains("Unsupported URL", ignoreCase = true) ->
                    ExtractorErrorType.UNAVAILABLE
                else -> ExtractorErrorType.NETWORK_ERROR
            }

            ExtractionResult.Error(
                errorType = errorType,
                message = when (errorType) {
                    ExtractorErrorType.GEO_RESTRICTED -> "This video is geo-restricted or unavailable in your region."
                    ExtractorErrorType.UNAVAILABLE -> "Video unavailable, private, or has been deleted."
                    ExtractorErrorType.AGE_RESTRICTED -> "This video is age-restricted or requires authentication."
                    else -> "Failed to extract stream: $msg"
                },
                causeMessage = msg
            )
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Unknown extraction error"
            Log.e(TAG, "Exception during yt-dlp extraction: $msg", e)
            ExtractionResult.Error(
                errorType = ExtractorErrorType.UNKNOWN,
                message = "Extraction failed: $msg",
                causeMessage = e.stackTraceToString()
            )
        }
    }
}
