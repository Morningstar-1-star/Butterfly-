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
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
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

    private val _playerError = MutableStateFlow<String?>(null)
    val playerError: StateFlow<String?> = _playerError.asStateFlow()

    private val _isEmbedOrWebPage = MutableStateFlow(false)
    val isEmbedOrWebPage: StateFlow<Boolean> = _isEmbedOrWebPage.asStateFlow()

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
    private var webViewInstance: android.webkit.WebView? = null

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    fun getOrCreateWebView(context: Context): android.webkit.WebView {
        val existing = webViewInstance
        return if (existing != null) {
            existing
        } else {
            val wv = android.webkit.WebView(context.applicationContext).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
            }
            webViewInstance = wv
            wv
        }
    }

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
                    2_500,  // minBufferMs (2.5s for fast start)
                    50_000, // maxBufferMs
                    800,    // bufferForPlaybackMs (0.8s for instant playback startup)
                    1_500   // bufferForPlaybackAfterRebufferMs (1.5s)
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            val player = ExoPlayer.Builder(context.applicationContext)
                .setLoadControl(loadControl)
                .build()
            player.playWhenReady = true
            player.addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    _firstFrameRendered.value = true
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
                    updatePlayerPositions(player)
                    if (state == Player.STATE_ENDED) {
                        _isPlaying.value = false
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
                    _playerError.value = error.localizedMessage ?: "Playback error"
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
        embedUrl: String?,
        initialPos: Long = 0L
    ) {
        val player = getExoPlayer(context)
        _playbackEnded.value = false
        if (streamData != null) {
            _activeStreamData.value = streamData
        }
        _playerError.value = null

        val rawUrl = streamOption?.videoUrl ?: streamOption?.videoStream?.url ?: hlsUrl ?: embedUrl
        if (rawUrl.isNullOrEmpty()) {
            _isEmbedOrWebPage.value = true
            return
        }

        val sourceType = com.example.model.PlaybackDecisionResolver.determineSourceType(rawUrl, streamOption?.format)
        val isEmbed = sourceType == com.example.model.PlaybackSourceType.EMBED_WEBVIEW
        val isMagnet = sourceType == com.example.model.PlaybackSourceType.MAGNET

        // Resolve playable URL: for magnets, pipe via TorrentStreamEngine local HTTP streaming server
        val effectivePlayableUrl = if (isMagnet || rawUrl.startsWith("magnet:") || streamOption?.format.equals("torrent", ignoreCase = true)) {
            com.example.torrent.TorrentStreamEngine.getStreamUrl(context, rawUrl, streamData?.title)
        } else {
            rawUrl
        }

        _isEmbedOrWebPage.value = isEmbed

        val mediaKey = "${effectivePlayableUrl}_${captionOption?.languageCode}"
        if (mediaKey == currentLoadedMediaKey && player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED) {
            // Already loaded and playing this media, ensure playing
            player.playWhenReady = true
            _isPlaying.value = true
            return
        }

        _firstFrameRendered.value = false
        currentLoadedMediaKey = mediaKey

        if (!isEmbed) {
            try {
                player.stop()
                player.clearMediaItems()

                val combinedHeaders = mutableMapOf<String, String>()
                streamData?.headers?.let { combinedHeaders.putAll(it) }
                streamOption?.headers?.let { combinedHeaders.putAll(it) }

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

                val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)

                fun buildMediaItem(
                    url: String,
                    format: String? = null,
                    subtitles: List<MediaItem.SubtitleConfiguration> = emptyList()
                ): MediaItem {
                    val uri = Uri.parse(url)
                    val lowerUrl = url.lowercase()
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
                        val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(captionOption.url))
                            .setMimeType(MimeTypes.TEXT_VTT)
                            .setLanguage(captionOption.languageCode)
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build()
                        subtitleConfigs.add(subtitleConfig)
                    }

                    if (streamOption.isMuxed && !vUrl.isNullOrEmpty()) {
                        val item = buildMediaItem(vUrl, streamOption.format, subtitleConfigs)
                        val mediaSource = mediaSourceFactory.createMediaSource(item)
                        player.setMediaSource(mediaSource)
                        mediaSourceSet = true
                    } else if (!streamOption.isMuxed && !vUrl.isNullOrEmpty() && !aUrl.isNullOrEmpty()) {
                        val videoItem = buildMediaItem(vUrl, streamOption.format, subtitleConfigs)
                        val audioItem = buildMediaItem(aUrl)
                        val videoSource = mediaSourceFactory.createMediaSource(videoItem)
                        val audioSource = mediaSourceFactory.createMediaSource(audioItem)

                        val mergedSource = MergingMediaSource(videoSource, audioSource)
                        player.setMediaSource(mergedSource)
                        mediaSourceSet = true
                    } else if (!vUrl.isNullOrEmpty()) {
                        val item = buildMediaItem(vUrl, streamOption.format, subtitleConfigs)
                        val mediaSource = mediaSourceFactory.createMediaSource(item)
                        player.setMediaSource(mediaSource)
                        mediaSourceSet = true
                    }
                }

                if (!mediaSourceSet && !hlsUrl.isNullOrEmpty()) {
                    val item = buildMediaItem(hlsUrl, "hls")
                    val mediaSource = mediaSourceFactory.createMediaSource(item)
                    player.setMediaSource(mediaSource)
                    mediaSourceSet = true
                } else if (!mediaSourceSet && !rawUrl.isNullOrEmpty()) {
                    val item = buildMediaItem(rawUrl, streamOption?.format)
                    val mediaSource = mediaSourceFactory.createMediaSource(item)
                    player.setMediaSource(mediaSource)
                    mediaSourceSet = true
                }

                if (initialPos > 0L) {
                    player.seekTo(initialPos)
                }

                player.prepare()
                player.playWhenReady = true
                _isPlaying.value = true
            } catch (e: Throwable) {
                _playerError.value = e.localizedMessage ?: "Playback initialization failed"
                _isEmbedOrWebPage.value = true
            }
        }
    }

    fun togglePlayPause() {
        exoPlayerInstance?.let { player ->
            if (player.isPlaying) {
                player.pause()
                _isPlaying.value = false
            } else {
                player.play()
                _isPlaying.value = true
            }
        }
    }

    fun play() {
        exoPlayerInstance?.let { player ->
            player.play()
            _isPlaying.value = true
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
        exoPlayerInstance?.seekTo(target)
        _currentPositionMs.value = target
        val dur = _durationMs.value
        if (dur > 0) {
            _progressFraction.value = (target.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayerInstance?.playbackParameters = PlaybackParameters(speed)
    }

    fun stopAndClear() {
        currentLoadedMediaKey = null
        _activeStreamData.value = null
        _progressFraction.value = 0f
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
        _isPlaying.value = false
        exoPlayerInstance?.let { player ->
            player.stop()
            player.clearMediaItems()
        }
        webViewInstance?.let { wv ->
            wv.loadUrl("about:blank")
        }
    }
}
