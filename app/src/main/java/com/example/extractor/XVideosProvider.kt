package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object XVideosProvider {
    private const val TAG = "XVideosProvider"
    const val PROVIDER_ID = "xvideos"

    private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getHome(limit: Int = 20, page: Int = 1): List<VideoItem> {
        val target = if (page <= 1) "https://www.xvideos.com/new/1" else "https://www.xvideos.com/new/$page"
        return parseXVideosHtml(target, limit)
    }

    fun search(query: String, limit: Int = 20, page: Int = 1): List<VideoItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val target = if (page <= 1) "https://www.xvideos.com/?k=$encoded" else "https://www.xvideos.com/?k=$encoded&p=$page"
        return parseXVideosHtml(target, limit)
    }

    private fun parseXVideosHtml(targetUrl: String, limit: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        val seenUrls = mutableSetOf<String>()
        try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Cookie", "age_verified=1; platform=pc; has_consent=1")
                .header("Referer", "https://www.xvideos.com/")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val doc = Jsoup.parse(html, "https://www.xvideos.com/")

            // 1. PRIMARY PARSER: Card-based Jsoup parsing
            // Each video block is parsed in total isolation so title, thumbnail, duration and link can NEVER desync
            val cards = doc.select(".thumb-block, div[id^=video_], div[data-id], .mozaique > div")
            for (card in cards) {
                if (list.size >= limit) break

                val linkEl = card.selectFirst("a[href*=/video]")
                    ?: card.selectFirst(".thumb a")
                    ?: card.selectFirst("p.title a")
                    ?: card.selectFirst("a[href^=\"/video\"]")
                    ?: continue

                var href = linkEl.attr("href").trim()
                if (href.isBlank() || href == "#" || href.contains("/channels/") || href.contains("/tags/") || href.contains("/profiles/") || href.contains("/categories/")) {
                    continue
                }

                if (!href.startsWith("http")) {
                    href = if (href.startsWith("/")) "https://www.xvideos.com$href" else "https://www.xvideos.com/$href"
                }

                if (seenUrls.contains(href)) continue
                seenUrls.add(href)

                val videoId = card.attr("data-id").ifBlank {
                    card.attr("id").removePrefix("video_").ifBlank {
                        Regex("""/video(?:\.?\w+)?/([0-9a-zA-Z_-]+)""").find(href)?.groupValues?.get(1) ?: href
                    }
                }

                // Title: extracted specifically from this card
                var title = ""
                val titleEl = card.selectFirst("p.title a") ?: card.selectFirst(".title a") ?: card.selectFirst(".title")
                if (titleEl != null) {
                    title = titleEl.attr("title").ifBlank { titleEl.text() }
                }
                if (title.isBlank()) {
                    title = linkEl.attr("title").ifBlank { linkEl.text() }
                }
                if (title.isBlank()) {
                    title = card.selectFirst("img")?.attr("alt") ?: ""
                }
                if (title.isBlank() || title.equals("XVideos", ignoreCase = true)) {
                    val slug = href.substringAfterLast("/").substringBefore("?").replace("_", " ").replace("-", " ")
                    if (slug.isNotBlank() && slug.length > 3) {
                        title = slug
                    }
                }
                title = Parser.unescapeEntities(title.trim(), false)
                    .replace(Regex("""\s*-\s*XVIDEOS\.COM\s*$""", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("""\s*-\s*Xvideos\.com\s*$""", RegexOption.IGNORE_CASE), "")
                    .trim()
                if (title.isBlank()) title = "XVideos Video $videoId"

                // Thumbnail: extracted specifically from this card
                var thumb = ""
                val imgEl = card.selectFirst(".thumb a img") ?: card.selectFirst("img")
                if (imgEl != null) {
                    val candidates = listOf(
                        imgEl.attr("data-src"),
                        imgEl.attr("data-thumb"),
                        imgEl.attr("data-image"),
                        imgEl.attr("data-poster"),
                        imgEl.attr("data-original"),
                        imgEl.attr("data-pv"),
                        imgEl.attr("data-src1"),
                        imgEl.attr("src")
                    )
                    for (cand in candidates) {
                        val c = cand.trim()
                        if (c.isNotBlank() &&
                            !c.contains("blank.gif") &&
                            !c.contains("loading.gif") &&
                            !c.contains("pixel.gif") &&
                            !c.startsWith("data:image") &&
                            (c.contains(".jpg") || c.contains(".jpeg") || c.contains(".webp") || c.contains(".png") || c.contains("xvideos-cdn") || c.contains("xv-cdn"))
                        ) {
                            thumb = c
                            break
                        }
                    }
                }

                if (thumb.isBlank()) {
                    val cardHtml = card.html()
                    val thumbRegex = Pattern.compile("""(?:data-src|data-thumb|data-image|data-poster|src)=["']([^"'\s,]+?\.(?:jpg|jpeg|webp|png)[^"'\s,]*)["']""", Pattern.CASE_INSENSITIVE)
                    val tm = thumbRegex.matcher(cardHtml)
                    while (tm.find()) {
                        val c = tm.group(1)?.trim() ?: continue
                        if (!c.contains("blank.gif") && !c.contains("loading.gif") && !c.contains("pixel.gif") && !c.startsWith("data:")) {
                            thumb = c
                            break
                        }
                    }
                }

                if (thumb.startsWith("//")) thumb = "https:$thumb"
                else if (thumb.startsWith("/")) thumb = "https://www.xvideos.com$thumb"

                // Duration
                val durText = card.selectFirst(".duration, span.duration, .metadata .duration, .bg .duration")?.text()?.trim()
                val durSec = parseDurationToSeconds(durText)

                // Uploader
                val uploaderEl = card.selectFirst(".metadata .name a, .metadata a, .name a, .uploader a, .profile a")
                val uploaderName = uploaderEl?.text()?.trim()?.ifBlank { "XVideos" } ?: "XVideos"
                val uploaderUrl = uploaderEl?.attr("href")?.let {
                    if (it.startsWith("/")) "https://www.xvideos.com$it" else it
                }

                // Preview frames for horizontal scrubber
                val previewList = if (thumb.isNotBlank()) {
                    com.example.util.PreviewFrameResolver.resolvePreviewFrames(
                        VideoItem(
                            id = href,
                            title = title,
                            uploaderName = uploaderName,
                            uploaderUrl = uploaderUrl,
                            thumbnailUrl = thumb,
                            durationSeconds = durSec,
                            providerId = PROVIDER_ID
                        )
                    )
                } else emptyList()

                // Optional preview video clip URL if present on hover
                val previewClip = imgEl?.attr("data-pv")?.takeIf { it.isNotBlank() && it.startsWith("http") }
                    ?: card.attr("data-pv").takeIf { it.isNotBlank() && it.startsWith("http") }

                list.add(
                    VideoItem(
                        id = href,
                        title = title,
                        uploaderName = uploaderName,
                        uploaderUrl = uploaderUrl,
                        thumbnailUrl = thumb,
                        durationSeconds = durSec,
                        providerId = PROVIDER_ID,
                        previewThumbnails = previewList,
                        previewClipUrl = previewClip
                    )
                )
            }

            // 2. FALLBACK PARSER: If Jsoup card selectors found nothing, use block-scoped regex
            if (list.isEmpty()) {
                val blockPattern = Pattern.compile("""<div[^>]*class="[^"]*thumb-block[^"]*"[^>]*>(.*?)</div>\s*</div>""", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
                val blockMatcher = blockPattern.matcher(html)
                while (blockMatcher.find() && list.size < limit) {
                    val block = blockMatcher.group(1) ?: continue

                    val linkMatcher = Pattern.compile("""href="(/video(?:\.?\d+|[^"'\s]+)/[^"]*)"""", Pattern.CASE_INSENSITIVE).matcher(block)
                    if (!linkMatcher.find()) continue
                    var path = linkMatcher.group(1) ?: continue
                    if (!path.startsWith("http")) {
                        path = if (path.startsWith("/")) "https://www.xvideos.com$path" else "https://www.xvideos.com/$path"
                    }
                    if (seenUrls.contains(path)) continue
                    seenUrls.add(path)

                    var title = ""
                    val titleMatcher = Pattern.compile("""(?:title="([^"]+)"|class="[^"]*title[^"]*"[^>]*>([^<]+))""", Pattern.CASE_INSENSITIVE).matcher(block)
                    if (titleMatcher.find()) {
                        title = (titleMatcher.group(1) ?: titleMatcher.group(2) ?: "").trim()
                    }
                    if (title.isBlank() || title.equals("XVideos", ignoreCase = true)) {
                        val slug = path.substringAfterLast("/").substringBefore("?").replace("_", " ").replace("-", " ")
                        if (slug.isNotBlank()) title = slug
                    }
                    title = Parser.unescapeEntities(title, false)
                        .replace(Regex("""\s*-\s*XVIDEOS\.COM\s*$""", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("""\s*-\s*Xvideos\.com\s*$""", RegexOption.IGNORE_CASE), "")
                        .trim()

                    var thumb = ""
                    val thumbMatcher = Pattern.compile("""(?:data-src|data-thumb|data-image|data-poster|src)=["']([^"'\s,]+?\.(?:jpg|jpeg|webp|png)[^"'\s,]*)["']""", Pattern.CASE_INSENSITIVE).matcher(block)
                    while (thumbMatcher.find()) {
                        val c = thumbMatcher.group(1)?.trim() ?: continue
                        if (!c.contains("blank.gif") && !c.contains("loading.gif") && !c.contains("pixel.gif") && !c.startsWith("data:")) {
                            thumb = c
                            break
                        }
                    }
                    if (thumb.startsWith("//")) thumb = "https:$thumb"
                    else if (thumb.startsWith("/")) thumb = "https://www.xvideos.com$thumb"

                    val durMatcher = Pattern.compile("""(?:duration|min|duration-box)[^>]*>([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?|[0-9]+\s*(?:min|h|sec))""", Pattern.CASE_INSENSITIVE).matcher(block)
                    val durSec = if (durMatcher.find()) parseDurationToSeconds(durMatcher.group(1)) else -1L

                    val previewList = if (thumb.isNotBlank()) {
                        com.example.util.PreviewFrameResolver.resolvePreviewFrames(
                            VideoItem(id = path, title = title, uploaderName = "XVideos", thumbnailUrl = thumb, providerId = PROVIDER_ID)
                        )
                    } else emptyList()

                    list.add(
                        VideoItem(
                            id = path,
                            title = title,
                            uploaderName = "XVideos",
                            thumbnailUrl = thumb,
                            durationSeconds = durSec,
                            providerId = PROVIDER_ID,
                            previewThumbnails = previewList
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "XVideos parse error: ${e.message}")
        }
        return list
    }

    suspend fun getStreamData(urlOrId: String, context: Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val cleanInput = urlOrId.trim()
        val targetUrl = when {
            cleanInput.startsWith("http") -> cleanInput
            cleanInput.startsWith("/") -> "https://www.xvideos.com$cleanInput"
            else -> "https://www.xvideos.com/video$cleanInput/"
        }

        val xvHeaders = mapOf(
            "User-Agent" to DEFAULT_USER_AGENT,
            "Cookie" to "age_verified=1; platform=pc; has_consent=1",
            "Referer" to "https://www.xvideos.com/"
        )

        try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Cookie", "age_verified=1; platform=pc; has_consent=1")
                .header("Referer", "https://www.xvideos.com/")
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!html.isNullOrBlank()) {
                // 1. Title extraction: prioritize html5player.setVideoTitle
                var title = ""
                val jsTitleMatch = Pattern.compile("""html5player\.setVideoTitle\s*\(\s*['"]([^'"]+)['"]\s*\)""", Pattern.CASE_INSENSITIVE).matcher(html)
                if (jsTitleMatch.find()) {
                    title = jsTitleMatch.group(1)?.trim() ?: ""
                }
                if (title.isBlank()) {
                    val ogTitleMatch = Pattern.compile("""<meta\s+property="og:title"\s+content="([^"]+)"""", Pattern.CASE_INSENSITIVE).matcher(html)
                    if (ogTitleMatch.find()) {
                        title = ogTitleMatch.group(1)?.trim() ?: ""
                    }
                }
                if (title.isBlank()) {
                    val docTitleMatch = Pattern.compile("""<title>([^<]+)</title>""", Pattern.CASE_INSENSITIVE).matcher(html)
                    if (docTitleMatch.find()) {
                        title = docTitleMatch.group(1)?.trim() ?: ""
                    }
                }
                title = Parser.unescapeEntities(title, false)
                    .replace(Regex("""\s*-\s*XVIDEOS\.COM\s*$""", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("""\s*-\s*Xvideos\.com\s*$""", RegexOption.IGNORE_CASE), "")
                    .trim()
                if (title.isBlank()) title = "XVideos"

                // 2. Thumbnail extraction: prioritize high-res 16:9 thumb from player
                var thumb = ""
                val jsThumb169Match = Pattern.compile("""html5player\.setThumbUrl169\s*\(\s*['"]([^'"]+)['"]\s*\)""", Pattern.CASE_INSENSITIVE).matcher(html)
                if (jsThumb169Match.find()) {
                    thumb = jsThumb169Match.group(1)?.trim() ?: ""
                }
                if (thumb.isBlank()) {
                    val jsThumbMatch = Pattern.compile("""html5player\.setThumbUrl\s*\(\s*['"]([^'"]+)['"]\s*\)""", Pattern.CASE_INSENSITIVE).matcher(html)
                    if (jsThumbMatch.find()) {
                        thumb = jsThumbMatch.group(1)?.trim() ?: ""
                    }
                }
                if (thumb.isBlank()) {
                    val ogImageMatch = Pattern.compile("""<meta\s+property="og:image"\s+content="([^"]+)"""", Pattern.CASE_INSENSITIVE).matcher(html)
                    if (ogImageMatch.find()) {
                        thumb = ogImageMatch.group(1)?.trim() ?: ""
                    }
                }
                if (thumb.isBlank()) {
                    val twImageMatch = Pattern.compile("""<meta\s+name="twitter:image"\s+content="([^"]+)"""", Pattern.CASE_INSENSITIVE).matcher(html)
                    if (twImageMatch.find()) {
                        thumb = twImageMatch.group(1)?.trim() ?: ""
                    }
                }
                if (thumb.startsWith("//")) thumb = "https:$thumb"
                else if (thumb.startsWith("/")) thumb = "https://www.xvideos.com$thumb"

                // 3. Uploader extraction
                val uploaderMatch = Pattern.compile("""<span[^>]*class="[^"]*name[^"]*"[^>]*><a[^>]*>([^<]+)</a></span>""", Pattern.CASE_INSENSITIVE).matcher(html)
                val uploaderName = if (uploaderMatch.find()) uploaderMatch.group(1)?.trim() ?: "XVideos" else "XVideos"

                val options = mutableListOf<PlayableStreamOption>()

                // HLS
                val hlsPattern = Pattern.compile("""html5player\.setVideoHLS\s*\(\s*['"]([^'"]+)['"]\s*\)""", Pattern.CASE_INSENSITIVE)
                val hlsMatcher = hlsPattern.matcher(html)
                if (hlsMatcher.find()) {
                    val hlsUrl = hlsMatcher.group(1) ?: ""
                    if (hlsUrl.isNotBlank()) {
                        options.add(
                            PlayableStreamOption(
                                qualityLabel = "Adaptive HLS (m3u8)",
                                format = "m3u8",
                                isMuxed = true,
                                videoUrl = hlsUrl,
                                providerType = ProviderType.DIRECT,
                                headers = xvHeaders
                            )
                        )
                    }
                }

                // High MP4
                val highPattern = Pattern.compile("""html5player\.setVideoUrlHigh\s*\(\s*['"]([^'"]+)['"]\s*\)""", Pattern.CASE_INSENSITIVE)
                val highMatcher = highPattern.matcher(html)
                if (highMatcher.find()) {
                    val highUrl = highMatcher.group(1) ?: ""
                    if (highUrl.isNotBlank()) {
                        options.add(
                            PlayableStreamOption(
                                qualityLabel = "High Quality MP4",
                                format = "mp4",
                                isMuxed = true,
                                videoUrl = highUrl,
                                providerType = ProviderType.DIRECT,
                                headers = xvHeaders
                            )
                        )
                    }
                }

                // Low MP4
                val lowPattern = Pattern.compile("""html5player\.setVideoUrlLow\s*\(\s*['"]([^'"]+)['"]\s*\)""", Pattern.CASE_INSENSITIVE)
                val lowMatcher = lowPattern.matcher(html)
                if (lowMatcher.find()) {
                    val lowUrl = lowMatcher.group(1) ?: ""
                    if (lowUrl.isNotBlank()) {
                        options.add(
                            PlayableStreamOption(
                                qualityLabel = "Low Quality MP4",
                                format = "mp4",
                                isMuxed = true,
                                videoUrl = lowUrl,
                                providerType = ProviderType.DIRECT,
                                headers = xvHeaders
                            )
                        )
                    }
                }

                // Generic video_url fallback
                if (options.isEmpty()) {
                    val genericPattern = Pattern.compile("""html5player\.setVideoUrl\s*\(\s*['"]([^'"]+)['"]\s*\)""", Pattern.CASE_INSENSITIVE)
                    val gm = genericPattern.matcher(html)
                    if (gm.find()) {
                        val gUrl = gm.group(1) ?: ""
                        if (gUrl.isNotBlank()) {
                            options.add(
                                PlayableStreamOption(
                                    qualityLabel = "Standard Quality MP4",
                                    format = "mp4",
                                    isMuxed = true,
                                    videoUrl = gUrl,
                                    providerType = ProviderType.DIRECT,
                                    headers = xvHeaders
                                )
                            )
                        }
                    }
                }

                if (options.isNotEmpty()) {
                    val bestOption = options.first()
                    return@withContext StreamData(
                        videoId = targetUrl,
                        videoUrl = bestOption.videoUrl ?: "",
                        title = title,
                        channelName = uploaderName,
                        description = title,
                        thumbnailUrl = thumb,
                        availableStreamOptions = options,
                        selectedStreamOption = bestOption,
                        providerId = PROVIDER_ID,
                        providerType = ProviderType.DIRECT,
                        headers = xvHeaders
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "XVideos direct HTML extraction failed: ${e.message}")
        }

        // Fallback to YtDlpResolver
        if (context != null) {
            try {
                Log.i(TAG, "Falling back to YtDlpResolver for XVideos URL: $targetUrl")
                val ytDlpRes = YtDlpResolver.extractStreamInfo(context, targetUrl)
                if (ytDlpRes is YouTubeExtractorHelper.ExtractionResult.Success) {
                    return@withContext ytDlpRes.streamData.copy(
                        providerId = PROVIDER_ID,
                        headers = xvHeaders
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "YtDlpResolver XVideos fallback failed: ${e.message}")
            }
        }

        null
    }
}
