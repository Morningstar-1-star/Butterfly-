package com.example.extractor.supjav

import android.util.Base64
import android.util.Log
import com.example.model.VideoItem
import com.example.model.parseDurationToSeconds
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.util.regex.Pattern

/**
 * Robust HTML parser for SupJav catalog listings and detail watch pages.
 */
object SupJavParser {

    private const val TAG = "SupJavParser"

    private val JAV_CODE_PATTERN = Pattern.compile(
        """\b([a-zA-Z]{2,10}[-_][0-9]{2,8}|[a-zA-Z]{2,10}\s+[0-9]{2,8}|fc2(?:[-_]ppv)?[-_][0-9]{4,8}|heyzo[-_][0-9]{4}|carib[-_][0-9]{6}[-_][0-9]{3}|1pon[-_][0-9]{6}[-_][0-9]{3})\b""",
        Pattern.CASE_INSENSITIVE
    )

    private val EMBED_URL_REGEX = Pattern.compile(
        """https?://[^\s"'<>]*(?:tvlogy\.(?:to|com)|streamwish\.(?:to|com|site)|wishembed\.(?:pro|site)|awish\.pro|dwish\.net|embedwish\.com|dood\.(?:to|so|ws|la|watch|pm)|doodstream\.com|ds2play\.com|voe\.sx|voe-unblock\.com|audaciousdefaulthouse\.com|streamtape\.(?:com|to|net|pe)|fasting\.su|fembed\.com|filelions\.(?:to|com)|mixdrop\.(?:co|to)|javcl\.com|streamtb\.(?:me|com)|stbturbo)[^\s"'<>]*""",
        Pattern.CASE_INSENSITIVE
    )

