package com.example.torrent.server

import android.util.Log
import com.example.torrent.engine.TorrentEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.*
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

class TorrentHttpServer(
    private val engine: TorrentEngine,
    val port: Int = 0 // 0 means dynamic ephemeral port
) {
    companion object {
        private const val TAG = "TorrentHttpServer"
        private const val BUFFER_SIZE = 64 * 1024 // 64 KB chunk
    }

    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val _isReady = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isReady: kotlinx.coroutines.flow.StateFlow<Boolean> = _isReady

    val assignedPort: Int
        get() = serverSocket?.localPort ?: if (port > 0) port else 0

    val streamUrl: String
        get() = "http://127.0.0.1:$assignedPort/stream?hash=${engine.getActiveInfoHash() ?: ""}"

    suspend fun awaitReady(): Int {
        if (isRunning.get() && serverSocket != null && !serverSocket!!.isClosed) {
            return assignedPort
        }
        return start()
    }

    fun start(): Int {
        if (isRunning.get() && serverSocket != null && !serverSocket!!.isClosed) {
            return assignedPort
        }

        synchronized(this) {
            if (isRunning.get() && serverSocket != null && !serverSocket!!.isClosed) {
                return assignedPort
            }

            // Close existing socket if lingering
            try { serverSocket?.close() } catch (_: Exception) {}

            val boundSocket = try {
                if (port > 0) {
                    try {
                        ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
                    } catch (e: Exception) {
                        Log.w(TAG, "Port $port occupied or unavailable, falling back to OS allocated dynamic port: ${e.message}")
                        ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
                    }
                } else {
                    ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind TorrentHttpServer: ${e.message}", e)
                throw IllegalStateException("Failed to bind Torrent HTTP server", e)
            }

            serverSocket = boundSocket
            isRunning.set(true)
            _isReady.value = true
            val listeningPort = boundSocket.localPort
            Log.i("ButterflyTorrent", "Torrent HTTP bridge server listening on http://127.0.0.1:$listeningPort")

            serverScope.launch {
                try {
                    while (isRunning.get() && !boundSocket.isClosed) {
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
                    Log.e(TAG, "Server socket accept loop error: ${e.message}")
                } finally {
                    stop()
                }
            }
            return listeningPort
        }
    }

    private suspend fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 60000
            val inStream = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
            val outStream = BufferedOutputStream(socket.getOutputStream())

            val requestLine = inStream.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val fullPath = parts[1]

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
                sendResponse(outStream, "405 Method Not Allowed", mapOf("Connection" to "close"), ByteArray(0))
                return
            }

            // Extract hash query parameter and validate against active engine session
            val uri = android.net.Uri.parse("http://127.0.0.1$fullPath")
            val requestedHash = uri.getQueryParameter("hash")?.lowercase()?.trim()
            val activeHash = engine.getActiveInfoHash()

            if (requestedHash != null && activeHash != null && requestedHash != activeHash) {
                Log.w("ButterflyTorrent", "Requested stream hash mismatch: requested=$requestedHash, active=$activeHash")
                sendResponse(
                    outStream,
                    "404 Not Found",
                    mapOf("Connection" to "close"),
                    "Torrent hash mismatch or session inactive\n".toByteArray(StandardCharsets.UTF_8)
                )
                return
            }

            // Await metadata and file size from active engine session (up to 45 seconds with frequent checks)
            var totalLength = engine.getActiveFileLength()
            var retries = 0
            while (totalLength <= 0 && retries < 450 && isRunning.get() && !socket.isClosed) {
                kotlinx.coroutines.delay(100)
                totalLength = engine.getActiveFileLength()
                retries++
                if (retries % 30 == 0) {
                    Log.i("ButterflyTorrent", "HTTP server waiting for metadata: ${retries / 10}s elapsed (hash: ${activeHash ?: requestedHash})")
                }
            }

            // If metadata is still unavailable after 45s, return 503 Service Unavailable
            if (totalLength <= 0) {
                Log.w("ButterflyTorrent", "Torrent metadata timed out after 45s for hash ${activeHash ?: requestedHash}. Returning 503.")
                sendResponse(
                    outStream,
                    "503 Service Unavailable",
                    mapOf(
                        "Retry-After" to "3",
                        "Connection" to "close"
                    ),
                    "503 Service Unavailable: Swarm metadata resolution timed out\n".toByteArray(StandardCharsets.UTF_8)
                )
                return
            }

            val fileName = engine.getFileName()
            val contentType = getContentType(fileName)
            val rangeHeader = headers["range"]

            var startByte = 0L
            var endByte = totalLength - 1
            var isRangeRequest = rangeHeader != null && rangeHeader.startsWith("bytes=")
            var isRangeInvalid = false

            if (isRangeRequest) {
                val rangeSpec = rangeHeader!!.substring(6).trim()
                if (rangeSpec.startsWith("-")) {
                    // Suffix byte range: e.g. bytes=-500
                    val suffixLen = rangeSpec.substring(1).toLongOrNull() ?: -1L
                    if (suffixLen <= 0L) {
                        isRangeInvalid = true
                    } else {
                        startByte = (totalLength - suffixLen).coerceAtLeast(0L)
                        endByte = totalLength - 1
                    }
                } else {
                    val rangeParts = rangeSpec.split("-", limit = 2)
                    if (rangeParts[0].isNotEmpty()) {
                        val parsedStart = rangeParts[0].toLongOrNull()
                        if (parsedStart == null || parsedStart < 0L || parsedStart >= totalLength) {
                            isRangeInvalid = true
                        } else {
                            startByte = parsedStart
                        }
                    }
                    if (!isRangeInvalid && rangeParts.size > 1 && rangeParts[1].isNotEmpty()) {
                        val parsedEnd = rangeParts[1].toLongOrNull()
                        if (parsedEnd == null || parsedEnd < startByte) {
                            isRangeInvalid = true
                        } else {
                            endByte = minOf(parsedEnd, totalLength - 1)
                        }
                    }
                }

                if (startByte > endByte || startByte >= totalLength) {
                    isRangeInvalid = true
                }
            }

            if (isRangeInvalid) {
                sendResponse(
                    outStream,
                    "416 Range Not Satisfiable",
                    mapOf(
                        "Content-Range" to "bytes */$totalLength",
                        "Content-Type" to "text/plain",
                        "Connection" to "close"
                    ),
                    "Requested Range Not Satisfiable\n".toByteArray(StandardCharsets.UTF_8)
                )
                return
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

            // Send HTTP headers
            val headerSb = StringBuilder("HTTP/1.1 $statusCode\r\n")
            for ((k, v) in responseHeaders) {
                headerSb.append("$k: $v\r\n")
            }
            headerSb.append("\r\n")
            outStream.write(headerSb.toString().toByteArray(StandardCharsets.US_ASCII))
            outStream.flush()

            if (method == "HEAD") return

            // Notify engine of seek
            engine.onPlaybackSeek(startByte)

            // Stream body in chunks
            var currentOffset = startByte
            val buffer = ByteArray(BUFFER_SIZE)
            var zeroReadCount = 0

            while (currentOffset <= endByte && isRunning.get() && !socket.isClosed) {
                val bytesToRead = minOf(BUFFER_SIZE.toLong(), (endByte - currentOffset + 1)).toInt()
                val bytesRead = engine.readBytesForStream(currentOffset, bytesToRead, buffer, 0)

                if (bytesRead > 0) {
                    zeroReadCount = 0
                    outStream.write(buffer, 0, bytesRead)
                    currentOffset += bytesRead
                    outStream.flush()
                } else if (bytesRead == -1) {
                    // EOF reached
                    break
                } else {
                    zeroReadCount++
                    if (zeroReadCount > 200) { // 200 * 100ms = 20s timeout
                        Log.w("ButterflyTorrent", "HTTP streaming timed out waiting for pieces at offset $currentOffset")
                        break
                    }
                    if (!isRunning.get() || socket.isClosed) break
                    kotlinx.coroutines.delay(100)
                }
            }
            Log.d("ButterflyTorrent", "Streamed ${currentOffset - startByte} bytes to client for hash ${activeHash ?: requestedHash}")
        } catch (e: Exception) {
            Log.d("ButterflyTorrent", "Client disconnected from torrent HTTP server: ${e.message}")
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
            _isReady.value = false
            try {
                serverSocket?.close()
                serverSocket = null
            } catch (_: Exception) {}
        }
    }
}
