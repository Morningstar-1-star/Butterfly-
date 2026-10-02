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

object FourKHDHubNuvioExtractor {
    private const val TAG = "FourKHDHubNuvio"
    private const val PROVIDER_ID = "4khdhub"

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

            val postLinks = doc.select("article a, h2 a, .entry-title a, .post-title a")
            val targetPostUrl = postLinks.firstOrNull()?.attr("abs:href") ?: return@withContext emptyList()

            val postReq = Request.Builder().url(targetPostUrl).header("User-Agent", "Mozilla/5.0").header("Referer", searchUrl).build()
            val postResp = httpClient.newCall(postReq).execute()
            val postHtml = postResp.body?.string() ?: return@withContext emptyList()
            val postDoc = Jsoup.parse(postHtml, targetPostUrl)

            val downloadElements = postDoc.select("a[href*=/links/], a[href*=/archives/], a[href*=hubcloud], a:contains(Download), a:contains(Watch)")
            for (el in downloadElements.take(5)) {
                val link = el.attr("abs:href")
                val resolved = NuvioHubCloudResolver.resolveStreamUrl(link, targetPostUrl)
                if (resolved != null) {
                    results.add(
                        resolved.copy(
                            name = "4KHDHub",
                            title = "${request.title} 4K UHD [4KHDHub]",
                            quality = "4K UHD",
                            providerId = PROVIDER_ID
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "4KHDHub extraction note: ${e.message}")
        }
        return@withContext results
    }
}
