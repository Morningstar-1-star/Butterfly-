package com.example.plugin.providers

import android.content.Context
import com.example.MainApplication
import com.example.extractor.YtDlpResolver
import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HotstarProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "hotstar"

    private val catalog = listOf(
        PluginVideoItem(
            id = "https://www.hotstar.com/in/shows/special-ops/1260022890/1260022891",
            title = "Special Ops - Episode 1: S हिम्मत की कीमत",
            uploaderName = "Disney+ Hotstar",
            uploaderAvatarUrl = "https://img10.hotstar.com/image/upload/f_auto,q_90/sources/r1/cms/prod/4917/1734917-h-52c6fbf0e854",
            viewCount = 24500000L,
            durationSeconds = 2700L,
            uploadDate = "2020-03-17",
            thumbnailUrl = "https://img10.hotstar.com/image/upload/f_auto,q_90/sources/r1/cms/prod/4917/1734917-h-52c6fbf0e854",
            description = "Himmat Singh, an officer at RAW, deduces a pattern in terror attacks and believes a single mastermind is behind them all.",
            providerId = providerId
        ),
        PluginVideoItem(
            id = "https://www.hotstar.com/in/shows/criminal-justice/1260004267/1260004268",
            title = "Criminal Justice - Episode 1: A Night to Remember",
            uploaderName = "Disney+ Hotstar",
            uploaderAvatarUrl = "https://img10.hotstar.com/image/upload/f_auto,q_90/sources/r1/cms/prod/4917/1734917-h-52c6fbf0e854",
            viewCount = 19200000L,
            durationSeconds = 2820L,
            uploadDate = "2019-04-05",
            thumbnailUrl = "https://img10.hotstar.com/image/upload/f_auto,q_90/sources/r1/cms/prod/4917/1734917-h-52c6fbf0e854",
            description = "Aditya's life turns upside down when a one-night stand turns into a nightmare as he wakes up with blood on his hands.",
            providerId = providerId
        ),
        PluginVideoItem(
            id = "https://www.hotstar.com/in/movies/brahmastra-part-one-shiva/1260110227",
            title = "Brahmāstra Part One: Shiva",
            uploaderName = "Disney+ Hotstar",
            uploaderAvatarUrl = "https://img10.hotstar.com/image/upload/f_auto,q_90/sources/r1/cms/prod/4917/1734917-h-52c6fbf0e854",
            viewCount = 38000000L,
            durationSeconds = 9900L,
            uploadDate = "2022-11-04",
            thumbnailUrl = "https://img10.hotstar.com/image/upload/f_auto,q_90/sources/r1/cms/prod/4917/1734917-h-52c6fbf0e854",
            description = "Shiva discovers his secret relationship to the element of fire and a mystical weapon known as the Brahmāstra.",
            providerId = providerId
        ),
        PluginVideoItem(
            id = "https://www.hotstar.com/in/shows/koffee-with-karan/1260105381/1260105382",
            title = "Koffee with Karan - Season 7 Premiere",
            uploaderName = "Disney+ Hotstar",
            uploaderAvatarUrl = "https://img10.hotstar.com/image/upload/f_auto,q_90/sources/r1/cms/prod/4917/1734917-h-52c6fbf0e854",
            viewCount = 15800000L,
            durationSeconds = 3100L,
            uploadDate = "2022-07-07",
            thumbnailUrl = "https://img10.hotstar.com/image/upload/f_auto,q_90/sources/r1/cms/prod/4917/1734917-h-52c6fbf0e854",
            description = "Karan Johar hosts Bollywood stars for candid chats, spicy games, and rapid-fire rounds.",
            providerId = providerId
        ),
        PluginVideoItem(
            id = "https://www.hotstar.com/in/sports/cricket/icc-men-t20-world-cup-highlights/1260123456",
            title = "Hotstar Sports - India vs Pakistan T20 World Cup Match Highlights",
            uploaderName = "Disney+ Hotstar Sports",
            uploaderAvatarUrl = "https://img10.hotstar.com/image/upload/f_auto,q_90/sources/r1/cms/prod/4917/1734917-h-52c6fbf0e854",
            viewCount = 52000000L,
            durationSeconds = 1800L,
            uploadDate = "2024-06-09",
            thumbnailUrl = "https://img10.hotstar.com/image/upload/f_auto,q_90/sources/r1/cms/prod/4917/1734917-h-52c6fbf0e854",
            description = "Unmissable thrilling cricket match highlights and key wickets from Hotstar Sports.",
            providerId = providerId
        ),
        PluginVideoItem(
            id = "https://www.hotstar.com/in/shows/mahabharat/435/1260000001",
            title = "Mahabharat - Episode 1",
            uploaderName = "Disney+ Hotstar",
            uploaderAvatarUrl = "https://img10.hotstar.com/image/upload/f_auto,q_90/sources/r1/cms/prod/4917/1734917-h-52c6fbf0e854",
            viewCount = 42000000L,
            durationSeconds = 1260L,
            uploadDate = "2013-09-16",
            thumbnailUrl = "https://img10.hotstar.com/image/upload/f_auto,q_90/sources/r1/cms/prod/4917/1734917-h-52c6fbf0e854",
            description = "The epic saga of duty, righteousness, and the Great War of Kurukshetra.",
            providerId = providerId
        )
    )

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        PagedResult(
            items = catalog,
            nextPageToken = null,
            hasMore = false
        )
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase()
        val matches = catalog.filter {
            it.title.lowercase().contains(q) ||
            it.description?.lowercase()?.contains(q) == true ||
            q.contains("hotstar") || q.contains("disney") || q.contains("cricket")
        }.toMutableList()

        if (matches.isEmpty() || q.contains("hotstar")) {
            val formattedUrl = if (q.startsWith("http://") || q.startsWith("https://")) {
                q
            } else {
                "https://www.hotstar.com/in/search?q=${q.replace(" ", "%20")}"
            }
            matches.add(
                0,
                PluginVideoItem(
                    id = formattedUrl,
                    title = "Disney+ Hotstar: ${query.take(50)}",
                    uploaderName = "Disney+ Hotstar",
                    uploaderAvatarUrl = "https://img10.hotstar.com/image/upload/f_auto,q_90/sources/r1/cms/prod/4917/1734917-h-52c6fbf0e854",
                    viewCount = 5000000L,
                    durationSeconds = 1800L,
                    uploadDate = "2024",
                    thumbnailUrl = "https://img10.hotstar.com/image/upload/f_auto,q_90/sources/r1/cms/prod/4917/1734917-h-52c6fbf0e854",
                    description = "Extract and stream Disney+ Hotstar media using yt-dlp native HotstarIE extractor.",
                    providerId = providerId
                )
            )
        }

        PagedResult(items = matches, nextPageToken = null, hasMore = false)
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val match = catalog.firstOrNull { it.id == idOrUrl }
        if (match != null) return@withContext match

        val title = idOrUrl.substringAfterLast("/").replace("-", " ").capitalizeWords()
        PluginVideoItem(
            id = idOrUrl,
            title = if (title.isNotBlank()) "Disney+ Hotstar - $title" else "Hotstar Stream",
            uploaderName = "Disney+ Hotstar",
            uploaderAvatarUrl = "https://img10.hotstar.com/image/upload/f_auto,q_90/sources/r1/cms/prod/4917/1734917-h-52c6fbf0e854",
            viewCount = 1000000L,
            durationSeconds = 1800L,
            uploadDate = "2024",
            thumbnailUrl = "https://img10.hotstar.com/image/upload/f_auto,q_90/sources/r1/cms/prod/4917/1734917-h-52c6fbf0e854",
            description = "Hotstar stream requested: $idOrUrl",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val targetUrl = if (idOrUrl.startsWith("http://") || idOrUrl.startsWith("https://")) {
            idOrUrl
        } else {
            "https://www.hotstar.com/$idOrUrl"
        }

        val ctx: Context? = try {
            MainApplication.appContext
        } catch (_: Throwable) {
            null
        }

        val streams = mutableListOf<PluginVideoStream>()
        var extractedTitle = "Disney+ Hotstar Video"
        var extractedDesc = "Hotstar stream extracted with native yt-dlp HotstarIE extractor"
        var extractedThumb: String? = null
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
            "Referer" to "https://www.hotstar.com/",
            "Origin" to "https://www.hotstar.com"
        )

        if (ctx != null) {
            try {
                when (val result = YtDlpResolver.extractStreamInfo(ctx, targetUrl)) {
                    is YtDlpResolver.ExtractionResult.Success -> {
                        extractedTitle = result.streamData.title
                        extractedDesc = result.streamData.description ?: extractedDesc
                        extractedThumb = result.streamData.thumbnailUrl

                        for (opt in result.playableOptions) {
                            val vUrl = opt.videoUrl ?: continue
                            streams.add(
                                PluginVideoStream(
                                    url = vUrl,
                                    qualityLabel = opt.qualityLabel,
                                    format = opt.format ?: "mp4",
                                    isMuxed = opt.isMuxed,
                                    audioUrl = opt.audioUrl
                                )
                            )
                        }
                    }
                    is YtDlpResolver.ExtractionResult.Error -> {
                        extractedDesc = "yt-dlp extraction note: ${result.message}"
                    }
                }
            } catch (e: Throwable) {
                extractedDesc = "Extraction attempt failed: ${e.message}"
            }
        }

        val primaryPlayableUrl = streams.firstOrNull()?.url ?: targetUrl

        PluginStreamInfo(
            id = idOrUrl,
            url = primaryPlayableUrl,
            title = extractedTitle,
            channelName = "Disney+ Hotstar",
            channelAvatarUrl = "https://img10.hotstar.com/image/upload/f_auto,q_90/sources/r1/cms/prod/4917/1734917-h-52c6fbf0e854",
            viewCount = 2000000L,
            likeCount = 100000L,
            uploadDate = "2024",
            thumbnailUrl = extractedThumb ?: "https://img10.hotstar.com/image/upload/f_auto,q_90/sources/r1/cms/prod/4917/1734917-h-52c6fbf0e854",
            description = extractedDesc,
            hlsUrl = streams.firstOrNull { it.format.lowercase().contains("hls") || it.url.contains(".m3u8") }?.url,
            videoStreams = streams,
            httpHeaders = headers
        )
    }

    private fun String.capitalizeWords(): String {
        return split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
}
