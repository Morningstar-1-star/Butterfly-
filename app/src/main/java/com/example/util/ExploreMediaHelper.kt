package com.example.util

import android.util.Log
import com.example.model.CastMember
import com.example.model.ExploreMediaItem
import com.example.model.ExploreMediaType
import com.example.model.ExploreSection
import com.example.model.ExploreSource
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
    private const val TMDB_API_KEY = "a07e22bc18f5cb106bfe4cc1f83ad8ed"
    private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w500"
    private const val TMDB_BACKDROP_BASE = "https://image.tmdb.org/t/p/w1280"

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
        if (item.cast.isNotEmpty() && !item.trailerYoutubeId.isNullOrBlank()) {
            return@withContext item
        }

        try {
            if (item.source == ExploreSource.TMDB || item.tmdbId != null) {
                val tmdbId = item.tmdbId ?: item.id.substringAfter("_")
                val typeStr = if (item.mediaType == ExploreMediaType.TV) "tv" else "movie"
                val detailUrl = "https://api.themoviedb.org/3/$typeStr/$tmdbId?api_key=$TMDB_API_KEY&append_to_response=credits,videos,external_ids"

                val req = Request.Builder().url(detailUrl).header("User-Agent", "Mozilla/5.0").build()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string()
                if (body != null) {
                    val json = JSONObject(body)
                    val imdbId = json.optJSONObject("external_ids")?.optString("imdb_id")?.takeIf { it.isNotBlank() }

                    // Credits / Cast
                    val castList = mutableListOf<CastMember>()
                    val castArr = json.optJSONObject("credits")?.optJSONArray("cast")
                    if (castArr != null) {
                        for (c in 0 until minOf(castArr.length(), 16)) {
                            val castObj = castArr.getJSONObject(c)
                            val name = castObj.optString("name")
                            val character = castObj.optString("character")
                            val profilePath = castObj.optString("profile_path").takeIf { it.isNotBlank() }
                            val avatar = profilePath?.let { "$TMDB_IMAGE_BASE$it" }
                            val personId = castObj.optInt("id", 0).takeIf { it > 0 }
                            castList.add(CastMember(name = name, role = character, avatarUrl = avatar, personId = personId))
                        }
                    }

                    // Trailer
                    var trailerKey: String? = item.trailerYoutubeId
                    val videoResults = json.optJSONObject("videos")?.optJSONArray("results")
                    if (videoResults != null && trailerKey == null) {
                        for (v in 0 until videoResults.length()) {
                            val vidObj = videoResults.getJSONObject(v)
                            val site = vidObj.optString("site")
                            val type = vidObj.optString("type")
                            if (site.equals("YouTube", ignoreCase = true) && (type.equals("Trailer", ignoreCase = true) || type.equals("Teaser", ignoreCase = true))) {
                                trailerKey = vidObj.optString("key")
                                break
                            }
                        }
                    }

                    return@withContext item.copy(
                        imdbId = imdbId ?: item.imdbId,
                        cast = castList.ifEmpty { item.cast },
                        trailerYoutubeId = trailerKey ?: item.trailerYoutubeId
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
