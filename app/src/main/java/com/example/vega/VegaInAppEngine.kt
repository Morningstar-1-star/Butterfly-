package com.example.vega

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

object VegaInAppEngine {
    private const val TAG = "VegaInAppEngine"

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun init(context: Context) {
        VegaProviderRegistry.initFromAssets(context)
    }

    suspend fun search(providerId: String, query: String): List<VegaSearchResult> = withContext(Dispatchers.IO) {
        val cleanProv = providerId.trim().lowercase()
        val baseUrl = VegaProviderRegistry.getBaseUrl(cleanProv).trimEnd('/')
        if (baseUrl.isBlank()) {
            Log.d(TAG, "No base URL configured for provider $providerId")
            return@withContext emptyList()
        }

        val cleanQuery = query.trim()
        val results = mutableListOf<VegaSearchResult>()

        try {
            // Strategy 1: Instant JSON search.php (Used by VegaMovies, MoviesDrive, etc.)
            val jsonResults = searchWithSearchPhp(cleanProv, baseUrl, cleanQuery)
            if (jsonResults.isNotEmpty()) {
                return@withContext jsonResults
            }

            // Strategy 2: WordPress / HTML Search (?s=query or /search/query)
            val htmlResults = searchWithHtmlQuery(cleanProv, baseUrl, cleanQuery)
            if (htmlResults.isNotEmpty()) {
                return@withContext htmlResults
            }
        } catch (e: Exception) {
            Log.w(TAG, "Search error for $cleanProv: ${e.message}")
        }

        return@withContext results
    }

    private suspend fun searchWithSearchPhp(providerId: String, baseUrl: String, query: String): List<VegaSearchResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<VegaSearchResult>()
        try {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            val targetUrl = "$baseUrl/search.php?q=$encoded"

            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "$baseUrl/")
                .header("Accept", "application/json, text/plain, */*")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext results
                val bodyStr = resp.body?.string().orEmpty().trim()
                if (!bodyStr.startsWith("{")) return@withContext results

                val json = JSONObject(bodyStr)
                val hits = json.optJSONArray("hits") ?: return@withContext results

