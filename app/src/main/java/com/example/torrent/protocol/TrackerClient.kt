package com.example.torrent.protocol

import android.util.Log
import com.example.torrent.bencode.Bencode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import java.util.concurrent.TimeUnit

object TrackerClient {

    private const val TAG = "TrackerClient"
    private const val PROTOCOL_ID = 0x41727101980L // Magic constant for UDP tracker BEP 15

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun announce(
        infoHashBytes: ByteArray,
        peerIdBytes: ByteArray,
        trackers: List<String>,
        port: Int = 6881,
        bytesLeft: Long = 0L,
        bytesDownloaded: Long = 0L,
        bytesUploaded: Long = 0L,
        event: String = "started"
    ): List<InetSocketAddress> = withContext(Dispatchers.IO) {
        val uniqueTrackers = trackers.distinct().take(12)
        val allPeers = coroutineScope {
            uniqueTrackers.map { trackerUrl ->
                async {
                    try {
                        if (trackerUrl.startsWith("udp://", ignoreCase = true)) {
                            announceUdp(trackerUrl, infoHashBytes, peerIdBytes, port, bytesLeft, bytesDownloaded, bytesUploaded, event)
                        } else if (trackerUrl.startsWith("http://", ignoreCase = true) || trackerUrl.startsWith("https://", ignoreCase = true)) {
                            announceHttp(trackerUrl, infoHashBytes, peerIdBytes, port, bytesLeft, bytesDownloaded, bytesUploaded, event)
                        } else {
                            emptyList()
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Tracker announce failed for $trackerUrl: ${e.message}")
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
        }

        val distinctPeers = allPeers.distinctBy { "${it.address.hostAddress}:${it.port}" }
        Log.i(TAG, "Announced to ${uniqueTrackers.size} trackers, resolved ${distinctPeers.size} distinct live peers.")
        distinctPeers
    }

    private fun announceUdp(
        trackerUrl: String,
        infoHashBytes: ByteArray,
        peerIdBytes: ByteArray,
        port: Int,
        bytesLeft: Long,
        bytesDownloaded: Long,
        bytesUploaded: Long,
        event: String
    ): List<InetSocketAddress> {
        val uri = URI(trackerUrl)
        val host = uri.host ?: return emptyList()
        val trackerPort = if (uri.port > 0) uri.port else 80

        var socket: DatagramSocket? = null
        try {
            val address = InetAddress.getByName(host)
            socket = DatagramSocket()
            socket.soTimeout = 4000

            val random = Random()
            val transactionId = random.nextInt()

            // 1. Connect Request (BEP 15)
            val connectReq = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
            connectReq.putLong(PROTOCOL_ID)
            connectReq.putInt(0) // Action: 0 = connect
            connectReq.putInt(transactionId)

            val sendPacket = DatagramPacket(connectReq.array(), 16, address, trackerPort)
            socket.send(sendPacket)

            // Receive Connect Response
            val recvBuffer = ByteArray(2048)
            val recvPacket = DatagramPacket(recvBuffer, recvBuffer.size)
            socket.receive(recvPacket)

            val connectResp = ByteBuffer.wrap(recvBuffer, 0, recvPacket.length).order(ByteOrder.BIG_ENDIAN)
            val action = connectResp.getInt()
            val respTransId = connectResp.getInt()
            if (action != 0 || respTransId != transactionId) {
                return emptyList()
            }
            val connectionId = connectResp.getLong()

            // 2. Announce Request (BEP 15)
            val eventCode = when (event.lowercase()) {
                "completed" -> 1
                "started" -> 2
                "stopped" -> 3
                else -> 0 // none
            }

            val announceTransId = random.nextInt()
            val announceReq = ByteBuffer.allocate(98).order(ByteOrder.BIG_ENDIAN)
            announceReq.putLong(connectionId)
            announceReq.putInt(1) // Action: 1 = announce
            announceReq.putInt(announceTransId)
            announceReq.put(infoHashBytes) // 20 bytes
            announceReq.put(peerIdBytes)   // 20 bytes
            announceReq.putLong(bytesDownloaded)
            announceReq.putLong(bytesLeft)
            announceReq.putLong(bytesUploaded)
            announceReq.putInt(eventCode)
            announceReq.putInt(0)          // IP address (0 = default)
            announceReq.putInt(random.nextInt()) // key
            announceReq.putInt(100)        // num_want (-1 = default, 100 peers)
            announceReq.putShort(port.toShort()) // port

            val annSendPacket = DatagramPacket(announceReq.array(), 98, address, trackerPort)
            socket.send(annSendPacket)

            // Receive Announce Response
            val annRecvBuffer = ByteArray(4096)
            val annRecvPacket = DatagramPacket(annRecvBuffer, annRecvBuffer.size)
            socket.receive(annRecvPacket)

            val annResp = ByteBuffer.wrap(annRecvBuffer, 0, annRecvPacket.length).order(ByteOrder.BIG_ENDIAN)
            val annAction = annResp.getInt()
            val annRespTransId = annResp.getInt()
            if (annAction != 1 || annRespTransId != announceTransId) {
                return emptyList()
            }

            annResp.getInt() // interval
            annResp.getInt() // leechers
            annResp.getInt() // seeders

            val peers = mutableListOf<InetSocketAddress>()
            while (annResp.remaining() >= 6) {
                val ipBytes = ByteArray(4)
                annResp.get(ipBytes)
                val peerPort = annResp.getShort().toInt() and 0xFFFF
                val peerIp = InetAddress.getByAddress(ipBytes)
                if (peerPort > 0 && !peerIp.isAnyLocalAddress && !peerIp.isLoopbackAddress) {
                    peers.add(InetSocketAddress(peerIp, peerPort))
                }
            }
            return peers
        } catch (_: Exception) {
            return emptyList()
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun announceHttp(
        trackerUrl: String,
        infoHashBytes: ByteArray,
        peerIdBytes: ByteArray,
        port: Int,
        bytesLeft: Long,
        bytesDownloaded: Long,
        bytesUploaded: Long,
        event: String
    ): List<InetSocketAddress> {
        val encodedHash = urlEncodeBytes(infoHashBytes)
        val encodedPeerId = urlEncodeBytes(peerIdBytes)

        val delimiter = if (trackerUrl.contains("?")) "&" else "?"
        val eventParam = if (event.isNotBlank()) "&event=$event" else ""
        val url = "$trackerUrl${delimiter}info_hash=$encodedHash&peer_id=$encodedPeerId&port=$port&uploaded=$bytesUploaded&downloaded=$bytesDownloaded&left=$bytesLeft&compact=1$eventParam"

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Butterfly/1.0 (Android)")
            .build()

        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful) return emptyList()

        val bodyBytes = resp.body?.bytes() ?: return emptyList()
        val decoded = Bencode.decode(bodyBytes) as? Map<String, Any?> ?: return emptyList()

        val peers = mutableListOf<InetSocketAddress>()
        val peersVal = decoded["peers"]

        if (peersVal is ByteArray) {
            val bb = ByteBuffer.wrap(peersVal).order(ByteOrder.BIG_ENDIAN)
            while (bb.remaining() >= 6) {
                val ipBytes = ByteArray(4)
                bb.get(ipBytes)
                val peerPort = bb.getShort().toInt() and 0xFFFF
                val peerIp = InetAddress.getByAddress(ipBytes)
                if (peerPort > 0 && !peerIp.isAnyLocalAddress && !peerIp.isLoopbackAddress) {
                    peers.add(InetSocketAddress(peerIp, peerPort))
                }
            }
        } else if (peersVal is List<*>) {
            for (p in peersVal) {
                if (p is Map<*, *>) {
                    val ipStr = Bencode.getString(p as Map<String, Any?>, "ip")
                    val peerPort = Bencode.getLong(p, "port").toInt()
                    if (ipStr.isNotBlank() && peerPort > 0) {
                        try {
                            peers.add(InetSocketAddress(ipStr, peerPort))
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        return peers
    }

    private fun urlEncodeBytes(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            val unsigned = b.toInt() and 0xFF
            if ((unsigned in 'a'.code..'z'.code) || (unsigned in 'A'.code..'Z'.code) || (unsigned in '0'.code..'9'.code) || unsigned == '-'.code || unsigned == '_'.code || unsigned == '.'.code || unsigned == '~'.code) {
                sb.append(unsigned.toChar())
            } else {
                sb.append(String.format("%%%02X", unsigned))
            }
        }
        return sb.toString()
    }
}
