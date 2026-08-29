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

object RedTubeProvider {
    private const val TAG = "RedTubeProvider"
    const val PROVIDER_ID = "redtube"

    private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getHome(page: Int = 1, limit: Int = 30): List<VideoItem> {
        val urls = listOf(
            if (page == 1) "https://www.redtube.com/top" else "https://www.redtube.com/top?page=$page",
            if (page == 1) "https://www.redtube.com/" else "https://www.redtube.com/?page=$page",
            if (page == 1) "https://www.redtube.com/mostviewed" else "https://www.redtube.com/mostviewed?page=$page",
            "https://api.redtube.com/?data=redtube.Videos.searchVideos&output=json&ordering=mostviewed&page=$page&thumbsize=all"
        )

        for (targetUrl in urls) {
            val list = if (targetUrl.contains("api.redtube.com")) {
                parseRedTubeJsonApi(targetUrl, limit)
            } else {
                parseRedTubeHtml(targetUrl, limit)
            }
            if (list.isNotEmpty()) {
                Log.d(TAG, "RedTube getHome page $page fetched ${list.size} videos from $targetUrl")
                return list
            }
        }

        return emptyList()
    }

    fun search(query: String, page: Int = 1, limit: Int = 30): List<VideoItem> {
        val cleanQuery = query.trim()
        val encoded = URLEncoder.encode(cleanQuery, "UTF-8")

        val urls = listOf(
            if (page == 1) "https://www.redtube.com/?search=$encoded" else "https://www.redtube.com/?search=$encoded&page=$page",
            "https://api.redtube.com/?data=redtube.Videos.searchVideos&output=json&search=$encoded&page=$page&thumbsize=all",
            "https://www.redtube.com/search/$encoded?page=$page"
        )

        for (targetUrl in urls) {
            val list = if (targetUrl.contains("api.redtube.com")) {
                parseRedTubeJsonApi(targetUrl, limit)
            } else {
                parseRedTubeHtml(targetUrl, limit)
            }
            if (list.isNotEmpty()) {
                Log.d(TAG, "RedTube search '$query' page $page fetched ${list.size} videos from $targetUrl")
                return list
            }
        }

        return emptyList()
    }

    fun getCreatorVideos(slugOrName: String, page: Int = 1, limit: Int = 30): List<VideoItem> {
        val clean = slugOrName.trim().lowercase().replace(" ", "-")
        val urls = listOf(
            "https://www.redtube.com/amateur/$clean?page=$page",
            "https://www.redtube.com/pornstar/$clean?page=$page",
            "https://www.redtube.com/channel/$clean?page=$page",
            "https://www.redtube.com/users/$clean/videos?page=$page"
        )

        for (u in urls) {
            val list = parseRedTubeHtml(u, limit)
            if (list.isNotEmpty()) return list
        }
        return search(slugOrName, page, limit)
    }

