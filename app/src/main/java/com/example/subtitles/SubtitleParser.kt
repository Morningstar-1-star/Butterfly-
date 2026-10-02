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
            trimmed.startsWith("{") && (trimmed.contains("\"events\"") || trimmed.contains("\"body\"") || trimmed.contains("\"transcript\"")) -> {
                if (trimmed.contains("\"events\"")) {
                    val j3 = parseJson3(trimmed)
                    if (j3.isNotEmpty()) j3 else com.example.util.SubtitleTranslator.parseBilibiliSubtitleJson(trimmed)
                } else {
                    com.example.util.SubtitleTranslator.parseBilibiliSubtitleJson(trimmed)
                }
            }
            trimmed.startsWith("<") && (trimmed.contains("<text") || trimmed.contains("<p") || trimmed.contains("<transcript") || trimmed.contains("<tt") || trimmed.contains("<timedtext")) -> {
                parseXmlTimedText(trimmed)
            }
            trimmed.startsWith("WEBVTT") || (trimmed.contains("-->") && !trimmed.contains("<text")) -> {
                val cues = parseWebVtt(trimmed)
                if (cues.isNotEmpty()) cues else parseXmlTimedText(trimmed).ifEmpty { parseSrt(trimmed) }
            }
            trimmed.contains("[Events]") || trimmed.contains("[Script Info]") -> {
                val cues = parseAssSsa(trimmed)
                if (cues.isNotEmpty()) cues else parseSrt(trimmed)
            }
            format == SubtitleFormat.JSON -> {
                val j3 = parseJson3(trimmed)
                if (j3.isNotEmpty()) j3 else com.example.util.SubtitleTranslator.parseBilibiliSubtitleJson(trimmed)
            }
            format == SubtitleFormat.VTT -> {
                val cues = parseWebVtt(trimmed)
                if (cues.isNotEmpty()) cues else parseXmlTimedText(trimmed).ifEmpty { parseSrt(trimmed) }
            }
            format == SubtitleFormat.ASS || format == SubtitleFormat.SSA -> {
                val cues = parseAssSsa(trimmed)
                if (cues.isNotEmpty()) cues else parseSrt(trimmed)
            }
            else -> {
                // Default fallback sequence: WebVTT -> JSON3 -> XML -> SRT
                val vttCues = if (trimmed.contains("-->")) parseWebVtt(trimmed) else emptyList()
                if (vttCues.isNotEmpty()) vttCues else {
                    val j3 = if (trimmed.startsWith("{")) parseJson3(trimmed) else emptyList()
                    if (j3.isNotEmpty()) j3 else {
                        val xmlCues = if (trimmed.contains("<")) parseXmlTimedText(trimmed) else emptyList()
                        if (xmlCues.isNotEmpty()) xmlCues else parseSrt(trimmed)
                    }
                }
            }
        }
    }

    /**
     * Parses YouTube TimedText XML and TTML subtitle format.
     * Example:
     * <text start="1.234" dur="2.5">Hello world</text>
     * or
     * <p begin="00:00:01.234" end="00:00:03.734">Hello world</p>
     */
    fun parseXmlTimedText(xmlContent: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        try {
            // Match <text start="..." dur="...">text</text>
            val textPattern = Pattern.compile("<text[^>]*start=[\"']([0-9.]+)[\"'][^>]*dur=[\"']([0-9.]+)[\"'][^>]*>(.*?)</text>", Pattern.DOTALL)
            var matcher = textPattern.matcher(xmlContent)
            while (matcher.find()) {
                val start = matcher.group(1)?.toFloatOrNull() ?: 0f
                val dur = matcher.group(2)?.toFloatOrNull() ?: 2.5f
                val rawText = matcher.group(3) ?: ""
                val clean = stripTags(rawText).trim()
                if (clean.isNotBlank()) {
                    cues.add(SubtitleCue(fromSeconds = start, toSeconds = start + dur, text = clean))
                }
            }

            if (cues.isEmpty()) {
                // Try alternate format: <text start="..." ...>text</text> without dur
                val simplePattern = Pattern.compile("<text[^>]*start=[\"']([0-9.]+)[\"'][^>]*>(.*?)</text>", Pattern.DOTALL)
                matcher = simplePattern.matcher(xmlContent)
                var prevStart = -1f
                var prevText = ""
                while (matcher.find()) {
                    val start = matcher.group(1)?.toFloatOrNull() ?: 0f
                    val rawText = matcher.group(2) ?: ""
                    val clean = stripTags(rawText).trim()
                    if (clean.isNotBlank()) {
                        if (prevStart >= 0f) {
                            val dur = (start - prevStart).coerceIn(1.0f, 6.0f)
                            cues.add(SubtitleCue(fromSeconds = prevStart, toSeconds = prevStart + dur, text = prevText))
                        }
                        prevStart = start
                        prevText = clean
                    }
                }
                if (prevStart >= 0f && prevText.isNotBlank()) {
                    cues.add(SubtitleCue(fromSeconds = prevStart, toSeconds = prevStart + 3.0f, text = prevText))
                }
            }

            if (cues.isEmpty()) {
                // Try YouTube XML format 3: <p t="1234" d="2500"><s>Hello</s></p>
                val pPattern = Pattern.compile("<p[^>]*t=[\"']([0-9]+)[\"'][^>]*d=[\"']([0-9]+)[\"'][^>]*>(.*?)</p>", Pattern.DOTALL)
                matcher = pPattern.matcher(xmlContent)
                while (matcher.find()) {
                    val tMs = matcher.group(1)?.toLongOrNull() ?: 0L
                    val dMs = matcher.group(2)?.toLongOrNull() ?: 2500L
                    val rawText = matcher.group(3) ?: ""
                    val clean = stripTags(rawText).trim()
                    if (clean.isNotBlank()) {
                        val startSec = tMs / 1000f
                        val durSec = (dMs / 1000f).coerceAtLeast(0.5f)
                        cues.add(SubtitleCue(fromSeconds = startSec, toSeconds = startSec + durSec, text = clean))
                    }
                }
            }

            if (cues.isEmpty()) {
                // Try TTML: <p begin="..." end="...">text</p>
                val ttmlPattern = Pattern.compile("<p[^>]*begin=[\"']([^\"']+)[\"'][^>]*end=[\"']([^\"']+)[\"'][^>]*>(.*?)</p>", Pattern.DOTALL)
                matcher = ttmlPattern.matcher(xmlContent)
                while (matcher.find()) {
                    val bStr = matcher.group(1) ?: ""
                    val eStr = matcher.group(2) ?: ""
                    val rawText = matcher.group(3) ?: ""
                    val start = parseTimestampFlexible(bStr)
                    val end = parseTimestampFlexible(eStr)
                    val clean = stripTags(rawText).trim()
                    if (clean.isNotBlank() && end > start) {
                        cues.add(SubtitleCue(fromSeconds = start, toSeconds = end, text = clean))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse XML timedtext: ${e.message}")
        }
        return cues
    }

    /**
     * Parses YouTube JSON3 format:
     * { "events": [ { "tStartMs": 1200, "dDurationMs": 2500, "segs": [ { "utf8": "..." } ] } ] }
     */
    fun parseJson3(json: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        try {
            val root = org.json.JSONObject(json)
            val events = root.optJSONArray("events") ?: return emptyList()
            for (i in 0 until events.length()) {
                val event = events.getJSONObject(i)
                val tStartMs = event.optLong("tStartMs", -1L)
                if (tStartMs < 0) continue
                val dDurationMs = event.optLong("dDurationMs", 2500L).coerceAtLeast(500L)
                val segs = event.optJSONArray("segs")
                val textBuilder = StringBuilder()
                if (segs != null) {
                    for (j in 0 until segs.length()) {
                        val seg = segs.getJSONObject(j)
                        textBuilder.append(seg.optString("utf8", ""))
                    }
                } else {
                    textBuilder.append(event.optString("utf8", ""))
                }
                val raw = textBuilder.toString().replace("\n", " ").trim()
                val clean = stripTags(raw).trim()
                if (clean.isNotBlank()) {
                    cues.add(
                        SubtitleCue(
                            fromSeconds = tStartMs / 1000f,
                            toSeconds = (tStartMs + dDurationMs) / 1000f,
                            text = clean
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing json3: ${e.message}")
        }
        return cues
    }

    private fun parseTimestampFlexible(ts: String): Float {
        val trimmed = ts.trim()
        val direct = trimmed.toFloatOrNull()
        if (direct != null) return direct
        return parseAssTimestamp(trimmed)
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
