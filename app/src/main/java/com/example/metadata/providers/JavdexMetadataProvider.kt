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
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * Javdex Metadata Provider (Adapted from JavdexLabs/Javdex plugin architecture).
 * Scrapes metadata and actor index from Jav321 and Airav mirror APIs.
 */
class JavdexMetadataProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : MetadataProvider {

    companion object {
        private const val TAG = "JavdexProvider"
        private const val JAV321_BASE_URL = "https://www.jav321.com"
    }

    override val id: String = "javdex"
    override val name: String = "Javdex"
    override val priority: Int = 90

    override suspend fun getMetadata(javCode: String): JavMetadata? = withContext(Dispatchers.IO) {
        val parsedCode = JavIdParser.parse(javCode) ?: javCode
        try {
            val url = "$JAV321_BASE_URL/search"
            val formBody = okhttp3.FormBody.Builder()
                .add("sn", parsedCode)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val html = response.body?.string() ?: return@withContext null
            val doc = Jsoup.parse(html, JAV321_BASE_URL)

            val titleEl = doc.select("h3 small").firstOrNull() ?: doc.select("h3").firstOrNull()
            val title = titleEl?.text()?.trim() ?: parsedCode

            val poster = doc.select(".col-md-3 img.img-responsive").firstOrNull()?.attr("abs:src")
                ?: doc.select(".col-md-9 img").firstOrNull()?.attr("abs:src")

            val screenshots = doc.select(".col-md-12 img.img-responsive").mapNotNull {
                it.attr("abs:src").ifBlank { null }
            }

            var releaseDate: String? = null
            var studio: String? = null
            val genres = mutableListOf<String>()
            val cast = mutableListOf<JavActor>()

            val pElements = doc.select(".col-md-9 p, .col-md-9 b")
            for (el in pElements) {
                val text = el.text()
                if (text.contains("發行日期:") || text.contains("Release Date:")) {
                    releaseDate = text.substringAfter(":").trim()
                } else if (text.contains("片商:") || text.contains("Studio:")) {
                    studio = text.substringAfter(":").trim()
                }
            }

            doc.select("a[href*=\"/genre/\"]").forEach {
                val g = it.text().trim()
                if (g.isNotBlank() && !genres.contains(g)) genres.add(g)
            }

            doc.select("a[href*=\"/star/\"]").forEach {
                val name = it.text().trim()
                if (name.isNotBlank() && cast.none { a -> a.name == name }) {
                    cast.add(JavActor(name = name))
                }
            }

            JavMetadata(
                id = parsedCode,
                code = parsedCode,
                title = title,
                releaseDate = releaseDate,
                year = releaseDate?.take(4),
                studio = studio,
                genres = genres,
                coverUrl = poster,
                thumbUrl = poster,
                previewImages = screenshots,
                cast = cast,
                providerSource = name,
                detailUrl = response.request.url.toString()
            )
        } catch (e: Exception) {
            Log.w(TAG, "Javdex fetch failed for $javCode: ${e.message}")
            null
        }
    }
}
