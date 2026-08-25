package com.example.torrent.provider

import android.util.Log
import com.example.torrent.model.TorrentRelease
import com.example.torrent.protocol.MagnetParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class EztvProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) : TorrentProvider {

    override val id: String = "eztv"
    override val name: String = "EZTV"
    override val isEnabled: Boolean = true

    companion object {
        private const val TAG = "EztvProvider"
        private const val BASE_URL = "https://eztv.re/api/get-torrents"
    }

    override suspend fun search(query: String, identity: MediaIdentity): List<TorrentRelease> = withContext(Dispatchers.IO) {
        val imdbId = identity.imdbId?.trim() ?: ""
        val cleanImdbId = imdbId.removePrefix("tt").trim()

        if (cleanImdbId.isBlank() && identity.title.isBlank()) return@withContext emptyList()

        val url = if (cleanImdbId.isNotBlank()) {
            "$BASE_URL?imdb_id=$cleanImdbId&limit=50"
        } else {
            return@withContext emptyList()
        }

        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Butterfly/1.0")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()

            val body = resp.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val torrents = json.optJSONArray("torrents") ?: return@withContext emptyList()

            val results = mutableListOf<TorrentRelease>()

            for (i in 0 until torrents.length()) {
                val torrent = torrents.getJSONObject(i)
                val releaseTitle = torrent.optString("title", "")
                val magnetUrl = torrent.optString("magnet_url", "")
                val hash = torrent.optString("hash", "").trim()
                val seeders = torrent.optInt("seeds", 0)
                val leechers = torrent.optInt("peers", 0)
                val sizeBytes = torrent.optLong("size_bytes", 0L)
                val seasonNum = torrent.optInt("season", 0)
                val episodeNum = torrent.optInt("episode", 0)

                // Match season and episode if searching for specific episode
                if (identity.season != null && seasonNum != 0 && seasonNum != identity.season) {
                    continue
                }
                if (identity.episode != null && episodeNum != 0 && episodeNum != identity.episode) {
                    continue
                }

                val finalHash = if (hash.isNotBlank()) hash else {
                    MagnetParser.parse(magnetUrl)?.infoHashHex ?: ""
                }
                if (finalHash.isBlank()) continue

                val quality = when {
                    releaseTitle.contains("2160p", ignoreCase = true) || releaseTitle.contains("4K", ignoreCase = true) -> "4K UHD"
                    releaseTitle.contains("1080p", ignoreCase = true) -> "1080p"
                    releaseTitle.contains("720p", ignoreCase = true) -> "720p"
                    releaseTitle.contains("480p", ignoreCase = true) -> "480p"
                    else -> "720p"
                }

                val codec = when {
                    releaseTitle.contains("x265", ignoreCase = true) || releaseTitle.contains("HEVC", ignoreCase = true) -> "x265 HEVC"
                    releaseTitle.contains("x264", ignoreCase = true) || releaseTitle.contains("H.264", ignoreCase = true) -> "x264"
                    else -> ""
                }

                val formattedSize = if (sizeBytes > 0) {
                    val gb = sizeBytes / (1024.0 * 1024.0 * 1024.0)
                    if (gb >= 1.0) String.format("%.2f GB", gb) else String.format("%d MB", sizeBytes / (1024 * 1024))
                } else ""

                results.add(
                    TorrentRelease(
                        title = releaseTitle,
                        infoHash = finalHash,
                        magnetUrl = if (magnetUrl.isNotBlank()) magnetUrl else MagnetParser.buildMagnetUrl(finalHash, releaseTitle),
                        provider = "EZTV",
                        seeders = seeders,
                        leechers = leechers,
                        sizeBytes = sizeBytes,
                        formattedSize = formattedSize,
                        quality = quality,
                        codec = codec,
                        season = if (seasonNum > 0) seasonNum else identity.season,
                        episode = if (episodeNum > 0) episodeNum else identity.episode
                    )
                )
            }

            results
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching from EZTV: ${e.message}")
            emptyList()
        }
    }
}
