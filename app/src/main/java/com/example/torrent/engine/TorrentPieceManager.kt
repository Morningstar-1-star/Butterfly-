package com.example.torrent.engine

import android.util.Log
import com.example.torrent.model.TorrentMetadata
import com.example.torrent.protocol.PeerConnection
import java.util.BitSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class BlockRequest(
    val pieceIndex: Int,
    val offset: Int,
    val length: Int
)

class TorrentPieceManager(
    val metadata: TorrentMetadata,
    val storage: TorrentStorage
) {
    companion object {
        private const val TAG = "TorrentPieceManager"
        const val BLOCK_SIZE = 16384 // 16 KB per block
    }

    val totalPieces: Int = metadata.pieceHashes.size
    val pieceLength: Int = metadata.pieceLength

    val firstVideoPiece: Int
    val lastVideoPiece: Int
    val videoPieceCount: Int

    val downloadedPieces = BitSet(totalPieces)
    private val inFlightBlocks = ConcurrentHashMap<String, Long>() // "piece:offset" -> timestamp
    private val completedBlocksPerPiece = ConcurrentHashMap<Int, BitSet>()

    private val currentPlaybackPiece = AtomicInteger(0)

    init {
        val videoStart = metadata.mainVideoFile.offset
        val videoEnd = metadata.mainVideoFile.offset + metadata.mainVideoFile.length - 1

        firstVideoPiece = (videoStart / pieceLength).toInt().coerceIn(0, totalPieces - 1)
        lastVideoPiece = (videoEnd / pieceLength).toInt().coerceIn(0, totalPieces - 1)
        videoPieceCount = (lastVideoPiece - firstVideoPiece + 1).coerceAtLeast(1)

        currentPlaybackPiece.set(firstVideoPiece)
    }

    fun setPlaybackPosition(byteOffset: Long) {
        val globalOffset = metadata.mainVideoFile.offset + byteOffset
        val targetPiece = (globalOffset / pieceLength).toInt().coerceIn(firstVideoPiece, lastVideoPiece)
        currentPlaybackPiece.set(targetPiece)
    }

    fun isHeaderAndFooterReady(): Boolean {
        val headerReady = downloadedPieces.get(firstVideoPiece) &&
                (videoPieceCount <= 1 || downloadedPieces.get(minOf(firstVideoPiece + 1, lastVideoPiece)))
        val footerReady = downloadedPieces.get(lastVideoPiece)
        return headerReady && footerReady
    }

    fun isRangeAvailable(startByteInVideo: Long, length: Int): Boolean {
        val startGlobal = metadata.mainVideoFile.offset + startByteInVideo
        val endGlobal = startGlobal + length - 1

        val startPiece = (startGlobal / pieceLength).toInt().coerceIn(0, totalPieces - 1)
        val endPiece = (endGlobal / pieceLength).toInt().coerceIn(0, totalPieces - 1)

        for (p in startPiece..endPiece) {
            if (!downloadedPieces.get(p)) return false
        }
        return true
    }

    fun getBufferProgress(): Float {
        val cur = currentPlaybackPiece.get()
        val windowSize = 20
        var downloadedInWindow = 0
        val end = minOf(cur + windowSize, lastVideoPiece)
        val totalInWindow = (end - cur + 1).coerceAtLeast(1)

        for (p in cur..end) {
            if (downloadedPieces.get(p)) downloadedInWindow++
        }
        return downloadedInWindow.toFloat() / totalInWindow.toFloat()
    }

    fun getTotalProgress(): Float {
        var count = 0
        for (p in firstVideoPiece..lastVideoPiece) {
            if (downloadedPieces.get(p)) count++
        }
        return count.toFloat() / videoPieceCount.toFloat()
    }

    fun getDownloadedBytes(): Long {
        var count = 0
        for (p in firstVideoPiece..lastVideoPiece) {
            if (downloadedPieces.get(p)) count++
        }
        return minOf(count.toLong() * pieceLength, metadata.mainVideoFile.length)
    }

    fun getNextBlockToRequest(peer: PeerConnection): BlockRequest? {
        val candidatePieces = getPrioritizedPieceList()
        val now = System.currentTimeMillis()

        for (pieceIndex in candidatePieces) {
            if (downloadedPieces.get(pieceIndex)) continue
            if (!peer.hasPiece(pieceIndex)) continue

            val pieceSize = getPieceSize(pieceIndex)
            val blocksInPiece = (pieceSize + BLOCK_SIZE - 1) / BLOCK_SIZE
            val bitset = completedBlocksPerPiece.computeIfAbsent(pieceIndex) { BitSet(blocksInPiece) }

            for (blockIdx in 0 until blocksInPiece) {
                if (bitset.get(blockIdx)) continue

                val blockOffset = blockIdx * BLOCK_SIZE
                val blockLength = minOf(BLOCK_SIZE, pieceSize - blockOffset)
                val key = "$pieceIndex:$blockOffset"

                val lastReq = inFlightBlocks[key]
                if (lastReq == null || (now - lastReq > 4000)) {
                    inFlightBlocks[key] = now
                    return BlockRequest(pieceIndex, blockOffset, blockLength)
                }
            }
        }
        return null
    }

    fun onBlockReceived(pieceIndex: Int, blockOffset: Int, data: ByteArray) {
        val key = "$pieceIndex:$blockOffset"
        inFlightBlocks.remove(key)

        storage.writePieceBlock(pieceIndex, blockOffset, data)

        val pieceSize = getPieceSize(pieceIndex)
        val blocksInPiece = (pieceSize + BLOCK_SIZE - 1) / BLOCK_SIZE
        val blockIdx = blockOffset / BLOCK_SIZE

        val bitset = completedBlocksPerPiece.computeIfAbsent(pieceIndex) { BitSet(blocksInPiece) }
        bitset.set(blockIdx)

        if (bitset.cardinality() >= blocksInPiece) {
            // Whole piece downloaded - verify SHA1
            val isValid = storage.verifyPieceHash(pieceIndex)
            if (isValid) {
                downloadedPieces.set(pieceIndex)
                completedBlocksPerPiece.remove(pieceIndex)
            } else {
                // Corrupt piece - clear blocks and re-request
                bitset.clear()
            }
        }
    }

    private fun getPrioritizedPieceList(): List<Int> {
        val list = LinkedHashSet<Int>()
        val cur = currentPlaybackPiece.get()

        // 1. Initial Seek Header (First 2 pieces of video)
        list.add(firstVideoPiece)
        if (firstVideoPiece + 1 <= lastVideoPiece) list.add(firstVideoPiece + 1)

        // 2. Initial Seek Footer (Last 2 pieces of video for MKV index/cues)
        list.add(lastVideoPiece)
        if (lastVideoPiece - 1 >= firstVideoPiece) list.add(lastVideoPiece - 1)

        // 3. Playback Sliding Window (Immediate 15 pieces from current position)
        val windowEnd = minOf(cur + 15, lastVideoPiece)
        for (p in cur..windowEnd) {
            list.add(p)
        }

        // 4. Wider Ahead Window (Next 30 pieces)
        val widerEnd = minOf(cur + 45, lastVideoPiece)
        for (p in (windowEnd + 1)..widerEnd) {
            list.add(p)
        }

        // 5. Remaining video pieces sequentially
        for (p in firstVideoPiece..lastVideoPiece) {
            list.add(p)
        }

        return list.toList()
    }

    private fun getPieceSize(pieceIndex: Int): Int {
        val globalOffset = pieceIndex.toLong() * pieceLength
        val remaining = metadata.totalLength - globalOffset
        return minOf(pieceLength.toLong(), remaining).toInt()
    }
}
