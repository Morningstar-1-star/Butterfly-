package com.example.resolver

import android.content.Context
import android.util.Log
import com.example.model.MediaIdentity
import com.example.model.StreamResult
import com.example.remote.MediaFlowProxyHelper
import com.example.resolver.dedup.SourceDeduplicator
import com.example.resolver.validation.MediaIdentityValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * Universal Provider Aggregator 2.0 (Inspired by Cauldron, Nuvio & AIOStreams architectures).
 *
 * Enforces the unified resilient aggregation pipeline:
 * 1. Concurrent Isolated Query Dispatch via [UnifiedSourceResolver] & [com.example.resolver.health.ProviderIsolationController]
 * 2. Strict Media Identity Validation via [MediaIdentityValidator]
 * 3. Standardized Output Normalization into [StreamResult]
 * 4. 4-Tier Deduplication & Stream Merging via [SourceDeduplicator]
 * 5. MediaFlow Proxying (Dynamic CORS/Header negotiation)
 * 6. Multi-Factor Quality & Reliability Ranking via [SourceRankingEngine]
 * 7. Progressive Flow Emission to UI & Media Player
 */
class UniversalProviderAggregator(private val context: Context) {

    companion object {
        private const val TAG = "UniversalAggregator"

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
     * Resolves, validates, aggregates, deduplicates, and ranks streams
     * returning an ongoing progressive Flow of [StreamResult] items.
     */
    fun aggregateStreams(
        identity: MediaIdentity,
        requiredCapabilities: Set<ProviderCapability> = emptySet()
    ): Flow<List<StreamResult>> = channelFlow {
        val collectedStreams = mutableListOf<StreamResult>()

        supervisorScope {
            launch {
                unifiedResolver.resolveSources(identity, requiredCapabilities).collect { candidates ->
                    if (candidates.isNotEmpty()) {
                        val normalizedList = mutableListOf<StreamResult>()

                        for (candidate in candidates) {
                            val stream = normalizeCandidate(candidate)
                            if (isValidStream(stream)) {
                                normalizedList.add(stream)
                            }
                        }

                        synchronized(collectedStreams) {
                            collectedStreams.clear()
                            collectedStreams.addAll(normalizedList)
                        }

                        // 4-Tier Deduplication & Merging
                        val deduplicated = SourceDeduplicator.deduplicateStreams(collectedStreams)

                        // Multi-Factor Ranking
                        val ranked = SourceRankingEngine.rankStreams(deduplicated)

                        send(ranked)
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun normalizeCandidate(candidate: SourceCandidate): StreamResult {
        val isTorrent = candidate.type == SourceStreamType.TORRENT || candidate.urlOrMagnet.startsWith("magnet:")
        val isHls = candidate.type == SourceStreamType.HLS || candidate.urlOrMagnet.contains(".m3u8", ignoreCase = true)
        val isDash = candidate.type == SourceStreamType.DASH || candidate.urlOrMagnet.contains(".mpd", ignoreCase = true)

        val infoHash = candidate.extraData["infoHash"]
            ?: if (candidate.urlOrMagnet.startsWith("magnet:")) {
                com.example.torrent.protocol.MagnetParser.parse(candidate.urlOrMagnet)?.infoHashHex
            } else null

        // Determine if stream needs MediaFlow proxying
        val needsProxy = !isTorrent && (candidate.headers.isNotEmpty() || MediaFlowProxyHelper.isMediaFlowEnabled())
        val proxiedUrl = if (needsProxy) {
            MediaFlowProxyHelper.buildProxiedUrl(
                originalUrl = candidate.urlOrMagnet,
                headers = candidate.headers,
                isHls = isHls,
                isDash = isDash
            )
        } else null

        val rankingScore = SourceRankingEngine.calculateCompositeScore(candidate)

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
        val trimmed = result.url.trim()
        if (trimmed.isBlank()) return false
        if (result.isTorrent) {
            return !result.infoHash.isNullOrBlank() || trimmed.startsWith("magnet:?xt=urn:btih:", ignoreCase = true) || trimmed.startsWith("magnet:", ignoreCase = true)
        }
        return trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("file://", ignoreCase = true) ||
                trimmed.startsWith("content://", ignoreCase = true)
    }
}
