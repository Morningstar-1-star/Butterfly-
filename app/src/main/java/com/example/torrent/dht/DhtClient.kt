package com.example.torrent.dht

import android.util.Log
import com.example.torrent.bencode.Bencode
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Production-ready BitTorrent DHT (BEP 5) Client.
 * Implements Kademlia-based Distributed Hash Table protocol over UDP KRPC.
 */
object DhtClient {
    private const val TAG = "DhtClient"
    private const val K_BUCKET_SIZE = 8
    private const val UDP_BUFFER_SIZE = 4096

    // Bootstrap DHT Nodes
    val BOOTSTRAP_NODES = listOf(
        "router.bittorrent.com" to 6881,
        "dht.transmissionbt.com" to 6881,
        "router.utorrent.com" to 6881,
        "dht.aelitis.com" to 6881
    )

    // Local 160-bit node ID (20 bytes)
    val localNodeId: ByteArray = ByteArray(20).apply {
        SecureRandom().nextBytes(this)
    }

    private var socket: DatagramSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Pending transactions: transactionId (2-byte string) -> CompletableDeferred<Map<String, Any?>>
    private val pendingTransactions = ConcurrentHashMap<String, CompletableDeferred<Map<String, Any?>>>()
    
    // Discovered nodes: nodeId (hex) -> DhtNode
    private val routingTable = ConcurrentHashMap<String, DhtNode>()

    // Token storage for announces: infoHashHex -> token byte array
    private val peerTokens = ConcurrentHashMap<String, ByteArray>()

    data class DhtNode(
        val id: ByteArray,
        val address: InetSocketAddress,
        var lastSeen: Long = System.currentTimeMillis()
    ) {
        val idHex: String get() = id.joinToString("") { "%02x".format(it) }
    }

