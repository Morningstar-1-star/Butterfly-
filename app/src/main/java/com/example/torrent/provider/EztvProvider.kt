package com.example.torrent.provider

import android.util.Log
import com.example.torrent.model.TorrentResult
import com.example.torrent.protocol.MagnetParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * EZTV TV-Show Torrent Indexer Provider.
 * Queries fast mirror endpoints for verified TV episodes and season packs.
 */
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
        private val MIRROR_URLS = listOf(
            "https://eztvx.xyz/api/get-torrents",
            "https://eztv.wf/api/get-torrents",
            "https://eztv.tf/api/get-torrents",
            "https://eztv1.unblockit.ink/api/get-torrents",
            "https://eztv.yt/api/get-torrents"
        )
    }

    override suspend fun search(query: String, identity: MediaIdentity): List<TorrentResult> = withContext(Dispatchers.IO) {
        val imdbId = identity.imdbId?.trim() ?: ""
        val cleanImdbId = imdbId.removePrefix("tt").trim()

        if (cleanImdbId.isBlank() && identity.title.isBlank()) return@withContext emptyList()
        if (cleanImdbId.isBlank()) return@withContext emptyList()

        for (baseUrl in MIRROR_URLS) {
            val url = "$baseUrl?imdb_id=$cleanImdbId&limit=50"
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .build()

                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) continue

                val body = resp.body?.string() ?: continue
                val json = JSONObject(body)
                val torrents = json.optJSONArray("torrents") ?: continue
                if (torrents.length() == 0) continue

                val results = mutableListOf<TorrentResult>()

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
                        if (gb >= 1.0) String.format(Locale.US, "%.2f GB", gb) else String.format(Locale.US, "%d MB", sizeBytes / (1024 * 1024))
                    } else ""

                    results.add(
                        TorrentResult(
                            title = releaseTitle,
                            magnet = if (magnetUrl.isNotBlank()) magnetUrl else MagnetParser.buildMagnetUrl(finalHash, releaseTitle),
                            infoHash = finalHash.lowercase(),
                            size = sizeBytes,
                            formattedSize = formattedSize,
                            seeders = seeders,
                            leechers = leechers,
                            source = "EZTV",
                            category = "TV",
                            quality = quality,
                            codec = codec,
                            season = if (seasonNum > 0) seasonNum else identity.season,
                            episode = if (episodeNum > 0) episodeNum else identity.episode
                        )
                    )
                }

                if (results.isNotEmpty()) {
                    Log.i(TAG, "EZTV search returned ${results.size} items from mirror $baseUrl")
                    return@withContext results
                }
            } catch (e: Exception) {
                Log.w(TAG, "Mirror $baseUrl failed: ${e.message}")
            }
        }
        emptyList()
    }
}
