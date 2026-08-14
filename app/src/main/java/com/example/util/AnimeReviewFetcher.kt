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

object AnimeReviewFetcher {

    private const val ANILIST_GRAPHQL_URL = "https://graphql.anilist.co"
    private const val JIKAN_BASE_URL = "https://api.jikan.moe/v4"
    private val http = HttpBridge()

    suspend fun fetchUnifiedAnimeReviews(
        title: String,
        videoId: String? = null,
        providerId: String? = null
    ): TorrentReviewsResult = withContext(Dispatchers.IO) {
        val cleanTitle = cleanAnimeTitle(title)
        var malId: Int? = extractMalId(videoId)

        // 1. Fetch AniList Reviews & resolve IDs
        val aniListResult = fetchAniListReviews(cleanTitle, malId)

        if (malId == null && aniListResult?.malId != null) {
            malId = aniListResult.malId
        }

        // 2. Fetch Jikan Reviews (MyAnimeList)
        val jikanResult = fetchJikanReviews(cleanTitle, malId)

        val aniListReviews = aniListResult?.reviews ?: emptyList()
        val jikanReviews = jikanResult?.reviews ?: emptyList()

        val allReviews = mutableListOf<VideoComment>()
        allReviews.addAll(aniListReviews)
        allReviews.addAll(jikanReviews)

        // Deduplicate identical/near-identical reviews across services
        val deduplicated = deduplicateReviews(allReviews)

        val aniListAvg = aniListResult?.avgScore ?: 0f
        val jikanAvg = jikanResult?.avgScore ?: 0f

        val compositeAvg = when {
            aniListAvg > 0f && jikanAvg > 0f -> (aniListAvg + jikanAvg) / 2f
            aniListAvg > 0f -> aniListAvg
            jikanAvg > 0f -> jikanAvg
            else -> 8.2f
        }

        val totalCount = (aniListResult?.totalCount ?: 0) + (jikanResult?.totalCount ?: 0)
        val resolvedTitle = aniListResult?.animeTitle ?: jikanResult?.animeTitle ?: cleanTitle

        TorrentReviewsResult(
            reviews = deduplicated,
            totalCount = maxOf(totalCount, deduplicated.size),
            averageRating = compositeAvg,
            mediaTitle = resolvedTitle
        )
    }

    private data class ServiceReviewData(
        val reviews: List<VideoComment>,
        val totalCount: Int,
        val avgScore: Float,
        val animeTitle: String?,
        val malId: Int? = null,
        val aniListId: Int? = null
    )

