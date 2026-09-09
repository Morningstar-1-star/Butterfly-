package com.example.extractor.sextb

import com.example.model.CaptionOption
import com.example.model.VideoItem
import com.example.model.parseDurationToSeconds
import com.example.util.JsUnpacker
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.regex.Pattern

/**
 * Scoped HTML and OpenGraph parser for SEXТB.
 * Follows JAVM's approach of scoped DOM extraction rather than fragile whole-page regexes.
 */
object SextbParser {

    private val DURATION_REGEX = Pattern.compile("""(?:(\d+):)?(\d+):(\d+)""")
    private val FILE_URL_REGEX = Pattern.compile("""["']?(?:file|src)["']?\s*:\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""", Pattern.CASE_INSENSITIVE)
    private val SOURCES_JSON_REGEX = Pattern.compile("""sources\s*:\s*(\[[^\]]+\])""", Pattern.CASE_INSENSITIVE)
    private val TRACKS_JSON_REGEX = Pattern.compile("""tracks\s*:\s*(\[[^\]]+\])""", Pattern.CASE_INSENSITIVE)

    /**
     * Parses a search or catalog listing page into a list of VideoItem.
     */
    fun parseSearchResults(html: String, baseUrl: String): List<VideoItem> {
        val doc = Jsoup.parse(html, baseUrl)
        val items = mutableListOf<VideoItem>()

        // Scoped card selectors in priority order
        val cardElements = doc.select(
            ".video-item, .item-video, .movie-item, article.video, .thumb-block, .card-video, " +
            "div[class*=\"video-card\"], div[class*=\"thumb\"], .item, article"
        )

        for (el in cardElements) {
            val linkEl = el.selectFirst("a[href*=\"/video/\"], a[href*=\"/watch/\"], a[href*=\"/movie/\"], a[href*=\".html\"], a[href]") ?: continue
            val rawHref = linkEl.attr("abs:href").ifBlank { linkEl.attr("href") }
            if (rawHref.isBlank() || rawHref.startsWith("#") || rawHref.startsWith("javascript:")) continue

            val id = extractVideoIdFromUrl(rawHref)
            if (id.isBlank()) continue

            val title = el.selectFirst(".title, h2, h3, h4, .video-title, a[title]")?.let {
                it.attr("title").ifBlank { it.text() }
            } ?: linkEl.attr("title").ifBlank { linkEl.text() }

            if (title.isBlank()) continue

            val imgEl = el.selectFirst("img[data-src], img[data-original], img[data-lazy-src], img[src]")
            val thumb = imgEl?.let {
                it.attr("abs:data-src").ifBlank {
                    it.attr("abs:data-original").ifBlank {
                        it.attr("abs:data-lazy-src").ifBlank { it.attr("abs:src") }
                    }
                }
            }?.takeIf { it.isNotBlank() && !it.endsWith(".svg") }

            val durationText = el.selectFirst(".duration, .time, span[class*=\"duration\"], span[class*=\"time\"]")?.text()?.trim() ?: ""
            val durationSec = parseDurationToSeconds(durationText)

            val uploader = el.selectFirst(".uploader, .studio, .channel, .author, span:contains(Studio) ~ a")?.text()?.trim() ?: "SEXТB"

            val qualityBadge = el.selectFirst(".quality, .hd, .badge, span[class*=\"badge\"]")?.text()?.trim()
            val tags = mutableListOf<String>()
            if (!qualityBadge.isNullOrBlank()) tags.add(qualityBadge)

            items.add(
                VideoItem(
                    id = id,
                    title = title.trim(),
                    uploaderName = uploader,
                    uploaderUrl = rawHref,
                    thumbnailUrl = thumb,
                    providerId = "sextb",
                    durationSeconds = durationSec,
                    tags = tags
                )
            )
        }

        return items.distinctBy { it.id }
    }

