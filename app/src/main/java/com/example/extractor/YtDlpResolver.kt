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

    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    fun prewarm(context: Context) {
        try {
            YoutubeDL.getInstance().init(context)
        } catch (e: Throwable) {
            Log.w(TAG, "YoutubeDL prewarm init note: ${e.message}")
        }
    }

    fun isYtDlpSupportedUrl(url: String): Boolean {
        val u = url.lowercase().trim()
        return u.startsWith("http://") || u.startsWith("https://") ||
                u.contains("youtube.com") || u.contains("youtu.be") ||
                u.contains("vimeo.com") || u.contains("dailymotion.com") ||
                u.contains("bilibili.com") || u.contains("tiktok.com") ||
                u.contains("twitch.tv") || u.contains("soundcloud.com") ||
                (url.length == 11 && !url.contains("/"))
    }

    private data class ParsedFormat(
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
        val protocol: String
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
                if (vcodec.startsWith("avc", ignoreCase = true) || vcodec.startsWith("h264", ignoreCase = true)) {
                    score += 50_000L // Highly compatible hardware decode
                } else if (vcodec.startsWith("vp09", ignoreCase = true) || vcodec.startsWith("vp9", ignoreCase = true)) {
                    score += 30_000L
                } else if (vcodec.startsWith("av01", ignoreCase = true) || vcodec.startsWith("av1", ignoreCase = true)) {
                    score += 20_000L
                }
                if (ext.equals("mp4", ignoreCase = true)) {
                    score += 10_000L
                }
                if (isMuxed) {
                    score += 5_000L
                }
                score += tbr.toLong()
                return score
            }
    }

    suspend fun extractStreamInfo(ctx: Context, targetUrl: String): YouTubeExtractorHelper.ExtractionResult = withContext(Dispatchers.IO) {
        try {
            val isYouTube = targetUrl.contains("youtube.com") || targetUrl.contains("youtu.be") || (targetUrl.length == 11 && !targetUrl.startsWith("http"))
            val videoUrl = when {
                targetUrl.startsWith("http") -> targetUrl
                targetUrl.length == 11 -> "https://www.youtube.com/watch?v=$targetUrl"
                else -> targetUrl
            }

            Log.i(TAG, "Executing yt-dlp stream extraction for: $videoUrl")

            try {
                YoutubeDL.getInstance().init(ctx)
            } catch (e: Throwable) {
                Log.w(TAG, "YoutubeDL init note: ${e.message}")
            }

            val request = YoutubeDLRequest(videoUrl)
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
                    request.addOption("--add-header", "Referer: https://www.youtube.com/")
                    request.addOption("--add-header", "Origin: https://www.youtube.com")
                    domainHeaders["Referer"] = "https://www.youtube.com/"
                    domainHeaders["Origin"] = "https://www.youtube.com"
                }
                lowerUrl.contains("vimeo.com") -> {
                    request.addOption("--add-header", "Referer: https://vimeo.com/")
                    domainHeaders["Referer"] = "https://vimeo.com/"
                }
                lowerUrl.contains("dailymotion.com") -> {
                    request.addOption("--add-header", "Referer: https://www.dailymotion.com/")
                    domainHeaders["Referer"] = "https://www.dailymotion.com/"
                }
                lowerUrl.contains("bilibili.com") -> {
                    request.addOption("--add-header", "Referer: https://www.bilibili.com/")
                    domainHeaders["Referer"] = "https://www.bilibili.com/"
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

            val response: YoutubeDLResponse = YoutubeDL.getInstance().execute(request)
            val jsonStr = response.out
            if (jsonStr.isBlank()) {
                throw IllegalStateException("yt-dlp returned empty JSON output")
            }

            val json = JSONObject(jsonStr)
            val videoId = json.optString("id", targetUrl)
            val title = json.optString("title", "Video")
            val uploader = json.optString("uploader", json.optString("channel", json.optString("extractor", "Online Video")))
            val description = json.optString("description", "")
            val thumbnail = json.optString("thumbnail", "")

            // Parse all formats returned by yt-dlp
            val parsedFormats = mutableListOf<ParsedFormat>()
            val formatsArray = json.optJSONArray("formats")
            if (formatsArray != null) {
                for (i in 0 until formatsArray.length()) {
                    val fmt = formatsArray.optJSONObject(i) ?: continue
                    val streamUrl = fmt.optString("url", "")
                    if (streamUrl.isBlank()) continue

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
                            protocol = protocol
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

            // 1. Muxed Video + Audio Progressive streams
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
                        headers = domainHeaders
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
                        headers = domainHeaders
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
                        headers = domainHeaders
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
                        headers = domainHeaders
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

            // Select best option: Prefer 1080p, then 720p, or highest quality available
            val bestOption = distinctOptions.firstOrNull { it.qualityLabel.startsWith("1080p") }
                ?: distinctOptions.firstOrNull { it.qualityLabel.startsWith("720p") }
                ?: distinctOptions.first()

            val providerId = if (isYouTube) "youtube" else json.optString("extractor_key", "generic").lowercase()
            val streamData = StreamData(
                videoId = videoId,
                videoUrl = bestOption.videoUrl ?: "",
                title = title,
                channelName = uploader,
                description = description,
                thumbnailUrl = thumbnail,
                availableStreamOptions = distinctOptions,
                selectedStreamOption = bestOption,
                providerId = providerId,
                providerType = ProviderType.DIRECT,
                headers = domainHeaders
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
        val primary = search(ctx, "trending videos", limit)
        if (primary.isNotEmpty()) return@withContext primary

        val fallback = search(ctx, "popular music videos", limit)
        if (fallback.isNotEmpty()) return@withContext fallback

        search(ctx, "top news", limit)
    }

    suspend fun search(ctx: Context, query: String, limit: Int = 25): List<VideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<VideoItem>()
        if (query.isBlank()) return@withContext list
        try {
            try {
                YoutubeDL.getInstance().init(ctx)
            } catch (e: Throwable) {
                Log.w(TAG, "YoutubeDL init note: ${e.message}")
            }

            val searchTarget = if (query.startsWith("http://") || query.startsWith("https://")) {
                query
            } else {
                "ytsearch$limit:$query"
            }

            val request = YoutubeDLRequest(searchTarget)
            request.addOption("--dump-json")
            request.addOption("--flat-playlist")
            request.addOption("--no-warnings")
            request.addOption("--ignore-errors")
            request.addOption("--user-agent", DEFAULT_USER_AGENT)

            val response: YoutubeDLResponse = YoutubeDL.getInstance().execute(request)
            val output = response.out
            if (output.isNotBlank()) {
                val lines = output.lines()
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                        try {
                            val json = JSONObject(trimmed)
                            val id = json.optString("id", "")
                            val title = json.optString("title", "")
                            if (id.isNotBlank() && title.isNotBlank()) {
                                val uploader = json.optString("uploader", json.optString("channel", "YouTube"))
                                val duration = json.optLong("duration", -1L)
                                val viewCount = json.optLong("view_count", -1L)
                                val thumb = json.optString("thumbnail", "https://i.ytimg.com/vi/$id/hqdefault.jpg")
                                list.add(
                                    VideoItem(
                                        id = id,
                                        title = title,
                                        uploaderName = uploader,
                                        durationSeconds = duration,
                                        viewCount = viewCount,
                                        thumbnailUrl = thumb,
                                        providerId = "youtube"
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed parsing yt-dlp item: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "yt-dlp search failed for '$query': ${e.message}")
        }
        list
    }
}
