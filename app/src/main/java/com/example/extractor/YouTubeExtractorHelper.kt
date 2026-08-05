package com.example.extractor

import com.example.model.CaptionOption
import com.example.model.ExtractorErrorDetails
import com.example.model.ExtractorErrorType
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

    sealed class ExtractionResult {
        data class Success(val streamData: StreamData) : ExtractionResult()
        data class Error(val errorDetails: ExtractorErrorDetails) : ExtractionResult()
    }

    fun fetchStreamData(urlOrId: String): ExtractionResult {
        ensureInitialized()
        val service = getYouTubeService()
        val fullUrl = if (urlOrId.startsWith("http://") || urlOrId.startsWith("https://")) {
            urlOrId
        } else {
            "https://www.youtube.com/watch?v=$urlOrId"
        }

        return try {
            val info = StreamInfo.getInfo(service, fullUrl)

            val progressiveStreams = info.videoStreams ?: emptyList()
            val videoOnlyStreams = info.videoOnlyStreams ?: emptyList()
            val audioStreams = info.audioStreams ?: emptyList()

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
                        technicalFixSuggestion = "YouTube may have restricted stream formats for this video or enabled PoToken/SABR protection."
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
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val stackTraceStr = sw.toString()
            val msg = e.message ?: "Unknown error during stream extraction"

            val errorType = when {
                e is ReCaptchaException -> ExtractorErrorType.RECAPTCHA_REQUIRED
                e is GeographicRestrictionException -> ExtractorErrorType.GEO_RESTRICTED
                e is ContentNotAvailableException -> ExtractorErrorType.UNAVAILABLE
                e is IOException -> ExtractorErrorType.NETWORK_ERROR
                msg.contains("PoToken", ignoreCase = true) || msg.contains("bot", ignoreCase = true) -> ExtractorErrorType.PO_TOKEN_REQUIRED
                msg.contains("SABR", ignoreCase = true) -> ExtractorErrorType.SABR_PROTECTION
                msg.contains("Signature", ignoreCase = true) || msg.contains("n-parameter", ignoreCase = true) -> ExtractorErrorType.SIGNATURE_DECRYPTION_FAILED
                msg.contains("age", ignoreCase = true) -> ExtractorErrorType.AGE_RESTRICTED
                else -> ExtractorErrorType.UNKNOWN
            }

            val suggestion = when (errorType) {
                ExtractorErrorType.PO_TOKEN_REQUIRED -> "YouTube now mandates a Proof of Origin (PoToken) for this video stream. Integrate a PoToken generator to provide visitor tokens."
                ExtractorErrorType.SABR_PROTECTION -> "YouTube SABR stream protection was triggered. Updated NewPipeExtractor rules are required."
                ExtractorErrorType.SIGNATURE_DECRYPTION_FAILED -> "YouTube updated its JavaScript player signature cipher algorithm. NewPipeExtractor rules need updating."
                ExtractorErrorType.RECAPTCHA_REQUIRED -> "YouTube flagged requests with reCAPTCHA. Retry later or resolve anti-bot challenge."
                ExtractorErrorType.AGE_RESTRICTED -> "This video is age-restricted on YouTube and requires user authentication or age bypass."
                ExtractorErrorType.GEO_RESTRICTED -> "This video is restricted in your current geographic location."
                ExtractorErrorType.NETWORK_ERROR -> "Network request failed. Check your internet connection."
                ExtractorErrorType.NO_PLAYABLE_STREAMS -> "No direct non-DRM streams were extracted."
                ExtractorErrorType.UNAVAILABLE -> "This video has been removed or set to private on YouTube."
                ExtractorErrorType.UNKNOWN -> "Extraction failed. Server-side YouTube changes or parser incompatibility."
            }

            ExtractionResult.Error(
                ExtractorErrorDetails(
                    errorType = errorType,
                    message = msg,
                    rawExceptionName = e.javaClass.simpleName,
                    fullStackTrace = stackTraceStr,
                    urlOrId = fullUrl,
                    technicalFixSuggestion = suggestion
                )
            )
        }
    }

    fun searchVideos(query: String): List<VideoItem> {
        ensureInitialized()
        return try {
            val service = getYouTubeService()
            val searchExtractor = service.getSearchExtractor(
                query,
                listOf(YoutubeSearchQueryHandlerFactory.VIDEOS),
                ""
            )
            val searchInfo = SearchInfo.getInfo(searchExtractor)
            searchInfo.relatedItems.filterIsInstance<StreamInfoItem>().map { item ->
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
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun fetchTrendingVideos(): List<VideoItem> {
        ensureInitialized()
        return try {
            val service = getYouTubeService()
            val kioskExtractor = service.kioskList.defaultKioskExtractor
            val kioskInfo = KioskInfo.getInfo(kioskExtractor)
            kioskInfo.relatedItems.filterIsInstance<StreamInfoItem>().map { item ->
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
        } catch (e: Exception) {
            emptyList()
        }
    }
}
