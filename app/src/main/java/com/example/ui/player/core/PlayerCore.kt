package com.example.ui.player.core

import android.content.Context
import android.os.Build
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
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
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
                8_000,    // minBufferMs (8s fast buffer and low RAM footprint)
                30_000,   // maxBufferMs (30s)
                350,      // bufferForPlaybackMs (350ms for near-instant video playback start)
                750       // bufferForPlaybackAfterRebufferMs (750ms fast resume)
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(5_000, true)
            .build()

        val audioEnhancementProcessor = AudioEnhancementEngine.getAudioProcessor()
        val isEmulator = Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.PRODUCT.contains("sdk")

        val customMediaCodecSelector = MediaCodecSelector { mimeType, requiresSecure, requiresTunneling ->
            val decoders = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecure, requiresTunneling)
            if (isEmulator) {
                // In emulator environments, virtual hardware codecs (e.g. goldfish) often lack required system resources
                // and trigger CCodec "Failed to query component interface for required system resources: 6" warnings.
                // Prioritize and isolate standard software decoders (c2.android.*, OMX.google.*) which execute reliably.
                val swDecoders = decoders.filter {
                    it.softwareOnly || it.name.startsWith("c2.android.") || it.name.startsWith("OMX.google.")
                }
                if (swDecoders.isNotEmpty()) {
                    swDecoders
                } else {
                    decoders.sortedWith(
                        compareByDescending<MediaCodecInfo> {
                            it.softwareOnly || it.name.startsWith("c2.android.") || it.name.startsWith("OMX.google.")
                        }.thenBy { it.name.contains("goldfish", ignoreCase = true) }
                    )
                }
            } else {
                decoders
            }
        }

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
            setMediaCodecSelector(customMediaCodecSelector)
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

    fun resetPlayback() {
        try {
            player?.stop()
            player?.clearMediaItems()
        } catch (e: Exception) {
            Log.w(TAG, "Error resetting ExoPlayer playback: ${e.message}")
        }
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
