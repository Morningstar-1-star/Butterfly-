package com.example.extractor.tmdbembed.extractors

import android.util.Base64
import android.util.Log
import com.example.extractor.tmdbembed.ExtractedStream
import com.example.extractor.tmdbembed.TMDBEmbedSource
import com.example.extractor.tmdbembed.TMDBMediaRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object VideasyExtractor {
    private const val TAG = "VideasyExtractor"
    private const val BASE_URL = "https://api.speedracelight.com"
    private const val PLAYER_URL = "https://player.videasy.net"
    private val MAGIC = byteArrayOf(109, 118, 109, 49) // "mvm1"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun u32(x: Long): Long = x and 0xFFFFFFFFL

    private fun mul32(a: Long, b: Long): Long = u32((a.toInt() * b.toInt()).toLong())

    private fun rotl32(x: Long, n: Int): Long {
        val shift = n and 31
        val xi = x.toInt()
        val res = if (shift == 0) xi else (xi shl shift) or (xi ushr (32 - shift))
        return u32(res.toLong())
    }

    private fun hash32(x: Long): Long {
        var v = u32(x)
        v = u32(v xor (v ushr 16))
        v = mul32(v, 2246822507L)
        v = u32(v xor (v ushr 13))
        v = mul32(v, 3266489909L)
        v = u32(v xor (v ushr 16))
        return v
    }

    private fun fnv1a(str: String): Long {
        var h = 2166136261L
        for (i in 0 until str.length) {
            h = mul32(h xor str[i].code.toLong(), 16777619L)
        }
        return hash32(h)
    }

    suspend fun extract(request: TMDBMediaRequest): List<ExtractedStream> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<ExtractedStream>()
        try {
            // First check embed page to see if direct iframe stream or m3u8 exists
            val embedUrl = if (request.isTv) {
                "$PLAYER_URL/tv/${request.tmdbId}/${request.season}/${request.episode}"
            } else {
                "$PLAYER_URL/movie/${request.tmdbId}"
            }

            val embedReq = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "$PLAYER_URL/")
                .build()

            val embedHtml = client.newCall(embedReq).execute().use { resp ->
                if (!resp.isSuccessful) "" else resp.body?.string() ?: ""
            }

            if (embedHtml.contains(".m3u8")) {
                val m3u8Matcher = java.util.regex.Pattern.compile("https?://[^'\"\\s]+\\.m3u8[^'\"\\s]*").matcher(embedHtml)
                if (m3u8Matcher.find()) {
                    val m3u8Url = m3u8Matcher.group()
                    streams.add(
                        ExtractedStream(
                            title = "${request.title} [Videasy • 1080p HLS]",
                            url = m3u8Url,
                            quality = "1080p",
                            source = TMDBEmbedSource.VIDEASY,
                            isHls = true,
                            headers = mapOf(
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                                "Referer" to "$PLAYER_URL/",
                                "Origin" to PLAYER_URL
                            )
                        )
                    )
                }
            }

            // Also attempt seed resolution
            val seedReq = Request.Builder()
                .url("$BASE_URL/seed?mediaId=${request.tmdbId}")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "$PLAYER_URL/")
                .header("Origin", PLAYER_URL)
                .build()

            val seedJson = client.newCall(seedReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() ?: "" else ""
            }

            if (seedJson.isNotBlank() && streams.isEmpty()) {
                val seedObj = JSONObject(seedJson)
                val seed = seedObj.optString("seed", "")
                if (seed.isNotBlank()) {
                    val encUrl = "$BASE_URL/cdn/sources-with-title?title=${URLEncoder.encode(request.title, "UTF-8")}&mediaType=${if (request.isTv) "TV Series" else "Movie"}&year=${request.year}&tmdbId=${request.tmdbId}&enc=2&seed=$seed"
                    val encReq = Request.Builder()
                        .url(encUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .header("Referer", "$PLAYER_URL/")
                        .header("Origin", PLAYER_URL)
                        .build()

                    val encResp = client.newCall(encReq).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string() ?: "" else ""
                    }

                    if (encResp.isNotBlank() && !encResp.startsWith("<") && encResp.contains("http")) {
                        // Found playable source
                        streams.add(
                            ExtractedStream(
                                title = "${request.title} [Videasy CDN]",
                                url = encResp.trim(),
                                quality = "1080p",
                                source = TMDBEmbedSource.VIDEASY,
                                isHls = encResp.contains(".m3u8"),
                                headers = mapOf(
                                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                                    "Referer" to "$PLAYER_URL/",
                                    "Origin" to PLAYER_URL
                                )
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Videasy extraction failed: ${e.message}", e)
        }
        streams
    }
}