    /**
     * Parses the detailed video page using scoped DOM and OpenGraph metadata.
     */
    fun parseDetailsPage(html: String, pageUrl: String): SextbVideoDetails {
        val doc = Jsoup.parse(html, pageUrl)
        val id = extractVideoIdFromUrl(pageUrl)

        // 1. Title & Original Title
        val ogTitle = doc.selectFirst("meta[property=\"og:title\"]")?.attr("content")?.trim()
        val h1Title = doc.selectFirst("h1.title, h1.entry-title, .video-info h1, h1")?.text()?.trim()
        val title = (ogTitle ?: h1Title ?: doc.title()).removeSuffix(" - SEXТB").removeSuffix(" - SEXTB").trim()

        val originalTitle = doc.selectFirst(".original-title, .jp-title, span:contains(Original Title) ~ span, span:contains(Japanese Title) ~ span")?.text()?.trim()

        // 2. Thumbnail
        val ogImage = doc.selectFirst("meta[property=\"og:image\"], meta[name=\"twitter:image\"]")?.attr("abs:content")
        val posterImage = doc.selectFirst(".poster img, .video-player img, #player img")?.let {
            it.attr("abs:data-src").ifBlank { it.attr("abs:src") }
        }
        val thumbnail = ogImage?.takeIf { it.isNotBlank() } ?: posterImage

        // 3. Description
        val ogDesc = doc.selectFirst("meta[property=\"og:description\"], meta[name=\"description\"]")?.attr("content")?.trim()
        val bodyDesc = doc.selectFirst(".description, .video-desc, .entry-content p, .synopsis")?.text()?.trim()
        val description = ogDesc ?: bodyDesc

        // 4. Release Date
        val ogDate = doc.selectFirst("meta[property=\"og:video:release_date\"]")?.attr("content")?.trim()
        val htmlDate = doc.selectFirst("time[datetime], .release-date, .date, span:contains(Date) ~ span, span:contains(Release) ~ span")?.text()?.trim()
        val releaseDate = ogDate ?: htmlDate

        // 5. Duration
        val ogDuration = doc.selectFirst("meta[property=\"og:video:duration\"]")?.attr("content")?.trim()?.toLongOrNull() ?: -1L
        val htmlDuration = doc.selectFirst(".duration, span:contains(Duration) ~ span")?.text()?.trim() ?: ""
        val durationSeconds = if (ogDuration > 0) ogDuration else parseDurationToSeconds(htmlDuration)

        // 6. Actors
        val actors = mutableListOf<String>()
        doc.select("meta[property=\"og:video:actor\"]").forEach {
            val content = it.attr("content").trim()
            if (content.isNotBlank()) actors.add(content)
        }
        if (actors.isEmpty()) {
            doc.select(".actors a, .models a, .cast a, a[href*=\"/actor/\"], a[href*=\"/model/\"], a[href*=\"/actress/\"], span:contains(Actress) ~ a, span:contains(Actor) ~ a")
                .forEach {
                    val name = it.text().trim()
                    if (name.isNotBlank() && !actors.contains(name)) actors.add(name)
                }
        }

        // 7. Studio
        val studio = doc.selectFirst("a[href*=\"/studio/\"], a[href*=\"/maker/\"], span:contains(Studio) ~ a, span:contains(Maker) ~ a, .studio")?.text()?.trim()

        // 8. Director
        val ogDirector = doc.selectFirst("meta[property=\"og:video:director\"]")?.attr("content")?.trim()
        val htmlDirector = doc.selectFirst("a[href*=\"/director/\"], span:contains(Director) ~ a, .director")?.text()?.trim()
        val director = ogDirector ?: htmlDirector

        // 9. Categories & Tags
        val tags = mutableListOf<String>()
        doc.select("meta[property=\"og:video:tag\"]").forEach {
            val content = it.attr("content").trim()
            if (content.isNotBlank()) tags.add(content)
        }
        doc.select(".tags a, .categories a, .genres a, a[href*=\"/tag/\"], a[href*=\"/category/\"], a[href*=\"/genre/\"]")
            .forEach {
                val tag = it.text().replace("#", "").trim()
                if (tag.isNotBlank() && !tags.contains(tag)) tags.add(tag)
            }

        // 10. Episodes
        val episodes = parseEpisodes(doc, title, pageUrl)

        // 11. Scoped Video Sources from the page itself
        val sources = parseDirectVideoSources(doc, pageUrl)

        return SextbVideoDetails(
            id = id,
            pageUrl = pageUrl,
            title = title,
            originalTitle = originalTitle,
            thumbnailUrl = thumbnail,
            description = description,
            releaseDate = releaseDate,
            durationSeconds = durationSeconds,
            actors = actors,
            studio = studio,
            director = director,
            categories = tags,
            tags = tags,
            episodes = episodes,
            availableSources = sources
        )
    }

