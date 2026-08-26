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
 * Production BitTorrent DHT (BEP 5) Client with pure ByteArray KRPC binary message handling.
 * Implements Kademlia-based Distributed Hash Table protocol over UDP.
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

    // Pending transactions: transactionId (Hex) -> CompletableDeferred<Map<String, Any?>>
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
            Log.w(TAG, "Failed to bind DHT socket on port $port: ${e.message}. Retrying with dynamic port.")
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
        } catch (_: Exception) {}
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
                        val tokenBytes = when (val t = r["token"]) {
                            is ByteArray -> t
                            is String -> t.toByteArray(Charsets.ISO_8859_1)
                            else -> null
                        }
                        if (tokenBytes != null) {
                            peerTokens[infoHashHex] = tokenBytes
                        }

                        // Parse 'values' (peers)
                        val values = r["values"] as? List<*>
                        if (values != null) {
                            for (v in values) {
                                val peerBytes = when (v) {
                                    is ByteArray -> v
                                    is String -> v.toByteArray(Charsets.ISO_8859_1)
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
                            is ByteArray -> n
                            is String -> n.toByteArray(Charsets.ISO_8859_1)
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
        val tBytes = generateTransactionId()
        val tKey = tBytes.joinToString("") { "%02x".format(it) }
        val query = mapOf(
            "t" to tBytes,
            "y" to "q",
            "q" to "find_node",
            "a" to mapOf(
                "id" to localNodeId,
                "target" to targetNodeId
            )
        )

        val deferred = CompletableDeferred<Map<String, Any?>>()
        pendingTransactions[tKey] = deferred

        try {
            sendBencoded(query, address)
            val response = withTimeoutOrNull(3000) { deferred.await() }
            if (response != null) {
                val r = response["r"] as? Map<*, *>
                val nodesBytes = when (val n = r?.get("nodes")) {
                    is ByteArray -> n
                    is String -> n.toByteArray(Charsets.ISO_8859_1)
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
            pendingTransactions.remove(tKey)
        }
        false
    }

    /**
     * BEP 5 announce_peer query: Announce that our node is downloading/seeding on port.
     */
    suspend fun announcePeer(infoHashBytes: ByteArray, tcpPort: Int, address: InetSocketAddress): Boolean = withContext(Dispatchers.IO) {
        val infoHashHex = infoHashBytes.joinToString("") { "%02x".format(it) }
        val token = peerTokens[infoHashHex] ?: return@withContext false

        val tBytes = generateTransactionId()
        val tKey = tBytes.joinToString("") { "%02x".format(it) }
        val query = mapOf(
            "t" to tBytes,
            "y" to "q",
            "q" to "announce_peer",
            "a" to mapOf(
                "id" to localNodeId,
                "info_hash" to infoHashBytes,
                "port" to tcpPort.toLong(),
                "token" to token
            )
        )

        val deferred = CompletableDeferred<Map<String, Any?>>()
        pendingTransactions[tKey] = deferred

        try {
            sendBencoded(query, address)
            val response = withTimeoutOrNull(3000) { deferred.await() }
            return@withContext response != null
        } catch (_: Exception) {
            return@withContext false
        } finally {
            pendingTransactions.remove(tKey)
        }
    }

    /**
     * BEP 5 ping query: Check if a DHT node is alive.
     */
    suspend fun ping(address: InetSocketAddress): Boolean = withContext(Dispatchers.IO) {
        val tBytes = generateTransactionId()
        val tKey = tBytes.joinToString("") { "%02x".format(it) }
        val query = mapOf(
            "t" to tBytes,
            "y" to "q",
            "q" to "ping",
            "a" to mapOf(
                "id" to localNodeId
            )
        )

        val deferred = CompletableDeferred<Map<String, Any?>>()
        pendingTransactions[tKey] = deferred

        try {
            sendBencoded(query, address)
            val response = withTimeoutOrNull(2500) { deferred.await() }
            return@withContext response != null
        } catch (_: Exception) {
            return@withContext false
        } finally {
            pendingTransactions.remove(tKey)
        }
    }

    private suspend fun sendGetPeersQuery(address: InetSocketAddress, infoHashBytes: ByteArray): Map<String, Any?>? = withContext(Dispatchers.IO) {
        val tBytes = generateTransactionId()
        val tKey = tBytes.joinToString("") { "%02x".format(it) }
        val query = mapOf(
            "t" to tBytes,
            "y" to "q",
            "q" to "get_peers",
            "a" to mapOf(
                "id" to localNodeId,
                "info_hash" to infoHashBytes
            )
        )

        val deferred = CompletableDeferred<Map<String, Any?>>()
        pendingTransactions[tKey] = deferred

        try {
            sendBencoded(query, address)
            return@withContext withTimeoutOrNull(3000) { deferred.await() }
        } catch (_: Exception) {
            return@withContext null
        } finally {
            pendingTransactions.remove(tKey)
        }
    }

    private fun sendBencoded(map: Map<String, Any?>, destination: InetSocketAddress) {
        val bytes = Bencode.encode(map)
        val packet = DatagramPacket(bytes, bytes.size, destination.address, destination.port)
        socket?.send(packet)
    }

    private suspend fun listenLoop() = withContext(Dispatchers.IO) {
        val buffer = ByteArray(UDP_BUFFER_SIZE)
        val packet = DatagramPacket(buffer, buffer.size)

        while (isRunning.get() && isActive) {
            try {
                socket?.receive(packet) ?: break
                val data = packet.data.copyOfRange(0, packet.length)
                val senderAddr = InetSocketAddress(packet.address, packet.port)

                scope.launch {
                    handleIncomingPacket(data, senderAddr)
                }
            } catch (e: Exception) {
                if (!isRunning.get()) break
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleIncomingPacket(data: ByteArray, sender: InetSocketAddress) {
        try {
            val decoded = Bencode.decode(data) as? Map<String, Any?> ?: return
            val y = Bencode.getString(decoded, "y")
            val tBytes = when (val t = decoded["t"]) {
                is ByteArray -> t
                is String -> t.toByteArray(Charsets.ISO_8859_1)
                else -> null
            } ?: return
            val tKey = tBytes.joinToString("") { "%02x".format(it) }

            when (y) {
                "r" -> {
                    // Response to our query
                    pendingTransactions[tKey]?.complete(decoded)

                    // Learn sender node ID
                    val r = decoded["r"] as? Map<String, Any?>
                    val nodeIdBytes = r?.let { Bencode.getBytes(it, "id") }
                    if (nodeIdBytes != null && nodeIdBytes.size == 20) {
                        addNodeToRoutingTable(nodeIdBytes, sender)
                    }
                }
                "q" -> {
                    // Incoming KRPC query from remote peer
                    handleIncomingQuery(tBytes, decoded, sender)
                }
                "e" -> {
                    // Error response
                    pendingTransactions[tKey]?.cancel()
                }
            }
        } catch (_: Exception) {}
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleIncomingQuery(tBytes: ByteArray, query: Map<String, Any?>, sender: InetSocketAddress) {
        val q = Bencode.getString(query, "q")
        val a = query["a"] as? Map<String, Any?> ?: return
        val senderId = Bencode.getBytes(a, "id")

        if (senderId != null && senderId.size == 20) {
            addNodeToRoutingTable(senderId, sender)
        }

        when (q) {
            "ping" -> {
                val resp = mapOf(
                    "t" to tBytes,
                    "y" to "r",
                    "r" to mapOf(
                        "id" to localNodeId
                    )
                )
                sendBencoded(resp, sender)
            }
            "find_node" -> {
                val target = Bencode.getBytes(a, "target") ?: localNodeId
                val closest = findClosestNodes(target, K_BUCKET_SIZE)
                val resp = mapOf(
                    "t" to tBytes,
                    "y" to "r",
                    "r" to mapOf(
                        "id" to localNodeId,
                        "nodes" to serializeCompactNodes(closest)
                    )
                )
                sendBencoded(resp, sender)
            }
            "get_peers" -> {
                val infoHash = Bencode.getBytes(a, "info_hash") ?: return
                val token = ByteArray(8).apply { SecureRandom().nextBytes(this) }
                val closest = findClosestNodes(infoHash, K_BUCKET_SIZE)
                val resp = mapOf(
                    "t" to tBytes,
                    "y" to "r",
                    "r" to mapOf(
                        "id" to localNodeId,
                        "token" to token,
                        "nodes" to serializeCompactNodes(closest)
                    )
                )
                sendBencoded(resp, sender)
            }
            "announce_peer" -> {
                val resp = mapOf(
                    "t" to tBytes,
                    "y" to "r",
                    "r" to mapOf(
                        "id" to localNodeId
                    )
                )
                sendBencoded(resp, sender)
            }
        }
    }

    private fun addNodeToRoutingTable(id: ByteArray, address: InetSocketAddress) {
        val hex = id.joinToString("") { "%02x".format(it) }
        val node = routingTable[hex]
        if (node != null) {
            node.lastSeen = System.currentTimeMillis()
        } else if (routingTable.size < 500) {
            routingTable[hex] = DhtNode(id, address)
        }
    }

    private fun parseCompactNodes(bytes: ByteArray) {
        var offset = 0
        while (offset + 26 <= bytes.size) {
            val nodeId = bytes.copyOfRange(offset, offset + 20)
            val ipBytes = bytes.copyOfRange(offset + 20, offset + 24)
            val port = ByteBuffer.wrap(bytes, offset + 24, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
            try {
                val ip = InetAddress.getByAddress(ipBytes)
                if (port > 0 && !ip.isAnyLocalAddress && !ip.isLoopbackAddress) {
                    addNodeToRoutingTable(nodeId, InetSocketAddress(ip, port))
                }
            } catch (_: Exception) {}
            offset += 26
        }
    }

    private fun serializeCompactNodes(nodes: List<DhtNode>): ByteArray {
        val bb = ByteBuffer.allocate(nodes.size * 26).order(ByteOrder.BIG_ENDIAN)
        for (n in nodes) {
            bb.put(n.id)
            bb.put(n.address.address.address)
            bb.putShort(n.address.port.toShort())
        }
        return bb.array()
    }

    private fun findClosestNodes(target: ByteArray, count: Int): List<DhtNode> {
        return routingTable.values.sortedBy { node ->
            xorDistance(node.id, target)
        }.take(count)
    }

    private fun xorDistance(id1: ByteArray, id2: ByteArray): Long {
        var dist = 0L
        for (i in 0 until minOf(8, minOf(id1.size, id2.size))) {
            val diff = (id1[i].toInt() xor id2[i].toInt()) and 0xFF
            dist = (dist shl 8) or diff.toLong()
        }
        return dist
    }

    private fun generateTransactionId(): ByteArray {
        val b = ByteArray(2)
        SecureRandom().nextBytes(b)
        return b
    }
}
