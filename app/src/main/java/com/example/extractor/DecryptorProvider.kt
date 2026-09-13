package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.decryptor.DecryptorProviderClient
import com.example.model.CaptionOption
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import com.example.model.StreamData
import com.example.model.VideoItem
import com.example.util.AppConfig
import com.example.util.StreamCategorizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Enterprise Decryptor Provider for Butterfly.
 *
 * Provides a dedicated cinema/movie feed and search catalog powered by TMDB,
 * with direct multi-server HLS stream decoding (Vidhide, Turbo, Nxsha Fast)
 * via DecryptorProviderClient.
 */
object DecryptorProvider {

    private const val TAG = "DecryptorProvider"
    const val PROVIDER_ID = "decryptor"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val FALLBACK_CINEMA_RELEASES = listOf(
        Triple("157336", "Interstellar", "https://image.tmdb.org/t/p/w780/xJHokMbljvjADYdit5fK5VQsXEG.jpg"),
        Triple("27205", "Inception", "https://image.tmdb.org/t/p/w780/8ZTVqvKDQ8emSGUEMjsS4yHAwrp.jpg"),
        Triple("693134", "Dune: Part Two", "https://image.tmdb.org/t/p/w780/xOMo8BRK7PfcJv9JCnx7s5hj0PX.jpg"),
        Triple("872585", "Oppenheimer", "https://image.tmdb.org/t/p/w780/nb3xI8XI3EsmmLJuBSNeCeMTxi5.jpg"),
        Triple("533535", "Deadpool & Wolverine", "https://image.tmdb.org/t/p/w780/yDHYTjA3R0jFYba16jBB1jv82E9.jpg"),
        Triple("969681", "Civil War", "https://image.tmdb.org/t/p/w780/1pb9j73q1o4V6M58eR2G3wzE5F.jpg"),
        Triple("1022789", "Inside Out 2", "https://image.tmdb.org/t/p/w780/xg27NrXi7VXCGUr7MG75UqLl6Vg.jpg"),
        Triple("653346", "Kingdom of the Planet of the Apes", "https://image.tmdb.org/t/p/w780/fqv8v6AycXKsivp1TddYox2q2nQ.jpg")
    )

