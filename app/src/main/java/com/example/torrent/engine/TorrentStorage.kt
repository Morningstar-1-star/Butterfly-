package com.example.torrent.engine

import android.content.Context
import android.util.Log
import com.example.torrent.model.TorrentFileItem
import com.example.torrent.model.TorrentMetadata
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance, thread-safe BitTorrent multi-file storage engine.
 * Seamlessly manages piece I/O and SHA-1 hashing across file boundaries.
 */
class TorrentStorage(
    private val context: Context,
    val metadata: TorrentMetadata
) {
    companion object {
        private const val TAG = "TorrentStorage"
    }

    private val cacheDir: File = File(context.cacheDir, "torrent_cache").apply { mkdirs() }
    private val sessionDir: File = File(cacheDir, metadata.infoHash).apply { mkdirs() }
    
    // Mapping of TorrentFileItem -> RandomAccessFile
    private val fileHandles = mutableListOf<FileEntry>()
    private val fileLock = Any()

    private data class FileEntry(
        val item: TorrentFileItem,
        val localFile: File,
        var raf: RandomAccessFile?
    )

    init {
        synchronized(fileLock) {
            val files = if (metadata.files.isNotEmpty()) metadata.files else listOf(metadata.mainVideoFile)
            for (f in files) {
                val safePath = f.name.replace(Regex("[^a-zA-Z0-9._/ -]"), "_")
                val local = File(sessionDir, safePath)
                local.parentFile?.mkdirs()
                try {
                    val raf = RandomAccessFile(local, "rw")
                    if (local.length() < f.length) {
                        raf.setLength(f.length)
                    }
                    fileHandles.add(FileEntry(f, local, raf))
                } catch (e: Exception) {
                    Log.e(TAG, "Error opening storage file ${f.name}: ${e.message}")
                }
            }
        }
    }

    /**
     * Writes raw piece block bytes across target files at global byte offset.
     */
    fun writePieceBlock(pieceIndex: Int, blockOffset: Int, data: ByteArray) {
        val globalOffset = pieceIndex.toLong() * metadata.pieceLength + blockOffset
        val length = data.size

        synchronized(fileLock) {
            var dataOffset = 0
            var remaining = length

            while (remaining > 0) {
                val currentGlobalPos = globalOffset + dataOffset
                // Find target file intersecting currentGlobalPos
                val entry = fileHandles.find {
                    currentGlobalPos >= it.item.offset && currentGlobalPos < it.item.offset + it.item.length
                } ?: break

                val offsetInFile = currentGlobalPos - entry.item.offset
                val availableInFile = entry.item.length - offsetInFile
                val writeBytes = minOf(remaining.toLong(), availableInFile).toInt()

                try {
                    val raf = entry.raf ?: RandomAccessFile(entry.localFile, "rw").also { entry.raf = it }
                    raf.seek(offsetInFile)
                    raf.write(data, dataOffset, writeBytes)
                } catch (e: Exception) {
                    Log.e(TAG, "Error writing block at global offset $currentGlobalPos to ${entry.item.name}: ${e.message}")
                    break
                }

                dataOffset += writeBytes
                remaining -= writeBytes
            }
        }
    }

    /**
     * Reads bytes spanning across files starting at global offset.
     */
    fun readGlobalBytes(globalOffset: Long, length: Int, buffer: ByteArray, bufferOffset: Int = 0): Int {
        synchronized(fileLock) {
            var bytesReadTotal = 0
            var remaining = length

            while (remaining > 0) {
                val currentGlobalPos = globalOffset + bytesReadTotal
                val entry = fileHandles.find {
                    currentGlobalPos >= it.item.offset && currentGlobalPos < it.item.offset + it.item.length
                } ?: break

                val offsetInFile = currentGlobalPos - entry.item.offset
                val availableInFile = entry.item.length - offsetInFile
                val toRead = minOf(remaining.toLong(), availableInFile).toInt()

                try {
                    val raf = entry.raf ?: RandomAccessFile(entry.localFile, "r").also { entry.raf = it }
                    raf.seek(offsetInFile)
                    val read = raf.read(buffer, bufferOffset + bytesReadTotal, toRead)
                    if (read <= 0) break
                    bytesReadTotal += read
                    remaining -= read
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading at global offset $currentGlobalPos: ${e.message}")
                    break
                }
            }
            return if (bytesReadTotal > 0) bytesReadTotal else -1
        }
    }

    /**
     * Reads bytes from the main media file for HTTP playback streaming.
     */
    fun readBytes(offset: Long, length: Int, buffer: ByteArray, bufferOffset: Int = 0): Int {
        val globalOffset = metadata.mainVideoFile.offset + offset
        return readGlobalBytes(globalOffset, length, buffer, bufferOffset)
    }

    /**
     * Computes SHA-1 hash of the piece across file boundaries and validates against metadata.
     */
    fun verifyPieceHash(pieceIndex: Int): Boolean {
        if (pieceIndex < 0 || pieceIndex >= metadata.pieceHashes.size) return false
        val expectedHash = metadata.pieceHashes[pieceIndex]

        // Prior to BEP 9 metadata completion or synthetic zero hashes, accept piece
        if (expectedHash.all { it == 0.toByte() }) {
            return true
        }

        val pieceGlobalOffset = pieceIndex.toLong() * metadata.pieceLength
        val actualPieceLen = minOf(metadata.pieceLength.toLong(), metadata.totalLength - pieceGlobalOffset).toInt()
        val pieceBuffer = ByteArray(actualPieceLen)

        val read = readGlobalBytes(pieceGlobalOffset, actualPieceLen, pieceBuffer, 0)
        if (read < actualPieceLen) {
            return false
        }

        return try {
            val digest = MessageDigest.getInstance("SHA-1")
            val actualHash = digest.digest(pieceBuffer)
            actualHash.contentEquals(expectedHash)
        } catch (_: Exception) {
            false
        }
    }

    fun getMainVideoFile(): File {
        val entry = fileHandles.find { it.item.name == metadata.mainVideoFile.name }
        return entry?.localFile ?: File(sessionDir, metadata.mainVideoFile.name)
    }

    fun close() {
        synchronized(fileLock) {
            for (entry in fileHandles) {
                try {
                    entry.raf?.close()
                    entry.raf = null
                } catch (_: Exception) {}
            }
        }
    }

    fun clearCache() {
        close()
        try {
            sessionDir.deleteRecursively()
        } catch (_: Exception) {}
    }
}
