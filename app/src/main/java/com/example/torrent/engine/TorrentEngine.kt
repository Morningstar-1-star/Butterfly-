package com.example.torrent.engine

import android.content.Context
import android.util.Log
import com.example.torrent.bencode.Bencode
import com.example.torrent.model.*
import com.example.torrent.protocol.MagnetParser
import com.example.torrent.protocol.ParsedMagnet
import com.example.torrent.protocol.PeerConnection
import com.example.torrent.protocol.TrackerClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class TorrentEngine(private val context: Context) {

    companion object {
        private const val TAG = "TorrentEngine"
        private const val MAX_PEER_CONNECTIONS = 30

        @Volatile
        private var instance: TorrentEngine? = null

        fun getInstance(context: Context): TorrentEngine {
            return instance ?: synchronized(this) {
                instance ?: TorrentEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _stats = MutableStateFlow(TorrentEngineStats())
    val stats: StateFlow<TorrentEngineStats> = _stats.asStateFlow()

    private val myPeerId: ByteArray = generatePeerId()

    private var activeSession: TorrentStreamSession? = null
    private var activeMagnet: ParsedMagnet? = null
    private var activeMetadata: TorrentMetadata? = null
    private var pieceManager: TorrentPieceManager? = null
    private var storage: TorrentStorage? = null

    private val isRunning = AtomicBoolean(false)
    private val connectedPeers = ConcurrentHashMap<String, PeerConnection>()
    private val metadataPieces = ConcurrentHashMap<Int, ByteArray>()
    private var expectedMetadataSize = 0

    private val bytesDownloadedTotal = AtomicLong(0L)
    private val bytesDownloadedInterval = AtomicLong(0L)

    private fun generatePeerId(): ByteArray {
        val bytes = ByteArray(20)
        val prefix = "-BF1000-".toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(prefix, 0, bytes, 0, prefix.size)
        val random = Random()
        for (i in prefix.size until 20) {
            bytes[i] = (random.nextInt(26) + 97).toByte()
        }
        return bytes
    }

    fun startSession(release: TorrentRelease, streamPort: Int = 8899): TorrentStreamSession {
        stopSession(clearCache = false)

        val parsed = MagnetParser.parse(release.magnetUrl.ifBlank { release.infoHash })
            ?: throw IllegalArgumentException("Invalid magnet or infoHash: ${release.magnetUrl}")

        activeMagnet = parsed
        isRunning.set(true)

        val sessionId = parsed.infoHashHex.take(12)
        val streamUrl = "http://127.0.0.1:$streamPort/torrent/$sessionId/stream.mkv"

        val session = TorrentStreamSession(
            sessionId = sessionId,
            release = release,
            httpStreamUrl = streamUrl
        )
        activeSession = session

        _stats.value = TorrentEngineStats(
            state = TorrentEngineState.CONNECTING_TRACKERS,
            infoHash = parsed.infoHashHex,
            activeFileName = release.title,
            streamPort = streamPort,
            streamUrl = streamUrl
        )

        engineScope.launch {
            runEnginePipeline(parsed, release)
        }

        return session
    }

    private suspend fun runEnginePipeline(magnet: ParsedMagnet, release: TorrentRelease) {
        // 1. ANNOUNCE TO TRACKERS & FIND PEERS
        _stats.value = _stats.value.copy(state = TorrentEngineState.CONNECTING_TRACKERS)
        val peers = TrackerClient.announce(
            infoHashBytes = magnet.infoHashBytes,
            peerIdBytes = myPeerId,
            trackers = magnet.trackers
        )

        _stats.value = _stats.value.copy(
            totalPeersFound = peers.size,
            state = TorrentEngineState.FETCHING_METADATA
        )

        // 2. CONNECT TO PEERS & FETCH METADATA (BEP 9 / ut_metadata)
        val peerJob = engineScope.launch {
            connectToPeers(peers, magnet)
        }

        // Wait for metadata resolution
        val metadataResolved = waitForMetadata(magnet, timeoutMs = 25000)
        if (!metadataResolved || activeMetadata == null) {
            // If peer metadata timeout, construct a virtual single-file stream layout
            activeMetadata = createSyntheticMetadata(magnet, release)
        }

        val meta = activeMetadata ?: return
        _stats.value = _stats.value.copy(
            state = TorrentEngineState.INITIALIZING_STORAGE,
            activeFileName = meta.mainVideoFile.name,
            fileSizeBytes = meta.mainVideoFile.length,
            totalPieces = meta.pieceHashes.size
        )

        // 3. INITIALIZE STORAGE & PIECE MANAGER
        storage = TorrentStorage(context, meta)
        pieceManager = TorrentPieceManager(meta, storage!!)

        _stats.value = _stats.value.copy(state = TorrentEngineState.BUFFERING)

        // 4. SPAWN STREAMING & DOWNLOADING WORKERS
        val downloadJob = engineScope.launch {
            runDownloadLoop()
        }

        val telemetryJob = engineScope.launch {
            runTelemetryLoop()
        }
    }

    private suspend fun connectToPeers(peers: List<InetSocketAddress>, magnet: ParsedMagnet) = withContext(Dispatchers.IO) {
        val shuffled = peers.shuffled()
        for (addr in shuffled) {
            if (!isRunning.get()) break
            if (connectedPeers.size >= MAX_PEER_CONNECTIONS) {
                delay(1000)
                continue
            }

            val key = "${addr.address.hostAddress}:${addr.port}"
            if (connectedPeers.containsKey(key)) continue

            launch {
                val peerConn = PeerConnection(
                    peerAddress = addr,
                    infoHashBytes = magnet.infoHashBytes,
                    myPeerIdBytes = myPeerId,
                    onMetadataPieceReceived = { piece, totalSize, data ->
                        handleMetadataPiece(piece, totalSize, data, magnet)
                    },
                    onBlockReceived = { pieceIndex, offset, data ->
                        handleBlockReceived(pieceIndex, offset, data)
                    },
                    onPeerDisconnected = { p ->
                        val k = "${p.peerAddress.address.hostAddress}:${p.peerAddress.port}"
                        connectedPeers.remove(k)
                    }
                )

                if (peerConn.connect()) {
                    connectedPeers[key] = peerConn
                    peerConn.startListening()

                    // If we need metadata, request metadata pieces
                    if (activeMetadata == null && peerConn.peerSupportsExtensions) {
                        for (i in 0..10) {
                            peerConn.requestMetadataPiece(i)
                        }
                    }
                }
            }
            delay(50)
        }
    }

    private fun handleMetadataPiece(pieceIndex: Int, totalSize: Int, data: ByteArray, magnet: ParsedMagnet) {
        if (activeMetadata != null) return
        expectedMetadataSize = totalSize
        metadataPieces[pieceIndex] = data

        val numPieces = (totalSize + 16383) / 16384
        if (metadataPieces.size >= numPieces) {
            val combined = ByteArrayOutputStream()
            for (i in 0 until numPieces) {
                val p = metadataPieces[i] ?: return
                combined.write(p)
            }
            val metaBytes = combined.toByteArray()

            // Verify SHA-1 of metadata info dictionary
            try {
                val digest = MessageDigest.getInstance("SHA-1").digest(metaBytes)
                if (digest.contentEquals(magnet.infoHashBytes)) {
                    val decoded = Bencode.decode(metaBytes) as? Map<String, Any?>
                    if (decoded != null) {
                        activeMetadata = parseMetadataFromDict(magnet.infoHashHex, decoded)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun parseMetadataFromDict(infoHash: String, infoDict: Map<String, Any?>): TorrentMetadata {
        val name = Bencode.getString(infoDict, "name", "Stream")
        val pieceLength = Bencode.getLong(infoDict, "piece length", 524288L).toInt()
        val piecesBytes = Bencode.getBytes(infoDict, "pieces") ?: ByteArray(0)

        val pieceHashes = mutableListOf<ByteArray>()
        for (i in 0 until piecesBytes.size step 20) {
            if (i + 20 <= piecesBytes.size) {
                pieceHashes.add(piecesBytes.copyOfRange(i, i + 20))
            }
        }

        val filesList = Bencode.getList(infoDict, "files")
        val files = mutableListOf<TorrentFileItem>()
        var currentOffset = 0L

        if (filesList.isNotEmpty()) {
            for ((index, fileObj) in filesList.withIndex()) {
                val fMap = fileObj as? Map<String, Any?> ?: continue
                val length = Bencode.getLong(fMap, "length", 0L)
                val pathList = Bencode.getList(fMap, "path")
                val path = pathList.joinToString("/") { it.toString() }
                val fileName = pathList.lastOrNull()?.toString() ?: "file_$index"
                val isVideo = isVideoFileName(fileName)

                files.add(
                    TorrentFileItem(
                        index = index,
                        path = path,
                        name = fileName,
                        length = length,
                        offset = currentOffset,
                        isVideo = isVideo
                    )
                )
                currentOffset += length
            }
        } else {
            val length = Bencode.getLong(infoDict, "length", 0L)
            files.add(
                TorrentFileItem(
                    index = 0,
                    path = name,
                    name = name,
                    length = length,
                    offset = 0L,
                    isVideo = isVideoFileName(name)
                )
            )
            currentOffset = length
        }

        // Find main video file (largest video file)
        val mainVideo = files.filter { it.isVideo }.maxByOrNull { it.length }
            ?: files.maxByOrNull { it.length }
            ?: TorrentFileItem(0, name, name, currentOffset, 0L, true)

        return TorrentMetadata(
            infoHash = infoHash,
            name = name,
            pieceLength = pieceLength,
            pieceHashes = pieceHashes,
            totalLength = currentOffset,
            files = files,
            mainVideoFile = mainVideo
        )
    }

    private fun createSyntheticMetadata(magnet: ParsedMagnet, release: TorrentRelease): TorrentMetadata {
        val estimatedSize = if (release.sizeBytes > 0) release.sizeBytes else 1_500_000_000L // 1.5 GB
        val pieceLen = 1048576 // 1MB
        val pieceCount = ((estimatedSize + pieceLen - 1) / pieceLen).toInt()

        val pieceHashes = List(pieceCount) { ByteArray(20) }
        val name = if (release.title.isNotBlank()) "${release.title}.mkv" else "${magnet.displayName}.mkv"

        val mainVideo = TorrentFileItem(
            index = 0,
            path = name,
            name = name,
            length = estimatedSize,
            offset = 0L,
            isVideo = true
        )

        return TorrentMetadata(
            infoHash = magnet.infoHashHex,
            name = name,
            pieceLength = pieceLen,
            pieceHashes = pieceHashes,
            totalLength = estimatedSize,
            files = listOf(mainVideo),
            mainVideoFile = mainVideo
        )
    }

    private fun isVideoFileName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".mkv") || lower.endsWith(".mp4") || lower.endsWith(".avi") ||
                lower.endsWith(".webm") || lower.endsWith(".mov") || lower.endsWith(".ts") ||
                lower.endsWith(".m4v")
    }

    private suspend fun waitForMetadata(magnet: ParsedMagnet, timeoutMs: Long): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (!isRunning.get()) return false
            if (activeMetadata != null) return true
            delay(200)
        }
        return activeMetadata != null
    }

    private fun handleBlockReceived(pieceIndex: Int, offset: Int, data: ByteArray) {
        pieceManager?.onBlockReceived(pieceIndex, offset, data)
        bytesDownloadedTotal.addAndGet(data.size.toLong())
        bytesDownloadedInterval.addAndGet(data.size.toLong())
    }

    private suspend fun runDownloadLoop() = withContext(Dispatchers.IO) {
        while (isRunning.get()) {
            val pm = pieceManager ?: run {
                delay(200)
                return@withContext
            }

            var requestSent = false
            for (peer in connectedPeers.values) {
                if (!peer.isChoked) {
                    val req = pm.getNextBlockToRequest(peer)
                    if (req != null) {
                        peer.requestBlock(req.pieceIndex, req.offset, req.length)
                        requestSent = true
                    }
                }
            }

            if (!requestSent) {
                delay(25)
            }
        }
    }

    private suspend fun runTelemetryLoop() {
        var lastTime = System.currentTimeMillis()
        while (isRunning.get()) {
            delay(1000)
            val now = System.currentTimeMillis()
            val elapsedSec = (now - lastTime).coerceAtLeast(1) / 1000.0
            lastTime = now

            val bytesInterval = bytesDownloadedInterval.getAndSet(0L)
            val speedBps = (bytesInterval / elapsedSec).toLong()

            val pm = pieceManager
            val bufferProg = pm?.getBufferProgress() ?: 0f
            val totalProg = pm?.getTotalProgress() ?: 0f
            val downloadedBytes = pm?.getDownloadedBytes() ?: bytesDownloadedTotal.get()

            val activeSeeders = connectedPeers.values.count { !it.isChoked }
            val state = when {
                bufferProg > 0.05f || (pm?.isHeaderAndFooterReady() == true) -> TorrentEngineState.STREAMING
                connectedPeers.isNotEmpty() -> TorrentEngineState.BUFFERING
                else -> TorrentEngineState.CONNECTING_TRACKERS
            }

            _stats.value = _stats.value.copy(
                state = state,
                connectedPeers = connectedPeers.size,
                activeSeeders = activeSeeders,
                downloadSpeedBps = speedBps,
                downloadedBytes = downloadedBytes,
                bufferProgress = bufferProg,
                totalProgress = totalProg,
                downloadedPiecesCount = pm?.downloadedPieces?.cardinality() ?: 0
            )
        }
    }

    fun onPlaybackSeek(byteOffset: Long) {
        pieceManager?.setPlaybackPosition(byteOffset)
    }

    suspend fun readBytesForStream(offset: Long, length: Int, buffer: ByteArray, bufferOffset: Int = 0): Int {
        val st = storage ?: return -1
        val pm = pieceManager ?: return -1

        pm.setPlaybackPosition(offset)

        // If data is ready in storage, read immediately
        if (pm.isRangeAvailable(offset, length)) {
            return st.readBytes(offset, length, buffer, bufferOffset)
        }

        // Wait up to 3 seconds for requested piece blocks to arrive
        val startWait = System.currentTimeMillis()
        while (System.currentTimeMillis() - startWait < 3000) {
            if (!isRunning.get()) break
            if (pm.isRangeAvailable(offset, length)) {
                return st.readBytes(offset, length, buffer, bufferOffset)
            }
            delay(50)
        }

        return st.readBytes(offset, length, buffer, bufferOffset)
    }

    fun getFileLength(): Long {
        return activeMetadata?.mainVideoFile?.length ?: 0L
    }

    fun getFileName(): String {
        return activeMetadata?.mainVideoFile?.name ?: "video.mkv"
    }

    fun stopSession(clearCache: Boolean = false) {
        isRunning.set(false)
        for (peer in connectedPeers.values) {
            peer.disconnect()
        }
        connectedPeers.clear()
        metadataPieces.clear()

        if (clearCache) {
            storage?.clearCache()
        } else {
            storage?.close()
        }

        storage = null
        pieceManager = null
        activeMetadata = null
        activeMagnet = null
        activeSession = null

        _stats.value = TorrentEngineStats(state = TorrentEngineState.IDLE)
    }
}
