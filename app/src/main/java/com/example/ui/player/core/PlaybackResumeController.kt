package com.example.ui.player.core

import android.content.Context
import android.util.Log
import com.example.util.PlaybackResumeManager

/**
 * Manages video playback resume state, position tracking, and persistence.
 */
class PlaybackResumeController(private val contextProvider: () -> Context?) {

    private var pendingResumePositionMs: Long? = null

    fun getPendingResumePosition(): Long? = pendingResumePositionMs

    fun setPendingResumePosition(pos: Long?) {
        pendingResumePositionMs = pos
    }

    fun clearPendingResumePosition() {
        pendingResumePositionMs = null
    }

    /**
     * Determines effective start position based on explicit initial seek, quality switches, and saved database progress.
     */
    fun resolveEffectiveResumePosition(
        context: Context,
        videoId: String?,
        initialPos: Long,
        isQualitySwitch: Boolean,
        previousPos: Long
    ): Long {
        return when {
            initialPos > 0L -> initialPos
            isQualitySwitch -> previousPos
            !videoId.isNullOrBlank() -> PlaybackResumeManager.getSavedPosition(context, videoId)
            else -> 0L
        }
    }

    /**
     * Persists playback progress for a video if valid.
     */
    fun onPositionUpdate(videoId: String?, currentPos: Long, durationMs: Long) {
        if (videoId.isNullOrBlank() || currentPos <= 0L) return
        val ctx = contextProvider() ?: return
        try {
            PlaybackResumeManager.savePosition(ctx, videoId, currentPos, durationMs)
        } catch (e: Exception) {
            Log.w("PlaybackResumeController", "Failed to save position for $videoId: ${e.message}")
        }
    }
}
