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
        private const val BUFFER_WINDOW_BYTES = 30 * 1024 * 1024L // 30 MB playback buffer window
        private const val HEAD_PIECES_COUNT = 8
        private const val TAIL_PIECES_COUNT = 6
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

    private val ALLOWED_VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "mov", "m4v")

    fun updateHttpStatus(status: String, range: String = "") {
        _stats.value = _stats.value.copy(
            httpStatusCode = status,
            lastRangeHeader = range
        )
    }

    private fun isMatchingEpisode(fileName: String, season: Int?, episode: Int?): Boolean {
        if (episode == null) return false
        val lower = fileName.lowercase()

        // Match SxxExx or SxEx patterns
        if (season != null) {
            val sRegex = Regex("""[sS]0*${season}[._\s-]*[eE]0*${episode}(?!\d)""")
            if (sRegex.containsMatchIn(lower)) return true

            val xRegex = Regex("""\b0*${season}[xX]0*${episode}(?!\d)""")
            if (xRegex.containsMatchIn(lower)) return true
        }

        // Match episode only patterns: e.g. E05, EP05, [05], Episode 05
        val epRegex = Regex("""(?:\b[eE][pP]?|\bepisode[._\s-]*|\[)0*${episode}(?:\]|\b|(?=[._\s-]))""")
        return epRegex.containsMatchIn(lower)
    }

    private fun isSampleOrIgnoredFile(path: String, name: String, length: Long, totalTorrentLength: Long): Boolean {
        val lowerPath = path.lowercase()
        val lowerName = name.lowercase()

        // Ignored keywords
        if (lowerPath.contains("sample") || lowerName.contains("sample")) return true
        if (lowerPath.contains("trailer") || lowerName.contains("trailer")) return true
        if (lowerPath.contains("featurette") || lowerName.contains("featurette")) return true
        if (lowerPath.contains("bonus") || lowerName.contains("bonus")) return true
        if (lowerName.startsWith("sample")) return true

        // If torrent is large (>100MB) and this video file is small (<25MB), it is likely a sample
        if (totalTorrentLength > 100 * 1024 * 1024L && length < 25 * 1024 * 1024L) {
            return true
        }
        return false
    }

    fun startSession(release: TorrentRelease, streamPort: Int = 8899): TorrentStreamSession {
        isStopping.set(false)
        stopSession(clearCache = false)

        val parsedMagnet = MagnetParser.parse(release.magnetUrl)
        val infoHash = when {
            release.infoHash.isNotBlank() -> release.infoHash.lowercase().trim()
            parsedMagnet != null -> parsedMagnet.infoHashHex.lowercase().trim()
            else -> ""
        }

        val effectiveRelease = if (release.infoHash.isBlank() && infoHash.isNotBlank()) {
            release.copy(
                infoHash = infoHash,
                magnetUrl = if (release.magnetUrl.isNotBlank()) release.magnetUrl else MagnetParser.buildMagnetUrl(infoHash, release.title)
            )
        } else {
            release
        }

        activeRelease = effectiveRelease
        val magnetUrl = if (effectiveRelease.magnetUrl.isNotBlank()) {
            effectiveRelease.magnetUrl
        } else {
            MagnetParser.buildMagnetUrl(infoHash, effectiveRelease.title, effectiveRelease.trackerUrls)
        }

        _stats.value = TorrentEngineStats(
            state = TorrentEngineState.CONNECTING_TRACKERS,
            infoHash = infoHash,
            activeFileName = release.fileName ?: release.title,
            fileSizeBytes = release.sizeBytes,
            streamPort = streamPort,
            streamUrl = "http://127.0.0.1:$streamPort/stream?hash=$infoHash"
        )

        engine.start()

        // Immediately register magnet with libtorrent so peer discovery & DHT start without blocking
        val effectiveMagnetUrl = if (magnetUrl.startsWith("magnet:?", ignoreCase = true)) {
            val parsed = MagnetParser.parse(magnetUrl)
            if (parsed != null && parsed.trackers.size < MagnetParser.DEFAULT_TRACKERS.size) {
                MagnetParser.buildMagnetUrl(parsed.infoHashHex, parsed.displayName, parsed.trackers)
            } else {
                magnetUrl
            }
        } else {
            MagnetParser.buildMagnetUrl(infoHash, release.title)
        }

        val initialHandle = engine.downloadMagnet(effectiveMagnetUrl, engine.cacheDir, sequential = true)
        currentHandle = initialHandle

        metadataJob = scope.launch {
            try {
                _stats.value = _stats.value.copy(state = TorrentEngineState.FETCHING_METADATA)

                var ti = initialHandle?.torrentFile() ?: engine.findHandle(infoHash)?.torrentFile()
                var attempts = 0
                val maxAttempts = 60 // 60 * 500ms = 30s
                while (ti == null && attempts < maxAttempts && !isStopping.get()) {
                    delay(500)
                    ti = engine.findHandle(infoHash)?.torrentFile()
                    attempts++
                    if (attempts == 25 && ti == null) {
                        Log.i(TAG, "Attempting fallback fetchMagnetMetadata for $infoHash")
                        ti = engine.fetchMagnetMetadata(effectiveMagnetUrl, timeoutSec = 8)
                    }
                }

                if (ti == null && !isStopping.get()) {
                    val peers = engine.getPeers(infoHash).size
                    val errCode = if (peers == 0) TorrentErrorCode.NO_PEERS else TorrentErrorCode.NO_METADATA
                    val errMsg = if (peers == 0) "No peers found in torrent swarm" else "Torrent metadata timed out"
                    Log.w(TAG, "Metadata timeout: $errCode ($errMsg)")
                    _stats.value = _stats.value.copy(
                        state = TorrentEngineState.ERROR,
                        errorCode = errCode,
                        errorMessage = errMsg
                    )
                    return@launch
                }

                if (ti != null && !isStopping.get()) {
                    onMetadataLoaded(ti, effectiveRelease, streamPort)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in session startup: ${e.message}", e)
                _stats.value = _stats.value.copy(
                    state = TorrentEngineState.ERROR,
                    errorCode = TorrentErrorCode.NO_METADATA,
                    errorMessage = e.message
                )
            }
        }

        startTelemetryLoop()

        return TorrentStreamSession(
            sessionId = infoHash,
            release = effectiveRelease,
            httpStreamUrl = "http://127.0.0.1:$streamPort/stream?hash=$infoHash",
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
            val ext = path.substringAfterLast('.', "").lowercase()
            val isVideo = ext in ALLOWED_VIDEO_EXTENSIONS && !isSampleOrIgnoredFile(path, name, size, ti.totalSize())
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

        val videoCandidates = filesList.filter { it.isVideo }
        if (videoCandidates.isEmpty()) {
            Log.e(TAG, "No valid video file (.mp4/.mkv/.webm/.mov/.m4v) found in torrent!")
            _stats.value = _stats.value.copy(
                state = TorrentEngineState.ERROR,
                errorCode = TorrentErrorCode.NO_VIDEO_FILE,
                errorMessage = "No compatible video file (.mp4/.mkv/.webm/.mov/.m4v) found in torrent."
            )
            return
        }

        // Determine target video file
        val targetFile = if (release.fileIndex != null && release.fileIndex in videoCandidates.map { it.index }) {
            videoCandidates.first { it.index == release.fileIndex }
        } else if (release.season != null || release.episode != null) {
            videoCandidates.firstOrNull { isMatchingEpisode(it.name, release.season, release.episode) }
                ?: videoCandidates.firstOrNull { isMatchingEpisode(it.path, release.season, release.episode) }
                ?: videoCandidates.maxByOrNull { it.length }!!
        } else if (!release.fileName.isNullOrBlank()) {
            videoCandidates.firstOrNull { it.name.equals(release.fileName, ignoreCase = true) }
                ?: videoCandidates.firstOrNull { it.name.contains(release.fileName, ignoreCase = true) }
                ?: videoCandidates.maxByOrNull { it.length }!!
        } else {
            videoCandidates.maxByOrNull { it.length }!!
        }

        activeFileItem = targetFile
        val priorities = Array(numFiles) { i ->
            if (i == targetFile.index) Priority.TOP_PRIORITY else Priority.IGNORE
        }

        val saveDir = engine.cacheDir
        val th = engine.download(
            torrentInfo = ti,
            saveDir = saveDir,
            filePriorities = priorities,
            sequential = true
        ) ?: currentHandle
        try {
            th?.prioritizeFiles(priorities)
        } catch (e: Exception) {
            Log.d(TAG, "prioritizeFiles note: ${e.message}")
        }
        currentHandle = th

        // Resolve absolute file path on disk
        val resolvedPath = File(saveDir, targetFile.path)
        resolvedPath.parentFile?.mkdirs()
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

        // Boost first 8 pieces (head) with highest deadline
        for (i in 0 until HEAD_PIECES_COUNT) {
            val p = startPiece + i
            if (p in 0 until totalPieces) {
                engine.setPiecePriority(infoHash, p, Priority.TOP_PRIORITY)
                engine.setPieceDeadline(infoHash, p, 50)
            }
        }

        // Boost last 6 pieces (tail) for MKV/MP4 container indexes & moov atom
        for (i in 0 until TAIL_PIECES_COUNT) {
            val p = endPiece - i
            if (p in 0 until totalPieces && p >= startPiece) {
                engine.setPiecePriority(infoHash, p, Priority.TOP_PRIORITY)
                engine.setPieceDeadline(infoHash, p, 100)
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

        // Cancel previous deadlines on seek
        engine.clearPieceDeadlines(infoHash)

        // Prioritize immediate seek target piece
        if (currentPiece in 0 until totalPieces) {
            engine.setPiecePriority(infoHash, currentPiece, Priority.TOP_PRIORITY)
            engine.setPieceDeadline(infoHash, currentPiece, 50)
        }

        // Prioritize lookahead window (~30 MB)
        val windowPieces = (BUFFER_WINDOW_BYTES / pieceLen).toInt().coerceAtLeast(8)
        for (i in 1 until windowPieces) {
            val p = currentPiece + i
            if (p in 0 until totalPieces) {
                engine.setPiecePriority(infoHash, p, Priority.TOP_PRIORITY)
                engine.setPieceDeadline(infoHash, p, 100 + i * 50)
            }
        }
        Log.i("ButterflyTorrent", "onPlaybackSeek to $byteOffset (Piece $currentPiece, window $windowPieces pieces)")
    }

    fun awaitRangeAvailable(offset: Long, length: Int, timeoutMs: Long = 15000L): Boolean {
        val ti = activeTorrentInfo ?: return false
        val fileItem = activeFileItem ?: return false
        val infoHash = activeRelease?.infoHash?.lowercase()?.trim() ?: return false
        val pieceLen = ti.pieceLength().toLong()
        if (pieceLen <= 0) return false

        val absoluteStart = fileItem.offset + offset
        val absoluteEnd = absoluteStart + length - 1
        val startPiece = (absoluteStart / pieceLen).toInt().coerceIn(0, ti.numPieces() - 1)
        val endPiece = (absoluteEnd / pieceLen).toInt().coerceIn(0, ti.numPieces() - 1)

        for (p in startPiece..endPiece) {
            if (!engine.havePiece(infoHash, p)) {
                engine.setPiecePriority(infoHash, p, Priority.TOP_PRIORITY)
                engine.setPieceDeadline(infoHash, p, 50)
            }
        }

        // Also prioritize upcoming lookahead pieces
        val lookaheadEnd = minOf(endPiece + 4, ti.numPieces() - 1)
        for (p in (endPiece + 1)..lookaheadEnd) {
            if (!engine.havePiece(infoHash, p)) {
                engine.setPiecePriority(infoHash, p, Priority.TOP_PRIORITY)
                engine.setPieceDeadline(infoHash, p, 150)
            }
        }

        val startMs = System.currentTimeMillis()
        while (System.currentTimeMillis() - startMs < timeoutMs && !isStopping.get()) {
            if (isRangeDownloaded(offset, length)) {
                engine.flushCache(infoHash)
                return true
            }
            try { Thread.sleep(50) } catch (_: Exception) { break }
        }

        val downloaded = isRangeDownloaded(offset, length)
        if (downloaded) {
            engine.flushCache(infoHash)
            return true
        }

        if (!isStopping.get()) {
            Log.w(TAG, "awaitRangeAvailable timed out for offset $offset, len $length")
            _stats.value = _stats.value.copy(
                errorCode = TorrentErrorCode.BUFFER_TIMEOUT,
                errorMessage = "Buffer timeout waiting for swarm piece data"
            )
        }
        return false
    }

    fun isRangeDownloaded(offset: Long, length: Int): Boolean {
        val ti = activeTorrentInfo ?: return false
        val fileItem = activeFileItem ?: return false
        val infoHash = activeRelease?.infoHash?.lowercase()?.trim() ?: return false
        if (infoHash.isBlank()) return false

        val pieceLen = ti.pieceLength().toLong()
        if (pieceLen <= 0) return false

        val absoluteStart = fileItem.offset + offset
        val absoluteEnd = absoluteStart + length - 1
        val startPiece = (absoluteStart / pieceLen).toInt().coerceIn(0, ti.numPieces() - 1)
        val endPiece = (absoluteEnd / pieceLen).toInt().coerceIn(0, ti.numPieces() - 1)

        for (p in startPiece..endPiece) {
            if (!engine.havePiece(infoHash, p)) {
                return false
            }
        }
        return true
    }

    /**
     * Reads bytes strictly from the active torrent file on disk for HTTP streaming.
     * Never uses a generic cache fallback.
     */
    fun readBytesForStream(offset: Long, length: Int, buffer: ByteArray, bufferOffset: Int = 0): Int {
        val fileItem = activeFileItem ?: return 0
        val diskFile = activeFileOnDisk ?: return 0

        val maxLen = fileItem.length
        if (offset >= maxLen) return -1
        val actualLen = min(length.toLong(), maxLen - offset).toInt()
        if (actualLen <= 0) return 0

        // Bounded wait for piece availability
        val available = awaitRangeAvailable(offset, actualLen, timeoutMs = 15000L)
        if (!available && offset < maxLen - 64 * 1024) {
            Log.d("ButterflyTorrent", "Range [$offset, ${offset + actualLen}] not available yet. Waiting for swarm pieces...")
            return 0
        }

        if (!diskFile.exists()) {
            Log.w("ButterflyTorrent", "Active file on disk does not exist yet: ${diskFile.absolutePath}")
            return 0
        }

        return fileLock.withLock {
            try {
                RandomAccessFile(diskFile, "r").use { raf ->
                    val fileLength = raf.length()
                    if (offset >= fileLength && offset < maxLen) {
                        return@withLock 0
                    } else if (offset >= maxLen) {
                        return@withLock -1
                    }
                    val readable = min(actualLen.toLong(), fileLength - offset).toInt()
                    if (readable <= 0) return@withLock 0

                    raf.seek(offset)
                    val readCount = raf.read(buffer, bufferOffset, readable)
                    if (readCount > 0) {
                        readCount
                    } else {
                        0
                    }
                }
            } catch (e: Exception) {
                Log.w("ButterflyTorrent", "readBytesForStream error at offset $offset: ${e.message}")
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
                    val infoHash = release.infoHash.lowercase().trim()
                    val ti = activeTorrentInfo
                    val th = engine.findHandle(infoHash)
                    if (ti == null) {
                        val dhtNodes = engine.getDhtNodes()
                        _stats.value = _stats.value.copy(
                            state = TorrentEngineState.FETCHING_METADATA,
                            infoHash = infoHash,
                            dhtNodes = dhtNodes,
                            errorMessage = if (dhtNodes == 0L) "Connecting to trackers & DHT network..." else "Retrieving swarm metadata ($dhtNodes DHT nodes active)..."
                        )
                    } else if (th != null && th.isValid) {
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
                        val bufferProg = calculateBufferProgress(th, ti, activeFileItem, playbackByteOffset.get())

                        val state = when {
                            isFinished -> TorrentEngineState.STREAMING
                            bufferProg >= 0.1f -> TorrentEngineState.STREAMING
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
