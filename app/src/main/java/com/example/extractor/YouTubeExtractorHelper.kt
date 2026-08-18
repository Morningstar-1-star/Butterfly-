package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

sealed class UrlParseResult {
    data class VideoId(val id: String) : UrlParseResult()
    data class ChannelId(val id: String) : UrlParseResult()
    data class PlaylistId(val id: String) : UrlParseResult()
    data class ShortId(val id: String) : UrlParseResult()
    object SearchQuery : UrlParseResult()
    data class Unknown(val url: String) : UrlParseResult()
    data class ParsedSearchResults(val items: List<VideoItem>) : UrlParseResult()
}

object YouTubeExtractorHelper {
    private const val TAG = "YouTubeExtractorHelper"

    interface CustomPoTokenProvider {
        fun getPoToken(visitorData: String?): String?
    }

    private var customPoTokenProvider: CustomPoTokenProvider? = null

    fun setPoTokenProvider(provider: CustomPoTokenProvider?) {
        customPoTokenProvider = provider
    }

    sealed class ExtractionResult {
        data class Success(val streamData: StreamData) : ExtractionResult()
        data class Error(val errorDetails: ExtractorErrorDetails) : ExtractionResult()
    }

    init {
        try {
            NewPipe.init(DownloaderImpl.getInstance())
            Log.i(TAG, "NewPipe initialized successfully")
        } catch (e: Exception) {
            Log.w(TAG, "NewPipe initialization note: ${e.message}")
        }
    }

