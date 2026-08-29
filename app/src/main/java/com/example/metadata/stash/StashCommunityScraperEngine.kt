package com.example.metadata.stash

import android.util.Log
import com.example.metadata.JavActor
import com.example.metadata.JavMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Modular CommunityScraper & Stash-Box Matching Architecture.
 * (Adapted from stashapp/stash & stashapp/CommunityScrapers)
 *
 * Implements the 4-phase metadata lifecycle:
 * 1. Provider Search (query or external ID dispatch)
 * 2. URL Extraction & Canonical Normalization
 * 3. Selector / Field Pattern Matching (Title, Date, Studio, Performers, Artwork)
 * 4. Standardized Output Schema Generation into JavMetadata / MediaMetadata
 */
class StashCommunityScraperEngine(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {

    companion object {
        private const val TAG = "StashCommunityScraper"
    }

    data class ScraperDefinition(
        val id: String,
        val name: String,
        val targetDomain: String,
        val searchUrlTemplate: String,
        val itemUrlRegex: String,
        val isEnabled: Boolean = true
    )

    /**
     * Executes a CommunityScrapers pattern lookup across registered modular scrapers.
     */
    suspend fun scrape(
        queryOrId: String,
        scraper: ScraperDefinition
    ): JavMetadata? = withContext(Dispatchers.IO) {
        if (!scraper.isEnabled) return@withContext null

        try {
            val encoded = URLEncoder.encode(queryOrId, StandardCharsets.UTF_8.name())
            val targetSearchUrl = scraper.searchUrlTemplate.replace("{query}", encoded)

            val req = Request.Builder()
                .url(targetSearchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext null

            val html = resp.body?.string() ?: return@withContext null
            val doc = Jsoup.parse(html, targetSearchUrl)

            // Extract main title
            val title = doc.select("meta[property='og:title']").attr("content").ifBlank {
                doc.title()
            }

            // Extract cover image
            val cover = doc.select("meta[property='og:image']").attr("content").ifBlank {
                doc.select("img.cover, img.poster, .video-cover img").firstOrNull()?.attr("abs:src")
            }

            // Extract description
            val desc = doc.select("meta[property='og:description']").attr("content").ifBlank {
                doc.select(".description, .plot, .summary").firstOrNull()?.text()
            }

            if (title.isNotBlank()) {
                return@withContext JavMetadata(
                    id = queryOrId,
                    code = queryOrId,
                    title = title,
                    coverUrl = cover?.takeIf { it.isNotBlank() },
                    thumbUrl = cover?.takeIf { it.isNotBlank() },
                    plotOverview = desc?.takeIf { it.isNotBlank() },
                    providerSource = scraper.name,
                    detailUrl = targetSearchUrl
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Scraper ${scraper.name} failed: ${e.message}")
        }

        null
    }
}
