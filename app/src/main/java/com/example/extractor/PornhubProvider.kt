package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import com.example.model.StreamData
import com.example.model.VideoItem
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
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val BYPASS_IP = "208.80.154.224"
    private const val BYPASS_COOKIES = "age_verified=1; platform=pc; accessAgeDisclaimerPH=1; ip_country=US; has_consent=1; expired_cookies=1; il=en"

    fun getHome(limit: Int = 20, page: Int = 1): List<VideoItem> {
        val apiUrl = "https://www.pornhub.com/webmasters/search?thumbsize=medium&ordering=mostviewed&page=$page"
        return parseWebmastersApi(apiUrl, limit)
    }

    fun search(query: String, limit: Int = 20, page: Int = 1): List<VideoItem> {
        if (query.isBlank()) return getHome(limit, page)
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val apiUrl = "https://www.pornhub.com/webmasters/search?search=$encoded&thumbsize=medium&page=$page"
        return parseWebmastersApi(apiUrl, limit)
    }

    private fun parseWebmastersApi(apiUrl: String, limit: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", DEFAULT_UA)
                .header("Cookie", BYPASS_COOKIES)
                .header("Referer", "https://www.pornhub.com/")
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

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
                if (thumb.isBlank()) {
                    val thumbsArr = vObj.optJSONArray("thumbs")
                    if (thumbsArr != null && thumbsArr.length() > 0) {
                        thumb = thumbsArr.optJSONObject(0)?.optString("src", "") ?: ""
                    }
                }
                if (thumb.startsWith("//")) thumb = "https:$thumb"

                val durStr = vObj.optString("duration", "0")
                val durSec = parseDurationToSeconds(durStr)
                val views = vObj.optLong("views", -1L)

                list.add(
                    VideoItem(
                        id = url,
                        title = title,
                        uploaderName = "Pornhub",
                        thumbnailUrl = thumb,
                        durationSeconds = durSec,
                        viewCount = views,
                        providerId = PROVIDER_ID
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
        val vkMatcher = Pattern.compile("viewkey=([a-zA-Z0-9_-]+)", Pattern.CASE_INSENSITIVE).matcher(trimmed)
        if (vkMatcher.find()) {
            return vkMatcher.group(1) ?: trimmed
        }
        return trimmed
    }

    fun getStreamData(urlOrId: String, context: Context?): StreamData? {
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
            val flashvarsMatcher = Pattern.compile("var\\s+flashvars(?:_\\d+)?\\s*=\\s*(\\{.*?\\});", Pattern.DOTALL).matcher(html)
            val streamOptions = mutableListOf<PlayableStreamOption>()

            var fvJsonStr = ""
            if (flashvarsMatcher.find()) {
                fvJsonStr = flashvarsMatcher.group(1) ?: ""
            } else {
                val mediaDefMatcher = Pattern.compile("\"mediaDefinitions\"\\s*:\\s*(\\[.*?\\])", Pattern.DOTALL).matcher(html)
                if (mediaDefMatcher.find()) {
                    fvJsonStr = "{\"mediaDefinitions\":" + (mediaDefMatcher.group(1) ?: "[]") + "}"
                }
            }

            if (fvJsonStr.isNotBlank()) {
                try {
                    val fvObj = JSONObject(fvJsonStr)
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
                                // Sometimes rawUrl is a JSON endpoint returning the actual CDN links
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
                                                        headers = mapOf(
                                                            "Referer" to "https://www.pornhub.com/",
                                                            "User-Agent" to DEFAULT_UA,
                                                            "Cookie" to BYPASS_COOKIES
                                                        )
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

                            val qualityLabel = if (quality.isNotBlank()) "${quality}p (${format.uppercase()})" else format.uppercase()

                            streamOptions.add(
                                PlayableStreamOption(
                                    qualityLabel = qualityLabel,
                                    format = if (isHls) "m3u8" else "mp4",
                                    isMuxed = true,
                                    videoUrl = rawUrl,
                                    providerType = ProviderType.OTHER,
                                    headers = mapOf(
                                        "Referer" to "https://www.pornhub.com/",
                                        "User-Agent" to DEFAULT_UA,
                                        "Cookie" to "age_verified=1; platform=pc; accessAgeDisclaimerPH=1; ip_country=US"
                                    )
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
                            headers = mapOf(
                                "Referer" to "https://www.pornhub.com/",
                                "User-Agent" to DEFAULT_UA
                            )
                        )
                    )
                }
            }

                if (streamOptions.isNotEmpty()) {
                    // Sort options so higher qualities (e.g., 1080p, 720p, 480p) or HLS come first
                    val sortedOptions = streamOptions.distinctBy { it.videoUrl ?: "" }.sortedWith(
                        compareByDescending<PlayableStreamOption> { it.format == "m3u8" }
                            .thenByDescending {
                                val num = Regex("""\d+""").find(it.qualityLabel)?.value?.toIntOrNull() ?: 0
                                num
                            }
                    )

                    Log.i(TAG, "Successfully extracted ${sortedOptions.size} Pornhub stream options for $vk")
                    val firstUrl = sortedOptions.firstOrNull()?.videoUrl ?: ""

                    return StreamData(
                        videoId = vk,
                        videoUrl = firstUrl,
                        title = title,
                        channelName = "Pornhub",
                        thumbnailUrl = thumb,
                        availableStreamOptions = sortedOptions,
                        selectedStreamOption = sortedOptions.firstOrNull(),
                        providerId = "pornhub",
                        headers = mapOf(
                            "Referer" to "https://www.pornhub.com/",
                            "User-Agent" to DEFAULT_UA,
                            "Cookie" to BYPASS_COOKIES
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Pornhub stream extraction error for $targetUrl: ${e.message}")
            }
        }
        return null
    }
}
