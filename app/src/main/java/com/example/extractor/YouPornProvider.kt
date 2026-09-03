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

object YouPornProvider {
    private const val TAG = "YouPornProvider"
    const val PROVIDER_ID = "youporn"

    private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val COMMON_HEADERS = mapOf(
        "User-Agent" to DEFAULT_USER_AGENT,
        "Cookie" to "age_verified=1; platform=pc",
        "Referer" to "https://www.youporn.com/"
    )

    // ------------------- CATALOG / LISTINGS -------------------

    fun getHome(page: Int = 1, limit: Int = 30): List<VideoItem> {
        val urls = listOf(
            "https://www.youporn.com/most_viewed/?page=$page",
            "https://www.youporn.com/browse/time/?page=$page",
            "https://www.youporn.com/top_rated/?page=$page",
            "https://www.youporn.com/?page=$page"
        )
        for (u in urls) {
            val list = parseYouPornHtml(u, limit)
            if (list.isNotEmpty()) {
                Log.d(TAG, "YouPorn getHome (page $page) fetched ${list.size} items from $u")
                return list
            }
        }
        return emptyList()
    }

    /**
     * YouPornVideos: YouPorn video (browse) playlists, with sorting, filtering and pagination
     */
    fun getVideos(sort: String = "time", page: Int = 1, limit: Int = 30): List<VideoItem> {
        val targetUrl = when (sort.lowercase()) {
            "views", "most_viewed", "popular" -> "https://www.youporn.com/most_viewed/?page=$page"
            "rating", "top_rated", "top" -> "https://www.youporn.com/top_rated/?page=$page"
            "duration", "longest" -> "https://www.youporn.com/browse/duration/?page=$page"
            else -> "https://www.youporn.com/browse/time/?page=$page"
        }
        return parseYouPornHtml(targetUrl, limit)
    }

    /**
     * YouPornCategory: YouPorn category, with sorting, filtering and pagination
     */
    fun getCategory(categorySlug: String, sort: String = "time", page: Int = 1, limit: Int = 30): List<VideoItem> {
        val slug = categorySlug.trim().lowercase().replace(" ", "-").removePrefix("category/")
        val url = if (sort.isNotBlank() && sort != "time") {
            "https://www.youporn.com/category/$slug/$sort/?page=$page"
        } else {
            "https://www.youporn.com/category/$slug/?page=$page"
        }
        return parseYouPornHtml(url, limit)
    }

    /**
     * YouPornChannel: YouPorn channel, with sorting and pagination
     */
    fun getChannel(channelName: String, page: Int = 1, limit: Int = 30): List<VideoItem> {
        val clean = channelName.trim().removePrefix("channel/").removePrefix("channels/")
        val url = "https://www.youporn.com/channel/$clean/?page=$page"
        return parseYouPornHtml(url, limit)
    }

    /**
     * YouPornCollection: YouPorn collection (user playlist), with sorting and pagination
     */
    fun getCollection(collectionId: String, page: Int = 1, limit: Int = 30): List<VideoItem> {
        val clean = collectionId.trim().removePrefix("collection/").removePrefix("playlist/")
        val urls = listOf(
            "https://www.youporn.com/collection/$clean/?page=$page",
            "https://www.youporn.com/playlist/$clean/?page=$page"
        )
        for (u in urls) {
            val list = parseYouPornHtml(u, limit)
            if (list.isNotEmpty()) return list
        }
        return emptyList()
    }

    /**
     * YouPornStar: YouPorn Pornstar, with description, sorting and pagination
     */
    fun getPornstar(pornstarName: String, page: Int = 1, limit: Int = 30): List<VideoItem> {
        val clean = pornstarName.trim().lowercase().replace(" ", "-").removePrefix("pornstar/").removePrefix("pornstars/")
        val url = "https://www.youporn.com/pornstar/$clean/?page=$page"
        return parseYouPornHtml(url, limit)
    }

