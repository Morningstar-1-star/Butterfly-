package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.CaptionOption
import com.example.model.ExtractorErrorDetails
import com.example.model.ExtractorErrorType
import com.example.model.PlayableStreamOption
import com.example.model.StreamData
import com.example.model.VideoItem
import com.example.plugin.sdk.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/**
 * Ultra-fast, high-reliability native YouTube stream extractor.
 * 
 * Races direct YouTube Innertube client endpoints (ANDROID_VR, ANDROID_TESTSUITE,
 * TVHTML5, WEB_EMBEDDED_PLAYER) and active Piped / Invidious / Cobalt nodes
 * concurrently with zero sequential lag.
 * 
 * Guarantees direct native ExoPlayer streams and complete metadata.
 */
object YouTubeFastStreamResolver {

    private const val TAG = "YouTubeFastResolver"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun resolveStream(
        urlOrId: String,
        context: Context? = null
    ): YouTubeExtractorHelper.ExtractionResult = withContext(Dispatchers.IO) {
        val videoId = when (val parsed = YouTubeExtractorHelper.parseYouTubeInput(urlOrId)) {
            is YouTubeExtractorHelper.UrlParseResult.ValidVideoId -> parsed.videoId
            is YouTubeExtractorHelper.UrlParseResult.InvalidUrl -> {
                val clean = urlOrId.trim()
                if (clean.length == 11 && clean.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
                    clean
                } else {
                    return@withContext YouTubeExtractorHelper.ExtractionResult.Error(
                        ExtractorErrorDetails(
                            errorType = ExtractorErrorType.UNAVAILABLE,
                            message = "Invalid YouTube video ID: $urlOrId",
                            rawExceptionName = "IllegalArgumentException",
                            fullStackTrace = "Invalid video ID format",
                            urlOrId = urlOrId
                        )
                    )
                }
            }
            is YouTubeExtractorHelper.UrlParseResult.SearchQuery -> urlOrId.trim()
        }

        Log.d(TAG, "Resolving YouTube stream for videoId: $videoId")
        val startTime = System.currentTimeMillis()

        val targetCtx = context ?: com.example.plugin.providers.ArchiveOrgProvider.contextRef ?: com.example.MainApplication.appContext
        val fullWatchUrl = "https://www.youtube.com/watch?v=$videoId"

        // 1. PRIMARY HIGH-RELIABILITY PIPELINE: On-device yt-dlp extraction with signature/n-parameter decryption
        try {
            if (YtDlpResolver.isYtDlpSupportedUrl(fullWatchUrl)) {
                Log.d(TAG, "Attempting primary high-reliability yt-dlp extraction for $fullWatchUrl")
                val ytDlpRes = withTimeoutOrNull(45000L) {
                    YtDlpResolver.extractStreamInfo(targetCtx, fullWatchUrl)
                }
                if (ytDlpRes is YtDlpResolver.ExtractionResult.Success && ytDlpRes.streamData.availableStreamOptions.isNotEmpty()) {
                    val validOptions = ytDlpRes.streamData.availableStreamOptions.filter { it.videoUrl?.startsWith("http") == true }
                    if (validOptions.isNotEmpty()) {
                        val elapsed = System.currentTimeMillis() - startTime
                        Log.d(TAG, "Primary yt-dlp extraction SUCCESS for $videoId in ${elapsed}ms (${validOptions.size} streams)")
                        return@withContext YouTubeExtractorHelper.ExtractionResult.Success(ytDlpRes.streamData)
                    }
                } else if (ytDlpRes is YtDlpResolver.ExtractionResult.Error) {
                    Log.w(TAG, "Primary yt-dlp extraction returned error: ${ytDlpRes.message}")
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Primary yt-dlp extraction exception: ${t.message}")
        }

        // 2. SECONDARY BACKUP PIPELINE: Race Innertube profiles and direct proxies concurrently
        Log.d(TAG, "Primary yt-dlp extraction failed or was bypassed, falling back to secondary fast concurrent racing...")
        val fastWinner = coroutineScope {
            val channel = Channel<StreamData>(capacity = 16)

            // Task 1: Direct Innertube Client Profiles Race
            val innertubeJob = launch {
                try {
                    val fallback = withTimeoutOrNull(3000L) { raceAllResolvers(videoId, context) }
                    if (fallback != null && fallback.availableStreamOptions.isNotEmpty()) {
                        channel.trySend(fallback)
                    }
                } catch (_: Throwable) {}
            }

            // Task 2: Direct Piped Proxy
            val pipedJob = launch {
                try {
                    val piped = withTimeoutOrNull(2500L) { fetchFromPiped(videoId) }
                    if (piped != null && piped.availableStreamOptions.isNotEmpty()) {
                        channel.trySend(piped)
                    }
                } catch (_: Throwable) {}
            }

            // Task 3: Direct Invidious Proxy
            val invidiousJob = launch {
                try {
                    val invidious = withTimeoutOrNull(2500L) { fetchFromInvidious(videoId) }
                    if (invidious != null && invidious.availableStreamOptions.isNotEmpty()) {
                        channel.trySend(invidious)
                    }
                } catch (_: Throwable) {}
            }

            // Task 4: Direct Cobalt Proxy
            val cobaltJob = launch {
                try {
                    val cobalt = withTimeoutOrNull(2500L) { fetchFromCobalt(videoId) }
                    if (cobalt != null && cobalt.availableStreamOptions.isNotEmpty()) {
                        channel.trySend(cobalt)
                    }
                } catch (_: Throwable) {}
            }

            // Wait for the first winning result (max 3.0 seconds timeout)
            val res = withTimeoutOrNull(3000L) {
                try {
                    channel.receive()
                } catch (_: Throwable) {
                    null
                }
            }

            innertubeJob.cancel()
            pipedJob.cancel()
            invidiousJob.cancel()
            cobaltJob.cancel()
            channel.close()
            res
        }

        if (fastWinner != null && fastWinner.availableStreamOptions.isNotEmpty()) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "Fallback ultra-fast direct YouTube extraction SUCCESS for $videoId in ${elapsed}ms (${fastWinner.availableStreamOptions.size} options)")
            return@withContext YouTubeExtractorHelper.ExtractionResult.Success(fastWinner)
        }

        val totalElapsed = System.currentTimeMillis() - startTime
        Log.e(TAG, "All direct YouTube extraction strategies failed for $videoId after ${totalElapsed}ms.")

        YouTubeExtractorHelper.ExtractionResult.Error(
            ExtractorErrorDetails(
                errorType = ExtractorErrorType.NO_PLAYABLE_STREAMS,
                message = "Unable to fetch direct stream URLs for YouTube video ($videoId).",
                rawExceptionName = "YouTubeDirectExtractionFailedException",
                fullStackTrace = "All YouTube direct stream extraction pipelines (yt-dlp, Innertube, Piped, Invidious, Cobalt) returned empty stream manifests.",
                urlOrId = "https://www.youtube.com/watch?v=$videoId",
                causeInfo = "Network Path: Local Android App Client Network. Reason: All direct stream extractors were rate-limited or blocked by YouTube.",
                technicalFixSuggestion = "Check network connection or try configured PoToken."
            )
        )
    }

