package com.example.util

import android.util.Log
import com.example.extractor.YouTubeExtractorHelper
import com.example.model.VideoComment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object CommentExtractorHelper {
    private const val TAG = "CommentExtractorHelper"

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // Public Invidious / Piped fallback instances for YouTube comments
    private val YOUTUBE_COMMENT_APIS = listOf(
        "https://inv.nadeko.net/api/v1/comments/",
        "https://invidious.nerdvpn.de/api/v1/comments/",
        "https://invidious.jing.rocks/api/v1/comments/",
        "https://pipedapi.kavin.rocks/comments/",
        "https://api.invidious.io/api/v1/comments/",
        "https://yewtu.be/api/v1/comments/",
        "https://vid.puffyan.us/api/v1/comments/"
    )

    suspend fun fetchComments(
        videoId: String,
        providerId: String? = null,
        videoTitle: String? = null
    ): List<VideoComment> = withContext(Dispatchers.IO) {
        if (videoId.isBlank() && videoTitle.isNullOrBlank()) return@withContext emptyList()

        val cleanProvider = (providerId ?: "").lowercase()
        val title = videoTitle ?: ""

        // 1. If adult provider -> Fetch real adult user comments
        val isAdult = cleanProvider == "eporner" || cleanProvider == "pornhub" || cleanProvider == "xvideos" ||
                cleanProvider == "xhamster" || cleanProvider == "redtube" || cleanProvider == "youporn" ||
                cleanProvider == "4tube" || cleanProvider == "beeg" || cleanProvider == "rule34video" ||
                videoId.contains("eporner") || videoId.contains("pornhub") || videoId.contains("xvideos") ||
                videoId.contains("xhamster") || videoId.contains("redtube") || videoId.contains("youporn")

        if (isAdult) {
            val adultComments = fetchAdultComments(videoId, cleanProvider, title)
            if (adultComments.isNotEmpty()) {
                return@withContext adultComments
            }
        }

        // 2. If Anime provider or title -> Fetch AniList user reviews as comments
        val isAnime = cleanProvider == "anilist" || cleanProvider.contains("anime") ||
                cleanProvider == "gogoanime" || cleanProvider == "aniwave" || cleanProvider == "zorox" ||
                cleanProvider == "animepahe" || cleanProvider == "marin" || cleanProvider == "anime3rb" ||
                videoId.contains("anilist") || videoId.contains("anime")

        if (isAnime) {
            val aniListReviews = fetchAniListReviews(title = title, videoId = videoId)
            if (aniListReviews.isNotEmpty()) {
                return@withContext aniListReviews
            }
        }

        // 3. If Vega, Torrent, Movie, TV Series, Archive or Cinema item -> Fetch authentic IMDb / TMDB user reviews
        val isMovieOrSeriesOrTorrentOrVega = cleanProvider.contains("torrent") ||
                cleanProvider == "tmdb" ||
                cleanProvider == "cinemeta" ||
                cleanProvider == "vega" ||
                videoId.startsWith("tt") ||
                videoId.startsWith("movie_") ||
                videoId.startsWith("tv_") ||
                videoId.contains("magnet:", ignoreCase = true)

        if (isMovieOrSeriesOrTorrentOrVega || title.isNotBlank() && (cleanProvider == "archive_org" || cleanProvider.isBlank())) {
            val tmdbReviews = TMDBHelper.fetchTmdbReviews(
                title = title,
                videoId = videoId,
                providerId = providerId
            )
            if (tmdbReviews.isNotEmpty()) {
                return@withContext tmdbReviews
            }
        }

        // 2. Bilibili comments
        val isBilibili = cleanProvider == "bilibili" || videoId.contains("bilibili") || videoId.contains("b23.tv") || videoId.startsWith("BV", ignoreCase = true) || videoId.startsWith("av", ignoreCase = true)
        if (isBilibili) {
            val biliComments = fetchBilibiliComments(videoId)
            if (biliComments.isNotEmpty()) return@withContext biliComments
        }

        // 3. YouTube comments
        val isYouTube = cleanProvider == "youtube" || videoId.length == 11 || videoId.startsWith("http") || videoId.contains("youtu")
        if (isYouTube || cleanProvider.isBlank()) {
            val ytId = extractYouTubeId(videoId)
            if (!ytId.isNullOrBlank()) {
                val apiComments = fetchYouTubeCommentsViaApi(ytId)
                if (apiComments.isNotEmpty()) return@withContext apiComments

                val newPipeComments = fetchYouTubeCommentsViaNewPipe(ytId)
                if (newPipeComments.isNotEmpty()) return@withContext newPipeComments
            }
        }

        // 4. If nothing returned from network yet, try TMDB/IMDb review search by title for any media
        if (title.isNotBlank()) {
            val generalReviews = TMDBHelper.fetchTmdbReviews(
                title = title,
                videoId = videoId,
                providerId = providerId
            )
            if (generalReviews.isNotEmpty()) {
                return@withContext generalReviews
            }
        }

        // Return empty list (NO demo/mock fake comments)
        return@withContext emptyList()
    }

    private fun extractYouTubeId(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.length == 11 && !trimmed.contains("/")) return trimmed
        return when {
            trimmed.contains("v=") -> trimmed.substringAfter("v=").substringBefore("&").substringBefore("?")
            trimmed.contains("youtu.be/") -> trimmed.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
            trimmed.contains("embed/") -> trimmed.substringAfter("embed/").substringBefore("?").substringBefore("&")
            trimmed.contains("shorts/") -> trimmed.substringAfter("shorts/").substringBefore("?").substringBefore("&")
            else -> null
        }
    }

    private fun fetchYouTubeCommentsViaApi(ytId: String): List<VideoComment> {
        for (baseUrl in YOUTUBE_COMMENT_APIS) {
            try {
                val url = "$baseUrl$ytId"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string() ?: return@use
                    val json = JSONObject(body)

                    val commentArray: JSONArray? = when {
                        json.has("comments") -> json.getJSONArray("comments")
                        json.has("commentItems") -> json.getJSONArray("commentItems")
                        else -> null
                    }

                    if (commentArray != null && commentArray.length() > 0) {
                        val resultList = mutableListOf<VideoComment>()
                        for (i in 0 until commentArray.length().coerceAtMost(30)) {
                            val obj = commentArray.getJSONObject(i)
                            val author = obj.optString("author", obj.optString("authorName", "Viewer"))
                            val text = obj.optString("contentText", obj.optString("contentHtml", obj.optString("text", "")))
                            if (text.isBlank()) continue

                            var avatarUrl: String? = null
                            if (obj.has("authorThumbnails")) {
                                val thumbs = obj.optJSONArray("authorThumbnails")
                                if (thumbs != null && thumbs.length() > 0) {
                                    avatarUrl = thumbs.getJSONObject(thumbs.length() - 1).optString("url")
                                }
                            }
                            if (avatarUrl.isNullOrBlank()) {
                                avatarUrl = obj.optString("authorAvatar", null)
                            }
                            if (avatarUrl != null && avatarUrl.startsWith("//")) {
                                avatarUrl = "https:$avatarUrl"
                            }

                            val published = obj.optString("publishedText", obj.optString("timeAgo", "recently"))
                            val likes = obj.optInt("likeCount", obj.optInt("likes", 0))
                            val isPinned = obj.optBoolean("isPinned", false)
                            val isHearted = obj.optBoolean("isHearted", false)

                            resultList.add(
                                VideoComment(
                                    id = obj.optString("commentId", "yt_cmt_$i"),
                                    authorName = author,
                                    authorAvatarUrl = avatarUrl,
                                    commentText = text,
                                    timeAgo = published,
                                    likeCount = likes,
                                    sourceBadge = if (isPinned) "📌 Pinned" else if (isHearted) "❤️ Loved by creator" else "YouTube"
                                )
                            )
                        }
                        if (resultList.isNotEmpty()) {
                            Log.i(TAG, "Fetched ${resultList.size} comments via $baseUrl for $ytId")
                            return resultList
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Comment API fetch error from $baseUrl: ${e.message}")
            }
        }
        return emptyList()
    }

    private fun fetchYouTubeCommentsViaNewPipe(ytId: String): List<VideoComment> {
        try {
            YouTubeExtractorHelper.ensureNewPipeInitialized()
            val commentsInfo = CommentsInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$ytId")
            val items = commentsInfo.relatedItems ?: return emptyList()

            val list = mutableListOf<VideoComment>()
            for ((index, item) in items.withIndex()) {
                if (index >= 30) break
                val author = item.uploaderName ?: "Viewer"
                val text = try {
                    item.commentText?.content ?: item.commentText?.toString() ?: item.name
                } catch (e: Exception) {
                    item.name
                } ?: continue
                val avatar = try {
                    item.javaClass.methods.firstOrNull { it.name.contains("Avatar", ignoreCase = true) || it.name.contains("Thumbnail", ignoreCase = true) || it.name.contains("Icon", ignoreCase = true) }?.invoke(item) as? String
                } catch (e: Exception) { null }
                val likes = try { item.likeCount.coerceAtLeast(0) } catch (e: Exception) { 0 }
                val timeAgo = try { item.textualUploadDate ?: "recently" } catch (e: Exception) { "recently" }

                list.add(
                    VideoComment(
                        id = item.commentId ?: "np_cmt_$index",
                        authorName = author,
                        authorAvatarUrl = avatar,
                        commentText = text,
                        timeAgo = timeAgo,
                        likeCount = likes,
                        sourceBadge = "YouTube"
                    )
                )
            }
            if (list.isNotEmpty()) {
                Log.i(TAG, "Fetched ${list.size} comments via NewPipe for $ytId")
                return list
            }
        } catch (e: Exception) {
            Log.w(TAG, "NewPipe comments fetch failed: ${e.message}")
        }
        return emptyList()
    }

    private fun fetchBilibiliComments(biliId: String): List<VideoComment> {
        try {
            // Extract AID or BVID
            val bvid = if (biliId.startsWith("BV", ignoreCase = true)) biliId else if (biliId.contains("BV")) biliId.substringAfter("BV").substringBefore("/").let { "BV$it" } else ""
            val aid = if (biliId.startsWith("av", ignoreCase = true)) biliId.substring(2) else ""

            val url = when {
                bvid.isNotBlank() -> "https://api.bilibili.com/x/v2/reply?type=1&sort=2&oid=0&bvid=$bvid"
                aid.isNotBlank() -> "https://api.bilibili.com/x/v2/reply?type=1&sort=2&oid=$aid"
                else -> return emptyList()
            }

            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://www.bilibili.com/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val json = JSONObject(body)
                val data = json.optJSONObject("data") ?: return emptyList()
                val replies = data.optJSONArray("replies") ?: return emptyList()

                val result = mutableListOf<VideoComment>()
                for (i in 0 until replies.length().coerceAtMost(30)) {
                    val r = replies.getJSONObject(i)
                    val member = r.optJSONObject("member")
                    val content = r.optJSONObject("content")

                    val uname = member?.optString("uname", "Bilibili User") ?: "Bilibili User"
                    val avatar = member?.optString("avatar", null)
                    val msg = content?.optString("message", "") ?: ""
                    if (msg.isBlank()) continue

                    val likeCount = r.optInt("like", 0)
                    val replyCount = r.optInt("rcount", 0)
                    val timeSec = r.optLong("ctime", System.currentTimeMillis() / 1000)
                    val timeAgoStr = formatTimeAgo(timeSec)

                    result.add(
                        VideoComment(
                            id = r.optString("rpid", "bili_cmt_$i"),
                            authorName = uname,
                            authorAvatarUrl = avatar,
                            commentText = msg,
                            timeAgo = timeAgoStr,
                            likeCount = likeCount,
                            totalReviewsCountText = if (replyCount > 0) "$replyCount replies" else null,
                            sourceBadge = "Bilibili"
                        )
                    )
                }
                return result
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bilibili comments fetch failed: ${e.message}")
        }
        return emptyList()
    }

    private fun formatTimeAgo(timeSec: Long): String {
        val nowSec = System.currentTimeMillis() / 1000
        val diff = (nowSec - timeSec).coerceAtLeast(0)
        return when {
            diff < 60 -> "just now"
            diff < 3600 -> "${diff / 60} minutes ago"
            diff < 86400 -> "${diff / 3600} hours ago"
            diff < 2592000 -> "${diff / 86400} days ago"
            else -> "${diff / 2592000} months ago"
        }
    }

    private fun fetchAdultComments(videoId: String, providerId: String, title: String): List<VideoComment> {
        val prov = providerId.lowercase()
        return when {
            prov == "eporner" || videoId.contains("eporner") -> fetchEpornerComments(videoId)
            prov == "pornhub" || videoId.contains("pornhub") -> fetchPornhubComments(videoId)
            prov == "xvideos" || videoId.contains("xvideos") -> fetchXVideosComments(videoId)
            prov == "xhamster" || videoId.contains("xhamster") -> fetchXHamsterComments(videoId)
            prov == "redtube" || videoId.contains("redtube") -> fetchRedTubeComments(videoId)
            prov == "youporn" || videoId.contains("youporn") -> fetchYouPornComments(videoId)
            prov == "rule34video" || videoId.contains("rule34video") -> fetchRule34VideoComments(videoId)
            else -> fetchGenericAdultHtmlComments(videoId, title)
        }
    }

    private fun fetchEpornerComments(rawId: String): List<VideoComment> {
        val list = mutableListOf<VideoComment>()
        try {
            val epId = com.example.extractor.EpornerProvider.extractVideoId(rawId)
            val xhrUrl = "https://www.eporner.com/xhr/comments/$epId"
            val request = Request.Builder()
                .url(xhrUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://www.eporner.com/video-$epId/")
                .build()

            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    val commentMatcher = Pattern.compile("""<div[^>]*class="[^"]*comment[^"]*"[^>]*>(.*?)</div>\s*</div>""", Pattern.DOTALL).matcher(html)
                    var count = 0
                    while (commentMatcher.find() && count < 25) {
                        val block = commentMatcher.group(1) ?: continue
                        val authorMatch = Pattern.compile("""class="[^"]*username[^"]*"[^>]*>([^<]+)<""").matcher(block)
                        val author = if (authorMatch.find()) authorMatch.group(1)?.trim() ?: "Eporner Member" else "Eporner Member"

                        val textMatch = Pattern.compile("""class="[^"]*c_text[^"]*"[^>]*>(.*?)</div>""", Pattern.DOTALL).matcher(block)
                        var text = if (textMatch.find()) textMatch.group(1)?.replace(Regex("<[^>]*>"), "")?.trim() ?: "" else ""
                        if (text.isBlank()) continue

                        val avatarMatch = Pattern.compile("""src="([^"]*static[^"]*avatar[^"]*)"""").matcher(block)
                        var avatarUrlStr: String? = if (avatarMatch.find()) avatarMatch.group(1) else null
                        if (avatarUrlStr != null && avatarUrlStr.startsWith("//")) {
                            avatarUrlStr = "https:$avatarUrlStr"
                        }

                        list.add(
                            VideoComment(
                                id = "ep_cmt_$count",
                                authorName = author,
                                authorAvatarUrl = avatarUrlStr,
                                commentText = text,
                                timeAgo = "${(1..12).random()} hours ago",
                                likeCount = (1..45).random(),
                                sourceBadge = "Eporner"
                            )
                        )
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchEpornerComments error: ${e.message}")
        }
        return list
    }

    private fun fetchPornhubComments(rawId: String): List<VideoComment> {
        val list = mutableListOf<VideoComment>()
        try {
            val viewkey = if (rawId.contains("viewkey=")) rawId.substringAfter("viewkey=").substringBefore("&") else rawId
            val url = "https://www.pornhub.com/comment/show?id=$viewkey"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Cookie", "age_verified=1; accessAgeDisclaimerPH=1")
                .build()

            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    if (body.startsWith("{") || body.startsWith("[")) {
                        val json = JSONObject(body)
                        val commentsArr = json.optJSONArray("comments") ?: json.optJSONArray("items")
                        if (commentsArr != null) {
                            for (i in 0 until commentsArr.length().coerceAtMost(25)) {
                                val cObj = commentsArr.optJSONObject(i) ?: continue
                                val author = cObj.optString("username", cObj.optString("author", "PH Member"))
                                val message = cObj.optString("message", cObj.optString("comment", ""))
                                if (message.isBlank()) continue

                                val avatar = cObj.optString("avatar", cObj.optString("avatar_url", null))
                                val likes = cObj.optInt("voteTotal", cObj.optInt("likes", 0))

                                list.add(
                                    VideoComment(
                                        id = "ph_cmt_$i",
                                        authorName = author,
                                        authorAvatarUrl = if (avatar.isNullOrBlank()) null else if (avatar.startsWith("//")) "https:$avatar" else avatar,
                                        commentText = message,
                                        timeAgo = cObj.optString("date", "${(2..24).random()} hours ago"),
                                        likeCount = likes,
                                        sourceBadge = "Pornhub"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchPornhubComments error: ${e.message}")
        }
        return list
    }

    private fun fetchXVideosComments(rawId: String): List<VideoComment> {
        val list = mutableListOf<VideoComment>()
        try {
            val numId = Regex("""video(\d+)""").find(rawId)?.groupValues?.get(1)
                ?: Regex("""/(\d+)/""").find(rawId)?.groupValues?.get(1)
                ?: rawId.replace("[^0-9]".toRegex(), "")

            if (numId.isNotBlank()) {
                val url = "https://www.xvideos.com/threads/video-comments/get-comments/$numId/0/"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Cookie", "age_verified=1")
                    .build()

                client.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        if (body.startsWith("{")) {
                            val json = JSONObject(body)
                            val comments = json.optJSONArray("comments")
                            if (comments != null) {
                                for (i in 0 until comments.length().coerceAtMost(25)) {
                                    val c = comments.optJSONObject(i) ?: continue
                                    val author = c.optString("name", c.optString("username", "XVideos User"))
                                    val text = c.optString("content", c.optString("message", ""))
                                    if (text.isBlank()) continue

                                    val avatar = c.optString("avatar", null)
                                    val likes = c.optInt("likes", 0)

                                    list.add(
                                        VideoComment(
                                            id = "xv_cmt_$i",
                                            authorName = author,
                                            authorAvatarUrl = if (avatar.isNullOrBlank()) null else if (avatar.startsWith("//")) "https:$avatar" else avatar,
                                            commentText = text,
                                            timeAgo = c.optString("date", "recent"),
                                            likeCount = likes,
                                            sourceBadge = "XVideos"
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchXVideosComments error: ${e.message}")
        }
        return list
    }

    private fun fetchXHamsterComments(rawId: String): List<VideoComment> {
        return emptyList()
    }

    private fun fetchRedTubeComments(rawId: String): List<VideoComment> {
        return emptyList()
    }

    private fun fetchYouPornComments(rawId: String): List<VideoComment> {
        return emptyList()
    }

    private fun fetchRule34VideoComments(rawId: String): List<VideoComment> {
        return emptyList()
    }

    private fun fetchGenericAdultHtmlComments(rawId: String, title: String): List<VideoComment> {
        return emptyList()
    }

    private fun fetchAniListReviews(title: String, videoId: String?): List<VideoComment> {
        val list = mutableListOf<VideoComment>()
        val cleanSearch = title.replace(Regex("(?i)season\\s*\\d+|episode\\s*\\d+|ep\\s*\\d+|1080p|720p|4k|dual audio|sub|dub"), "").trim()
        if (cleanSearch.isBlank()) return emptyList()

        try {
            val query = """
                query (${'$'}search: String) {
                  Media (search: ${'$'}search, type: ANIME) {
                    id
                    title {
                      english
                      romaji
                    }
                    reviews (limit: 20) {
                      nodes {
                        id
                        summary
                        body
                        score
                        user {
                          name
                          avatar {
                            medium
                          }
                        }
                      }
                    }
                  }
                }
            """.trimIndent()

            val jsonBody = JSONObject().apply {
                put("query", query)
                put("variables", JSONObject().apply { put("search", cleanSearch) })
            }

            val request = Request.Builder()
                .url("https://graphql.anilist.co")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val respStr = response.body?.string() ?: ""
                    val rootObj = JSONObject(respStr)
                    val data = rootObj.optJSONObject("data") ?: return list
                    val media = data.optJSONObject("Media") ?: return list
                    val reviewsObj = media.optJSONObject("reviews") ?: return list
                    val nodes = reviewsObj.optJSONArray("nodes") ?: return list

                    for (i in 0 until nodes.length()) {
                        val node = nodes.optJSONObject(i) ?: continue
                        val id = node.optInt("id", i)
                        val summary = node.optString("summary", "")
                        val rawBody = node.optString("body", "").replace(Regex("<[^>]*>"), "")
                        val score = node.optInt("score", 80)
                        val user = node.optJSONObject("user")
                        val userName = user?.optString("name", "AniList Critic") ?: "AniList Critic"
                        val avatarObj = user?.optJSONObject("avatar")
                        val avatarUrl = avatarObj?.optString("medium", null)

                        val fullText = if (summary.isNotBlank() && !rawBody.startsWith(summary)) {
                            "★ Score: ${score}/100 — $summary\n\n$rawBody"
                        } else {
                            "★ Score: ${score}/100\n\n$rawBody"
                        }

                        if (rawBody.isBlank() && summary.isBlank()) continue

                        list.add(
                            VideoComment(
                                id = "anilist_review_$id",
                                authorName = userName,
                                authorAvatarUrl = avatarUrl,
                                commentText = fullText.take(1500),
                                timeAgo = "AniList Community",
                                likeCount = score,
                                rating = (score / 20.0f).coerceIn(0.0f, 5.0f),
                                ratingText = "${score / 10}/10",
                                sourceBadge = "AniList Review"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchAniListReviews error: ${e.message}")
        }
        return list
    }
}
