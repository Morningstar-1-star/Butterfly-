package com.example.util

import android.util.Log
import com.example.model.MediaIdentity
import com.example.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object MediaIdResolver {

    private const val TAG = "MediaIdResolver"
    private const val TMDB_API_KEY = "a07e22bc18f5cb106bfe4cc1f83ad8ed"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    // In-memory cache to prevent redundant TMDB network lookups
    private val identityCache = ConcurrentHashMap<String, MediaIdentity>()

    suspend fun resolve(rawInput: String): MediaIdentity = withContext(Dispatchers.IO) {
        val clean = rawInput.trim()
        if (clean.isBlank()) {
            return@withContext MediaIdentity(rawQueryOrUrl = rawInput)
        }

        // Return from cache if already resolved
        identityCache[clean]?.let { cached ->
            Log.d(TAG, "[Cache Hit] Resolved '$clean' -> imdbId=${cached.imdbId}, tmdbId=${cached.tmdbId}, type=${cached.mediaType}")
            return@withContext cached
        }

        // 1. URLs or Magnets
        if (clean.startsWith("http://", ignoreCase = true) ||
            clean.startsWith("https://", ignoreCase = true) ||
            clean.startsWith("magnet:", ignoreCase = true)
        ) {
            val identity = MediaIdentity(rawQueryOrUrl = clean, mediaType = MediaType.VIDEO)
            identityCache[clean] = identity
            return@withContext identity
        }

        // 2. IMDb Format: tt1234567 or tt1234567:1:2 or tt1234567_1_2
        if (clean.contains("tt", ignoreCase = true)) {
            val imdbRegex = Regex("""(tt\d{6,10})(?:[:_sS](\d+))?(?:[:_eE](\d+))?""", RegexOption.IGNORE_CASE)
            val match = imdbRegex.find(clean)
            if (match != null) {
                val imdbId = match.groupValues[1]
                val season = match.groupValues.getOrNull(2)?.toIntOrNull()
                val episode = match.groupValues.getOrNull(3)?.toIntOrNull()
                val isTv = season != null || clean.contains("series", ignoreCase = true) || clean.contains("tv", ignoreCase = true)

                // Fetch TMDB ID from IMDb ID
                val tmdbPair = resolveTmdbFromImdb(imdbId)
                val resolvedType = tmdbPair?.second ?: if (isTv) MediaType.TV else MediaType.MOVIE

                val identity = MediaIdentity(
                    tmdbId = tmdbPair?.first,
                    imdbId = imdbId,
                    mediaType = resolvedType,
                    season = season ?: if (resolvedType == MediaType.TV) 1 else null,
                    episode = episode ?: if (resolvedType == MediaType.TV) 1 else null,
                    rawQueryOrUrl = clean
                )
                Log.d(TAG, "[IMDb Match] Resolved '$clean' -> imdbId=${identity.imdbId}, tmdbId=${identity.tmdbId}, type=${identity.mediaType}")
                identityCache[clean] = identity
                return@withContext identity
            }
        }

        // 3. TMDB Prefix Format: movie_550, tv_1399_1_2, tv_1399_s1_e2, tmdb_1399
        if (clean.startsWith("movie_", ignoreCase = true) ||
            clean.startsWith("tv_", ignoreCase = true) ||
            clean.startsWith("tmdb_", ignoreCase = true)
        ) {
            val parts = clean.split("_")
            val isTv = clean.startsWith("tv_", ignoreCase = true)
            val type = if (isTv) MediaType.TV else MediaType.MOVIE
            val tmdbId = parts.getOrNull(1)?.filter { it.isDigit() }
            
            var season: Int? = null
            var episode: Int? = null

            for (p in parts.drop(2)) {
                val lowerP = p.lowercase()
                if (lowerP.startsWith("s") && lowerP.length > 1 && lowerP.drop(1).all { it.isDigit() }) {
                    season = lowerP.drop(1).toIntOrNull()
                } else if (lowerP.startsWith("e") && lowerP.length > 1 && lowerP.drop(1).all { it.isDigit() }) {
                    episode = lowerP.drop(1).toIntOrNull()
                } else if (p.all { it.isDigit() }) {
                    if (season == null) season = p.toIntOrNull()
                    else if (episode == null) episode = p.toIntOrNull()
                }
            }

            if (!tmdbId.isNullOrEmpty()) {
                val imdbId = resolveImdbFromTmdb(tmdbId, type)
                val identity = MediaIdentity(
                    tmdbId = tmdbId,
                    imdbId = imdbId,
                    mediaType = type,
                    season = season ?: if (isTv) 1 else null,
                    episode = episode ?: if (isTv) 1 else null,
                    rawQueryOrUrl = clean
                )
                Log.d(TAG, "[TMDB Prefix Match] Resolved '$clean' -> imdbId=${identity.imdbId}, tmdbId=${identity.tmdbId}, type=${identity.mediaType}, S${identity.season}E${identity.episode}")
                identityCache[clean] = identity
                return@withContext identity
            }
        }

        // 4. Pure numeric TMDB ID: e.g. "969681" or "550"
        if (clean.all { it.isDigit() }) {
            val tmdbId = clean
            val (imdbId, type) = resolveTmdbDetails(tmdbId)
            val identity = MediaIdentity(
                tmdbId = tmdbId,
                imdbId = imdbId,
                mediaType = type,
                rawQueryOrUrl = clean
            )
            Log.d(TAG, "[Numeric TMDB Match] Resolved '$clean' -> imdbId=${identity.imdbId}, tmdbId=${identity.tmdbId}, type=${identity.mediaType}")
            identityCache[clean] = identity
            return@withContext identity
        }

        // 5. Title / Query Search
        val searchedIdentity = searchTmdbForTitle(clean)
        if (searchedIdentity != null) {
            Log.d(TAG, "[Title Search Match] Resolved '$clean' -> imdbId=${searchedIdentity.imdbId}, tmdbId=${searchedIdentity.tmdbId}")
            identityCache[clean] = searchedIdentity
            return@withContext searchedIdentity
        }

        // 6. Unresolved Raw Input (No fake IMDb ID created)
        val fallback = MediaIdentity(rawQueryOrUrl = clean)
        Log.w(TAG, "[Unresolved Input] Could not resolve IMDb/TMDB for '$clean'. No fake IDs fabricated.")
        identityCache[clean] = fallback
        return@withContext fallback
    }

    private fun resolveTmdbFromImdb(imdbId: String): Pair<String, MediaType>? {
        return try {
            val url = "https://api.themoviedb.org/3/find/$imdbId?api_key=$TMDB_API_KEY&external_source=imdb_id"
            val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            val movieResults = json.optJSONArray("movie_results")
            if (movieResults != null && movieResults.length() > 0) {
                val item = movieResults.getJSONObject(0)
                val id = item.optInt("id", -1)
                if (id != -1) return Pair(id.toString(), MediaType.MOVIE)
            }

            val tvResults = json.optJSONArray("tv_results")
            if (tvResults != null && tvResults.length() > 0) {
                val item = tvResults.getJSONObject(0)
                val id = item.optInt("id", -1)
                if (id != -1) return Pair(id.toString(), MediaType.TV)
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving TMDB from IMDb $imdbId: ${e.message}")
            null
        }
    }

    private fun resolveTmdbDetails(tmdbId: String): Pair<String?, MediaType> {
        try {
            // Try Movie endpoint first with append_to_response
            val movieUrl = "https://api.themoviedb.org/3/movie/$tmdbId?api_key=$TMDB_API_KEY&append_to_response=external_ids"
            val req1 = Request.Builder().url(movieUrl).header("User-Agent", "Mozilla/5.0").build()
            val resp1 = httpClient.newCall(req1).execute()
            if (resp1.isSuccessful) {
                val body = resp1.body?.string() ?: ""
                val json = JSONObject(body)
                val extIds = json.optJSONObject("external_ids")
                val imdbId = extIds?.optString("imdb_id", null)?.takeIf { it.startsWith("tt") }
                return Pair(imdbId, MediaType.MOVIE)
            }

            // Fallback to TV endpoint
            val tvUrl = "https://api.themoviedb.org/3/tv/$tmdbId?api_key=$TMDB_API_KEY&append_to_response=external_ids"
            val req2 = Request.Builder().url(tvUrl).header("User-Agent", "Mozilla/5.0").build()
            val resp2 = httpClient.newCall(req2).execute()
            if (resp2.isSuccessful) {
                val body = resp2.body?.string() ?: ""
                val json = JSONObject(body)
                val extIds = json.optJSONObject("external_ids")
                val imdbId = extIds?.optString("imdb_id", null)?.takeIf { it.startsWith("tt") }
                return Pair(imdbId, MediaType.TV)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving TMDB details for $tmdbId: ${e.message}")
        }
        return Pair(null, MediaType.MOVIE)
    }

    private fun resolveImdbFromTmdb(tmdbId: String, type: MediaType): String? {
        return try {
            val endpoint = if (type == MediaType.TV) "tv" else "movie"
            val url = "https://api.themoviedb.org/3/$endpoint/$tmdbId/external_ids?api_key=$TMDB_API_KEY"
            val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val imdbId = json.optString("imdb_id", null)
            if (!imdbId.isNullOrBlank() && imdbId.startsWith("tt")) imdbId else null
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving IMDb from TMDB $tmdbId: ${e.message}")
            null
        }
    }

    private fun searchTmdbForTitle(title: String): MediaIdentity? {
        return try {
            var parseSeason: Int? = null
            var parseEpisode: Int? = null

            val seRegex = Regex("(?i)[_\\s]s(\\d+)[_\\s]?e(\\d+)")
            val match = seRegex.find(title)
            val titleNoSe = if (match != null) {
                parseSeason = match.groupValues[1].toIntOrNull()
                parseEpisode = match.groupValues[2].toIntOrNull()
                title.replace(seRegex, "")
            } else {
                title
            }

            val cleanTitle = TMDBHelper.cleanTitleForSearch(titleNoSe)
            val encoded = URLEncoder.encode(cleanTitle, "UTF-8")
            val url = "https://api.themoviedb.org/3/search/multi?api_key=$TMDB_API_KEY&query=$encoded"
            val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return null

            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val mediaTypeStr = item.optString("media_type")
                if (mediaTypeStr == "movie" || mediaTypeStr == "tv") {
                    val tmdbId = item.optInt("id", -1).toString()
                    if (tmdbId != "-1") {
                        val type = if (mediaTypeStr == "tv") MediaType.TV else MediaType.MOVIE
                        val imdbId = resolveImdbFromTmdb(tmdbId, type)
                        return MediaIdentity(
                            tmdbId = tmdbId,
                            imdbId = imdbId,
                            mediaType = type,
                            season = parseSeason ?: if (type == MediaType.TV) 1 else null,
                            episode = parseEpisode ?: if (type == MediaType.TV) 1 else null,
                            rawQueryOrUrl = title
                        )
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error searching TMDB for title '$title': ${e.message}")
            null
        }
    }
}
