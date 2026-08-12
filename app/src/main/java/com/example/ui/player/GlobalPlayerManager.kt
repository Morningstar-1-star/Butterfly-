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

    private val okHttpClient = OkHttpClient.Builder().build()

    private val _activeStreamData = MutableStateFlow<StreamData?>(null)
    val activeStreamData: StateFlow<StreamData?> = _activeStreamData.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _progressFraction = MutableStateFlow(0f)
    val progressFraction: StateFlow<Float> = _progressFraction.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playerError = MutableStateFlow<String?>(null)
    val playerError: StateFlow<String?> = _playerError.asStateFlow()

    private val _isEmbedOrWebPage = MutableStateFlow(false)
    val isEmbedOrWebPage: StateFlow<Boolean> = _isEmbedOrWebPage.asStateFlow()

    private val _firstFrameRendered = MutableStateFlow(false)
    val firstFrameRendered: StateFlow<Boolean> = _firstFrameRendered.asStateFlow()

    private val _areControlsVisible = MutableStateFlow(true)
    val areControlsVisible: StateFlow<Boolean> = _areControlsVisible.asStateFlow()

    fun showControls() {
        val pv = playerViewInstance ?: return
        pv.showController()
        _areControlsVisible.value = true
    }

    fun hideControls() {
        val pv = playerViewInstance ?: return
        pv.hideController()
        _areControlsVisible.value = false
    }

    fun toggleControlsVisibility() {
        val pv = playerViewInstance ?: return
        if (pv.isControllerFullyVisible) {
            pv.hideController()
            _areControlsVisible.value = false
        } else {
            pv.showController()
            _areControlsVisible.value = true
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
                useController = true
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
            val player = ExoPlayer.Builder(context.applicationContext).build()
            player.playWhenReady = true
            player.addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    _firstFrameRendered.value = true
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                    if (playing) {
                        // Fallback trigger if surface didn't emit onRenderedFirstFrame
                        _firstFrameRendered.value = true
                    }
                }

                override fun onPlaybackStateChanged(state: Int) {
                    _isPlaying.value = player.isPlaying
                    if (state == Player.STATE_ENDED) {
                        _isPlaying.value = false
                        _playbackEnded.value = true
                    }
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

    private fun startProgressTracker() {
        progressTrackerJob?.cancel()
        progressTrackerJob = scope.launch {
            while (isActive) {
                val player = exoPlayerInstance
                if (player != null && player.isPlaying) {
                    val cur = player.currentPosition
                    val dur = player.duration
                    if (dur > 0 && cur >= 0) {
                        _currentPositionMs.value = cur
                        _durationMs.value = dur
                        val newFrac = (cur.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                        if (kotlin.math.abs(_progressFraction.value - newFrac) >= 0.005f) {
                            _progressFraction.value = newFrac
                        }
                    }
                }
                delay(500)
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

        if (isMagnet) {
            _isEmbedOrWebPage.value = false
            _playerError.value = "Resolving torrent stream via Debrid engine..."
            scope.launch(Dispatchers.IO) {
                val resolver = com.example.plugin.manager.TorrentResolver(context)
                val title = streamData?.title ?: streamOption?.qualityLabel ?: "Stream"
                val resolved = resolver.resolveTorrent(rawUrl, title)
                if (resolved != null && resolved.playableUrl.isNotBlank()) {
                    scope.launch(Dispatchers.Main) {
                        _playerError.value = null
                        val resolvedOption = streamOption?.copy(
                            videoUrl = resolved.playableUrl,
                            format = if (resolved.isHls) "hls" else "mp4"
                        ) ?: PlayableStreamOption(
                            qualityLabel = title,
                            format = if (resolved.isHls) "hls" else "mp4",
                            isMuxed = true,
                            videoUrl = resolved.playableUrl,
                            audioUrl = null
                        )
                        prepareAndPlay(
                            context = context,
                            streamData = streamData ?: _activeStreamData.value,
                            streamOption = resolvedOption,
                            hlsUrl = resolved.playableUrl,
                            captionOption = captionOption,
                            embedUrl = null,
                            initialPos = initialPos
                        )
                    }
                } else {
                    scope.launch(Dispatchers.Main) {
                        _playerError.value = "Torrent magnet requires a Debrid service. Please configure TorBox API Key in Settings."
                        if (!embedUrl.isNullOrEmpty()) {
                            _isEmbedOrWebPage.value = true
                        }
                    }
                }
            }
            return
        }

        _isEmbedOrWebPage.value = isEmbed

        val mediaKey = "${rawUrl}_${captionOption?.languageCode}"
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
                if (defaultHeaders.isNotEmpty()) {
                    dataSourceFactory.setDefaultRequestProperties(defaultHeaders)
                }

                val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)

                if (streamOption != null) {
                    val vUrl = streamOption.videoUrl ?: streamOption.videoStream?.url
                    val aUrl = streamOption.audioUrl ?: streamOption.audioStream?.url

                    if (streamOption.isMuxed && vUrl != null) {
                        val builder = MediaItem.Builder().setUri(Uri.parse(vUrl))
                        if (captionOption != null) {
                            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(captionOption.url))
                                .setMimeType(MimeTypes.TEXT_VTT)
                                .setLanguage(captionOption.languageCode)
                                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                .build()
                            builder.setSubtitleConfigurations(listOf(subtitleConfig))
                        }
                        val mediaSource = mediaSourceFactory.createMediaSource(builder.build())
                        player.setMediaSource(mediaSource)
                    } else if (!streamOption.isMuxed && vUrl != null && aUrl != null) {
                        val videoSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(Uri.parse(vUrl)))
                        val audioSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(Uri.parse(aUrl)))

                        val mergedSource = MergingMediaSource(videoSource, audioSource)
                        player.setMediaSource(mergedSource)
                    } else if (vUrl != null) {
                        val mediaSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(Uri.parse(vUrl)))
                        player.setMediaSource(mediaSource)
                    }
                } else if (!hlsUrl.isNullOrEmpty()) {
                    val mediaSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(Uri.parse(hlsUrl)))
                    player.setMediaSource(mediaSource)
                }

                if (initialPos > 0L) {
                    player.seekTo(initialPos)
                }

                player.prepare()
                player.playWhenReady = true
                _isPlaying.value = true
            } catch (e: Exception) {
                _playerError.value = e.localizedMessage
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
        exoPlayerInstance?.seekTo(positionMs.coerceAtLeast(0L))
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
