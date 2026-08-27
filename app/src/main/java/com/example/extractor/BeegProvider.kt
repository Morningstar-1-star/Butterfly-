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
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getHome(limit: Int = 20, page: Int = 1): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        val offset = ((page - 1) * limit).coerceAtLeast(0)
        try {
            val urls = listOf(
                "https://store.externulls.com/tag/videos/home?limit=$limit&offset=$offset",
                "https://store.externulls.com/tag/videos/main?limit=$limit&offset=$offset"
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
                                customThumb = if (v.startsWith("http")) v else "https://thumbs.externulls.com/240x180/$v.jpg"
                            } else if ((col == "sf_author" || col == "sf_studio" || col == "sf_user_name" || col == "sf_channel_name") && v.isNotBlank()) {
                                uploader = v
                            }
                        }
                    }

                    val fcFacts = item.optJSONArray("fc_facts")
                    var modelOrStudioName = ""
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
                        }
                    }

                    if (uploader == "Beeg" && modelOrStudioName.isNotBlank()) {
                        uploader = modelOrStudioName
                    }

                    val duration = fileObj.optLong("fl_duration", 0L)
                    var thumb = customThumb
                    if (thumb.isBlank()) {
                        val tagsArr = item.optJSONArray("tags")
                        if (tagsArr != null) {
                            for (tIdx in 0 until tagsArr.length()) {
                                val tObj = tagsArr.optJSONObject(tIdx) ?: continue
                                val tThumbs = tObj.optJSONArray("thumbs")
                                if (tThumbs != null && tThumbs.length() > 0) {
                                    val tId = tThumbs.optJSONObject(0)?.optString("id", "") ?: ""
                                    if (tId.isNotBlank()) {
                                        thumb = "https://cdn34769805.ahacdn.me/thumbs/$tId.jpg"
                                        break
                                    }
                                }
                            }
                        }
                    }
                    if (thumb.isBlank()) {
                        thumb = "https://thumbs.externulls.com/480x360/$fileId.jpg"
                    }

                    list.add(
                        VideoItem(
                            id = "https://beeg.com/$fileId",
                            title = title,
                            uploaderName = uploader,
                            thumbnailUrl = thumb,
                            durationSeconds = duration,
                            providerId = PROVIDER_ID
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
        val offset = ((page - 1) * limit).coerceAtLeast(0)
        try {
            val encodedQ = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            val url = "https://store.externulls.com/tag/videos/$encodedQ?limit=$limit&offset=$offset"
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
                                customThumb = if (v.startsWith("http")) v else "https://thumbs.externulls.com/240x180/$v.jpg"
                            } else if ((col == "sf_author" || col == "sf_studio" || col == "sf_user_name" || col == "sf_channel_name") && v.isNotBlank()) {
                                uploader = v
                            }
                        }
                    }

                    val fcFacts = item.optJSONArray("fc_facts")
                    var modelOrStudioName = ""
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
                        }
                    }

                    if (uploader == "Beeg" && modelOrStudioName.isNotBlank()) {
                        uploader = modelOrStudioName
                    }

                    val duration = fileObj.optLong("fl_duration", 0L)
                    var thumb = customThumb
                    if (thumb.isBlank()) {
                        thumb = "https://thumbs.externulls.com/480x360/$fileId.jpg"
                    }

                    list.add(
                        VideoItem(
                            id = "https://beeg.com/$fileId",
                            title = title,
                            uploaderName = uploader,
                            thumbnailUrl = thumb,
                            durationSeconds = duration,
                            providerId = PROVIDER_ID
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
        val fileId = Regex("""\d+""").find(cleanInput)?.value ?: ""
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
                if (fileObj != null) {
                    var title = "Beeg Video"
                    var uploader = "Beeg"
                    var customThumb = ""
                    val dataArr = fileObj.optJSONArray("data")
                    if (dataArr != null) {
                        for (j in 0 until dataArr.length()) {
                            val d = dataArr.optJSONObject(j) ?: continue
                            val col = d.optString("cd_column", "")
                            val v = d.optString("cd_value", "")
                            if (col == "sf_name" && v.isNotBlank() && title == "Beeg Video") {
                                title = v
                            } else if ((col == "sf_thumb" || col == "sf_preview" || col == "sf_image" || col == "sf_poster") && v.isNotBlank() && customThumb.isBlank()) {
                                customThumb = if (v.startsWith("http")) v else "https://thumbs.externulls.com/240x180/$v.jpg"
                            } else if ((col == "sf_author" || col == "sf_studio" || col == "sf_user_name" || col == "sf_channel_name") && v.isNotBlank()) {
                                uploader = v
                            }
                        }
                    }

                    val fcFacts = json.optJSONArray("fc_facts")
                    var modelOrStudioName = ""
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
                        }
                    }
                    if (uploader == "Beeg" && modelOrStudioName.isNotBlank()) {
                        uploader = modelOrStudioName
                    }

                    val thumb = when {
                        customThumb.isNotBlank() -> customThumb
                        else -> "https://thumbs.externulls.com/480x360/$fileId.jpg"
                    }

                    val options = mutableListOf<PlayableStreamOption>()

                    val hlsObj = fileObj.optJSONObject("hls_resources")
                    val hlsMulti = hlsObj?.optString("fl_cdn_multi", "") ?: ""
                    if (hlsMulti.isNotBlank()) {
                        val fullHlsUrl = "https://video.beeg.com/$hlsMulti"
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

                    val fallback = fileObj.optString("fallback", "")
                    if (fallback.isNotBlank()) {
                        val fullFallbackUrl = "https://video.beeg.com/$fallback"
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

                    val resources = fileObj.optJSONObject("resources")
                    if (resources != null) {
                        val keys = resources.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val pathVal = resources.optString(k, "")
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
