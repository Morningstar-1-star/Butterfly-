package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.VideoItem
import com.example.model.parseDurationToSeconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * High-performance extractor for JAV (Japanese Adult Video) providers:
 * - 123AV / JAVPlayer
 * - Javtiful
 * - MissAV
 * - Jable.tv
 */
object JavVideoExtractor {
    private const val TAG = "JavVideoExtractor"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9,ja;q=0.8")
                .build()
            chain.proceed(req)
        }
        .build()

    suspend fun getHome(providerId: String, limit: Int = 20, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val pid = providerId.lowercase().trim()
        when (pid) {
            "123av", "javplayer" -> get123AvHome(limit, page)
            "javtiful" -> getJavtifulHome(limit, page)
            "jav_all", "all_jav" -> getAllJavHome(limit, page)
            else -> {
                val list123 = get123AvHome(limit / 2, page)
                val listJavtiful = getJavtifulHome(limit / 2, page)
                (list123 + listJavtiful).distinctBy { it.id }
            }
        }
    }

    suspend fun search(providerId: String, query: String, limit: Int = 20, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()
        val pid = providerId.lowercase().trim()

        when (pid) {
            "123av", "javplayer" -> search123Av(cleanQuery, limit, page)
            "javtiful" -> searchJavtiful(cleanQuery, limit, page)
            "jav_all", "all_jav" -> searchAllJav(cleanQuery, limit, page)
            else -> {
                val res1 = search123Av(cleanQuery, limit, page)
                val res2 = searchJavtiful(cleanQuery, limit, page)
                (res1 + res2).distinctBy { it.id }
            }
        }
    }

    // ----------------------------------------------------
    // 123AV Extractor
    // ----------------------------------------------------
    private fun get123AvHome(limit: Int, page: Int): List<VideoItem> {
        val url = if (page <= 1) "https://123av.com/en" else "https://123av.com/en/new?page=$page"
        return parse123AvPage(url, limit)
    }

    private fun search123Av(query: String, limit: Int, page: Int): List<VideoItem> {
        val encoded = try { URLEncoder.encode(query, "UTF-8") } catch (e: Exception) { query }
        val url = if (page <= 1) {
            "https://123av.com/en/search?keyword=$encoded"
        } else {
            "https://123av.com/en/search?keyword=$encoded&page=$page"
        }
        return parse123AvPage(url, limit)
    }

    private fun parse123AvPage(url: String, limit: Int): List<VideoItem> {
        val items = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder().url(url).build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return emptyList()

            val html = resp.body?.string() ?: return emptyList()
            val doc = Jsoup.parse(html)
            val cards = doc.select(".card, article, .video-card, .thumb-block")

            for (card in cards) {
                if (items.size >= limit) break
                val linkEl = card.select("a.card__link, a.card__cover, a[href*='/en/v/'], a[href*='/v/']").firstOrNull() ?: continue
                val href = linkEl.attr("href")
                if (href.isBlank()) continue

                val codeMatch = Regex("/(?:en/)?v/([^/\\?]+)").find(href)
                val rawId = codeMatch?.groupValues?.get(1) ?: href.substringAfterLast("/")

                val title = card.select(".card__title, .title, h3").text().trim().ifBlank {
                    linkEl.attr("title").ifBlank { rawId.uppercase() }
                }

                val imgEl = card.select("img.card__img, img").firstOrNull()
                var thumb = imgEl?.attr("src")?.ifBlank { null } ?: imgEl?.attr("data-src")?.ifBlank { null }
                if (thumb != null && thumb.startsWith("//")) thumb = "https:$thumb"

                val durText = card.select(".card__dur, .duration, span.badge").text().trim()
                val durationSec = parseDurationToSeconds(durText)

                val viewsText = card.select(".card__views, .views").text().trim()
                val views = parseViewsCount(viewsText)

                items.add(
                    VideoItem(
                        id = "123av_$rawId",
                        title = title,
                        uploaderName = "123AV / JAVPlayer",
                        thumbnailUrl = thumb,
                        durationSeconds = durationSec,
                        viewCount = views,
                        providerId = "123av",
                        description = "123AV JAV Direct Stream • Code: ${rawId.uppercase()}"
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "123AV parsing error for $url: ${e.message}")
        }
        return items
    }

    // ----------------------------------------------------
    // Javtiful Extractor
    // ----------------------------------------------------
    private fun getJavtifulHome(limit: Int, page: Int): List<VideoItem> {
        val url = if (page <= 1) "https://javtiful.com" else "https://javtiful.com/trending?page=$page"
        return parseJavtifulPage(url, limit)
    }

    private fun searchJavtiful(query: String, limit: Int, page: Int): List<VideoItem> {
        val encoded = try { URLEncoder.encode(query, "UTF-8") } catch (e: Exception) { query }
        val url = if (page <= 1) {
            "https://javtiful.com/search?q=$encoded"
        } else {
            "https://javtiful.com/search?q=$encoded&page=$page"
        }
        return parseJavtifulPage(url, limit)
    }

    private fun parseJavtifulPage(url: String, limit: Int): List<VideoItem> {
        val items = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder().url(url).build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return emptyList()

            val html = resp.body?.string() ?: return emptyList()
            val doc = Jsoup.parse(html)
            val cards = doc.select(".front-video-card, article.front-video-card, .video-item")

            for (card in cards) {
                if (items.size >= limit) break
                val linkEl = card.select("a.front-video-title, a.front-video-thumb, a[href*='/video/']").firstOrNull() ?: continue
                val href = linkEl.attr("href")
                if (href.isBlank()) continue

                val idMatch = Regex("/video/(\\d+)(?:/([^/?]+))?").find(href)
                val rawId = if (idMatch != null) {
                    val numId = idMatch.groupValues[1]
                    val slug = idMatch.groupValues.getOrNull(2) ?: ""
                    if (slug.isNotBlank()) "${numId}_$slug" else numId
                } else {
                    href.substringAfterLast("/")
                }

                val title = card.select(".front-video-title, .title").text().trim().ifBlank {
                    linkEl.text().trim().ifBlank { rawId.uppercase() }
                }

                val imgEl = card.select("img").firstOrNull()
                var thumb = imgEl?.attr("data-front-lazy-src")?.ifBlank { null }
                    ?: imgEl?.attr("src")?.ifBlank { null }
                if (thumb != null && thumb.startsWith("/")) thumb = "https://javtiful.com$thumb"
                if (thumb != null && thumb.startsWith("//")) thumb = "https:$thumb"

                val durText = card.select(".front-duration-tag, .duration").text().trim()
                val durationSec = parseDurationToSeconds(durText)

                val statText = card.select(".front-video-stat, .views").text().trim()
                val views = parseViewsCount(statText)

                items.add(
                    VideoItem(
                        id = "javtiful_$rawId",
                        title = title,
                        uploaderName = "Javtiful",
                        thumbnailUrl = thumb,
                        durationSeconds = durationSec,
                        viewCount = views,
                        providerId = "javtiful",
                        description = "Javtiful High Speed Stream • ID: $rawId"
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Javtiful parsing error for $url: ${e.message}")
        }
        return items
    }

    // ----------------------------------------------------
    // Aggregator (All JAV Sources)
    // ----------------------------------------------------
    private suspend fun getAllJavHome(limit: Int, page: Int): List<VideoItem> = coroutineScope {
        val d1 = async { get123AvHome(limit, page) }
        val d2 = async { getJavtifulHome(limit, page) }

        val res1 = d1.await()
        val res2 = d2.await()

        val interleaved = mutableListOf<VideoItem>()
        val maxLen = maxOf(res1.size, res2.size)
        for (i in 0 until maxLen) {
            if (i < res1.size) interleaved.add(res1[i])
            if (i < res2.size) interleaved.add(res2[i])
        }
        interleaved.distinctBy { it.id }.take(limit)
    }

    private suspend fun searchAllJav(query: String, limit: Int, page: Int): List<VideoItem> = coroutineScope {
        val d1 = async { search123Av(query, limit, page) }
        val d2 = async { searchJavtiful(query, limit, page) }

        val res1 = d1.await()
        val res2 = d2.await()

        (res1 + res2).distinctBy { it.id }.take(limit)
    }

    private fun parseViewsCount(raw: String): Long {
        if (raw.isBlank()) return 0L
        val clean = raw.lowercase().replace(",", "").trim()
        return try {
            when {
                clean.endsWith("m") -> (clean.removeSuffix("m").toDouble() * 1_000_000).toLong()
                clean.endsWith("k") -> (clean.removeSuffix("k").toDouble() * 1_000).toLong()
                else -> clean.filter { it.isDigit() }.toLongOrNull() ?: 0L
            }
        } catch (e: Exception) {
            0L
        }
    }
}
