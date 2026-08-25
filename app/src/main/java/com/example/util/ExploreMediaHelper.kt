package com.example.util

import android.util.Log
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object ExploreMediaHelper {

    private const val TAG = "ExploreMediaHelper"
    private const val TMDB_API_KEY = AppConfig.TMDB_API_KEY
    private const val TMDB_IMAGE_BASE = AppConfig.TMDB_IMAGE_BASE_W500
    private const val TMDB_BACKDROP_BASE = AppConfig.TMDB_BACKDROP_BASE

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<String, List<ExploreMediaItem>>()

    suspend fun fetchExploreFeed(): List<ExploreSection> = withContext(Dispatchers.IO) {
        supervisorScope {
            val trendingMoviesDeferred = async { fetchTmdbTrendingMovies() }
            val trendingTvDeferred = async { fetchTmdbTrendingTv() }
            val popularAnimeDeferred = async { fetchAniListTrendingAnime() }
            val topRatedAnimeDeferred = async { fetchJikanTopAnime() }
            val topRatedMoviesDeferred = async { fetchTmdbTopRatedMovies() }
            val popularMoviesDeferred = async { fetchTmdbPopularMovies() }

            val trendingMovies = try { trendingMoviesDeferred.await() } catch (e: Exception) { getCuratedMovies() }
            val trendingTv = try { trendingTvDeferred.await() } catch (e: Exception) { getCuratedTv() }
            val popularAnime = try { popularAnimeDeferred.await() } catch (e: Exception) { getCuratedAnime() }
            val topRatedAnime = try { topRatedAnimeDeferred.await() } catch (e: Exception) { emptyList() }
            val topRatedMovies = try { topRatedMoviesDeferred.await() } catch (e: Exception) { emptyList() }
            val popularMovies = try { popularMoviesDeferred.await() } catch (e: Exception) { emptyList() }

            val sections = mutableListOf<ExploreSection>()

            if (trendingMovies.isNotEmpty()) {
                sections.add(
                    ExploreSection(
                        title = "Trending Movies",
                        subtitle = "Most watched this week • TMDB & IMDb",
                        iconName = "movie",
                        items = trendingMovies
                    )
                )
            }

            if (popularAnime.isNotEmpty()) {
                sections.add(
                    ExploreSection(
                        title = "Trending Anime",
                        subtitle = "Top airing & seasonal hits • AniList",
                        iconName = "anime",
                        items = popularAnime
                    )
                )
            }

            if (trendingTv.isNotEmpty()) {
                sections.add(
                    ExploreSection(
                        title = "Popular TV Shows",
                        subtitle = "Binge-worthy series & new seasons • TMDB",
                        iconName = "tv",
                        items = trendingTv
                    )
                )
            }

            if (topRatedAnime.isNotEmpty()) {
                sections.add(
                    ExploreSection(
                        title = "Top Rated Anime of All Time",
                        subtitle = "Highest rated masterworks • MyAnimeList (Jikan)",
                        iconName = "star",
                        items = topRatedAnime
                    )
                )
            }

            if (topRatedMovies.isNotEmpty()) {
                sections.add(
                    ExploreSection(
                        title = "Critically Acclaimed Movies",
                        subtitle = "IMDb 8.0+ & TMDB Top Rated",
                        iconName = "award",
                        items = topRatedMovies
                    )
                )
            }

            if (popularMovies.isNotEmpty()) {
                sections.add(
                    ExploreSection(
                        title = "Popular Blockbusters",
                        subtitle = "Action, Sci-Fi & Adventure",
                        iconName = "fire",
                        items = popularMovies
                    )
                )
            }

            sections
        }
    }

    suspend fun searchAll(query: String): List<ExploreMediaItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        supervisorScope {
            val tmdbDeferred = async { searchTmdb(query) }
            val animeDeferred = async { searchAniListAnime(query) }
            val jikanDeferred = async { searchJikanAnime(query) }

            val tmdbResults = try { tmdbDeferred.await() } catch (e: Exception) { emptyList() }
            val animeResults = try { animeDeferred.await() } catch (e: Exception) { emptyList() }
            val jikanResults = try { jikanDeferred.await() } catch (e: Exception) { emptyList() }

            val combined = (tmdbResults + animeResults + jikanResults)
                .distinctBy { "${it.mediaType}_${it.title.lowercase().trim()}" }
                .sortedByDescending { it.rating }

            combined
        }
    }

    suspend fun fetchTrendingSearchTopics(): List<String> = withContext(Dispatchers.IO) {
        try {
            val topics = mutableListOf<String>()
            val movies = try { fetchTmdbTrendingMovies() } catch (e: Exception) { emptyList() }
            val tv = try { fetchTmdbTrendingTv() } catch (e: Exception) { emptyList() }
            val anime = try { fetchAniListTrendingAnime() } catch (e: Exception) { emptyList() }

            movies.take(6).forEach { if (it.title.isNotBlank()) topics.add(it.title.trim()) }
            tv.take(6).forEach { if (it.title.isNotBlank()) topics.add(it.title.trim()) }
            anime.take(4).forEach { if (it.title.isNotBlank()) topics.add(it.title.trim()) }

            if (topics.isNotEmpty()) {
                topics.distinct().take(12)
            } else {
                getCuratedTrendingTopics()
            }
        } catch (e: Exception) {
            getCuratedTrendingTopics()
        }
    }

    fun getCuratedTrendingTopics(): List<String> = listOf(
        "Toy Story 5",
        "Mutiny",
        "Spider-Man: Brand New Day",
        "Lanterns",
        "Reacher",
        "Silo",
        "Deadpool & Wolverine",
        "Dune: Part Two",
        "Stranger Things",
        "Arcane",
        "Solo Leveling",
        "House of the Dragon"
    )

    suspend fun fetchCategoryItems(mediaType: ExploreMediaType): List<ExploreMediaItem> = withContext(Dispatchers.IO) {
        when (mediaType) {
            ExploreMediaType.MOVIE -> {
                val trending = fetchTmdbTrendingMovies()
                val popular = fetchTmdbPopularMovies()
                (trending + popular).distinctBy { it.id }
            }
            ExploreMediaType.TV -> {
                val trending = fetchTmdbTrendingTv()
                val popular = fetchTmdbPopularTv()
                (trending + popular).distinctBy { it.id }
            }
            ExploreMediaType.ANIME -> {
                val aniList = fetchAniListTrendingAnime()
                val jikan = fetchJikanTopAnime()
                (aniList + jikan).distinctBy { it.title.lowercase().trim() }
            }
            else -> {
                val allFeed = fetchExploreFeed()
                allFeed.flatMap { it.items }.distinctBy { it.id }
            }
        }
    }

    // ==================== TMDB INTEGRATION ====================

    private fun fetchTmdbTrendingMovies(): List<ExploreMediaItem> {
        val cacheKey = "tmdb_trending_movies"
        cache[cacheKey]?.let { return it }

        val url = "https://api.themoviedb.org/3/trending/movie/week?api_key=$TMDB_API_KEY"
        val list = parseTmdbList(url, ExploreMediaType.MOVIE)
        if (list.isNotEmpty()) cache[cacheKey] = list
        return list
    }

    private fun fetchTmdbPopularMovies(): List<ExploreMediaItem> {
        val cacheKey = "tmdb_popular_movies"
        cache[cacheKey]?.let { return it }

        val url = "https://api.themoviedb.org/3/movie/popular?api_key=$TMDB_API_KEY"
        val list = parseTmdbList(url, ExploreMediaType.MOVIE)
        if (list.isNotEmpty()) cache[cacheKey] = list
        return list
    }

    private fun fetchTmdbTopRatedMovies(): List<ExploreMediaItem> {
        val cacheKey = "tmdb_top_rated_movies"
        cache[cacheKey]?.let { return it }

        val url = "https://api.themoviedb.org/3/movie/top_rated?api_key=$TMDB_API_KEY"
        val list = parseTmdbList(url, ExploreMediaType.MOVIE)
        if (list.isNotEmpty()) cache[cacheKey] = list
        return list
    }

    private fun fetchTmdbTrendingTv(): List<ExploreMediaItem> {
        val cacheKey = "tmdb_trending_tv"
        cache[cacheKey]?.let { return it }

        val url = "https://api.themoviedb.org/3/trending/tv/week?api_key=$TMDB_API_KEY"
        val list = parseTmdbList(url, ExploreMediaType.TV)
        if (list.isNotEmpty()) cache[cacheKey] = list
        return list
    }

    private fun fetchTmdbPopularTv(): List<ExploreMediaItem> {
        val cacheKey = "tmdb_popular_tv"
        cache[cacheKey]?.let { return it }

        val url = "https://api.themoviedb.org/3/tv/popular?api_key=$TMDB_API_KEY"
        val list = parseTmdbList(url, ExploreMediaType.TV)
        if (list.isNotEmpty()) cache[cacheKey] = list
        return list
    }

    private fun searchTmdb(query: String): List<ExploreMediaItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://api.themoviedb.org/3/search/multi?api_key=$TMDB_API_KEY&query=$encoded"
        return parseTmdbMultiSearch(url)
    }

    private fun parseTmdbList(url: String, defaultType: ExploreMediaType): List<ExploreMediaItem> {
        try {
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return emptyList()
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return emptyList()

            val list = mutableListOf<ExploreMediaItem>()
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val id = item.optInt("id", 0)
                if (id <= 0) continue

                val title = item.optString("title").ifBlank { item.optString("name") }
                if (title.isBlank()) continue

                val originalTitle = item.optString("original_title").ifBlank { item.optString("original_name") }
                val posterPath = item.optString("poster_path").takeIf { it.isNotBlank() }
                val backdropPath = item.optString("backdrop_path").takeIf { it.isNotBlank() }
                val posterUrl = posterPath?.let { "$TMDB_IMAGE_BASE$it" }
                val backdropUrl = backdropPath?.let { "$TMDB_BACKDROP_BASE$it" }
                val voteAvg = item.optDouble("vote_average", 0.0)
                val releaseDate = item.optString("release_date").ifBlank { item.optString("first_air_date") }
                val year = releaseDate.take(4)
                val overview = item.optString("overview")
                val genreIds = item.optJSONArray("genre_ids")
                val genres = mapGenreIds(genreIds)

                val mediaType = if (item.has("title")) ExploreMediaType.MOVIE else defaultType

                list.add(
                    ExploreMediaItem(
                        id = if (mediaType == ExploreMediaType.MOVIE) "movie_$id" else "tv_$id",
                        title = title,
                        originalTitle = originalTitle,
                        mediaType = mediaType,
                        source = ExploreSource.TMDB,
                        posterUrl = posterUrl,
                        backdropUrl = backdropUrl,
                        rating = voteAvg,
                        ratingSource = "TMDB",
                        releaseYear = year,
                        genres = genres,
                        overview = overview,
                        tmdbId = id.toString()
                    )
                )
            }
            return list
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching TMDB list from $url", e)
            return emptyList()
        }
    }

    private fun parseTmdbMultiSearch(url: String): List<ExploreMediaItem> {
        try {
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return emptyList()
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return emptyList()

            val list = mutableListOf<ExploreMediaItem>()
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val mediaTypeStr = item.optString("media_type")
                if (mediaTypeStr != "movie" && mediaTypeStr != "tv") continue

                val id = item.optInt("id", 0)
                if (id <= 0) continue

                val title = item.optString("title").ifBlank { item.optString("name") }
                if (title.isBlank()) continue

                val posterPath = item.optString("poster_path").takeIf { it.isNotBlank() }
                val backdropPath = item.optString("backdrop_path").takeIf { it.isNotBlank() }
                val posterUrl = posterPath?.let { "$TMDB_IMAGE_BASE$it" }
                val backdropUrl = backdropPath?.let { "$TMDB_BACKDROP_BASE$it" }
                val voteAvg = item.optDouble("vote_average", 0.0)
                val releaseDate = item.optString("release_date").ifBlank { item.optString("first_air_date") }
                val year = releaseDate.take(4)
                val overview = item.optString("overview")
                val genres = mapGenreIds(item.optJSONArray("genre_ids"))

                val type = if (mediaTypeStr == "movie") ExploreMediaType.MOVIE else ExploreMediaType.TV

                list.add(
                    ExploreMediaItem(
                        id = "${mediaTypeStr}_$id",
                        title = title,
                        mediaType = type,
                        source = ExploreSource.TMDB,
                        posterUrl = posterUrl,
                        backdropUrl = backdropUrl,
                        rating = voteAvg,
                        ratingSource = "TMDB",
                        releaseYear = year,
                        genres = genres,
                        overview = overview,
                        tmdbId = id.toString()
                    )
                )
            }
            return list
        } catch (e: Exception) {
            Log.e(TAG, "Error in TMDB multi search", e)
            return emptyList()
        }
    }

    private fun mapGenreIds(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val map = mapOf(
            28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy",
            80 to "Crime", 99 to "Documentary", 18 to "Drama", 10751 to "Family",
            14 to "Fantasy", 36 to "History", 27 to "Horror", 10402 to "Music",
            9648 to "Mystery", 10749 to "Romance", 878 to "Sci-Fi", 10770 to "TV Movie",
            53 to "Thriller", 10752 to "War", 37 to "Western", 10759 to "Action & Adventure",
            10765 to "Sci-Fi & Fantasy", 10768 to "War & Politics"
        )
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val id = arr.optInt(i)
            map[id]?.let { list.add(it) }
        }
        return list
    }

    // ==================== ANILIST (GRAPHQL) INTEGRATION ====================

    private fun fetchAniListTrendingAnime(): List<ExploreMediaItem> {
        val cacheKey = "anilist_trending_anime"
        cache[cacheKey]?.let { return it }

        val query = """
            query {
              Page(page: 1, perPage: 25) {
                media(sort: TRENDING_DESC, type: ANIME, isAdult: false) {
                  id
                  idMal
                  title {
                    romaji
                    english
                    native
                  }
                  coverImage {
                    extraLarge
                    large
                  }
                  bannerImage
                  averageScore
                  format
                  episodes
                  genres
                  description(asHtml: false)
                  seasonYear
                  trailer {
                    id
                    site
                  }
                  studios(isMain: true) {
                    nodes {
                      name
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val list = executeAniListQuery(query)
        if (list.isNotEmpty()) cache[cacheKey] = list
        return list
    }

    private fun searchAniListAnime(queryStr: String): List<ExploreMediaItem> {
        val sanitized = queryStr.replace("\"", "\\\"")
        val query = """
            query {
              Page(page: 1, perPage: 20) {
                media(search: "$sanitized", type: ANIME, isAdult: false) {
                  id
                  idMal
                  title {
                    romaji
                    english
                  }
                  coverImage {
                    extraLarge
                    large
                  }
                  bannerImage
                  averageScore
                  format
                  episodes
                  genres
                  description(asHtml: false)
                  seasonYear
                  trailer {
                    id
                    site
                  }
                }
              }
            }
        """.trimIndent()
        return executeAniListQuery(query)
    }

    private fun executeAniListQuery(graphqlQuery: String): List<ExploreMediaItem> {
        try {
            val bodyJson = JSONObject()
            bodyJson.put("query", graphqlQuery)

            val reqBody = bodyJson.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val req = Request.Builder()
                .url("https://graphql.anilist.co")
                .post(reqBody)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return emptyList()
            val json = JSONObject(body)
            val mediaArr = json.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media") ?: return emptyList()

            val list = mutableListOf<ExploreMediaItem>()
            for (i in 0 until mediaArr.length()) {
                val media = mediaArr.getJSONObject(i)
                val id = media.optInt("id", 0)
                if (id <= 0) continue

                val titleObj = media.optJSONObject("title")
                val english = titleObj?.optString("english")?.takeIf { it.isNotBlank() && it != "null" }
                val romaji = titleObj?.optString("romaji")?.takeIf { it.isNotBlank() && it != "null" }
                val nativeTitle = titleObj?.optString("native")?.takeIf { it.isNotBlank() && it != "null" }
                val title = english ?: romaji ?: nativeTitle ?: "Anime #$id"

                val coverObj = media.optJSONObject("coverImage")
                val posterUrl = coverObj?.optString("extraLarge")?.takeIf { it.isNotBlank() && it != "null" }
                    ?: coverObj?.optString("large")
                val bannerUrl = media.optString("bannerImage").takeIf { it.isNotBlank() && it != "null" } ?: posterUrl

                val avgScore = media.optInt("averageScore", 0)
                val ratingDouble = if (avgScore > 0) avgScore / 10.0 else 8.5
                val episodes = media.optInt("episodes", 0).takeIf { it > 0 }
                val year = media.optInt("seasonYear", 0).takeIf { it > 0 }?.toString() ?: "2024"
                val desc = media.optString("description").replace(Regex("<.*?>"), "").trim()

                val genresArr = media.optJSONArray("genres")
                val genres = mutableListOf<String>()
                if (genresArr != null) {
                    for (g in 0 until genresArr.length()) {
                        genres.add(genresArr.optString(g))
                    }
                }

                val studioName = media.optJSONObject("studios")?.optJSONArray("nodes")?.optJSONObject(0)?.optString("name")
                val trailerId = media.optJSONObject("trailer")?.let { tr ->
                    if (tr.optString("site").equals("youtube", ignoreCase = true)) tr.optString("id") else null
                }

                list.add(
                    ExploreMediaItem(
                        id = "anime_anilist_$id",
                        title = title,
                        originalTitle = romaji ?: nativeTitle,
                        mediaType = ExploreMediaType.ANIME,
                        source = ExploreSource.ANILIST,
                        posterUrl = posterUrl,
                        backdropUrl = bannerUrl,
                        rating = ratingDouble,
                        ratingSource = "AniList",
                        releaseYear = year,
                        genres = genres,
                        overview = desc,
                        episodesCount = episodes,
                        studio = studioName,
                        trailerYoutubeId = trailerId
                    )
                )
            }
            return list
        } catch (e: Exception) {
            Log.e(TAG, "Error executing AniList query", e)
            return emptyList()
        }
    }

    // ==================== JIKAN (MYANIMELIST) INTEGRATION ====================

    private fun fetchJikanTopAnime(): List<ExploreMediaItem> {
        val cacheKey = "jikan_top_anime"
        cache[cacheKey]?.let { return it }

        val url = "https://api.jikan.moe/v4/top/anime?limit=25"
        val list = parseJikanList(url)
        if (list.isNotEmpty()) cache[cacheKey] = list
        return list
    }

    private fun searchJikanAnime(query: String): List<ExploreMediaItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://api.jikan.moe/v4/anime?q=$encoded&limit=20"
        return parseJikanList(url)
    }

    private fun parseJikanList(url: String): List<ExploreMediaItem> {
        try {
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return emptyList()
            val json = JSONObject(body)
            val dataArr = json.optJSONArray("data") ?: return emptyList()

            val list = mutableListOf<ExploreMediaItem>()
            for (i in 0 until dataArr.length()) {
                val anime = dataArr.getJSONObject(i)
                val malId = anime.optInt("mal_id", 0)
                if (malId <= 0) continue

                val title = anime.optString("title_english").takeIf { it.isNotBlank() && it != "null" }
                    ?: anime.optString("title")
                val originalTitle = anime.optString("title_japanese").takeIf { it.isNotBlank() && it != "null" }

                val imagesObj = anime.optJSONObject("images")
                val jpgObj = imagesObj?.optJSONObject("jpg")
                val posterUrl = jpgObj?.optString("large_image_url") ?: jpgObj?.optString("image_url")

                val score = anime.optDouble("score", 8.8)
                val year = anime.optInt("year", 0).takeIf { it > 0 }?.toString() ?: "2024"
                val episodes = anime.optInt("episodes", 0).takeIf { it > 0 }
                val synopsis = anime.optString("synopsis").replace(Regex("\\[Written by.*?\\]"), "").trim()
                val status = anime.optString("status")

                val genresArr = anime.optJSONArray("genres")
                val genres = mutableListOf<String>()
                if (genresArr != null) {
                    for (g in 0 until genresArr.length()) {
                        val gObj = genresArr.optJSONObject(g)
                        gObj?.optString("name")?.let { genres.add(it) }
                    }
                }

                val studio = anime.optJSONArray("studios")?.optJSONObject(0)?.optString("name")
                val trailerYtId = anime.optJSONObject("trailer")?.optString("youtube_id")?.takeIf { it.isNotBlank() && it != "null" }

                list.add(
                    ExploreMediaItem(
                        id = "anime_jikan_$malId",
                        title = title,
                        originalTitle = originalTitle,
                        mediaType = ExploreMediaType.ANIME,
                        source = ExploreSource.JIKAN,
                        posterUrl = posterUrl,
                        backdropUrl = posterUrl,
                        rating = score,
                        ratingSource = "MAL",
                        releaseYear = year,
                        genres = genres,
                        overview = synopsis,
                        episodesCount = episodes,
                        status = status,
                        studio = studio,
                        trailerYoutubeId = trailerYtId
                    )
                )
            }
            return list
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Jikan list from $url", e)
            return emptyList()
        }
    }

    // ==================== FULL MEDIA DETAILS RESOLVER ====================

    suspend fun resolveFullMediaDetails(item: ExploreMediaItem): ExploreMediaItem = withContext(Dispatchers.IO) {
        try {
            if (item.source == ExploreSource.TMDB || item.tmdbId != null || (!item.id.startsWith("anime_") && item.id.contains("_"))) {
                val tmdbId = item.tmdbId ?: item.id.substringAfter("_")
                val typeStr = if (item.mediaType == ExploreMediaType.TV) "tv" else "movie"
                val detailUrl = "https://api.themoviedb.org/3/$typeStr/$tmdbId?api_key=$TMDB_API_KEY&append_to_response=credits,videos,images,reviews,recommendations,similar,external_ids"

                val req = Request.Builder().url(detailUrl).header("User-Agent", "Mozilla/5.0").build()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string()
                if (body != null) {
                    val json = JSONObject(body)
                    val imdbId = json.optJSONObject("external_ids")?.optString("imdb_id")?.takeIf { it.isNotBlank() }
                    val tagline = json.optString("tagline").takeIf { it.isNotBlank() }

                    // Runtime
                    val runtimeMins = if (item.mediaType == ExploreMediaType.MOVIE) {
                        json.optInt("runtime", 0)
                    } else {
                        json.optJSONArray("episode_run_time")?.optInt(0, 0) ?: 0
                    }
                    val runtimeText = if (runtimeMins > 0) {
                        val hours = runtimeMins / 60
                        val mins = runtimeMins % 60
                        if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                    } else null

                    // Director / Creator
                    var director: String? = null
                    val crewArr = json.optJSONObject("credits")?.optJSONArray("crew")
                    if (crewArr != null) {
                        for (cr in 0 until crewArr.length()) {
                            val cObj = crewArr.getJSONObject(cr)
                            val job = cObj.optString("job")
                            if (job.equals("Director", ignoreCase = true) || job.equals("Creator", ignoreCase = true) || job.equals("Executive Producer", ignoreCase = true)) {
                                director = cObj.optString("name")
                                break
                            }
                        }
                    }
                    if (director.isNullOrBlank() && json.has("created_by")) {
                        director = json.optJSONArray("created_by")?.optJSONObject(0)?.optString("name")
                    }

                    // Credits / Cast
                    val castList = mutableListOf<CastMember>()
                    val castArr = json.optJSONObject("credits")?.optJSONArray("cast")
                    if (castArr != null) {
                        for (c in 0 until minOf(castArr.length(), 18)) {
                            val castObj = castArr.getJSONObject(c)
                            val name = castObj.optString("name")
                            val character = castObj.optString("character")
                            val profilePath = castObj.optString("profile_path").takeIf { it.isNotBlank() }
                            val avatar = profilePath?.let { "$TMDB_IMAGE_BASE$it" }
                            val personId = castObj.optInt("id", 0).takeIf { it > 0 }
                            castList.add(CastMember(name = name, role = character, avatarUrl = avatar, personId = personId))
                        }
                    }

                    // Videos / Trailers / Clips
                    val clipsList = mutableListOf<MediaClipItem>()
                    var trailerKey: String? = item.trailerYoutubeId
                    val videoResults = json.optJSONObject("videos")?.optJSONArray("results")
                    if (videoResults != null) {
                        for (v in 0 until videoResults.length()) {
                            val vidObj = videoResults.getJSONObject(v)
                            val site = vidObj.optString("site", "YouTube")
                            val type = vidObj.optString("type", "Trailer")
                            val key = vidObj.optString("key", "")
                            val name = vidObj.optString("name", "Clip")
                            val vidId = vidObj.optString("id", "")

                            if (key.isNotBlank()) {
                                val thumb = "https://i.ytimg.com/vi/$key/hqdefault.jpg"
                                clipsList.add(
                                    MediaClipItem(
                                        id = vidId.ifBlank { key },
                                        name = name,
                                        type = type,
                                        site = site,
                                        key = key,
                                        thumbnailUrl = thumb
                                    )
                                )
                                if (trailerKey == null && (type.equals("Trailer", ignoreCase = true) || type.equals("Teaser", ignoreCase = true))) {
                                    trailerKey = key
                                }
                            }
                        }
                    }

                    // Screenshots / Backdrops
                    val screenshots = mutableListOf<String>()
                    val backdropsArr = json.optJSONObject("images")?.optJSONArray("backdrops")
                    if (backdropsArr != null) {
                        for (b in 0 until minOf(backdropsArr.length(), 12)) {
                            val bObj = backdropsArr.getJSONObject(b)
                            val fPath = bObj.optString("file_path", "")
                            if (fPath.isNotBlank()) {
                                screenshots.add("$TMDB_BACKDROP_BASE$fPath")
                            }
                        }
                    }

                    // User Reviews & Comments from IMDb / TMDB
                    val reviewsList = mutableListOf<MediaReviewItem>()
                    val reviewsArr = json.optJSONObject("reviews")?.optJSONArray("results")
                    if (reviewsArr != null) {
                        for (r in 0 until minOf(reviewsArr.length(), 8)) {
                            val rObj = reviewsArr.getJSONObject(r)
                            val author = rObj.optString("author", "User")
                            val content = rObj.optString("content", "").trim()
                            val rId = rObj.optString("id", "")
                            val createdAt = rObj.optString("created_at", "").take(10)
                            val authorDetails = rObj.optJSONObject("author_details")
                            val rawRating = authorDetails?.optDouble("rating", -1.0) ?: -1.0
                            val rating = if (rawRating > 0) rawRating else null
                            val avatarPath = authorDetails?.optString("avatar_path", "")
                            val avatarUrl = when {
                                avatarPath.isNullOrBlank() -> null
                                avatarPath.startsWith("/http") -> avatarPath.substring(1)
                                avatarPath.startsWith("http") -> avatarPath
                                else -> "$TMDB_IMAGE_BASE$avatarPath"
                            }

                            if (content.isNotBlank()) {
                                reviewsList.add(
                                    MediaReviewItem(
                                        id = rId,
                                        author = author,
                                        authorAvatarUrl = avatarUrl,
                                        rating = rating,
                                        content = content,
                                        createdAt = createdAt,
                                        source = "IMDb / TMDB"
                                    )
                                )
                            }
                        }
                    }

                    // Related & Recommended Content
                    val relatedList = mutableListOf<ExploreMediaItem>()
                    val recsArr = json.optJSONObject("recommendations")?.optJSONArray("results")
                        ?: json.optJSONObject("similar")?.optJSONArray("results")
                    if (recsArr != null) {
                        for (rc in 0 until minOf(recsArr.length(), 10)) {
                            val rcObj = recsArr.getJSONObject(rc)
                            val rcId = rcObj.optInt("id", 0)
                            if (rcId <= 0) continue
                            val rcTitle = rcObj.optString("title").ifBlank { rcObj.optString("name") }
                            if (rcTitle.isBlank()) continue
                            val rcPoster = rcObj.optString("poster_path").takeIf { it.isNotBlank() }?.let { "$TMDB_IMAGE_BASE$it" }
                            val rcBackdrop = rcObj.optString("backdrop_path").takeIf { it.isNotBlank() }?.let { "$TMDB_BACKDROP_BASE$it" }
                            val rcRating = rcObj.optDouble("vote_average", 0.0)
                            val rcYear = rcObj.optString("release_date").ifBlank { rcObj.optString("first_air_date") }.take(4)
                            val rcType = if (item.mediaType == ExploreMediaType.TV) ExploreMediaType.TV else ExploreMediaType.MOVIE

                            relatedList.add(
                                ExploreMediaItem(
                                    id = "${if (rcType == ExploreMediaType.TV) "tv" else "movie"}_$rcId",
                                    title = rcTitle,
                                    mediaType = rcType,
                                    source = ExploreSource.TMDB,
                                    posterUrl = rcPoster,
                                    backdropUrl = rcBackdrop,
                                    rating = rcRating,
                                    ratingSource = "TMDB",
                                    releaseYear = rcYear,
                                    overview = rcObj.optString("overview"),
                                    tmdbId = rcId.toString()
                                )
                            )
                        }
                    }

                    return@withContext item.copy(
                        imdbId = imdbId ?: item.imdbId,
                        tagline = tagline ?: item.tagline,
                        runtimeText = runtimeText ?: item.runtimeText,
                        director = director ?: item.director,
                        cast = castList.ifEmpty { item.cast },
                        trailerYoutubeId = trailerKey ?: item.trailerYoutubeId,
                        screenshots = screenshots.ifEmpty { item.screenshots },
                        clipsAndTrailers = clipsList.ifEmpty { item.clipsAndTrailers },
                        reviews = reviewsList.ifEmpty { item.reviews },
                        relatedContent = relatedList.ifEmpty { item.relatedContent }
                    )
                }
            } else if (item.source == ExploreSource.ANILIST || item.id.contains("anilist")) {
                // AniList detailed query
                val aniListId = item.id.substringAfterLast("_").toIntOrNull()
                if (aniListId != null) {
                    val query = """
                        query {
                          Media(id: $aniListId) {
                            id
                            idMal
                            description(asHtml: false)
                            episodes
                            duration
                            status
                            genres
                            averageScore
                            bannerImage
                            trailer {
                              id
                              site
                            }
                            studios(isMain: true) {
                              nodes {
                                name
                              }
                            }
                            characters(perPage: 12) {
                              edges {
                                role
                                node {
                                  name { full }
                                  image { medium }
                                }
                              }
                            }
                            reviews(perPage: 6, sort: [RATING_DESC]) {
                              nodes {
                                id
                                summary
                                body(asHtml: false)
                                score
                                createdAt
                                user {
                                  name
                                  avatar { medium }
                                }
                              }
                            }
                            recommendations(perPage: 8, sort: [RATING_DESC]) {
                              nodes {
                                mediaRecommendation {
                                  id
                                  title { english romaji }
                                  coverImage { large }
                                  averageScore
                                  seasonYear
                                  episodes
                                }
                              }
                            }
                          }
                        }
                    """.trimIndent()

                    val bodyJson = JSONObject()
                    bodyJson.put("query", query)
                    val req = Request.Builder()
                        .url("https://graphql.anilist.co")
                        .post(bodyJson.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .header("User-Agent", "Mozilla/5.0")
                        .build()
                    val resp = client.newCall(req).execute()
                    val body = resp.body?.string()
                    if (body != null) {
                        val mediaObj = JSONObject(body).optJSONObject("data")?.optJSONObject("Media")
                        if (mediaObj != null) {
                            val durationMins = mediaObj.optInt("duration", 0)
                            val durationText = if (durationMins > 0) "${durationMins}m / ep" else null
                            val studio = mediaObj.optJSONObject("studios")?.optJSONArray("nodes")?.optJSONObject(0)?.optString("name")
                            val trailerId = mediaObj.optJSONObject("trailer")?.optString("id")?.takeIf { it.isNotBlank() && it != "null" }
                            val banner = mediaObj.optString("bannerImage").takeIf { it.isNotBlank() && it != "null" }

                            // Characters / Cast
                            val castList = mutableListOf<CastMember>()
                            val charEdges = mediaObj.optJSONObject("characters")?.optJSONArray("edges")
                            if (charEdges != null) {
                                for (c in 0 until charEdges.length()) {
                                    val edge = charEdges.getJSONObject(c)
                                    val charName = edge.optJSONObject("node")?.optJSONObject("name")?.optString("full") ?: ""
                                    val role = edge.optString("role", "Main")
                                    val img = edge.optJSONObject("node")?.optJSONObject("image")?.optString("medium")
                                    if (charName.isNotBlank()) {
                                        castList.add(CastMember(name = charName, role = role, avatarUrl = img))
                                    }
                                }
                            }

                            // Reviews
                            val reviewsList = mutableListOf<MediaReviewItem>()
                            val reviewNodes = mediaObj.optJSONObject("reviews")?.optJSONArray("nodes")
                            if (reviewNodes != null) {
                                for (rv in 0 until reviewNodes.length()) {
                                    val rNode = reviewNodes.getJSONObject(rv)
                                    val rId = rNode.optInt("id", 0).toString()
                                    val userObj = rNode.optJSONObject("user")
                                    val author = userObj?.optString("name", "AniList User") ?: "AniList User"
                                    val avatar = userObj?.optJSONObject("avatar")?.optString("medium")
                                    val score = rNode.optInt("score", 0).toDouble() / 10.0
                                    val summary = rNode.optString("summary", "")
                                    val rBody = rNode.optString("body", "").trim()
                                    val fullContent = if (summary.isNotBlank()) "$summary\n\n$rBody" else rBody

                                    reviewsList.add(
                                        MediaReviewItem(
                                            id = rId,
                                            author = author,
                                            authorAvatarUrl = avatar,
                                            rating = if (score > 0) score else null,
                                            content = fullContent.take(1200),
                                            source = "AniList"
                                        )
                                    )
                                }
                            }

                            // Recommendations
                            val recsList = mutableListOf<ExploreMediaItem>()
                            val recNodes = mediaObj.optJSONObject("recommendations")?.optJSONArray("nodes")
                            if (recNodes != null) {
                                for (rc in 0 until recNodes.length()) {
                                    val rMedia = recNodes.getJSONObject(rc).optJSONObject("mediaRecommendation") ?: continue
                                    val recId = rMedia.optInt("id", 0)
                                    if (recId <= 0) continue
                                    val titleObj = rMedia.optJSONObject("title")
                                    val recTitle = titleObj?.optString("english")?.takeIf { it.isNotBlank() && it != "null" }
                                        ?: titleObj?.optString("romaji") ?: "Anime #$recId"
                                    val recPoster = rMedia.optJSONObject("coverImage")?.optString("large")
                                    val recScore = rMedia.optInt("averageScore", 0).toDouble() / 10.0
                                    val recYear = rMedia.optInt("seasonYear", 0).takeIf { it > 0 }?.toString() ?: "2024"
                                    val eps = rMedia.optInt("episodes", 0).takeIf { it > 0 }

                                    recsList.add(
                                        ExploreMediaItem(
                                            id = "anime_anilist_$recId",
                                            title = recTitle,
                                            mediaType = ExploreMediaType.ANIME,
                                            source = ExploreSource.ANILIST,
                                            posterUrl = recPoster,
                                            rating = recScore,
                                            ratingSource = "AniList",
                                            releaseYear = recYear,
                                            episodesCount = eps
                                        )
                                    )
                                }
                            }

                            // Clips / Trailer
                            val clips = mutableListOf<MediaClipItem>()
                            if (!trailerId.isNullOrBlank()) {
                                clips.add(
                                    MediaClipItem(
                                        id = trailerId,
                                        name = "${item.title} - Official Trailer",
                                        type = "Trailer",
                                        site = "YouTube",
                                        key = trailerId,
                                        thumbnailUrl = "https://i.ytimg.com/vi/$trailerId/hqdefault.jpg"
                                    )
                                )
                            }

                            return@withContext item.copy(
                                runtimeText = durationText ?: item.runtimeText,
                                studio = studio ?: item.studio,
                                trailerYoutubeId = trailerId ?: item.trailerYoutubeId,
                                backdropUrl = banner ?: item.backdropUrl,
                                cast = castList.ifEmpty { item.cast },
                                reviews = reviewsList.ifEmpty { item.reviews },
                                relatedContent = recsList.ifEmpty { item.relatedContent },
                                clipsAndTrailers = clips.ifEmpty { item.clipsAndTrailers },
                                screenshots = if (!banner.isNullOrBlank()) listOf(banner) else item.screenshots
                            )
                        }
                    }
                }
            } else if (item.source == ExploreSource.JIKAN || item.id.contains("jikan")) {
                // Jikan / MyAnimeList reviews & pictures
                val malId = item.id.substringAfterLast("_").toIntOrNull()
                if (malId != null) {
                    val reviewsUrl = "https://api.jikan.moe/v4/anime/$malId/reviews"
                    val reviewsReq = Request.Builder().url(reviewsUrl).header("User-Agent", "Mozilla/5.0").build()
                    val rResp = client.newCall(reviewsReq).execute()
                    val rBody = rResp.body?.string()
                    val reviewsList = mutableListOf<MediaReviewItem>()
                    if (rBody != null) {
                        val rArr = JSONObject(rBody).optJSONArray("data")
                        if (rArr != null) {
                            for (rv in 0 until minOf(rArr.length(), 6)) {
                                val rvObj = rArr.getJSONObject(rv)
                                val userObj = rvObj.optJSONObject("user")
                                val author = userObj?.optString("username", "MAL Reviewer") ?: "MAL Reviewer"
                                val avatar = userObj?.optJSONObject("images")?.optJSONObject("jpg")?.optString("image_url")
                                val score = rvObj.optDouble("score", 0.0)
                                val reviewText = rvObj.optString("review", "").trim()

                                if (reviewText.isNotBlank()) {
                                    reviewsList.add(
                                        MediaReviewItem(
                                            id = "mal_rv_$rv",
                                            author = author,
                                            authorAvatarUrl = avatar,
                                            rating = if (score > 0) score else null,
                                            content = reviewText.take(1200),
                                            source = "MyAnimeList"
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Screenshots / Pictures from Jikan
                    val picsUrl = "https://api.jikan.moe/v4/anime/$malId/pictures"
                    val picsReq = Request.Builder().url(picsUrl).header("User-Agent", "Mozilla/5.0").build()
                    val pResp = client.newCall(picsReq).execute()
                    val pBody = pResp.body?.string()
                    val screenshots = mutableListOf<String>()
                    if (pBody != null) {
                        val pArr = JSONObject(pBody).optJSONArray("data")
                        if (pArr != null) {
                            for (p in 0 until minOf(pArr.length(), 8)) {
                                val pObj = pArr.getJSONObject(p)
                                val jpgObj = pObj.optJSONObject("jpg")
                                val pUrl = jpgObj?.optString("large_image_url") ?: jpgObj?.optString("image_url")
                                if (!pUrl.isNullOrBlank()) screenshots.add(pUrl)
                            }
                        }
                    }

                    return@withContext item.copy(
                        reviews = reviewsList.ifEmpty { item.reviews },
                        screenshots = screenshots.ifEmpty { item.screenshots }
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving full media details", e)
        }
        item
    }

    // ==================== CURATED OFFLINE / INSTANT FALLBACKS ====================

    private fun getCuratedMovies(): List<ExploreMediaItem> {
        return listOf(
            ExploreMediaItem(
                id = "movie_693134",
                title = "Dune: Part Two",
                mediaType = ExploreMediaType.MOVIE,
                source = ExploreSource.TMDB,
                posterUrl = "https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/w1280/xOMo8BRK7PfcJv9JCnx7s520QIq.jpg",
                rating = 8.6,
                ratingSource = "IMDb",
                releaseYear = "2024",
                genres = listOf("Sci-Fi", "Adventure", "Action"),
                overview = "Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family.",
                tmdbId = "693134",
                imdbId = "tt15239678"
            ),
            ExploreMediaItem(
                id = "movie_872585",
                title = "Oppenheimer",
                mediaType = ExploreMediaType.MOVIE,
                source = ExploreSource.TMDB,
                posterUrl = "https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/w1280/rLb2cwF3Pazuxaj0sRXQ037tGI1.jpg",
                rating = 8.9,
                ratingSource = "IMDb",
                releaseYear = "2023",
                genres = listOf("Drama", "History", "Biography"),
                overview = "The story of J. Robert Oppenheimer’s role in the development of the atomic bomb during World War II.",
                tmdbId = "872585",
                imdbId = "tt15398776"
            ),
            ExploreMediaItem(
                id = "movie_533535",
                title = "Deadpool & Wolverine",
                mediaType = ExploreMediaType.MOVIE,
                source = ExploreSource.TMDB,
                posterUrl = "https://image.tmdb.org/t/p/w500/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/w1280/yDHYTfA3R0jFYba16jBB1ef8oIt.jpg",
                rating = 8.0,
                ratingSource = "TMDB",
                releaseYear = "2024",
                genres = listOf("Action", "Comedy", "Sci-Fi"),
                overview = "A listless Wade Wilson toils away in civilian life until a global threat pushes him into teaming up with Wolverine.",
                tmdbId = "533535",
                imdbId = "tt6263850"
            ),
            ExploreMediaItem(
                id = "movie_157336",
                title = "Interstellar",
                mediaType = ExploreMediaType.MOVIE,
                source = ExploreSource.TMDB,
                posterUrl = "https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/w1280/xJHokMbljvjADYdit5fK5VQsXEG.jpg",
                rating = 8.7,
                ratingSource = "IMDb",
                releaseYear = "2014",
                genres = listOf("Adventure", "Drama", "Sci-Fi"),
                overview = "A team of explorers travel through a wormhole in space in an attempt to ensure humanity's survival.",
                tmdbId = "157336",
                imdbId = "tt0816692"
            )
        )
    }

    private fun getCuratedTv(): List<ExploreMediaItem> {
        return listOf(
            ExploreMediaItem(
                id = "tv_94605",
                title = "Arcane",
                mediaType = ExploreMediaType.TV,
                source = ExploreSource.TMDB,
                posterUrl = "https://image.tmdb.org/t/p/w500/fqldf2t8ztc9aiwn396nlvupKR9.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/w1280/q8k1bA1l9P9ZqQZlFkUf6WcM2tY.jpg",
                rating = 9.0,
                ratingSource = "IMDb",
                releaseYear = "2024",
                genres = listOf("Animation", "Sci-Fi", "Action", "Drama"),
                overview = "Set in the utopian region of Piltover and the oppressed underground of Zaun, the story follows two iconic champions.",
                episodesCount = 18,
                tmdbId = "94605",
                imdbId = "tt11126994"
            ),
            ExploreMediaItem(
                id = "tv_1399",
                title = "Game of Thrones",
                mediaType = ExploreMediaType.TV,
                source = ExploreSource.TMDB,
                posterUrl = "https://image.tmdb.org/t/p/w500/1XS1oqL89opfnbLl8WnZY1O1uJx.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/w1280/2OMB0ynKlyIenMJWI2Dy9IWT4c.jpg",
                rating = 9.2,
                ratingSource = "IMDb",
                releaseYear = "2011",
                genres = listOf("Sci-Fi & Fantasy", "Drama", "Action"),
                overview = "Nine noble families fight for control over the lands of Westeros.",
                episodesCount = 73,
                tmdbId = "1399",
                imdbId = "tt0944947"
            )
        )
    }

    private fun getCuratedAnime(): List<ExploreMediaItem> {
        return listOf(
            ExploreMediaItem(
                id = "anime_anilist_16498",
                title = "Attack on Titan",
                originalTitle = "Shingeki no Kyojin",
                mediaType = ExploreMediaType.ANIME,
                source = ExploreSource.ANILIST,
                posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx16498-m5ZMNSczuSiO.png",
                backdropUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/banner/16498-735NioOJuhW5.jpg",
                rating = 9.1,
                ratingSource = "MAL",
                releaseYear = "2013",
                genres = listOf("Action", "Drama", "Fantasy", "Mystery"),
                overview = "Centuries ago, mankind was slaughtered to near extinction by monstrous humanoid creatures called Titans.",
                episodesCount = 89,
                studio = "WIT Studio / MAPPA"
            ),
            ExploreMediaItem(
                id = "anime_anilist_101922",
                title = "Demon Slayer: Kimetsu no Yaiba",
                originalTitle = "Kimetsu no Yaiba",
                mediaType = ExploreMediaType.ANIME,
                source = ExploreSource.ANILIST,
                posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx101922-PEn1CTbeUgqm.jpg",
                backdropUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/banner/101922-YfZhKLRqrZrP.jpg",
                rating = 8.7,
                ratingSource = "AniList",
                releaseYear = "2019",
                genres = listOf("Action", "Fantasy", "Supernatural"),
                overview = "A young man becomes a demon slayer after his family is slaughtered and his younger sister turned into a demon.",
                episodesCount = 55,
                studio = "ufotable"
            ),
            ExploreMediaItem(
                id = "anime_anilist_113415",
                title = "Jujutsu Kaisen",
                originalTitle = "Jujutsu Kaisen",
                mediaType = ExploreMediaType.ANIME,
                source = ExploreSource.ANILIST,
                posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx113415-bbBWj4pEFseh.jpg",
                backdropUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/banner/113415-jQBSkxWAAk83.jpg",
                rating = 8.8,
                ratingSource = "AniList",
                releaseYear = "2020",
                genres = listOf("Action", "Supernatural", "Dark Fantasy"),
                overview = "Yuji Itadori swallows a cursed talisman - the finger of a demon - and becomes cursed himself.",
                episodesCount = 47,
                studio = "MAPPA"
            ),
            ExploreMediaItem(
                id = "anime_anilist_154587",
                title = "Frieren: Beyond Journey's End",
                originalTitle = "Sousou no Frieren",
                mediaType = ExploreMediaType.ANIME,
                source = ExploreSource.ANILIST,
                posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx154587-n1HJZbbNmdf8.jpg",
                backdropUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/banner/154587-k81m4F9r1s00.jpg",
                rating = 9.3,
                ratingSource = "MAL",
                releaseYear = "2023",
                genres = listOf("Adventure", "Drama", "Fantasy"),
                overview = "An elf mage reflection upon the meaning of human life and friendship decades after defeating the Demon King.",
                episodesCount = 28,
                studio = "Madhouse"
            )
        )
    }
}
