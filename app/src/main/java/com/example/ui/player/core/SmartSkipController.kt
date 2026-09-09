package com.example.ui.player.core

import android.content.Context
import com.example.smartskip.SmartSkipPlayerEngine

/**
 * Handles SmartSkip coordination with the active playback position.
 */
class SmartSkipController(private val contextProvider: () -> Context?) {

    fun onPositionUpdate(currentPositionMs: Long) {
        val ctx = contextProvider() ?: return
        try {
            SmartSkipPlayerEngine.onPlaybackPositionUpdate(ctx, currentPositionMs)
        } catch (_: Exception) {}
    }
}
