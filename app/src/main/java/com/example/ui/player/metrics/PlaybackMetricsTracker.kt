package com.example.ui.player.metrics

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks real-time video playback performance, buffer health, network throughput,
 * and media cache efficiency across all streaming sources.
 */
object PlaybackMetricsTracker {
    private const val TAG = "PlaybackMetricsTracker"

    private val _startupLatencyMs = MutableStateFlow(0L)
    val startupLatencyMs: StateFlow<Long> = _startupLatencyMs.asStateFlow()

    private val _rebufferCount = MutableStateFlow(0)
    val rebufferCount: StateFlow<Int> = _rebufferCount.asStateFlow()

    private val _totalRebufferDurationMs = MutableStateFlow(0L)
    val totalRebufferDurationMs: StateFlow<Long> = _totalRebufferDurationMs.asStateFlow()

    private val _bufferDurationSec = MutableStateFlow(0f)
    val bufferDurationSec: StateFlow<Float> = _bufferDurationSec.asStateFlow()

    private val _currentBitrateKbps = MutableStateFlow(0)
    val currentBitrateKbps: StateFlow<Int> = _currentBitrateKbps.asStateFlow()

    private val _currentResolution = MutableStateFlow("Auto")
    val currentResolution: StateFlow<String> = _currentResolution.asStateFlow()

    private val _measuredThroughputMbps = MutableStateFlow(0f)
    val measuredThroughputMbps: StateFlow<Float> = _measuredThroughputMbps.asStateFlow()

    private val _networkBytesDownloaded = MutableStateFlow(0L)
    val networkBytesDownloaded: StateFlow<Long> = _networkBytesDownloaded.asStateFlow()

    private val _cachedBytesRead = MutableStateFlow(0L)
    val cachedBytesRead: StateFlow<Long> = _cachedBytesRead.asStateFlow()

    private val _cacheHitPercentage = MutableStateFlow(0f)
    val cacheHitPercentage: StateFlow<Float> = _cacheHitPercentage.asStateFlow()

    private val _lastSeekLatencyMs = MutableStateFlow(0L)
    val lastSeekLatencyMs: StateFlow<Long> = _lastSeekLatencyMs.asStateFlow()

    private val _currentCdnHost = MutableStateFlow("Direct")
    val currentCdnHost: StateFlow<String> = _currentCdnHost.asStateFlow()

    private val _connectionType = MutableStateFlow("Cellular / Wi-Fi")
    val connectionType: StateFlow<String> = _connectionType.asStateFlow()

    private val _showDiagnosticsOverlay = MutableStateFlow(false)
    val showDiagnosticsOverlay: StateFlow<Boolean> = _showDiagnosticsOverlay.asStateFlow()

    // Internal timing state
    @Volatile
    private var preparationStartTimeMs: Long = 0L

    @Volatile
    private var rebufferStartTimeMs: Long = 0L

    @Volatile
    private var seekStartTimeMs: Long = 0L

    private val netBytesCounter = AtomicLong(0L)
    private val cacheBytesCounter = AtomicLong(0L)

    fun startPreparation(mediaUrl: String) {
        preparationStartTimeMs = System.currentTimeMillis()
        val host = runCatching { Uri.parse(mediaUrl).host }.getOrNull() ?: "Unknown CDN"
        _currentCdnHost.value = host
        _startupLatencyMs.value = 0L
    }

    fun onFirstFrameRendered() {
        if (preparationStartTimeMs > 0L) {
            val latency = System.currentTimeMillis() - preparationStartTimeMs
            _startupLatencyMs.value = latency
            Log.i(TAG, "Video startup completed in ${latency}ms")
        }
        if (seekStartTimeMs > 0L) {
            val seekLatency = System.currentTimeMillis() - seekStartTimeMs
            _lastSeekLatencyMs.value = seekLatency
            seekStartTimeMs = 0L
        }
    }

    fun onRebufferStarted() {
        if (rebufferStartTimeMs == 0L) {
            rebufferStartTimeMs = System.currentTimeMillis()
            _rebufferCount.value += 1
            Log.w(TAG, "Rebuffering started (count=${_rebufferCount.value})")
        }
    }

    fun onRebufferEnded() {
        if (rebufferStartTimeMs > 0L) {
            val rebufferDuration = System.currentTimeMillis() - rebufferStartTimeMs
            _totalRebufferDurationMs.value += rebufferDuration
            rebufferStartTimeMs = 0L
            Log.i(TAG, "Rebuffering resolved in ${rebufferDuration}ms")
        }
    }

    fun onSeekStarted() {
        seekStartTimeMs = System.currentTimeMillis()
    }

    fun onSeekCompleted() {
        if (seekStartTimeMs > 0L) {
            val seekLatency = System.currentTimeMillis() - seekStartTimeMs
            _lastSeekLatencyMs.value = seekLatency
            seekStartTimeMs = 0L
            Log.d(TAG, "Seek rendered in ${seekLatency}ms")
        }
    }

    fun recordNetworkBytes(bytes: Long) {
        if (bytes <= 0L) return
        val totalNet = netBytesCounter.addAndGet(bytes)
        _networkBytesDownloaded.value = totalNet
        recalculateCacheHitRatio()
    }

    fun recordCachedBytes(bytes: Long) {
        if (bytes <= 0L) return
        val totalCache = cacheBytesCounter.addAndGet(bytes)
        _cachedBytesRead.value = totalCache
        recalculateCacheHitRatio()
    }

    private fun recalculateCacheHitRatio() {
        val c = cacheBytesCounter.get().toFloat()
        val n = netBytesCounter.get().toFloat()
        val total = c + n
        if (total > 0f) {
            _cacheHitPercentage.value = ((c / total) * 100f).coerceIn(0f, 100f)
        }
    }

    fun updateThroughput(mbps: Float) {
        if (mbps > 0f) {
            _measuredThroughputMbps.value = mbps
        }
    }

    fun updateBufferHealth(bufferedSec: Float) {
        _bufferDurationSec.value = bufferedSec.coerceAtLeast(0f)
    }

    fun updateVideoFormat(width: Int, height: Int, bitrate: Int) {
        if (width > 0 && height > 0) {
            _currentResolution.value = "${width}x${height} (${height}p)"
        }
        if (bitrate > 0) {
            _currentBitrateKbps.value = bitrate / 1000
        }
    }

    fun updateNetworkType(type: String) {
        _connectionType.value = type
    }

    fun toggleDiagnosticsOverlay() {
        _showDiagnosticsOverlay.value = !_showDiagnosticsOverlay.value
    }

    fun setDiagnosticsOverlayVisible(visible: Boolean) {
        _showDiagnosticsOverlay.value = visible
    }

    fun resetCacheMetrics() {
        cacheBytesCounter.set(0L)
        _cachedBytesRead.value = 0L
        _cacheHitPercentage.value = 0f
    }

    fun resetSession() {
        preparationStartTimeMs = 0L
        rebufferStartTimeMs = 0L
        seekStartTimeMs = 0L
        _startupLatencyMs.value = 0L
        _rebufferCount.value = 0
        _totalRebufferDurationMs.value = 0L
        _bufferDurationSec.value = 0f
        _currentBitrateKbps.value = 0
        _currentResolution.value = "Auto"
        _lastSeekLatencyMs.value = 0L
    }
}
