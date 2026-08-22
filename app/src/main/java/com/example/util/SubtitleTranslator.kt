package com.example.util

import android.content.Context
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class SubtitleCue(
    val fromSeconds: Float,
    val toSeconds: Float,
    val text: String,
    val translatedText: String? = null
) {
    val fromMs: Long get() = (fromSeconds * 1000).toLong()
    val toMs: Long get() = (toSeconds * 1000).toLong()
}

object SubtitleTranslator {
    private const val TAG = "SubtitleTranslator"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // In-memory LRU cache for translations: "sourceText|targetLang" -> translatedText
    private val translationCache = LruCache<String, String>(2000)

    // Supported target languages
    val supportedLanguages = listOf(
        "en" to "English",
        "hi" to "Hindi (हिंदी)",
        "ja" to "Japanese (日本語)",
        "zh" to "Chinese (中文)",
        "ko" to "Korean (한국어)",
        "es" to "Spanish (Español)",
        "fr" to "French (Français)",
        "de" to "German (Deutsch)",
        "ru" to "Russian (Русский)",
        "ar" to "Arabic (العربية)",
        "pt" to "Portuguese (Português)",
        "id" to "Indonesian (Bahasa Indonesia)"
    )

    /**
     * Checks if a string contains Chinese, Japanese, or Korean characters.
     */
    fun containsCjk(text: String): Boolean {
        for (c in text) {
            val ub = Character.UnicodeBlock.of(c)
            if (ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
                ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                ub == Character.UnicodeBlock.HIRAGANA ||
                ub == Character.UnicodeBlock.KATAKANA ||
                ub == Character.UnicodeBlock.HANGUL_SYLLABLES
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Translates a single line of text synchronously (e.g. within an existing background worker).
     */
    fun translateTextSync(text: String, targetLang: String = "en", sourceLang: String = "auto"): String {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return text

        val cacheKey = "$cleanText|$targetLang"
        val cached = translationCache.get(cacheKey)
        if (cached != null) return cached

        // If target is en and text has no CJK/foreign chars and looks like English, return as-is
        if (targetLang == "en" && !containsCjk(cleanText) && cleanText.matches(Regex("^[a-zA-Z0-9\\s\\p{Punct}]+$"))) {
            translationCache.put(cacheKey, cleanText)
            return cleanText
        }

        try {
            val encoded = URLEncoder.encode(cleanText, "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sourceLang&tl=$targetLang&dt=t&q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val responseBody = httpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!responseBody.isNullOrBlank()) {
                val jsonArr = JSONArray(responseBody)
                val sentencesArr = jsonArr.optJSONArray(0)
                if (sentencesArr != null) {
                    val resultBuilder = StringBuilder()
                    for (i in 0 until sentencesArr.length()) {
                        val sentence = sentencesArr.optJSONArray(i)
                        val part = sentence?.optString(0, "") ?: ""
                        resultBuilder.append(part)
                    }
                    val translated = resultBuilder.toString().trim()
                    if (translated.isNotBlank()) {
                        translationCache.put(cacheKey, translated)
                        return translated
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sync translation failed for '$cleanText': ${e.message}")
        }

        return cleanText
    }

    /**
     * Translates a single line of text (e.g. video title or caption line).
     */
    suspend fun translateText(text: String, targetLang: String = "en", sourceLang: String = "auto"): String = withContext(Dispatchers.IO) {
        translateTextSync(text, targetLang, sourceLang)
    }

    /**
     * Translates a list of SubtitleCue items to target language.
     */
    suspend fun translateCues(cues: List<SubtitleCue>, targetLang: String = "en", sourceLang: String = "auto"): List<SubtitleCue> = withContext(Dispatchers.IO) {
        if (cues.isEmpty() || targetLang == sourceLang) return@withContext cues

        // Group short cues to batch translate where possible
        cues.map { cue ->
            val translated = translateText(cue.text, targetLang = targetLang, sourceLang = sourceLang)
            cue.copy(translatedText = translated)
        }
    }

    /**
     * Parses Bilibili's official JSON subtitle format:
     * { "body": [ { "from": 0.5, "to": 2.1, "content": "..." } ] }
     */
    fun parseBilibiliSubtitleJson(jsonStr: String): List<SubtitleCue> {
        val result = mutableListOf<SubtitleCue>()
        try {
            val json = org.json.JSONObject(jsonStr)
            val body = json.optJSONArray("body") ?: return result
            for (i in 0 until body.length()) {
                val item = body.optJSONObject(i) ?: continue
                val fromSec = item.optDouble("from", 0.0).toFloat()
                val toSec = item.optDouble("to", 0.0).toFloat()
                val content = item.optString("content", "").trim()
                if (content.isNotEmpty()) {
                    result.add(SubtitleCue(fromSeconds = fromSec, toSeconds = toSec, text = content))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing Bilibili subtitle JSON: ${e.message}")
        }
        return result
    }

    /**
     * Converts a list of SubtitleCue into standard WebVTT format string.
     */
    fun cuesToWebVtt(cues: List<SubtitleCue>, useTranslated: Boolean = false): String {
        val sb = StringBuilder()
        sb.append("WEBVTT\n\n")
        cues.forEachIndexed { index, cue ->
            val startText = formatVttTimestamp(cue.fromSeconds)
            val endText = formatVttTimestamp(cue.toSeconds)
            val textToUse = if (useTranslated && !cue.translatedText.isNullOrBlank()) {
                cue.translatedText
            } else {
                cue.text
            }
            sb.append("${index + 1}\n")
            sb.append("$startText --> $endText\n")
            sb.append("$textToUse\n\n")
        }
        return sb.toString()
    }

    private fun formatVttTimestamp(seconds: Float): String {
        val totalMs = (seconds * 1000).toLong()
        val hrs = totalMs / 3600000
        val mins = (totalMs % 3600000) / 60000
        val secs = (totalMs % 60000) / 1000
        val ms = totalMs % 1000
        return String.format("%02d:%02d:%02d.%03d", hrs, mins, secs, ms)
    }
}
