package com.example.extractor.tmdbembed.extractors

import android.util.Log
import com.example.extractor.tmdbembed.ExtractedStream
import com.example.extractor.tmdbembed.TMDBEmbedSource
import com.example.extractor.tmdbembed.TMDBMediaRequest
import com.example.extractor.tmdbembed.toJsonObjectOrNull
import com.example.model.CaptionOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object VidlinkExtractor {
    private const val TAG = "VidlinkExtractor"
    private const val BASE_URL = "https://vidlink.pro"
    private const val ENC_API = "https://enc-dec.app/api/enc-vidlink"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun extract(request: TMDBMediaRequest): List<ExtractedStream> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<ExtractedStream>()
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Referer" to "$BASE_URL/"
        )

        try {
            // Step 1: Encrypt tmdbId
            val encReq = Request.Builder()
                .url("$ENC_API?text=${request.tmdbId}")
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val encBody = client.newCall(encReq).execute().use { resp ->
                if (!resp.isSuccessful) "" else resp.body?.string() ?: ""
            }

            val encJson = encBody.toJsonObjectOrNull()
            val encryptedId = encJson?.optString("result", "") ?: ""

            if (encryptedId.isNotBlank()) {
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
                    if (!resp.isSuccessful) "" else resp.body?.string() ?: ""
                }

                val json = apiBody.toJsonObjectOrNull()
                val streamObj = json?.optJSONObject("stream")

                if (streamObj != null) {
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
                }
            }

            // Fallback: Check direct embed page if API didn't return stream
            if (streams.isEmpty()) {
                val embedUrl = if (request.isTv) {
                    "$BASE_URL/tv/${request.tmdbId}/${request.season}/${request.episode}"
                } else {
                    "$BASE_URL/movie/${request.tmdbId}"
                }
                val embedReq = Request.Builder()
                    .url(embedUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "$BASE_URL/")
                    .build()
                val html = client.newCall(embedReq).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() ?: "" else ""
                }
                if (html.isNotBlank()) {
                    val m = Pattern.compile("https?://[^\"'\\s]+\\.m3u8[^\"'\\s]*").matcher(html)
                    if (m.find()) {
                        streams.add(
                            ExtractedStream(
                                title = "${request.title} [Vidlink • Direct HLS]",
                                url = m.group(),
                                quality = "1080p",
                                source = TMDBEmbedSource.VIDLINK,
                                isHls = true,
                                headers = headers
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vidlink extraction note: ${e.message}")
        }
        streams
    }
}

