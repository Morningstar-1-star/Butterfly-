package com.example.torrent.protocol

import android.util.Log
import com.example.torrent.bencode.Bencode
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.BitSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class PeerConnection(
    val peerAddress: InetSocketAddress,
    val infoHashBytes: ByteArray,
    val myPeerIdBytes: ByteArray,
    val onMetadataPieceReceived: ((pieceIndex: Int, totalSize: Int, data: ByteArray) -> Unit)? = null,
    val onBlockReceived: ((pieceIndex: Int, offset: Int, data: ByteArray) -> Unit)? = null,
    val onPeerDisconnected: ((PeerConnection) -> Unit)? = null
) {
    companion object {
        private const val TAG = "PeerConnection"
        private const val EXTENDED_MSG_ID = 20
        private const val UT_METADATA_EXT_ID = 1
    }

    private var socket: Socket? = null
    private var inStream: DataInputStream? = null
    private var outStream: DataOutputStream? = null
    private val isRunning = AtomicBoolean(false)

    var isChoked: Boolean = true
        private set
    var isInterested: Boolean = false
        private set
    var peerSupportsExtensions: Boolean = false
        private set

    var peerUtMetadataId: Int = -1
        private set
    var metadataSize: Int = 0
        private set

    val peerPieces = BitSet()
    var pieceCount: Int = 0

    fun connect(timeoutMs: Int = 5000): Boolean {
        try {
            socket = Socket()
            socket?.soTimeout = 15000
            socket?.connect(peerAddress, timeoutMs)

            inStream = DataInputStream(socket!!.getInputStream())
            outStream = DataOutputStream(socket!!.getOutputStream())

            if (!sendHandshake()) {
                disconnect()
                return false
            }

            if (!receiveHandshake()) {
                disconnect()
                return false
            }

            isRunning.set(true)

            // Send BEP 10 extended handshake if extensions are supported
            sendExtendedHandshake()

            // Send unchoke and interested
            sendInterested()

            return true
        } catch (e: Exception) {
            disconnect()
            return false
        }
    }

    private fun sendHandshake(): Boolean {
        try {
            val out = outStream ?: return false
            val buffer = ByteBuffer.allocate(68)
            buffer.put(19.toByte())
            buffer.put("BitTorrent protocol".toByteArray(StandardCharsets.US_ASCII))

            // 8 reserved bytes, enable BEP 10 extension bit (0x10 on 5th byte - index 4)
            val reserved = ByteArray(8)
            reserved[5] = (reserved[5].toInt() or 0x10).toByte()
            buffer.put(reserved)

            buffer.put(infoHashBytes)
            buffer.put(myPeerIdBytes)

            out.write(buffer.array())
            out.flush()
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun receiveHandshake(): Boolean {
        try {
            val inp = inStream ?: return false
            val pstrlen = inp.readUnsignedByte()
            if (pstrlen != 19) return false

            val pstrBytes = ByteArray(19)
            inp.readFully(pstrBytes)
            val pstr = String(pstrBytes, StandardCharsets.US_ASCII)
            if (pstr != "BitTorrent protocol") return false

            val reserved = ByteArray(8)
            inp.readFully(reserved)
            peerSupportsExtensions = (reserved[5].toInt() and 0x10) != 0

            val peerHash = ByteArray(20)
            inp.readFully(peerHash)
            if (!peerHash.contentEquals(infoHashBytes)) return false

            val peerId = ByteArray(20)
            inp.readFully(peerId)
            return true
        } catch (_: Exception) {
            return false
        }
    }

    fun sendExtendedHandshake() {
        try {
            val payload = mapOf(
                "m" to mapOf("ut_metadata" to UT_METADATA_EXT_ID),
                "v" to "Butterfly 1.0"
            )
            val bencoded = Bencode.encode(payload)
            sendExtendedMessage(0, bencoded)
        } catch (_: Exception) {}
    }

    fun requestMetadataPiece(pieceIndex: Int) {
        if (peerUtMetadataId <= 0) return
        try {
            val payload = mapOf(
                "msg_type" to 0, // 0 = request
                "piece" to pieceIndex
            )
            val bencoded = Bencode.encode(payload)
            sendExtendedMessage(peerUtMetadataId, bencoded)
        } catch (_: Exception) {}
    }

    private fun sendExtendedMessage(extendedId: Int, data: ByteArray) {
        try {
            val out = outStream ?: return
            val length = 2 + data.size // 1 byte for 20 (extended), 1 byte for extendedId + payload
            out.writeInt(length)
            out.writeByte(EXTENDED_MSG_ID)
            out.writeByte(extendedId)
            out.write(data)
            out.flush()
        } catch (_: Exception) {}
    }

    fun sendInterested() {
        try {
            val out = outStream ?: return
            out.writeInt(1)
            out.writeByte(2) // 2 = interested
            out.flush()
            isInterested = true
        } catch (_: Exception) {}
    }

    fun requestBlock(pieceIndex: Int, begin: Int, length: Int) {
        try {
            val out = outStream ?: return
            out.writeInt(13)
            out.writeByte(6) // 6 = request
            out.writeInt(pieceIndex)
            out.writeInt(begin)
            out.writeInt(length)
            out.flush()
        } catch (_: Exception) {}
    }

    fun startListening() {
        Thread {
            try {
                val inp = inStream ?: return@Thread
                while (isRunning.get()) {
                    val length = inp.readInt()
                    if (length < 0) break
                    if (length == 0) {
                        // Keep-Alive
                        continue
                    }

                    val msgId = inp.readUnsignedByte()
                    val payloadLength = length - 1

                    when (msgId) {
                        0 -> isChoked = true
                        1 -> isChoked = false
                        2 -> {} // interested
                        3 -> {} // not interested
                        4 -> {
                            // Have
                            val pieceIndex = inp.readInt()
                            peerPieces.set(pieceIndex)
                        }
                        5 -> {
                            // Bitfield
                            val bitfieldBytes = ByteArray(payloadLength)
                            inp.readFully(bitfieldBytes)
                            pieceCount = bitfieldBytes.size * 8
                            for (i in bitfieldBytes.indices) {
                                val b = bitfieldBytes[i].toInt()
                                for (bit in 0 until 8) {
                                    if ((b and (1 shl (7 - bit))) != 0) {
                                        peerPieces.set(i * 8 + bit)
                                    }
                                }
                            }
                        }
                        6 -> {
                            // Request - skip payload (we are leeching)
                            inp.skipBytes(payloadLength)
                        }
                        7 -> {
                            // Piece block
                            val pieceIndex = inp.readInt()
                            val begin = inp.readInt()
                            val blockDataLength = payloadLength - 8
                            val blockData = ByteArray(blockDataLength)
                            inp.readFully(blockData)
                            onBlockReceived?.invoke(pieceIndex, begin, blockData)
                        }
                        8 -> {
                            // Cancel
                            inp.skipBytes(payloadLength)
                        }
                        EXTENDED_MSG_ID -> {
                            // Extended message (BEP 10)
                            val extMsgId = inp.readUnsignedByte()
                            val extPayloadLength = payloadLength - 1
                            val extData = ByteArray(extPayloadLength)
                            inp.readFully(extData)
                            handleExtendedMessage(extMsgId, extData)
                        }
                        else -> {
                            inp.skipBytes(payloadLength)
                        }
                    }
                }
            } catch (e: Exception) {
                // Disconnected
            } finally {
                disconnect()
            }
        }.start()
    }

    private fun handleExtendedMessage(extMsgId: Int, data: ByteArray) {
        try {
            if (extMsgId == 0) {
                // Peer's extended handshake
                val decoded = Bencode.decode(data) as? Map<String, Any?> ?: return
                val mDict = Bencode.getDict(decoded, "m")
                if (mDict != null) {
                    peerUtMetadataId = Bencode.getLong(mDict, "ut_metadata", -1L).toInt()
                }
                metadataSize = Bencode.getLong(decoded, "metadata_size", 0L).toInt()
            } else if (extMsgId == UT_METADATA_EXT_ID || extMsgId == peerUtMetadataId) {
                // ut_metadata message (BEP 9)
                val stream = ByteArrayInputStream(data)
                val decoded = Bencode.decode(stream) as? Map<String, Any?> ?: return
                val msgType = Bencode.getLong(decoded, "msg_type", -1L).toInt()
                val piece = Bencode.getLong(decoded, "piece", -1L).toInt()
                val totalSize = Bencode.getLong(decoded, "total_size", metadataSize.toLong()).toInt()

                if (msgType == 1 && piece >= 0) {
                    // msg_type 1 = data
                    val dictBytes = Bencode.encode(decoded)
                    val rawDataOffset = dictBytes.size
                    if (data.size > rawDataOffset) {
                        val pieceData = data.copyOfRange(rawDataOffset, data.size)
                        onMetadataPieceReceived?.invoke(piece, totalSize, pieceData)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun hasPiece(index: Int): Boolean = peerPieces.get(index)

    fun disconnect() {
        if (isRunning.getAndSet(false)) {
            try { socket?.close() } catch (_: Exception) {}
            onPeerDisconnected?.invoke(this)
        }
    }
}
