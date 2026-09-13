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

    private val _areControlsVisible = MutableStateFlow(true)
    val areControlsVisible: StateFlow<Boolean> = _areControlsVisible.asStateFlow()

    private val _playbackEnded = MutableStateFlow(false)
    val playbackEnded: StateFlow<Boolean> = _playbackEnded.asStateFlow()

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
            updatePositions(exo)

            if (state == Player.STATE_READY) {
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
                _progressFraction.value = 1f
            } else {
                _playbackEnded.value = false
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            playerCore?.let { core ->
                _audioTracks.value = core.parseAudioTracks(tracks)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val activeProvider = _activeStreamData.value?.providerId
            val (diagnostics, httpStatus) = recoveryManager.diagnoseError(error, activeProvider)

            val activeData = _activeStreamData.value
            val currentOption = activeData?.selectedStreamOption
            val availableOptions = activeData?.availableStreamOptions.orEmpty()

            val failedUrl = currentOption?.videoUrl ?: playerCore?.player?.currentMediaItem?.localConfiguration?.uri?.toString()
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
        if (cues.isNotEmpty() && _subtitleMode.value != SubtitleMode.OFF && _subtitleMode.value != SubtitleMode.AI_LIVE_CAPTIONS && _subtitleMode.value != SubtitleMode.EXTERNAL_PROVIDER) {
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

        try {
            player.stop()
            player.clearMediaItems()

            fun sanitizeMediaUrl(input: String?): String? {
                if (input.isNullOrBlank()) return null
                var trimmed = input.trim()
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
                        .replace("\n", "").replace("\r", "").replace("\t", "")
                        .replace(" ", "%20").replace("\"", "%22").replace("<", "%3C")
                        .replace(">", "%3E").replace("\\", "/")
                    val parsed = Uri.parse(sanitized)
                    if (parsed.scheme.isNullOrEmpty()) null else sanitized
                } catch (_: Throwable) {
                    null
                }
            }

            fun buildMediaItem(
                inputUrl: String,
                format: String? = null,
                subtitles: List<MediaItem.SubtitleConfiguration> = emptyList()
            ): MediaItem? {
                val cleanUrl = sanitizeMediaUrl(inputUrl) ?: return null
                val uri = Uri.parse(cleanUrl)
                val lowerUrl = cleanUrl.lowercase()
                val lowerFormat = format?.lowercase()
                val builder = MediaItem.Builder().setUri(uri)

                val isExplicitHls = lowerFormat == "hls" || lowerFormat == "m3u8" || lowerUrl.endsWith(".m3u8") || lowerUrl.contains(".m3u8?") || lowerUrl.contains("/hls/")
                val isExplicitMpd = lowerFormat == "mpd" || lowerUrl.endsWith(".mpd") || lowerUrl.contains(".mpd?")
                val isExplicitMkv = lowerFormat == "mkv" || lowerUrl.endsWith(".mkv")
                val isExplicitAudioWebm = lowerFormat == "audio_webm" || lowerUrl.contains("mime=audio%2fwebm") || lowerUrl.contains("mime=audio/webm")
                val isExplicitVideoWebm = lowerFormat == "webm" || lowerUrl.contains("mime=video%2fwebm") || lowerUrl.contains("mime=video/webm") || lowerUrl.endsWith(".webm")
                val isExplicitAudioMp4 = lowerFormat == "audio_mp4" || lowerFormat == "m4a" || lowerUrl.contains("mime=audio%2fmp4") || lowerUrl.contains("mime=audio/mp4")
                val isExplicitVideoMp4 = lowerFormat == "video_mp4" || lowerFormat == "mp4" || lowerUrl.contains(".mp4") || lowerUrl.contains(".m4s") || lowerUrl.contains("mime=video%2fmp4") || lowerUrl.contains("mime=video/mp4")

                if (isExplicitHls) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
                else if (isExplicitMpd) builder.setMimeType(MimeTypes.APPLICATION_MPD)
                else if (isExplicitMkv) builder.setMimeType(MimeTypes.VIDEO_MATROSKA)
                else if (isExplicitAudioWebm) builder.setMimeType(MimeTypes.AUDIO_WEBM)
                else if (isExplicitVideoWebm) builder.setMimeType(MimeTypes.VIDEO_WEBM)
                else if (isExplicitAudioMp4) builder.setMimeType(MimeTypes.AUDIO_MP4)
                else if (isExplicitVideoMp4) builder.setMimeType(MimeTypes.VIDEO_MP4)

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

                // Subtitles provided by Decryptor or other multi-stream providers
                if (streamOption.subtitles.isNotEmpty()) {
                    streamOption.subtitles.forEach { sub ->
                        val cleanSubUrl = sanitizeMediaUrl(sub.url)
                        if (cleanSubUrl != null) {
                            val isVtt = sub.format.equals("vtt", ignoreCase = true) || cleanSubUrl.contains(".vtt", ignoreCase = true)
                            val mimeType = if (isVtt) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
                            val config = MediaItem.SubtitleConfiguration.Builder(Uri.parse(cleanSubUrl))
                                .setMimeType(mimeType)
                                .setLanguage(sub.languageCode)
                                .setLabel(sub.languageName)
                                .setSelectionFlags(if (sub.languageCode.startsWith("en", ignoreCase = true)) C.SELECTION_FLAG_DEFAULT else 0)
                                .build()
                            subtitleConfigs.add(config)
                        }
                    }
                }

                if (streamOption.isMuxed && !vUrl.isNullOrEmpty()) {
                    val mediaSourceFactory = MediaSourceFactoryHelper.createMediaSourceFactory(vUrl, streamData, streamOption.headers)
                    val item = buildMediaItem(vUrl, streamOption.format, subtitleConfigs)
                    if (item != null) {
                        val mediaSource = mediaSourceFactory.createMediaSource(item)
                        player.setMediaSource(mediaSource)
                        mediaSourceSet = true
                    }
                } else if (!streamOption.isMuxed && !vUrl.isNullOrEmpty() && !aUrl.isNullOrEmpty()) {
                    val videoSourceFactory = MediaSourceFactoryHelper.createMediaSourceFactory(vUrl, streamData, streamOption.headers)
                    val audioHeaders = if (streamOption.audioHeaders.isNotEmpty()) streamOption.audioHeaders else streamOption.headers
                    val audioSourceFactory = MediaSourceFactoryHelper.createMediaSourceFactory(aUrl, streamData, audioHeaders)

                    val videoItem = buildMediaItem(vUrl, streamOption.format.ifEmpty { "video_mp4" }, subtitleConfigs)
                    val audioItem = buildMediaItem(aUrl, if (aUrl.contains("webm")) "audio_webm" else "audio_mp4")
                    if (videoItem != null && audioItem != null) {
                        val videoSource = videoSourceFactory.createMediaSource(videoItem)
                        val audioSource = audioSourceFactory.createMediaSource(audioItem)
                        try {
                            val mergedSource = MergingMediaSource(true, true, videoSource, audioSource)
                            player.setMediaSource(mergedSource)
                            mediaSourceSet = true
                        } catch (e: Exception) {
                            Log.w("PlaybackSession", "MergingMediaSource failed, falling back to videoSource: ${e.message}")
                            player.setMediaSource(videoSource)
                            mediaSourceSet = true
                        }
                    } else if (videoItem != null) {
                        val videoSource = videoSourceFactory.createMediaSource(videoItem)
                        player.setMediaSource(videoSource)
                        mediaSourceSet = true
                    }
                } else if (!vUrl.isNullOrEmpty()) {
                    val mediaSourceFactory = MediaSourceFactoryHelper.createMediaSourceFactory(vUrl, streamData, streamOption.headers)
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
                        val mediaSourceFactory = MediaSourceFactoryHelper.createMediaSourceFactory(cleanHls, streamData)
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
                        val mediaSourceFactory = MediaSourceFactoryHelper.createMediaSourceFactory(cleanRaw, streamData, streamOption?.headers ?: emptyMap())
                        val mediaSource = mediaSourceFactory.createMediaSource(item)
                        player.setMediaSource(mediaSource)
                        mediaSourceSet = true
                    }
                }
            }

            if (!mediaSourceSet) {
                _playerError.value = "Unable to parse valid video stream URL"
                return
            }

            if (effectiveResumePos > 0L) {
                player.seekTo(effectiveResumePos)
            }

            PlaybackPipelineTracker.logPrepare(
                urlSnippet = effectivePlayableUrl.take(60),
                headersCount = streamOption?.headers?.size ?: 0
            )

            player.prepare()
            player.playWhenReady = true
            _isPlaying.value = true

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
        val player = playerCore?.player
        val playerDur = player?.duration?.takeIf { it > 0 && it != C.TIME_UNSET } ?: _durationMs.value
        val safeMax = if (playerDur > 1000L) playerDur - 500L else if (playerDur > 0L) playerDur else Long.MAX_VALUE
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
        _selectedSubtitleTrack.value = option
        if (option == null) {
            _bilibiliCues.value = emptyList()
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url(option.url)
                    .header("User-Agent", NetworkManager.DEFAULT_USER_AGENT)
                    .header("Referer", "https://www.bilibili.com/")
                    .build()
                val jsonStr = NetworkManager.scraperClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
                if (!jsonStr.isNullOrBlank()) {
                    val rawCues = SubtitleTranslator.parseBilibiliSubtitleJson(jsonStr)
                    val targetLang = _targetCaptionLanguage.value
                    val translatedCues = SubtitleTranslator.translateCues(rawCues, targetLang = targetLang)
                    _bilibiliCues.value = translatedCues
                    if (_subtitleMode.value == SubtitleMode.OFF) {
                        _subtitleMode.value = SubtitleMode.BILIBILI_TRANSLATED
                    }
                }
            } catch (e: Exception) {
                Log.w("PlaybackSession", "Failed to load Bilibili subtitle JSON: ${e.message}")
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
        playerCore?.selectAudioTrack(option)
    }

    fun setPreferredAudioLanguage(languageCode: String) {
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
}
