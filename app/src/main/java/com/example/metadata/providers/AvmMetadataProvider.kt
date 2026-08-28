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
 * AVM Metadata Provider (Adapted from dengji85/avm).
 * Specializes in DMM / Fanza scraping with direct contentId parsing, official sample trailers, and FC2/MGS scraping.
 */
class AvmMetadataProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : MetadataProvider {

    companion object {
        private const val TAG = "AvmProvider"
    }

    override val id: String = "avm"
    override val name: String = "AVM (DMM / MGS / FC2)"
    override val priority: Int = 85

    override suspend fun getMetadata(javCode: String): JavMetadata? = withContext(Dispatchers.IO) {
        val parsed = JavIdParser.parse(javCode) ?: javCode

        if (parsed.startsWith("FC2", ignoreCase = true)) {
            return@withContext scrapeFc2(parsed)
        }

        // Standard DMM scrape
        val contentId = JavIdParser.toDmmContentId(parsed)
        val dmmUrl = "https://www.dmm.co.jp/digital/videoa/-/detail/=/cid=$contentId/"

        try {
            val request = Request.Builder()
                .url(dmmUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Cookie", "age_check_done=1; ckcy=1")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val html = response.body?.string() ?: return@withContext null
            val doc = Jsoup.parse(html, "https://www.dmm.co.jp")

            val title = doc.select("#title").firstOrNull()?.text()?.trim() ?: parsed
            val coverUrl = doc.select("#$contentId").firstOrNull()?.attr("abs:src")
                ?: doc.select(".theater a").firstOrNull()?.attr("abs:href")

            val sampleTrailerUrl = doc.select("#detail-sample-movie a[onclick*=\"sampleplay\"]").firstOrNull()?.let { el ->
                val onclick = el.attr("onclick")
                val sampleMatch = Regex("sampleplay\\('([^']+)'\\)").find(onclick)
                sampleMatch?.groupValues?.getOrNull(1)
            }

            val screenshots = doc.select("#sample-image-block img").mapNotNull {
                it.attr("abs:src").ifBlank { null }
            }.map { it.replace("-(?:[0-9]+)\\.jpg", "jp-$0").replace("thumb", "large") }

            val genres = doc.select(".nw tr:contains(ジャンル) a").map { it.text().trim() }
            val actresses = doc.select("#performer a").map {
                JavActor(name = it.text().trim())
            }

            val studio = doc.select(".nw tr:contains(メーカー) a").firstOrNull()?.text()?.trim()
            val releaseDate = doc.select(".nw tr:contains(配信開始日) td:last-child").firstOrNull()?.text()?.trim()
                ?: doc.select(".nw tr:contains(商品発売日) td:last-child").firstOrNull()?.text()?.trim()

            JavMetadata(
                id = parsed,
                code = parsed,
                title = title,
                releaseDate = releaseDate,
                year = releaseDate?.take(4),
                studio = studio,
                genres = genres,
                coverUrl = coverUrl,
                thumbUrl = coverUrl,
                previewImages = screenshots,
                sampleVideoUrl = sampleTrailerUrl,
                cast = actresses,
                providerSource = name,
                detailUrl = dmmUrl
            )
        } catch (e: Exception) {
            Log.w(TAG, "AVM DMM scrape failed for $javCode: ${e.message}")
            null
        }
    }

    private fun scrapeFc2(fc2Code: String): JavMetadata? {
        val idNum = fc2Code.replace(Regex("[^0-9]"), "")
        if (idNum.isBlank()) return null
        try {
            val url = "https://adult.contents.fc2.com/article/$idNum/"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Cookie", "adult_checked=1")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null

            val doc = Jsoup.parse(response.body?.string() ?: "", "https://adult.contents.fc2.com")
            val title = doc.select(".items_article_headerInfo h3").firstOrNull()?.text()?.trim() ?: fc2Code
            val cover = doc.select(".items_article_MainitemThumb img").firstOrNull()?.attr("abs:src")
            val screenshots = doc.select(".items_article_SampleImagesArea img").mapNotNull {
                it.attr("abs:src").ifBlank { null }
            }

            return JavMetadata(
                id = fc2Code,
                code = fc2Code,
                title = title,
                coverUrl = cover,
                thumbUrl = cover,
                previewImages = screenshots,
                genres = listOf("FC2-PPV", "Amateur"),
                providerSource = name,
                detailUrl = url
            )
        } catch (e: Exception) {
            Log.w(TAG, "AVM FC2 scrape failed: ${e.message}")
            return null
        }
    }
}
