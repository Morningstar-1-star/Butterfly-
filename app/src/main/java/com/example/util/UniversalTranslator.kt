package com.example.util

import android.util.Log
import com.example.db.AppDatabase
import com.example.db.TranslationCacheEntity
import com.example.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class TranslationResult(
    val originalText: String,
    val translatedEN: String,
    val translatedHI: String,
    val detectedLanguage: String,
    val confidence: Float = 1.0f
)

object UniversalTranslator {
    private const val TAG = "UniversalTranslator"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // In-memory LRU / concurrent cache for fast UI access
    private val memoryCache = ConcurrentHashMap<String, TranslationResult>()

    // Protected proper nouns, studio names, anime tags and established technical terms
    private val PROTECTED_TERMS = listOf(
        "Sony", "Marvel", "Disney", "Netflix", "Warner Bros", "Universal", "Paramount", "Toei Animation",
        "MAPPA", "Kyoto Animation", "Madhouse", "Bones", "Ufotable", "A-1 Pictures", "Wit Studio",
        "CloverWorks", "Aniplex", "Bandai", "Crunchyroll", "Funimation", "Bilibili", "YouTube",
        "Goku", "Vegeta", "Naruto", "Sasuke", "Luffy", "Zoro", "Ichigo", "Tanjiro", "Nezuko", "Gojo",
        "Eren", "Levi", "Deku", "Bakugo", "Saitama", "Sukuna", "Boruto", "Kakashi",
        "4K", "1080p", "720p", "60fps", "HDR", "Dolby Vision", "Atmos", "OST", "MV", "AMV",
        "OP", "ED", "Trailer", "Teaser", "Official", "Live", "Remix", "Cover", "Episode", "Season",
        "BluRay", "WEBRip", "HDTV", "DVDRip", "HEVC", "x264", "x265", "AAC", "FLAC"
    )

    /**
     * Detects primary language of the text.
     * Returns ISO 639-1 code: "ja", "zh", "ko", "ar", "ru", "hi", "es", "fr", "de", "en", etc.
     */
    fun detectLanguage(text: String): String {
        if (text.isBlank()) return "en"

        var cjkCount = 0
        var hiraganaKatakanaCount = 0
        var hangulCount = 0
        var arabicCount = 0
        var cyrillicCount = 0
        var devanagariCount = 0
        var latinCount = 0

        for (cp in text.codePoints()) {
            val block = Character.UnicodeBlock.of(cp)
            when {
                block == Character.UnicodeBlock.HIRAGANA ||
                block == Character.UnicodeBlock.KATAKANA ||
                block == Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS -> {
                    hiraganaKatakanaCount++
                }
                block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
                block == Character.UnicodeBlock.HANGUL_JAMO ||
                block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO -> {
                    hangulCount++
                }
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
                block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS -> {
                    cjkCount++
                }
                block == Character.UnicodeBlock.ARABIC ||
                block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A ||
                block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B ||
                block == Character.UnicodeBlock.ARABIC_SUPPLEMENT -> {
                    arabicCount++
                }
                block == Character.UnicodeBlock.CYRILLIC ||
                block == Character.UnicodeBlock.CYRILLIC_SUPPLEMENTARY -> {
                    cyrillicCount++
                }
                block == Character.UnicodeBlock.DEVANAGARI ||
                block == Character.UnicodeBlock.DEVANAGARI_EXTENDED -> {
                    devanagariCount++
                }
                block == Character.UnicodeBlock.BASIC_LATIN ||
                block == Character.UnicodeBlock.LATIN_1_SUPPLEMENT ||
                block == Character.UnicodeBlock.LATIN_EXTENDED_A -> {
                    if (Character.isLetter(cp)) latinCount++
                }
            }
        }

        // Japanese check (presence of Kana is definitive for Japanese even if Kanji exists)
        if (hiraganaKatakanaCount > 0) return "ja"
        if (hangulCount > 0) return "ko"
        if (cjkCount > 1) return "zh"
        if (arabicCount > 1) return "ar"
        if (cyrillicCount > 2) return "ru"
        if (devanagariCount > 2) return "hi"

        // European language keyword heuristics for Latin script
        val lower = text.lowercase()
        if (lower.contains(" temporada") || lower.contains(" película") || lower.contains(" capítulo") || lower.contains(" tráiler") || lower.contains(" castellano") || lower.contains(" español")) return "es"
        if (lower.contains(" épisode") || lower.contains(" film complet") || lower.contains(" bande annonce") || lower.contains(" saison ")) return "fr"
        if (lower.contains(" staffel") || lower.contains(" ganzer film") || lower.contains(" folge ")) return "de"
        if (lower.contains(" dublado") || lower.contains(" legendado") || lower.contains(" completo ")) return "pt"
        if (lower.contains(" bölum") || lower.contains(" fragman") || lower.contains(" dizi ")) return "tr"
        if (lower.contains(" vietsub") || lower.contains(" thuyet minh") || lower.contains(" tập ")) return "vi"
        if (lower.contains("ตอนที่") || lower.contains("พากย์ไทย")) return "th"

        return "en"
    }

