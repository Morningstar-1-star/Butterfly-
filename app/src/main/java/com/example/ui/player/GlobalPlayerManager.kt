package com.example.ui.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.model.CaptionOption
import com.example.model.PlayableStreamOption
import com.example.model.StreamData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

object GlobalPlayerManager {

    private var exoPlayerInstance: ExoPlayer? = null
    private var scope = CoroutineScope(Dispatchers.Main)
    private var progressTrackerJob: Job? = null

    private val okHttpClient = OkHttpClient.Builder()
        .connectionPool(okhttp3.ConnectionPool(32, 5, java.util.concurrent.TimeUnit.MINUTES))
        .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
        .connectTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            var request = chain.request()
            val urlStr = request.url.toString().lowercase()
            val builder = request.newBuilder()

            // Default Desktop Chrome User-Agent if missing or generic okhttp
            val existingUa = request.header("User-Agent")
            if (existingUa.isNullOrBlank() || existingUa.startsWith("okhttp", ignoreCase = true)) {
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            }

            when {
                urlStr.contains("youtube.com") || urlStr.contains("googlevideo.com") || urlStr.contains("youtu.be") -> {
                    if (request.header("Referer") == null) builder.header("Referer", "https://www.youtube.com/")
                    if (request.header("Origin") == null) builder.header("Origin", "https://www.youtube.com")
                }
                urlStr.contains("eporner.com") || urlStr.contains("eporner") || urlStr.contains("static-cluster") -> {
                    if (request.header("Referer") == null) builder.header("Referer", "https://www.eporner.com/")
                    if (request.header("Origin") == null) builder.header("Origin", "https://www.eporner.com")
                }
                urlStr.contains("archive.org") || urlStr.contains("us.archive.org") || urlStr.contains("ia") -> {
                    if (request.header("Referer") == null) builder.header("Referer", "https://archive.org/")
                    if (request.header("Accept") == null) builder.header("Accept", "*/*")
                }
                urlStr.contains("dailymotion.com") || urlStr.contains("dmcdn.net") || urlStr.contains("dai.ly") -> {
                    if (request.header("Referer") == null) builder.header("Referer", "https://www.dailymotion.com/")
                    if (request.header("Origin") == null) builder.header("Origin", "https://www.dailymotion.com")
                }
                urlStr.contains("pornhub.com") || urlStr.contains("phncdn.com") -> {
                    if (request.header("Referer") == null) builder.header("Referer", "https://www.pornhub.com/")
                    if (request.header("Origin") == null) builder.header("Origin", "https://www.pornhub.com")
                    if (request.header("Cookie") == null) builder.header("Cookie", "age_verified=1")
                }
                urlStr.contains("vimeo.com") -> {
                    if (request.header("Referer") == null) builder.header("Referer", "https://vimeo.com/")
                }
            }

