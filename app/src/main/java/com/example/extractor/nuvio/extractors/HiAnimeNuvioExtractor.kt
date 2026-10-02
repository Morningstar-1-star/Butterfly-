package com.example.extractor.nuvio.extractors

import android.util.Log
import com.example.extractor.nuvio.NuvioDomainManager
import com.example.extractor.nuvio.NuvioStreamResult
import com.example.extractor.nuvio.NuvioSubtitle
import com.example.extractor.tmdbembed.TMDBMediaRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object HiAnimeNuvioExtractor {
    private const val TAG = "HiAnimeNuvio"
    private const val PROVIDER_ID = "hianime"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun extract(request: TMDBMediaRequest): List<NuvioStreamResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<NuvioStreamResult>()
        val domain = NuvioDomainManager.getDomain(PROVIDER_ID)

        try {
            // Megacloud / Vidwish embed endpoint
            val epNumber = request.episode ?: 1
            val streamUrl = "$domain/embed-2/e-1/${request.tmdbId}?ep=$epNumber"
            val req = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://hianime.to")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val html = resp.body?.string() ?: ""
                val m3u8Match = Regex("""file:\s*["']([^"']+\.m3u8[^"']*)["']""").find(html)?.groupValues?.get(1)
                if (!m3u8Match.isNullOrBlank()) {
                    results.add(
                        NuvioStreamResult(
                            name = "HiAnime",
                            title = "${request.title} (HiAnime Sub/Dub)",
                            url = m3u8Match,
                            quality = "1080p",
                            isHls = true,
                            providerId = PROVIDER_ID,
                            headers = mapOf("Referer" to "https://megacloud.bloggy.click", "User-Agent" to "Mozilla/5.0")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "HiAnime Nuvio note: ${e.message}")
        }
        return@withContext results
    }
}
