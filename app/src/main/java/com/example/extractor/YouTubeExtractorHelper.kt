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

    suspend fun fetchYouTubeTrending(context: Context? = null): List<VideoItem> = withContext(Dispatchers.IO) {
        // Step 1: NewPipe Trending Kiosk
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

        // Step 2: yt-dlp Trending Fallback
        if (context != null) {
            Log.i(TAG, "Attempting yt-dlp trending fallback")
            val ytdlItems = YtDlpResolver.fetchTrending(context)
            if (ytdlItems.isNotEmpty()) {
                Log.i(TAG, "Fetched ${ytdlItems.size} trending videos via yt-dlp fallback")
                return@withContext ytdlItems
            }
        }

        emptyList()
    }

    suspend fun searchYouTube(query: String, context: Context? = null): List<VideoItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        // Step 1: NewPipe Search
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

        // Step 2: yt-dlp Search Fallback
        if (context != null) {
            Log.i(TAG, "Attempting yt-dlp search fallback for query: '$query'")
            val ytdlItems = YtDlpResolver.search(context, query)
            if (ytdlItems.isNotEmpty()) {
                Log.i(TAG, "Fetched ${ytdlItems.size} search results for '$query' via yt-dlp")
                return@withContext ytdlItems
            }
        }

        emptyList()
    }

    suspend fun resolveStream(urlOrId: String, context: Context? = null, providerId: String? = null): ExtractionResult = withContext(Dispatchers.IO) {
        val isArchive = providerId == "archive_org" || providerId == "archive" || urlOrId.contains("archive.org") || urlOrId.startsWith("archive_") || urlOrId.startsWith("archive:")
        if (isArchive) {
            val archiveData = ArchiveOrgProvider.getStreamData(urlOrId)
            if (archiveData != null) {
                Log.i(TAG, "Resolved via ArchiveOrgProvider for $urlOrId")
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

        val isEporner = providerId == "eporner" || urlOrId.contains("eporner.com")
        if (isEporner) {
            val epornerData = EpornerProvider.getStreamData(urlOrId)
            if (epornerData != null) {
                Log.i(TAG, "Resolved via EpornerProvider for $urlOrId")
                return@withContext ExtractionResult.Success(epornerData)
            } else if (context != null) {
                Log.i(TAG, "EpornerProvider direct scraping failed, falling back to YtDlpResolver for $urlOrId")
                return@withContext YtDlpResolver.extractStreamInfo(context, urlOrId)
            }
        }

        val isYouTube = providerId == "youtube" || urlOrId.contains("youtube.com") || urlOrId.contains("youtu.be") || (urlOrId.length == 11 && !urlOrId.startsWith("http"))

        if (isYouTube) {
            val videoId = when {
                urlOrId.contains("v=") -> urlOrId.substringAfter("v=").substringBefore("&")
                urlOrId.contains("youtu.be/") -> urlOrId.substringAfter("youtu.be/").substringBefore("?")
                else -> urlOrId
            }

            val targetUrl = if (urlOrId.startsWith("http")) urlOrId else "https://www.youtube.com/watch?v=$videoId"
            com.example.util.PlaybackPipelineTracker.logExtractionStart(videoId, targetUrl)
            Log.i(TAG, "Resolving YouTube Video ID: '$videoId', Target URL: '$targetUrl'")

            // Step 1: NewPipe Extractor (Primary)
            try {
                try {
                    NewPipe.init(DownloaderImpl.getInstance())
                } catch (ignored: Exception) {}

                val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, targetUrl)
                val title = streamInfo.name ?: "YouTube Video"
                val uploader = streamInfo.uploaderName ?: "YouTube"
                val desc = streamInfo.description?.getContent() ?: ""
                val thumb = streamInfo.thumbnails?.firstOrNull()?.url ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                val progressiveStreams = streamInfo.videoStreams ?: emptyList()
                val videoOnlyStreams = streamInfo.videoOnlyStreams ?: emptyList()
                val audioStreams = streamInfo.audioStreams ?: emptyList()

                com.example.util.PlaybackPipelineTracker.logNewpipeResult(
                    progressiveCount = progressiveStreams.size,
                    adaptiveCount = videoOnlyStreams.size
                )

                val ytHeaders = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                )

                val bestAudioStream = audioStreams.maxByOrNull { it.averageBitrate }
                val options = mutableListOf<PlayableStreamOption>()

                // 1. Progressive streams (Video + Audio muxed - H.264/MP4 preferred)
                progressiveStreams.forEach { vStream ->
                    val vUrl = vStream.content
                    val resolution = vStream.resolution ?: "720p"
                    val formatName = vStream.format?.name ?: "mp4"
                    if (!vUrl.isNullOrBlank()) {
                        options.add(
                            PlayableStreamOption(
                                qualityLabel = "$resolution Progressive ($formatName)",
                                format = formatName,
                                isMuxed = true,
                                videoStream = vStream,
                                videoUrl = vUrl,
                                providerType = ProviderType.DIRECT,
                                headers = ytHeaders
                            )
                        )
                    }
                }

                // 2. Adaptive Video Streams (Paired with best audio stream - fallback only)
                videoOnlyStreams.forEach { vStream ->
                    val vUrl = vStream.content
                    val resolution = vStream.resolution ?: "1080p"
                    val formatName = vStream.format?.name ?: "mp4"
                    if (!vUrl.isNullOrBlank()) {
                        options.add(
                            PlayableStreamOption(
                                qualityLabel = "$resolution Adaptive ($formatName)",
                                format = formatName,
                                isMuxed = false,
                                videoStream = vStream,
                                audioStream = bestAudioStream,
                                videoUrl = vUrl,
                                audioUrl = bestAudioStream?.content,
                                providerType = ProviderType.DIRECT,
                                headers = ytHeaders
                            )
                        )
                    }
                }

                // 3. HLS Master Playlist
                if (!streamInfo.hlsUrl.isNullOrBlank()) {
                    options.add(
                        PlayableStreamOption(
                            qualityLabel = "Adaptive HLS (m3u8)",
                            format = "m3u8",
                            isMuxed = true,
                            videoUrl = streamInfo.hlsUrl,
                            providerType = ProviderType.DIRECT,
                            headers = ytHeaders
                        )
                    )
                }

                if (options.isNotEmpty()) {
                    // Sort options: prefer progressive muxed streams, then high resolution
                    val sortedOptions = options.sortedWith(
                        compareByDescending<PlayableStreamOption> { parseQualityScore(it) }
                    ).distinctBy { it.qualityLabel }

                    // Priority: First prefer muxed MP4 progressive (720p, then 480p, then 360p, then any mp4 muxed)
                    val muxedMp4Streams = sortedOptions.filter { it.isMuxed && it.format.equals("mp4", ignoreCase = true) && !it.videoUrl.isNullOrBlank() }
                    val bestOption = muxedMp4Streams.firstOrNull { it.qualityLabel.startsWith("720p") }
                        ?: muxedMp4Streams.firstOrNull { it.qualityLabel.startsWith("1080p") }
                        ?: muxedMp4Streams.firstOrNull { it.qualityLabel.startsWith("480p") }
                        ?: muxedMp4Streams.firstOrNull { it.qualityLabel.startsWith("360p") }
                        ?: muxedMp4Streams.firstOrNull()
                        ?: sortedOptions.firstOrNull { it.isMuxed && !it.videoUrl.isNullOrBlank() }
                        ?: sortedOptions.first()

                    com.example.util.PlaybackPipelineTracker.logFormatSelected(
                        label = bestOption.qualityLabel,
                        isMuxed = bestOption.isMuxed,
                        format = bestOption.format,
                        urlSnippet = bestOption.videoUrl?.take(60) ?: "unknown"
                    )

                    val streamData = StreamData(
                        videoId = videoId,
                        videoUrl = bestOption.videoUrl ?: "",
                        title = title,
                        channelName = uploader,
                        description = desc,
                        thumbnailUrl = thumb,
                        progressiveStreams = progressiveStreams,
                        videoOnlyStreams = videoOnlyStreams,
                        audioStreams = audioStreams,
                        availableStreamOptions = sortedOptions,
                        selectedStreamOption = bestOption,
                        hlsUrl = streamInfo.hlsUrl,
                        providerId = "youtube",
                        providerType = ProviderType.DIRECT,
                        headers = ytHeaders
                    )
                    Log.i(TAG, "NewPipe extraction success: ${sortedOptions.size} formats available. Selected: ${bestOption.qualityLabel}")
                    return@withContext ExtractionResult.Success(streamData)
                }
            } catch (e: Exception) {
                Log.w(TAG, "NewPipe extraction failed: ${e.message}")
            }

            // Step 2: yt-dlp Fallback for YouTube ONLY if NewPipe failed
            if (context != null) {
                Log.i(TAG, "YouTube Resolution Step 2 (yt-dlp fallback): $targetUrl")
                val ytDlpResult = YtDlpResolver.extractStreamInfo(context, targetUrl)
                if (ytDlpResult is ExtractionResult.Success) {
                    val primary = ytDlpResult.streamData.selectedStreamOption
                        ?: ytDlpResult.streamData.availableStreamOptions.firstOrNull()
                    if (primary != null) {
                        com.example.util.PlaybackPipelineTracker.logFormatSelected(
                            label = primary.qualityLabel,
                            isMuxed = primary.isMuxed,
                            format = primary.format,
                            urlSnippet = primary.videoUrl?.take(60) ?: "unknown"
                        )
                    }
                    return@withContext ytDlpResult
                }
            }

            return@withContext ExtractionResult.Error(
                ExtractorErrorDetails(
                    errorType = ExtractorErrorType.NO_PLAYABLE_STREAMS,
                    message = "Unable to resolve playable stream for this video.",
                    rawExceptionName = "StreamResolutionException",
                    fullStackTrace = "",
                    urlOrId = targetUrl,
                    causeInfo = "Both NewPipe and yt-dlp extraction layers were attempted.",
                    technicalFixSuggestion = "Check network connection or try again later."
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

    fun parseQualityScore(option: PlayableStreamOption): Long {
        val label = option.qualityLabel
        val regex = Regex("(\\d{3,4})p")
        val match = regex.find(label)
        val height = match?.groupValues?.get(1)?.toIntOrNull() ?: when {
            label.contains("2160", ignoreCase = true) || label.contains("4k", ignoreCase = true) -> 2160
            label.contains("1440", ignoreCase = true) || label.contains("2k", ignoreCase = true) -> 1440
            label.contains("1080", ignoreCase = true) -> 1080
            label.contains("720", ignoreCase = true) -> 720
            label.contains("480", ignoreCase = true) -> 480
            label.contains("360", ignoreCase = true) -> 360
            label.contains("240", ignoreCase = true) -> 240
            label.contains("144", ignoreCase = true) -> 144
            else -> 720
        }
        var score = height * 10_000L
        if (option.format.equals("mp4", ignoreCase = true) || label.contains("mp4", ignoreCase = true)) score += 2_000_000L
        if (option.isMuxed) score += 50_000_000L // Highly prioritize standalone playable streams over adaptive video-only
        return score
    }

    suspend fun fetchStreamData(urlOrId: String, context: Context? = null, providerId: String? = null): ExtractionResult {
        return resolveStream(urlOrId, context, providerId)
    }

    suspend fun searchVideos(query: String, context: Context? = null): FeedResult = withContext(Dispatchers.IO) {
        try {
            val ytItems = searchYouTube(query, context)
            val archiveItems = ArchiveOrgProvider.search(query, 1)
            val combined = (ytItems + archiveItems).distinctBy { it.id }
            FeedResult.Success(combined)
        } catch (e: Exception) {
            FeedResult.Error(
                FeedErrorDetails(
                    rawExceptionName = e.javaClass.simpleName,
                    message = e.message ?: "Search failed",
                    fullStackTrace = e.stackTraceToString(),
                    urlOrQuery = query
                )
            )
        }
    }
}
