package com.example.resolver

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.model.PlayableStreamOption
import com.example.model.StreamData
import com.example.remote.MediaFlowProxyHelper
import com.example.resolver.health.FailureType
import com.example.resolver.health.ProviderHealthManager
import com.example.resolver.mirror.MirrorManager
import com.example.ui.player.GlobalPlayerManager
import com.example.util.AppConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ResolvedPlayback(
    val candidate: SourceCandidate,
    val mediaUri: String,
    val headers: Map<String, String> = emptyMap(),
    val format: String = "mp4",
    val isTorrentStream: Boolean = false
)

/**
 * High-performance playback engine with Multi-Stage Intelligent Fallback.
 * (Adapted from Cauldron resilient playback & Nuvio mirror failover specifications).
 *
 * Fallback Chain:
 * 1. Primary candidate
 * 2. Same-provider healthy mirrors (via [MirrorManager])
 * 3. Next best ranked candidate from the active pool
 * 4. Fallback notification & seamless playback position / speed recovery
 */
class UnifiedPlaybackResolver private constructor(private val context: Context) {

    companion object {
        private const val TAG = "UnifiedPlaybackResolver"

        @Volatile
        private var instance: UnifiedPlaybackResolver? = null

        fun getInstance(context: Context): UnifiedPlaybackResolver {
            return instance ?: synchronized(this) {
                instance ?: UnifiedPlaybackResolver(context.applicationContext).also { instance = it }
            }
        }
    }

    private val _activeCandidate = MutableStateFlow<SourceCandidate?>(null)
    val activeCandidate: StateFlow<SourceCandidate?> = _activeCandidate.asStateFlow()

    private val _isResolving = MutableStateFlow(false)
    val isResolving: StateFlow<Boolean> = _isResolving.asStateFlow()

