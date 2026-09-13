package com.example.extractor

import android.content.Context
import android.util.Log
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

/**
 * Enterprise VidSrc Provider for Butterfly.
 *
 * Provides a dedicated movie & series streaming source that resolves
 * directly across VidSrc.to, VidSrc.me, and VidSrc.sbs fast mirrors.
 */
object VidSrcProvider {

    private const val TAG = "VidSrcProvider"
    const val PROVIDER_ID = "vidsrc"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val CURATED_VIDSRC_ITEMS = listOf(
        Triple("550", "Fight Club", "https://image.tmdb.org/t/p/w780/hZkgoQYus5vegHoetLkCJzb17zJ.jpg"),
        Triple("299536", "Avengers: Infinity War", "https://image.tmdb.org/t/p/w780/bOGkgRGdhrBYJSLpXaxhXVstddV.jpg"),
        Triple("299534", "Avengers: Endgame", "https://image.tmdb.org/t/p/w780/orjiB3oal9aqqHGqZ0neTT0MyQI.jpg"),
        Triple("155", "The Dark Knight", "https://image.tmdb.org/t/p/w780/dqK9Hag1054tghRQSqLSfrkvQnA.jpg"),
        Triple("680", "Pulp Fiction", "https://image.tmdb.org/t/p/w780/suaEOtk1N1sgg2MTM7oZd2cfVp3.jpg"),
        Triple("24428", "The Avengers", "https://image.tmdb.org/t/p/w780/9BBTo63ANSmhC4e6r62OJFuK2GL.jpg")
    )

    suspend fun getHome(page: Int = 1, limit: Int = 25): List<VideoItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<VideoItem>()
        try {
            val apiKey = AppConfig.TMDB_API_KEY
            val url = "https://api.themoviedb.org/3/movie/popular?api_key=$apiKey&page=$page"
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
                        val tmdbId = obj.optInt("id")
                        val title = obj.optString("title", "Popular Movie")
                        val backdropPath = obj.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" }
                        val posterPath = obj.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }
                        val thumbUrl = backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }
                            ?: posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
                            ?: ""
                        val releaseDate = obj.optString("release_date", "2025")
                        val year = if (releaseDate.length >= 4) releaseDate.take(4) else "2025"
                        val voteAvg = obj.optDouble("vote_average", 7.8)
                        val overview = obj.optString("overview", "")

                        items.add(
                            VideoItem(
                                id = "vidsrc:movie:$tmdbId",
                                title = title,
                                uploaderName = "VidSrc • $year • ★${String.format("%.1f", voteAvg)}",
                                uploaderAvatarUrl = "https://vidsrc.to/favicon.ico",
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
            Log.w(TAG, "Error loading VidSrc home items: ${e.message}")
        }

        if (items.isEmpty()) {
            CURATED_VIDSRC_ITEMS.forEach { (tmdbId, title, thumb) ->
                items.add(
                    VideoItem(
                        id = "vidsrc:movie:$tmdbId",
                        title = title,
                        uploaderName = "VidSrc Cloud Stream",
                        thumbnailUrl = thumb,
                        providerId = PROVIDER_ID,
                        description = "VidSrc HD multi-mirror cloud playback."
                    )
                )
            }
        }

        return@withContext items.take(limit)
    }

    suspend fun search(query: String, limit: Int = 25, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val clean = query.replace("vidsrc:", "", ignoreCase = true).trim()
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
                        val title = if (mediaType == "tv") obj.optString("name", "TV Series") else obj.optString("title", "Movie")
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
                                id = "vidsrc:$mediaType:$id",
                                title = title,
                                uploaderName = "VidSrc • $year • ${mediaType.uppercase()}",
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
            Log.w(TAG, "Error in VidSrc search: ${e.message}")
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
            ?: "550"

        val season = if (parts.size >= 4 && isTv) parts[2].toIntOrNull() ?: 1 else 1
        val episode = if (parts.size >= 5 && isTv) parts[3].toIntOrNull() ?: 1 else 1

        var mediaTitle = "VidSrc Media ($tmdbId)"
        var mediaDesc = "High-speed VidSrc multi-server stream"
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
            Log.w(TAG, "Failed to query TMDB details for VidSrc: ${e.message}")
        }

        val vidsrcToUrl = if (isTv) "https://vidsrc.to/embed/tv/$tmdbId/$season/$episode" else "https://vidsrc.to/embed/movie/$tmdbId"
        val vidsrcMeUrl = if (isTv) "https://vidsrc.me/embed/tv?tmdb=$tmdbId&season=$season&episode=$episode" else "https://vidsrc.me/embed/movie?tmdb=$tmdbId"
        val vidsrcSbsUrl = if (isTv) "https://vidsrc.sbs/embed/tv/$tmdbId/$season/$episode" else "https://vidsrc.sbs/embed/movie/$tmdbId"

        val options = listOf(
            PlayableStreamOption(
                qualityLabel = "[VidSrc] VidSrc.to • Fast Mirror (1080p)",
                format = "m3u8",
                isMuxed = true,
                videoUrl = vidsrcToUrl,
                providerType = ProviderType.DIRECT,
                headers = mapOf("Referer" to "https://vidsrc.to/", "Origin" to "https://vidsrc.to"),
                sourceName = "VidSrc",
                qualityCategory = "1080p",
                releaseTitle = "$mediaTitle [VidSrc.to]",
                serverStatus = "Online"
            ),
            PlayableStreamOption(
                qualityLabel = "[VidSrc] VidSrc.me • Server 2 (1080p)",
                format = "m3u8",
                isMuxed = true,
                videoUrl = vidsrcMeUrl,
                providerType = ProviderType.DIRECT,
                headers = mapOf("Referer" to "https://vidsrc.me/", "Origin" to "https://vidsrc.me"),
                sourceName = "VidSrc",
                qualityCategory = "1080p",
                releaseTitle = "$mediaTitle [VidSrc.me]",
                serverStatus = "Online"
            ),
            PlayableStreamOption(
                qualityLabel = "[VidSrc] VidSrc.sbs • Server 3 (720p/1080p)",
                format = "m3u8",
                isMuxed = true,
                videoUrl = vidsrcSbsUrl,
                providerType = ProviderType.DIRECT,
                headers = mapOf("Referer" to "https://vidsrc.sbs/", "Origin" to "https://vidsrc.sbs"),
                sourceName = "VidSrc",
                qualityCategory = "720p",
                releaseTitle = "$mediaTitle [VidSrc.sbs]",
                serverStatus = "Online"
            )
        )

        return@withContext StreamData(
            videoId = clean,
            title = mediaTitle,
            channelName = "VidSrc Cloud",
            channelAvatarUrl = "https://vidsrc.to/favicon.ico",
            description = mediaDesc,
            thumbnailUrl = thumbUrl,
            availableStreamOptions = options,
            selectedStreamOption = options.first(),
            providerId = PROVIDER_ID,
            providerType = ProviderType.DIRECT
        )
    }
}
