package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import com.example.model.StreamData
import com.example.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Txxx.com Real Provider & Stream Extractor.
 * High-speed native API parser for real TXXX video listings, live search, and direct CDN HD stream playback.
 */
object TxxxProvider {
    private const val TAG = "TxxxProvider"
    const val PROVIDER_ID = "txxx"
    private const val BASE_URL = "https://txxx.com"
    private const val MIRROR_URL = "https://txxx.tube"

    private const val DEFAULT_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private const val BASE164_ALPHABET =
        "\u0410\u0412\u0421D\u0415FGHIJKL\u041cNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789.,~"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", DEFAULT_UA)
                .header("Referer", "$BASE_URL/")
                .header("Origin", BASE_URL)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cookie", "age_confirmed=1; age_verified=1; platform=pc; country=US; ft_mature=1; consent=1")
                .build()
            chain.proceed(req)
        }
        .build()

    /**
     * Fetch real trending / latest adult video feed from Txxx API.
     */
    suspend fun getHome(limit: Int = 24, page: Int = 1, context: Context? = null): List<VideoItem> = withContext(Dispatchers.IO) {
        val safePage = if (page < 1) 1 else page
        val safeLimit = if (limit in 1..100) limit else 24

        // 1. Fetch real listings from official JSON API with primary + mirror fallback
        val liveVideos = withTimeoutOrNull(12000L) {
            coroutineScope {
                val primaryDef = async {
                    fetchVideosFromApi("$BASE_URL/api/json/videos2/14400/str/latest-updates/$safeLimit/..$safePage.all...json")
                }
                val mirrorDef = async {
                    fetchVideosFromApi("$MIRROR_URL/api/json/videos2/14400/str/latest-updates/$safeLimit/..$safePage.all...json")
                }

                val primaryRes = primaryDef.await()
                if (primaryRes.isNotEmpty()) return@coroutineScope primaryRes

                val mirrorRes = mirrorDef.await()
                if (mirrorRes.isNotEmpty()) return@coroutineScope mirrorRes

                // Fallback to most popular if latest updates is temporarily empty
                val popularDef = async {
                    fetchVideosFromApi("$BASE_URL/api/json/videos2/14400/str/most-popular/$safeLimit/..$safePage.all...json")
                }
                popularDef.await()
            }
        }

        if (!liveVideos.isNullOrEmpty()) {
            Log.i(TAG, "Txxx getHome fetched ${liveVideos.size} real videos (page $safePage)")
            return@withContext liveVideos.take(safeLimit)
        }

        Log.w(TAG, "Txxx getHome returned empty live results for page $safePage")
        emptyList()
    }

    /**
     * Real-time search across the entire Txxx catalog.
     */
    suspend fun search(query: String, limit: Int = 24, page: Int = 1, context: Context? = null): List<VideoItem> = withContext(Dispatchers.IO) {
        val clean = query.replace(Regex("(?i)txxx:"), "").trim()
        if (clean.isBlank()) return@withContext getHome(limit, page, context)
        val safePage = if (page < 1) 1 else page
        val safeLimit = if (limit in 1..100) limit else 24
        val encoded = URLEncoder.encode(clean, "UTF-8")

        val searchResults = withTimeoutOrNull(12000L) {
            coroutineScope {
                val primaryDef = async {
                    fetchVideosFromApi("$BASE_URL/api/videos2.php?params=14400/str/relevance/$safeLimit/search..$safePage.all..&s=$encoded")
                }
                val mirrorDef = async {
                    fetchVideosFromApi("$MIRROR_URL/api/videos2.php?params=14400/str/relevance/$safeLimit/search..$safePage.all..&s=$encoded")
                }

                val pRes = primaryDef.await()
                if (pRes.isNotEmpty()) return@coroutineScope pRes

                val mRes = mirrorDef.await()
                if (mRes.isNotEmpty()) return@coroutineScope mRes

                emptyList()
            }
        }

        if (!searchResults.isNullOrEmpty()) {
            Log.i(TAG, "Txxx search '$clean' fetched ${searchResults.size} real videos")
            return@withContext searchResults.take(safeLimit)
        }

        Log.w(TAG, "Txxx search '$clean' returned no results")
        emptyList()
    }

    /**
     * Parse JSON payload from Txxx video listing endpoints into VideoItem domain models.
     */
    private fun fetchVideosFromApi(endpoint: String): List<VideoItem> {
        val results = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder()
                .url(endpoint)
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return emptyList()

            val trimmed = jsonStr.trim()
            val videosArray: JSONArray = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> {
                    val root = JSONObject(trimmed)
                    root.optJSONArray("videos") ?: JSONArray()
                }
                else -> return emptyList()
            }

            for (i in 0 until videosArray.length()) {
                val obj = videosArray.optJSONObject(i) ?: continue
                val videoId = obj.optString("video_id").trim()
                if (videoId.isBlank()) continue

                val title = obj.optString("title").ifBlank {
                    obj.optString("title_ru").ifBlank { "Txxx Video #$videoId" }
                }

                val durationStr = obj.optString("duration")
                val durationSec = parseDuration(durationStr)

                val views = obj.optLong("video_viewed", 0L)
                val uploader = obj.optString("display_name").ifBlank {
                    obj.optString("username").ifBlank { "Txxx HD" }
                }

                // Screenshot URL
                val scr = obj.optString("scr")
                val thumb = when {
                    scr.isNotBlank() -> scr
                    else -> {
                        val num = videoId.toLongOrNull() ?: 0L
                        val s2 = 1000L * (num / 1000L)
                        "https://tn.txxx.tube/contents/videos_screenshots/$s2/$videoId/288x162/1.jpg"
                    }
                }

                val dir = obj.optString("dir")
                val desc = obj.optString("description").ifBlank {
                    obj.optString("categories").ifBlank { "Txxx Full HD Video" }
                }

                results.add(
                    VideoItem(
                        id = "txxx:$videoId",
                        title = title,
                        uploaderName = uploader,
                        uploaderUrl = dir,
                        thumbnailUrl = thumb,
                        durationSeconds = durationSec,
                        viewCount = views,
                        providerId = PROVIDER_ID,
                        description = desc
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Txxx API endpoint $endpoint: ${e.message}")
        }
        return results
    }

    /**
     * Resolves the real full HD stream URL and metadata for a Txxx video.
     */
    suspend fun getStreamData(urlOrId: String, context: Context?): StreamData? = withContext(Dispatchers.IO) {
        val cleanId = urlOrId.removePrefix("txxx:").trim('/')
        val numericId = Regex("""\b(\d{5,10})\b""").find(cleanId)?.groupValues?.get(1)

        val defaultHeaders = mapOf(
            "User-Agent" to DEFAULT_UA,
            "Referer" to "$BASE_URL/",
            "Origin" to BASE_URL
        )

        var realTitle: String? = null
        var realThumb: String? = null
        var realDuration: Long = -1L

        // 1. Fetch real video metadata from Txxx JSON API if numeric ID is available
        if (!numericId.isNullOrBlank()) {
            try {
                val num = numericId.toLong()
                val s1 = 1_000_000L * (num / 1_000_000L)
                val s2 = 1_000L * (num / 1_000L)
                val infoUrl = "$BASE_URL/api/json/video/14400/$s1/$s2/$numericId.json"
                val infoReq = Request.Builder().url(infoUrl).build()

                httpClient.newCall(infoReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            val infoJson = JSONObject(body)
                            val vidObj = infoJson.optJSONObject("video")
                            if (vidObj != null) {
                                realTitle = vidObj.optString("title").ifBlank { null }
                                realThumb = vidObj.optString("thumbsrc").ifBlank { vidObj.optString("thumb").ifBlank { null } }
                                val durStr = vidObj.optString("duration")
                                if (durStr.isNotBlank()) {
                                    realDuration = parseDuration(durStr)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Txxx info metadata fetch note: ${e.message}")
            }
        }

        // 2. Fetch real stream payload from Txxx videofile API
        if (!numericId.isNullOrBlank()) {
            val candidateEndpoints = listOf(
                "$BASE_URL/api/videofile.php?video_id=$numericId&lifetime=8640000",
                "$MIRROR_URL/api/videofile.php?video_id=$numericId&lifetime=8640000"
            )

            for (apiUrl in candidateEndpoints) {
                try {
                    val vReq = Request.Builder()
                        .url(apiUrl)
                        .header("Referer", "$BASE_URL/videos/$numericId/")
                        .build()

                    val rawResp = httpClient.newCall(vReq).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string() else null
                    }

                    if (!rawResp.isNullOrBlank() && rawResp.contains("video_url")) {
                        val array = JSONArray(rawResp)
                        for (idx in 0 until array.length()) {
                            val item = array.optJSONObject(idx) ?: continue
                            val encodedUrl = item.optString("video_url")
                            if (encodedUrl.isNotBlank()) {
                                val decodedRelative = base164Decode(encodedUrl)
                                if (decodedRelative.isNotBlank()) {
                                    val fullDirectUrl = if (decodedRelative.startsWith("http")) {
                                        decodedRelative
                                    } else {
                                        "$BASE_URL${if (decodedRelative.startsWith("/")) "" else "/"}$decodedRelative"
                                    }

                                    // Resolve final CDN stream URL by following redirects
                                    val finalStreamUrl = resolveFinalStreamUrl(fullDirectUrl, numericId)
                                    val isHls = finalStreamUrl.contains(".m3u8")

                                    val streamOption = PlayableStreamOption(
                                        qualityLabel = if (isHls) "Auto HLS" else "1080p Full HD",
                                        format = if (isHls) "m3u8" else "mp4",
                                        isMuxed = true,
                                        videoUrl = finalStreamUrl,
                                        providerType = ProviderType.DIRECT,
                                        headers = defaultHeaders,
                                        qualityCategory = "1080p"
                                    )

                                    Log.i(TAG, "Successfully extracted real stream for Txxx video $numericId")
                                    return@withContext StreamData(
                                        videoId = urlOrId,
                                        videoUrl = finalStreamUrl,
                                        title = realTitle ?: "Txxx HD Video #$numericId",
                                        channelName = "Txxx HD Official",
                                        thumbnailUrl = realThumb ?: "https://tn.txxx.tube/contents/videos_screenshots/${1000L * (numericId.toLong() / 1000L)}/$numericId/preview.jpg",
                                        providerId = PROVIDER_ID,
                                        providerType = ProviderType.DIRECT,
                                        availableStreamOptions = listOf(streamOption),
                                        selectedStreamOption = streamOption,
                                        headers = defaultHeaders
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error fetching videofile for $apiUrl: ${e.message}")
                }
            }
        }

        // 3. Resilient fallback to native Yt-Dlp if context is provided
        if (context != null) {
            try {
                val fullUrl = when {
                    urlOrId.startsWith("http") -> urlOrId
                    !numericId.isNullOrBlank() -> "$BASE_URL/videos/$numericId/"
                    else -> "$BASE_URL/$cleanId"
                }
                val ytdlResult = YtDlpResolver.extractStreamInfo(context, fullUrl)
                if (ytdlResult is YouTubeExtractorHelper.ExtractionResult.Success) {
                    return@withContext ytdlResult.streamData.copy(
                        providerId = PROVIDER_ID,
                        channelName = "Txxx HD Official"
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Txxx yt-dlp fallback note: ${e.message}")
            }
        }

        Log.e(TAG, "Unable to extract real video stream for $urlOrId")
        null
    }

    /**
     * Follow HTTP redirects to resolve the direct CDN MP4 stream URL.
     */
    private fun resolveFinalStreamUrl(directUrl: String, videoId: String): String {
        return try {
            val req = Request.Builder()
                .url(directUrl)
                .header("User-Agent", DEFAULT_UA)
                .header("Referer", "$BASE_URL/videos/$videoId/")
                .header("Origin", BASE_URL)
                .build()

            httpClient.newCall(req).execute().use { resp ->
                resp.request.url.toString()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Stream redirect resolution note: ${e.message}")
            directUrl
        }
    }

    /**
     * Decodes Txxx obfuscated base164 video stream token into a clean URL path.
     */
    fun base164Decode(encoded: String): String {
        val clean = encoded.filter { BASE164_ALPHABET.contains(it) }
        val out = ByteArrayOutputStream()
        var s = 0
        while (s < clean.length) {
            val o = if (s < clean.length) BASE164_ALPHABET.indexOf(clean[s++]) else -1
            val n = if (s < clean.length) BASE164_ALPHABET.indexOf(clean[s++]) else -1
            val i = if (s < clean.length) BASE164_ALPHABET.indexOf(clean[s++]) else -1
            val r = if (s < clean.length) BASE164_ALPHABET.indexOf(clean[s++]) else -1
            if (o < 0 || n < 0) break
            val b1 = (o shl 2) or (n ushr 4)
            out.write(b1)
            if (i != 64 && i >= 0) {
                val b2 = ((15 and n) shl 4) or (i ushr 2)
                out.write(b2)
            }
            if (r != 64 && r >= 0) {
                val b3 = ((3 and i) shl 6) or r
                out.write(b3)
            }
        }
        val rawStr = out.toString("UTF-8")
        return try {
            URLDecoder.decode(rawStr, "UTF-8")
        } catch (e: Exception) {
            rawStr
        }
    }

    private fun parseDuration(d: String): Long {
        val clean = d.replace(Regex("""[^0-9:]"""), "")
        val parts = clean.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> -1L
        }
    }
}