    fun start(port: Int = 6882) {
        if (isRunning.getAndSet(true)) return
        try {
            socket = DatagramSocket(port)
            socket?.soTimeout = 3000
            Log.i(TAG, "DHT Client started on port $port with Node ID: ${localNodeId.joinToString("") { "%02x".format(it) }}")

            scope.launch {
                listenLoop()
            }

            scope.launch {
                bootstrap()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to bind DHT socket on port $port: ${e.message}. Retrying with ephemeral port.")
            try {
                socket = DatagramSocket(0)
                socket?.soTimeout = 3000
                scope.launch { listenLoop() }
                scope.launch { bootstrap() }
            } catch (ex: Exception) {
                Log.e(TAG, "Fatal DHT startup failure: ${ex.message}")
                isRunning.set(false)
            }
        }
    }

    fun stop() {
        isRunning.set(false)
        try {
            socket?.close()
        } catch (e: Exception) {
            // Ignored
        }
        socket = null
        pendingTransactions.clear()
        Log.i(TAG, "DHT Client stopped.")
    }

    private suspend fun bootstrap() = withContext(Dispatchers.IO) {
        for ((host, port) in BOOTSTRAP_NODES) {
            try {
                val ips = InetAddress.getAllByName(host)
                for (ip in ips.take(2)) {
                    val addr = InetSocketAddress(ip, port)
                    findNode(localNodeId, addr)
                }
            } catch (e: Exception) {
                Log.d(TAG, "DHT bootstrap resolve failed for $host: ${e.message}")
            }
        }
    }

    /**
     * BEP 5 get_peers query: Search DHT network for peers distributing the specified infoHash.
     */
    suspend fun getPeers(infoHashBytes: ByteArray, timeoutMs: Long = 10000): List<InetSocketAddress> = withContext(Dispatchers.IO) {
        if (!isRunning.get()) start()

        val discoveredPeers = ConcurrentHashMap.newKeySet<InetSocketAddress>()
        val infoHashHex = infoHashBytes.joinToString("") { "%02x".format(it) }
        val targetNodes = routingTable.values.toList().take(20)

        val queryJobs = targetNodes.map { node ->
            async {
                try {
                    val response = sendGetPeersQuery(node.address, infoHashBytes)
                    if (response != null) {
                        val r = response["r"] as? Map<*, *> ?: return@async

                        // Check token
                        val token = (r["token"] as? String)?.toByteArray(Charsets.ISO_8859_1)
                        if (token != null) {
                            peerTokens[infoHashHex] = token
                        }

                        // Parse 'values' (peers)
                        val values = r["values"] as? List<*>
                        if (values != null) {
                            for (v in values) {
                                val peerBytes = when (v) {
                                    is String -> v.toByteArray(Charsets.ISO_8859_1)
                                    is ByteArray -> v
                                    else -> null
                                }
                                if (peerBytes != null && peerBytes.size == 6) {
                                    val ip = InetAddress.getByAddress(peerBytes.copyOfRange(0, 4))
                                    val port = ByteBuffer.wrap(peerBytes, 4, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
                                    if (port > 0) {
                                        discoveredPeers.add(InetSocketAddress(ip, port))
                                    }
                                }
                            }
                        }

                        // Parse 'nodes' (closer DHT nodes)
                        val compactNodes = when (val n = r["nodes"]) {
                            is String -> n.toByteArray(Charsets.ISO_8859_1)
                            is ByteArray -> n
                            else -> null
                        }
                        if (compactNodes != null && compactNodes.size >= 26) {
                            parseCompactNodes(compactNodes)
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "get_peers error from ${node.address}: ${e.message}")
                }
            }
        }

        withTimeoutOrNull(timeoutMs) {
            queryJobs.awaitAll()
        }

        val result = discoveredPeers.toList()
        Log.i(TAG, "DHT getPeers for $infoHashHex returned ${result.size} live peers.")
        result
    }

    /**
     * BEP 5 find_node query: Find closest nodes to target ID.
     */
    suspend fun findNode(targetNodeId: ByteArray, address: InetSocketAddress): Boolean = withContext(Dispatchers.IO) {
        val t = generateTransactionId()
        val query = mapOf(
            "t" to t,
            "y" to "q",
            "q" to "find_node",
            "a" to mapOf(
                "id" to String(localNodeId, Charsets.ISO_8859_1),
                "target" to String(targetNodeId, Charsets.ISO_8859_1)
            )
        )

        val deferred = CompletableDeferred<Map<String, Any?>>()
        pendingTransactions[t] = deferred

        try {
            sendBencoded(query, address)
            val response = withTimeoutOrNull(3000) { deferred.await() }
            if (response != null) {
                val r = response["r"] as? Map<*, *>
                val nodesBytes = when (val n = r?.get("nodes")) {
                    is String -> n.toByteArray(Charsets.ISO_8859_1)
                    is ByteArray -> n
                    else -> null
                }
                if (nodesBytes != null) {
                    parseCompactNodes(nodesBytes)
                }
                return@withContext true
            }
        } catch (e: Exception) {
            Log.d(TAG, "find_node failed for $address: ${e.message}")
        } finally {
            pendingTransactions.remove(t)
        }
        false
    }

    /**
     * BEP 5 ping query: Check if a DHT node is alive.
     */
    suspend fun ping(address: InetSocketAddress): Boolean = withContext(Dispatchers.IO) {
        val t = generateTransactionId()
        val query = mapOf(
            "t" to t,
            "y" to "q",
            "q" to "ping",
            "a" to mapOf(
                "id" to String(localNodeId, Charsets.ISO_8859_1)
            )
        )

        val deferred = CompletableDeferred<Map<String, Any?>>()
        pendingTransactions[t] = deferred

        try {
            sendBencoded(query, address)
            val response = withTimeoutOrNull(2500) { deferred.await() }
            return@withContext response != null
        } catch (e: Exception) {
            return@withContext false
        } finally {
            pendingTransactions.remove(t)
        }
    }

    private suspend fun sendGetPeersQuery(address: InetSocketAddress, infoHashBytes: ByteArray): Map<String, Any?>? = withContext(Dispatchers.IO) {
        val t = generateTransactionId()
        val query = mapOf(
            "t" to t,
            "y" to "q",
            "q" to "get_peers",
            "a" to mapOf(
                "id" to String(localNodeId, Charsets.ISO_8859_1),
                "info_hash" to String(infoHashBytes, Charsets.ISO_8859_1)
            )
        )

        val deferred = CompletableDeferred<Map<String, Any?>>()
        pendingTransactions[t] = deferred

        try {
            sendBencoded(query, address)
            withTimeoutOrNull(4000) { deferred.await() }
        } catch (e: Exception) {
            null
        } finally {
            pendingTransactions.remove(t)
        }
    }

    private fun sendBencoded(data: Map<String, Any?>, address: InetSocketAddress) {
        try {
            val inetAddr = address.address ?: return
            val encodedBytes = Bencode.encode(data)
            val sock = socket ?: return
            if (sock.isClosed) return
            val packet = DatagramPacket(encodedBytes, encodedBytes.size, inetAddr, address.port)
            sock.send(packet)
        } catch (_: Exception) {
            // Guard against rate limit / audit socket exceptions
        }
    }

    private fun listenLoop() {
        val buffer = ByteArray(UDP_BUFFER_SIZE)
        while (isRunning.get()) {
            try {
                val sock = socket
                if (sock == null || sock.isClosed) break
                val packet = DatagramPacket(buffer, buffer.size)
                sock.receive(packet)

                val length = packet.length
                if (length <= 0) continue
                val rawData = buffer.copyOf(length)
                val senderAddr = InetSocketAddress(packet.address, packet.port)

                scope.launch {
                    handleIncomingPacket(rawData, senderAddr)
                }
            } catch (e: java.net.SocketTimeoutException) {
                // Normal timeout on receive, continue listening
                continue
            } catch (e: Exception) {
                if (!isRunning.get()) break
                try {
                    Thread.sleep(100)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleIncomingPacket(data: ByteArray, sender: InetSocketAddress) {
        try {
            val decoded = Bencode.decode(data) as? Map<String, Any?> ?: return
            val y = decoded["y"] as? String ?: return
            val t = decoded["t"] as? String ?: ""

            when (y) {
                "r" -> {
                    // Response to our query
                    val deferred = pendingTransactions[t]
                    deferred?.complete(decoded)

                    val r = decoded["r"] as? Map<String, Any?>
                    val remoteIdStr = r?.get("id") as? String
                    if (remoteIdStr != null) {
                        val remoteId = remoteIdStr.toByteArray(Charsets.ISO_8859_1)
                        if (remoteId.size == 20) {
                            val node = DhtNode(remoteId, sender)
                            routingTable[node.idHex] = node
                        }
                    }
                }
                "q" -> {
                    // Incoming query from remote node
                    handleIncomingQuery(decoded, t, sender)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error decoding DHT packet from $sender: ${e.message}")
        }
    }

    private fun handleIncomingQuery(query: Map<String, Any?>, t: String, sender: InetSocketAddress) {
        val q = query["q"] as? String ?: return
        val a = query["a"] as? Map<*, *> ?: return

        when (q) {
            "ping" -> {
                val response = mapOf(
                    "t" to t,
                    "y" to "r",
                    "r" to mapOf(
                        "id" to String(localNodeId, Charsets.ISO_8859_1)
                    )
                )
                sendBencoded(response, sender)
            }
            "find_node" -> {
                val target = (a["target"] as? String)?.toByteArray(Charsets.ISO_8859_1) ?: localNodeId
                val closest = getClosestNodes(target, K_BUCKET_SIZE)
                val response = mapOf(
                    "t" to t,
                    "y" to "r",
                    "r" to mapOf(
                        "id" to String(localNodeId, Charsets.ISO_8859_1),
                        "nodes" to String(compactNodes(closest), Charsets.ISO_8859_1)
                    )
                )
                sendBencoded(response, sender)
            }
            "get_peers" -> {
                val infoHash = (a["info_hash"] as? String)?.toByteArray(Charsets.ISO_8859_1) ?: localNodeId
                val closest = getClosestNodes(infoHash, K_BUCKET_SIZE)
                val token = generateToken(sender.address)
                val response = mapOf(
                    "t" to t,
                    "y" to "r",
                    "r" to mapOf(
                        "id" to String(localNodeId, Charsets.ISO_8859_1),
                        "token" to String(token, Charsets.ISO_8859_1),
                        "nodes" to String(compactNodes(closest), Charsets.ISO_8859_1)
                    )
                )
                sendBencoded(response, sender)
            }
            "announce_peer" -> {
                val response = mapOf(
                    "t" to t,
                    "y" to "r",
                    "r" to mapOf(
                        "id" to String(localNodeId, Charsets.ISO_8859_1)
                    )
                )
                sendBencoded(response, sender)
            }
        }
    }

    /**
     * BEP 5 announce_peer query: Announce that the local peer is downloading or seeding the torrent.
     */
    suspend fun announcePeer(
        infoHashBytes: ByteArray,
        port: Int,
        impliedPort: Boolean = false
    ): Int = withContext(Dispatchers.IO) {
        val infoHashHex = infoHashBytes.joinToString("") { "%02x".format(it) }
        val token = peerTokens[infoHashHex] ?: return@withContext 0
        val targetNodes = getClosestNodes(infoHashBytes, 8)
        var successfulAnnounces = 0

        for (node in targetNodes) {
            val t = generateTransactionId()
            val query = mapOf(
                "t" to t,
                "y" to "q",
                "q" to "announce_peer",
                "a" to mapOf(
                    "id" to String(localNodeId, Charsets.ISO_8859_1),
                    "info_hash" to String(infoHashBytes, Charsets.ISO_8859_1),
                    "port" to port,
                    "token" to String(token, Charsets.ISO_8859_1),
                    "implied_port" to if (impliedPort) 1 else 0
                )
            )
            val deferred = CompletableDeferred<Map<String, Any?>>()
            pendingTransactions[t] = deferred
            try {
                sendBencoded(query, node.address)
                val resp = withTimeoutOrNull(2500) { deferred.await() }
                if (resp != null && resp["y"] == "r") {
                    successfulAnnounces++
                }
            } catch (_: Exception) {
            } finally {
                pendingTransactions.remove(t)
            }
        }
        Log.i(TAG, "Announced $infoHashHex to $successfulAnnounces nodes.")
        successfulAnnounces
    }

    private fun getClosestNodes(targetId: ByteArray, limit: Int): List<DhtNode> {
        return routingTable.values
            .sortedBy { xorDistance(it.id, targetId) }
            .take(limit)
    }

    private fun xorDistance(a: ByteArray, b: ByteArray): java.math.BigInteger {
        val xor = ByteArray(20)
        for (i in 0 until minOf(20, a.size, b.size)) {
            xor[i] = (a[i].toInt() xor b[i].toInt()).toByte()
        }
        return java.math.BigInteger(1, xor)
    }

    private fun generateToken(addr: InetAddress): ByteArray {
        val buffer = ByteBuffer.allocate(8)
        buffer.put(addr.address)
        buffer.putInt((System.currentTimeMillis() / (1000 * 60 * 10)).toInt()) // 10 min window
        return buffer.array()
    }

    private fun compactNodes(nodes: List<DhtNode>): ByteArray {
        val buffer = ByteBuffer.allocate(nodes.size * 26).order(ByteOrder.BIG_ENDIAN)
        for (n in nodes) {
            buffer.put(n.id)
            buffer.put(n.address.address.address)
            buffer.putShort(n.address.port.toShort())
        }
        return buffer.array()
    }

    private fun parseCompactNodes(nodesBytes: ByteArray) {
        var offset = 0
        while (offset + 26 <= nodesBytes.size) {
            val nodeId = nodesBytes.copyOfRange(offset, offset + 20)
            val ipBytes = nodesBytes.copyOfRange(offset + 20, offset + 24)
            val port = ByteBuffer.wrap(nodesBytes, offset + 24, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF

            try {
                val ip = InetAddress.getByAddress(ipBytes)
                if (port > 0 && !ip.isAnyLocalAddress && !ip.isLoopbackAddress) {
                    val node = DhtNode(nodeId, InetSocketAddress(ip, port))
                    if (routingTable.size < 300) {
                        routingTable[node.idHex] = node
                    }
                }
            } catch (e: Exception) {
                // Ignore invalid IP
            }
            offset += 26
        }
    }

    private fun getCompactRoutingTableNodes(limit: Int): ByteArray {
        val nodes = routingTable.values.take(limit)
        val buffer = ByteBuffer.allocate(nodes.size * 26).order(ByteOrder.BIG_ENDIAN)
        for (n in nodes) {
            buffer.put(n.id)
            buffer.put(n.address.address.address)
            buffer.putShort(n.address.port.toShort())
        }
        return buffer.array()
    }

    private fun generateTransactionId(): String {
        val r = SecureRandom().nextInt(65535)
        val b = ByteArray(2)
        b[0] = (r shr 8).toByte()
        b[1] = r.toByte()
        return String(b, Charsets.ISO_8859_1)
    }
}
