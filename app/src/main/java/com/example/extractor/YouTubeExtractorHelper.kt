package com.example.extractor

import android.util.Log
import com.example.model.CaptionOption
import com.example.model.ExtractorErrorDetails
import com.example.model.ExtractorErrorType
import com.example.model.FeedErrorDetails
import com.example.model.FeedResult
import com.example.model.PlayableStreamOption
import com.example.model.StreamData
import com.example.model.VideoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Locale

object YouTubeExtractorHelper {

    @Volatile
    private var isInitialized = false

    interface CustomPoTokenProvider {
        fun getPoToken(visitorData: String?): String?
    }

    private var poTokenProvider: CustomPoTokenProvider? = null

    fun setPoTokenProvider(provider: CustomPoTokenProvider) {
        this.poTokenProvider = provider
    }

    fun getPoTokenProvider(): CustomPoTokenProvider? = poTokenProvider

    fun ensureInitialized() {
        if (!isInitialized) {
            synchronized(this) {
                if (!isInitialized) {
                    val localization = Localization.fromLocale(Locale.getDefault())
                    val contentCountry = ContentCountry(Locale.getDefault().country)
                    NewPipe.init(DownloaderImpl.getInstance(), localization, contentCountry)
                    isInitialized = true
                }
            }
        }
    }

    fun getYouTubeService(): StreamingService {
        ensureInitialized()
        return ServiceList.YouTube
    }

    private fun logDebug(tag: String, message: String) {
        try {
            Log.d(tag, message)
        } catch (e: Throwable) {
            println("[$tag] [DEBUG] $message")
        }
    }

    private fun logWarn(tag: String, message: String) {
        try {
            Log.w(tag, message)
        } catch (e: Throwable) {
            println("[$tag] [WARN] $message")
        }
    }

    private fun logError(tag: String, message: String, throwable: Throwable? = null) {
        try {
            if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
        } catch (e: Throwable) {
            println("[$tag] [ERROR] $message")
            throwable?.printStackTrace()
        }
    }

    sealed class UrlParseResult {
        data class ValidVideoId(val videoId: String) : UrlParseResult()
        data class InvalidUrl(val message: String) : UrlParseResult()
        object SearchQuery : UrlParseResult()
    }

    /**
     * Parses input string to determine if it is a valid YouTube video ID, a YouTube URL,
     * an invalid YouTube URL, or a search query.
     */
    fun parseYouTubeInput(input: String): UrlParseResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return UrlParseResult.SearchQuery

        val rawIdRegex = Regex("^[a-zA-Z0-9_-]{11}$")

        // Identifiers for URL attempts
        val isUrlAttempt = trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("www.", ignoreCase = true) ||
                trimmed.contains("youtube.com", ignoreCase = true) ||
                trimmed.contains("youtu.be", ignoreCase = true)

        if (isUrlAttempt) {
            // 1. youtube.com/watch?v=ID or m.youtube.com/watch?v=ID (parameter v can be anywhere in query)
            val watchRegex = Regex("""[?&]v=([a-zA-Z0-9_-]{11})(?:[&?]|\b)""", RegexOption.IGNORE_CASE)
            watchRegex.find(trimmed)?.groupValues?.get(1)?.let {
                logDebug("YouTubeExtractor", "[PARSER] Extracted video ID '$it' from watch URL: '$trimmed'")
                return UrlParseResult.ValidVideoId(it)
            }

            // 2. youtu.be/ID
            val shortUrlRegex = Regex("""youtu\.be\/([a-zA-Z0-9_-]{11})(?:[\/?&]|\b)""", RegexOption.IGNORE_CASE)
            shortUrlRegex.find(trimmed)?.groupValues?.get(1)?.let {
                logDebug("YouTubeExtractor", "[PARSER] Extracted video ID '$it' from youtu.be URL: '$trimmed'")
                return UrlParseResult.ValidVideoId(it)
            }

            // 3. youtube.com/shorts/ID
            val shortsRegex = Regex("""youtube\.com\/shorts\/([a-zA-Z0-9_-]{11})(?:[\/?&]|\b)""", RegexOption.IGNORE_CASE)
            shortsRegex.find(trimmed)?.groupValues?.get(1)?.let {
                logDebug("YouTubeExtractor", "[PARSER] Extracted video ID '$it' from shorts URL: '$trimmed'")
                return UrlParseResult.ValidVideoId(it)
            }

            // 4. youtube.com/live/ID
            val liveRegex = Regex("""youtube\.com\/live\/([a-zA-Z0-9_-]{11})(?:[\/?&]|\b)""", RegexOption.IGNORE_CASE)
            liveRegex.find(trimmed)?.groupValues?.get(1)?.let {
                logDebug("YouTubeExtractor", "[PARSER] Extracted video ID '$it' from live URL: '$trimmed'")
                return UrlParseResult.ValidVideoId(it)
            }

            // 5. Generic embed/v path: youtube.com/embed/ID or youtube.com/v/ID
            val embedRegex = Regex("""youtube\.com\/(?:embed|v)\/([a-zA-Z0-9_-]{11})(?:[\/?&]|\b)""", RegexOption.IGNORE_CASE)
            embedRegex.find(trimmed)?.groupValues?.get(1)?.let {
                logDebug("YouTubeExtractor", "[PARSER] Extracted video ID '$it' from embed/v URL: '$trimmed'")
                return UrlParseResult.ValidVideoId(it)
            }

            // URL attempt detected but no valid 11-char video ID found
            logWarn("YouTubeExtractor", "[PARSER] Invalid YouTube URL provided: '$trimmed'")
            return UrlParseResult.InvalidUrl("Invalid YouTube URL")
        }