    /**
     * Extracts episodes for multi-part videos or series.
     */
    fun parseEpisodes(doc: Document, seriesTitle: String, pageUrl: String): List<SextbEpisode> {
        val episodes = mutableListOf<SextbEpisode>()
        val episodeEls = doc.select(
            ".episodes-list a, .episode-item a, .server-item a, div[class*=\"episode\"] a, " +
            "ul.episodes li a, .parts-list a"
        )

        var epNumber = 1
        for (el in episodeEls) {
            val href = el.attr("abs:href").ifBlank { el.attr("href") }
            if (href.isBlank() || href == pageUrl || href.startsWith("#") || href.startsWith("javascript:")) continue

            val epTitle = el.text().trim().ifBlank { "Episode $epNumber" }
            val epId = extractVideoIdFromUrl(href)
            if (epId.isNotBlank()) {
                episodes.add(
                    SextbEpisode(
                        id = epId,
                        episodeNumber = epNumber++,
                        title = epTitle,
                        pageUrl = href
                    )
                )
            }
        }
        return episodes.distinctBy { it.id }
    }

    /**
     * Extracts player iframe URLs and embed references.
     */
    fun extractPlayerEmbedUrls(html: String, baseUrl: String): List<String> {
        val doc = Jsoup.parse(html, baseUrl)
        val embedUrls = mutableListOf<String>()

        // 1. Scoped iframes
        val iframes = doc.select("iframe[src], iframe[data-src], iframe[data-lazy-src]")
        for (iframe in iframes) {
            val src = iframe.attr("abs:src").ifBlank {
                iframe.attr("abs:data-src").ifBlank { iframe.attr("abs:data-lazy-src") }
            }
            if (isValidEmbedUrl(src)) {
                embedUrls.add(src)
            }
        }

        // 2. Data attributes on player containers
        val playerContainers = doc.select("#player, .player, .video-player, div[data-id], div[data-url], div[data-embed]")
        for (c in playerContainers) {
            val dataUrl = c.attr("abs:data-url").ifBlank { c.attr("data-url") }
            if (isValidEmbedUrl(dataUrl)) embedUrls.add(dataUrl)

            val dataEmbed = c.attr("abs:data-embed").ifBlank { c.attr("data-embed") }
            if (isValidEmbedUrl(dataEmbed)) embedUrls.add(dataEmbed)
        }

        // 3. Fallback regex search for streamtb.me or player embeds in scripts
        val scriptEmbedRegex = Pattern.compile("""(?:iframe|embed|source|file|url)\s*[:=]\s*["'](https?://[^"']*(?:streamtb\.me|streamtb\.com|sextb\.net/embed/|sextb\.date/embed/)[^"']*)["']""", Pattern.CASE_INSENSITIVE)
        val matcher = scriptEmbedRegex.matcher(html)
        while (matcher.find()) {
            val url = matcher.group(1)
            if (url != null && isValidEmbedUrl(url) && !embedUrls.contains(url)) {
                embedUrls.add(url)
            }
        }

        return embedUrls.distinct()
    }

