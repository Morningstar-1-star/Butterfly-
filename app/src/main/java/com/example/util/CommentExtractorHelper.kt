package com.example.util

import android.util.Log
import com.example.extractor.YouTubeExtractorHelper
import com.example.model.VideoComment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import java.util.concurrent.TimeUnit

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

        // 1. If torrent, movie, tv series, archive or cinema item -> Fetch authentic IMDb / TMDB user reviews
        val isMovieOrSeriesOrTorrent = cleanProvider.contains("torrent") ||
                cleanProvider == "tmdb" ||
                cleanProvider == "cinemeta" ||
                cleanProvider == "vega" ||
                videoId.startsWith("tt") ||
                videoId.startsWith("movie_") ||
                videoId.startsWith("tv_") ||
                videoId.contains("magnet:", ignoreCase = true)

        if (isMovieOrSeriesOrTorrent || title.isNotBlank() && (cleanProvider == "archive_org" || cleanProvider.isBlank())) {
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
}
