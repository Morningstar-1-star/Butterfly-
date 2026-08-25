package com.example.torrent.provider

import android.util.Log
import com.example.torrent.model.TorrentRelease
import com.example.torrent.protocol.MagnetParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class YtsProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) : TorrentProvider {

    override val id: String = "yts"
    override val name: String = "YTS"
    override val isEnabled: Boolean = true

    companion object {
        private const val TAG = "YtsProvider"
        private const val BASE_URL = "https://yts.mx/api/v2/list_movies.json"
    }

    override suspend fun search(query: String, identity: MediaIdentity): List<TorrentRelease> = withContext(Dispatchers.IO) {
        if (identity.mediaType.equals("tv", ignoreCase = true)) {
            // YTS only has movies
            return@withContext emptyList()
        }

        val searchTerm = if (!identity.imdbId.isNullOrBlank()) {
            identity.imdbId
        } else if (identity.title.isNotBlank()) {
            identity.title
        } else {
            query
        }

        if (searchTerm.isBlank()) return@withContext emptyList()

        try {
            val encodedQuery = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8.name())
            val url = "$BASE_URL?query_term=$encodedQuery&limit=10"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Butterfly/1.0")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()

            val body = resp.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val data = json.optJSONObject("data") ?: return@withContext emptyList()
            val movies = data.optJSONArray("movies") ?: return@withContext emptyList()

            val results = mutableListOf<TorrentRelease>()

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

                    val releaseTitle = "$movieTitle [$quality] [$type] [YTS.MX]"
                    val magnetUrl = MagnetParser.buildMagnetUrl(hash, releaseTitle)

                    val displayQuality = if (quality.contains("2160", ignoreCase = true)) "4K UHD" else quality

                    results.add(
                        TorrentRelease(
                            title = releaseTitle,
                            infoHash = hash,
                            magnetUrl = magnetUrl,
                            provider = "YTS",
                            seeders = seeders,
                            leechers = leechers,
                            sizeBytes = sizeBytes,
                            formattedSize = sizeFormatted,
                            quality = displayQuality,
                            codec = videoCodec,
                            hdr = if (quality.contains("2160")) "HDR" else "",
                            audioChannels = "5.1 Surround"
                        )
                    )
                }
            }

            results
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching from YTS: ${e.message}")
            emptyList()
        }
    }
}
