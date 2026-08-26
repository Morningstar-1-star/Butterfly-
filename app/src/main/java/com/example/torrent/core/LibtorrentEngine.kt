package com.example.torrent.core

import android.content.Context
import android.util.Log
import com.example.torrent.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.libtorrent4j.*
import org.libtorrent4j.alerts.*
import org.libtorrent4j.swig.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Low-level engine bridging libtorrent4j SessionManager and native C++ BitTorrent subsystem.
 * Handles DHT, PEX, LSD, trackers, peer management, alerts, rate limits, and multi-file storage.
 */
class LibtorrentEngine(private val context: Context) {

    companion object {
        private const val TAG = "LibtorrentEngine"

        @Volatile
        private var instance: LibtorrentEngine? = null

        fun getInstance(context: Context): LibtorrentEngine {
            return instance ?: synchronized(this) {
                instance ?: LibtorrentEngine(context.applicationContext).also { instance = it }
            }
        }

        fun hexToSha1Hash(hex: String): Sha1Hash {
            val clean = hex.trim().lowercase()
            return Sha1Hash(sha1_hash.from_hex(clean))
        }
    }

    private val sessionManager = SessionManager()
    private val isRunning = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val cacheDir: File = File(context.cacheDir, "libtorrent_cache").apply { mkdirs() }
    val downloadDir: File = File(context.getExternalFilesDir(null) ?: context.filesDir, "Downloads").apply { mkdirs() }

    private val _alerts = MutableSharedFlow<Alert<*>>(extraBufferCapacity = 64)
    val alerts: SharedFlow<Alert<*>> = _alerts.asSharedFlow()

    private val alertListener = object : AlertListener {
        override fun types(): IntArray? = null // Listen to all alerts

        override fun alert(alert: Alert<*>) {
            _alerts.tryEmit(alert)
            when (alert.type()) {
                AlertType.TORRENT_ERROR -> Log.w(TAG, "Torrent error alert: ${alert.message()}")
                AlertType.METADATA_RECEIVED -> Log.i(TAG, "Metadata received alert: ${alert.message()}")
                AlertType.PORTMAP_ERROR -> Log.d(TAG, "Portmap: ${alert.message()}")
                else -> {}
            }
        }
    }

    fun start(settings: TorrentSettings = TorrentSettings()) {
        if (isRunning.getAndSet(true)) return
        Log.i(TAG, "Starting libtorrent4j SessionManager...")

        try {
            sessionManager.addListener(alertListener)

            val sp = SettingsPack()
            sp.setBoolean(settings_pack.bool_types.enable_dht.swigValue(), settings.dhtEnabled)
            sp.setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), settings.lsdEnabled)
            sp.setInteger(settings_pack.int_types.connections_limit.swigValue(), settings.maxConnections)
            sp.setString(settings_pack.string_types.user_agent.swigValue(), "Butterfly/1.0 libtorrent/2.1.0")

            if (settings.maxDownloadSpeedBps > 0) {
                sp.setInteger(settings_pack.int_types.download_rate_limit.swigValue(), settings.maxDownloadSpeedBps.toInt())
            }
            if (settings.maxUploadSpeedBps > 0) {
                sp.setInteger(settings_pack.int_types.upload_rate_limit.swigValue(), settings.maxUploadSpeedBps.toInt())
            }

