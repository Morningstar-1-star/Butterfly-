package com.example.plugin.providers

import android.content.Context
import android.util.Log
import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import com.example.util.CloudFoldersSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class TelegramProvider(private val context: Context? = null) : ContentProviderApi {

    override val providerId: String = "telegram"

    override val capabilities: ProviderCapabilities
        get() = ProviderCapabilities(
            supportsSearch = true,
            supportsMovie = false,
            supportsSeries = false,
            supportsAnime = false,
            supportsTorrent = false
        )

    private val http = HttpBridge()

    companion object {
        private const val TAG = "TelegramProvider"
    }

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val channelUrls = if (context != null) {
            CloudFoldersSettingsManager.getTelegramChannelUrls(context)
        } else {
            emptyList()
        }

        val items = mutableListOf<PluginVideoItem>()

        if (channelUrls.isEmpty()) {
            return@withContext PagedResult(emptyList(), nextPageToken = null)
        }

        channelUrls.forEach { channelUrl ->
            try {
                val cleanUrl = if (channelUrl.startsWith("http")) channelUrl else "https://t.me/s/$channelUrl"
                val webUrl = if (!cleanUrl.contains("t.me/s/")) cleanUrl.replace("t.me/", "t.me/s/") else cleanUrl

                val resp = http.get(webUrl)
                if (resp.statusCode == 200) {
                    val html = resp.body
                    val channelName = cleanUrl.substringAfter("t.me/s/").substringBefore("/").ifBlank { "Telegram Channel" }

                    // Match each post message block in telegram web preview
                    val messageBlocks = html.split(Regex("class=\"tgme_widget_message\\s+"))
                    
                    messageBlocks.drop(1).forEachIndexed { blockIdx, block ->
                        try {
                            // Extract video src or video player link
                            val videoSrcMatch = Regex("src=\"([^\"]+\\.(?:mp4|m3u8)[^\"]*)\"", RegexOption.IGNORE_CASE).find(block)
                                ?: Regex("src=\"(https?://v\\.tgme\\.org/[^\"]+)\"", RegexOption.IGNORE_CASE).find(block)
                                ?: Regex("<video[^>]+src=\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(block)

                            // Extract post ID
                            val postIdMatch = Regex("data-post=\"([^\"]+)\"").find(block)
                                ?: Regex("href=\"https://t\\.me/s/([^\"]+)\"").find(block)
                                ?: Regex("href=\"https://t\\.me/([^\"]+)\"").find(block)

                            // Extract text content
                            val textMatch = Regex("class=\"tgme_widget_message_text[^\"]*\">([\\s\\S]*?)</div>").find(block)
                            val rawText = textMatch?.groupValues?.get(1)
                                ?.replace(Regex("<[^>]*>"), "")
                                ?.replace("&amp;", "&")
                                ?.replace("&quot;", "\"")
                                ?.trim() ?: ""

                            // Extract thumbnail
                            val thumbMatch = Regex("background-image:url\\('([^']+)'\\)").find(block)
                                ?: Regex("src=\"(https://cdn[^\"]+)\"").find(block)
                            val thumbUrl = thumbMatch?.groupValues?.get(1) ?: "https://telegram.org/img/t_logo.png"

                            val videoUrl = videoSrcMatch?.groupValues?.get(1)?.replace("&amp;", "&")
                            val postId = postIdMatch?.groupValues?.get(1)

                            if (videoUrl != null || postId != null) {
                                val postTitle = rawText.lines().firstOrNull { it.isNotBlank() }?.take(80) 
                                    ?: "Telegram Video #${blockIdx + 1}"

                                val streamId = if (videoUrl != null) {
                                    "tg_vid_" + URLEncoder.encode(videoUrl, "UTF-8")
                                } else {
                                    "tg_post_" + URLEncoder.encode(postId!!, "UTF-8")
                                }

                                items.add(
                                    PluginVideoItem(
                                        id = streamId,
                                        title = "[$channelName] $postTitle",
                                        uploaderName = channelName,
                                        viewCount = (500..80000).random().toLong(),
                                        durationSeconds = 0L,
                                        uploadDate = "2026-08-16",
                                        thumbnailUrl = thumbUrl,
                                        providerId = providerId
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing block in $channelUrl: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error loading Telegram channel $channelUrl: ${e.message}")
            }
        }

        PagedResult(items, nextPageToken = null)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val allHome = home(pageToken).items
        val filtered = allHome.filter { 
            it.title.contains(query, ignoreCase = true) || it.uploaderName?.contains(query, ignoreCase = true) == true
        }

        PagedResult(filtered, nextPageToken = null)
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        PluginVideoItem(
            id = idOrUrl,
            title = "Telegram Media Stream",
            uploaderName = "Telegram Channel",
            durationSeconds = 0L,
            thumbnailUrl = "https://telegram.org/img/t_logo.png",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val rawValue = when {
            idOrUrl.startsWith("tg_vid_") -> java.net.URLDecoder.decode(idOrUrl.removePrefix("tg_vid_"), "UTF-8")
            idOrUrl.startsWith("tg_post_") -> {
                val postPath = java.net.URLDecoder.decode(idOrUrl.removePrefix("tg_post_"), "UTF-8")
                "https://t.me/$postPath?embed=1"
            }
            idOrUrl.startsWith("http") -> idOrUrl
            else -> "https://t.me/s/$idOrUrl"
        }

        val isDirectFile = rawValue.endsWith(".mp4") || rawValue.contains("v.tgme.org") || rawValue.contains(".m3u8")
        val format = if (isDirectFile) "mp4" else "embed"

        val videoStreams = listOf(
            PluginVideoStream(
                url = rawValue,
                qualityLabel = if (isDirectFile) "Telegram Direct MP4 Stream" else "Telegram Web Player Embed",
                format = format,
                isMuxed = true
            )
        )

        PluginStreamInfo(
            id = idOrUrl,
            url = rawValue,
            title = "Telegram Media Stream",
            channelName = "Telegram Channel",
            description = "High Speed Telegram Media Stream",
            videoStreams = videoStreams
        )
    }
}
