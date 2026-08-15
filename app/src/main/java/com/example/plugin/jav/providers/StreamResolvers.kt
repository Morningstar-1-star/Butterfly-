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
 * MissAV Surrit HLS Stream Resolver
 */
class MissAvSurritStreamResolver : StreamProvider {
    override val id: String = "missav_surrit"
    override val name: String = "MissAV Surrit HLS"
    override var isEnabled: Boolean = true

    override suspend fun resolveStreams(javId: String, title: String): List<JavStream> = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().lowercase()
        val results = mutableListOf<JavStream>()
        try {
            val missAvUrl = "https://missav.ws/en/$cleanJavId"
            val req = Request.Builder().url(missAvUrl).header("User-Agent", "Mozilla/5.0").build()
            val res = streamClient.newCall(req).execute()
            val html = res.body?.string() ?: ""
            val m3u8Match = Pattern.compile("m3u8\\|([a-zA-Z0-9_-]+)").matcher(html)
            if (m3u8Match.find()) {
                val token = m3u8Match.group(1) ?: ""
                val streamUrl = "https://surrit.com/$token/playlist.m3u8"
                val headers = mapOf("Referer" to "https://missav.ws/")
                if (verifyStreamUrl(streamUrl, headers)) {
                    results.add(
                        JavStream(
                            id = "missav_$cleanJavId",
                            javId = javId.uppercase(),
                            url = streamUrl,
                            title = "$javId (MissAV HLS)",
                            qualityLabel = "1080p",
                            codec = "H.264",
                            mimeType = "application/x-mpegURL",
                            headers = headers,
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
 * JableTV Direct HLS Stream Resolver
 */
class JableTvStreamResolver : StreamProvider {
    override val id: String = "jable_tv"
    override val name: String = "JableTV Direct HLS"
    override var isEnabled: Boolean = true

    override suspend fun resolveStreams(javId: String, title: String): List<JavStream> = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().lowercase()
        val results = mutableListOf<JavStream>()
        try {
            val jableUrl = "https://jable.tv/videos/$cleanJavId/"
            val req = Request.Builder().url(jableUrl).header("User-Agent", "Mozilla/5.0").build()
            val res = streamClient.newCall(req).execute()
            val html = res.body?.string() ?: ""
            val hlsMatch = Pattern.compile("hlsUrl\\s*=\\s*['\"](.*?)['\"]").matcher(html)
            if (hlsMatch.find()) {
                val streamUrl = hlsMatch.group(1) ?: ""
                if (streamUrl.isNotBlank()) {
                    val headers = mapOf("Referer" to "https://jable.tv/")
                    if (verifyStreamUrl(streamUrl, headers)) {
                        results.add(
                            JavStream(
                                id = "jable_$cleanJavId",
                                javId = javId.uppercase(),
                                url = streamUrl,
                                title = "$javId (JableTV HLS)",
                                qualityLabel = "720p",
                                codec = "H.264",
                                mimeType = "application/x-mpegURL",
                                headers = headers,
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
                        if (embeddedUrl.isNotBlank() && verifyStreamUrl(embeddedUrl)) {
                            results.add(
                                JavStream(
                                    id = "avgle_${cleanJavId}_$i",
                                    javId = cleanJavId,
                                    url = embeddedUrl,
                                    title = videoTitle.ifBlank { "$cleanJavId Stream" },
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
        } catch (e: Exception) {
            // Silently handled
        }
        results
    }
}

/**
 * yt-dlp Native Extractor Resolver
 */
class YtDlpExtractorResolver : StreamProvider {
    override val id: String = "ytdlp_extractor"
    override val name: String = "yt-dlp Native Extractor"
    override var isEnabled: Boolean = true

    override suspend fun resolveStreams(javId: String, title: String): List<JavStream> = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().lowercase()
        val results = mutableListOf<JavStream>()
        try {
            val targetUrl = "https://missav.ws/en/$cleanJavId"
            if (com.example.extractor.YtDlpResolver.isYtDlpSupportedUrl(targetUrl)) {
                val req = Request.Builder().url(targetUrl).header("User-Agent", "Mozilla/5.0").build()
                val res = streamClient.newCall(req).execute()
                val html = res.body?.string() ?: ""
                val m3u8Match = Pattern.compile("m3u8\\|([a-zA-Z0-9_-]+)").matcher(html)
                if (m3u8Match.find()) {
                    val token = m3u8Match.group(1) ?: ""
                    val directUrl = "https://surrit.com/$token/playlist.m3u8"
                    if (verifyStreamUrl(directUrl, mapOf("Referer" to "https://missav.ws/"))) {
                        results.add(
                            JavStream(
                                id = "ytdlp_$cleanJavId",
                                javId = javId.uppercase(),
                                url = directUrl,
                                title = "$javId (yt-dlp Direct HLS)",
                                qualityLabel = "1080p",
                                mimeType = "application/x-mpegURL",
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
                    if (streamUrl.isNotBlank() && verifyStreamUrl(streamUrl)) {
                        streams.add(
                            JavStream(
                                id = "mediafusion_${cleanJavId}_$i",
                                javId = cleanJavId,
                                url = streamUrl,
                                title = obj.optString("title", "$cleanJavId Stream"),
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
                    if (streamUrl.isNotBlank() && verifyStreamUrl(streamUrl)) {
                        streams.add(
                            JavStream(
                                id = "comet_${cleanJavId}_$i",
                                javId = cleanJavId,
                                url = streamUrl,
                                title = obj.optString("title", "$cleanJavId Stream"),
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
