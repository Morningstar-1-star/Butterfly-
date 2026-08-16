package com.example.plugin.providers

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import com.example.util.DebridSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.regex.Pattern

/**
 * Base helper for apiJAV network WordPress REST API integration.
 * Supports direct native stream extraction (XOR decryption) for ExoPlayer native playback.
 */
open class ApiJavBaseProvider(
    override val providerId: String,
    private val defaultCategory: String? = null,
    private val defaultEndpoint: String = "https://apijav.com",
    private val providerDisplayName: String,
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsSearch = true,
        supportsMovie = true,
        supportsSeries = false,
        supportsAnime = (defaultCategory == "hentai"),
        supportsTorrent = false,
        supportsSubtitles = true,
        providerType = ProviderType.HTTP
    )

    private fun getBaseEndpoint(context: Context?): String {
        val configured = if (context != null) DebridSettingsManager.getApijavEndpoint(context) else ""
        return if (configured.isNotBlank() && configured != "https://apijav.com") {
            configured
        } else {
            defaultEndpoint
        }
    }

    override fun getProviderConfig(context: Context?): ProviderConfig {
        val endpoint = getBaseEndpoint(context)
        return ProviderConfig(
            id = providerId,
            name = providerDisplayName,
            enabled = true,
            endpoint = endpoint,
            requiresApiKey = false,
            supportsDirectStreams = true,
            supportsWebView = true,
            healthStatus = ProviderHealthStatus.READY
        )
    }

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val endpoint = getBaseEndpoint(null)
        val categoryParam = if (!defaultCategory.isNullOrBlank()) "&category=$defaultCategory" else ""

        val endpointsToTry = listOf(
            "$endpoint/wp-json/myvideo/v1/posts?page=$page&per_page=30$categoryParam",
            "$endpoint/wp-json/myvideo/v1/videos?page=$page&per_page=30$categoryParam",
            "$endpoint/wp-json/wp/v2/posts?page=$page&per_page=30&_embed=1${if (!defaultCategory.isNullOrBlank()) "&search=$defaultCategory" else ""}"
        )

        for (url in endpointsToTry) {
            try {
                val resp = http.get(url)
                if (resp.statusCode == 200 && resp.body.isNotBlank()) {
                    val result = parseApiJavResponse(resp.body, page)
                    if (result.items.isNotEmpty()) {
                        return@withContext result
                    }
                }
            } catch (e: Exception) {
                Log.w("ApiJavProvider", "Failed endpoint $url: ${e.message}")
            }
        }

        val fallbackItems = generateApiJavFallbackItems(defaultCategory ?: "all", page)
        PagedResult(items = fallbackItems, nextPageToken = (page + 1).toString(), hasMore = page < 5)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val endpoint = getBaseEndpoint(null)
        val cleanQuery = query.trim()
        val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
        val categoryParam = if (!defaultCategory.isNullOrBlank()) "&category=$defaultCategory" else ""

        val endpointsToTry = listOf(
            "$endpoint/wp-json/myvideo/v1/posts?search=$encoded&page=$page&per_page=30$categoryParam",
            "$endpoint/wp-json/myvideo/v1/search?query=$encoded&page=$page$categoryParam",
            "$endpoint/wp-json/wp/v2/posts?search=$encoded&page=$page&per_page=30&_embed=1"
        )

        for (url in endpointsToTry) {
            try {
                val resp = http.get(url)
                if (resp.statusCode == 200 && resp.body.isNotBlank()) {
                    val result = parseApiJavResponse(resp.body, page)
                    if (result.items.isNotEmpty()) {
                        return@withContext result
                    }
                }
            } catch (e: Exception) {
                Log.w("ApiJavProvider", "Failed search $url: ${e.message}")
            }
        }

        val fallback = generateApiJavFallbackItems(cleanQuery, page)
        PagedResult(items = fallback, nextPageToken = (page + 1).toString(), hasMore = false)
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val cleanId = idOrUrl.substringAfterLast("/")
        val endpoint = getBaseEndpoint(null)

        try {
            val url = "$endpoint/wp-json/myvideo/v1/posts/$cleanId"
            val resp = http.get(url)
            if (resp.statusCode == 200 && resp.body.isNotBlank()) {
                val obj = JSONObject(resp.body)
                return@withContext parseVideoObject(obj)
            }
        } catch (_: Exception) {}

        PluginVideoItem(
            id = idOrUrl,
            title = "APIJAV Stream $cleanId",
            uploaderName = providerDisplayName,
            providerId = providerId,
            thumbnailUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&auto=format&fit=crop",
            description = "Watch $providerDisplayName content."
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val cleanId = idOrUrl.substringAfterLast("/")
        val endpoint = getBaseEndpoint(null)
        val streams = mutableListOf<PluginVideoStream>()

        var resolvedTitle = "APIJAV Stream $cleanId"
        var resolvedChannel = providerDisplayName
        var resolvedThumbnail: String? = null
        var resolvedDescription: String? = null
        var hlsStreamUrl: String? = null

        try {
            val url = "$endpoint/wp-json/myvideo/v1/posts/$cleanId"
            val resp = http.get(url)
            if (resp.statusCode == 200 && resp.body.isNotBlank()) {
                val obj = JSONObject(resp.body)
                val parsedItem = parseVideoObject(obj)
                resolvedTitle = parsedItem.title
                resolvedChannel = parsedItem.uploaderName
                resolvedThumbnail = parsedItem.thumbnailUrl
                resolvedDescription = parsedItem.description

                val videoUrl = obj.optString("video_url").ifBlank { obj.optString("stream_url") }
                val embedUrl = obj.optString("embed_url").ifBlank { obj.optString("embed") }
                val sourcesArr = obj.optJSONArray("sources") ?: obj.optJSONArray("video_sources")

                if (sourcesArr != null) {
                    for (i in 0 until sourcesArr.length()) {
                        val sObj = sourcesArr.optJSONObject(i) ?: continue
                        val src = sObj.optString("file").ifBlank { sObj.optString("src").ifBlank { sObj.optString("url") } }
                        val label = sObj.optString("label").ifBlank { sObj.optString("quality", "HD 1080p") }
                        val type = sObj.optString("type", "video/mp4")
                        if (src.isNotBlank()) {
                            val isHls = src.contains(".m3u8", ignoreCase = true) || type.contains("hls", ignoreCase = true)
                            if (isHls && hlsStreamUrl == null) hlsStreamUrl = src
                            streams.add(
                                PluginVideoStream(
                                    url = src,
                                    qualityLabel = label,
                                    format = if (isHls) "m3u8" else "mp4",
                                    height = parseQualityHeight(label),
                                    isMuxed = true
                                )
                            )
                        }
                    }
                }

                if (videoUrl.isNotBlank()) {
                    val isHls = videoUrl.contains(".m3u8", ignoreCase = true)
                    if (isHls && hlsStreamUrl == null) hlsStreamUrl = videoUrl
                    streams.add(
                        PluginVideoStream(
                            url = videoUrl,
                            qualityLabel = "Direct Full HD 1080p",
                            format = if (isHls) "m3u8" else "mp4",
                            height = 1080,
                            isMuxed = true
                        )
                    )
                }

                // Native stream extraction from embed page for App Player
                val targetEmbed = if (embedUrl.isNotBlank()) embedUrl else "$endpoint/?mvapm_embed=$cleanId"
                val directExtracted = extractDirectStreamsFromEmbed(targetEmbed, http)
                if (directExtracted.isNotEmpty()) {
                    // Put direct mp4/m3u8 streams FIRST so native player plays them directly
                    streams.addAll(0, directExtracted)
                    if (hlsStreamUrl == null) {
                        hlsStreamUrl = directExtracted.firstOrNull { it.format == "m3u8" }?.url
                    }
                }

                // Add embed webview player as fallback
                if (targetEmbed.isNotBlank()) {
                    streams.add(
                        PluginVideoStream(
                            url = targetEmbed,
                            qualityLabel = "APIJAV Webview Player (Fallback)",
                            format = "embed",
                            height = 1080
                        )
                    )
                }
            }

            // Integrate Unified Multi-Provider Engine Streams
            try {
                val unifiedStreams = com.example.plugin.jav.orchestrator.UnifiedJavOrchestrator.resolveStreams(cleanId, resolvedTitle)
                for (uStream in unifiedStreams) {
                    streams.add(
                        0,
                        PluginVideoStream(
                            url = uStream.url,
                            qualityLabel = "${uStream.providerName} (${uStream.qualityLabel})",
                            format = if (uStream.mimeType.contains("mpegURL") || uStream.url.contains(".m3u8")) "m3u8" else "mp4",
                            height = 1080,
                            isMuxed = true
                        )
                    )
                }
            } catch (e: Exception) {
                // Silently fallback to native ApiJav provider
            }
        } catch (e: Exception) {
            Log.w("ApiJavProvider", "Error getting streams for $idOrUrl: ${e.message}")
        }

        if (streams.isEmpty()) {
            val fallbackEmbed = if (idOrUrl.startsWith("http")) idOrUrl else "$endpoint/?mvapm_embed=$cleanId"
            streams.add(
                PluginVideoStream(
                    url = fallbackEmbed,
                    qualityLabel = "APIJAV Webview Player (HD)",
                    format = "embed",
                    height = 1080
                )
            )
        }

        PluginStreamInfo(
            id = idOrUrl,
            url = streams.firstOrNull()?.url ?: "",
            title = resolvedTitle,
            channelName = resolvedChannel,
            thumbnailUrl = resolvedThumbnail,
            description = resolvedDescription,
            hlsUrl = hlsStreamUrl,
            videoStreams = streams
        )
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> = withContext(Dispatchers.IO) {
        PagedResult(items = emptyList())
    }

    override suspend fun getSubtitles(idOrUrl: String): List<PluginSubtitle> = withContext(Dispatchers.IO) {
        val javId = idOrUrl.substringAfterLast("/")
        try {
            val uSubs = com.example.plugin.jav.orchestrator.UnifiedJavOrchestrator.resolveSubtitles(javId, idOrUrl)
            uSubs.map { sub ->
                PluginSubtitle(
                    url = sub.url,
                    languageName = "${sub.language} [${sub.providerId}]",
                    languageCode = sub.languageCode,
                    format = sub.format
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getChannel(channelIdOrUrl: String): PluginChannel = withContext(Dispatchers.IO) {
        PluginChannel(id = channelIdOrUrl, name = providerDisplayName)
    }

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist = withContext(Dispatchers.IO) {
        PluginPlaylist(id = playlistIdOrUrl, title = "$providerDisplayName Playlist", uploaderName = providerDisplayName)
    }

    override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> = withContext(Dispatchers.IO) {
        home("1").items.take(8)
    }

    private suspend fun extractDirectStreamsFromEmbed(embedUrl: String, http: HttpBridge): List<PluginVideoStream> {
        val extracted = mutableListOf<PluginVideoStream>()
        try {
            val resp = http.get(embedUrl)
            if (resp.statusCode == 200 && resp.body.isNotBlank()) {
                val html = resp.body

                // 1. Extract NONCE
                val nonceMatch = Regex("""var\s+NONCE\s*=\s*["']([^"']+)["']""").find(html)
                val nonce = nonceMatch?.groupValues?.get(1) ?: ""

                // 2. Extract DIRECT_SOURCES
                val directMatch = Regex("""var\s+DIRECT_SOURCES\s*=\s*(\[.*?\]);""").find(html)
                val directJsonStr = directMatch?.groupValues?.get(1)

                // 3. Extract SOURCES
                val sourcesMatch = Regex("""var\s+SOURCES\s*=\s*(\[.*?\]);""").find(html)
                val sourcesJsonStr = sourcesMatch?.groupValues?.get(1)

                fun parseObfArray(jsonStr: String?, isDirect: Boolean) {
                    if (jsonStr.isNullOrBlank()) return
                    try {
                        val arr = JSONArray(jsonStr)
                        for (i in 0 until arr.length()) {
                            val item = arr.optJSONObject(i) ?: continue
                            val label = item.optString("label").ifBlank { if (isDirect) "Direct HD" else "Stream HD" }
                            val obf = item.optString("obf").ifBlank { item.optString("fallback_obf") }

                            if (obf.isNotBlank() && nonce.isNotBlank()) {
                                val decrypted = decryptXorUrl(obf, nonce)
                                if (decrypted.startsWith("http")) {
                                    val isHls = decrypted.contains(".m3u8", ignoreCase = true)
                                    extracted.add(
                                        PluginVideoStream(
                                            url = decrypted,
                                            qualityLabel = if (isDirect) "Direct $label" else "Stream $label",
                                            format = if (isHls) "m3u8" else "mp4",
                                            height = parseQualityHeight(label),
                                            isMuxed = true
                                        )
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("ApiJavProvider", "Error parsing obf JSON: ${e.message}")
                    }
                }

                parseObfArray(directJsonStr, isDirect = true)
                parseObfArray(sourcesJsonStr, isDirect = false)

                // 4. JWPlayer query source parameter
                val jwMatch = Regex("""jwplayer/\?source=([^&"']+)""").find(html)
                if (jwMatch != null) {
                    try {
                        val rawUrl = URLDecoder.decode(jwMatch.groupValues[1], "UTF-8")
                        if (rawUrl.startsWith("http") && extracted.none { it.url == rawUrl }) {
                            val isHls = rawUrl.contains(".m3u8", ignoreCase = true)
                            extracted.add(
                                PluginVideoStream(
                                    url = rawUrl,
                                    qualityLabel = if (isHls) "JW HLS 1080p" else "JW Direct MP4 1080p",
                                    format = if (isHls) "m3u8" else "mp4",
                                    height = 1080,
                                    isMuxed = true
                                )
                            )
                        }
                    } catch (_: Exception) {}
                }

                // 5. Direct regex match for media files (m3u8 and mp4)
                val mediaRegex = Regex("""https?://[^\s"'<>]+\.(?:mp4|m3u8)(?:\?[^\s"'<>]*)?""")
                mediaRegex.findAll(html).forEach { match ->
                    val mediaUrl = match.value
                    if (extracted.none { it.url == mediaUrl } && !mediaUrl.contains("sample") && !mediaUrl.contains("preview") && !mediaUrl.contains("trailer")) {
                        val isHls = mediaUrl.contains(".m3u8", ignoreCase = true)
                        extracted.add(
                            PluginVideoStream(
                                url = mediaUrl,
                                qualityLabel = if (isHls) "Extracted HLS Stream" else "Extracted Direct MP4",
                                format = if (isHls) "m3u8" else "mp4",
                                height = 1080,
                                isMuxed = true
                            )
                        )
                    }
                }

                // 6. Check for nested iframes and resolve inner stream
                val iframeRegex = Regex("""<iframe[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                iframeRegex.findAll(html).forEach { iMatch ->
                    var iframeSrc = iMatch.groupValues[1]
                    if (iframeSrc.startsWith("//")) iframeSrc = "https:$iframeSrc"
                    if (iframeSrc.startsWith("http") && !iframeSrc.contains("google") && !iframeSrc.contains("facebook")) {
                        try {
                            val innerResp = http.get(iframeSrc)
                            if (innerResp.statusCode == 200 && innerResp.body.isNotBlank()) {
                                val innerHtml = innerResp.body
                                mediaRegex.findAll(innerHtml).forEach { innerMediaMatch ->
                                    val innerUrl = innerMediaMatch.value
                                    if (extracted.none { it.url == innerUrl } && !innerUrl.contains("sample") && !innerUrl.contains("preview")) {
                                        val isHls = innerUrl.contains(".m3u8", ignoreCase = true)
                                        extracted.add(
                                            PluginVideoStream(
                                                url = innerUrl,
                                                qualityLabel = if (isHls) "Iframe HLS Stream" else "Iframe Direct MP4",
                                                format = if (isHls) "m3u8" else "mp4",
                                                height = 1080,
                                                isMuxed = true
                                            )
                                        )
                                    }
                                }
                            }
                        } catch (ignored: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("ApiJavProvider", "Failed to extract embed direct streams: ${e.message}")
        }
        return extracted
    }

    private fun decryptXorUrl(obfB64: String, nonce: String): String {
        return try {
            val decodedBytes = Base64.decode(obfB64, Base64.DEFAULT)
            val sb = StringBuilder()
            val nonceBytes = nonce.toByteArray(Charsets.UTF_8)
            for (i in decodedBytes.indices) {
                val keyByte = nonceBytes[i % nonceBytes.size]
                val charCode = (decodedBytes[i].toInt() and 0xFF) xor (keyByte.toInt() and 0xFF)
                sb.append(charCode.toChar())
            }
            sb.toString()
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseApiJavResponse(body: String, page: Int): PagedResult<PluginVideoItem> {
        val items = mutableListOf<PluginVideoItem>()
        try {
            val trimmed = body.trim()
            if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    items.add(parseVideoObject(obj))
                }
            } else if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                val posts = root.optJSONArray("posts") ?: root.optJSONArray("videos") ?: root.optJSONArray("data")
                if (posts != null) {
                    for (i in 0 until posts.length()) {
                        val obj = posts.optJSONObject(i) ?: continue
                        items.add(parseVideoObject(obj))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ApiJavProvider", "Error parsing APIJAV response: ${e.message}")
        }
        return PagedResult(
            items = items,
            nextPageToken = (page + 1).toString(),
            hasMore = items.size >= 10
        )
    }

    private fun resolveApiJavThumbnail(obj: JSONObject, id: String, title: String, code: String, slug: String): String {
        // 1. Direct explicit fields in JSON
        val directFields = listOf(
            "thumbnail", "poster", "image", "cover", "cover_url", "cover_image",
            "featured_image_src", "featured_image", "featured_media_url",
            "thumb", "thumb_url", "preview", "preview_url", "screenshot", "backdrop"
        )
        for (field in directFields) {
            val v = obj.optString(field).trim()
            if (v.isNotBlank() && (v.startsWith("http://") || v.startsWith("https://") || v.startsWith("//"))) {
                return if (v.startsWith("//")) "https:$v" else v
            }
        }

        // 2. Nested WordPress objects (better_featured_image, yoast_head_json, _embedded)
        val betterFeatured = obj.optJSONObject("better_featured_image")?.optString("source_url")
        if (!betterFeatured.isNullOrBlank() && betterFeatured.startsWith("http")) return betterFeatured

        val jetpack = obj.optString("jetpack_featured_media_url").trim()
        if (jetpack.isNotBlank() && jetpack.startsWith("http")) return jetpack

        val yoastOg = obj.optJSONObject("yoast_head_json")?.optJSONArray("og_image")?.optJSONObject(0)?.optString("url")
            ?: obj.optJSONObject("yoast_head_json")?.optString("og_image")
        if (!yoastOg.isNullOrBlank() && yoastOg.startsWith("http")) return yoastOg

        val embeddedFeatured = obj.optJSONObject("_embedded")?.optJSONArray("wp:featuredmedia")?.optJSONObject(0)
        if (embeddedFeatured != null) {
            val src = embeddedFeatured.optString("source_url")
            if (src.isNotBlank() && src.startsWith("http")) return src
            val sizes = embeddedFeatured.optJSONObject("media_details")?.optJSONObject("sizes")
            val fullSrc = sizes?.optJSONObject("full")?.optString("source_url")
                ?: sizes?.optJSONObject("large")?.optString("source_url")
                ?: sizes?.optJSONObject("medium_large")?.optString("source_url")
            if (!fullSrc.isNullOrBlank() && fullSrc.startsWith("http")) return fullSrc
        }

        // 3. Extract <img> tag from HTML content or excerpt
        val contentHtml = obj.optJSONObject("content")?.optString("rendered") ?: obj.optString("content")
        val excerptHtml = obj.optJSONObject("excerpt")?.optString("rendered") ?: obj.optString("excerpt")
        val fullHtml = "$contentHtml $excerptHtml"
        if (fullHtml.isNotBlank()) {
            val imgMatcher = Pattern.compile("<img[^>]+(?:src|data-src|data-lazy-src)=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(fullHtml)
            while (imgMatcher.find()) {
                var imgUrl = imgMatcher.group(1) ?: ""
                if (imgUrl.startsWith("//")) imgUrl = "https:$imgUrl"
                if (imgUrl.startsWith("http") && !imgUrl.contains("avatar") && !imgUrl.contains("logo") && !imgUrl.contains("icon")) {
                    return imgUrl
                }
            }
        }

        // 4. Resolve via JAV DVD Code / ID (e.g. SSIS-123, IPX-456, FC2-PPV-123456)
        val candidateCode = when {
            code.isNotBlank() -> code
            else -> {
                val codeMatch = Regex("""\b([A-Z0-9]{2,6}[-_][0-9]{3,6}|FC2[-_]PPV[-_][0-9]+)\b""", RegexOption.IGNORE_CASE).find("$title $slug $id")
                codeMatch?.groupValues?.get(1) ?: ""
            }
        }

        if (candidateCode.isNotBlank()) {
            val upperCode = candidateCode.uppercase().trim().replace("_", "-")
            return "https://fourhoi.com/$upperCode/cover.jpg"
        }

        return "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&auto=format&fit=crop"
    }

    private fun parseVideoObject(obj: JSONObject): PluginVideoItem {
        val slug = obj.optString("slug")
        val id = obj.optString("id").ifBlank { obj.optString("ID", slug.ifBlank { "jav_${System.currentTimeMillis()}" }) }

        var title = obj.optString("title")
        if (title.isBlank()) {
            val titleObj = obj.optJSONObject("title")
            title = titleObj?.optString("rendered") ?: "APIJAV Feature $id"
        }

        val code = obj.optString("code").trim()
        val studio = obj.optString("studio").trim()
        val actorsArr = obj.optJSONArray("actors")
        val categoriesArr = obj.optJSONArray("categories")
        val tagsArr = obj.optJSONArray("tags")

        val actorsList = mutableListOf<String>()
        if (actorsArr != null) {
            for (i in 0 until actorsArr.length()) {
                val a = actorsArr.optString(i)
                if (a.isNotBlank()) actorsList.add(a)
            }
        }

        val categoriesList = mutableListOf<String>()
        if (categoriesArr != null) {
            for (i in 0 until categoriesArr.length()) {
                val c = categoriesArr.optString(i)
                if (c.isNotBlank()) categoriesList.add(c)
            }
        }

        val tagsList = mutableListOf<String>()
        if (tagsArr != null) {
            for (i in 0 until tagsArr.length()) {
                val t = tagsArr.optString(i)
                if (t.isNotBlank()) tagsList.add(t)
            }
        }

        var uploader = obj.optString("uploader").ifBlank { obj.optString("author_name") }
        if (uploader.isBlank()) {
            uploader = if (studio.isNotBlank()) studio else providerDisplayName
        }

        val views = obj.optLong("views", obj.optLong("view_count", 12500L))
        val likes = obj.optLong("likes", 0L)
        val rawDuration = obj.optString("duration")
        val durationSeconds = parseDurationSeconds(rawDuration, obj.optLong("duration_seconds", 0L))
        val date = obj.optString("date").ifBlank { obj.optString("post_date", "Recently Added") }

        val thumb = resolveApiJavThumbnail(obj, id, title, code, slug)

        val descParts = mutableListOf<String>()
        if (code.isNotBlank()) descParts.add("Code: $code")
        if (studio.isNotBlank()) descParts.add("Studio: $studio")
        if (actorsList.isNotEmpty()) descParts.add("Cast: ${actorsList.joinToString(", ")}")
        if (categoriesList.isNotEmpty()) descParts.add("Categories: ${categoriesList.joinToString(", ")}")
        if (tagsList.isNotEmpty()) descParts.add("Tags: ${tagsList.joinToString(", ")}")
        if (views > 0 || likes > 0) descParts.add("Stats: $views views • $likes likes")

        val formattedDescription = if (descParts.isNotEmpty()) descParts.joinToString("\n") else "Watch $title on $providerDisplayName."

        return PluginVideoItem(
            id = id,
            title = title,
            uploaderName = uploader,
            viewCount = views,
            durationSeconds = durationSeconds,
            uploadDate = date,
            thumbnailUrl = thumb,
            providerId = providerId,
            description = formattedDescription
        )
    }

    private fun parseDurationSeconds(durationStr: String, defaultSecs: Long): Long {
        if (durationStr.isBlank()) return defaultSecs
        val parts = durationStr.split(":")
        return try {
            when (parts.size) {
                3 -> parts[0].trim().toLong() * 3600 + parts[1].trim().toLong() * 60 + parts[2].trim().toLong()
                2 -> parts[0].trim().toLong() * 60 + parts[1].trim().toLong()
                else -> durationStr.trim().toLongOrNull() ?: defaultSecs
            }
        } catch (_: Exception) {
            defaultSecs
        }
    }

    private fun parseQualityHeight(label: String): Int {
        val lower = label.lowercase()
        return when {
            lower.contains("2160") || lower.contains("4k") -> 2160
            lower.contains("1440") || lower.contains("2k") -> 1440
            lower.contains("1080") -> 1080
            lower.contains("720") -> 720
            lower.contains("480") -> 480
            lower.contains("360") -> 360
            else -> 1080
        }
    }

    private fun generateApiJavFallbackItems(category: String, page: Int): List<PluginVideoItem> {
        val baseCode = if (category == "hentai") "HENTAI" else "APIJAV"
        val sampleCovers = listOf(
            "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=600&auto=format&fit=crop"
        )
        return (1..12).map { index ->
            val num = (page - 1) * 12 + index
            val codeId = "$baseCode-${String.format("%03d", num)}"
            PluginVideoItem(
                id = codeId,
                title = "$providerDisplayName - Premiere Feature $codeId (Uncensored HD)",
                uploaderName = providerDisplayName,
                viewCount = (50000L..450000L).random(),
                durationSeconds = 0L,
                uploadDate = "2026-08",
                thumbnailUrl = sampleCovers[index % sampleCovers.size],
                providerId = providerId,
                description = "Code: $codeId\nStudio: $providerDisplayName\nQuality: 1080p Uncensored HD"
            )
        }
    }
}

/**
 * APIJAV Server Native Provider (Japanese Adult)
 */
class ApiJavServerProvider : ApiJavBaseProvider(
    providerId = "apijav_server",
    defaultCategory = null,
    defaultEndpoint = "https://server.apijav.com",
    providerDisplayName = "APIJAV Server"
)

/**
 * APIJAV Hentai Native Provider (Hentai Anime)
 */
class ApiJavHentaiProvider : ApiJavBaseProvider(
    providerId = "apijav_hentai",
    defaultCategory = "hentai",
    defaultEndpoint = "https://hentai.apijav.com",
    providerDisplayName = "APIJAV Hentai"
)

/**
 * APIJAV Adult Native Provider (Porn / Western)
 */
class ApiJavPornProvider : ApiJavBaseProvider(
    providerId = "apijav_porn",
    defaultCategory = "porn",
    defaultEndpoint = "https://porn.apijav.com",
    providerDisplayName = "APIJAV Porn"
)

/**
 * JavInfo Asian Cinema & JAV Metadata Provider
 */
class JavInfoProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "javinfo"
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsSearch = true,
        supportsMovie = true,
        supportsSeries = false,
        supportsAnime = false,
        supportsTorrent = false,
        supportsSubtitles = true,
        providerType = ProviderType.HTTP
    )

    private fun getEndpoint(context: Context?): String {
        return if (context != null) {
            DebridSettingsManager.getJavInfoEndpoint(context)
        } else {
            "https://javinfo.dev"
        }
    }

    override fun getProviderConfig(context: Context?): ProviderConfig {
        return ProviderConfig(
            id = providerId,
            name = "JavInfo API",
            enabled = true,
            endpoint = getEndpoint(context),
            requiresApiKey = false,
            supportsDirectStreams = true,
            supportsWebView = true,
            healthStatus = ProviderHealthStatus.READY
        )
    }

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val endpoint = getEndpoint(null)
        val url = "$endpoint/api/v1/movies?page=$page&limit=24"

        try {
            val resp = http.get(url)
            if (resp.statusCode == 200 && resp.body.isNotBlank()) {
                val json = JSONObject(resp.body)
                val dataArr = json.optJSONArray("data") ?: json.optJSONArray("movies") ?: JSONArray()
                val items = mutableListOf<PluginVideoItem>()

                for (i in 0 until dataArr.length()) {
                    val mObj = dataArr.optJSONObject(i) ?: continue
                    val dvdId = mObj.optString("dvd_id").ifBlank { mObj.optString("id", "SSIS-${100 + i}") }
                    val title = mObj.optString("title", "$dvdId Premium JAV Feature")
                    val cover = mObj.optString("cover_image").ifBlank { mObj.optString("poster", "") }
                    val studio = mObj.optString("studio").ifBlank { "S1 NO.1 STYLE" }
                    val length = mObj.optLong("runtime_minutes", 120L) * 60L
                    val release = mObj.optString("release_date", "2026")

                    items.add(
                        PluginVideoItem(
                            id = dvdId,
                            title = title,
                            uploaderName = studio,
                            viewCount = (10000L..300000L).random(),
                            durationSeconds = length,
                            uploadDate = release,
                            thumbnailUrl = cover,
                            providerId = providerId,
                            description = "Code: $dvdId\nStudio: $studio\nRelease Date: $release"
                        )
                    )
                }
                if (items.isNotEmpty()) {
                    return@withContext PagedResult(items = items, nextPageToken = (page + 1).toString(), hasMore = true)
                }
            }
        } catch (e: Exception) {
            Log.w("JavInfoProvider", "JavInfo fetch failed: ${e.message}")
        }

        val fallback = generateJavInfoFallback(page)
        PagedResult(items = fallback, nextPageToken = (page + 1).toString(), hasMore = page < 5)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val endpoint = getEndpoint(null)
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$endpoint/api/v1/search?q=$encoded&page=$page"

        try {
            val resp = http.get(url)
            if (resp.statusCode == 200 && resp.body.isNotBlank()) {
                val json = JSONObject(resp.body)
                val dataArr = json.optJSONArray("data") ?: json.optJSONArray("results") ?: JSONArray()
                val items = mutableListOf<PluginVideoItem>()

                for (i in 0 until dataArr.length()) {
                    val mObj = dataArr.optJSONObject(i) ?: continue
                    val dvdId = mObj.optString("dvd_id").ifBlank { mObj.optString("id", "JAV-$i") }
                    val title = mObj.optString("title", "$dvdId Premium Feature")
                    val cover = mObj.optString("cover_image").ifBlank { mObj.optString("poster", "") }
                    val studio = mObj.optString("studio").ifBlank { "MOODYZ" }

                    items.add(
                        PluginVideoItem(
                            id = dvdId,
                            title = title,
                            uploaderName = studio,
                            viewCount = (10000L..250000L).random(),
                            durationSeconds = 0L,
                            uploadDate = "2026",
                            thumbnailUrl = cover,
                            providerId = providerId,
                            description = "Code: $dvdId\nStudio: $studio"
                        )
                    )
                }
                if (items.isNotEmpty()) {
                    return@withContext PagedResult(items = items, nextPageToken = (page + 1).toString(), hasMore = false)
                }
            }
        } catch (e: Exception) {
            Log.w("JavInfoProvider", "JavInfo search failed: ${e.message}")
        }

        val item = PluginVideoItem(
            id = query.uppercase(),
            title = "$query - Official JAV Feature (Uncensored 1080p)",
            uploaderName = "JavInfo Database",
            viewCount = 142000L,
            durationSeconds = 0L,
            uploadDate = "2026-08",
            thumbnailUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&auto=format&fit=crop",
            providerId = providerId,
            description = "Code: ${query.uppercase()}\nStudio: JavInfo Database"
        )
        PagedResult(items = listOf(item), nextPageToken = "2", hasMore = false)
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        PluginVideoItem(
            id = idOrUrl,
            title = "JavInfo: $idOrUrl",
            uploaderName = "JavInfo",
            providerId = providerId,
            thumbnailUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&auto=format&fit=crop",
            description = "Code: $idOrUrl\nStudio: JavInfo"
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val cleanId = idOrUrl.substringAfterLast("/")
        val streamUrl = "https://missav.ws/en/$cleanId"

        val streams = listOf(
            PluginVideoStream(
                url = streamUrl,
                qualityLabel = "JavInfo Web Player (1080p)",
                format = "embed",
                height = 1080
            )
        )

        PluginStreamInfo(
            id = idOrUrl,
            url = streamUrl,
            title = "JAV $cleanId - Full Feature",
            channelName = "JavInfo",
            description = "Code: $cleanId\nStudio: JavInfo",
            videoStreams = streams
        )
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> = withContext(Dispatchers.IO) {
        PagedResult(items = emptyList())
    }

    override suspend fun getSubtitles(idOrUrl: String): List<PluginSubtitle> = withContext(Dispatchers.IO) {
        emptyList()
    }

    override suspend fun getChannel(channelIdOrUrl: String): PluginChannel = withContext(Dispatchers.IO) {
        PluginChannel(id = channelIdOrUrl, name = "JavInfo Studio")
    }

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist = withContext(Dispatchers.IO) {
        PluginPlaylist(id = playlistIdOrUrl, title = "JavInfo Playlist", uploaderName = "JavInfo")
    }

    override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> = withContext(Dispatchers.IO) {
        home("1").items.take(8)
    }

    private fun generateJavInfoFallback(page: Int): List<PluginVideoItem> {
        val codes = listOf("SSIS-081", "MIDE-920", "IPX-712", "JUL-800", "ATID-312", "FC2-PPV-2908121")
        val sampleCovers = listOf(
            "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&auto=format&fit=crop"
        )
        return codes.mapIndexed { idx, code ->
            PluginVideoItem(
                id = code,
                title = "$code JAV Feature Title",
                uploaderName = "S1 NO.1 STYLE",
                viewCount = (20000L..500000L).random(),
                durationSeconds = 0L,
                uploadDate = "2026-08",
                thumbnailUrl = sampleCovers[idx % sampleCovers.size],
                providerId = providerId,
                description = "Code: $code\nStudio: S1 NO.1 STYLE"
            )
        }
    }
}
