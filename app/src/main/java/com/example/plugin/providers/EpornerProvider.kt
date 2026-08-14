package com.example.plugin.providers

import android.util.Log
import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class EpornerProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "eporner"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val url = "https://www.eporner.com/api/v2/video/search/?per_page=20&page=$page&thumbsize=big&order=latest&format=json"
        fetchVideos(url, page)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val encodedQuery = try { URLEncoder.encode(query, "UTF-8") } catch (e: Exception) { query }
        val url = "https://www.eporner.com/api/v2/video/search/?query=$encodedQuery&per_page=20&page=$page&thumbsize=big&order=top-monthly&format=json"
        fetchVideos(url, page)
    }

    private suspend fun fetchVideos(url: String, currentPage: Int): PagedResult<PluginVideoItem> {
        val resp = http.get(url)
        if (resp.statusCode != 200) return PagedResult(emptyList())

        return try {
            val json = JSONObject(resp.body)
            val videosArr = json.optJSONArray("videos") ?: JSONArray()
            val list = mutableListOf<PluginVideoItem>()

            for (i in 0 until videosArr.length()) {
                val item = videosArr.getJSONObject(i)
                val id = item.optString("id")
                val title = item.optString("title", "Video $id")
                val thumbObj = item.optJSONObject("default_thumb")
                val thumbUrl = thumbObj?.optString("src") ?: item.optString("default_thumb")
                val duration = item.optLong("length_sec", 0L)
                val views = item.optLong("views", 0L)
                val keywords = item.optString("keywords", "")
                val uploader = parseUploaderFromKeywordsOrTitle(title, keywords)

                if (id.isNotBlank()) {
                    list.add(
                        PluginVideoItem(
                            id = id,
                            title = title,
                            uploaderName = uploader,
                            viewCount = views,
                            durationSeconds = duration,
                            thumbnailUrl = thumbUrl,
                            providerId = providerId
                        )
                    )
                }
            }
            val hasMore = list.isNotEmpty() && currentPage < json.optInt("total_pages", 100)
            PagedResult(items = list, nextPageToken = (currentPage + 1).toString(), hasMore = hasMore)
        } catch (e: Exception) {
            PagedResult(emptyList())
        }
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val id = extractId(idOrUrl)
        val url = "https://www.eporner.com/api/v2/video/id/?id=$id&format=json"
        val resp = http.get(url)
        if (resp.statusCode == 200) {
            try {
                val json = JSONObject(resp.body)
                val thumbObj = json.optJSONObject("default_thumb")
                val thumbUrl = thumbObj?.optString("src") ?: json.optString("default_thumb")
                val title = json.optString("title", "Eporner Video")
                val keywords = json.optString("keywords", "")
                val uploader = parseUploaderFromKeywordsOrTitle(title, keywords)
                return@withContext PluginVideoItem(
                    id = id,
                    title = title,
                    uploaderName = uploader,
                    viewCount = json.optLong("views", 0L),
                    durationSeconds = json.optLong("length_sec", 0L),
                    thumbnailUrl = thumbUrl,
                    providerId = providerId
                )
            } catch (e: Exception) {
                // Fallback
            }
        }
        PluginVideoItem(
            id = id,
            title = "Eporner Video $id",
            uploaderName = "Eporner Creator",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val id = extractId(idOrUrl)
        val candidateUrls = mutableListOf<Pair<String, String>>() // Pair(qualityLabel, url)
        var videoTitle = "Eporner Video $id"
        var uploaderName = "Eporner Creator"
        var metaKeywords = ""

        // 1. Fetch HTML page to locate download links and direct stream candidate URLs
        val pageUrlsToTry = listOf(
            "https://www.eporner.com/video-$id/",
            "https://www.eporner.com/hd-porn/$id/",
            "https://www.eporner.com/embed/$id/"
        )

        var pageHtml = ""
        for (pUrl in pageUrlsToTry) {
            try {
                val resp = http.get(pUrl, mapOf(
                    "User-Agent" to USER_AGENT,
                    "Accept-Language" to "en-US,en;q=0.9"
                ))
                if (resp.statusCode == 200 && resp.body.isNotBlank()) {
                    pageHtml = resp.body
                    break
                }
            } catch (e: Exception) {
                Log.w("EpornerProvider", "Failed fetching HTML from $pUrl: ${e.message}")
            }
        }

        if (pageHtml.isNotBlank()) {
            // Extract title if present
            val titleMatch = Regex("""<title>(.*?)</title>""", RegexOption.IGNORE_CASE).find(pageHtml)
            if (titleMatch != null) {
                val extractedTitle = titleMatch.groupValues[1].replace("- EPORNER", "").trim()
                if (extractedTitle.isNotBlank()) videoTitle = extractedTitle
            }

            // Extract real uploader/creator name from HTML
            val realUploader = extractUploaderNameFromHtml(pageHtml)
            if (realUploader.isNotBlank()) {
                uploaderName = realUploader
            }

            // Extract keywords
            val kwMatch = Regex("""<meta\s+name=["']keywords["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(pageHtml)
            if (kwMatch != null) {
                metaKeywords = kwMatch.groupValues[1]
            }

            // Extract explicit /dload/ hrefs from HTML
            val dloadRegex = Regex("""href=["'](/dload/[^"']+)["']""", RegexOption.IGNORE_CASE)
            dloadRegex.findAll(pageHtml).forEach { match ->
                val relUrl = match.groupValues[1]
                val fullUrl = if (relUrl.startsWith("http")) relUrl else "https://www.eporner.com$relUrl"
                val quality = when {
                    fullUrl.contains("1080") -> "1080p Full HD MP4"
                    fullUrl.contains("720") -> "720p HD MP4"
                    fullUrl.contains("480") -> "480p SD MP4"
                    fullUrl.contains("360") -> "360p Low MP4"
                    fullUrl.contains("240") -> "240p Low MP4"
                    else -> "Direct MP4"
                }
                candidateUrls.add(Pair(quality, fullUrl))
            }

            // Extract numeric video ID if dload links were missing
            if (candidateUrls.isEmpty()) {
                val numericIdMatch = Regex("""/(\d+)-(?:1080|720|480|360|240)p\.mp4""", RegexOption.IGNORE_CASE).find(pageHtml)
                    ?: Regex("""id=(\d+)""", RegexOption.IGNORE_CASE).find(pageHtml)
                    ?: Regex("""'video',\s*(\d+)""", RegexOption.IGNORE_CASE).find(pageHtml)

                val numericId = numericIdMatch?.groupValues?.get(1)
                if (numericId != null) {
                    val qualities = listOf("1080", "720", "480", "360", "240")
                    qualities.forEach { q ->
                        candidateUrls.add(Pair("${q}p MP4", "https://www.eporner.com/dload/$id/$q/$numericId-${q}p.mp4"))
                    }
                }
            }

            // Also check for direct MP4/HLS links in JS/schema
            val directMediaRegex = Regex("""(https?://[^"'\s]+\.(?:mp4|m3u8)[^"'\s]*)""", RegexOption.IGNORE_CASE)
            directMediaRegex.findAll(pageHtml).forEach { match ->
                val url = match.groupValues[1].replace("\\/", "/").replace("&amp;", "&")
                if (!url.contains("404.mp4") && !candidateUrls.any { it.second == url }) {
                    candidateUrls.add(Pair("Direct Stream MP4", url))
                }
            }
        }

        // 2. Fetch API v2 sources if candidates still empty
        if (candidateUrls.isEmpty()) {
            try {
                val apiUrl = "https://www.eporner.com/api/v2/video/id/?id=$id&format=json"
                val apiResp = http.get(apiUrl, mapOf("User-Agent" to USER_AGENT))
                if (apiResp.statusCode == 200) {
                    val json = JSONObject(apiResp.body)
                    if (videoTitle == "Eporner Video $id") {
                        videoTitle = json.optString("title", videoTitle)
                    }
                    val keywords = json.optString("keywords", "")
                    if (uploaderName == "Eporner Creator") {
                        uploaderName = parseUploaderFromKeywordsOrTitle(videoTitle, keywords)
                    }

                    val sourcesObj = json.optJSONObject("sources")
                    val mp4Obj = sourcesObj?.optJSONObject("mp4")
                    if (mp4Obj != null) {
                        val keys = mp4Obj.keys()
                        while (keys.hasNext()) {
                            val q = keys.next()
                            val item = mp4Obj.optJSONObject(q)
                            val src = item?.optString("src") ?: mp4Obj.optString(q)
                            if (src.isNotBlank()) {
                                candidateUrls.add(Pair("$q Direct MP4", src.replace("\\/", "/")))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("EpornerProvider", "API v2 fallback failed: ${e.message}")
            }
        }

        // 3. Validate each candidate URL with HEAD/GET probe and resolve redirects to genuine direct playable streams
        val validatedStreams = mutableListOf<PluginVideoStream>()
        val seenFinalUrls = mutableSetOf<String>()

        for ((qualityLabel, rawCandidateUrl) in candidateUrls) {
            val resolvedUrl = validateAndResolveUrl(rawCandidateUrl)
            if (resolvedUrl != null && !seenFinalUrls.contains(resolvedUrl)) {
                seenFinalUrls.add(resolvedUrl)
                val format = if (resolvedUrl.contains(".m3u8", ignoreCase = true)) "hls" else "mp4"
                validatedStreams.add(
                    PluginVideoStream(
                        url = resolvedUrl,
                        qualityLabel = qualityLabel,
                        format = format,
                        isMuxed = true
                    )
                )
            }
        }

        // 4. Fallback to YtDlpResolver if validatedStreams is still empty
        if (validatedStreams.isEmpty()) {
            val ctx = com.example.plugin.providers.ArchiveOrgProvider.contextRef
            if (ctx != null) {
                try {
                    val fullEpornerUrl = "https://www.eporner.com/video-$id/"
                    when (val ytRes = com.example.extractor.YtDlpResolver.extractStreamInfo(ctx, fullEpornerUrl)) {
                        is com.example.extractor.YtDlpResolver.ExtractionResult.Success -> {
                            for (opt in ytRes.playableOptions) {
                                val vUrl = opt.videoUrl ?: continue
                                if (!seenFinalUrls.contains(vUrl)) {
                                    seenFinalUrls.add(vUrl)
                                    validatedStreams.add(
                                        PluginVideoStream(
                                            url = vUrl,
                                            qualityLabel = opt.qualityLabel,
                                            format = opt.format,
                                            isMuxed = opt.isMuxed
                                        )
                                    )
                                }
                            }
                        }
                        else -> {}
                    }
                } catch (e: Exception) {
                    Log.w("EpornerProvider", "YtDlp fallback failed: ${e.message}")
                }
            }
        }

        if (validatedStreams.isEmpty()) {
            val embedUrl = "https://www.eporner.com/embed/$id/"
            validatedStreams.add(
                PluginVideoStream(
                    url = embedUrl,
                    qualityLabel = "Eporner Web Stream",
                    format = "embed",
                    isMuxed = true
                )
            )
        }

        val primaryStreamUrl = validatedStreams.firstOrNull()?.url ?: "https://www.eporner.com/embed/$id/"
        val highResThumb = "https://static.eporner.com/thumbs/$id/big.jpg"

        val tagsFormatted = if (metaKeywords.isNotBlank()) {
            metaKeywords.split(",").take(8).joinToString(" ") { "#${it.trim().replace(" ", "")}" }
        } else ""

        val descriptionText = buildString {
            append("Creator / Uploader: $uploaderName\n")
            if (tagsFormatted.isNotBlank()) append("Tags: $tagsFormatted\n\n")
            append("Stream quality extracted directly from Eporner High-Speed Network.")
        }

        PluginStreamInfo(
            id = id,
            url = primaryStreamUrl,
            title = videoTitle,
            channelName = uploaderName,
            channelAvatarUrl = "https://www.eporner.com/avatar/${uploaderName.lowercase().replace(" ", "")}.jpg",
            description = descriptionText,
            thumbnailUrl = highResThumb,
            videoStreams = validatedStreams
        )
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> = withContext(Dispatchers.IO) {
        val id = extractId(idOrUrl)
        val url = "https://www.eporner.com/video-$id/"
        val comments = mutableListOf<PluginComment>()

        try {
            val resp = http.get(url, mapOf("User-Agent" to USER_AGENT))
            if (resp.statusCode == 200 && resp.body.isNotBlank()) {
                val html = resp.body

                // Regex for Eporner comments block
                val commentPattern = Regex("""<div[^>]*class=["'][^"']*(?:comment-item|mb-comment|comm-item|comment_box)[^"']*["'][^>]*>(.*?)</div>\s*</div>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                val matches = commentPattern.findAll(html).toList()

                var index = 0
                for (match in matches) {
                    val block = match.groupValues[1]

                    val authorMatch = Regex("""<span[^>]*class=["'][^"']*comm_author[^"']*["'][^>]*>(.*?)</span>""", RegexOption.IGNORE_CASE).find(block)
                        ?: Regex("""<a[^>]*class=["'][^"']*(?:author|user)[^"']*["'][^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE).find(block)
                        ?: Regex("""class=["']comm_author["'][^>]*>(.*?)<""", RegexOption.IGNORE_CASE).find(block)

                    val textMatch = Regex("""<div[^>]*class=["'][^"']*comm_text[^"']*["'][^>]*>(.*?)</div>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(block)
                        ?: Regex("""<p[^>]*class=["'][^"']*comment-text[^"']*["'][^>]*>(.*?)</p>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(block)
                        ?: Regex("""class=["']commtext["'][^>]*>(.*?)<""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(block)

                    val timeMatch = Regex("""<span[^>]*class=["'][^"']*comm_date[^"']*["'][^>]*>(.*?)</span>""", RegexOption.IGNORE_CASE).find(block)

                    val author = authorMatch?.groupValues?.get(1)?.replace(Regex("<[^>]*>"), "")?.trim() ?: "User"
                    val text = textMatch?.groupValues?.get(1)?.replace(Regex("<[^>]*>"), "")?.trim() ?: ""
                    val timeText = timeMatch?.groupValues?.get(1)?.replace(Regex("<[^>]*>"), "")?.trim() ?: "Recently"

                    if (text.isNotBlank()) {
                        index++
                        comments.add(
                            PluginComment(
                                id = "ep_comm_${id}_$index",
                                authorName = author,
                                content = text,
                                publishedTime = timeText,
                                likeCount = 0L
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("EpornerProvider", "Failed parsing comments: ${e.message}")
        }

        PagedResult(comments)
    }

    override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> = withContext(Dispatchers.IO) {
        val id = extractId(idOrUrl)
        val url = "https://www.eporner.com/video-$id/"
        val relatedList = mutableListOf<PluginVideoItem>()

        try {
            val resp = http.get(url, mapOf("User-Agent" to USER_AGENT))
            if (resp.statusCode == 200 && resp.body.isNotBlank()) {
                val html = resp.body

                // Parse related video cards from HTML
                val itemRegex = Regex("""href=["']/(?:video|hd-porn)/([A-Za-z0-9]+)/?["'][^>]*title=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                val matches = itemRegex.findAll(html).toList()

                val seenIds = mutableSetOf<String>()
                seenIds.add(id)

                for (m in matches) {
                    val relId = m.groupValues[1]
                    val relTitle = m.groupValues[2].trim()

                    if (!seenIds.contains(relId) && relTitle.isNotBlank()) {
                        seenIds.add(relId)
                        val thumbUrl = "https://static.eporner.com/thumbs/$relId/big.jpg"
                        relatedList.add(
                            PluginVideoItem(
                                id = relId,
                                title = relTitle,
                                uploaderName = "Eporner Creator",
                                thumbnailUrl = thumbUrl,
                                durationSeconds = 0L,
                                viewCount = 0L,
                                providerId = providerId
                            )
                        )
                    }
                    if (relatedList.size >= 15) break
                }
            }
        } catch (e: Exception) {
            Log.w("EpornerProvider", "Failed getting recommendations: ${e.message}")
        }

        if (relatedList.isEmpty()) {
            // Fallback search by general top monthly
            val fallbackSearch = search("hd porn", pageToken = "1")
            return@withContext fallbackSearch.items.filter { it.id != id }.take(15)
        }

        relatedList
    }

    private fun extractUploaderNameFromHtml(html: String): String {
        val patterns = listOf(
            Regex("""<a[^>]+href=["']/uploader/([^"'/]+)/?["'][^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE),
            Regex("""<a[^>]+href=["']/profile/([^"'/]+)/?["'][^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE),
            Regex("""class=["']mv-uploader["'][^>]*><a[^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE),
            Regex("""uploader:\s*<a[^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE),
            Regex("""class=["']vid-uploader["'][^>]*>(.*?)</span>""", RegexOption.IGNORE_CASE),
            Regex("""["']author["']:\s*\{\s*["']@type["']:\s*["']Person["'],\s*["']name["']:\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            val match = pattern.find(html)
            if (match != null) {
                val rawName = match.groupValues.last().replace(Regex("<[^>]*>"), "").trim()
                if (rawName.isNotBlank() && !rawName.equals("eporner", ignoreCase = true) && !rawName.equals("home", ignoreCase = true)) {
                    return rawName
                }
            }
        }
        return ""
    }

    private fun parseUploaderFromKeywordsOrTitle(title: String, keywords: String): String {
        if (keywords.isNotBlank()) {
            val kwList = keywords.split(",").map { it.trim() }
            val studioKw = kwList.firstOrNull { kw ->
                kw.length in 3..25 && !kw.contains("hd") && !kw.contains("porn") && !kw.contains("video") && !kw.contains("sex")
            }
            if (studioKw != null) return studioKw.replaceFirstChar { it.uppercase() }
        }

        // Try extracting studio/channel name from title e.g. "Title - StudioName" or "StudioName - Title"
        if (title.contains("-")) {
            val parts = title.split("-").map { it.trim() }
            if (parts.size >= 2) {
                val lastPart = parts.last()
                if (lastPart.length in 3..20 && !lastPart.contains("1080p") && !lastPart.contains("4k")) {
                    return lastPart
                }
            }
        }
        return "Eporner Creator"
    }

    private suspend fun validateAndResolveUrl(candidateUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val headReq = Request.Builder()
                .url(candidateUrl)
                .header("User-Agent", USER_AGENT)
                .head()
                .build()

            var response = try {
                HttpBridge.sharedClient.newCall(headReq).execute()
            } catch (e: Exception) {
                null
            }

            // Retry with GET range request if HEAD failed or was non-200 / non-302
            if (response == null || (!response.isSuccessful && response.code != 302 && response.code != 301)) {
                val getReq = Request.Builder()
                    .url(candidateUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Range", "bytes=0-1024")
                    .get()
                    .build()
                response = try {
                    HttpBridge.sharedClient.newCall(getReq).execute()
                } catch (e: Exception) {
                    null
                }
            }

            if (response != null) {
                val finalUrl = response.request.url.toString()
                val code = response.code
                val contentType = response.header("Content-Type")?.lowercase() ?: ""
                response.close()

                val isSuccess = code in 200..299
                val isNotHtml = !contentType.contains("html")
                val isNot404 = !finalUrl.contains("404.mp4", ignoreCase = true)
                val isNotLogin = !finalUrl.contains("/login", ignoreCase = true)

                if (isSuccess && isNotHtml && isNot404 && isNotLogin) {
                    return@withContext finalUrl
                }
            }
        } catch (e: Exception) {
            Log.w("EpornerProvider", "Validation failed for candidate $candidateUrl: ${e.message}")
        }
        return@withContext null
    }

    private fun extractId(input: String): String {
        val clean = input.trim()
        val pattern = Regex("""(?:video-|embed/|hd-porn/|dload/)([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
        val match = pattern.find(clean)
        if (match != null) {
            return match.groupValues[1]
        }
        if (clean.contains("eporner.com/")) {
            val parts = clean.split("/")
            for (part in parts) {
                if (part.length in 8..15 && part.all { it.isLetterOrDigit() }) {
                    return part
                }
            }
        }
        return clean
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}

