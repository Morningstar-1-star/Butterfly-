package com.example.util

import android.util.Log
import com.example.model.CastMember
import com.example.model.EpisodeItem
import com.example.model.MediaDetailInfo
import com.example.model.SeriesSeason
import com.example.model.StreamData
import com.example.model.VideoTrailerClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object TMDBHelper {

    private val TAG = "TMDBHelper"
    private val TMDB_API_KEY get() = AppConfig.TMDB_API_KEY

    private val WEB_AND_ADULT_PROVIDERS = setOf(
        "youtube", "vimeo", "dailymotion", "bilibili",
        "pornhub", "xhamster", "xvideos", "eporner", "beeg",
        "youporn", "redtube", "4tube", "rule34video", "multisource"
    )

    fun isWebOrAdultProvider(providerId: String?): Boolean {
        if (providerId.isNullOrBlank()) return false
        val clean = providerId.trim().lowercase()
        return WEB_AND_ADULT_PROVIDERS.contains(clean) ||
                clean.contains("pornhub") || clean.contains("xhamster") ||
                clean.contains("xvideos") || clean.contains("eporner") ||
                clean.contains("adult")
    }

    fun shouldUseTmdb(
        providerId: String?,
        videoId: String?,
        isTrailer: Boolean = false,
        isMovieOrSeries: Boolean = false
    ): Boolean {
        if (isTrailer || isMovieOrSeries) return true

        if (!videoId.isNullOrBlank()) {
            val vId = videoId.lowercase()
            if (vId.startsWith("movie_") || vId.startsWith("tv_") || vId.startsWith("tt") ||
                vId.startsWith("tmdb_") || vId.startsWith("anilist_") || vId.startsWith("jikan_")) {
                return true
            }
        }

        if (isWebOrAdultProvider(providerId)) {
            return false
        }

        return true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    fun formatDateToLong(rawDate: String?): String {
        if (rawDate.isNullOrBlank()) return ""
        val trimmed = rawDate.trim()

        if (trimmed.contains("ago", ignoreCase = true) || trimmed.contains("views", ignoreCase = true)) {
            return ""
        }

        // Match ISO format YYYY-MM-DD
        val isoRegex = Regex("^(\\d{4})-(\\d{2})-(\\d{2})")
        val match = isoRegex.find(trimmed)
        if (match != null) {
            val (year, monthStr, dayStr) = match.destructured
            val monthInt = monthStr.toIntOrNull() ?: 1
            val dayInt = dayStr.toIntOrNull() ?: 1
            val monthNames = listOf(
                "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"
            )
            val monthName = monthNames.getOrElse(monthInt - 1) { "January" }
            return "$monthName $dayInt, $year"
        }

        // Match YYYY format
        if (trimmed.matches(Regex("^\\d{4}$"))) {
            return "Release Year: $trimmed"
        }

        return trimmed
    }

    fun cleanTitleForSearch(raw: String): String {
        var clean = raw
            .replace(Regex("\\[.*?\\]"), " ")
            .replace(Regex("\\(.*?\\)"), " ")
            .replace(Regex("(?i)s\\d+\\s*e\\d+.*"), " ")
            .replace(Regex("(?i)season\\s*\\d+.*"), " ")
            .replace(Regex("(?i)episode\\s*\\d+.*"), " ")
            .replace(Regex("(?i)ep\\s*\\d+.*"), " ")
            .replace(Regex("(?i)720p|1080p|2160p|4k|hdr|hd|web-dl|webrip|bluray|x264|x265|hevc|dvdrip|aac|h264|h265"), " ")
            .replace(Regex("\\b(19|20)\\d{2}\\b"), " ")
            .replace(Regex("[_.-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (clean.isBlank()) {
            clean = raw.takeWhile { it != '-' && it != '[' && it != '(' }.trim().ifEmpty { raw }
        }
        return clean
    }

    suspend fun fetchMediaDetails(
        rawTitle: String,
        videoId: String? = null,
        providerId: String? = null,
        forceTmdb: Boolean = false
    ): MediaDetailInfo? = withContext(Dispatchers.IO) {
        // Step 0: Check if title or videoId matches a JAV code and resolve via JAV Metadata & Enrichment pipeline
        val javCode = com.example.metadata.JavIdParser.parse(rawTitle)
            ?: (videoId?.let { com.example.metadata.JavIdParser.parse(it) })
        if (javCode != null) {
            try {
                val javMeta = com.example.metadata.JavMetadataResolver.resolve(javCode)
                if (javMeta != null) {
                    return@withContext javMeta.toMediaDetailInfo()
                }
            } catch (e: Exception) {
                Log.w(TAG, "JAV metadata resolution fallback for $javCode: ${e.message}")
            }
        }

        if (!forceTmdb && !shouldUseTmdb(providerId, videoId)) {
            Log.i(TAG, "Skipping TMDB auto-matching for web/adult provider: providerId=$providerId, title=$rawTitle")
            return@withContext null
        }

        val cleanTitle = cleanTitleForSearch(rawTitle)
        val lower = cleanTitle.lowercase()

        val liveTmdb = searchTmdbDetails(cleanTitle, videoId)
        if (liveTmdb != null) return@withContext liveTmdb

        if (isWebOrAdultProvider(providerId)) {
            return@withContext null
        }

        return@withContext getLocalDetailsForTitle(rawTitle, lower)
    }

    private fun searchTmdbDetails(cleanTitle: String, videoId: String? = null): MediaDetailInfo? {
        try {
            var mediaId = -1
            var mediaType = "movie"

            // 1. First attempt direct ID resolution from videoId if available
            if (!videoId.isNullOrBlank()) {
                if (videoId.startsWith("movie_")) {
                    mediaId = videoId.removePrefix("movie_").toIntOrNull() ?: -1
                    mediaType = "movie"
                } else if (videoId.startsWith("tv_")) {
                    mediaId = videoId.removePrefix("tv_").toIntOrNull() ?: -1
                    mediaType = "tv"
                } else if (videoId.startsWith("tt")) {
                    try {
                        val findUrl = "https://api.themoviedb.org/3/find/$videoId?api_key=$TMDB_API_KEY&external_source=imdb_id"
                        val req = Request.Builder().url(findUrl).header("User-Agent", "Mozilla/5.0").build()
                        val resp = client.newCall(req).execute()
                        val body = resp.body?.string()
                        if (body != null) {
                            val json = JSONObject(body)
                            val movies = json.optJSONArray("movie_results")
                            val tvs = json.optJSONArray("tv_results")
                            if (movies != null && movies.length() > 0) {
                                mediaId = movies.getJSONObject(0).optInt("id", -1)
                                mediaType = "movie"
                            } else if (tvs != null && tvs.length() > 0) {
                                mediaId = tvs.getJSONObject(0).optInt("id", -1)
                                mediaType = "tv"
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore lookup error
                    }
                }
            }

            // 2. Search TMDB multi if mediaId not found yet
            if (mediaId <= 0) {
                val queriesToTry = mutableListOf<String>()
                queriesToTry.add(cleanTitle)

                if (cleanTitle.contains(":") || cleanTitle.contains("-")) {
                    val shortTitle = cleanTitle.split(":", "-")[0].trim()
                    if (shortTitle.length >= 3 && shortTitle != cleanTitle) {
                        queriesToTry.add(shortTitle)
                    }
                }

                val words = cleanTitle.split(" ")
                if (words.size > 3) {
                    queriesToTry.add(words.take(3).joinToString(" "))
                }

                for (q in queriesToTry.distinct()) {
                    val encodedQuery = URLEncoder.encode(q, "UTF-8")
                    val searchUrl = "https://api.themoviedb.org/3/search/multi?api_key=$TMDB_API_KEY&query=$encodedQuery"

                    val request = Request.Builder()
                        .url(searchUrl)
                        .header("User-Agent", "Mozilla/5.0")
                        .build()

                    val response = client.newCall(request).execute()
                    val bodyString = response.body?.string() ?: continue

                    val searchJson = JSONObject(bodyString)
                    val results = searchJson.optJSONArray("results") ?: continue

                    for (i in 0 until results.length()) {
                        val item = results.getJSONObject(i)
                        val type = item.optString("media_type")
                        if (type == "movie" || type == "tv") {
                            mediaId = item.optInt("id", -1)
                            mediaType = type
                            break
                        }
                    }
                    if (mediaId != -1) break
                }
            }

            if (mediaId <= 0) return null

            val detailUrl = "https://api.themoviedb.org/3/$mediaType/$mediaId?api_key=$TMDB_API_KEY&append_to_response=credits,images,videos&include_image_language=en,null"
            val detailReq = Request.Builder()
                .url(detailUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val detailResp = client.newCall(detailReq).execute()
            val detailBody = detailResp.body?.string() ?: return null

            val detailJson = JSONObject(detailBody)

            val canonicalTitle = if (mediaType == "movie") {
                detailJson.optString("title", cleanTitle)
            } else {
                detailJson.optString("name", cleanTitle)
            }.ifBlank { cleanTitle }

            val overview = detailJson.optString("overview", "").ifBlank {
                "Explore full details, cast, and high-definition direct stream sources for $canonicalTitle."
            }

            val rawReleaseDate = if (mediaType == "movie") {
                detailJson.optString("release_date")
            } else {
                detailJson.optString("first_air_date")
            }
            val formattedDate = formatDateToLong(rawReleaseDate)

            val voteAvg = detailJson.optDouble("vote_average", 0.0)
            val ratingText = if (voteAvg > 0) "★ ${String.format(java.util.Locale.US, "%.1f", voteAvg)} / 10 TMDB" else "TMDB Rated"

            val studios = mutableListOf<String>()
            val prodCompanies = detailJson.optJSONArray("production_companies")
            if (prodCompanies != null) {
                for (i in 0 until minOf(prodCompanies.length(), 2)) {
                    val comp = prodCompanies.getJSONObject(i)
                    val name = comp.optString("name")
                    if (name.isNotBlank()) studios.add(name)
                }
            }
            val networks = detailJson.optJSONArray("networks")
            if (networks != null) {
                for (i in 0 until minOf(networks.length(), 2)) {
                    val net = networks.getJSONObject(i)
                    val name = net.optString("name")
                    if (name.isNotBlank() && !studios.contains(name)) studios.add(name)
                }
            }
            val studioText = if (studios.isNotEmpty()) studios.joinToString(" / ") else ""

            val genresList = mutableListOf<String>()
            val genresArr = detailJson.optJSONArray("genres")
            if (genresArr != null) {
                for (i in 0 until genresArr.length()) {
                    val gName = genresArr.getJSONObject(i).optString("name")
                    if (gName.isNotBlank()) genresList.add(gName)
                }
            }

            var director = ""
            var writer = ""

            val creditsObj = detailJson.optJSONObject("credits")
            if (creditsObj != null) {
                val crewArr = creditsObj.optJSONArray("crew")
                if (crewArr != null) {
                    val directors = mutableListOf<String>()
                    val writers = mutableListOf<String>()

                    for (i in 0 until crewArr.length()) {
                        val member = crewArr.getJSONObject(i)
                        val job = member.optString("job")
                        val name = member.optString("name")
                        val dept = member.optString("department")

                        if (job.equals("Director", ignoreCase = true) && name.isNotBlank()) {
                            if (!directors.contains(name)) directors.add(name)
                        } else if ((job.equals("Writer", ignoreCase = true) || dept.equals("Writing", ignoreCase = true)) && name.isNotBlank()) {
                            if (!writers.contains(name)) writers.add(name)
                        }
                    }

                    if (directors.isNotEmpty()) director = directors.take(2).joinToString(" / ")
                    if (writers.isNotEmpty()) writer = writers.take(2).joinToString(" / ")
                }
            }

            val castList = mutableListOf<CastMember>()
            if (creditsObj != null) {
                val castArr = creditsObj.optJSONArray("cast")
                if (castArr != null) {
                    for (i in 0 until minOf(castArr.length(), 16)) {
                        val cObj = castArr.getJSONObject(i)
                        val name = cObj.optString("name")
                        val character = cObj.optString("character", "Cast")
                        val profilePath = cObj.optString("profile_path")
                        val avatarUrl = if (profilePath.isNotBlank() && profilePath != "null") {
                            "https://image.tmdb.org/t/p/w185$profilePath"
                        } else null

                        if (name.isNotBlank()) {
                            val pId = cObj.optInt("id", -1).takeIf { it > 0 }
                            castList.add(
                                CastMember(
                                    name = name,
                                    role = character.ifBlank { "Actor" },
                                    avatarUrl = avatarUrl,
                                    personId = pId
                                )
                            )
                        }
                    }
                }
            }

            val screenshotsList = mutableListOf<String>()
            val imagesObj = detailJson.optJSONObject("images")
            if (imagesObj != null) {
                val backdropsArr = imagesObj.optJSONArray("backdrops")
                if (backdropsArr != null) {
                    for (i in 0 until minOf(backdropsArr.length(), 12)) {
                        val filePath = backdropsArr.getJSONObject(i).optString("file_path")
                        if (filePath.isNotBlank() && filePath != "null") {
                            screenshotsList.add("https://image.tmdb.org/t/p/w780$filePath")
                        }
                    }
                }
            }

            // Season 1 stills for TV shows if backdrops are few
            if (screenshotsList.size < 3 && mediaType == "tv") {
                try {
                    val seasonUrl = "https://api.themoviedb.org/3/tv/$mediaId/season/1?api_key=$TMDB_API_KEY"
                    val sReq = Request.Builder().url(seasonUrl).header("User-Agent", "Mozilla/5.0").build()
                    val sResp = client.newCall(sReq).execute()
                    val sBody = sResp.body?.string()
                    if (sBody != null) {
                        val sJson = JSONObject(sBody)
                        val epArr = sJson.optJSONArray("episodes")
                        if (epArr != null) {
                            for (i in 0 until minOf(epArr.length(), 12)) {
                                val stillPath = epArr.getJSONObject(i).optString("still_path")
                                if (stillPath.isNotBlank() && stillPath != "null") {
                                    screenshotsList.add("https://image.tmdb.org/t/p/w780$stillPath")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore season stills error
                }
            }

            if (screenshotsList.isEmpty()) {
                val backdropPath = detailJson.optString("backdrop_path")
                if (backdropPath.isNotBlank() && backdropPath != "null") {
                    screenshotsList.add("https://image.tmdb.org/t/p/w780$backdropPath")
                }
                val posterPath = detailJson.optString("poster_path")
                if (posterPath.isNotBlank() && posterPath != "null") {
                    screenshotsList.add("https://image.tmdb.org/t/p/w780$posterPath")
                }
            }

            val clipsList = mutableListOf<VideoTrailerClip>()
            val videosObj = detailJson.optJSONObject("videos")
            if (videosObj != null) {
                val vArr = videosObj.optJSONArray("results")
                if (vArr != null) {
                    for (i in 0 until vArr.length()) {
                        val vItem = vArr.getJSONObject(i)
                        val site = vItem.optString("site")
                        val key = vItem.optString("key")
                        val name = vItem.optString("name")
                        val type = vItem.optString("type", "Trailer")

                        if (site.equals("YouTube", ignoreCase = true) && key.isNotBlank()) {
                            val thumb = "https://i.ytimg.com/vi/$key/hqdefault.jpg"
                            clipsList.add(
                                VideoTrailerClip(
                                    title = name.ifBlank { "$canonicalTitle $type" },
                                    youtubeKey = key,
                                    thumbnailUrl = thumb,
                                    clipType = type
                                )
                            )
                        }
                    }
                }
            }

            if (clipsList.isEmpty()) {
                try {
                    val vUrl = "https://api.themoviedb.org/3/$mediaType/$mediaId/videos?api_key=$TMDB_API_KEY"
                    val vReq = Request.Builder().url(vUrl).header("User-Agent", "Mozilla/5.0").build()
                    val vResp = client.newCall(vReq).execute()
                    val vBody = vResp.body?.string()
                    if (vBody != null) {
                        val vJson = JSONObject(vBody)
                        val vArr = vJson.optJSONArray("results")
                        if (vArr != null) {
                            for (i in 0 until vArr.length()) {
                                val vItem = vArr.getJSONObject(i)
                                val site = vItem.optString("site")
                                val key = vItem.optString("key")
                                val name = vItem.optString("name")
                                val type = vItem.optString("type", "Trailer")

                                if (site.equals("YouTube", ignoreCase = true) && key.isNotBlank()) {
                                    val thumb = "https://i.ytimg.com/vi/$key/hqdefault.jpg"
                                    clipsList.add(
                                        VideoTrailerClip(
                                            title = name.ifBlank { "$canonicalTitle $type" },
                                            youtubeKey = key,
                                            thumbnailUrl = thumb,
                                            clipType = type
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }

            if (clipsList.isEmpty()) {
                val backdropPath = detailJson.optString("backdrop_path")
                val thumb = if (backdropPath.isNotBlank() && backdropPath != "null") {
                    "https://image.tmdb.org/t/p/w780$backdropPath"
                } else null

                clipsList.add(
                    VideoTrailerClip(
                        title = "$canonicalTitle - Official Teaser Trailer",
                        youtubeKey = null,
                        thumbnailUrl = thumb,
                        durationText = "",
                        clipType = "Official Trailer"
                    )
                )
            }

            val budgetLong = detailJson.optLong("budget", 0L)
            val revenueLong = detailJson.optLong("revenue", 0L)
            val budgetText = if (budgetLong > 0) "$%,d".format(java.util.Locale.US, budgetLong) else null
            val revenueText = if (revenueLong > 0) "$%,d".format(java.util.Locale.US, revenueLong) else null
            val statusText = detailJson.optString("status").takeIf { it.isNotBlank() }

            return MediaDetailInfo(
                title = canonicalTitle,
                plotOverview = overview,
                releaseDateFormatted = formattedDate,
                ratingText = ratingText,
                director = director,
                writer = writer,
                studioOrCollection = studioText,
                genres = genresList,
                cast = castList,
                screenshots = screenshotsList,
                clipsAndTrailers = clipsList,
                budget = budgetText,
                revenue = revenueText,
                boxOffice = revenueText,
                status = statusText
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching TMDB media details for $cleanTitle: ${e.message}")
            return null
        }
    }

    suspend fun fetchCast(rawTitle: String): List<CastMember> = withContext(Dispatchers.IO) {
        val details = fetchMediaDetails(rawTitle)
        return@withContext details?.cast ?: emptyList()
    }

    suspend fun fetchFilmographyForPerson(personName: String, personId: Int?): List<com.example.model.CastFilmographyItem> = withContext(Dispatchers.IO) {
        try {
            var targetPersonId = personId ?: -1
            if (targetPersonId <= 0) {
                val encodedQuery = URLEncoder.encode(personName, "UTF-8")
                val searchUrl = "https://api.themoviedb.org/3/search/person?api_key=$TMDB_API_KEY&query=$encodedQuery"
                val req = Request.Builder().url(searchUrl).header("User-Agent", "Mozilla/5.0").build()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                val results = json.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    targetPersonId = results.getJSONObject(0).optInt("id", -1)
                }
            }

            if (targetPersonId <= 0) return@withContext emptyList()

            val creditsUrl = "https://api.themoviedb.org/3/person/$targetPersonId/combined_credits?api_key=$TMDB_API_KEY"
            val req = Request.Builder().url(creditsUrl).header("User-Agent", "Mozilla/5.0").build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val castArr = json.optJSONArray("cast") ?: return@withContext emptyList()

            val filmography = mutableListOf<com.example.model.CastFilmographyItem>()
            for (i in 0 until castArr.length()) {
                val item = castArr.getJSONObject(i)
                val mId = item.optInt("id", -1)
                val mediaType = item.optString("media_type", "movie")
                val title = if (mediaType == "movie") item.optString("title") else item.optString("name")
                if (title.isBlank()) continue

                val posterPath = item.optString("poster_path")
                val backdropPath = item.optString("backdrop_path")
                val releaseDate = if (mediaType == "movie") item.optString("release_date") else item.optString("first_air_date")
                val year = releaseDate.take(4).ifBlank { "2024" }
                val character = item.optString("character", "Cast")
                val voteAvg = item.optDouble("vote_average", 7.5)

                val posterUrl = if (posterPath.isNotBlank() && posterPath != "null") "https://image.tmdb.org/t/p/w342$posterPath" else null
                val backdropUrl = if (backdropPath.isNotBlank() && backdropPath != "null") "https://image.tmdb.org/t/p/w780$backdropPath" else null

                filmography.add(
                    com.example.model.CastFilmographyItem(
                        id = "${mediaType}_$mId",
                        title = title,
                        mediaType = mediaType,
                        posterUrl = posterUrl,
                        backdropUrl = backdropUrl,
                        releaseYear = year,
                        character = character,
                        voteAverage = voteAvg
                    )
                )
            }

            return@withContext filmography.distinctBy { it.title }.take(30)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching filmography for $personName: ${e.message}")
            return@withContext emptyList()
        }
    }

    private fun getLocalDetailsForTitle(rawTitle: String, lower: String): MediaDetailInfo {
        val clean = cleanTitleForSearch(rawTitle)
        return MediaDetailInfo(
            title = clean.ifBlank { "Media Item" },
            plotOverview = "Metadata unavailable.",
            releaseDateFormatted = "",
            ratingText = "",
            director = "",
            writer = "",
            studioOrCollection = "",
            genres = emptyList(),
            cast = emptyList(),
            screenshots = emptyList(),
            clipsAndTrailers = emptyList()
        )
    }

    private fun getLocalCastForTitle(lowerTitle: String): List<CastMember> {
        return emptyList()
    }

    private val tvSeasonsCache = ConcurrentHashMap<String, List<SeriesSeason>>()

    suspend fun fetchTvSeasonsAndEpisodes(streamData: StreamData): List<SeriesSeason> = withContext(Dispatchers.IO) {
        if (streamData.title.isBlank()) {
            return@withContext emptyList()
        }
        val rawTitle = streamData.title
        val videoId = streamData.videoId
        val cleanTitle = cleanTitleForSearch(rawTitle)
        val providerId = (streamData.providerId ?: "tmdb").lowercase()
        val fallbackThumb = streamData.effectiveThumbnailUrl ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        val isTorrentOrVega = providerId == "torrent" || providerId == "vega" || providerId.startsWith("vega_")
        val isArchiveMulti = (providerId == "archive" || providerId == "archive_org" || providerId == "archive.org") && streamData.availableStreamOptions.size > 1

        if (!isTorrentOrVega && !isArchiveMulti) {
            return@withContext emptyList()
        }

        if (isArchiveMulti) {
            val archiveSeasons = parseSeasonsFromStreamOptions(streamData)
            return@withContext archiveSeasons
        }

        if (isTorrentOrVega) {
            val titleLower = rawTitle.lowercase()
            val isTvSeries = titleLower.contains("season") || titleLower.contains("s0") ||
                    titleLower.contains("s1") || titleLower.contains("s2") ||
                    titleLower.contains("episode") || titleLower.contains("ep0") ||
                    titleLower.contains(" complete ") || videoId.contains("tv_")
            if (!isTvSeries) {
                return@withContext emptyList()
            }
        }

        val cacheKey = "${cleanTitle.lowercase()}_$videoId"
        tvSeasonsCache[cacheKey]?.let { return@withContext it }

        try {
            var tvId: Int? = null

            // 1. Check videoId for explicit tv_ ID or IMDb tt ID
            if (videoId.isNotBlank()) {
                if (videoId.startsWith("tv_")) {
                    tvId = videoId.removePrefix("tv_").substringBefore("_").toIntOrNull()
                } else if (videoId.startsWith("tt")) {
                    val findUrl = "https://api.themoviedb.org/3/find/$videoId?api_key=$TMDB_API_KEY&external_source=imdb_id"
                    val req = Request.Builder().url(findUrl).header("User-Agent", "Mozilla/5.0").build()
                    val resp = client.newCall(req).execute()
                    val body = resp.body?.string()
                    if (body != null) {
                        val json = JSONObject(body)
                        val tvResults = json.optJSONArray("tv_results")
                        if (tvResults != null && tvResults.length() > 0) {
                            tvId = tvResults.getJSONObject(0).optInt("id")
                        }
                    }
                }
            }

            // 2. Search TMDB TV database by clean title
            if (tvId == null && cleanTitle.isNotBlank()) {
                val encoded = URLEncoder.encode(cleanTitle, "UTF-8")
                val searchUrl = "https://api.themoviedb.org/3/search/tv?api_key=$TMDB_API_KEY&query=$encoded&page=1"
                val req = Request.Builder().url(searchUrl).header("User-Agent", "Mozilla/5.0").build()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string()
                if (body != null) {
                    val json = JSONObject(body)
                    val results = json.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        tvId = results.getJSONObject(0).optInt("id")
                    }
                }
            }

            if (tvId != null && tvId > 0) {
                // Fetch TV show detail to get season list
                val detailUrl = "https://api.themoviedb.org/3/tv/$tvId?api_key=$TMDB_API_KEY"
                val dReq = Request.Builder().url(detailUrl).header("User-Agent", "Mozilla/5.0").build()
                val dResp = client.newCall(dReq).execute()
                val dBody = dResp.body?.string()
                if (dBody != null) {
                    val dJson = JSONObject(dBody)
                    val seasonsArr = dJson.optJSONArray("seasons")
                    val resultSeasons = mutableListOf<SeriesSeason>()

                    if (seasonsArr != null && seasonsArr.length() > 0) {
                        for (i in 0 until seasonsArr.length()) {
                            val seasonObj = seasonsArr.getJSONObject(i)
                            val seasonNum = seasonObj.optInt("season_number", i + 1)
                            if (seasonNum <= 0 && seasonsArr.length() > 1) continue // Skip Specials unless only season

                            val seasonName = seasonObj.optString("name", "Season $seasonNum")

                            // Fetch episodes for this season
                            val seasonUrl = "https://api.themoviedb.org/3/tv/$tvId/season/$seasonNum?api_key=$TMDB_API_KEY"
                            val sReq = Request.Builder().url(seasonUrl).header("User-Agent", "Mozilla/5.0").build()
                            val sResp = client.newCall(sReq).execute()
                            val sBody = sResp.body?.string()

                            val episodeList = mutableListOf<EpisodeItem>()
                            if (sBody != null) {
                                val sJson = JSONObject(sBody)
                                val epArr = sJson.optJSONArray("episodes")
                                if (epArr != null) {
                                    for (j in 0 until epArr.length()) {
                                        val epObj = epArr.getJSONObject(j)
                                        val epNum = epObj.optInt("episode_number", j + 1)
                                        val epName = epObj.optString("name", "Episode $epNum")
                                        val stillPath = epObj.optString("still_path")
                                        val voteAvg = epObj.optDouble("vote_average", 8.0)
                                        val runtime = epObj.optInt("runtime", 0)

                                        val epThumb = if (stillPath.isNotBlank() && stillPath != "null") {
                                            "https://image.tmdb.org/t/p/w500$stillPath"
                                        } else fallbackThumb

                                        val epId = "tv_${tvId}_s${seasonNum}_e${epNum}"

                                        episodeList.add(
                                            EpisodeItem(
                                                id = epId,
                                                seasonNumber = seasonNum,
                                                episodeNumber = epNum,
                                                title = epName.ifBlank { "Episode $epNum" },
                                                durationText = if (runtime > 0) "${runtime}m" else "",
                                                thumbnailUrl = epThumb,
                                                providerId = providerId,
                                                viewsText = "★ ${String.format("%.1f", voteAvg)} IMDb"
                                            )
                                        )
                                    }
                                }
                            }

                            if (episodeList.isNotEmpty()) {
                                resultSeasons.add(SeriesSeason(seasonNum, seasonName, episodeList))
                            }
                        }
                    }

                    if (resultSeasons.isNotEmpty()) {
                        val boundSeasons = bindAvailableStreamOptionsToSeasons(resultSeasons, streamData)
                        tvSeasonsCache[cacheKey] = boundSeasons
                        return@withContext boundSeasons
                    }
                }
            }

            // TVmaze API Fallback
            if (cleanTitle.isNotBlank()) {
                val encodedTitle = URLEncoder.encode(cleanTitle, "UTF-8")
                val mazeUrl = "https://api.tvmaze.com/singlesearch/shows?q=$encodedTitle&embed=episodes"
                val mReq = Request.Builder().url(mazeUrl).header("User-Agent", "Mozilla/5.0").build()
                val mResp = client.newCall(mReq).execute()
                val mBody = mResp.body?.string()
                if (mBody != null) {
                    val mJson = JSONObject(mBody)
                    val showName = mJson.optString("name", cleanTitle)
                    val embedded = mJson.optJSONObject("_embedded")
                    val episodesArr = embedded?.optJSONArray("episodes")

                    if (episodesArr != null && episodesArr.length() > 0) {
                        val seasonMap = mutableMapOf<Int, MutableList<EpisodeItem>>()
                        for (idx in 0 until episodesArr.length()) {
                            val epObj = episodesArr.getJSONObject(idx)
                            val sNum = epObj.optInt("season", 1)
                            val eNum = epObj.optInt("number", idx + 1)
                            val name = epObj.optString("name", "Episode $eNum")
                            val runtime = epObj.optInt("runtime", 0)
                            val imageObj = epObj.optJSONObject("image")
                            val epThumb = imageObj?.optString("medium") ?: fallbackThumb
                            val ratingObj = epObj.optJSONObject("rating")
                            val ratingVal = ratingObj?.optDouble("average", 8.4) ?: 8.4

                            val epItem = EpisodeItem(
                                id = "${cleanTitle.lowercase().replace(" ", "_")}_s${sNum}_e${eNum}",
                                seasonNumber = sNum,
                                episodeNumber = eNum,
                                title = name,
                                durationText = if (runtime > 0) "${runtime}m" else "",
                                thumbnailUrl = epThumb,
                                providerId = providerId,
                                viewsText = "★ ${String.format("%.1f", ratingVal)} IMDb"
                            )

                            seasonMap.getOrPut(sNum) { mutableListOf() }.add(epItem)
                        }

                        val mazeSeasons = seasonMap.map { (sNum, epList) ->
                            SeriesSeason(sNum, "Season $sNum", epList)
                        }.sortedBy { it.seasonNumber }

                        if (mazeSeasons.isNotEmpty()) {
                            val boundSeasons = bindAvailableStreamOptionsToSeasons(mazeSeasons, streamData)
                            tvSeasonsCache[cacheKey] = boundSeasons
                            return@withContext boundSeasons
                        }
                    }
                }
            }
            // 3. Fallback: Parse seasons and episodes directly from availableStreamOptions (e.g. for Archive.org multi-episode collections like Spider-Man 1994, animated series, etc.)
            if (streamData.availableStreamOptions.size > 1) {
                val archiveSeasons = parseSeasonsFromStreamOptions(streamData)
                if (archiveSeasons.isNotEmpty()) {
                    tvSeasonsCache[cacheKey] = archiveSeasons
                    return@withContext archiveSeasons
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in fetchTvSeasonsAndEpisodes for $cleanTitle", e)
        }

        // Final check on stream options directly
        if (streamData.availableStreamOptions.size > 1) {
            val directSeasons = parseSeasonsFromStreamOptions(streamData)
            if (directSeasons.isNotEmpty()) {
                tvSeasonsCache[cacheKey] = directSeasons
                return@withContext directSeasons
            }
        }

        val fallback = emptyList<SeriesSeason>()
        return@withContext fallback
    }

    /**
     * Parses season and episode hierarchy directly from stream options (e.g. "01x01 Night of the Lizard", "S01E02", etc.)
     */
    private fun parseSeasonsFromStreamOptions(streamData: StreamData): List<SeriesSeason> {
        val options = streamData.availableStreamOptions
        if (options.isEmpty()) return emptyList()

        val seasonMap = mutableMapOf<Int, MutableList<EpisodeItem>>()
        val epPattern = Regex("(?i)(?:s(\\d{1,2})[._\\s-]*e(\\d{1,3}))|(?:(\\d{1,2})[xX](\\d{1,3}))|(?:season[._\\s-]*(\\d{1,2})[._\\s-]*episode[._\\s-]*(\\d{1,3}))|(?:(?:ep|episode)[._\\s-]*(\\d{1,3}))")

        for ((idx, opt) in options.withIndex()) {
            val label = opt.qualityLabel.trim()
            val match = epPattern.find(label)

            var sNum = 1
            var eNum = idx + 1

            if (match != null) {
                val g = match.groupValues
                when {
                    g[1].isNotEmpty() && g[2].isNotEmpty() -> {
                        sNum = g[1].toIntOrNull() ?: 1
                        eNum = g[2].toIntOrNull() ?: (idx + 1)
                    }
                    g[3].isNotEmpty() && g[4].isNotEmpty() -> {
                        sNum = g[3].toIntOrNull() ?: 1
                        eNum = g[4].toIntOrNull() ?: (idx + 1)
                    }
                    g[5].isNotEmpty() && g[6].isNotEmpty() -> {
                        sNum = g[5].toIntOrNull() ?: 1
                        eNum = g[6].toIntOrNull() ?: (idx + 1)
                    }
                    g[7].isNotEmpty() -> {
                        eNum = g[7].toIntOrNull() ?: (idx + 1)
                    }
                }
            }

            // Clean title for display
            var epTitle = label
                .replace(Regex("(?i)\\(\\d+p.*?\\)"), "")
                .replace(Regex("(?i)\\[.*?\\]"), "")
                .replace(Regex("(?i)\\.ia\\.mp4|\\.mp4|\\.mkv|\\.avi"), "")
                .replace("_", " ")
                .trim()

            if (match != null) {
                epTitle = epTitle.removePrefix(match.value).trim()
                if (epTitle.startsWith("-") || epTitle.startsWith(".")) {
                    epTitle = epTitle.substring(1).trim()
                }
            }

            if (epTitle.isBlank()) {
                epTitle = "Episode $eNum"
            }

            val epItem = EpisodeItem(
                id = opt.videoUrl ?: "${streamData.videoId}_s${sNum}_e${eNum}",
                seasonNumber = sNum,
                episodeNumber = eNum,
                title = epTitle,
                durationText = if (opt.format.isNotBlank()) opt.format.uppercase() else "Direct",
                thumbnailUrl = streamData.effectiveThumbnailUrl,
                providerId = streamData.providerId ?: "archive_org",
                viewsText = "Direct Stream"
            )

            seasonMap.getOrPut(sNum) { mutableListOf() }.add(epItem)
        }

        return seasonMap.map { (sNum, eps) ->
            SeriesSeason(
                seasonNumber = sNum,
                seasonName = "Season $sNum",
                episodes = eps.sortedBy { it.episodeNumber }
            )
        }.sortedBy { it.seasonNumber }
    }

    /**
     * Fetches authentic TMDB / IMDb reviews and ratings for movies, series, or torrents
     */
    suspend fun fetchTmdbReviews(
        title: String,
        videoId: String? = null,
        providerId: String? = null
    ): List<com.example.model.VideoComment> = withContext(Dispatchers.IO) {
        val cleanTitle = cleanTitleForSearch(title)
        if (cleanTitle.isBlank() && videoId.isNullOrBlank()) return@withContext emptyList()

        try {
            var mediaId: Int? = null
            var mediaType = "movie"

            // 1. Check videoId for explicit ID
            if (!videoId.isNullOrBlank()) {
                if (videoId.startsWith("movie_")) {
                    mediaId = videoId.removePrefix("movie_").toIntOrNull()
                    mediaType = "movie"
                } else if (videoId.startsWith("tv_")) {
                    mediaId = videoId.removePrefix("tv_").substringBefore("_").toIntOrNull()
                    mediaType = "tv"
                } else if (videoId.startsWith("tt")) {
                    val findUrl = "https://api.themoviedb.org/3/find/$videoId?api_key=$TMDB_API_KEY&external_source=imdb_id"
                    val req = Request.Builder().url(findUrl).header("User-Agent", "Mozilla/5.0").build()
                    val resp = client.newCall(req).execute()
                    val body = resp.body?.string()
                    if (body != null) {
                        val json = JSONObject(body)
                        val movies = json.optJSONArray("movie_results")
                        val tvs = json.optJSONArray("tv_results")
                        if (movies != null && movies.length() > 0) {
                            mediaId = movies.getJSONObject(0).optInt("id")
                            mediaType = "movie"
                        } else if (tvs != null && tvs.length() > 0) {
                            mediaId = tvs.getJSONObject(0).optInt("id")
                            mediaType = "tv"
                        }
                    }
                }
            }

            // 2. Search TMDB Multi if ID not found
            if (mediaId == null && cleanTitle.isNotBlank()) {
                val encodedQuery = URLEncoder.encode(cleanTitle, "UTF-8")
                val searchUrl = "https://api.themoviedb.org/3/search/multi?api_key=$TMDB_API_KEY&query=$encodedQuery"
                val req = Request.Builder().url(searchUrl).header("User-Agent", "Mozilla/5.0").build()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string()
                if (body != null) {
                    val json = JSONObject(body)
                    val results = json.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        for (i in 0 until results.length()) {
                            val item = results.getJSONObject(i)
                            val type = item.optString("media_type")
                            if (type == "movie" || type == "tv") {
                                mediaId = item.optInt("id")
                                mediaType = type
                                break
                            }
                        }
                    }
                }
            }

            if (mediaId != null && mediaId > 0) {
                val reviewsUrl = "https://api.themoviedb.org/3/$mediaType/$mediaId/reviews?api_key=$TMDB_API_KEY&page=1"
                val req = Request.Builder().url(reviewsUrl).header("User-Agent", "Mozilla/5.0").build()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string()
                if (body != null) {
                    val json = JSONObject(body)
                    val results = json.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        val reviewList = mutableListOf<com.example.model.VideoComment>()
                        for (i in 0 until results.length().coerceAtMost(30)) {
                            val obj = results.getJSONObject(i)
                            val author = obj.optString("author", "Verified Reviewer")
                            val content = obj.optString("content", "")
                            if (content.isBlank()) continue

                            val authorDetails = obj.optJSONObject("author_details")
                            val ratingVal = authorDetails?.optDouble("rating", 0.0) ?: 0.0
                            var avatarPath = authorDetails?.optString("avatar_path", null)
                            if (avatarPath != null && avatarPath != "null") {
                                avatarPath = if (avatarPath.startsWith("http")) {
                                    avatarPath.removePrefix("/")
                                } else {
                                    "https://image.tmdb.org/t/p/w185$avatarPath"
                                }
                            } else {
                                avatarPath = null
                            }

                            val createdAt = obj.optString("created_at", "")
                            val timeAgo = if (createdAt.length >= 10) createdAt.take(10) else "Recently"
                            val reviewUrl = obj.optString("url", null)

                            val ratingBadge = if (ratingVal > 0.0) "${String.format("%.1f", ratingVal)}/10 ★" else null

                            reviewList.add(
                                com.example.model.VideoComment(
                                    id = obj.optString("id", "tmdb_rev_$i"),
                                    authorName = author,
                                    authorAvatarUrl = avatarPath,
                                    commentText = content,
                                    timeAgo = timeAgo,
                                    likeCount = if (ratingVal > 0) (ratingVal * 12).toInt() else 24,
                                    rating = if (ratingVal > 0) (ratingVal / 2.0).toFloat() else null,
                                    ratingText = ratingBadge,
                                    sourceBadge = "IMDb / TMDB Review",
                                    reviewUrl = reviewUrl
                                )
                            )
                        }
                        if (reviewList.isNotEmpty()) {
                            return@withContext reviewList
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching TMDB reviews for $cleanTitle: ${e.message}")
        }

        return@withContext emptyList()
    }

    private fun bindAvailableStreamOptionsToSeasons(
        seasons: List<SeriesSeason>,
        streamData: StreamData
    ): List<SeriesSeason> {
        val options = streamData.availableStreamOptions
        val provider = streamData.providerId ?: ""
        if (options.isEmpty() && provider != "archive_org") return seasons

        var globalIndex = 0
        return seasons.map { season ->
            val updatedEps = season.episodes.map { ep ->
                val targetUrl = if (globalIndex < options.size && !options[globalIndex].videoUrl.isNullOrBlank()) {
                    options[globalIndex].videoUrl!!
                } else if (provider == "archive_org" && streamData.videoId.isNotBlank()) {
                    "https://archive.org/download/${streamData.videoId}::${globalIndex + 1}"
                } else {
                    ep.id
                }
                globalIndex++
                ep.copy(id = targetUrl)
            }
            season.copy(episodes = updatedEps)
        }
    }

    suspend fun fetchExploreCategoryMovies(genreId: Int, categoryLabel: String): List<com.example.model.VideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<com.example.model.VideoItem>()
        try {
            val url = "https://api.themoviedb.org/3/discover/movie?api_key=$TMDB_API_KEY&with_genres=$genreId&sort_by=popularity.desc&page=1"
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string()
            if (body != null) {
                val json = JSONObject(body)
                val results = json.optJSONArray("results")
                if (results != null) {
                    for (i in 0 until minOf(results.length(), 10)) {
                        val obj = results.getJSONObject(i)
                        val tmdbId = obj.optInt("id")
                        val title = obj.optString("title", "Untitled")
                        val posterPath = obj.optString("poster_path")
                        val releaseDate = obj.optString("release_date", "2025")
                        val year = if (releaseDate.length >= 4) releaseDate.take(4) else "2025"
                        val voteAvg = obj.optDouble("vote_average", 7.5)

                        val posterUrl = if (posterPath.isNotBlank() && posterPath != "null") {
                            "https://image.tmdb.org/t/p/w500$posterPath"
                        } else null

                        if (posterUrl != null) {
                            list.add(
                                com.example.model.VideoItem(
                                    id = "movie_$tmdbId",
                                    title = title,
                                    uploaderName = "$year • $categoryLabel • ★${String.format("%.1f", voteAvg)}",
                                    thumbnailUrl = posterUrl,
                                    providerId = "tmdb"
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching explore category $genreId", e)
        }
        return@withContext list
    }

    suspend fun fetchJikanTopAnime(): List<com.example.model.VideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<com.example.model.VideoItem>()
        try {
            val url = "https://api.jikan.moe/v4/top/anime?limit=10"
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string()
            if (body != null) {
                val json = JSONObject(body)
                val data = json.optJSONArray("data")
                if (data != null) {
                    for (i in 0 until data.length()) {
                        val obj = data.getJSONObject(i)
                        val malId = obj.optInt("mal_id")
                        val title = obj.optString("title", "Anime")
                        val year = obj.optInt("year", 2024)
                        val score = obj.optDouble("score", 0.0)
                        val imagesObj = obj.optJSONObject("images")
                        val jpgObj = imagesObj?.optJSONObject("jpg")
                        val imageUrl = jpgObj?.optString("large_image_url", jpgObj.optString("image_url", ""))

                        if (!imageUrl.isNullOrBlank()) {
                            list.add(
                                com.example.model.VideoItem(
                                    id = "mal_$malId",
                                    title = title,
                                    uploaderName = "$year • Anime • ★${String.format("%.1f", score)}",
                                    thumbnailUrl = imageUrl,
                                    providerId = "jikan"
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Jikan top anime", e)
        }
        return@withContext list
    }

    suspend fun fetchAniListTrendingAnime(): List<com.example.model.VideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<com.example.model.VideoItem>()
        try {
            val query = """
                query {
                  Page(page: 1, perPage: 10) {
                    media(type: ANIME, sort: POPULARITY_DESC) {
                      id
                      title {
                        english
                        romaji
                      }
                      coverImage {
                        extraLarge
                        large
                      }
                      seasonYear
                      averageScore
                    }
                  }
                }
            """.trimIndent()

            val jsonObj = JSONObject().apply {
                put("query", query)
            }

            val mediaType = "application/json".toMediaTypeOrNull()
            val requestBody = jsonObj.toString().toRequestBody(mediaType)

            val req = Request.Builder()
                .url("https://graphql.anilist.co")
                .post(requestBody)
                .header("User-Agent", "Butterfly/1.0")
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string()
            if (body != null) {
                val json = JSONObject(body)
                val mediaArray = json.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media")
                if (mediaArray != null) {
                    for (i in 0 until mediaArray.length()) {
                        val media = mediaArray.getJSONObject(i)
                        val aniId = media.optInt("id")
                        val titleObj = media.optJSONObject("title")
                        val title = titleObj?.optString("english")?.takeIf { it.isNotBlank() }
                            ?: titleObj?.optString("romaji")
                            ?: "Anime"
                        val coverObj = media.optJSONObject("coverImage")
                        val coverImage = coverObj?.optString("extraLarge")?.takeIf { it.isNotBlank() }
                            ?: coverObj?.optString("large")
                            ?: ""
                        val year = media.optInt("seasonYear", 2024)
                        val scoreRaw = media.optDouble("averageScore", 80.0)
                        val score = scoreRaw / 10.0

                        if (coverImage.isNotBlank()) {
                            list.add(
                                com.example.model.VideoItem(
                                    id = "anilist_$aniId",
                                    title = title,
                                    uploaderName = "$year • AniList • ★${String.format("%.1f", score)}",
                                    thumbnailUrl = coverImage,
                                    providerId = "anilist"
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching AniList anime", e)
        }
        return@withContext list
    }

    suspend fun resolveRealPoster(query: String, providerId: String? = null): String? = withContext(Dispatchers.IO) {
        if (isWebOrAdultProvider(providerId)) return@withContext null
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.themoviedb.org/3/search/multi?api_key=$TMDB_API_KEY&query=$encoded&page=1"
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string()
            if (body != null) {
                val json = JSONObject(body)
                val results = json.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val first = results.getJSONObject(0)
                    val posterPath = first.optString("poster_path")
                    if (posterPath.isNotBlank() && posterPath != "null") {
                        return@withContext "https://image.tmdb.org/t/p/w500$posterPath"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving poster for query: $query", e)
        }
        return@withContext null
    }
}
