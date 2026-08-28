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
 * Javinizer Metadata Provider (Adapted from javinizer/javinizer-go).
 * Scrapes rich metadata, covers, sample preview images, and cast info from Javbus & DMM.
 */
class JavinizerMetadataProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) : MetadataProvider {

    companion object {
        private const val TAG = "JavinizerProvider"
        private const val JAVBUS_BASE_URL = "https://www.javbus.com"
    }

    override val id: String = "javinizer"
    override val name: String = "Javinizer (Javbus & DMM)"
    override val priority: Int = 100

    override suspend fun getMetadata(javCode: String): JavMetadata? = withContext(Dispatchers.IO) {
        val parsedCode = JavIdParser.parse(javCode) ?: javCode
        try {
            // Step 1: Scrape Javbus page
            val url = "$JAVBUS_BASE_URL/$parsedCode"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Cookie", "existmag=all")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext null
            }

            val html = response.body?.string() ?: return@withContext null
            val doc = Jsoup.parse(html, JAVBUS_BASE_URL)

            val title = doc.select("h3").firstOrNull()?.text()?.trim() ?: parsedCode
            val coverImg = doc.select(".bigImage img").firstOrNull()?.attr("abs:src")
                ?: doc.select(".screencap img").firstOrNull()?.attr("abs:src")

            // Parse metadata info block
            var releaseDate: String? = null
            var durationMinutes: Int? = null
            var director: String? = null
            var studio: String? = null
            var label: String? = null
            var series: String? = null

            val infoElements = doc.select(".info p")
            for (p in infoElements) {
                val text = p.text()
                when {
                    text.contains("發行日期:") || text.contains("Release Date:") -> {
                        releaseDate = text.substringAfter(":").trim()
                    }
                    text.contains("長度:") || text.contains("Length:") -> {
                        val lengthStr = text.substringAfter(":").replace("分鐘", "").replace("min", "").trim()
                        durationMinutes = lengthStr.toIntOrNull()
                    }
                    text.contains("導演:") || text.contains("Director:") -> {
                        director = p.select("a").text().trim().ifBlank { text.substringAfter(":").trim() }
                    }
                    text.contains("製作商:") || text.contains("Studio:") || text.contains("Maker:") -> {
                        studio = p.select("a").text().trim().ifBlank { text.substringAfter(":").trim() }
                    }
                    text.contains("發行商:") || text.contains("Label:") -> {
                        label = p.select("a").text().trim().ifBlank { text.substringAfter(":").trim() }
                    }
                    text.contains("系列:") || text.contains("Series:") -> {
                        series = p.select("a").text().trim().ifBlank { text.substringAfter(":").trim() }
                    }
                }
            }

            // Parse genres
            val genres = doc.select(".info .genre a").map { it.text().trim() }.filter { it.isNotBlank() }

            // Parse preview screenshot images
            val screenshots = doc.select("#sample-waterfall .sample-box img").mapNotNull {
                it.attr("abs:src").ifBlank { null }
            }.map { it.replace("thumbs/", "").replace("thumb_", "") }

            // Parse actresses / cast
            val cast = doc.select(".star-name a").map { starEl ->
                val name = starEl.text().trim()
                val avatar = doc.select(".star-box[title=\"$name\"] img, .avatar-box img").firstOrNull()?.attr("abs:src")
                JavActor(
                    name = name,
                    avatarUrl = avatar
                )
            }.ifEmpty {
                doc.select(".info p:has(span:contains(女)) a").map {
                    JavActor(name = it.text().trim())
                }
            }

            val year = releaseDate?.take(4)

            JavMetadata(
                id = parsedCode,
                code = parsedCode,
                title = title,
                releaseDate = releaseDate,
                year = year,
                durationMinutes = durationMinutes,
                director = director,
                studio = studio,
                label = label,
                series = series,
                genres = genres,
                coverUrl = coverImg,
                thumbUrl = coverImg,
                previewImages = screenshots,
                cast = cast,
                providerSource = name,
                detailUrl = url
            )
        } catch (e: Exception) {
            Log.w(TAG, "Javinizer metadata fetch failed for $javCode: ${e.message}")
            null
        }
    }

    override suspend fun search(query: String): List<JavMetadata> = withContext(Dispatchers.IO) {
        val parsedCode = JavIdParser.parse(query)
        if (parsedCode != null) {
            val direct = getMetadata(parsedCode)
            if (direct != null) return@withContext listOf(direct)
        }

        try {
            val searchUrl = "$JAVBUS_BASE_URL/search/${java.net.URLEncoder.encode(query, "UTF-8")}"
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val doc = Jsoup.parse(response.body?.string() ?: "", JAVBUS_BASE_URL)
            doc.select(".movie-box").mapNotNull { box ->
                val code = box.select("date").firstOrNull()?.text()?.trim() ?: return@mapNotNull null
                val title = box.select(".photo-info span").firstOrNull()?.text()?.trim() ?: code
                val cover = box.select(".photo-frame img").firstOrNull()?.attr("abs:src")
                val date = box.select("date").lastOrNull()?.text()?.trim()

                JavMetadata(
                    id = code,
                    code = code,
                    title = title,
                    releaseDate = date,
                    year = date?.take(4),
                    coverUrl = cover,
                    providerSource = name,
                    detailUrl = box.attr("abs:href")
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Javinizer search failed for $query: ${e.message}")
            emptyList()
        }
    }
}
