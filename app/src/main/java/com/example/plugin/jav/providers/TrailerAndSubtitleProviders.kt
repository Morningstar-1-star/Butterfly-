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
        val req = Request.Builder().url(url).head().header("User-Agent", "Mozilla/5.0").build()
        val res = trailerClient.newCall(req).execute()
        res.isSuccessful || res.code == 302 || res.code == 206
    } catch (e: Exception) {
        false
    }
}

/**
 * DMM FreePV CDN Sample Trailer Provider
 */
class DmmFreePvTrailerProvider : TrailerProvider {
    override val id: String = "dmm_freepv"
    override val name: String = "DMM FreePV CDN Sample"
    override var isEnabled: Boolean = true

    override suspend fun fetchTrailers(javId: String): List<JavTrailer> = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().uppercase()
        val results = mutableListOf<JavTrailer>()

        try {
            val f = cleanJavId.lowercase().replace("-", "").replace("_", "")
            if (f.length >= 3) {
                val candidateUrls = listOf(
                    "https://cc3001.dmm.co.jp/litevideo/freepv/${f.take(1)}/${f.take(3)}/$f/${f}_mbf_w.mp4",
                    "https://cc3001.dmm.co.jp/litevideo/freepv/${f.take(1)}/${f.take(3)}/$f/${f}_dmb_w.mp4",
                    "https://cc3001.dmm.co.jp/litevideo/freepv/${f.take(1)}/${f.take(3)}/$f/${f}_sm_w.mp4"
                )
                val thumbUrl = "https://images.dmm.co.jp/digital/video/$f/${f}ps.jpg"

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
                        break
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
 * SubtitleCat Engine Subtitle Provider
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
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            val res = trailerClient.newCall(req).execute()
            val html = res.body?.string() ?: ""

            val subMatch = Pattern.compile("href=\"(sub-.*?\\.html)\"").matcher(html)
            if (subMatch.find()) {
                val subPage = subMatch.group(1) ?: ""
                val subPageUrl = "https://www.subtitlecat.com/$subPage"
                val pageReq = Request.Builder().url(subPageUrl).header("User-Agent", "Mozilla/5.0").build()
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
                    val langName = obj.optString("LanguageName", "English")
                    val iso = obj.optString("ISO639", "en")
                    val format = obj.optString("SubFormat", "srt")

                    if (downloadLink.isNotBlank() && verifyUrl(downloadLink)) {
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
        } catch (e: Exception) {
            // Silently handled
        }

        results
    }
}