            chain.proceed(builder.build())
        }
        .dns(object : okhttp3.Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> {
                return try {
                    okhttp3.Dns.SYSTEM.lookup(hostname)
                } catch (e: java.net.UnknownHostException) {
                    try {
                        java.net.InetAddress.getAllByName(hostname).toList().ifEmpty {
                            throw e
                        }
                    } catch (fallbackError: Throwable) {
                        throw e
                    }
                }
            }
        })
        .build()

    private val _activeStreamData = MutableStateFlow<StreamData?>(null)
    val activeStreamData: StateFlow<StreamData?> = _activeStreamData.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _bufferedPositionMs = MutableStateFlow(0L)
    val bufferedPositionMs: StateFlow<Long> = _bufferedPositionMs.asStateFlow()

    private val _progressFraction = MutableStateFlow(0f)
    val progressFraction: StateFlow<Float> = _progressFraction.asStateFlow()

    private val _bufferedFraction = MutableStateFlow(0f)
    val bufferedFraction: StateFlow<Float> = _bufferedFraction.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _playerError = MutableStateFlow<String?>(null)
    val playerError: StateFlow<String?> = _playerError.asStateFlow()

    private val _firstFrameRendered = MutableStateFlow(false)
    val firstFrameRendered: StateFlow<Boolean> = _firstFrameRendered.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<com.example.model.AudioTrackOption>>(emptyList())
    val audioTracks: StateFlow<List<com.example.model.AudioTrackOption>> = _audioTracks.asStateFlow()

    private val _areControlsVisible = MutableStateFlow(true)
    val areControlsVisible: StateFlow<Boolean> = _areControlsVisible.asStateFlow()

    private var autoHideControlsJob: Job? = null

    fun scheduleControlsAutoHide(delayMs: Long = 2700L) {
        autoHideControlsJob?.cancel()
        autoHideControlsJob = scope.launch {
            delay(delayMs)
            _areControlsVisible.value = false
        }
    }

    fun setControlsVisibility(visible: Boolean) {
        _areControlsVisible.value = visible
        if (visible) {
            scheduleControlsAutoHide()
        } else {
            autoHideControlsJob?.cancel()
        }
    }

    fun showControls(autoHideDelayMs: Long = 2700L) {
        _areControlsVisible.value = true
        scheduleControlsAutoHide(autoHideDelayMs)
    }

    fun hideControls() {
        autoHideControlsJob?.cancel()
        _areControlsVisible.value = false
    }

    fun toggleControlsVisibility(autoHideDelayMs: Long = 2700L) {
        val next = !_areControlsVisible.value
        _areControlsVisible.value = next
        if (next) {
            scheduleControlsAutoHide(autoHideDelayMs)
        } else {
            autoHideControlsJob?.cancel()
        }
    }

    private val _playbackEnded = MutableStateFlow(false)
    val playbackEnded: StateFlow<Boolean> = _playbackEnded.asStateFlow()

    fun clearPlaybackEnded() {
        _playbackEnded.value = false
    }

    private var currentLoadedMediaKey: String? = null

    fun notifyFirstFrameRendered() {
        _firstFrameRendered.value = true
    }

    fun resetFirstFrameState() {
        _firstFrameRendered.value = false
    }

    private var playbackFailedListener: (() -> Unit)? = null

    fun setPlaybackFailedListener(listener: (() -> Unit)?) {
        playbackFailedListener = listener
    }

    private var playerViewInstance: androidx.media3.ui.PlayerView? = null

    fun getOrCreatePlayerView(context: Context): androidx.media3.ui.PlayerView {
        val existing = playerViewInstance
        return if (existing != null) {
            existing
        } else {
            val player = getExoPlayer(context)
            val pv = androidx.media3.ui.PlayerView(context.applicationContext).apply {
                this.player = player
                useController = false
                controllerShowTimeoutMs = 2800
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_ALWAYS)
                setControllerVisibilityListener(androidx.media3.ui.PlayerView.ControllerVisibilityListener { visibility ->
                    _areControlsVisible.value = (visibility == android.view.View.VISIBLE)
                })
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            playerViewInstance = pv
            pv
        }
    }

    fun getExoPlayer(context: Context): ExoPlayer {
        val existing = exoPlayerInstance
        return if (existing == null) {
            val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    2_500,   // minBufferMs (2.5s minimum buffer)
                    50_000,  // maxBufferMs
                    500,     // bufferForPlaybackMs (0.5s for instant playback startup)
                    1_000    // bufferForPlaybackAfterRebufferMs (1.0s)
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBackBuffer(15_000, true)
                .build()

            val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context.applicationContext).apply {
                setEnableDecoderFallback(true)
                setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            }

            val player = ExoPlayer.Builder(context.applicationContext)
                .setRenderersFactory(renderersFactory)
                .setLoadControl(loadControl)
                .build()
            player.playWhenReady = true
            player.addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    _firstFrameRendered.value = true
                    com.example.util.PlaybackPipelineTracker.logFirstFrame(player.duration)
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                    if (playing) {
                        _firstFrameRendered.value = true
                    }
                    updatePlayerPositions(player)
                }

                override fun onPlaybackStateChanged(state: Int) {
                    _isPlaying.value = player.isPlaying
                    _isBuffering.value = (state == Player.STATE_BUFFERING)
                    updatePlayerPositions(player)
                    if (state == Player.STATE_READY && player.playWhenReady) {
                        _firstFrameRendered.value = true
                        _isBuffering.value = false
                        com.example.util.PlaybackPipelineTracker.logFirstFrame(player.duration)
                    }
                    if (state == Player.STATE_ENDED) {
                        _isPlaying.value = false
                        _isBuffering.value = false
                        _playbackEnded.value = true
                    }
                }

                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    updateAudioTracks(tracks)
                }

                override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                    updatePlayerPositions(player)
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    updatePlayerPositions(player)
                }

                override fun onPlayerError(error: PlaybackException) {
                    val rootCause = error.cause
                    val httpStatus = (rootCause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
                    val errorCodeName = error.errorCodeName

                    com.example.util.PlaybackPipelineTracker.logPlaybackError(
                        errorCodeName = errorCodeName,
                        errorCode = error.errorCode,
                        message = error.message,
                        causeName = rootCause?.javaClass?.simpleName,
                        causeMessage = rootCause?.message,
                        httpStatus = httpStatus
                    )

                    val detailedError = StringBuilder("Media3 Error [$errorCodeName / ${error.errorCode}]: ${error.message ?: "Unknown error"}")
                    if (rootCause != null) {
                        detailedError.append("\nCause: [${rootCause.javaClass.simpleName}] ${rootCause.message}")
                    }
                    if (httpStatus != null) {
                        detailedError.append(" (HTTP Status $httpStatus)")
                    }

                    _playerError.value = detailedError.toString()
                    _isPlaying.value = false
                    playbackFailedListener?.invoke()
                }
            })
            exoPlayerInstance = player
            startProgressTracker()
            player
        } else {
            existing
        }
    }

    private fun updatePlayerPositions(player: ExoPlayer) {
        val cur = player.currentPosition
        val dur = player.duration
        val buf = player.bufferedPosition
        if (dur > 0 && cur >= 0) {
            _currentPositionMs.value = cur
            _durationMs.value = dur
            _bufferedPositionMs.value = buf.coerceAtLeast(cur)
            _progressFraction.value = (cur.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            _bufferedFraction.value = (buf.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
        } else if (cur >= 0) {
            _currentPositionMs.value = cur
        }
    }

    private fun updateAudioTracks(tracks: androidx.media3.common.Tracks) {
        val list = mutableListOf<com.example.model.AudioTrackOption>()
        var groupIdx = 0
        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val rawLang = format.language ?: ""
                    val displayLang = when (rawLang.lowercase()) {
                        "jpn", "ja", "japanese" -> "Japanese (日本語)"
                        "eng", "en", "english" -> "English"
                        "hin", "hi", "hindi" -> "Hindi (हिंदी)"
                        "spa", "es", "spanish" -> "Spanish (Español)"
                        "fre", "fra", "fr", "french" -> "French (Français)"
                        "ger", "deu", "de", "german" -> "German (Deutsch)"
                        "chi", "zho", "zh", "chinese" -> "Chinese (中文)"
                        "kor", "ko", "korean" -> "Korean (한국어)"
                        "por", "pt", "portuguese" -> "Portuguese"
                        "rus", "ru", "russian" -> "Russian"
                        "" -> "Track ${list.size + 1}"
                        else -> rawLang.replaceFirstChar { it.uppercase() }
                    }
                    val label = if (!format.label.isNullOrBlank()) {
                        "${format.label} ($displayLang)"
                    } else {
                        displayLang
                    }
                    val channels = if (format.channelCount > 0) {
                        if (format.channelCount == 2) "Stereo"
                        else if (format.channelCount == 6) "5.1 Surround"
                        else "${format.channelCount} Ch"
                    } else ""

                    val isSelected = group.isTrackSelected(i)
                    list.add(
                        com.example.model.AudioTrackOption(
                            groupIndex = groupIdx,
                            trackIndex = i,
                            label = label,
                            languageCode = rawLang.ifBlank { "default" },
                            displayLanguage = displayLang,
                            isSelected = isSelected,
                            channelInfo = channels,
                            trackGroup = group
                        )
                    )
                }
            }
            groupIdx++
        }
        _audioTracks.value = list
    }

    fun selectAudioTrack(option: com.example.model.AudioTrackOption) {
        exoPlayerInstance?.let { player ->
            val override = androidx.media3.common.TrackSelectionOverride(
                option.trackGroup.mediaTrackGroup,
                option.trackIndex
            )
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(override)
                .build()
            updateAudioTracks(player.currentTracks)
        }
    }

    fun setPreferredAudioLanguage(languageCode: String) {
        exoPlayerInstance?.let { player ->
            val builder = player.trackSelectionParameters.buildUpon()
            if (languageCode == "auto" || languageCode.isBlank()) {
                builder.setPreferredAudioLanguage(null)
            } else {
                builder.setPreferredAudioLanguage(languageCode)
            }
            player.trackSelectionParameters = builder.build()
            updateAudioTracks(player.currentTracks)
        }
    }

    private fun startProgressTracker() {
        progressTrackerJob?.cancel()
        progressTrackerJob = scope.launch {
            while (isActive) {
                val player = exoPlayerInstance
                if (player != null) {
                    updatePlayerPositions(player)
                }
                delay(150)
            }
        }
    }

    fun prepareAndPlay(
        context: Context,
        streamData: StreamData?,
        streamOption: PlayableStreamOption?,
        hlsUrl: String?,
        captionOption: CaptionOption?,
        initialPos: Long = 0L
    ) {
        val player = getExoPlayer(context)
        _playbackEnded.value = false
        if (streamData != null) {
            _activeStreamData.value = streamData
        }
        _playerError.value = null

        val rawUrl = streamOption?.videoUrl ?: streamOption?.videoStream?.url ?: hlsUrl
        if (rawUrl.isNullOrEmpty()) {
            currentLoadedMediaKey = null
            _firstFrameRendered.value = false
            _isPlaying.value = false
            _currentPositionMs.value = 0L
            _durationMs.value = 0L
            _bufferedPositionMs.value = 0L
            _progressFraction.value = 0f
            _bufferedFraction.value = 0f
            exoPlayerInstance?.let { p ->
                p.playWhenReady = false
                p.stop()
                p.clearMediaItems()
            }
            return
        }

        val effectivePlayableUrl = rawUrl
        val mediaKey = "${effectivePlayableUrl}_${captionOption?.languageCode}"
        if (mediaKey == currentLoadedMediaKey && player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED) {
            player.playWhenReady = true
            _isPlaying.value = true
            return
        }

        _firstFrameRendered.value = false
        _currentPositionMs.value = initialPos
        _durationMs.value = 0L
        _bufferedPositionMs.value = 0L
        _progressFraction.value = 0f
        _bufferedFraction.value = 0f
        _playerError.value = null
        currentLoadedMediaKey = mediaKey

        try {
            player.stop()
            player.clearMediaItems()

            val combinedHeaders = mutableMapOf<String, String>()
            streamData?.headers?.let { combinedHeaders.putAll(it) }
            streamOption?.headers?.let { combinedHeaders.putAll(it) }

            val uriHost = try { Uri.parse(rawUrl).host ?: "unknown" } catch (_: Exception) { "unknown" }
            android.util.Log.i("GlobalPlayerManager", "[PLAYBACK_START] provider=${streamData?.providerId ?: "direct"}, videoId=${streamData?.videoId}, format=${streamOption?.format}, isMuxed=${streamOption?.isMuxed}, host=$uriHost, quality='${streamOption?.qualityLabel}', headersCount=${combinedHeaders.size}, headerKeys=${combinedHeaders.keys}")

            val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            val defaultHeaders = mutableMapOf<String, String>()
            var customUserAgentSet = false
            combinedHeaders.forEach { (k, v) ->
                if (k.equals("User-Agent", ignoreCase = true)) {
                    dataSourceFactory.setUserAgent(v)
                    customUserAgentSet = true
                } else {
                    defaultHeaders[k] = v
                }
            }
            if (!customUserAgentSet) {
                dataSourceFactory.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            }

            // Inject domain-specific referer & origin headers if not explicitly specified
            val lowerTarget = rawUrl.lowercase()
            val hasReferer = defaultHeaders.keys.any { it.equals("Referer", ignoreCase = true) }
            if (!hasReferer) {
                when {
                    lowerTarget.contains("youtube.com") || lowerTarget.contains("googlevideo.com") || lowerTarget.contains("youtu.be") -> {
                        defaultHeaders["Referer"] = "https://www.youtube.com/"
                        if (!defaultHeaders.keys.any { it.equals("Origin", ignoreCase = true) }) {
                            defaultHeaders["Origin"] = "https://www.youtube.com"
                        }
                    }
                    lowerTarget.contains("dailymotion.com") || lowerTarget.contains("dmcdn.net") || lowerTarget.contains("dai.ly") || lowerTarget.contains("cdndirector") -> {
                        defaultHeaders["Referer"] = "https://www.dailymotion.com/"
                        if (!defaultHeaders.keys.any { it.equals("Origin", ignoreCase = true) }) {
                            defaultHeaders["Origin"] = "https://www.dailymotion.com"
                        }
                    }
                    lowerTarget.contains("archive.org") -> {
                        defaultHeaders["Referer"] = "https://archive.org/"
                        defaultHeaders["Accept"] = "*/*"
                    }
                    lowerTarget.contains("pornhub.com") || lowerTarget.contains("phncdn.com") -> {
                        defaultHeaders["Referer"] = "https://www.pornhub.com/"
                        if (!defaultHeaders.keys.any { it.equals("Origin", ignoreCase = true) }) {
                            defaultHeaders["Origin"] = "https://www.pornhub.com"
                        }
                        if (!defaultHeaders.keys.any { it.equals("Cookie", ignoreCase = true) }) {
                            defaultHeaders["Cookie"] = "age_verified=1"
                        }
                    }
                    lowerTarget.contains("eporner.com") -> {
                        defaultHeaders["Referer"] = "https://www.eporner.com/"
                    }
                    lowerTarget.contains("redtube.com") -> {
                        defaultHeaders["Referer"] = "https://www.redtube.com/"
                    }
                    lowerTarget.contains("xhamster.com") -> {
                        defaultHeaders["Referer"] = "https://xhamster.com/"
                    }
                    lowerTarget.contains("xvideos.com") -> {
                        defaultHeaders["Referer"] = "https://www.xvideos.com/"
                    }
                    lowerTarget.contains("vimeo.com") -> {
                        defaultHeaders["Referer"] = "https://vimeo.com/"
                    }
                    lowerTarget.contains("helvid") || lowerTarget.contains("upload18") || lowerTarget.contains("apijav") -> {
                        defaultHeaders["Referer"] = "https://upload18.org/"
                        if (!defaultHeaders.keys.any { it.equals("Origin", ignoreCase = true) }) {
                            defaultHeaders["Origin"] = "https://upload18.org"
                        }
                    }
                }
            }

            if (defaultHeaders.isNotEmpty()) {
                dataSourceFactory.setDefaultRequestProperties(defaultHeaders)
            }

            val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
                .setConstantBitrateSeekingEnabled(true)
                .setMp4ExtractorFlags(androidx.media3.extractor.mp4.Mp4Extractor.FLAG_READ_SEF_DATA)

            val errorHandlingPolicy = object : androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(1) {
                override fun getRetryDelayMsFor(loadErrorInfo: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                    val rootCause = loadErrorInfo.exception
                    if (rootCause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                        if (rootCause.responseCode in 400..599) {
                            return androidx.media3.common.C.TIME_UNSET
                        }
                    }
                    return super.getRetryDelayMsFor(loadErrorInfo)
                }
            }

            val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
                .setLoadErrorHandlingPolicy(errorHandlingPolicy)

            fun sanitizeMediaUrl(rawUrl: String?): String? {
                if (rawUrl.isNullOrBlank()) return null
                var trimmed = rawUrl.trim()
                if (trimmed.isEmpty()) return null

                if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://") &&
                    !trimmed.startsWith("file://") && !trimmed.startsWith("content://") &&
                    !trimmed.startsWith("asset://") && !trimmed.startsWith("rtmp://") &&
                    !trimmed.startsWith("rtsp://") && !trimmed.startsWith("udp://")
                ) {
                    if (trimmed.contains(".") && !trimmed.startsWith("/")) {
                        trimmed = "https://$trimmed"
                    } else {
                        return null
                    }
                }

                return try {
                    val sanitized = trimmed
                        .replace("\n", "")
                        .replace("\r", "")
                        .replace("\t", "")
                        .replace(" ", "%20")
                        .replace("\"", "%22")
                        .replace("<", "%3C")
                        .replace(">", "%3E")
                        .replace("\\", "/")

                    val parsed = Uri.parse(sanitized)
                    if (parsed.scheme.isNullOrEmpty()) return null
                    sanitized
                } catch (_: Throwable) {
                    null
                }
            }

            fun buildMediaItem(
                rawUrl: String,
                format: String? = null,
                subtitles: List<MediaItem.SubtitleConfiguration> = emptyList()
            ): MediaItem? {
                val cleanUrl = sanitizeMediaUrl(rawUrl) ?: return null
                val uri = Uri.parse(cleanUrl)
                val lowerUrl = cleanUrl.lowercase()
                val lowerFormat = format?.lowercase()

                val builder = MediaItem.Builder().setUri(uri)

                if (lowerFormat == "hls" || lowerUrl.contains(".m3u8") || lowerUrl.contains("m3u8")) {
                    builder.setMimeType(MimeTypes.APPLICATION_M3U8)
                } else if (lowerFormat == "mpd" || lowerUrl.contains(".mpd")) {
                    builder.setMimeType(MimeTypes.APPLICATION_MPD)
                } else if (lowerUrl.contains("mime=video%2fwebm") || lowerUrl.contains("mime=video/webm") || lowerUrl.contains(".webm") || lowerFormat == "webm") {
                    builder.setMimeType(MimeTypes.VIDEO_WEBM)
                } else if (lowerUrl.contains("mime=audio%2fwebm") || lowerUrl.contains("mime=audio/webm")) {
                    builder.setMimeType(MimeTypes.AUDIO_WEBM)
                } else if (lowerUrl.contains("mime=audio%2fmp4") || lowerUrl.contains("mime=audio/mp4") || lowerUrl.contains("mime=audio%2fm4a")) {
                    builder.setMimeType(MimeTypes.AUDIO_MP4)
                } else if (lowerUrl.contains("mime=video%2fmp4") || lowerUrl.contains("mime=video/mp4") || lowerUrl.contains(".mp4") || lowerFormat == "mp4") {
                    builder.setMimeType(MimeTypes.VIDEO_MP4)
                }

                if (subtitles.isNotEmpty()) {
                    builder.setSubtitleConfigurations(subtitles)
                }

                return builder.build()
            }

            var mediaSourceSet = false
            if (streamOption != null) {
                val vUrl = streamOption.videoUrl ?: streamOption.videoStream?.url
                val aUrl = streamOption.audioUrl ?: streamOption.audioStream?.url

                val subtitleConfigs = mutableListOf<MediaItem.SubtitleConfiguration>()
                if (captionOption != null && !captionOption.url.isNullOrEmpty()) {
                    val cleanCapUrl = sanitizeMediaUrl(captionOption.url)
                    if (cleanCapUrl != null) {
                        val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(cleanCapUrl))
                            .setMimeType(MimeTypes.TEXT_VTT)
                            .setLanguage(captionOption.languageCode)
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build()
                        subtitleConfigs.add(subtitleConfig)
                    }
                }

                if (streamOption.isMuxed && !vUrl.isNullOrEmpty()) {
                    val item = buildMediaItem(vUrl, streamOption.format, subtitleConfigs)
                    if (item != null) {
                        val mediaSource = mediaSourceFactory.createMediaSource(item)
                        player.setMediaSource(mediaSource)
                        mediaSourceSet = true
                    }
                } else if (!streamOption.isMuxed && !vUrl.isNullOrEmpty() && !aUrl.isNullOrEmpty()) {
                    val videoItem = buildMediaItem(vUrl, streamOption.format, subtitleConfigs)
                    val audioItem = buildMediaItem(aUrl)
                    if (videoItem != null && audioItem != null) {
                        val videoSource = mediaSourceFactory.createMediaSource(videoItem)
                        val audioSource = mediaSourceFactory.createMediaSource(audioItem)
                        val mergedSource = MergingMediaSource(videoSource, audioSource)
                        player.setMediaSource(mergedSource)
                        mediaSourceSet = true
                    } else if (videoItem != null) {
                        val videoSource = mediaSourceFactory.createMediaSource(videoItem)
                        player.setMediaSource(videoSource)
                        mediaSourceSet = true
                    }
                } else if (!vUrl.isNullOrEmpty()) {
                    val item = buildMediaItem(vUrl, streamOption.format, subtitleConfigs)
                    if (item != null) {
                        val mediaSource = mediaSourceFactory.createMediaSource(item)
                        player.setMediaSource(mediaSource)
                        mediaSourceSet = true
                    }
                }
            }

            if (!mediaSourceSet && !hlsUrl.isNullOrEmpty()) {
                val cleanHls = sanitizeMediaUrl(hlsUrl)
                if (cleanHls != null) {
                    val item = buildMediaItem(cleanHls, "hls")
                    if (item != null) {
                        val mediaSource = mediaSourceFactory.createMediaSource(item)
                        player.setMediaSource(mediaSource)
                        mediaSourceSet = true
                    }
                }
            } else if (!mediaSourceSet && !rawUrl.isNullOrEmpty()) {
                val cleanRaw = sanitizeMediaUrl(rawUrl)
                if (cleanRaw != null) {
                    val item = buildMediaItem(cleanRaw, streamOption?.format)
                    if (item != null) {
                        val mediaSource = mediaSourceFactory.createMediaSource(item)
                        player.setMediaSource(mediaSource)
                        mediaSourceSet = true
                    }
                }
            }

            if (!mediaSourceSet) {
                _playerError.value = "Unable to parse valid video stream URL"
                playbackFailedListener?.invoke()
                return
            }

            if (initialPos > 0L) {
                player.seekTo(initialPos)
            }

            com.example.util.PlaybackPipelineTracker.logPrepare(
                urlSnippet = effectivePlayableUrl.take(60),
                headersCount = combinedHeaders.size
            )

            player.prepare()
            player.playWhenReady = true
            _isPlaying.value = true

            // Save to Room Database for offline access and zero-buffering preloading
            val ctx = context.applicationContext
            scope.launch(Dispatchers.IO) {
                try {
                    val videoRepo = com.example.db.VideoCacheRepository(ctx)
                    if (streamData != null) {
                        videoRepo.cacheVideoMetadata(
                            videoId = streamData.videoId,
                            title = streamData.title,
                            channelName = streamData.channelName,
                            thumbnailUrl = "https://i.ytimg.com/vi/${streamData.videoId}/hqdefault.jpg",
                            description = streamData.description,
                            duration = "",
                            providerId = streamData.providerId
                        )
                        if (effectivePlayableUrl.isNotBlank()) {
                            videoRepo.cachePreloadedStream(
                                videoId = streamData.videoId,
                                streamUrl = effectivePlayableUrl,
                                hlsUrl = hlsUrl ?: streamData.hlsUrl,
                                qualityLabel = streamOption?.qualityLabel ?: "Auto"
                            )
                        }
                    }
                } catch (_: Throwable) {}
            }
        } catch (e: Throwable) {
            _playerError.value = e.localizedMessage ?: "Playback initialization failed"
        }
    }

    fun hasLoadedMedia(): Boolean = currentLoadedMediaKey != null && (exoPlayerInstance?.mediaItemCount ?: 0) > 0

    fun togglePlayPause() {
        exoPlayerInstance?.let { player ->
            if (player.isPlaying) {
                player.pause()
                _isPlaying.value = false
            } else if (player.mediaItemCount > 0) {
                player.play()
                _isPlaying.value = true
            }
        }
    }

    fun play() {
        exoPlayerInstance?.let { player ->
            if (player.mediaItemCount > 0) {
                player.play()
                _isPlaying.value = true
            }
        }
    }

    fun pause() {
        exoPlayerInstance?.let { player ->
            player.pause()
            _isPlaying.value = false
        }
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0L)
        _currentPositionMs.value = target
        val dur = _durationMs.value
        if (dur > 0) {
            _progressFraction.value = (target.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
        }
        exoPlayerInstance?.seekTo(target)
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayerInstance?.playbackParameters = PlaybackParameters(speed)
    }

    fun stopAndClear() {
        autoHideControlsJob?.cancel()
        currentLoadedMediaKey = null
        _activeStreamData.value = null
        _progressFraction.value = 0f
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
        _bufferedPositionMs.value = 0L
        _bufferedFraction.value = 0f
        _isPlaying.value = false
        _firstFrameRendered.value = false
        _playerError.value = null
        exoPlayerInstance?.let { player ->
            try {
                player.playWhenReady = false
                player.stop()
                player.clearMediaItems()
            } catch (ignored: Throwable) {}
        }
    }
}
