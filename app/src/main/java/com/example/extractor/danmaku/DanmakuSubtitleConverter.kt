package com.example.extractor.danmaku

import java.util.Locale

/**
 * Converts timed Danmaku comments to ASS (Advanced SubStation Alpha) / SRT subtitle format.
 * Adapted from UlyssesZh/yt-dlp-danmaku.
 */
object DanmakuSubtitleConverter {

    /**
     * Converts Danmaku comments into an ASS subtitle file format suitable for Media3 / ExoPlayer ASS decoders.
     */
    fun convertToAss(comments: List<DanmakuComment>, title: String = "Danmaku"): String {
        val sb = StringBuilder()

        // Header
        sb.appendLine("[Script Info]")
        sb.appendLine("Title: $title")
        sb.appendLine("ScriptType: v4.00+")
        sb.appendLine("Collisions: Normal")
        sb.appendLine("PlayResX: 1920")
        sb.appendLine("PlayResY: 1080")
        sb.appendLine("WrapStyle: 2")
        sb.appendLine()

        // Styles
        sb.appendLine("[V4+ Styles]")
        sb.appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")
        sb.appendLine("Style: DanmakuScroll,sans-serif,38,&H00FFFFFF,&H00FFFFFF,&H00000000,&H00000000,1,0,0,0,100,100,0,0,1,2,0,2,20,20,20,1")
        sb.appendLine("Style: DanmakuTop,sans-serif,38,&H00FFFFFF,&H00FFFFFF,&H00000000,&H00000000,1,0,0,0,100,100,0,0,1,2,0,8,20,20,50,1")
        sb.appendLine("Style: DanmakuBottom,sans-serif,38,&H00FFFFFF,&H00FFFFFF,&H00000000,&H00000000,1,0,0,0,100,100,0,0,1,2,0,2,20,20,80,1")
        sb.appendLine()

        // Events
        sb.appendLine("[Events]")
        sb.appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")

        for (c in comments) {
            val startMs = (c.timeOffsetSec * 1000).toLong()
            val durationMs = when (c.mode) {
                4, 5 -> 4000L // Top/bottom comments stay 4 seconds
                else -> 6000L // Scrolling comments stay 6 seconds
            }
            val endMs = startMs + durationMs

            val style = when (c.mode) {
                5 -> "DanmakuTop"
                4 -> "DanmakuBottom"
                else -> "DanmakuScroll"
            }

            val startStr = formatAssTime(startMs)
            val endStr = formatAssTime(endMs)
            val sanitizedText = c.text.replace("\n", " ").replace("\\", "\\\\")

            sb.appendLine("Dialogue: 0,$startStr,$endStr,$style,,0,0,0,,{\\c${formatAssColor(c.color)}}$sanitizedText")
        }

        return sb.toString()
    }

    private fun formatAssTime(millis: Long): String {
        val hrs = millis / 3600000
        val mins = (millis % 3600000) / 60000
        val secs = (millis % 60000) / 1000
        val cs = (millis % 1000) / 10
        return String.format(Locale.US, "%d:%02d:%02d.%02d", hrs, mins, secs, cs)
    }

    private fun formatAssColor(decimalColor: Long): String {
        val r = (decimalColor shr 16) and 0xFF
        val g = (decimalColor shr 8) and 0xFF
        val b = decimalColor and 0xFF
        // ASS format is &HBBGGRR&
        return String.format(Locale.US, "&H%02X%02X%02X&", b, g, r)
    }
}
