package com.example.core.interfaces

import com.example.torrent.model.TorrentEngineStats
import com.example.torrent.model.TorrentRelease
import com.example.torrent.model.TorrentStreamSession
import kotlinx.coroutines.flow.StateFlow

interface TorrentEngineInterface {
    val stats: StateFlow<TorrentEngineStats>
    fun startSession(release: TorrentRelease, streamPort: Int = 8899): TorrentStreamSession
    fun stopSession(clearCache: Boolean = false)
    fun onPlaybackSeek(byteOffset: Long)
    fun readBytesForStream(offset: Long, length: Int, buffer: ByteArray, bufferOffset: Int = 0): Int

    // Stream range availability & status APIs
    fun awaitRangeAvailable(offset: Long, length: Int, timeoutMs: Long = 15000L): Boolean
    fun getActiveInfoHash(): String?
    fun getActiveFileLength(): Long
    fun getActiveFilePath(): String?
    fun isRangeAvailable(offset: Long, length: Int): Boolean
}
