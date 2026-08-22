package com.example.subtitles

import android.util.Log
import com.example.util.SubtitleCue
import java.util.regex.Pattern

/**
 * Universal parser for SRT, WebVTT, and ASS/SSA subtitle file formats into List<SubtitleCue>.
 */
object SubtitleParser {
    private const val TAG = "SubtitleParser"

    fun parse(content: String, format: SubtitleFormat = SubtitleFormat.UNKNOWN): List<SubtitleCue> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return emptyList()

        return when {
            format == SubtitleFormat.JSON || (trimmed.startsWith("{") && trimmed.contains("\"body\"")) -> {
                com.example.util.SubtitleTranslator.parseBilibiliSubtitleJson(trimmed)
            }
            format == SubtitleFormat.VTT || trimmed.startsWith("WEBVTT") -> {
                parseWebVtt(trimmed)
            }
            format == SubtitleFormat.ASS || format == SubtitleFormat.SSA || trimmed.contains("[Events]") || trimmed.contains("[Script Info]") -> {
                parseAssSsa(trimmed)
            }
            else -> {
                // Default fallback to SRT parser, if fails try WebVTT
                val srtCues = parseSrt(trimmed)
                if (srtCues.isNotEmpty()) srtCues else parseWebVtt(trimmed)
            }
        }
    }

    /**
     * Parses SubRip (.srt) subtitle format.
     * Example:
     * 1
     * 00:01:20,000 --> 00:01:23,500
     * Hello world
     */
    fun parseSrt(srtContent: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        try {
            val normalized = srtContent.replace("\r\n", "\n").replace("\r", "\n")
            val blocks = normalized.split("\n\n")

            val timePattern = Pattern.compile("(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})\\s*-->\\s*(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})")

            for (block in blocks) {
                val lines = block.trim().lines().filter { it.isNotBlank() }
                if (lines.isEmpty()) continue

                var timeLineIndex = -1
                var matcher: java.util.regex.Matcher? = null

                for (i in 0 until lines.size.coerceAtMost(3)) {
                    val m = timePattern.matcher(lines[i])
                    if (m.find()) {
                        timeLineIndex = i
                        matcher = m
                        break
                    }
                }

                if (timeLineIndex != -1 && matcher != null) {
                    val startSec = timeToSeconds(
                        matcher.group(1)?.toIntOrNull() ?: 0,
                        matcher.group(2)?.toIntOrNull() ?: 0,
                        matcher.group(3)?.toIntOrNull() ?: 0,
                        matcher.group(4)?.padEnd(3, '0')?.take(3)?.toIntOrNull() ?: 0
                    )
                    val endSec = timeToSeconds(
                        matcher.group(5)?.toIntOrNull() ?: 0,
                        matcher.group(6)?.toIntOrNull() ?: 0,
                        matcher.group(7)?.toIntOrNull() ?: 0,
                        matcher.group(8)?.padEnd(3, '0')?.take(3)?.toIntOrNull() ?: 0
                    )

                    val textLines = lines.drop(timeLineIndex + 1)
                    val text = textLines.joinToString(" ") { stripTags(it) }.trim()

                    if (text.isNotEmpty() && endSec > startSec) {
                        cues.add(SubtitleCue(fromSeconds = startSec, toSeconds = endSec, text = text))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse SRT: ${e.message}")
        }
        return cues
    }

    /**
     * Parses WebVTT (.vtt) format.
     */
    fun parseWebVtt(vttContent: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        try {
            val normalized = vttContent.replace("\r\n", "\n").replace("\r", "\n")
            val blocks = normalized.split("\n\n")

            // Pattern supports 00:00:00.000 or 00:00.000
            val timePattern = Pattern.compile("(?:(\\d{1,2}):)?(\\d{2}):(\\d{2})\\.(\\d{1,3})\\s*-->\\s*(?:(\\d{1,2}):)?(\\d{2}):(\\d{2})\\.(\\d{1,3})")

            for (block in blocks) {
                val lines = block.trim().lines().filter { it.isNotBlank() }
                if (lines.isEmpty()) continue
                if (lines[0].startsWith("WEBVTT") || lines[0].startsWith("NOTE")) continue

                var timeLineIndex = -1
                var matcher: java.util.regex.Matcher? = null

                for (i in 0 until lines.size.coerceAtMost(3)) {
                    val m = timePattern.matcher(lines[i])
                    if (m.find()) {
                        timeLineIndex = i
                        matcher = m
                        break
                    }
                }

                if (timeLineIndex != -1 && matcher != null) {
                    val h1 = matcher.group(1)?.toIntOrNull() ?: 0
                    val m1 = matcher.group(2)?.toIntOrNull() ?: 0
                    val s1 = matcher.group(3)?.toIntOrNull() ?: 0
                    val ms1 = matcher.group(4)?.padEnd(3, '0')?.take(3)?.toIntOrNull() ?: 0
                    val startSec = timeToSeconds(h1, m1, s1, ms1)

                    val h2 = matcher.group(5)?.toIntOrNull() ?: 0
                    val m2 = matcher.group(6)?.toIntOrNull() ?: 0
                    val s2 = matcher.group(7)?.toIntOrNull() ?: 0
                    val ms2 = matcher.group(8)?.padEnd(3, '0')?.take(3)?.toIntOrNull() ?: 0
                    val endSec = timeToSeconds(h2, m2, s2, ms2)

                    val textLines = lines.drop(timeLineIndex + 1)
                    val text = textLines.joinToString(" ") { stripTags(it) }.trim()

                    if (text.isNotEmpty() && endSec > startSec) {
                        cues.add(SubtitleCue(fromSeconds = startSec, toSeconds = endSec, text = text))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse WebVTT: ${e.message}")
        }
        return cues
    }

    /**
     * Parses Advanced SubStation Alpha (.ass / .ssa).
     * Example:
     * Dialogue: 0,0:01:20.00,0:01:23.50,Default,,0,0,0,,{\k20}Hello world
     */
    fun parseAssSsa(assContent: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        try {
            val lines = assContent.replace("\r\n", "\n").replace("\r", "\n").lines()
            var inEvents = false
            var formatIndices = mutableMapOf<String, Int>()

            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.startsWith("[Events]")) {
                    inEvents = true
                    continue
                }
                if (line.startsWith("[") && line.endsWith("]")) {
                    inEvents = false
                    continue
                }

                if (inEvents) {
                    if (line.startsWith("Format:")) {
                        val headerParts = line.substringAfter("Format:").split(",").map { it.trim().lowercase() }
                        formatIndices.clear()
                        headerParts.forEachIndexed { index, part -> formatIndices[part] = index }
                    } else if (line.startsWith("Dialogue:")) {
                        val dialogueContent = line.substringAfter("Dialogue:").trim()
                        val parts = dialogueContent.split(",", limit = if (formatIndices.isNotEmpty()) formatIndices.size else 10)

                        val startIdx = formatIndices["start"] ?: 1
                        val endIdx = formatIndices["end"] ?: 2
                        val textIdx = formatIndices["text"] ?: (parts.size - 1)

                        if (parts.size > endIdx && parts.size > textIdx) {
                            val startStr = parts[startIdx].trim()
                            val endStr = parts[endIdx].trim()
                            val rawText = parts[textIdx].trim()

                            val startSec = parseAssTimestamp(startStr)
                            val endSec = parseAssTimestamp(endStr)
                            val cleanText = stripAssTags(rawText).trim()

                            if (cleanText.isNotEmpty() && endSec > startSec) {
                                cues.add(SubtitleCue(fromSeconds = startSec, toSeconds = endSec, text = cleanText))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse ASS/SSA: ${e.message}")
        }
        return cues
    }

    private fun timeToSeconds(hours: Int, minutes: Int, seconds: Int, millis: Int): Float {
        return (hours * 3600f) + (minutes * 60f) + seconds.toFloat() + (millis / 1000f)
    }

    private fun parseAssTimestamp(timeStr: String): Float {
        return try {
            val parts = timeStr.split(":")
            if (parts.size == 3) {
                val h = parts[0].toIntOrNull() ?: 0
                val m = parts[1].toIntOrNull() ?: 0
                val secParts = parts[2].split(".")
                val s = secParts[0].toIntOrNull() ?: 0
                val ms = if (secParts.size > 1) secParts[1].padEnd(3, '0').take(3).toIntOrNull() ?: 0 else 0
                timeToSeconds(h, m, s, ms)
            } else 0f
        } catch (_: Exception) {
            0f
        }
    }

    private fun stripTags(text: String): String {
        return text
            .replace(Regex("<[^>]*>"), "")
            .replace(Regex("\\{[^}]*\\}"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
    }

    private fun stripAssTags(text: String): String {
        return text
            .replace(Regex("\\{[^}]*\\}"), "")
            .replace("\\N", " ")
            .replace("\\n", " ")
            .replace("\\h", " ")
            .trim()
    }
}
