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
 * 1. Javinizer-Go Scraper Engine
 * Uses Javinizer-Go's native multi-source pipeline (R18 / MGStage searcher & NFO parser).
 */
class JavinizerGoMetadataProvider : MetadataProvider {
    override val id: String = "javinizer_go"
    override val name: String = "Javinizer-Go Engine"
    override var isEnabled: Boolean = true

    override suspend fun fetchMetadata(javId: String): JavMetadata? = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().uppercase()
        val rawNum = cleanJavId.replace("-", "")

        // Primary: R18 API/Search logic from Javinizer-Go
        try {
            val r18Url = "https://www.r18.com/common/search/searchword=$cleanJavId"
            val req = Request.Builder()
                .url(r18Url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            val res = sharedClient.newCall(req).execute()
            val html = res.body?.string() ?: ""

            if (res.isSuccessful && html.contains("data-item-id")) {
                val imgMatch = Pattern.compile("<img.*?src=\"(https://.*?r18\\.com/.*?\\.jpg)\"").matcher(html)
                val coverUrl = if (imgMatch.find()) imgMatch.group(1) ?: "" else ""

                val titleMatch = Pattern.compile("class=\"txt-name\">(.*?)</span>").matcher(html)
                val title = if (titleMatch.find()) titleMatch.group(1)?.trim() ?: "" else ""

                val makerMatch = Pattern.compile("class=\"maker\">(.*?)</span>").matcher(html)
                val studio = if (makerMatch.find()) makerMatch.group(1)?.trim() ?: "" else ""

                val upperTitle = title.uppercase()
                if ((upperTitle.contains(cleanJavId) || upperTitle.contains(rawNum) || html.uppercase().contains(cleanJavId)) && coverUrl.isNotBlank()) {
                    return@withContext JavMetadata(
                        javId = cleanJavId,
                        title = title.ifBlank { "$cleanJavId (R18 Javinizer)" },
                        coverUrl = coverUrl.replace("js-", "pl-").replace("-", "pl"),
                        studio = studio,
                        overallConfidenceScore = 91
                    )
                }
            }
        } catch (e: Exception) {
            // Fallback to DMM / MGStage
        }

        // Secondary: Javinizer DMM Logic Fallback
        try {
            val parts = cleanJavId.split("-", "_")
            if (parts.size >= 2) {
                val prefix = parts[0].lowercase()
                val numStr = parts[1].lowercase()
                val paddedNum = numStr.padStart(5, '0')
                val dmmCid = "$prefix$paddedNum"
                
                val dmmUrl = "https://www.dmm.co.jp/mono/dvd/-/detail/=/cid=$dmmCid/"
                val req = Request.Builder()
                    .url(dmmUrl)
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Cookie", "age_check_done=1")
                    .build()
                val res = sharedClient.newCall(req).execute()
                val html = res.body?.string() ?: ""

                if (res.isSuccessful && html.isNotBlank()) {
                    val titleMatch = Pattern.compile("id=\"title\">(.*?)</h1>").matcher(html)
                    val title = if (titleMatch.find()) titleMatch.group(1)?.trim() ?: "" else ""
                    val imgMatch = Pattern.compile("href=\"(https://pics.dmm.co.jp/mono/movie/.+?pl.jpg)\"").matcher(html)
                    val coverUrl = if (imgMatch.find()) imgMatch.group(1) ?: "" else ""
                    
                    if (title.isNotBlank() && coverUrl.isNotBlank()) {
                        return@withContext JavMetadata(
                            javId = cleanJavId,
                            title = title,
                            coverUrl = coverUrl,
                            studio = "DMM (Javinizer)",
                            overallConfidenceScore = 90
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Silently handled
        }

        // Secondary: MGStage Search logic from Javinizer-Go
        try {
            val mgUrl = "https://www.mgstage.com/search/c/0?search_word=$cleanJavId"
            val req = Request.Builder()
                .url(mgUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Cookie", "adc=1")
                .build()
            val res = sharedClient.newCall(req).execute()
            val html = res.body?.string() ?: ""

            if (res.isSuccessful && html.contains("search_list")) {
                val imgMatch = Pattern.compile("<img.*?src=\"(https://.*?mgstage\\.com/images/.*?\\.jpg)\"").matcher(html)
                val coverUrl = if (imgMatch.find()) imgMatch.group(1) ?: "" else ""

                val titleMatch = Pattern.compile("<h5 class=\"title\">(.*?)</h5>").matcher(html)
                val title = if (titleMatch.find()) titleMatch.group(1)?.replace(Regex("<.*?>"), "")?.trim() ?: "" else ""

                val upperTitle = title.uppercase()
                if ((upperTitle.contains(cleanJavId) || upperTitle.contains(rawNum) || html.uppercase().contains(cleanJavId)) && coverUrl.isNotBlank()) {
                    return@withContext JavMetadata(
                        javId = cleanJavId,
                        title = title,
                        coverUrl = coverUrl,
                        studio = "MGStage Studio",
                        overallConfidenceScore = 89
                    )
                }
            }
        } catch (e: Exception) {
            // Silently handled
        }

        null
    }
}

/**
 * 2. AVM (Adult Video Manager) Engine
 * Uses AVM's native FC2 Club & DMM CID search scraper logic.
 */
class AvmMetadataProvider : MetadataProvider {
    override val id: String = "avm_engine"
    override val name: String = "AVM (Adult Video Manager)"
    override var isEnabled: Boolean = true

    override suspend fun fetchMetadata(javId: String): JavMetadata? = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().uppercase()
        val rawNum = cleanJavId.replace("-", "")

        // Primary: FC2 Club parser for FC2 IDs
        if (cleanJavId.startsWith("FC2")) {
            try {
                val fc2Num = cleanJavId.replace(Regex("[^0-9]"), "")
                val url = "https://fc2club.com/html/FC2-$fc2Num.html"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                val res = sharedClient.newCall(req).execute()
                val html = res.body?.string() ?: ""

                if (res.isSuccessful && html.isNotBlank()) {
                    val titleMatch = Pattern.compile("<h3>(.*?)</h3>").matcher(html)
                    val title = if (titleMatch.find()) titleMatch.group(1)?.trim() ?: "" else ""

                    val imgMatch = Pattern.compile("class=\"img-thumbnail\" src=\"(.*?)\"").matcher(html)
                    val coverUrl = if (imgMatch.find()) {
                        val raw = imgMatch.group(1) ?: ""
                        if (raw.startsWith("/")) "https://fc2club.com$raw" else raw
                    } else ""

                    if (coverUrl.isNotBlank()) {
                        return@withContext JavMetadata(
                            javId = cleanJavId,
                            title = title.ifBlank { cleanJavId },
                            coverUrl = coverUrl,
                            studio = "FC2",
                            overallConfidenceScore = 93
                        )
                    }
                }
            } catch (e: Exception) {
                // Fallthrough to DMM CID Search
            }
        }

        // Secondary: DMM CID Search logic from AVM
        try {
            val dmmUrl = "https://www.dmm.co.jp/search/=/searchstr=$cleanJavId"
            val req = Request.Builder()
                .url(dmmUrl)
                .header("User-Agent", "Mozilla/5.0")
                .header("Cookie", "age_check_done=1")
                .build()
            val res = sharedClient.newCall(req).execute()
            val html = res.body?.string() ?: ""

            if (res.isSuccessful && html.contains("m-equalHeight")) {
                val imgMatch = Pattern.compile("src=\"(https://images\\.dmm\\.co\\.jp/digital/video/.*?\\.jpg)\"").matcher(html)
                val coverUrl = if (imgMatch.find()) imgMatch.group(1) ?: "" else ""

                val titleMatch = Pattern.compile("<p class=\"txt\">(.*?)</p>").matcher(html)
                val title = if (titleMatch.find()) titleMatch.group(1)?.replace(Regex("<.*?>"), "")?.trim() ?: "" else ""

                val upperTitle = title.uppercase()
                if ((upperTitle.contains(cleanJavId) || upperTitle.contains(rawNum) || html.uppercase().contains(cleanJavId)) && coverUrl.isNotBlank()) {
                    return@withContext JavMetadata(
                        javId = cleanJavId,
                        title = title,
                        coverUrl = coverUrl,
                        studio = "DMM FANZA",
                        overallConfidenceScore = 88
                    )
                }
            }
        } catch (e: Exception) {
            // Silently handled
        }

        null
    }
}

/**
 * 3. Javdex Indexer Engine
 * Uses Javdex's explicit session cookies (`Cookie: over18=1; locale=en; theme=light`) on JavDB.
 */
class JavdexMetadataProvider : MetadataProvider {
    override val id: String = "javdex"
    override val name: String = "Javdex Indexer"
    override var isEnabled: Boolean = true

    override suspend fun fetchMetadata(javId: String): JavMetadata? = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().uppercase()
        val rawNum = cleanJavId.replace("-", "")

        try {
            val url = "https://javdb.com/search?q=$cleanJavId&f=all"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Cookie", "over18=1; locale=en; theme=light")
                .build()
            val res = sharedClient.newCall(req).execute()
            val html = res.body?.string() ?: ""

            if (res.isSuccessful && html.contains("/v/")) {
                val linkMatch = Pattern.compile("href=\"(/v/[a-zA-Z0-9]+)\"").matcher(html)
                if (linkMatch.find()) {
                    val detailPath = linkMatch.group(1) ?: ""
                    val detailUrl = "https://javdb.com$detailPath"
                    val detailReq = Request.Builder()
                        .url(detailUrl)
                        .header("User-Agent", "Mozilla/5.0")
                        .header("Cookie", "over18=1; locale=en")
                        .build()
                    val detailRes = sharedClient.newCall(detailReq).execute()
                    val detailHtml = detailRes.body?.string() ?: ""

                    val titleMatch = Pattern.compile("<h2 class=\"title is-4\">(.*?)</h2>", Pattern.DOTALL).matcher(detailHtml)
                    val title = if (titleMatch.find()) titleMatch.group(1)?.replace(Regex("<.*?>"), "")?.trim() ?: "" else ""

                    val imgMatch = Pattern.compile("class=\"cover\" src=\"(.*?)\"").matcher(detailHtml)
                    val coverUrl = if (imgMatch.find()) imgMatch.group(1) ?: "" else ""

                    val scoreMatch = Pattern.compile("class=\"score\">(.*?)</span>").matcher(detailHtml)
                    val score = if (scoreMatch.find()) scoreMatch.group(1)?.trim() ?: "" else ""

                    val upperTitle = title.uppercase()
                    if ((upperTitle.contains(cleanJavId) || upperTitle.contains(rawNum) || detailHtml.uppercase().contains(cleanJavId)) && coverUrl.isNotBlank()) {
                        return@withContext JavMetadata(
                            javId = cleanJavId,
                            title = if (score.isNotBlank()) "$title [$score]" else title,
                            coverUrl = coverUrl,
                            studio = "Javdex Verified",
                            overallConfidenceScore = 90
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Silently handled
        }

        null
    }
}

/**
 * 4. OpenAver Scraper Engine
 * Uses OpenAver's native JavMenu and JavBooks API scraper pipeline.
 */
class OpenAverMetadataProvider : MetadataProvider {
    override val id: String = "openaver"
    override val name: String = "OpenAver Engine"
    override var isEnabled: Boolean = true

    override suspend fun fetchMetadata(javId: String): JavMetadata? = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().uppercase()
        val rawNum = cleanJavId.replace("-", "")

        // Primary: JavMenu API from OpenAver
        try {
            val url = "https://javmenu.com/en/search?q=$cleanJavId"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            val res = sharedClient.newCall(req).execute()
            val html = res.body?.string() ?: ""

            if (res.isSuccessful && html.contains("card-img-top")) {
                val coverMatch = Pattern.compile("<img.*?src=\"(https://.*?)\".*?class=\"card-img-top\"").matcher(html)
                val coverUrl = if (coverMatch.find()) coverMatch.group(1) ?: "" else ""

                val titleMatch = Pattern.compile("<h5 class=\"card-title\">(.*?)</h5>").matcher(html)
                val title = if (titleMatch.find()) titleMatch.group(1)?.trim() ?: "" else ""

                val upperTitle = title.uppercase()
                if ((upperTitle.contains(cleanJavId) || upperTitle.contains(rawNum) || html.uppercase().contains(cleanJavId)) && coverUrl.isNotBlank()) {
                    return@withContext JavMetadata(
                        javId = cleanJavId,
                        title = title,
                        coverUrl = coverUrl,
                        studio = "OpenAver JavMenu",
                        overallConfidenceScore = 87
                    )
                }
            }
        } catch (e: Exception) {
            // Silently handled
        }

        null
    }
}

/**
 * 5. MDCx Engine (Movie Data Capture x)
 * Uses MDCx's native AirAV Barcode API & MGStage `adc=1` age-gate bypass parser.
 */
class MdcxMetadataProvider : MetadataProvider {
    override val id: String = "mdcx"
    override val name: String = "MDCx Engine"
    override var isEnabled: Boolean = true

    override suspend fun fetchMetadata(javId: String): JavMetadata? = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().uppercase()
        val rawNum = cleanJavId.replace("-", "")

        // Primary: MDCx AirAV Barcode API plugin
        try {
            val url = "https://www.airav.wiki/api/video/barcode?barcode=$cleanJavId"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "MDCx Python/3.10 Scraper")
                .build()
            val res = sharedClient.newCall(req).execute()
            val jsonStr = res.body?.string() ?: ""

            if (res.isSuccessful && jsonStr.isNotBlank()) {
                val json = JSONObject(jsonStr)
                val status = json.optString("status")
                if (status == "ok" || json.has("result")) {
                    val result = json.optJSONObject("result") ?: json
                    val name = result.optString("name")
                    val imgUrl = result.optString("img_url")
                    val factory = result.optString("factory")

                    val upperName = name.uppercase()
                    if ((upperName.contains(cleanJavId) || upperName.contains(rawNum)) && imgUrl.isNotBlank()) {
                        return@withContext JavMetadata(
                            javId = cleanJavId,
                            title = name,
                            coverUrl = imgUrl,
                            studio = factory.ifBlank { "MDCx AirAV" },
                            overallConfidenceScore = 92
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Fallthrough to MGStage plugin
        }

        // Secondary: MDCx MGStage plugin (`Cookie: adc=1`)
        try {
            val mgUrl = "https://www.mgstage.com/product/product_detail/$cleanJavId/"
            val req = Request.Builder()
                .url(mgUrl)
                .header("User-Agent", "MDCx Python/3.10 Scraper")
                .header("Cookie", "adc=1")
                .build()
            val res = sharedClient.newCall(req).execute()
            val html = res.body?.string() ?: ""

            if (res.isSuccessful && html.contains("detail_data")) {
                val imgMatch = Pattern.compile("id=\"EnlargeImage\" href=\"(https://.*?mgstage\\.com/images/.*?\\.jpg)\"").matcher(html)
                val coverUrl = if (imgMatch.find()) imgMatch.group(1) ?: "" else ""

                val titleMatch = Pattern.compile("<h1 class=\"tag\">(.*?)</h1>").matcher(html)
                val title = if (titleMatch.find()) titleMatch.group(1)?.trim() ?: "" else ""

                val upperTitle = title.uppercase()
                if ((upperTitle.contains(cleanJavId) || upperTitle.contains(rawNum) || html.uppercase().contains(cleanJavId)) && coverUrl.isNotBlank()) {
                    return@withContext JavMetadata(
                        javId = cleanJavId,
                        title = title,
                        coverUrl = coverUrl,
                        studio = "MDCx MGStage",
                        overallConfidenceScore = 90
                    )
                }
            }
        } catch (e: Exception) {
            // Silently handled
        }

        null
    }
}

/**
 * 6. FSS (Film Scraper System)
 * Uses FSS's native Arzon `age_check=1` adult catalog scraper logic.
 */
class FssMetadataProvider : MetadataProvider {
    override val id: String = "fss"
    override val name: String = "FSS Film Scraper System"
    override var isEnabled: Boolean = true

    override suspend fun fetchMetadata(javId: String): JavMetadata? = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().uppercase()
        val rawNum = cleanJavId.replace("-", "")

        try {
            val url = "https://www.arzon.jp/itemlist.html?q=$cleanJavId"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "FSS Go Scraper Engine/2.0")
                .header("Cookie", "age_check=1")
                .build()
            val res = sharedClient.newCall(req).execute()
            val html = res.body?.string() ?: ""

            if (res.isSuccessful && html.contains("arzon.jp/item/")) {
                val imgMatch = Pattern.compile("img src=\"(https://.*?arzon\\.jp/item/.*?)\"").matcher(html)
                val coverUrl = if (imgMatch.find()) imgMatch.group(1) ?: "" else ""

                val titleMatch = Pattern.compile("<a href=\".*?\"><img.*?alt=\"(.*?)\"").matcher(html)
                val title = if (titleMatch.find()) titleMatch.group(1)?.trim() ?: "" else ""

                val upperTitle = title.uppercase()
                if ((upperTitle.contains(cleanJavId) || upperTitle.contains(rawNum) || html.uppercase().contains(cleanJavId)) && coverUrl.isNotBlank()) {
                    return@withContext JavMetadata(
                        javId = cleanJavId,
                        title = title,
                        coverUrl = coverUrl,
                        studio = "FSS Arzon Catalog",
                        overallConfidenceScore = 86
                    )
                }
            }
        } catch (e: Exception) {
            // Silently handled
        }

        null
    }
}

/**
 * Standard JavLibrary & JavBus Metadata Scraper
 */
class JavLibraryBusMetadataProvider : MetadataProvider {
    override val id: String = "javlibrary_javbus"
    override val name: String = "JavLibrary & JavBus Scraper"
    override var isEnabled: Boolean = true

    override suspend fun fetchMetadata(javId: String): JavMetadata? = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().uppercase()
        val rawNum = cleanJavId.replace("-", "")

        try {
            val busUrl = "https://www.javbus.com/en/$cleanJavId"
            val req = Request.Builder()
                .url(busUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            val res = sharedClient.newCall(req).execute()
            val html = res.body?.string() ?: ""
            if (res.isSuccessful && !html.contains("404 Not Found")) {
                val coverMatch = Pattern.compile("class=\"bigImage\" href=\"(.*?)\"").matcher(html)
                val coverUrl = if (coverMatch.find()) {
                    val raw = coverMatch.group(1) ?: ""
                    if (raw.startsWith("/")) "https://www.javbus.com$raw" else raw
                } else ""

                val titleMatch = Pattern.compile("<h3>(.*?)</h3>").matcher(html)
                val title = if (titleMatch.find()) titleMatch.group(1)?.trim() ?: "" else ""

                val upperTitle = title.uppercase()
                if ((upperTitle.contains(cleanJavId) || upperTitle.contains(rawNum) || html.uppercase().contains(cleanJavId)) && coverUrl.isNotBlank()) {
                    return@withContext JavMetadata(
                        javId = cleanJavId,
                        title = title.ifBlank { cleanJavId },
                        coverUrl = coverUrl,
                        studio = "JavBus Studio",
                        overallConfidenceScore = 88
                    )
                }
            }
        } catch (e: Exception) {
            // Silently handled
        }

        null
    }
}

/**
 * Standard Jav321 Form Search Metadata Provider
 */
class Jav321MetadataProvider : MetadataProvider {
    override val id: String = "jav321_search"
    override val name: String = "Jav321 Search Database"
    override var isEnabled: Boolean = true

    override suspend fun fetchMetadata(javId: String): JavMetadata? = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().uppercase()
        val rawNum = cleanJavId.replace("-", "")

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

            val upperTitle = title.uppercase()
            if ((upperTitle.contains(cleanJavId) || upperTitle.contains(rawNum) || html.uppercase().contains(cleanJavId)) && coverUrl.isNotBlank()) {
                return@withContext JavMetadata(
                    javId = cleanJavId,
                    title = title,
                    coverUrl = coverUrl,
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
 * Standard JavDB Catalog Metadata Provider
 */
class JavDbMetadataProvider : MetadataProvider {
    override val id: String = "javdb_catalog"
    override val name: String = "JavDB Catalog"
    override var isEnabled: Boolean = true

    override suspend fun fetchMetadata(javId: String): JavMetadata? = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().uppercase()
        val rawNum = cleanJavId.replace("-", "")

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

                val upperTitle = title.uppercase()
                if ((upperTitle.contains(cleanJavId) || upperTitle.contains(rawNum) || detailHtml.uppercase().contains(cleanJavId)) && coverUrl.isNotBlank()) {
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
 * Standard JavMenu Search Engine Metadata Provider
 */
class JavMenuMetadataProvider : MetadataProvider {
    override val id: String = "javmenu_search"
    override val name: String = "JavMenu Search Engine"
    override var isEnabled: Boolean = true

    override suspend fun fetchMetadata(javId: String): JavMetadata? = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().uppercase()
        val rawNum = cleanJavId.replace("-", "")

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

            val upperTitle = title.uppercase()
            if ((upperTitle.contains(cleanJavId) || upperTitle.contains(rawNum) || html.uppercase().contains(cleanJavId)) && coverUrl.isNotBlank()) {
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
 * Standard GFriends GitHub CDN Avatar Provider
 */
class GFriendsAvatarProvider : MetadataProvider {
    override val id: String = "gfriends_cdn"
    override val name: String = "GFriends GitHub CDN (Filetree)"
    override var isEnabled: Boolean = true

    private var fileTreeCache: JSONObject? = null
    private var fileTreeTimestamp: Long = 0

    private suspend fun getFileTree(): JSONObject? = withContext(Dispatchers.IO) {
        if (fileTreeCache != null && System.currentTimeMillis() - fileTreeTimestamp < 86400_000) {
            return@withContext fileTreeCache
        }
        try {
            val url = "https://raw.githubusercontent.com/gfriends/gfriends/master/Content/Filetree.json"
            val req = Request.Builder().url(url).build()
            val res = sharedClient.newCall(req).execute()
            val jsonStr = res.body?.string() ?: ""
            if (res.isSuccessful && jsonStr.isNotBlank()) {
                val json = JSONObject(jsonStr)
                fileTreeCache = json
                fileTreeTimestamp = System.currentTimeMillis()
                return@withContext json
            }
        } catch (e: Exception) {
            // Silently handled
        }
        null
    }

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
                val fileTree = getFileTree()
                var avatarUrl = ""
                
                if (fileTree != null) {
                    val contentObj = fileTree.optJSONObject("Content")
                    if (contentObj != null) {
                        val keys = contentObj.keys()
                        while (keys.hasNext()) {
                            val folder = keys.next()
                            val filesArray = contentObj.optJSONArray(folder)
                            if (filesArray != null) {
                                for (i in 0 until filesArray.length()) {
                                    val fileName = filesArray.optString(i)
                                    val nameWithoutExt = fileName.substringBeforeLast(".")
                                    if (nameWithoutExt.equals(actorName, ignoreCase = true)) {
                                        avatarUrl = "https://raw.githubusercontent.com/gfriends/gfriends/master/Content/$folder/$fileName"
                                        break
                                    }
                                }
                            }
                            if (avatarUrl.isNotBlank()) break
                        }
                    }
                }
                
                if (avatarUrl.isBlank()) {
                    // Fallback to legacy single-name check
                    val fallbackUrl = "https://raw.githubusercontent.com/gfriends/gfriends/master/Content/File/$actorName.jpg"
                    val headReq = Request.Builder().url(fallbackUrl).head().build()
                    val headRes = sharedClient.newCall(headReq).execute()
                    if (headRes.isSuccessful) {
                        avatarUrl = fallbackUrl
                    }
                }

                if (avatarUrl.isNotBlank()) {
                    return@withContext JavMetadata(
                        javId = cleanJavId,
                        title = "$cleanJavId - Star: $actorName",
                        coverUrl = avatarUrl,
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
 * Standard AirAV Barcode API Metadata Provider
 */
class AirAvBarcodeMetadataProvider : MetadataProvider {
    override val id: String = "airav_barcode"
    override val name: String = "AirAV Barcode API"
    override var isEnabled: Boolean = true

    override suspend fun fetchMetadata(javId: String): JavMetadata? = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().uppercase()
        val rawNum = cleanJavId.replace("-", "")

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

                val upperName = name.uppercase()
                if ((upperName.contains(cleanJavId) || upperName.contains(rawNum)) && imgUrl.isNotBlank()) {
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
 * Standard Arzon Adult Catalog Metadata Provider
 */
class ArzonCatalogMetadataProvider : MetadataProvider {
    override val id: String = "arzon_catalog"
    override val name: String = "Arzon Adult Catalog"
    override var isEnabled: Boolean = true

    override suspend fun fetchMetadata(javId: String): JavMetadata? = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().uppercase()
        val rawNum = cleanJavId.replace("-", "")

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

            val upperTitle = title.uppercase()
            if ((upperTitle.contains(cleanJavId) || upperTitle.contains(rawNum) || html.uppercase().contains(cleanJavId)) && coverUrl.isNotBlank()) {
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
