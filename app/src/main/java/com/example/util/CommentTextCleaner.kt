package com.example.util

import android.os.Build
import android.text.Html
import java.util.regex.Pattern

/**
 * High-performance HTML Sanitizer & Timestamp Link Normalizer for YouTube / Media comments.
 * Eliminates raw HTML tags (e.g. <a href="..." data-jump-time="457">7:37</a>) and cleans
 * HTML entities so comments are displayed as clean, readable text with interactive timestamps.
 */
object CommentTextCleaner {

    private val JUMP_TIME_REGEX = Regex("""<a\s+[^>]*data-jump-time="(\d+)"[^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE)
    private val WATCH_T_REGEX = Regex("""<a\s+[^>]*href="[^"]*(?:[?&]t=|\bt=)(\d+)[^"]*"[^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE)
    private val GENERIC_A_REGEX = Regex("""<a\s+[^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE)
    private val BR_REGEX = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val P_REGEX = Regex("""</?p\s*/?>""", RegexOption.IGNORE_CASE)
    private val ALL_TAGS_REGEX = Regex("""<[^>]+>""")
    private val MULTI_NEWLINE_REGEX = Regex("""\n{3,}""")

    /**
     * Cleans raw comment HTML / text into pure clean human-readable text.
     * Converts jump-time links into timestamps like "7:37" or "15:45".
     */
    fun cleanCommentText(rawHtmlOrText: String?): String {
        if (rawHtmlOrText.isNullOrBlank()) return ""

        var s = rawHtmlOrText.trim()

        // 1. Convert <br> and <p> tags into standard newlines
        s = BR_REGEX.replace(s, "\n")
        s = P_REGEX.replace(s, "\n")

        // 2. Extract anchor text from timestamp links (e.g. <a ...>7:37</a> -> 7:37)
        s = JUMP_TIME_REGEX.replace(s) { mr -> mr.groupValues[2] }
        s = WATCH_T_REGEX.replace(s) { mr -> mr.groupValues[2] }
        s = GENERIC_A_REGEX.replace(s) { mr -> mr.groupValues[1] }

        // 3. Unescape HTML entities using Android framework
        s = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(s, Html.FROM_HTML_MODE_COMPACT).toString()
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(s).toString()
            }
        } catch (_: Throwable) {
            manualUnescape(s)
        }

        // 4. Strip any remaining leftover tags
        s = ALL_TAGS_REGEX.replace(s, "")

        // 5. Secondary pass for unescaped entities
        s = manualUnescape(s)

        // 6. Normalize multiple blank lines into clean paragraph breaks
        s = MULTI_NEWLINE_REGEX.replace(s, "\n\n").trim()

        return s
    }

    private fun manualUnescape(text: String): String {
        return text
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&#8217;", "’")
            .replace("&#8216;", "‘")
            .replace("&#8220;", "“")
            .replace("&#8221;", "”")
            .replace("&#8212;", "—")
            .replace("&#8211;", "–")
    }

    /**
     * Converts a timestamp string (e.g. "07:37", "1:15:30", "0:45") into total seconds.
     */
    fun parseTimestampSeconds(timestampText: String): Long? {
        val clean = timestampText.trim().trim('(', ')', '[', ']', ' ')
        val parts = clean.split(":")
        return when (parts.size) {
            3 -> {
                val h = parts[0].toLongOrNull() ?: return null
                val m = parts[1].toLongOrNull() ?: return null
                val s = parts[2].toLongOrNull() ?: return null
                h * 3600 + m * 60 + s
            }
            2 -> {
                val m = parts[0].toLongOrNull() ?: return null
                val s = parts[1].toLongOrNull() ?: return null
                m * 60 + s
            }
            1 -> parts[0].toLongOrNull()
            else -> null
        }
    }
}
