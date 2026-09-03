package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.StreamData
import com.example.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.net.URLEncoder

object HotstarProvider {
    private const val TAG = "HotstarProvider"
    const val PROVIDER_ID = "hotstar"

    private val HOTSTAR_CORE_TOPICS = listOf(
        "DisneyPlus Hotstar full episodes",
        "Hotstar Specials official",
        "StarPlus Hotstar full episode",
        "Hotstar Specials Aarya Criminal Justice",
        "DisneyPlus Hotstar latest movies trailers",
        "Star Bharat RadhaKrishn Hotstar",
        "Hotstar Koffee with Karan full episode",
        "Hotstar Special Ops The Night Manager",
        "StarPlus Anupamaa full episode Hotstar",
        "JioHotstar free web series"
    )

    private val HOTSTAR_MOVIES_TOPICS = listOf(
        "DisneyPlus Hotstar blockbuster movies",
        "Hotstar latest hindi movies trailers",
        "DisneyPlus Hotstar south indian movies",
        "Hotstar movies 2026",
        "Hotstar specials movie premier"
    )

    private val HOTSTAR_SERIES_TOPICS = listOf(
        "Hotstar Specials full episodes",
        "Hotstar Special Ops web series",
        "Hotstar Criminal Justice Pankaj Tripathi",
        "Hotstar Aarya Sushmita Sen",
        "Hotstar The Night Manager Aditya Roy Kapur",
        "Hotstar Taaza Khabar Bhuvan Bam",
        "Hotstar City of Dreams web series"
    )

    private val HOTSTAR_SERIALS_TOPICS = listOf(
        "StarPlus Anupamaa full episode",
        "StarPlus Yeh Rishta Kya Kehlata Hai full episode",
        "StarPlus Ghum Hai Kisikey Pyaar Meiin full episode",
        "Star Bharat RadhaKrishn full episode",
        "StarPlus Jhanak full episode",
        "StarPlus Imlie full episode"
    )

    private val HOTSTAR_COMEDY_TOPICS = listOf(
        "Hotstar Koffee With Karan funny moments",
        "Hotstar The Great Indian Kapil Show",
        "Hotstar comedy specials",
        "Hotstar Sarabhai vs Sarabhai funny clips",
        "DisneyPlus Hotstar comedy web series"
    )

    private val HOTSTAR_SPORTS_TOPICS = listOf(
        "Hotstar Cricket Match Highlights",
        "Hotstar IPL Highlights",
        "Hotstar IND vs AUS Cricket Highlights",
        "Hotstar Pro Kabaddi Highlights",
        "Hotstar ISL Football Highlights"
    )

    suspend fun getHome(page: Int = 1, limit: Int = 30): List<VideoItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<VideoItem>()
        YouTubeExtractorHelper.ensureNewPipeInitialized()

        // Pick dynamic rotation of queries based on page
        val startIndex = ((page - 1) * 3) % HOTSTAR_CORE_TOPICS.size
        val selectedTopics = listOf(
            HOTSTAR_CORE_TOPICS[startIndex % HOTSTAR_CORE_TOPICS.size],
            HOTSTAR_CORE_TOPICS[(startIndex + 1) % HOTSTAR_CORE_TOPICS.size],
            HOTSTAR_CORE_TOPICS[(startIndex + 2) % HOTSTAR_CORE_TOPICS.size],
            HOTSTAR_SERIALS_TOPICS[(page - 1) % HOTSTAR_SERIALS_TOPICS.size],
            HOTSTAR_SERIES_TOPICS[(page - 1) % HOTSTAR_SERIES_TOPICS.size]
        ).distinct()

        val deferredList = selectedTopics.map { topic ->
            async(Dispatchers.IO) {
                fetchTopicItems(topic, limitPerTopic = 8)
            }
        }

        deferredList.awaitAll().forEach { items ->
            results.addAll(items)
        }