        // Raw 11-character video ID check
        if (rawIdRegex.matches(trimmed)) {
            logDebug("YouTubeExtractor", "[PARSER] Input matches raw 11-char video ID: '$trimmed'")
            return UrlParseResult.ValidVideoId(trimmed)
        }

        // Plain search query
        return UrlParseResult.SearchQuery
    }

    sealed class ExtractionResult {
        data class Success(val streamData: StreamData) : ExtractionResult()
        data class Error(val errorDetails: ExtractorErrorDetails) : ExtractionResult()
    }

    fun fetchStreamData(urlOrId: String): ExtractionResult {
        ensureInitialized()
        val service = getYouTubeService()

        val videoId = when (val parsed = parseYouTubeInput(urlOrId)) {
            is UrlParseResult.ValidVideoId -> parsed.videoId
            is UrlParseResult.InvalidUrl -> {
                return ExtractionResult.Error(
                    ExtractorErrorDetails(
                        errorType = ExtractorErrorType.UNAVAILABLE,
                        message = "Invalid YouTube URL",
                        rawExceptionName = "IllegalArgumentException",
                        fullStackTrace = "Unable to extract a valid 11-character video ID from input: '$urlOrId'",
                        urlOrId = urlOrId,
                        technicalFixSuggestion = "Check the YouTube URL format and try again."
                    )
                )
            }
            is UrlParseResult.SearchQuery -> urlOrId.trim()
        }

        val fullUrl = "https://www.youtube.com/watch?v=$videoId"

        logDebug("YouTubeExtractor", "[TRACE] BEFORE StreamInfo.getInfo for videoId: '$videoId', fullUrl: '$fullUrl'")
        println("=== [TRACE] BEFORE StreamInfo.getInfo ===")
        println(" - Extracted video ID: $videoId")
        println(" - Target URL: $fullUrl")

        return try {
            val info = StreamInfo.getInfo(service, fullUrl)

            val progressiveStreams = info.videoStreams ?: emptyList()
            val videoOnlyStreams = info.videoOnlyStreams ?: emptyList()
            val audioStreams = info.audioStreams ?: emptyList()
            val totalStreams = progressiveStreams.size + videoOnlyStreams.size + audioStreams.size

            logDebug(
                "YouTubeExtractor",
                "[TRACE] AFTER StreamInfo.getInfo SUCCESS! videoId: '$videoId', Title: '${info.name}', Uploader: '${info.uploaderName}', Streams: $totalStreams"
            )
            println("=== [TRACE] AFTER StreamInfo.getInfo SUCCESS ===")
            println(" - Extracted video ID: $videoId")
            println(" - StreamInfo title: ${info.name}")
            println(" - Uploader: ${info.uploaderName}")
            println(" - Stream count: $totalStreams")

            val bestAudio = audioStreams.maxByOrNull { it.averageBitrate }

            val options = mutableListOf<PlayableStreamOption>()

            // 1. Muxed progressive streams (Video + Audio)
            for (vs in progressiveStreams) {
                options.add(
                    PlayableStreamOption(
                        qualityLabel = "${vs.resolution ?: "Video"} (Progressive)",
                        format = vs.format?.name ?: "MP4",
                        isMuxed = true,
                        videoStream = vs,
                        audioStream = null
                    )
                )
            }

            // 2. High quality video-only streams combined with best audio stream
            if (bestAudio != null) {
                for (vo in videoOnlyStreams) {
                    options.add(
                        PlayableStreamOption(
                            qualityLabel = "${vo.resolution ?: "HD"} (Adaptive)",
                            format = "${vo.format?.name ?: "MP4"} + ${bestAudio.format?.name ?: "M4A"}",
                            isMuxed = false,
                            videoStream = vo,
                            audioStream = bestAudio
                        )
                    )
                }
            }

            if (options.isEmpty() && info.hlsUrl.isNullOrEmpty()) {
                return ExtractionResult.Error(
                    ExtractorErrorDetails(
                        errorType = ExtractorErrorType.NO_PLAYABLE_STREAMS,
                        message = "No non-DRM playable video or audio streams were found for this video.",
                        rawExceptionName = "NoPlayableStreamsException",
                        fullStackTrace = "videoStreams: ${progressiveStreams.size}, videoOnlyStreams: ${videoOnlyStreams.size}, audioStreams: ${audioStreams.size}",
                        urlOrId = fullUrl,
                        technicalFixSuggestion = "YouTube returned no direct streams for this video URL."
                    )
                )
            }

            val defaultSelectedOption = options.firstOrNull { it.qualityLabel.contains("720p") }
                ?: options.firstOrNull { it.isMuxed }
                ?: options.firstOrNull()

            val captions = (info.subtitles ?: emptyList()).map { sub ->
                CaptionOption(
                    languageName = sub.displayLanguageName ?: sub.languageTag ?: "Unknown",
                    languageCode = sub.languageTag ?: "en",
                    format = sub.format?.name ?: "VTT",
                    url = sub.content
                )
            }

            val related = (info.relatedItems ?: emptyList()).filterIsInstance<StreamInfoItem>().map { item ->
                VideoItem(
                    id = item.url.substringAfter("v=").substringBefore("&"),
                    title = item.name ?: "",
                    uploaderName = item.uploaderName ?: "",
                    uploaderUrl = item.uploaderUrl,
                    uploaderAvatarUrl = item.uploaderAvatars?.firstOrNull()?.url,
                    viewCount = item.viewCount,
                    durationSeconds = item.duration,
                    uploadDate = item.uploadDate?.offsetDateTime()?.toString(),
                    thumbnailUrl = item.thumbnails?.firstOrNull()?.url
                )
            }

            val avatarUrl = info.uploaderAvatars?.firstOrNull()?.url

            val streamData = StreamData(
                videoId = info.id ?: urlOrId,
                videoUrl = fullUrl,
                title = info.name ?: "Untitled Video",
                channelName = info.uploaderName ?: "Unknown Channel",
                channelAvatarUrl = avatarUrl,
                subscriberCountText = null,
                viewCount = info.viewCount,
                likeCount = info.likeCount,
                uploadDate = info.uploadDate?.offsetDateTime()?.toLocalDate()?.toString(),
                description = info.description?.content,
                progressiveStreams = progressiveStreams,
                videoOnlyStreams = videoOnlyStreams,
                audioStreams = audioStreams,
                captionOptions = captions,
                availableStreamOptions = options,
                selectedStreamOption = defaultSelectedOption,
                hlsUrl = info.hlsUrl,
                relatedVideos = related
            )

            ExtractionResult.Success(streamData)

        } catch (e: Exception) {
            logError("YouTubeExtractor", "Exception in fetchStreamData for $fullUrl", e)
            e.printStackTrace()

            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val stackTraceStr = sw.toString()
            val msg = e.message ?: "No exception message available (${e.javaClass.canonicalName ?: e.javaClass.name})"
            val causeStr = e.cause?.let { "${it.javaClass.name}: ${it.message}" }

            val errorType = when {
                e is ReCaptchaException -> ExtractorErrorType.RECAPTCHA_REQUIRED
                e is GeographicRestrictionException -> ExtractorErrorType.GEO_RESTRICTED
                e is ContentNotAvailableException -> ExtractorErrorType.UNAVAILABLE
                e is IOException -> ExtractorErrorType.NETWORK_ERROR
                msg.contains("PoToken", ignoreCase = true) -> ExtractorErrorType.PO_TOKEN_REQUIRED
                msg.contains("SABR", ignoreCase = true) -> ExtractorErrorType.SABR_PROTECTION
                msg.contains("Signature", ignoreCase = true) || msg.contains("n-parameter", ignoreCase = true) -> ExtractorErrorType.SIGNATURE_DECRYPTION_FAILED
                msg.contains("age", ignoreCase = true) -> ExtractorErrorType.AGE_RESTRICTED
                else -> ExtractorErrorType.UNKNOWN
            }

            val suggestion = when (errorType) {
                ExtractorErrorType.PO_TOKEN_REQUIRED -> "YouTube exception explicitly references PoToken."
                ExtractorErrorType.RECAPTCHA_REQUIRED -> "YouTube flagged requests with reCAPTCHA."
                ExtractorErrorType.AGE_RESTRICTED -> "This video is age-restricted on YouTube."
                ExtractorErrorType.GEO_RESTRICTED -> "This video is restricted in your geographic location."
                ExtractorErrorType.NETWORK_ERROR -> "Network IO error."
                ExtractorErrorType.UNAVAILABLE -> "This video is unavailable."
                else -> "Exact Exception: ${e.javaClass.canonicalName ?: e.javaClass.name}"
            }

            ExtractionResult.Error(
                ExtractorErrorDetails(
                    errorType = errorType,
                    message = msg,
                    rawExceptionName = e.javaClass.canonicalName ?: e.javaClass.name,
                    fullStackTrace = stackTraceStr,
                    urlOrId = fullUrl,
                    causeInfo = causeStr,
                    technicalFixSuggestion = suggestion
                )
            )
        }
    }

    fun searchVideos(query: String): FeedResult {
        ensureInitialized()
        return try {
            val service = getYouTubeService()
            val searchExtractor = service.getSearchExtractor(
                query,
                listOf(YoutubeSearchQueryHandlerFactory.VIDEOS),
                ""
            )
            val searchInfo = SearchInfo.getInfo(searchExtractor)
            val items = searchInfo.relatedItems.filterIsInstance<StreamInfoItem>().map { item ->
                VideoItem(
                    id = item.url.substringAfter("v=").substringBefore("&"),
                    title = item.name ?: "",
                    uploaderName = item.uploaderName ?: "",
                    uploaderUrl = item.uploaderUrl,
                    uploaderAvatarUrl = item.uploaderAvatars?.firstOrNull()?.url,
                    viewCount = item.viewCount,
                    durationSeconds = item.duration,
                    uploadDate = item.uploadDate?.offsetDateTime()?.toString(),
                    thumbnailUrl = item.thumbnails?.firstOrNull()?.url
                )
            }
            FeedResult.Success(items)
        } catch (e: Exception) {
            logError("YouTubeExtractor", "Exception in searchVideos for query '$query'", e)
            e.printStackTrace()

            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val stackTraceStr = sw.toString()
            val msg = e.message ?: "No error message provided (${e.javaClass.canonicalName ?: e.javaClass.name})"
            val causeStr = e.cause?.let { "${it.javaClass.name}: ${it.message}" }

            FeedResult.Error(
                FeedErrorDetails(
                    rawExceptionName = e.javaClass.canonicalName ?: e.javaClass.name,
                    message = msg,
                    fullStackTrace = stackTraceStr,
                    causeInfo = causeStr,
                    urlOrQuery = query
                )
            )
        }
    }

    fun fetchTrendingVideos(): FeedResult {
        ensureInitialized()
        return try {
            searchVideos("trending videos")
        } catch (e: Exception) {
            logError("YouTubeExtractor", "Exception in fetchTrendingVideos", e)
            e.printStackTrace()

            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val stackTraceStr = sw.toString()
            val msg = e.message ?: "No error message provided (${e.javaClass.canonicalName ?: e.javaClass.name})"
            val causeStr = e.cause?.let { "${it.javaClass.name}: ${it.message}" }

            FeedResult.Error(
                FeedErrorDetails(
                    rawExceptionName = e.javaClass.canonicalName ?: e.javaClass.name,
                    message = msg,
                    fullStackTrace = stackTraceStr,
                    causeInfo = causeStr,
                    urlOrQuery = "Trending Videos Feed"
                )
            )
        }
    }
}
