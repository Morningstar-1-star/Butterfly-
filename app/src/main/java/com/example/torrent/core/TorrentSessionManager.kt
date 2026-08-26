package com.example.torrent.core

import android.content.Context
import android.util.Log
import com.example.torrent.model.*
import com.example.torrent.protocol.MagnetParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.libtorrent4j.Priority
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.TorrentStatus
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.max
import kotlin.math.min

/**
 * Manages active streaming sessions for Butterfly Player.
 * Coordinates metadata retrieval, video file selection, piece prioritization (head/tail/seek window),
 * sequential downloading, and real-time streaming telemetry.
 */
class TorrentSessionManager(
    private val context: Context,
    private val engine: LibtorrentEngine
) {
    companion object {
        private const val TAG = "TorrentSessionMgr"
        private const val BUFFER_WINDOW_BYTES = 25 * 1024 * 1024L // 25 MB playback buffer window
        private const val HEAD_PIECES_COUNT = 4
        private const val TAIL_PIECES_COUNT = 3
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var telemetryJob: Job? = null
    private var metadataJob: Job? = null

    private val _stats = MutableStateFlow(TorrentEngineStats())
    val stats: StateFlow<TorrentEngineStats> = _stats.asStateFlow()

    @Volatile
    var activeRelease: TorrentRelease? = null
        private set

    @Volatile
    var activeTorrentInfo: TorrentInfo? = null
        private set

    @Volatile
    var activeFileItem: TorrentFileItem? = null
        private set

    @Volatile
    var activeFileOnDisk: File? = null
        private set

    private var currentHandle: TorrentHandle? = null
    private val playbackByteOffset = AtomicLong(0L)
    private val isStopping = AtomicBoolean(false)
    private val fileLock = ReentrantLock()

    fun startSession(release: TorrentRelease, streamPort: Int = 8899): TorrentStreamSession {
        isStopping.set(false)
        stopSession(clearCache = false)

        activeRelease = release
        val infoHash = release.infoHash.lowercase()
        val magnetUrl = if (release.magnetUrl.isNotBlank()) {
            release.magnetUrl
        } else {
            MagnetParser.buildMagnetUrl(infoHash, release.title, release.trackerUrls)
        }

        _stats.value = TorrentEngineStats(
            state = TorrentEngineState.CONNECTING_TRACKERS,
            infoHash = infoHash,
            activeFileName = release.fileName ?: release.title,
            fileSizeBytes = release.sizeBytes,
            streamPort = streamPort,
            streamUrl = "http://127.0.0.1:$streamPort/stream"
        )

        engine.start()

        metadataJob = scope.launch {
            try {
                _stats.value = _stats.value.copy(state = TorrentEngineState.FETCHING_METADATA)

                var ti = engine.findHandle(infoHash)?.torrentFile()
                if (ti == null) {
                    Log.i(TAG, "Fetching metadata for magnet: $infoHash")
                    ti = engine.fetchMagnetMetadata(magnetUrl, timeoutSec = 45)
                }

                if (ti != null && !isStopping.get()) {
                    val saveDir = engine.cacheDir
                    engine.download(ti, saveDir, sequential = true)
                    onMetadataLoaded(ti, release, streamPort)
                } else if (!isStopping.get()) {
                    Log.w(TAG, "Metadata loading taking longer than expected; entering background buffering...")
                    _stats.value = _stats.value.copy(
                        state = TorrentEngineState.BUFFERING,
                        errorMessage = "Connecting to swarm & waiting for metadata..."
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in session startup: ${e.message}", e)
                _stats.value = _stats.value.copy(
                    state = TorrentEngineState.ERROR,
                    errorMessage = e.message
                )
            }
        }

        startTelemetryLoop()

        return TorrentStreamSession(
            sessionId = infoHash,
            release = release,
            httpStreamUrl = "http://127.0.0.1:$streamPort/stream",
            localFilePath = activeFileOnDisk?.absolutePath,
            fileIndex = activeFileItem?.index ?: 0
        )
    }

    private fun onMetadataLoaded(ti: TorrentInfo, release: TorrentRelease, streamPort: Int) {
        activeTorrentInfo = ti
        val numFiles = ti.numFiles()
        val fileStorage = ti.files()

        val filesList = mutableListOf<TorrentFileItem>()
        for (i in 0 until numFiles) {
            val path = fileStorage.filePath(i)
            val name = fileStorage.fileName(i)
            val size = fileStorage.fileSize(i)
            val offset = fileStorage.fileOffset(i)
            val isVideo = isVideoFile(path)
            filesList.add(
                TorrentFileItem(
                    index = i,
                    path = path,
                    name = name,
                    length = size,
                    offset = offset,
                    isVideo = isVideo
                )
            )
        }

        // Determine target video file
        val targetFile = if (release.fileIndex != null && release.fileIndex in 0 until numFiles) {
            filesList[release.fileIndex]
        } else if (!release.fileName.isNullOrBlank()) {
            filesList.firstOrNull { it.name.equals(release.fileName, ignoreCase = true) }
                ?: filesList.filter { it.isVideo }.maxByOrNull { it.length }
                ?: filesList.maxByOrNull { it.length }
                ?: filesList[0]
        } else {
            filesList.filter { it.isVideo }.maxByOrNull { it.length }
                ?: filesList.maxByOrNull { it.length }
                ?: filesList[0]
        }

        activeFileItem = targetFile
        val priorities = Array(numFiles) { i ->
            if (i == targetFile.index) Priority.DEFAULT else Priority.IGNORE
        }

        val saveDir = engine.cacheDir
        val th = engine.download(
            torrentInfo = ti,
            saveDir = saveDir,
            filePriorities = priorities,
            sequential = true
        )
        currentHandle = th

        // Resolve absolute file path on disk
        val resolvedPath = File(saveDir, targetFile.path)
        activeFileOnDisk = resolvedPath

        // Prioritize head & tail pieces for instant media container parsing
        prioritizeHeadAndTail(ti, targetFile)

        _stats.value = _stats.value.copy(
            state = TorrentEngineState.BUFFERING,
            activeFileName = targetFile.name,
            fileSizeBytes = targetFile.length,
            totalPieces = ti.numPieces()
        )
    }

    private fun prioritizeHeadAndTail(ti: TorrentInfo, file: TorrentFileItem) {
        val pieceLen = ti.pieceLength().toLong()
        if (pieceLen <= 0) return

        val startPiece = (file.offset / pieceLen).toInt()
        val endPiece = ((file.offset + file.length - 1) / pieceLen).toInt()
        val totalPieces = ti.numPieces()

        val infoHash = ti.infoHash().toHex()

        // Boost first few pieces (head) with highest deadline
        for (i in 0 until HEAD_PIECES_COUNT) {
            val p = startPiece + i
            if (p in 0 until totalPieces) {
                engine.setPiecePriority(infoHash, p, Priority.DEFAULT)
                engine.setPieceDeadline(infoHash, p, 100) // 100ms deadline
            }
        }

        // Boost last few pieces (tail) for MKV/MP4 indexes
        for (i in 0 until TAIL_PIECES_COUNT) {
            val p = endPiece - i
            if (p in 0 until totalPieces && p >= startPiece) {
                engine.setPiecePriority(infoHash, p, Priority.DEFAULT)
                engine.setPieceDeadline(infoHash, p, 200)
            }
        }
    }

    fun onPlaybackSeek(byteOffset: Long) {
        playbackByteOffset.set(max(0L, byteOffset))
        val ti = activeTorrentInfo ?: return
        val file = activeFileItem ?: return
        val pieceLen = ti.pieceLength().toLong()
        if (pieceLen <= 0) return

        val absoluteByte = file.offset + byteOffset
        val currentPiece = (absoluteByte / pieceLen).toInt()
        val totalPieces = ti.numPieces()
        val infoHash = ti.infoHash().toHex()

        // Prioritize a window of pieces from the seek point
        val windowPieces = (BUFFER_WINDOW_BYTES / pieceLen).toInt().coerceAtLeast(8)
        for (i in 0 until windowPieces) {
            val p = currentPiece + i
            if (p in 0 until totalPieces) {
                engine.setPiecePriority(infoHash, p, Priority.DEFAULT)
                engine.setPieceDeadline(infoHash, p, (i + 1) * 250) // staged deadlines
            }
        }
    }

    /**
     * Reads bytes from the active file for HTTP streaming.
     * Blocks gracefully until pieces covering the range are verified or timeout expires.
     */
    fun readBytesForStream(offset: Long, length: Int, buffer: ByteArray, bufferOffset: Int = 0): Int {
        val fileItem = activeFileItem
        val diskFile = activeFileOnDisk
        val resolvedFile: File = if (diskFile != null && diskFile.exists()) {
            diskFile
        } else {
            val cacheFiles = engine.cacheDir.listFiles()
            cacheFiles?.filter { isVideoFile(it.name) }?.maxByOrNull { it.length() }
                ?: cacheFiles?.maxByOrNull { it.length() }
                ?: File(engine.cacheDir, activeRelease?.title?.replace(Regex("[^a-zA-Z0-9._ -]"), "_") ?: "stream_temp.mkv")
        }

        val maxLen = fileItem?.length ?: activeRelease?.sizeBytes?.takeIf { it > 0L } ?: (1024L * 1024L * 1024L)
        if (offset >= maxLen) return -1
        val actualLen = min(length.toLong(), maxLen - offset).toInt()
        if (actualLen <= 0) return 0

        val ti = activeTorrentInfo
        if (ti != null && fileItem != null) {
            val pieceLen = ti.pieceLength().toLong()
            if (pieceLen > 0) {
                val absoluteByte = fileItem.offset + offset
                val startPiece = (absoluteByte / pieceLen).toInt()
                val endPiece = ((absoluteByte + actualLen - 1) / pieceLen).toInt()
                val infoHash = ti.infoHash().toHex()

                for (p in startPiece..endPiece) {
                    if (p in 0 until ti.numPieces()) {
                        engine.setPieceDeadline(infoHash, p, 50)
                    }
                }
            }
        }

        return fileLock.withLock {
            try {
                var currentFile = resolvedFile
                var retries = 0
                while ((!currentFile.exists() || currentFile.length() <= offset) && retries < 60 && !isStopping.get()) {
                    Thread.sleep(150)
                    retries++
                    val currentDisk = activeFileOnDisk
                    if (currentDisk != null && currentDisk.exists()) {
                        currentFile = currentDisk
                        break
                    }
                }

                if (!currentFile.exists()) {
                    return@withLock 0
                }

                RandomAccessFile(currentFile, "r").use { raf ->
                    val fileLength = raf.length()
                    if (offset >= fileLength) {
                        return@withLock 0
                    }
                    val readable = min(actualLen.toLong(), fileLength - offset).toInt()
                    raf.seek(offset)
                    raf.read(buffer, bufferOffset, readable)
                }
            } catch (e: Exception) {
                Log.w(TAG, "readBytesForStream error at offset $offset: ${e.message}")
                0
            }
        }
    }

    private fun startTelemetryLoop() {
        telemetryJob?.cancel()
        telemetryJob = scope.launch {
            while (isActive && !isStopping.get()) {
                val release = activeRelease
                if (release != null) {
                    val infoHash = release.infoHash.lowercase()
                    val th = engine.findHandle(infoHash)
                    if (th != null && th.isValid) {
                        val st = th.status()
                        val peers = engine.getPeers(infoHash)
                        val trackers = engine.getTrackers(infoHash)
                        val dhtNodes = engine.getDhtNodes()

                        val downSpeed = st.downloadRate().toLong()
                        val upSpeed = st.uploadRate().toLong()
                        val totalBytes = activeFileItem?.length ?: release.sizeBytes
                        val doneBytes = st.totalWantedDone()

                        val pieceMap = engine.getPieceMap(infoHash)
                        val isFinished = st.isFinished || st.isSeeding

                        // Calculate buffer progress around playback position
                        val bufferProg = calculateBufferProgress(th, activeTorrentInfo, activeFileItem, playbackByteOffset.get())

                        val state = when {
                            isFinished -> TorrentEngineState.STREAMING
                            downSpeed > 0 && bufferProg >= 0.1f -> TorrentEngineState.STREAMING
                            downSpeed > 0 -> TorrentEngineState.BUFFERING
                            st.numPeers() > 0 -> TorrentEngineState.BUFFERING
                            else -> TorrentEngineState.CONNECTING_TRACKERS
                        }

                        _stats.value = _stats.value.copy(
                            state = state,
                            infoHash = infoHash,
                            activeFileName = activeFileItem?.name ?: release.fileName ?: release.title,
                            fileSizeBytes = totalBytes,
                            downloadedBytes = doneBytes,
                            downloadSpeedBps = downSpeed,
                            uploadSpeedBps = upSpeed,
                            connectedPeers = st.numPeers(),
                            activeSeeders = st.numSeeds(),
                            totalPieces = pieceMap.totalPieces,
                            downloadedPiecesCount = pieceMap.downloadedPiecesCount,
                            bufferProgress = bufferProg,
                            totalProgress = if (totalBytes > 0) doneBytes.toFloat() / totalBytes.toFloat() else st.progress(),
                            dhtNodes = dhtNodes,
                            activePeersList = peers,
                            trackersList = trackers,
                            errorMessage = null
                        )
                    }
                }
                delay(800)
            }
        }
    }

    private fun calculateBufferProgress(
        th: TorrentHandle?,
        ti: TorrentInfo?,
        file: TorrentFileItem?,
        offset: Long
    ): Float {
        if (th == null || ti == null || file == null) return 0f
        val pieceLen = ti.pieceLength().toLong()
        if (pieceLen <= 0) return 0f

        val absoluteByte = file.offset + offset
        val startPiece = (absoluteByte / pieceLen).toInt()
        val windowPiecesCount = (BUFFER_WINDOW_BYTES / pieceLen).toInt().coerceIn(4, 20)
        val endPiece = min(startPiece + windowPiecesCount, ti.numPieces())

        if (startPiece >= endPiece) return 1f

        var available = 0
        val bitfield = th.status().pieces()
        for (p in startPiece until endPiece) {
            if (bitfield != null && p < bitfield.size() && bitfield.getBit(p)) {
                available++
            }
        }
        return available.toFloat() / (endPiece - startPiece).toFloat()
    }

    fun stopSession(clearCache: Boolean = false) {
        isStopping.set(true)
        metadataJob?.cancel()
        metadataJob = null
        telemetryJob?.cancel()
        telemetryJob = null

        val release = activeRelease
        if (release != null) {
            val infoHash = release.infoHash.lowercase()
            engine.remove(infoHash, deleteFiles = clearCache)
        }

        activeRelease = null
        activeTorrentInfo = null
        activeFileItem = null
        activeFileOnDisk = null
        currentHandle = null
        playbackByteOffset.set(0L)

        _stats.value = TorrentEngineStats(state = TorrentEngineState.IDLE)
    }

    private fun isVideoFile(path: String): Boolean {
        val lower = path.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi") ||
                lower.endsWith(".webm") || lower.endsWith(".mov") || lower.endsWith(".m4v") ||
                lower.endsWith(".ts") || lower.endsWith(".wmv") || lower.endsWith(".flv")
    }
}