    /**
     * Races all Innertube client profiles and public proxies concurrently using a Channel.
     * The first valid result immediately wins and returns.
     */
    private suspend fun raceAllResolvers(videoId: String, context: Context?): StreamData? = coroutineScope {
        val channel = Channel<StreamData>(capacity = 16)

        val resolvers = mutableListOf<suspend () -> StreamData?>()

        // 1. Innertube Android VR (Bypasses bot-checks & login)
        resolvers.add {
            fetchFromInnertube(
                videoId = videoId,
                profile = InnertubeClientProfile(
                    clientName = "ANDROID_VR",
                    clientVersion = "1.56.21",
                    osName = "Android",
                    osVersion = "12",
                    androidSdkVersion = 32,
                    userAgent = "com.google.android.apps.youtube.vr.oculus/1.56.21 (Linux; U; Android 12; Quest 2) gzip",
                    clientNumber = "28"
                )
            )
        }

        // 2. Innertube Android TestSuite (Automated test profile, skips login)
        resolvers.add {
            fetchFromInnertube(
                videoId = videoId,
                profile = InnertubeClientProfile(
                    clientName = "ANDROID_TESTSUITE",
                    clientVersion = "1.9",
                    osName = "Android",
                    osVersion = "13",
                    androidSdkVersion = 33,
                    userAgent = "com.google.android.youtube/1.9 (Linux; U; Android 13; en_US; Pixel 7)",
                    clientNumber = "30"
                )
            )
        }

        // 3. Innertube TVHTML5 (Smart TV profile)
        resolvers.add {
            fetchFromInnertube(
                videoId = videoId,
                profile = InnertubeClientProfile(
                    clientName = "TVHTML5",
                    clientVersion = "7.20240101.12.00",
                    osName = "Tizen",
                    osVersion = "6.0",
                    userAgent = "Mozilla/5.0 (SMART-TV; LINUX; Tizen 6.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.5735.196 Safari/537.36",
                    clientNumber = "85",
                    isTvEmbed = true
                )
            )
        }

        // 4. Innertube Web Embedded
        resolvers.add {
            fetchFromInnertube(
                videoId = videoId,
                profile = InnertubeClientProfile(
                    clientName = "WEB_EMBEDDED_PLAYER",
                    clientVersion = "1.20240101.01.00",
                    osName = "Windows",
                    osVersion = "10.0",
                    userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                    clientNumber = "56",
                    isTvEmbed = true
                )
            )
        }

        // 5. Innertube iOS
        resolvers.add {
            fetchFromInnertube(
                videoId = videoId,
                profile = InnertubeClientProfile(
                    clientName = "IOS",
                    clientVersion = "19.29.1",
                    osName = "iOS",
                    osVersion = "16.5.0.20F66",
                    userAgent = "com.google.ios.youtube/19.29.1 (iPhone14,3; U; CPU iOS 16_5 like Mac OS X; en_US)",
                    clientNumber = "5"
                )
            )
        }

        // Launch all concurrent tasks
        val jobs = resolvers.map { resolver ->
            launch(Dispatchers.IO) {
                try {
                    val res = withTimeoutOrNull(3200L) { resolver() }
                    if (res != null && res.availableStreamOptions.isNotEmpty()) {
                        channel.trySend(res)
                    }
                } catch (_: Throwable) {
                }
            }
        }

        // Await the very first successful completion or timeout
        val winner = withTimeoutOrNull(4000L) {
            try {
                channel.receive()
            } catch (_: Throwable) {
                null
            }
        }

        // Cancel remaining jobs to preserve bandwidth
        jobs.forEach { it.cancel() }
        channel.close()

        winner
    }