    /**
     * Resolves the media source for a candidate and seamlessly switches playback,
     * preserving position, speed, and track configurations.
     */
    suspend fun switchSource(
        candidate: SourceCandidate,
        initialPosOverride: Long? = null,
        onStatus: (String) -> Unit = {}
    ): Boolean = withContext(Dispatchers.Main) {
        _isResolving.value = true
        onStatus("Switching to ${candidate.serverName}...")

        val currentPos = initialPosOverride ?: GlobalPlayerManager.currentPositionMs.value.coerceAtLeast(0L)
        val currentSpeed = GlobalPlayerManager.getExoPlayer(context).playbackParameters.speed

        val isHanime = candidate.urlOrMagnet.contains("hanime", ignoreCase = true)
        val initialCandidate = if (isHanime && !candidate.urlOrMagnet.contains(".m3u8", ignoreCase = true) && !candidate.urlOrMagnet.contains(".mp4", ignoreCase = true)) {
            onStatus("Resolving direct anime media stream...")
            val stream = try {
                withContext(Dispatchers.IO) {
                    com.example.extractor.Hanime1Provider.getStreamData(candidate.urlOrMagnet, context)
                }
            } catch (e: Exception) {
                null
            }
            if (stream != null && stream.availableStreamOptions.isNotEmpty()) {
                val best = stream.selectedStreamOption ?: stream.availableStreamOptions.first()
                val isHls = best.format.equals("m3u8", ignoreCase = true)
                candidate.copy(
                    type = if (isHls) SourceStreamType.HLS else SourceStreamType.DIRECT,
                    urlOrMagnet = best.videoUrl.orEmpty(),
                    format = best.format ?: "mp4",
                    headers = best.headers
                )
            } else {
                candidate
            }
        } else {
            candidate
        }

        val isEmbed = initialCandidate.type == SourceStreamType.EMBED_WEBVIEW ||
                initialCandidate.urlOrMagnet.contains("/embed/", ignoreCase = true) ||
                initialCandidate.urlOrMagnet.contains("vidsrc.", ignoreCase = true) ||
                initialCandidate.urlOrMagnet.contains("vidrock.", ignoreCase = true) ||
                initialCandidate.urlOrMagnet.contains("vidlink.", ignoreCase = true) ||
                initialCandidate.urlOrMagnet.contains("2embed.", ignoreCase = true)

        val effectiveCandidate = if (isEmbed && !initialCandidate.urlOrMagnet.contains(".m3u8", ignoreCase = true) && !initialCandidate.urlOrMagnet.contains(".mp4", ignoreCase = true)) {
            onStatus("Extracting native video stream from ${candidate.serverName}...")
            val tmdbId = Regex("""(?:/(?:movie|tv)/|[?&]tmdb=)(\d+)""").find(candidate.urlOrMagnet)?.groupValues?.get(1).orEmpty()
            val isTv = candidate.urlOrMagnet.contains("/tv") || candidate.urlOrMagnet.contains("tv=")
            val tvMatch = Regex("""/tv/\d+/(\d+)/(\d+)|[?&]season=(\d+)&episode=(\d+)""").find(candidate.urlOrMagnet)
            val epSeason = tvMatch?.let { it.groupValues[1].ifEmpty { it.groupValues[3] }.toIntOrNull() } ?: 1
            val epEpisode = tvMatch?.let { it.groupValues[2].ifEmpty { it.groupValues[4] }.toIntOrNull() } ?: 1
            val directStreams = if (tmdbId.isNotBlank()) {
                try {
                    com.example.extractor.vidsrc.VidSrcStreamExtractor.resolveMultiServerOptions(
                        context = context,
                        tmdbIdOrUrl = tmdbId,
                        mediaType = if (isTv) "tv" else "movie",
                        season = epSeason,
                        episode = epEpisode,
                        title = candidate.title,
                        providerName = candidate.providerName
                    )
                } catch (e: Exception) {
                    emptyList()
                }
            } else emptyList()

            if (directStreams.isNotEmpty() && !directStreams.first().videoUrl.isNullOrBlank()) {
                val primary = directStreams.first()
                initialCandidate.copy(
                    type = SourceStreamType.HLS,
                    urlOrMagnet = primary.videoUrl.orEmpty(),
                    format = primary.format ?: "m3u8",
                    headers = primary.headers
                )
            } else {
                val sniffed = try {
                    withContext(Dispatchers.Main) {
                        com.example.extractor.vidsrc.VidSrcStreamExtractor.sniffEmbedUrl(context, initialCandidate.urlOrMagnet)
                    }
                } catch (e: Exception) {
                    null
                }
                if (sniffed != null && !sniffed.videoUrl.isNullOrBlank() && !sniffed.videoUrl.contains("/embed/")) {
                    initialCandidate.copy(
                        type = SourceStreamType.HLS,
                        urlOrMagnet = sniffed.videoUrl.orEmpty(),
                        format = sniffed.format ?: "m3u8",
                        headers = sniffed.headers
                    )
                } else {
                    Log.w(TAG, "Failed to resolve live media stream for embed: ${initialCandidate.urlOrMagnet}")
                    onStatus("Failed to resolve playable media for ${initialCandidate.serverName}")
                    _isResolving.value = false
                    return@withContext false
                }
            }
        } else {
            if (initialCandidate.type == SourceStreamType.EMBED_WEBVIEW) {
                initialCandidate.copy(type = SourceStreamType.HLS, format = "m3u8")
            } else {
                initialCandidate
            }
        }

        try {
            val resolved = withContext(Dispatchers.IO) {
                resolveCandidateToStream(effectiveCandidate, onStatus)
            }

            if (resolved == null) {
                onStatus("Failed to resolve ${effectiveCandidate.serverName}")
                _isResolving.value = false
                return@withContext false
            }

            _activeCandidate.value = effectiveCandidate

            // Package into StreamData & PlayableStreamOption for GlobalPlayerManager
            val streamOption = PlayableStreamOption(
                qualityLabel = effectiveCandidate.quality,
                format = resolved.format,
                isMuxed = true,
                videoUrl = resolved.mediaUri,
                headers = resolved.headers
            )

            val streamData = StreamData(
                videoId = effectiveCandidate.id,
                title = effectiveCandidate.title,
                channelName = effectiveCandidate.serverName,
                availableStreamOptions = listOf(streamOption),
                selectedStreamOption = streamOption,
                providerId = effectiveCandidate.providerId,
                headers = resolved.headers
            )

            // Feed into GlobalPlayerManager with preserved position
            GlobalPlayerManager.prepareAndPlay(
                context = context,
                streamData = streamData,
                streamOption = streamOption,
                hlsUrl = if (effectiveCandidate.type == SourceStreamType.HLS) resolved.mediaUri else null,
                captionOption = null,
                initialPos = currentPos
            )

            // Restore playback speed
            try {
                GlobalPlayerManager.getExoPlayer(context).setPlaybackSpeed(currentSpeed)
            } catch (_: Exception) {}

            MirrorManager.recordMirrorSuccess(candidate.providerId, candidate.urlOrMagnet)
            onStatus("Playing via ${candidate.serverName}")
            _isResolving.value = false
            true
        } catch (e: Exception) {
            Log.e(TAG, "Source switch failed: ${e.message}", e)

            // Resilient Fallback: If MediaFlow proxying was active and failed, attempt direct playback
            if (!candidate.isTorrent && candidate.type != SourceStreamType.EMBED_WEBVIEW &&
                MediaFlowProxyHelper.shouldProxy(candidate) && AppConfig.isMediaFlowFallbackToDirect()
            ) {
                Log.w(TAG, "MediaFlow proxy stream encountered error, attempting direct playback fallback...")
                onStatus("Proxy issue, falling back to direct stream...")
                try {
                    val directOption = PlayableStreamOption(
                        qualityLabel = candidate.quality,
                        format = candidate.format,
                        isMuxed = true,
                        videoUrl = candidate.urlOrMagnet,
                        headers = candidate.headers
                    )
                    val directData = StreamData(
                        videoId = candidate.id,
                        title = candidate.title,
                        channelName = candidate.serverName,
                        availableStreamOptions = listOf(directOption),
                        selectedStreamOption = directOption,
                        providerId = candidate.providerId,
                        headers = candidate.headers
                    )
                    GlobalPlayerManager.prepareAndPlay(
                        context = context,
                        streamData = directData,
                        streamOption = directOption,
                        hlsUrl = if (candidate.type == SourceStreamType.HLS) candidate.urlOrMagnet else null,
                        captionOption = null,
                        initialPos = currentPos
                    )
                    _isResolving.value = false
                    return@withContext true
                } catch (fallbackEx: Exception) {
                    Log.e(TAG, "Direct stream fallback also failed: ${fallbackEx.message}", fallbackEx)
                }
            }

            MirrorManager.recordMirrorFailure(
                providerId = candidate.providerId,
                mirrorUrl = candidate.urlOrMagnet,
                failureType = FailureType.EXTRACTION_FAILED,
                errorMessage = e.message
            )
            onStatus("Playback error: ${e.message}")
            _isResolving.value = false
            false
        }
    }

