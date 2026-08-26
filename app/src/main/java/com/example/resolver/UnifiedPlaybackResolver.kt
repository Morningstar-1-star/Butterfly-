package com.example.resolver

import android.content.Context
import android.util.Log
import com.example.model.PlayableStreamOption
import com.example.model.StreamData
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
 * High-performance playback engine responsible for converting a SourceCandidate
 * (Vega direct stream or BitTorrent magnet) into an active Media3 playback session
 * with seamless state preservation (position, speed, audio tracks).
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

            onStatus("Playing via ${candidate.serverName}")
            _isResolving.value = false
            true
        } catch (e: Exception) {
            Log.e(TAG, "Source switch failed: ${e.message}", e)
            onStatus("Playback error: ${e.message}")
            _isResolving.value = false
            false
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

            val release = TorrentRelease(
                title = candidate.title,
                infoHash = candidate.extraData["infoHash"] ?: "",
                magnetUrl = candidate.urlOrMagnet,
                provider = candidate.providerName,
                seeders = candidate.seeders,
                leechers = candidate.leechers,
                sizeBytes = candidate.sizeBytes,
                formattedSize = candidate.formattedSize,
                quality = candidate.quality,
                codec = candidate.extraData["codec"] ?: "",
                hdr = candidate.extraData["hdr"] ?: "",
                audioChannels = candidate.extraData["audio"] ?: ""
            )

            onStatus("Connecting to swarm (${candidate.seeders} seeds)...")
            torrentEngine.startSession(release, streamPort = server.assignedPort)

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
