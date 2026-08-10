package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class JavApiSearchProvider(
    private val http: HttpBridge = HttpBridge(),
    private val baseUrl: String = "https://javapi.onrender.com",
    private val apiKey: String = "my-secret-key"
) : ContentProviderApi {

    override val providerId: String = "javapi_search"

    private val defaultPopularCodes = listOf(
        "ABC-123", "CAWD-001", "SSIS-001", "IPX-100", "STSK-236",
        "HMDNV-949", "SIRO-5681", "MIAB-001", "JUL-001", "ADN-001"
    )

    override fun getProviderConfig(context: android.content.Context?): ProviderConfig {
        return ProviderConfig(
            id = providerId,
            name = "JavAPI Search Engine",
            enabled = true,
            endpoint = baseUrl,
            supportsDirectStreams = true,
            healthStatus = ProviderHealthStatus.READY
        )
    }

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val items = mutableListOf<PluginVideoItem>()

        val startIndex = ((page - 1) * 5) % defaultPopularCodes.size
        for (i in 0 until 5) {
            val codeIndex = (startIndex + i) % defaultPopularCodes.size
            val code = defaultPopularCodes[codeIndex]
            val item = queryJavApi(code)
            items.add(
                item ?: PluginVideoItem(
                    id = code,
                    title = "[$code] Aggregated JAV Search & Scrapers",
                    uploaderName = "JavDB + 8 Scrapers • javapi",
                    uploadDate = "★ 9.7 • 2026",
                    viewCount = 0L,
                    durationSeconds = 7200L,
                    thumbnailUrl = null,
                    providerId = providerId
                )
            )
        }

        PagedResult(items = items, nextPageToken = (page + 1).toString(), hasMore = true)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val cleanCode = query.trim().uppercase()
        val item = queryJavApi(cleanCode)

        val items = mutableListOf<PluginVideoItem>()
        items.add(
            item ?: PluginVideoItem(
                id = cleanCode,
                title = "[$cleanCode] Aggregated JAV Video",
                uploaderName = "JavDB + Scrapers",
                uploadDate = "★ 9.4 • 2026",
                viewCount = 0L,
                durationSeconds = 7200L,
                thumbnailUrl = null,
                providerId = providerId
            )
        )

        PagedResult(items = items, nextPageToken = (page + 1).toString(), hasMore = false)
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val code = extractCode(idOrUrl)
        queryJavApi(code) ?: PluginVideoItem(
            id = code,
            title = "[$code] Aggregated JAV Stream",
            uploaderName = "JavDB + 8 Scrapers",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val code = extractCode(idOrUrl)
        val url = "$baseUrl/api/v1/search?code=$code"
        val headers = mapOf("X-API-Key" to apiKey)

        val videoStreams = mutableListOf<PluginVideoStream>()
        var descriptionText = "Aggregated streams from JavDB & 8 video hosting scrapers"

        try {
            val resp = http.get(url, headers = headers)
            if (resp.statusCode == 200 && resp.body.isNotBlank()) {
                val json = JSONObject(resp.body)
                val movie = json.optJSONObject("movie")
                val summary = movie?.optString("summary")
                if (!summary.isNullOrBlank()) {
                    descriptionText = summary
                }

                val previewVideo = movie?.optString("preview_video_url")
                if (!previewVideo.isNullOrBlank()) {
                    videoStreams.add(
                        PluginVideoStream(
                            url = previewVideo,
                            qualityLabel = "Official Preview Trailer (MP4)",
                            format = "mp4",
                            isMuxed = true
                        )
                    )
                }

                val videosArr = json.optJSONArray("videos")
                if (videosArr != null && videosArr.length() > 0) {
                    for (i in 0 until videosArr.length()) {
                        val videoObj = videosArr.optJSONObject(i) ?: continue
                        val siteName = videoObj.optString("siteName", "Scraper #${i + 1}")
                        val status = videoObj.optString("status", "success")
                        if (status != "success") continue

                        val version = videoObj.optString("version", "original")
                        val label = videoObj.optString("label", siteName)
                        val pageUrl = videoObj.optString("pageUrl")

                        val sourcesArr = videoObj.optJSONArray("videoSources")
                        if (sourcesArr != null && sourcesArr.length() > 0) {
                            for (j in 0 until sourcesArr.length()) {
                                val sourceObj = sourcesArr.optJSONObject(j) ?: continue
                                val srcUrl = sourceObj.optString("url")
                                val type = sourceObj.optString("type", "video/mp4")
                                val format = if (type.contains("mpegURL") || srcUrl.contains(".m3u8")) "hls" else "mp4"

                                if (srcUrl.isNotBlank()) {
                                    videoStreams.add(
                                        PluginVideoStream(
                                            url = srcUrl,
                                            qualityLabel = "$siteName ($version - $label)",
                                            format = format,
                                            isMuxed = true
                                        )
                                    )
                                }
                            }
                        } else if (!pageUrl.isNullOrBlank()) {
                            videoStreams.add(
                                PluginVideoStream(
                                    url = pageUrl,
                                    qualityLabel = "$siteName Web Embed",
                                    format = "embed",
                                    isMuxed = true
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        // Standard fallback streams
        if (videoStreams.isEmpty()) {
            videoStreams.add(
                PluginVideoStream(
                    url = "https://server.apijav.com/?mvapm_embed=$code",
                    qualityLabel = "MISSAV / Server Embed",
                    format = "embed",
                    isMuxed = true
                )
            )
            videoStreams.add(
                PluginVideoStream(
                    url = "https://hayav.com/video/$code/",
                    qualityLabel = "HAYAV Chinese Sub Mirror",
                    format = "embed",
                    isMuxed = true
                )
            )
        }

        PluginStreamInfo(
            id = code,
            url = videoStreams.first().url,
            title = "[$code] Aggregated JAV Stream",
            channelName = "JavDB + 8 Scrapers",
            description = descriptionText,
            videoStreams = videoStreams
        )
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> = PagedResult(emptyList())

    private suspend fun queryJavApi(code: String): PluginVideoItem? {
        val url = "$baseUrl/api/v1/search?code=$code"
        val headers = mapOf("X-API-Key" to apiKey)

        return try {
            val resp = http.get(url, headers = headers)
            if (resp.statusCode != 200 || resp.body.isBlank()) return null

            val json = JSONObject(resp.body)
            val movie = json.optJSONObject("movie") ?: return null
            val number = movie.optString("number", code)
            val title = movie.optString("title", "[$number] Aggregated JAV Release")
            val coverUrl = movie.optString("cover_url")
            val thumbUrl = movie.optString("thumb_url", coverUrl)
            val duration = movie.optLong("duration", 120L)
            val score = movie.optDouble("score", 8.5)
            val maker = movie.optString("maker_name", "JAV")

            PluginVideoItem(
                id = number,
                title = title,
                uploaderName = "$number • $maker",
                uploadDate = "★ $score • JavDB + Scrapers",
                viewCount = 0L,
                durationSeconds = duration * 60L,
                thumbnailUrl = thumbUrl.ifBlank { coverUrl },
                providerId = providerId
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun extractCode(input: String): String =
        input.trim().uppercase().substringAfterLast("/").substringAfterLast("=").ifBlank { "ABC-123" }
}