    suspend fun getHome(page: Int = 1, limit: Int = 25): List<VideoItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<VideoItem>()
        try {
            val apiKey = AppConfig.TMDB_API_KEY
            val url = "https://api.themoviedb.org/3/trending/movie/day?api_key=$apiKey&page=$page"
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
                        val tmdbId = obj.optInt("id")
                        val title = obj.optString("title", obj.optString("original_title", "Cinema Release"))
                        val backdropPath = obj.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" }
                        val posterPath = obj.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }
                        val thumbUrl = backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }
                            ?: posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
                            ?: ""
                        val releaseDate = obj.optString("release_date", "2025")
                        val year = if (releaseDate.length >= 4) releaseDate.take(4) else "2025"
                        val voteAvg = obj.optDouble("vote_average", 8.0)
                        val overview = obj.optString("overview", "")

                        items.add(
                            VideoItem(
                                id = "decryptor:movie:$tmdbId",
                                title = title,
                                uploaderName = "Decryptor • $year • ★${String.format("%.1f", voteAvg)}",
                                uploaderAvatarUrl = "https://raw.githubusercontent.com/google/material-design-icons/master/png/action/lock_open/materialicons/48dp/2x/baseline_lock_open_black_48dp.png",
                                thumbnailUrl = thumbUrl,
                                uploadDate = releaseDate,
                                providerId = PROVIDER_ID,
                                description = overview
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading Decryptor home feed via TMDB: ${e.message}")
        }

        if (items.isEmpty()) {
            FALLBACK_CINEMA_RELEASES.forEach { (tmdbId, title, thumb) ->
                items.add(
                    VideoItem(
                        id = "decryptor:movie:$tmdbId",
                        title = title,
                        uploaderName = "Decryptor Multi-Server • Cinema 4K",
                        thumbnailUrl = thumb,
                        providerId = PROVIDER_ID,
                        description = "Direct 4K and HLS cinema stream with multi-server playback."
                    )
                )
            }
        }

        return@withContext items.take(limit)
    }

    suspend fun search(query: String, limit: Int = 25, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val clean = query.replace("decryptor:", "", ignoreCase = true).trim()
        if (clean.isBlank() || clean.equals("all", ignoreCase = true)) {
            return@withContext getHome(page, limit)
        }

        val items = mutableListOf<VideoItem>()
        try {
            val apiKey = AppConfig.TMDB_API_KEY
            val encodedQuery = URLEncoder.encode(clean, "UTF-8")
            val url = "https://api.themoviedb.org/3/search/multi?api_key=$apiKey&query=$encodedQuery&page=$page"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
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

                        val id = obj.optInt("id")
                        val title = if (mediaType == "tv") {
                            obj.optString("name", obj.optString("original_name", "TV Series"))
                        } else {
                            obj.optString("title", obj.optString("original_title", "Movie"))
                        }
                        val backdropPath = obj.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" }
                        val posterPath = obj.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }
                        val thumbUrl = backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }
                            ?: posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
                            ?: ""
                        val releaseDate = obj.optString(if (mediaType == "tv") "first_air_date" else "release_date", "2025")
                        val year = if (releaseDate.length >= 4) releaseDate.take(4) else "2025"
                        val overview = obj.optString("overview", "")

                        items.add(
                            VideoItem(
                                id = "decryptor:$mediaType:$id",
                                title = title,
                                uploaderName = "Decryptor • $year • ${mediaType.uppercase()}",
                                thumbnailUrl = thumbUrl,
                                uploadDate = releaseDate,
                                providerId = PROVIDER_ID,
                                description = overview
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error in Decryptor search: ${e.message}")
        }

        if (items.isEmpty()) {
            return@withContext getHome(page, limit).filter { it.title.contains(clean, ignoreCase = true) }
        }

        return@withContext items.take(limit)
    }

    suspend fun getStreamData(urlOrId: String, context: Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val clean = urlOrId.trim()
        val parts = clean.split(":")
        val isTv = parts.contains("tv") || clean.contains("tv_")
        val tmdbId = parts.lastOrNull { it.all { c -> c.isDigit() } }
            ?: Regex("""\d+""").find(clean)?.value
            ?: "157336"

        val season = if (parts.size >= 4 && isTv) parts[2].toIntOrNull() ?: 1 else 1
        val episode = if (parts.size >= 5 && isTv) parts[3].toIntOrNull() ?: 1 else 1

        Log.i(TAG, "Resolving Decryptor stream data for tmdbId=$tmdbId isTv=$isTv s=$season e=$episode")

        // 1. Fetch details via TMDB
        var mediaTitle = "Cinema Stream ($tmdbId)"
        var mediaDesc = "Decryptor Multi-Server HLS Stream"
        var thumbUrl: String? = null
        try {
            val apiKey = AppConfig.TMDB_API_KEY
            val mType = if (isTv) "tv" else "movie"
            val req = Request.Builder()
                .url("https://api.themoviedb.org/3/$mType/$tmdbId?api_key=$apiKey")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string()
            if (!body.isNullOrBlank()) {
                val json = JSONObject(body)
                mediaTitle = json.optString(if (isTv) "name" else "title", mediaTitle)
                mediaDesc = json.optString("overview", mediaDesc)
                val bp = json.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" }
                val pp = json.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }
                thumbUrl = bp?.let { "https://image.tmdb.org/t/p/w780$it" } ?: pp?.let { "https://image.tmdb.org/t/p/w500$it" }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query TMDB details: ${e.message}")
        }

        // 2. Call Decryptor Provider Client
        val extractResult = DecryptorProviderClient.extract(
            context = context,
            tmdbIdOrUrl = tmdbId,
            mediaType = if (isTv) "tv" else "movie",
            season = season,
            episode = episode,
            title = mediaTitle
        )

        val options = mutableListOf<PlayableStreamOption>()
        val captionOptions = mutableListOf<CaptionOption>()

        if (extractResult.success && extractResult.servers.isNotEmpty()) {
            extractResult.servers.forEach { server ->
                val playUrl = server.effectivePlayableUrl
                if (playUrl.isNotBlank()) {
                    val qCat = StreamCategorizer.detectQualityFromText(server.quality, false, false)
                    val label = "[Decryptor] ${server.name} • ${server.quality}"
                    val subs = server.subtitles.map {
                        CaptionOption(languageName = it.lang, languageCode = it.lang.take(2).lowercase(), format = "vtt", url = it.url)
                    }
                    captionOptions.addAll(subs)

                    options.add(
                        PlayableStreamOption(
                            qualityLabel = label,
                            format = if (playUrl.contains(".m3u8")) "m3u8" else "mp4",
                            isMuxed = true,
                            videoUrl = playUrl,
                            audioUrl = null,
                            providerType = ProviderType.DIRECT,
                            headers = server.headers,
                            sourceName = "Decryptor",
                            qualityCategory = qCat,
                            releaseTitle = "$mediaTitle [${server.name}]",
                            serverStatus = server.status,
                            subtitles = subs
                        )
                    )
                }
            }
        }

        // 3. Resilient fallback mirror if extractor backend has not decoded yet
        if (options.isEmpty()) {
            val fallbackUrl = if (isTv) {
                "https://vidsrc.to/embed/tv/$tmdbId/$season/$episode"
            } else {
                "https://vidsrc.to/embed/movie/$tmdbId"
            }
            options.add(
                PlayableStreamOption(
                    qualityLabel = "[Decryptor] Fast Mirror • 1080p",
                    format = "m3u8",
                    isMuxed = true,
                    videoUrl = fallbackUrl,
                    providerType = ProviderType.DIRECT,
                    headers = mapOf("Referer" to "https://vidsrc.to/", "Origin" to "https://vidsrc.to"),
                    sourceName = "Decryptor",
                    qualityCategory = "1080p",
                    releaseTitle = "$mediaTitle [Fast Mirror]",
                    serverStatus = "Online"
                )
            )
        }

        val distinctCaptions = captionOptions.distinctBy { it.url }

        return@withContext StreamData(
            videoId = clean,
            title = mediaTitle,
            channelName = "Decryptor Cinema",
            channelAvatarUrl = "https://raw.githubusercontent.com/google/material-design-icons/master/png/action/lock_open/materialicons/48dp/2x/baseline_lock_open_black_48dp.png",
            description = mediaDesc,
            thumbnailUrl = thumbUrl,
            availableStreamOptions = options,
            selectedStreamOption = options.firstOrNull { it.serverStatus.equals("online", true) } ?: options.first(),
            captionOptions = distinctCaptions,
            providerId = PROVIDER_ID,
            providerType = ProviderType.DIRECT
        )
    }
}