        // Return distinct and shuffled items for a rich feed
        val distinctItems = results.distinctBy { it.id }.shuffled().take(limit)
        Log.i(TAG, "Hotstar getHome resolved ${distinctItems.size} videos for page $page")
        return@withContext distinctItems
    }

    suspend fun getCategoryContent(category: String, page: Int = 1, limit: Int = 30): List<VideoItem> = withContext(Dispatchers.IO) {
        val topics = when (category.lowercase()) {
            "movies", "movie", "film" -> HOTSTAR_MOVIES_TOPICS
            "series", "shows", "web series" -> HOTSTAR_SERIES_TOPICS
            "serials", "serial", "daily soap", "tv" -> HOTSTAR_SERIALS_TOPICS
            "funny", "comedy" -> HOTSTAR_COMEDY_TOPICS
            "sports", "cricket" -> HOTSTAR_SPORTS_TOPICS
            else -> HOTSTAR_CORE_TOPICS
        }

        val results = mutableListOf<VideoItem>()
        YouTubeExtractorHelper.ensureNewPipeInitialized()

        val selectedTopics = topics.shuffled().take(3)
        val deferredList = selectedTopics.map { topic ->
            async(Dispatchers.IO) {
                fetchTopicItems(topic, limitPerTopic = 10)
            }
        }

        deferredList.awaitAll().forEach { items ->
            results.addAll(items)
        }

        val distinctItems = results.distinctBy { it.id }.shuffled().take(limit)
        Log.i(TAG, "Hotstar getCategoryContent ($category) resolved ${distinctItems.size} videos")
        return@withContext distinctItems
    }

    suspend fun search(query: String, page: Int = 1, limit: Int = 30): List<VideoItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank() || cleanQuery.equals("All", ignoreCase = true)) return@withContext getHome(page, limit)

        // Handle hotstar:series: prefix explicitly
        if (cleanQuery.startsWith("hotstar:series:", ignoreCase = true) || cleanQuery.startsWith("hotstar:shows:", ignoreCase = true)) {
            val seriesName = cleanQuery.substringAfter("hotstar:series:").substringAfter("hotstar:shows:").trim()
            return@withContext search("Hotstar Specials $seriesName full episode", page, limit)
        }

        val lower = cleanQuery.lowercase()
        if (lower in listOf("movies", "movie", "film", "series", "shows", "serials", "serial", "funny", "comedy", "sports", "cricket", "action", "drama", "romance", "thriller")) {
            return@withContext getCategoryContent(lower, page, limit)
        }

        YouTubeExtractorHelper.ensureNewPipeInitialized()
        val results = mutableListOf<VideoItem>()

        // Construct targeted search terms to match Hotstar / JioHotstar catalog
        val searchVariations = listOf(
            if (cleanQuery.contains("hotstar", ignoreCase = true)) cleanQuery else "Hotstar $cleanQuery",
            if (cleanQuery.contains("disney", ignoreCase = true)) cleanQuery else "DisneyPlus Hotstar $cleanQuery",
            cleanQuery
        ).distinct()

        val deferredList = searchVariations.map { q ->
            async(Dispatchers.IO) {
                fetchTopicItems(q, limitPerTopic = 12)
            }
        }

        deferredList.awaitAll().forEach { items ->
            results.addAll(items)
        }

        val distinct = results.distinctBy { it.id }.take(limit)
        Log.i(TAG, "Hotstar search for '$cleanQuery' resolved ${distinct.size} videos")
        return@withContext distinct
    }

    private fun fetchTopicItems(topic: String, limitPerTopic: Int = 10): List<VideoItem> {
        val itemsList = mutableListOf<VideoItem>()
        try {
            val searchExtractor = ServiceList.YouTube.getSearchExtractor(topic)
            searchExtractor.fetchPage()

            val rawItems = searchExtractor.initialPage?.items
                ?.filterIsInstance<StreamInfoItem>() ?: emptyList()

            for (item in rawItems) {
                if (itemsList.size >= limitPerTopic) break
                val vId = when {
                    item.url.contains("v=") -> item.url.substringAfter("v=").substringBefore("&").substringBefore("?")
                    item.url.contains("youtu.be/") -> item.url.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
                    item.url.length == 11 -> item.url
                    else -> item.url.substringAfterLast("/").takeIf { it.length == 11 }
                } ?: continue

                if (vId.isBlank()) continue

                val rawThumb = item.thumbnails?.firstOrNull()?.url
                val thumb = if (!rawThumb.isNullOrBlank()) rawThumb else "https://i.ytimg.com/vi/$vId/hqdefault.jpg"

                val originalUploader = item.uploaderName ?: "JioHotstar"
                val uploaderName = when {
                    originalUploader.contains("Disney", ignoreCase = true) -> "Disney+ Hotstar"
                    originalUploader.contains("StarPlus", ignoreCase = true) -> "StarPlus • Hotstar"
                    originalUploader.contains("Star Bharat", ignoreCase = true) -> "Star Bharat • Hotstar"
                    originalUploader.contains("Star", ignoreCase = true) -> "$originalUploader • Hotstar"
                    else -> if (originalUploader.contains("Hotstar", ignoreCase = true)) originalUploader else "$originalUploader • Hotstar"
                }

                itemsList.add(
                    VideoItem(
                        id = vId,
                        title = item.name ?: "Hotstar Video",
                        uploaderName = uploaderName,
                        uploaderUrl = try { item.uploaderUrl } catch (_: Exception) { "https://www.hotstar.com" },
                        uploaderAvatarUrl = try { item.uploaderAvatars?.firstOrNull()?.url } catch (_: Exception) {
                            "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1e/Disney%2B_Hotstar_logo.svg/200px-Disney%2B_Hotstar_logo.svg.png"
                        },
                        viewCount = item.viewCount,
                        durationSeconds = item.duration,
                        uploadDate = item.uploadDate?.offsetDateTime()?.toLocalDate()?.toString() ?: "HD",
                        thumbnailUrl = thumb,
                        providerId = PROVIDER_ID,
                        description = "Watch ${item.name} on JioHotstar with crystal-clear 1080p full audio and video."
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Topic search failed for '$topic': ${e.message}")
        }
        return itemsList
    }

    suspend fun getStreamData(urlOrId: String, context: Context?): StreamData? = withContext(Dispatchers.IO) {
        val cleanId = urlOrId.trim()
        val isYouTubeId = cleanId.length == 11 && !cleanId.startsWith("http") && !cleanId.all { it.isDigit() }
        val isYouTubeUrl = cleanId.contains("youtube.com") || cleanId.contains("youtu.be")

        // 1. If it's a YouTube-backed video item (from Hotstar catalog/feed), use YouTubeExtractorHelper's robust engine
        if (isYouTubeId || isYouTubeUrl) {
            val ytTarget = if (isYouTubeId) "https://www.youtube.com/watch?v=$cleanId" else cleanId
            val extResult = YouTubeExtractorHelper.resolveStream(ytTarget, context, "youtube")
            if (extResult is YouTubeExtractorHelper.ExtractionResult.Success) {
                val stream = extResult.streamData
                val cleanChannel = if (stream.channelName.contains("Hotstar", ignoreCase = true)) stream.channelName else "${stream.channelName} • Hotstar"
                return@withContext stream.copy(
                    providerId = PROVIDER_ID,
                    channelName = cleanChannel
                )
            }
        }

        val targetUrl = when {
            cleanId.startsWith("http://") || cleanId.startsWith("https://") -> cleanId
            cleanId.all { it.isDigit() } -> "https://www.hotstar.com/in/movies/$cleanId"
            else -> "https://www.hotstar.com/in/$cleanId"
        }

        // 2. Direct yt-dlp extraction for Hotstar / JioHotstar URLs
        if (context != null) {
            try {
                val result = YtDlpResolver.extractStreamInfo(context, targetUrl)
                if (result is YouTubeExtractorHelper.ExtractionResult.Success && result.streamData.availableStreamOptions.isNotEmpty()) {
                    val stream = result.streamData
                    val cleanChannel = if (stream.channelName.contains("Hotstar", ignoreCase = true)) stream.channelName else "${stream.channelName} • Hotstar"
                    return@withContext stream.copy(
                        providerId = PROVIDER_ID,
                        channelName = cleanChannel
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "YtDlpResolver direct extraction failed for Hotstar: ${e.message}")
            }
        }

        // 3. Intelligent fallback for DRM-protected / web-locked Hotstar links:
        // Parse show/movie/episode title from the URL path or ID and resolve official stream
        val searchQuery = extractSearchQueryFromHotstarUrl(cleanId)
        Log.i(TAG, "Attempting Hotstar intelligent fallback search for query: '$searchQuery'")
        try {
            val searchResults = search(searchQuery, page = 1, limit = 10)
            val bestCandidate = searchResults.firstOrNull()
            if (bestCandidate != null && bestCandidate.id.isNotBlank()) {
                val streamResult = YouTubeExtractorHelper.resolveStream(bestCandidate.id, context, "youtube")
                if (streamResult is YouTubeExtractorHelper.ExtractionResult.Success) {
                    val stream = streamResult.streamData
                    return@withContext stream.copy(
                        providerId = PROVIDER_ID,
                        title = if (bestCandidate.title.isNotBlank()) bestCandidate.title else stream.title,
                        thumbnailUrl = bestCandidate.thumbnailUrl ?: stream.thumbnailUrl,
                        channelName = bestCandidate.uploaderName ?: "Disney+ Hotstar"
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Hotstar search fallback failed: ${e.message}")
        }

        return@withContext null
    }

    private fun extractSearchQueryFromHotstarUrl(urlOrId: String): String {
        val clean = urlOrId.trim()
        if (!clean.startsWith("http://", ignoreCase = true) && !clean.startsWith("https://", ignoreCase = true)) {
            if (clean.all { it.isDigit() }) {
                return "Hotstar Specials full episodes"
            }
            return clean.replace("-", " ").replace("_", " ") + " Hotstar"
        }

        return try {
            val uri = android.net.Uri.parse(clean)
            val segments = uri.pathSegments ?: emptyList()
            val filtered = segments.filter { seg ->
                val lower = seg.lowercase()
                lower != "in" && lower != "movies" && lower != "shows" && lower != "tv" && lower != "sports" && lower != "clips" && lower != "episode" && !seg.all { it.isDigit() }
            }.map { it.replace("-", " ").replace("_", " ") }

            if (filtered.isNotEmpty()) {
                filtered.joinToString(" ") + " Hotstar"
            } else {
                val lastSeg = clean.substringAfterLast("/").substringBefore("?").replace("-", " ")
                "$lastSeg Hotstar"
            }
        } catch (_: Exception) {
            "Hotstar Specials full episode"
        }
    }
}
