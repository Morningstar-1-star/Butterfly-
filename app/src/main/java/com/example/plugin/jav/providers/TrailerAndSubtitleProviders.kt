package com.example.plugin.jav.providers

import com.example.plugin.jav.JavSubtitle
import com.example.plugin.jav.JavTrailer
import com.example.plugin.jav.SubtitleProvider
import com.example.plugin.jav.TrailerProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

private val trailerClient = OkHttpClient.Builder()
    .connectTimeout(6, TimeUnit.SECONDS)
    .readTimeout(8, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

private fun verifyUrl(url: String): Boolean {
    return try {
        val req = Request.Builder()
            .url(url)
            .head()
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()
        val res = trailerClient.newCall(req).execute()
        res.isSuccessful || res.code == 302 || res.code == 206
    } catch (e: Exception) {
        false
    }
}

/**
 * 1. SubtitleCat Engine Subtitle Provider
 * Strictly validates that search results and downloaded subtitles match the requested JAV ID.
 */
class SubtitleCatProvider : SubtitleProvider {
    override val id: String = "subtitlecat"
    override val name: String = "SubtitleCat Engine"
    override var isEnabled: Boolean = true

    override suspend fun searchSubtitles(javId: String, title: String): List<JavSubtitle> = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().uppercase()
        val results = mutableListOf<JavSubtitle>()

        try {
            val url = "https://www.subtitlecat.com/index.php?search=$cleanJavId"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            val res = trailerClient.newCall(req).execute()
            val html = res.body?.string() ?: ""

            // Pattern for sub pages: href="(sub-.*?.html)"
            val matcher = Pattern.compile("href=\"(sub-.*?\\.html)\"[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE).matcher(html)
            while (matcher.find()) {
                val subPage = matcher.group(1) ?: ""
                val subTitle = matcher.group(2) ?: ""

                // Ensure result strictly matches JAV ID
                val cleanSubTitle = subTitle.replace(Regex("<.*?>"), "").uppercase()
                if (cleanSubTitle.contains(cleanJavId) || subPage.uppercase().contains(cleanJavId.replace("-", ""))) {
                    val subPageUrl = "https://www.subtitlecat.com/$subPage"
                    val pageReq = Request.Builder()
                        .url(subPageUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .build()
                    val pageRes = trailerClient.newCall(pageReq).execute()
                    val pageHtml = pageRes.body?.string() ?: ""

                    val downloadMatch = Pattern.compile("href=\"(download.php\\?.*?)\"").matcher(pageHtml)
                    if (downloadMatch.find()) {
                        val directDownloadUrl = "https://www.subtitlecat.com/" + downloadMatch.group(1)
                        if (verifyUrl(directDownloadUrl)) {
                            results.add(
                                JavSubtitle(
                                    id = "subcat_$cleanJavId",
                                    javId = cleanJavId,
                                    language = "English",
                                    languageCode = "en",
                                    url = directDownloadUrl,
                                    format = "srt",
                                    providerId = id,
                                    matchScore = 95
                                )
                            )
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Silently handled
        }

        results
    }
}

/**
 * 2. DMM FreePV CDN Sample Trailer Provider (JAV-Preview)
 * Generates official DMM / FANZA preview CID formats (padded CIDs) and checks DMM CDN for HTTP 200.
 */
class DmmFreePvTrailerProvider : TrailerProvider {
    override val id: String = "dmm_freepv"
    override val name: String = "DMM FreePV CDN Sample (JAV-Preview)"
    override var isEnabled: Boolean = true

    override suspend fun fetchTrailers(javId: String): List<JavTrailer> = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().uppercase()
        val results = mutableListOf<JavTrailer>()

        try {
            // Convert e.g. "IPX-800" to "ipx00800" and "ipx800"
            val parts = cleanJavId.split("-", "_")
            val candidateCids = mutableListOf<String>()

            if (parts.size >= 2) {
                val prefix = parts[0].lowercase()
                val numStr = parts[1].lowercase()
                val paddedNum = numStr.padStart(5, '0')
                candidateCids.add("$prefix$paddedNum")
                candidateCids.add("$prefix$numStr")
                candidateCids.add("h_123$prefix$paddedNum")
                candidateCids.add("1$prefix$paddedNum")
            } else {
                val f = cleanJavId.lowercase().replace("-", "").replace("_", "")
                candidateCids.add(f)
            }

            for (cid in candidateCids) {
                if (cid.length < 3) continue
                val c1 = cid.take(1)
                val c3 = cid.take(3)

                val candidateUrls = listOf(
                    "https://cc3001.dmm.co.jp/litevideo/freepv/$c1/$c3/$cid/${cid}_mbf_w.mp4",
                    "https://cc3001.dmm.co.jp/litevideo/freepv/$c1/$c3/$cid/${cid}_dmb_w.mp4",
                    "https://cc3001.dmm.co.jp/litevideo/freepv/$c1/$c3/$cid/${cid}_sm_w.mp4",
                    "https://cc3001.dmm.co.jp/litevideo/freepv/$c1/$c3/$cid/${cid}_dm_w.mp4"
                )
                val thumbUrl = "https://images.dmm.co.jp/digital/video/$cid/${cid}ps.jpg"

                for (vUrl in candidateUrls) {
                    if (verifyUrl(vUrl)) {
                        results.add(
                            JavTrailer(
                                id = "preview_$cleanJavId",
                                javId = cleanJavId,
                                title = "$cleanJavId Official DMM Preview",
                                videoUrl = vUrl,
                                thumbnailUrl = thumbUrl,
                                durationSeconds = 120L,
                                providerId = id,
                                providerName = name
                            )
                        )
                        return@withContext results
                    }
                }
            }
        } catch (e: Exception) {
            // Silently handled
        }

        results
    }
}

/**
 * OpenSubtitles REST API Provider
 */
class OpenSubtitlesRestProvider : SubtitleProvider {
    override val id: String = "opensubtitles_rest"
    override val name: String = "OpenSubtitles REST API"
    override var isEnabled: Boolean = true

    override suspend fun searchSubtitles(javId: String, title: String): List<JavSubtitle> = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().uppercase()
        val results = mutableListOf<JavSubtitle>()

        try {
            val openSubsUrl = "https://rest.opensubtitles.org/search/query-$cleanJavId/sublanguageid-eng,jpn,zho"
            val req = Request.Builder()
                .url(openSubsUrl)
                .header("User-Agent", "TemporaryUserAgent")
                .build()
            val res = trailerClient.newCall(req).execute()
            val jsonStr = res.body?.string() ?: ""
            if (jsonStr.isNotBlank() && jsonStr.startsWith("[")) {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length().coerceAtMost(5)) {
                    val obj = array.getJSONObject(i)
                    val downloadLink = obj.optString("SubDownloadLink")
                    val subFileName = obj.optString("SubFileName", "").uppercase()
                    val langName = obj.optString("LanguageName", "English")
                    val iso = obj.optString("ISO639", "en")
                    val format = obj.optString("SubFormat", "srt")

                    // Strictly verify JAV ID match in filename or query
                    if (downloadLink.isNotBlank() && (subFileName.contains(cleanJavId) || subFileName.contains(cleanJavId.replace("-", "")))) {
                        if (verifyUrl(downloadLink)) {
                            results.add(
                                JavSubtitle(
                                    id = "opensubs_${cleanJavId}_$i",
                                    javId = cleanJavId,
                                    language = langName,
                                    languageCode = iso,
                                    url = downloadLink,
                                    format = format,
                                    providerId = id,
                                    matchScore = 90
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Silently handled
        }

        results
    }
}
