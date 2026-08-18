package com.example.extractor

import android.content.Context
import android.util.Log
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

object MultiSourceProvider {
    private const val TAG = "MultiSourceProvider"

    private val httpClient = OkHttpClient.Builder()
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

    suspend fun getHome(context: Context, providerId: String, limit: Int = 20): List<VideoItem> = withContext(Dispatchers.IO) {
        val items = when (providerId.lowercase()) {
            "dailymotion" -> getDailymotionHome(limit)
            "vimeo" -> getVimeoHome(limit)
            "pornhub" -> getPornhubHome(limit)
            "xvideos" -> getXVideosHome(limit)
            "xhamster" -> getXHamsterHome(limit)
            "redtube" -> getRedTubeHome(limit)
            "youporn" -> getYouPornHome(limit)
            else -> emptyList()
        }

        if (items.isNotEmpty()) {
            return@withContext items
        }

        val fallbackQuery = when (providerId.lowercase()) {
            "dailymotion" -> "trending"
            "vimeo" -> "staff picks"
            "bilibili" -> "anime"
            "pornhub" -> "trending"
            "xvideos" -> "popular"
            "xhamster" -> "popular"
            "redtube" -> "trending"
            "youporn" -> "popular"
            "4tube" -> "video"
            "beeg" -> "popular"
            "rule34video" -> "animation"
            else -> "popular"
        }
        try {
            YtDlpResolver.search(context, fallbackQuery, limit, providerId)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun search(context: Context, providerId: String, query: String, limit: Int = 20): List<VideoItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val items = when (providerId.lowercase()) {
            "dailymotion" -> searchDailymotion(query, limit)
            "vimeo" -> searchVimeo(query, limit)
            "pornhub" -> searchPornhub(query, limit)
            "xvideos" -> searchXVideos(query, limit)
            "xhamster" -> searchXHamster(query, limit)
            "redtube" -> searchRedTube(query, limit)
            "youporn" -> searchYouPorn(query, limit)
            else -> emptyList()
        }

        if (items.isNotEmpty()) {
            return@withContext items
        }

        try {
            YtDlpResolver.search(context, query, limit, providerId)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ------------------- DAILYMOTION -------------------
    private fun getDailymotionHome(limit: Int): List<VideoItem> {
        val url = "https://api.dailymotion.com/videos?fields=id,title,owner.username,thumbnail_720_url,duration,views_total&flags=featured&limit=$limit"
        return parseDailymotionApi(url)
    }

    private fun searchDailymotion(query: String, limit: Int): List<VideoItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://api.dailymotion.com/videos?fields=id,title,owner.username,thumbnail_720_url,duration,views_total&search=$encoded&limit=$limit"
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
        val url = "https://vimeo.com/api/v2/channel/staffpicks/videos.json"
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder().url(url).build()
            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until minOf(jsonArray.length(), limit)) {
                val item = jsonArray.optJSONObject(i) ?: continue
                val id = item.optLong("id", 0L)
                val videoUrl = item.optString("url", "https://vimeo.com/$id")
                val title = item.optString("title", "Vimeo Video")
                val uploader = item.optString("user_name", "Vimeo")
                val thumb = item.optString("thumbnail_large", item.optString("thumbnail_medium", ""))
                val duration = item.optLong("duration", -1L)
                val views = item.optLong("stats_number_of_plays", -1L)

                list.add(
                    VideoItem(
                        id = videoUrl,
                        title = title,
                        uploaderName = uploader,
                        durationSeconds = duration,
                        viewCount = views,
                        thumbnailUrl = thumb,
                        providerId = "vimeo"
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vimeo home error: ${e.message}")
        }
        return list
    }

    private fun searchVimeo(query: String, limit: Int): List<VideoItem> {
        return emptyList()
    }

    // ------------------- PORNHUB -------------------
    private fun getPornhubHome(limit: Int): List<VideoItem> {
        return parsePornhubHtml("https://www.pornhub.com/video?o=trending", limit)
    }

    private fun searchPornhub(query: String, limit: Int): List<VideoItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return parsePornhubHtml("https://www.pornhub.com/video/search?search=$encoded", limit)
    }

    private fun parsePornhubHtml(targetUrl: String, limit: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder().url(targetUrl).header("Cookie", "age_verified=1").build()
            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val pattern = Pattern.compile("<a\\s+href=\"(/view_video\\.php\\?viewkey=([^\"]+))\"[^>]*title=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(html)
            val seenKeys = mutableSetOf<String>()

            val imgPattern = Pattern.compile("data-mediumthumbnail=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE)
            val imgMatcher = imgPattern.matcher(html)
            val thumbs = mutableListOf<String>()
            while (imgMatcher.find()) {
                thumbs.add(imgMatcher.group(1) ?: "")
            }

            var thumbIdx = 0
            while (matcher.find() && list.size < limit) {
                val href = matcher.group(1) ?: continue
                val viewkey = matcher.group(2) ?: continue
                val title = matcher.group(3) ?: "Pornhub Video"

                if (seenKeys.contains(viewkey)) continue
                seenKeys.add(viewkey)

                val thumb = if (thumbIdx < thumbs.size) thumbs[thumbIdx++] else ""

                list.add(
                    VideoItem(
                        id = "https://www.pornhub.com$href",
                        title = title,
                        uploaderName = "Pornhub",
                        thumbnailUrl = thumb,
                        providerId = "pornhub"
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pornhub parse error: ${e.message}")
        }
        return list
    }

    // ------------------- XVIDEOS -------------------
    private fun getXVideosHome(limit: Int): List<VideoItem> {
        return parseXVideosHtml("https://www.xvideos.com/new/1", limit)
    }

    private fun searchXVideos(query: String, limit: Int): List<VideoItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return parseXVideosHtml("https://www.xvideos.com/?k=$encoded", limit)
    }

    private fun parseXVideosHtml(targetUrl: String, limit: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder().url(targetUrl).build()
            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val pattern = Pattern.compile("<a\\s+href=\"(/video(\\d+)/[^\"]+)\"[^>]*title=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(html)
            val seenIds = mutableSetOf<String>()

            val thumbPattern = Pattern.compile("data-src=\"(https://[^\"]+?\\.jpg[^\"]*)\"", Pattern.CASE_INSENSITIVE)
            val thumbMatcher = thumbPattern.matcher(html)
            val thumbs = mutableListOf<String>()
            while (thumbMatcher.find()) {
                thumbs.add(thumbMatcher.group(1) ?: "")
            }

            var thumbIdx = 0
            while (matcher.find() && list.size < limit) {
                val path = matcher.group(1) ?: continue
                val videoId = matcher.group(2) ?: continue
                val title = matcher.group(3) ?: "XVideos"

                if (seenIds.contains(videoId)) continue
                seenIds.add(videoId)

                val thumb = if (thumbIdx < thumbs.size) thumbs[thumbIdx++] else ""

                list.add(
                    VideoItem(
                        id = "https://www.xvideos.com$path",
                        title = title,
                        uploaderName = "XVideos",
                        thumbnailUrl = thumb,
                        providerId = "xvideos"
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "XVideos parse error: ${e.message}")
        }
        return list
    }

    // ------------------- XHAMSTER -------------------
    private fun getXHamsterHome(limit: Int): List<VideoItem> {
        return parseXHamsterHtml("https://xhamster.com/trending", limit)
    }

    private fun searchXHamster(query: String, limit: Int): List<VideoItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return parseXHamsterHtml("https://xhamster.com/search/$encoded", limit)
    }

    private fun parseXHamsterHtml(targetUrl: String, limit: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder().url(targetUrl).build()
            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val pattern = Pattern.compile("<a\\s+href=\"(https://xhamster\\.com/videos/[^\"]+)\"[^>]*title=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(html)
            val seen = mutableSetOf<String>()

            while (matcher.find() && list.size < limit) {
                val videoUrl = matcher.group(1) ?: continue
                val title = matcher.group(2) ?: "XHamster Video"

                if (seen.contains(videoUrl)) continue
                seen.add(videoUrl)

                list.add(
                    VideoItem(
                        id = videoUrl,
                        title = title,
                        uploaderName = "XHamster",
                        thumbnailUrl = "",
                        providerId = "xhamster"
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "XHamster parse error: ${e.message}")
        }
        return list
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
    private fun getYouPornHome(limit: Int): List<VideoItem> {
        return parseYouPornHtml("https://www.youporn.com/most_viewed/", limit)
    }

    private fun searchYouPorn(query: String, limit: Int): List<VideoItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return parseYouPornHtml("https://www.youporn.com/search/?query=$encoded", limit)
    }

    private fun parseYouPornHtml(targetUrl: String, limit: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder().url(targetUrl).build()
            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val pattern = Pattern.compile("<a\\s+href=\"(/watch/(\\d+)/[^\"]*)\"[^>]*title=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(html)
            val seen = mutableSetOf<String>()

            while (matcher.find() && list.size < limit) {
                val path = matcher.group(1) ?: continue
                val id = matcher.group(2) ?: continue
                val title = matcher.group(3) ?: "YouPorn Video"

                if (seen.contains(id)) continue
                seen.add(id)

                list.add(
                    VideoItem(
                        id = "https://www.youporn.com$path",
                        title = title,
                        uploaderName = "YouPorn",
                        thumbnailUrl = "",
                        providerId = "youporn"
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "YouPorn parse error: ${e.message}")
        }
        return list
    }
}