                for (i in 0 until hits.length()) {
                    val hit = hits.optJSONObject(i) ?: continue
                    val doc = hit.optJSONObject("document") ?: continue
                    val title = doc.optString("post_title")
                        .replace("Download", "", ignoreCase = true)
                        .trim()
                    val permalink = doc.optString("permalink")
                    if (title.isBlank() || permalink.isBlank()) continue

                    val thumb = doc.optString("post_thumbnail")
                    val rating = doc.optString("rating").ifBlank { doc.optString("imdb_id") }

                    val fullLink = if (permalink.startsWith("http")) permalink else "$baseUrl${if (permalink.startsWith("/")) "" else "/"}$permalink"

                    results.add(
                        VegaSearchResult(
                            id = fullLink,
                            title = title,
                            link = fullLink,
                            imageUrl = thumb.ifBlank { null },
                            providerId = providerId,
                            extraInfo = rating.ifBlank { null }
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return@withContext results
    }

    private suspend fun searchWithHtmlQuery(providerId: String, baseUrl: String, query: String): List<VegaSearchResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<VegaSearchResult>()
        try {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            val searchUrls = listOf(
                "$baseUrl/?s=$encoded",
                "$baseUrl/page/1/?s=$encoded",
                "$baseUrl/search/$encoded"
            )

            for (url in searchUrls) {
                try {
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", "$baseUrl/")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .build()

                    val html = httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use null
                        resp.body?.string()
                    } ?: continue

                    val doc = Jsoup.parse(html, baseUrl)
                    val cards = doc.select("article, .post, .post-item, .movie-card, .poster-card, .entry-list-item, .movies-grid a, .card-grid a, .latestPost, .entry, .item, .film-item, .flw-item, .ml-item, .result-item, .search-result, .blog-items article, .post-list article")

                    for (card in cards) {
                        val anchor = if (card.tagName().equals("a", ignoreCase = true)) card else card.select("a").firstOrNull() ?: continue
                        val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
                        if (href.isBlank() || href == baseUrl || href == "$baseUrl/" || href.contains("wp-comments") || href.contains("category/")) continue

                        val titleEl = card.select(".movie-card-title, .entry-title, .poster-title, .title, .film-name, .mli-info h2, .details h3, h2, h3, h1").firstOrNull()
                        val rawTitle = titleEl?.text()?.ifBlank { anchor.attr("title") } ?: anchor.text()
                        val cleanTitle = rawTitle.replace("Download", "", ignoreCase = true).trim()
                        if (cleanTitle.isBlank() || cleanTitle.length < 2) continue

                        val img = card.select("img").firstOrNull()
                        val thumb = img?.absUrl("src")
                            ?.ifBlank { img.attr("data-src") }
                            ?.ifBlank { img.attr("data-lazy-src") }
                            ?.ifBlank { img.attr("data-original") }
                            ?.ifBlank { img.attr("data-cfsrc") }
                            ?.ifBlank { img.attr("src") }

                        val rating = card.select(".imdb-score, .rating, .badge, .quality-badge, .poster-quality").firstOrNull()?.text()

                        results.add(
                            VegaSearchResult(
                                id = href,
                                title = cleanTitle,
                                link = href,
                                imageUrl = thumb?.ifBlank { null },
                                providerId = providerId,
                                extraInfo = rating?.ifBlank { null }
                            )
                        )
                    }

                    if (results.isNotEmpty()) break
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        return@withContext results.distinctBy { it.link }
    }

    suspend fun getMeta(providerId: String, pageUrl: String): VegaMetaResult? = withContext(Dispatchers.IO) {
        val cleanProv = providerId.trim().lowercase()
        val baseUrl = VegaProviderRegistry.getBaseUrl(cleanProv).trimEnd('/')
        val targetUrl = if (pageUrl.startsWith("http")) pageUrl else "$baseUrl${if (pageUrl.startsWith("/")) "" else "/"}$pageUrl"

        try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "$baseUrl/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body?.string().orEmpty()
            }

            if (html.isBlank()) return@withContext null
            val doc = Jsoup.parse(html, targetUrl)

            val rawTitle = doc.select(".entry-title, .post-title, h1, .movie-title").firstOrNull()?.text()
                ?.replace("Download", "", ignoreCase = true)?.trim()
                ?: doc.title().replace("Download", "", ignoreCase = true).substringBefore("-").trim()

            val synopsis = doc.select(".entry-content p, .storyline, .synopsis, .description").firstOrNull { it.text().length > 30 }?.text()

            val poster = doc.select(".entry-content img, .poster img, .movie-poster img").firstOrNull()?.let { img ->
                img.absUrl("src").ifBlank { img.attr("data-src") }.ifBlank { img.attr("src") }
            }

            val linkList = mutableListOf<VegaLinkList>()
            val downloadAnchors = doc.select("a.dwd-button, a.btn, a[href*='drive'], a[href*='fastdl'], a[href*='hubcloud'], a[href*='vcloud'], a[href*='nexdrive'], a[href*='gdflix'], a[href*='pixeldrain'], a[href*='gofile']")

            val qualityBuckets = mutableMapOf<String, MutableList<VegaDirectLink>>()

            for (a in downloadAnchors) {
                val href = a.absUrl("href").ifBlank { a.attr("href") }
                if (href.isBlank() || href.contains("wp-comments") || href.contains("vegamovies-apk")) continue

                val anchorText = a.text().trim()
                val parentText = a.parent()?.text().orEmpty()
                val combinedText = "$anchorText $parentText".lowercase()

                val quality = when {
                    combinedText.contains("2160p") || combinedText.contains("4k") -> "4K 2160p"
                    combinedText.contains("1080p") -> "1080p Full HD"
                    combinedText.contains("720p") -> "720p HD"
                    combinedText.contains("480p") -> "480p SD"
                    else -> "HD 720p"
                }

                val title = if (anchorText.isNotBlank()) anchorText else "Download ($quality)"

                qualityBuckets.getOrPut(quality) { mutableListOf() }.add(
                    VegaDirectLink(
                        title = title,
                        link = href,
                        type = "movie"
                    )
                )
            }

            qualityBuckets.forEach { (q, links) ->
                linkList.add(
                    VegaLinkList(
                        title = "$rawTitle ($q)",
                        quality = q,
                        directLinks = links.distinctBy { it.link }
                    )
                )
            }

            if (linkList.isEmpty()) {
                val allLinks = doc.select("a[href*='download'], a[href*='link'], a.btn")
                    .mapNotNull { el ->
                        val h = el.absUrl("href").ifBlank { el.attr("href") }
                        if (h.isNotBlank() && h.startsWith("http") && !h.contains("vegamovies") && !h.contains("facebook")) {
                            VegaDirectLink(title = el.text().ifBlank { "Direct Download" }, link = h)
                        } else null
                    }
                    .distinctBy { it.link }

                if (allLinks.isNotEmpty()) {
                    linkList.add(
                        VegaLinkList(
                            title = rawTitle,
                            quality = "1080p HD",
                            directLinks = allLinks
                        )
                    )
                }
            }

            return@withContext VegaMetaResult(
                title = rawTitle,
                synopsis = synopsis,
                image = poster,
                poster = poster,
                type = "movie",
                linkList = linkList
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing meta for $targetUrl: ${e.message}")
        }
        return@withContext null
    }

    suspend fun getEpisodes(providerId: String, episodesLink: String): List<VegaEpisode> = withContext(Dispatchers.IO) {
        val episodes = mutableListOf<VegaEpisode>()
        try {
            val req = Request.Builder()
                .url(episodesLink)
                .header("User-Agent", USER_AGENT)
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext episodes
                resp.body?.string().orEmpty()
            }

            if (html.isBlank()) return@withContext episodes
            val doc = Jsoup.parse(html, episodesLink)
            val anchors = doc.select("a:contains(Episode), a:contains(Ep), a.btn, a[href*='download']")

            var count = 1
            for (a in anchors) {
                val href = a.absUrl("href").ifBlank { a.attr("href") }
                if (href.isBlank()) continue
                val text = a.text().trim()
                episodes.add(
                    VegaEpisode(
                        title = text.ifBlank { "Episode $count" },
                        link = href,
                        episodeNumber = count
                    )
                )
                count++
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error getting episodes: ${e.message}")
        }
        return@withContext episodes
    }

    suspend fun getStream(providerId: String, link: String, type: String = "movie"): List<VegaStreamResult> = withContext(Dispatchers.IO) {
        try {
            val streams = VegaExtractorEngine.extractStreams(link)
            if (streams.isNotEmpty()) {
                return@withContext streams
            }

            if (link.contains("nexdrive") || link.contains("drive") || link.contains("link")) {
                val req = Request.Builder()
                    .url(link)
                    .header("User-Agent", USER_AGENT)
                    .build()

                val html = httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
                }

                if (html.isNotBlank()) {
                    val doc = Jsoup.parse(html, link)
                    val subLinks = doc.select("a[href*='fastdl'], a[href*='vcloud'], a[href*='hubcloud'], a[href*='pixeldrain'], a[href*='filepress'], a[href*='gofile']")
                    for (a in subLinks) {
                        val subHref = a.absUrl("href").ifBlank { a.attr("href") }
                        if (subHref.isNotBlank()) {
                            val subStreams = VegaExtractorEngine.extractStreams(subHref)
                            if (subStreams.isNotEmpty()) return@withContext subStreams
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving stream for $link: ${e.message}")
        }
        return@withContext emptyList()
    }
}