    suspend fun getStreamData(urlOrId: String, context: Context?): StreamData? = withContext(Dispatchers.IO) {
        val cleanId = extractVideoId(urlOrId)
        val fullUrl = if (urlOrId.startsWith("http")) urlOrId else "https://www.redtube.com/$cleanId"
        val defaultHeaders = mapOf(
            "User-Agent" to DEFAULT_USER_AGENT,
            "Referer" to "https://www.redtube.com/",
            "Origin" to "https://www.redtube.com",
            "Cookie" to "age_verified=1; platform=pc; has_consent=1"
        )

        // 1. Direct RedTube HTML & Media API Scraping
        try {
            val req = Request.Builder()
                .url(fullUrl)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Cookie", "age_verified=1; platform=pc; has_consent=1")
                .header("Referer", "https://www.redtube.com/")
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!html.isNullOrBlank()) {
                val streamOptions = mutableListOf<PlayableStreamOption>()
                var hlsMasterUrl: String? = null
                var title = "RedTube Video"
                var thumb = ""
                var channelName = "RedTube"
                var channelAvatar: String? = null
                var duration = 0L

                // Extract Title
                val titleMatch = Pattern.compile("""<meta\s+property="og:title"\s+content="([^"]+)"""", Pattern.CASE_INSENSITIVE).matcher(html)
                if (titleMatch.find()) {
                    title = titleMatch.group(1)?.replace(" - RedTube", "")?.trim() ?: title
                } else {
                    val h1Match = Pattern.compile("""<h1[^>]*class="[^"]*video-title[^"]*"[^>]*>(.*?)</h1>""", Pattern.DOTALL or Pattern.CASE_INSENSITIVE).matcher(html)
                    if (h1Match.find()) title = h1Match.group(1)?.trim() ?: title
                }

                // Extract Thumb
                val thumbMatch = Pattern.compile("""<meta\s+property="og:image"\s+content="([^"]+)"""", Pattern.CASE_INSENSITIVE).matcher(html)
                if (thumbMatch.find()) {
                    thumb = thumbMatch.group(1)?.trim() ?: ""
                }

                // Extract Channel / Author Info
                val authorMatch = Pattern.compile("""<a[^>]*class="[^"]*(?:video-infobar__link|author-title-text|video_author_link)[^"]*"[^>]*>(.*?)</a>""", Pattern.DOTALL or Pattern.CASE_INSENSITIVE).matcher(html)
                if (authorMatch.find()) {
                    val aName = authorMatch.group(1)?.trim() ?: ""
                    if (aName.isNotBlank()) channelName = aName
                }

                val avatarMatch = Pattern.compile("""<img[^>]*class="[^"]*(?:user-image|avatar|channel-logo)[^"]*"[^>]*src="([^"]+)"""", Pattern.CASE_INSENSITIVE).matcher(html)
                if (avatarMatch.find()) {
                    channelAvatar = avatarMatch.group(1)?.trim()
                }

                // Extract Media Definitions JSON
                val mediaDefPattern = Pattern.compile("""mediaDefinitions?\s*:\s*(\[[^\]]+\])""", Pattern.DOTALL)
                val mDef = mediaDefPattern.matcher(html)
                if (mDef.find()) {
                    try {
                        val arr = JSONArray(mDef.group(1))
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            val format = obj.optString("format", "")
                            var videoEndpoint = obj.optString("videoUrl", "")
                            if (videoEndpoint.isBlank()) continue

                            if (videoEndpoint.startsWith("/")) {
                                videoEndpoint = "https://www.redtube.com$videoEndpoint"
                            }

                            // If videoEndpoint is a media dispatcher (/media/mp4 or /media/hls), call it to get actual stream URLs
                            if (videoEndpoint.contains("/media/")) {
                                try {
                                    val mReq = Request.Builder()
                                        .url(videoEndpoint)
                                        .header("User-Agent", DEFAULT_USER_AGENT)
                                        .header("Cookie", "age_verified=1; platform=pc; has_consent=1")
                                        .header("Referer", fullUrl)
                                        .build()

                                    val mRespStr = httpClient.newCall(mReq).execute().use { it.body?.string() }
                                    if (!mRespStr.isNullOrBlank() && mRespStr.startsWith("[")) {
                                        val streamArr = JSONArray(mRespStr)
                                        for (j in 0 until streamArr.length()) {
                                            val sObj = streamArr.optJSONObject(j) ?: continue
                                            val sFormat = sObj.optString("format", "mp4")
                                            val sQuality = sObj.optString("quality", "720")
                                            val sUrl = sObj.optString("videoUrl", "")

                                            if (sUrl.isNotBlank()) {
                                                if (sFormat.equals("hls", ignoreCase = true) || sUrl.contains(".m3u8")) {
                                                    if (hlsMasterUrl == null) hlsMasterUrl = sUrl
                                                }
                                                val qInt = sQuality.toIntOrNull() ?: 720
                                                val isDefault = sObj.optBoolean("defaultQuality", false)
                                                streamOptions.add(
                                                    PlayableStreamOption(
                                                        qualityLabel = "${sQuality}p",
                                                        format = if (sFormat.equals("hls", true)) "m3u8" else "mp4",
                                                        isMuxed = true,
                                                        videoUrl = sUrl,
                                                        providerType = ProviderType.OTHER,
                                                        headers = defaultHeaders
                                                    )
                                                )
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Error resolving RedTube media endpoint $videoEndpoint: ${e.message}")
                                }
                            } else if (videoEndpoint.startsWith("http")) {
                                if (format.equals("hls", ignoreCase = true) || videoEndpoint.contains(".m3u8")) {
                                    hlsMasterUrl = videoEndpoint
                                } else {
                                    streamOptions.add(
                                        PlayableStreamOption(
                                            qualityLabel = "Auto Quality",
                                            format = "mp4",
                                            isMuxed = true,
                                            videoUrl = videoEndpoint,
                                            providerType = ProviderType.OTHER,
                                            headers = defaultHeaders
                                        )
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing mediaDefinitions: ${e.message}")
                    }
                }

                // If streams found, sort by resolution (1080p -> 720p -> 480p -> 240p)
                if (streamOptions.isNotEmpty() || hlsMasterUrl != null) {
                    val sortedOptions = streamOptions
                        .distinctBy { it.videoUrl }
                        .sortedByDescending { it.qualityLabel.replace("p", "").toIntOrNull() ?: 0 }

                    val primaryStream = sortedOptions.firstOrNull { it.qualityLabel == "720p" || it.qualityLabel == "1080p" }
                        ?: sortedOptions.firstOrNull()

                    val primaryUrl = primaryStream?.videoUrl ?: hlsMasterUrl ?: ""

                    Log.i(TAG, "Successfully extracted RedTube stream for $cleanId (${sortedOptions.size} options)")
                    return@withContext StreamData(
                        videoId = cleanId,
                        videoUrl = primaryUrl,
                        title = title,
                        channelName = channelName,
                        channelAvatarUrl = channelAvatar,
                        thumbnailUrl = thumb,
                        availableStreamOptions = sortedOptions,
                        selectedStreamOption = primaryStream,
                        hlsUrl = hlsMasterUrl,
                        providerId = PROVIDER_ID,
                        headers = defaultHeaders
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct RedTube scraping error: ${e.message}")
        }

        // 2. YtDlp fallback
        if (context != null) {
            try {
                val ytdlResult = YtDlpResolver.extractStreamInfo(context, fullUrl)
                if (ytdlResult is YouTubeExtractorHelper.ExtractionResult.Success) {
                    return@withContext ytdlResult.streamData.copy(providerId = PROVIDER_ID, headers = defaultHeaders)
                }
            } catch (e: Exception) {
                Log.w(TAG, "YtDlp error for RedTube: ${e.message}")
            }
        }

        // 3. Fallback search resolver via title across MultiSource
        try {
            val cleanQuery = cleanId.replace(Regex("""[-_]"""), " ").replace(Regex("""(?i)(?:redtube|video|hd|4k|1080p|720p|\d{6,})"""), "").trim()
            if (cleanQuery.isNotBlank()) {
                val searchResults = EpornerProvider.search(cleanQuery, page = 1, limit = 5)
                if (searchResults.isNotEmpty()) {
                    val streamData = EpornerProvider.getStreamData(searchResults.first().id, context)
                    if (streamData != null && streamData.availableStreamOptions.isNotEmpty()) {
                        return@withContext streamData.copy(
                            videoId = cleanId,
                            videoUrl = fullUrl,
                            title = streamData.title,
                            providerId = PROVIDER_ID,
                            headers = streamData.headers
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fallback search error: ${e.message}")
        }

        throw java.io.IOException("Unable to extract stream for RedTube video $cleanId")
    }

    private fun extractVideoId(urlOrId: String): String {
        val clean = urlOrId.trim()
        val numMatch = Pattern.compile("""redtube\.com/(\d+)""", Pattern.CASE_INSENSITIVE).matcher(clean)
        if (numMatch.find()) {
            return numMatch.group(1) ?: clean
        }
        val justNum = Pattern.compile("""^(\d+)$""").matcher(clean)
        if (justNum.find()) {
            return clean
        }
        return clean.substringAfterLast("/").substringBefore("?").substringBefore("&")
    }

    private fun parseRedTubeHtml(targetUrl: String, limit: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Cookie", "age_verified=1; platform=pc; has_consent=1")
                .header("Referer", "https://www.redtube.com/")
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val seen = mutableSetOf<String>()

            // Pattern for Redtube videoblock li items
            val blockPattern = Pattern.compile("""<li[^>]*class="[^"]*(?:videoblock_list|tm_video_block|thumbnail-card)[^"]*"[^>]*data-video-id="(\d+)"[^>]*>(.*?)</li>""", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
            val matcher = blockPattern.matcher(html)

            while (matcher.find() && list.size < limit) {
                val videoId = matcher.group(1) ?: continue
                val block = matcher.group(2) ?: continue
                if (seen.contains(videoId)) continue
                seen.add(videoId)

                val fullUrl = "https://www.redtube.com/$videoId"

                // Title
                var title = "RedTube Video $videoId"
                val titleMatch = Pattern.compile("""(?:title|alt)="([^"]+)"""", Pattern.CASE_INSENSITIVE).matcher(block)
                while (titleMatch.find()) {
                    val t = titleMatch.group(1)?.trim() ?: continue
                    if (t.isNotBlank() && !t.equals("RedTube", ignoreCase = true) && !t.startsWith("http") && t.length > 3) {
                        title = t
                        break
                    }
                }

                // Thumbnail & Preview Frames
                var thumb = ""
                val thumbMatch = Pattern.compile("""(?:data-o_thumb|data-src|data-srcset|src)=["']([^"'\s,]+)["']""", Pattern.CASE_INSENSITIVE).matcher(block)
                while (thumbMatch.find()) {
                    val candidate = thumbMatch.group(1)?.trim() ?: continue
                    if (candidate.startsWith("http") && !candidate.contains("data:image") && !candidate.endsWith(".svg")) {
                        thumb = candidate
                        break
                    }
                }

                // Teaser Video Preview MP4 (data-mediabook)
                var previewVideoUrl: String? = null
                val mbMatch = Pattern.compile("""data-mediabook=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE).matcher(block)
                if (mbMatch.find()) {
                    previewVideoUrl = mbMatch.group(1)?.replace("&amp;", "&")
                }

                // Storyboard Scrubbing Frames (data-path with {index}.jpg)
                val previewThumbnails = mutableListOf<String>()
                val pathMatch = Pattern.compile("""data-path=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE).matcher(block)
                if (pathMatch.find()) {
                    val pathTemplate = pathMatch.group(1)?.trim() ?: ""
                    if (pathTemplate.contains("{index}")) {
                        for (idx in 1..16) {
                            previewThumbnails.add(pathTemplate.replace("{index}", idx.toString()))
                        }
                    }
                }

                // Duration
                var duration = -1L
                val durMatch = Pattern.compile("""class="[^"]*(?:tm_video_duration|video-properties|duration)[^"]*"[^>]*>([^<]+)</span>""", Pattern.CASE_INSENSITIVE).matcher(block)
                if (durMatch.find()) {
                    duration = parseDuration(durMatch.group(1) ?: "")
                }

                // Uploader / Creator Name & Url
                var uploader = "RedTube"
                var uploaderUrl: String? = null
                val uploaderIdMatch = Pattern.compile("""data-uploader-name=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE).matcher(block)
                if (uploaderIdMatch.find()) {
                    uploader = uploaderIdMatch.group(1)?.trim() ?: uploader
                } else {
                    val authorLinkMatch = Pattern.compile("""<a[^>]*class="[^"]*author-title-text[^"]*"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", Pattern.CASE_INSENSITIVE).matcher(block)
                    if (authorLinkMatch.find()) {
                        uploaderUrl = "https://www.redtube.com" + (authorLinkMatch.group(1) ?: "")
                        uploader = authorLinkMatch.group(2)?.trim() ?: uploader
                    }
                }

                // View Count
                var viewCount = 0L
                val viewsMatch = Pattern.compile("""<span[^>]*class=['"][^'"]*info-views[^'"]*['"][^>]*>([^<]+)</span>""", Pattern.CASE_INSENSITIVE).matcher(block)
                if (viewsMatch.find()) {
                    viewCount = parseViewCount(viewsMatch.group(1) ?: "")
                }

                list.add(
                    VideoItem(
                        id = fullUrl,
                        title = title,
                        uploaderName = uploader,
                        uploaderUrl = uploaderUrl,
                        thumbnailUrl = thumb,
                        durationSeconds = duration,
                        viewCount = viewCount,
                        previewClipUrl = previewVideoUrl,
                        previewThumbnails = previewThumbnails,
                        providerId = PROVIDER_ID
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "RedTube HTML parse error: ${e.message}")
        }
        return list
    }

    private fun parseRedTubeJsonApi(apiUrl: String, limit: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val root = JSONObject(jsonStr)
            val videosArr = root.optJSONArray("videos") ?: return list

            for (i in 0 until videosArr.length()) {
                if (list.size >= limit) break
                val item = videosArr.optJSONObject(i) ?: continue
                val videoObj = item.optJSONObject("video") ?: item

                val id = videoObj.optString("video_id", "")
                if (id.isBlank()) continue

                val title = videoObj.optString("title", "RedTube Video")
                val url = videoObj.optString("url", "https://www.redtube.com/$id")
                val durStr = videoObj.optString("duration", "")
                val duration = parseDuration(durStr)
                val views = videoObj.optLong("views", 0L)

                // Pick highest resolution valid thumbnail from thumbs array
                var thumb = ""
                val previewThumbnails = mutableListOf<String>()
                val thumbsArr = videoObj.optJSONArray("thumbs")
                if (thumbsArr != null) {
                    for (tIdx in 0 until thumbsArr.length()) {
                        val tObj = thumbsArr.optJSONObject(tIdx) ?: continue
                        val src = tObj.optString("src", "")
                        if (src.isNotBlank() && !src.contains("//original/")) {
                            previewThumbnails.add(src)
                            if (tObj.optString("size") == "big" || tObj.optString("size") == "medium2" || thumb.isBlank()) {
                                thumb = src
                            }
                        }
                    }
                }
                if (thumb.isBlank()) {
                    thumb = videoObj.optString("default_thumb", "").takeIf { !it.contains("//original/") }
                        ?: videoObj.optString("thumb", "").takeIf { !it.contains("//original/") }
                        ?: "https://pix-cdn77.rdtcdn.com/videos/$id/original/(m=e0YH8f)0.jpg"
                }

                list.add(
                    VideoItem(
                        id = url,
                        title = title,
                        uploaderName = "RedTube",
                        thumbnailUrl = thumb,
                        durationSeconds = duration,
                        viewCount = views,
                        previewThumbnails = previewThumbnails,
                        providerId = PROVIDER_ID
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "RedTube JSON API parse error: ${e.message}")
        }
        return list
    }

    private fun parseDuration(raw: String): Long {
        val clean = raw.trim()
        if (clean.isBlank()) return -1L
        val parts = clean.split(":")
        return when (parts.size) {
            2 -> (parts[0].toLongOrNull() ?: 0L) * 60L + (parts[1].toLongOrNull() ?: 0L)
            3 -> (parts[0].toLongOrNull() ?: 0L) * 3600L + (parts[1].toLongOrNull() ?: 0L) * 60L + (parts[2].toLongOrNull() ?: 0L)
            else -> -1L
        }
    }

    private fun parseViewCount(raw: String): Long {
        val clean = raw.trim().uppercase().replace(",", "")
        return when {
            clean.endsWith("B") -> ((clean.dropLast(1).toDoubleOrNull() ?: 0.0) * 1_000_000_000).toLong()
            clean.endsWith("M") -> ((clean.dropLast(1).toDoubleOrNull() ?: 0.0) * 1_000_000).toLong()
            clean.endsWith("K") -> ((clean.dropLast(1).toDoubleOrNull() ?: 0.0) * 1_000).toLong()
            else -> clean.replace("[^0-9]".toRegex(), "").toLongOrNull() ?: 0L
        }
    }
}
