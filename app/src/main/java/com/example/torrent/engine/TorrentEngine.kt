package com.example.torrent.engine

import android.content.Context
import com.example.torrent.core.LibtorrentEngine
import com.example.torrent.core.TorrentDownloadManager
import com.example.torrent.core.TorrentSessionManager
import com.example.torrent.model.*
import kotlinx.coroutines.flow.StateFlow

/**
 * Public facade for BitTorrent operations in Butterfly.
 * Keeps backward compatibility for existing UI, player, and viewmodels while delegating
 * all low-level swarm operations, streaming, DHT, and storage to libtorrent4j.
 */
class TorrentEngine(private val context: Context) {

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

    val stats: StateFlow<TorrentEngineStats> = sessionManager.stats

    fun startSession(release: TorrentRelease, streamPort: Int = 8899): TorrentStreamSession {
        return sessionManager.startSession(release, streamPort)
    }

    fun stopSession(clearCache: Boolean = false) {
        sessionManager.stopSession(clearCache)
    }

    fun onPlaybackSeek(byteOffset: Long) {
        sessionManager.onPlaybackSeek(byteOffset)
    }

    fun readBytesForStream(offset: Long, length: Int, buffer: ByteArray, bufferOffset: Int = 0): Int {
        return sessionManager.readBytesForStream(offset, length, buffer, bufferOffset)
    }

    fun getFileLength(): Long {
        return sessionManager.activeFileItem?.length
            ?: sessionManager.activeRelease?.sizeBytes
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
