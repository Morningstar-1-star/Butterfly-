package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.extractor.nuvio.NuvioProviderRepository
import com.example.model.VideoItem
import com.example.util.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object NuvioProvider {

    private const val TAG = "NuvioProvider"
    const val PROVIDER_ID = "nuvio"
    const val DISPLAY_NAME = "Nuvio"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    suspend fun getHome(
        page: Int = 1,
        limit: Int = 25,
        specificProviderId: String? = null,
        context: Context? = null
    ): List<VideoItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<VideoItem>()
        val currentProviderId = if (!specificProviderId.isNullOrBlank()) {
            if (specificProviderId.startsWith("nuvio_")) specificProviderId else "nuvio_$specificProviderId"
        } else {
            PROVIDER_ID
        }

        val rawId = currentProviderId.removePrefix("nuvio_")
        val defItem = NuvioProviderRepository.ALL_OFFICIAL_PROVIDERS.find { it.id.equals(rawId, ignoreCase = true) }
        val providerDisplayName = defItem?.name ?: "Nuvio ($rawId)"

        try {
            val apiKey = AppConfig.TMDB_API_KEY
            val url = "https://api.themoviedb.org/3/trending/all/day?api_key=$apiKey&page=$page"
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
                        val title = obj.optString("title", obj.optString("name", "Cinema Release"))
                        val posterPath = obj.optString("poster_path", "")
                        val backdropPath = obj.optString("backdrop_path", "")
                        val year = (obj.optString("release_date", obj.optString("first_air_date", ""))).take(4)
                        val overview = obj.optString("overview", "")

                        val thumbUrl = when {
                            backdropPath.isNotBlank() && backdropPath != "null" -> "https://image.tmdb.org/t/p/w780$backdropPath"
                            posterPath.isNotBlank() && posterPath != "null" -> "https://image.tmdb.org/t/p/w500$posterPath"
                            else -> "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=800"
                        }

                        val itemId = "$currentProviderId:$mediaType:$tmdbId"
                        val uploader = if (year.isNotBlank()) "$providerDisplayName • $year" else providerDisplayName

                        items.add(
                            VideoItem(
                                id = itemId,
                                title = title,
                                uploaderName = uploader,
                                uploaderAvatarUrl = defItem?.logo?.takeIf { it.isNotBlank() },
                                durationSeconds = -1L,
                                viewCount = (obj.optInt("vote_count", 0) * 100L).coerceAtLeast(1000L),
                                uploadDate = year,
                                thumbnailUrl = thumbUrl,
                                description = overview,
                                tags = listOf(providerDisplayName, "Nuvio", if (mediaType == "tv") "series" else "movie", "cinema", "direct"),
                                providerId = currentProviderId
                            )
                        )
                        if (items.size >= limit) break
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching Nuvio home feed: ${e.message}")
        }
        return@withContext items
    }

    suspend fun search(
        query: String,
        limit: Int = 20,
        page: Int = 1,
        specificProviderId: String? = null,
        context: Context? = null
    ): List<VideoItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val items = mutableListOf<VideoItem>()
        val currentProviderId = if (!specificProviderId.isNullOrBlank()) {
            if (specificProviderId.startsWith("nuvio_")) specificProviderId else "nuvio_$specificProviderId"
        } else {
            PROVIDER_ID
        }

        val rawId = currentProviderId.removePrefix("nuvio_")
        val defItem = NuvioProviderRepository.ALL_OFFICIAL_PROVIDERS.find { it.id.equals(rawId, ignoreCase = true) }
        val providerDisplayName = defItem?.name ?: "Nuvio ($rawId)"

        try {
            val apiKey = AppConfig.TMDB_API_KEY
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
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
                        val title = obj.optString("title", obj.optString("name", "Cinema Release"))
                        val posterPath = obj.optString("poster_path", "")
                        val backdropPath = obj.optString("backdrop_path", "")
                        val year = (obj.optString("release_date", obj.optString("first_air_date", ""))).take(4)
                        val overview = obj.optString("overview", "")

                        val thumbUrl = when {
                            backdropPath.isNotBlank() && backdropPath != "null" -> "https://image.tmdb.org/t/p/w780$backdropPath"
                            posterPath.isNotBlank() && posterPath != "null" -> "https://image.tmdb.org/t/p/w500$posterPath"
                            else -> "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=800"
                        }

                        val itemId = "$currentProviderId:$mediaType:$tmdbId"
                        val uploader = if (year.isNotBlank()) "$providerDisplayName • $year" else providerDisplayName

                        items.add(
                            VideoItem(
                                id = itemId,
                                title = title,
                                uploaderName = uploader,
                                uploaderAvatarUrl = defItem?.logo?.takeIf { it.isNotBlank() },
                                durationSeconds = -1L,
                                viewCount = (obj.optInt("vote_count", 0) * 100L).coerceAtLeast(1000L),
                                uploadDate = year,
                                thumbnailUrl = thumbUrl,
                                description = overview,
                                tags = listOf(providerDisplayName, "Nuvio", if (mediaType == "tv") "series" else "movie", "cinema", "direct"),
                                providerId = currentProviderId
                            )
                        )
                        if (items.size >= limit) break
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error searching Nuvio catalog: ${e.message}")
        }
        return@withContext items
    }

    suspend fun getStreamData(
        urlOrId: String,
        context: Context?,
        specificProviderId: String? = null
    ): com.example.model.StreamData? = withContext(Dispatchers.IO) {
        val appContext = context ?: return@withContext null
        try {
            var mediaType = "movie"
            var tmdbId = ""
            var season = 1
            var episode = 1
            var targetProvider = specificProviderId

            var clean = urlOrId.trim()
            if (clean.startsWith("nuvio_", ignoreCase = true)) {
                val sub = clean.substringAfter("nuvio_").substringBefore(":")
                if (targetProvider.isNullOrBlank()) targetProvider = sub
                clean = clean.substringAfter(":")
            } else if (clean.startsWith("nuvio:", ignoreCase = true)) {
                clean = clean.removePrefix("nuvio:")
            }

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
                    val numMatch = Regex("""\b(\d{3,8})\b""").find(clean)
                    if (numMatch != null) {
                        tmdbId = numMatch.groupValues[1]
                        if (clean.contains("/tv/") || clean.contains("tv")) mediaType = "tv"
                    }
                }
            }

            if (tmdbId.isBlank() && clean.isNotBlank()) {
                val encodedQuery = URLEncoder.encode(clean, "UTF-8")
                val searchUrl = "https://api.themoviedb.org/3/search/multi?api_key=${AppConfig.TMDB_API_KEY}&query=$encodedQuery"
                val searchReq = Request.Builder().url(searchUrl).header("User-Agent", "Mozilla/5.0").build()
                val resp = httpClient.newCall(searchReq).execute()
                val body = resp.body?.string()
                if (!body.isNullOrBlank()) {
                    val json = JSONObject(body)
                    val resArr = json.optJSONArray("results")
                    if (resArr != null && resArr.length() > 0) {
                        val firstObj = resArr.getJSONObject(0)
                        tmdbId = firstObj.optInt("id", 0).takeIf { it > 0 }?.toString() ?: ""
                        mediaType = firstObj.optString("media_type", "movie")
                    }
                }
            }

            if (tmdbId.isBlank()) {
                Log.w(TAG, "Cannot extract valid TMDB ID from $urlOrId")
                return@withContext null
            }

            // Fetch TMDB metadata for title, year, overview, backdrop, imdbId
            val tmdbUrl = "https://api.themoviedb.org/3/$mediaType/$tmdbId?api_key=${AppConfig.TMDB_API_KEY}&append_to_response=external_ids"
            val tmdbReq = Request.Builder().url(tmdbUrl).header("User-Agent", "Mozilla/5.0").build()

            var title = "Nuvio Cinema ($tmdbId)"
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

            val request = com.example.extractor.tmdbembed.TMDBMediaRequest(
                tmdbId = tmdbId,
                mediaType = mediaType,
                season = season,
                episode = episode,
                title = title,
                year = year,
                imdbId = imdbId
            )

            // Resolve streams through NuvioProviderEngine
            val streamOptions = com.example.extractor.nuvio.NuvioProviderEngine.resolveNuvioStreams(
                context = appContext,
                request = request,
                specificProviderId = targetProvider
            )

            if (streamOptions.isEmpty()) {
                Log.w(TAG, "No playable streams found for '$title' across Nuvio providers")
                return@withContext null
            }

            val primaryOption = streamOptions.first()
            val allCaptions = mutableListOf<com.example.model.CaptionOption>()
            for (opt in streamOptions) {
                for (cap in opt.subtitles) {
                    if (allCaptions.none { it.url == cap.url }) {
                        allCaptions.add(cap)
                    }
                }
            }

            val effectiveProviderId = if (!targetProvider.isNullOrBlank()) "nuvio_$targetProvider" else PROVIDER_ID
            val sourceLabel = if (primaryOption.sourceName.isNotBlank()) primaryOption.sourceName else "Nuvio"

            com.example.model.StreamData(
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
                providerType = com.example.model.ProviderType.NUVIO,
                headers = primaryOption.headers,
                tags = listOf(sourceLabel, "Nuvio", mediaType, "cinema", "direct")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Nuvio stream resolution error for $urlOrId: ${e.message}", e)
            null
        }
    }
}
