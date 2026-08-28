package com.example.extractor.danmaku

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import java.util.zip.InflaterInputStream

/**
 * Danmaku Comment model representing on-screen timed comments.
 */
data class DanmakuComment(
    val timeOffsetSec: Double,
    val mode: Int, // 1: Scroll, 4: Bottom, 5: Top
    val fontSize: Int,
    val color: Long,
    val timestamp: Long,
    val text: String
)

/**
 * Bilibili & Live Danmaku Extractor.
 * Adapted from UlyssesZh/yt-dlp-danmaku.
 * Fetches and parses XML/deflate danmaku streams.
 */
class DanmakuExtractor(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val TAG = "DanmakuExtractor"
    }

    /**
     * Fetches danmaku XML for a given Bilibili CID.
     */
    fun fetchDanmakuByCid(cid: Long): List<DanmakuComment> {
        val url = "https://comment.bilibili.com/$cid.xml"
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Accept-Encoding", "gzip, deflate")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return emptyList()

            val bytes = resp.body?.bytes() ?: return emptyList()
            val xmlString = try {
                // Try decompressing deflate if needed
                InflaterInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8).readText()
            } catch (_: Exception) {
                String(bytes, Charsets.UTF_8)
            }

            return parseXml(xmlString)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch danmaku for cid $cid: ${e.message}")
            return emptyList()
        }
    }

    /**
     * Parses Bilibili XML comment nodes into DanmakuComment objects.
     */
    fun parseXml(xmlContent: String): List<DanmakuComment> {
        if (xmlContent.isBlank()) return emptyList()

        val comments = mutableListOf<DanmakuComment>()
        try {
            val doc = Jsoup.parse(xmlContent, "", Parser.xmlParser())
            val dElements = doc.select("d")

            for (el in dElements) {
                val pAttr = el.attr("p")
                val text = el.text()
                if (pAttr.isBlank() || text.isBlank()) continue

                val parts = pAttr.split(",")
                if (parts.size >= 5) {
                    val timeOffset = parts[0].toDoubleOrNull() ?: 0.0
                    val mode = parts[1].toIntOrNull() ?: 1
                    val fontSize = parts[2].toIntOrNull() ?: 25
                    val color = parts[3].toLongOrNull() ?: 16777215L
                    val timestamp = parts[4].toLongOrNull() ?: 0L

                    comments.add(
                        DanmakuComment(
                            timeOffsetSec = timeOffset,
                            mode = mode,
                            fontSize = fontSize,
                            color = color,
                            timestamp = timestamp,
                            text = text
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing danmaku XML: ${e.message}")
        }

        return comments.sortedBy { it.timeOffsetSec }
    }
}
