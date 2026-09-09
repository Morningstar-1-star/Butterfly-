package com.example.ui.player.core

import android.content.Context
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.example.model.AudioTrackOption
import com.example.ui.player.audio.AudioEnhancementEngine

/**
 * Low-level ExoPlayer / Media3 core manager.
 * Manages player instance creation, renderers factory, audio enhancements sink,
 * load control, and audio/video track selection.
 */
class PlayerCore(
    private val context: Context,
    private val listener: Player.Listener
) {
    private val TAG = "PlayerCore"

    var player: ExoPlayer? = null
        private set

    init {
        initializePlayer()
    }

    private fun initializePlayer() {
        val appContext = context.applicationContext
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000,   // minBufferMs (15s minimum buffer to prevent bandwidth thrashing and memory pressure)
                60_000,   // maxBufferMs (60s)
                800,      // bufferForPlaybackMs (800ms for instant initial playback startup)
                1_500     // bufferForPlaybackAfterRebufferMs (1.5s fast resume after buffering)
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(10_000, true)
            .build()

        val audioEnhancementProcessor = AudioEnhancementEngine.getAudioProcessor()
        val renderersFactory = object : DefaultRenderersFactory(appContext) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                return try {
                    DefaultAudioSink.Builder(context)
                        .setAudioProcessors(arrayOf(audioEnhancementProcessor))
                        .setEnableFloatOutput(false)
                        .setEnableAudioTrackPlaybackParams(true)
                        .build()
                } catch (e: Throwable) {
                    Log.w(TAG, "AudioSink build fallback: ${e.message}")
                    null
                }
            }
        }.apply {
            setEnableDecoderFallback(true)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        }

        val exo = ExoPlayer.Builder(appContext)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .build()

        exo.playWhenReady = true
        exo.addListener(listener)
        player = exo
    }

    fun play() {
        player?.play()
    }

    fun pause() {
        player?.pause()
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        player?.playbackParameters = PlaybackParameters(speed)
    }

    fun setRepeatMode(repeatMode: Int) {
        player?.repeatMode = repeatMode
    }

    fun parseAudioTracks(tracks: Tracks): List<AudioTrackOption> {
        val list = mutableListOf<AudioTrackOption>()
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
                        AudioTrackOption(
                            groupIndex = groupIdx,
                            trackIndex = i,
                            label = label,
                            languageCode = rawLang,
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
        return list
    }

    fun selectAudioTrack(option: AudioTrackOption) {
        val exo = player ?: return
        val tracks = exo.currentTracks
        var currentGroupIdx = 0
        for (group in tracks.groups) {
            if (currentGroupIdx == option.groupIndex && group.type == C.TRACK_TYPE_AUDIO) {
                val override = androidx.media3.common.TrackSelectionOverride(
                    group.mediaTrackGroup,
                    listOf(option.trackIndex)
                )
                exo.trackSelectionParameters = exo.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .addOverride(override)
                    .build()
                break
            }
            currentGroupIdx++
        }
    }

    fun setPreferredAudioLanguage(languageCode: String) {
        val exo = player ?: return
        exo.trackSelectionParameters = exo.trackSelectionParameters
            .buildUpon()
            .setPreferredAudioLanguage(languageCode)
            .build()
    }

    fun release() {
        try {
            player?.stop()
            player?.clearMediaItems()
            player?.removeListener(listener)
            player?.release()
            player = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing ExoPlayer: ${e.message}")
        }
    }
}
