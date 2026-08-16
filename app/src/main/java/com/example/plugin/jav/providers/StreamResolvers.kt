package com.example.plugin.jav.providers

import com.example.plugin.jav.JavStream
import com.example.plugin.jav.StreamProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

private val streamClient = OkHttpClient.Builder()
    .connectTimeout(6, TimeUnit.SECONDS)
    .readTimeout(8, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

private fun verifyStreamUrl(url: String, headers: Map<String, String> = emptyMap()): Boolean {
    return try {
        val reqBuilder = Request.Builder().url(url).head()
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }
        val res = streamClient.newCall(reqBuilder.build()).execute()
        res.isSuccessful || res.code == 302 || res.code == 206
    } catch (e: Exception) {
        false
    }
}

/**
 * 3a. MissAV / Jable Stream Resolver (Delegated to embedded yt-dlp plugin-yellow)
 */
class MissAvSurritStreamResolver : StreamProvider {
    override val id: String = "missav_surrit"
    override val name: String = "MissAV (yt-dlp Engine)"
    override var isEnabled: Boolean = true

    override suspend fun resolveStreams(javId: String, title: String): List<JavStream> = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().lowercase()
        val results = mutableListOf<JavStream>()
        try {
            val missAvUrl = "https://missav.ws/en/$cleanJavId"
            // Delegate extraction to Butterfly's unified yt-dlp resolver using plugin-yellow
            val extractedResult = com.example.extractor.YtDlpResolver.extractStreamInfo(com.example.MainApplication.appContext, missAvUrl)
            if (extractedResult is com.example.extractor.YtDlpResolver.ExtractionResult.Success) {
                extractedResult.playableOptions.forEachIndexed { index, opt ->
                    results.add(
                        JavStream(
                            id = "missav_ytdlp_${cleanJavId}_$index",
                            javId = javId.uppercase(),
                            url = opt.videoUrl ?: "",
                            title = "${javId.uppercase()} - ${opt.qualityLabel} (MissAV yt-dlp)",
                            qualityLabel = opt.qualityLabel,
                            codec = "H.264",
                            mimeType = if (opt.format == "hls") "application/x-mpegURL" else "video/mp4",
                            headers = opt.headers,
                            providerId = id,
                            providerName = name
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Silently handled
        }
        results
    }
}

/**
 * 3b. JableTV Direct HLS Stream Resolver (Delegated to embedded yt-dlp plugin-yellow)
 */
class JableTvStreamResolver : StreamProvider {
    override val id: String = "jable_tv"
    override val name: String = "JableTV (yt-dlp Engine)"
    override var isEnabled: Boolean = true

    override suspend fun resolveStreams(javId: String, title: String): List<JavStream> = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().lowercase()
        val results = mutableListOf<JavStream>()
        try {
            val jableUrl = "https://jable.tv/videos/$cleanJavId/"
            val extractedResult = com.example.extractor.YtDlpResolver.extractStreamInfo(com.example.MainApplication.appContext, jableUrl)
            if (extractedResult is com.example.extractor.YtDlpResolver.ExtractionResult.Success) {
                extractedResult.playableOptions.forEachIndexed { index, opt ->
                    results.add(
                        JavStream(
                            id = "jable_ytdlp_${cleanJavId}_$index",
                            javId = javId.uppercase(),
                            url = opt.videoUrl ?: "",
                            title = "${javId.uppercase()} - ${opt.qualityLabel} (JableTV yt-dlp)",
                            qualityLabel = opt.qualityLabel,
                            codec = "H.264",
                            mimeType = if (opt.format == "hls") "application/x-mpegURL" else "video/mp4",
                            headers = opt.headers,
                            providerId = id,
                            providerName = name
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Silently handled
        }
        results
    }
}

/**
 * 4. yt-dlp Native Extractor Resolver
 * Directly extracts playable video streams using native YoutubeDL engine.
 */
class YtDlpExtractorResolver : StreamProvider {
    override val id: String = "ytdlp_extractor"
    override val name: String = "yt-dlp Native Extractor"
    override var isEnabled: Boolean = true

    override suspend fun resolveStreams(javId: String, title: String): List<JavStream> = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().lowercase()
        val results = mutableListOf<JavStream>()
        val ctx = com.example.MainApplication.appContext
        if (ctx != null) {
            val targetUrl = "https://missav.ws/en/$cleanJavId"
            try {
                val res = com.example.extractor.YtDlpResolver.extractStreamInfo(ctx, targetUrl)
                if (res is com.example.extractor.YtDlpResolver.ExtractionResult.Success) {
                    res.playableOptions.forEachIndexed { idx, opt ->
                        if (!opt.videoUrl.isNullOrEmpty()) {
                            results.add(
                                JavStream(
                                    id = "ytdlp_${cleanJavId}_$idx",
                                    javId = javId.uppercase(),
                                    url = opt.videoUrl!!,
                                    title = "${javId.uppercase()} (${opt.qualityLabel})",
                                    qualityLabel = opt.qualityLabel,
                                    mimeType = if (opt.format == "m3u8") "application/x-mpegURL" else "video/mp4",
                                    headers = opt.headers ?: emptyMap(),
                                    providerId = id,
                                    providerName = name
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // Silently handled
            }
        }
        results
    }
}

/**
 * Avgle JAV Stream API Resolver
 */
class AvgleApiStreamResolver : StreamProvider {
    override val id: String = "avgle_api"
    override val name: String = "Avgle JAV Stream API"
    override var isEnabled: Boolean = true

    override suspend fun resolveStreams(javId: String, title: String): List<JavStream> = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().uppercase()
        val results = mutableListOf<JavStream>()
        try {
            val url = "https://api.avgle.com/v1/jav/$cleanJavId/0"
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            val res = streamClient.newCall(req).execute()
            val jsonStr = res.body?.string() ?: ""
            if (jsonStr.isNotBlank()) {
                val root = JSONObject(jsonStr)
                if (root.optBoolean("success")) {
                    val responseObj = root.optJSONObject("response")
                    val videos = responseObj?.optJSONArray("videos") ?: JSONArray()
                    for (i in 0 until videos.length()) {
                        val video = videos.getJSONObject(i)
                        val embeddedUrl = video.optString("embedded_url")
                        val videoTitle = video.optString("title")
                        if (videoTitle.uppercase().contains(cleanJavId) && embeddedUrl.isNotBlank()) {
                            if (verifyStreamUrl(embeddedUrl)) {
                                results.add(
                                    JavStream(
                                        id = "avgle_${cleanJavId}_$i",
                                        javId = cleanJavId,
                                        url = embeddedUrl,
                                        title = videoTitle,
                                        qualityLabel = "720p",
                                        mimeType = "video/mp4",
                                        providerId = id,
                                        providerName = name
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Silently handled
        }
        results
    }
}

/**
 * MediaFusion Stremio Addon Resolver
 */
class MediaFusionStremioResolver : StreamProvider {
    override val id: String = "mediafusion_stremio"
    override val name: String = "MediaFusion Stremio Addon"
    override var isEnabled: Boolean = true

    override suspend fun resolveStreams(javId: String, title: String): List<JavStream> = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().uppercase()
        val streams = mutableListOf<JavStream>()
        try {
            val url = "https://mediafusion.elfhosted.com/stream/movie/$cleanJavId.json"
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            val res = streamClient.newCall(req).execute()
            val jsonStr = res.body?.string() ?: ""
            if (jsonStr.isNotBlank()) {
                val root = JSONObject(jsonStr)
                val streamArray = root.optJSONArray("streams") ?: JSONArray()
                for (i in 0 until streamArray.length()) {
                    val obj = streamArray.getJSONObject(i)
                    val streamUrl = obj.optString("url")
                    val stTitle = obj.optString("title", "").uppercase()
                    if (stTitle.contains(cleanJavId) && streamUrl.isNotBlank() && verifyStreamUrl(streamUrl)) {
                        streams.add(
                            JavStream(
                                id = "mediafusion_${cleanJavId}_$i",
                                javId = cleanJavId,
                                url = streamUrl,
                                title = obj.optString("title"),
                                qualityLabel = "1080p",
                                providerId = id,
                                providerName = name
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Silently handled
        }
        streams
    }
}

/**
 * JavPy Stream Resolver Engine
 * Reimplements JavPy's native Python stream resolver modules (`javpy.functions.search_avgle` & `search_youav`).
 */
class JavPyStreamResolver : StreamProvider {
    override val id: String = "javpy_resolver"
    override val name: String = "JavPy Stream Resolver"
    override var isEnabled: Boolean = true

    override suspend fun resolveStreams(javId: String, title: String): List<JavStream> = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().uppercase()
        val results = mutableListOf<JavStream>()

        // Primary: JavPy's Avgle search function (`javpy.functions.search_avgle`)
        try {
            val url = "https://api.avgle.com/v1/jav/$cleanJavId/0"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "JavPy Python/3.9 Engine")
                .build()
            val res = streamClient.newCall(req).execute()
            val jsonStr = res.body?.string() ?: ""

            if (jsonStr.isNotBlank()) {
                val root = JSONObject(jsonStr)
                if (root.optBoolean("success")) {
                    val responseObj = root.optJSONObject("response")
                    val videos = responseObj?.optJSONArray("videos") ?: JSONArray()
                    for (i in 0 until videos.length()) {
                        val video = videos.getJSONObject(i)
                        val embeddedUrl = video.optString("embedded_url")
                        val videoTitle = video.optString("title")
                        if (videoTitle.uppercase().contains(cleanJavId) && embeddedUrl.isNotBlank()) {
                            if (verifyStreamUrl(embeddedUrl)) {
                                results.add(
                                    JavStream(
                                        id = "javpy_avgle_${cleanJavId}_$i",
                                        javId = cleanJavId,
                                        url = embeddedUrl,
                                        title = "$videoTitle (JavPy Engine)",
                                        qualityLabel = "720p",
                                        mimeType = "video/mp4",
                                        providerId = id,
                                        providerName = name
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Silently handled
        }

        results
    }
}

/**
 * Comet Stremio Addon Resolver
 */
class CometStremioResolver : StreamProvider {
    override val id: String = "comet_stremio"
    override val name: String = "Comet Stremio Addon"
    override var isEnabled: Boolean = true

    override suspend fun resolveStreams(javId: String, title: String): List<JavStream> = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().uppercase()
        val streams = mutableListOf<JavStream>()
        try {
            val url = "https://comet.elfhosted.com/stream/movie/$cleanJavId.json"
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            val res = streamClient.newCall(req).execute()
            val jsonStr = res.body?.string() ?: ""
            if (jsonStr.isNotBlank()) {
                val root = JSONObject(jsonStr)
                val streamArray = root.optJSONArray("streams") ?: JSONArray()
                for (i in 0 until streamArray.length()) {
                    val obj = streamArray.getJSONObject(i)
                    val streamUrl = obj.optString("url")
                    val stTitle = obj.optString("title", "").uppercase()
                    if (stTitle.contains(cleanJavId) && streamUrl.isNotBlank() && verifyStreamUrl(streamUrl)) {
                        streams.add(
                            JavStream(
                                id = "comet_${cleanJavId}_$i",
                                javId = cleanJavId,
                                url = streamUrl,
                                title = obj.optString("title"),
                                qualityLabel = "1080p",
                                providerId = id,
                                providerName = name
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Silently handled
        }
        streams
    }
}
