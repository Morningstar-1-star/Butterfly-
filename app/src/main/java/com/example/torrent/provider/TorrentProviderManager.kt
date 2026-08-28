package com.example.torrent.provider

import android.util.Log
import com.example.torrent.engine.TorrentSearchEngine
import com.example.torrent.model.TorrentRelease
import com.example.torrent.model.TorrentResult
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
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "TorrentProviderManager"
        private val TMDB_API_KEY get() = com.example.util.AppConfig.TMDB_API_KEY

        @Volatile
        private var instance: TorrentProviderManager? = null

        fun getInstance(): TorrentProviderManager {
            return instance ?: synchronized(this) {
                instance ?: TorrentProviderManager().also { instance = it }
            }
        }
    }

    private val searchEngine = TorrentSearchEngine(client)
    private val imdbIdCache = ConcurrentHashMap<String, String>()

    val providers: List<TorrentProvider>
        get() = searchEngine.getAllProviders()

    suspend fun searchTorrents(
        query: String,
        identity: MediaIdentity
    ): List<TorrentResult> = withContext(Dispatchers.IO) {
        val effectiveIdentity = resolveImdbIdIfNeeded(identity)
        searchEngine.search(query, effectiveIdentity)
    }

    suspend fun searchReleases(
        query: String,
        identity: MediaIdentity
    ): List<TorrentRelease> = withContext(Dispatchers.IO) {
        val effectiveIdentity = resolveImdbIdIfNeeded(identity)
        val results = searchEngine.search(query, effectiveIdentity)
        results.map { it.toTorrentRelease() }
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
}
