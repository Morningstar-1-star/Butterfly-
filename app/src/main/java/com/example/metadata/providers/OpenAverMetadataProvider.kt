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
 * OpenAver Metadata Provider (Adapted from slive777/OpenAver).
 * Multi-source scraper referencing JavLibrary and Airav for title translations and uncensored designations.
 */
class OpenAverMetadataProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : MetadataProvider {

    companion object {
        private const val TAG = "OpenAverProvider"
    }

    override val id: String = "openaver"
    override val name: String = "OpenAver"
    override val priority: Int = 80

    override suspend fun getMetadata(javCode: String): JavMetadata? = withContext(Dispatchers.IO) {
        val parsed = JavIdParser.parse(javCode) ?: javCode
        try {
            // Javlibrary search query
            val searchUrl = "https://www.javlibrary.com/en/vl_searchbyid.php?keyword=$parsed"
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Cookie", "over18=1")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val doc = Jsoup.parse(response.body?.string() ?: "", "https://www.javlibrary.com/en/")
            val titleEl = doc.select(".post-title a, h3.post-title").firstOrNull()
            val title = titleEl?.text()?.trim() ?: parsed

            val cover = doc.select("#video_jacket_img").firstOrNull()?.attr("abs:src")
            val releaseDate = doc.select("#video_date .text").firstOrNull()?.text()?.trim()
            val durationMinutes = doc.select("#video_length .text").firstOrNull()?.text()?.replace("min", "")?.trim()?.toIntOrNull()
            val director = doc.select("#video_director .text a").firstOrNull()?.text()?.trim()
            val studio = doc.select("#video_maker .text a").firstOrNull()?.text()?.trim()
            val label = doc.select("#video_label .text a").firstOrNull()?.text()?.trim()

            val genres = doc.select(".genre a").map { it.text().trim() }
            val cast = doc.select(".star a").map { JavActor(name = it.text().trim()) }
            val ratingStr = doc.select("#video_review .score").firstOrNull()?.text()?.replace("(", "")?.replace(")", "")?.trim()
            val rating = ratingStr?.toFloatOrNull()

            JavMetadata(
                id = parsed,
                code = parsed,
                title = title,
                releaseDate = releaseDate,
                year = releaseDate?.take(4),
                durationMinutes = durationMinutes,
                director = director,
                studio = studio,
                label = label,
                genres = genres,
                coverUrl = cover,
                thumbUrl = cover,
                cast = cast,
                rating = rating,
                providerSource = name,
                detailUrl = response.request.url.toString()
            )
        } catch (e: Exception) {
            Log.w(TAG, "OpenAver fetch failed: ${e.message}")
            null
        }
    }
}