    /**
     * Attempts playback on [primaryCandidate], and automatically cascades through:
     * 1. Same-provider mirrors (via [MirrorManager])
     * 2. Secondary candidate options in [candidatePool]
     * 3. Best surviving stream candidate
     */
    suspend fun playWithFallback(
        primaryCandidate: SourceCandidate,
        candidatePool: List<SourceCandidate>,
        onStatus: (String) -> Unit = {}
    ): Boolean = withContext(Dispatchers.Main) {
        val currentPos = GlobalPlayerManager.currentPositionMs.value.coerceAtLeast(0L)

        // Stage 1: Try Primary Candidate
        val primarySuccess = switchSource(primaryCandidate, currentPos, onStatus)
        if (primarySuccess) return@withContext true

        Log.w(TAG, "Primary candidate ${primaryCandidate.serverName} failed. Triggering mirror failover...")

        // Stage 2: Try Same-Provider Mirrors
        val mirrors = MirrorManager.getOrderedMirrors(primaryCandidate.providerId)
        if (mirrors.size > 1 && !primaryCandidate.isTorrent) {
            for (mirrorDomain in mirrors) {
                if (!primaryCandidate.urlOrMagnet.contains(mirrorDomain, ignoreCase = true)) {
                    val replacedUrl = replaceDomain(primaryCandidate.urlOrMagnet, mirrorDomain)
                    if (replacedUrl != primaryCandidate.urlOrMagnet) {
                        onStatus("Failing over to mirror: $mirrorDomain")
                        val mirrorCandidate = primaryCandidate.copy(
                            id = "${primaryCandidate.id}_mirror",
                            serverName = "${primaryCandidate.serverName} (Mirror)",
                            urlOrMagnet = replacedUrl
                        )
                        val mirrorSuccess = switchSource(mirrorCandidate, currentPos, onStatus)
                        if (mirrorSuccess) {
                            Log.i(TAG, "Same-provider mirror failover succeeded: $mirrorDomain")
                            return@withContext true
                        }
                    }
                }
            }
        }

        // Stage 3: Cascade to Secondary Candidates in Pool
        val secondaryCandidates = candidatePool
            .filter { it.id != primaryCandidate.id && it.providerId != primaryCandidate.providerId }
            .sortedByDescending { SourceRankingEngine.calculateCompositeScore(it) }

        for (candidate in secondaryCandidates.take(6)) {
            onStatus("Cascading to fallback provider: ${candidate.providerName}...")
            val secondarySuccess = switchSource(candidate, currentPos, onStatus)
            if (secondarySuccess) {
                Log.i(TAG, "Secondary provider fallback succeeded: ${candidate.providerName}")
                onStatus("Recovered playback via ${candidate.providerName}")
                return@withContext true
            }
        }

        onStatus("All available stream candidates and mirrors failed")
        false
    }

