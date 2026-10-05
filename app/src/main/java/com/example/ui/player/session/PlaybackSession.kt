package com.example.ui.player.session

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import com.example.db.VideoCacheRepository
import com.example.effects.ShaderEnhancementLoader
import com.example.effects.VideoEffectsManager
import com.example.effects.VideoEnhancementEngine
import com.example.model.AudioTrackOption
import com.example.model.CaptionOption
import com.example.model.PlayableStreamOption
import com.example.model.StreamData
import com.example.subtitles.SubtitleManager
import com.example.ui.player.GlobalPlayerManager.SubtitleMode
import com.example.ui.player.core.MediaHeaderHelper
import com.example.ui.player.core.MediaSourceFactoryHelper
import com.example.ui.player.core.PlaybackResumeController
import com.example.ui.player.core.PlayerCore
import com.example.ui.player.core.PlayerRecoveryManager
import com.example.ui.player.core.SmartSkipController
import com.example.util.NetworkManager
import com.example.util.PlaybackPipelineTracker
import com.example.util.PlaybackPreferences
import com.example.util.SubtitleCue
import com.example.util.SubtitleTranslator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Request

/**
 * PlaybackSession: Decoupled playback orchestrator that mediates between
 * UI state representation, PlayerCore (Media3/ExoPlayer), SmartSkip, Resume, and Subtitle systems.
 */
class PlaybackSession(private val appContext: Context) {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressTrackingJob: Job? = null
    private var autoHideControlsJob: Job? = null
    private var videoEffectsJob: Job? = null
    private var hasAppliedCustomEffects = false

    // Coordinators
    private val resumeController = PlaybackResumeController { appContext }
    private val recoveryManager = PlayerRecoveryManager()
    private val smartSkipController = SmartSkipController { appContext }

    // Media3 ConcatenatingMediaSource Queue Manager
    val queueManager = com.example.ui.player.queue.Media3QueueManager(
        appContext = appContext,
        mainScope = scope,
        onActiveVideoChanged = { newStreamData, videoItem ->
            onQueueActiveVideoChanged(newStreamData, videoItem)
        }
    )

    // Media key to avoid duplicate loads
    private var currentLoadedMediaKey: String? = null

    // State flows
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

    private val _audioTracks = MutableStateFlow<List<AudioTrackOption>>(emptyList())
    val audioTracks: StateFlow<List<AudioTrackOption>> = _audioTracks.asStateFlow()

    private var userPreferredAudioLanguage: String? = null

    private val _subtitleMode = MutableStateFlow(SubtitleMode.OFF)
    val subtitleMode: StateFlow<SubtitleMode> = _subtitleMode.asStateFlow()

    private val _bilibiliSubtitleTracks = MutableStateFlow<List<CaptionOption>>(emptyList())
    val bilibiliSubtitleTracks: StateFlow<List<CaptionOption>> = _bilibiliSubtitleTracks.asStateFlow()

    private val _selectedSubtitleTrack = MutableStateFlow<CaptionOption?>(null)
    val selectedSubtitleTrack: StateFlow<CaptionOption?> = _selectedSubtitleTrack.asStateFlow()

    private val _bilibiliCues = MutableStateFlow<List<SubtitleCue>>(emptyList())
    val bilibiliCues: StateFlow<List<SubtitleCue>> = _bilibiliCues.asStateFlow()

    private val _targetCaptionLanguage = MutableStateFlow("en")
    val targetCaptionLanguage: StateFlow<String> = _targetCaptionLanguage.asStateFlow()

    private val _currentActiveSubtitleText = MutableStateFlow("")
    val currentActiveSubtitleText: StateFlow<String> = _currentActiveSubtitleText.asStateFlow()

    private val _currentActiveTranslatedText = MutableStateFlow("")
    val currentActiveTranslatedText: StateFlow<String> = _currentActiveTranslatedText.asStateFlow()

    private val _isLoopEnabled = MutableStateFlow(false)
    val isLoopEnabled: StateFlow<Boolean> = _isLoopEnabled.asStateFlow()

    private val _videoAspectRatio = MutableStateFlow(16f / 9f)
    val videoAspectRatio: StateFlow<Float> = _videoAspectRatio.asStateFlow()

    private val _areControlsVisible = MutableStateFlow(false)
    val areControlsVisible: StateFlow<Boolean> = _areControlsVisible.asStateFlow()

