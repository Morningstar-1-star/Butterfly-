package com.example.extractor

import android.net.Uri
import android.util.Log
import com.example.model.CaptionOption
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import com.example.model.StreamData
import com.example.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object ArchiveOrgProvider {
    private const val TAG = "ArchiveOrgProvider"
    const val PROVIDER_ID = "archive_org"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val CURATED_FALLBACK_ITEMS = listOf(
        VideoItem(
            id = "Awaara1951HindiMovie",
            title = "Awaara (1951) - Raj Kapoor Classic Hindi Cinema",
            uploaderName = "Raj Kapoor Films",
            uploadDate = "1951",
            thumbnailUrl = "https://archive.org/services/img/Awaara1951HindiMovie",
            viewCount = 4250000L,
            providerId = PROVIDER_ID
        ),
        VideoItem(
            id = "shree_420_1955",
            title = "Shree 420 (1955) - Raj Kapoor & Nargis Hindi Masterpiece",
            uploaderName = "Classic Indian Cinema",
            uploadDate = "1955",
            thumbnailUrl = "https://archive.org/services/img/shree_420_1955",
            viewCount = 3980000L,
            providerId = PROVIDER_ID
        ),
        VideoItem(
            id = "pyaasa_1957_hindi",
            title = "Pyaasa (1957) - Guru Dutt Masterpiece Hindi Movie",
            uploaderName = "Guru Dutt Films",
            uploadDate = "1957",
            thumbnailUrl = "https://archive.org/services/img/pyaasa_1957_hindi",
            viewCount = 3120000L,
            providerId = PROVIDER_ID
        ),
        VideoItem(
            id = "do_bigha_zamin_1953",
            title = "Do Bigha Zamin (1953) - Bimal Roy Classic Hindi Film",
            uploaderName = "Bimal Roy Productions",
            uploadDate = "1953",
            thumbnailUrl = "https://archive.org/services/img/do_bigha_zamin_1953",
            viewCount = 2850000L,
            providerId = PROVIDER_ID
        ),
        VideoItem(
            id = "night_of_the_living_dead",
            title = "Night of the Living Dead (1968)",
            uploaderName = "George A. Romero",
            uploadDate = "1968",
            thumbnailUrl = "https://archive.org/services/img/night_of_the_living_dead",
            viewCount = 2650000L,
            providerId = PROVIDER_ID
        ),
        VideoItem(
            id = "SteamboatWillie1928_201903",
            title = "Steamboat Willie (1928) - Walt Disney Landmark Cartoon",
            uploaderName = "National Film Archive",
            uploadDate = "1928",
            thumbnailUrl = "https://archive.org/services/img/SteamboatWillie1928_201903",
            viewCount = 980000L,
            providerId = PROVIDER_ID
        )
    )

    private fun httpGet(url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json, */*")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTTP get failed for $url: ${e.message}")
            null
        }
    }

    suspend fun getHome(page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val categories = listOf(
            "mediatype:movies AND (collection:feature_films OR collection:bollywood OR collection:classic_tv OR collection:animationandcartoons) AND downloads:[2000 TO *]",
            "mediatype:movies AND (language:(hindi OR eng OR jpn) OR collection:feature_films OR collection:movies) AND downloads:[3000 TO *]",
            "mediatype:movies AND (collection:animationandcartoons OR \"classic animation\" OR \"cartoon\") AND downloads:[1500 TO *]",
            "mediatype:movies AND (collection:feature_films OR collection:cult_movies OR collection:cinema OR \"public domain\") AND downloads:[4000 TO *]"
        )

        val queryIndex = (page - 1).coerceAtLeast(0) % categories.size
        val curatedQuery = categories[queryIndex]
        val encodedQuery = URLEncoder.encode(curatedQuery, "UTF-8")

        val url = "https://archive.org/advancedsearch.php?q=$encodedQuery&fl[]=identifier&fl[]=title&fl[]=creator&fl[]=publicdate&fl[]=description&fl[]=downloads&fl[]=mediatype&sort[]=-downloads&rows=30&page=$page&output=json"

        val items = mutableListOf<VideoItem>()
        val body = httpGet(url)
        if (!body.isNullOrBlank()) {
            items.addAll(parseArchiveList(body))
        }

        if (items.isEmpty()) {
            val scrapeUrl = "https://archive.org/services/search/v1/scrape?q=$encodedQuery&fields=identifier,title,creator,publicdate,description,downloads&count=30"
            val scrapeBody = httpGet(scrapeUrl)
            if (!scrapeBody.isNullOrBlank()) {
                items.addAll(parseArchiveList(scrapeBody))
            }
        }

        if (items.isEmpty() && page == 1) {
            items.addAll(CURATED_FALLBACK_ITEMS)
        }

        items.distinctBy { it.id }
    }

    suspend fun search(query: String, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val clean = query.replace("[^a-zA-Z0-9 ]".toRegex(), " ").trim()
        if (clean.isBlank()) return@withContext emptyList()

        val searchQuery = "mediatype:movies AND ($clean)"
        val encodedQuery = URLEncoder.encode(searchQuery, "UTF-8")
        val url = "https://archive.org/advancedsearch.php?q=$encodedQuery&fl[]=identifier&fl[]=title&fl[]=creator&fl[]=publicdate&fl[]=description&fl[]=downloads&fl[]=mediatype&sort[]=-downloads&rows=30&page=$page&output=json"

        val items = mutableListOf<VideoItem>()
        val body = httpGet(url)
        if (!body.isNullOrBlank()) {
            items.addAll(parseArchiveList(body))
        }

        if (items.isEmpty()) {
            val filteredFallback = CURATED_FALLBACK_ITEMS.filter {
                it.title.contains(query, ignoreCase = true) || it.uploaderName.contains(query, ignoreCase = true)
            }
            items.addAll(filteredFallback)
        }

        items.distinctBy { it.id }
    }

    suspend fun getStreamData(idOrUrl: String): StreamData? = withContext(Dispatchers.IO) {
        val identifier = extractId(idOrUrl)
        if (identifier.isBlank()) return@withContext null

        val metaUrl = "https://archive.org/metadata/$identifier"
        val body = httpGet(metaUrl)

        var resolvedTitle = identifier.replace("_", " ")
        var resolvedCreator = "Internet Archive"
        var resolvedDesc = "Internet Archive Public Domain Stream"
        val options = mutableListOf<PlayableStreamOption>()
        val captions = mutableListOf<CaptionOption>()

        if (!body.isNullOrBlank()) {
            try {
                val json = JSONObject(body)
                val meta = json.optJSONObject("metadata")
                resolvedTitle = meta?.optString("title")?.ifBlank { resolvedTitle } ?: resolvedTitle
                resolvedCreator = extractCreator(meta)
                resolvedDesc = meta?.optString("description")?.ifBlank { resolvedDesc } ?: resolvedDesc

                val filesArr = json.optJSONArray("files") ?: JSONArray()
                val videoFiles = mutableListOf<JSONObject>()

                for (i in 0 until filesArr.length()) {
                    val f = filesArr.optJSONObject(i) ?: continue
                    val name = f.optString("name", "")
                    val format = f.optString("format", "").lowercase()

                    val isThumb = name.endsWith("_thumb.mp4") || format.contains("thumbnail") || name.endsWith("_thumb.jpg")
                    val isMetadata = name.endsWith(".xml") || name.endsWith(".sqlite") || name.endsWith(".torrent") || name.endsWith(".txt") || name.endsWith(".png") || name.endsWith(".jpg")

                    if (!isThumb && !isMetadata) {
                        val isVideoExt = name.endsWith(".mp4", ignoreCase = true) ||
                                name.endsWith(".mkv", ignoreCase = true) ||
                                name.endsWith(".webm", ignoreCase = true) ||
                                name.endsWith(".avi", ignoreCase = true) ||
                                name.endsWith(".mov", ignoreCase = true) ||
                                name.endsWith(".ogv", ignoreCase = true) ||
                                format.contains("mp4") || format.contains("h.264") || format.contains("mpeg4") ||
                                format.contains("matroska") || format.contains("webm") || format.contains("avi")

                        if (isVideoExt) {
                            videoFiles.add(f)
                        } else if (format.contains("vtt") || name.endsWith(".vtt", ignoreCase = true) || name.endsWith(".srt", ignoreCase = true)) {
                            val encodedName = Uri.encode(name, "/@:.-_")
                            val fileUrl = "https://archive.org/download/$identifier/$encodedName"
                            captions.add(
                                CaptionOption(
                                    url = fileUrl,
                                    languageCode = "en",
                                    languageName = "English",
                                    format = if (name.endsWith(".vtt", ignoreCase = true)) "vtt" else "srt"
                                )
                            )
                        }
                    }
                }

                for (f in videoFiles) {
                    val name = f.optString("name")
                    val title = f.optString("title", "").ifBlank {
                        name.removeSuffix(".ia.mp4")
                            .removeSuffix(".mp4")
                            .removeSuffix(".mkv")
                            .removeSuffix(".webm")
                            .removeSuffix(".avi")
                            .replace("_", " ")
                    }
                    val encodedName = Uri.encode(name, "/@:.-_")
                    val fileUrl = "https://archive.org/download/$identifier/$encodedName"
                    val height = f.optInt("height", 0)
                    val heightLabel = if (height > 0) "${height}p" else "720p"

                    options.add(
                        PlayableStreamOption(
                            qualityLabel = "$title ($heightLabel)",
                            format = "mp4",
                            isMuxed = true,
                            videoUrl = fileUrl,
                            providerType = ProviderType.DIRECT,
                            headers = mapOf("Referer" to "https://archive.org/")
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed parsing metadata: ${e.message}")
            }
        }

        if (options.isEmpty()) {
            val fallbackUrl = if (idOrUrl.startsWith("http://") || idOrUrl.startsWith("https://")) {
                idOrUrl
            } else {
                "https://archive.org/download/$identifier/${identifier}.mp4"
            }
            options.add(
                PlayableStreamOption(
                    qualityLabel = "Standard MP4 (720p)",
                    format = "mp4",
                    isMuxed = true,
                    videoUrl = fallbackUrl,
                    providerType = ProviderType.DIRECT,
                    headers = mapOf("Referer" to "https://archive.org/")
                )
            )
            options.add(
                PlayableStreamOption(
                    qualityLabel = "Fast MPEG-4 (512kb)",
                    format = "mp4",
                    isMuxed = true,
                    videoUrl = "https://archive.org/download/$identifier/${identifier}_512kb.mp4",
                    providerType = ProviderType.DIRECT,
                    headers = mapOf("Referer" to "https://archive.org/")
                )
            )
        }

        StreamData(
            videoId = identifier,
            videoUrl = options.firstOrNull()?.videoUrl ?: "",
            title = resolvedTitle,
            channelName = resolvedCreator,
            description = resolvedDesc,
            thumbnailUrl = "https://archive.org/services/img/$identifier",
            availableStreamOptions = options,
            selectedStreamOption = options.firstOrNull(),
            captionOptions = captions,
            providerId = PROVIDER_ID,
            providerType = ProviderType.DIRECT
        )
    }

    private fun parseArchiveList(jsonStr: String): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val json = JSONObject(jsonStr)
            val response = json.optJSONObject("response")
            val docs = response?.optJSONArray("docs") ?: json.optJSONArray("items") ?: JSONArray()

            for (i in 0 until docs.length()) {
                val d = docs.optJSONObject(i) ?: continue
                val id = d.optString("identifier", "").ifBlank { d.optString("id", "") }
                if (id.isBlank()) continue
                val title = d.optString("title", "").ifBlank { id.replace("_", " ") }
                val creator = extractCreator(d)
                val downloads = d.optLong("downloads", 0L)
                val publicDate = d.optString("publicdate", d.optString("date", ""))

                if (!com.example.util.LanguageFilterHelper.isAllowed(title, creator, PROVIDER_ID)) {
                    continue
                }

                list.add(
                    VideoItem(
                        id = id,
                        title = title,
                        uploaderName = creator,
                        uploadDate = publicDate.takeIf { it.isNotBlank() },
                        thumbnailUrl = "https://archive.org/services/img/$id",
                        viewCount = downloads,
                        providerId = PROVIDER_ID
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "parseArchiveList error: ${e.message}")
        }
        return list.distinctBy { it.id }
    }

    private fun extractCreator(obj: JSONObject?): String {
        if (obj == null) return "Internet Archive"
        val creator = obj.opt("creator")
        return when (creator) {
            is String -> creator.ifBlank { "Internet Archive" }
            is JSONArray -> if (creator.length() > 0) creator.optString(0, "Internet Archive") else "Internet Archive"
            else -> obj.optString("uploader", "Internet Archive")
        }
    }

    private fun extractId(input: String): String {
        var clean = input.trim()
        if (clean.contains("archive.org/details/")) {
            clean = clean.substringAfter("archive.org/details/").substringBefore("/").substringBefore("?").substringBefore("#")
        } else if (clean.contains("archive.org/download/")) {
            clean = clean.substringAfter("archive.org/download/").substringBefore("/").substringBefore("?").substringBefore("#")
        } else if (clean.startsWith("http")) {
            clean = clean.substringAfterLast("/").substringBefore("?").substringBefore("#")
        }
        return clean
    }
}
