package com.example.ui.player.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.example.ui.player.metrics.PlaybackMetricsTracker
import java.util.concurrent.ConcurrentHashMap

/**
 * Adaptive Bandwidth & Network Throughput Coordinator.
 * Replaces hardcoded assumptions with network-aware conservative initial estimates
 * (3.5–4.5 Mbps initial safe baseline, never forcing 15 Mbps blindly), then rapidly
 * adapts to real measured throughput per CDN/source to select sustainable 1080p quality.
 */
@OptIn(UnstableApi::class)
object AdaptiveBandwidthManager {
    private const val TAG = "AdaptiveBandwidthMgr"

    // Per-host rolling throughput estimate in bits per second (EMA smoothing factor alpha=0.3)
    private val cdnRollingBitrateEstimates = ConcurrentHashMap<String, Long>()

    @Volatile
    private var bandwidthMeterInstance: DefaultBandwidthMeter? = null

    fun getBandwidthMeter(context: Context): DefaultBandwidthMeter {
        val existing = bandwidthMeterInstance
        if (existing != null) return existing

        synchronized(this) {
            val s = bandwidthMeterInstance
            if (s != null) return s

            val appContext = context.applicationContext
            val netTypeName = detectCurrentNetworkTypeName(appContext)
            val initialEstimate = getConservativeInitialEstimateForNetwork(netTypeName)

            val builder = DefaultBandwidthMeter.Builder(appContext)
                .setInitialBitrateEstimate(initialEstimate)
                .setInitialBitrateEstimate(C.NETWORK_TYPE_WIFI, 4_500_000L)
                .setInitialBitrateEstimate(C.NETWORK_TYPE_ETHERNET, 5_000_000L)
                .setInitialBitrateEstimate(C.NETWORK_TYPE_4G, 3_500_000L)
                .setInitialBitrateEstimate(C.NETWORK_TYPE_3G, 1_200_000L)
                .setInitialBitrateEstimate(C.NETWORK_TYPE_OTHER, 2_500_000L)
                .setResetOnNetworkTypeChange(true)
                .setSlidingWindowMaxWeight(2000)

            val meter = builder.build()
            val mainHandler = Handler(Looper.getMainLooper())

            meter.addEventListener(mainHandler) { elapsedMs, bytesTransferred, bitrateEstimate ->
                if (elapsedMs > 0 && bytesTransferred > 0) {
                    val instantThroughputBps = (bytesTransferred * 8L * 1000L) / elapsedMs
                    val smoothMbps = (bitrateEstimate.toFloat() / 1_000_000f).coerceAtLeast(0.1f)

                    PlaybackMetricsTracker.updateThroughput(smoothMbps)
                    PlaybackMetricsTracker.recordNetworkBytes(bytesTransferred)

                    val activeHost = PlaybackMetricsTracker.currentCdnHost.value
                    if (activeHost.isNotBlank() && activeHost != "Unknown CDN" && activeHost != "Direct") {
                        recordHostSample(activeHost, instantThroughputBps)
                    }
                }
            }

            bandwidthMeterInstance = meter
            PlaybackMetricsTracker.updateNetworkType(netTypeName)
            Log.i(TAG, "Initialized DefaultBandwidthMeter with initial estimate=${initialEstimate / 1_000_000f} Mbps ($netTypeName)")
            return meter
        }
    }

    private fun recordHostSample(host: String, sampleBps: Long) {
        val prev = cdnRollingBitrateEstimates[host] ?: sampleBps
        // Exponential Moving Average: 70% previous + 30% new sample
        val updated = ((prev * 0.7) + (sampleBps * 0.3)).toLong()
        cdnRollingBitrateEstimates[host] = updated
    }

    fun getEstimatedBitrateForHost(host: String?): Long? {
        if (host.isNullOrBlank()) return null
        return cdnRollingBitrateEstimates[host]
    }

    private fun detectCurrentNetworkTypeName(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "Broadband Network"
        val activeNet = cm.activeNetwork ?: return "Offline"
        val caps = cm.getNetworkCapabilities(activeNet) ?: return "Broadband Network"

        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                try {
                    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    when (tm?.dataNetworkType) {
                        TelephonyManager.NETWORK_TYPE_NR -> "Cellular 5G"
                        TelephonyManager.NETWORK_TYPE_LTE -> "Cellular 4G LTE"
                        else -> "Cellular Mobile"
                    }
                } catch (_: SecurityException) {
                    "Cellular Mobile"
                }
            }
            else -> "Network"
        }
    }

    private fun getConservativeInitialEstimateForNetwork(networkTypeName: String): Long {
        return when {
            networkTypeName.contains("5G", ignoreCase = true) -> 3_500_000L  // ~3.5 Mbps safe starting estimate (rapidly increases via meter)
            networkTypeName.contains("Wi-Fi", ignoreCase = true) || networkTypeName.contains("Ethernet", ignoreCase = true) -> 4_500_000L
            networkTypeName.contains("4G", ignoreCase = true) || networkTypeName.contains("LTE", ignoreCase = true) -> 3_000_000L
            networkTypeName.contains("3G", ignoreCase = true) -> 1_200_000L
            else -> 2_500_000L // 2.5 Mbps default baseline
        }
    }
}
