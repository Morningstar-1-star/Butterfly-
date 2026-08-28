package com.example.extractor.chapters

import android.util.Log
import java.util.regex.Pattern

/**
 * Custom Chapter & Timestamp Segment Parser.
 * Adapted from bashonly/yt-dlp-YTCustomChapters.
 * Extracts video timestamps, chapters, and cues from video descriptions, comments, or metadata.
 */
data class VideoChapter(
    val title: String,
    val startTimeMs: Long,
    val endTimeMs: Long = 0L
)

object YTCustomChapters {
    private const val TAG = "YTCustomChapters"

    // Matches timestamps like "01:23", "1:02:30", "(02:45)", "[1:23:45]"
    private val TIMESTAMP_PATTERN = Pattern.compile(
        """(?:^|\s)[(\[]?(\d{1,2}:(?:\d{2}:)?\d{2})[)\]]?\s*[-–—:]?\s*(.+)""",
        Pattern.MULTILINE
    )

    /**
     * Parses chapters from video description text or comments.
     */
    fun extractChapters(description: String, durationMs: Long = 0L): List<VideoChapter> {
        if (description.isBlank()) return emptyList()

        val chapters = mutableListOf<VideoChapter>()
        val matcher = TIMESTAMP_PATTERN.matcher(description)

        while (matcher.find()) {
            val timeStr = matcher.group(1)?.trim() ?: continue
            val title = matcher.group(2)?.trim()?.take(100) ?: continue

            val timeMs = parseTimestampToMs(timeStr)
            if (timeMs >= 0) {
                chapters.add(VideoChapter(title = title, startTimeMs = timeMs))
            }
        }

        // Sort by start time
        val sorted = chapters.sortedBy { it.startTimeMs }
        if (sorted.isEmpty()) return emptyList()

        // Populate end times
        val finalized = mutableListOf<VideoChapter>()
        for (i in sorted.indices) {
            val current = sorted[i]
            val nextStart = if (i + 1 < sorted.size) sorted[i + 1].startTimeMs else durationMs
            val effectiveEnd = if (nextStart > current.startTimeMs) nextStart else current.startTimeMs + 60_000L
            finalized.add(current.copy(endTimeMs = effectiveEnd))
        }

        Log.d(TAG, "Extracted ${finalized.size} custom chapters from description")
        return finalized
    }

    private fun parseTimestampToMs(timeStr: String): Long {
        val parts = timeStr.split(":")
        return try {
            when (parts.size) {
                2 -> { // MM:SS
                    val mins = parts[0].toLong()
                    val secs = parts[1].toLong()
                    (mins * 60 + secs) * 1000L
                }
                3 -> { // HH:MM:SS
                    val hours = parts[0].toLong()
                    val mins = parts[1].toLong()
                    val secs = parts[2].toLong()
                    (hours * 3600 + mins * 60 + secs) * 1000L
                }
                else -> -1L
            }
        } catch (_: Exception) {
            -1L
        }
    }
}
