package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import com.example.model.StreamData
import com.example.model.VideoItem
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object XHamsterProvider {
    private const val TAG = "XHamsterProvider"
    const val PROVIDER_ID = "xhamster"

    private const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // ------------------- STREAM URL DECODER -------------------
    private fun imul(a: Long, b: Long): Int {
        val aInt = a.toInt()
        val bInt = b.toInt()
        val aLow = aInt and 0xFFFF
        val aHigh = aInt ushr 16
        val bLow = bInt and 0xFFFF
        val bHigh = bInt ushr 16
        return (aLow * bLow + (((aHigh * bLow + aLow * bHigh) and 0xFFFF) shl 16))
    }

    fun decodeHexUrl(hexStr: String?): String? {
        if (hexStr.isNullOrBlank() || hexStr.length < 12 || hexStr.length % 2 != 0) {
            return null
        }
        if (!hexStr.matches(Regex("^[0-9a-fA-F]+$"))) {
            return null
        }

        val rawBytes: ByteArray
        try {
            val len = hexStr.length
            rawBytes = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                rawBytes[i / 2] = ((Character.digit(hexStr[i], 16) shl 4) + Character.digit(hexStr[i + 1], 16)).toByte()
                i += 2
            }
        } catch (e: Exception) {
            return null
        }

        if (rawBytes.size < 5) return null

        val algoId = rawBytes[0].toInt() and 0xFF
        val seed = (rawBytes[1].toInt() and 0xFF) or
                ((rawBytes[2].toInt() and 0xFF) shl 8) or
                ((rawBytes[3].toInt() and 0xFF) shl 16) or
                ((rawBytes[4].toInt() and 0xFF) shl 24)

        var curr = seed
        fun nextByte(): Int {
            when (algoId) {
                1 -> {
                    curr = (imul(curr.toLong(), 1664525L) + 0x3c6ef35f)
                    return curr and 0xFF
                }
                2 -> {
                    curr = curr xor (curr shl 13)
                    curr = curr xor (curr ushr 17)
                    curr = curr xor (curr shl 5)
                    return curr and 0xFF
                }
                3 -> {
                    curr += 0x9e3779b9.toInt()
                    var e = curr
                    e = e xor (e ushr 16)
                    e = imul(e.toLong(), 0x85ebca77L)
                    e = e xor (e ushr 13)
                    e = imul(e.toLong(), 0xc2b2ae3dL)
                    e = e xor (e ushr 16)
                    return e and 0xFF
                }
                4 -> {
                    curr += 0x6d2b79f5.toInt()
                    var e = (curr shl 7) or (curr ushr 25)
                    e += 0x9e3779b9.toInt()
                    e = e xor (e ushr 11)
                    e = imul(e.toLong(), 0x27d4eb2dL)
                    return e and 0xFF
                }
                5 -> {
                    curr = curr xor (curr shl 7)
                    curr = curr xor (curr ushr 9)
                    curr = curr xor (curr shl 8)
                    curr += 0xa5a5a5a5.toInt()
                    return curr and 0xFF
                }
                6 -> {
                    curr = imul(curr.toLong(), 0x2c9277b5L) + 0xac564b05.toInt()
                    val shift1 = curr ushr 18
                    val shift2 = (curr ushr 27) and 31
                    val v = (curr xor shift1) ushr shift2
                    return v and 0xFF
                }
                7 -> {
                    curr += 0x9e3779b9.toInt()
                    var e = curr xor (curr shl 5)
                    e = imul(e.toLong(), 0x7feb352dL)
                    e = e xor (e ushr 15)
                    e = imul(e.toLong(), 0x846ca68bL)
                    return e and 0xFF
                }
                else -> return 0
            }
        }

        val decrypted = ByteArray(rawBytes.size - 5)
        for (idx in 5 until rawBytes.size) {
            decrypted[idx - 5] = (rawBytes[idx].toInt() xor nextByte()).toByte()
        }

        return try {
            val result = String(decrypted, Charsets.UTF_8)
            if (result.startsWith("http")) result else null
        } catch (e: Exception) {
            null
        }
    }

    // ------------------- CATALOG / SEARCH -------------------
    fun getHome(limit: Int = 20, page: Int = 1): List<VideoItem> {
        val urls = listOf(
            if (page > 1) "https://xhamster.com/best/$page" else "https://xhamster.com/best",
            if (page > 1) "https://xhamster.com/trending/$page" else "https://xhamster.com/trending",
            "https://xhamster.com/"
        )
        for (u in urls) {
            val items = parseListingHtml(u, limit)
            if (items.isNotEmpty()) return items
        }
        return emptyList()
    }

    fun search(query: String, limit: Int = 20, page: Int = 1): List<VideoItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val targetUrl = if (page > 1) "https://xhamster.com/search/$encoded?page=$page" else "https://xhamster.com/search/$encoded"
        return parseListingHtml(targetUrl, limit)
    }

    private fun parseListingHtml(targetUrl: String, limit: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", DEFAULT_UA)
                .header("Cookie", "age_verified=1; platform=pc; has_consent=1")
                .header("Referer", "https://xhamster.com/")
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            // Find all video thumbnail cards (matching relative /videos/ links or full URLs)
            val cardPattern = Pattern.compile(
                """<a\s+[^>]*href="((?:https://xhamster\.com)?/videos/[^"]+)"[^>]*>(.*?)</a>""",
                Pattern.DOTALL or Pattern.CASE_INSENSITIVE
            )
            val matcher = cardPattern.matcher(html)
            val seen = mutableSetOf<String>()

            while (matcher.find() && list.size < limit) {
                val rawUrl = matcher.group(1) ?: continue
                val videoUrl = if (rawUrl.startsWith("http")) rawUrl else "https://xhamster.com$rawUrl"
                val body = matcher.group(2) ?: ""

                if (seen.contains(videoUrl)) continue
                seen.add(videoUrl)

                // Title
                var title = "xHamster Video"
                val tMatch = Pattern.compile("""(?:aria-label|alt|title)="([^"]+)"""", Pattern.CASE_INSENSITIVE).matcher(body)
                if (tMatch.find()) {
                    val candidateTitle = tMatch.group(1)?.trim() ?: ""
                    if (candidateTitle.isNotBlank() && !candidateTitle.equals("thumb", ignoreCase = true)) {
                        title = candidateTitle
                    }
                }

                // Thumbnail: Prioritize real CDN image URLs and avoid svg/data placeholder URIs
                var thumb = ""
                val imgCandidatePattern = Pattern.compile("""(?:data-src|data-lazy-src|data-preview|data-webp|data-thumb-url|data-poster|srcset|src)=["']([^"'\s,]+)["']""", Pattern.CASE_INSENSITIVE)
                val imgMatcher = imgCandidatePattern.matcher(body)
                while (imgMatcher.find()) {
                    val candidate = imgMatcher.group(1)?.trim() ?: continue
                    if (candidate.startsWith("http") && !candidate.contains("data:image") && !candidate.endsWith(".svg")) {
                        thumb = candidate
                        break
                    }
                }

                if (thumb.isBlank()) {
                    val genericUrlMatch = Pattern.compile("""(https?://[^"'\s>]*(?:xhcdn|xhamster|rdtcdn)[^"'\s>]*\.(?:jpg|jpeg|webp|png)[^"'\s>]*)""", Pattern.CASE_INSENSITIVE).matcher(body)
                    if (genericUrlMatch.find()) {
                        thumb = genericUrlMatch.group(1) ?: ""
                    }
                }

                if (thumb.isBlank()) {
                    thumb = "https://ei-ph.rdtcdn.com/videos/original/(m=eaSaaSbWaaa)${list.size % 8 + 1}.jpg"
                }

                // Duration
                var durSec = -1L
                val durMatch = Pattern.compile("([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?)").matcher(body)
                if (durMatch.find()) {
                    val durStr = durMatch.group(1) ?: ""
                    durSec = parseDurationToSeconds(durStr)
                }

                val previewList = if (thumb.isNotBlank()) {
                    com.example.util.PreviewFrameResolver.resolvePreviewFrames(
                        VideoItem(id = videoUrl, title = title, uploaderName = "xHamster", thumbnailUrl = thumb, providerId = PROVIDER_ID)
                    )
                } else emptyList()

                list.add(
                    VideoItem(
                        id = videoUrl,
                        title = title,
                        uploaderName = "xHamster",
                        thumbnailUrl = thumb,
                        durationSeconds = durSec,
                        providerId = PROVIDER_ID,
                        previewThumbnails = previewList
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Listing parse error for $targetUrl: ${e.message}")
        }
        return list
    }

    private fun parseDurationToSeconds(durStr: String): Long {
        val parts = durStr.split(":")
        var total = 0L
        for (p in parts) {
            total = total * 60 + (p.toLongOrNull() ?: 0L)
        }
        return total
    }

    // ------------------- STREAM EXTRACTION -------------------
    fun getStreamData(urlOrId: String, context: Context?): StreamData? {
        val targetUrl = if (urlOrId.startsWith("http")) urlOrId else "https://xhamster.com/videos/$urlOrId"
        Log.d(TAG, "Fetching xHamster stream data for $targetUrl")

        try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", DEFAULT_UA)
                .header("Cookie", "age_verified=1; platform=pc")
                .header("Referer", "https://xhamster.com/")
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return null

            // Title
            var title = "xHamster Video"
            val metaTitle = Pattern.compile("<meta\\s+property=\"og:title\"\\s+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(html)
            if (metaTitle.find()) {
                title = metaTitle.group(1)?.trim() ?: title
            } else {
                val h1Match = Pattern.compile("<h1[^>]*>(.*?)</h1>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE).matcher(html)
                if (h1Match.find()) {
                    title = h1Match.group(1)?.replace(Regex("<[^>]*>"), "")?.trim() ?: title
                }
            }

            // Thumbnail
            var thumb = ""
            val metaThumb = Pattern.compile("<meta\\s+property=\"og:image\"\\s+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(html)
            if (metaThumb.find()) {
                thumb = metaThumb.group(1)?.trim() ?: ""
            }

            val headers = mapOf(
                "User-Agent" to DEFAULT_UA,
                "Referer" to "https://xhamster.com/"
            )

            val streamOptions = mutableListOf<PlayableStreamOption>()

            // 1. Extract window.initials -> xplayerSettings
            val initialsMatch = Pattern.compile("window\\.initials\\s*=\\s*(\\{.*?\\});</script>", Pattern.DOTALL).matcher(html)
            if (initialsMatch.find()) {
                val jsonStr = initialsMatch.group(1) ?: ""
                try {
                    val root = JSONObject(jsonStr)
                    if (root.has("videoModel")) {
                        val vm = root.getJSONObject("videoModel")
                        if (thumb.isBlank()) {
                            thumb = vm.optString("thumbURL", "")
                        }
                    }

                    if (root.has("xplayerSettings")) {
                        val xps = root.getJSONObject("xplayerSettings")
                        if (xps.has("sources")) {
                            val sources = xps.getJSONObject("sources")
                            // HLS streams
                            if (sources.has("hls")) {
                                val hlsObj = sources.getJSONObject("hls")
                                for (key in listOf("h264", "av1")) {
                                    if (hlsObj.has(key)) {
                                        val entry = hlsObj.getJSONObject(key)
                                        val encUrl = entry.optString("url", "")
                                        val decUrl = decodeHexUrl(encUrl)
                                        if (!decUrl.isNullOrBlank()) {
                                            streamOptions.add(
                                                PlayableStreamOption(
                                                    qualityLabel = "Auto HLS (${key.uppercase()})",
                                                    format = "m3u8",
                                                    isMuxed = true,
                                                    videoUrl = decUrl,
                                                    providerType = ProviderType.OTHER,
                                                    headers = headers
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            // Standard progressive MP4 streams
                            if (sources.has("standard")) {
                                val stdObj = sources.getJSONObject("standard")
                                for (codec in listOf("h264", "av1")) {
                                    if (stdObj.has(codec)) {
                                        val arr = stdObj.getJSONArray(codec)
                                        for (i in 0 until arr.length()) {
                                            val item = arr.getJSONObject(i)
                                            val encUrl = item.optString("url", "")
                                            val decUrl = decodeHexUrl(encUrl)
                                            val quality = item.optString("quality", "720p")
                                            val label = item.optString("label", quality)
                                            if (!decUrl.isNullOrBlank()) {
                                                val isHls = decUrl.contains(".m3u8")
                                                streamOptions.add(
                                                    PlayableStreamOption(
                                                        qualityLabel = "$label ($codec)",
                                                        format = if (isHls) "m3u8" else "mp4",
                                                        isMuxed = true,
                                                        videoUrl = decUrl,
                                                        providerType = ProviderType.OTHER,
                                                        headers = headers
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing window.initials JSON: ${e.message}")
                }
            }

            // 2. Direct regex fallback for m3u8 / xhcdn URLs
            if (streamOptions.isEmpty()) {
                val cleanHtml = html.replace("\\/", "/")
                val m3u8Matcher = Pattern.compile("https?://video-nss[^\",\\s<>]+\\.m3u8", Pattern.CASE_INSENSITIVE).matcher(cleanHtml)
                while (m3u8Matcher.find()) {
                    val m3u8Url = m3u8Matcher.group(0) ?: continue
                    streamOptions.add(
                        PlayableStreamOption(
                            qualityLabel = "Direct HLS",
                            format = "m3u8",
                            isMuxed = true,
                            videoUrl = m3u8Url,
                            providerType = ProviderType.OTHER,
                            headers = headers
                        )
                    )
                }
            }

            if (streamOptions.isEmpty()) {
                Log.w(TAG, "No playable streams found for xHamster video: $targetUrl")
                return null
            }

            val sortedOptions = streamOptions.distinctBy { it.videoUrl ?: "" }.sortedWith(
                compareByDescending<PlayableStreamOption> { it.format == "m3u8" }
                    .thenByDescending {
                        val num = Regex("""\d+""").find(it.qualityLabel)?.value?.toIntOrNull() ?: 0
                        num
                    }
            )

            val primaryStream = sortedOptions.firstOrNull()
            val primaryUrl = primaryStream?.videoUrl ?: ""

            return StreamData(
                videoId = targetUrl,
                videoUrl = primaryUrl,
                title = title,
                channelName = "xHamster",
                thumbnailUrl = thumb,
                availableStreamOptions = sortedOptions,
                selectedStreamOption = primaryStream,
                providerId = PROVIDER_ID,
                headers = headers
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception extracting xHamster stream: ${e.message}", e)
            return null
        }
    }
}
