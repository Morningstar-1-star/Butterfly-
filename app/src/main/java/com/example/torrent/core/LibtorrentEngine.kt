package com.example.torrent.core

import android.content.Context
import android.util.Log
import com.example.torrent.model.*
import com.example.torrent.protocol.MagnetParser
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
            sp.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), true)
            sp.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), true)
            sp.setBoolean(settings_pack.bool_types.enable_incoming_tcp.swigValue(), true)
            sp.setBoolean(settings_pack.bool_types.enable_incoming_utp.swigValue(), true)
            sp.setBoolean(settings_pack.bool_types.enable_outgoing_tcp.swigValue(), true)
            sp.setBoolean(settings_pack.bool_types.enable_outgoing_utp.swigValue(), true)
            sp.setBoolean(settings_pack.bool_types.announce_to_all_trackers.swigValue(), true)
            sp.setBoolean(settings_pack.bool_types.announce_to_all_tiers.swigValue(), true)
            sp.setString(settings_pack.string_types.listen_interfaces.swigValue(), "0.0.0.0:6881,0.0.0.0:0,[::]:6881,[::]:0")
            sp.setString(
                settings_pack.string_types.dht_bootstrap_nodes.swigValue(),
                "router.bittorrent.com:6881,dht.transmissionbt.com:6881,router.utorrent.com:6881,dht.aelitis.com:6881,dht.libtorrent.org:25401,node.bittorrent.com:6881,node.transmissionbt.com:6881"
            )
            val batterySaver = try { com.example.util.BatterySaverManager.getInstance(context) } catch (_: Exception) { null }
            val isSaverActive = batterySaver?.isPowerSaveActive?.value == true && batterySaver.lowPowerTorrent.value
            val connLimit = if (isSaverActive) 30 else settings.maxConnections.coerceAtLeast(120)
            sp.setInteger(settings_pack.int_types.connections_limit.swigValue(), connLimit)
            sp.setString(settings_pack.string_types.user_agent.swigValue(), "Butterfly/1.0 libtorrent/2.1.0")

            if (settings.maxDownloadSpeedBps > 0) {
                sp.setInteger(settings_pack.int_types.download_rate_limit.swigValue(), settings.maxDownloadSpeedBps.toInt())
            }
            if (settings.maxUploadSpeedBps > 0) {
                sp.setInteger(settings_pack.int_types.upload_rate_limit.swigValue(), settings.maxUploadSpeedBps.toInt())
            } else if (isSaverActive) {
                // Throttle background upload traffic on battery
                sp.setInteger(settings_pack.int_types.upload_rate_limit.swigValue(), 10 * 1024)
            }

            // SOCKS5 Proxy Configuration
            if (com.example.util.AppConfig.isTorrentProxyEnabled()) {
                val pHost = com.example.util.AppConfig.getTorrentProxyHost()
                val pPort = com.example.util.AppConfig.getTorrentProxyPort()
                val pUser = com.example.util.AppConfig.getTorrentProxyUser()
                val pPass = com.example.util.AppConfig.getTorrentProxyPass()

                if (pHost.isNotBlank() && pPort > 0) {
                    sp.setInteger(settings_pack.int_types.proxy_type.swigValue(), settings_pack.proxy_type_t.socks5.swigValue())
                    sp.setString(settings_pack.string_types.proxy_hostname.swigValue(), pHost)
                    sp.setInteger(settings_pack.int_types.proxy_port.swigValue(), pPort)
                    if (pUser.isNotBlank()) {
                        sp.setString(settings_pack.string_types.proxy_username.swigValue(), pUser)
                    }
                    if (pPass.isNotBlank()) {
                        sp.setString(settings_pack.string_types.proxy_password.swigValue(), pPass)
                    }
                    sp.setBoolean(settings_pack.bool_types.proxy_peer_connections.swigValue(), true)
                    sp.setBoolean(settings_pack.bool_types.proxy_tracker_connections.swigValue(), true)
                    sp.setBoolean(settings_pack.bool_types.proxy_hostnames.swigValue(), true)
                    Log.i(TAG, "Configured SOCKS5 proxy on startup: $pHost:$pPort")
                }
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

    fun setProxy(host: String, port: Int, username: String? = null, password: String? = null) {
        try {
            val sp = SettingsPack()
            if (host.isNotBlank() && port in 1..65535) {
                sp.setInteger(settings_pack.int_types.proxy_type.swigValue(), settings_pack.proxy_type_t.socks5.swigValue())
                sp.setString(settings_pack.string_types.proxy_hostname.swigValue(), host)
                sp.setInteger(settings_pack.int_types.proxy_port.swigValue(), port)
                if (!username.isNullOrBlank()) {
                    sp.setString(settings_pack.string_types.proxy_username.swigValue(), username)
                }
                if (!password.isNullOrBlank()) {
                    sp.setString(settings_pack.string_types.proxy_password.swigValue(), password)
                }
                sp.setBoolean(settings_pack.bool_types.proxy_peer_connections.swigValue(), true)
                sp.setBoolean(settings_pack.bool_types.proxy_tracker_connections.swigValue(), true)
                sp.setBoolean(settings_pack.bool_types.proxy_hostnames.swigValue(), true)
                Log.i(TAG, "Dynamic SOCKS5 proxy applied: $host:$port")
            } else {
                sp.setInteger(settings_pack.int_types.proxy_type.swigValue(), settings_pack.proxy_type_t.none.swigValue())
                Log.i(TAG, "Disabled BitTorrent proxy (direct connection mode)")
            }
            if (isRunning()) {
                sessionManager.swig().apply_settings(sp.swig())
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to apply dynamic proxy settings: ${e.message}", e)
        }
    }

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
        val hex = torrentInfo.infoHash().toHex()
        val existing = findHandle(hex)
        if (existing != null && existing.isValid) {
            try {
                if (filePriorities != null) {
                    existing.prioritizeFiles(filePriorities)
                }
                if (sequential) {
                    existing.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
                }
                existing.resume()
                return existing
            } catch (e: Exception) {
                Log.d(TAG, "Reusing existing handle: ${e.message}")
            }
        }

        return try {
            if (filePriorities != null) {
                sessionManager.download(torrentInfo, saveDir, null, filePriorities, null, null)
            } else {
                sessionManager.download(torrentInfo, saveDir)
            }

            val th = findHandle(hex)
            th?.let {
                if (sequential) {
                    it.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
                }
                it.resume()
            }
            th
        } catch (e: Exception) {
            Log.e(TAG, "download failed: ${e.message}", e)
            findHandle(hex)
        }
    }

    fun downloadMagnet(
        magnetUri: String,
        saveDir: File = cacheDir,
        sequential: Boolean = true
    ): TorrentHandle? {
        if (!isRunning()) start()
        return try {
            val flags = if (sequential) TorrentFlags.SEQUENTIAL_DOWNLOAD else org.libtorrent4j.swig.torrent_flags_t()
            sessionManager.download(magnetUri, saveDir, flags)
            val parsed = MagnetParser.parse(magnetUri)
            val infoHash = parsed?.infoHashHex ?: ""
            if (infoHash.isNotBlank()) {
                var th: TorrentHandle? = null
                for (i in 0..15) {
                    th = findHandle(infoHash)
                    if (th != null) break
                    Thread.sleep(100)
                }
                if (th != null) {
                    if (sequential) {
                        th.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
                    }
                    parsed?.trackers?.forEach { tr ->
                        try {
                            th.addTracker(org.libtorrent4j.AnnounceEntry(tr))
                        } catch (_: Exception) {}
                    }
                    MagnetParser.DEFAULT_TRACKERS.forEach { tr ->
                        try {
                            th.addTracker(org.libtorrent4j.AnnounceEntry(tr))
                        } catch (_: Exception) {}
                    }
                    th.resume()
                }
                th
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "downloadMagnet failed: ${e.message}", e)
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
                val ipStr = p.ip() ?: ""
                val ipPortSplit = ipStr.split(":")
                val ipClean = ipPortSplit.firstOrNull() ?: ipStr
                val parsedPort = ipPortSplit.getOrNull(1)?.toIntOrNull() ?: 0
                val flagsStr = p.flags().toString()
                val isSeed = p.progress() >= 0.999f || flagsStr.contains("seed", ignoreCase = true)
                val isChoked = flagsStr.contains("choked", ignoreCase = true)
                val isInterested = flagsStr.contains("interested", ignoreCase = true)

                TorrentPeerInfo(
                    ip = ipClean,
                    port = parsedPort,
                    clientName = p.client().ifBlank { "Peer ($ipClean)" },
                    downloadRateBps = p.downSpeed().toLong(),
                    uploadRateBps = p.upSpeed().toLong(),
                    isChoked = isChoked,
                    isInterested = isInterested,
                    isSeed = isSeed,
                    progress = p.progress(),
                    flags = flagsStr
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
                val url = t.url() ?: ""
                val tier = t.tier()
                TorrentTrackerInfo(
                    url = url,
                    status = "Tier $tier Active",
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
