package com.example.torrent

import android.content.Context
import android.util.Log
import com.example.util.MegaCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread

/**
 * Local HTTP Stream Server for Mega.nz files.
 * Provides on-the-fly decryption from Mega encrypted download URLs into standard HTTP video streams for ExoPlayer.
 */
object MegaStreamServer {
    private const val TAG = "MegaStreamServer"
    private var serverSocket: ServerSocket? = null
    private var serverPort: Int = 0
    private var isRunning = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    data class MegaStreamEntry(
        val nodeHandle: String,
        val folderId: String,
        val fileName: String,
        val sizeBytes: Long,
        val keyBytes: ByteArray,
        val ivBytes: ByteArray,
        var cachedDirectDownloadUrl: String? = null
    )

    private val activeStreams = ConcurrentHashMap<String, MegaStreamEntry>()

    fun start(context: Context) {
        if (isRunning && serverSocket != null && !serverSocket!!.isClosed) return

        synchronized(this) {
            if (isRunning) return
            try {
                serverSocket = ServerSocket(0)
                serverPort = serverSocket!!.localPort
                isRunning = true
                Log.d(TAG, "MegaStreamServer started on 127.0.0.1:$serverPort")

                thread(isDaemon = true, name = "MegaStreamServer-Thread") {
                    while (isRunning) {
                        try {
                            val socket = serverSocket?.accept() ?: break
                            thread(isDaemon = true) {
                                handleClient(socket)
                            }
                        } catch (e: Exception) {
                            if (!isRunning) break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MegaStreamServer", e)
            }
        }
    }

    fun registerStream(
        handle: String,
        folderId: String,
        fileName: String,
        sizeBytes: Long,
        keyBytes: ByteArray,
        ivBytes: ByteArray,
        downloadUrl: String? = null
    ): String {
        val entry = MegaStreamEntry(
            nodeHandle = handle,
            folderId = folderId,
            fileName = fileName,
            sizeBytes = sizeBytes,
            keyBytes = keyBytes,
            ivBytes = ivBytes,
            cachedDirectDownloadUrl = downloadUrl
        )
        activeStreams[handle] = entry

        if (serverPort == 0 && serverSocket != null) {
            serverPort = serverSocket!!.localPort
        }
        val port = if (serverPort != 0) serverPort else 8998
        return "http://127.0.0.1:$port/mega/$handle"
    }

    /**
     * Resolves the direct download URL from Mega CS API if not already cached.
     */
    suspend fun getOrFetchDownloadUrl(entry: MegaStreamEntry): String? = withContext(Dispatchers.IO) {
        if (!entry.cachedDirectDownloadUrl.isNullOrBlank()) {
            return@withContext entry.cachedDirectDownloadUrl
        }

        try {
            val apiUrl = if (entry.folderId.isNotBlank()) {
                "https://g.mega.co.nz/cs?id=${(100000..999999).random()}&n=${entry.folderId}"
            } else {
                "https://g.mega.co.nz/cs?id=${(100000..999999).random()}"
            }

            val payloadObj = JSONObject().apply {
                put("a", "g")
                put("g", 1)
                put("n", entry.nodeHandle)
            }
            val mediaType = "application/json".toMediaTypeOrNull()
            val requestBody = JSONArray().put(payloadObj).toString().toRequestBody(mediaType)

            val req = Request.Builder()
                .url(apiUrl)
                .post(requestBody)
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful && body.startsWith("[")) {
                val jsonArr = JSONArray(body)
                if (jsonArr.length() > 0) {
                    val resObj = jsonArr.optJSONObject(0)
                    val gUrl = resObj?.optString("g")
                    if (!gUrl.isNullOrBlank()) {
                        entry.cachedDirectDownloadUrl = gUrl
                        return@withContext gUrl
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching direct download url for handle: ${entry.nodeHandle}", e)
        }
        return@withContext null
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 30000
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            val headers = readHttpHeaders(input)
            val requestLine = headers.getOrNull(0) ?: ""
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                socket.close()
                return
            }

            val path = parts[1] // e.g. /mega/{handle}
            val handle = path.substringAfter("/mega/").substringBefore("?").substringBefore("/")
            val entry = activeStreams[handle]

            if (entry == null) {
                send404(output)
                socket.close()
                return
            }

            // Parse Range header
            var rangeStart: Long = 0
            var rangeEnd: Long = if (entry.sizeBytes > 0) entry.sizeBytes - 1 else -1

            headers.forEach { line ->
                if (line.startsWith("Range:", ignoreCase = true)) {
                    val rangeVal = line.substringAfter(":").trim()
                    if (rangeVal.startsWith("bytes=")) {
                        val rangeParts = rangeVal.substringAfter("bytes=").split("-")
                        rangeStart = rangeParts.getOrNull(0)?.toLongOrNull() ?: 0L
                        if (rangeParts.size > 1 && rangeParts[1].isNotBlank()) {
                            rangeEnd = rangeParts[1].toLongOrNull() ?: rangeEnd
                        }
                    }
                }
            }

            var downloadUrl = entry.cachedDirectDownloadUrl
            if (downloadUrl.isNullOrBlank()) {
                // Fetch synchronously
                val apiUrl = if (entry.folderId.isNotBlank()) {
                    "https://g.mega.co.nz/cs?id=${(100000..999999).random()}&n=${entry.folderId}"
                } else {
                    "https://g.mega.co.nz/cs?id=${(100000..999999).random()}"
                }

                val payloadObj = JSONObject().apply {
                    put("a", "g")
                    put("g", 1)
                    put("n", entry.nodeHandle)
                }
                val mediaType = "application/json".toMediaTypeOrNull()
                val requestBody = JSONArray().put(payloadObj).toString().toRequestBody(mediaType)

                val req = Request.Builder().url(apiUrl).post(requestBody).build()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string() ?: ""
                if (resp.isSuccessful && body.startsWith("[")) {
                    val jsonArr = JSONArray(body)
                    if (jsonArr.length() > 0) {
                        val resObj = jsonArr.optJSONObject(0)
                        downloadUrl = resObj?.optString("g")
                        entry.cachedDirectDownloadUrl = downloadUrl
                    }
                }
            }

            if (downloadUrl.isNullOrBlank()) {
                send502(output)
                socket.close()
                return
            }

            // Mega AES-CTR encryption aligns on 16-byte blocks.
            // Request range from upstream aligned on 16 bytes:
            val alignedStart = (rangeStart / 16) * 16
            val initialBlockIndex = rangeStart / 16
            val offsetInFirstBlock = (rangeStart % 16).toInt()

            val upstreamReq = Request.Builder()
                .url(downloadUrl)
                .header("Range", "bytes=$alignedStart-")
                .build()

            val upstreamResp = client.newCall(upstreamReq).execute()
            val upstreamBody = upstreamResp.body
            if (!upstreamResp.isSuccessful || upstreamBody == null) {
                send502(output)
                socket.close()
                return
            }

            val totalContentLength = if (entry.sizeBytes > 0) entry.sizeBytes else upstreamBody.contentLength()
            val finalEnd = if (rangeEnd != -1L && rangeEnd < totalContentLength) rangeEnd else totalContentLength - 1
            val servingLength = (finalEnd - rangeStart) + 1

            val isPartial = (rangeStart > 0 || rangeEnd != -1L)
            val statusHeader = if (isPartial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n"

            val contentType = getContentType(entry.fileName)
            val headerResponse = StringBuilder().apply {
                append(statusHeader)
                append("Content-Type: $contentType\r\n")
                append("Accept-Ranges: bytes\r\n")
                append("Content-Length: $servingLength\r\n")
                if (isPartial) {
                    append("Content-Range: bytes $rangeStart-$finalEnd/$totalContentLength\r\n")
                }
                append("Connection: close\r\n")
                append("\r\n")
            }.toString()

            output.write(headerResponse.toByteArray(Charsets.UTF_8))
            output.flush()

            // Setup AES-CTR cipher starting from initialBlockIndex
            // Mega IV is: [iv0, iv1, (initialBlockIndex >> 32), (initialBlockIndex & 0xFFFFFFFF)]
            val cipher = initMegaCtrCipher(entry.keyBytes, entry.ivBytes, initialBlockIndex)

            val upstreamStream = upstreamBody.byteStream()
            val buffer = ByteArray(32 * 1024)
            var bytesToSkipInStream = offsetInFirstBlock
            var totalWritten: Long = 0

            while (totalWritten < servingLength) {
                val needed = minOf(buffer.size.toLong(), (servingLength - totalWritten) + bytesToSkipInStream).toInt()
                val read = upstreamStream.read(buffer, 0, needed)
                if (read <= 0) break

                val decrypted = cipher.update(buffer, 0, read)
                if (decrypted != null && decrypted.isNotEmpty()) {
                    var writeOffset = 0
                    var writeLen = decrypted.size

                    if (bytesToSkipInStream > 0) {
                        if (bytesToSkipInStream >= decrypted.size) {
                            bytesToSkipInStream -= decrypted.size
                            continue
                        } else {
                            writeOffset = bytesToSkipInStream
                            writeLen = decrypted.size - bytesToSkipInStream
                            bytesToSkipInStream = 0
                        }
                    }

                    val actualToWrite = minOf(writeLen.toLong(), servingLength - totalWritten).toInt()
                    output.write(decrypted, writeOffset, actualToWrite)
                    output.flush()
                    totalWritten += actualToWrite
                }
            }
        } catch (e: Exception) {
            // Client disconnected or pipe closed
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {}
        }
    }

    private fun initMegaCtrCipher(key: ByteArray, baseIv: ByteArray, blockIndex: Long): Cipher {
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        val secretKey = SecretKeySpec(key, "AES")

        // Prepare IV with high 64-bit from baseIv, low 64-bit as blockIndex
        val ivBuffer = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
        ivBuffer.put(baseIv, 0, 8) // First 8 bytes of IV
        ivBuffer.putLong(blockIndex) // Counter as 64-bit integer

        val ivSpec = IvParameterSpec(ivBuffer.array())
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
        return cipher
    }

    private fun readHttpHeaders(input: InputStream): List<String> {
        val lines = mutableListOf<String>()
        val sb = StringBuilder()
        var lastChar = ' '

        while (true) {
            val b = input.read()
            if (b == -1) break
            val c = b.toChar()
            if (c == '\n' && lastChar == '\r') {
                val line = sb.toString().trim()
                if (line.isEmpty()) break
                lines.add(line)
                sb.clear()
            } else if (c != '\r') {
                sb.append(c)
            }
            lastChar = c
        }
        return lines
    }

    private fun getContentType(fileName: String): String {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".mp4") -> "video/mp4"
            lower.endsWith(".mkv") -> "video/x-matroska"
            lower.endsWith(".webm") -> "video/webm"
            lower.endsWith(".avi") -> "video/x-msvideo"
            lower.endsWith(".mov") -> "video/quicktime"
            lower.endsWith(".ts") -> "video/mp2t"
            lower.endsWith(".m4v") -> "video/x-m4v"
            else -> "video/mp4"
        }
    }

    private fun send404(output: OutputStream) {
        val resp = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        output.write(resp.toByteArray(Charsets.UTF_8))
        output.flush()
    }

    private fun send502(output: OutputStream) {
        val resp = "HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        output.write(resp.toByteArray(Charsets.UTF_8))
        output.flush()
    }
}
