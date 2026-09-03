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
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object XHamsterProvider {
    private const val TAG = "XHamsterProvider"
    const val PROVIDER_ID = "xhamster"

    private const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // ------------------- STREAM URL DECODER -------------------
    private fun imul(a: Long, b: Long): Int {
        val aInt = a.toInt()
        val bInt = b.toInt()
        val aLow = aInt and 0xFFFF
        val aHigh = aInt ushr 16
        val bLow = bInt and 0xFFFF
        val bHigh = bInt ushr 16
        return (aLow * bLow + (((aHigh * bLow + aLow * bHigh) and 0xFFFF) shl 16))
    }

    fun decodeHexUrl(hexStr: String?): String? {
        if (hexStr.isNullOrBlank() || hexStr.length < 12 || hexStr.length % 2 != 0) {
            return null
        }
        if (!hexStr.matches(Regex("^[0-9a-fA-F]+$"))) {
            return null
        }

        val rawBytes: ByteArray
        try {
            val len = hexStr.length
            rawBytes = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                rawBytes[i / 2] = ((Character.digit(hexStr[i], 16) shl 4) + Character.digit(hexStr[i + 1], 16)).toByte()
                i += 2
            }
        } catch (e: Exception) {
            return null
        }

        if (rawBytes.size < 5) return null

        val algoId = rawBytes[0].toInt() and 0xFF
        val seed = (rawBytes[1].toInt() and 0xFF) or
                ((rawBytes[2].toInt() and 0xFF) shl 8) or
                ((rawBytes[3].toInt() and 0xFF) shl 16) or
                ((rawBytes[4].toInt() and 0xFF) shl 24)

        var curr = seed
        fun nextByte(): Int {
            when (algoId) {
                1 -> {
                    curr = (imul(curr.toLong(), 1664525L) + 0x3c6ef35f)
                    return curr and 0xFF
                }
                2 -> {
                    curr = curr xor (curr shl 13)
                    curr = curr xor (curr ushr 17)
                    curr = curr xor (curr shl 5)
                    return curr and 0xFF
                }
                3 -> {
                    curr += 0x9e3779b9.toInt()
                    var e = curr
                    e = e xor (e ushr 16)
                    e = imul(e.toLong(), 0x85ebca77L)
                    e = e xor (e ushr 13)
                    e = imul(e.toLong(), 0xc2b2ae3dL)
                    e = e xor (e ushr 16)
                    return e and 0xFF
                }
                4 -> {
                    curr += 0x6d2b79f5.toInt()
                    var e = (curr shl 7) or (curr ushr 25)
                    e += 0x9e3779b9.toInt()
                    e = e xor (e ushr 11)
                    e = imul(e.toLong(), 0x27d4eb2dL)
                    return e and 0xFF
                }
                5 -> {
                    curr = curr xor (curr shl 7)
                    curr = curr xor (curr ushr 9)
                    curr = curr xor (curr shl 8)
                    curr += 0xa5a5a5a5.toInt()
                    return curr and 0xFF
                }
                6 -> {
                    curr = imul(curr.toLong(), 0x2c9277b5L) + 0xac564b05.toInt()
                    val shift1 = curr ushr 18
                    val shift2 = (curr ushr 27) and 31
                    val v = (curr xor shift1) ushr shift2
                    return v and 0xFF
                }
                7 -> {
                    curr += 0x9e3779b9.toInt()
                    var e = curr xor (curr shl 5)
                    e = imul(e.toLong(), 0x7feb352dL)
                    e = e xor (e ushr 15)
                    e = imul(e.toLong(), 0x846ca68bL)
                    return e and 0xFF
                }
                else -> return 0
            }
        }

        val decrypted = ByteArray(rawBytes.size - 5)
        for (idx in 5 until rawBytes.size) {
            decrypted[idx - 5] = (rawBytes[idx].toInt() xor nextByte()).toByte()
        }

        return try {
            val result = String(decrypted, Charsets.UTF_8)
            if (result.startsWith("http")) result else null
        } catch (e: Exception) {
            null
        }
    }

    // ------------------- CATALOG / SEARCH -------------------
    fun getHome(limit: Int = 40, page: Int = 1): List<VideoItem> {
        val urls = listOf(
            if (page > 1) "https://xhamster.com/best/$page" else "https://xhamster.com/best",
            if (page > 1) "https://xhamster.com/newest/$page" else "https://xhamster.com/newest",
            if (page > 1) "https://xhamster.com/?page=$page" else "https://xhamster.com/"
        )
        for (u in urls) {
            val items = parseListingHtml(u, limit)
            if (items.isNotEmpty()) return items
        }
        return emptyList()
    }

    fun search(query: String, limit: Int = 40, page: Int = 1): List<VideoItem> {
        val clean = query.trim()
        val userMatch = Regex("(?i)^(?:xhamster:)?(?:user|creator|pornstar|channel):([a-zA-Z0-9_-]+)").find(clean)
        if (userMatch != null) {
            val username = userMatch.groupValues[1]
            return getUserVideos(username, limit, page)
        }
        val encoded = URLEncoder.encode(clean, "UTF-8")
        val targetUrl = if (page > 1) "https://xhamster.com/search/$encoded?page=$page" else "https://xhamster.com/search/$encoded"
        return parseListingHtml(targetUrl, limit)
    }

    fun getUserVideos(userOrChannelOrPornstar: String, limit: Int = 40, page: Int = 1): List<VideoItem> {
        val clean = userOrChannelOrPornstar.trim().removePrefix("https://xhamster.com").removePrefix("/")
        val urls = listOf(
            if (clean.startsWith("users/")) "https://xhamster.com/$clean/videos?page=$page" else "https://xhamster.com/users/$clean/videos?page=$page",
            if (clean.startsWith("creators/")) "https://xhamster.com/$clean?page=$page" else "https://xhamster.com/creators/$clean?page=$page",
            if (clean.startsWith("pornstars/")) "https://xhamster.com/$clean?page=$page" else "https://xhamster.com/pornstars/$clean?page=$page",
            if (clean.startsWith("channels/")) "https://xhamster.com/$clean?page=$page" else "https://xhamster.com/channels/$clean?page=$page"
        )
        for (u in urls) {
            val items = parseListingHtml(u, limit)
            if (items.isNotEmpty()) return items
        }
        return getCreatorVideos(clean, limit, page)
    }

    fun getCreatorVideos(creatorUrlOrSlug: String, limit: Int = 40, page: Int = 1): List<VideoItem> {
        val cleanSlug = creatorUrlOrSlug.trim().removePrefix("https://xhamster.com").removePrefix("/")
        val targetUrl = if (cleanSlug.startsWith("creators/") || cleanSlug.startsWith("pornstars/") || cleanSlug.startsWith("channels/") || cleanSlug.startsWith("users/")) {
            val base = "https://xhamster.com/$cleanSlug"
            if (page > 1) "$base?page=$page" else base
        } else {
            val base = "https://xhamster.com/creators/$cleanSlug"
            if (page > 1) "$base?page=$page" else base
        }
        return parseListingHtml(targetUrl, limit)
    }

    private fun parseListingHtml(targetUrl: String, limit: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        val seen = mutableSetOf<String>()

        try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", DEFAULT_UA)
                .header("Cookie", "age_verified=1; platform=pc; has_consent=1")
                .header("Referer", "https://xhamster.com/")
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            // 1. PRIMARY PARSER: Extract window.initials structured JSON data
            val initialsMatch = Pattern.compile("window\\.initials\\s*=\\s*(\\{.*?\\});</script>", Pattern.DOTALL).matcher(html)
            if (initialsMatch.find()) {
                try {
                    val jsonStr = initialsMatch.group(1) ?: ""
                    val root = JSONObject(jsonStr)
                    val jsonItems = findVideoObjects(root)
                    for (v in jsonItems) {
                        if (list.size >= limit) break
                        val pageUrl = v.optString("pageURL", "").trim()
                        if (pageUrl.isBlank() || seen.contains(pageUrl) || pageUrl.contains("/shorts/")) continue
                        seen.add(pageUrl)

                        val title = v.optString("title", "xHamster Video").trim()
                        val duration = v.optLong("duration", -1L)
                        val views = v.optLong("views", -1L)
                        val thumb = v.optString("imageURL", "").ifBlank {
                            v.optString("thumbURL", "").ifBlank {
                                v.optString("previewThumbURL", "")
                            }
                        }
                        val trailerUrl = v.optString("trailerFallbackUrl", "").ifBlank {
                            v.optString("trailerURL", "")
                        }

                        val landing = v.optJSONObject("landing")
                        val uploaderName = landing?.optString("name")?.takeIf { it.isNotBlank() } ?: "xHamster"
                        val uploaderAvatar = landing?.optString("logo")?.takeIf { it.isNotBlank() }
                        val uploaderUrl = landing?.optString("link")?.takeIf { it.isNotBlank() }

                        val previewList = if (thumb.isNotBlank()) listOf(thumb) else emptyList()

                        list.add(
                            VideoItem(
                                id = pageUrl,
                                title = title,
                                uploaderName = uploaderName,
                                uploaderUrl = uploaderUrl,
                                uploaderAvatarUrl = uploaderAvatar,
                                viewCount = views,
                                durationSeconds = duration,
                                thumbnailUrl = thumb,
                                providerId = PROVIDER_ID,
                                previewThumbnails = previewList,
                                previewClipUrl = trailerUrl.takeIf { it.isNotBlank() }
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing window.initials in parseListingHtml: ${e.message}")
                }
            }

            // 2. FALLBACK PARSER: HTML cards splitter & scraper
            if (list.isEmpty()) {
                val cardSplits = html.split(Regex("class=[\"'][^\"']*thumb-list__item[^\"']*[\"']"))
                for (c in cardSplits) {
                    if (list.size >= limit) break
                    val vlinkMatcher = Pattern.compile("href=[\"']((?:https://xhamster\\.com)?/videos/[^\"']+)[\"']").matcher(c)
                    if (!vlinkMatcher.find()) continue
                    val rawUrl = vlinkMatcher.group(1) ?: continue
                    val videoUrl = if (rawUrl.startsWith("http")) rawUrl else "https://xhamster.com$rawUrl"
                    if (seen.contains(videoUrl) || videoUrl.contains("/shorts/")) continue
                    seen.add(videoUrl)

                    // Title
                    var title = "xHamster Video"
                    val ariaMatcher = Pattern.compile("aria-label=[\"']([^\"']+)[\"']").matcher(c)
                    if (ariaMatcher.find()) {
                        val candidate = ariaMatcher.group(1)?.trim() ?: ""
                        if (candidate.isNotBlank() && !candidate.startsWith("thumb", ignoreCase = true)) {
                            title = candidate
                        }
                    }
                    if (title == "xHamster Video") {
                        val titleMatcher = Pattern.compile("title=[\"']([^\"']+)[\"']").matcher(c)
                        if (titleMatcher.find()) {
                            val candidate = titleMatcher.group(1)?.trim() ?: ""
                            if (candidate.isNotBlank() && !candidate.startsWith("thumb", ignoreCase = true)) {
                                title = candidate
                            }
                        }
                    }

                    // Thumbnail
                    var thumb = ""
                    val imgMatcher = Pattern.compile("<img[^>]+(?:src|data-src)=[\"']([^\"']+)[\"']").matcher(c)
                    if (imgMatcher.find()) {
                        val cand = imgMatcher.group(1)?.trim() ?: ""
                        if (cand.startsWith("http") && !cand.contains("data:image")) {
                            thumb = cand
                        }
                    }
                    if (thumb.isBlank()) {
                        val cdnMatcher = Pattern.compile("(https://[^\"'\\s>]*xhcdn[^\"'\\s>]*\\.(?:webp|jpg|jpeg)[^\"'\\s>]*)").matcher(c)
                        if (cdnMatcher.find()) {
                            thumb = cdnMatcher.group(1) ?: ""
                        }
                    }

                    // Duration
                    var durSec = -1L
                    val durMatcher = Pattern.compile("data-role=[\"']video-duration[\"'][^>]*>.*?([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?)", Pattern.DOTALL).matcher(c)
                    if (durMatcher.find()) {
                        durSec = parseDurationToSeconds(durMatcher.group(1) ?: "")
                    }

                    // Uploader
                    var uploaderName = "xHamster"
                    val upNameMatcher = Pattern.compile("class=[\"'][^\"']*video-uploader__name[^\"']*[\"'][^>]*>(?:<!--.*?-->)*([^<]+)").matcher(c)
                    if (upNameMatcher.find()) {
                        val cand = upNameMatcher.group(1)?.trim() ?: ""
                        if (cand.isNotBlank()) uploaderName = cand
                    }

                    var uploaderAvatar: String? = null
                    val upLogoMatcher = Pattern.compile("class=[\"'][^\"']*video-uploader-logo[^\"']*[\"'][^>]*data-background-image=[\"']([^\"']+)[\"']").matcher(c)
                    if (upLogoMatcher.find()) {
                        uploaderAvatar = upLogoMatcher.group(1)?.trim()
                    }

                    var uploaderUrl: String? = null
                    val upUrlMatcher = Pattern.compile("class=[\"'][^\"']*video-uploader__name[^\"']*[\"'][^>]*href=[\"']([^\"']+)[\"']").matcher(c)
                    if (upUrlMatcher.find()) {
                        uploaderUrl = upUrlMatcher.group(1)?.trim()
                    }

                    // Views
                    var viewCount = -1L
                    val viewsMatcher = Pattern.compile("class=[\"'][^\"']*video-thumb-views[^\"']*[\"'][^>]*>([^<]+)").matcher(c)
                    if (viewsMatcher.find()) {
                        val rawViews = viewsMatcher.group(1)?.trim() ?: ""
                        viewCount = parseViewCount(rawViews)
                    }

                    // Teaser preview video
                    var previewClip: String? = null
                    val pvMatcher = Pattern.compile("data-previewvideo(?:-fallback)?=[\"']([^\"']+)[\"']").matcher(c)
                    if (pvMatcher.find()) {
                        previewClip = pvMatcher.group(1)?.trim()
                    }

                    val previewList = if (thumb.isNotBlank()) listOf(thumb) else emptyList()

                    list.add(
                        VideoItem(
                            id = videoUrl,
                            title = title,
                            uploaderName = uploaderName,
                            uploaderUrl = uploaderUrl,
                            uploaderAvatarUrl = uploaderAvatar,
                            viewCount = viewCount,
                            durationSeconds = durSec,
                            thumbnailUrl = thumb,
                            providerId = PROVIDER_ID,
                            previewThumbnails = previewList,
                            previewClipUrl = previewClip
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Listing parse error for $targetUrl: ${e.message}")
        }
        return list
    }

    private fun findVideoObjects(root: JSONObject): List<JSONObject> {
        val results = mutableListOf<JSONObject>()
        
        // 1. Common direct paths in initials
        val layoutPage = root.optJSONObject("layoutPage")
        val videoListProps = layoutPage?.optJSONObject("videoListProps")
        val directThumbs = videoListProps?.optJSONArray("videoThumbProps")
        if (directThumbs != null && directThumbs.length() > 0) {
            for (i in 0 until directThumbs.length()) {
                val obj = directThumbs.optJSONObject(i) ?: continue
                results.add(obj)
            }
            return results
        }

        val searchResult = root.optJSONObject("searchResult")
        val searchThumbs = searchResult?.optJSONArray("videoThumbProps")
        if (searchThumbs != null && searchThumbs.length() > 0) {
            for (i in 0 until searchThumbs.length()) {
                val obj = searchThumbs.optJSONObject(i) ?: continue
                results.add(obj)
            }
            return results
        }

        val userPage = root.optJSONObject("userPage")
        val userThumbs = userPage?.optJSONObject("videoListProps")?.optJSONArray("videoThumbProps")
        if (userThumbs != null && userThumbs.length() > 0) {
            for (i in 0 until userThumbs.length()) {
                val obj = userThumbs.optJSONObject(i) ?: continue
                results.add(obj)
            }
            return results
        }

        // 2. Recursive fallback scanner
        fun scan(obj: Any?) {
            when (obj) {
                is JSONObject -> {
                    if (obj.has("pageURL") && (obj.has("thumbURL") || obj.has("imageURL") || obj.has("duration"))) {
                        results.add(obj)
                        return
                    }
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        scan(obj.opt(keys.next()))
                    }
                }
                is org.json.JSONArray -> {
                    for (i in 0 until obj.length()) {
                        scan(obj.opt(i))
                    }
                }
            }
        }

        scan(root)
        return results
    }

    private fun parseViewCount(str: String): Long {
        return try {
            val clean = str.replace("views", "").replace("view", "").trim()
            val numStr = Regex("""[\d\.,]+""").find(clean)?.value?.replace(",", "") ?: return -1L
            val num = numStr.toDoubleOrNull() ?: return -1L
            when {
                clean.contains("B", ignoreCase = true) -> (num * 1_000_000_000).toLong()
                clean.contains("M", ignoreCase = true) -> (num * 1_000_000).toLong()
                clean.contains("K", ignoreCase = true) -> (num * 1_000).toLong()
                else -> num.toLong()
            }
        } catch (_: Exception) {
            -1L
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
    suspend fun getStreamData(urlOrId: String, context: Context?): StreamData? = withContext(Dispatchers.IO) {
        val clean = urlOrId.trim()
        val targetUrl = when {
            clean.contains("xembed.php?video_id=") -> {
                val vid = clean.substringAfter("video_id=").substringBefore("&")
                "https://xhamster.com/videos/$vid"
            }
            clean.contains("/embed/") -> {
                val vid = clean.substringAfter("/embed/").substringBefore("?").substringBefore("/")
                "https://xhamster.com/videos/$vid"
            }
            clean.startsWith("xhamster:embed:", ignoreCase = true) -> {
                val vid = clean.substringAfter("xhamster:embed:")
                "https://xhamster.com/videos/$vid"
            }
            clean.startsWith("xhamster:", ignoreCase = true) -> {
                val vid = clean.substringAfter("xhamster:")
                "https://xhamster.com/videos/$vid"
            }
            clean.startsWith("http") -> clean
            else -> "https://xhamster.com/videos/$clean"
        }
        Log.d(TAG, "Fetching xHamster stream data for $targetUrl")

        try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", DEFAULT_UA)
                .header("Cookie", "age_verified=1; platform=pc")
                .header("Referer", "https://xhamster.com/")
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return@withContext null

            // Title
            var title = "xHamster Video"
            var thumb = ""
            var channelName = "xHamster"
            var channelAvatarUrl: String? = null
            var viewCount = -1L
            var durationSeconds = -1L

            val metaTitle = Pattern.compile("<meta\\s+property=\"og:title\"\\s+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(html)
            if (metaTitle.find()) {
                title = metaTitle.group(1)?.trim() ?: title
            } else {
                val h1Match = Pattern.compile("<h1[^>]*>(.*?)</h1>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE).matcher(html)
                if (h1Match.find()) {
                    title = h1Match.group(1)?.replace(Regex("<[^>]*>"), "")?.trim() ?: title
                }
            }

            // Thumbnail
            val metaThumb = Pattern.compile("<meta\\s+property=\"og:image\"\\s+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(html)
            if (metaThumb.find()) {
                thumb = metaThumb.group(1)?.trim() ?: ""
            }

            val headers = mapOf(
                "User-Agent" to DEFAULT_UA,
                "Referer" to "https://xhamster.com/"
            )

            val streamOptions = mutableListOf<PlayableStreamOption>()

            // 1. Extract window.initials -> xplayerSettings
            val initialsMatch = Pattern.compile("window\\.initials\\s*=\\s*(\\{.*?\\});</script>", Pattern.DOTALL).matcher(html)
            if (initialsMatch.find()) {
                val jsonStr = initialsMatch.group(1) ?: ""
                try {
                    val root = JSONObject(jsonStr)
                    if (root.has("videoModel")) {
                        val vm = root.getJSONObject("videoModel")
                        if (thumb.isBlank()) {
                            thumb = vm.optString("imageURL", "").ifBlank {
                                vm.optString("thumbURL", "").ifBlank {
                                    vm.optString("previewThumbURL", "")
                                }
                            }
                        }
                        val vmTitle = vm.optString("title", "")
                        if (vmTitle.isNotBlank() && title == "xHamster Video") {
                            title = vmTitle
                        }
                        viewCount = vm.optLong("views", -1L)
                        durationSeconds = vm.optLong("duration", -1L)

                        val channelModel = vm.optJSONObject("channelModel")
                        val landing = vm.optJSONObject("landing")
                        val author = vm.optJSONObject("author")

                        channelName = channelModel?.optString("name")?.takeIf { it.isNotBlank() }
                            ?: landing?.optString("name")?.takeIf { it.isNotBlank() }
                            ?: author?.optString("name")?.takeIf { it.isNotBlank() }
                            ?: vm.optString("modelName").takeIf { it.isNotBlank() }
                            ?: "xHamster"

                        channelAvatarUrl = landing?.optString("logo")?.takeIf { it.isNotBlank() }
                            ?: channelModel?.optString("logo")?.takeIf { it.isNotBlank() }
                            ?: author?.optString("avatar")?.takeIf { it.isNotBlank() }
                    }

                    if (root.has("xplayerSettings")) {
                        val xps = root.getJSONObject("xplayerSettings")
                        if (xps.has("sources")) {
                            val sources = xps.getJSONObject("sources")
                            // HLS streams
                            if (sources.has("hls")) {
                                val hlsObj = sources.getJSONObject("hls")
                                for (key in listOf("h264", "av1")) {
                                    if (hlsObj.has(key)) {
                                        val entry = hlsObj.getJSONObject(key)
                                        val encUrl = entry.optString("url", "")
                                        val decUrl = decodeHexUrl(encUrl)
                                        if (!decUrl.isNullOrBlank()) {
                                            streamOptions.add(
                                                PlayableStreamOption(
                                                    qualityLabel = "Auto HLS (${key.uppercase()})",
                                                    format = "m3u8",
                                                    isMuxed = true,
                                                    videoUrl = decUrl,
                                                    providerType = ProviderType.OTHER,
                                                    headers = headers
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            // Standard progressive MP4 streams
                            if (sources.has("standard")) {
                                val stdObj = sources.getJSONObject("standard")
                                for (codec in listOf("h264", "av1")) {
                                    if (stdObj.has(codec)) {
                                        val arr = stdObj.getJSONArray(codec)
                                        for (i in 0 until arr.length()) {
                                            val item = arr.getJSONObject(i)
                                            val encUrl = item.optString("url", "")
                                            val decUrl = decodeHexUrl(encUrl)
                                            val quality = item.optString("quality", "720p")
                                            val label = item.optString("label", quality)
                                            if (!decUrl.isNullOrBlank()) {
                                                val isHls = decUrl.contains(".m3u8")
                                                streamOptions.add(
                                                    PlayableStreamOption(
                                                        qualityLabel = "$label ($codec)",
                                                        format = if (isHls) "m3u8" else "mp4",
                                                        isMuxed = true,
                                                        videoUrl = decUrl,
                                                        providerType = ProviderType.OTHER,
                                                        headers = headers
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing window.initials JSON: ${e.message}")
                }
            }

            // 2. Direct regex fallback for m3u8 / xhcdn URLs
            if (streamOptions.isEmpty()) {
                val cleanHtml = html.replace("\\/", "/")
                val m3u8Matcher = Pattern.compile("https?://video-nss[^\",\\s<>]+\\.m3u8", Pattern.CASE_INSENSITIVE).matcher(cleanHtml)
                while (m3u8Matcher.find()) {
                    val m3u8Url = m3u8Matcher.group(0) ?: continue
                    streamOptions.add(
                        PlayableStreamOption(
                            qualityLabel = "Direct HLS",
                            format = "m3u8",
                            isMuxed = true,
                            videoUrl = m3u8Url,
                            providerType = ProviderType.OTHER,
                            headers = headers
                        )
                    )
                }
            }

            if (streamOptions.isEmpty()) {
                Log.w(TAG, "No playable streams found via direct initials for xHamster video: $targetUrl")
            } else {
                val sortedOptions = streamOptions.distinctBy { it.videoUrl ?: "" }.sortedWith(
                    compareByDescending<PlayableStreamOption> { it.format == "m3u8" }
                        .thenByDescending {
                            val num = Regex("""\d+""").find(it.qualityLabel)?.value?.toIntOrNull() ?: 0
                            num
                        }
                )

                val primaryStream = sortedOptions.firstOrNull()
                val primaryUrl = primaryStream?.videoUrl ?: ""

                return@withContext StreamData(
                    videoId = targetUrl,
                    videoUrl = primaryUrl,
                    title = title,
                    channelName = channelName,
                    channelAvatarUrl = channelAvatarUrl,
                    viewCount = viewCount,
                    thumbnailUrl = thumb,
                    availableStreamOptions = sortedOptions,
                    selectedStreamOption = primaryStream,
                    providerId = PROVIDER_ID,
                    headers = headers
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception extracting xHamster stream: ${e.message}", e)
        }

        // 2. YtDlp extraction
        if (context != null) {
            try {
                val ytdlResult = YtDlpResolver.extractStreamInfo(context, targetUrl)
                if (ytdlResult is YouTubeExtractorHelper.ExtractionResult.Success) {
                    return@withContext ytdlResult.streamData.copy(providerId = PROVIDER_ID)
                }
            } catch (e: Exception) {
                Log.w(TAG, "YtDlp extraction error for xHamster: ${e.message}")
            }
        }

        // 3. Fallback search resolver
        try {
            val cleanTitle = urlOrId.substringAfterLast("/").replace("-", " ").replace(Regex("""(?i)(?:xhamster|video|hd|4k|\d{5,})"""), "").trim()
            if (cleanTitle.isNotBlank()) {
                val searchResults = EpornerProvider.search(cleanTitle, page = 1, limit = 5)
                if (searchResults.isNotEmpty()) {
                    val streamData = EpornerProvider.getStreamData(searchResults.first().id, context)
                    if (streamData != null && streamData.availableStreamOptions.isNotEmpty()) {
                        return@withContext streamData.copy(
                            videoId = targetUrl,
                            videoUrl = targetUrl,
                            title = cleanTitle.replaceFirstChar { it.uppercase() },
                            channelName = "xHamster",
                            providerId = PROVIDER_ID,
                            headers = streamData.headers
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fallback resolver error: ${e.message}")
        }

        return@withContext null
    }
}
