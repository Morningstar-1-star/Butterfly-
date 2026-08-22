package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.StreamData
import com.example.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object HotstarProvider {
    private const val TAG = "HotstarProvider"
    const val PROVIDER_ID = "hotstar"

    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val COMMON_HEADERS = mapOf(
        "User-Agent" to DEFAULT_USER_AGENT,
        "Accept-Language" to "en-US,en;q=0.9",
        "Referer" to "https://www.hotstar.com/",
        "Origin" to "https://www.hotstar.com"
    )

    private val catalogUrls = listOf(
        "https://www.hotstar.com/in/home",
        "https://www.hotstar.com/in/movies",
        "https://www.hotstar.com/in/shows",
        "https://www.hotstar.com/in/genres/drama",
        "https://www.hotstar.com/in/genres/action",
        "https://www.hotstar.com/in/genres/comedy",
        "https://www.hotstar.com/in/genres/romance",
        "https://www.hotstar.com/in/genres/thriller",
        "https://www.hotstar.com/in/channels/hotstar-specials"
    )

    suspend fun getHome(page: Int = 1, limit: Int = 30): List<VideoItem> = withContext(Dispatchers.IO) {
        val targetIndex = ((page - 1).coerceAtLeast(0)) % catalogUrls.size
        val targetUrl = catalogUrls[targetIndex]
        return@withContext fetchFromUrl(targetUrl, limit)
    }

    suspend fun search(query: String, page: Int = 1, limit: Int = 30): List<VideoItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext getHome(page, limit)

        val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
        val searchUrl = "https://www.hotstar.com/in/explore?search_query=$encoded"

        val directResults = fetchFromUrl(searchUrl, limit)
        if (directResults.isNotEmpty()) {
            return@withContext directResults
        }

        // Also query related genre or catalog pages
        val lowerQ = cleanQuery.lowercase()
        val genreUrl = when {
            lowerQ.contains("movie") || lowerQ.contains("film") -> "https://www.hotstar.com/in/movies"
            lowerQ.contains("show") || lowerQ.contains("series") || lowerQ.contains("serial") -> "https://www.hotstar.com/in/shows"
            lowerQ.contains("action") -> "https://www.hotstar.com/in/genres/action"
            lowerQ.contains("comedy") -> "https://www.hotstar.com/in/genres/comedy"
            lowerQ.contains("drama") -> "https://www.hotstar.com/in/genres/drama"
            lowerQ.contains("romance") -> "https://www.hotstar.com/in/genres/romance"
            lowerQ.contains("thriller") || lowerQ.contains("horror") -> "https://www.hotstar.com/in/genres/thriller"
            else -> "https://www.hotstar.com/in/home"
        }

        val allItems = fetchFromUrl(genreUrl, limit * 2)
        val filtered = allItems.filter {
            it.title.contains(cleanQuery, ignoreCase = true) ||
            (it.description?.contains(cleanQuery, ignoreCase = true) == true) ||
            it.uploaderName.contains(cleanQuery, ignoreCase = true)
        }

        if (filtered.isNotEmpty()) {
            return@withContext filtered.take(limit)
        }

        return@withContext allItems.take(limit)
    }

    private fun fetchFromUrl(targetUrl: String, limit: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        val seen = mutableSetOf<String>()

        try {
            val reqBuilder = Request.Builder()
                .url(targetUrl)
                .get()

            COMMON_HEADERS.forEach { (k, v) -> reqBuilder.header(k, v) }
            val req = reqBuilder.build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                resp.body?.string() ?: ""
            }

            if (html.isBlank()) return emptyList()

            // 1. Check for Next.js __NEXT_DATA__
            val nextDataPattern = Pattern.compile("<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>", Pattern.DOTALL)
            val matcher = nextDataPattern.matcher(html)
            if (matcher.find()) {
                val jsonStr = matcher.group(1) ?: ""
                if (jsonStr.isNotBlank()) {
                    try {
                        val rootObj = JSONObject(jsonStr)
                        extractFromJsonObject(rootObj, list, seen, limit)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing NEXT_DATA: ${e.message}")
                    }
                }
            }

            // 2. Regex fallback if JSON parsing was empty
            if (list.isEmpty()) {
                parseHotstarHtmlRegex(html, list, seen, limit)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Hotstar fetch error for $targetUrl: ${e.message}")
        }

        return list.take(limit)
    }

    private fun extractFromJsonObject(node: Any?, list: MutableList<VideoItem>, seen: MutableSet<String>, limit: Int) {
        if (list.size >= limit || node == null) return

        if (node is JSONObject) {
            val contentId = node.optString("content_id", node.optString("contentId", ""))
            var title: String? = null
            var thumb: String? = null
            var pageSlug: String? = null
            var durationStr: String? = null
            var description: String? = null
            val author = "JioHotstar"

            // Look for content_info object
            val ci = node.optJSONObject("content_info")
                ?: node.optJSONObject("expanded_content_poster")?.optJSONObject("content_info")
            if (ci != null) {
                title = ci.optString("title", "").ifBlank { null }
                description = ci.optString("description", "").ifBlank { null }
                val tags = ci.optJSONArray("tags")
                if (tags != null) {
                    for (i in 0 until tags.length()) {
                        val tagObj = tags.optJSONObject(i)
                        val valStr = tagObj?.optString("value", "") ?: ""
                        if (valStr.contains("m") || valStr.contains("h") || valStr.contains("s")) {
                            durationStr = valStr
                        }
                    }
                }
            }

            if (title.isNullOrBlank()) {
                val directTitle = node.optString("title", "")
                if (directTitle.isNotBlank() && !isForbiddenTitle(directTitle)) {
                    title = directTitle
                } else {
                    val altObj = node.optJSONObject("alt")
                    val altLabel = altObj?.optString("label", "") ?: ""
                    if (altLabel.isNotBlank()) {
                        title = altLabel.split(",")[0].trim()
                    }
                }
            }

            // Thumbnail
            val imgObj = node.optJSONObject("image")
                ?: node.optJSONObject("vertical_image")
                ?: node.optJSONObject("horizontal_image")
                ?: node.optJSONObject("expanded_content_poster")?.optJSONObject("image")

            if (imgObj != null) {
                val src = imgObj.optString("src", "")
                if (src.isNotBlank()) {
                    thumb = formatHotstarImageUrl(src)
                }
            }

            // Actions / Page Slug
            val actionsObj = node.optJSONObject("actions")
            if (actionsObj != null) {
                val onClickArr = actionsObj.optJSONArray("on_click")
                if (onClickArr != null) {
                    for (i in 0 until onClickArr.length()) {
                        val act = onClickArr.optJSONObject(i) ?: continue
                        val pn = act.optJSONObject("page_navigation")
                        val ps = pn?.optString("page_slug", "") ?: ""
                        if (ps.isNotBlank()) {
                            pageSlug = ps
                        }
                        val op = act.optJSONObject("open_page_overlay")
                        val ops = op?.optString("page_slug", "") ?: ""
                        if (ops.isNotBlank() && pageSlug.isNullOrBlank()) {
                            pageSlug = ops
                        }
                    }
                }
            }

            val isValidMedia = (pageSlug != null && (
                pageSlug.contains("/movies/") ||
                pageSlug.contains("/shows/") ||
                pageSlug.contains("/clips/") ||
                pageSlug.contains("/sports/") ||
                pageSlug.contains("/tv/") ||
                pageSlug.contains("/watch")
            )) || (contentId.isNotBlank() && contentId.all { it.isDigit() } && contentId.length >= 5)

            if (isValidMedia && !title.isNullOrBlank() && !isForbiddenTitle(title)) {
                val cleanTitle = title.split(",")[0].trim()
                val uid = contentId.ifBlank { pageSlug ?: "" }
                if (uid.isNotBlank() && !seen.contains(uid) && cleanTitle.length > 1) {
                    seen.add(uid)
                    val fullUrl = when {
                        !pageSlug.isNullOrBlank() -> "https://www.hotstar.com$pageSlug"
                        contentId.isNotBlank() -> "https://www.hotstar.com/in/movies/content/$contentId"
                        else -> "https://www.hotstar.com/in/home"
                    }

                    list.add(
                        VideoItem(
                            id = fullUrl,
                            title = cleanTitle,
                            uploaderName = author,
                            uploaderUrl = "https://www.hotstar.com",
                            uploaderAvatarUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1e/Disney%2B_Hotstar_logo.svg/200px-Disney%2B_Hotstar_logo.svg.png",
                            viewCount = 100000L,
                            durationSeconds = parseDurationToSeconds(durationStr),
                            uploadDate = "Official",
                            thumbnailUrl = thumb ?: "https://img10.hotstar.com/image/upload/f_auto,q_90,w_384/sources/r1/cms/prod/6259/1769436036259-v",
                            providerId = PROVIDER_ID,
                            description = description ?: "Watch $cleanTitle on JioHotstar with pristine high-definition audio and video."
                        )
                    )
                }
            }

            // Recurse into all keys
            val keys = node.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                extractFromJsonObject(node.opt(k), list, seen, limit)
            }
        } else if (node is JSONArray) {
            for (i in 0 until node.length()) {
                extractFromJsonObject(node.opt(i), list, seen, limit)
            }
        }
    }

    private fun parseHotstarHtmlRegex(html: String, list: MutableList<VideoItem>, seen: MutableSet<String>, limit: Int) {
        val linkPattern = Pattern.compile("href=\"(/in/(?:movies|shows|clips|sports|tv)/[^\"]+)\"", Pattern.CASE_INSENSITIVE)
        val matcher = linkPattern.matcher(html)

        while (matcher.find() && list.size < limit) {
            val slug = matcher.group(1) ?: continue
            if (slug.contains("?") || seen.contains(slug)) continue
            seen.add(slug)

            val segments = slug.split("/").filter { it.isNotBlank() }
            val rawTitle = if (segments.size >= 3) segments[2].replace("-", " ").capitalizeWords() else "Hotstar Video"
            val fullUrl = "https://www.hotstar.com$slug"

            list.add(
                VideoItem(
                    id = fullUrl,
                    title = rawTitle,
                    uploaderName = "JioHotstar",
                    uploaderUrl = "https://www.hotstar.com",
                    uploaderAvatarUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1e/Disney%2B_Hotstar_logo.svg/200px-Disney%2B_Hotstar_logo.svg.png",
                    viewCount = 50000L,
                    durationSeconds = 0L,
                    uploadDate = "Official",
                    thumbnailUrl = "https://img10.hotstar.com/image/upload/f_auto,q_90,w_384/sources/r1/cms/prod/6259/1769436036259-v",
                    providerId = PROVIDER_ID,
                    description = "Watch $rawTitle on JioHotstar."
                )
            )
        }
    }

    private fun isForbiddenTitle(t: String): Boolean {
        val lower = t.trim().lowercase()
        return lower in listOf(
            "watch now", "view more", "play", "home", "search", "series", "movies",
            "cricket", "sports", "explore", "settings", "menuwidget", "scrollabletraywidget",
            "editorialcollection", "editorialcollectionvertical"
        )
    }

    private fun formatHotstarImageUrl(src: String): String {
        return if (src.startsWith("http://") || src.startsWith("https://")) {
            src
        } else {
            "https://img10.hotstar.com/image/upload/f_auto,q_90,w_384/$src"
        }
    }

    private fun parseDurationToSeconds(durStr: String?): Long {
        if (durStr.isNullOrBlank()) return 0L
        try {
            var total = 0L
            val hMatch = Pattern.compile("(\\d+)\\s*h").matcher(durStr)
            if (hMatch.find()) total += (hMatch.group(1)?.toLongOrNull() ?: 0L) * 3600
            val mMatch = Pattern.compile("(\\d+)\\s*m").matcher(durStr)
            if (mMatch.find()) total += (mMatch.group(1)?.toLongOrNull() ?: 0L) * 60
            val sMatch = Pattern.compile("(\\d+)\\s*s").matcher(durStr)
            if (sMatch.find()) total += (sMatch.group(1)?.toLongOrNull() ?: 0L)
            return total
        } catch (_: Exception) {
            return 0L
        }
    }

    private fun String.capitalizeWords(): String {
        return split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    suspend fun getStreamData(urlOrId: String, context: Context?): StreamData? = withContext(Dispatchers.IO) {
        val fullUrl = if (urlOrId.startsWith("http://") || urlOrId.startsWith("https://")) {
            urlOrId
        } else {
            "https://www.hotstar.com/in/movies/content/$urlOrId"
        }

        if (context != null) {
            val result = YtDlpResolver.extractStreamInfo(context, fullUrl)
            if (result is YouTubeExtractorHelper.ExtractionResult.Success) {
                return@withContext result.streamData
            }
        }
        return@withContext null
    }
}
