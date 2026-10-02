package com.example.extractor.nuvio.extractors

import android.util.Log
import com.example.extractor.nuvio.NuvioDomainManager
import com.example.extractor.nuvio.NuvioHubCloudResolver
import com.example.extractor.nuvio.NuvioStreamResult
import com.example.extractor.tmdbembed.TMDBMediaRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

object MoviesModNuvioExtractor {
    private const val TAG = "MoviesModNuvio"
    private const val PROVIDER_ID = "moviesmod"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun extract(request: TMDBMediaRequest): List<NuvioStreamResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<NuvioStreamResult>()
        val domain = NuvioDomainManager.getDomain(PROVIDER_ID)
        val query = request.title.ifBlank { "Movie" }

        try {
            val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            val searchUrl = "$domain/?s=$encodedQuery"
            val req = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", domain)
                .build()

            val resp = httpClient.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext emptyList()
            val doc = Jsoup.parse(html, searchUrl)

            val postLinks = doc.select(".post-item a, article a, h2 a, .latestPost a")
            val targetPostUrl = postLinks.firstOrNull { el ->
                val title = el.text().lowercase()
                title.contains(query.lowercase().take(6)) || (request.year.isNotBlank() && title.contains(request.year))
            }?.attr("abs:href") ?: postLinks.firstOrNull()?.attr("abs:href")

            if (targetPostUrl.isNullOrBlank()) {
                return@withContext emptyList()
            }

            // Fetch post page
            val postReq = Request.Builder().url(targetPostUrl).header("User-Agent", "Mozilla/5.0").header("Referer", searchUrl).build()
            val postResp = httpClient.newCall(postReq).execute()
            val postHtml = postResp.body?.string() ?: return@withContext emptyList()
            val postDoc = Jsoup.parse(postHtml, targetPostUrl)

            val downloadElements = postDoc.select("a[href*=/archives/], a[href*=/links/], a[href*=hubcloud], a[href*=fastdl], a.btn-download, a.btn-primary")
            for (el in downloadElements.take(5)) {
                val link = el.attr("abs:href")
                val text = el.text()
                val parentText = el.parent()?.text() ?: ""
                val fullDesc = "$text $parentText"

                val quality = when {
                    fullDesc.contains("4k", ignoreCase = true) || fullDesc.contains("2160p", ignoreCase = true) -> "4K UHD"
                    fullDesc.contains("1080p", ignoreCase = true) -> "1080p FHD"
                    fullDesc.contains("720p", ignoreCase = true) -> "720p HD"
                    else -> "1080p"
                }

                val resolved = NuvioHubCloudResolver.resolveStreamUrl(link, targetPostUrl)
                if (resolved != null) {
                    results.add(
                        resolved.copy(
                            name = "MoviesMod",
                            title = "${request.title} ($quality) [MoviesMod]",
                            quality = quality,
                            providerId = PROVIDER_ID
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MoviesMod extraction error: ${e.message}")
        }
        return@withContext results
    }
}
