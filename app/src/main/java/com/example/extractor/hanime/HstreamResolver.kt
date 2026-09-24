package com.example.extractor.hanime

import android.util.Log
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import com.example.model.StreamData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * High-Speed Direct Stream Extractor for Hstream / Anime Mirrors.
 * Bypasses Cloudflare by leveraging open REST endpoints & media CDN clusters.
 */
object HstreamResolver {
    private const val TAG = "HstreamResolver"
    private const val BASE_URL = "https://hstream.moe"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val list = cookieStore.getOrPut(url.host) { mutableListOf() }
                synchronized(list) {
                    list.removeAll { old -> cookies.any { it.name == old.name } }
                    list.addAll(cookies)
                }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: emptyList()
            }
        })
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Resolves a direct playable stream given an anime slug or title.
     */
    suspend fun resolveStream(slugOrTitle: String): StreamData? = withContext(Dispatchers.IO) {
        val cleanSlug = slugOrTitle.lowercase().trim()
            .replace(Regex("""^hanimetv:|^hanime1:|^hanime:|\.html$"""), "")
            .trim('/')

        if (cleanSlug.isBlank()) return@withContext null

        try {
            // 1. Fetch initial cookies and CSRF token from base or direct episode page
            val targetEpisodeUrl = "$BASE_URL/hentai/$cleanSlug"
            var html = fetchPageHtml(targetEpisodeUrl)
            var episodeUrl = targetEpisodeUrl

            if (html.isNullOrBlank() || !html.contains("e_id")) {
                // Try searching for the title / slug
                val foundUrl = searchEpisodeUrl(cleanSlug)
                if (!foundUrl.isNullOrBlank()) {
                    episodeUrl = foundUrl
                    html = fetchPageHtml(episodeUrl)
                }
            }

            if (html.isNullOrBlank()) {
                Log.d(TAG, "No HTML page found on Hstream for $cleanSlug")
                return@withContext null
            }

            val csrf = extractCsrfToken(html)
            val episodeId = extractEpisodeId(html)

            if (csrf.isBlank() || episodeId.isBlank()) {
                Log.d(TAG, "Missing CSRF ($csrf) or episodeId ($episodeId) for $cleanSlug")
                return@withContext null
            }

            // 2. Call player API
            val apiReqBody = JSONObject().apply {
                put("episode_id", episodeId.toIntOrNull() ?: episodeId)
            }

            val apiRequest = Request.Builder()
                .url("$BASE_URL/player/api")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, text/plain, */*")
                .header("Content-Type", "application/json")
                .header("Origin", BASE_URL)
                .header("Referer", episodeUrl)
                .header("X-CSRF-TOKEN", csrf)
                .header("X-Requested-With", "XMLHttpRequest")
                .post(apiReqBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val apiResp = client.newCall(apiRequest).execute()
            if (!apiResp.isSuccessful) {
                Log.d(TAG, "Hstream player API failed: code=${apiResp.code}")
                return@withContext null
            }

            val respBody = apiResp.body?.string() ?: return@withContext null
            val apiJson = JSONObject(respBody)

            val title = apiJson.optString("title", cleanSlug)
            val streamPath = apiJson.optString("stream_url", "")
            val poster = apiJson.optString("poster", "")
            val fullPoster = if (poster.startsWith("http")) poster else if (poster.isNotBlank()) "$BASE_URL$poster" else ""

            val streamDomains = mutableListOf<String>()
            val domainsArr = apiJson.optJSONArray("stream_domains")
            if (domainsArr != null) {
                for (i in 0 until domainsArr.length()) {
                    val d = domainsArr.optString(i)
                    if (d.isNotBlank()) streamDomains.add(d.trimEnd('/'))
                }
            }
            val asiaDomainsArr = apiJson.optJSONArray("asia_stream_domains")
            if (asiaDomainsArr != null) {
                for (i in 0 until asiaDomainsArr.length()) {
                    val d = asiaDomainsArr.optString(i)
                    if (d.isNotBlank() && !streamDomains.contains(d.trimEnd('/'))) {
                        streamDomains.add(d.trimEnd('/'))
                    }
                }
            }

            if (streamDomains.isEmpty() || streamPath.isBlank()) {
                Log.d(TAG, "Hstream player API returned no stream domains or path")
                return@withContext null
            }

            val primaryDomain = streamDomains.first()
            val mediaHeaders = mapOf(
                "Referer" to "$BASE_URL/",
                "Origin" to BASE_URL,
                "User-Agent" to USER_AGENT
            )

            val options = mutableListOf<PlayableStreamOption>()

            // 1080p MP4
            val mp41080Url = "$primaryDomain/$streamPath/x264.1080p.mp4"
            options.add(
                PlayableStreamOption(
                    qualityLabel = "1080p FHD MP4",
                    format = "mp4",
                    isMuxed = true,
                    videoUrl = mp41080Url,
                    providerType = ProviderType.OTHER,
                    headers = mediaHeaders
                )
            )

            // 720p MP4
            val mp4720Url = "$primaryDomain/$streamPath/x264.720p.mp4"
            options.add(
                PlayableStreamOption(
                    qualityLabel = "720p HD MP4",
                    format = "mp4",
                    isMuxed = true,
                    videoUrl = mp4720Url,
                    providerType = ProviderType.OTHER,
                    headers = mediaHeaders
                )
            )

            // MPD Manifest (Adaptive DASH)
            val mpdUrl = "$primaryDomain/$streamPath/720/manifest.mpd"
            options.add(
                PlayableStreamOption(
                    qualityLabel = "Adaptive DASH",
                    format = "mpd",
                    isMuxed = true,
                    videoUrl = mpdUrl,
                    providerType = ProviderType.OTHER,
                    headers = mediaHeaders
                )
            )

            // Fallback backup domain
            if (streamDomains.size > 1) {
                val backupDomain = streamDomains[1]
                options.add(
                    PlayableStreamOption(
                        qualityLabel = "720p HD (Backup Server)",
                        format = "mp4",
                        isMuxed = true,
                        videoUrl = "$backupDomain/$streamPath/x264.720p.mp4",
                        providerType = ProviderType.OTHER,
                        headers = mediaHeaders
                    )
                )
            }

            val selectedOption = options[1] // prefer 720p MP4 for guaranteed fast buffering

            return@withContext StreamData(
                videoId = cleanSlug,
                videoUrl = selectedOption.videoUrl ?: mp4720Url,
                title = title,
                channelName = "Hanime Anime Stream",
                thumbnailUrl = fullPoster,
                description = "High definition anime stream from Hstream network.",
                availableStreamOptions = options,
                selectedStreamOption = selectedOption,
                providerId = "hanime1",
                headers = mediaHeaders
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error in Hstream stream resolution: ${e.message}")
            null
        }
    }

    private fun fetchPageHtml(url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", "$BASE_URL/")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun searchEpisodeUrl(query: String): String? {
        return try {
            // First get homepage to get cookies and CSRF
            val homeHtml = fetchPageHtml(BASE_URL) ?: return null
            val csrf = extractCsrfToken(homeHtml)
            if (csrf.isBlank()) return null

            val simplified = query.replace("-", " ").replace(Regex("""\b(episode|ep|\d+)\b.*"""), "").trim()
            val postData = "_token=${URLEncoder.encode(csrf, "UTF-8")}&search=${URLEncoder.encode(simplified.ifBlank { query }, "UTF-8")}"

            val req = Request.Builder()
                .url("$BASE_URL/search")
                .header("User-Agent", USER_AGENT)
                .header("Referer", "$BASE_URL/")
                .header("Origin", BASE_URL)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(postData.toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull()))
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val html = resp.body?.string() ?: return null
                val doc = Jsoup.parse(html)
                val searchContainer = doc.select(".search-results, #results, .anime-list, main").firstOrNull() ?: doc
                val links = searchContainer.select("a[href*='/hentai/']")
                val queryWords = query.lowercase().split("-", "_", " ").filter { it.length >= 3 }
                for (a in links) {
                    val href = a.attr("href")
                    val text = a.text().lowercase()
                    if (href.contains("/hentai/")) {
                        // Must match at least one significant query word to avoid false positives
                        val matched = queryWords.isNotEmpty() && queryWords.any { text.contains(it) || href.contains(it) }
                        if (matched) {
                            return if (href.startsWith("http")) href else "$BASE_URL$href"
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun extractCsrfToken(html: String): String {
        val m = Pattern.compile("""(?:csrf=[\"']([^\"']+)[\"']|name=[\"']csrf-token[\"']\s+content=[\"']([^\"']+)[\"'])""").matcher(html)
        if (m.find()) {
            return m.group(1) ?: m.group(2) ?: ""
        }
        return ""
    }

    private fun extractEpisodeId(html: String): String {
        val m = Pattern.compile("""(?:id=[\"']e_id[\"'][^>]*value=[\"']([^\"']+)[\"']|value=[\"']([^\"']+)[\"'][^>]*id=[\"']e_id[\"'])""").matcher(html)
        if (m.find()) {
            return m.group(1) ?: m.group(2) ?: ""
        }
        return ""
    }
}
