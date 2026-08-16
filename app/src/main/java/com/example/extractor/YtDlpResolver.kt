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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object YtDlpResolver {

    private const val TAG = "YtDlpResolver"

    @Volatile
    private var isInitialized = false

    fun init(context: Context) {
        if (!isInitialized) {
            synchronized(this) {
                if (!isInitialized) {
                    try {
                        val appCtx = context.applicationContext
                        YoutubeDL.getInstance().init(appCtx)
                        try {
                            FFmpeg.getInstance().init(appCtx)
                        } catch (fe: Throwable) {
                            Log.w(TAG, "FFmpeg init warning: ${fe.message}")
                        }
                        // Use bundled release without blocking network update on init
                        isInitialized = true
                        Log.d(TAG, "YoutubeDL initialized successfully")
                    } catch (e: Throwable) {
                        Log.e(TAG, "Failed to initialize YoutubeDL", e)
                    }
                }
            }
        }
    }

    fun prewarm(context: Context) {
        init(context)
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Pre-warming yt-dlp execution engine, QuickJS, and extractor threads...")
                locateQuickJsRuntime(context)
                YouTubeExtractorHelper.ensureInitialized()
                Log.d(TAG, "yt-dlp engine fully primed at app launch (0 cold-start delay)")
            } catch (e: Throwable) {
                Log.w(TAG, "Pre-warm exception: ${e.message}")
            }
        }
    }

    fun isYouTubeUrl(url: String): Boolean {
        val u = url.lowercase().trim()
        return u.contains("youtube.com") || u.contains("youtu.be") || u.contains("youtube-nocookie.com")
    }

    fun isYtDlpSupportedUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return false

        if (isYouTubeUrl(trimmed)) return true

        // Raw 11-char YouTube ID
        if (Regex("^[a-zA-Z0-9_-]{11}$").matches(trimmed)) return true

        // Check video hosts supported by yt-dlp & integrated plugins
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            val u = trimmed.lowercase()
            if (u.contains("eporner.com") || u.contains("archive.org")) return false
            return u.contains("adultswim.com") || u.contains("dailymotion.com") || u.contains("dai.ly") ||
                    u.contains("vimeo.com") || u.contains("tiktok.com") ||
                    u.contains("twitter.com") || u.contains("x.com") ||
                    u.contains("pornhub.com") || u.contains("xhamster.com") || u.contains("redtube.com") || u.contains("xvideos.com") ||
                    u.contains("peer.tube") || u.contains("nicovideo.jp") || u.contains("twitch.tv") ||
                    u.contains("coomer.su") || u.contains("kemono.su") || u.contains("pmvhaven.com") ||
                    u.contains("aniwatchtv.to") || u.contains("aniwatch.to") || u.contains("kaido.to") ||
                    u.contains("hianime.to") || u.contains("hanime.tv") || u.contains("bilibili.com") ||
                    u.contains("closedport") || u.contains("streamhub") || u.contains("vidspeed") || u.contains("filelions") ||
                    u.contains("91porn") || u.contains("jable") || u.contains("missav") ||
                    u.contains("uncensoredjav") || u.contains("javuncensored") || u.contains("7mmtv") || u.contains("supjav")
        }

        return false
    }

    private fun preparePlugins(context: Context): File? {
        try {
            val pluginDir = File(context.filesDir, "yt_plugins")
            val extractorDir = File(pluginDir, "yt_dlp_plugins/extractor")
            if (!extractorDir.exists()) {
                extractorDir.mkdirs()
            }
            val assetManager = context.assets
            val assetPath = "yt_plugins/yt_dlp_plugins/extractor"
            val files = assetManager.list(assetPath) ?: emptyArray()
            for (fileName in files) {
                val outFile = File(extractorDir, fileName)
                if (!outFile.exists() || outFile.length() == 0L) {
                    assetManager.open("$assetPath/$fileName").use { input ->
                        outFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
            return pluginDir
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to prepare yt-dlp plugin files: ${e.message}")
            return null
        }
    }

    fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return input

        // Handle YouTube 11-char ID
        if (Regex("^[a-zA-Z0-9_-]{11}$").matches(trimmed)) {
            return "https://www.youtube.com/watch?v=$trimmed"
        }

        // Standardize URLs
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            if (isYouTubeUrl(trimmed)) {
                return "https://www.youtube.com/watch?v=$trimmed"
            }
            return "https://$trimmed"
        }

        return trimmed
    }

    fun locateQuickJsRuntime(context: Context): String? {
        try {
            val candidates = listOf(
                File(context.filesDir, "quickjs"),
                File(context.filesDir, "qjs"),
                File(context.cacheDir, "quickjs"),
                File(context.cacheDir, "qjs"),
                File(context.applicationInfo.nativeLibraryDir, "libquickjs.so"),
                File(context.applicationInfo.nativeLibraryDir, "libqjs.so"),
                File("/system/bin/quickjs"),
                File("/system/bin/qjs"),
                File("/system/xbin/quickjs"),
                File("/data/local/tmp/quickjs"),
                File("/data/local/tmp/qjs")
            )

            for (file in candidates) {
                if (file.exists()) {
                    if (!file.canExecute()) {
                        try {
                            file.setExecutable(true, false)
                        } catch (_: Throwable) {}
                    }
                    if (file.canExecute() || file.name.endsWith(".so")) {
                        Log.d(TAG, "Located usable QuickJS JS runtime at: ${file.absolutePath}")
                        return file.absolutePath
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error locating QuickJS runtime: ${e.message}")
        }
        return null
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

    @Volatile
    private var customProxyUrl: String? = null

    fun setProxy(proxyUrl: String?) {
        this.customProxyUrl = proxyUrl
    }

    fun getProxy(): String? = customProxyUrl ?: System.getProperty("yt_dlp_proxy") ?: System.getProperty("adultswim_proxy")

    suspend fun extractStreamInfo(
        context: Context,
        urlOrId: String
    ): ExtractionResult = withContext(Dispatchers.IO) {
        init(context)

        if (!isInitialized) {
            Log.w(TAG, "extractStreamInfo skipped because YoutubeDL is not initialized")
            return@withContext ExtractionResult.Error(
                errorType = ExtractorErrorType.UNKNOWN,
                message = "yt-dlp engine unavailable on this device",
                causeMessage = "YoutubeDL initialization failed or native library missing"
            )
        }

        val fullUrl = normalizeUrl(urlOrId)
        val isYouTube = isYouTubeUrl(fullUrl)
        val isAdultSwim = fullUrl.contains("adultswim.com", ignoreCase = true)
        val isHotstar = fullUrl.contains("hotstar.com", ignoreCase = true)

        Log.d(TAG, "Starting yt-dlp primary extraction for: $fullUrl (youtube=$isYouTube, adultswim=$isAdultSwim, hotstar=$isHotstar)")

        try {
            val request = YoutubeDLRequest(fullUrl)

            // Pass plugin directory to yt-dlp to load embedded plugin extractors
            val pluginDir = preparePlugins(context)
            if (pluginDir != null && pluginDir.exists()) {
                request.addOption("--plugin-dirs", pluginDir.absolutePath)
            }

            // Format selection: Allow yt-dlp to choose best available direct streams
            request.addOption("-f", "b/bestvideo+bestaudio/best")
            request.addOption("--no-check-certificates")
            request.addOption("--geo-bypass")
            request.addOption("--geo-bypass-country", "IN")
            request.addOption("--geo-bypass-headers")
            request.addOption("--no-playlist")
            request.addOption("--socket-timeout", "8")
            request.addOption("--no-warnings")
            request.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")

            val activeProxy = getProxy()
            if (!activeProxy.isNullOrBlank()) {
                Log.d(TAG, "Routing yt-dlp request through proxy: $activeProxy")
                request.addOption("--proxy", activeProxy)
            } else if (isAdultSwim) {
                request.addOption("--add-header", "Referer:https://www.adultswim.com/")
                request.addOption("--add-header", "Origin:https://www.adultswim.com")
            } else if (isHotstar) {
                request.addOption("--add-header", "Referer:https://www.hotstar.com/")
                request.addOption("--add-header", "Origin:https://www.hotstar.com")
            } else if (fullUrl.contains("vimeo.com", ignoreCase = true)) {
                request.addOption("--add-header", "Referer:https://vimeo.com/")
                request.addOption("--add-header", "Origin:https://vimeo.com")
            } else if (fullUrl.contains("tiktok.com", ignoreCase = true)) {
                request.addOption("--add-header", "Referer:https://www.tiktok.com/")
                request.addOption("--geo-bypass")
                request.addOption("--geo-bypass-country", "US")
            } else if (fullUrl.contains("twitch.tv", ignoreCase = true)) {
                request.addOption("--add-header", "Referer:https://www.twitch.tv/")
                request.addOption("--add-header", "Origin:https://www.twitch.tv")
            } else if (fullUrl.contains("bilibili.com", ignoreCase = true)) {
                request.addOption("--add-header", "Referer:https://www.bilibili.com/")
            } else if (fullUrl.contains("instagram.com", ignoreCase = true)) {
                request.addOption("--add-header", "Referer:https://www.instagram.com/")
            } else if (fullUrl.contains("vk.com", ignoreCase = true)) {
                request.addOption("--add-header", "Referer:https://vk.com/")
            } else if (fullUrl.contains("rutube.ru", ignoreCase = true)) {
                request.addOption("--add-header", "Referer:https://rutube.ru/")
            }

            // PO-Token mechanism integration if available (Do NOT hardcode old player_client versions)
            val extractorArgs = mutableListOf<String>()
            val poTokenProvider = YouTubeExtractorHelper.getPoTokenProvider()
            val poToken = poTokenProvider?.getPoToken(null)
            if (!poToken.isNullOrBlank() && isYouTube) {
                Log.d(TAG, "Injecting available PO-Token into yt-dlp extractor args")
                extractorArgs.add("po_token=web+$poToken")
            }

            if (isYouTube && extractorArgs.isNotEmpty()) {
                request.addOption("--extractor-args", "youtube:" + extractorArgs.joinToString(";"))
            }

            // Configure QuickJS Runtime for EJS / JS challenge solving
            val quickJsPath = locateQuickJsRuntime(context)
            if (quickJsPath != null) {
                Log.d(TAG, "Configuring yt-dlp JS runtime: quickjs:$quickJsPath")
                request.addOption("--js-runtimes", "quickjs:$quickJsPath")
            } else {
                Log.d(TAG, "QuickJS binary path not found, letting yt-dlp use default environment JS runtime")
            }

            // Execute yt-dlp with 45s timeout for high-reliability on-device extraction
            val videoInfo: VideoInfo = kotlinx.coroutines.withTimeoutOrNull(45000L) {
                YoutubeDL.getInstance().getInfo(request)
            } ?: throw YoutubeDLException("yt-dlp extraction timed out after 45s")

            val title = videoInfo.title ?: "Extracted Video"
            val uploader = videoInfo.uploader ?: if (isYouTube) "YouTube Channel" else "Web Creator"
            val thumbnail = videoInfo.thumbnail

            // Extract HTTP headers
            val extractedHeaders = mutableMapOf<String, String>()
            videoInfo.httpHeaders?.forEach { (k, v) ->
                if (!v.isNullOrBlank()) {
                    extractedHeaders[k] = v
                }
            }

            val options = mutableListOf<PlayableStreamOption>()

            // First check formats
            val formats = videoInfo.formats
            if (!formats.isNullOrEmpty()) {
                // Find muxed formats (video + audio)
                val muxedFormats = formats.filter {
                    !it.url.isNullOrEmpty() &&
                            (it.vcodec != "none" && it.vcodec != null && it.vcodec != "null") &&
                            (it.acodec != "none" && it.acodec != null && it.acodec != "null")
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

                // Build adaptive streams for all available resolutions (1080p, 1440p, 4K, 720p, etc.)
                val videoFmts = formats.filter { !it.url.isNullOrEmpty() && it.vcodec != "none" && it.vcodec != null && it.vcodec != "null" }
                    .distinctBy { it.height }
                    .sortedByDescending { it.height }
                val audioFmts = formats.filter { !it.url.isNullOrEmpty() && it.acodec != "none" && it.acodec != null && it.acodec != "null" }
                val bestAudio = audioFmts.maxByOrNull { it.tbr } ?: audioFmts.firstOrNull()

                for (vf in videoFmts) {
                    val fmtHeaders = extractedHeaders.toMutableMap()
                    vf.httpHeaders?.forEach { (k, v) -> if (!v.isNullOrBlank()) fmtHeaders[k] = v }
                    val h = vf.height
                    val label = when {
                        h >= 2160 -> "2160p (4K)"
                        h >= 1440 -> "1440p (2K)"
                        h >= 1080 -> "1080p HD"
                        h >= 720 -> "720p HD"
                        h > 0 -> "${h}p"
                        else -> "Adaptive Quality"
                    }

                    options.add(
                        PlayableStreamOption(
                            qualityLabel = label,
                            format = vf.ext ?: "mp4",
                            isMuxed = bestAudio == null,
                            videoUrl = vf.url,
                            audioUrl = bestAudio?.url,
                            headers = fmtHeaders,
                            providerType = ProviderType.OTHER
                        )
                    )
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

            val selectedOpt = options.firstOrNull { it.qualityLabel.contains("1080p") }
                ?: options.firstOrNull { it.qualityLabel.contains("720p") }
                ?: options.first()

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
                providerId = if (isYouTube) "youtube" else "ytdlp"
            )

            Log.d(TAG, "yt-dlp Primary Extraction SUCCESS: title='$title', options=${options.size}, headers=${extractedHeaders.size}")

            ExtractionResult.Success(
                streamData = streamData,
                playableOptions = options
            )

        } catch (e: YoutubeDLException) {
            val msg = e.localizedMessage ?: "yt-dlp extraction failed"
            Log.e(TAG, "YoutubeDLException: $msg", e)

            val isIpOrBotGuardBlocked = msg.contains("Sign in to confirm you", ignoreCase = true) ||
                    msg.contains("LOGIN_REQUIRED", ignoreCase = true) ||
                    (msg.contains("Sign in", ignoreCase = true) && msg.contains("bot", ignoreCase = true))

            val errorType = when {
                isIpOrBotGuardBlocked -> ExtractorErrorType.YOUTUBE_IP_BLOCKED
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
                    ExtractorErrorType.YOUTUBE_IP_BLOCKED -> "YouTube IP / BotGuard Blocked: Sign in to confirm you're not a bot."
                    ExtractorErrorType.GEO_RESTRICTED -> "This video is geo-restricted or unavailable in your region."
                    ExtractorErrorType.UNAVAILABLE -> "Video unavailable, private, or has been deleted."
                    ExtractorErrorType.AGE_RESTRICTED -> "This video is age-restricted or requires authentication."
                    else -> "Failed to extract stream: $msg"
                },
                causeMessage = msg
            )
        } catch (e: Throwable) {
            val msg = e.localizedMessage ?: "Unknown extraction error"
            Log.e(TAG, "Throwable during yt-dlp extraction: $msg", e)
            ExtractionResult.Error(
                errorType = ExtractorErrorType.UNKNOWN,
                message = "Extraction failed: $msg",
                causeMessage = e.stackTraceToString()
            )
        }
    }
}