    /**
     * Gets user-friendly display name for language code.
     */
    fun getLanguageDisplayName(code: String): String {
        return when (code.lowercase()) {
            "en" -> "English"
            "hi" -> "हिंदी (Hindi)"
            "ja" -> "日本語 (Japanese)"
            "zh" -> "中文 (Chinese)"
            "ko" -> "한국어 (Korean)"
            "ar" -> "العربية (Arabic)"
            "ru" -> "Русский (Russian)"
            "es" -> "Español (Spanish)"
            "fr" -> "Français (French)"
            "de" -> "Deutsch (German)"
            "pt" -> "Português"
            "it" -> "Italiano"
            "tr" -> "Türkçe"
            "vi" -> "Tiếng Việt"
            "th" -> "ไทย (Thai)"
            "id" -> "Bahasa Indonesia"
            else -> code.uppercase()
        }
    }

    /**
     * Translates a single VideoItem if needed and returns an updated copy with translation fields.
     */
    suspend fun translateVideoItem(item: VideoItem, database: AppDatabase? = null): VideoItem {
        val originalTitle = item.title
        val detected = item.detectedLanguage ?: detectLanguage(originalTitle)

        // If English or Hindi, no translation needed
        if (detected == "en" || detected == "hi") {
            return item.copy(
                originalTitle = originalTitle,
                translatedTitleEN = originalTitle,
                translatedTitleHI = null,
                detectedLanguage = detected
            )
        }

        // If already translated and non-empty, reuse
        if (!item.translatedTitleEN.isNullOrBlank()) {
            return item
        }

        val result = translateTitle(originalTitle, database)

        return item.copy(
            originalTitle = originalTitle,
            translatedTitleEN = result.translatedEN,
            translatedTitleHI = null,
            detectedLanguage = result.detectedLanguage
        )
    }