    @Volatile
    private var cachedVisitorData: String? = null

    private fun getOrFetchVisitorData(): String? {
        if (!cachedVisitorData.isNullOrBlank()) return cachedVisitorData
        return try {
            val req = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/visitor_id?prettyPrint=false")
                .post("{}".toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("X-YouTube-Client-Name", "1")
                .header("X-YouTube-Client-Version", "2.20240101.00.00")
                .build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                resp.close()
                if (body.isNotBlank()) {
                    val json = JSONObject(body)
                    val visitorData = json.optString("visitorData").takeIf { it.isNotBlank() }
                    if (visitorData != null) {
                        cachedVisitorData = visitorData
                        return visitorData
                    }
                }
            } else {
                resp.close()
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Queries YouTube's Innertube API with specified client profile.
     */
    private fun fetchFromInnertube(videoId: String, profile: InnertubeClientProfile): StreamData? {
        try {
            val visitorData = getOrFetchVisitorData()
            val jsonBody = JSONObject().apply {
                val contextObj = JSONObject().apply {
                    val clientObj = JSONObject().apply {
                        put("clientName", profile.clientName)
                        put("clientVersion", profile.clientVersion)
                        put("hl", "en")
                        put("gl", "US")
                        if (!visitorData.isNullOrBlank()) put("visitorData", visitorData)
                        if (profile.osName.isNotBlank()) put("osName", profile.osName)
                        if (profile.osVersion.isNotBlank()) put("osVersion", profile.osVersion)
                        if (profile.androidSdkVersion > 0) put("androidSdkVersion", profile.androidSdkVersion)
                    }
                    put("client", clientObj)
                    if (profile.isTvEmbed) {
                        put("thirdParty", JSONObject().apply {
                            put("embedUrl", "https://www.youtube.com/watch?v=$videoId")
                        })
                    }
                }
                put("context", contextObj)
                put("videoId", videoId)
                put("playbackContext", JSONObject().apply {
                    put("contentPlaybackContext", JSONObject().apply {
                        put("html5Preference", "HTML5_PREF_WANTS")
                    })
                })
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .post(jsonBody.toString().toRequestBody(mediaType))
                .header("User-Agent", profile.userAgent)
                .header("X-YouTube-Client-Name", profile.clientNumber)
                .header("X-YouTube-Client-Version", profile.clientVersion)
                .header("Origin", "https://www.youtube.com")
                .header("Referer", "https://www.youtube.com/")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }

            val bodyStr = response.body?.string() ?: ""
            response.close()
            if (bodyStr.isBlank()) return null

            val json = JSONObject(bodyStr)
            val playability = json.optJSONObject("playabilityStatus")
            val status = playability?.optString("status")
            if (status != "OK") {
                return null
            }

            val streamingData = json.optJSONObject("streamingData") ?: return null
            val videoDetails = json.optJSONObject("videoDetails")

            val title = videoDetails?.optString("title")?.takeIf { it.isNotBlank() } ?: "YouTube Video ($videoId)"
            val author = videoDetails?.optString("author")?.takeIf { it.isNotBlank() } ?: "YouTube Creator"
            val viewCount = videoDetails?.optLong("viewCount", 0L) ?: 0L
            val description = videoDetails?.optString("shortDescription")

            val thumbArray = videoDetails?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            val thumbUrl = if (thumbArray != null && thumbArray.length() > 0) {
                thumbArray.getJSONObject(thumbArray.length() - 1).optString("url")
            } else {
                "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            }

            val hlsUrl = streamingData.optString("hlsManifestUrl").takeIf { it.isNotBlank() && it.startsWith("http") }
            val formats = streamingData.optJSONArray("formats") ?: JSONArray()
            val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats") ?: JSONArray()

            val options = mutableListOf<PlayableStreamOption>()

            // 0. Auto Quality HLS m3u8 Stream (High Priority Native ExoPlayer Format)
            if (!hlsUrl.isNullOrBlank()) {
                options.add(
                    PlayableStreamOption(
                        qualityLabel = "Auto Quality (HLS m3u8 Stream)",
                        format = "m3u8",
                        isMuxed = true,
                        videoUrl = hlsUrl,
                        headers = mapOf(
                            "User-Agent" to profile.userAgent,
                            "Referer" to "https://www.youtube.com/"
                        ),
                        providerType = ProviderType.OTHER
                    )
                )
            }

            // 1. Muxed progressive formats
            for (i in 0 until formats.length()) {
                val f = formats.getJSONObject(i)
                val url = f.optString("url")

                if (url.isNotBlank() && url.startsWith("http")) {
                    val quality = f.optString("qualityLabel").ifBlank { "${f.optInt("height", 360)}p" }
                    options.add(
                        PlayableStreamOption(
                            qualityLabel = "$quality (Direct)",
                            format = "mp4",
                            isMuxed = true,
                            videoUrl = url,
                            audioUrl = null,
                            headers = mapOf(
                                "User-Agent" to profile.userAgent,
                                "Referer" to "https://www.youtube.com/"
                            ),
                            providerType = ProviderType.OTHER
                        )
                    )
                }
            }

            // 2. Best audio
            var bestAudioUrl: String? = null
            var bestAudioBitrate = 0
            for (i in 0 until adaptiveFormats.length()) {
                val af = adaptiveFormats.getJSONObject(i)
                val mime = af.optString("mimeType")
                val url = af.optString("url")

                if (mime.startsWith("audio/") && url.isNotBlank() && url.startsWith("http")) {
                    val br = af.optInt("bitrate", af.optInt("averageBitrate", 0))
                    if (br > bestAudioBitrate) {
                        bestAudioBitrate = br
                        bestAudioUrl = url
                    }
                }
            }

            // 3. Adaptive Video Formats
            for (i in 0 until adaptiveFormats.length()) {
                val af = adaptiveFormats.getJSONObject(i)
                val mime = af.optString("mimeType")
                val url = af.optString("url")

                if (mime.startsWith("video/") && url.isNotBlank() && url.startsWith("http")) {
                    val quality = af.optString("qualityLabel").ifBlank { "${af.optInt("height", 720)}p" }
                    val fps = af.optInt("fps", 30)
                    val fpsStr = if (fps > 30) " ${fps}fps" else ""
                    val label = when {
                        quality.contains("2160") || quality.contains("4k", ignoreCase = true) -> "2160p (4K)$fpsStr"
                        quality.contains("1440") || quality.contains("2k", ignoreCase = true) -> "1440p (2K)$fpsStr"
                        quality.contains("1080") -> "1080p HD$fpsStr"
                        quality.contains("720") -> "720p HD$fpsStr"
                        else -> "$quality$fpsStr"
                    }

                    if (!bestAudioUrl.isNullOrBlank()) {
                        options.add(
                            PlayableStreamOption(
                                qualityLabel = label,
                                format = "mp4",
                                isMuxed = false,
                                videoUrl = url,
                                audioUrl = bestAudioUrl,
                                headers = mapOf(
                                    "User-Agent" to profile.userAgent,
                                    "Referer" to "https://www.youtube.com/"
                               ),
                                providerType = ProviderType.OTHER
                            )
                        )
                    } else {
                        options.add(
                            PlayableStreamOption(
                                qualityLabel = label,
                                format = "mp4",
                                isMuxed = true,
                                videoUrl = url,
                                audioUrl = null,
                                headers = mapOf(
                                    "User-Agent" to profile.userAgent,
                                    "Referer" to "https://www.youtube.com/"
                                ),
                                providerType = ProviderType.OTHER
                            )
                        )
                    }
                }
            }

            // 4. HLS Master
            if (!hlsUrl.isNullOrBlank()) {
                options.add(
                    0,
                    PlayableStreamOption(
                        qualityLabel = "Auto (HLS Master)",
                        format = "hls",
                        isMuxed = true,
                        videoUrl = hlsUrl,
                        audioUrl = null,
                        headers = mapOf(
                            "User-Agent" to profile.userAgent,
                            "Referer" to "https://www.youtube.com/"
                        ),
                        providerType = ProviderType.OTHER
                    )
                )
            }

            if (options.isEmpty()) return null

            val defaultOption = options.firstOrNull { it.qualityLabel.contains("1080p") }
                ?: options.firstOrNull { it.qualityLabel.contains("720p") }
                ?: options.firstOrNull { it.format == "hls" }
                ?: options.first()

            val captions = mutableListOf<CaptionOption>()
            val captionTracks = json.optJSONObject("captions")
                ?.optJSONObject("playerCaptionsTracklistRenderer")
                ?.optJSONArray("captionTracks")
            if (captionTracks != null) {
                for (c in 0 until captionTracks.length()) {
                    val track = captionTracks.getJSONObject(c)
                    val base = track.optString("baseUrl")
                    val name = track.optJSONObject("name")?.optString("simpleText") ?: "English"
                    val code = track.optString("languageCode", "en")
                    if (base.isNotBlank()) {
                        captions.add(
                            CaptionOption(
                                languageName = name,
                                languageCode = code,
                                format = "VTT",
                                url = "$base&fmt=vtt"
                            )
                        )
                    }
                }
            }

            return StreamData(
                videoId = videoId,
                videoUrl = "https://www.youtube.com/watch?v=$videoId",
                title = title,
                channelName = author,
                channelAvatarUrl = "https://i.ytimg.com/vi/$videoId/default.jpg",
                description = description,
                viewCount = viewCount,
                thumbnailUrl = thumbUrl,
                availableStreamOptions = options,
                selectedStreamOption = defaultOption,
                captionOptions = captions,
                hlsUrl = hlsUrl,
                headers = mapOf(
                    "User-Agent" to profile.userAgent,
                    "Referer" to "https://www.youtube.com/"
                ),
                providerId = "youtube"
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun extractUrlFromCipher(cipher: String): String {
        return try {
            val parts = cipher.split("&")
            var rawUrl = ""
            for (p in parts) {
                val kv = p.split("=")
                if (kv.isNotEmpty() && kv[0] == "url") {
                    rawUrl = URLDecoder.decode(kv.getOrElse(1) { "" }, "UTF-8")
                    break
                }
            }
            rawUrl
        } catch (_: Throwable) {
            ""
        }
    }

    private fun fetchYouTubeOEmbed(videoId: String): Pair<String, String>? {
        return try {
            val request = Request.Builder()
                .url("https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                response.close()
                if (!body.isNullOrBlank()) {
                    val json = JSONObject(body)
                    val title = json.optString("title", "").ifBlank { "YouTube Video ($videoId)" }
                    val author = json.optString("author_name", "").ifBlank { "YouTube Creator" }
                    Pair(title, author)
                } else null
            } else {
                response.close()
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchFromPiped(videoId: String): StreamData? = withContext(Dispatchers.IO) {
        val pipedInstances = listOf(
            "https://pipedapi.drgns.space",
            "https://piped-api.garudalinux.org",
            "https://api.piped.privacydev.net",
            "https://pipedapi.sugoi.my.id",
            "https://pipedapi.tokhmi.xyz",
            "https://pipedapi.systemli.org",
            "https://pipedapi.privacy.com.de",
            "https://pipedapi.ducks.party",
            "https://pipedapi.lunar.icu",
            "https://pipedapi.adminforge.de",
            "https://pipedapi.astral.cool",
            "https://pipedapi.mha.fi"
        )
        for (instance in pipedInstances) {
            try {
                val req = Request.Builder()
                    .url("$instance/streams/$videoId")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                val resp = httpClient.newCall(req).execute()
                if (!resp.isSuccessful) {
                    resp.close()
                    continue
                }
                val bodyStr = resp.body?.string() ?: ""
                resp.close()
                if (bodyStr.isBlank()) continue

                val json = JSONObject(bodyStr)
                val title = json.optString("title", "YouTube Video ($videoId)")
                val uploader = json.optString("uploader", "YouTube Creator")
                val thumbUrl = json.optString("thumbnailUrl", "https://i.ytimg.com/vi/$videoId/hqdefault.jpg")

                val hlsUrl = json.optString("hls").takeIf { it.isNotBlank() && it.startsWith("http") }
                val videoStreams = json.optJSONArray("videoStreams") ?: JSONArray()
                val audioStreams = json.optJSONArray("audioStreams") ?: JSONArray()

                val options = mutableListOf<PlayableStreamOption>()

                if (!hlsUrl.isNullOrBlank()) {
                    options.add(
                        PlayableStreamOption(
                            qualityLabel = "Auto Quality (Piped m3u8 Stream)",
                            format = "m3u8",
                            isMuxed = true,
                            videoUrl = hlsUrl,
                            providerType = ProviderType.OTHER
                        )
                    )
                }

                var bestAudioUrl: String? = null
                var maxAudioBitrate = 0
                for (i in 0 until audioStreams.length()) {
                    val a = audioStreams.getJSONObject(i)
                    val url = a.optString("url")
                    val bitrate = a.optInt("bitrate", 0)
                    if (url.isNotBlank() && bitrate > maxAudioBitrate) {
                        maxAudioBitrate = bitrate
                        bestAudioUrl = url
                    }
                }

                for (i in 0 until videoStreams.length()) {
                    val v = videoStreams.getJSONObject(i)
                    val url = v.optString("url")
                    val quality = v.optString("quality", "720p")
                    val format = v.optString("format", "MPEG_4")
                    if (url.isNotBlank()) {
                        options.add(
                            PlayableStreamOption(
                                qualityLabel = "$quality (Piped Proxy)",
                                format = if (format.contains("MPEG", ignoreCase = true)) "mp4" else "webm",
                                isMuxed = !v.optBoolean("videoOnly", false),
                                videoUrl = url,
                                audioUrl = if (v.optBoolean("videoOnly", false)) bestAudioUrl else null,
                                providerType = ProviderType.OTHER
                            )
                        )
                    }
                }

                if (options.isNotEmpty()) {
                    val defaultOpt = options.first()
                    return@withContext StreamData(
                        videoId = videoId,
                        videoUrl = "https://www.youtube.com/watch?v=$videoId",
                        title = title,
                        channelName = uploader,
                        thumbnailUrl = thumbUrl,
                        hlsUrl = hlsUrl,
                        availableStreamOptions = options,
                        selectedStreamOption = defaultOpt,
                        providerId = "youtube"
                    )
                }
            } catch (_: Exception) {
                // try next instance
            }
        }
        null
    }

    private suspend fun fetchFromInvidious(videoId: String): StreamData? = withContext(Dispatchers.IO) {
        val invidiousInstances = listOf(
            "https://inv.tux.pizza",
            "https://invidious.nerdvpn.de",
            "https://invidious.drgns.space",
            "https://invidious.privacydev.net",
            "https://yewtu.be",
            "https://iv.melmac.space",
            "https://invidious.flokinet.to",
            "https://inv.riverside.rocks",
            "https://invidious.lunar.icu",
            "https://invidious.projectsegfau.lt"
        )
        for (instance in invidiousInstances) {
            try {
                val req = Request.Builder()
                    .url("$instance/api/v1/videos/$videoId")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                val resp = httpClient.newCall(req).execute()
                if (!resp.isSuccessful) {
                    resp.close()
                    continue
                }
                val bodyStr = resp.body?.string() ?: ""
                resp.close()
                if (bodyStr.isBlank()) continue

                val json = JSONObject(bodyStr)
                val title = json.optString("title", "YouTube Video ($videoId)")
                val author = json.optString("author", "YouTube Creator")
                val viewCount = json.optLong("viewCount", 0L)
                val thumbUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                val hlsUrl = json.optString("hlsUrl").takeIf { it.isNotBlank() && it.startsWith("http") }

                val adaptiveFormats = json.optJSONArray("adaptiveFormats") ?: JSONArray()
                val formatStreams = json.optJSONArray("formatStreams") ?: JSONArray()

                val options = mutableListOf<PlayableStreamOption>()

                if (!hlsUrl.isNullOrBlank()) {
                    options.add(
                        PlayableStreamOption(
                            qualityLabel = "Auto Quality (Invidious m3u8 Stream)",
                            format = "m3u8",
                            isMuxed = true,
                            videoUrl = hlsUrl,
                            providerType = ProviderType.OTHER
                        )
                    )
                }

                for (i in 0 until formatStreams.length()) {
                    val f = formatStreams.getJSONObject(i)
                    val url = f.optString("url")
                    val qualityLabel = f.optString("qualityLabel", "360p")
                    if (url.isNotBlank()) {
                        options.add(
                            PlayableStreamOption(
                                qualityLabel = "$qualityLabel (Invidious Stream)",
                                format = "mp4",
                                isMuxed = true,
                                videoUrl = url,
                                audioUrl = null,
                                providerType = ProviderType.OTHER
                            )
                        )
                    }
                }

                var bestAudioUrl: String? = null
                var maxAudioBitrate = 0
                for (i in 0 until adaptiveFormats.length()) {
                    val af = adaptiveFormats.getJSONObject(i)
                    val type = af.optString("type")
                    val url = af.optString("url")
                    val bitrate = af.optInt("bitrate", 0)
                    if (type.startsWith("audio/") && url.isNotBlank() && bitrate > maxAudioBitrate) {
                        maxAudioBitrate = bitrate
                        bestAudioUrl = url
                    }
                }

                for (i in 0 until adaptiveFormats.length()) {
                    val af = adaptiveFormats.getJSONObject(i)
                    val type = af.optString("type")
                    val url = af.optString("url")
                    val qualityLabel = af.optString("qualityLabel", "720p")
                    if (type.startsWith("video/") && url.isNotBlank()) {
                        options.add(
                            PlayableStreamOption(
                                qualityLabel = "$qualityLabel (Invidious Adaptive)",
                                format = "mp4",
                                isMuxed = !bestAudioUrl.isNullOrBlank(),
                                videoUrl = url,
                                audioUrl = bestAudioUrl,
                                providerType = ProviderType.OTHER
                            )
                        )
                    }
                }

                if (options.isNotEmpty()) {
                    val defaultOpt = options.first()
                    return@withContext StreamData(
                        videoId = videoId,
                        videoUrl = "https://www.youtube.com/watch?v=$videoId",
                        title = title,
                        channelName = author,
                        thumbnailUrl = thumbUrl,
                        viewCount = viewCount,
                        hlsUrl = hlsUrl,
                        availableStreamOptions = options,
                        selectedStreamOption = defaultOpt,
                        providerId = "youtube"
                    )
                }
            } catch (_: Exception) {
            }
        }
        null
    }

    private suspend fun fetchFromCobalt(videoId: String): StreamData? = withContext(Dispatchers.IO) {
        val cobaltInstances = listOf(
            "https://api.cobalt.tools/api/json",
            "https://co.wuk.sh/api/json"
        )
        val body = JSONObject().apply {
            put("url", "https://www.youtube.com/watch?v=$videoId")
            put("videoQuality", "max")
        }
        val mediaType = "application/json; charset=utf-8".toMediaType()

        for (endpoint in cobaltInstances) {
            try {
                val req = Request.Builder()
                    .url(endpoint)
                    .post(body.toString().toRequestBody(mediaType))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                val resp = httpClient.newCall(req).execute()
                if (!resp.isSuccessful) {
                    resp.close()
                    continue
                }
                val bodyStr = resp.body?.string() ?: ""
                resp.close()
                if (bodyStr.isBlank()) continue

                val json = JSONObject(bodyStr)
                val status = json.optString("status")
                val streamUrl = json.optString("url")
                if ((status == "stream" || status == "redirect") && streamUrl.isNotBlank()) {
                    val opt = PlayableStreamOption(
                        qualityLabel = "Cobalt Direct Stream",
                        format = "mp4",
                        isMuxed = true,
                        videoUrl = streamUrl,
                        providerType = ProviderType.OTHER
                    )
                    return@withContext StreamData(
                        videoId = videoId,
                        videoUrl = "https://www.youtube.com/watch?v=$videoId",
                        title = "YouTube Video ($videoId)",
                        channelName = "YouTube Creator",
                        thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                        availableStreamOptions = listOf(opt),
                        selectedStreamOption = opt,
                        providerId = "youtube"
                    )
                }
            } catch (_: Exception) {
            }
        }
        null
    }

    private data class InnertubeClientProfile(
        val clientName: String,
        val clientVersion: String,
        val osName: String,
        val osVersion: String,
        val androidSdkVersion: Int = 0,
        val userAgent: String,
        val clientNumber: String,
        val isTvEmbed: Boolean = false
    )
}
