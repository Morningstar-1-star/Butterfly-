package com.example.torrent.server

import android.util.Log
import com.example.torrent.core.TorrentSessionManager
import com.example.torrent.engine.TorrentEngine
import kotlinx.coroutines.*
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-performance HTTP 1.1 Range streaming bridge for Media3 / ExoPlayer.
 * Exposes BitTorrent swarm playback as a standard HTTP progressive stream with full Range,
 * 206 Partial Content, Content-Range, Content-Length, and dynamic piece deadline acceleration.
 */
class TorrentStreamServer(
    private val sessionProvider: () -> TorrentSessionManager?,
    val port: Int = 0
) {
    companion object {
        private const val TAG = "TorrentStreamServer"
        private const val CHUNK_SIZE = 64 * 1024 // 64 KB streaming chunk
    }

    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)

    val assignedPort: Int
        get() = serverSocket?.localPort ?: if (port > 0) port else 8899

    val streamUrl: String
        get() = "http://127.0.0.1:$assignedPort/stream"

    fun start() {
        if (isRunning.getAndSet(true)) return

        serverScope.launch {
            try {
                val boundSocket = try {
                    ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
                } catch (_: Exception) {
                    ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
                }
                serverSocket = boundSocket
                Log.i(TAG, "Torrent Stream Server running on http://127.0.0.1:${boundSocket.localPort}")

                while (isRunning.get()) {
                    val clientSocket = try {
                        boundSocket.accept()
                    } catch (_: Exception) {
                        break
                    }

                    launch {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server socket error: ${e.message}")
            } finally {
                stop()
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 30000
            val inStream = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
            val outStream = BufferedOutputStream(socket.getOutputStream())

            val requestLine = inStream.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()

            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = inStream.readLine() ?: break
                if (line.isEmpty()) break
                val headerParts = line.split(":", limit = 2)
                if (headerParts.size == 2) {
                    headers[headerParts[0].trim().lowercase()] = headerParts[1].trim()
                }
            }

            if (method != "GET" && method != "HEAD") {
                sendResponse(outStream, "405 Method Not Allowed", emptyMap(), ByteArray(0))
                return
            }

            val sessionMgr = sessionProvider()
            if (sessionMgr == null) {
                sendResponse(outStream, "503 Service Unavailable", emptyMap(), "No active torrent session".toByteArray())
                return
            }

            // Wait for file metadata
            var totalLength = sessionMgr.activeFileItem?.length ?: sessionMgr.activeRelease?.sizeBytes ?: 0L
            var retries = 0
            while (totalLength <= 0 && retries < 120 && !socket.isClosed && isRunning.get()) {
                delay(500)
                totalLength = sessionMgr.activeFileItem?.length ?: sessionMgr.activeRelease?.sizeBytes ?: 0L
                retries++
            }

            if (totalLength <= 0) {
                sendResponse(outStream, "503 Service Unavailable", emptyMap(), "Metadata discovery timeout".toByteArray())
                return
            }

            val fileName = sessionMgr.activeFileItem?.name ?: sessionMgr.activeRelease?.fileName ?: "video.mkv"
            val contentType = getContentType(fileName)
            val rangeHeader = headers["range"]

            var startByte = 0L
            var endByte = totalLength - 1
            val isRangeRequest = rangeHeader != null && rangeHeader.startsWith("bytes=")

            if (isRangeRequest) {
                val rangeSpec = rangeHeader!!.substring(6).trim()
                val rangeParts = rangeSpec.split("-", limit = 2)
                if (rangeParts[0].isNotEmpty()) {
                    startByte = rangeParts[0].toLongOrNull() ?: 0L
                }
                if (rangeParts.size > 1 && rangeParts[1].isNotEmpty()) {
                    endByte = rangeParts[1].toLongOrNull() ?: (totalLength - 1)
                }
                if (startByte < 0) startByte = 0
                if (endByte >= totalLength) endByte = totalLength - 1
            }

            val contentLength = (endByte - startByte + 1).coerceAtLeast(0L)
            val statusCode = if (isRangeRequest) "206 Partial Content" else "200 OK"

            val responseHeaders = mutableMapOf(
                "Accept-Ranges" to "bytes",
                "Content-Type" to contentType,
                "Content-Length" to contentLength.toString(),
                "Access-Control-Allow-Origin" to "*",
                "Connection" to "close"
            )

            if (isRangeRequest) {
                responseHeaders["Content-Range"] = "bytes $startByte-$endByte/$totalLength"
            }

            // Write HTTP headers
            val headerSb = StringBuilder("HTTP/1.1 $statusCode\r\n")
            for ((k, v) in responseHeaders) {
                headerSb.append("$k: $v\r\n")
            }
            headerSb.append("\r\n")
            outStream.write(headerSb.toString().toByteArray(StandardCharsets.US_ASCII))
            outStream.flush()

            if (method == "HEAD") return

            // Notify session of playback position to prioritize piece deadlines
            sessionMgr.onPlaybackSeek(startByte)

            // Stream body in chunks
            var currentOffset = startByte
            val buffer = ByteArray(CHUNK_SIZE)

            while (currentOffset <= endByte && isRunning.get()) {
                val bytesToRead = minOf(CHUNK_SIZE.toLong(), (endByte - currentOffset + 1)).toInt()
                val bytesRead = sessionMgr.readBytesForStream(currentOffset, bytesToRead, buffer, 0)

                if (bytesRead > 0) {
                    outStream.write(buffer, 0, bytesRead)
                    currentOffset += bytesRead
                    outStream.flush()
                } else {
                    // Piece still downloading - yield briefly to allow libtorrent block write
                    if (!isRunning.get() || socket.isClosed) break
                    delay(80)
                }
            }
        } catch (_: Exception) {
            // Player closed connection or seeked
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun sendResponse(out: OutputStream, status: String, headers: Map<String, String>, body: ByteArray) {
        val sb = StringBuilder("HTTP/1.1 $status\r\n")
        for ((k, v) in headers) {
            sb.append("$k: $v\r\n")
        }
        sb.append("Content-Length: ${body.size}\r\n\r\n")
        out.write(sb.toString().toByteArray(StandardCharsets.US_ASCII))
        if (body.isNotEmpty()) {
            out.write(body)
        }
        out.flush()
    }

    private fun getContentType(fileName: String): String {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".mkv") -> "video/x-matroska"
            lower.endsWith(".mp4") || lower.endsWith(".m4v") -> "video/mp4"
            lower.endsWith(".webm") -> "video/webm"
            lower.endsWith(".avi") -> "video/x-msvideo"
            lower.endsWith(".mov") -> "video/quicktime"
            lower.endsWith(".ts") -> "video/mp2t"
            else -> "video/x-matroska"
        }
    }

    fun stop() {
        if (isRunning.getAndSet(false)) {
            try {
                serverSocket?.close()
                serverSocket = null
            } catch (_: Exception) {}
        }
    }
}