    private fun replaceDomain(originalUrl: String, newDomain: String): String {
        return try {
            val uri = Uri.parse(originalUrl)
            val path = uri.encodedPath ?: ""
            val query = if (uri.encodedQuery != null) "?${uri.encodedQuery}" else ""
            val cleanDomain = newDomain.trimEnd('/')
            "$cleanDomain$path$query"
        } catch (_: Exception) {
            originalUrl
        }
    }

    private suspend fun resolveCandidateToStream(
        candidate: SourceCandidate,
        onStatus: (String) -> Unit
    ): ResolvedPlayback? {
        val isDirectHttpUrl = candidate.urlOrMagnet.startsWith("http://", ignoreCase = true) ||
                              candidate.urlOrMagnet.startsWith("https://", ignoreCase = true)

        if (!isDirectHttpUrl) {
            return null
        }

        val isHls = candidate.type == SourceStreamType.HLS || candidate.urlOrMagnet.contains(".m3u8", ignoreCase = true)
        val isDash = candidate.type == SourceStreamType.DASH || candidate.urlOrMagnet.contains(".mpd", ignoreCase = true)

        // Check if stream should route through MediaFlow-Proxy-Light
        if (MediaFlowProxyHelper.shouldProxy(candidate)) {
            onStatus("Routing stream via MediaFlow Proxy...")
            val proxiedUrl = MediaFlowProxyHelper.buildProxiedUrl(
                originalUrl = candidate.urlOrMagnet,
                headers = candidate.headers,
                isHls = isHls,
                isDash = isDash
            )

            Log.i(TAG, "Proxied stream with MediaFlow: ${MediaFlowProxyHelper.sanitizeUrlForLogging(proxiedUrl)}")

            return ResolvedPlayback(
                candidate = candidate,
                mediaUri = proxiedUrl,
                headers = candidate.headers,
                format = candidate.format,
                isTorrentStream = false
            )
        }

        // Direct stream (no proxy required)
        return ResolvedPlayback(
            candidate = candidate,
            mediaUri = candidate.urlOrMagnet,
            headers = candidate.headers,
            format = candidate.format,
            isTorrentStream = false
        )
    }

    fun setActiveCandidate(candidate: SourceCandidate?) {
        _activeCandidate.value = candidate
    }

    fun release() {
        _activeCandidate.value = null
    }
}
