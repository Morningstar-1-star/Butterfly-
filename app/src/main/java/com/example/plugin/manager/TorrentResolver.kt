package com.example.plugin.manager

import android.util.Log
import com.example.utils.TorrentUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ResolvedTorrentStream(
    val infoHash: String,
    val playableUrl: String,
    val title: String,
    val fileName: String? = null,
    val sizeBytes: Long = 0L,
    val isHls: Boolean = false
)

class TorrentResolver {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Resolves a magnet or infoHash into a direct playable HTTP/HLS stream URL.
     * Returns null if the torrent cannot be resolved into a playable HTTP media stream.
     */
    suspend fun resolveTorrent(
        magnetOrHash: String,
        title: String = "Torrent Stream",
        apiKey: String? = null
    ): ResolvedTorrentStream? = withContext(Dispatchers.IO) {
        val infoHash = TorrentUtils.extractInfoHash(magnetOrHash) ?: return@withContext null
        Log.d("TorrentResolver", "[Torrent] Resolving infoHash: $infoHash for title: $title")

        // 1. Check if the URL is already a direct HTTP/HLS stream provided by Torrentio or Debrid
        if (magnetOrHash.startsWith("http://") || magnetOrHash.startsWith("https://")) {
            if (isDirectPlayableUrl(magnetOrHash)) {
                Log.d("TorrentResolver", "[Torrent] Found direct playable stream URL: $magnetOrHash")
                return@withContext ResolvedTorrentStream(
                    infoHash = infoHash,
                    playableUrl = magnetOrHash,
                    title = title,
                    isHls = magnetOrHash.contains(".m3u8", ignoreCase = true)
                )
            }
        }

        // 2. Try TorBox API if user configured an API token
        val effectiveKey = apiKey?.trim() ?: ""
        if (effectiveKey.isNotBlank()) {
            val torboxResult = resolveTorBoxHash(infoHash, effectiveKey)
            if (torboxResult != null) {
                Log.d("TorrentResolver", "[Torrent] Resolved via TorBox API: ${torboxResult.playableUrl}")
                return@withContext torboxResult
            } else {
                Log.w("TorrentResolver", "[Torrent] TorBox API resolution returned null for infoHash $infoHash")
            }
        } else {
            Log.w("TorrentResolver", "[Torrent] TorBox API key not configured. Debrid resolution skipped.")
        }

        Log.d("TorrentResolver", "[Torrent] No direct HTTP/Debrid stream available for infoHash $infoHash.")
        null
    }

    private suspend fun isDirectPlayableUrl(url: String): Boolean {
        return withTimeoutOrNull(3000L) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Android) ExoPlayer/2.18")
                    .head()
                    .build()
                val response = httpClient.newCall(request).execute()
                val isOk = response.isSuccessful
                val contentType = response.header("Content-Type")?.lowercase() ?: ""
                response.close()
                isOk && !contentType.contains("html") && (contentType.contains("video") || contentType.contains("stream") || contentType.contains("application/vnd.apple.mpegurl") || contentType.contains("octet-stream"))
            } catch (e: Exception) {
                false
            }
        } ?: false
    }

    private suspend fun resolveTorBoxHash(infoHash: String, apiKey: String): ResolvedTorrentStream? {
        return withTimeoutOrNull(4000L) {
            try {
                val url = "https://api.torbox.app/v1/api/torrents/checkcached?hash=$infoHash&format=object"
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $apiKey")
                    .get()
                    .build()
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    response.close()
                    val json = JSONObject(body)
                    if (json.optBoolean("success", false)) {
                        val data = json.optJSONObject("data")
                        if (data != null && data.has(infoHash.lowercase())) {
                            val cachedObj = data.getJSONObject(infoHash.lowercase())
                            val name = cachedObj.optString("name", "TorBox Cached Video")
                            val size = cachedObj.optLong("size", 0L)
                            val streamUrl = "https://api.torbox.app/v1/api/torrents/requestdl?hash=$infoHash&token=$apiKey"
                            return@withTimeoutOrNull ResolvedTorrentStream(
                                infoHash = infoHash,
                                playableUrl = streamUrl,
                                title = name,
                                sizeBytes = size
                            )
                        }
                    }
                } else {
                    response.close()
                }
                null
            } catch (e: Exception) {
                null
            }
        }
    }
}
