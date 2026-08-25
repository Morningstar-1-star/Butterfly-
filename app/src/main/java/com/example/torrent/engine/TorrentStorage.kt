package com.example.torrent.engine

import android.content.Context
import android.util.Log
import com.example.torrent.model.TorrentFileItem
import com.example.torrent.model.TorrentMetadata
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class TorrentStorage(
    private val context: Context,
    val metadata: TorrentMetadata
) {
    companion object {
        private const val TAG = "TorrentStorage"
    }

    private val cacheDir: File = File(context.cacheDir, "torrent_cache").apply { mkdirs() }
    private val sessionDir: File = File(cacheDir, metadata.infoHash).apply { mkdirs() }
    private val targetFile: File
    private var randomAccessFile: RandomAccessFile? = null
    private val fileLock = Any()

    init {
        val safeName = metadata.mainVideoFile.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        targetFile = File(sessionDir, safeName)
        try {
            randomAccessFile = RandomAccessFile(targetFile, "rw")
            // Allocate file length sparsely or directly
            if (targetFile.length() < metadata.mainVideoFile.length) {
                randomAccessFile?.setLength(metadata.mainVideoFile.length)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing storage file: ${e.message}", e)
        }
    }

    fun writePieceBlock(pieceIndex: Int, blockOffset: Int, data: ByteArray) {
        synchronized(fileLock) {
            val raf = randomAccessFile ?: return
            try {
                val pieceGlobalOffset = pieceIndex.toLong() * metadata.pieceLength
                val videoFileGlobalOffset = metadata.mainVideoFile.offset
                val videoFileLength = metadata.mainVideoFile.length

                val writeGlobalOffset = pieceGlobalOffset + blockOffset

                // Check if this block falls within the selected video file range
                if (writeGlobalOffset >= videoFileGlobalOffset && writeGlobalOffset < videoFileGlobalOffset + videoFileLength) {
                    val relativeOffsetInVideo = writeGlobalOffset - videoFileGlobalOffset
                    val availableInVideo = videoFileLength - relativeOffsetInVideo
                    val writeLength = minOf(data.size.toLong(), availableInVideo).toInt()

                    raf.seek(relativeOffsetInVideo)
                    raf.write(data, 0, writeLength)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing piece $pieceIndex block: ${e.message}")
            }
        }
    }

    fun readBytes(offset: Long, length: Int, buffer: ByteArray, bufferOffset: Int = 0): Int {
        synchronized(fileLock) {
            val raf = randomAccessFile ?: return -1
            return try {
                if (offset >= metadata.mainVideoFile.length) return -1
                val bytesToRead = minOf(length.toLong(), metadata.mainVideoFile.length - offset).toInt()
                raf.seek(offset)
                raf.read(buffer, bufferOffset, bytesToRead)
            } catch (e: Exception) {
                Log.e(TAG, "Error reading bytes at offset $offset: ${e.message}")
                -1
            }
        }
    }

    fun verifyPieceHash(pieceIndex: Int): Boolean {
        if (pieceIndex < 0 || pieceIndex >= metadata.pieceHashes.size) return false
        val expectedHash = metadata.pieceHashes[pieceIndex]

        // If piece hash is synthetic zeroes (prior to BEP 9 metadata completion), accept piece
        if (expectedHash.all { it == 0.toByte() }) {
            return true
        }

        val pieceGlobalOffset = pieceIndex.toLong() * metadata.pieceLength
        val videoFileGlobalOffset = metadata.mainVideoFile.offset
        val videoFileLength = metadata.mainVideoFile.length

        // If piece is entirely outside video file, treat as valid
        if (pieceGlobalOffset + metadata.pieceLength <= videoFileGlobalOffset || pieceGlobalOffset >= videoFileGlobalOffset + videoFileLength) {
            return true
        }

        // Read piece data
        synchronized(fileLock) {
            val raf = randomAccessFile ?: return false
            try {
                val digest = MessageDigest.getInstance("SHA-1")
                val actualPieceLen = minOf(metadata.pieceLength.toLong(), metadata.totalLength - pieceGlobalOffset).toInt()
                val pieceBuffer = ByteArray(actualPieceLen)

                val relStart = maxOf(0L, pieceGlobalOffset - videoFileGlobalOffset)
                val relEnd = minOf(videoFileLength, (pieceGlobalOffset + actualPieceLen) - videoFileGlobalOffset)
                val bytesInVideo = (relEnd - relStart).toInt()

                if (bytesInVideo > 0) {
                    raf.seek(relStart)
                    raf.readFully(pieceBuffer, 0, bytesInVideo)
                }

                val actualHash = digest.digest(pieceBuffer)
                return actualHash.contentEquals(expectedHash)
            } catch (_: Exception) {
                return false
            }
        }
    }

    fun getLocalFile(): File = targetFile

    fun close() {
        synchronized(fileLock) {
            try {
                randomAccessFile?.close()
                randomAccessFile = null
            } catch (_: Exception) {}
        }
    }

    fun clearCache() {
        close()
        try {
            sessionDir.deleteRecursively()
        } catch (_: Exception) {}
    }
}
