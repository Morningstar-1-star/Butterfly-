package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.*
import dev.ffmpegkit_maintained.ytdlp.YtDlp
import dev.ffmpegkit_maintained.ytdlp.YtDlpRequest
import dev.ffmpegkit_maintained.ytdlp.YtDlpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject

object YtDlpResolver {
    private const val TAG = "YtDlpResolver"

    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    @Volatile
    private var isInitialized = false
    private val initLock = Any()
    private val processSemaphore = kotlinx.coroutines.sync.Semaphore(2)

    fun ensureInitialized(ctx: Context) {
        if (!isInitialized) {
            synchronized(initLock) {
                if (!isInitialized) {
                    try {
                        YtDlp.init(ctx.applicationContext)
                        isInitialized = true
                        Log.i(TAG, "yt-dlp-android initialized successfully")
                    } catch (e: Throwable) {
                        Log.w(TAG, "yt-dlp-android init note: ${e.message}")
                    }
                }
            }
        }
    }

    fun prewarm(ctx: Context) {
        ensureInitialized(ctx)
    }

    suspend fun getEngineVersion(ctx: Context): String = withContext(Dispatchers.IO) {
        ensureInitialized(ctx)
        try {
            val request = YtDlpRequest("https://www.youtube.com")
            request.addOption("--version")
            val response = processSemaphore.withPermit {
                YtDlp.execute(request, null)
            }
            val output = response.output.trim()
            val versionMatch = Regex("""\d{4}\.\d{2}\.\d{2}.*""").find(output)
            if (versionMatch != null) {
                versionMatch.value
            } else if (output.isNotBlank()) {
                output.lines().firstOrNull { it.isNotBlank() } ?: "2024.12.13"
            } else {
                "2024.12.13"
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to retrieve yt-dlp engine version: ${e.message}")
            "2024.12.13"
        }
    }

    fun isYtDlpSupportedUrl(url: String): Boolean {
        val u = url.lowercase().trim()
        val supportedDomains = listOf(
            "youtube.com", "youtu.be",
            "vimeo.com",
            "dailymotion.com", "dai.ly",
            "bilibili.com",
            "pornhub.com", "phncdn.com",
            "xvideos.com",
            "4tube.com",
            "beeg.com",
            "rule34video.com",
            "redtube.com",
            "xhamster.com",
            "youporn.com",
            "eporner.com",
            "archive.org",
            "hotstar.com", "jiohotstar.com",
            "tiktok.com",
            "twitch.tv",
            "soundcloud.com"
        )
        return supportedDomains.any { u.contains(it) } || (url.length == 11 && !url.contains("/"))
    }

    data class ParsedFormat(
        val formatId: String,
        val url: String,
        val ext: String,
        val resolution: String,
        val width: Int,
        val height: Int,
        val fps: Double,
        val tbr: Double,
        val vbr: Double,
        val abr: Double,
        val vcodec: String,
        val acodec: String,
        val formatNote: String,
        val protocol: String,
        val httpHeaders: Map<String, String> = emptyMap()
    ) {
        val isAudioOnly: Boolean
            get() = (vcodec == "none" || vcodec.isBlank()) && (acodec != "none" && acodec.isNotBlank())

        val isVideoOnly: Boolean
            get() = (vcodec != "none" && vcodec.isNotBlank()) && (acodec == "none" || acodec.isBlank())

        val isMuxed: Boolean
            get() = (vcodec != "none" && vcodec.isNotBlank()) && (acodec != "none" && acodec.isNotBlank())

        val isHlsOrDash: Boolean
            get() = protocol.contains("m3u8", ignoreCase = true) ||
                    ext.equals("m3u8", ignoreCase = true) ||
                    ext.equals("mpd", ignoreCase = true) ||
                    protocol.contains("dash", ignoreCase = true)

        val isH264: Boolean
            get() = vcodec.startsWith("avc", ignoreCase = true) || vcodec.startsWith("h264", ignoreCase = true)

        val effectiveHeight: Int
            get() {
                if (height > 0) return height
                val regex = Regex("(\\d{3,4})p?")
                val match = regex.find(resolution) ?: regex.find(formatNote)
                return match?.groupValues?.get(1)?.toIntOrNull() ?: 720
            }

        val qualityScore: Long
            get() {
                var score = effectiveHeight * 10_000_000L
                score += (fps.toLong() * 100_000L)
                if (isH264) {
                    score += 500_000L // Highly compatible hardware decode
                } else if (vcodec.startsWith("vp09", ignoreCase = true) || vcodec.startsWith("vp9", ignoreCase = true)) {
                    score += 30_000L
                } else if (vcodec.startsWith("av01", ignoreCase = true) || vcodec.startsWith("av1", ignoreCase = true)) {
                    score += 20_000L
                }
                if (ext.equals("mp4", ignoreCase = true)) {
                    score += 200_000L
                }
                if (isMuxed) {
                    score += 1_000_000L // Prefer muxed stream for direct playback without merging
                }
                score += tbr.toLong()
                return score
            }
    }

    suspend fun extractStreamInfo(ctx: Context, targetUrl: String): YouTubeExtractorHelper.ExtractionResult = withContext(Dispatchers.IO) {
        try {
            val isYouTube = targetUrl.contains("youtube.com") || targetUrl.contains("youtu.be") || (targetUrl.length == 11 && !targetUrl.startsWith("http") && !targetUrl.contains(" "))
            val videoUrl = when {
                targetUrl.startsWith("http://") || targetUrl.startsWith("https://") -> targetUrl
                targetUrl.length == 11 && !targetUrl.contains(" ") -> "https://www.youtube.com/watch?v=$targetUrl"
                else -> "ytsearch1:$targetUrl"
            }

            Log.i(TAG, "Executing yt-dlp stream extraction for: $videoUrl")

            ensureInitialized(ctx)

            val request = YtDlpRequest(videoUrl)
            request.addOption("--dump-json")
            request.addOption("--no-playlist")
            request.addOption("--ignore-errors")
            request.addOption("--no-warnings")
            request.addOption("--user-agent", DEFAULT_USER_AGENT)
            request.addOption("--add-header", "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            request.addOption("--add-header", "Accept-Language: en-US,en;q=0.9")
            request.addOption("--add-header", "Sec-Fetch-Mode: navigate")

            // Domain-specific Referer & Origin headers
            val domainHeaders = mutableMapOf<String, String>()
            domainHeaders["User-Agent"] = DEFAULT_USER_AGENT
            val lowerUrl = videoUrl.lowercase()
            when {
                lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be") -> {
                    // Do not inject synthetic Referer/Origin for YouTube so googlevideo.com streams avoid 403 Forbidden
                }
                lowerUrl.contains("vimeo") || lowerUrl.contains("vimeocdn") -> {
                    request.addOption("--add-header", "Referer: https://vimeo.com/")
                    request.addOption("--add-header", "Origin: https://vimeo.com")
                    domainHeaders["Referer"] = "https://vimeo.com/"
                    domainHeaders["Origin"] = "https://vimeo.com"
                }
                lowerUrl.contains("dailymotion.com") || lowerUrl.contains("dai.ly") -> {
                    request.addOption("--add-header", "Referer: https://www.dailymotion.com/")
                    domainHeaders["Referer"] = "https://www.dailymotion.com/"
                }
                lowerUrl.contains("archive.org") -> {
                    request.addOption("--add-header", "Referer: https://archive.org/")
                    domainHeaders["Referer"] = "https://archive.org/"
                }
                lowerUrl.contains("bilibili") || lowerUrl.contains("b23.tv") -> {
                    request.addOption("--add-header", "Referer: https://www.bilibili.com/")
                    domainHeaders["Referer"] = "https://www.bilibili.com/"
                }
                lowerUrl.contains("pornhub.com") || lowerUrl.contains("phncdn.com") -> {
                    request.addOption("--add-header", "Referer: https://www.pornhub.com/")
                    request.addOption("--add-header", "Cookie: age_verified=1; platform=pc; accessAgeDisclaimerPH=1; ip_country=US; has_consent=1; expired_cookies=1; il=en")
                    request.addOption("--add-header", "X-Forwarded-For: 208.80.154.224")
                    request.addOption("--geo-bypass")
                    request.addOption("--geo-bypass-country", "US")
                    domainHeaders["Referer"] = "https://www.pornhub.com/"
                    domainHeaders["Cookie"] = "age_verified=1; platform=pc; accessAgeDisclaimerPH=1; ip_country=US; has_consent=1; expired_cookies=1; il=en"
                    domainHeaders["X-Forwarded-For"] = "208.80.154.224"
                }
                lowerUrl.contains("xvideos.com") -> {
                    request.addOption("--add-header", "Referer: https://www.xvideos.com/")
                    domainHeaders["Referer"] = "https://www.xvideos.com/"
                }
                lowerUrl.contains("4tube.com") -> {
                    request.addOption("--add-header", "Referer: https://www.4tube.com/")
                    domainHeaders["Referer"] = "https://www.4tube.com/"
                }
                lowerUrl.contains("beeg.com") -> {
                    request.addOption("--add-header", "Referer: https://beeg.com/")
                    domainHeaders["Referer"] = "https://beeg.com/"
                }
                lowerUrl.contains("rule34video.com") -> {
                    request.addOption("--add-header", "Referer: https://rule34video.com/")
                    domainHeaders["Referer"] = "https://rule34video.com/"
                }
                lowerUrl.contains("redtube.com") -> {
                    request.addOption("--add-header", "Referer: https://www.redtube.com/")
                    domainHeaders["Referer"] = "https://www.redtube.com/"
                }
                lowerUrl.contains("xhamster.com") -> {
                    request.addOption("--add-header", "Referer: https://xhamster.com/")
                    domainHeaders["Referer"] = "https://xhamster.com/"
                }
                lowerUrl.contains("youporn.com") -> {
                    request.addOption("--add-header", "Referer: https://www.youporn.com/")
                    domainHeaders["Referer"] = "https://www.youporn.com/"
                }
                lowerUrl.contains("eporner.com") -> {
                    request.addOption("--add-header", "Referer: https://www.eporner.com/")
                    domainHeaders["Referer"] = "https://www.eporner.com/"
                }
                lowerUrl.contains("hotstar.com") || lowerUrl.contains("jiohotstar.com") -> {
                    request.addOption("--add-header", "Referer: https://www.hotstar.com/")
                    request.addOption("--add-header", "Origin: https://www.hotstar.com")
                    request.addOption("--extractor-args", "hotstar:vcodec=h264")
                    request.addOption("--geo-bypass")
                    domainHeaders["Referer"] = "https://www.hotstar.com/"
                    domainHeaders["Origin"] = "https://www.hotstar.com"
                }
                lowerUrl.contains("tiktok.com") -> {
                    request.addOption("--add-header", "Referer: https://www.tiktok.com/")
                    domainHeaders["Referer"] = "https://www.tiktok.com/"
                }
                lowerUrl.contains("twitch.tv") -> {
                    request.addOption("--add-header", "Referer: https://www.twitch.tv/")
                    domainHeaders["Referer"] = "https://www.twitch.tv/"
                }
            }

            ensureInitialized(ctx)
            val response: YtDlpResponse = processSemaphore.withPermit {
                YtDlp.execute(request, null)
            }
            val jsonStr = response.output
            if (jsonStr.isBlank()) {
                throw IllegalStateException("yt-dlp returned empty JSON output")
            }

            val json = JSONObject(jsonStr)
            val videoId = json.optString("id", targetUrl)
            val title = json.optString("title", "Video")
            val uploader = json.optString("uploader", json.optString("channel", json.optString("extractor", "Online Video")))
            val description = json.optString("description", "")
            val thumbnail = json.optString("thumbnail", "")
            val channelAvatar = json.optString("channel_avatar", json.optString("uploader_avatar", json.optString("avatar", ""))).ifBlank {
                var foundAvatar = ""
                val thumbs = json.optJSONArray("thumbnails")
                if (thumbs != null) {
                    for (t in 0 until thumbs.length()) {
                        val thumbObj = thumbs.optJSONObject(t) ?: continue
                        val idStr = thumbObj.optString("id", "")
                        val u = thumbObj.optString("url", "")
                        if (idStr.contains("avatar", ignoreCase = true) && u.isNotBlank()) {
                            foundAvatar = u
                            break
                        }
                    }
                }
                foundAvatar
            }.ifBlank { null }

            // Parse top-level http_headers
            val topHeaders = mutableMapOf<String, String>()
            topHeaders.putAll(domainHeaders)
            val jsonTopHeaders = json.optJSONObject("http_headers")
            if (jsonTopHeaders != null) {
                val keys = jsonTopHeaders.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = jsonTopHeaders.optString(k, "")
                    if (k.isNotBlank() && v.isNotBlank()) {
                        topHeaders[k] = v
                    }
                }
            }

            // Parse all formats returned by yt-dlp
            val parsedFormats = mutableListOf<ParsedFormat>()
            val formatsArray = json.optJSONArray("formats")
            if (formatsArray != null) {
                for (i in 0 until formatsArray.length()) {
                    val fmt = formatsArray.optJSONObject(i) ?: continue
                    val streamUrl = fmt.optString("url", "").trim()
                    if (streamUrl.isBlank() || (!streamUrl.startsWith("http://", ignoreCase = true) && !streamUrl.startsWith("https://", ignoreCase = true))) continue
                    if (streamUrl.endsWith(".html", ignoreCase = true) || streamUrl.endsWith(".htm", ignoreCase = true) || streamUrl.contains("/error_403", ignoreCase = true) || streamUrl.contains("/error_404", ignoreCase = true)) continue

                    val formatId = fmt.optString("format_id", "")
                    val ext = fmt.optString("ext", "mp4")
                    val res = fmt.optString("resolution", "")
                    val width = fmt.optInt("width", 0)
                    val height = fmt.optInt("height", 0)
                    val fps = fmt.optDouble("fps", 30.0)
                    val tbr = fmt.optDouble("tbr", 0.0)
                    val vbr = fmt.optDouble("vbr", 0.0)
                    val abr = fmt.optDouble("abr", 0.0)
                    val vcodec = fmt.optString("vcodec", "none")
                    val acodec = fmt.optString("acodec", "none")
                    val note = fmt.optString("format_note", "")
                    val protocol = fmt.optString("protocol", "https")

                    if (note.contains("drm", ignoreCase = true) || note.contains("encrypted", ignoreCase = true) || vcodec.contains("drm", ignoreCase = true)) {
                        continue
                    }

                    val fmtHeaders = mutableMapOf<String, String>()
                    fmtHeaders.putAll(topHeaders)
                    val jsonFmtHeaders = fmt.optJSONObject("http_headers")
                    if (jsonFmtHeaders != null) {
                        val hKeys = jsonFmtHeaders.keys()
                        while (hKeys.hasNext()) {
                            val hk = hKeys.next()
                            val hv = jsonFmtHeaders.optString(hk, "")
                            if (hk.isNotBlank() && hv.isNotBlank()) {
                                fmtHeaders[hk] = hv
                            }
                        }
                    }
                    if (lowerUrl.contains("bilibili") || lowerUrl.contains("b23.tv")) {
                        fmtHeaders.remove("Origin")
                        fmtHeaders.remove("origin")
                        fmtHeaders["Referer"] = "https://www.bilibili.com/"
                    }

                    parsedFormats.add(
                        ParsedFormat(
                            formatId = formatId,
                            url = streamUrl,
                            ext = ext,
                            resolution = res,
                            width = width,
                            height = height,
                            fps = fps,
                            tbr = tbr,
                            vbr = vbr,
                            abr = abr,
                            vcodec = vcodec,
                            acodec = acodec,
                            formatNote = note,
                            protocol = protocol,
                            httpHeaders = fmtHeaders
                        )
                    )
                }
            }

            // Find best audio format for adaptive muxing
            val bestAudio = parsedFormats
                .filter { it.isAudioOnly && it.url.isNotBlank() }
                .sortedWith(
                    compareByDescending<ParsedFormat> { it.abr }
                        .thenByDescending { if (it.ext == "m4a" || it.acodec.contains("mp4a")) 2 else 1 }
                )
                .firstOrNull()

            val streamOptions = mutableListOf<PlayableStreamOption>()

            // 1. Muxed Video + Audio Progressive streams (H.264 / MP4 preferred)
            val muxedFormats = parsedFormats.filter { it.isMuxed && it.url.isNotBlank() }
                .sortedByDescending { it.qualityScore }

            for (fmt in muxedFormats) {
                val h = fmt.effectiveHeight
                val fpsStr = if (fmt.fps > 30.0) "${fmt.fps.toInt()}fps" else ""
                val codecStr = friendlyCodec(fmt.vcodec)
                val label = "${h}p $fpsStr Progressive (${fmt.ext} - $codecStr)".replace("  ", " ").trim()

                streamOptions.add(
                    PlayableStreamOption(
                        qualityLabel = label,
                        format = fmt.ext,
                        isMuxed = true,
                        videoUrl = fmt.url,
                        providerType = ProviderType.DIRECT,
                        headers = fmt.httpHeaders
                    )
                )
            }

            // 2. Adaptive Video Streams (paired with best audio track)
            val videoOnlyFormats = parsedFormats.filter { it.isVideoOnly && it.url.isNotBlank() }
                .sortedByDescending { it.qualityScore }

            for (fmt in videoOnlyFormats) {
                val h = fmt.effectiveHeight
                val fpsStr = if (fmt.fps > 30.0) "${fmt.fps.toInt()}fps" else ""
                val codecStr = friendlyCodec(fmt.vcodec)
                val label = "${h}p $fpsStr Adaptive (${fmt.ext} - $codecStr)".replace("  ", " ").trim()

                streamOptions.add(
                    PlayableStreamOption(
                        qualityLabel = label,
                        format = fmt.ext,
                        isMuxed = bestAudio == null,
                        videoUrl = fmt.url,
                        audioUrl = bestAudio?.url,
                        providerType = ProviderType.DIRECT,
                        headers = fmt.httpHeaders,
                        audioHeaders = bestAudio?.httpHeaders ?: emptyMap()
                    )
                )
            }

            // 3. Manifests (HLS / DASH)
            val manifestFormats = parsedFormats.filter { it.isHlsOrDash && it.url.isNotBlank() }
            for (fmt in manifestFormats) {
                val manifestType = if (fmt.ext.equals("m3u8", ignoreCase = true) || fmt.protocol.contains("m3u8")) "HLS (m3u8)" else "DASH (mpd)"
                streamOptions.add(
                    PlayableStreamOption(
                        qualityLabel = "Adaptive Stream $manifestType",
                        format = fmt.ext,
                        isMuxed = true,
                        videoUrl = fmt.url,
                        providerType = ProviderType.DIRECT,
                        headers = fmt.httpHeaders
                    )
                )
            }

            // 4. Direct fallback URL from top-level JSON
            val topDirectUrl = json.optString("url", "")
            if (topDirectUrl.isNotBlank() && streamOptions.none { it.videoUrl == topDirectUrl }) {
                streamOptions.add(
                    PlayableStreamOption(
                        qualityLabel = "yt-dlp Direct Stream",
                        format = "mp4",
                        isMuxed = true,
                        videoUrl = topDirectUrl,
                        providerType = ProviderType.DIRECT,
                        headers = topHeaders
                    )
                )
            }

            if (streamOptions.isEmpty()) {
                return@withContext YouTubeExtractorHelper.ExtractionResult.Error(
                    ExtractorErrorDetails(
                        errorType = ExtractorErrorType.NO_PLAYABLE_STREAMS,
                        message = "No playable formats found via yt-dlp",
                        rawExceptionName = "NoStreamsException",
                        fullStackTrace = "",
                        urlOrId = targetUrl,
                        causeInfo = "yt-dlp returned no stream URLs",
                        technicalFixSuggestion = "Check URL or video permissions."
                    )
                )
            }

            // Deduplicate options by quality label
            val distinctOptions = streamOptions.distinctBy { it.qualityLabel }

            // Select best option: First prefer muxed 1080p/720p H.264/MP4, then any muxed, then adaptive 1080p/720p
            val bestOption = distinctOptions.firstOrNull { it.isMuxed && it.qualityLabel.startsWith("1080p") }
                ?: distinctOptions.firstOrNull { it.isMuxed && it.qualityLabel.startsWith("720p") }
                ?: distinctOptions.firstOrNull { it.isMuxed }
                ?: distinctOptions.firstOrNull { it.qualityLabel.startsWith("1080p") }
                ?: distinctOptions.firstOrNull { it.qualityLabel.startsWith("720p") }
                ?: distinctOptions.first()

            val providerId = if (isYouTube) "youtube" else json.optString("extractor_key", "generic").lowercase()
            val streamData = StreamData(
                videoId = videoId,
                videoUrl = bestOption.videoUrl ?: "",
                title = title,
                channelName = uploader,
                channelAvatarUrl = channelAvatar,
                description = description,
                thumbnailUrl = thumbnail,
                availableStreamOptions = distinctOptions,
                selectedStreamOption = bestOption,
                providerId = providerId,
                providerType = ProviderType.DIRECT,
                headers = bestOption.headers
            )

            Log.i(TAG, "yt-dlp success: found ${distinctOptions.size} streams, selected '${bestOption.qualityLabel}'")
            YouTubeExtractorHelper.ExtractionResult.Success(streamData)
        } catch (e: Throwable) {
            Log.e(TAG, "yt-dlp execution failed: ${e.message}", e)
            YouTubeExtractorHelper.ExtractionResult.Error(
                ExtractorErrorDetails(
                    errorType = ExtractorErrorType.NETWORK_ERROR,
                    message = e.message ?: "yt-dlp extraction failed",
                    rawExceptionName = e.javaClass.simpleName,
                    fullStackTrace = e.stackTraceToString(),
                    urlOrId = targetUrl,
                    causeInfo = e.message,
                    technicalFixSuggestion = "Check internet connection or retry."
                )
            )
        }
    }

    private fun friendlyCodec(codec: String): String {
        val lower = codec.lowercase()
        return when {
            lower.startsWith("avc") || lower.startsWith("h264") -> "H.264"
            lower.startsWith("h265") || lower.startsWith("hevc") || lower.startsWith("hev") -> "HEVC"
            lower.startsWith("vp09") || lower.startsWith("vp9") -> "VP9"
            lower.startsWith("av01") || lower.startsWith("av1") -> "AV1"
            codec.isBlank() || codec == "none" -> "Default"
            else -> codec
        }
    }

    suspend fun fetchTrending(ctx: Context, limit: Int = 25): List<VideoItem> = withContext(Dispatchers.IO) {
        YouTubeExtractorHelper.fetchYouTubeTrending(ctx)
    }

    suspend fun search(ctx: Context, query: String, limit: Int = 25, providerId: String = "youtube"): List<VideoItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val pid = providerId.lowercase()
        if (pid == "youtube") {
            YouTubeExtractorHelper.searchYouTube(query, ctx)
        } else {
            MultiSourceProvider.search(ctx, pid, query, limit)
        }
    }
}
