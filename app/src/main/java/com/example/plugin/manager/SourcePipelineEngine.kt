package com.example.plugin.manager

import android.content.Context
import android.util.Log
import com.example.intelligence.SourceIntelligenceEngine
import com.example.model.*
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.PluginStreamInfo
import com.example.plugin.sdk.model.PluginVideoStream
import com.example.plugin.sdk.model.ProviderType
import com.example.util.MediaIdResolver
import com.example.utils.TorrentUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class PipelineValidationResult(
    val playableStreams: List<PlayableStreamOption>,
    val failedLogs: List<FailedSourceLog> = emptyList(),
    val mediaIdentity: MediaIdentity? = null
)

class SourcePipelineEngine(
    private val healthMonitor: ProviderHealthMonitor = ProviderHealthMonitor(),
    private val streamValidator: StreamValidator = StreamValidator(),
    private val torrentResolver: TorrentResolver = TorrentResolver(),
    private val context: Context? = null
) {
    private val intelligenceEngine: SourceIntelligenceEngine? by lazy {
        context?.let { SourceIntelligenceEngine.getInstance(it) }
    }

    fun extractInfoHash(url: String): String? {
        return TorrentUtils.extractInfoHash(url)
    }

    fun normalizeUrl(url: String): String {
        var clean = url.trim()
        if (clean.startsWith("magnet:", ignoreCase = true)) {
            val hash = extractInfoHash(clean)
            return if (hash != null) "magnet:?xt=urn:btih:$hash" else clean
        }
        clean = clean.replace(Regex("([?&])(utm_[^&]+|_t=[^&]+|ref=[^&]+)"), "")
            .replace(Regex("[?&]$"), "")
        return clean
    }

    /**
     * UNIFIED STREAM DISCOVERY & RANKING PIPELINE
     *
     * UI / MainViewModel -> SourcePipelineEngine -> Provider Manager -> Enabled Providers (Concurrent)
     * -> Normalize & Deduplicate -> Validate -> Debrid Resolution -> Score & Rank -> Playable Sources
     */
    suspend fun discoverAndRankStreams(
        idOrUrl: String,
        providers: List<ContentProviderApi>,
        torBoxApiKey: String? = null,
        targetProviderId: String? = null
    ): PipelineValidationResult = coroutineScope {
        // 1. CANONICAL MEDIA IDENTITY RESOLUTION
        val identity = MediaIdResolver.resolve(idOrUrl)
        Log.d("SourcePipelineEngine", "Canonical Media Identity resolved: imdbId=${identity.imdbId}, tmdbId=${identity.tmdbId}, type=${identity.mediaType}, S${identity.season}E${identity.episode} for input '$idOrUrl'")

        // Filter providers based on targetProviderId or health
        val candidateProviders = if (!targetProviderId.isNullOrBlank() && targetProviderId != "all") {
            providers.filter { it.providerId == targetProviderId }
        } else {
            providers
        }

        val activeProviders = candidateProviders.filter { provider ->
            healthMonitor.isProviderHealthy(provider.providerId)
        }.ifEmpty { candidateProviders }

        Log.d("SourcePipelineEngine", "Querying ${activeProviders.size} providers concurrently...")

        // 2. CONCURRENT DISCOVERY ACROSS ALL ENABLED PROVIDERS
        val deferredResults = activeProviders.map { provider ->
            async(Dispatchers.IO) {
                fetchFromProviderWithTimeout(provider, idOrUrl)
            }
        }

        val providerOutputs = deferredResults.awaitAll().filterNotNull()

        // 3. AGGREGATE & NORMALIZE RAW STREAMS
        val rawStreamsWithProvider = mutableListOf<Pair<ContentProviderApi, PluginVideoStream>>()
        for ((provider, streamInfo) in providerOutputs) {
            for (vStream in streamInfo.videoStreams) {
                rawStreamsWithProvider.add(Pair(provider, vStream))
            }
            if (streamInfo.videoStreams.isEmpty() && streamInfo.url.isNotBlank()) {
                rawStreamsWithProvider.add(
                    Pair(
                        provider,
                        PluginVideoStream(
                            url = streamInfo.url,
                            qualityLabel = "${provider.providerId} Direct Stream",
                            format = "hls",
                            isMuxed = true
                        )
                    )
                )
            }
        }

        // 4. DEDUPLICATION BY INFOHASH / NORMALIZED URL
        val seenInfoHashes = mutableSetOf<String>()
        val seenUrls = mutableSetOf<String>()
        val deduplicatedStreams = mutableListOf<Pair<ContentProviderApi, PluginVideoStream>>()

        for ((provider, stream) in rawStreamsWithProvider) {
            val rawUrl = stream.url ?: continue
            val infoHash = extractInfoHash(rawUrl)
            if (infoHash != null) {
                if (seenInfoHashes.contains(infoHash)) {
                    Log.d("SourcePipelineEngine", "[Deduplication] Dropped duplicate torrent infoHash: $infoHash from ${provider.providerId}")
                    continue
                }
                seenInfoHashes.add(infoHash)
                deduplicatedStreams.add(Pair(provider, stream))
            } else {
                val normUrl = normalizeUrl(rawUrl)
                if (seenUrls.contains(normUrl)) {
                    Log.d("SourcePipelineEngine", "[Deduplication] Dropped duplicate stream URL: $normUrl from ${provider.providerId}")
                    continue
                }
                seenUrls.add(normUrl)
                deduplicatedStreams.add(Pair(provider, stream))
            }
        }

        // 5. VALIDATION, DEBRID RESOLUTION & SCORING
        val playable = mutableListOf<Pair<PlayableStreamOption, Int>>()
        val failedLogs = mutableListOf<FailedSourceLog>()

        for ((provider, stream) in deduplicatedStreams) {
            val rawUrl = stream.url ?: continue
            val title = stream.qualityLabel.ifBlank { "${provider.providerId} Stream" }
            val infoHash = extractInfoHash(rawUrl)

            // Stage: Initial Stream Validation
            val validation = streamValidator.validateStream(rawUrl)
            if (!validation.isValid) {
                failedLogs.add(
                    FailedSourceLog(
                        providerId = provider.providerId,
                        sourceTitle = title,
                        rawUrl = rawUrl,
                        errorType = validation.failureReason?.name ?: "VALIDATION_FAILED",
                        httpStatus = if (validation.httpCode != 0) validation.httpCode else null,
                        urlType = if (infoHash != null) "MAGNET" else "URL",
                        stage = SourceLifecycleStage.VALIDATED,
                        failureReason = validation.failureReason?.description ?: "Initial URL or magnet validation failed"
                    )
                )
                continue
            }

            // Stage: Debrid Resolution for Magnet Links
            var finalUrl = rawUrl
            var isResolvedDebrid = false
            var format = stream.format

            if (infoHash != null) {
                val resolvedTor = torrentResolver.resolveTorrent(rawUrl, title, torBoxApiKey)
                if (resolvedTor != null) {
                    finalUrl = resolvedTor.playableUrl
                    isResolvedDebrid = true
                    format = if (resolvedTor.isHls) "hls" else "mp4"
                }
            }

            val decisionType = PlaybackDecisionResolver.determineSourceType(finalUrl, format)

            // Calculate Scoring & Ranking
            var score = 50

            // Quality score
            val lowerLabel = title.lowercase()
            when {
                lowerLabel.contains("4k") || lowerLabel.contains("2160") -> score += 70
                lowerLabel.contains("1080") -> score += 50
                lowerLabel.contains("720") -> score += 35
                lowerLabel.contains("480") -> score += 20
                else -> score += 15
            }

            // Source type score
            when (decisionType) {
                PlaybackSourceType.DIRECT_STREAM -> score += if (isResolvedDebrid) 110 else 120
                PlaybackSourceType.EMBED_WEBVIEW -> score += 60
                PlaybackSourceType.MAGNET -> score += 30
            }

            // Codec bonus
            when {
                lowerLabel.contains("av1") -> score += 30
                lowerLabel.contains("hevc") || lowerLabel.contains("h265") -> score += 20
                lowerLabel.contains("h264") || lowerLabel.contains("avc") -> score += 10
            }

            // Provider intelligence score
            val intelScore = intelligenceEngine?.getIntelligenceScore(provider.providerId) ?: 75.0
            score += (intelScore / 2.0).toInt()

            val pType = if (infoHash != null && !isResolvedDebrid) ProviderType.TORRENT else ProviderType.OTHER

            val option = PlayableStreamOption(
                qualityLabel = title,
                format = format,
                isMuxed = stream.isMuxed,
                videoUrl = finalUrl,
                audioUrl = null,
                providerType = pType
            )

            playable.add(Pair(option, score))
        }

        // Sort descending by score
        val sortedPlayable = playable.sortedByDescending { it.second }.map { it.first }

        Log.d("SourcePipelineEngine", "Pipeline completed: ${sortedPlayable.size} playable streams found (${failedLogs.size} failed)")

        PipelineValidationResult(
            playableStreams = sortedPlayable,
            failedLogs = failedLogs,
            mediaIdentity = identity
        )
    }

    private suspend fun fetchFromProviderWithTimeout(
        provider: ContentProviderApi,
        idOrUrl: String
    ): Pair<ContentProviderApi, PluginStreamInfo>? {
        val startTime = System.currentTimeMillis()
        intelligenceEngine?.recordRequestStart(provider.providerId)

        return try {
            val result = withTimeoutOrNull(6000L) {
                provider.getStreams(idOrUrl)
            }
            val latency = System.currentTimeMillis() - startTime

            if (result != null) {
                healthMonitor.recordSuccess(provider.providerId, latency)
                intelligenceEngine?.recordRequestSuccess(provider.providerId, latency)
                Pair(provider, result)
            } else {
                Log.w("SourcePipelineEngine", "Provider '${provider.providerId}' timed out (>6s)")
                healthMonitor.recordFailure(provider.providerId, StreamFailureReason.TIMEOUT)
                null
            }
        } catch (e: Exception) {
            Log.e("SourcePipelineEngine", "Provider '${provider.providerId}' threw exception: ${e.message}")
            healthMonitor.recordFailure(provider.providerId, StreamFailureReason.PARSING_FAILED)
            null
        }
    }
}
