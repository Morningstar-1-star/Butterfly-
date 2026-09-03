package com.example.bunkr.resolver

import com.example.bunkr.model.BunkrUrlInfo
import com.example.bunkr.model.BunkrUrlType
import java.net.URI
import java.util.regex.Pattern

object BunkrUrlUtils {

    const val DEFAULT_BUNKR_DOMAIN = "bunkr.site"

    val KNOWN_BUNKR_DOMAINS = listOf(
        "bunkr.site",
        "bunkr.is",
        "bunkr.si",
        "bunkr.ws",
        "bunkr.black",
        "bunkr.media",
        "bunkr.ax",
        "bunkr.ph",
        "bunkr.cr",
        "bunkrr.su"
    )

    // Matches bunkr domains: bunkr.cr, bunkr.is, bunkr.la, bunkrr.org, bunkr.ph, bunkr.site, etc.
    private val BUNKR_DOMAIN_PATTERN = Pattern.compile(
        "^(https?://)?(www\\.)?(bunkr|bunkrr)\\.[a-z]{2,8}(/.*)?$",
        Pattern.CASE_INSENSITIVE
    )

    private val ALBUM_PATH_PATTERN = Pattern.compile(
        "^/a/([a-zA-Z0-9_\\-]+)",
        Pattern.CASE_INSENSITIVE
    )

    private val FILE_PATH_PATTERN = Pattern.compile(
        "^/(f|v|d|get)/([a-zA-Z0-9_\\-]+)",
        Pattern.CASE_INSENSITIVE
    )

    fun isBunkrUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val trimmed = url.trim()
        return BUNKR_DOMAIN_PATTERN.matcher(trimmed).matches() ||
                trimmed.contains("bunkr.", ignoreCase = true) ||
                trimmed.contains("bunkrr.", ignoreCase = true)
    }

    fun parseUrl(rawUrl: String): BunkrUrlInfo? {
        if (rawUrl.isBlank()) return null
        val trimmed = rawUrl.trim()
        val formatted = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else trimmed

        return try {
            val uri = URI(formatted)
            var host = uri.host ?: DEFAULT_BUNKR_DOMAIN
            if (!host.contains("bunkr", ignoreCase = true)) {
                host = DEFAULT_BUNKR_DOMAIN
            }
            val path = uri.path ?: "/"

            val albumMatcher = ALBUM_PATH_PATTERN.matcher(path)
            if (albumMatcher.find()) {
                val albumId = albumMatcher.group(1) ?: return null
                val canonicalUrl = "https://$host/a/$albumId"
                return BunkrUrlInfo(
                    rawUrl = rawUrl,
                    canonicalUrl = canonicalUrl,
                    type = BunkrUrlType.ALBUM,
                    id = albumId,
                    domain = host
                )
            }

            val fileMatcher = FILE_PATH_PATTERN.matcher(path)
            if (fileMatcher.find()) {
                val fileId = fileMatcher.group(2) ?: return null
                val canonicalUrl = "https://$host/f/$fileId"
                return BunkrUrlInfo(
                    rawUrl = rawUrl,
                    canonicalUrl = canonicalUrl,
                    type = BunkrUrlType.FILE,
                    id = fileId,
                    domain = host
                )
            }

            // Fallback heuristics
            if (path.contains("/a/")) {
                val id = path.substringAfter("/a/").substringBefore("/").substringBefore("?")
                if (id.isNotBlank()) {
                    return BunkrUrlInfo(rawUrl, "https://$host/a/$id", BunkrUrlType.ALBUM, id, host)
                }
            } else if (path.contains("/f/") || path.contains("/v/") || path.contains("/d/")) {
                val delimiter = when {
                    path.contains("/f/") -> "/f/"
                    path.contains("/v/") -> "/v/"
                    else -> "/d/"
                }
                val id = path.substringAfter(delimiter).substringBefore("/").substringBefore("?")
                if (id.isNotBlank()) {
                    return BunkrUrlInfo(rawUrl, "https://$host/f/$id", BunkrUrlType.FILE, id, host)
                }
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    fun normalizeThumbnailUrl(raw: String?, domain: String = DEFAULT_BUNKR_DOMAIN, fileId: String = ""): String? {
        val clean = raw?.trim()
        if (clean.isNullOrBlank()) {
            return if (fileId.isNotBlank()) "https://i.$domain/thumbs/$fileId.jpg" else null
        }
        return when {
            clean.startsWith("http://") || clean.startsWith("https://") -> clean
            clean.startsWith("//") -> "https:$clean"
            clean.startsWith("/") -> "https://$domain$clean"
            clean.startsWith("thumbs/") -> "https://$domain/$clean"
            else -> "https://$domain/thumbs/$clean"
        }
    }

    fun extractUrlsFromText(text: String): List<BunkrUrlInfo> {
        if (text.isBlank()) return emptyList()
        val lines = text.split(Regex("[\\n\\r\\s,;]+"))
        val results = mutableListOf<BunkrUrlInfo>()
        val seenCanonical = mutableSetOf<String>()

        for (line in lines) {
            val clean = line.trim()
            if (clean.isBlank()) continue
            val parsed = parseUrl(clean)
            if (parsed != null && seenCanonical.add(parsed.canonicalUrl)) {
                results.add(parsed)
            }
        }
        return results
    }

    fun buildDefaultHeaders(refererUrl: String = "https://$DEFAULT_BUNKR_DOMAIN/"): Map<String, String> {
        return mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Referer" to refererUrl,
            "Origin" to refererUrl.removeSuffix("/"),
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,video/*;q=0.8,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9",
            "Sec-Fetch-Mode" to "navigate"
        )
    }
}
