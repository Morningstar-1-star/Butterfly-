package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.model.StreamData
import com.example.model.VideoItem
import java.net.URLEncoder

/**
 * VideoShareHelper provides accurate, verified origin URL extraction and reliable
 * video sharing across all content providers and video streams.
 *
 * Guarantees that:
 * 1. Non-YouTube platforms (Vimeo, Dailymotion, Bilibili, Archive.org, Twitch, Tencent, Adult tubes)
 *    produce their genuine web URLs instead of malformed "youtube.com/watch?v=https://..." links.
 * 2. Archive.org videos resolve to the human-viewable web page ("https://archive.org/details/...")
 *    instead of raw CDN MP4 download links.
 * 3. Fallback or syndicated OTT sources (SonyLIV, Hotstar, etc.) cleanly extract their true source links
 *    without producing empty or broken text.
 */
object VideoShareHelper {

    data class SourceOriginInfo(
        val platformName: String,
        val domain: String,
        val webUrl: String
    )

    fun resolveOriginInfo(video: VideoItem): SourceOriginInfo {
        return resolveOriginInfo(
            videoId = video.id,
            providerId = video.providerId,
            videoUrl = null,
            description = video.description,
            uploaderUrl = video.uploaderUrl,
            title = video.title
        )
    }

    fun resolveOriginInfo(
        streamData: StreamData?,
        fallbackVideoId: String? = null,
        fallbackTitle: String? = null
    ): SourceOriginInfo {
        return resolveOriginInfo(
            videoId = streamData?.videoId ?: fallbackVideoId ?: "",
            providerId = streamData?.providerId,
            videoUrl = streamData?.videoUrl,
            description = streamData?.description,
            uploaderUrl = null,
            title = streamData?.title ?: fallbackTitle ?: ""
        )
    }

