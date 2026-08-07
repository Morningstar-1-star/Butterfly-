package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * OpenSubtitles & SubDL Subtitle Provider Plugin
 */
class SubtitleProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "subtitles_multi"
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsSearch = true,
        supportsSubtitles = true
    )

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = PagedResult(emptyList())

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = PagedResult(emptyList())

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = PluginVideoItem(id = idOrUrl, title = idOrUrl, uploaderName = "Subtitles Engine", providerId = providerId)

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo {
        val subs = getSubtitles(idOrUrl)
        return PluginStreamInfo(id = idOrUrl, url = "", title = "Subtitle Provider", channelName = "OpenSubtitles", subtitles = subs)
    }

    override suspend fun getSubtitles(idOrUrl: String): List<PluginSubtitle> = withContext(Dispatchers.IO) {
        listOf(
            PluginSubtitle(
                url = "https://rest.opensubtitles.org/subtitles/en/$idOrUrl.vtt",
                languageCode = "en",
                languageName = "English (OpenSubtitles)",
                format = "vtt"
            ),
            PluginSubtitle(
                url = "https://subdl.com/api/v1/subtitles?imdb_id=$idOrUrl&languages=en",
                languageCode = "en",
                languageName = "English (SubDL)",
                format = "srt"
            ),
            PluginSubtitle(
                url = "https://rest.opensubtitles.org/subtitles/es/$idOrUrl.vtt",
                languageCode = "es",
                languageName = "Spanish (OpenSubtitles)",
                format = "vtt"
            )
        )
    }
}
