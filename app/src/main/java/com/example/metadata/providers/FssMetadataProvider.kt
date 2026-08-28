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
 * FSS Metadata Provider (Adapted from Anastylosis/FSS).
 * High-speed fallback metadata scraper extracting gallery previews and publisher classifications.
 */
class FssMetadataProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : MetadataProvider {

    companion object {
        private const val TAG = "FssProvider"
    }

    override val id: String = "fss"
    override val name: String = "FSS"
    override val priority: Int = 70

    override suspend fun getMetadata(javCode: String): JavMetadata? = withContext(Dispatchers.IO) {
        val parsed = JavIdParser.parse(javCode) ?: javCode
        try {
            // Javbus uncensored / fallback endpoint
            val url = "https://www.javbus.com/uncensored/$parsed"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Cookie", "existmag=all")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val doc = Jsoup.parse(response.body?.string() ?: "", "https://www.javbus.com")
            val title = doc.select("h3").firstOrNull()?.text()?.trim() ?: parsed
            val cover = doc.select(".bigImage img").firstOrNull()?.attr("abs:src")
            val screenshots = doc.select("#sample-waterfall img").mapNotNull { it.attr("abs:src").ifBlank { null } }

            JavMetadata(
                id = parsed,
                code = parsed,
                title = title,
                coverUrl = cover,
                thumbUrl = cover,
                previewImages = screenshots,
                providerSource = name,
                detailUrl = url
            )
        } catch (e: Exception) {
            Log.w(TAG, "FSS fetch failed: ${e.message}")
            null
        }
    }
}
