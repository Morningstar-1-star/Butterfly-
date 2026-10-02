package com.example.extractor.nuvio.extractors

import android.util.Log
import com.example.extractor.nuvio.NuvioDomainManager
import com.example.extractor.nuvio.NuvioStreamResult
import com.example.extractor.tmdbembed.TMDBMediaRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

object AllMovieLandNuvioExtractor {
    private const val TAG = "AllMovieLandNuvio"
    private const val PROVIDER_ID = "allmovieland"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun extract(request: TMDBMediaRequest): List<NuvioStreamResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<NuvioStreamResult>()
        val domain = NuvioDomainManager.getDomain(PROVIDER_ID)

        try {
            val streamPageUrl = if (request.isTv) {
                "$domain/series/${request.tmdbId}/${request.season ?: 1}/${request.episode ?: 1}"
            } else {
                "$domain/movie/${request.tmdbId}"
            }

            val req = Request.Builder()
                .url(streamPageUrl)
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", domain)
                .build()

            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val html = resp.body?.string() ?: ""
                val m3u8Match = Regex("""https?://[^\s"']+\.m3u8[^\s"']*""").find(html)?.value
                if (!m3u8Match.isNullOrBlank()) {
                    results.add(
                        NuvioStreamResult(
                            name = "AllMovieLand",
                            title = "${request.title} (AllMovieLand)",
                            url = m3u8Match,
                            quality = "1080p",
                            isHls = true,
                            providerId = PROVIDER_ID,
                            headers = mapOf("Referer" to domain, "User-Agent" to "Mozilla/5.0")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "AllMovieLand note: ${e.message}")
        }
        return@withContext results
    }
}