            val sessionParams = SessionParams(sp)
            sessionManager.start(sessionParams)
            Log.i(TAG, "libtorrent4j session successfully started. DHT running: ${sessionManager.isDhtRunning}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start libtorrent session: ${e.message}", e)
            isRunning.set(false)
        }
    }

    fun isRunning(): Boolean = isRunning.get() && sessionManager.isRunning

    fun getDhtNodes(): Long {
        return if (isRunning()) sessionManager.dhtNodes() else 0L
    }

    fun fetchMagnetMetadata(magnetUri: String, timeoutSec: Int = 45): TorrentInfo? {
        if (!isRunning()) start()
        return try {
            val tempDir = File(cacheDir, "temp_magnet").apply { mkdirs() }
            val bytes = sessionManager.fetchMagnet(magnetUri, timeoutSec, tempDir)
            if (bytes != null && bytes.isNotEmpty()) {
                TorrentInfo.bdecode(bytes)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchMagnetMetadata failed for $magnetUri: ${e.message}")
            null
        }
    }

    fun download(
        torrentInfo: TorrentInfo,
        saveDir: File = cacheDir,
        filePriorities: Array<Priority>? = null,
        sequential: Boolean = false
    ): TorrentHandle? {
        if (!isRunning()) start()
        return try {
            if (filePriorities != null) {
                sessionManager.download(torrentInfo, saveDir, null, filePriorities, null, null)
            } else {
                sessionManager.download(torrentInfo, saveDir)
            }

            val th = findHandle(torrentInfo.infoHash().toHex())
            th?.let {
                if (sequential) {
                    it.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
                }
            }
            th
        } catch (e: Exception) {
            Log.e(TAG, "download failed: ${e.message}", e)
            null
        }
    }

    fun findHandle(infoHashHex: String): TorrentHandle? {
        return try {
            val sha1 = hexToSha1Hash(infoHashHex)
            sessionManager.find(sha1)
        } catch (e: Exception) {
            null
        }
    }

    fun setSequential(infoHashHex: String, sequential: Boolean) {
        val th = findHandle(infoHashHex) ?: return
        if (sequential) {
            th.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
        } else {
            th.unsetFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
        }
    }

    fun setFilePriorities(infoHashHex: String, priorities: Array<Priority>) {
        findHandle(infoHashHex)?.prioritizeFiles(priorities)
    }

    fun setPiecePriority(infoHashHex: String, pieceIndex: Int, priority: Priority) {
        findHandle(infoHashHex)?.piecePriority(pieceIndex, priority)
    }

    fun setPieceDeadline(infoHashHex: String, pieceIndex: Int, deadlineMs: Int) {
        findHandle(infoHashHex)?.setPieceDeadline(pieceIndex, deadlineMs)
    }

    fun resetPieceDeadline(infoHashHex: String, pieceIndex: Int) {
        findHandle(infoHashHex)?.resetPieceDeadline(pieceIndex)
    }

    fun clearPieceDeadlines(infoHashHex: String) {
        findHandle(infoHashHex)?.clearPieceDeadlines()
    }

    fun pause(infoHashHex: String) {
        findHandle(infoHashHex)?.pause()
    }

    fun resume(infoHashHex: String) {
        findHandle(infoHashHex)?.resume()
    }

    fun remove(infoHashHex: String, deleteFiles: Boolean = false) {
        val th = findHandle(infoHashHex) ?: return
        try {
            if (deleteFiles) {
                sessionManager.remove(th, SessionHandle.DELETE_FILES)
            } else {
                sessionManager.remove(th)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error removing torrent $infoHashHex: ${e.message}")
        }
    }

    fun updateSettings(settings: TorrentSettings) {
        if (!isRunning()) return
        try {
            val sp = SettingsPack()
            sp.setBoolean(settings_pack.bool_types.enable_dht.swigValue(), settings.dhtEnabled)
            sp.setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), settings.lsdEnabled)
            sp.setInteger(settings_pack.int_types.connections_limit.swigValue(), settings.maxConnections)
            if (settings.maxDownloadSpeedBps > 0) {
                sp.setInteger(settings_pack.int_types.download_rate_limit.swigValue(), settings.maxDownloadSpeedBps.toInt())
            } else {
                sp.setInteger(settings_pack.int_types.download_rate_limit.swigValue(), 0)
            }
            if (settings.maxUploadSpeedBps > 0) {
                sp.setInteger(settings_pack.int_types.upload_rate_limit.swigValue(), settings.maxUploadSpeedBps.toInt())
            } else {
                sp.setInteger(settings_pack.int_types.upload_rate_limit.swigValue(), 0)
            }
            sessionManager.applySettings(sp)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply settings: ${e.message}")
        }
    }

    fun getPeers(infoHashHex: String): List<TorrentPeerInfo> {
        val th = findHandle(infoHashHex) ?: return emptyList()
        return try {
            val peers = th.peerInfo() ?: return emptyList()
            peers.map { p ->
                TorrentPeerInfo(
                    ip = p.ip(),
                    port = 0,
                    clientName = p.client(),
                    downloadRateBps = p.downSpeed().toLong(),
                    uploadRateBps = p.upSpeed().toLong(),
                    isChoked = false,
                    isInterested = false,
                    isSeed = false,
                    progress = p.progress(),
                    flags = p.flags().toString()
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getTrackers(infoHashHex: String): List<TorrentTrackerInfo> {
        val th = findHandle(infoHashHex) ?: return emptyList()
        return try {
            val trackers = th.trackers() ?: return emptyList()
            trackers.map { t ->
                TorrentTrackerInfo(
                    url = t.url(),
                    status = "Active",
                    peersCount = 0,
                    seedsCount = 0,
                    message = ""
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getPieceMap(infoHashHex: String): PieceMapInfo {
        val th = findHandle(infoHashHex) ?: return PieceMapInfo()
        return try {
            val st = th.status()
            val totalPieces = th.torrentFile()?.numPieces() ?: 0
            val bitfield = st.pieces()
            val pieces = BooleanArray(totalPieces) { i ->
                if (bitfield != null && i < bitfield.size()) bitfield.getBit(i) else false
            }
            var downloaded = 0
            for (b in pieces) {
                if (b) downloaded++
            }
            PieceMapInfo(
                totalPieces = totalPieces,
                downloadedPiecesCount = downloaded,
                piecesBitfield = pieces
            )
        } catch (_: Exception) {
            PieceMapInfo()
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        Log.i(TAG, "Stopping libtorrent session...")
        try {
            sessionManager.removeListener(alertListener)
            sessionManager.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping session: ${e.message}")
        }
    }
}
