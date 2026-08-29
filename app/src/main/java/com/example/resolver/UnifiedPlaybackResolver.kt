package com.example.resolver

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.model.PlayableStreamOption
import com.example.model.StreamData
import com.example.resolver.health.FailureType
import com.example.resolver.health.ProviderHealthManager
import com.example.resolver.mirror.MirrorManager
import com.example.torrent.engine.TorrentEngine
import com.example.torrent.model.TorrentRelease
import com.example.torrent.server.TorrentHttpServer
import com.example.ui.player.GlobalPlayerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ResolvedPlayback(
    val candidate: SourceCandidate,
    val mediaUri: String,
    val headers: Map<String, String> = emptyMap(),
    val format: String = "mp4",
    val isTorrentStream: Boolean = false,
    val torrentServer: TorrentHttpServer? = null
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

    private val torrentEngine = TorrentEngine.getInstance(context)
    private var activeTorrentServer: TorrentHttpServer? = null

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

        if (candidate.type == SourceStreamType.EMBED_WEBVIEW) {
            _activeCandidate.value = candidate
            GlobalPlayerManager.prepareAndPlay(
                context = context,
                streamData = null,
                streamOption = null,
                hlsUrl = null,
                captionOption = null
            )
            onStatus("Loaded embed player: ${candidate.serverName}")
            _isResolving.value = false
            return@withContext true
        }

        try {
            val resolved = withContext(Dispatchers.IO) {
                resolveCandidateToStream(candidate, onStatus)
            }

            if (resolved == null) {
                onStatus("Failed to resolve ${candidate.serverName}")
                _isResolving.value = false
                return@withContext false
            }

            _activeCandidate.value = candidate

            // Package into StreamData & PlayableStreamOption for GlobalPlayerManager
            val streamOption = PlayableStreamOption(
                qualityLabel = candidate.quality,
                format = resolved.format,
                isMuxed = true,
                videoUrl = resolved.mediaUri,
                headers = resolved.headers
            )

            val streamData = StreamData(
                videoId = candidate.id,
                title = candidate.title,
                channelName = candidate.serverName,
                availableStreamOptions = listOf(streamOption),
                selectedStreamOption = streamOption,
                providerId = candidate.providerId,
                headers = resolved.headers
            )

            // Feed into GlobalPlayerManager with preserved position
            GlobalPlayerManager.prepareAndPlay(
                context = context,
                streamData = streamData,
                streamOption = streamOption,
                hlsUrl = if (candidate.type == SourceStreamType.HLS) resolved.mediaUri else null,
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

        for (candidate in secondaryCandidates.take(3)) {
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
        if (candidate.isTorrent) {
            onStatus("Initializing BitTorrent Swarm...")
            return resolveTorrentCandidate(candidate, onStatus)
        }

        // Direct / HLS / DASH stream
        return ResolvedPlayback(
            candidate = candidate,
            mediaUri = candidate.urlOrMagnet,
            headers = candidate.headers,
            format = candidate.format,
            isTorrentStream = false
        )
    }

    private suspend fun resolveTorrentCandidate(
        candidate: SourceCandidate,
        onStatus: (String) -> Unit
    ): ResolvedPlayback? = withContext(Dispatchers.IO) {
        try {
            // Clean up previous torrent server if active
            activeTorrentServer?.stop()
            activeTorrentServer = null
            torrentEngine.stopSession(clearCache = false)

            // Create HTTP server on dynamic port (port 0 = auto-assign free ephemeral port)
            val server = TorrentHttpServer(torrentEngine, port = 0)
            activeTorrentServer = server
            server.start()

            val parsedMagnet = com.example.torrent.protocol.MagnetParser.parse(candidate.urlOrMagnet)
            val infoHash = candidate.extraData["infoHash"]?.takeIf { it.isNotBlank() }
                ?: parsedMagnet?.infoHashHex
                ?: ""

            val magnetUrl = if (candidate.urlOrMagnet.startsWith("magnet:?", ignoreCase = true)) {
                candidate.urlOrMagnet
            } else if (infoHash.isNotBlank()) {
                com.example.torrent.protocol.MagnetParser.buildMagnetUrl(
                    infoHash,
                    candidate.title,
                    parsedMagnet?.trackers ?: com.example.torrent.protocol.MagnetParser.DEFAULT_TRACKERS
                )
            } else {
                candidate.urlOrMagnet
            }

            val release = TorrentRelease(
                title = candidate.title,
                infoHash = infoHash.lowercase().trim(),
                magnetUrl = magnetUrl,
                provider = candidate.providerName,
                seeders = candidate.seeders,
                leechers = candidate.leechers,
                sizeBytes = candidate.sizeBytes,
                formattedSize = candidate.formattedSize,
                quality = candidate.quality,
                codec = candidate.extraData["codec"] ?: "",
                hdr = candidate.extraData["hdr"] ?: "",
                audioChannels = candidate.extraData["audio"] ?: "",
                trackerUrls = parsedMagnet?.trackers ?: com.example.torrent.protocol.MagnetParser.DEFAULT_TRACKERS
            )

            onStatus("Connecting to BitTorrent swarm (${candidate.seeders} seeds)...")
            torrentEngine.startSession(release, streamPort = server.assignedPort)

            var metadataWaitMs = 0
            val maxWaitMs = 30000
            while (torrentEngine.getFileLength() <= 0 && metadataWaitMs < maxWaitMs) {
                delay(400)
                metadataWaitMs += 400
                val stats = torrentEngine.stats.value
                val peerCount = stats.connectedPeers
                val dhtNodes = stats.dhtNodes
                if (peerCount > 0) {
                    onStatus("Discovered $peerCount peers ($dhtNodes DHT nodes), loading torrent metadata...")
                } else if (dhtNodes > 0) {
                    onStatus("Searching DHT swarm ($dhtNodes nodes active)...")
                } else {
                    onStatus("Connecting to trackers & DHT network...")
                }
            }

            if (torrentEngine.getFileLength() <= 0) {
                Log.w(TAG, "Torrent metadata fetch timed out after $maxWaitMs ms for $infoHash")
                onStatus("Timed out connecting to swarm (no seeders found)")
                return@withContext null
            }

            onStatus("Torrent metadata ready, buffering video stream...")

            val dynamicStreamUrl = server.streamUrl
            Log.i(TAG, "Torrent stream ready at: $dynamicStreamUrl (Port: ${server.assignedPort})")

            ResolvedPlayback(
                candidate = candidate,
                mediaUri = dynamicStreamUrl,
                headers = mapOf("Accept-Ranges" to "bytes"),
                format = "mkv",
                isTorrentStream = true,
                torrentServer = server
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving torrent stream: ${e.message}", e)
            null
        }
    }

    fun release() {
        activeTorrentServer?.stop()
        activeTorrentServer = null
        torrentEngine.stopSession(clearCache = false)
        _activeCandidate.value = null
    }
}
