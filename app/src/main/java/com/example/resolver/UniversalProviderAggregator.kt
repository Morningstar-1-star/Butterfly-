package com.example.resolver

import android.content.Context
import android.util.Log
import com.example.model.MediaIdentity
import com.example.model.ProviderResult
import com.example.model.StreamResult
import com.example.remote.MediaFlowProxyHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * Universal Provider Aggregation Engine (Adapted from Steven-Shirley/aioStreams).
 *
 * Enforces the standardized 7-step pipeline:
 * 1. Provider Query Dispatch (Parallel with complete fault isolation)
 * 2. Standardized Output Normalization into StreamResult
 * 3. Strict URL / Magnet Validation & Connectivity Health Checks
 * 4. Deduplication by canonical URL and BitTorrent InfoHash
 * 5. MediaFlow Proxying (Automatic proxy wrapping for streams requiring headers/CORS)
 * 6. Multi-Factor Quality & Health Score Ranking
 * 7. Progressive Flow Emission to the UI and Player
 */
class UniversalProviderAggregator(private val context: Context) {

    companion object {
        private const val TAG = "AIOStreamsAggregator"

        @Volatile
        private var instance: UniversalProviderAggregator? = null

        fun getInstance(context: Context): UniversalProviderAggregator {
            return instance ?: synchronized(this) {
                instance ?: UniversalProviderAggregator(context.applicationContext).also { instance = it }
            }
        }
    }

    private val unifiedResolver = UnifiedSourceResolver.getInstance(context)

    /**
     * Resolves, aggregates, normalizes, validates, deduplicates, and ranks streams
     * returning an ongoing progressive Flow of [StreamResult]s.
     */
    fun aggregateStreams(identity: MediaIdentity): Flow<List<StreamResult>> = channelFlow {
        val streamMap = mutableMapOf<String, StreamResult>()

        supervisorScope {
            launch {
                unifiedResolver.resolveSources(identity).collect { candidates ->
                    if (candidates.isNotEmpty()) {
                        synchronized(streamMap) {
                            for (candidate in candidates) {
                                val normalized = normalizeCandidate(candidate)
                                if (isValidStream(normalized)) {
                                    val key = getDeduplicationKey(normalized)
                                    val existing = streamMap[key]
                                    if (existing == null || normalized.rankingScore > existing.rankingScore) {
                                        streamMap[key] = normalized
                                    }
                                }
                            }
                        }

                        // Rank and emit current snapshot
                        val rankedResults = rankStreams(streamMap.values.toList())
                        send(rankedResults)
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Normalizes a SourceCandidate into a standard StreamResult and applies MediaFlow proxying if needed.
     */
    private fun normalizeCandidate(candidate: SourceCandidate): StreamResult {
        val isTorrent = candidate.type == SourceStreamType.TORRENT || candidate.urlOrMagnet.startsWith("magnet:")
        val isHls = candidate.type == SourceStreamType.HLS || candidate.urlOrMagnet.contains(".m3u8", ignoreCase = true)
        val isDash = candidate.type == SourceStreamType.DASH || candidate.urlOrMagnet.contains(".mpd", ignoreCase = true)

        val infoHash = candidate.extraData["infoHash"]
            ?: if (candidate.urlOrMagnet.startsWith("magnet:")) {
                com.example.torrent.protocol.MagnetParser.parse(candidate.urlOrMagnet)?.infoHashHex
            } else null

        // Determine if stream needs MediaFlow proxying (custom headers or user preference)
        val needsProxy = !isTorrent && (candidate.headers.isNotEmpty() || MediaFlowProxyHelper.isMediaFlowEnabled())
        val proxiedUrl = if (needsProxy) {
            MediaFlowProxyHelper.buildProxiedUrl(
                originalUrl = candidate.urlOrMagnet,
                headers = candidate.headers,
                isHls = isHls,
                isDash = isDash
            )
        } else null

        val rankingScore = computeRankingScore(candidate)

        return StreamResult(
            id = candidate.id,
            title = candidate.title,
            providerId = candidate.providerId,
            providerName = candidate.providerName,
            streamType = candidate.type,
            url = candidate.urlOrMagnet,
            hlsUrl = if (isHls) candidate.urlOrMagnet else null,
            dashUrl = if (isDash) candidate.urlOrMagnet else null,
            format = candidate.format,
            qualityLabel = candidate.quality,
            qualityScore = candidate.qualityScore,
            bitrateBps = candidate.extraData["bitrate"]?.toLongOrNull() ?: 0L,
            isMuxed = true,
            sizeBytes = candidate.sizeBytes,
            formattedSize = candidate.formattedSize,
            seeders = candidate.seeders,
            leechers = candidate.leechers,
            isTorrent = isTorrent,
            infoHash = infoHash,
            headers = candidate.headers,
            subtitleUrls = candidate.subtitleUrls,
            audioTracks = candidate.audioTracks,
            healthScore = candidate.healthScore,
            rankingScore = rankingScore,
            isPlayable = candidate.isPlayable,
            isProxiedViaMediaFlow = proxiedUrl != null && proxiedUrl != candidate.urlOrMagnet,
            mediaFlowProxyUrl = proxiedUrl,
            extraData = candidate.extraData
        )
    }

    private fun isValidStream(result: StreamResult): Boolean {
        if (result.url.isBlank()) return false
        if (result.isTorrent) {
            return !result.infoHash.isNullOrBlank() || result.url.startsWith("magnet:")
        }
        return result.url.startsWith("http://") || result.url.startsWith("https://")
    }

    private fun getDeduplicationKey(result: StreamResult): String {
        return when {
            result.isTorrent && !result.infoHash.isNullOrBlank() -> "torrent_${result.infoHash.lowercase()}"
            else -> "url_${result.url.substringBefore("?").lowercase()}"
        }
    }

    private fun computeRankingScore(candidate: SourceCandidate): Int {
        var score = candidate.qualityScore
        if (candidate.type == SourceStreamType.DIRECT || candidate.type == SourceStreamType.HLS) {
            score += 200 // Bonus for instantaneous direct playback
        }
        if (candidate.seeders > 0) {
            score += minOf(candidate.seeders * 3, 150)
        }
        if (candidate.healthScore > 80) {
            score += 50
        }
        return score
    }

    private fun rankStreams(streams: List<StreamResult>): List<StreamResult> {
        return streams.sortedWith(
            compareByDescending<StreamResult> { it.rankingScore }
                .thenByDescending { it.qualityScore }
                .thenByDescending { it.seeders }
                .thenByDescending { it.healthScore }
        )
    }
}
