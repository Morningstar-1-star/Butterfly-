package com.example.torrent.provider

import android.util.Log
import com.example.torrent.model.TorrentRelease
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class TorrentProviderManager(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "TorrentProviderManager"
        private const val TMDB_API_KEY = com.example.util.AppConfig.TMDB_API_KEY

        @Volatile
        private var instance: TorrentProviderManager? = null

        fun getInstance(): TorrentProviderManager {
            return instance ?: synchronized(this) {
                instance ?: TorrentProviderManager().also { instance = it }
            }
        }
    }

    private val providers = listOf<TorrentProvider>(
        TorrentioProvider(client),
        YtsProvider(client),
        EztvProvider(client),
        NyaaProvider(client)
    )

    private val imdbIdCache = ConcurrentHashMap<String, String>()

    suspend fun searchReleases(
        query: String,
        identity: MediaIdentity
    ): List<TorrentRelease> = withContext(Dispatchers.IO) {
        val effectiveIdentity = resolveImdbIdIfNeeded(identity)

        val results = supervisorScope {
            providers.filter { it.isEnabled }.map { provider ->
                async {
                    try {
                        provider.search(query, effectiveIdentity)
                    } catch (e: Exception) {
                        Log.e(TAG, "Provider ${provider.name} failed: ${e.message}")
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
        }

        // Deduplicate by infoHash
        val distinct = LinkedHashMap<String, TorrentRelease>()
        for (rel in results) {
            val key = rel.infoHash.lowercase()
            if (key.isBlank()) continue
            val existing = distinct[key]
            if (existing == null || rel.seeders > existing.seeders) {
                distinct[key] = rel
            }
        }

        // Enrich and sort
        val sortedList = distinct.values.map { enrichRelease(it) }
            .sortedWith(
                compareByDescending<TorrentRelease> { it.seeders > 0 }
                    .thenByDescending { it.qualityScore }
                    .thenByDescending { it.seeders }
            )

        Log.i(TAG, "Found ${sortedList.size} releases for \"${identity.title}\"")
        sortedList
    }

    private suspend fun resolveImdbIdIfNeeded(identity: MediaIdentity): MediaIdentity {
        if (!identity.imdbId.isNullOrBlank() && identity.imdbId.startsWith("tt")) {
            return identity
        }

        val cacheKey = "${identity.mediaType}_${identity.tmdbId}_${identity.title}"
        val cached = imdbIdCache[cacheKey]
        if (cached != null) {
            return identity.copy(imdbId = cached)
        }

        var resolvedImdb: String? = null

        // 1. Try resolving via TMDB ID if available
        if (!identity.tmdbId.isNullOrBlank() && identity.tmdbId.matches(Regex("^\\d+$"))) {
            resolvedImdb = fetchImdbFromTmdb(identity.tmdbId, identity.mediaType ?: "movie")
        }

        // 2. If still null, search TMDB by title
        if (resolvedImdb == null && identity.title.isNotBlank()) {
            resolvedImdb = searchTmdbForImdb(identity.title, identity.year, identity.mediaType ?: "movie")
        }

        // 3. If still null, search Cinemeta Stremio API
        if (resolvedImdb == null && identity.title.isNotBlank()) {
            resolvedImdb = searchCinemetaForImdb(identity.title, identity.mediaType ?: "movie")
        }

        if (resolvedImdb != null) {
            imdbIdCache[cacheKey] = resolvedImdb
            return identity.copy(imdbId = resolvedImdb)
        }

        return identity
    }

    private fun fetchImdbFromTmdb(tmdbId: String, mediaType: String): String? {
        val endpoint = if (mediaType.equals("tv", ignoreCase = true)) "tv" else "movie"
        val url = "https://api.themoviedb.org/3/$endpoint/$tmdbId/external_ids?api_key=$TMDB_API_KEY"

        try {
            val req = Request.Builder().url(url).build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return null

            val body = resp.body?.string() ?: return null
            val json = JSONObject(body)
            val imdb = json.optString("imdb_id", "")
            return if (imdb.isNotBlank() && imdb.startsWith("tt")) imdb else null
        } catch (_: Exception) {
            return null
        }
    }

    private fun searchTmdbForImdb(title: String, year: String?, mediaType: String): String? {
        val endpoint = if (mediaType.equals("tv", ignoreCase = true)) "search/tv" else "search/movie"
        val encTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.name())
        val yearParam = if (!year.isNullOrBlank()) "&year=$year" else ""
        val url = "https://api.themoviedb.org/3/$endpoint?api_key=$TMDB_API_KEY&query=$encTitle$yearParam"

        try {
            val req = Request.Builder().url(url).build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return null

            val body = resp.body?.string() ?: return null
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return null
            if (results.length() == 0) return null

            val first = results.getJSONObject(0)
            val tmdbId = first.optString("id", "")
            if (tmdbId.isBlank()) return null

            return fetchImdbFromTmdb(tmdbId, mediaType)
        } catch (_: Exception) {
            return null
        }
    }

    private fun searchCinemetaForImdb(title: String, mediaType: String): String? {
        val type = if (mediaType.equals("tv", ignoreCase = true)) "series" else "movie"
        val enc = URLEncoder.encode(title, StandardCharsets.UTF_8.name())
        val url = "https://v3-cinemeta.strem.io/catalog/$type/top/search=$enc.json"

        try {
            val req = Request.Builder().url(url).build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return null

            val body = resp.body?.string() ?: return null
            val json = JSONObject(body)
            val metas = json.optJSONArray("metas") ?: return null
            if (metas.length() == 0) return null

            val first = metas.getJSONObject(0)
            val id = first.optString("id", "")
            return if (id.startsWith("tt")) id else null
        } catch (_: Exception) {
            return null
        }
    }

    private fun enrichRelease(release: TorrentRelease): TorrentRelease {
        val text = release.title + " " + release.quality + " " + release.codec

        var quality = release.quality
        if (quality.isBlank() || quality == "1080p") {
            quality = when {
                text.contains("2160p", ignoreCase = true) || text.contains("4K", ignoreCase = true) || text.contains("UHD", ignoreCase = true) -> "4K UHD"
                text.contains("1080p", ignoreCase = true) || text.contains("FHD", ignoreCase = true) -> "1080p"
                text.contains("720p", ignoreCase = true) || text.contains("HD", ignoreCase = true) -> "720p"
                text.contains("480p", ignoreCase = true) -> "480p"
                else -> "1080p"
            }
        }

        var codec = release.codec
        if (codec.isBlank()) {
            codec = when {
                text.contains("x265", ignoreCase = true) || text.contains("HEVC", ignoreCase = true) || text.contains("H.265", ignoreCase = true) -> "x265 HEVC"
                text.contains("AV1", ignoreCase = true) -> "AV1"
                text.contains("x264", ignoreCase = true) || text.contains("H.264", ignoreCase = true) || text.contains("AVC", ignoreCase = true) -> "x264"
                else -> ""
            }
        }

        var hdr = release.hdr
        if (hdr.isBlank()) {
            hdr = when {
                text.contains("DV", ignoreCase = true) && text.contains("HDR", ignoreCase = true) -> "Dolby Vision + HDR"
                text.contains("Dolby Vision", ignoreCase = true) || text.contains("DV", ignoreCase = true) -> "Dolby Vision"
                text.contains("HDR10+", ignoreCase = true) -> "HDR10+"
                text.contains("HDR", ignoreCase = true) -> "HDR"
                else -> ""
            }
        }

        var audio = release.audioChannels
        if (audio.isBlank()) {
            audio = when {
                text.contains("Atmos", ignoreCase = true) -> "Dolby Atmos"
                text.contains("TrueHD", ignoreCase = true) -> "TrueHD"
                text.contains("DTS-HD", ignoreCase = true) || text.contains("DTS", ignoreCase = true) -> "DTS-HD"
                text.contains("7.1", ignoreCase = true) -> "7.1"
                text.contains("5.1", ignoreCase = true) || text.contains("DD5.1", ignoreCase = true) || text.contains("AC3", ignoreCase = true) || text.contains("EAC3", ignoreCase = true) -> "5.1 Surround"
                text.contains("AAC", ignoreCase = true) -> "AAC"
                else -> ""
            }
        }

        return release.copy(
            quality = quality,
            codec = codec,
            hdr = hdr,
            audioChannels = audio
        )
    }
}
