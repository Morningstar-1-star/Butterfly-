package com.example.bunkr.resolver

import android.util.Log
import com.example.bunkr.model.BunkrAlbum
import com.example.bunkr.model.BunkrException
import com.example.bunkr.model.BunkrFile
import com.example.bunkr.model.BunkrUrlType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class BunkrAlbumCrawler(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    companion object {
        private const val TAG = "BunkrAlbumCrawler"
    }

    suspend fun crawlAlbum(albumUrl: String): Pair<BunkrAlbum, List<BunkrFile>> = withContext(Dispatchers.IO) {
        val parsed = BunkrUrlUtils.parseUrl(albumUrl)
            ?: throw BunkrException.InvalidUrlException(albumUrl)

        if (parsed.type != BunkrUrlType.ALBUM) {
            throw BunkrException.InvalidUrlException("Expected album URL (/a/...), got ${parsed.canonicalUrl}")
        }

        val canonicalUrl = parsed.canonicalUrl
        val albumId = parsed.id

        val request = Request.Builder()
            .url(canonicalUrl)
            .apply {
                BunkrUrlUtils.buildDefaultHeaders(canonicalUrl).forEach { (k, v) ->
                    addHeader(k, v)
                }
            }
            .build()

        val html = try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 429) {
                        throw BunkrException.RateLimitedException(parsed.domain)
                    }
                    throw BunkrException.AlbumUnavailableException(albumId, "HTTP ${response.code}")
                }
                response.body?.string() ?: ""
            }
        } catch (e: Exception) {
            if (e is BunkrException) throw e
            throw BunkrException.NetworkTimeoutException(canonicalUrl)
        }

        if (html.isBlank()) {
            throw BunkrException.AlbumUnavailableException(albumId, "Empty response body")
        }

        val doc = Jsoup.parse(html, canonicalUrl)

        // 1. Extract Album Title
        var albumTitle = ""
        val h1Text = doc.select("h1").text().trim()
        if (h1Text.isNotBlank()) {
            albumTitle = h1Text
        } else {
            val titleText = doc.title().trim()
            albumTitle = titleText.replace(" - Bunkr", "", ignoreCase = true)
                .replace(" - Bunkr.cr", "", ignoreCase = true)
                .replace("Bunkr - ", "", ignoreCase = true)
                .trim()
        }
        if (albumTitle.isBlank()) {
            albumTitle = "Bunkr Album $albumId"
        }

        val discoveredFiles = mutableListOf<BunkrFile>()
        val seenFileIds = mutableSetOf<String>()

        // 2. Try parsing Next.js __NEXT_DATA__ JSON payload first (most reliable)
        val nextDataScript = doc.select("script#__NEXT_DATA__").firstOrNull()
        if (nextDataScript != null) {
            try {
                val jsonStr = nextDataScript.data()
                val json = JSONObject(jsonStr)
                val pageProps = json.optJSONObject("props")?.optJSONObject("pageProps")
                val albumObj = pageProps?.optJSONObject("album")
                val filesArray = albumObj?.optJSONArray("files")
                    ?: pageProps?.optJSONArray("files")

                val jsonTitle = albumObj?.optString("name") ?: albumObj?.optString("title")
                if (!jsonTitle.isNullOrBlank()) {
                    albumTitle = jsonTitle
                }

                if (filesArray != null) {
                    parseFilesFromJsonArray(filesArray, albumId, discoveredFiles, seenFileIds)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed to parse __NEXT_DATA__ on $albumId, falling back to DOM scraping: ${e.message}")
            }
        }

        // 3. Fallback / supplementary DOM scraping using Jsoup
        if (discoveredFiles.isEmpty()) {
            parseFilesFromDom(doc, albumId, discoveredFiles, seenFileIds)
        }

        if (discoveredFiles.isEmpty()) {
            Log.w(TAG, "No files found in album $albumId ($canonicalUrl)")
        }

        val album = BunkrAlbum(
            albumId = albumId,
            title = albumTitle,
            sourceUrl = canonicalUrl,
            isEnabled = true,
            lastScanTime = System.currentTimeMillis(),
            itemCount = discoveredFiles.size,
            createdAt = System.currentTimeMillis()
        )

        return@withContext Pair(album, discoveredFiles)
    }

    private fun parseFilesFromJsonArray(
        filesArray: JSONArray,
        albumId: String,
        outFiles: MutableList<BunkrFile>,
        seenFileIds: MutableSet<String>
    ) {
        for (i in 0 until filesArray.length()) {
            val item = filesArray.optJSONObject(i) ?: continue
            val fileId = item.optString("id").takeIf { it.isNotBlank() }
                ?: item.optString("slug").takeIf { it.isNotBlank() }
                ?: item.optString("name").takeIf { it.isNotBlank() }
                ?: continue

            if (!seenFileIds.add(fileId)) continue

            val title = item.optString("name").takeIf { it.isNotBlank() }
                ?: item.optString("title").takeIf { it.isNotBlank() }
                ?: "Bunkr File $fileId"

            val sourceUrl = "https://${BunkrUrlUtils.DEFAULT_BUNKR_DOMAIN}/f/$fileId"
            val thumbnail = item.optString("thumbnail").takeIf { it.isNotBlank() }
                ?: item.optString("poster").takeIf { it.isNotBlank() }
                ?: item.optString("icon").takeIf { it.isNotBlank() }

            val size = item.optString("formattedSize").takeIf { it.isNotBlank() }
                ?: item.optString("size").takeIf { it.isNotBlank() }
                ?: ""

            val mediaType = item.optString("mediaType").takeIf { it.isNotBlank() }
                ?: if (title.endsWith(".mp4", true) || title.endsWith(".mkv", true) || title.endsWith(".mov", true) || title.endsWith(".webm", true)) "video" else "media"

            val bunkrFile = BunkrFile(
                fileId = fileId,
                albumId = albumId,
                title = title,
                sourceUrl = sourceUrl,
                thumbnailUrl = thumbnail,
                mediaType = mediaType,
                fileSize = size,
                orderIndex = i,
                lastUpdated = System.currentTimeMillis()
            )
            outFiles.add(bunkrFile)
        }
    }

    private fun parseFilesFromDom(
        doc: org.jsoup.nodes.Document,
        albumId: String,
        outFiles: MutableList<BunkrFile>,
        seenFileIds: MutableSet<String>
    ) {
        // Select links pointing to /f/ or /v/
        val links = doc.select("a[href*=/f/], a[href*=/v/]")
        var orderIndex = 0

        for (link in links) {
            val href = link.attr("abs:href").ifBlank { link.attr("href") }
            val parsedInfo = BunkrUrlUtils.parseUrl(href) ?: continue

            if (parsedInfo.type != BunkrUrlType.FILE) continue
            val fileId = parsedInfo.id

            if (!seenFileIds.add(fileId)) continue

            // Determine title
            var title = link.text().trim()
            if (title.isBlank() || title.equals("download", true) || title.equals("view", true)) {
                val img = link.select("img").firstOrNull()
                val alt = img?.attr("alt")?.trim()
                if (!alt.isNullOrBlank()) {
                    title = alt
                }
            }
            if (title.isBlank()) {
                val card = link.parents().select(".box-item, .grid-images_cell, div").firstOrNull()
                val cardTitle = card?.select(".title, p, h2, h3, span")?.firstOrNull()?.text()?.trim()
                if (!cardTitle.isNullOrBlank()) {
                    title = cardTitle
                }
            }
            if (title.isBlank()) {
                title = "Video $fileId"
            }

            // Thumbnail
            val img = link.select("img").firstOrNull()
            val thumbUrl = img?.attr("abs:src")?.ifBlank { img.attr("src") }

            val bunkrFile = BunkrFile(
                fileId = fileId,
                albumId = albumId,
                title = title,
                sourceUrl = parsedInfo.canonicalUrl,
                thumbnailUrl = thumbUrl,
                mediaType = "video",
                orderIndex = orderIndex++,
                lastUpdated = System.currentTimeMillis()
            )
            outFiles.add(bunkrFile)
        }
    }
}
