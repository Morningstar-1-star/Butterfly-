package com.example.cloudsocial.telegram

import com.example.cloudsocial.db.CloudSocialMediaEntity
import com.example.cloudsocial.db.CloudSocialSourceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

data class TelegramUrlInfo(
    val channelUsername: String,
    val messageId: Long? = null,
    val isPrivateChat: Boolean = false,
    val inviteHash: String? = null
)

class TelegramSourceResolver {

    companion object {
        private val CHANNEL_URL_PATTERN = Pattern.compile("(?:https?://)?t\\.me/(?:s/)?([a-zA-Z0-9_]+)(?:/(\\d+))?")
        private val PRIVATE_CHAT_PATTERN = Pattern.compile("(?:https?://)?t\\.me/c/(\\d+)/(\\d+)")
        private val INVITE_LINK_PATTERN = Pattern.compile("(?:https?://)?t\\.me/\\+([a-zA-Z0-9_-]+)")
        private val USERNAME_PATTERN = Pattern.compile("^@?([a-zA-Z0-9_]{4,32})$")

        fun parseUrl(input: String): TelegramUrlInfo? {
            val clean = input.trim()

            // 1. Check invite link
            val inviteMatcher = INVITE_LINK_PATTERN.matcher(clean)
            if (inviteMatcher.find()) {
                val hash = inviteMatcher.group(1) ?: return null
                return TelegramUrlInfo(channelUsername = "joinchat_$hash", inviteHash = hash, isPrivateChat = true)
            }

            // 2. Check private channel/chat message (c/12345/678)
            val privateMatcher = PRIVATE_CHAT_PATTERN.matcher(clean)
            if (privateMatcher.find()) {
                val chatId = privateMatcher.group(1) ?: ""
                val msgId = privateMatcher.group(2)?.toLongOrNull()
                return TelegramUrlInfo(channelUsername = "c_$chatId", messageId = msgId, isPrivateChat = true)
            }

            // 3. Check public channel / message URL
            val channelMatcher = CHANNEL_URL_PATTERN.matcher(clean)
            if (channelMatcher.find()) {
                val username = channelMatcher.group(1) ?: return null
                if (username == "s" || username == "c") return null
                val msgId = channelMatcher.group(2)?.toLongOrNull()
                return TelegramUrlInfo(channelUsername = username, messageId = msgId)
            }

            // 4. Check @username format
            val userMatcher = USERNAME_PATTERN.matcher(clean)
            if (userMatcher.find()) {
                val username = userMatcher.group(1) ?: return null
                return TelegramUrlInfo(channelUsername = username)
            }

            return null
        }
    }

    suspend fun scanChannel(source: CloudSocialSourceEntity, maxItems: Int = 100): List<CloudSocialMediaEntity> = withContext(Dispatchers.IO) {
        val parsed = parseUrl(source.sourceUrl) ?: return@withContext emptyList()
        val channelUsername = parsed.channelUsername

        val mediaList = mutableListOf<CloudSocialMediaEntity>()

        try {
            // Crawl Telegram Web preview channel (t.me/s/username)
            val webUrl = "https://t.me/s/$channelUsername"
            val doc: Document = Jsoup.connect(webUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .timeout(10000)
                .get()

            val messageElements = doc.select(".tgme_widget_message")
            for (element in messageElements) {
                val rawMsgId = element.attr("data-post") // e.g., "channel/123"
                val messageId = rawMsgId.substringAfterLast("/", "")

                // Extract Video / Animation elements
                val videoElem = element.select("video").first()
                val photoElem = element.select(".tgme_widget_message_photo_wrap").first()
                val textElem = element.select(".tgme_widget_message_text").first()
                val titleOrText = textElem?.text()?.take(100) ?: "Telegram Post #${messageId.ifBlank { "Media" }}"
                val fullCaption = textElem?.text() ?: ""

                var videoStreamUrl = videoElem?.attr("src")
                if (videoStreamUrl.isNullOrBlank()) {
                    // Check video wrap background or data attribute
                    videoStreamUrl = element.select(".tgme_widget_message_video_player video").attr("src")
                }

                var thumbUrl = photoElem?.attr("style")?.let { style ->
                    val urlMatcher = Pattern.compile("url\\('([^']+)'\\)").matcher(style)
                    if (urlMatcher.find()) urlMatcher.group(1) else null
                }

                val mimeType = if (!videoStreamUrl.isNullOrBlank()) "video/mp4" else if (!thumbUrl.isNullOrBlank()) "image/jpeg" else "video/mp4"
                val mediaCategory = if (!videoStreamUrl.isNullOrBlank()) "video" else if (!thumbUrl.isNullOrBlank()) "image" else "video"

                // Create stable media item ID
                val itemKey = "telegram_${channelUsername}_${messageId.ifBlank { System.currentTimeMillis().toString() }}"
                val postUrl = if (messageId.isNotBlank()) "https://t.me/$channelUsername/$messageId" else source.sourceUrl

                val mediaItem = CloudSocialMediaEntity(
                    id = itemKey,
                    sourceId = source.id,
                    type = "TELEGRAM",
                    remoteId = messageId.ifBlank { itemKey },
                    parentId = channelUsername,
                    title = titleOrText,
                    caption = fullCaption,
                    sourceUrl = postUrl,
                    directStreamUrl = videoStreamUrl,
                    thumbnailUrl = thumbUrl,
                    mimeType = mimeType,
                    fileSize = 0L,
                    formattedSize = "Telegram Media",
                    durationMs = 0L,
                    mediaCategory = mediaCategory,
                    dateTimestamp = System.currentTimeMillis(),
                    resolution = "HD"
                )

                mediaList.add(mediaItem)
                if (mediaList.size >= maxItems) break
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext mediaList
    }

    suspend fun resolveStreamUrl(mediaItem: CloudSocialMediaEntity): String = withContext(Dispatchers.IO) {
        if (!mediaItem.directStreamUrl.isNullOrBlank() && mediaItem.directStreamUrl.startsWith("http")) {
            return@withContext mediaItem.directStreamUrl
        }

        val parsed = parseUrl(mediaItem.sourceUrl)
        if (parsed != null && parsed.channelUsername.isNotBlank() && parsed.messageId != null) {
            try {
                // Fetch single message web page
                val msgUrl = "https://t.me/s/${parsed.channelUsername}/${parsed.messageId}"
                val doc = Jsoup.connect(msgUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(8000)
                    .get()

                val videoSrc = doc.select("video").first()?.attr("src")
                if (!videoSrc.isNullOrBlank() && videoSrc.startsWith("http")) {
                    return@withContext videoSrc
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return@withContext mediaItem.sourceUrl
    }
}
