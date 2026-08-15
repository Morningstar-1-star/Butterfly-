package com.example.plugin.jav.providers

import com.example.plugin.jav.JavMetadata
import com.example.plugin.jav.MetadataProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

private val sharedClient = OkHttpClient.Builder()
    .connectTimeout(6, TimeUnit.SECONDS)
    .readTimeout(8, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

/**
 * JavLibrary & JavBus Web Metadata Scraper
 */
class JavLibraryBusMetadataProvider : MetadataProvider {
    override val id: String = "javlibrary_javbus"
    override val name: String = "JavLibrary & JavBus Scraper"
    override var isEnabled: Boolean = true

    override suspend fun fetchMetadata(javId: String): JavMetadata? = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().uppercase()
        // Primary: JavLibrary search
        try {
            val url = "https://www.javlibrary.com/en/vl_searchbyid.php?keyword=$cleanJavId"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            val res = sharedClient.newCall(req).execute()
            val html = res.body?.string() ?: ""

            val titleMatch = Pattern.compile("<h3 class=\"post-title text\">(.*?)</h3>").matcher(html)
            val title = if (titleMatch.find()) titleMatch.group(1)?.replace(Regex("<.*?>"), "")?.trim() ?: "" else ""

            val coverMatch = Pattern.compile("id=\"video_cover\" src=\"(.*?)\"").matcher(html)
            val coverUrl = if (coverMatch.find()) {
                val raw = coverMatch.group(1) ?: ""
                if (raw.startsWith("//")) "https:$raw" else raw
            } else ""

            val studioMatch = Pattern.compile("rel=\"tag\">(.*?)</a></span>").matcher(html)
            val studio = if (studioMatch.find()) studioMatch.group(1) ?: "" else ""

            if (title.isNotBlank() && coverUrl.isNotBlank()) {
                return@withContext JavMetadata(
                    javId = cleanJavId,
                    title = title,
                    coverUrl = coverUrl,
                    studio = studio,
                    overallConfidenceScore = 92
                )
            }
        } catch (e: Exception) {
            // Fallback to JavBus
        }

        // Secondary: JavBus
        try {
            val busUrl = "https://www.javbus.com/en/$cleanJavId"
            val req = Request.Builder().url(busUrl).header("User-Agent", "Mozilla/5.0").build()
            val res = sharedClient.newCall(req).execute()
            val html = res.body?.string() ?: ""
            if (!res.isSuccessful || html.contains("404 Not Found")) return@withContext null

            val coverMatch = Pattern.compile("class=\"bigImage\" href=\"(.*?)\"").matcher(html)
            val coverUrl = if (coverMatch.find()) {
                val raw = coverMatch.group(1) ?: ""
                if (raw.startsWith("/")) "https://www.javbus.com$raw" else raw
            } else ""

            val titleMatch = Pattern.compile("<h3>(.*?)</h3>").matcher(html)
            val title = if (titleMatch.find()) titleMatch.group(1)?.trim() ?: "" else ""

            if (title.isNotBlank() && coverUrl.isNotBlank()) {
                return@withContext JavMetadata(
                    javId = cleanJavId,
                    title = title,
                    coverUrl = coverUrl,
                    studio = "JavBus Studio",
                    overallConfidenceScore = 88
                )
            }
        } catch (e: Exception) {
            // Silently handled
        }

        null
    }
}

/**
 * Jav321 Form Search Metadata Provider
 */
class Jav321MetadataProvider : MetadataProvider {
    override val id: String = "jav321_search"
    override val name: String = "Jav321 Search Database"
    override var isEnabled: Boolean = true