    /**
     * YouPornTag: YouPorn tag (porntags), with sorting, filtering and pagination
     */
    fun getTag(tagName: String, page: Int = 1, limit: Int = 30): List<VideoItem> {
        val clean = tagName.trim().lowercase().replace(" ", "-").removePrefix("tag/").removePrefix("porntags/")
        val urls = listOf(
            "https://www.youporn.com/porntags/$clean/?page=$page",
            "https://www.youporn.com/tag/$clean/?page=$page"
        )
        for (u in urls) {
            val list = parseYouPornHtml(u, limit)
            if (list.isNotEmpty()) return list
        }
        return emptyList()
    }

    fun search(query: String, page: Int = 1, limit: Int = 30): List<VideoItem> {
        val clean = query.trim()
        val lower = clean.lowercase()

        // 1. Direct command prefixes
        when {
            lower.startsWith("youporn:category:") || lower.startsWith("category:") -> {
                val cat = clean.substringAfter(":").substringAfter(":")
                return getCategory(cat, page = page, limit = limit)
            }
            lower.startsWith("youporn:channel:") || lower.startsWith("channel:") -> {
                val ch = clean.substringAfter(":").substringAfter(":")
                return getChannel(ch, page = page, limit = limit)
            }
            lower.startsWith("youporn:collection:") || lower.startsWith("youporn:playlist:") || lower.startsWith("collection:") -> {
                val col = clean.substringAfter(":").substringAfter(":")
                return getCollection(col, page = page, limit = limit)
            }
            lower.startsWith("youporn:star:") || lower.startsWith("youporn:pornstar:") || lower.startsWith("pornstar:") -> {
                val star = clean.substringAfter(":").substringAfter(":")
                return getPornstar(star, page = page, limit = limit)
            }
            lower.startsWith("youporn:tag:") || lower.startsWith("tag:") || lower.startsWith("porntags:") -> {
                val tag = clean.substringAfter(":").substringAfter(":")
                return getTag(tag, page = page, limit = limit)
            }
            lower == "youporn:videos" || lower == "youporn:browse" -> {
                return getVideos("time", page, limit)
            }
            lower == "youporn:top_rated" -> {
                return getVideos("top_rated", page, limit)
            }
            lower == "youporn:most_viewed" -> {
                return getVideos("most_viewed", page, limit)
            }
        }

        val encoded = URLEncoder.encode(clean.removePrefix("youporn:").trim(), "UTF-8")
        val urls = mutableListOf<String>()

        // Check if query is a simple category word
        if (lower.matches(Regex("^[a-z0-9-]+$")) && lower.length in 3..25) {
            urls.add("https://www.youporn.com/category/$lower/?page=$page")
        }
        urls.add("https://www.youporn.com/search/?query=$encoded&page=$page")
        urls.add("https://www.youporn.com/results?search_query=$encoded&page=$page")

        for (targetUrl in urls) {
            val list = parseYouPornHtml(targetUrl, limit)
            if (list.isNotEmpty()) {
                Log.d(TAG, "YouPorn search '$query' (page $page) fetched ${list.size} items from $targetUrl")
                return list
            }
        }
        return emptyList()
    }

