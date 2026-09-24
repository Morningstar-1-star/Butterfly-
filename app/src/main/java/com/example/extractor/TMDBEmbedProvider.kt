package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.extractor.tmdbembed.TMDBEmbedConfig
import com.example.extractor.tmdbembed.TMDBEmbedExtractorEngine
import com.example.extractor.tmdbembed.TMDBEmbedSource
import com.example.extractor.tmdbembed.TMDBMediaRequest
import com.example.model.CaptionOption
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import com.example.model.StreamData
import com.example.model.VideoItem
import com.example.util.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object TMDBEmbedProvider {

    private const val TAG = "TMDBEmbedProvider"
    const val PROVIDER_ID = "tmdb_embed"
    const val DISPLAY_NAME = "TMDB Embed"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    suspend fun getHome(
        page: Int = 1,
        limit: Int = 25,
        specificSource: TMDBEmbedSource? = null,
        context: Context? = null
    ): List<VideoItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<VideoItem>()
        try {
            val apiKey = AppConfig.TMDB_API_KEY
            val url = "https://api.themoviedb.org/3/trending/all/day?api_key=$apiKey&page=$page"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val activeSource = specificSource ?: (context?.let { TMDBEmbedConfig.getDefaultSource(it) } ?: TMDBEmbedSource.fromId(TMDBEmbedConfig.cachedDefaultSourceName) ?: TMDBEmbedSource.VIXSRC)
            val currentProviderId = if (specificSource != null) "tmdb_${specificSource.id}" else PROVIDER_ID
            val sourceName = activeSource.displayName

            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string()
            if (!body.isNullOrBlank()) {
                val json = JSONObject(body)
                val results = json.optJSONArray("results")
                if (results != null) {
                    for (i in 0 until results.length()) {
                        val obj = results.optJSONObject(i) ?: continue
                        val mediaType = obj.optString("media_type", "movie")
                        if (mediaType != "movie" && mediaType != "tv") continue
                        val tmdbId = obj.optInt("id")
                        val title = obj.optString("title", obj.optString("name", "Cinema Release"))
                        val posterPath = obj.optString("poster_path", "")
                        val backdropPath = obj.optString("backdrop_path", "")
                        val rating = obj.optDouble("vote_average", 0.0)
                        val year = (obj.optString("release_date", obj.optString("first_air_date", ""))).take(4)
                        val overview = obj.optString("overview", "")

                        val thumbUrl = when {
                            backdropPath.isNotBlank() && backdropPath != "null" -> "https://image.tmdb.org/t/p/w780$backdropPath"
                            posterPath.isNotBlank() && posterPath != "null" -> "https://image.tmdb.org/t/p/w500$posterPath"
                            else -> "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=800"
                        }

                        val itemId = "$currentProviderId:$mediaType:$tmdbId"
                        val uploader = if (year.isNotBlank()) "$sourceName • $year" else sourceName

                        items.add(
                            VideoItem(
                                id = itemId,
                                title = title,
                                uploaderName = uploader,
                                durationSeconds = -1L,
                                viewCount = (obj.optInt("vote_count", 0) * 100L).coerceAtLeast(1000L),
                                uploadDate = year,
                                thumbnailUrl = thumbUrl,
                                description = overview,
                                tags = listOf(sourceName, "TMDB", if (mediaType == "tv") "series" else "movie", "cinema", "hls"),
                                providerId = currentProviderId
                            )
                        )
                        if (items.size >= limit) break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load TMDB Embed home feed: ${e.message}", e)
        }
        items
    }

    suspend fun search(
        query: String,
        limit: Int = 20,
        page: Int = 1,
        specificSource: TMDBEmbedSource? = null,
        context: Context? = null
    ): List<VideoItem> = withContext(Dispatchers.IO) {
        val clean = query.replace("$PROVIDER_ID:", "", ignoreCase = true)
            .replace("tmdb_embed:", "", ignoreCase = true)
            .replace("tmdb:", "", ignoreCase = true)
            .let { q ->
                var res = q
                for (s in TMDBEmbedSource.allSources) {
                    res = res.replace("tmdb_${s.id}:", "", ignoreCase = true)
                }
                res
            }
            .trim()
        if (clean.isBlank()) return@withContext emptyList()

        val activeSource = specificSource ?: (context?.let { TMDBEmbedConfig.getDefaultSource(it) } ?: TMDBEmbedSource.fromId(TMDBEmbedConfig.cachedDefaultSourceName) ?: TMDBEmbedSource.VIXSRC)
        val currentProviderId = if (specificSource != null) "tmdb_${specificSource.id}" else PROVIDER_ID
        val sourceName = activeSource.displayName

        val items = mutableListOf<VideoItem>()
        try {
            val apiKey = AppConfig.TMDB_API_KEY
            val encodedQuery = URLEncoder.encode(clean, "UTF-8")
            val url = "https://api.themoviedb.org/3/search/multi?api_key=$apiKey&query=$encodedQuery&page=$page"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string()
            if (!body.isNullOrBlank()) {
                val json = JSONObject(body)
                val results = json.optJSONArray("results")
                if (results != null) {
                    for (i in 0 until results.length()) {
                        val obj = results.optJSONObject(i) ?: continue
                        val mediaType = obj.optString("media_type", "movie")
                        if (mediaType != "movie" && mediaType != "tv") continue

                        val tmdbId = obj.optInt("id")
                        val title = obj.optString("title", obj.optString("name", clean))
                        val posterPath = obj.optString("poster_path", "")
                        val backdropPath = obj.optString("backdrop_path", "")
                        val rating = obj.optDouble("vote_average", 0.0)
                        val year = (obj.optString("release_date", obj.optString("first_air_date", ""))).take(4)
                        val overview = obj.optString("overview", "")

                        val thumbUrl = when {
                            backdropPath.isNotBlank() && backdropPath != "null" -> "https://image.tmdb.org/t/p/w780$backdropPath"
                            posterPath.isNotBlank() && posterPath != "null" -> "https://image.tmdb.org/t/p/w500$posterPath"
                            else -> "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=800"
                        }

                        val itemId = "$currentProviderId:$mediaType:$tmdbId"
                        val uploader = if (year.isNotBlank()) "$sourceName • $year" else sourceName

                        items.add(
                            VideoItem(
                                id = itemId,
                                title = title,
                                uploaderName = uploader,
                                durationSeconds = -1L,
                                viewCount = (obj.optInt("vote_count", 0) * 100L).coerceAtLeast(1000L),
                                uploadDate = year,
                                thumbnailUrl = thumbUrl,
                                description = overview,
                                tags = listOf(sourceName, "TMDB", if (mediaType == "tv") "series" else "movie", "cinema", "hls"),
                                providerId = currentProviderId
                            )
                        )
                        if (items.size >= limit) break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for TMDB Embed query '$clean': ${e.message}", e)
        }
        items
    }

    suspend fun getStreamData(urlOrId: String, context: Context?, specificSource: TMDBEmbedSource? = null): StreamData? = withContext(Dispatchers.IO) {
        val appContext = context ?: return@withContext null
        try {
            // Parse TMDB ID and media info
            // Formats:
            // "tmdb_vixsrc:movie:550"
            // "tmdb_embed:movie:550"
            // "tmdb_embed:tv:1399:1:1"
            // "550"
            var mediaType = "movie"
            var tmdbId = ""
            var season = 1
            var episode = 1

            var detectedSource = specificSource
            if (detectedSource == null) {
                for (s in TMDBEmbedSource.allSources) {
                    if (urlOrId.startsWith("tmdb_${s.id}:", ignoreCase = true)) {
                        detectedSource = s
                        break
                    }
                }
            }

            var clean = urlOrId
            for (s in TMDBEmbedSource.allSources) {
                clean = clean.removePrefix("tmdb_${s.id}:")
            }
            clean = clean.removePrefix("tmdb_embed:").removePrefix("tmdb:")
            val parts = clean.split(":")

            when {
                parts.size >= 4 && parts[0] == "tv" -> {
                    mediaType = "tv"
                    tmdbId = parts[1]
                    season = parts[2].toIntOrNull() ?: 1
                    episode = parts[3].toIntOrNull() ?: 1
                }
                parts.size >= 2 && (parts[0] == "movie" || parts[0] == "tv") -> {
                    mediaType = parts[0]
                    tmdbId = parts[1]
                    if (parts.size >= 4) {
                        season = parts[2].toIntOrNull() ?: 1
                        episode = parts[3].toIntOrNull() ?: 1
                    }
                }
                parts.size == 1 && parts[0].all { it.isDigit() } -> {
                    tmdbId = parts[0]
                }
                else -> {
                    val numMatch = Regex("""\b(\d{3,8})\b""").find(urlOrId)
                    if (numMatch != null) {
                        tmdbId = numMatch.groupValues[1]
                        if (urlOrId.contains("/tv/") || urlOrId.contains("tv")) mediaType = "tv"
                    }
                }
            }

            if (tmdbId.isBlank()) {
                Log.w(TAG, "Cannot extract TMDB ID from $urlOrId")
                return@withContext null
            }

            // Fetch TMDB metadata
            val apiKey = AppConfig.TMDB_API_KEY
            val tmdbUrl = "https://api.themoviedb.org/3/$mediaType/$tmdbId?api_key=$apiKey&append_to_response=external_ids"
            val tmdbReq = Request.Builder()
                .url(tmdbUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            var title = "TMDB Media ($tmdbId)"
            var year = ""
            var backdropUrl: String? = null
            var overview = ""
            var imdbId: String? = null

            try {
                httpClient.newCall(tmdbReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            val json = JSONObject(body)
                            title = json.optString("title", json.optString("name", title))
                            year = (json.optString("release_date", json.optString("first_air_date", ""))).take(4)
                            val bPath = json.optString("backdrop_path", "")
                            if (bPath.isNotBlank() && bPath != "null") {
                                backdropUrl = "https://image.tmdb.org/t/p/w1280$bPath"
                            }
                            overview = json.optString("overview", "")
                            val ext = json.optJSONObject("external_ids")
                            if (ext != null) {
                                imdbId = ext.optString("imdb_id", null)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "TMDB metadata fetch failed: ${e.message}")
            }

            val request = TMDBMediaRequest(
                tmdbId = tmdbId,
                mediaType = mediaType,
                season = season,
                episode = episode,
                title = title,
                year = year,
                imdbId = imdbId
            )

            // Resolve streams through TMDBEmbedExtractorEngine
            val streamOptions = TMDBEmbedExtractorEngine.resolveStreamOptions(
                context = appContext,
                request = request,
                specificSource = detectedSource
            )
            if (streamOptions.isEmpty()) {
                Log.w(TAG, "No playable streams found for $title across TMDB Embed sources")
                return@withContext null
            }

            val primaryOption = streamOptions.first()
            val allCaptions = mutableListOf<CaptionOption>()
            for (opt in streamOptions) {
                for (cap in opt.subtitles) {
                    if (allCaptions.none { it.url == cap.url }) {
                        allCaptions.add(cap)
                    }
                }
            }

            val effectiveProviderId = if (detectedSource != null) "tmdb_${detectedSource.id}" else PROVIDER_ID
            val sourceLabel = if (primaryOption.detectedSourceName.isNotBlank()) primaryOption.detectedSourceName else (detectedSource?.displayName ?: "TMDB Embed")

            StreamData(
                videoId = urlOrId,
                videoUrl = primaryOption.videoUrl ?: "",
                title = if (mediaType == "tv") "$title S${season}E${episode}" else title,
                channelName = sourceLabel,
                thumbnailUrl = backdropUrl,
                description = overview,
                availableStreamOptions = streamOptions,
                selectedStreamOption = primaryOption,
                hlsUrl = if (primaryOption.format.equals("hls", ignoreCase = true)) primaryOption.videoUrl else null,
                captionOptions = allCaptions,
                providerId = effectiveProviderId,
                providerType = ProviderType.TMDB_EMBED,
                headers = primaryOption.headers,
                tags = listOf(sourceLabel, "TMDB", mediaType, "cinema")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in getStreamData: ${e.message}", e)
            null
        }
    }
}
