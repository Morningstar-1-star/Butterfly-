package com.example.plugin.manager

import android.content.Context
import android.util.Log
import com.example.intelligence.SourceIntelligenceEngine
import com.example.model.PlayableStreamOption
import com.example.model.StreamFailureReason
import com.example.model.StreamType
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.PluginStreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class RankedStream(
    val option: PlayableStreamOption,
    val score: Int,
    val providerId: String,
    val streamType: StreamType,
    val validationLatencyMs: Long
)

class StreamEngine(
    private val healthMonitor: ProviderHealthMonitor = ProviderHealthMonitor(),
    private val streamValidator: StreamValidator = StreamValidator(),
    private val context: Context? = null
) {

    private val intelligenceEngine: SourceIntelligenceEngine? by lazy {
        context?.let { SourceIntelligenceEngine.getInstance(it) }
    }

    suspend fun searchAndRankStreams(
        providers: List<ContentProviderApi>,
        queryOrId: String
    ): List<RankedStream> = coroutineScope {
        val activeProviders = providers.filter { provider ->
            healthMonitor.isProviderHealthy(provider.providerId)
        }

        val deferredResults = activeProviders.map { provider ->
            async(Dispatchers.IO) {
                fetchFromProviderWithTimeout(provider, queryOrId)
            }
        }

        val allStreamInfos = deferredResults.awaitAll().filterNotNull()
        val rankedStreams = mutableListOf<RankedStream>()

        for ((provider, streamInfo) in allStreamInfos) {
            val validStreams = validateAndScoreStreams(provider.providerId, provider.providerId, streamInfo)
            rankedStreams.addAll(validStreams)
        }

        // Sort descending by calculated score
        rankedStreams.sortedByDescending { it.score }
    }

    private suspend fun fetchFromProviderWithTimeout(
        provider: ContentProviderApi,
        queryOrId: String
    ): Pair<ContentProviderApi, PluginStreamInfo>? {
        val startTime = System.currentTimeMillis()
        intelligenceEngine?.recordRequestStart(provider.providerId)

        return try {
            val result = withTimeoutOrNull(6000L) { // 6s strict timeout per prompt
                provider.getStreams(queryOrId)
            }
            val latency = System.currentTimeMillis() - startTime

            if (result != null) {
                healthMonitor.recordSuccess(provider.providerId, latency)
                intelligenceEngine?.recordRequestSuccess(provider.providerId, latency)
                Pair(provider, result)
            } else {
                Log.w("StreamEngine", "Provider ${provider.providerId} timed out (>6s)")
                healthMonitor.recordFailure(provider.providerId, StreamFailureReason.TIMEOUT)
                null
            }
        } catch (e: Exception) {
            Log.e("StreamEngine", "Provider ${provider.providerId} failed: ${e.message}")
            healthMonitor.recordFailure(provider.providerId, StreamFailureReason.PARSING_FAILED)
            null
        }
    }

    private suspend fun validateAndScoreStreams(
        providerId: String,
        providerName: String,
        streamInfo: PluginStreamInfo
    ): List<RankedStream> = withContext(Dispatchers.Default) {
        val results = mutableListOf<RankedStream>()
        val intelScore = intelligenceEngine?.getIntelligenceScore(providerId) ?: 75.0

        // Process video streams
        for (stream in streamInfo.videoStreams) {
            val validation = streamValidator.validateStream(stream.url)
            if (!validation.isValid) continue

            val typeScore = when (validation.streamType) {
                StreamType.DIRECT_HLS -> 120
                StreamType.DIRECT_MP4 -> 100
                StreamType.DIRECT_DASH -> 90
                StreamType.MAGNET -> 85
                StreamType.EMBED_PAGE -> 80
                StreamType.UNKNOWN -> 30
            }

            val resScore = when {
                stream.qualityLabel.contains("4k", ignoreCase = true) || stream.qualityLabel.contains("2160", ignoreCase = true) || stream.height >= 2160 -> 70
                stream.qualityLabel.contains("1080", ignoreCase = true) || stream.height >= 1080 -> 50
                stream.qualityLabel.contains("720", ignoreCase = true) || stream.height >= 720 -> 35
                stream.qualityLabel.contains("480", ignoreCase = true) || stream.height >= 480 -> 20
                else -> 10
            }

            // Codec preference score (AV1 -> HEVC -> H.264)
            val codecBonus = when {
                stream.codec.equals("AV1", ignoreCase = true) || stream.qualityLabel.contains("AV1", ignoreCase = true) -> 35
                stream.codec.equals("HEVC", ignoreCase = true) || stream.qualityLabel.contains("HEVC", ignoreCase = true) || stream.qualityLabel.contains("H265", ignoreCase = true) -> 25
                stream.codec.equals("H264", ignoreCase = true) || stream.qualityLabel.contains("H264", ignoreCase = true) || stream.qualityLabel.contains("AVC", ignoreCase = true) -> 10
                else -> 5
            }

            val latencyBonus = maxOf(0, (3000 - validation.latencyMs).toInt() / 100)
            val intelBonus = (intelScore / 2.0).toInt() // Source Intelligence boost

            val totalScore = typeScore + resScore + codecBonus + latencyBonus + intelBonus

            val option = PlayableStreamOption(
                qualityLabel = "${providerName} - ${stream.qualityLabel}",
                format = stream.format,
                isMuxed = stream.isMuxed,
                videoUrl = validation.url,
                audioUrl = null
            )

            results.add(
                RankedStream(
                    option = option,
                    score = totalScore,
                    providerId = providerId,
                    streamType = validation.streamType,
                    validationLatencyMs = validation.latencyMs
                )
            )
        }

        // Fallback for single stream URL in streamInfo
        if (results.isEmpty() && streamInfo.url.isNotBlank()) {
            val validation = streamValidator.validateStream(streamInfo.url)
            if (validation.isValid) {
                val typeScore = when (validation.streamType) {
                    StreamType.DIRECT_HLS -> 110
                    StreamType.DIRECT_MP4 -> 95
                    StreamType.EMBED_PAGE -> 60
                    StreamType.MAGNET -> 40
                    else -> 20
                }
                val option = PlayableStreamOption(
                    qualityLabel = "$providerName Direct Stream",
                    videoUrl = validation.url,
                    audioUrl = null,
                    format = "hls",
                    isMuxed = true
                )
                results.add(
                    RankedStream(
                        option = option,
                        score = typeScore + (intelScore / 2.0).toInt(),
                        providerId = providerId,
                        streamType = validation.streamType,
                        validationLatencyMs = validation.latencyMs
                    )
                )
            }
        }

        results
    }
}
