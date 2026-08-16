package com.example.plugin.providers

import android.content.Context
import com.example.MainApplication
import com.example.extractor.YtDlpResolver
import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AdultSwimProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "adultswim"

    private val catalog = listOf(
        PluginVideoItem(
            id = "https://www.adultswim.com/videos/rick-and-morty/pilot",
            title = "Rick and Morty - Pilot",
            uploaderName = "Adult Swim",
            uploaderAvatarUrl = "https://i.ytimg.com/vi/E8cq8s6CAnI/hqdefault.jpg",
            viewCount = 18500000L,
            durationSeconds = 1320L,
            uploadDate = "2013-12-02",
            thumbnailUrl = "https://i.ytimg.com/vi/E8cq8s6CAnI/hqdefault.jpg",
            description = "Rick moves in with his daughter's family and establishes a bad influence on his grandson, Morty.",
            providerId = providerId
        ),
        PluginVideoItem(
            id = "https://www.adultswim.com/videos/smiling-friends/desmonds-big-day-out",
            title = "Smiling Friends - Desmond's Big Day Out",
            uploaderName = "Adult Swim",
            uploaderAvatarUrl = "https://i.ytimg.com/vi/9xYqGvI40kE/hqdefault.jpg",
            viewCount = 12400000L,
            durationSeconds = 660L,
            uploadDate = "2020-04-01",
            thumbnailUrl = "https://i.ytimg.com/vi/9xYqGvI40kE/hqdefault.jpg",
            description = "Pim and Charlie try to make a depressed man named Desmond smile.",
            providerId = providerId
        ),
        PluginVideoItem(
            id = "https://www.adultswim.com/videos/robot-chicken/star-wars-special",
            title = "Robot Chicken - Star Wars Special",
            uploaderName = "Adult Swim",
            uploaderAvatarUrl = "https://i.ytimg.com/vi/84_B3pI5aO8/hqdefault.jpg",
            viewCount = 9500000L,
            durationSeconds = 1380L,
            uploadDate = "2007-06-17",
            thumbnailUrl = "https://i.ytimg.com/vi/84_B3pI5aO8/hqdefault.jpg",
            description = "Stop-motion animation sketches parodying the Star Wars saga.",
            providerId = providerId
        ),
        PluginVideoItem(
            id = "https://www.adultswim.com/videos/aqua-teen-hunger-force/mayhem-of-the-mooninites",
            title = "Aqua Teen Hunger Force - Mayhem of the Mooninites",
            uploaderName = "Adult Swim",
            uploaderAvatarUrl = "https://i.ytimg.com/vi/6p4T7mS53sc/hqdefault.jpg",
            viewCount = 7800000L,
            durationSeconds = 690L,
            uploadDate = "2001-10-14",
            thumbnailUrl = "https://i.ytimg.com/vi/6p4T7mS53sc/hqdefault.jpg",
            description = "Master Shake and Frylock encounter the mischievous Mooninites, Ignignokt and Err.",
            providerId = providerId
        ),
        PluginVideoItem(
            id = "https://www.adultswim.com/videos/the-eric-andre-show/legalize-ranch",
            title = "The Eric Andre Show - Legalize Ranch",
            uploaderName = "Adult Swim",
            uploaderAvatarUrl = "https://i.ytimg.com/vi/31g0YE61PLQ/hqdefault.jpg",
            viewCount = 6400000L,
            durationSeconds = 600L,
            uploadDate = "2012-05-20",
            thumbnailUrl = "https://i.ytimg.com/vi/31g0YE61PLQ/hqdefault.jpg",
            description = "Surreal anti-talk show absurdity hosted by Eric Andre and Hannibal Buress.",
            providerId = providerId
        ),
        PluginVideoItem(
            id = "https://www.adultswim.com/videos/metalocalypse/curse-of-dethklok",
            title = "Metalocalypse - Curse of Dethklok",
            uploaderName = "Adult Swim",
            uploaderAvatarUrl = "https://i.ytimg.com/vi/vS8i5m4LhYQ/hqdefault.jpg",
            viewCount = 5200000L,
            durationSeconds = 690L,
            uploadDate = "2006-08-06",
            thumbnailUrl = "https://i.ytimg.com/vi/vS8i5m4LhYQ/hqdefault.jpg",
            description = "Heavy metal band Dethklok performs in Finland and accidentally summons a giant troll.",
            providerId = providerId
        ),
        PluginVideoItem(
            id = "https://www.adultswim.com/videos/the-venture-bros/dia-de-los-muertos",
            title = "The Venture Bros. - Dia de los Muertos",
            uploaderName = "Adult Swim",
            uploaderAvatarUrl = "https://i.ytimg.com/vi/pPz7L40K2v8/hqdefault.jpg",
            viewCount = 4900000L,
            durationSeconds = 1320L,
            uploadDate = "2004-08-07",
            thumbnailUrl = "https://i.ytimg.com/vi/pPz7L40K2v8/hqdefault.jpg",
            description = "Dr. Venture visits Tijuana for a medical procedure while Hank and Dean explore Mexico.",
            providerId = providerId
        ),
        PluginVideoItem(
            id = "https://www.adultswim.com/videos/primal/spear-and-fang",
            title = "Genndy Tartakovsky's Primal - Spear and Fang",
            uploaderName = "Adult Swim",
            uploaderAvatarUrl = "https://i.ytimg.com/vi/CO2O0vG2a_M/hqdefault.jpg",
            viewCount = 8200000L,
            durationSeconds = 1320L,
            uploadDate = "2019-10-08",
            thumbnailUrl = "https://i.ytimg.com/vi/CO2O0vG2a_M/hqdefault.jpg",
            description = "A caveman and a tyrannosaurus bond over shared tragedy in a prehistoric world.",
            providerId = providerId
        ),
        PluginVideoItem(
            id = "https://www.adultswim.com/videos/space-ghost-coast-to-coast/knifin-around",
            title = "Space Ghost Coast to Coast - Knifin' Around",
            uploaderName = "Adult Swim",
            uploaderAvatarUrl = "https://i.ytimg.com/vi/Yx53p1G4mGg/hqdefault.jpg",
            viewCount = 3100000L,
            durationSeconds = 660L,
            uploadDate = "1994-04-15",
            thumbnailUrl = "https://i.ytimg.com/vi/Yx53p1G4mGg/hqdefault.jpg",
            description = "Space Ghost interviews Björk and Thom Yorke in this classic animated talk show.",
            providerId = providerId
        ),
        PluginVideoItem(
            id = "https://www.adultswim.com/videos/joe-pera-talks-with-you/joe-pera-reads-you-the-church-announcement",
            title = "Joe Pera Talks With You - Church Announcement",
            uploaderName = "Adult Swim",
            uploaderAvatarUrl = "https://i.ytimg.com/vi/sT1p0S5iC5o/hqdefault.jpg",
            viewCount = 2700000L,
            durationSeconds = 660L,
            uploadDate = "2018-05-20",
            thumbnailUrl = "https://i.ytimg.com/vi/sT1p0S5iC5o/hqdefault.jpg",
            description = "Joe Pera discovers the song 'Baba O'Riley' by The Who and cannot stop playing it.",
            providerId = providerId
        ),
        PluginVideoItem(
            id = "https://www.adultswim.com/videos/off-the-air/liquid",
            title = "Off the Air - Liquid",
            uploaderName = "Adult Swim",
            uploaderAvatarUrl = "https://i.ytimg.com/vi/2d7i3i3kX8A/hqdefault.jpg",
            viewCount = 4100000L,
            durationSeconds = 660L,
            uploadDate = "2011-01-01",
            thumbnailUrl = "https://i.ytimg.com/vi/2d7i3i3kX8A/hqdefault.jpg",
            description = "Experimental psychedelic video art showcase featuring fluid animation and soundscapes.",
            providerId = providerId
        ),
        PluginVideoItem(
            id = "https://www.adultswim.com/streams",
            title = "Adult Swim - 24/7 Live Stream",
            uploaderName = "Adult Swim",
            uploaderAvatarUrl = "https://i.ytimg.com/vi/LiveAdultSwim/hqdefault.jpg",
            viewCount = 15000000L,
            durationSeconds = 0L,
            uploadDate = "Live",
            thumbnailUrl = "https://i.ytimg.com/vi/LiveAdultSwim/hqdefault.jpg",
            description = "Official Adult Swim 24/7 continuous stream broadcast.",
            providerId = providerId
        )
    )

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        PagedResult(items = catalog, nextPageToken = null, hasMore = false)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val q = query.lowercase().trim()
        if (q.isBlank()) {
            return@withContext PagedResult(items = catalog, nextPageToken = null, hasMore = false)
        }

        // Filter catalog or generate dynamic Adult Swim video search entry
        val matches = catalog.filter {
            it.title.lowercase().contains(q) ||
            it.description?.lowercase()?.contains(q) == true
        }.toMutableList()

        if (matches.isEmpty() || q.contains("adult") || q.contains("swim")) {
            val formattedUrl = if (q.startsWith("http://") || q.startsWith("https://")) {
                q
            } else {
                "https://www.adultswim.com/videos/${q.replace(" ", "-")}"
            }
            matches.add(
                0,
                PluginVideoItem(
                    id = formattedUrl,
                    title = "Adult Swim: ${query.take(50)}",
                    uploaderName = "Adult Swim",
                    uploaderAvatarUrl = null,
                    viewCount = 1000000L,
                    durationSeconds = 1200L,
                    uploadDate = "2024",
                    thumbnailUrl = "https://i.ytimg.com/vi/E8cq8s6CAnI/hqdefault.jpg",
                    description = "Extract and play Adult Swim video stream via yt-dlp geo-unblocked engine.",
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
            title = if (title.isNotBlank()) "Adult Swim - $title" else "Adult Swim Video",
            uploaderName = "Adult Swim",
            uploaderAvatarUrl = null,
            viewCount = 500000L,
            durationSeconds = 1200L,
            uploadDate = "2024",
            thumbnailUrl = "https://i.ytimg.com/vi/E8cq8s6CAnI/hqdefault.jpg",
            description = "Adult Swim stream requested: $idOrUrl",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val targetUrl = if (idOrUrl.startsWith("http://") || idOrUrl.startsWith("https://")) {
            idOrUrl
        } else {
            "https://www.adultswim.com/videos/$idOrUrl"
        }

        val ctx: Context? = try {
            MainApplication.appContext
        } catch (_: Throwable) {
            null
        }

        val streams = mutableListOf<PluginVideoStream>()
        var extractedTitle = "Adult Swim Video"
        var extractedDesc = "Adult Swim stream extracted with yt-dlp geo-unblock"
        var extractedThumb: String? = null
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
            "Referer" to "https://www.adultswim.com/",
            "Origin" to "https://www.adultswim.com"
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
            channelName = "Adult Swim",
            channelAvatarUrl = "https://i.ytimg.com/vi/E8cq8s6CAnI/hqdefault.jpg",
            viewCount = 1000000L,
            likeCount = 50000L,
            uploadDate = "2024",
            thumbnailUrl = extractedThumb ?: "https://i.ytimg.com/vi/E8cq8s6CAnI/hqdefault.jpg",
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
