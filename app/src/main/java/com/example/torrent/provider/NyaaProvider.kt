package com.example.torrent.provider

import android.util.Log
import com.example.torrent.model.TorrentRelease
import com.example.torrent.protocol.MagnetParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class NyaaProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) : TorrentProvider {

    override val id: String = "nyaa"
    override val name: String = "Nyaa / AnimeTosho"
    override val isEnabled: Boolean = true

    companion object {
        private const val TAG = "NyaaProvider"
        private const val BASE_URL = "https://animetosho.org/api/v1/search"
    }

    override suspend fun search(query: String, identity: MediaIdentity): List<TorrentRelease> = withContext(Dispatchers.IO) {
        val titleQuery = if (identity.title.isNotBlank()) {
            var q = identity.title
            if (identity.episode != null) {
                val epStr = String.format("%02d", identity.episode)
                q = "$q $epStr"
            }
            q
        } else {
            query
        }

        if (titleQuery.isBlank()) return@withContext emptyList()

        try {
            val enc = URLEncoder.encode(titleQuery, StandardCharsets.UTF_8.name())
            val url = "$BASE_URL?q=$enc"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Butterfly/1.0")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()

            val body = resp.body?.string() ?: return@withContext emptyList()
            val array = JSONArray(body)
            val results = mutableListOf<TorrentRelease>()

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val releaseTitle = item.optString("title", "")
                val infoHash = item.optString("info_hash", "").trim()
                val magnetUrl = item.optString("magnet_uri", "")
                val seeders = item.optInt("seeders", 0)
                val leechers = item.optInt("leechers", 0)
                val totalBytes = item.optLong("total_size", 0L)

                val finalHash = if (infoHash.isNotBlank()) infoHash else {
                    MagnetParser.parse(magnetUrl)?.infoHashHex ?: ""
                }
                if (finalHash.isBlank()) continue

                val quality = when {
                    releaseTitle.contains("1080p", ignoreCase = true) -> "1080p"
                    releaseTitle.contains("720p", ignoreCase = true) -> "720p"
                    releaseTitle.contains("480p", ignoreCase = true) -> "480p"
                    releaseTitle.contains("2160p", ignoreCase = true) || releaseTitle.contains("4K", ignoreCase = true) -> "4K UHD"
                    else -> "1080p"
                }

                val codec = when {
                    releaseTitle.contains("x265", ignoreCase = true) || releaseTitle.contains("HEVC", ignoreCase = true) -> "x265 HEVC"
                    releaseTitle.contains("AV1", ignoreCase = true) -> "AV1"
                    releaseTitle.contains("x264", ignoreCase = true) || releaseTitle.contains("H.264", ignoreCase = true) -> "x264"
                    else -> ""
                }

                val formattedSize = if (totalBytes > 0) {
                    val gb = totalBytes / (1024.0 * 1024.0 * 1024.0)
                    if (gb >= 1.0) String.format("%.2f GB", gb) else String.format("%d MB", totalBytes / (1024 * 1024))
                } else ""

                results.add(
                    TorrentRelease(
                        title = releaseTitle,
                        infoHash = finalHash,
                        magnetUrl = if (magnetUrl.isNotBlank()) magnetUrl else MagnetParser.buildMagnetUrl(finalHash, releaseTitle),
                        provider = "Nyaa",
                        seeders = seeders,
                        leechers = leechers,
                        sizeBytes = totalBytes,
                        formattedSize = formattedSize,
                        quality = quality,
                        codec = codec,
                        hdr = if (releaseTitle.contains("HDR", ignoreCase = true)) "HDR" else "",
                        audioChannels = "Dual Audio / Japanese",
                        season = identity.season,
                        episode = identity.episode
                    )
                )
            }

            results
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching from Nyaa/AnimeTosho: ${e.message}")
            emptyList()
        }
    }
}
