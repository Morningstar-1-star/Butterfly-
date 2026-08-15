package com.example.util

import com.example.model.VideoComment
import com.example.plugin.bridge.HttpBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class TorrentReviewsResult(
    val reviews: List<VideoComment>,
    val totalCount: Int,
    val averageRating: Float,
    val mediaTitle: String,
    val imdbId: String? = null
)

object TorrentReviewFetcher {

    private const val API_KEY = "3155fdb497f7575a144f26adebcbf980"
    private const val BASE_URL = "https://api.themoviedb.org/3"
    private val http = HttpBridge()

    suspend fun fetchReviewsForTorrent(
        title: String,
        videoId: String? = null,
        providerId: String? = null
    ): TorrentReviewsResult = withContext(Dispatchers.IO) {
        // Check if this is an explicit anime provider or matches anime
        val isExplicitAnimeProvider = providerId == "jikan_anime" || providerId == "nyaa" || providerId == "anime" ||
                videoId?.startsWith("jikan_") == true || videoId?.startsWith("mal_") == true

        if (isExplicitAnimeProvider) {
            val animeRes = AnimeReviewFetcher.fetchUnifiedAnimeReviews(title, videoId, providerId)
            if (animeRes.reviews.isNotEmpty()) {
                return@withContext animeRes
            }
        } else {
            try {
                val animeRes = AnimeReviewFetcher.fetchUnifiedAnimeReviews(title, videoId, providerId)
                if (animeRes.reviews.isNotEmpty()) {
                    return@withContext animeRes
                }
            } catch (e: Exception) {
                // Fall back to TMDB
            }
        }

        var seasonNum: Int? = null
        var episodeNum: Int? = null

        try {
            val cleanTitle = cleanTorrentTitle(title)
            var tmdbId: Int? = null
            var mediaType = "movie"
            var imdbId: String? = null

            // 1. Check if videoId contains TMDB ID or IMDb ID directly
            if (!videoId.isNullOrBlank()) {
                if (videoId.startsWith("movie_")) {
                    tmdbId = videoId.removePrefix("movie_").toIntOrNull()
                    mediaType = "movie"
                } else if (videoId.startsWith("tv_")) {
                    val parts = videoId.removePrefix("tv_").split("_")
                    tmdbId = parts.getOrNull(0)?.toIntOrNull()
                    mediaType = "tv"
                    parts.forEach { part ->
                        if (part.startsWith("s") && part.length > 1) {
                            seasonNum = part.substring(1).toIntOrNull()
                        } else if (part.startsWith("e") && part.length > 1) {
                            episodeNum = part.substring(1).toIntOrNull()
                        }
                    }
                } else if (videoId.startsWith("tt")) {
                    imdbId = videoId
                }
            }

            if (seasonNum == null) {
                val sMatch = Regex("(?i)(?:s|season\\s*)(\\d+)").find(title)
                seasonNum = sMatch?.groupValues?.get(1)?.toIntOrNull()
            }
            if (episodeNum == null) {
                val eMatch = Regex("(?i)(?:e|ep|episode\\s*)(\\d+)").find(title)
                episodeNum = eMatch?.groupValues?.get(1)?.toIntOrNull()
            }

            // 2. If no TMDB ID found yet, search TMDB by title or find by IMDb ID
            if (tmdbId == null && !imdbId.isNullOrBlank()) {
                val findUrl = "$BASE_URL/find/$imdbId?api_key=$API_KEY&external_source=imdb_id"
                val resp = http.get(findUrl)
                if (resp.statusCode == 200) {
                    val json = JSONObject(resp.body)
                    val movieResults = json.optJSONArray("movie_results")
                    val tvResults = json.optJSONArray("tv_results")

                    if (movieResults != null && movieResults.length() > 0) {
                        tmdbId = movieResults.getJSONObject(0).optInt("id")
                        mediaType = "movie"
                    } else if (tvResults != null && tvResults.length() > 0) {
                        tmdbId = tvResults.getJSONObject(0).optInt("id")
                        mediaType = "tv"
                    }
                }
            }

            // 3. Search TMDB by title if still no TMDB ID
            if (tmdbId == null && cleanTitle.isNotBlank()) {
                val encoded = URLEncoder.encode(cleanTitle, "UTF-8")
                val searchUrl = "$BASE_URL/search/multi?api_key=$API_KEY&query=$encoded&page=1"
                val resp = http.get(searchUrl)
                if (resp.statusCode == 200) {
                    val json = JSONObject(resp.body)
                    val results = json.optJSONArray("results") ?: JSONArray()
                    for (i in 0 until results.length()) {
                        val item = results.getJSONObject(i)
                        val type = item.optString("media_type", "movie")
                        if (type == "movie" || type == "tv") {
                            tmdbId = item.optInt("id")
                            mediaType = type
                            break
                        }
                    }
                }
            }

            if (tmdbId == null || tmdbId == 0) {
                return@withContext TorrentReviewsResult(
                    reviews = emptyList(),
                    totalCount = 0,
                    averageRating = 0f,
                    mediaTitle = cleanTitle
                )
            }

            // 4. Fetch details to get total review stats & imdb_id
            var avgRating = 7.0f
            var voteCount = 5044
            var canonicalTitle = cleanTitle

            val detailsUrl = "$BASE_URL/$mediaType/$tmdbId?api_key=$API_KEY"
            val detailsResp = http.get(detailsUrl)
            if (detailsResp.statusCode == 200) {
                val dJson = JSONObject(detailsResp.body)
                avgRating = dJson.optDouble("vote_average", 7.0).toFloat()
                voteCount = dJson.optInt("vote_count", 5044)
                canonicalTitle = dJson.optString("title").ifEmpty { dJson.optString("name", cleanTitle) }
                imdbId = dJson.optString("imdb_id").ifEmpty { imdbId }
            }

            // 5. Fetch Real User Reviews (Pages 1 to 10)
            val parsedComments = mutableListOf<VideoComment>()
            var totalResultsCount = voteCount

            for (page in 1..10) {
                val reviewsUrl = "$BASE_URL/$mediaType/$tmdbId/reviews?api_key=$API_KEY&page=$page"
                val rResp = http.get(reviewsUrl)
                if (rResp.statusCode == 200) {
                    val rJson = JSONObject(rResp.body)
                    totalResultsCount = rJson.optInt("total_results", voteCount)
                    val resultsArray = rJson.optJSONArray("results") ?: JSONArray()
                    if (resultsArray.length() == 0) break

                    for (i in 0 until resultsArray.length()) {
                        val reviewObj = resultsArray.getJSONObject(i)
                        val revId = reviewObj.optString("id", "rev_${System.currentTimeMillis()}_$i")
                        val author = reviewObj.optString("author", "IMDb / TMDB User")
                        val authorDetails = reviewObj.optJSONObject("author_details")

                        val username = authorDetails?.optString("username")?.ifEmpty { author } ?: author
                        val avatarPath = authorDetails?.optString("avatar_path")
                        val ratingVal = authorDetails?.optDouble("rating", -1.0)

                        val rawRating = if (ratingVal != null && ratingVal > 0) {
                            ratingVal.toFloat()
                        } else {
                            0f
                        }

                        val avatarUrl = when {
                            avatarPath.isNullOrBlank() || avatarPath == "null" -> null
                            avatarPath.startsWith("/https") -> avatarPath.substring(1)
                            avatarPath.startsWith("http") -> avatarPath
                            else -> "https://image.tmdb.org/t/p/w185$avatarPath"
                        }

                        val contentText = reviewObj.optString("content", "").trim()
                        if (contentText.isBlank()) continue

                        val createdAt = reviewObj.optString("created_at", "")
                        val formattedDate = formatDate(createdAt)

                        val isSpoiler = contentText.contains("spoiler", ignoreCase = true) ||
                                contentText.contains("[spoiler]", ignoreCase = true) ||
                                contentText.contains("warning", ignoreCase = true)

                        val titleHeadline = extractReviewHeadline(contentText, canonicalTitle)

                        parsedComments.add(
                            VideoComment(
                                id = revId,
                                authorName = username,
                                authorAvatarUrl = avatarUrl,
                                commentText = contentText,
                                timeAgo = formattedDate,
                                likeCount = 0,
                                dislikeCount = 0,
                                isLikedByMe = false,
                                isDislikedByMe = false,
                                rating = rawRating,
                                reviewTitle = titleHeadline,
                                isSpoiler = isSpoiler,
                                totalReviewsCountText = formatCount(totalResultsCount)
                            )
                        )
                    }
                } else {
                    break
                }
            }

            TorrentReviewsResult(
                reviews = parsedComments,
                totalCount = maxOf(totalResultsCount, parsedComments.size),
                averageRating = avgRating,
                mediaTitle = canonicalTitle,
                imdbId = imdbId
            )

        } catch (e: Exception) {
            e.printStackTrace()
            val fallbackTitle = cleanTorrentTitle(title).ifBlank { "Media Item" }
            TorrentReviewsResult(
                reviews = emptyList(),
                totalCount = 0,
                averageRating = 0f,
                mediaTitle = fallbackTitle
            )
        }
    }

    private fun cleanTorrentTitle(raw: String): String {
        return TMDBHelper.cleanTitleForSearch(raw)
    }

    private fun formatDate(dateStr: String): String {
        if (dateStr.isBlank()) return "Recently"
        return try {
            val inFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            inFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = inFormat.parse(dateStr)
            val outFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
            if (date != null) outFormat.format(date) else dateStr.take(10)
        } catch (e: Exception) {
            try {
                dateStr.take(10)
            } catch (e2: Exception) {
                "Recently"
            }
        }
    }

    private fun extractReviewHeadline(content: String, defaultTitle: String): String {
        val lines = content.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isNotEmpty()) {
            val first = lines.first()
            if (first.length in 5..90 && !first.endsWith(".")) {
                return first
            }
            if (first.length <= 90) return first
            return first.take(80) + "..."
        }
        return "$defaultTitle Review"
    }

    private fun formatCount(count: Int): String {
        return when {
            count >= 1000 -> String.format(Locale.US, "%.1fK", count / 1000.0)
            else -> count.toString()
        }
    }
}
