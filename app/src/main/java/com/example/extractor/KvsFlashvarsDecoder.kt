package com.example.extractor

import android.util.Log
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Utility for parsing KVS (Kernel Video Sharing) and generic video player flashvars,
 * extracting license_code, video_url / video_alt_url / quality streams, and deobfuscating KVS hash URLs.
 */
object KvsFlashvarsDecoder {
    private const val TAG = "KvsFlashvarsDecoder"

    fun parseFlashvars(html: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        if (html.isBlank()) return params

        try {
            // 1. var flashvars = { ... } or flashvars = { ... }
            val jsonPattern = Regex("""(?:var\s+)?flashvars\s*=\s*\{([^}]+)\}""", RegexOption.IGNORE_CASE)
            val jsonMatch = jsonPattern.find(html)
            if (jsonMatch != null) {
                val body = jsonMatch.groupValues[1]
                val kvPattern = Regex("""['"]?([a-zA-Z0-9_\[\]%.-]+)['"]?\s*:\s*['"]([^'"]+)['"]""")
                kvPattern.findAll(body).forEach { m ->
                    val k = m.groupValues[1]
                    val v = m.groupValues[2]
                    params[k] = v
                }
            }

            // 2. flashvars["key"] = "value" or flashvars.key = "value"
            val assignPattern = Regex("""flashvars(?:\[['"]([^'"]+)['"]\]|\.([a-zA-Z0-9_]+))\s*=\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
            assignPattern.findAll(html).forEach { m ->
                val k = m.groupValues[1].ifBlank { m.groupValues[2] }
                val v = m.groupValues[3]
                if (k.isNotBlank()) params[k] = v
            }

            // 3. Key=Value URL encoded flashvars string e.g. flashvars = "license_code=...&video_url=..."
            val strPattern = Regex("""flashvars\s*=\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
            val strMatch = strPattern.find(html)
            if (strMatch != null) {
                val query = strMatch.groupValues[1]
                query.split("&").forEach { pair ->
                    val parts = pair.split("=")
                    if (parts.size >= 2) {
                        try {
                            val k = URLDecoder.decode(parts[0], "UTF-8")
                            val v = URLDecoder.decode(parts.subList(1, parts.size).joinToString("="), "UTF-8")
                            params[k] = v
                        } catch (_: Exception) {}
                    }
                }
            }

            // 4. Standalone license_code extraction
            if (!params.containsKey("license_code")) {
                val lic = Regex("""license_code\s*[:=]\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                if (lic != null) params["license_code"] = lic
            }

            // 5. Standalone video_url / video_alt_url / video_urls extraction from HTML/script
            val urlPatterns = listOf(
                Regex("""video_urls?\[([^\]]+)\]\s*[:=]\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE),
                Regex("""video_urls%5B([^%]+)%5D\s*[:=]\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE),
                Regex("""video_url\s*[:=]\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE),
                Regex("""video_alt_url\d*\s*[:=]\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
            )

            for (p in urlPatterns) {
                p.findAll(html).forEach { m ->
                    if (m.groupValues.size > 2) {
                        val quality = m.groupValues[1]
                        val url = m.groupValues[2]
                        params["video_urls][$quality]"] = url
                    } else if (m.groupValues.size > 1) {
                        val url = m.groupValues[1]
                        params["video_url"] = url
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing flashvars: ${e.message}")
        }

        return params
    }

    /**
     * De-obfuscates KVS function/0/ URLs using the canonical KVS license token permutation algorithm.
     */
    fun decodeKvsUrl(rawUrl: String, licenseCode: String?): String {
        var url = rawUrl.replace("\\/", "/").trim()
        val hasFunc = url.contains("function/0/") || url.startsWith("function/0/")
        if (!hasFunc) {
            // Also check for general function/N/ prefix
            return if (url.startsWith("function/")) url.replace(Regex("""(?i)^function/\d+/"""), "") else url
        }

        val urlWithoutPrefix = if (url.startsWith("function/0/")) {
            url.substring("function/0/".length)
        } else {
            url.substringAfter("function/0/")
        }

        if (licenseCode.isNullOrBlank()) return urlWithoutPrefix

        return try {
            val licenseToken = getKvsLicenseToken(licenseCode)
            val uri = java.net.URI(urlWithoutPrefix)
            val path = uri.path ?: ""
            val urlParts = path.split("/").toMutableList()

            // In KVS: path is /get_file/<storage_id>/<hash>/<dir1>/<dir2>/<filename>.mp4/
            // urlParts[0] = "", urlParts[1] = "get_file", urlParts[2] = "<storage_id>", urlParts[3] = "<hash>"
            if (urlParts.size > 3) {
                val hashLength = 32
                val origHash = urlParts[3]
                if (origHash.length >= hashLength && licenseToken.size >= hashLength) {
                    val hashPrefix = origHash.take(hashLength)
                    val indices = (0 until hashLength).toMutableList()
                    var accum = 0
                    for (src in (hashLength - 1) downTo 0) {
                        accum += licenseToken[src]
                        val dest = (src + accum) % hashLength
                        val temp = indices[src]
                        indices[src] = indices[dest]
                        indices[dest] = temp
                    }

                    val decodedHash = StringBuilder()
                    for (idx in indices) {
                        decodedHash.append(hashPrefix[idx])
                    }
                    urlParts[3] = decodedHash.toString() + origHash.substring(hashLength)

                    val newPath = urlParts.joinToString("/")
                    val newUri = java.net.URI(uri.scheme, uri.authority, newPath, uri.query, uri.fragment)
                    return newUri.toString()
                }
            }
            urlWithoutPrefix
        } catch (e: Exception) {
            Log.w(TAG, "Failed KVS permutation de-obfuscation: ${e.message}")
            urlWithoutPrefix
        }
    }

    private fun getKvsLicenseToken(licenseCode: String): List<Int> {
        val cleanLicense = licenseCode.replace("$", "").trim()
        val licenseValues = cleanLicense.mapNotNull { it.digitToIntOrNull() }
        if (licenseValues.isEmpty()) return emptyList()

        val modLicense = cleanLicense.replace('0', '1')
        val center = modLicense.length / 2
        val frontHalf = modLicense.take(center + 1).toLongOrNull() ?: 1L
        val backHalf = modLicense.substring(center).toLongOrNull() ?: 1L
        val diffStr = (4 * kotlin.math.abs(frontHalf - backHalf)).toString().take(center + 1)

        val tokens = mutableListOf<Int>()
        for ((index, ch) in diffStr.withIndex()) {
            val current = ch.digitToIntOrNull() ?: 0
            for (offset in 0 until 4) {
                val lIdx = index + offset
                if (lIdx < licenseValues.size) {
                    tokens.add((licenseValues[lIdx] + current) % 10)
                } else {
                    tokens.add(current % 10)
                }
            }
        }
        return tokens
    }
}
