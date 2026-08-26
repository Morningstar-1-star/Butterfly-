package com.example.extractor

import android.net.Uri
import android.util.Log
import com.example.model.CaptionOption
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import com.example.model.StreamData
import com.example.model.VideoItem
import com.example.model.parseDurationToSeconds
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
        .followSslRedirects(true)
        .addInterceptor { chain ->
            var request = chain.request()
            val urlStr = request.url.toString().lowercase()
            if (urlStr.contains("archive.org") || urlStr.contains("us.archive.org") || urlStr.contains("ia")) {
                val builder = request.newBuilder()
                if (request.header("User-Agent") == null) {
                    builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                }
                if (request.header("Referer") == null) {
                    builder.header("Referer", "https://archive.org/")
                }
                request = builder.build()
            }
            chain.proceed(request)
        }
        .build()

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
            "mediatype:movies AND (collection:feature_films OR collection:bollywood OR collection:classic_tv OR collection:animationandcartoons) AND downloads:[1000 TO *]",
            "mediatype:movies AND (collection:scifi_horror OR collection:cult_movies OR collection:cinema) AND downloads:[800 TO *]",
            "mediatype:movies AND (collection:animationandcartoons OR \"classic animation\" OR \"cartoon\") AND downloads:[500 TO *]",
            "mediatype:movies AND (collection:feature_films OR collection:silent_films OR collection:movies) AND downloads:[1200 TO *]",
            "mediatype:movies AND (collection:classic_tv OR collection:television OR \"vintage tv\") AND downloads:[600 TO *]",
            "mediatype:movies AND (collection:documentaries OR collection:short_films) AND downloads:[500 TO *]"
        )

        val sorts = listOf("-downloads", "-publicdate", "-addeddate", "-review_date")
        val randomSort = sorts.random()

        val queryIndex = ((page - 1).coerceAtLeast(0) + (0 until categories.size).random()) % categories.size
        val curatedQuery = categories[queryIndex]
        val encodedQuery = URLEncoder.encode(curatedQuery, "UTF-8")

        val url = "https://archive.org/advancedsearch.php?q=$encodedQuery&fl[]=identifier&fl[]=title&fl[]=creator&fl[]=publicdate&fl[]=description&fl[]=downloads&fl[]=mediatype&fl[]=length&fl[]=duration&sort[]=$randomSort&rows=30&page=$page&output=json"

        val items = mutableListOf<VideoItem>()
        val body = httpGet(url)
        if (!body.isNullOrBlank()) {
            items.addAll(parseArchiveList(body))
        }

        if (items.isEmpty()) {
            val scrapeUrl = "https://archive.org/services/search/v1/scrape?q=$encodedQuery&fields=identifier,title,creator,publicdate,description,downloads,length,duration&count=30"
            val scrapeBody = httpGet(scrapeUrl)
            if (!scrapeBody.isNullOrBlank()) {
                items.addAll(parseArchiveList(scrapeBody))
            }
        }

        items.distinctBy { it.id }.shuffled()
    }

    suspend fun search(query: String, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val clean = query.replace("[^a-zA-Z0-9 ]".toRegex(), " ").trim()
        if (clean.isBlank()) return@withContext emptyList()

        val searchQuery = "mediatype:movies AND ($clean)"
        val encodedQuery = URLEncoder.encode(searchQuery, "UTF-8")
        val url = "https://archive.org/advancedsearch.php?q=$encodedQuery&fl[]=identifier&fl[]=title&fl[]=creator&fl[]=publicdate&fl[]=description&fl[]=downloads&fl[]=mediatype&fl[]=length&fl[]=duration&sort[]=-downloads&rows=30&page=$page&output=json"

        val items = mutableListOf<VideoItem>()
        val body = httpGet(url)
        if (!body.isNullOrBlank()) {
            items.addAll(parseArchiveList(body))
        }

        items.distinctBy { it.id }
    }

    suspend fun getStreamData(idOrUrl: String, context: android.content.Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val identifier = extractId(idOrUrl)
        if (identifier.isBlank()) return@withContext null

        val metaUrl = "https://archive.org/metadata/$identifier"
        val body = httpGet(metaUrl)

        var resolvedTitle = identifier.replace("_", " ")
        var resolvedCreator = "Internet Archive"
        var resolvedDesc = "Internet Archive Public Domain Stream"
        val options = mutableListOf<PlayableStreamOption>()
        val captions = mutableListOf<CaptionOption>()

        val archiveHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Referer" to "https://archive.org/"
        )

        if (!body.isNullOrBlank()) {
            try {
                val json = JSONObject(body)
                val server = json.optString("server", "").trim()
                val dir = json.optString("dir", "").trim()
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
                            val fileUrl = if (server.isNotBlank() && dir.isNotBlank()) {
                                "https://$server$dir/$encodedName"
                            } else {
                                "https://archive.org/download/$identifier/$encodedName"
                            }
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
                    val fmt = f.optString("format", "")
                    val title = f.optString("title", "").ifBlank {
                        name.removeSuffix(".ia.mp4")
                            .removeSuffix(".mp4")
                            .removeSuffix(".mkv")
                            .removeSuffix(".webm")
                            .removeSuffix(".avi")
                            .replace("_", " ")
                    }
                    val encodedName = Uri.encode(name, "/@:.-_")
                    val fileUrl = if (server.isNotBlank() && dir.isNotBlank()) {
                        "https://$server$dir/$encodedName"
                    } else {
                        "https://archive.org/download/$identifier/$encodedName"
                    }
                    val height = f.optInt("height", 0)
                    val heightLabel = if (height > 0) "${height}p" else "Direct"

                    val isH264Mp4 = name.endsWith(".mp4", ignoreCase = true) || fmt.contains("h.264", ignoreCase = true) || fmt.contains("mp4", ignoreCase = true)

                    options.add(
                        PlayableStreamOption(
                            qualityLabel = "$title ($heightLabel${if (isH264Mp4) " MP4" else ""})",
                            format = if (isH264Mp4) "mp4" else "video",
                            isMuxed = true,
                            videoUrl = fileUrl,
                            providerType = ProviderType.DIRECT,
                            headers = archiveHeaders
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed parsing metadata: ${e.message}")
            }
        }

        // If no direct video options were found in metadata, fallback to YtDlpResolver
        if (options.isEmpty() && context != null) {
            val archiveUrl = if (idOrUrl.startsWith("http")) idOrUrl else "https://archive.org/details/$identifier"
            val ytdlResult = YtDlpResolver.extractStreamInfo(context, archiveUrl)
            if (ytdlResult is YouTubeExtractorHelper.ExtractionResult.Success) {
                return@withContext ytdlResult.streamData
            }
        }

        if (options.isEmpty()) {
            return@withContext null
        }

        // Sort options: prefer MP4/H.264, 720p/1080p, non-512kb
        val sortedOptions = options.sortedWith(
            compareByDescending<PlayableStreamOption> { opt ->
                var score = 0
                if (opt.format.equals("mp4", ignoreCase = true) || opt.qualityLabel.contains("MP4", ignoreCase = true)) score += 1000
                if (opt.qualityLabel.contains("720p") || opt.qualityLabel.contains("1080p") || opt.qualityLabel.contains("480p")) score += 500
                if (opt.qualityLabel.contains("512kb", ignoreCase = true)) score -= 200
                score
            }
        )

        val bestOption = sortedOptions.firstOrNull() ?: return@withContext null

        StreamData(
            videoId = identifier,
            videoUrl = bestOption.videoUrl ?: "",
            title = resolvedTitle,
            channelName = resolvedCreator,
            description = resolvedDesc,
            thumbnailUrl = "https://archive.org/services/img/$identifier",
            availableStreamOptions = sortedOptions,
            selectedStreamOption = bestOption,
            captionOptions = captions,
            providerId = PROVIDER_ID,
            providerType = ProviderType.DIRECT,
            headers = bestOption.headers
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
                val lenStr = d.optString("length", d.optString("duration", ""))
                val durationSec = parseDurationToSeconds(lenStr)

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
                        durationSeconds = durationSec,
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

    fun extractId(input: String): String {
        var clean = input.trim()
            .removePrefix("archive_org:")
            .removePrefix("archive:")
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