    override suspend fun fetchMetadata(javId: String): JavMetadata? = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().uppercase()
        try {
            val url = "https://www.jav321.com/search"
            val formBody = FormBody.Builder().add("sn", cleanJavId).build()
            val req = Request.Builder()
                .url(url)
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            val res = sharedClient.newCall(req).execute()
            val html = res.body?.string() ?: ""
            if (!res.isSuccessful || html.isBlank() || html.contains("No video found")) return@withContext null

            val titleMatch = Pattern.compile("<h3>(.*?)</h3>").matcher(html)
            val title = if (titleMatch.find()) titleMatch.group(1)?.replace(Regex("<.*?>"), "")?.trim() ?: "" else ""

            val coverMatch = Pattern.compile("img-thumbnail\" src=\"(.*?)\"").matcher(html)
            val coverUrl = if (coverMatch.find()) coverMatch.group(1) ?: "" else ""

            val studioMatch = Pattern.compile("Maker:</b>\\s*<a.*?>(.*?)</a>").matcher(html)
            val studio = if (studioMatch.find()) studioMatch.group(1) ?: "" else ""

            if (title.isNotBlank() && coverUrl.isNotBlank()) {
                return@withContext JavMetadata(
                    javId = cleanJavId,
                    title = title,
                    coverUrl = coverUrl,
                    studio = studio,
                    overallConfidenceScore = 90
                )
            }
        } catch (e: Exception) {
            null
        }
        null
    }
}

/**
 * JavDB Catalog Metadata Provider
 */
class JavDbMetadataProvider : MetadataProvider {
    override val id: String = "javdb_catalog"
    override val name: String = "JavDB Catalog"
    override var isEnabled: Boolean = true

    override suspend fun fetchMetadata(javId: String): JavMetadata? = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().uppercase()
        try {
            val url = "https://javdb.com/search?q=$cleanJavId&f=all"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            val res = sharedClient.newCall(req).execute()
            val html = res.body?.string() ?: ""
            if (html.isBlank()) return@withContext null

            val linkMatch = Pattern.compile("href=\"(/v/[a-zA-Z0-9]+)\"").matcher(html)
            if (linkMatch.find()) {
                val detailPath = linkMatch.group(1) ?: ""
                val detailUrl = "https://javdb.com$detailPath"
                val detailReq = Request.Builder().url(detailUrl).header("User-Agent", "Mozilla/5.0").build()
                val detailRes = sharedClient.newCall(detailReq).execute()
                val detailHtml = detailRes.body?.string() ?: ""

                val titleMatch = Pattern.compile("<h2 class=\"title is-4\">(.*?)</h2>", Pattern.DOTALL).matcher(detailHtml)
                val title = if (titleMatch.find()) titleMatch.group(1)?.replace(Regex("<.*?>"), "")?.trim() ?: "" else ""

                val imgMatch = Pattern.compile("class=\"cover\" src=\"(.*?)\"").matcher(detailHtml)
                val coverUrl = if (imgMatch.find()) imgMatch.group(1) ?: "" else ""

                if (title.isNotBlank() && coverUrl.isNotBlank()) {
                    return@withContext JavMetadata(
                        javId = cleanJavId,
                        title = title,
                        coverUrl = coverUrl,
                        overallConfidenceScore = 85
                    )
                }
            }
        } catch (e: Exception) {
            null
        }
        null
    }
}

/**
 * JavMenu Search Engine Metadata Provider
 */
class JavMenuMetadataProvider : MetadataProvider {
    override val id: String = "javmenu_search"
    override val name: String = "JavMenu Search Engine"
    override var isEnabled: Boolean = true

    override suspend fun fetchMetadata(javId: String): JavMetadata? = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().uppercase()
        try {
            val url = "https://javmenu.com/en/search?q=$cleanJavId"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            val res = sharedClient.newCall(req).execute()
            val html = res.body?.string() ?: ""
            if (!res.isSuccessful || html.isBlank()) return@withContext null

            val coverMatch = Pattern.compile("<img.*?src=\"(https://.*?)\".*?class=\"card-img-top\"").matcher(html)
            val coverUrl = if (coverMatch.find()) coverMatch.group(1) ?: "" else ""

            val titleMatch = Pattern.compile("<h5 class=\"card-title\">(.*?)</h5>").matcher(html)
            val title = if (titleMatch.find()) titleMatch.group(1)?.trim() ?: "" else ""

            if (title.isNotBlank() && coverUrl.isNotBlank()) {
                return@withContext JavMetadata(
                    javId = cleanJavId,
                    title = title,
                    coverUrl = coverUrl,
                    overallConfidenceScore = 80
                )
            }
        } catch (e: Exception) {
            null
        }
        null
    }
}

/**
 * GFriends GitHub CDN Avatar Provider
 */
