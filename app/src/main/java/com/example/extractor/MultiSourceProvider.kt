package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.VideoItem
import com.example.model.parseDurationToSeconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object MultiSourceProvider {
    private const val TAG = "MultiSourceProvider"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            chain.proceed(req)
        }
        .build()

    suspend fun getHome(context: Context, providerId: String, limit: Int = 20, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val pid = providerId.lowercase()

        // 1. Try custom scrapers / APIs first
        val customItems = when (pid) {
            "dailymotion" -> getDailymotionHome(limit, page)
            "vimeo" -> getVimeoHome(limit)
            "bilibili" -> getBilibiliHome(page, limit)
            "pornhub" -> getPornhubHome(limit, page)
            "xvideos" -> getXVideosHome(limit, page)
            "xhamster" -> getXHamsterHome(limit, page)
            "redtube" -> RedTubeProvider.getHome(page, limit)
            "youporn" -> getYouPornHome(page, limit)
            "hotstar", "jiohotstar" -> HotstarProvider.getHome(page, limit)
            "beeg" -> getBeegHome(limit)
            "4tube" -> FourTubeProvider.getHome(page, limit)
            "eporner" -> EpornerProvider.getHome(limit, page)
            "rule34video" -> parseRule34Html(if (page > 1) "https://rule34video.com/?page=$page" else "https://rule34video.com/", limit)
            else -> emptyList()
        }

        customItems
    }

    suspend fun search(context: Context, providerId: String, query: String, limit: Int = 20, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val pid = providerId.lowercase()

        when (pid) {
            "dailymotion" -> searchDailymotion(query, limit, page)
            "vimeo" -> searchVimeo(query, limit)
            "bilibili" -> searchBilibili(query, page, limit)
            "pornhub" -> searchPornhub(query, limit, page)
            "xvideos" -> searchXVideos(query, limit, page)
            "xhamster" -> searchXHamster(query, limit, page)
            "redtube" -> RedTubeProvider.search(query, page, limit)
            "youporn" -> searchYouPorn(query, page, limit)
            "hotstar", "jiohotstar" -> HotstarProvider.search(query, page, limit)
            "beeg" -> BeegProvider.search(query, limit)
            "4tube" -> FourTubeProvider.search(query, page, limit)
            "eporner" -> EpornerProvider.search(query, limit, page)
            "rule34video" -> parseRule34Html("https://rule34video.com/search/${URLEncoder.encode(query, "UTF-8")}/${if (page > 1) "?page=$page" else ""}", limit)
            else -> emptyList()
        }
    }

    // ------------------- BILIBILI -------------------
    private fun getBilibiliHome(page: Int = 1, limit: Int = 20): List<VideoItem> {
        val url = "https://api.bilibili.com/x/web-interface/popular?ps=$limit&pn=$page"
        return parseBilibiliJsonApi(url)
    }

    private fun searchBilibili(query: String, page: Int = 1, limit: Int = 20): List<VideoItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword=$encoded&page=$page"
        return parseBilibiliSearchJsonApi(url)
    }

    private fun parseBilibiliJsonApi(url: String): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://www.bilibili.com/")
                .build()
            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val jsonObj = JSONObject(jsonStr)
            val dataObj = jsonObj.optJSONObject("data") ?: return list
            val array = dataObj.optJSONArray("list") ?: return list

            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val bvid = item.optString("bvid", "")
                if (bvid.isBlank()) continue
                val rawTitle = item.optString("title", "Bilibili Video")
                val cleanTitle = rawTitle.replace(Regex("<[^>]*>"), "").trim()
                val translatedTitle = try {
                    com.example.util.SubtitleTranslator.translateTextSync(cleanTitle, targetLang = "en", sourceLang = "zh")
                } catch (e: Exception) {
                    cleanTitle
                }
                val finalTitle = if (translatedTitle.isNotBlank() && translatedTitle != cleanTitle) {
                    translatedTitle
                } else {
                    cleanTitle
                }

                var pic = item.optString("pic", "")
                if (pic.startsWith("//")) pic = "https:$pic"
                val owner = item.optJSONObject("owner")?.optString("name", "Bilibili") ?: "Bilibili"
                val duration = item.optLong("duration", -1L)
                val stat = item.optJSONObject("stat")
                val viewCount = stat?.optLong("view", -1L) ?: -1L

                list.add(
                    VideoItem(
                        id = "https://www.bilibili.com/video/$bvid",
                        title = finalTitle,
                        uploaderName = owner,
                        durationSeconds = duration,
                        viewCount = viewCount,
                        thumbnailUrl = pic,
                        providerId = "bilibili"
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bilibili parse error: ${e.message}")
        }
        return list
    }

    private fun parseBilibiliSearchJsonApi(url: String): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://www.bilibili.com/")
                .build()
            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val jsonObj = JSONObject(jsonStr)
            val dataObj = jsonObj.optJSONObject("data") ?: return list
            val array = dataObj.optJSONArray("result") ?: return list

            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val bvid = item.optString("bvid", "")
                if (bvid.isBlank()) continue
                val rawTitle = item.optString("title", "Bilibili Video")
                val cleanTitle = rawTitle.replace(Regex("<[^>]*>"), "").trim()
                val translatedTitle = try {
                    com.example.util.SubtitleTranslator.translateTextSync(cleanTitle, targetLang = "en", sourceLang = "zh")
                } catch (e: Exception) {
                    cleanTitle
                }
                val finalTitle = if (translatedTitle.isNotBlank() && translatedTitle != cleanTitle) {
                    translatedTitle
                } else {
                    cleanTitle
                }

                var pic = item.optString("pic", "")
                if (pic.startsWith("//")) pic = "https:$pic"
                val author = item.optString("author", "Bilibili")
                val play = item.optLong("play", -1L)

                list.add(
                    VideoItem(
                        id = "https://www.bilibili.com/video/$bvid",
                        title = finalTitle,
                        uploaderName = author,
                        durationSeconds = -1L,
                        viewCount = play,
                        thumbnailUrl = pic,
                        providerId = "bilibili"
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bilibili search parse error: ${e.message}")
        }
        return list
    }

    // ------------------- BEEG -------------------
    private fun getBeegHome(limit: Int): List<VideoItem> {
        return BeegProvider.getHome(limit)
    }

    // ------------------- DAILYMOTION -------------------
    private fun getDailymotionHome(limit: Int, page: Int = 1): List<VideoItem> {
        val url = "https://api.dailymotion.com/videos?fields=id,title,owner.username,thumbnail_720_url,duration,views_total&flags=featured&limit=$limit&page=$page"
        return parseDailymotionApi(url)
    }

    private fun searchDailymotion(query: String, limit: Int, page: Int = 1): List<VideoItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://api.dailymotion.com/videos?fields=id,title,owner.username,thumbnail_720_url,duration,views_total&search=$encoded&limit=$limit&page=$page"
        return parseDailymotionApi(url)
    }

    private fun parseDailymotionApi(apiUrl: String): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder().url(apiUrl).build()
            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val json = JSONObject(jsonStr)
            val listArr = json.optJSONArray("list") ?: return list
            for (i in 0 until listArr.length()) {
                val item = listArr.optJSONObject(i) ?: continue
                val id = item.optString("id", "")
                if (id.isBlank()) continue
                val title = item.optString("title", "Dailymotion Video")
                val ownerObj = item.optJSONObject("owner")
                val uploader = ownerObj?.optString("username", "Dailymotion") ?: "Dailymotion"
                val thumb = item.optString("thumbnail_720_url", "")
                val duration = item.optLong("duration", -1L)
                val views = item.optLong("views_total", -1L)

                list.add(
                    VideoItem(
                        id = "https://www.dailymotion.com/video/$id",
                        title = title,
                        uploaderName = uploader,
                        durationSeconds = duration,
                        viewCount = views,
                        thumbnailUrl = thumb,
                        providerId = "dailymotion"
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Dailymotion API error: ${e.message}")
        }
        return list
    }

    // ------------------- VIMEO -------------------
    private fun getVimeoHome(limit: Int): List<VideoItem> {
        return VimeoProvider.getHome(limit)
    }

    private fun searchVimeo(query: String, limit: Int): List<VideoItem> {
        return VimeoProvider.search(query, limit)
    }

    // ------------------- PORNHUB -------------------
    private fun getPornhubHome(limit: Int, page: Int = 1): List<VideoItem> {
        val items = PornhubProvider.getHome(limit, page)
        if (items.isNotEmpty()) return items
        val pageParam = if (page > 1) "&page=$page" else ""
        return parsePornhubHtml("https://www.pornhub.com/video?o=trending$pageParam", limit)
    }

    private fun searchPornhub(query: String, limit: Int, page: Int = 1): List<VideoItem> {
        val items = PornhubProvider.search(query, limit, page)
        if (items.isNotEmpty()) return items
        val encoded = URLEncoder.encode(query, "UTF-8")
        val pageParam = if (page > 1) "&page=$page" else ""
        return parsePornhubHtml("https://www.pornhub.com/video/search?search=$encoded$pageParam", limit)
    }

    private fun parsePornhubHtml(targetUrl: String, limit: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        val mirrors = listOf(
            targetUrl,
            targetUrl.replace("www.pornhub.com", "rt.pornhub.com"),
            targetUrl.replace("www.pornhub.com", "www.pornhub.org"),
            targetUrl.replace("www.pornhub.com", "cn.pornhub.com")
        ).distinct()

        val bypassIp = "208.80.154.224"
        val bypassCookies = "age_verified=1; platform=pc; accessAgeDisclaimerPH=1; ip_country=US; has_consent=1; expired_cookies=1; il=en"

        for (url in mirrors) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Cookie", bypassCookies)
                    .header("Referer", "https://www.pornhub.com/")
                    .header("X-Forwarded-For", bypassIp)
                    .header("X-Real-IP", bypassIp)
                    .header("CF-Connecting-IP", bypassIp)
                    .header("Client-IP", bypassIp)
                    .build()

                val pornhubClient = httpClient.newBuilder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()

                val (code, bodyStr) = pornhubClient.newCall(req).execute().use { resp ->
                    Pair(resp.code, if (resp.isSuccessful) resp.body?.string() else null)
                }

                if (bodyStr.isNullOrEmpty() || bodyStr.length < 500) continue

                val seenKeys = mutableSetOf<String>()
                val vkPattern = Pattern.compile("(?:data-video-vkey=\"|href=\"/view_video\\.php\\?viewkey=)([a-zA-Z0-9_-]+)", Pattern.CASE_INSENSITIVE)
                val vkMatcher = vkPattern.matcher(bodyStr)

                while (vkMatcher.find() && list.size < limit) {
                    val viewkey = vkMatcher.group(1) ?: continue
                    if (seenKeys.contains(viewkey)) continue
                    seenKeys.add(viewkey)

                    val startIdx = vkMatcher.start()
                    val blockStart = (startIdx - 150).coerceAtLeast(0)
                    val blockEnd = (startIdx + 1500).coerceAtMost(bodyStr.length)
                    val block = bodyStr.substring(blockStart, blockEnd)

                    // 1. Extract title
                    var title = "Pornhub Video"
                    val titleMatcher = Pattern.compile("(?:title|alt|data-title)=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(block)
                    while (titleMatcher.find()) {
                        val candidate = titleMatcher.group(1) ?: continue
                        if (candidate.isNotBlank() && !candidate.contains("Pornhub", ignoreCase = true) && candidate.length > 3) {
                            title = candidate
                            break
                        }
                    }

                    // 2. Extract thumbnail
                    var thumb = ""
                    val thumbMatcher = Pattern.compile("(?:data-mediumthumbnail|data-thumb_url|data-src|data-image|data-poster|src)=\"([^\"]*(?:phncdn|pornhub|jpg|jpeg|webp|png)[^\"]*)\"", Pattern.CASE_INSENSITIVE).matcher(block)
                    if (thumbMatcher.find()) {
                        thumb = thumbMatcher.group(1) ?: ""
                    }
                    if (thumb.startsWith("//")) {
                        thumb = "https:$thumb"
                    }

                    // 3. Extract duration
                    var duration = -1L
                    val durationMatcher = Pattern.compile("(?:<var class=\"duration\">|data-duration=\"|<span class=\"duration\">)([0-9:]+)", Pattern.CASE_INSENSITIVE).matcher(block)
                    if (durationMatcher.find()) {
                        val durStr = durationMatcher.group(1) ?: ""
                        val parts = durStr.split(":")
                        if (parts.size == 2) {
                            duration = (parts[0].toLongOrNull() ?: 0L) * 60L + (parts[1].toLongOrNull() ?: 0L)
                        } else if (parts.size == 3) {
                            duration = (parts[0].toLongOrNull() ?: 0L) * 3600L + (parts[1].toLongOrNull() ?: 0L) * 60L + (parts[2].toLongOrNull() ?: 0L)
                        }
                    }

                    list.add(
                        VideoItem(
                            id = "https://www.pornhub.com/view_video.php?viewkey=$viewkey",
                            title = title,
                            uploaderName = "Pornhub",
                            thumbnailUrl = thumb,
                            durationSeconds = duration,
                            providerId = "pornhub"
                        )
                    )
                }

                if (list.isNotEmpty()) {
                    break
                }
            } catch (e: Exception) {
                Log.w(TAG, "Pornhub parse error for $url: ${e.message}")
            }
        }
        return list
    }

    // ------------------- XVIDEOS -------------------
    private fun getXVideosHome(limit: Int, page: Int = 1): List<VideoItem> {
        return XVideosProvider.getHome(limit, page)
    }

    private fun searchXVideos(query: String, limit: Int, page: Int = 1): List<VideoItem> {
        return XVideosProvider.search(query, limit, page)
    }

    // ------------------- XHAMSTER -------------------
    private fun getXHamsterHome(limit: Int, page: Int = 1): List<VideoItem> {
        return XHamsterProvider.getHome(limit, page)
    }

    private fun searchXHamster(query: String, limit: Int, page: Int = 1): List<VideoItem> {
        return XHamsterProvider.search(query, limit, page)
    }

    // ------------------- REDTUBE -------------------
    private fun getRedTubeHome(limit: Int): List<VideoItem> {
        return parseRedTubeHtml("https://www.redtube.com/top", limit)
    }

    private fun searchRedTube(query: String, limit: Int): List<VideoItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return parseRedTubeHtml("https://www.redtube.com/?search=$encoded", limit)
    }

    private fun parseRedTubeHtml(targetUrl: String, limit: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder().url(targetUrl).build()
            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val pattern = Pattern.compile("<a\\s+href=\"(/(\\d+))\"[^>]*title=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(html)
            val seen = mutableSetOf<String>()

            while (matcher.find() && list.size < limit) {
                val path = matcher.group(1) ?: continue
                val id = matcher.group(2) ?: continue
                val title = matcher.group(3) ?: "RedTube Video"

                if (seen.contains(id)) continue
                seen.add(id)

                list.add(
                    VideoItem(
                        id = "https://www.redtube.com$path",
                        title = title,
                        uploaderName = "RedTube",
                        thumbnailUrl = "",
                        providerId = "redtube"
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "RedTube parse error: ${e.message}")
        }
        return list
    }

    // ------------------- YOUPORN -------------------
    private fun getYouPornHome(page: Int = 1, limit: Int = 30): List<VideoItem> {
        return YouPornProvider.getHome(page, limit)
    }

    private fun searchYouPorn(query: String, page: Int = 1, limit: Int = 30): List<VideoItem> {
        return YouPornProvider.search(query, page, limit)
    }

    // ------------------- 4TUBE -------------------
    private fun parse4tubeHtml(targetUrl: String, limit: Int): List<VideoItem> {
        return FourTubeProvider.getHome(1, limit)
    }

    // ------------------- RULE34VIDEO -------------------
    private fun parseRule34Html(targetUrl: String, limit: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("Referer", "https://rule34video.com/")
                .build()
            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            // 1. Block-level parsing for item containers
            val itemPattern = Pattern.compile("<div[^>]*class=\"[^\"]*(?:item|thumb|video-item)[^\"]*\"[^>]*>(.*?)</div>\\s*</div>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
            val itemMatcher = itemPattern.matcher(html)
            val seen = mutableSetOf<String>()

            while (itemMatcher.find() && list.size < limit) {
                val block = itemMatcher.group(1) ?: continue

                val linkMatcher = Pattern.compile("href=\"(https?://rule34video\\.com)?(/video/(\\d+)/[^\"]*)\"", Pattern.CASE_INSENSITIVE).matcher(block)
                if (!linkMatcher.find()) continue
                val rawPath = linkMatcher.group(2) ?: continue
                val id = linkMatcher.group(3) ?: continue
                if (seen.contains(id)) continue
                seen.add(id)

                val fullUrl = "https://rule34video.com$rawPath"

                // Extract title
                var title = "Rule34 Video #$id"
                val titleMatcher = Pattern.compile("title=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(block)
                if (titleMatcher.find()) {
                    title = titleMatcher.group(1) ?: title
                } else {
                    val altMatcher = Pattern.compile("alt=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(block)
                    if (altMatcher.find()) {
                        title = altMatcher.group(1) ?: title
                    }
                }

                // Extract thumbnail
                var thumb = ""
                val thumbMatcher = Pattern.compile("(?:data-original|data-src|data-thumb|src)=\"([^\"]*(?:contents/videos_screenshots|preview|thumb|screenshots)[^\"]*)\"", Pattern.CASE_INSENSITIVE).matcher(block)
                if (thumbMatcher.find()) {
                    thumb = thumbMatcher.group(1) ?: ""
                }
                if (thumb.isBlank()) {
                    val genericImg = Pattern.compile("(?:data-original|data-src|data-thumb|src)=\"([^\"]+?\\.(?:jpg|jpeg|webp|png)[^\"]*)\"", Pattern.CASE_INSENSITIVE).matcher(block)
                    if (genericImg.find()) {
                        thumb = genericImg.group(1) ?: ""
                    }
                }
                if (thumb.startsWith("//")) {
                    thumb = "https:$thumb"
                } else if (thumb.startsWith("/")) {
                    thumb = "https://rule34video.com$thumb"
                }

                // Extract duration
                var duration = -1L
                val durationMatcher = Pattern.compile("<span[^>]*class=\"[^\"]*duration[^\"]*\"[^>]*>([0-9:]+)</span>", Pattern.CASE_INSENSITIVE).matcher(block)
                if (durationMatcher.find()) {
                    val durStr = durationMatcher.group(1) ?: ""
                    val parts = durStr.split(":")
                    if (parts.size == 2) {
                        duration = (parts[0].toLongOrNull() ?: 0L) * 60L + (parts[1].toLongOrNull() ?: 0L)
                    } else if (parts.size == 3) {
                        duration = (parts[0].toLongOrNull() ?: 0L) * 3600L + (parts[1].toLongOrNull() ?: 0L) * 60L + (parts[2].toLongOrNull() ?: 0L)
                    }
                }

                list.add(
                    VideoItem(
                        id = fullUrl,
                        title = title,
                        uploaderName = "Rule34Video",
                        thumbnailUrl = thumb,
                        durationSeconds = duration,
                        providerId = "rule34video"
                    )
                )
            }

            // Fallback general pattern if block parsing didn't collect enough
            if (list.isEmpty()) {
                val pattern = Pattern.compile("<a\\s+[^>]*href=\"(https://rule34video\\.com/video/(\\d+)/([^\"]*))\"[^>]*title=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE)
                val matcher = pattern.matcher(html)
                while (matcher.find() && list.size < limit) {
                    val url = matcher.group(1) ?: continue
                    val id = matcher.group(2) ?: continue
                    val title = matcher.group(4) ?: "Rule34Video"
                    if (seen.contains(id)) continue
                    seen.add(id)

                    val startIdx = matcher.start()
                    val endIdx = (startIdx + 300).coerceAtMost(html.length)
                    val snippet = html.substring(startIdx, endIdx)
                    var durSec = -1L
                    val durMatch = Pattern.compile("([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?)").matcher(snippet)
                    if (durMatch.find()) {
                        durSec = parseDurationToSeconds(durMatch.group(1))
                    }

                    list.add(
                        VideoItem(
                            id = url,
                            title = title,
                            uploaderName = "Rule34Video",
                            thumbnailUrl = "https://rule34video.com/contents/videos_screenshots/${(id.toIntOrNull() ?: 0) / 1000 * 1000}/$id/preview.jpg",
                            durationSeconds = durSec,
                            providerId = "rule34video"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Rule34Video parse error: ${e.message}")
        }
        return list
    }
}