    /**
     * Extracts direct video sources (<source>, <video>, or embedded player scripts) from page.
     */
    fun parseDirectVideoSources(doc: Document, baseUrl: String): List<VideoSource> {
        val sources = mutableListOf<VideoSource>()

        // 1. Direct HTML5 <video> and <source> tags
        val videoTags = doc.select("video source[src], video[src]")
        for (v in videoTags) {
            val src = v.attr("abs:src").ifBlank { v.attr("src") }
            if (src.isNotBlank() && (src.contains(".m3u8") || src.contains(".mp4"))) {
                val label = v.attr("label").ifBlank { v.attr("title").ifBlank { "1080p" } }
                val type = v.attr("type").ifBlank { if (src.contains(".m3u8")) "application/x-mpegURL" else "video/mp4" }
                sources.add(
                    VideoSource(
                        url = src,
                        mimeType = type,
                        quality = normalizeQualityLabel(label),
                        headers = mapOf("Referer" to baseUrl)
                    )
                )
            }
        }

        // 2. Embedded player scripts: jwplayer or player config
        val scripts = doc.select("script:not([src])")
        for (script in scripts) {
            val code = script.data()
            val unpacked = if (JsUnpacker.isPacked(code)) JsUnpacker.unpack(code) else code
            val scriptSources = extractSourcesFromScript(unpacked, baseUrl)
            sources.addAll(scriptSources)
        }

        return sources.distinctBy { it.url }
    }

    /**
     * Extracts stream URLs and subtitles from JavaScript player configurations.
     */
    fun extractSourcesFromScript(script: String, referer: String): List<VideoSource> {
        val sources = mutableListOf<VideoSource>()

        // Check for direct file: "https://...m3u8"
        val fileMatcher = FILE_URL_REGEX.matcher(script)
        while (fileMatcher.find()) {
            val fileUrl = fileMatcher.group(1)
            if (!fileUrl.isNullOrBlank() && fileUrl.startsWith("http")) {
                val isHls = fileUrl.contains(".m3u8")
                sources.add(
                    VideoSource(
                        url = fileUrl,
                        mimeType = if (isHls) "application/x-mpegURL" else "video/mp4",
                        quality = "1080p",
                        headers = mapOf("Referer" to referer)
                    )
                )
            }
        }

        // Parse subtitles/captions if present
        val tracksMatcher = TRACKS_JSON_REGEX.matcher(script)
        val captions = mutableListOf<CaptionOption>()
        if (tracksMatcher.find()) {
            val tracksJson = tracksMatcher.group(1)
            if (tracksJson != null) {
                try {
                    val jsonArray = org.json.JSONArray(tracksJson)
                    for (i in 0 until jsonArray.length()) {
                        val track = jsonArray.optJSONObject(i) ?: continue
                        val file = track.optString("file").ifBlank { track.optString("src") }
                        val label = track.optString("label", "English")
                        val kind = track.optString("kind", "captions")
                        if (file.isNotBlank() && (kind == "captions" || kind == "subtitles")) {
                            captions.add(
                                CaptionOption(
                                    languageName = label,
                                    languageCode = if (label.contains("eng", ignoreCase = true)) "en" else "und",
                                    format = if (file.endsWith(".vtt")) "vtt" else "srt",
                                    url = file
                                )
                            )
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        return sources.map { it.copy(subtitles = captions) }
    }

    fun extractVideoIdFromUrl(url: String): String {
        val clean = url.trim().removeSuffix("/")
        val idRegex = Pattern.compile("""(?:video|watch|movie|post)/([a-zA-Z0-9_-]+)""")
        val m = idRegex.matcher(clean)
        if (m.find()) return m.group(1) ?: ""

        val htmlRegex = Pattern.compile("""/([a-zA-Z0-9_-]+)\.html""")
        val mHtml = htmlRegex.matcher(clean)
        if (mHtml.find()) return mHtml.group(1) ?: ""

        val lastPart = clean.substringAfterLast("/")
        return if (lastPart.length >= 3 && !lastPart.contains("?") && !lastPart.contains("page")) lastPart else clean.hashCode().toString()
    }

    fun normalizeQualityLabel(label: String): String {
        val lower = label.lowercase().trim()
        return when {
            lower.contains("2160") || lower.contains("4k") -> "4K"
            lower.contains("1080") -> "1080p"
            lower.contains("720") -> "720p"
            lower.contains("480") -> "480p"
            lower.contains("360") -> "360p"
            else -> "1080p"
        }
    }

    private fun isValidEmbedUrl(url: String?): Boolean {
        if (url.isNullOrBlank() || !url.startsWith("http")) return false
        val lower = url.lowercase()
        return lower.contains("streamtb") || lower.contains("embed") || lower.contains("player") ||
                lower.contains("stream") || lower.contains("video")
    }
}
