package com.example.extractor.tmdbembed.extractors

import android.util.Log
import com.example.extractor.tmdbembed.ExtractedStream
import com.example.extractor.tmdbembed.TMDBEmbedSource
import com.example.extractor.tmdbembed.TMDBMediaRequest
import com.example.model.CaptionOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object VidlinkExtractor {
    private const val TAG = "VidlinkExtractor"
    private const val BASE_URL = "https://vidlink.pro"
    private const val ENC_API = "https://enc-dec.app/api/enc-vidlink"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun extract(request: TMDBMediaRequest): List<ExtractedStream> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<ExtractedStream>()
        try {
            // Step 1: Encrypt tmdbId
            val encReq = Request.Builder()
                .url("$ENC_API?text=${request.tmdbId}")
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val encBody = client.newCall(encReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string() ?: ""
            }

            val encJson = JSONObject(encBody)
            val encryptedId = encJson.optString("result", "")
            if (encryptedId.isBlank()) return@withContext emptyList()

            // Step 2: Request stream from Vidlink API
            val apiUrl = if (request.isTv) {
                "$BASE_URL/api/b/tv/$encryptedId/${request.season}/${request.episode}?multiLang=0"
            } else {
                "$BASE_URL/api/b/movie/$encryptedId?multiLang=0"
            }

            val apiReq = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "$BASE_URL/")
                .build()

            val apiBody = client.newCall(apiReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string() ?: ""
            }

            if (apiBody.isBlank()) return@withContext emptyList()
            val json = JSONObject(apiBody)
            val streamObj = json.optJSONObject("stream") ?: return@withContext emptyList()

            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                "Referer" to "$BASE_URL/"
            )

            // Extract subtitles if present
            val captions = mutableListOf<CaptionOption>()
            val tracksArray = streamObj.optJSONArray("tracks")
            if (tracksArray != null) {
                for (i in 0 until tracksArray.length()) {
                    val track = tracksArray.optJSONObject(i) ?: continue
                    val kind = track.optString("kind", "")
                    if (kind.contains("sub", ignoreCase = true) || kind.contains("captions", ignoreCase = true)) {
                        val label = track.optString("label", track.optString("name", "Sub $i"))
                        val file = track.optString("file", track.optString("url", ""))
                        if (file.isNotBlank()) {
                            captions.add(CaptionOption(languageName = label, languageCode = "en", format = "vtt", url = file))
                        }
                    }
                }
            }

            // Extract qualities
            val qualitiesObj = streamObj.optJSONObject("qualities")
            if (qualitiesObj != null) {
                val keys = qualitiesObj.keys()
                while (keys.hasNext()) {
                    val qKey = keys.next()
                    val qData = qualitiesObj.opt(qKey)
                    val qUrl = when (qData) {
                        is JSONObject -> qData.optString("url", "")
                        is String -> qData
                        else -> ""
                    }
                    if (qUrl.isNotBlank() && qUrl.startsWith("http")) {
                        streams.add(
                            ExtractedStream(
                                title = "${request.title} [Vidlink • ${qKey.uppercase()}]",
                                url = qUrl,
                                quality = if (qKey.contains("4k", ignoreCase = true)) "4K" else "${qKey}p",
                                source = TMDBEmbedSource.VIDLINK,
                                isHls = qUrl.contains(".m3u8"),
                                headers = headers,
                                subtitles = captions
                            )
                        )
                    }
                }
            }

            // Check direct playlist URL
            val playlistUrl = streamObj.optString("playlist", "")
            if (playlistUrl.isNotBlank() && playlistUrl.startsWith("http")) {
                streams.add(
                    ExtractedStream(
                        title = "${request.title} [Vidlink • Master Playlist]",
                        url = playlistUrl,
                        quality = "1080p",
                        source = TMDBEmbedSource.VIDLINK,
                        isHls = true,
                        headers = headers,
                        subtitles = captions
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vidlink extraction failed: ${e.message}", e)
        }
        streams
    }
}
