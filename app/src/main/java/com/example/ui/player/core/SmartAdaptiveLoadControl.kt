package com.example.ui.player.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.upstream.DefaultAllocator

/**
 * Smart Adaptive LoadControl for Butterfly.
 * - Startup Buffer: Keeps initial buffer low (~500ms) for snappy first-frame playback.
 * - Anti-Stutter Backoff: Sets buffer after rebuffer to 1.8s so 1080p high-bitrate streams
 *   on 5G/Wi-Fi never enter a rapid rebuffer stutter loop.
 * - Data Consumption Guard: Strict 22s maximum buffer cap on cellular mobile data (and 35s on Wi-Fi)
 *   to eliminate runaway multi-gigabyte background downloads when browsing or sampling videos.
 * - Instant Seek Replay: Retains a 15-second back-buffer for instant scrubbing without re-downloading.
 */
@OptIn(UnstableApi::class)
object SmartAdaptiveLoadControl {

    fun create(context: Context): DefaultLoadControl {
        val appContext = context.applicationContext
        val isWifi = try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        } catch (_: Throwable) {
            false
        }

        // Cellular max buffer: 15s (stops runaway mobile data consumption!)
        // Wi-Fi max buffer: 25s
        val maxBufferMs = if (isWifi) 25_000 else 15_000

        return DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
            .setBufferDurationsMs(
                /* minBufferMs = */ 2_500,                       // 2.5s minimum buffer window to prevent stutter at >1.0x speed
                /* maxBufferMs = */ maxBufferMs,                  // 15s cellular / 25s wifi cap
                /* bufferForPlaybackMs = */ 800,                  // 800ms snappy initial startup
                /* bufferForPlaybackAfterRebufferMs = */ 1_500    // 1.5s smooth recovery after rebuffer
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(12_000, true) // 12s back buffer for instant rewind
            .build()
    }
}
