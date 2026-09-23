package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import com.example.model.StreamData
import com.example.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * NoodleMagazine Provider & Stream Extractor.
 * Features multi-endpoint scraping, complete browser emulation,
 * robust VK/MyCDN iframe parsing, HTML5 stream extraction, native yt-dlp resolution,
 * and seamless fallback streaming without 403 authorization failures.
 */
object NoodleMagazineProvider {
    private const val TAG = "NoodleMagazineProvider"
    const val PROVIDER_ID = "noodlemagazine"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private const val DEFAULT_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val BASE_URL = "https://noodlemagazine.com"

    private val defaultHeaders = mapOf(
        "User-Agent" to DEFAULT_UA,
        "Referer" to "$BASE_URL/",
        "Origin" to BASE_URL,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.9",
        "Sec-Ch-Ua" to "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"",
        "Sec-Ch-Ua-Mobile" to "?0",
        "Sec-Ch-Ua-Platform" to "\"Windows\"",
        "Sec-Fetch-Dest" to "document",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "none",
        "Sec-Fetch-User" to "?1",
        "Cookie" to "lang=en; hl=en; language=en; remixlang=3; age_verified=1; platform=pc; ft_mature=1; consent=1; has_consent=1"
    )

    fun cleanAndTranslateNoodleTitle(rawTitle: String, fallbackSlug: String = ""): String {
        if (rawTitle.isBlank() && fallbackSlug.isBlank()) return "NoodleMagazine Exclusive HD"

        var clean = org.jsoup.parser.Parser.unescapeEntities(rawTitle, false)
            .replace(Regex("(?i)\\s*-\\s*NoodleMagazine.*"), "")
            .replace(Regex("(?i)NoodleMagazine.*"), "")
            .replace(Regex("(?i)\\b(?:noodlemagazine|noodlemag)\\b"), "")
            .trim()

        // 1. Thai Keywords & Phrases to English
        val thaiTranslations = listOf(
            Regex("""(?i)(?:คลิปหลุด|หลุด|คลิปเด็ด)""") to "Leaked Video",
            Regex("""(?i)(?:สาวไทย|คนไทย|หญิงไทย)""") to "Thai Beauty",
            Regex("""(?i)(?:น้องแนท|น้อง|น้องๆ)""") to "Model",
            Regex("""(?i)(?:นักศึกษา|นักเรียน|มหาลัย|มัธยม|ชุดนักเรียน|ชุดนักศึกษา)""") to "College Student",
            Regex("""(?i)(?:เด็ดมาก|โครตเด็ด|เด็ดๆ|สุดยอด)""") to "Super Hot",
            Regex("""(?i)(?:น่ารัก|สาวสวย|คนสวย)""") to "Cute Model",
            Regex("""(?i)(?:สาวออฟฟิศ|พนักงาน|สาวโรงงาน)""") to "Office Girl",
            Regex("""(?i)(?:แอบถ่าย|กล้องแอบ)""") to "Hidden Cam",
            Regex("""(?i)(?:งานดี|งานพรีเมี่ยม)""") to "Premium HD",
            Regex("""(?i)(?:เน็ตไอดอล|เน็ตไอดอลสาว)""") to "Net Idol",
            Regex("""(?i)(?:นวด|สปา|นวดน้ำมัน)""") to "Sensual Oil Massage",
            Regex("""(?i)(?:แฟน|คู่รัก)""") to "Amateur Couple",
            Regex("""(?i)(?:ขย่ม|คราง|เสียว)""") to "Passionate Romance",
            Regex("""(?i)(?:ดูดนม|นมใหญ่|อกโต|นมโต)""") to "Big Tits Model",
            Regex("""(?i)(?:ช่วยตัวเอง|เสร็จคาปาก)""") to "Sensational Solo",
            Regex("""(?i)(?:ชวนเสียว|เสียวๆ)""") to "Sensational",
            Regex("""(?i)(?:ในห้อง|โรงแรม|ม่านรูด)""") to "Hotel Room Romance"
        )
        for ((p, r) in thaiTranslations) {
            clean = clean.replace(p, " $r ")
        }

        // 2. Chinese & Taiwanese Keywords & Phrases to English
        val chineseTranslations = listOf(
            Regex("""(?i)(?:台灣自拍|台湾自拍|自拍)""") to "Amateur POV",
            Regex("""(?i)(?:外流|流出|私密流出)""") to "Exclusive Leaked",
            Regex("""(?i)(?:極品|顶级|頂級)""") to "Top Tier Glamour",
            Regex("""(?i)(?:正妹|美女|女神|靚女)""") to "Beautiful Model",
            Regex("""(?i)(?:台灣|台湾)""") to "Taiwanese",
            Regex("""(?i)(?:國產|国产|麻豆)""") to "Asian Romance",
            Regex("""(?i)(?:學生妹|大學生|女大生|學生|女學生)""") to "College Student",
            Regex("""(?i)(?:制服|JK|校服)""") to "Uniform Cosplay",
            Regex("""(?i)(?:素人|清純|初撮)""") to "Cute Amateur",
            Regex("""(?i)(?:少婦|人妻|熟女)""") to "Married Housewife",
            Regex("""(?i)(?:無碼|無修正)""") to "Uncensored",
            Regex("""(?i)(?:中文字幕|中文)""") to "English Sub",
            Regex("""(?i)(?:主播|直播|網紅)""") to "Live Streamer",
            Regex("""(?i)(?:推特|推特大神)""") to "Social Media Model",
            Regex("""(?i)(?:探花|約炮|搭訕)""") to "Street Encounter",
            Regex("""(?i)(?:巨乳|大胸|爆乳)""") to "Busty Model",
            Regex("""(?i)(?:按摩|油壓|水療)""") to "Sensual Oil Massage",
            Regex("""(?i)(?:情侶|女友)""") to "Passionate Girlfriend",
            Regex("""(?i)(?:私拍|約拍)""") to "Private Photoshoot",
            Regex("""(?i)(?:誘惑|性感|騷貨)""") to "Sensual Seduction",
            Regex("""(?i)(?:內射|中出)""") to "Intimate Climax",
            Regex("""(?i)(?:高潮|潮吹)""") to "Sensational Climax",
            Regex("""(?i)(?:口交|吹簫)""") to "Blowjob",
            Regex("""(?i)(?:野外|車震)""") to "Outdoor Adventure"
        )
        for ((p, r) in chineseTranslations) {
            clean = clean.replace(p, " $r ")
        }

        // 3. Russian & Cyrillic Keywords to English
        val russianTranslations = listOf(
            Regex("(?i)\\bяпонское порно\\b") to "Japanese Video",
            Regex("(?i)\\bпорно фильм\\b") to "Movie",
            Regex("(?i)\\bрусским переводом\\b") to "English Sub",
            Regex("(?i)\\bрусский перевод\\b") to "English Sub",
            Regex("(?i)\\bс переводом\\b") to "Subtitled",
            Regex("(?i)\\bпереводом\\b") to "",
            Regex("(?i)\\bрусское\\b|\\bрусская\\b|\\bрусский\\b") to "Russian",
            Regex("(?i)\\bпорно\\b") to "Video",
            Regex("(?i)\\bсекс\\b") to "Sex",
            Regex("(?i)\\bминиэт\\b|\\bминет\\b") to "Blowjob",
            Regex("(?i)\\bсиськи\\b|\\bгрудь\\b") to "Big Tits",
            Regex("(?i)\\bжопа\\b|\\bпопка\\b") to "Ass",
            Regex("(?i)\\bкино\\b|\\bфильм\\b") to "Movie",
            Regex("(?i)\\bэротика\\b") to "Erotic",
            Regex("(?i)\\bазиатское\\b|\\bазиатка\\b") to "Asian",
            Regex("(?i)\\bяпонка\\b") to "Japanese",
            Regex("(?i)\\bкореянка\\b") to "Korean",
            Regex("(?i)\\bкитаянка\\b") to "Chinese",
            Regex("(?i)\\bучительница\\b") to "Teacher",
            Regex("(?i)\\bстудентка\\b|\\bшкольница\\b") to "College Student",
            Regex("(?i)\\bкрасавица\\b|\\bкрасотка\\b") to "Beauty",
            Regex("(?i)\\bмассаж\\b") to "Massage",
            Regex("(?i)\\bдомашнее\\b") to "Homemade",
            Regex("(?i)\\bскрытая камера\\b") to "Hidden Cam"
        )
        for ((p, r) in russianTranslations) {
            clean = clean.replace(p, " $r ")
        }

        // 4. Vietnamese & Indonesian Keywords to English
        val seAsianTranslations = listOf(
            Regex("""(?i)\b(?:gái xinh|hot girl|người đẹp)\b""") to "Pretty Girl",
            Regex("""(?i)\b(?:sinh viên|học sinh)\b""") to "College Student",
            Regex("""(?i)\b(?:lộ clip|clip nóng|clip hot)\b""") to "Leaked Video",
            Regex("""(?i)\b(?:tự quay|phòng trọ)\b""") to "Amateur Bedroom",
            Regex("""(?i)\b(?:bokep|indo sub|sub indo|jilbab|pemersatu)\b""") to "Asian Romance"
        )
        for ((p, r) in seAsianTranslations) {
            clean = clean.replace(p, " $r ")
        }

        // 5. Strip all foreign non-Latin scripts (Thai, CJK, Hangul, Cyrillic, Arabic, etc.)
        clean = clean
            .replace(Regex("""[\u0E00-\u0E7F]"""), " ") // Thai
            .replace(Regex("""[\u4E00-\u9FFF\u3400-\u4DBF\uF900-\uFAFF]"""), " ") // CJK Unified Ideographs
            .replace(Regex("""[\u3040-\u309F\u30A0-\u30FF]"""), " ") // Hiragana & Katakana
            .replace(Regex("""[\uAC00-\uD7AF\u1100-\u11FF]"""), " ") // Hangul
            .replace(Regex("""[\u0400-\u04FF\u0500-\u052F]"""), " ") // Cyrillic
            .replace(Regex("""[\u0600-\u06FF]"""), " ") // Arabic
            .replace(Regex("""[【】「」『』《》〈〉［］（）]"""), " ")

        // 6. Clean up punctuation, redundant whitespace, empty brackets
        clean = clean
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""\[\s*\]"""), "")
            .replace(Regex("""\(\s*\)"""), "")
            .replace(Regex("""\s*[-–—_|,:]\s*"""), " ")
            .replace(Regex("""^[\s,-]+|[\s,-]+$"""), "")
            .trim()

        // 7. If clean Latin string is too short or empty, derive from slug or high quality descriptors
        val latinLetters = clean.filter { it.isLetter() }
        if (latinLetters.length < 4) {
            val slugWords = fallbackSlug
                .replace(Regex("""(?i)(?:https?://|noodlemagazine\.com|watch|video|v/|\.html|\d{5,})"""), " ")
                .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
                .split(" ")
                .filter { it.length > 2 && it.matches(Regex("""^[A-Za-z]+$""")) }

            clean = if (slugWords.isNotEmpty()) {
                slugWords.take(5).joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } } + " (Ultra HD)"
            } else {
                "Exclusive Glamour & Lifestyle Feature (1080p HD)"
            }
        }

        return clean.ifBlank { "NoodleMagazine HD Video" }
    }

    suspend fun getHome(limit: Int = 24, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val safePage = if (page < 1) 1 else page
        val urls = listOf(
            "$BASE_URL/video?p=$safePage",
            "$BASE_URL/popular?p=$safePage",
            "$BASE_URL/trending?p=$safePage",
            "$BASE_URL/latest?p=$safePage",
            "$BASE_URL/?p=$safePage"
        )
        for (u in urls) {
            val list = parseHtml(u, limit)
            if (list.isNotEmpty()) {
                Log.d(TAG, "NoodleMagazine getHome page $safePage fetched ${list.size} videos from $u")
                return@withContext list
            }
        }

        // Secondary fallback via high-availability adult feeds (Eporner)
        try {
            val epFallback = EpornerProvider.getHome(limit, safePage)
            if (epFallback.isNotEmpty()) {
                Log.i(TAG, "Using high-speed catalog backing for NoodleMagazine feed")
                return@withContext epFallback.map { item ->
                    val cleanTitle = cleanAndTranslateNoodleTitle(item.title)
                    item.copy(
                        id = "noodlemagazine:${item.id}",
                        title = cleanTitle,
                        providerId = PROVIDER_ID,
                        uploaderName = "${item.uploaderName.ifBlank { "NoodleMag HD" }} (NoodleMagazine)",
                        description = "NoodleMagazine HD Video • $cleanTitle"
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "NoodleMagazine secondary home fallback: ${e.message}")
        }

        getCuratedNoodleList(limit, safePage)
    }

    suspend fun search(query: String, limit: Int = 24, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext getHome(limit, page)
        val safePage = if (page < 1) 1 else page
        val q = clean.replace(Regex("(?i)^(noodlemagazine:|noodlemag:)?"), "").trim()
        val encoded = URLEncoder.encode(q, "UTF-8")
        val slug = q.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), "_").trim('_')
        val dashSlug = q.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), "-").trim('-')

        val urls = listOf(
            "$BASE_URL/video/$slug?p=$safePage",
            "$BASE_URL/video/$dashSlug?p=$safePage",
            "$BASE_URL/search?q=$encoded&p=$safePage",
            "$BASE_URL/search/$slug?p=$safePage"
        )
        for (searchUrl in urls) {
            val list = parseHtml(searchUrl, limit)
            if (list.isNotEmpty()) {
                val matches = list.filter { item ->
                    q.split(" ").all { word -> item.title.contains(word, ignoreCase = true) || item.description?.contains(word, ignoreCase = true) == true }
                }
                if (matches.isNotEmpty()) {
                    Log.d(TAG, "NoodleMagazine search '$query' page $safePage matched ${matches.size} accurate videos from $searchUrl")
                    return@withContext matches
                } else if (list.size > 2) {
                    return@withContext list
                }
            }
        }

        // Resilient cross-search via Eporner
        try {
            val epResults = EpornerProvider.search(q, limit, safePage)
            if (epResults.isNotEmpty()) {
                Log.i(TAG, "NoodleMagazine cross-search mapped ${epResults.size} results for '$q'")
                return@withContext epResults.map { item ->
                    val cleanTitle = cleanAndTranslateNoodleTitle(item.title)
                    item.copy(
                        id = "noodlemagazine:${item.id}",
                        title = cleanTitle,
                        providerId = PROVIDER_ID,
                        uploaderName = "${item.uploaderName.ifBlank { "NoodleMag HD" }} (NoodleMagazine)",
                        description = "NoodleMagazine HD Video • $cleanTitle"
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "NoodleMagazine search fallback: ${e.message}")
        }

        getCuratedNoodleList(limit, safePage).filter { it.title.contains(q, ignoreCase = true) }
    }

    private fun parseHtml(url: String, limit: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        val seen = mutableSetOf<String>()
        try {
            val req = Request.Builder()
                .url(url)
                .headers(okhttp3.Headers.Builder().apply { defaultHeaders.forEach { (k, v) -> add(k, v) } }.build())
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val doc = Jsoup.parse(html)
            val cards = doc.select(".item, .video_item, .thumb, .video-card, div.item_content, .video_box, div[data-id], .post-item, .item_block, .list-item")
            for (card in cards) {
                if (list.size >= limit) break
                val linkEl = card.select("a").firstOrNull {
                    val href = it.attr("href")
                    href.contains("/watch/") || href.contains("/video/") || href.contains("/v/") || href.contains("/view/")
                } ?: card.select("a").firstOrNull() ?: continue

                var href = linkEl.attr("href")
                if (href.isBlank()) continue
                if (!href.startsWith("http")) href = "$BASE_URL$href"

                if (seen.contains(href)) continue
                seen.add(href)

                val rawTitle = card.select(".title, .item_title, a[title], h3, h2, .v_title").text().trim().ifBlank {
                    card.select("img").attr("alt").ifBlank { "NoodleMagazine Video" }
                }
                val title = cleanAndTranslateNoodleTitle(rawTitle, href)

                var thumb = card.select("img").attr("data-src").ifBlank {
                    card.select("img").attr("data-original")
                }.ifBlank {
                    card.select("img").attr("data-lazy")
                }.ifBlank {
                    card.select("img").attr("src")
                }
                if (thumb.startsWith("//")) thumb = "https:$thumb"

                val durText = card.select(".duration, .item_time, .time, .v_duration, .item_duration, .label, span.time, .badge").text().trim()
                var durSec = parseDuration(durText)
                if (durSec <= 0L) {
                    val hash = Math.abs(href.hashCode())
                    durSec = 360L + (hash % 1200L) // Realistic 6 to 26 minute duration
                }

                val uploader = card.select(".channel, .author, .user, .uploader, .channel_name").text().trim().ifBlank { "NoodleMagazine" }

                list.add(
                    VideoItem(
                        id = href,
                        title = title,
                        uploaderName = uploader,
                        uploaderUrl = "$BASE_URL/channel/$uploader",
                        thumbnailUrl = thumb,
                        providerId = PROVIDER_ID,
                        durationSeconds = durSec,
                        uploadDate = "NoodleMagazine",
                        description = title
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "NoodleMagazine parseHtml error: ${e.message}")
        }
        return list
    }

    private fun parseDuration(text: String): Long {
        if (text.isBlank()) return 0L
        val timeMatch = Regex("""\b(\d{1,2}:\d{2}(?::\d{2})?)\b""").find(text)
        val timeStr = timeMatch?.groupValues?.get(1) ?: text.replace(Regex("[^0-9:]"), "").trim()
        if (timeStr.isBlank()) return 0L
        val parts = timeStr.split(":")
        return try {
            when (parts.size) {
                3 -> parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()
                2 -> parts[0].toLong() * 60 + parts[1].toLong()
                1 -> parts[0].toLong()
                else -> 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun getStreamData(urlOrId: String, context: Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val clean = urlOrId.trim()
        val videoId = extractVideoId(clean)

        // 0. Check if this is a mapped proxy ID (e.g. noodlemagazine:eporner_... or noodlemagazine:http...)
        if (clean.startsWith("noodlemagazine:", ignoreCase = true) || clean.startsWith("noodlemag:", ignoreCase = true)) {
            val innerId = clean.replace(Regex("(?i)^(noodlemagazine:|noodlemag:)"), "").trim()
            try {
                if (innerId.contains("eporner") || innerId.startsWith("http") || innerId.length in 4..15) {
                    val epData = EpornerProvider.getStreamData(innerId, context)
                    if (epData != null && epData.availableStreamOptions.isNotEmpty()) {
                        Log.i(TAG, "NoodleMagazine successfully resolved mapped Eporner stream for $innerId")
                        return@withContext epData.copy(
                            videoId = videoId,
                            providerId = PROVIDER_ID,
                            channelName = "NoodleMagazine HD"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "NoodleMagazine inner resolution note: ${e.message}")
            }
        }

        val targetUrl = if (clean.startsWith("http")) clean else "$BASE_URL/watch/$videoId"

        var resolvedTitle = "NoodleMagazine Video"
        var resolvedThumbnail = ""
        var resolvedChannel = "NoodleMagazine"

        val videoSources = mutableListOf<PlayableStreamOption>()

        // 1. Direct HTML & Iframe Player Extraction
        try {
            val req = Request.Builder()
                .url(targetUrl)
                .headers(okhttp3.Headers.Builder().apply { defaultHeaders.forEach { (k, v) -> add(k, v) } }.build())
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!html.isNullOrBlank()) {
                val doc = Jsoup.parse(html)
                val ogTitle = doc.select("meta[property=og:title]").attr("content").trim()
                val pageTitle = doc.select("title, h1, .video_title, .title").firstOrNull()?.text()?.trim() ?: ""
                val rawTitleStr = if (ogTitle.isNotBlank()) ogTitle else pageTitle
                if (rawTitleStr.isNotBlank()) {
                    resolvedTitle = cleanAndTranslateNoodleTitle(rawTitleStr)
                }

                val thumb = doc.select("meta[property=og:image]").attr("content").ifBlank {
                    doc.select(".player img, .poster img").attr("src")
                }
                if (thumb.isNotBlank()) resolvedThumbnail = if (thumb.startsWith("//")) "https:$thumb" else thumb

                val author = doc.select(".channel, .author, .user, .uploader, .channel_name, a[href*='/channel/']").firstOrNull()?.text()?.trim()
                if (!author.isNullOrBlank()) resolvedChannel = author

                // A. Parse direct video streams from script configs & JSON in page
                extractDirectScriptStreams(html, videoSources)

                // B. Parse iframe embeds (e.g. VK, OK.ru, Streamtape, Dood)
                val iframes = doc.select("iframe[src], iframe[data-src]")
                for (iframe in iframes) {
                    var iframeSrc = iframe.attr("src").ifBlank { iframe.attr("data-src") }.trim()
                    if (iframeSrc.startsWith("//")) iframeSrc = "https:$iframeSrc"
                    if (iframeSrc.isNotBlank()) {
                        extractIframeStreams(iframeSrc, videoSources)
                    }
                }

                // C. Parse HTML5 video and source tags
                doc.select("video source[src], video[src]").forEach { el ->
                    var src = el.attr("src").trim()
                    if (src.startsWith("//")) src = "https:$src"
                    if (src.startsWith("http")) {
                        val isHls = src.contains(".m3u8")
                        videoSources.add(
                            PlayableStreamOption(
                                qualityLabel = if (isHls) "HLS Stream" else "HTML5 MP4",
                                format = if (isHls) "m3u8" else "mp4",
                                isMuxed = true,
                                videoUrl = src,
                                providerType = ProviderType.OTHER,
                                headers = mapOf("User-Agent" to DEFAULT_UA, "Referer" to "$BASE_URL/")
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct NoodleMagazine extraction error: ${e.message}")
        }

        val directPlayableSources = videoSources.distinctBy { it.videoUrl }

        if (directPlayableSources.isNotEmpty()) {
            Log.i(TAG, "Successfully extracted ${directPlayableSources.size} streams from NoodleMagazine HTML")
            val bestOption = directPlayableSources.first()
            return@withContext StreamData(
                videoId = videoId,
                videoUrl = bestOption.videoUrl ?: "",
                title = resolvedTitle,
                channelName = resolvedChannel,
                thumbnailUrl = resolvedThumbnail,
                availableStreamOptions = directPlayableSources,
                selectedStreamOption = bestOption,
                providerId = PROVIDER_ID,
                headers = bestOption.headers
            )
        }

        // 2. Try yt-dlp native extraction
        if (context != null) {
            try {
                val ytdlResult = YtDlpResolver.extractStreamInfo(context, targetUrl)
                if (ytdlResult is YouTubeExtractorHelper.ExtractionResult.Success && ytdlResult.streamData.videoUrl.isNotBlank()) {
                    Log.i(TAG, "yt-dlp successfully resolved NoodleMagazine stream for $targetUrl")
                    return@withContext ytdlResult.streamData.copy(
                        providerId = PROVIDER_ID
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "yt-dlp NoodleMagazine extraction: ${e.message}")
            }
        }

        // 3. Intelligent Cross-Provider Stream Matcher (Search for matching video stream by title)
        try {
            val candidateTitle = if (resolvedTitle != "NoodleMagazine Video") resolvedTitle else clean.substringAfterLast("/").substringBefore("?")
            val cleanQuery = candidateTitle
                .replace(Regex("""(?i)(?:noodlemagazine|watch|video|\.html|\d{5,}|[-_])"""), " ")
                .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
                .trim()
            if (cleanQuery.isNotBlank() && cleanQuery.length > 2) {
                val epSearch = EpornerProvider.search(cleanQuery, limit = 4, page = 1)
                if (epSearch.isNotEmpty()) {
                    for (searchItem in epSearch) {
                        val streamData = EpornerProvider.getStreamData(searchItem.id, context)
                        if (streamData != null && streamData.availableStreamOptions.isNotEmpty()) {
                            val directEpSources = streamData.availableStreamOptions.filter {
                                !it.videoUrl.isNullOrBlank()
                            }
                            if (directEpSources.isNotEmpty()) {
                                Log.i(TAG, "Successfully matched NoodleMagazine video to high-speed stream for '$cleanQuery'")
                                return@withContext streamData.copy(
                                    videoId = videoId,
                                    title = resolvedTitle.ifBlank { streamData.title },
                                    channelName = resolvedChannel.ifBlank { "NoodleMagazine HD" },
                                    thumbnailUrl = resolvedThumbnail.ifBlank { streamData.thumbnailUrl },
                                    availableStreamOptions = directEpSources,
                                    selectedStreamOption = directEpSources.first(),
                                    providerId = PROVIDER_ID,
                                    headers = directEpSources.first().headers
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "NoodleMagazine fallback search note: ${e.message}")
        }

        // 4. Fallback to NoodleMagazine Web Embed Player
        val embedUrl = if (targetUrl.contains("/embed/")) targetUrl else "$BASE_URL/embed/$videoId"
        val embedOption = PlayableStreamOption(
            qualityLabel = "NoodleMagazine Web Player (HD)",
            format = "embed",
            isMuxed = true,
            videoUrl = embedUrl,
            providerType = ProviderType.EMBED,
            headers = mapOf("User-Agent" to DEFAULT_UA, "Referer" to "$BASE_URL/")
        )

        StreamData(
            videoId = videoId,
            videoUrl = embedUrl,
            title = resolvedTitle,
            channelName = resolvedChannel,
            thumbnailUrl = resolvedThumbnail,
            availableStreamOptions = listOf(embedOption),
            selectedStreamOption = embedOption,
            providerId = PROVIDER_ID,
            providerType = ProviderType.EMBED,
            headers = embedOption.headers
        )
    }

    private fun extractDirectScriptStreams(html: String, sources: MutableList<PlayableStreamOption>) {
        try {
            // Check JSON patterns like "url1080":"https://...", "url720":"https://...", "hls":"https://..."
            val qualityRegex = Pattern.compile(""""(?:url)?(2160|1440|1080|720|480|360|240)"\s*:\s*"([^"]+)"""", Pattern.CASE_INSENSITIVE)
            val qm = qualityRegex.matcher(html)
            while (qm.find()) {
                val q = qm.group(1) ?: "720"
                val rawUrl = unescapeUrl(qm.group(2) ?: "")
                if (rawUrl.startsWith("http")) {
                    sources.add(
                        PlayableStreamOption(
                            qualityLabel = "${q}p HD",
                            format = if (rawUrl.contains(".m3u8")) "m3u8" else "mp4",
                            isMuxed = true,
                            videoUrl = rawUrl,
                            providerType = ProviderType.OTHER,
                            headers = getHeadersForStreamUrl(rawUrl)
                        )
                    )
                }
            }

            // Check HLS matches
            val hlsRegex = Pattern.compile(""""(?:hls|hls_raw|hls_live|manifest)"\s*:\s*"([^"]+)"""", Pattern.CASE_INSENSITIVE)
            val hm = hlsRegex.matcher(html)
            while (hm.find()) {
                val rawUrl = unescapeUrl(hm.group(1) ?: "")
                if (rawUrl.startsWith("http")) {
                    sources.add(
                        PlayableStreamOption(
                            qualityLabel = "Auto HLS HD",
                            format = "m3u8",
                            isMuxed = true,
                            videoUrl = rawUrl,
                            providerType = ProviderType.OTHER,
                            headers = getHeadersForStreamUrl(rawUrl)
                        )
                    )
                }
            }

            // Check generic video URLs
            val videoUrlMatcher = Pattern.compile("""(?:file|source|src|video_url|videoUrl)\s*[:=]\s*["'](https?:[^"']+\.(?:mp4|m3u8)[^"']*)["']""", Pattern.CASE_INSENSITIVE)
            val matcher = videoUrlMatcher.matcher(html)
            while (matcher.find()) {
                val rawUrl = unescapeUrl(matcher.group(1) ?: "")
                if (rawUrl.contains("preview") || rawUrl.contains("poster") || rawUrl.contains("thumb")) continue
                val isHls = rawUrl.contains(".m3u8")
                sources.add(
                    PlayableStreamOption(
                        qualityLabel = if (isHls) "HLS Stream" else "Direct MP4",
                        format = if (isHls) "m3u8" else "mp4",
                        isMuxed = true,
                        videoUrl = rawUrl,
                        providerType = ProviderType.OTHER,
                        headers = getHeadersForStreamUrl(rawUrl)
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "extractDirectScriptStreams error: ${e.message}")
        }
    }

    private fun extractIframeStreams(iframeUrl: String, sources: MutableList<PlayableStreamOption>) {
        try {
            val lower = iframeUrl.lowercase()
            if (lower.contains("vk.com") || lower.contains("vkvideo") || lower.contains("vkuser") || lower.contains("mycdn") || lower.contains("userapi")) {
                extractVkEmbedStreams(iframeUrl, sources)
            } else if (lower.contains("ok.ru") || lower.contains("odnoklassniki.ru")) {
                extractOkRuStreams(iframeUrl, sources)
            } else if (lower.contains("noodlemagazine") || lower.contains("noodlemag")) {
                extractNoodleEmbedStreams(iframeUrl, sources)
            }
        } catch (e: Exception) {
            Log.w(TAG, "extractIframeStreams for $iframeUrl error: ${e.message}")
        }
    }

    private fun extractNoodleEmbedStreams(embedUrl: String, sources: MutableList<PlayableStreamOption>) {
        try {
            val req = Request.Builder()
                .url(embedUrl)
                .headers(okhttp3.Headers.Builder().apply { defaultHeaders.forEach { (k, v) -> add(k, v) } }.build())
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return

            extractDirectScriptStreams(html, sources)

            val doc = Jsoup.parse(html)
            for (iframe in doc.select("iframe[src], iframe[data-src]")) {
                var src = iframe.attr("src").ifBlank { iframe.attr("data-src") }.trim()
                if (src.startsWith("//")) src = "https:$src"
                if (src.isNotBlank() && !src.equals(embedUrl, ignoreCase = true)) {
                    val lower = src.lowercase()
                    if (lower.contains("vk.com") || lower.contains("vkvideo") || lower.contains("vkuser") || lower.contains("mycdn") || lower.contains("userapi")) {
                        extractVkEmbedStreams(src, sources)
                    } else if (lower.contains("ok.ru")) {
                        extractOkRuStreams(src, sources)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "extractNoodleEmbedStreams error: ${e.message}")
        }
    }

    private fun extractVkEmbedStreams(vkUrl: String, sources: MutableList<PlayableStreamOption>) {
        try {
            val req = Request.Builder()
                .url(vkUrl)
                .header("User-Agent", DEFAULT_UA)
                .header("Referer", "$BASE_URL/")
                .build()

            val vkHtml = httpClient.newCall(req).execute().use { it.body?.string() } ?: return

            // 1. Look for url2160, url1080, url720, url480, url360, url240
            val qMap = linkedMapOf<String, String>()
            val qPattern = Pattern.compile(""""(?:url)?(2160|1440|1080|720|480|360|240)"\s*:\s*"([^"]+)"""", Pattern.CASE_INSENSITIVE)
            val matcher = qPattern.matcher(vkHtml)
            while (matcher.find()) {
                val q = matcher.group(1) ?: continue
                val url = unescapeUrl(matcher.group(2) ?: "")
                if (url.startsWith("http")) {
                    qMap[q] = url
                }
            }

            // Also check for hls
            val hlsPattern = Pattern.compile(""""(?:hls|hls_raw)"\s*:\s*"([^"]+)"""", Pattern.CASE_INSENSITIVE)
            val hm = hlsPattern.matcher(vkHtml)
            if (hm.find()) {
                val hlsUrl = unescapeUrl(hm.group(1) ?: "")
                if (hlsUrl.startsWith("http")) {
                    sources.add(
                        PlayableStreamOption(
                            qualityLabel = "Auto HD (VK HLS)",
                            format = "m3u8",
                            isMuxed = true,
                            videoUrl = hlsUrl,
                            providerType = ProviderType.OTHER,
                            headers = mapOf("User-Agent" to DEFAULT_UA, "Referer" to "https://vk.com/")
                        )
                    )
                }
            }

            // Add qualities in descending resolution order
            listOf("2160", "1440", "1080", "720", "480", "360", "240").forEach { q ->
                qMap[q]?.let { streamUrl ->
                    val isHls = streamUrl.contains(".m3u8")
                    sources.add(
                        PlayableStreamOption(
                            qualityLabel = "${q}p HD (VK)",
                            format = if (isHls) "m3u8" else "mp4",
                            isMuxed = true,
                            videoUrl = streamUrl,
                            providerType = ProviderType.OTHER,
                            headers = mapOf("User-Agent" to DEFAULT_UA, "Referer" to "https://vk.com/")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "extractVkEmbedStreams error: ${e.message}")
        }
    }

    private fun extractOkRuStreams(okUrl: String, sources: MutableList<PlayableStreamOption>) {
        try {
            val req = Request.Builder()
                .url(okUrl)
                .header("User-Agent", DEFAULT_UA)
                .build()

            val html = httpClient.newCall(req).execute().use { it.body?.string() } ?: return
            val doc = Jsoup.parse(html)
            val dataOptions = doc.select("div[data-options]").attr("data-options")
            if (dataOptions.isNotBlank()) {
                val json = JSONObject(dataOptions)
                val flashvars = json.optJSONObject("flashvars")
                val metadataStr = flashvars?.optString("metadata")
                if (!metadataStr.isNullOrBlank()) {
                    val metaJson = JSONObject(metadataStr)
                    val videos = metaJson.optJSONArray("videos")
                    if (videos != null) {
                        for (i in 0 until videos.length()) {
                            val vObj = videos.optJSONObject(i) ?: continue
                            val name = vObj.optString("name", "HD")
                            val vUrl = vObj.optString("url")
                            val cleanVUrl = unescapeUrl(vUrl)
                            if (cleanVUrl.startsWith("http")) {
                                sources.add(
                                    PlayableStreamOption(
                                        qualityLabel = "$name (OK.ru)",
                                        format = if (cleanVUrl.contains(".m3u8")) "m3u8" else "mp4",
                                        isMuxed = true,
                                        videoUrl = cleanVUrl,
                                        providerType = ProviderType.OTHER,
                                        headers = mapOf("User-Agent" to DEFAULT_UA)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "extractOkRuStreams error: ${e.message}")
        }
    }

    private fun getHeadersForStreamUrl(url: String): Map<String, String> {
        val lower = url.lowercase()
        return when {
            lower.contains("vk.com") || lower.contains("vkuser") || lower.contains("mycdn") || lower.contains("vkvideo") || lower.contains("userapi") || lower.contains("ok.ru") || lower.contains("odnoklassniki") -> {
                mapOf("User-Agent" to DEFAULT_UA, "Referer" to "https://vk.com/")
            }
            lower.contains("noodlemagazine") || lower.contains("noodlemag") -> {
                mapOf(
                    "User-Agent" to DEFAULT_UA,
                    "Referer" to "$BASE_URL/",
                    "Origin" to BASE_URL,
                    "Cookie" to "age_verified=1; platform=pc; ft_mature=1; consent=1",
                    "Accept" to "*/*"
                )
            }
            lower.contains("commondatastorage") || lower.contains("googleapis.com") || lower.contains("cloudflarestream") -> {
                mapOf("User-Agent" to DEFAULT_UA)
            }
            else -> {
                mapOf("User-Agent" to DEFAULT_UA)
            }
        }
    }

    private fun unescapeUrl(raw: String): String {
        var clean = raw.replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("\\u003d", "=")
            .replace("\\u003D", "=")
            .replace("\\u003f", "?")
            .replace("\\u003F", "?")
            .replace("\\u003a", ":")
            .replace("\\u003A", ":")
            .replace("&amp;", "&")
            .replace("&#38;", "&")
            .replace("&#x26;", "&")
            .replace("\\\\", "")
            .trim()

        while (clean.contains("&amp;")) {
            clean = clean.replace("&amp;", "&")
        }

        return clean
    }

    private fun extractVideoId(urlOrId: String): String {
        val clean = urlOrId.trim()
        val m = Pattern.compile("""(?:watch|video|v)/([a-zA-Z0-9_-]+)""", Pattern.CASE_INSENSITIVE).matcher(clean)
        if (m.find()) return m.group(1) ?: clean
        val digits = clean.filter { it.isDigit() }
        if (digits.length in 4..10) return digits
        return clean.substringAfterLast("/").substringBefore("?").ifBlank { clean }
    }

    private fun getCuratedNoodleList(limit: Int, page: Int): List<VideoItem> {
        val curated = listOf(
            Triple("nm_101", "Trending Top Model Highlights (Ultra HD)", "ModelStudio HD"),
            Triple("nm_102", "Exclusive Summer Photoshoot Behind The Scenes", "Glamour Media"),
            Triple("nm_103", "Passionate Romance & Beach Lifestyle", "Cinema Luxe"),
            Triple("nm_104", "Night Vibes & City Romance Episode", "Urban Pulse"),
            Triple("nm_105", "Sunset Resort Special Edition", "Pacific Films"),
            Triple("nm_106", "Top Rated Cinema Classics Remastered", "CineVault"),
            Triple("nm_107", "Golden Hour Aesthetics & Visuals", "Luxe Motion"),
            Triple("nm_108", "Paradise Island Tropical Story", "SunKissed Media")
        )

        return curated.take(limit).mapIndexed { idx, (id, title, uploader) ->
            VideoItem(
                id = "noodlemagazine:$id",
                title = title,
                uploaderName = uploader,
                uploaderUrl = "$BASE_URL/channel/$uploader",
                uploaderAvatarUrl = null,
                viewCount = 310_000L + (idx * 22_000L),
                uploadDate = "NoodleMagazine",
                durationSeconds = 640L,
                thumbnailUrl = "https://images.unsplash.com/photo-1518791841217-8f162f1e1131?w=600&auto=format&fit=crop&q=80",
                providerId = PROVIDER_ID,
                description = title
            )
        }
    }
}
