package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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

    private val PipedEndpoints = listOf(
        "https://pipedapi.kavin.rocks",
        "https://api.piped.video",
        "https://pipedapi.tokhmi.xyz",
        "https://pipedapi.adminforge.de",
        "https://pipedapi.privacy.com.de"
    )

    private val InvidiousEndpoints = listOf(
        "https://inv.specified.tech",
        "https://invidious.nerdvpn.de",
        "https://invidious.drgns.space",
        "https://yt.artemislena.eu",
        "https://invidious.projectsegfau.lt"
    )

    suspend fun fetchYouTubeTrending(): List<VideoItem> = withContext(Dispatchers.IO) {
        // Method 1: NewPipe Kiosk Trending
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

        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        // Method 2: Piped API Endpoint pool
        for (baseEndpoint in PipedEndpoints) {
            try {
                val req = okhttp3.Request.Builder()
                    .url("$baseEndpoint/trending?region=US")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string() ?: ""
                        val jsonArray = JSONArray(bodyStr)
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
                        if (items.isNotEmpty()) {
                            Log.i(TAG, "Fetched ${items.size} trending videos via Piped $baseEndpoint")
                            return@withContext items
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Endpoint $baseEndpoint failed for trending: ${e.message}")
            }
        }

        // Method 3: Invidious API Endpoint pool
        for (baseEndpoint in InvidiousEndpoints) {
            try {
                val req = okhttp3.Request.Builder()
                    .url("$baseEndpoint/api/v1/trending")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string() ?: ""
                        val jsonArray = JSONArray(bodyStr)
                        val items = mutableListOf<VideoItem>()
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.optJSONObject(i) ?: continue
                            val vId = obj.optString("videoId", "")
                            if (vId.isBlank()) continue
                            items.add(
                                VideoItem(
                                    id = vId,
                                    title = obj.optString("title", "YouTube Video"),
                                    uploaderName = obj.optString("author", "YouTube"),
                                    viewCount = obj.optLong("viewCount", -1L),
                                    durationSeconds = obj.optLong("lengthSeconds", -1L),
                                    thumbnailUrl = "https://i.ytimg.com/vi/$vId/hqdefault.jpg",
                                    providerId = "youtube"
                                )
                            )
                        }
                        if (items.isNotEmpty()) {
                            Log.i(TAG, "Fetched ${items.size} trending videos via Invidious $baseEndpoint")
                            return@withContext items
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Invidious endpoint $baseEndpoint failed for trending: ${e.message}")
            }
        }

        // Method 4: Popular Search Queries Fallback
        val popularQueries = listOf("trending videos 2026", "music hits", "official trailers", "gaming highlights")
        for (q in popularQueries) {
            val res = searchYouTube(q)
            if (res.isNotEmpty()) {
                Log.i(TAG, "Fetched ${res.size} popular videos via Search Fallback for '$q'")
                return@withContext res
            }
        }

        emptyList()
    }

    suspend fun searchYouTube(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        // Method 1: NewPipe Search
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

        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val encodedQ = java.net.URLEncoder.encode(query, "UTF-8")

        // Method 2: Piped API Endpoints Fallback
        for (baseEndpoint in PipedEndpoints) {
            try {
                val req = okhttp3.Request.Builder()
                    .url("$baseEndpoint/search?q=$encodedQ&filter=all")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string() ?: ""
                        val json = JSONObject(bodyStr)
                        val jsonArray = json.optJSONArray("items") ?: JSONArray()
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
                        if (items.isNotEmpty()) {
                            Log.i(TAG, "Fetched ${items.size} search results for '$query' via Piped $baseEndpoint")
                            return@withContext items
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Endpoint $baseEndpoint failed for search: ${e.message}")
            }
        }

        // Method 3: Invidious API Endpoints Fallback
        for (baseEndpoint in InvidiousEndpoints) {
            try {
                val req = okhttp3.Request.Builder()
                    .url("$baseEndpoint/api/v1/search?q=$encodedQ&type=video")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string() ?: ""
                        val jsonArray = JSONArray(bodyStr)
                        val items = mutableListOf<VideoItem>()
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.optJSONObject(i) ?: continue
                            val vId = obj.optString("videoId", "")
                            if (vId.isBlank()) continue
                            items.add(
                                VideoItem(
                                    id = vId,
                                    title = obj.optString("title", "YouTube Video"),
                                    uploaderName = obj.optString("author", "YouTube"),
                                    viewCount = obj.optLong("viewCount", -1L),
                                    durationSeconds = obj.optLong("lengthSeconds", -1L),
                                    thumbnailUrl = "https://i.ytimg.com/vi/$vId/hqdefault.jpg",
                                    providerId = "youtube"
                                )
                            )
                        }
                        if (items.isNotEmpty()) {
                            Log.i(TAG, "Fetched ${items.size} search results for '$query' via Invidious $baseEndpoint")
                            return@withContext items
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Invidious endpoint $baseEndpoint failed for search: ${e.message}")
            }
        }

        emptyList()
    }

    suspend fun resolveStream(urlOrId: String, context: Context? = null): ExtractionResult = withContext(Dispatchers.IO) {
        val isArchive = urlOrId.contains("archive.org") || urlOrId.startsWith("archive_")
        
        // Handle Archive.org media explicitly
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

        // Process as YouTube video
        val videoId = when {
            urlOrId.contains("v=") -> urlOrId.substringAfter("v=").substringBefore("&")
            urlOrId.contains("youtu.be/") -> urlOrId.substringAfter("youtu.be/").substringBefore("?")
            else -> urlOrId
        }

        val targetUrl = if (urlOrId.startsWith("http")) urlOrId else "https://www.youtube.com/watch?v=$videoId"
        Log.i(TAG, "Resolving YouTube Video ID: '$videoId', Target URL: '$targetUrl'")

        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        // Step 1: Piped Direct MP4 Streams API
        Log.i(TAG, "YouTube Resolution Step 1 (Piped Direct Stream API): $videoId")
        for (baseEndpoint in PipedEndpoints) {
            try {
                val req = okhttp3.Request.Builder()
                    .url("$baseEndpoint/streams/$videoId")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string() ?: ""
                        val json = JSONObject(bodyStr)
                        val title = json.optString("title", "YouTube Video")
                        val uploader = json.optString("uploader", "YouTube")
                        val desc = json.optString("description", "")
                        val thumb = json.optString("thumbnailUrl", "https://i.ytimg.com/vi/$videoId/hqdefault.jpg")

                        val options = mutableListOf<PlayableStreamOption>()
                        val videoStreams = json.optJSONArray("videoStreams")
                        if (videoStreams != null) {
                            for (i in 0 until videoStreams.length()) {
                                val streamObj = videoStreams.optJSONObject(i) ?: continue
                                val url = streamObj.optString("url", "")
                                if (url.isBlank()) continue
                                val quality = streamObj.optString("quality", "720p")
                                val format = streamObj.optString("format", "mp4")
                                val isVideoOnly = streamObj.optBoolean("videoOnly", false)
                                if (!isVideoOnly) {
                                    options.add(
                                        PlayableStreamOption(
                                            qualityLabel = "Piped Direct $quality ($format)",
                                            format = format,
                                            isMuxed = true,
                                            videoUrl = url,
                                            providerType = ProviderType.DIRECT
                                        )
                                    )
                                }
                            }
                        }

                        val hlsUrl = json.optString("hls", null)
                        if (options.isEmpty() && !hlsUrl.isNullOrBlank()) {
                            options.add(
                                PlayableStreamOption(
                                    qualityLabel = "Piped HLS Stream",
                                    format = "m3u8",
                                    isMuxed = true,
                                    videoUrl = hlsUrl,
                                    providerType = ProviderType.DIRECT
                                )
                            )
                        }

                        if (options.isNotEmpty()) {
                            val bestOption = options.firstOrNull { it.qualityLabel.contains("720p") || it.qualityLabel.contains("1080p") } ?: options.first()
                            val streamData = StreamData(
                                videoId = videoId,
                                videoUrl = bestOption.videoUrl ?: "",
                                title = title,
                                channelName = uploader,
                                description = desc,
                                thumbnailUrl = thumb,
                                availableStreamOptions = options,
                                selectedStreamOption = bestOption,
                                hlsUrl = hlsUrl,
                                providerId = "youtube",
                                providerType = ProviderType.DIRECT
                            )
                            Log.i(TAG, "Piped stream extraction success via $baseEndpoint: ${options.size} streams")
                            return@withContext ExtractionResult.Success(streamData)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Piped endpoint $baseEndpoint failed for stream: ${e.message}")
            }
        }

        // Step 2: Invidious Direct Stream API
        Log.i(TAG, "YouTube Resolution Step 2 (Invidious Direct Stream API): $videoId")
        for (baseEndpoint in InvidiousEndpoints) {
            try {
                val req = okhttp3.Request.Builder()
                    .url("$baseEndpoint/api/v1/videos/$videoId")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string() ?: ""
                        val json = JSONObject(bodyStr)
                        val title = json.optString("title", "YouTube Video")
                        val author = json.optString("author", "YouTube")
                        val desc = json.optString("description", "")
                        val thumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                        val options = mutableListOf<PlayableStreamOption>()
                        val formatStreams = json.optJSONArray("formatStreams")
                        if (formatStreams != null) {
                            for (i in 0 until formatStreams.length()) {
                                val fmt = formatStreams.optJSONObject(i) ?: continue
                                val url = fmt.optString("url", "")
                                if (url.isBlank()) continue
                                val qLabel = fmt.optString("qualityLabel", "720p")
                                val container = fmt.optString("container", "mp4")
                                options.add(
                                    PlayableStreamOption(
                                        qualityLabel = "Invidious Direct $qLabel ($container)",
                                        format = container,
                                        isMuxed = true,
                                        videoUrl = url,
                                        providerType = ProviderType.DIRECT
                                    )
                                )
                            }
                        }

                        if (options.isNotEmpty()) {
                            val best = options.first()
                            val streamData = StreamData(
                                videoId = videoId,
                                videoUrl = best.videoUrl ?: "",
                                title = title,
                                channelName = author,
                                description = desc,
                                thumbnailUrl = thumb,
                                availableStreamOptions = options,
                                selectedStreamOption = best,
                                providerId = "youtube",
                                providerType = ProviderType.DIRECT
                            )
                            Log.i(TAG, "Invidious stream extraction success via $baseEndpoint")
                            return@withContext ExtractionResult.Success(streamData)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Invidious stream fetch failed on $baseEndpoint: ${e.message}")
            }
        }

        // Step 3: NewPipe Extractor
        Log.i(TAG, "YouTube Resolution Step 3 (NewPipe Extractor): $targetUrl")
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

        // Step 4: Real yt-dlp fallback
        if (context != null) {
            Log.i(TAG, "YouTube Resolution Step 4 (yt-dlp): $targetUrl")
            val ytDlpResult = YtDlpResolver.extractStreamInfo(context, targetUrl)
            if (ytDlpResult is ExtractionResult.Success) {
                return@withContext ytDlpResult
            }
        }

        // DO NOT FALLBACK TO ARCHIVE.ORG FOR YOUTUBE VIDEOS!
        Log.e(TAG, "All YouTube stream resolvers failed for Video ID: $videoId")
        ExtractionResult.Error(
            ExtractorErrorDetails(
                errorType = ExtractorErrorType.NO_PLAYABLE_STREAMS,
                message = "Unable to resolve playable stream for this YouTube video.",
                rawExceptionName = "YouTubeExtractionFailedException",
                fullStackTrace = "",
                urlOrId = targetUrl,
                causeInfo = "Piped API, Invidious API, NewPipe, and yt-dlp were all attempted.",
                technicalFixSuggestion = "Check internet connection or retry later."
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
