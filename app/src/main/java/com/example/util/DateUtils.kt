package com.example.util

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

/**
 * Universal Date and Time formatter tailored for YouTube-style relative timestamps and metadata.
 */
object DateUtils {

    /**
     * Converts raw date strings (ISO-8601, yyyy-MM-dd, timestamp, etc.) into clean YouTube relative time.
     * Examples: "4 days ago", "2 weeks ago", "10 months ago", "10 hours ago", "1 year ago", "just now".
     * If the string cannot be parsed into a date (or is non-date text like a provider name), returns null.
     */
    fun formatRelativeTime(dateStr: String?): String? {
        if (dateStr.isNullOrBlank()) return null
        val trimmed = dateStr.trim()

        // 1. If already in relative format (e.g., "4 days ago", "2 weeks ago", "Streamed 3 days ago")
        if (trimmed.contains("ago", ignoreCase = true) || 
            trimmed.contains("just now", ignoreCase = true) ||
            trimmed.contains("yesterday", ignoreCase = true) ||
            trimmed.contains("today", ignoreCase = true)
        ) {
            return trimmed
        }

        // 2. Try parsing numeric epoch milliseconds or seconds
        if (trimmed.all { it.isDigit() }) {
            val num = trimmed.toLongOrNull()
            if (num != null) {
                val millis = if (num < 100_000_000_000L) num * 1000L else num
                return calculateTimeAgoFromMillis(millis)
            }
        }

        // 3. Try parsing ISO-8601 or java.time formats
        try {
            val instant = try {
                Instant.parse(trimmed)
            } catch (_: Exception) {
                try {
                    OffsetDateTime.parse(trimmed).toInstant()
                } catch (_: Exception) {
                    try {
                        ZonedDateTime.parse(trimmed).toInstant()
                    } catch (_: Exception) {
                        try {
                            LocalDateTime.parse(trimmed).atZone(ZoneId.systemDefault()).toInstant()
                        } catch (_: Exception) {
                            try {
                                LocalDate.parse(trimmed).atStartOfDay(ZoneId.systemDefault()).toInstant()
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }
                }
            }
            if (instant != null) {
                return calculateTimeAgoFromMillis(instant.toEpochMilli())
            }
        } catch (_: Exception) {}

        // 4. Try common date patterns
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy-MM-dd",
            "yyyy/MM/dd",
            "yyyy.MM.dd",
            "dd-MM-yyyy",
            "dd/MM/yyyy",
            "MMM dd, yyyy",
            "dd MMM yyyy",
            "MMMM dd, yyyy",
            "dd MMMM yyyy"
        )

        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.ENGLISH)
                sdf.isLenient = true
                val parsedDate = sdf.parse(trimmed)
                if (parsedDate != null) {
                    return calculateTimeAgoFromMillis(parsedDate.time)
                }
            } catch (_: Exception) {}
        }

        // 5. Fallback: If it contains a 4-digit year, check if it's just a year
        if (trimmed.length == 4 && trimmed.all { it.isDigit() }) {
            val year = trimmed.toIntOrNull()
            if (year != null && year in 1900..2100) {
                val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                val diff = currentYear - year
                return when {
                    diff <= 0 -> "this year"
                    diff == 1 -> "1 year ago"
                    else -> "$diff years ago"
                }
            }
        }

        // If not a recognized date format, return null rather than displaying raw provider names or junk
        return null
    }

    private fun calculateTimeAgoFromMillis(millis: Long): String {
        val now = System.currentTimeMillis()
        val diffSec = ((now - millis) / 1000).coerceAtLeast(0)

        return when {
            diffSec < 60 -> "just now"
            diffSec < 3600 -> {
                val mins = diffSec / 60
                if (mins <= 1) "1 minute ago" else "$mins minutes ago"
            }
            diffSec < 86400 -> {
                val hours = diffSec / 3600
                if (hours <= 1) "1 hour ago" else "$hours hours ago"
            }
            diffSec < 7 * 86400 -> {
                val days = diffSec / 86400
                if (days <= 1) "1 day ago" else "$days days ago"
            }
            diffSec < 30 * 86400 -> {
                val weeks = diffSec / (7 * 86400)
                if (weeks <= 1) "1 week ago" else "$weeks weeks ago"
            }
            diffSec < 365 * 86400 -> {
                // Between 30 days and 365 days: show months (e.g. 1 month ago, 10 months ago)
                val months = kotlin.math.max(1, kotlin.math.min(11, kotlin.math.round(diffSec / (30.4375 * 86400)).toInt()))
                if (months <= 1) "1 month ago" else "$months months ago"
            }
            else -> {
                // When it crosses months (>= 365 days), it only shows the years!
                val years = kotlin.math.max(1, (diffSec / (365.25 * 86400)).toInt())
                if (years <= 1) "1 year ago" else "$years years ago"
            }
        }
    }

    /**
     * Formats view count numbers into YouTube-style compact notation (e.g., 315K views, 1.8M views).
     */
    fun formatViews(viewCount: Long, rawFormatted: String? = null): String {
        if (!rawFormatted.isNullOrBlank()) {
            val trimmed = rawFormatted.trim()
            val lower = trimmed.lowercase()
            if (lower.contains("views") || lower.contains("view")) {
                val numPart = trimmed.substringBefore("view", "").substringBefore("View", "").trim()
                val isPlural = lower.contains("views")
                val cleanNum = numPart
                    .replace("k", "K")
                    .replace("m", "M")
                    .replace("b", "B")
                return if (cleanNum.isNotBlank()) "$cleanNum ${if (isPlural) "views" else "view"}" else trimmed
            }
            val cleanNum = trimmed
                .replace("k", "K")
                .replace("m", "M")
                .replace("b", "B")
            return "$cleanNum views"
        }
        if (viewCount <= 0L) return ""
        return when {
            viewCount >= 1_000_000_000 -> {
                val b = viewCount / 1_000_000_000.0
                if ((viewCount % 1_000_000_000L) == 0L) "${viewCount / 1_000_000_000}B views"
                else String.format(Locale.ENGLISH, "%.1fB views", b)
            }
            viewCount >= 10_000_000 -> "${viewCount / 1_000_000}M views"
            viewCount >= 1_000_000 -> {
                val m = viewCount / 1_000_000.0
                if ((viewCount % 1_000_000L) == 0L) "${viewCount / 1_000_000}M views"
                else String.format(Locale.ENGLISH, "%.1fM views", m)
            }
            viewCount >= 100_000 -> "${viewCount / 1_000}K views"
            viewCount >= 1_000 -> {
                val k = viewCount / 1_000.0
                if ((viewCount % 1_000L) == 0L) "${viewCount / 1_000}K views"
                else {
                    val formatted = String.format(Locale.ENGLISH, "%.1fK views", k)
                    if (formatted.endsWith(".0K views")) formatted.replace(".0K views", "K views") else formatted
                }
            }
            viewCount == 1L -> "1 view"
            else -> "$viewCount views"
        }
    }

    /**
     * Formats duration in seconds into digital timestamp (e.g. 3:45 or 1:12:05).
     */
    fun formatDurationSeconds(seconds: Long): String {
        if (seconds <= 0) return "0:00"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format(Locale.ENGLISH, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.ENGLISH, "%d:%02d", m, s)
        }
    }

    /**
     * Cleans channel name by removing redundant provider suffixes (e.g. "NGP Movies • PopcornTV" -> "NGP Movies").
     */
    fun cleanChannelName(channelName: String): String {
        if (channelName.isBlank()) return ""
        val cleaned = channelName
            .replace(Regex("""\s*[•·]\s*(PopcornTV|Popcorn|SonyLIV|Sony|Disney\+?|HBO|CuriosityStream|NoodleMag|CAM4|Chaturbate|IMDb|MX\s*Player|Internet\s*Archive|Archive)$""", RegexOption.IGNORE_CASE), "")
            .trim()
        return cleaned.ifBlank { channelName.trim() }
    }

    /**
     * Builds the unified YouTube-style metadata line:
     * "[Channel Name] • [Views] • [Time Ago]"
     * e.g. "Jit Production • 315K views • 4 days ago"
     * or "GS REACTION • 35K views • 10 months ago"
     */
    fun buildYouTubeMetadataLine(
        channelName: String,
        formattedViews: String,
        timeAgo: String?,
        extraTag: String? = null
    ): String {
        val cleanChannel = cleanChannelName(channelName)
        return buildList {
            if (cleanChannel.isNotBlank()) {
                add(cleanChannel)
            }
            if (formattedViews.isNotBlank()) {
                add(formattedViews)
            }
            if (!timeAgo.isNullOrBlank()) {
                add(timeAgo)
            }
            if (!extraTag.isNullOrBlank()) {
                add(extraTag)
            }
        }.joinToString(" • ")
    }
}
