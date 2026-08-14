package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class JavInfoFanzaProvider(
    private val http: HttpBridge = HttpBridge(),
    private val apiKey: String = "public_demo"
) : ContentProviderApi {

    override val providerId: String = "javinfo_fanza"
    private val apiBase = "https://api.javinfo.dev/movie"

    override fun getProviderConfig(context: android.content.Context?): ProviderConfig {
        return ProviderConfig(
            id = providerId,
            name = "FANZA / JavInfo Provider",
            enabled = true,
            endpoint = apiBase,
            supportsDirectStreams = true,
            healthStatus = ProviderHealthStatus.READY
        )
    }

    private val defaultPopularCodes = listOf(
        "SSIS-001", "CAWD-001", "IPX-100", "STSK-236", "HMDNV-949",
        "SIRO-5681", "MIAB-001", "JUL-001", "ADN-001", "MIDE-001"
    )

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val items = mutableListOf<PluginVideoItem>()

        val startIndex = ((page - 1) * 5) % defaultPopularCodes.size
        for (i in 0 until 5) {
            val codeIndex = (startIndex + i) % defaultPopularCodes.size
            val code = defaultPopularCodes[codeIndex]
            val item = queryFanza(code)
            items.add(
                item ?: PluginVideoItem(
                    id = code,
                    title = "[$code] FANZA High Definition JAV",
                    uploaderName = "FANZA • JavInfo",
                    uploadDate = "★ 9.8 • 2026",
                    viewCount = 0L,
                    durationSeconds = 7200L,
                    thumbnailUrl = "https://pics.dmm.co.jp/digital/video/${code.lowercase().replace("-", "")}/${code.lowercase().replace("-", "")}pl.jpg",
                    providerId = providerId
                )
            )
        }
        PagedResult(items = items, nextPageToken = (page + 1).toString(), hasMore = true)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val cleanCode = query.trim().uppercase()
        val item = queryFanza(cleanCode)

        val items = mutableListOf<PluginVideoItem>()
        items.add(
            item ?: PluginVideoItem(
                id = cleanCode,
                title = "[$cleanCode] FANZA Official JAV Stream",
                uploaderName = "FANZA • JavInfo",
                uploadDate = "★ 9.6 • 2026",
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
        queryFanza(code) ?: PluginVideoItem(
            id = code,
            title = "[$code] FANZA JAV Stream",
            uploaderName = "FANZA • JavInfo",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val code = extractCode(idOrUrl)
        val cleanCodeNoDash = code.lowercase().replace("-", "")

        val videoStreams = mutableListOf<PluginVideoStream>()

        try {
            val directApiJav = ApiJavServerProvider(http).getStreams(code)
            videoStreams.addAll(directApiJav.videoStreams.filter { it.format == "hls" || it.format == "mp4" })
        } catch (e: Exception) {
            // Ignore
        }

        if (videoStreams.isEmpty()) {
            videoStreams.add(
                PluginVideoStream(
                    url = "https://server.apijav.com/?mvapm_embed=$code",
                    qualityLabel = "PRO HD (FANZA Direct Server)",
                    format = "embed",
                    isMuxed = true
                )
            )
        }

        videoStreams.add(
            PluginVideoStream(
                url = "https://cc3001.dmm.co.jp/litevideo/freepv/${cleanCodeNoDash.take(1)}/${cleanCodeNoDash.take(3)}/$cleanCodeNoDash/${cleanCodeNoDash}_mvh_w.mp4",
                qualityLabel = "FANZA Official Trailer (Direct MP4)",
                format = "mp4",
                isMuxed = true
            )
        )

        PluginStreamInfo(
            id = code,
            url = videoStreams.first().url,
            title = "[$code] FANZA Stream",
            channelName = "FANZA • JavInfo",
            description = "FANZA / DMM JAV Stream Node",
            videoStreams = videoStreams
        )
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> = PagedResult(emptyList())

    private suspend fun queryFanza(code: String): PluginVideoItem? {
        val bodyJson = JSONObject().apply { put("q", code) }
        val headers = mapOf("x-javinfo-key" to apiKey, "Content-Type" to "application/json")

        return try {
            val resp = http.post(apiBase, bodyJson.toString(), headers = headers)
            if (resp.statusCode != 200 || resp.body.isBlank()) return null

            val json = JSONObject(resp.body)
            val resultObj = json.optJSONObject("result") ?: return null
            val dvdId = resultObj.optString("dvdId", code)
            val title = resultObj.optString("titleEn", "[$dvdId] FANZA Release")
            val releaseDate = resultObj.optString("releaseDate", "2026")
            val runtime = resultObj.optLong("runtimeMins", 120L)
            val jacketUrl = resultObj.optString("jacketFullUrl")

            val makers = resultObj.optJSONArray("makers")
            val makerStr = if (makers != null && makers.length() > 0) makers.optString(0) else "FANZA"

            PluginVideoItem(
                id = dvdId,
                title = title.ifBlank { "[$dvdId] FANZA Video" },
                uploaderName = "$dvdId • $makerStr",
                uploadDate = "★ 9.8 • $releaseDate",
                viewCount = 0L,
                durationSeconds = runtime * 60L,
                thumbnailUrl = jacketUrl.ifBlank { "https://pics.dmm.co.jp/digital/video/${dvdId.lowercase().replace("-", "")}/${dvdId.lowercase().replace("-", "")}pl.jpg" },
                providerId = providerId
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun extractCode(input: String): String =
        input.trim().uppercase().substringAfterLast("/").substringAfterLast("=").ifBlank { "SSIS-001" }
}

class JavInfoJavDbProvider(
    private val http: HttpBridge = HttpBridge(),
    private val apiKey: String = "public_demo"
) : ContentProviderApi {

    override val providerId: String = "javinfo_javdb"
    private val apiBase = "https://api.javinfo.dev/movie"

    override fun getProviderConfig(context: android.content.Context?): ProviderConfig {
        return ProviderConfig(
            id = providerId,
            name = "JavDB / JavInfo Provider",
            enabled = true,
            endpoint = apiBase,
            supportsDirectStreams = true,
            healthStatus = ProviderHealthStatus.READY
        )
    }

    private val defaultPopularCodes = listOf(
        "CAWD-001", "SSIS-001", "IPX-100", "STSK-236", "HMDNV-949",
        "SIRO-5681", "MIAB-001", "JUL-001", "ADN-001", "MIDE-001"
    )

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val items = mutableListOf<PluginVideoItem>()

        val startIndex = ((page - 1) * 5) % defaultPopularCodes.size
        for (i in 0 until 5) {
            val codeIndex = (startIndex + i) % defaultPopularCodes.size
            val code = defaultPopularCodes[codeIndex]
            val item = queryJavDb(code)
            items.add(
                item ?: PluginVideoItem(
                    id = code,
                    title = "[$code] JavDB High Definition Release",
                    uploaderName = "JavDB • JavInfo",
                    uploadDate = "★ 9.6 • 2026",
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
        val item = queryJavDb(cleanCode)

        val items = mutableListOf<PluginVideoItem>()
        items.add(
            item ?: PluginVideoItem(
                id = cleanCode,
                title = "[$cleanCode] JavDB Video Entry",
                uploaderName = "JavDB • JavInfo",
                uploadDate = "★ 9.5 • 2026",
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
        queryJavDb(code) ?: PluginVideoItem(
            id = code,
            title = "[$code] JavDB Stream & Downloads",
            uploaderName = "JavDB • JavInfo",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val code = extractCode(idOrUrl)
        val bodyJson = JSONObject().apply {
            put("q", code)
            put("providers", "javdb")
        }
        val headers = mapOf("x-javinfo-key" to apiKey, "Content-Type" to "application/json")

        val videoStreams = mutableListOf<PluginVideoStream>()
        var descriptionText = "JavDB Metadata & Magnet Stream Node"

        try {
            val resp = http.post(apiBase, bodyJson.toString(), headers = headers)
            if (resp.statusCode == 200 && resp.body.isNotBlank()) {
                val json = JSONObject(resp.body)
                val resultObj = json.optJSONObject("result")
                val extraObj = resultObj?.optJSONObject("extra")
                val downloadLinks = extraObj?.optJSONArray("downloadLinks")

                if (downloadLinks != null && downloadLinks.length() > 0) {
                    for (i in 0 until downloadLinks.length()) {
                        val linkObj = downloadLinks.optJSONObject(i) ?: continue
                        val name = linkObj.optString("name", "JavDB Torrent $i")
                        val magnet = linkObj.optString("magnet")
                        val isHd = linkObj.optBoolean("hd", true)

                        if (magnet.isNotBlank()) {
                            videoStreams.add(
                                PluginVideoStream(
                                    url = magnet,
                                    qualityLabel = "$name ${if (isHd) "(HD 1080p)" else ""}",
                                    format = "torrent",
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

        // Add standard web player embed streams
        videoStreams.add(
            PluginVideoStream(
                url = "https://server.apijav.com/?mvapm_embed=$code",
                qualityLabel = "JavDB Direct Web Player",
                format = "embed",
                isMuxed = true
            )
        )
        videoStreams.add(
            PluginVideoStream(
                url = "https://missav.ws/en/$code",
                qualityLabel = "MissAV Player Mirror",
                format = "embed",
                isMuxed = true
            )
        )

        PluginStreamInfo(
            id = code,
            url = videoStreams.first().url,
            title = "[$code] JavDB Streams & Magnets",
            channelName = "JavDB • JavInfo",
            description = descriptionText,
            videoStreams = videoStreams
        )
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> = PagedResult(emptyList())

    private suspend fun queryJavDb(code: String): PluginVideoItem? {
        val bodyJson = JSONObject().apply {
            put("q", code)
            put("providers", "javdb")
        }
        val headers = mapOf("x-javinfo-key" to apiKey, "Content-Type" to "application/json")

        return try {
            val resp = http.post(apiBase, bodyJson.toString(), headers = headers)
            if (resp.statusCode != 200 || resp.body.isBlank()) return null

            val json = JSONObject(resp.body)
            val resultObj = json.optJSONObject("result") ?: return null
            val dvdId = resultObj.optString("dvdId", code)
            val runtime = resultObj.optLong("runtimeMins", 120L)
            val extraObj = resultObj.optJSONObject("extra")
            val score = extraObj?.optDouble("score", 4.5) ?: 4.5
            val voteCount = extraObj?.optInt("voteCount", 320) ?: 320

            PluginVideoItem(
                id = dvdId,
                title = "[$dvdId] JavDB Multi-Source Release",
                uploaderName = "$dvdId • JavDB",
                uploadDate = "★ $score ($voteCount votes)",
                viewCount = 0L,
                durationSeconds = runtime * 60L,
                thumbnailUrl = null,
                providerId = providerId
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun extractCode(input: String): String =
        input.trim().uppercase().substringAfterLast("/").substringAfterLast("=").ifBlank { "CAWD-001" }
}
