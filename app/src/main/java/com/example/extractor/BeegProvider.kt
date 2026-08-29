package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object BeegProvider {
    private const val TAG = "BeegProvider"
    const val PROVIDER_ID = "beeg"

    private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getHome(limit: Int = 20, page: Int = 1): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        val effectiveLimit = maxOf(limit, 20)
        val offset = ((page - 1) * limit).coerceAtLeast(0)
        try {
            val urls = listOf(
                "https://store.externulls.com/tag/videos/home?limit=$effectiveLimit&offset=$offset",
                "https://store.externulls.com/tag/videos/main?limit=$effectiveLimit&offset=$offset"
            )
            var jsonStr: String? = null
            for (u in urls) {
                try {
                    val req = Request.Builder()
                        .url(u)
                        .header("User-Agent", DEFAULT_USER_AGENT)
                        .header("Referer", "https://beeg.com/")
                        .header("Origin", "https://beeg.com")
                        .header("Accept", "application/json, text/plain, */*")
                        .build()

                    val res = httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string() else null
                    }
                    if (!res.isNullOrBlank() && res.trim().startsWith("[")) {
                        jsonStr = res
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed fetch from $u: ${e.message}")
                }
            }

            if (!jsonStr.isNullOrBlank()) {
                val array = JSONArray(jsonStr)
                for (i in 0 until minOf(array.length(), limit)) {
                    val item = array.optJSONObject(i) ?: continue
                    val fileObj = item.optJSONObject("file") ?: continue
                    val fileId = fileObj.opt("id")?.toString() ?: ""
                    if (fileId.isBlank()) continue

                    var title = "Beeg Video"
                    var customThumb = ""
                    var uploader = "Beeg"
                    val dataArr = fileObj.optJSONArray("data")
                    if (dataArr != null) {
                        for (j in 0 until dataArr.length()) {
                            val d = dataArr.optJSONObject(j) ?: continue
                            val col = d.optString("cd_column", "")
                            val v = d.optString("cd_value", "")
                            if (col == "sf_name" && v.isNotBlank() && title == "Beeg Video") {
                                title = v
                            } else if ((col == "sf_thumb" || col == "sf_preview" || col == "sf_image" || col == "sf_poster") && v.isNotBlank() && customThumb.isBlank()) {
                                customThumb = if (v.startsWith("http")) v else "https://thumbs.externulls.com/videos/$fileId/$v.webp?w=480&h=270"
                            } else if ((col == "sf_author" || col == "sf_studio" || col == "sf_user_name" || col == "sf_channel_name") && v.isNotBlank()) {
                                uploader = v
                            }
                        }
                    }

                    val fcFacts = item.optJSONArray("fc_facts")
                    var modelOrStudioName = ""
                    val previewOffsets = mutableListOf<String>()
                    if (fcFacts != null && fcFacts.length() > 0) {
                        for (fIdx in 0 until fcFacts.length()) {
                            val factObj = fcFacts.optJSONObject(fIdx) ?: continue
                            val tagObj = factObj.optJSONObject("tag")
                            val tgType = tagObj?.optString("tg_type", "") ?: ""
                            val tgName = tagObj?.optString("tg_name", "") ?: factObj.optString("fc_name", "")
                            if (tgName.isNotBlank()) {
                                if (tgType.equals("model", ignoreCase = true) || tgType.equals("studio", ignoreCase = true) || tgType.equals("site", ignoreCase = true)) {
                                    modelOrStudioName = tgName
                                    break
                                } else if (modelOrStudioName.isBlank()) {
                                    modelOrStudioName = tgName
                                }
                            }

                            val factThumbs = factObj.optJSONArray("fc_thumbs")
                            if (factThumbs != null) {
                                for (t in 0 until factThumbs.length()) {
                                    val off = factThumbs.opt(t)?.toString() ?: ""
                                    if (off.isNotBlank() && !previewOffsets.contains(off)) {
                                        previewOffsets.add(off)
                                    }
                                }
                            }
                        }
                    }

                    if (uploader == "Beeg" && modelOrStudioName.isNotBlank()) {
                        uploader = modelOrStudioName
                    }

                    val duration = fileObj.optLong("fl_duration", 0L)
                    val primaryOffset = previewOffsets.firstOrNull() ?: "0"
                    val thumb = if (customThumb.isNotBlank()) {
                        customThumb
                    } else {
                        "https://thumbs.externulls.com/videos/$fileId/$primaryOffset.webp?w=480&h=270"
                    }

                    val previewList = previewOffsets.take(10).map { off ->
                        "https://thumbs.externulls.com/videos/$fileId/$off.webp?w=480&h=270"
                    }

                    list.add(
                        VideoItem(
                            id = "https://beeg.com/$fileId",
                            title = title,
                            uploaderName = uploader,
                            thumbnailUrl = thumb,
                            durationSeconds = duration,
                            providerId = PROVIDER_ID,
                            previewThumbnails = previewList
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Beeg parse error: ${e.message}")
        }
        return list
    }

    fun search(query: String, limit: Int = 20, page: Int = 1): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        if (query.isBlank()) return list
        val effectiveLimit = maxOf(limit, 20)
        val offset = ((page - 1) * limit).coerceAtLeast(0)
        try {
            val encodedQ = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            val url = "https://store.externulls.com/tag/videos/$encodedQ?limit=$effectiveLimit&offset=$offset"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Referer", "https://beeg.com/")
                .header("Origin", "https://beeg.com")
                .header("Accept", "application/json, text/plain, */*")
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!jsonStr.isNullOrBlank() && jsonStr.trim().startsWith("[")) {
                val array = JSONArray(jsonStr)
                for (i in 0 until minOf(array.length(), limit)) {
                    val item = array.optJSONObject(i) ?: continue
                    val fileObj = item.optJSONObject("file") ?: continue
                    val fileId = fileObj.opt("id")?.toString() ?: ""
                    if (fileId.isBlank()) continue

                    var title = "Beeg Video"
                    var customThumb = ""
                    var uploader = "Beeg"
                    val dataArr = fileObj.optJSONArray("data")
                    if (dataArr != null) {
                        for (j in 0 until dataArr.length()) {
                            val d = dataArr.optJSONObject(j) ?: continue
                            val col = d.optString("cd_column", "")
                            val v = d.optString("cd_value", "")
                            if (col == "sf_name" && v.isNotBlank() && title == "Beeg Video") {
                                title = v
                            } else if ((col == "sf_thumb" || col == "sf_preview" || col == "sf_image" || col == "sf_poster") && v.isNotBlank() && customThumb.isBlank()) {
                                customThumb = if (v.startsWith("http")) v else "https://thumbs.externulls.com/videos/$fileId/$v.webp?w=480&h=270"
                            } else if ((col == "sf_author" || col == "sf_studio" || col == "sf_user_name" || col == "sf_channel_name") && v.isNotBlank()) {
                                uploader = v
                            }
                        }
                    }

                    val fcFacts = item.optJSONArray("fc_facts")
                    var modelOrStudioName = ""
                    val previewOffsets = mutableListOf<String>()
                    if (fcFacts != null && fcFacts.length() > 0) {
                        for (fIdx in 0 until fcFacts.length()) {
                            val factObj = fcFacts.optJSONObject(fIdx) ?: continue
                            val tagObj = factObj.optJSONObject("tag")
                            val tgType = tagObj?.optString("tg_type", "") ?: ""
                            val tgName = tagObj?.optString("tg_name", "") ?: factObj.optString("fc_name", "")
                            if (tgName.isNotBlank()) {
                                if (tgType.equals("model", ignoreCase = true) || tgType.equals("studio", ignoreCase = true) || tgType.equals("site", ignoreCase = true)) {
                                    modelOrStudioName = tgName
                                    break
                                } else if (modelOrStudioName.isBlank()) {
                                    modelOrStudioName = tgName
                                }
                            }

                            val factThumbs = factObj.optJSONArray("fc_thumbs")
                            if (factThumbs != null) {
                                for (t in 0 until factThumbs.length()) {
                                    val off = factThumbs.opt(t)?.toString() ?: ""
                                    if (off.isNotBlank() && !previewOffsets.contains(off)) {
                                        previewOffsets.add(off)
                                    }
                                }
                            }
                        }
                    }

                    if (uploader == "Beeg" && modelOrStudioName.isNotBlank()) {
                        uploader = modelOrStudioName
                    }

                    val duration = fileObj.optLong("fl_duration", 0L)
                    val primaryOffset = previewOffsets.firstOrNull() ?: "0"
                    val thumb = if (customThumb.isNotBlank()) {
                        customThumb
                    } else {
                        "https://thumbs.externulls.com/videos/$fileId/$primaryOffset.webp?w=480&h=270"
                    }

                    val previewList = previewOffsets.take(10).map { off ->
                        "https://thumbs.externulls.com/videos/$fileId/$off.webp?w=480&h=270"
                    }

                    list.add(
                        VideoItem(
                            id = "https://beeg.com/$fileId",
                            title = title,
                            uploaderName = uploader,
                            thumbnailUrl = thumb,
                            durationSeconds = duration,
                            providerId = PROVIDER_ID,
                            previewThumbnails = previewList
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Beeg search error: ${e.message}")
        }
        return list
    }

    suspend fun getStreamData(urlOrId: String, context: Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val cleanInput = urlOrId.trim()
        val digitsOnly = Regex("""\d+""").find(cleanInput)?.value ?: ""
        val fileId = digitsOnly.trimStart('0').ifEmpty { digitsOnly }
        if (fileId.isBlank()) return@withContext null

        val beegHeaders = mapOf(
            "User-Agent" to DEFAULT_USER_AGENT,
            "Referer" to "https://beeg.com/",
            "Origin" to "https://beeg.com"
        )

        try {
            val detailUrl = "https://store.externulls.com/facts/file/$fileId"
            val req = Request.Builder()
                .url(detailUrl)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Referer", "https://beeg.com/")
                .header("Origin", "https://beeg.com")
                .header("Accept", "application/json, text/plain, */*")
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!jsonStr.isNullOrBlank() && jsonStr.trim().startsWith("{")) {
                val json = JSONObject(jsonStr)
                val fileObj = json.optJSONObject("file")
                val fcFacts = json.optJSONArray("fc_facts")

                var title = "Beeg Video"
                var uploader = "Beeg"
                var customThumb = ""

                val dataArr = fileObj?.optJSONArray("data")
                if (dataArr != null) {
                    for (j in 0 until dataArr.length()) {
                        val d = dataArr.optJSONObject(j) ?: continue
                        val col = d.optString("cd_column", "")
                        val v = d.optString("cd_value", "")
                        if (col == "sf_name" && v.isNotBlank() && title == "Beeg Video") {
                            title = v
                        } else if ((col == "sf_thumb" || col == "sf_preview" || col == "sf_image" || col == "sf_poster") && v.isNotBlank() && customThumb.isBlank()) {
                            customThumb = if (v.startsWith("http")) v else "https://thumbs.externulls.com/videos/$fileId/$v.webp?w=480&h=270"
                        } else if ((col == "sf_author" || col == "sf_studio" || col == "sf_user_name" || col == "sf_channel_name") && v.isNotBlank()) {
                            uploader = v
                        }
                    }
                }

                var modelOrStudioName = ""
                val previewOffsets = mutableListOf<String>()
                if (fcFacts != null && fcFacts.length() > 0) {
                    for (fIdx in 0 until fcFacts.length()) {
                        val factObj = fcFacts.optJSONObject(fIdx) ?: continue
                        val tagObj = factObj.optJSONObject("tag")
                        val tgType = tagObj?.optString("tg_type", "") ?: ""
                        val tgName = tagObj?.optString("tg_name", "") ?: factObj.optString("fc_name", "")
                        if (tgName.isNotBlank()) {
                            if (tgType.equals("model", ignoreCase = true) || tgType.equals("studio", ignoreCase = true) || tgType.equals("site", ignoreCase = true)) {
                                modelOrStudioName = tgName
                                break
                            } else if (modelOrStudioName.isBlank()) {
                                modelOrStudioName = tgName
                            }
                        }

                        val factThumbs = factObj.optJSONArray("fc_thumbs")
                        if (factThumbs != null) {
                            for (t in 0 until factThumbs.length()) {
                                val off = factThumbs.opt(t)?.toString() ?: ""
                                if (off.isNotBlank() && !previewOffsets.contains(off)) {
                                    previewOffsets.add(off)
                                }
                            }
                        }
                    }
                }

                if (uploader == "Beeg" && modelOrStudioName.isNotBlank()) {
                    uploader = modelOrStudioName
                }

                val primaryOffset = previewOffsets.firstOrNull() ?: "0"
                val thumb = when {
                    customThumb.isNotBlank() -> customThumb
                    else -> "https://thumbs.externulls.com/videos/$fileId/$primaryOffset.webp?w=480&h=270"
                }

                val options = mutableListOf<PlayableStreamOption>()

                // 1. Extract HLS streams from fileObj and fc_facts
                fun addHlsCandidate(hlsMulti: String?) {
                    if (!hlsMulti.isNullOrBlank()) {
                        val fullHlsUrl = "https://video.beeg.com/$hlsMulti"
                        if (options.none { it.videoUrl == fullHlsUrl }) {
                            options.add(
                                PlayableStreamOption(
                                    qualityLabel = "Auto (HLS)",
                                    format = "m3u8",
                                    isMuxed = true,
                                    videoUrl = fullHlsUrl,
                                    headers = beegHeaders
                                )
                            )
                        }
                    }
                }

                addHlsCandidate(fileObj?.optJSONObject("hls_resources")?.optString("fl_cdn_multi"))
                addHlsCandidate(fileObj?.optJSONObject("hls_resources_tmp")?.optString("fl_cdn_multi"))

                if (fcFacts != null) {
                    for (fIdx in 0 until fcFacts.length()) {
                        val f = fcFacts.optJSONObject(fIdx) ?: continue
                        addHlsCandidate(f.optJSONObject("hls_resources")?.optString("fl_cdn_multi"))
                        addHlsCandidate(f.optJSONObject("hls_resources_tmp")?.optString("fl_cdn_multi"))
                    }
                }

                // 2. Extract direct fallback MP4 streams
                fun addFallbackCandidate(fallbackPath: String?) {
                    if (!fallbackPath.isNullOrBlank()) {
                        val fullFallbackUrl = "https://video.beeg.com/$fallbackPath"
                        if (options.none { it.videoUrl == fullFallbackUrl }) {
                            options.add(
                                PlayableStreamOption(
                                    qualityLabel = "480p (MP4)",
                                    format = "mp4",
                                    isMuxed = true,
                                    videoUrl = fullFallbackUrl,
                                    headers = beegHeaders
                                )
                            )
                        }
                    }
                }

                addFallbackCandidate(fileObj?.optString("fallback"))
                if (fcFacts != null) {
                    for (fIdx in 0 until fcFacts.length()) {
                        val f = fcFacts.optJSONObject(fIdx) ?: continue
                        addFallbackCandidate(f.optString("fallback"))
                    }
                }

                // 3. Extract resource MP4 streams (fl_cdn_240, fl_cdn_480, fl_cdn_720, etc.)
                fun addResourcesCandidates(resObj: JSONObject?) {
                    if (resObj != null) {
                        val keys = resObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val pathVal = resObj.optString(k, "")
                            if (pathVal.isNotBlank()) {
                                val qualLabel = if (k.startsWith("fl_cdn_")) k.removePrefix("fl_cdn_") + "p (MP4)" else "360p (MP4)"
                                val fullResUrl = "https://video.beeg.com/$pathVal"
                                if (options.none { it.videoUrl == fullResUrl }) {
                                    options.add(
                                        PlayableStreamOption(
                                            qualityLabel = qualLabel,
                                            format = "mp4",
                                            isMuxed = true,
                                            videoUrl = fullResUrl,
                                            headers = beegHeaders
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                addResourcesCandidates(fileObj?.optJSONObject("resources"))
                if (fcFacts != null) {
                    for (fIdx in 0 until fcFacts.length()) {
                        val f = fcFacts.optJSONObject(fIdx) ?: continue
                        addResourcesCandidates(f.optJSONObject("resources"))
                    }
                }

                if (options.isNotEmpty()) {
                    val primaryOption = options.first()
                    val hlsUrl = options.firstOrNull { it.format == "m3u8" }?.videoUrl
                    return@withContext StreamData(
                        videoId = fileId,
                        videoUrl = primaryOption.videoUrl ?: "",
                        title = title,
                        channelName = uploader,
                        thumbnailUrl = thumb,
                        availableStreamOptions = options,
                        selectedStreamOption = primaryOption,
                        hlsUrl = hlsUrl,
                        providerId = PROVIDER_ID,
                        headers = beegHeaders
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Beeg stream extraction error: ${e.message}", e)
        }

        // Fallback to YtDlpResolver if native extraction failed
        if (context != null) {
            try {
                val targetUrl = "https://beeg.com/$fileId"
                Log.i(TAG, "Resolving Beeg video ID via YtDlpResolver: $fileId ($targetUrl)")
                val ytDlpRes = YtDlpResolver.extractStreamInfo(context, targetUrl)
                if (ytDlpRes is YouTubeExtractorHelper.ExtractionResult.Success) {
                    return@withContext ytDlpRes.streamData.copy(
                        providerId = PROVIDER_ID,
                        headers = beegHeaders
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "YtDlpResolver Beeg fallback failed: ${e.message}")
            }
        }

        null
    }
}