    suspend fun fetchYouTubeTrending(): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            try { NewPipe.init(DownloaderImpl.getInstance()) } catch (ignored: Exception) {}
            val kioskInfo = org.schabi.newpipe.extractor.kiosk.KioskInfo.getInfo(
                ServiceList.YouTube,
                "Trending"
            )
            val items = kioskInfo.relatedItems?.filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
                ?.map { item ->
                    val vId = item.url.substringAfter("v=").substringBefore("&")
                    val thumb = item.thumbnails?.firstOrNull()?.url ?: "https://i.ytimg.com/vi/$vId/hqdefault.jpg"
                    VideoItem(
                        id = vId,
                        title = item.name ?: "YouTube Video",
                        uploaderName = item.uploaderName ?: "YouTube",
                        viewCount = item.viewCount,
                        durationSeconds = item.duration,
                        thumbnailUrl = thumb,
                        providerId = "youtube"
                    )
                } ?: emptyList()
            if (items.isNotEmpty()) {
                Log.i(TAG, "Fetched ${items.size} trending videos via NewPipe Kiosk")
                return@withContext items
            }
        } catch (e: Exception) {
            Log.w(TAG, "NewPipe trending fetch failed: ${e.message}")
        }

        emptyList()
    }

    suspend fun searchYouTube(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        try {
            try { NewPipe.init(DownloaderImpl.getInstance()) } catch (ignored: Exception) {}
            val searchExtractor = ServiceList.YouTube.getSearchExtractor(query)
            searchExtractor.fetchPage()
            val items = searchExtractor.initialPage?.items?.filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
                ?.map { item ->
                    val vId = item.url.substringAfter("v=").substringBefore("&")
                    val thumb = item.thumbnails?.firstOrNull()?.url ?: "https://i.ytimg.com/vi/$vId/hqdefault.jpg"
                    VideoItem(
                        id = vId,
                        title = item.name ?: "YouTube Video",
                        uploaderName = item.uploaderName ?: "YouTube",
                        viewCount = item.viewCount,
                        durationSeconds = item.duration,
                        thumbnailUrl = thumb,
                        providerId = "youtube"
                    )
                } ?: emptyList()
            if (items.isNotEmpty()) {
                Log.i(TAG, "Fetched ${items.size} search results for '$query' via NewPipe")
                return@withContext items
            }
        } catch (e: Exception) {
            Log.w(TAG, "NewPipe search failed: ${e.message}")
        }

        emptyList()
    }

    suspend fun resolveStream(urlOrId: String, context: Context? = null): ExtractionResult = withContext(Dispatchers.IO) {
        val isArchive = urlOrId.contains("archive.org") || urlOrId.startsWith("archive_")
        if (isArchive) {
            val archiveData = ArchiveOrgProvider.getStreamData(urlOrId)
            if (archiveData != null) {
                Log.i(TAG, "Resolved via ArchiveOrgProvider")
                return@withContext ExtractionResult.Success(archiveData)
            } else {
                return@withContext ExtractionResult.Error(
                    ExtractorErrorDetails(
                        errorType = ExtractorErrorType.NO_PLAYABLE_STREAMS,
                        message = "Archive.org video could not be loaded",
                        rawExceptionName = "ArchiveExtractionException",
                        fullStackTrace = "",
                        urlOrId = urlOrId
                    )
                )
            }
        }

        val isYouTube = urlOrId.contains("youtube.com") || urlOrId.contains("youtu.be") || (urlOrId.length == 11 && !urlOrId.startsWith("http"))

        if (isYouTube) {
            val videoId = when {
                urlOrId.contains("v=") -> urlOrId.substringAfter("v=").substringBefore("&")
                urlOrId.contains("youtu.be/") -> urlOrId.substringAfter("youtu.be/").substringBefore("?")
                else -> urlOrId
            }

            val targetUrl = if (urlOrId.startsWith("http")) urlOrId else "https://www.youtube.com/watch?v=$videoId"
            Log.i(TAG, "Resolving YouTube Video ID: '$videoId', Target URL: '$targetUrl'")

            // Step 1: NewPipe Extractor
            Log.i(TAG, "YouTube Resolution Step 1 (NewPipe Extractor): $targetUrl")
            try {
                try {
                    NewPipe.init(DownloaderImpl.getInstance())
                } catch (ignored: Exception) {}

                val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, targetUrl)
                val title = streamInfo.name ?: "YouTube Video"
                val uploader = streamInfo.uploaderName ?: "YouTube"
                val desc = streamInfo.description?.getContent() ?: ""
                val thumb = streamInfo.thumbnails?.firstOrNull()?.url ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                val options = mutableListOf<PlayableStreamOption>()

                streamInfo.videoStreams?.forEach { vStream ->
                    val vUrl = vStream.content
                    val resolution = vStream.resolution ?: "720p"
                    val formatName = vStream.format?.name ?: "mp4"
                    options.add(
                        PlayableStreamOption(
                            qualityLabel = "NewPipe Video $resolution ($formatName)",
                            format = formatName,
                            isMuxed = true,
                            videoStream = vStream,
                            videoUrl = vUrl,
                            providerType = ProviderType.DIRECT,
                            headers = mapOf("Referer" to "https://www.youtube.com/")
                        )
                    )
                }

                if (options.isEmpty() && !streamInfo.hlsUrl.isNullOrBlank()) {
                    options.add(
                        PlayableStreamOption(
                            qualityLabel = "NewPipe HLS Stream",
                            format = "m3u8",
                            isMuxed = true,
                            videoUrl = streamInfo.hlsUrl,
                            providerType = ProviderType.DIRECT,
                            headers = mapOf("Referer" to "https://www.youtube.com/")
                        )
                    )
                }

                if (options.isNotEmpty()) {
                    val bestOption = options.firstOrNull { it.qualityLabel.contains("1080p") } ?: options.first()
                    val streamData = StreamData(
                        videoId = videoId,
                        videoUrl = bestOption.videoUrl ?: "",
                        title = title,
                        channelName = uploader,
                        description = desc,
                        thumbnailUrl = thumb,
                        availableStreamOptions = options,
                        selectedStreamOption = bestOption,
                        hlsUrl = streamInfo.hlsUrl,
                        providerId = "youtube",
                        providerType = ProviderType.DIRECT
                    )
                    Log.i(TAG, "NewPipe extraction success: ${options.size} formats available.")
                    return@withContext ExtractionResult.Success(streamData)
                }
            } catch (e: Exception) {
                Log.w(TAG, "NewPipe extraction failed: ${e.message}")
            }

            // Step 2: Fallback to yt-dlp for YouTube
            if (context != null) {
                Log.i(TAG, "YouTube Resolution Step 2 (yt-dlp fallback): $targetUrl")
                val ytDlpResult = YtDlpResolver.extractStreamInfo(context, targetUrl)
                if (ytDlpResult is ExtractionResult.Success) {
                    return@withContext ytDlpResult
                }
            }

            return@withContext ExtractionResult.Error(
                ExtractorErrorDetails(
                    errorType = ExtractorErrorType.NO_PLAYABLE_STREAMS,
                    message = "Unable to resolve playable stream for this YouTube video.",
                    rawExceptionName = "YouTubeExtractionFailedException",
                    fullStackTrace = "",
                    urlOrId = targetUrl,
                    causeInfo = "NewPipe Extractor and yt-dlp fallback were both attempted.",
                    technicalFixSuggestion = "Check internet connection or retry later."
                )
            )
        } else {
            // Generic non-YouTube URL: direct yt-dlp resolution
            if (context != null) {
                Log.i(TAG, "Non-YouTube URL, resolving via yt-dlp: $urlOrId")
                return@withContext YtDlpResolver.extractStreamInfo(context, urlOrId)
            } else {
                return@withContext ExtractionResult.Error(
                    ExtractorErrorDetails(
                        errorType = ExtractorErrorType.NO_PLAYABLE_STREAMS,
                        message = "Context required for generic stream resolution",
                        rawExceptionName = "NoContextException",
                        fullStackTrace = "",
                        urlOrId = urlOrId
                    )
                )
            }
        }
    }

    suspend fun fetchStreamData(urlOrId: String, context: Context? = null): ExtractionResult {
        return resolveStream(urlOrId, context)
    }

    suspend fun searchVideos(query: String): UrlParseResult = withContext(Dispatchers.IO) {
        try {
            val ytItems = searchYouTube(query)
            val archiveItems = ArchiveOrgProvider.search(query, 1)
            val combined = (ytItems + archiveItems).distinctBy { it.id }
            UrlParseResult.ParsedSearchResults(combined)
        } catch (e: Exception) {
            UrlParseResult.ParsedSearchResults(emptyList())
        }
    }
}
