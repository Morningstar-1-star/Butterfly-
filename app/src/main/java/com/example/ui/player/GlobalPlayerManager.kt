package com.example.ui.player

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.example.model.AudioTrackOption
import com.example.model.CaptionOption
import com.example.model.PlayableStreamOption
import com.example.model.StreamData
import com.example.ui.player.session.PlaybackSession
import com.example.util.SubtitleCue
import kotlinx.coroutines.flow.StateFlow

/**
 * GlobalPlayerManager: Public facade for video playback across Butterfly.
 * Delegated to [PlaybackSession] for modular architecture and separation of concerns
 * (UI State -> PlaybackSession -> PlayerCore / Media3).
 */
object GlobalPlayerManager {

    enum class SubtitleMode {
        OFF,
        BILIBILI_ORIGINAL,
        BILIBILI_TRANSLATED,
        EXTERNAL_PROVIDER,
        AI_LIVE_CAPTIONS
    }

    private var sessionInstance: PlaybackSession? = null

    private fun getOrCreateSession(context: Context? = null): PlaybackSession {
        var s = sessionInstance
        if (s == null) {
            synchronized(this) {
                s = sessionInstance
                if (s == null) {
                    val ctx = context?.applicationContext ?: try {
                        com.example.MainApplication.appContext
                    } catch (_: Throwable) {
                        null
                    }
                    if (ctx != null) {
                        s = PlaybackSession(ctx)
                        sessionInstance = s
                    }
                }
            }
        }
        return s ?: throw IllegalStateException("GlobalPlayerManager requires Application Context")
    }

    val activeStreamData: StateFlow<StreamData?>
        get() = getOrCreateSession().activeStreamData

    val currentPositionMs: StateFlow<Long>
        get() = getOrCreateSession().currentPositionMs

    val durationMs: StateFlow<Long>
        get() = getOrCreateSession().durationMs

    val bufferedPositionMs: StateFlow<Long>
        get() = getOrCreateSession().bufferedPositionMs

    val progressFraction: StateFlow<Float>
        get() = getOrCreateSession().progressFraction

    val bufferedFraction: StateFlow<Float>
        get() = getOrCreateSession().bufferedFraction

    val isPlaying: StateFlow<Boolean>
        get() = getOrCreateSession().isPlaying

    val isBuffering: StateFlow<Boolean>
        get() = getOrCreateSession().isBuffering

    val playerError: StateFlow<String?>
        get() = getOrCreateSession().playerError

    val firstFrameRendered: StateFlow<Boolean>
        get() = getOrCreateSession().firstFrameRendered

    val audioTracks: StateFlow<List<AudioTrackOption>>
        get() = getOrCreateSession().audioTracks

    val subtitleMode: StateFlow<SubtitleMode>
        get() = getOrCreateSession().subtitleMode

    val bilibiliSubtitleTracks: StateFlow<List<CaptionOption>>
        get() = getOrCreateSession().bilibiliSubtitleTracks

    val selectedSubtitleTrack: StateFlow<CaptionOption?>
        get() = getOrCreateSession().selectedSubtitleTrack

    val bilibiliCues: StateFlow<List<SubtitleCue>>
        get() = getOrCreateSession().bilibiliCues

    val targetCaptionLanguage: StateFlow<String>
        get() = getOrCreateSession().targetCaptionLanguage

    val currentActiveSubtitleText: StateFlow<String>
        get() = getOrCreateSession().currentActiveSubtitleText

    val currentActiveTranslatedText: StateFlow<String>
        get() = getOrCreateSession().currentActiveTranslatedText

    val isLoopEnabled: StateFlow<Boolean>
        get() = getOrCreateSession().isLoopEnabled

    val videoAspectRatio: StateFlow<Float>
        get() = getOrCreateSession().videoAspectRatio

    val areControlsVisible: StateFlow<Boolean>
        get() = getOrCreateSession().areControlsVisible

    val playbackEnded: StateFlow<Boolean>
        get() = getOrCreateSession().playbackEnded

    fun getExoPlayer(context: Context): ExoPlayer {
        return getOrCreateSession(context).getExoPlayer()
    }

    fun prepareAndPlay(
        context: Context,
        streamData: StreamData?,
        streamOption: PlayableStreamOption?,
        captionOption: CaptionOption? = null,
        initialPos: Long = 0L,
        hlsUrl: String? = null
    ) {
        val session = getOrCreateSession(context)
        session.prepareAndPlay(context, streamData, streamOption, captionOption, initialPos, hlsUrl)
    }

    fun loadAndPlayStream(
        context: Context,
        streamData: StreamData?,
        streamOption: PlayableStreamOption?,
        captionOption: CaptionOption? = null,
        initialPos: Long = 0L,
        hlsUrl: String? = null
    ) {
        prepareAndPlay(context, streamData, streamOption, captionOption, initialPos, hlsUrl)
    }

    fun togglePlayPause() {
        sessionInstance?.togglePlayPause()
    }

    fun play() {
        sessionInstance?.play()
    }

    fun pause() {
        sessionInstance?.pause()
    }

    fun seekTo(positionMs: Long) {
        sessionInstance?.seekTo(positionMs)
    }

    fun seekForward(deltaMs: Long = 10000L) {
        sessionInstance?.seekForward(deltaMs)
    }

    fun seekBackward(deltaMs: Long = 10000L) {
        sessionInstance?.seekBackward(deltaMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        sessionInstance?.setPlaybackSpeed(speed)
    }

    fun setLoopVideo(enabled: Boolean, context: Context? = null) {
        sessionInstance?.setLoopVideo(enabled, context)
    }

    fun setSubtitleMode(mode: SubtitleMode, context: Context? = null) {
        sessionInstance?.setSubtitleMode(mode)
    }

    fun setTargetCaptionLanguage(langCode: String) {
        sessionInstance?.setTargetCaptionLanguage(langCode)
    }

    fun selectBilibiliSubtitleTrack(option: CaptionOption?) {
        sessionInstance?.selectBilibiliSubtitleTrack(option)
    }

    fun selectAudioTrack(option: AudioTrackOption) {
        sessionInstance?.selectAudioTrack(option)
    }

    fun setPreferredAudioLanguage(languageCode: String) {
        sessionInstance?.setPreferredAudioLanguage(languageCode)
    }

    fun scheduleControlsAutoHide(delayMs: Long = 2700L) {
        sessionInstance?.scheduleControlsAutoHide(delayMs)
    }

    fun setControlsVisibility(visible: Boolean) {
        sessionInstance?.setControlsVisibility(visible)
    }

    fun showControls(autoHideDelayMs: Long = 2700L) {
        sessionInstance?.showControls(autoHideDelayMs)
    }

    fun hideControls() {
        sessionInstance?.hideControls()
    }

    fun toggleControlsVisibility(autoHideDelayMs: Long = 2700L) {
        sessionInstance?.toggleControlsVisibility(autoHideDelayMs)
    }

    fun clearPlaybackEnded() {
        sessionInstance?.clearPlaybackEnded()
    }

    fun notifyFirstFrameRendered() {
        sessionInstance?.notifyFirstFrameRendered()
    }

    fun resetFirstFrameState() {
        sessionInstance?.resetFirstFrameState()
    }

    fun setPlaybackFailedListener(listener: ((Int?) -> Unit)?) {
        sessionInstance?.setPlaybackFailedListener(listener)
    }

    fun hasLoadedMedia(): Boolean = sessionInstance?.hasLoadedMedia() ?: false

    fun stopAndClear() {
        sessionInstance?.stopAndClear()
    }
}