    /**
     * Translates title to English if needed.
     * Guaranteed safe fallback: if title is in English or Hindi, returns original unchanged.
     */
    suspend fun translateTitle(title: String, database: AppDatabase? = null): TranslationResult {
        if (title.isBlank()) {
            return TranslationResult(title, title, "", "en", 1.0f)
        }

        val detectedLang = detectLanguage(title)

        // If title is already English or Hindi, no translation needed!
        if (detectedLang == "en" || detectedLang == "hi") {
            return TranslationResult(
                originalText = title,
                translatedEN = title,
                translatedHI = "",
                detectedLanguage = detectedLang,
                confidence = 1.0f
            )
        }

        // Check memory cache
        memoryCache[title]?.let { return it }

        // Check database cache for English translation
        if (database != null) {
            try {
                val dbCachedEn = withContext(Dispatchers.IO) {
                    database.translationCacheDao().getTranslation(title, "en")
                }
                if (dbCachedEn != null) {
                    val res = TranslationResult(
                        originalText = title,
                        translatedEN = dbCachedEn.translatedText,
                        translatedHI = "",
                        detectedLanguage = dbCachedEn.detectedLanguage ?: detectedLang,
                        confidence = dbCachedEn.confidence
                    )
                    memoryCache[title] = res
                    return res
                }
            } catch (e: Exception) {
                Log.w(TAG, "DB translation cache read error: ${e.message}")
            }
        }

        // Foreign language (e.g. Japanese, Korean, Chinese, Spanish, French, etc.): translate to English
        val translatedEN = fetchTranslationApi(title, sourceLang = detectedLang, targetLang = "en") ?: title

        val confidence = if (translatedEN == title) 0.6f else 0.98f
        val result = TranslationResult(
            originalText = title,
            translatedEN = cleanTranslation(translatedEN, title),
            translatedHI = "",
            detectedLanguage = detectedLang,
            confidence = confidence
        )

        memoryCache[title] = result

        // Persist English translation to database cache asynchronously
        if (database != null) {
            withContext(Dispatchers.IO) {
                try {
                    database.translationCacheDao().insertTranslation(
                        TranslationCacheEntity(
                            sourceText = title,
                            targetLang = "en",
                            translatedText = result.translatedEN,
                            detectedLanguage = result.detectedLanguage,
                            confidence = result.confidence,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "DB translation cache write error: ${e.message}")
                }
            }
        }

        return result
    }

    /**
     * Translates a long description text into EN and HI.
     */
    suspend fun translateDescription(
        description: String,
        database: AppDatabase? = null
    ): Pair<String?, String?> {
        if (description.isBlank()) return Pair(null, null)

        val detected = detectLanguage(description.take(200))
        if (detected == "en") {
            val hiDesc = fetchTranslationApi(description.take(1000), sourceLang = "en", targetLang = "hi")
            return Pair(description, hiDesc)
        }

        val enDesc = fetchTranslationApi(description.take(1000), sourceLang = detected, targetLang = "en")
        val hiDesc = fetchTranslationApi(description.take(1000), sourceLang = detected, targetLang = "hi")
        return Pair(enDesc ?: description, hiDesc ?: description)
    }

    /**
     * Calls high-speed lightweight translation endpoints with protection for proper nouns and fallback.
     */
    private suspend fun fetchTranslationApi(
        text: String,
        sourceLang: String,
        targetLang: String
    ): String? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext text

        try {
            // 1. Google Translate Public Fast Single-Hop API
            val encoded = URLEncoder.encode(text, "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sourceLang&tl=$targetLang&dt=t&q=$encoded"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string()
                if (!bodyStr.isNullOrBlank()) {
                    val jsonArray = JSONArray(bodyStr)
                    val sentences = jsonArray.optJSONArray(0)
                    if (sentences != null && sentences.length() > 0) {
                        val sb = StringBuilder()
                        for (i in 0 until sentences.length()) {
                            val part = sentences.optJSONArray(i)
                            if (part != null) {
                                sb.append(part.optString(0, ""))
                            }
                        }
                        val result = sb.toString().trim()
                        if (result.isNotBlank()) {
                            return@withContext result
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Translation API primary attempt error: ${e.message}")
        }

        // 2. Secondary fallback via Lingva API mirror
        try {
            val encoded = URLEncoder.encode(text, "UTF-8")
            val url = "https://lingva.ml/api/v1/$sourceLang/$targetLang/$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string()
                if (!bodyStr.isNullOrBlank()) {
                    val jsonObj = org.json.JSONObject(bodyStr)
                    val translation = jsonObj.optString("translation")
                    if (translation.isNotBlank()) {
                        return@withContext translation
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Translation API secondary attempt error: ${e.message}")
        }

        return@withContext null
    }

    /**
     * Cleans up translation string, protects proper names, studios and established terms from mistranslation.
     */
    private fun cleanTranslation(translated: String, original: String): String {
        var clean = translated.trim()
        if (clean.isBlank()) return original

        // Restore protected brand/studio names if they were altered
        for (term in PROTECTED_TERMS) {
            if (original.contains(term, ignoreCase = true) && !clean.contains(term, ignoreCase = true)) {
                // If the translation lost the proper noun, preserve it
                clean = "$clean ($term)"
            }
        }

        return clean
    }
}
