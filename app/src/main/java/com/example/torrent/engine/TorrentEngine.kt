package com.example.torrent.engine

import android.content.Context
import com.example.torrent.core.LibtorrentEngine
import com.example.torrent.core.TorrentDownloadManager
import com.example.torrent.core.TorrentSessionManager
import com.example.torrent.model.*
import kotlinx.coroutines.flow.StateFlow

import com.example.core.interfaces.TorrentEngineInterface

/**
 * Public facade for BitTorrent operations in Butterfly.
 * Keeps backward compatibility for existing UI, player, and viewmodels while delegating
 * all low-level swarm operations, streaming, DHT, and storage to libtorrent4j.
 */
class TorrentEngine(private val context: Context) : TorrentEngineInterface {

    companion object {
        private const val TAG = "TorrentEngine"

        @Volatile
        private var instance: TorrentEngine? = null

        fun getInstance(context: Context): TorrentEngine {
            return instance ?: synchronized(this) {
                instance ?: TorrentEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    val libtorrentEngine: LibtorrentEngine = LibtorrentEngine.getInstance(context)
    val sessionManager: TorrentSessionManager = TorrentSessionManager(context, libtorrentEngine)
    val downloadManager: TorrentDownloadManager = TorrentDownloadManager(context, libtorrentEngine)

    override val stats: StateFlow<TorrentEngineStats>
        get() = sessionManager.stats

    override fun startSession(release: TorrentRelease, streamPort: Int): TorrentStreamSession {
        return sessionManager.startSession(release, streamPort)
    }

    override fun stopSession(clearCache: Boolean) {
        sessionManager.stopSession(clearCache)
    }

    override fun onPlaybackSeek(byteOffset: Long) {
        sessionManager.onPlaybackSeek(byteOffset)
    }

    override fun readBytesForStream(offset: Long, length: Int, buffer: ByteArray, bufferOffset: Int): Int {
        return sessionManager.readBytesForStream(offset, length, buffer, bufferOffset)
    }

    override fun awaitRangeAvailable(offset: Long, length: Int, timeoutMs: Long): Boolean {
        return sessionManager.awaitRangeAvailable(offset, length, timeoutMs)
    }

    override fun getActiveInfoHash(): String? {
        return sessionManager.activeRelease?.infoHash?.lowercase()?.trim()
    }

    override fun getActiveFileLength(): Long {
        return sessionManager.activeFileItem?.length
            ?: sessionManager.activeRelease?.sizeBytes
            ?: 0L
    }

    override fun getActiveFilePath(): String? {
        return sessionManager.activeFileOnDisk?.absolutePath
    }

    override fun isRangeAvailable(offset: Long, length: Int): Boolean {
        return sessionManager.isRangeDownloaded(offset, length)
    }

    fun getFileLength(): Long {
        return sessionManager.activeFileItem?.length
            ?: 0L
    }

    fun getFileName(): String {
        return sessionManager.activeFileItem?.name
            ?: sessionManager.activeRelease?.fileName
            ?: sessionManager.activeRelease?.title
            ?: "video.mkv"
    }

    // --- Flud / Advanced Torrent Controls ---

    fun updateSettings(settings: TorrentSettings) {
        downloadManager.updateSettings(settings)
    }

    fun getSettings(): TorrentSettings {
        return downloadManager.settings.value
    }

    fun getActivePeers(): List<TorrentPeerInfo> {
        val infoHash = sessionManager.activeRelease?.infoHash ?: return emptyList()
        return libtorrentEngine.getPeers(infoHash)
    }

    fun getActiveTrackers(): List<TorrentTrackerInfo> {
        val infoHash = sessionManager.activeRelease?.infoHash ?: return emptyList()
        return libtorrentEngine.getTrackers(infoHash)
    }

    fun getPieceMap(): PieceMapInfo {
        val infoHash = sessionManager.activeRelease?.infoHash ?: return PieceMapInfo()
        return libtorrentEngine.getPieceMap(infoHash)
    }

    fun getDhtNodes(): Long {
        return libtorrentEngine.getDhtNodes()
    }
}
