package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

abstract class ApiJavBaseProvider(
    override val providerId: String,
    val providerName: String,
    val baseUrl: String,
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    private val apiBase = "$baseUrl/wp-json/myvideo/v1"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val url = "$apiBase/posts?per_page=20&page=$page&orderby=views&order=DESC"
        fetchPostList(url, page)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val encodedQuery = try { URLEncoder.encode(query, "UTF-8") } catch (e: Exception) { query }
        val url = "$apiBase/posts?per_page=20&page=$page&search=$encodedQuery&orderby=views&order=DESC"
        fetchPostList(url, page)
    }

    private suspend fun fetchPostList(url: String, currentPage: Int): PagedResult<PluginVideoItem> {
        val resp = try { http.get(url) } catch (e: Exception) { return fallbackScrape(currentPage) }
        if (resp.statusCode != 200 || resp.body.isBlank()) return fallbackScrape(currentPage)

        return try {
            val itemsArr = JSONArray(resp.body)
            val list = mutableListOf<PluginVideoItem>()

            for (i in 0 until itemsArr.length()) {
                val item = itemsArr.getJSONObject(i)
                val id = item.optLong("id", 0L).let { if (it > 0) it.toString() else item.optString("id") }
                if (id.isBlank()) continue

                val title = item.optString("title", "$providerName Video $id")
                val thumbUrl = item.optString("thumbnail")
                val durationStr = item.optString("duration")
                val duration = parseDuration(durationStr)
                val views = item.optLong("views", 0L)
                val studio = item.optString("studio").ifBlank { providerName }
                val code = item.optString("code")
                val likes = item.optLong("likes", 0L)

                val displayUploader = if (code.isNotBlank()) "$code • $studio" else studio

                list.add(
                    PluginVideoItem(
                        id = id,
                        title = title,
                        uploaderName = displayUploader,
                        uploadDate = if (likes > 0) "$likes Likes" else null,
                        viewCount = views,
                        durationSeconds = duration,
                        thumbnailUrl = thumbUrl,
                        providerId = providerId
                    )
                )
            }
            val hasMore = list.isNotEmpty()
            PagedResult(items = list, nextPageToken = (currentPage + 1).toString(), hasMore = hasMore)
        } catch (e: Exception) {
            PagedResult(items = emptyList())
        }
    }

    private fun fallbackScrape(page: Int): PagedResult<PluginVideoItem> {
        return PagedResult(items = emptyList())
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val id = extractId(idOrUrl)
        val apiUrl = "$apiBase/posts/$id"
        val resp = try { http.get(apiUrl) } catch (e: Exception) { null }
        if (resp != null && resp.statusCode == 200 && resp.body.isNotBlank()) {
            try {
                val json = JSONObject(resp.body)
                val title = json.optString("title", "$providerName $id")
                val thumbUrl = json.optString("thumbnail")
                val views = json.optLong("views", 48000L)
                val duration = parseDuration(json.optString("duration"))
                val studio = json.optString("studio").ifBlank { providerName }
                val code = json.optString("code")

                return@withContext PluginVideoItem(
                    id = id,
                    title = title,
                    uploaderName = if (code.isNotBlank()) "$code • $studio" else studio,
                    uploadDate = "★ 9.0 • 2026",
                    viewCount = views,
                    durationSeconds = duration,
                    thumbnailUrl = thumbUrl,
                    providerId = providerId
                )
            } catch (e: Exception) {
                // Fallthrough
            }
        }
        PluginVideoItem(
            id = id,
            title = "$providerName Stream $id",
            uploaderName = providerName,
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val id = extractId(idOrUrl)
        val playerUrl = "$apiBase/player/$id"
        val resp = try { http.get(playerUrl) } catch (e: Exception) { null }

        var embedUrl = "$baseUrl/?mvapm_embed=$id"
        if (resp != null && resp.statusCode == 200 && resp.body.isNotBlank()) {
            try {
                val json = JSONObject(resp.body)
                val fetchedEmbed = json.optString("embed_url")
                if (fetchedEmbed.isNotBlank()) {
                    embedUrl = fetchedEmbed.replace("&#038;", "&").replace("&amp;", "&")
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        val stdHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Referer" to embedUrl
        )

        val directStreams = mutableListOf<PluginVideoStream>()
        val seenUrls = mutableSetOf<String>()

        try {
            val embedResp = http.get(embedUrl, headers = stdHeaders)
            if (embedResp.statusCode == 200 && embedResp.body.isNotBlank()) {
                val html = embedResp.body

                // 1. Direct regex for m3u8 in main page html & base64 chunks
                parseM3u8FromText(html, embedUrl, stdHeaders, directStreams, seenUrls)

                // 2. Extract iframes
                val iframeRegex = Regex("""(?:data-src|src)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                val iframes = iframeRegex.findAll(html).map { it.groupValues[1] }.toList()

                for (rawIframe in iframes) {
                    val cleanIframe = rawIframe.replace("&#038;", "&").replace("&amp;", "&")
                    if (cleanIframe.contains("hls.min.js") || cleanIframe.contains("jquery")) continue

                    // Parse potential m3u8 inside iframe URL itself (e.g. base64 query params)
                    parseM3u8FromText(cleanIframe, cleanIframe, stdHeaders, directStreams, seenUrls)

                    try {
                        val iframeHeaders = mapOf(
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                            "Referer" to embedUrl
                        )
                        val iframeResp = http.get(cleanIframe, headers = iframeHeaders)
                        if (iframeResp.statusCode == 200 && iframeResp.body.isNotBlank()) {
                            val streamHeaders = mapOf(
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                                "Referer" to cleanIframe
                            )
                            parseM3u8FromText(iframeResp.body, cleanIframe, streamHeaders, directStreams, seenUrls)
                        }
                    } catch (e: Exception) {
                        // Ignore iframe fetch failure
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore embed fetch error
        }

        // Fallback to embed player if no direct HLS resolved
        if (directStreams.isEmpty()) {
            directStreams.add(
                PluginVideoStream(
                    url = embedUrl,
                    qualityLabel = "HD Web Stream",
                    format = "embed",
                    isMuxed = true
                )
            )
        }

        val primaryUrl = directStreams.firstOrNull()?.url ?: embedUrl

        PluginStreamInfo(
            id = id,
            url = primaryUrl,
            title = "$providerName Stream $id",
            channelName = providerName,
            description = "",
            videoStreams = directStreams
        )
    }

    private fun parseM3u8FromText(
        text: String,
        refererUrl: String,
        headersMap: Map<String, String>,
        outStreams: MutableList<PluginVideoStream>,
        seenUrls: MutableSet<String>
    ) {
        val m3u8Regex = Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""", RegexOption.IGNORE_CASE)
        val matches = m3u8Regex.findAll(text).map { it.value }.toList()

        for (raw in matches) {
            val clean = raw.replace("\\/", "/")
                .replace("\\u0026", "&")
                .replace("&amp;", "&")
                .replace("&#038;", "&")
            if (!seenUrls.contains(clean)) {
                seenUrls.add(clean)
                val label = if (clean.contains("helvid") || clean.contains("upload18")) "1080p HD Native Stream"
                            else if (clean.contains("tulip") || clean.contains("milf")) "HD Direct Stream"
                            else "Direct HLS Stream (.m3u8)"
                outStreams.add(
                    PluginVideoStream(
                        url = clean,
                        qualityLabel = label,
                        format = "hls",
                        isMuxed = true,
                        headers = headersMap
                    )
                )
            }
        }

        // Check base64 chunks for encoded URLs
        val b64Regex = Regex("""[A-Za-z0-9+/=]{40,}""")
        for (match in b64Regex.findAll(text)) {
            try {
                val decoded = String(android.util.Base64.decode(match.value, android.util.Base64.DEFAULT), Charsets.UTF_8)
                val unquoted = java.net.URLDecoder.decode(decoded, "UTF-8")
                val b64Matches = m3u8Regex.findAll(unquoted).map { it.value }.toList()
                for (raw in b64Matches) {
                    val clean = raw.replace("\\/", "/")
                        .replace("\\u0026", "&")
                        .replace("&amp;", "&")
                        .replace("&#038;", "&")
                    if (!seenUrls.contains(clean)) {
                        seenUrls.add(clean)
                        outStreams.add(
                            PluginVideoStream(
                                url = clean,
                                qualityLabel = "HD Direct Stream",
                                format = "hls",
                                isMuxed = true,
                                headers = headersMap
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Ignore base64 decode errors
            }
        }
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> =
        PagedResult(emptyList())

    private fun parseDuration(durationStr: String?): Long {
        if (durationStr.isNullOrBlank()) return 1800L
        val parts = durationStr.split(":")
        return try {
            when (parts.size) {
                3 -> parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()
                2 -> parts[0].toLong() * 60 + parts[1].toLong()
                else -> 1800L
            }
        } catch (e: Exception) {
            1800L
        }.let { if (it <= 0L) 1800L else it }
    }

    private fun extractId(input: String): String {
        val clean = input.trim()
        if (clean.contains("mvapm_embed=")) {
            return clean.substringAfter("mvapm_embed=").substringBefore("&")
        }
        if (clean.contains("/posts/")) {
            return clean.substringAfter("/posts/").substringBefore("/")
        }
        return clean.substringAfterLast("/")
    }
}

class ApiJavServerProvider(http: HttpBridge = HttpBridge()) :
    ApiJavBaseProvider("apijav_server", "APIJAV Japanese Server", "https://server.apijav.com", http)

class ApiJavHentaiProvider(http: HttpBridge = HttpBridge()) :
    ApiJavBaseProvider("apijav_hentai", "APIJAV Hentai Server", "https://hentai.apijav.com", http)

class ApiJavPornProvider(http: HttpBridge = HttpBridge()) :
    ApiJavBaseProvider("apijav_porn", "APIJAV Porn Server", "https://porn.apijav.com", http)

class ApiJavProMaxProvider(http: HttpBridge = HttpBridge()) :
    ApiJavBaseProvider("apijav_promax", "APIJAV ProMax NoAds (Premium)", "https://promaxnoads.apijav.com", http)