class GFriendsAvatarProvider : MetadataProvider {
    override val id: String = "gfriends_cdn"
    override val name: String = "GFriends GitHub CDN"
    override var isEnabled: Boolean = true

    override suspend fun fetchMetadata(javId: String): JavMetadata? = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().uppercase()
        try {
            val busUrl = "https://www.javbus.com/en/$cleanJavId"
            val req = Request.Builder().url(busUrl).header("User-Agent", "Mozilla/5.0").build()
            val res = sharedClient.newCall(req).execute()
            val html = res.body?.string() ?: ""
            if (!res.isSuccessful || html.isBlank()) return@withContext null

            val actorMatch = Pattern.compile("<a href=\"https://www.javbus.com/en/star/.*?\">(.*?)</a>").matcher(html)
            val actorName = if (actorMatch.find()) actorMatch.group(1)?.trim() ?: "" else ""

            if (actorName.isNotBlank()) {
                val gfriendsUrl = "https://raw.githubusercontent.com/gfriends/gfriends/master/Content/File/$actorName.jpg"
                val headReq = Request.Builder().url(gfriendsUrl).head().build()
                val headRes = sharedClient.newCall(headReq).execute()
                if (headRes.isSuccessful) {
                    return@withContext JavMetadata(
                        javId = cleanJavId,
                        title = "$cleanJavId - Star: $actorName",
                        coverUrl = gfriendsUrl,
                        actors = listOf(actorName),
                        overallConfidenceScore = 95
                    )
                }
            }
        } catch (e: Exception) {
            null
        }
        null
    }
}

/**
 * AirAV Barcode API Metadata Provider
 */
class AirAvBarcodeMetadataProvider : MetadataProvider {
    override val id: String = "airav_barcode"
    override val name: String = "AirAV Barcode API"
    override var isEnabled: Boolean = true

    override suspend fun fetchMetadata(javId: String): JavMetadata? = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().uppercase()
        try {
            val url = "https://www.airav.wiki/api/video/barcode?barcode=$cleanJavId"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            val res = sharedClient.newCall(req).execute()
            val jsonStr = res.body?.string() ?: ""
            if (!res.isSuccessful || jsonStr.isBlank()) return@withContext null

            val json = JSONObject(jsonStr)
            val status = json.optString("status")
            if (status == "ok" || json.has("result")) {
                val result = json.optJSONObject("result") ?: json
                val name = result.optString("name")
                val imgUrl = result.optString("img_url")
                val factory = result.optString("factory")

                if (name.isNotBlank() && imgUrl.isNotBlank()) {
                    return@withContext JavMetadata(
                        javId = cleanJavId,
                        title = name,
                        coverUrl = imgUrl,
                        studio = factory,
                        overallConfidenceScore = 88
                    )
                }
            }
        } catch (e: Exception) {
            null
        }
        null
    }
}

/**
 * Arzon Adult Catalog Metadata Provider
 */
class ArzonCatalogMetadataProvider : MetadataProvider {
    override val id: String = "arzon_catalog"
    override val name: String = "Arzon Adult Catalog"
    override var isEnabled: Boolean = true

    override suspend fun fetchMetadata(javId: String): JavMetadata? = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().uppercase()
        try {
            val url = "https://www.arzon.jp/itemlist.html?q=$cleanJavId"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Cookie", "age_check=1")
                .build()
            val res = sharedClient.newCall(req).execute()
            val html = res.body?.string() ?: ""
            if (!res.isSuccessful || html.isBlank() || html.contains("該当する商品は見つかりませんでした")) return@withContext null

            val imgMatch = Pattern.compile("img src=\"(https://.*?arzon.jp/item/.*?)\"").matcher(html)
            val coverUrl = if (imgMatch.find()) imgMatch.group(1) ?: "" else ""

            val titleMatch = Pattern.compile("<a href=\".*?\"><img.*?alt=\"(.*?)\"").matcher(html)
            val title = if (titleMatch.find()) titleMatch.group(1)?.trim() ?: "" else ""

            if (title.isNotBlank() && coverUrl.isNotBlank()) {
                return@withContext JavMetadata(
                    javId = cleanJavId,
                    title = title,
                    coverUrl = coverUrl,
                    overallConfidenceScore = 82
                )
            }
        } catch (e: Exception) {
            null
        }
        null
    }
}
