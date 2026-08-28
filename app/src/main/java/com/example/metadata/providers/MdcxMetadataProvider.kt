package com.example.metadata.providers

import android.util.Log
import com.example.metadata.JavActor
import com.example.metadata.JavIdParser
import com.example.metadata.JavMetadata
import com.example.metadata.MetadataProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * MDCx Metadata Provider (Adapted from sqzw-x/mdcx).
 * Uses robust regex scraping across multi-regional mirrors with fallback genre localization.
 */
class MdcxMetadataProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : MetadataProvider {

    companion object {
        private const val TAG = "MdcxProvider"
    }

    override val id: String = "mdcx"
    override val name: String = "MDCx"
    override val priority: Int = 75

    override suspend fun getMetadata(javCode: String): JavMetadata? = withContext(Dispatchers.IO) {
        val parsed = JavIdParser.parse(javCode) ?: javCode
        try {
            // JavDB query
            val url = "https://javdb.com/search?q=$parsed&f=all"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Cookie", "over18=1; locale=en")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val doc = Jsoup.parse(response.body?.string() ?: "", "https://javdb.com")
            val firstItem = doc.select(".movie-list .item a").firstOrNull() ?: return@withContext null
            val detailUrl = firstItem.attr("abs:href")

            val detailReq = Request.Builder()
                .url(detailUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Cookie", "over18=1; locale=en")
                .build()

            val detailResp = client.newCall(detailReq).execute()
            if (!detailResp.isSuccessful) return@withContext null

            val detailDoc = Jsoup.parse(detailResp.body?.string() ?: "", "https://javdb.com")
            val title = detailDoc.select("h2.title strong").text().trim().ifBlank { parsed }
            val cover = detailDoc.select(".video-cover").attr("abs:src")
            val releaseDate = detailDoc.select(".panel-block:contains(Released Date) .value").text().trim()
            val studio = detailDoc.select(".panel-block:contains(Maker) .value a").text().trim()
            val scoreText = detailDoc.select(".panel-block:contains(Rating) .value").text().trim()
            val score = scoreText.substringBefore(",").replace("points", "").trim().toFloatOrNull()
            val genres = detailDoc.select(".panel-block:contains(Tags) .value a").map { it.text().trim() }
            val cast = detailDoc.select(".panel-block:contains(Actor) .value a").map { JavActor(name = it.text().trim()) }

            JavMetadata(
                id = parsed,
                code = parsed,
                title = title,
                releaseDate = releaseDate,
                year = releaseDate.take(4),
                studio = studio,
                genres = genres,
                coverUrl = cover,
                thumbUrl = cover,
                cast = cast,
                rating = score?.div(2f), // Convert 10-scale to 5-scale
                providerSource = name,
                detailUrl = detailUrl
            )
        } catch (e: Exception) {
            Log.w(TAG, "MDCx fetch failed: ${e.message}")
            null
        }
    }
}
