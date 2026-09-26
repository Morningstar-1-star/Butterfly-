package com.example.torrent.provider

import android.util.Log
import com.example.torrent.model.TorrentResult
import com.example.torrent.protocol.MagnetParser
import com.example.util.SecureDnsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * YTS (YIFY) Torrent Indexer Provider.
 * High-speed JSON API for official YTS releases (720p, 1080p, 4K BluRay/WEBRip)
 * with multi-mirror resilience and Secure DNS fallback.
 */
class YtsProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .dns(SecureDnsManager.appDns)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
) : TorrentProvider {

    override val id: String = "yts"
    override val name: String = "YTS"
    override val isEnabled: Boolean = true

    companion object {
        private const val TAG = "YtsProvider"
        private val MIRROR_URLS = listOf(
            "https://yts.mx/api/v2/list_movies.json",
            "https://yts.rs/api/v2/list_movies.json",
            "https://yts.do/api/v2/list_movies.json",
            "https://yts.pm/api/v2/list_movies.json",
            "https://yts.lt/api/v2/list_movies.json"
        )
    }

    override suspend fun search(query: String, identity: MediaIdentity): List<TorrentResult> = withContext(Dispatchers.IO) {
        if (identity.mediaType.equals("tv", ignoreCase = true) || identity.mediaType.equals("anime", ignoreCase = true)) {
            // YTS only indexes movies
            return@withContext emptyList()
        }

        val searchTerm = if (!identity.imdbId.isNullOrBlank() && identity.imdbId.startsWith("tt")) {
            identity.imdbId
        } else if (identity.title.isNotBlank()) {
            identity.title
        } else {
            query
        }

        if (searchTerm.isBlank()) return@withContext emptyList()

        val encodedQuery = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8.name())

        for (baseUrl in MIRROR_URLS) {
            try {
                val url = "$baseUrl?query_term=$encodedQuery&limit=15"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) continue

                val body = resp.body?.string() ?: continue
                val json = JSONObject(body)
                val data = json.optJSONObject("data") ?: continue
                val movies = data.optJSONArray("movies") ?: continue

                val results = mutableListOf<TorrentResult>()

                for (i in 0 until movies.length()) {
                    val movie = movies.getJSONObject(i)
                    val movieTitle = movie.optString("title_long", movie.optString("title", identity.title))
                    val movieImdb = movie.optString("imdb_code", "")

                    // If we have an IMDb ID, verify it matches
                    if (!identity.imdbId.isNullOrBlank() && movieImdb.isNotBlank() && !movieImdb.equals(identity.imdbId, ignoreCase = true)) {
                        continue
                    }

                    val torrents = movie.optJSONArray("torrents") ?: continue

                    for (j in 0 until torrents.length()) {
                        val torrent = torrents.getJSONObject(j)
                        val hash = torrent.optString("hash", "").trim()
                        if (hash.isBlank()) continue

                        val quality = torrent.optString("quality", "1080p")
                        val type = torrent.optString("type", "bluray")
                        val seeders = torrent.optInt("seeds", 0)
                        val leechers = torrent.optInt("peers", 0)
                        val sizeFormatted = torrent.optString("size", "")
                        val sizeBytes = torrent.optLong("size_bytes", 0L)
                        val videoCodec = torrent.optString("video_codec", "x264")

                        val releaseTitle = "$movieTitle [$quality] [$type] [YTS]"
                        val magnetUrl = MagnetParser.buildMagnetUrl(hash, releaseTitle)

                        val displayQuality = if (quality.contains("2160", ignoreCase = true)) "4K UHD" else quality

                        results.add(
                            TorrentResult(
                                title = releaseTitle,
                                magnet = magnetUrl,
                                infoHash = hash.lowercase(),
                                size = sizeBytes,
                                formattedSize = sizeFormatted,
                                seeders = seeders,
                                leechers = leechers,
                                source = "YTS",
                                category = "Movies",
                                quality = displayQuality,
                                codec = videoCodec,
                                hdr = if (quality.contains("2160")) "HDR" else "",
                                audioChannels = "5.1 Surround",
                                uploadDate = movie.optString("date_uploaded", "")
                            )
                        )
                    }
                }

                if (results.isNotEmpty()) {
                    return@withContext results
                }
            } catch (e: Exception) {
                Log.d(TAG, "Mirror $baseUrl attempt note: ${e.message}")
            }
        }

        emptyList()
    }
}
