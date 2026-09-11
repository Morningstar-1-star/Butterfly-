package com.example.torrent.cardigann.filter

import android.util.Base64
import com.example.torrent.cardigann.model.CardigannFilterConfig
import com.example.torrent.model.TorrentResult
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * Filter Engine implementing all Cardigann / Prowlarr V11 transformation filters.
 * Applies sequential filters to extracted fields.
 */
object CardigannFilterEngine {

    fun applyFilters(initialValue: String, filters: List<CardigannFilterConfig>): String {
        var current = initialValue
        for (filter in filters) {
            current = applySingleFilter(current, filter)
        }
        return current
    }

    private fun applySingleFilter(value: String, filter: CardigannFilterConfig): String {
        return when (filter.name.lowercase(Locale.US)) {
            "regexp" -> {
                val patternStr = filter.getArgString(0)
                if (patternStr.isBlank()) return value
                try {
                    val pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
                    val matcher = pattern.matcher(value)
                    if (matcher.find()) {
                        if (matcher.groupCount() >= 1) {
                            matcher.group(1) ?: value
                        } else {
                            matcher.group(0) ?: value
                        }
                    } else {
                        value
                    }
                } catch (_: Exception) {
                    value
                }
            }

            "re_replace" -> {
                val patternStr = filter.getArgString(0)
                val replacement = filter.getArgString(1, "")
                if (patternStr.isBlank()) return value
                try {
                    value.replace(Regex(patternStr, RegexOption.IGNORE_CASE), replacement)
                } catch (_: Exception) {
                    value
                }
            }

            "replace" -> {
                val target = filter.getArgString(0)
                val replacement = filter.getArgString(1, "")
                if (target.isEmpty()) return value
                value.replace(target, replacement)
            }

            "split" -> {
                val delimiter = filter.getArgString(0, " ")
                val index = filter.getArgInt(1, 0)
                val parts = value.split(delimiter)
                if (index >= 0 && index < parts.size) {
                    parts[index]
                } else if (index < 0 && parts.size + index >= 0) {
                    parts[parts.size + index]
                } else {
                    value
                }
            }

            "trim" -> value.trim()

            "tolower" -> value.lowercase(Locale.US)

            "toupper" -> value.uppercase(Locale.US)

            "dateparse", "timeparse" -> {
                val format = filter.getArgString(0)
                parseDate(value, format)
            }

            "timeago", "relative" -> parseRelativeTime(value)

            "bytesparse", "size" -> {
                val bytes = TorrentResult.parseBytes(value)
                bytes.toString()
            }

            "urlencode" -> {
                try {
                    URLEncoder.encode(value, StandardCharsets.UTF_8.name())
                } catch (_: Exception) {
                    value
                }
            }

            "urldecode" -> {
                try {
                    URLDecoder.decode(value, StandardCharsets.UTF_8.name())
                } catch (_: Exception) {
                    value
                }
            }

            "base64decode" -> {
                try {
                    val decoded = Base64.decode(value.trim(), Base64.DEFAULT)
                    String(decoded, StandardCharsets.UTF_8)
                } catch (_: Exception) {
                    value
                }
            }

            "base64encode" -> {
                try {
                    Base64.encodeToString(value.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
                } catch (_: Exception) {
                    value
                }
            }

            "hexdecode" -> {
                try {
                    val clean = value.replace(" ", "").trim()
                    val bytes = ByteArray(clean.length / 2)
                    for (i in bytes.indices) {
                        bytes[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                    }
                    String(bytes, StandardCharsets.UTF_8)
                } catch (_: Exception) {
                    value
                }
            }

            "querystring" -> {
                val paramName = filter.getArgString(0)
                if (paramName.isBlank()) return value
                extractQueryParam(value, paramName)
            }

            "append" -> {
                val suffix = filter.getArgString(0)
                value + suffix
            }

            "prepend" -> {
                val prefix = filter.getArgString(0)
                prefix + value
            }

            "default" -> {
                val fallback = filter.getArgString(0)
                if (value.isBlank()) fallback else value
            }

            "strip_tags" -> {
                value.replace(Regex("<[^>]*>"), "").trim()
            }

            "validfilename" -> {
                value.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
            }

            else -> value
        }
    }

    private fun extractQueryParam(urlOrQuery: String, paramName: String): String {
        val query = if (urlOrQuery.contains("?")) {
            urlOrQuery.substringAfter("?")
        } else {
            urlOrQuery
        }
        val pairs = query.split("&")
        for (pair in pairs) {
            val parts = pair.split("=", limit = 2)
            if (parts.isNotEmpty() && parts[0].equals(paramName, ignoreCase = true)) {
                return if (parts.size > 1) {
                    try {
                        URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name())
                    } catch (_: Exception) {
                        parts[1]
                    }
                } else ""
            }
        }
        return ""
    }

    private fun parseDate(dateStr: String, format: String): String {
        val clean = dateStr.trim()
        if (clean.isBlank()) return ""

        val formatsToTry = mutableListOf<String>()
        if (format.isNotBlank()) formatsToTry.add(format)
        formatsToTry.addAll(
            listOf(
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd",
                "MMM d yyyy",
                "MMM dd, yyyy",
                "d MMM yyyy",
                "dd.MM.yyyy",
                "dd/MM/yyyy",
                "MM/dd/yyyy",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "EEE, dd MMM yyyy HH:mm:ss Z"
            )
        )

        for (fmt in formatsToTry) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                sdf.isLenient = true
                val parsed: Date? = sdf.parse(clean)
                if (parsed != null) {
                    val outSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    return outSdf.format(parsed)
                }
            } catch (_: Exception) {
                // Continue trying
            }
        }
        return clean
    }

    fun parseRelativeTime(relativeStr: String): String {
        val clean = relativeStr.trim().lowercase(Locale.US)
        val now = Calendar.getInstance()

        return try {
            val num = Regex("""\d+""").find(clean)?.value?.toIntOrNull() ?: 1
            when {
                clean.contains("sec") -> now.add(Calendar.SECOND, -num)
                clean.contains("min") -> now.add(Calendar.MINUTE, -num)
                clean.contains("hour") || clean.contains("hr") -> now.add(Calendar.HOUR_OF_DAY, -num)
                clean.contains("yesterday") -> now.add(Calendar.DAY_OF_YEAR, -1)
                clean.contains("day") -> now.add(Calendar.DAY_OF_YEAR, -num)
                clean.contains("week") || clean.contains("wk") -> now.add(Calendar.WEEK_OF_YEAR, -num)
                clean.contains("month") || clean.contains("mo") -> now.add(Calendar.MONTH, -num)
                clean.contains("year") || clean.contains("yr") -> now.add(Calendar.YEAR, -num)
            }
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            sdf.format(now.time)
        } catch (_: Exception) {
            relativeStr
        }
    }
}
