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
                "https://www.youtube.com/feed/trending"
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
                Log.i(TAG, "Fetched ${items.size} trending videos via NewPipe")
                return@withContext items
            }
        } catch (e: Exception) {
            Log.w(TAG, "NewPipe trending fetch failed: ${e.message}")
        }

        try {
            val client = okhttp3.OkHttpClient()
            val req = okhttp3.Request.Builder()
                .url("https://pipedapi.kavin.rocks/trending?region=US")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val bodyStr = resp.body?.string() ?: ""
                val jsonArray = org.json.JSONArray(bodyStr)
                val items = mutableListOf<VideoItem>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(i) ?: continue
                    val url = obj.optString("url", "")
                    val vId = url.substringAfter("v=").substringBefore("&")
                    if (vId.isBlank()) continue
                    items.add(
                        VideoItem(
                            id = vId,
                            title = obj.optString("title", "YouTube Video"),
                            uploaderName = obj.optString("uploaderName", "YouTube"),
                            viewCount = obj.optLong("views", -1L),
                            durationSeconds = obj.optLong("duration", -1L),
                            thumbnailUrl = obj.optString("thumbnail", "https://i.ytimg.com/vi/$vId/hqdefault.jpg"),
                            providerId = "youtube"
                        )
                    )
                }
                Log.i(TAG, "Fetched ${items.size} trending videos via Piped API fallback")
                return@withContext items
            }
        } catch (e: Exception) {
            Log.e(TAG, "Piped API trending fallback failed: ${e.message}")
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

        try {
            val client = okhttp3.OkHttpClient()
            val req = okhttp3.Request.Builder()
                .url("https://pipedapi.kavin.rocks/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&filter=all")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val bodyStr = resp.body?.string() ?: ""
                val json = org.json.JSONObject(bodyStr)
                val jsonArray = json.optJSONArray("items") ?: org.json.JSONArray()
                val items = mutableListOf<VideoItem>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(i) ?: continue
                    val type = obj.optString("type", "")
                    if (type != "stream") continue
                    val url = obj.optString("url", "")
                    val vId = url.substringAfter("v=").substringBefore("&")
                    if (vId.isBlank()) continue
                    items.add(
                        VideoItem(
                            id = vId,
                            title = obj.optString("title", "YouTube Video"),
                            uploaderName = obj.optString("uploaderName", "YouTube"),
                            viewCount = obj.optLong("views", -1L),
                            durationSeconds = obj.optLong("duration", -1L),
                            thumbnailUrl = obj.optString("thumbnail", "https://i.ytimg.com/vi/$vId/hqdefault.jpg"),
                            providerId = "youtube"
                        )
                    )
                }
                Log.i(TAG, "Fetched ${items.size} search results for '$query' via Piped API fallback")
                return@withContext items
            }
        } catch (e: Exception) {
            Log.e(TAG, "Piped API search fallback failed: ${e.message}")
        }

        emptyList()
    }

    suspend fun resolveStream(urlOrId: String, context: Context? = null): ExtractionResult = withContext(Dispatchers.IO) {
        val archiveData = ArchiveOrgProvider.getStreamData(urlOrId)
        if (archiveData != null) {
            Log.i(TAG, "Resolved via ArchiveOrgProvider")
            return@withContext ExtractionResult.Success(archiveData)
        }

        val targetUrl = if (urlOrId.startsWith("http")) urlOrId else "https://www.youtube.com/watch?v=$urlOrId"
        Log.i(TAG, "Attempting NewPipe primary extraction for: $targetUrl")

        try {
            try {
                NewPipe.init(DownloaderImpl.getInstance())
            } catch (ignored: Exception) {}

            val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, targetUrl)
            val title = streamInfo.name ?: "YouTube Video"
            val uploader = streamInfo.uploaderName ?: "YouTube"
            val desc = streamInfo.description?.getContent() ?: ""
            val thumb = streamInfo.thumbnails?.firstOrNull()?.url ?: ""

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

            streamInfo.videoOnlyStreams?.forEach { vStream ->
                val vUrl = vStream.content
                val resolution = vStream.resolution ?: "720p"
                val formatName = vStream.format?.name ?: "mp4"
                options.add(
                    PlayableStreamOption(
                        qualityLabel = "NewPipe Video-Only $resolution ($formatName)",
                        format = formatName,
                        isMuxed = false,
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
                    videoId = urlOrId,
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
                Log.i(TAG, "NewPipe extraction success: ${options.size} formats available. Selected: ${bestOption.qualityLabel}")
                return@withContext ExtractionResult.Success(streamData)
            } else {
                Log.w(TAG, "NewPipe returned zero streams, falling back to yt-dlp")
            }
        } catch (e: Exception) {
            Log.w(TAG, "NewPipe extraction failed: ${e.message}. Falling back to yt-dlp.", e)
        }

        if (context != null) {
            Log.i(TAG, "Triggering yt-dlp fallback resolver")
            val ytDlpResult = YtDlpResolver.extractStreamInfo(context, targetUrl)
            if (ytDlpResult is ExtractionResult.Success) {
                return@withContext ytDlpResult
            }
        }

        ExtractionResult.Error(
            ExtractorErrorDetails(
                errorType = ExtractorErrorType.NO_PLAYABLE_STREAMS,
                message = "Could not resolve YouTube stream",
                rawExceptionName = "ExtractionFailedException",
                fullStackTrace = "",
                urlOrId = targetUrl,
                causeInfo = "Both NewPipe and yt-dlp failed",
                technicalFixSuggestion = "Check network or video availability."
            )
        )
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