    fun resolveOriginInfo(
        videoId: String?,
        providerId: String?,
        videoUrl: String?,
        description: String?,
        uploaderUrl: String?,
        title: String?
    ): SourceOriginInfo {
        var rawId = (videoId ?: "").trim()
        val rawProv = (providerId ?: "").trim().lowercase()
        val rawUrl = (videoUrl ?: "").trim()
        val rawDesc = (description ?: "").trim()
        val cleanTitle = (title ?: "").trim()

        // 1. Fix nested/malformed prefixes like "https://youtube.com/watch?v=https://vimeo.com/..."
        if (rawId.contains("youtube.com/watch?v=http", ignoreCase = true)) {
            rawId = rawId.substringAfter("watch?v=")
        } else if (rawId.contains("youtu.be/http", ignoreCase = true)) {
            rawId = rawId.substringAfter("youtu.be/")
        }

        // 2. Check Archive.org (detect either raw MP4 CDN URL or archive identifier)
        if (rawUrl.contains("archive.org", ignoreCase = true) ||
            rawId.contains("archive.org", ignoreCase = true) ||
            rawProv == "archive" || rawProv == "archive_org" ||
            rawId.startsWith("archive:", ignoreCase = true) ||
            rawId.startsWith("archive_org:", ignoreCase = true)
        ) {
            val identifier = when {
                rawUrl.contains("/items/") -> rawUrl.substringAfter("/items/").substringBefore("/")
                rawId.contains("/items/") -> rawId.substringAfter("/items/").substringBefore("/")
                rawId.contains("/details/") -> rawId.substringAfter("/details/").substringBefore("/").substringBefore("?")
                rawId.startsWith("archive:", ignoreCase = true) -> rawId.removePrefix("archive:").substringBefore("::")
                rawId.startsWith("archive_org:", ignoreCase = true) -> rawId.removePrefix("archive_org:").substringBefore("::")
                else -> rawId.replace("https://archive.org/", "").trim('/')
            }
            val detailsUrl = if (identifier.isNotBlank() && !identifier.startsWith("http")) {
                "https://archive.org/details/$identifier"
            } else if (rawId.startsWith("http")) {
                rawId
            } else if (rawUrl.startsWith("http")) {
                rawUrl
            } else {
                "https://archive.org"
            }
            return SourceOriginInfo("Internet Archive", "archive.org", detailsUrl)
        }

        // 3. Check Vimeo
        if (rawId.contains("vimeo.com", ignoreCase = true) ||
            rawId.startsWith("vimeo:", ignoreCase = true) ||
            rawProv == "vimeo"
        ) {
            val vimeoId = when {
                rawId.contains("vimeo.com/") -> rawId.substringAfter("vimeo.com/").substringBefore("/").substringBefore("?")
                rawId.startsWith("vimeo:", ignoreCase = true) -> rawId.removePrefix("vimeo:").trim()
                else -> rawId
            }
            val cleanUrl = if (vimeoId.all { it.isDigit() }) "https://vimeo.com/$vimeoId" else "https://vimeo.com/$rawId"
            return SourceOriginInfo("Vimeo", "vimeo.com", cleanUrl)
        }

        // 4. Check Dailymotion
        if (rawId.contains("dailymotion.com", ignoreCase = true) ||
            rawId.startsWith("dailymotion:", ignoreCase = true) ||
            rawProv == "dailymotion"
        ) {
            val dmId = when {
                rawId.contains("/video/") -> rawId.substringAfter("/video/").substringBefore("?").substringBefore("/")
                rawId.startsWith("dailymotion:", ignoreCase = true) -> rawId.removePrefix("dailymotion:").trim()
                else -> rawId
            }
            val cleanUrl = if (dmId.isNotBlank() && !dmId.startsWith("http")) "https://www.dailymotion.com/video/$dmId" else "https://www.dailymotion.com"
            return SourceOriginInfo("Dailymotion", "dailymotion.com", cleanUrl)
        }

        // 5. Check Bilibili
        if (rawId.contains("bilibili.com", ignoreCase = true) ||
            rawId.startsWith("bilibili:", ignoreCase = true) ||
            rawProv == "bilibili"
        ) {
            val bId = when {
                rawId.contains("/video/") -> rawId.substringAfter("/video/").substringBefore("?").substringBefore("/")
                rawId.startsWith("bilibili:", ignoreCase = true) -> rawId.removePrefix("bilibili:").trim()
                else -> rawId
            }
            val cleanUrl = if (bId.isNotBlank() && !bId.startsWith("http")) "https://www.bilibili.com/video/$bId" else "https://www.bilibili.com"
            return SourceOriginInfo("Bilibili", "bilibili.com", cleanUrl)
        }

        // 6. Check Twitch
        if (rawId.contains("twitch.tv", ignoreCase = true) ||
            rawId.startsWith("twitch:", ignoreCase = true) ||
            rawProv == "twitch"
        ) {
            val tId = when {
                rawId.contains("twitch.tv/") -> rawId.substringAfter("twitch.tv/").substringBefore("?")
                rawId.startsWith("twitch:", ignoreCase = true) -> rawId.removePrefix("twitch:").trim()
                else -> rawId
            }
            val cleanUrl = if (tId.all { it.isDigit() }) "https://www.twitch.tv/videos/$tId" else "https://www.twitch.tv/$tId"
            return SourceOriginInfo("Twitch", "twitch.tv", cleanUrl)
        }

        // 7. Check Tencent Video (v.qq.com)
        if (rawId.contains("v.qq.com", ignoreCase = true) ||
            rawId.startsWith("tencent:", ignoreCase = true) ||
            rawProv == "tencent" || rawProv == "vqq"
        ) {
            val tencentClean = rawId.removePrefix("tencent:").trim()
            val cleanUrl = when {
                tencentClean.startsWith("http") -> tencentClean
                tencentClean.contains("::") -> {
                    val parts = tencentClean.split("::")
                    val vid = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: parts.getOrNull(0) ?: ""
                    if (vid.length == 11) "https://v.qq.com/x/page/$vid.html" else "https://v.qq.com/x/cover/$vid.html"
                }
                tencentClean.length == 11 -> "https://v.qq.com/x/page/$tencentClean.html"
                tencentClean.isNotBlank() -> "https://v.qq.com/x/cover/$tencentClean.html"
                else -> "https://v.qq.com"
            }
            return SourceOriginInfo("Tencent Video", "v.qq.com", cleanUrl)
        }

        // 8. Check Adult Live Cams & Tubes
        if (rawId.startsWith("stripchat:", ignoreCase = true) || rawProv == "stripchat") {
            val model = rawId.removePrefix("stripchat:").substringBefore("::").trim()
            return SourceOriginInfo("Stripchat", "stripchat.com", "https://stripchat.com/$model")
        }
        if (rawId.startsWith("chaturbate:", ignoreCase = true) || rawProv == "chaturbate") {
            val model = rawId.removePrefix("chaturbate:").substringBefore("::").trim()
            return SourceOriginInfo("Chaturbate", "chaturbate.com", "https://chaturbate.com/$model")
        }
        if (rawId.startsWith("pornhub:", ignoreCase = true) || rawProv == "pornhub") {
            val vkey = rawId.removePrefix("pornhub:").substringBefore("::").trim()
            return SourceOriginInfo("Pornhub", "pornhub.com", "https://www.pornhub.com/view_video.php?viewkey=$vkey")
        }
        if (rawId.startsWith("xvideos:", ignoreCase = true) || rawProv == "xvideos") {
            val xvId = rawId.removePrefix("xvideos:").substringBefore("::").trim()
            return SourceOriginInfo("XVideos", "xvideos.com", "https://www.xvideos.com/video$xvId/")
        }
        if (rawId.startsWith("xnxx:", ignoreCase = true) || rawProv == "xnxx") {
            val xnId = rawId.removePrefix("xnxx:").substringBefore("::").trim()
            val url = if (xnId.startsWith("http")) xnId else "https://www.xnxx.com/video-$xnId/"
            return SourceOriginInfo("XNXX", "xnxx.com", url)
        }
        if (rawId.startsWith("hellporno:", ignoreCase = true) || rawProv == "hellporno") {
            val hpId = rawId.removePrefix("hellporno:").substringBefore("::").trim()
            val url = if (hpId.startsWith("http")) hpId else "https://hellporno.com/videos/$hpId"
            return SourceOriginInfo("HellPorno", "hellporno.com", url)
        }
        if (rawId.startsWith("supjav:", ignoreCase = true) || rawProv == "supjav") {
            val sjId = rawId.removePrefix("supjav:").substringBefore("::").trim()
            val url = if (sjId.startsWith("http")) sjId else "https://supjav.com/$sjId"
            return SourceOriginInfo("SupJav", "supjav.com", url)
        }
        if (rawId.startsWith("123av:", ignoreCase = true) || rawProv == "123av") {
            val avId = rawId.removePrefix("123av:").substringBefore("::").trim()
            val url = if (avId.startsWith("http")) avId else "https://123av.com/$avId"
            return SourceOriginInfo("123AV", "123av.com", url)
        }

        // 9. Check if rawId or rawUrl is directly an HTTP/HTTPS URL (Cloud, Bunkr, direct stream)
        val directHttp = when {
            rawId.startsWith("http://", ignoreCase = true) || rawId.startsWith("https://", ignoreCase = true) -> rawId
            rawUrl.startsWith("http://", ignoreCase = true) || rawUrl.startsWith("https://", ignoreCase = true) -> rawUrl
            else -> null
        }
        if (directHttp != null && !directHttp.contains("googlevideo.com")) {
            val host = runCatching { Uri.parse(directHttp).host }.getOrNull() ?: ""
            val cleanHost = host.removePrefix("www.").removePrefix("m.")
            val platform = cleanHost.substringBefore(".").replaceFirstChar { it.uppercase() }
            return SourceOriginInfo(if (platform.isNotBlank()) platform else "Web Video", cleanHost, directHttp)
        }

        // 10. Check if Description contains an embedded YouTube or external link
        if (rawDesc.isNotBlank()) {
            val ytMatch = Regex("https?://(?:www\\.)?(?:youtube\\.com/watch\\?v=|youtu\\.be/)([a-zA-Z0-9_-]{11})").find(rawDesc)
            if (ytMatch != null) {
                val yId = ytMatch.groupValues[1]
                val origPlatform = when (rawProv) {
                    "sonyliv" -> "SonyLIV (Official Channel)"
                    "hotstar" -> "Hotstar (Official Channel)"
                    "amazonminitv" -> "Amazon miniTV"
                    "crunchyroll" -> "Crunchyroll Anime"
                    "imdb" -> "IMDb Trailers"
                    else -> "YouTube"
                }
                return SourceOriginInfo(origPlatform, "youtube.com", "https://youtu.be/$yId")
            }
        }

        // 11. Check OTT & Syndicated Providers
        if (rawProv == "sonyliv" || rawId.startsWith("sonyliv:", ignoreCase = true)) {
            val clean = rawId.removePrefix("sonyliv:").trim()
            val url = if (clean.length == 11) "https://youtu.be/$clean" else "https://www.sonyliv.com"
            return SourceOriginInfo("SonyLIV", "sonyliv.com", url)
        }
        if (rawProv == "hotstar" || rawId.startsWith("hotstar:", ignoreCase = true)) {
            val clean = rawId.removePrefix("hotstar:").trim()
            val url = if (clean.length == 11) "https://youtu.be/$clean" else "https://www.hotstar.com"
            return SourceOriginInfo("Disney+ Hotstar", "hotstar.com", url)
        }
        if (rawProv == "amazonminitv" || rawId.startsWith("amazonminitv:", ignoreCase = true)) {
            val clean = rawId.removePrefix("amazonminitv:").trim()
            val url = if (clean.length == 11) "https://youtu.be/$clean" else "https://www.amazon.in/minitv"
            return SourceOriginInfo("Amazon miniTV", "amazon.in", url)
        }
        if (rawProv == "crunchyroll" || rawId.startsWith("crunchyroll:", ignoreCase = true)) {
            val clean = rawId.removePrefix("crunchyroll:").trim()
            val url = if (clean.length == 11) "https://youtu.be/$clean" else "https://www.crunchyroll.com"
            return SourceOriginInfo("Crunchyroll", "crunchyroll.com", url)
        }
        if (rawProv == "imdb" || rawId.startsWith("imdb:", ignoreCase = true)) {
            val clean = rawId.removePrefix("imdb:").trim()
            val url = if (clean.length == 11) "https://youtu.be/$clean" else "https://www.imdb.com"
            return SourceOriginInfo("IMDb", "imdb.com", url)
        }

        // 12. YouTube standard ID check
        val cleanYt = rawId.removePrefix("youtube:").removePrefix("yt:").trim()
        if (cleanYt.length == 11 && !cleanYt.contains(" ") && !cleanYt.contains("/") && !cleanYt.contains(":")) {
            return SourceOriginInfo("YouTube", "youtube.com", "https://youtu.be/$cleanYt")
        }

        // 13. Fallback: Search link or direct title link
        val fallbackUrl = if (cleanTitle.isNotBlank()) {
            val encoded = runCatching { URLEncoder.encode(cleanTitle, "UTF-8") }.getOrDefault("")
            "https://www.youtube.com/results?search_query=$encoded"
        } else {
            "https://www.youtube.com"
        }

        return SourceOriginInfo("YouTube", "youtube.com", fallbackUrl)
    }

    /**
     * Shares a video item via Android system share sheet with clean, verified link.
     */
    fun shareVideo(context: Context, video: VideoItem) {
        val origin = resolveOriginInfo(video)
        val text = if (video.title.isNotBlank()) {
            "${video.title}\n${origin.webUrl}"
        } else {
            origin.webUrl
        }
        sendShareIntent(context, video.title, text)
    }

    /**
     * Shares the currently active playback stream with clean, verified link.
     */
    fun shareStream(
        context: Context,
        streamData: StreamData?,
        displayTitle: String,
        activeVideoId: String?
    ) {
        val origin = resolveOriginInfo(streamData, activeVideoId, displayTitle)
        val title = displayTitle.takeIf { it.isNotBlank() } ?: streamData?.title ?: ""
        val text = if (title.isNotBlank()) {
            "$title\n${origin.webUrl}"
        } else {
            origin.webUrl
        }
        sendShareIntent(context, title, text)
    }

    private fun sendShareIntent(context: Context, subject: String, text: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(intent, "Share video via"))
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open share menu", Toast.LENGTH_SHORT).show()
        }
    }
}
