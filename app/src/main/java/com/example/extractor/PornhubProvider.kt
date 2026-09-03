package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
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

object PornhubProvider {
    private const val TAG = "PornhubProvider"
    const val PROVIDER_ID = "pornhub"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val BYPASS_IP = "208.80.154.224"
    private const val BYPASS_COOKIES = "age_verified=1; platform=pc; accessAgeDisclaimerPH=1; ip_country=US; has_consent=1; expired_cookies=1; il=en"

    private val defaultHeaders = mapOf(
        "Referer" to "https://www.pornhub.com/",
        "Origin" to "https://www.pornhub.com",
        "User-Agent" to DEFAULT_UA,
        "Cookie" to BYPASS_COOKIES
    )

    fun getHome(limit: Int = 20, page: Int = 1): List<VideoItem> {
        val safePage = if (page < 1) 1 else page
        val apiUrls = listOf(
            "https://www.pornhub.com/webmasters/search?thumbsize=large&ordering=mostviewed&page=$safePage",
            "https://www.pornhub.com/webmasters/search?thumbsize=large&ordering=recent&page=$safePage",
            "https://www.pornhub.com/webmasters/search?thumbsize=large&ordering=featured&page=$safePage",
            "https://www.pornhub.com/webmasters/search?thumbsize=large&ordering=rating&page=$safePage"
        )
        for (u in apiUrls) {
            val list = parseWebmastersApi(u, limit)
            if (list.isNotEmpty()) return list
        }

        // HTML Scraping Fallback
        val htmlUrls = if (safePage > 1) {
            listOf(
                "https://www.pornhub.com/video?o=mv&page=$safePage",
                "https://www.pornhub.com/video?o=tr&page=$safePage",
                "https://rt.pornhub.com/video?o=mv&page=$safePage"
            )
        } else {
            listOf(
                "https://www.pornhub.com/video?o=mv",
                "https://www.pornhub.com/recommended",
                "https://www.pornhub.com/video?o=tr",
                "https://rt.pornhub.com/video?o=mv"
            )
        }

        for (u in htmlUrls) {
            val htmlList = parsePornhubHtml(u, limit)
            if (htmlList.isNotEmpty()) return htmlList
        }

        return emptyList()
    }

    fun search(query: String, limit: Int = 20, page: Int = 1): List<VideoItem> {
        if (query.isBlank()) return getHome(limit, page)
        val safePage = if (page < 1) 1 else page
        val clean = query.trim()

        // 1. Thumbzilla integration: query like thumbzilla:... or search on thumbzilla.com
        if (clean.startsWith("thumbzilla:", ignoreCase = true) || clean.contains("thumbzilla.com")) {
            val tzQuery = clean.replace(Regex("(?i)^thumbzilla:"), "").trim()
            val tzList = searchThumbzilla(tzQuery, limit, safePage)
            if (tzList.isNotEmpty()) return tzList
        }

        // 2. Playlist parsing: pornhub:playlist:<id> or https://www.pornhub.com/playlist/...
        if (clean.startsWith("pornhub:playlist:", ignoreCase = true) || clean.contains("/playlist/")) {
            val plId = clean.substringAfter("pornhub:playlist:").substringAfter("/playlist/").substringBefore("/").substringBefore("?")
            val plUrl = "https://www.pornhub.com/playlist/$plId"
            val plList = parsePornhubHtml(plUrl, limit)
            if (plList.isNotEmpty()) return plList
        }

        // 3. User / Model uploads: pornhub:user:<name>, pornhub:model:<name>, or /users/ /model/
        if (clean.startsWith("pornhub:user:", ignoreCase = true) || clean.startsWith("pornhub:model:", ignoreCase = true) || clean.contains("/users/") || clean.contains("/model/")) {
            val user = clean.substringAfter("pornhub:user:").substringAfter("pornhub:model:").substringAfter("/users/").substringAfter("/model/").substringBefore("/").substringBefore("?")
            val userUrl = if (clean.contains("model")) "https://www.pornhub.com/model/$user/videos?page=$safePage" else "https://www.pornhub.com/users/$user/videos?page=$safePage"
            val userList = parsePornhubHtml(userUrl, limit)
            if (userList.isNotEmpty()) return userList
        }

        val encoded = URLEncoder.encode(clean.replace(Regex("(?i)^(pornhub:|thumbzilla:)?"), "").trim(), "UTF-8")
        val apiUrl = "https://www.pornhub.com/webmasters/search?search=$encoded&thumbsize=large&page=$safePage"
        val list = parseWebmastersApi(apiUrl, limit)
        if (list.isNotEmpty()) return list

        // Secondary fallback to category API search
        val catUrl = "https://www.pornhub.com/webmasters/search?category=$encoded&thumbsize=large&page=$safePage"
        val catList = parseWebmastersApi(catUrl, limit)
        if (catList.isNotEmpty()) return catList

        // HTML Search Fallback
        val htmlSearchUrl = "https://www.pornhub.com/video/search?search=$encoded&page=$safePage"
        val htmlList = parsePornhubHtml(htmlSearchUrl, limit)
        if (htmlList.isNotEmpty()) return htmlList

        // Thumbzilla fallback for any query if PH returns empty
        val tzFallback = searchThumbzilla(clean, limit, safePage)
        if (tzFallback.isNotEmpty()) return tzFallback

        return emptyList()
    }