    private val DIRECT_MEDIA_REGEX = Pattern.compile(
        """https?://[^\s"'<>]+\.(?:m3u8|mp4)(?:[^\s"'<>]*)?""",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Parses video items from a SupJav catalog or search results HTML document.
     */
    fun parseVideoCards(html: String, baseUrl: String): List<VideoItem> {
        val items = mutableListOf<VideoItem>()
        if (html.isBlank()) return items

        try {
            val doc: Document = Jsoup.parse(html, baseUrl)

            // Select post containers across various SupJav themes/mirrors
            val cardElements = doc.select(".post, .posts > div, article.post, .post-item, .item-video, .thumb-block, div.posts article, div[id^=post-], .video-item, div[id^=video-]")

            for (card in cardElements) {
                try {
                    val linkEl = card.selectFirst("a[href*='supjav'], a[href$='.html'], a.post-title, h2 a, h3 a, a[itemprop='url'], a:has(img), a") ?: continue
                    val rawHref = linkEl.attr("href").trim()
                    if (rawHref.isBlank() || rawHref == "#" || rawHref.startsWith("javascript:")) continue

                    val pageUrl = resolveAbsoluteUrl(rawHref, baseUrl)
                    val slug = extractSlugFromUrl(pageUrl)
                    if (slug.isBlank()) continue

                    // Accurate Title extraction
                    val titleEl = card.selectFirst(
                        ".video-name a, h2.post-title a, h3.post-title a, h2 a, h3 a, .entry-title a, .post-title a, .title a, a.post-title, .video-name, h2.post-title, h3.post-title, h2, h3, .entry-title, .post-title, .title"
                    )
                    var rawTitle = titleEl?.text()?.trim() ?: ""
                    if (rawTitle.isBlank()) {
                        rawTitle = linkEl.attr("title").trim()
                    }
                    if (rawTitle.isBlank()) {
                        rawTitle = card.selectFirst("a[title]:not([rel='tag']):not([href*='/tag/']):not([href*='/category/'])")?.attr("title")?.trim() ?: ""
                    }
                    if (rawTitle.isBlank()) {
                        rawTitle = card.selectFirst("img.video-image, img.wp-post-image, .thumb img, a img, img")?.attr("alt")?.trim() ?: ""
                    }
                    if (rawTitle.isBlank()) {
                        rawTitle = slug.replace("-", " ").replace("_", " ").uppercase()
                    }

                    val title = cleanTitle(rawTitle, slug)

                    // Extract standard JAV Code from title or slug (e.g. SSIS-123)
                    val code = extractJavCode(title).ifBlank { extractJavCode(slug) }

                    // Accurate Thumbnail extraction
                    val imgEl = card.selectFirst("img.video-image, img.wp-post-image, .thumb img, .post-thumbnail img, .poster img, a[itemprop='url'] img, a:has(img) img, img")
                    val thumb = extractBestImageUrl(imgEl, card, baseUrl)

                    // Duration extraction
                    val durEl = card.selectFirst(".duration, .meta-duration, span.dur, .meta .time, .badge-duration")
                    val durText = durEl?.text()?.trim() ?: ""
                    val durationSec = parseDurationToSeconds(durText)

                    // View count / meta
                    val viewsEl = card.selectFirst(".views, .meta-views, span.view")
                    val viewsText = viewsEl?.text()?.trim() ?: ""
                    val viewCount = parseViewCount(viewsText)

                    val brand = com.example.util.ChannelLogoHelper.getBrandInfo("SupJav", null, title)
                    val encName = try { java.net.URLEncoder.encode(code.ifBlank { "SupJav" }.take(30), "UTF-8") } catch (_: Exception) { "SupJav" }
                    val uploaderAvatar = brand.logoUrls.firstOrNull()
                        ?: "https://ui-avatars.com/api/?name=$encName&background=3F51B5&color=fff&size=256&bold=true"
                    val uploaderUrl = "supjav_${code.lowercase().replace(Regex("[^a-z0-9]"), "")}"

                    val previewList = mutableListOf<String>()
                    if (!thumb.isNullOrBlank()) {
                        previewList.add(thumb)
                        val sjFrameMatch = Regex("""/(\d+)\.(jpg|webp|jpeg)""").find(thumb)
                        if (sjFrameMatch != null) {
                            val base = thumb.substring(0, sjFrameMatch.range.first)
                            val ext = sjFrameMatch.groupValues[2]
                            previewList.addAll((1..16).map { idx -> "$base/$idx.$ext" })
                        }
                    }

                    val desc = buildString {
                        if (code.isNotBlank()) append("JAV Code: $code • ")
                        append("SupJav Official Asian & Japanese Release\nQuality: 1080p HD Uncensored Stream")
                    }

                    val item = VideoItem(
                        id = "supjav_$slug",
                        title = title,
                        uploaderName = if (code.isNotBlank()) "SupJav • $code" else "SupJav Studio",
                        uploaderUrl = uploaderUrl,
                        uploaderAvatarUrl = uploaderAvatar,
                        thumbnailUrl = thumb,
                        durationSeconds = durationSec,
                        viewCount = viewCount,
                        providerId = "supjav",
                        previewThumbnails = previewList.distinct(),
                        description = desc
                    )
                    items.add(item)
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing individual video card: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing SupJav HTML document: ${e.message}")
        }

        return items.distinctBy { it.id }
    }

    /**
     * Parses the details and embedded video server players from a SupJav watch page.
     */
    fun parseVideoDetails(html: String, pageUrl: String): SupJavVideoDetails {
        val doc = Jsoup.parse(html, pageUrl)
        val slug = extractSlugFromUrl(pageUrl)

        // 1. Accurate Title Extraction
        val ogTitle = doc.selectFirst("meta[property='og:title']")?.attr("content")?.trim()
        val headingTitle = doc.selectFirst("h1.post-title, h1.entry-title, h1, .entry-title, .post-title")?.text()?.trim()
        val twitterTitle = doc.selectFirst("meta[name='twitter:title']")?.attr("content")?.trim()
        val docTitle = doc.title().trim()

        var rawTitle = when {
            !ogTitle.isNullOrBlank() -> ogTitle
            !headingTitle.isNullOrBlank() -> headingTitle
            !twitterTitle.isNullOrBlank() -> twitterTitle
            docTitle.isNotBlank() -> docTitle
            else -> "SupJav Video ($slug)"
        }
        val title = cleanTitle(rawTitle, slug)

        // 2. JAV Code
        val code = extractJavCode(title).ifBlank { extractJavCode(slug) }

        // 3. Poster / Cover Thumbnail Extraction (prefer high-res og:image or poster)
        val ogImage = doc.selectFirst("meta[property='og:image']")?.attr("content")?.trim()
        val metaThumb = doc.selectFirst("meta[itemprop='thumbnailUrl']")?.attr("content")?.trim()
        val twitterImage = doc.selectFirst("meta[name='twitter:image']")?.attr("content")?.trim()
        val linkImage = doc.selectFirst("link[rel='image_src']")?.attr("href")?.trim()
        val detailImgEl = doc.selectFirst("img.wp-post-image, .post-content img, .entry-content img, .player-wrapper img, .poster img, img")

        val thumbCandidates = listOfNotNull(ogImage, metaThumb, twitterImage, linkImage)
        var thumb: String? = thumbCandidates.firstOrNull { isValidImageUrl(it) }
        if (thumb == null) {
            thumb = extractBestImageUrl(detailImgEl, null, pageUrl)
        }
        if (!thumb.isNullOrBlank()) {
            thumb = resolveAbsoluteUrl(thumb, pageUrl)
        }

        // 4. Actresses / Cast
        val actresses = mutableListOf<String>()
        val castEls = doc.select("a[href*='/actress/'], a[href*='/cast/'], a[href*='/model/'], a[rel='tag'][href*='cast'], .actress a, .cast a")
        for (cast in castEls) {
            val name = cast.text().trim()
            if (name.isNotBlank()) actresses.add(name)
        }

        // 5. Tags
        val tags = mutableListOf<String>()
        val tagEls = doc.select("a[href*='/tag/'], a[rel='tag'], .tags a, .categories a")
        for (tag in tagEls) {
            val name = tag.text().trim()
            if (name.isNotBlank() && !actresses.contains(name)) tags.add(name)
        }

        // 6. Release Date
        val dateEl = doc.selectFirst("time, .date, .post-date, .meta-date, [itemprop='datePublished']")
        val releaseDate = dateEl?.text()?.trim()

        // 7. Server Embeds
        val embedServers = extractServerEmbeds(doc, html, pageUrl)

        return SupJavVideoDetails(
            id = slug,
            title = title,
            code = code,
            uploaderUrl = pageUrl,
            thumbnailUrl = thumb,
            durationSeconds = 0L,
            viewCount = 0L,
            tags = tags.distinct(),
            actresses = actresses.distinct(),
            releaseDate = releaseDate,
            embedSources = embedServers
        )
    }

    /**
     * Finds and extracts all video server embed links and direct players on the page.
     */
    fun extractServerEmbeds(doc: Document, html: String, pageUrl: String): List<SupJavServerEmbed> {
        val servers = mutableListOf<SupJavServerEmbed>()
        val seenUrls = mutableSetOf<String>()

        fun addServer(name: String, url: String, type: String = "embed") {
            val trimmedUrl = url.trim().replace("\\/", "/")
            if (trimmedUrl.isBlank() || seenUrls.contains(trimmedUrl)) return
            if (trimmedUrl.startsWith("javascript:") || trimmedUrl == "#") return
            val absoluteUrl = resolveAbsoluteUrl(trimmedUrl, pageUrl)
            seenUrls.add(absoluteUrl)
            servers.add(SupJavServerEmbed(name, absoluteUrl, type))
        }

        // 1. Server buttons / links (.btn-server, a.btn-server, div.server, etc.)
        val serverButtons = doc.select(
            ".btn-server, a.btn-server, div.server, a.server, [data-link], [data-src], [data-href], .player-btn, a[href*='tvlogy'], a[href*='streamwish'], a[href*='dood'], a[href*='voe'], a[href*='streamtape'], a[href*='fembed'], a[href*='fasting'], a[href*='filelions']"
        )

        for (btn in serverButtons) {
            val serverName = btn.text().trim().ifBlank {
                btn.attr("title").ifBlank { btn.attr("data-server").ifBlank { "Server" } }
            }

            var link = btn.attr("data-link").ifBlank { null }
                ?: btn.attr("data-src").ifBlank { null }
                ?: btn.attr("data-href").ifBlank { null }
                ?: btn.attr("href").ifBlank { null }

            if (link != null && link.isNotBlank()) {
                // If base64 encoded
                if (!link.startsWith("http") && link.length > 20 && !link.contains(" ") && isBase64(link)) {
                    val decoded = decodeBase64Safe(link)
                    if (decoded.startsWith("http")) {
                        link = decoded
                    }
                }
                addServer(serverName, link, "embed")
            }
        }

        // 2. Iframes embedded in page
        val iframes = doc.select("iframe[src], iframe[data-src], iframe[data-lazy-src]")
        for (iframe in iframes) {
            val src = iframe.attr("src").ifBlank { iframe.attr("data-src").ifBlank { iframe.attr("data-lazy-src") } }.trim()
            if (src.isNotBlank()) {
                val serverName = identifyServerName(src)
                addServer(serverName, src, "iframe")
            }
        }

        // 3. Regex scan across full raw HTML / Script tags
        val matcher = EMBED_URL_REGEX.matcher(html)
        while (matcher.find()) {
            val url = matcher.group()
            val serverName = identifyServerName(url)
            addServer(serverName, url, "regex")
        }

        // 4. Check for direct HLS .m3u8 or MP4 URLs inside scripts or video tags
        val directMediaMatcher = DIRECT_MEDIA_REGEX.matcher(html)
        while (directMediaMatcher.find()) {
            val mediaUrl = directMediaMatcher.group()
            if (!mediaUrl.contains(".jpg") && !mediaUrl.contains(".png") && !mediaUrl.contains(".webp")) {
                addServer("SupJav Direct Media", mediaUrl, "direct")
            }
        }

        return servers
    }

    fun identifyServerName(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains("tvlogy") -> "TVLogy HLS"
            lower.contains("streamwish") || lower.contains("wishembed") || lower.contains("awish") || lower.contains("dwish") || lower.contains("embedwish") -> "StreamWish FHD"
            lower.contains("dood") || lower.contains("ds2play") -> "DoodStream"
            lower.contains("voe") || lower.contains("audaciousdefaulthouse") -> "VOE 1080p"
            lower.contains("streamtape") || lower.contains("streamta.pe") -> "StreamTape"
            lower.contains("fasting") || lower.contains("fembed") -> "Fembed / Fasting"
            lower.contains("filelions") -> "FileLions"
            lower.contains("mixdrop") -> "MixDrop"
            lower.contains("javcl") -> "JavCL"
            lower.contains("stb") || lower.contains("streamtb") -> "StreamTB"
            lower.contains(".m3u8") -> "Direct HLS"
            lower.contains(".mp4") -> "Direct MP4"
            else -> "SupJav Stream"
        }
    }

    fun extractJavCode(text: String): String {
        if (text.isBlank()) return ""
        val parsed = JavEnglishTitleHelper.extractJavCode(text)
        if (parsed.isNotBlank()) return parsed
        val matcher = JAV_CODE_PATTERN.matcher(text)
        if (matcher.find()) {
            return matcher.group(1).uppercase().replace(" ", "-")
        }
        return ""
    }

    fun extractSlugFromUrl(url: String): String {
        val clean = url.substringBefore("?").substringBefore("#").trim('/')
        val lastSegment = clean.substringAfterLast('/')
        return lastSegment.removeSuffix(".html").removeSuffix(".htm")
    }

    private fun resolveAbsoluteUrl(href: String, baseUrl: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        if (href.startsWith("//")) return "https:$href"
        return try {
            val baseUri = URI(baseUrl)
            baseUri.resolve(href).toString()
        } catch (e: Exception) {
            if (href.startsWith("/")) {
                val host = baseUrl.substringBefore("://") + "://" + baseUrl.substringAfter("://").substringBefore("/")
                "$host$href"
            } else {
                "${baseUrl.trimEnd('/')}/$href"
            }
        }
    }

    private fun parseViewCount(raw: String): Long {
        if (raw.isBlank()) return 0L
        val clean = raw.lowercase().replace(",", "").trim()
        return try {
            when {
                clean.endsWith("m") -> (clean.removeSuffix("m").toDouble() * 1_000_000).toLong()
                clean.endsWith("k") -> (clean.removeSuffix("k").toDouble() * 1_000).toLong()
                else -> clean.filter { it.isDigit() }.toLongOrNull() ?: 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun isBase64(str: String): Boolean {
        if (str.length % 4 != 0) return false
        val base64Pattern = Pattern.compile("^[A-Za-z0-9+/=]+$")
        return base64Pattern.matcher(str).matches()
    }

    fun cleanTitle(raw: String, slug: String = ""): String {
        return JavEnglishTitleHelper.toEnglishTitle(raw, slug)
    }

    private fun extractBestImageUrl(imgEl: Element?, card: Element?, baseUrl: String): String? {
        val candidates = mutableListOf<String>()

        if (imgEl != null) {
            val dataSrc = imgEl.attr("data-src").trim()
            if (dataSrc.isNotBlank()) candidates.add(dataSrc)

            val dataOrig = imgEl.attr("data-original").trim()
            if (dataOrig.isNotBlank()) candidates.add(dataOrig)

            val dataLazy = imgEl.attr("data-lazy-src").trim()
            if (dataLazy.isNotBlank()) candidates.add(dataLazy)

            val dataSrcset = imgEl.attr("data-srcset").trim()
            if (dataSrcset.isNotBlank()) {
                val bestFromSrcset = extractBestFromSrcset(dataSrcset)
                if (!bestFromSrcset.isNullOrBlank()) candidates.add(bestFromSrcset)
            }

            val srcset = imgEl.attr("srcset").trim()
            if (srcset.isNotBlank()) {
                val bestFromSrcset = extractBestFromSrcset(srcset)
                if (!bestFromSrcset.isNullOrBlank()) candidates.add(bestFromSrcset)
            }

            val src = imgEl.attr("src").trim()
            if (src.isNotBlank()) candidates.add(src)
        }

        if (card != null) {
            val metaThumb = card.selectFirst("meta[itemprop='thumbnailUrl']")?.attr("content")?.trim()
            if (!metaThumb.isNullOrBlank()) candidates.add(metaThumb)

            val metaOg = card.selectFirst("meta[property='og:image']")?.attr("content")?.trim()
            if (!metaOg.isNullOrBlank()) candidates.add(metaOg)
        }

        for (cand in candidates) {
            val clean = cand.substringBefore(" ").trim()
            if (isValidImageUrl(clean)) {
                return resolveAbsoluteUrl(clean, baseUrl)
            }
        }
        return null
    }

    private fun isValidImageUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        if (lower.startsWith("data:")) return false
        if (lower.contains("1x1") || lower.contains("blank.gif") || lower.contains("spacer.gif") || lower.contains("pixel.gif") || lower.contains("placeholder")) return false
        return true
    }

    private fun extractBestFromSrcset(srcset: String): String? {
        val parts = srcset.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        val best = parts.maxByOrNull { part ->
            val sizeStr = part.substringAfterLast(" ", "").trim().removeSuffix("w").removeSuffix("x")
            sizeStr.toIntOrNull() ?: 0
        } ?: parts.last()
        return best.substringBefore(" ").trim()
    }

    private fun decodeBase64Safe(encoded: String): String {
        return try {
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}