    private fun parseYouPornHtml(targetUrl: String, limit: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Cookie", "age_verified=1; platform=pc; premium_redirect_cookie=1")
                .header("Referer", "https://www.youporn.com/")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val seen = mutableSetOf<String>()

            // Strategy 1: Direct Anchor Link Pattern matching all watch anchors
            val anchorPattern = Pattern.compile(
                """<a\s+[^>]*href="(/watch/(\d+)/?[^"]*)"[^>]*>(.*?)</a>""",
                Pattern.DOTALL or Pattern.CASE_INSENSITIVE
            )
            val aMatcher = anchorPattern.matcher(html)

            while (aMatcher.find() && list.size < limit) {
                val path = aMatcher.group(1) ?: continue
                val id = aMatcher.group(2) ?: continue
                val aInner = aMatcher.group(3) ?: ""
                val fullTag = aMatcher.group(0) ?: ""

                if (seen.contains(id)) continue
                seen.add(id)

                // 1. Title Extraction
                var title = ""
                val altMatch = Pattern.compile("""alt="([^"]+)"""", Pattern.CASE_INSENSITIVE).matcher(aInner)
                if (altMatch.find() && !altMatch.group(1).isNullOrBlank()) {
                    title = altMatch.group(1)!!.trim()
                } else {
                    val titleMatch = Pattern.compile("""title="([^"]+)"""", Pattern.CASE_INSENSITIVE).matcher(fullTag)
                    if (titleMatch.find() && !titleMatch.group(1).isNullOrBlank()) {
                        title = titleMatch.group(1)!!.trim()
                    }
                }
                if (title.isBlank()) {
                    val endPos = aMatcher.end()
                    val nextChunk = html.substring(endPos, minOf(endPos + 400, html.length))
                    val titlePattern = Pattern.compile("""class="[^"]*video-title[^"]*"[^>]*>(?:<[^>]*>)*\s*([^<]+)""", Pattern.CASE_INSENSITIVE)
                    val tm = titlePattern.matcher(nextChunk)
                    if (tm.find() && !tm.group(1).isNullOrBlank()) {
                        title = tm.group(1)!!.trim()
                    }
                }
                if (title.isBlank()) {
                    title = "YouPorn Video $id"
                }

                // 2. Thumbnail Extraction
                var thumb = ""
                val thumbPatterns = listOf(
                    Pattern.compile("""(?:data-src|data-poster|data-image|data-thumbnail|data-mediabook)="([^"]+?\.(?:jpg|jpeg|webp)[^"]*)"""", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("""data-src="([^"]+)"""", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("""data-poster="([^"]+)"""", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("""src="([^"]+?\.(?:jpg|jpeg|webp)[^"]*)"""", Pattern.CASE_INSENSITIVE)
                )
                for (p in thumbPatterns) {
                    val m = p.matcher(aInner)
                    if (m.find()) {
                        val found = m.group(1)?.trim() ?: ""
                        if (found.isNotBlank() && !found.endsWith(".gif")) {
                            thumb = found
                            break
                        }
                    }
                }
                if (thumb.isBlank()) {
                    val startPos = maxOf(0, aMatcher.start() - 350)
                    val endPos = minOf(html.length, aMatcher.end() + 350)
                    val surroundingChunk = html.substring(startPos, endPos)
                    val cdnMatcher = Pattern.compile("""(https?://[a-z0-9\.\-]*ypncdn\.com/[^"'\s>]+?\.(?:jpg|jpeg|webp))""", Pattern.CASE_INSENSITIVE).matcher(surroundingChunk)
                    if (cdnMatcher.find()) {
                        thumb = cdnMatcher.group(1) ?: ""
                    }
                }
                if (thumb.isBlank() && id.isNotBlank()) {
                    thumb = "https://di-ph.ypncdn.com/videos/$id/1.jpg"
                }

                // 3. Duration Extraction
                var durSec = -1L
                val durMatch = Pattern.compile("""(?:duration|video-duration)[^>]*>(?:<[^>]*>)*\s*([0-9:]+)""", Pattern.CASE_INSENSITIVE).matcher(aInner)
                if (durMatch.find()) {
                    durSec = parseDurationToSeconds(durMatch.group(1)?.trim() ?: "")
                } else {
                    val dMatch2 = Pattern.compile("""([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?)""").matcher(aInner)
                    if (dMatch2.find()) {
                        durSec = parseDurationToSeconds(dMatch2.group(1)?.trim() ?: "")
                    }
                }

                // 4. Author / Channel Extraction
                var author = "YouPorn"
                val endPos = aMatcher.end()
                val nextChunk = html.substring(endPos, minOf(endPos + 500, html.length))
                val authorMatch = Pattern.compile("""class="[^"]*(?:author|uploader|by-user|channel)[^"]*"[^>]*>(?:<[^>]*>)*\s*([^<]+)""", Pattern.CASE_INSENSITIVE).matcher(nextChunk)
                if (authorMatch.find()) {
                    val a = authorMatch.group(1)?.trim() ?: ""
                    if (a.isNotBlank()) author = a
                }

                val fullUrl = if (path.startsWith("http")) path else "https://www.youporn.com$path"
                list.add(
                    VideoItem(
                        id = fullUrl,
                        title = title,
                        uploaderName = author,
                        thumbnailUrl = thumb,
                        durationSeconds = durSec,
                        providerId = PROVIDER_ID
                    )
                )
            }

            // Strategy 2: Fast JSON-LD or VideoObject Metadata extraction in HTML if anchor scan missed
            if (list.isEmpty()) {
                val jsonLdPattern = Pattern.compile("""<script[^>]+type="application/ld\+json"[^>]*>(.*?)</script>""", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
                val jsonLdMatcher = jsonLdPattern.matcher(html)
                while (jsonLdMatcher.find() && list.size < limit) {
                    val rawJson = jsonLdMatcher.group(1)?.trim() ?: continue
                    try {
                        if (rawJson.startsWith("[")) {
                            val arr = JSONArray(rawJson)
                            for (i in 0 until arr.length()) {
                                parseJsonLdVideo(arr.getJSONObject(i), list, seen, limit)
                            }
                        } else if (rawJson.startsWith("{")) {
                            val obj = JSONObject(rawJson)
                            if (obj.has("@graph")) {
                                val gArr = obj.getJSONArray("@graph")
                                for (i in 0 until gArr.length()) {
                                    parseJsonLdVideo(gArr.getJSONObject(i), list, seen, limit)
                                }
                            } else {
                                parseJsonLdVideo(obj, list, seen, limit)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "YouPorn parse error for $targetUrl: ${e.message}")
        }
        return list
    }

    private fun parseJsonLdVideo(obj: JSONObject, list: MutableList<VideoItem>, seen: MutableSet<String>, limit: Int) {
        if (list.size >= limit) return
        val type = obj.optString("@type", "")
        if (type.equals("VideoObject", ignoreCase = true) || obj.has("embedUrl") || obj.has("contentUrl") || obj.has("thumbnailUrl")) {
            val url = obj.optString("url", obj.optString("embedUrl", obj.optString("contentUrl", "")))
            if (url.isBlank() || seen.contains(url)) return
            seen.add(url)

            val name = obj.optString("name", obj.optString("headline", "YouPorn Video"))
            val thumb = when {
                obj.has("thumbnailUrl") -> {
                    val t = obj.opt("thumbnailUrl")
                    if (t is JSONArray && t.length() > 0) t.getString(0) else t?.toString() ?: ""
                }
                obj.has("thumbnail") -> obj.optString("thumbnail", "")
                else -> ""
            }
            val author = obj.optJSONObject("author")?.optString("name", "YouPorn") ?: "YouPorn"

            list.add(
                VideoItem(
                    id = url,
                    title = name,
                    uploaderName = author,
                    thumbnailUrl = thumb,
                    providerId = PROVIDER_ID
                )
            )
        }
    }

    private fun parseDurationToSeconds(durStr: String): Long {
        val parts = durStr.split(":")
        var total = 0L
        for (p in parts) {
            total = total * 60 + (p.toLongOrNull() ?: 0L)
        }
        return total
    }

    // ------------------- STREAM EXTRACTION -------------------

    suspend fun getStreamData(urlOrId: String, context: Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val cleanInput = urlOrId.trim()
        val targetUrl = when {
            cleanInput.startsWith("http") -> cleanInput
            cleanInput.startsWith("/") -> "https://www.youporn.com$cleanInput"
            else -> "https://www.youporn.com/watch/$cleanInput/"
        }

        Log.d(TAG, "Extracting YouPorn stream data for: $targetUrl")

        try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Cookie", "age_verified=1; platform=pc")
                .header("Referer", "https://www.youporn.com/")
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!html.isNullOrBlank()) {
                // Title
                var title = "YouPorn Video"
                val titlePattern = Pattern.compile("<meta\\s+property=\"og:title\"\\s+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE)
                val titleMatcher = titlePattern.matcher(html)
                if (titleMatcher.find()) {
                    title = titleMatcher.group(1)?.trim() ?: title
                } else {
                    val h1Pattern = Pattern.compile("<h1[^>]*>(.*?)</h1>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
                    val h1Matcher = h1Pattern.matcher(html)
                    if (h1Matcher.find()) {
                        title = h1Matcher.group(1)?.replace(Regex("<[^>]*>"), "")?.trim() ?: title
                    }
                }

                // Thumbnail
                var thumb = ""
                val thumbPattern = Pattern.compile("<meta\\s+property=\"og:image\"\\s+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE)
                val thumbMatcher = thumbPattern.matcher(html)
                if (thumbMatcher.find()) {
                    thumb = thumbMatcher.group(1)?.trim() ?: ""
                }

                // Channel/Author
                var channel = "YouPorn"
                val chanPattern = Pattern.compile("class=\"author-title-text[^\"]*\"[^>]*>([^<]+)</a>", Pattern.CASE_INSENSITIVE)
                val chanMatcher = chanPattern.matcher(html)
                if (chanMatcher.find()) {
                    channel = chanMatcher.group(1)?.trim() ?: channel
                }

                val streamOptions = mutableListOf<PlayableStreamOption>()

                // 1. Extract mediaDefinitions JSON
                val mediaDefPattern = Pattern.compile("\"mediaDefinitions\"\\s*:\\s*(\\[.*?\\])\\s*,\\s*\"", Pattern.DOTALL)
                val mediaDefMatcher = mediaDefPattern.matcher(html)

                if (mediaDefMatcher.find()) {
                    val mediaDefsJson = mediaDefMatcher.group(1) ?: ""
                    try {
                        val arr = JSONArray(mediaDefsJson)
                        for (i in 0 until arr.length()) {
                            val mdObj = arr.getJSONObject(i)
                            val formatType = mdObj.optString("format", "mp4")
                            val isRemote = mdObj.optBoolean("remote", false)
                            val rawVideoUrl = mdObj.optString("videoUrl", "").replace("\\/", "/")

                            if (rawVideoUrl.isNotBlank() && rawVideoUrl.startsWith("http")) {
                                if (isRemote) {
                                    // Fetch remote media JSON endpoint
                                    try {
                                        val remoteReq = Request.Builder()
                                            .url(rawVideoUrl)
                                            .header("User-Agent", DEFAULT_USER_AGENT)
                                            .header("Cookie", "age_verified=1; platform=pc")
                                            .header("Referer", targetUrl)
                                            .build()

                                        val remoteJsonStr = httpClient.newCall(remoteReq).execute().use { rResp ->
                                            if (rResp.isSuccessful) rResp.body?.string() else null
                                        }

                                        if (!remoteJsonStr.isNullOrBlank()) {
                                            val subArr = JSONArray(remoteJsonStr)
                                            for (j in 0 until subArr.length()) {
                                                val streamObj = subArr.getJSONObject(j)
                                                val streamUrl = streamObj.optString("videoUrl", "").replace("\\/", "/")
                                                if (streamUrl.isNotBlank() && streamUrl.startsWith("http")) {
                                                    val q = streamObj.optString("quality", "720")
                                                    val fmt = streamObj.optString("format", formatType).lowercase()
                                                    val isHls = fmt == "hls" || streamUrl.contains(".m3u8")
                                                    val label = if (q.isNotBlank()) "${q}p (${fmt.uppercase()})" else fmt.uppercase()

                                                    streamOptions.add(
                                                        PlayableStreamOption(
                                                            qualityLabel = label,
                                                            format = if (isHls) "m3u8" else "mp4",
                                                            isMuxed = true,
                                                            videoUrl = streamUrl,
                                                            providerType = ProviderType.OTHER,
                                                            headers = COMMON_HEADERS
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    } catch (remoteErr: Exception) {
                                        Log.w(TAG, "Error fetching remote media endpoint $rawVideoUrl: ${remoteErr.message}")
                                    }
                                } else {
                                    val q = mdObj.optString("quality", "720")
                                    val isHls = formatType.equals("hls", ignoreCase = true) || rawVideoUrl.contains(".m3u8")
                                    streamOptions.add(
                                        PlayableStreamOption(
                                            qualityLabel = if (q.isNotBlank()) "${q}p (${formatType.uppercase()})" else formatType.uppercase(),
                                            format = if (isHls) "m3u8" else "mp4",
                                            isMuxed = true,
                                            videoUrl = rawVideoUrl,
                                            providerType = ProviderType.OTHER,
                                            headers = COMMON_HEADERS
                                        )
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing mediaDefinitions: ${e.message}")
                    }
                }

                // 2. Direct regex fallback for m3u8 / ypncdn URLs if mediaDefinitions didn't produce streams
                if (streamOptions.isEmpty()) {
                    val cleanHtml = html.replace("\\/", "/")
                    val directPattern = Pattern.compile("https?://[^\"',\\s<>]+\\.(?:m3u8|mp4)[^\"',\\s<>]*", Pattern.CASE_INSENSITIVE)
                    val dMatcher = directPattern.matcher(cleanHtml)
                    val seenDirect = mutableSetOf<String>()

                    while (dMatcher.find()) {
                        val dUrl = dMatcher.group(0) ?: continue
                        if (seenDirect.contains(dUrl) || dUrl.contains("timeline") || dUrl.contains("original_") || dUrl.contains(".jpg")) continue
                        seenDirect.add(dUrl)

                        val isHls = dUrl.contains(".m3u8")
                        streamOptions.add(
                            PlayableStreamOption(
                                qualityLabel = if (isHls) "HLS Auto" else "MP4 Direct",
                                format = if (isHls) "m3u8" else "mp4",
                                isMuxed = true,
                                videoUrl = dUrl,
                                providerType = ProviderType.OTHER,
                                headers = COMMON_HEADERS
                            )
                        )
                    }
                }

                if (streamOptions.isNotEmpty()) {
                    val sortedOptions = streamOptions.distinctBy { it.videoUrl ?: "" }.sortedWith(
                        compareByDescending<PlayableStreamOption> {
                            Regex("""\d+""").find(it.qualityLabel)?.value?.toIntOrNull() ?: 0
                        }.thenByDescending { it.format == "mp4" }
                    )

                    val primaryStream = sortedOptions.firstOrNull()
                    val primaryUrl = primaryStream?.videoUrl ?: ""

                    Log.i(TAG, "Successfully extracted ${sortedOptions.size} stream options for YouPorn: $targetUrl")

                    return@withContext StreamData(
                        videoId = targetUrl,
                        videoUrl = primaryUrl,
                        title = title,
                        channelName = channel,
                        thumbnailUrl = thumb,
                        availableStreamOptions = sortedOptions,
                        selectedStreamOption = primaryStream,
                        providerId = PROVIDER_ID,
                        headers = COMMON_HEADERS
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "YouPorn direct HTML extraction failed: ${e.message}")
        }

        // Fallback to YtDlpResolver
        if (context != null) {
            try {
                Log.i(TAG, "Falling back to YtDlpResolver for YouPorn URL: $targetUrl")
                val ytDlpRes = YtDlpResolver.extractStreamInfo(context, targetUrl)
                if (ytDlpRes is YouTubeExtractorHelper.ExtractionResult.Success) {
                    return@withContext ytDlpRes.streamData.copy(
                        providerId = PROVIDER_ID,
                        headers = COMMON_HEADERS
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "YtDlpResolver YouPorn fallback failed: ${e.message}")
            }
        }

        null
    }
}
