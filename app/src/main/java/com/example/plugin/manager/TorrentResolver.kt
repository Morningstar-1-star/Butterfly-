package com.example.plugin.manager

import android.content.Context
import android.util.Log
import com.example.util.DebridSettingsManager
import com.example.utils.TorrentUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
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

private data class TorBoxFileCandidate(
    val fileId: Long,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val isPreferredFormat: Boolean,
    val isVideo: Boolean,
    val isSample: Boolean
)

class TorrentResolver(
    private val context: Context? = null
) {
    companion object {
        private const val TAG = "TorrentResolver"
        private const val TORBOX_BASE_URL = "https://api.torbox.app/v1/api"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
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
        Log.d(TAG, "[Torrent] Resolving infoHash: $infoHash for title: '$title'")

        // 1. Check if the URL is already a direct HTTP/HLS stream
        if (magnetOrHash.startsWith("http://") || magnetOrHash.startsWith("https://")) {
            if (isDirectPlayableUrl(magnetOrHash)) {
                Log.d(TAG, "[Torrent] Found direct playable stream URL: $magnetOrHash")
                return@withContext ResolvedTorrentStream(
                    infoHash = infoHash,
                    playableUrl = magnetOrHash,
                    title = title,
                    isHls = magnetOrHash.contains(".m3u8", ignoreCase = true)
                )
            }
        }

        // 2. Try TorBox API if user configured an API token
        val effectiveKey = apiKey?.trim()?.ifEmpty { null }
            ?: context?.let { DebridSettingsManager.getTorBoxApiKey(it) }?.trim()?.ifEmpty { null }
            ?: ""

        if (effectiveKey.isNotBlank()) {
            val torboxResult = resolveTorBoxHash(infoHash, effectiveKey, title)
            if (torboxResult != null) {
                Log.d(TAG, sanitize("[Torrent] Resolved via TorBox API: ${torboxResult.playableUrl}", effectiveKey))
                return@withContext torboxResult
            } else {
                Log.w(TAG, sanitize("[Torrent] TorBox API resolution returned null for infoHash $infoHash", effectiveKey))
            }
        } else {
            Log.w(TAG, "[Torrent] TorBox API key not configured. Debrid resolution skipped.")
        }

        Log.d(TAG, "[Torrent] No direct HTTP/Debrid stream available for infoHash $infoHash.")
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

    private suspend fun resolveTorBoxHash(
        infoHash: String,
        apiKey: String,
        title: String
    ): ResolvedTorrentStream? {
        return withTimeoutOrNull(20000L) {
            try {
                // Step 1: Check TorBox cached availability
                val cachedStatus = checkTorBoxCached(infoHash, apiKey)
                Log.d(TAG, sanitize("[TorBox] Hash $infoHash cached on TorBox: $cachedStatus", apiKey))

                // Step 2: Query user's torrent list (mylist) to see if torrent is already present
                var torrentObj = findTorrentInMyList(infoHash, apiKey)
                var torrentId: Long = torrentObj?.optLong("id", -1L)?.takeIf { it != -1L }
                    ?: torrentObj?.optLong("torrent_id", -1L)
                    ?: -1L

                // Step 3: If torrent not in mylist, create/add it via official TorBox API
                if (torrentId == -1L) {
                    Log.d(TAG, sanitize("[TorBox] Torrent not in mylist. Adding torrent via createtorrent for hash $infoHash...", apiKey))
                    torrentId = createTorBoxTorrent(infoHash, title, apiKey)
                    if (torrentId == -1L) {
                        Log.e(TAG, sanitize("[TorBox] Failed to create torrent on TorBox for hash $infoHash.", apiKey))
                        return@withTimeoutOrNull null
                    }
                    Log.d(TAG, sanitize("[TorBox] Created torrent on TorBox with torrent_id=$torrentId", apiKey))
                } else {
                    Log.d(TAG, sanitize("[TorBox] Found existing torrent in mylist with torrent_id=$torrentId", apiKey))
                }

                // Step 4: Fetch/poll torrent file list until ready
                torrentObj = pollTorrentUntilReady(torrentId, infoHash, cachedStatus, apiKey)
                if (torrentObj == null) {
                    Log.e(TAG, sanitize("[TorBox] Could not obtain valid torrent object or file list for torrent_id=$torrentId", apiKey))
                    return@withTimeoutOrNull null
                }

                val filesArray = torrentObj.optJSONArray("files")
                if (filesArray == null || filesArray.length() == 0) {
                    Log.e(TAG, sanitize("[TorBox] Torrent file list is empty for torrent_id=$torrentId", apiKey))
                    return@withTimeoutOrNull null
                }

                // Step 5: Select the correct playable video file
                val selectedCandidate = selectBestPlayableVideoFile(filesArray)
                if (selectedCandidate == null) {
                    Log.w(TAG, sanitize("[TorBox] No valid playable video file found in torrent_id=$torrentId (total files: ${filesArray.length()})", apiKey))
                    return@withTimeoutOrNull null
                }

                Log.d(TAG, sanitize("[TorBox] Selected video file_id=${selectedCandidate.fileId}, name='${selectedCandidate.name}', size=${selectedCandidate.sizeBytes} for torrent_id=$torrentId", apiKey))

                // Step 6: Request official download link via requestdl
                val playableUrl = requestTorBoxDownloadUrl(torrentId, selectedCandidate.fileId, apiKey)
                if (playableUrl.isNullOrBlank() || playableUrl.startsWith("magnet:", ignoreCase = true)) {
                    Log.e(TAG, sanitize("[TorBox] Requestdl failed or returned invalid stream URL for torrent_id=$torrentId, file_id=${selectedCandidate.fileId}", apiKey))
                    return@withTimeoutOrNull null
                }

                Log.d(TAG, sanitize("[TorBox] Successfully resolved playable HTTP URL for torrent_id=$torrentId, file_id=${selectedCandidate.fileId}: $playableUrl", apiKey))

                ResolvedTorrentStream(
                    infoHash = infoHash,
                    playableUrl = playableUrl,
                    title = selectedCandidate.name.ifBlank { title },
                    fileName = selectedCandidate.name,
                    sizeBytes = selectedCandidate.sizeBytes,
                    isHls = playableUrl.contains(".m3u8", ignoreCase = true)
                )
            } catch (e: Exception) {
                Log.e(TAG, sanitize("[TorBox] Exception during TorBox resolution for $infoHash: ${e.message}", apiKey), e)
                null
            }
        }
    }

    private fun checkTorBoxCached(infoHash: String, apiKey: String): Boolean {
        try {
            val url = "$TORBOX_BASE_URL/torrents/checkcached?hash=$infoHash&format=object"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val code = response.code
            val bodyStr = response.body?.string() ?: ""
            response.close()

            Log.d(TAG, sanitize("[TorBox API] checkcached status=$code body=$bodyStr", apiKey))

            if (code in 200..299 && bodyStr.isNotBlank()) {
                val json = JSONObject(bodyStr)
                if (json.optBoolean("success", false)) {
                    val data = json.opt("data")
                    if (data is JSONObject) {
                        val keyLower = infoHash.lowercase()
                        if (data.has(keyLower)) {
                            val valObj = data.optJSONObject(keyLower)
                            return valObj != null || data.has(keyLower)
                        }
                    } else if (data is JSONArray) {
                        for (i in 0 until data.length()) {
                            val elem = data.opt(i)
                            if (elem is String && elem.equals(infoHash, ignoreCase = true)) return true
                            if (elem is JSONObject && elem.optString("hash").equals(infoHash, ignoreCase = true)) return true
                        }
                    }
                }
            } else {
                Log.w(TAG, sanitize("[TorBox API] checkcached returned HTTP $code: $bodyStr", apiKey))
            }
        } catch (e: Exception) {
            Log.e(TAG, sanitize("[TorBox API] checkcached exception: ${e.message}", apiKey))
        }
        return false
    }

    private fun findTorrentInMyList(infoHash: String, apiKey: String): JSONObject? {
        try {
            val url = "$TORBOX_BASE_URL/torrents/mylist?bypass_cache=true"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val code = response.code
            val bodyStr = response.body?.string() ?: ""
            response.close()

            Log.d(TAG, sanitize("[TorBox API] mylist status=$code", apiKey))

            if (code in 200..299 && bodyStr.isNotBlank()) {
                val json = JSONObject(bodyStr)
                if (json.optBoolean("success", false)) {
                    val dataArr = json.optJSONArray("data")
                    if (dataArr != null) {
                        for (i in 0 until dataArr.length()) {
                            val torObj = dataArr.optJSONObject(i) ?: continue
                            val hash = torObj.optString("hash")
                            if (hash.equals(infoHash, ignoreCase = true)) {
                                return torObj
                            }
                        }
                    }
                }
            } else {
                Log.w(TAG, sanitize("[TorBox API] mylist returned HTTP $code: $bodyStr", apiKey))
            }
        } catch (e: Exception) {
            Log.e(TAG, sanitize("[TorBox API] mylist exception: ${e.message}", apiKey))
        }
        return null
    }

    private fun createTorBoxTorrent(infoHash: String, title: String, apiKey: String): Long {
        try {
            val magnetUrl = TorrentUtils.formatMagnetUrl(infoHash, title)
            val formBody = FormBody.Builder()
                .add("magnet", magnetUrl)
                .build()

            val url = "$TORBOX_BASE_URL/torrents/createtorrent"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .post(formBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val code = response.code
            val bodyStr = response.body?.string() ?: ""
            response.close()

            Log.d(TAG, sanitize("[TorBox API] createtorrent status=$code body=$bodyStr", apiKey))

            if (code in 200..299 && bodyStr.isNotBlank()) {
                val json = JSONObject(bodyStr)
                if (json.optBoolean("success", false)) {
                    val dataObj = json.optJSONObject("data")
                    val tid = dataObj?.optLong("torrent_id", -1L)?.takeIf { it != -1L }
                        ?: dataObj?.optLong("id", -1L)
                        ?: -1L
                    if (tid != -1L) return tid
                } else {
                    Log.w(TAG, sanitize("[TorBox API] createtorrent failed: detail=${json.optString("detail")}", apiKey))
                }
            } else {
                Log.w(TAG, sanitize("[TorBox API] createtorrent HTTP $code error: $bodyStr", apiKey))
            }
        } catch (e: Exception) {
            Log.e(TAG, sanitize("[TorBox API] createtorrent exception: ${e.message}", apiKey))
        }
        return -1L
    }

    private suspend fun pollTorrentUntilReady(
        torrentId: Long,
        infoHash: String,
        isCached: Boolean,
        apiKey: String
    ): JSONObject? {
        val maxAttempts = 5
        val pollDelayMs = 2000L

        for (attempt in 1..maxAttempts) {
            val torrentObj = findTorrentInMyList(infoHash, apiKey)
            if (torrentObj != null) {
                val files = torrentObj.optJSONArray("files")
                val state = torrentObj.optString("download_state", "").lowercase()
                val progress = torrentObj.optDouble("progress", 0.0)

                val fileCount = files?.length() ?: 0
                Log.d(TAG, sanitize("[TorBox API] Poll attempt $attempt/$maxAttempts for torrent_id=$torrentId: state='$state', progress=$progress, filesCount=$fileCount", apiKey))

                val isReadyState = isCached ||
                        state.contains("completed") ||
                        state.contains("cached") ||
                        state.contains("seeding") ||
                        state.contains("uploading") ||
                        progress >= 0.99

                if (fileCount > 0 && isReadyState) {
                    return torrentObj
                }
                if (fileCount > 0 && attempt >= 2) {
                    return torrentObj
                }
            } else {
                Log.d(TAG, sanitize("[TorBox API] Poll attempt $attempt/$maxAttempts: torrent_id=$torrentId not visible in mylist yet", apiKey))
            }

            if (attempt < maxAttempts) {
                delay(pollDelayMs)
            }
        }
        return findTorrentInMyList(infoHash, apiKey)
    }

    private fun selectBestPlayableVideoFile(filesArray: JSONArray): TorBoxFileCandidate? {
        val candidates = mutableListOf<TorBoxFileCandidate>()

        for (i in 0 until filesArray.length()) {
            val fileObj = filesArray.optJSONObject(i) ?: continue
            val fileId = fileObj.optLong("id", -1L).takeIf { it != -1L }
                ?: fileObj.optLong("file_id", -1L)
            if (fileId == -1L) continue

            val name = fileObj.optString("short_name").ifBlank { fileObj.optString("name") }
            val size = fileObj.optLong("size", 0L)
            val mimeType = fileObj.optString("mimetype").lowercase()

            val nameLower = name.lowercase()

            // Exclude non-video files
            val isSub = nameLower.endsWith(".srt") || nameLower.endsWith(".ass") || nameLower.endsWith(".vtt") || nameLower.endsWith(".sub") || nameLower.endsWith(".idx")
            val isNfoOrText = nameLower.endsWith(".nfo") || nameLower.endsWith(".txt") || nameLower.endsWith(".url") || nameLower.endsWith(".htm") || nameLower.endsWith(".html") || nameLower.endsWith(".sfv")
            val isImage = nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".png") || nameLower.endsWith(".gif") || nameLower.endsWith(".webp")
            val isArchive = nameLower.endsWith(".zip") || nameLower.endsWith(".rar") || nameLower.endsWith(".7z") || nameLower.endsWith(".tar") || nameLower.endsWith(".gz") || nameLower.endsWith(".iso") || nameLower.endsWith(".exe")

            if (isSub || isNfoOrText || isImage || isArchive) continue

            val isSample = nameLower.contains("sample") || nameLower.contains("trailer")
            val isPreferredFormat = nameLower.endsWith(".mkv") || nameLower.endsWith(".mp4")
            val hasVideoExt = isPreferredFormat || nameLower.endsWith(".avi") || nameLower.endsWith(".webm") ||
                    nameLower.endsWith(".mov") || nameLower.endsWith(".flv") || nameLower.endsWith(".m4v") ||
                    nameLower.endsWith(".ts") || nameLower.endsWith(".wmv") || nameLower.endsWith(".mpg") || nameLower.endsWith(".mpeg")
            val isVideoMime = mimeType.startsWith("video/")

            val isVideo = hasVideoExt || isVideoMime
            if (isVideo) {
                candidates.add(
                    TorBoxFileCandidate(
                        fileId = fileId,
                        name = name,
                        sizeBytes = size,
                        mimeType = mimeType,
                        isPreferredFormat = isPreferredFormat,
                        isVideo = true,
                        isSample = isSample
                    )
                )
            }
        }

        if (candidates.isEmpty()) return null

        val nonSampleCandidates = candidates.filter { !it.isSample }
        val pool = nonSampleCandidates.ifEmpty { candidates }

        return pool.maxWithOrNull(
            compareBy<TorBoxFileCandidate> { it.isPreferredFormat }
                .thenBy { it.sizeBytes }
        )
    }

    private fun requestTorBoxDownloadUrl(
        torrentId: Long,
        fileId: Long,
        apiKey: String
    ): String? {
        try {
            val url = "$TORBOX_BASE_URL/torrents/requestdl?token=$apiKey&torrent_id=$torrentId&file_id=$fileId"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val code = response.code
            val bodyStr = response.body?.string() ?: ""
            response.close()

            Log.d(TAG, sanitize("[TorBox API] requestdl status=$code body=$bodyStr", apiKey))

            if (code in 200..299 && bodyStr.isNotBlank()) {
                val json = JSONObject(bodyStr)
                if (json.optBoolean("success", false)) {
                    val dataElem = json.opt("data")
                    if (dataElem is String && dataElem.startsWith("http")) {
                        return dataElem
                    } else if (dataElem is JSONObject) {
                        val link = dataElem.optString("link").ifBlank { dataElem.optString("url") }
                        if (link.startsWith("http")) return link
                    }
                } else {
                    Log.w(TAG, sanitize("[TorBox API] requestdl failed: detail=${json.optString("detail")}", apiKey))
                }
            } else {
                Log.w(TAG, sanitize("[TorBox API] requestdl returned HTTP $code: $bodyStr", apiKey))
            }
        } catch (e: Exception) {
            Log.e(TAG, sanitize("[TorBox API] requestdl exception: ${e.message}", apiKey))
        }
        return null
    }

    private fun sanitize(msg: String, apiKey: String): String {
        if (apiKey.isBlank()) return msg
        return msg.replace(apiKey, "***")
    }
}

