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

    suspend fun getHome(context: Context, providerId: String, limit: Int = 20, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val pid = providerId.lowercase()

        // 1. Try custom scrapers / APIs first
        val customItems = when (pid) {
            "dailymotion" -> getDailymotionHome(limit)
            "vimeo" -> getVimeoHome(limit)
            "bilibili" -> getBilibiliHome(page, limit)
            "pornhub" -> getPornhubHome(limit)
            "xvideos" -> getXVideosHome(limit)
            "xhamster" -> getXHamsterHome(limit)
            "redtube" -> getRedTubeHome(limit)
            "youporn" -> getYouPornHome(limit)
            "beeg" -> getBeegHome(limit)
            "4tube" -> parse4tubeHtml("https://www.4tube.com/", limit)
            "rule34video" -> parseRule34Html("https://rule34video.com/", limit)
            else -> emptyList()
        }

        if (customItems.isNotEmpty()) {
            return@withContext customItems
        }

        // 2. Fallback to YtDlpResolver
        val fallbackQuery = when (pid) {
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
            YtDlpResolver.search(context, fallbackQuery, limit, pid)
        } catch (e: Exception) {
            Log.w(TAG, "YtDlpResolver home search failed for $pid: ${e.message}")
            emptyList()
        }
    }

    suspend fun search(context: Context, providerId: String, query: String, limit: Int = 20): List<VideoItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val pid = providerId.lowercase()

        val customItems = when (pid) {
            "dailymotion" -> searchDailymotion(query, limit)
            "vimeo" -> searchVimeo(query, limit)
            "bilibili" -> searchBilibili(query, 1, limit)
            "pornhub" -> searchPornhub(query, limit)
            "xvideos" -> searchXVideos(query, limit)
            "xhamster" -> searchXHamster(query, limit)
            "redtube" -> searchRedTube(query, limit)
            "youporn" -> searchYouPorn(query, limit)
            "4tube" -> parse4tubeHtml("https://www.4tube.com/search/${URLEncoder.encode(query, "UTF-8")}", limit)
            "rule34video" -> parseRule34Html("https://rule34video.com/search/${URLEncoder.encode(query, "UTF-8")}/", limit)
            else -> emptyList()
        }

        if (customItems.isNotEmpty()) {
            return@withContext customItems
        }

        try {
            YtDlpResolver.search(context, query, limit, pid)
        } catch (e: Exception) {
            Log.w(TAG, "YtDlpResolver search failed for $pid: ${e.message}")
            emptyList()
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
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder()
                .url("https://api.beeg.com/api/v6/index/main/0/pc")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val array = JSONArray(jsonStr)
            for (i in 0 until minOf(array.length(), limit)) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id", "")
                if (id.isBlank()) continue
                val title = item.optString("title", "Beeg Video")
                val thumb = "https://img.beeg.com/240x180/$id.jpg"
                list.add(
                    VideoItem(
                        id = "https://beeg.com/$id",
                        title = title,
                        uploaderName = "Beeg",
                        thumbnailUrl = thumb,
                        providerId = "beeg"
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Beeg parse error: ${e.message}")
        }
        return list
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
            val req = Request.Builder()
                .url(targetUrl)
                .header("Cookie", "age_verified=1; platform=pc")
                .header("Referer", "https://www.pornhub.com/")
                .build()
            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            // Parse block by block (videoBox or phimage containers)
            val blockPattern = Pattern.compile("<li[^>]*class=\"[^\"]*(?:videoBox|pcVideoListItem)[^\"]*\"[^>]*>(.*?)</li>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
            val blockMatcher = blockPattern.matcher(html)
            val seenKeys = mutableSetOf<String>()

            while (blockMatcher.find() && list.size < limit) {
                val block = blockMatcher.group(1) ?: continue

                // 1. Extract viewkey / link
                val linkMatcher = Pattern.compile("href=\"(/view_video\\.php\\?viewkey=([a-zA-Z0-9_-]+))\"", Pattern.CASE_INSENSITIVE).matcher(block)
                if (!linkMatcher.find()) continue
                val href = linkMatcher.group(1) ?: continue
                val viewkey = linkMatcher.group(2) ?: continue
                if (seenKeys.contains(viewkey)) continue
                seenKeys.add(viewkey)

                // 2. Extract title
                var title = "Pornhub Video"
                val titleMatcher = Pattern.compile("title=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(block)
                if (titleMatcher.find()) {
                    title = titleMatcher.group(1) ?: title
                } else {
                    val altMatcher = Pattern.compile("alt=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(block)
                    if (altMatcher.find()) {
                        title = altMatcher.group(1) ?: title
                    }
                }

                // 3. Extract thumbnail
                var thumb = ""
                val thumbMatcher = Pattern.compile("(?:data-mediumthumbnail|data-thumb_url|data-src|data-image|src)=\"([^\"]*(?:phncdn|pornhub)[^\"]*)\"", Pattern.CASE_INSENSITIVE).matcher(block)
                if (thumbMatcher.find()) {
                    thumb = thumbMatcher.group(1) ?: ""
                }
                if (thumb.isBlank()) {
                    val genericImg = Pattern.compile("(?:data-mediumthumbnail|data-thumb_url|data-src|data-image|src)=\"(https?://[^\"]+?\\.(?:jpg|jpeg|webp|png)[^\"]*)\"", Pattern.CASE_INSENSITIVE).matcher(block)
                    if (genericImg.find()) {
                        thumb = genericImg.group(1) ?: ""
                    }
                }
                if (thumb.startsWith("//")) {
                    thumb = "https:$thumb"
                }

                // 4. Extract duration
                var duration = -1L
                val durationMatcher = Pattern.compile("<var class=\"duration\">([0-9:]+)</var>", Pattern.CASE_INSENSITIVE).matcher(block)
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
                        id = "https://www.pornhub.com$href",
                        title = title,
                        uploaderName = "Pornhub",
                        thumbnailUrl = thumb,
                        durationSeconds = duration,
                        providerId = "pornhub"
                    )
                )
            }

            // Fallback general regex if block parsing found nothing
            if (list.isEmpty()) {
                val fallbackPattern = Pattern.compile("<a\\s+[^>]*href=\"(/view_video\\.php\\?viewkey=([a-zA-Z0-9_-]+))\"[^>]*title=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE)
                val fallbackMatcher = fallbackPattern.matcher(html)
                while (fallbackMatcher.find() && list.size < limit) {
                    val href = fallbackMatcher.group(1) ?: continue
                    val viewkey = fallbackMatcher.group(2) ?: continue
                    val title = fallbackMatcher.group(3) ?: "Pornhub Video"
                    if (seenKeys.contains(viewkey)) continue
                    seenKeys.add(viewkey)

                    list.add(
                        VideoItem(
                            id = "https://www.pornhub.com$href",
                            title = title,
                            uploaderName = "Pornhub",
                            thumbnailUrl = "",
                            providerId = "pornhub"
                        )
                    )
                }
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

    // ------------------- 4TUBE -------------------
    private fun parse4tubeHtml(targetUrl: String, limit: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder().url(targetUrl).build()
            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val pattern = Pattern.compile("<a\\s+[^>]*href=\"(https://www\\.4tube\\.com/videos/(\\d+)/[^\"]*)\"[^>]*>", Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(html)
            val seen = mutableSetOf<String>()

            while (matcher.find() && list.size < limit) {
                val url = matcher.group(1) ?: continue
                val id = matcher.group(2) ?: continue
                if (seen.contains(id)) continue
                seen.add(id)

                list.add(
                    VideoItem(
                        id = url,
                        title = "4tube Video",
                        uploaderName = "4tube",
                        thumbnailUrl = "",
                        providerId = "4tube"
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "4tube parse error: ${e.message}")
        }
        return list
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

                    list.add(
                        VideoItem(
                            id = url,
                            title = title,
                            uploaderName = "Rule34Video",
                            thumbnailUrl = "https://rule34video.com/contents/videos_screenshots/${(id.toIntOrNull() ?: 0) / 1000 * 1000}/$id/preview.jpg",
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