    private val _playbackEnded = MutableStateFlow(false)
    val playbackEnded: StateFlow<Boolean> = _playbackEnded.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                val pixelRatio = if (videoSize.pixelWidthHeightRatio > 0f) videoSize.pixelWidthHeightRatio else 1f
                val ratio = (videoSize.width.toFloat() * pixelRatio) / videoSize.height.toFloat()
                if (ratio in 0.4f..2.5f) {
                    _videoAspectRatio.value = ratio
                }
                VideoEnhancementEngine.updateVideoDimensions(videoSize.width, videoSize.height)
            }
        }

        override fun onRenderedFirstFrame() {
            _firstFrameRendered.value = true
            com.example.ui.player.metrics.PlaybackMetricsTracker.onFirstFrameRendered()
            playerCore?.player?.duration?.let { PlaybackPipelineTracker.logFirstFrame(it) }
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
            playerCore?.player?.let { updatePositions(it) }
        }

        override fun onPlaybackStateChanged(state: Int) {
            val exo = playerCore?.player ?: return
            _isPlaying.value = exo.isPlaying
            _isBuffering.value = (state == Player.STATE_BUFFERING)
            if (state == Player.STATE_BUFFERING) {
                com.example.ui.player.metrics.PlaybackMetricsTracker.onRebufferStarted()
            }
            updatePositions(exo)

            if (state == Player.STATE_READY) {
                com.example.ui.player.metrics.PlaybackMetricsTracker.onSeekCompleted()
                com.example.ui.player.metrics.PlaybackMetricsTracker.onRebufferEnded()
                if (exo.playWhenReady) {
                    _isBuffering.value = false
                }
                resumeController.getPendingResumePosition()?.let { targetPos ->
                    if (targetPos > 0L && kotlin.math.abs(exo.currentPosition - targetPos) > 1000L) {
                        exo.seekTo(targetPos)
                    }
                    resumeController.clearPendingResumePosition()
                }
            }

            if (state == Player.STATE_ENDED) {
                _isPlaying.value = false
                _isBuffering.value = false
                _playbackEnded.value = true
                _progressFraction.value = if (_durationMs.value > 0L) 1f else 0f
            } else {
                _playbackEnded.value = false
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            queueManager.onMediaItemTransition(mediaItem, reason)
        }

        override fun onTracksChanged(tracks: Tracks) {
            for (group in tracks.groups) {
                if (group.type == C.TRACK_TYPE_VIDEO && group.isSelected) {
                    for (i in 0 until group.length) {
                        if (group.isTrackSelected(i)) {
                            val f = group.getTrackFormat(i)
                            com.example.ui.player.metrics.PlaybackMetricsTracker.updateVideoFormat(f.width, f.height, f.bitrate)
                            break
                        }
                    }
                }
            }
            playerCore?.let { core ->
                val parsed = core.parseAudioTracks(tracks)
                _audioTracks.value = parsed
                userPreferredAudioLanguage?.let { lang ->
                    val matching = parsed.find { it.languageCode.equals(lang, ignoreCase = true) || it.label.contains(lang, ignoreCase = true) }
                    if (matching != null && !matching.isSelected) {
                        core.selectAudioTrack(matching)
                    }
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val activeProvider = _activeStreamData.value?.providerId
            val (diagnostics, httpStatus) = recoveryManager.diagnoseError(error, activeProvider)

            val activeData = _activeStreamData.value
            val currentOption = activeData?.selectedStreamOption
            val availableOptions = activeData?.availableStreamOptions.orEmpty()

            val failedUrl = currentOption?.videoUrl ?: playerCore?.player?.currentMediaItem?.localConfiguration?.uri?.toString()

            if (activeProvider == "bilibili" || failedUrl?.contains("bilivideo") == true || failedUrl?.contains("bilibili") == true) {
                val host = runCatching { android.net.Uri.parse(failedUrl).host }.getOrNull() ?: "unknown"
                val protocol = runCatching { android.net.Uri.parse(failedUrl).scheme }.getOrNull() ?: "unknown"
                Log.w("BilibiliDiagnostics", "Bilibili Playback Error: httpStatus=$httpStatus, error=${error.message}, protocol=$protocol, host=$host, failedUrl=${failedUrl?.take(120)}")
            }

            // If error is transient (e.g. during seek or network timeout on YouTube/HLS), retry on current option first
            val isTransientSeekOrNetError = (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW)
            if (isTransientSeekOrNetError && activeData != null && currentOption != null) {
                val curPos = _currentPositionMs.value.coerceAtLeast(0L)
                Log.i("PlaybackSession", "Transient playback error $httpStatus on ${currentOption.qualityLabel}, retrying at $curPos ms")
                _playerError.value = null
                prepareAndPlay(appContext, activeData, currentOption, initialPos = curPos)
                return
            }

            recoveryManager.markStreamFailed(failedUrl)
            if (currentOption?.providerType == com.example.model.ProviderType.DECRYPTOR || currentOption?.sourceName.equals("Decryptor", ignoreCase = true)) {
                com.example.decryptor.DecryptorProviderClient.markServerFailed(failedUrl)
            }

            // Priority: if a Decryptor server fails, try next available Decryptor server first
            val isCurrentDecryptor = currentOption?.providerType == com.example.model.ProviderType.DECRYPTOR ||
                    currentOption?.sourceName.equals("Decryptor", ignoreCase = true)

            val nextOption = if (isCurrentDecryptor) {
                availableOptions.firstOrNull { opt ->
                    (opt.providerType == com.example.model.ProviderType.DECRYPTOR || opt.sourceName.equals("Decryptor", ignoreCase = true)) &&
                            !opt.videoUrl.isNullOrBlank() &&
                            !recoveryManager.isStreamFailed(opt.videoUrl) &&
                            !com.example.decryptor.DecryptorProviderClient.isServerFailed(opt.videoUrl)
                } ?: availableOptions.firstOrNull { opt ->
                    !opt.videoUrl.isNullOrBlank() && !recoveryManager.isStreamFailed(opt.videoUrl)
                }
            } else {
                availableOptions.firstOrNull { opt ->
                    !opt.videoUrl.isNullOrBlank() && !recoveryManager.isStreamFailed(opt.videoUrl)
                }
            }

            if (activeData != null && nextOption != null) {
                Log.i("PlaybackSession", "Playback error $httpStatus; falling back to option: ${nextOption.qualityLabel}")
                _playerError.value = null
                val updatedData = activeData.copy(selectedStreamOption = nextOption)
                prepareAndPlay(appContext, updatedData, nextOption)
                return
            }

            // Auto-Recovery Fallback to Web/Embed Player if direct stream fails
            if (activeData != null) {
                val embedOption = activeData.availableStreamOptions.firstOrNull { it.format.equals("embed", true) }
                if (embedOption != null && !recoveryManager.isStreamFailed(embedOption.videoUrl)) {
                    recoveryManager.markStreamFailed(embedOption.videoUrl)
                    Log.i("PlaybackSession", "Switching to web embed player for ${activeData.videoId}")
                    _playerError.value = null
                    val updatedData = activeData.copy(selectedStreamOption = embedOption)
                    prepareAndPlay(appContext, updatedData, embedOption)
                    return
                }
            }

            _playerError.value = diagnostics
            _isPlaying.value = false
        }
    }

    private var playerCore: PlayerCore? = null

    init {
        val isLooping = PlaybackPreferences.getInstance(appContext).loopVideoEnabled.value
        _isLoopEnabled.value = isLooping
    }

    fun getExoPlayer(): ExoPlayer {
        if (playerCore == null) {
            playerCore = PlayerCore(appContext, playerListener)
            val exo = playerCore!!.player!!
            queueManager.attachPlayer(exo)
            exo.repeatMode = if (_isLoopEnabled.value) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            startProgressTracker()
            attachVideoEffectsPipeline(exo)
        }
        return playerCore!!.player!!
    }

    private fun startProgressTracker() {
        progressTrackingJob?.cancel()
        progressTrackingJob = scope.launch {
            while (true) {
                val exo = playerCore?.player
                if (exo != null && exo.playbackState != Player.STATE_IDLE && exo.playbackState != Player.STATE_ENDED) {
                    updatePositions(exo)
                }
                delay(300L)
            }
        }
    }

    private fun attachVideoEffectsPipeline(player: ExoPlayer) {
        videoEffectsJob?.cancel()
        videoEffectsJob = CoroutineScope(Dispatchers.Main).launch {
            VideoEnhancementEngine.config.collect { config ->
                try {
                    val isAnime = VideoEnhancementEngine.telemetry.value.isAnimeDetected
                    val effect = ShaderEnhancementLoader.createEffect(config, isAnime)
                    if (effect != null) {
                        try {
                            player.setVideoEffects(listOf(effect))
                            hasAppliedCustomEffects = true
                        } catch (e: Throwable) {
                            Log.w("PlaybackSession", "setVideoEffects fallback: ${e.message}")
                            player.setVideoEffects(emptyList())
                            hasAppliedCustomEffects = false
                        }
                    } else if (hasAppliedCustomEffects) {
                        try {
                            player.setVideoEffects(emptyList())
                        } catch (_: Throwable) {}
                        hasAppliedCustomEffects = false
                    }
                } catch (e: Throwable) {
                    Log.w("PlaybackSession", "Video effects update notice: ${e.message}")
                }
            }
        }
    }

    private fun updatePositions(player: ExoPlayer) {
        val cur = player.currentPosition
        val dur = player.duration
        val buf = player.bufferedPosition
        val bufferedDurationSec = ((buf - cur).coerceAtLeast(0L) / 1000f)
        com.example.ui.player.metrics.PlaybackMetricsTracker.updateBufferHealth(bufferedDurationSec)
        if (dur > 0 && cur >= 0) {
            _currentPositionMs.value = cur
            _durationMs.value = dur
            _bufferedPositionMs.value = buf.coerceAtLeast(cur)
            _progressFraction.value = (cur.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            _bufferedFraction.value = (buf.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            _activeStreamData.value?.videoId?.let { vid ->
                resumeController.onPositionUpdate(vid, cur, dur)
            }
        } else if (cur >= 0) {
            _currentPositionMs.value = cur
            _activeStreamData.value?.videoId?.let { vid ->
                resumeController.onPositionUpdate(vid, cur, _durationMs.value)
            }
        }

        SubtitleManager.updatePlaybackPosition(cur)
        smartSkipController.onPositionUpdate(cur)

        val posSec = cur / 1000f
        val cues = _bilibiliCues.value
        if (cues.isNotEmpty() && _subtitleMode.value != SubtitleMode.OFF && _subtitleMode.value != SubtitleMode.AI_LIVE_CAPTIONS) {
            val activeCue = cues.find { posSec >= it.fromSeconds && posSec <= it.toSeconds }
            if (activeCue != null) {
                _currentActiveSubtitleText.value = activeCue.text
                _currentActiveTranslatedText.value = activeCue.translatedText ?: activeCue.text
            } else {
                _currentActiveSubtitleText.value = ""
                _currentActiveTranslatedText.value = ""
            }
        } else if (_subtitleMode.value == SubtitleMode.EXTERNAL_PROVIDER) {
            _currentActiveSubtitleText.value = SubtitleManager.currentActiveOriginalText.value
            _currentActiveTranslatedText.value = SubtitleManager.currentActiveTranslatedText.value
        }
    }

    fun prepareAndPlay(
        context: Context? = null,
        streamData: StreamData?,
        streamOption: PlayableStreamOption?,
        captionOption: CaptionOption? = null,
        initialPos: Long = 0L,
        hlsUrl: String? = null
    ) {
        val player = getExoPlayer()
        _activeStreamData.value = streamData
        _bilibiliSubtitleTracks.value = streamData?.captionOptions ?: emptyList()
        _selectedSubtitleTrack.value = captionOption

        val rawUrl = streamOption?.videoUrl
            ?: streamOption?.videoStream?.url
            ?: hlsUrl
            ?: streamData?.hlsUrl

        if (rawUrl.isNullOrBlank()) {
            _playerError.value = "No playable video source available"
            player.playWhenReady = false
            player.stop()
            player.clearMediaItems()
            return
        }

        val isEmbedWebUrl = streamOption?.format.equals("embed", true) ||
                (rawUrl.contains("/embed/", ignoreCase = true) && !rawUrl.contains(".mp4") && !rawUrl.contains(".m3u8"))

        if (isEmbedWebUrl) {
            player.playWhenReady = false
            player.stop()
            player.clearMediaItems()
            _playerError.value = null
            return
        }

        val effectivePlayableUrl = rawUrl
        val mediaKey = "${effectivePlayableUrl}_${captionOption?.languageCode}"
        if (mediaKey == currentLoadedMediaKey && player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED) {
            player.playWhenReady = true
            _isPlaying.value = true
            return
        }

        val previousPos = _currentPositionMs.value.coerceAtLeast(try { playerCore?.player?.currentPosition ?: 0L } catch (_: Throwable) { 0L })
        val isQualitySwitch = streamData?.videoId != null && streamData.videoId == _activeStreamData.value?.videoId && previousPos > 0L

        val effectiveResumePos = resumeController.resolveEffectiveResumePosition(
            context = appContext,
            videoId = streamData?.videoId,
            initialPos = initialPos,
            isQualitySwitch = isQualitySwitch,
            previousPos = previousPos
        )

        _firstFrameRendered.value = false
        _areControlsVisible.value = false
        autoHideControlsJob?.cancel()
        com.example.ui.player.metrics.PlaybackMetricsTracker.resetSession()
        com.example.ui.player.metrics.PlaybackMetricsTracker.startPreparation(rawUrl)
        _currentPositionMs.value = effectiveResumePos
        _durationMs.value = 0L
        _bufferedPositionMs.value = 0L
        _progressFraction.value = 0f
        _bufferedFraction.value = 0f
        _playerError.value = null
        _subtitleMode.value = SubtitleMode.OFF
        _currentActiveSubtitleText.value = ""
        _currentActiveTranslatedText.value = ""
        _bilibiliCues.value = emptyList()
        _selectedSubtitleTrack.value = null
        currentLoadedMediaKey = mediaKey
        resumeController.setPendingResumePosition(effectiveResumePos.takeIf { it > 0L })

        // Auto-enable captions if available on the stream
        val initialCaption = captionOption ?: streamData?.captionOptions?.firstOrNull {
            it.languageCode.startsWith("en", ignoreCase = true) || it.languageName.contains("english", ignoreCase = true)
        } ?: streamData?.captionOptions?.firstOrNull()

        if (initialCaption != null) {
            selectCaptionOption(appContext ?: context, initialCaption)
        }

        try {
            player.stop()
            player.clearMediaItems()

            val mediaSource = MediaSourceFactoryHelper.buildMediaSource(
                context = appContext ?: context,
                streamData = streamData,
                streamOption = streamOption,
                captionOption = captionOption,
                hlsUrl = hlsUrl,
                tag = streamData?.videoId
            )

            if (mediaSource == null) {
                _playerError.value = "Unable to parse valid video stream URL"
                return
            }

            if (streamData != null) {
                queueManager.setInitialStream(
                    streamData = streamData,
                    streamOption = streamOption,
                    captionOption = captionOption,
                    initialMediaSource = mediaSource
                )
            } else {
                player.setMediaSource(mediaSource)
            }

            if (effectiveResumePos > 0L) {
                player.seekTo(effectiveResumePos)
            }

            com.example.ui.player.metrics.PlaybackMetricsTracker.startPreparation(effectivePlayableUrl)

            PlaybackPipelineTracker.logPrepare(
                urlSnippet = effectivePlayableUrl.take(60),
                headersCount = streamOption?.headers?.size ?: 0
            )

            if (streamData?.providerId == "bilibili" || effectivePlayableUrl.contains("bilibili") || effectivePlayableUrl.contains("bilivideo")) {
                val host = runCatching { android.net.Uri.parse(effectivePlayableUrl).host }.getOrNull() ?: "unknown"
                val protocol = runCatching { android.net.Uri.parse(effectivePlayableUrl).scheme }.getOrNull() ?: "unknown"
                Log.i("BilibiliDiagnostics", "Bilibili Stream Prepare: format=${streamOption?.format}, quality=${streamOption?.qualityLabel}, isMuxed=${streamOption?.isMuxed}, protocol=$protocol, host=$host, vUrl=${effectivePlayableUrl.take(120)}")
            }

            player.prepare()
            player.playWhenReady = true
            _isPlaying.value = true
            playerCore?.setPlaybackSpeed(_playbackSpeed.value)

            if (streamData != null) {
                VideoEffectsManager.onVideoChanged(streamData.videoId)
                VideoEnhancementEngine.onVideoLoaded(
                    videoId = streamData.videoId,
                    title = streamData.title,
                    channel = streamData.channelName,
                    tags = null,
                    description = streamData.description,
                    width = 0,
                    height = 0
                )
                com.example.smartskip.SmartSkipPlayerEngine.onVideoChanged(
                    context = appContext,
                    videoId = streamData.videoId,
                    durationMs = _durationMs.value,
                    title = streamData.title,
                    channelName = streamData.channelName,
                    providerId = streamData.providerId
                )

                SubtitleManager.resolveSubtitlesForPlayback(
                    context = appContext,
                    streamData = streamData,
                    onUsableSubtitleFound = null,
                    onFallbackToWhisper = null
                )

                scope.launch(Dispatchers.IO) {
                    try {
                        val videoRepo = VideoCacheRepository(appContext)
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
                    } catch (_: Throwable) {}
                }
            }
        } catch (e: Throwable) {
            _playerError.value = e.localizedMessage ?: "Playback initialization failed"
        }
    }

    fun togglePlayPause() {
        val exo = playerCore?.player ?: return
        if (exo.isPlaying) {
            exo.pause()
            _isPlaying.value = false
        } else if (exo.mediaItemCount > 0) {
            exo.play()
            _isPlaying.value = true
        }
    }

    fun play() {
        playerCore?.play()
        _isPlaying.value = true
    }

    fun pause() {
        playerCore?.pause()
        _isPlaying.value = false
        _activeStreamData.value?.videoId?.let { vid ->
            resumeController.onPositionUpdate(vid, _currentPositionMs.value, _durationMs.value)
        }
    }

    fun seekTo(positionMs: Long) {
        com.example.ui.player.metrics.PlaybackMetricsTracker.onSeekStarted()
        val player = playerCore?.player
        val playerDur = player?.duration?.takeIf { it > 0 && it != C.TIME_UNSET } ?: _durationMs.value
        val safeMax = if (playerDur > 1000L) (playerDur - 100L) else if (playerDur > 0L) playerDur else Long.MAX_VALUE
        val target = positionMs.coerceIn(0L, safeMax)

        _currentPositionMs.value = target
        if (playerDur > 0) {
            _progressFraction.value = (target.toFloat() / playerDur.toFloat()).coerceIn(0f, 1f)
        }
        playerCore?.seekTo(target)
    }

    fun seekForward(deltaMs: Long = 10000L) {
        val cur = _currentPositionMs.value
        val dur = _durationMs.value
        val target = if (dur > 0) (cur + deltaMs).coerceAtMost(dur) else cur + deltaMs
        seekTo(target)
    }

    fun seekBackward(deltaMs: Long = 10000L) {
        val cur = _currentPositionMs.value
        val target = (cur - deltaMs).coerceAtLeast(0L)
        seekTo(target)
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        playerCore?.setPlaybackSpeed(speed)
    }

    fun setLoopVideo(enabled: Boolean, context: Context? = null) {
        _isLoopEnabled.value = enabled
        playerCore?.setRepeatMode(if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF)
        context?.let { ctx ->
            PlaybackPreferences.getInstance(ctx).setLoopVideoEnabled(enabled)
        }
    }

    fun setSubtitleMode(mode: SubtitleMode) {
        _subtitleMode.value = mode
    }

    fun setTargetCaptionLanguage(langCode: String) {
        _targetCaptionLanguage.value = langCode
        SubtitleManager.setSelectedLanguage(langCode)
        val cues = _bilibiliCues.value
        if (cues.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                val translated = SubtitleTranslator.translateCues(cues, targetLang = langCode)
                _bilibiliCues.value = translated
            }
        }
    }

    fun selectBilibiliSubtitleTrack(option: CaptionOption?) {
        selectCaptionOption(appContext, option)
    }

    fun selectCaptionOption(context: Context? = null, option: CaptionOption?) {
        _selectedSubtitleTrack.value = option
        if (option == null) {
            _bilibiliCues.value = emptyList()
            _currentActiveSubtitleText.value = ""
            _currentActiveTranslatedText.value = ""
            _subtitleMode.value = SubtitleMode.OFF
            return
        }

        val rawUrl = option.url
        if (rawUrl.isBlank()) return
        val url = if (rawUrl.startsWith("http://")) rawUrl.replaceFirst("http://", "https://") else rawUrl

        scope.launch(Dispatchers.IO) {
            try {
                val isBili = url.contains("bilibili") || url.contains("biliapi") || url.contains("hdslb") || option.format.contains("json", ignoreCase = true)
                val isYouTube = url.contains("youtube.com") || url.contains("googlevideo.com") || url.contains("youtu.be")
                val isArchive = url.contains("archive.org")
                val isVimeo = url.contains("vimeo") || url.contains("vimeocdn")

                fun fetchUrl(targetUrl: String, withReferer: Boolean = true): String? {
                    return try {
                        val reqBuilder = Request.Builder()
                            .url(targetUrl)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        if (withReferer) {
                            when {
                                isBili -> {
                                    reqBuilder.header("Referer", "https://www.bilibili.com/")
                                }
                                isYouTube -> {
                                    reqBuilder.header("Referer", "https://www.youtube.com/")
                                }
                                isArchive -> {
                                    reqBuilder.header("Referer", "https://archive.org/")
                                }
                                isVimeo -> {
                                    reqBuilder.header("Referer", "https://vimeo.com/")
                                }
                            }
                        }
                        NetworkManager.scraperClient.newCall(reqBuilder.build()).execute().use { resp ->
                            if (resp.isSuccessful) resp.body?.string() else null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }

                var rawContent = fetchUrl(url)
                if (rawContent.isNullOrBlank() && isYouTube) {
                    // Try alternative formats if original URL failed or returned empty
                    if (url.contains("&fmt=vtt")) {
                        rawContent = fetchUrl(url.replace("&fmt=vtt", ""))
                    } else if (url.contains("?fmt=vtt")) {
                        rawContent = fetchUrl(url.replace("?fmt=vtt", ""))
                    }
                    if (rawContent.isNullOrBlank()) {
                        val sep = if (url.contains("?")) "&" else "?"
                        rawContent = fetchUrl("$url${sep}fmt=json3") ?: fetchUrl("$url${sep}fmt=vtt")
                    }
                }
                if (rawContent.isNullOrBlank()) {
                    // Retry without referer (some endpoints prefer direct get without referer)
                    rawContent = fetchUrl(url, withReferer = false)
                }

                if (!rawContent.isNullOrBlank()) {
                    val cleanContent = rawContent.trim().removePrefix("\uFEFF")
                    val fmt = when {
                        option.format.contains("json", ignoreCase = true) -> com.example.subtitles.SubtitleFormat.JSON
                        option.format.contains("vtt", ignoreCase = true) || url.contains(".vtt", ignoreCase = true) -> com.example.subtitles.SubtitleFormat.VTT
                        option.format.contains("srt", ignoreCase = true) || url.contains(".srt", ignoreCase = true) -> com.example.subtitles.SubtitleFormat.SRT
                        option.format.contains("ass", ignoreCase = true) || url.contains(".ass", ignoreCase = true) -> com.example.subtitles.SubtitleFormat.ASS
                        else -> com.example.subtitles.SubtitleFormat.UNKNOWN
                    }
                    val cues = com.example.subtitles.SubtitleParser.parse(cleanContent, fmt)
                    if (cues.isNotEmpty()) {
                        // Immediately show the cues to the user without blocking
                        _bilibiliCues.value = cues
                        _subtitleMode.value = if (isBili) SubtitleMode.BILIBILI_TRANSLATED else SubtitleMode.EXTERNAL_PROVIDER
                        Log.i("PlaybackSession", "Loaded ${cues.size} caption cues for ${option.languageName} (${option.languageCode}) from $url")
                    }
                }
            } catch (e: Exception) {
                Log.w("PlaybackSession", "Failed to load caption track: ${e.message}")
            }
        }
    }

    fun loadAndPlayStream(
        context: Context? = null,
        streamData: StreamData?,
        streamOption: PlayableStreamOption?,
        captionOption: CaptionOption? = null,
        initialPos: Long = 0L,
        hlsUrl: String? = null
    ) {
        prepareAndPlay(context, streamData, streamOption, captionOption, initialPos, hlsUrl)
    }

    fun selectAudioTrack(option: AudioTrackOption) {
        userPreferredAudioLanguage = option.languageCode.ifBlank { null }
        playerCore?.selectAudioTrack(option)
    }

    fun setPreferredAudioLanguage(languageCode: String) {
        userPreferredAudioLanguage = if (languageCode != "auto") languageCode else null
        playerCore?.setPreferredAudioLanguage(languageCode)
    }

    fun scheduleControlsAutoHide(delayMs: Long = 2700L) {
        autoHideControlsJob?.cancel()
        autoHideControlsJob = scope.launch {
            delay(delayMs)
            _areControlsVisible.value = false
        }
    }

    fun setControlsVisibility(visible: Boolean) {
        _areControlsVisible.value = visible
        if (visible) scheduleControlsAutoHide() else autoHideControlsJob?.cancel()
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
        if (next) scheduleControlsAutoHide(autoHideDelayMs) else autoHideControlsJob?.cancel()
    }

    fun clearPlaybackEnded() {
        _playbackEnded.value = false
    }

    fun notifyFirstFrameRendered() {
        _firstFrameRendered.value = true
    }

    fun resetFirstFrameState() {
        _firstFrameRendered.value = false
    }

    fun hasLoadedMedia(): Boolean = currentLoadedMediaKey != null && (playerCore?.player?.mediaItemCount ?: 0) > 0

    fun stopAndClear() {
        autoHideControlsJob?.cancel()
        resumeController.clearPendingResumePosition()
        _activeStreamData.value?.videoId?.let { vid ->
            resumeController.onPositionUpdate(vid, _currentPositionMs.value, _durationMs.value)
        }
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
        com.example.smartskip.SmartSkipPlayerEngine.reset()
        playerCore?.resetPlayback()
    }

    fun releasePlayer() {
        autoHideControlsJob?.cancel()
        currentLoadedMediaKey = null
        _activeStreamData.value = null
        _isPlaying.value = false
        playerCore?.release()
        playerCore = null
    }

    fun setPlaybackFailedListener(listener: ((Int?) -> Unit)?) {
        recoveryManager.setPlaybackFailedListener(listener)
    }

    private fun onQueueActiveVideoChanged(streamData: StreamData, videoItem: com.example.model.VideoItem) {
        _activeStreamData.value = streamData
        _bilibiliSubtitleTracks.value = streamData.captionOptions
        _selectedSubtitleTrack.value = streamData.captionOptions.firstOrNull()
        _currentPositionMs.value = 0L
        _durationMs.value = (videoItem.durationSeconds.takeIf { it > 0 } ?: 0L) * 1000L
        _progressFraction.value = 0f
        _playerError.value = null
        _firstFrameRendered.value = false
        _playbackEnded.value = false
        _isPlaying.value = true
        playerCore?.setPlaybackSpeed(_playbackSpeed.value)

        VideoEffectsManager.onVideoChanged(streamData.videoId)
        VideoEnhancementEngine.onVideoLoaded(
            videoId = streamData.videoId,
            title = streamData.title,
            channel = streamData.channelName,
            tags = null,
            description = streamData.description,
            width = 0,
            height = 0
        )
        com.example.smartskip.SmartSkipPlayerEngine.onVideoChanged(
            context = appContext,
            videoId = streamData.videoId,
            durationMs = _durationMs.value,
            title = streamData.title,
            channelName = streamData.channelName,
            providerId = streamData.providerId
        )
        SubtitleManager.resolveSubtitlesForPlayback(
            context = appContext,
            streamData = streamData,
            onUsableSubtitleFound = null,
            onFallbackToWhisper = null
        )
        scope.launch(Dispatchers.IO) {
            try {
                val videoRepo = VideoCacheRepository(appContext)
                videoRepo.cacheVideoMetadata(
                    videoId = streamData.videoId,
                    title = streamData.title,
                    channelName = streamData.channelName,
                    thumbnailUrl = streamData.thumbnailUrl ?: "https://i.ytimg.com/vi/${streamData.videoId}/hqdefault.jpg",
                    description = streamData.description,
                    duration = videoItem.formattedDuration,
                    providerId = streamData.providerId
                )
            } catch (_: Throwable) {}
        }
    }

    val playbackQueue: StateFlow<List<com.example.model.VideoItem>>
        get() = queueManager.videoQueue

    val queueItems: StateFlow<List<com.example.ui.player.queue.MediaQueueItem>>
        get() = queueManager.queueItems

    val currentQueueIndex: StateFlow<Int>
        get() = queueManager.currentQueueIndex

    val hasNextItem: StateFlow<Boolean>
        get() = queueManager.hasNextItem

    val hasPreviousItem: StateFlow<Boolean>
        get() = queueManager.hasPreviousItem

    fun addToQueue(video: com.example.model.VideoItem) {
        queueManager.addToQueue(video)
    }

    fun addPlaylistToQueue(videos: List<com.example.model.VideoItem>) {
        queueManager.addVideosToQueue(videos, playNext = false)
    }

    fun playNextInQueue(video: com.example.model.VideoItem) {
        queueManager.playNextInQueue(video)
    }

    fun removeFromQueue(index: Int) {
        queueManager.removeFromQueue(index)
    }

    fun removeFromQueue(video: com.example.model.VideoItem) {
        queueManager.removeFromQueue(video)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        queueManager.moveQueueItem(fromIndex, toIndex)
    }

    fun clearQueue() {
        queueManager.clearQueue()
    }

    fun skipToQueueIndex(index: Int) {
        queueManager.skipToQueueIndex(index)
    }

    fun skipToNext(): Boolean {
        return queueManager.skipToNext()
    }

    fun skipToPrevious(): Boolean {
        return queueManager.skipToPrevious()
    }
}