    private fun searchThumbzilla(query: String, limit: Int, page: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val encoded = URLEncoder.encode(query.ifBlank { "trending" }, "UTF-8")
            val tzUrl = if (query.isBlank()) "https://www.thumbzilla.com/trending?page=$page" else "https://www.thumbzilla.com/video/search?q=$encoded&page=$page"
            val req = Request.Builder()
                .url(tzUrl)
                .header("User-Agent", DEFAULT_UA)
                .header("Referer", "https://www.thumbzilla.com/")
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val doc = org.jsoup.Jsoup.parse(html)
            val items = doc.select(".js-thumb, .thumb, a.js-thumb")
            for (item in items) {
                if (list.size >= limit) break
                val linkEl = if (item.tagName() == "a") item else item.select("a").firstOrNull() ?: continue
                val href = linkEl.attr("href")
                if (href.isBlank()) continue
                val fullUrl = if (href.startsWith("http")) href else "https://www.thumbzilla.com$href"

                val title = item.select(".title, .info, a[title]").text().trim().ifBlank {
                    item.select("img").attr("alt").ifBlank { "Thumbzilla Video" }
                }

                var thumb = item.select("img").attr("data-src").ifBlank {
                    item.select("img").attr("data-original")
                }.ifBlank {
                    item.select("img").attr("src")
                }
                if (thumb.startsWith("//")) thumb = "https:$thumb"

                val durText = item.select(".duration, .time").text().trim()
                val durSec = parseDurationToSeconds(durText)

                list.add(
                    VideoItem(
                        id = fullUrl,
                        title = title,
                        uploaderName = "Thumbzilla",
                        uploaderUrl = "https://www.thumbzilla.com",
                        thumbnailUrl = thumb,
                        providerId = PROVIDER_ID,
                        durationSeconds = durSec,
                        uploadDate = "Thumbzilla",
                        description = title
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Thumbzilla search error: ${e.message}")
        }
        return list
    }

    private fun parsePornhubHtml(targetUrl: String, limit: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        val seenKeys = mutableSetOf<String>()
        try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", DEFAULT_UA)
                .header("Cookie", BYPASS_COOKIES)
                .header("Referer", "https://www.pornhub.com/")
                .header("X-Forwarded-For", BYPASS_IP)
                .header("CF-Connecting-IP", BYPASS_IP)
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val doc = org.jsoup.Jsoup.parse(html)
            val cards = doc.select(".videoblock, .videoItem, .phimage, .pcVideoListItem, .wrap, div[data-video-vkey], li[data-video-vkey]")

            for (card in cards) {
                if (list.size >= limit) break
                val linkEl = card.select("a").firstOrNull {
                    val h = it.attr("href")
                    h.contains("viewkey=") || h.contains("/view_video.php")
                } ?: card.select("a").firstOrNull() ?: continue

                val href = linkEl.attr("href")
                val vk = extractViewkey(href).ifBlank { card.attr("data-video-vkey") }
                if (vk.isBlank() || seenKeys.contains(vk)) continue
                seenKeys.add(vk)

                val title = card.select(".title, .linkVideoThumb, a[title], .title a").attr("title").ifBlank {
                    card.select(".title, .linkVideoThumb, h5, a").text().trim()
                }.ifBlank { "Pornhub Video $vk" }

                var thumb = card.select("img").attr("data-src").ifBlank {
                    card.select("img").attr("data-thumb_url")
                }.ifBlank {
                    card.select("img").attr("data-image")
                }.ifBlank {
                    card.select("img").attr("src")
                }

                if (thumb.startsWith("//")) thumb = "https:$thumb"

                val durText = card.select(".duration, .var-duration, .time").text().trim()
                val durSec = parseDurationToSeconds(durText)
                val uploader = card.select(".usernameWrap a, .uploader a, .videoUploaderBlock a").text().trim().ifBlank { "Pornhub" }

                val fullUrl = if (href.startsWith("http")) href else "https://www.pornhub.com/view_video.php?viewkey=$vk"

                list.add(
                    VideoItem(
                        id = fullUrl,
                        title = title,
                        uploaderName = uploader,
                        thumbnailUrl = thumb,
                        durationSeconds = durSec,
                        providerId = PROVIDER_ID
                    )
                )
            }

            // Fallback scan: scan all <a> links
            if (list.isEmpty()) {
                val allLinks = doc.select("a")
                for (a in allLinks) {
                    if (list.size >= limit) break
                    val href = a.attr("href")
                    if (!href.contains("viewkey=")) continue
                    val vk = extractViewkey(href)
                    if (vk.isBlank() || seenKeys.contains(vk)) continue
                    seenKeys.add(vk)

                    val title = a.attr("title").ifBlank { a.text().trim() }.ifBlank { "Pornhub $vk" }
                    val img = a.select("img").firstOrNull() ?: a.parent()?.select("img")?.firstOrNull()
                    var thumb = img?.attr("data-src")?.ifBlank { img.attr("src") } ?: ""
                    if (thumb.startsWith("//")) thumb = "https:$thumb"

                    val fullUrl = if (href.startsWith("http")) href else "https://www.pornhub.com/view_video.php?viewkey=$vk"
                    list.add(
                        VideoItem(
                            id = fullUrl,
                            title = title,
                            uploaderName = "Pornhub",
                            thumbnailUrl = thumb,
                            durationSeconds = -1L,
                            providerId = PROVIDER_ID
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "parsePornhubHtml error ($targetUrl): ${e.message}")
        }
        return list
    }

    private fun parseWebmastersApi(apiUrl: String, limit: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", DEFAULT_UA)
                .header("Cookie", BYPASS_COOKIES)
                .header("Referer", "https://www.pornhub.com/")
                .header("X-Forwarded-For", BYPASS_IP)
                .header("X-Real-IP", BYPASS_IP)
                .header("CF-Connecting-IP", BYPASS_IP)
                .header("Client-IP", BYPASS_IP)
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            if (!jsonStr.trim().startsWith("{")) return list

            val root = JSONObject(jsonStr)
            val videosArr = root.optJSONArray("videos") ?: return list

            for (i in 0 until videosArr.length()) {
                if (list.size >= limit) break
                val vObj = videosArr.optJSONObject(i) ?: continue

                val videoId = vObj.optString("video_id", "")
                val title = vObj.optString("title", "Pornhub Video")
                val url = vObj.optString("url", if (videoId.isNotBlank()) "https://www.pornhub.com/view_video.php?viewkey=$videoId" else "")
                if (url.isBlank()) continue

                var thumb = vObj.optString("default_thumb", "").ifBlank { vObj.optString("thumb", "") }
                val thumbsList = mutableListOf<String>()

                val thumbsArr = vObj.optJSONArray("thumbs")
                if (thumbsArr != null && thumbsArr.length() > 0) {
                    for (tIdx in 0 until thumbsArr.length()) {
                        val tObj = thumbsArr.optJSONObject(tIdx) ?: continue
                        var src = tObj.optString("src", "")
                        if (src.isNotBlank()) {
                            if (src.startsWith("//")) src = "https:$src"
                            thumbsList.add(src)
                        }
                    }
                    if (thumb.isBlank() && thumbsList.isNotEmpty()) {
                        thumb = thumbsList.first()
                    }
                }
                if (thumb.startsWith("//")) thumb = "https:$thumb"

                // Extract creator/pornstar name if available
                var creator = "Pornhub"
                val starsArr = vObj.optJSONArray("pornstars")
                if (starsArr != null && starsArr.length() > 0) {
                    val star = starsArr.optJSONObject(0)?.optString("pornstar_name", "") ?: ""
                    if (star.isNotBlank()) creator = star
                }

                val durStr = vObj.optString("duration", "0")
                val durSec = parseDurationToSeconds(durStr)
                val views = vObj.optLong("views", -1L)

                list.add(
                    VideoItem(
                        id = url,
                        title = title,
                        uploaderName = creator,
                        thumbnailUrl = thumb,
                        durationSeconds = durSec,
                        viewCount = views,
                        providerId = PROVIDER_ID,
                        previewThumbnails = thumbsList.take(16)
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pornhub Webmasters API error ($apiUrl): ${e.message}")
        }
        return list
    }

    private fun parseDurationToSeconds(durStr: String): Long {
        if (durStr.isBlank()) return 0L
        val parts = durStr.split(":")
        var total = 0L
        for (p in parts) {
            total = total * 60 + (p.toLongOrNull() ?: 0L)
        }
        return total
    }

    fun extractViewkey(urlOrId: String): String {
        val trimmed = urlOrId.trim()
        val vkMatcher = Pattern.compile("(?:viewkey=|/embed/|/view_video\\.php\\?viewkey=)([a-zA-Z0-9_-]+)", Pattern.CASE_INSENSITIVE).matcher(trimmed)
        if (vkMatcher.find()) {
            return vkMatcher.group(1) ?: trimmed
        }
        val prefixMatcher = Pattern.compile("^(ph[a-zA-Z0-9_-]+)$", Pattern.CASE_INSENSITIVE).matcher(trimmed)
        if (prefixMatcher.find()) {
            return prefixMatcher.group(1) ?: trimmed
        }
        return trimmed
    }

    suspend fun getStreamData(urlOrId: String, context: Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val vk = extractViewkey(urlOrId)
        val candidateUrls = listOf(
            if (urlOrId.startsWith("http")) urlOrId else "https://www.pornhub.com/view_video.php?viewkey=$vk",
            "https://rt.pornhub.com/view_video.php?viewkey=$vk",
            "https://www.pornhub.org/view_video.php?viewkey=$vk",
            "https://cn.pornhub.com/view_video.php?viewkey=$vk"
        ).distinct()

        Log.d(TAG, "Fetching Pornhub stream data (viewkey: $vk)")

        for (targetUrl in candidateUrls) {
            try {
                val req = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", DEFAULT_UA)
                    .header("Cookie", BYPASS_COOKIES)
                    .header("Referer", "https://www.pornhub.com/")
                    .header("X-Forwarded-For", BYPASS_IP)
                    .header("X-Real-IP", BYPASS_IP)
                    .header("CF-Connecting-IP", BYPASS_IP)
                    .header("Client-IP", BYPASS_IP)
                    .build()

                val html = httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }

                if (html.isNullOrEmpty() || html.length < 500) {
                    continue
                }

                // Extract Title
                var title = "Pornhub Video"
                val titlePattern = Pattern.compile("<h1[^>]*class=\"[^\"]*inlineFree[^\"]*\"[^>]*>(.*?)</h1>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
                val titleMatcher = titlePattern.matcher(html)
                if (titleMatcher.find()) {
                    title = titleMatcher.group(1)?.replace(Regex("<[^>]*>"), "")?.trim() ?: title
                } else {
                    val metaTitle = Pattern.compile("<meta\\s+property=\"og:title\"\\s+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(html)
                    if (metaTitle.find()) {
                        title = metaTitle.group(1)?.trim() ?: title
                    }
                }

                // Extract Thumbnail
                var thumb = ""
                val metaThumb = Pattern.compile("<meta\\s+property=\"og:image\"\\s+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(html)
                if (metaThumb.find()) {
                    thumb = metaThumb.group(1)?.trim() ?: ""
                }

                // Extract flashvars or mediaDefinitions JSON
                val flashvarsMatcher = Pattern.compile("var\\s+flashvars(?:_\\d+)?\\s*=\\s*(\\{.*?\\});\\s*(?:var|</script>)", Pattern.DOTALL).matcher(html)
                var fvJsonStr = ""
                if (flashvarsMatcher.find()) {
                    fvJsonStr = flashvarsMatcher.group(1) ?: ""
                } else {
                    val fallbackFv = Pattern.compile("var\\s+flashvars(?:_\\d+)?\\s*=\\s*(\\{.*?\\});", Pattern.DOTALL).matcher(html)
                    if (fallbackFv.find()) {
                        fvJsonStr = fallbackFv.group(1) ?: ""
                    } else {
                        val mediaDefMatcher = Pattern.compile("\"mediaDefinitions\"\\s*:\\s*(\\[.*?\\])", Pattern.DOTALL).matcher(html)
                        if (mediaDefMatcher.find()) {
                            fvJsonStr = "{\"mediaDefinitions\":" + (mediaDefMatcher.group(1) ?: "[]") + "}"
                        }
                    }
                }

                val streamOptions = mutableListOf<PlayableStreamOption>()
                var durationSeconds = 0L

                if (fvJsonStr.isNotBlank()) {
                    try {
                        val fvObj = JSONObject(fvJsonStr)

                        val fvTitle = fvObj.optString("video_title", "")
                        if (fvTitle.isNotBlank()) title = fvTitle

                        val fvThumb = fvObj.optString("image_url", "")
                        if (fvThumb.isNotBlank()) thumb = fvThumb

                        durationSeconds = fvObj.optLong("video_duration", 0L)

                        if (fvObj.has("mediaDefinitions")) {
                            val mediaArr = fvObj.getJSONArray("mediaDefinitions")
                            for (i in 0 until mediaArr.length()) {
                                val item = mediaArr.getJSONObject(i)
                                val rawUrl = item.optString("videoUrl", "").replace("\\/", "/")
                                if (rawUrl.isBlank() || !rawUrl.startsWith("http")) continue

                                val quality = item.optString("quality", "720")
                                val format = item.optString("format", "hls")
                                val isHls = format.equals("hls", ignoreCase = true) || rawUrl.contains(".m3u8")

                                val isDirectMedia = rawUrl.contains(".m3u8") || rawUrl.contains(".mp4")
                                if (!isDirectMedia && rawUrl.contains("pornhub.com")) {
                                    // Handle get_media endpoints if returned
                                    try {
                                        val subReq = Request.Builder()
                                            .url(rawUrl)
                                            .header("User-Agent", DEFAULT_UA)
                                            .header("Cookie", BYPASS_COOKIES)
                                            .header("Referer", "https://www.pornhub.com/")
                                            .build()
                                        val subResp = httpClient.newCall(subReq).execute().use { it.body?.string() }
                                        if (!subResp.isNullOrBlank() && (subResp.trim().startsWith("[") || subResp.trim().startsWith("{"))) {
                                            val subArray = if (subResp.trim().startsWith("[")) JSONArray(subResp) else JSONArray().put(JSONObject(subResp))
                                            for (k in 0 until subArray.length()) {
                                                val subItem = subArray.optJSONObject(k) ?: continue
                                                val subVideoUrl = subItem.optString("videoUrl", "").replace("\\/", "/")
                                                if (subVideoUrl.isNotBlank() && subVideoUrl.startsWith("http")) {
                                                    val subQual = subItem.optString("quality", quality)
                                                    val subFmt = subItem.optString("format", format)
                                                    val subIsHls = subFmt.equals("hls", ignoreCase = true) || subVideoUrl.contains(".m3u8")
                                                    streamOptions.add(
                                                        PlayableStreamOption(
                                                            qualityLabel = "${subQual}p (${subFmt.uppercase()})",
                                                            format = if (subIsHls) "m3u8" else "mp4",
                                                            isMuxed = true,
                                                            videoUrl = subVideoUrl,
                                                            providerType = ProviderType.OTHER,
                                                            headers = defaultHeaders
                                                        )
                                                    )
                                                }
                                            }
                                            continue
                                        }
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Failed resolving sub media definition: ${e.message}")
                                    }
                                }

                                val qualityLabel = if (quality.isNotBlank() && quality != "[]") "${quality}p (${format.uppercase()})" else format.uppercase()

                                streamOptions.add(
                                    PlayableStreamOption(
                                        qualityLabel = qualityLabel,
                                        format = if (isHls) "m3u8" else "mp4",
                                        isMuxed = true,
                                        videoUrl = rawUrl,
                                        providerType = ProviderType.OTHER,
                                        headers = defaultHeaders
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing flashvars JSON: ${e.message}")
                    }
                }

                // Fallback: If flashvars didn't yield options, regex search for mediaUrl / m3u8 in html
                if (streamOptions.isEmpty()) {
                    val urlPattern = Pattern.compile("https?:[\\\\/]+[^\\s\"'<>]+\\.(?:mp4|m3u8)[^\\s\"'<>]*", Pattern.CASE_INSENSITIVE)
                    val matcher = urlPattern.matcher(html)
                    val seenUrls = mutableSetOf<String>()
                    while (matcher.find()) {
                        val rawUrl = matcher.group(0).replace("\\/", "/")
                        if (seenUrls.contains(rawUrl) || rawUrl.contains("phncdn.com/c6371/videos") || rawUrl.contains("vtt:")) continue
                        seenUrls.add(rawUrl)

                        val isHls = rawUrl.contains(".m3u8")
                        streamOptions.add(
                            PlayableStreamOption(
                                qualityLabel = if (isHls) "HLS Auto" else "MP4 Direct",
                                format = if (isHls) "m3u8" else "mp4",
                                isMuxed = true,
                                videoUrl = rawUrl,
                                providerType = ProviderType.OTHER,
                                headers = defaultHeaders
                            )
                        )
                    }
                }

                if (streamOptions.isNotEmpty()) {
                    val sortedOptions = streamOptions.distinctBy { it.videoUrl ?: "" }.sortedWith(
                        compareByDescending<PlayableStreamOption> { it.format == "m3u8" }
                            .thenByDescending {
                                val num = Regex("""\d+""").find(it.qualityLabel)?.value?.toIntOrNull() ?: 0
                                num
                            }
                    )

                    Log.i(TAG, "Successfully extracted ${sortedOptions.size} Pornhub stream options for $vk")
                    val firstUrl = sortedOptions.firstOrNull()?.videoUrl ?: ""
                    val hlsUrl = sortedOptions.firstOrNull { it.format == "m3u8" }?.videoUrl

                    return@withContext StreamData(
                        videoId = vk,
                        videoUrl = firstUrl,
                        title = title,
                        channelName = "Pornhub",
                        thumbnailUrl = thumb,
                        availableStreamOptions = sortedOptions,
                        selectedStreamOption = sortedOptions.firstOrNull(),
                        hlsUrl = hlsUrl,
                        providerId = PROVIDER_ID,
                        headers = defaultHeaders
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Pornhub stream extraction error for $targetUrl: ${e.message}")
            }
        }

        // Fallback to YtDlpResolver
        if (context != null) {
            try {
                val targetUrl = "https://www.pornhub.com/view_video.php?viewkey=$vk"
                Log.i(TAG, "Resolving Pornhub video viewkey via YtDlpResolver: $vk ($targetUrl)")
                val ytDlpRes = YtDlpResolver.extractStreamInfo(context, targetUrl)
                if (ytDlpRes is YouTubeExtractorHelper.ExtractionResult.Success) {
                    return@withContext ytDlpRes.streamData.copy(
                        providerId = PROVIDER_ID,
                        headers = defaultHeaders
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "YtDlpResolver Pornhub fallback failed: ${e.message}")
            }
        }

        null
    }
}
