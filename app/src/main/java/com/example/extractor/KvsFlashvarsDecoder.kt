package com.example.extractor

import android.util.Log
import java.net.URLDecoder

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
     * De-obfuscates KVS function/0/ URLs using the license_code key.
     */
    fun decodeKvsUrl(rawUrl: String, licenseCode: String?): String {
        var url = rawUrl.replace("\\/", "/").trim()
        val hasFunc = url.contains("function/") || url.startsWith("function/")
        if (!hasFunc) return url

        val cleanUrl = url.replace(Regex("""(?i)^function/\d+/"""), "")
            .replace(Regex("""(?i)function/\d+/"""), "")

        if (licenseCode.isNullOrBlank()) return cleanUrl

        val licDigits = licenseCode.replace(Regex("""[^0-9]"""), "")
        if (licDigits.isEmpty()) return cleanUrl

        try {
            val key = licDigits.map { it.toString().toInt() }
            val getFileRegex = Regex("""(/get_file/\d+/)([a-zA-Z0-9]{32})(/*)""")
            val match = getFileRegex.find(cleanUrl)
            if (match != null) {
                val prefix = match.groupValues[1]
                val hash = match.groupValues[2]
                val suffix = match.groupValues[3]

                val decodedHash = StringBuilder()
                for (i in hash.indices) {
                    val ch = hash[i]
                    val shift = key[i % key.size]
                    val newCh = when (ch) {
                        in 'a'..'z' -> 'a' + ((ch - 'a' - shift + 26) % 26)
                        in 'A'..'Z' -> 'A' + ((ch - 'A' - shift + 26) % 26)
                        in '0'..'9' -> '0' + ((ch - '0' - shift + 10) % 10)
                        else -> ch
                    }
                    decodedHash.append(newCh)
                }
                return cleanUrl.replace(match.groupValues[0], "$prefix${decodedHash}$suffix")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed KVS de-obfuscation: ${e.message}")
        }

        return cleanUrl
    }
}