    private suspend fun fetchAniListReviews(title: String, malId: Int?): ServiceReviewData? {
        try {
            val query = """
                query (${'$'}search: String, ${'$'}idMal: Int) {
                  Media(search: ${'$'}search, idMal: ${'$'}idMal, type: ANIME) {
                    id
                    idMal
                    title {
                      romaji
                      english
                      native
                    }
                    averageScore
                    reviews(page: 1, perPage: 25) {
                      pageInfo {
                        total
                      }
                      nodes {
                        id
                        summary
                        body
                        rating
                        ratingAmount
                        score
                        siteUrl
                        createdAt
                        user {
                          name
                          avatar {
                            large
                            medium
                          }
                        }
                      }
                    }
                  }
                }
            """.trimIndent()

            val variables = JSONObject()
            if (malId != null && malId > 0) {
                variables.put("idMal", malId)
            } else if (title.isNotBlank()) {
                variables.put("search", title)
            } else {
                return null
            }

            val requestBody = JSONObject().apply {
                put("query", query)
                put("variables", variables)
            }

            val response = http.post(ANILIST_GRAPHQL_URL, requestBody.toString(), "application/json")
            if (response.statusCode != 200) return null

            val json = JSONObject(response.body)
            val data = json.optJSONObject("data") ?: return null
            val media = data.optJSONObject("Media") ?: return null

            val resolvedAniListId = media.optInt("id", 0)
            val resolvedMalId = media.optInt("idMal", 0)
            val titlesObj = media.optJSONObject("title")
            val englishTitle = titlesObj?.optString("english")
            val romajiTitle = titlesObj?.optString("romaji")
            val animeTitle = if (!englishTitle.isNullOrBlank()) englishTitle else if (!romajiTitle.isNullOrBlank()) romajiTitle else title
            val avgScore100 = media.optInt("averageScore", 80)
            val avgRating10 = avgScore100 / 10f

            val reviewsObj = media.optJSONObject("reviews")
            val total = reviewsObj?.optJSONObject("pageInfo")?.optInt("total", 0) ?: 0
            val nodes = reviewsObj?.optJSONArray("nodes") ?: JSONArray()

            val commentsList = mutableListOf<VideoComment>()
            for (i in 0 until nodes.length()) {
                val node = nodes.getJSONObject(i)
                val revId = "anilist_${node.optInt("id", i)}"
                val summary = node.optString("summary", "").trim()
                val rawBody = node.optString("body", "").trim()
                val cleanBody = stripMarkdownAndHtml(rawBody)
                val score = node.optInt("score", 0)
                val siteUrl = node.optString("siteUrl", "https://anilist.co")
                val createdAtSec = node.optLong("createdAt", 0L)
                val dateStr = formatDateFromEpoch(createdAtSec)
                val userObj = node.optJSONObject("user")
                val username = userObj?.optString("name", "AniList User") ?: "AniList User"
                val avatarObj = userObj?.optJSONObject("avatar")
                val avatarUrl = avatarObj?.optString("large") ?: avatarObj?.optString("medium")

                val ratingVal = if (score > 0) score / 10f else null
                val ratingText = if (score > 0) "$score/100" else null

                commentsList.add(
                    VideoComment(
                        id = revId,
                        authorName = username,
                        authorAvatarUrl = avatarUrl,
                        commentText = cleanBody.ifBlank { summary },
                        timeAgo = dateStr,
                        likeCount = node.optInt("rating", 0),
                        dislikeCount = 0,
                        rating = ratingVal,
                        ratingText = ratingText,
                        reviewTitle = if (summary.isNotBlank()) summary else extractHeadline(cleanBody, animeTitle),
                        summary = summary,
                        sourceBadge = "AniList",
                        reviewUrl = siteUrl
                    )
                )
            }

            return ServiceReviewData(
                reviews = commentsList,
                totalCount = maxOf(total, commentsList.size),
                avgScore = avgRating10,
                animeTitle = animeTitle,
                malId = if (resolvedMalId > 0) resolvedMalId else null,
                aniListId = if (resolvedAniListId > 0) resolvedAniListId else null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private suspend fun fetchJikanReviews(title: String, initialMalId: Int?): ServiceReviewData? {
        try {
            var malId = initialMalId

            if (malId == null || malId <= 0) {
                if (title.isBlank()) return null
                val encoded = URLEncoder.encode(title, "UTF-8")
                val searchUrl = "$JIKAN_BASE_URL/anime?q=$encoded&limit=1"
                val searchResp = http.get(searchUrl)
                if (searchResp.statusCode == 200) {
                    val sJson = JSONObject(searchResp.body)
                    val sData = sJson.optJSONArray("data")
                    if (sData != null && sData.length() > 0) {
                        malId = sData.getJSONObject(0).optInt("mal_id", 0)
                    }
                }
            }

            if (malId == null || malId <= 0) return null

            val reviewsUrl = "$JIKAN_BASE_URL/anime/$malId/reviews"
            val resp = http.get(reviewsUrl)
            if (resp.statusCode != 200) return null

            val json = JSONObject(resp.body)
            val dataArr = json.optJSONArray("data") ?: JSONArray()

            val commentsList = mutableListOf<VideoComment>()
            var totalScoreSum = 0f
            var validScoreCount = 0

            for (i in 0 until dataArr.length()) {
                val item = dataArr.getJSONObject(i)
                val revId = "jikan_${item.optInt("mal_id", i)}"
                val reviewUrl = item.optString("url", "https://myanimelist.net")
                val rawReviewText = item.optString("review", "").trim()
                val cleanText = stripMarkdownAndHtml(rawReviewText)
                val score = item.optInt("score", 0)
                val isSpoiler = item.optBoolean("is_spoiler", false)
                val votes = item.optInt("votes", 0)
                val dateStr = formatDateFromIso(item.optString("date", ""))

                val userObj = item.optJSONObject("user")
                val username = userObj?.optString("username", "MAL User") ?: "MAL User"
                val imagesObj = userObj?.optJSONObject("images")
                val jpgObj = imagesObj?.optJSONObject("jpg")
                val avatarUrl = jpgObj?.optString("image_url")

                if (score > 0) {
                    totalScoreSum += score
                    validScoreCount++
                }

                val headline = extractHeadline(cleanText, title)

                commentsList.add(
                    VideoComment(
                        id = revId,
                        authorName = username,
                        authorAvatarUrl = avatarUrl,
                        commentText = cleanText,
                        timeAgo = dateStr,
                        likeCount = votes,
                        dislikeCount = 0,
                        rating = if (score > 0) score.toFloat() else null,
                        ratingText = if (score > 0) "$score/10" else null,
                        reviewTitle = headline,
                        summary = headline,
                        isSpoiler = isSpoiler,
                        sourceBadge = "MyAnimeList",
                        reviewUrl = reviewUrl
                    )
                )
            }

            val avgRating = if (validScoreCount > 0) totalScoreSum / validScoreCount else 8.1f

            return ServiceReviewData(
                reviews = commentsList,
                totalCount = commentsList.size,
                avgScore = avgRating,
                animeTitle = title,
                malId = malId
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun deduplicateReviews(reviews: List<VideoComment>): List<VideoComment> {
        val seenSignatures = mutableSetOf<String>()
        val result = mutableListOf<VideoComment>()

        for (rev in reviews) {
            val authorClean = rev.authorName.lowercase().trim()
            val snippet = rev.commentText.take(50).lowercase().replace(Regex("[^a-z0-9]"), "")
            val signature = "$authorClean:$snippet"

            if (snippet.isNotBlank() && seenSignatures.contains(signature)) {
                continue
            }
            if (snippet.isNotBlank()) {
                seenSignatures.add(signature)
            }
            result.add(rev)
        }
        return result
    }

    private fun extractMalId(videoId: String?): Int? {
        if (videoId == null) return null
        if (videoId.toIntOrNull() != null) return videoId.toInt()
        if (videoId.startsWith("jikan_")) {
            return videoId.removePrefix("jikan_").toIntOrNull()
        }
        if (videoId.startsWith("mal_")) {
            return videoId.removePrefix("mal_").toIntOrNull()
        }
        return null
    }

    fun cleanAnimeTitle(raw: String): String {
        return raw.replace(Regex("(?i)\\[.*?\\]|\\(.*?\\)"), "")
            .replace(Regex("(?i)1080p|720p|4k|web-dl|x264|x265|hevc|multi|sub|dub|bdrip|hdrip|dual audio"), "")
            .replace("-", " ")
            .trim()
    }

    private fun stripMarkdownAndHtml(input: String): String {
        return input
            .replace(Regex("<[^>]*>"), "")
            .replace(Regex("\\[(spoiler|code|quote)\\].*?\\[/\\1\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[.*?\\]\\(.*?\\)"), "")
            .replace(Regex("[#*`_~]"), "")
            .trim()
    }

    private fun extractHeadline(text: String, title: String): String {
        val firstSentence = text.split(Regex("[.!?\n]")).firstOrNull { it.trim().length in 10..120 }?.trim()
        if (!firstSentence.isNullOrBlank()) return firstSentence
        return if (text.length > 70) text.take(70) + "..." else text
    }

    private fun formatDateFromEpoch(epochSeconds: Long): String {
        if (epochSeconds <= 0) return "Recently"
        return try {
            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.US)
            sdf.timeZone = TimeZone.getDefault()
            sdf.format(java.util.Date(epochSeconds * 1000))
        } catch (e: Exception) {
            "Recently"
        }
    }

    private fun formatDateFromIso(isoStr: String): String {
        if (isoStr.isBlank()) return "Recently"
        return try {
            val cleanIso = isoStr.take(10)
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = inputFormat.parse(cleanIso)
            val outputFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
            if (date != null) outputFormat.format(date) else cleanIso
        } catch (e: Exception) {
            isoStr.take(10).ifBlank { "Recently" }
        }
    }
}
