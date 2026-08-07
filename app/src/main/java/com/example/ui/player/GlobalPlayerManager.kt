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

    private var currentLoadedMediaKey: String? = null

    fun getExoPlayer(context: Context): ExoPlayer {
        if (exoPlayerInstance == null) {
            val player = ExoPlayer.Builder(context.applicationContext).build()
            player.playWhenReady = true
            player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                }

                override fun onPlaybackStateChanged(state: Int) {
                    _isPlaying.value = player.isPlaying
                    if (state == Player.STATE_ENDED) {
                        _isPlaying.value = false
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    _playerError.value = error.localizedMessage ?: "Playback error"
                    _isPlaying.value = false
                }
            })
            exoPlayerInstance = player
            startProgressTracker()
        }
        return exoPlayerInstance!!
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
        _activeStreamData.value = streamData
        _playerError.value = null

        val rawUrl = streamOption?.videoUrl ?: streamOption?.videoStream?.url ?: hlsUrl ?: embedUrl
        if (rawUrl.isNullOrEmpty()) {
            _isEmbedOrWebPage.value = true
            return
        }

        val urlLower = rawUrl.lowercase()
        val fmtLower = streamOption?.format?.lowercase() ?: ""
        val isEmbed = fmtLower == "embed" ||
                urlLower.startsWith("magnet:") ||
                urlLower.contains("embed") ||
                urlLower.contains("eporner.com") ||
                urlLower.contains("apijav") ||
                urlLower.contains("dailymotion.com") ||
                urlLower.contains("vimeo.com") ||
                urlLower.contains("peertube") ||
                urlLower.contains("nvembed") ||
                urlLower.contains("mvembed") ||
                (urlLower.startsWith("http") && !urlLower.contains(".mp4") && !urlLower.contains(".m3u8") && !urlLower.contains("googlevideo.com"))

        _isEmbedOrWebPage.value = isEmbed

        val mediaKey = "${streamData?.videoId}_${rawUrl}_${captionOption?.languageCode}"
        if (mediaKey == currentLoadedMediaKey && player.playbackState != Player.STATE_ENDED) {
            // Already loaded and playing this media, ensure playing
            player.playWhenReady = true
            _isPlaying.value = true
            return
        }

        currentLoadedMediaKey = mediaKey

        if (!isEmbed) {
            try {
                player.stop()
                player.clearMediaItems()

                val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")

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
                        player.setMediaItem(builder.build())
                    } else if (!streamOption.isMuxed && vUrl != null && aUrl != null) {
                        val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(MediaItem.fromUri(Uri.parse(vUrl)))
                        val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(MediaItem.fromUri(Uri.parse(aUrl)))

                        val mergedSource = MergingMediaSource(videoSource, audioSource)
                        player.setMediaSource(mergedSource)
                    }
                } else if (!hlsUrl.isNullOrEmpty()) {
                    val mediaItem = MediaItem.fromUri(Uri.parse(hlsUrl))
                    player.setMediaItem(mediaItem)
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
    }
}
