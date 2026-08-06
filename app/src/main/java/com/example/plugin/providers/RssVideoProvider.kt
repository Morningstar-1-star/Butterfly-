package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

class RssVideoProvider(
    private val feedUrl: String = "https://rss.art19.com/apology-line",
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "rss_video"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val resp = http.get(feedUrl)
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList())

        val list = parseRssFeed(resp.body)
        PagedResult(items = list)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        if (query.startsWith("http")) {
            val customProvider = RssVideoProvider(query, http)
            customProvider.home(pageToken)
        } else {
            val all = home().items
            val filtered = all.filter { it.title.contains(query, ignoreCase = true) }
            PagedResult(items = filtered)
        }
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        PluginVideoItem(
            id = idOrUrl,
            title = idOrUrl.substringAfterLast("/"),
            uploaderName = "RSS Feed",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val isAudio = idOrUrl.contains(".mp3") || idOrUrl.contains(".m4a")
        val audioStreams = if (isAudio) listOf(
            PluginAudioStream(
                url = idOrUrl,
                qualityLabel = "128kbps",
                format = "mp3"
            )
        ) else emptyList()

        val videoStreams = if (!isAudio) listOf(
            PluginVideoStream(
                url = idOrUrl,
                qualityLabel = "720p",
                format = "mp4",
                isMuxed = true
            )
        ) else emptyList()

        PluginStreamInfo(
            id = idOrUrl,
            url = idOrUrl,
            title = idOrUrl.substringAfterLast("/"),
            channelName = "RSS Media Feed",
            videoStreams = videoStreams,
            audioStreams = audioStreams
        )
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> = withContext(Dispatchers.IO) {
        PagedResult(items = emptyList())
    }

    override suspend fun getSubtitles(idOrUrl: String): List<PluginSubtitle> = withContext(Dispatchers.IO) {
        emptyList()
    }

    override suspend fun getChannel(channelIdOrUrl: String): PluginChannel = withContext(Dispatchers.IO) {
        PluginChannel(id = "rss", name = "RSS Channel")
    }

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist = withContext(Dispatchers.IO) {
        PluginPlaylist(id = playlistIdOrUrl, title = "RSS Playlist", uploaderName = "RSS")
    }

    override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> = withContext(Dispatchers.IO) {
        home().items
    }

    private fun parseRssFeed(xmlStr: String): List<PluginVideoItem> {
        val list = mutableListOf<PluginVideoItem>()
        try {
            val doc = Jsoup.parse(xmlStr, "", Parser.xmlParser())
            val channelTitle = doc.select("channel > title").text()
            val items = doc.select("item")
            for (element in items) {
                val title = element.select("title").text()
                val enclosure = element.select("enclosure")
                val mediaUrl = enclosure.attr("url")
                val pubDate = element.select("pubDate").text()
                val itunesPic = element.select("itunes|image").attr("href")

                if (mediaUrl.isNotBlank()) {
                    list.add(
                        PluginVideoItem(
                            id = mediaUrl,
                            title = title,
                            uploaderName = if (channelTitle.isNotBlank()) channelTitle else "RSS Feed",
                            uploadDate = pubDate,
                            thumbnailUrl = if (itunesPic.isNotBlank()) itunesPic else null,
                            providerId = providerId
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
