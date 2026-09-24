package com.example.extractor.tmdbembed.extractors

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

object HDGharTvExtractor {
    private const val TAG = "HDGharTvExtractor"
    private const val BASE_URL = "https://hdghartv.cc/api"
    private const val REFERER = "https://hdghartv.cc/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun extract(request: TMDBMediaRequest): List<ExtractedStream> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<ExtractedStream>()
        try {
            val title = request.title.ifBlank { "Fight Club" }
            val encoded = URLEncoder.encode(title, "UTF-8")
            val searchUrl = "$BASE_URL/search?q=$encoded"

            val searchReq = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json, */*")
                .header("Referer", REFERER)
                .build()

            val body = client.newCall(searchReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string() ?: ""
            }

            if (body.isBlank()) return@withContext emptyList()
            val json = JSONObject(body)
            val list = if (request.isTv) json.optJSONArray("series") else json.optJSONArray("movies")
            if (list != null && list.length() > 0) {
                val item = list.getJSONObject(0)
                val links = item.optJSONArray("streamingLinks")
                if (links != null) {
                    for (i in 0 until links.length()) {
                        val linkObj = links.optJSONObject(i) ?: continue
                        val url = linkObj.optString("url", linkObj.optString("link", ""))
                        if (url.isNotBlank() && url.startsWith("http")) {
                            val qual = linkObj.optString("quality", "1080p")
                            streams.add(
                                ExtractedStream(
                                    title = "${request.title} [HDGharTV • $qual]",
                                    url = url,
                                    quality = qual,
                                    source = TMDBEmbedSource.HDGHAR_TV,
                                    isHls = url.contains(".m3u8"),
                                    headers = mapOf(
                                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                                        "Referer" to REFERER
                                    )
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "HDGharTV extraction failed: ${e.message}", e)
        }
        streams
    }
}
