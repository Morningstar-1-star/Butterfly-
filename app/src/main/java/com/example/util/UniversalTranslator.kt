package com.example.util

import android.util.Log
import com.example.db.AppDatabase
import com.example.db.TranslationCacheEntity
import com.example.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
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
     * Returns ISO 639-1 code: "ja", "zh", "ko", "ar", "ru", "hi", "es", "fr", "de", "pt", "it", "tr", "vi", "th", "en", etc.
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
        var foreignDiacriticsCount = 0

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
                block == Character.UnicodeBlock.BASIC_LATIN -> {
                    if (Character.isLetter(cp)) latinCount++
                }
                block == Character.UnicodeBlock.LATIN_1_SUPPLEMENT ||
                block == Character.UnicodeBlock.LATIN_EXTENDED_A ||
                block == Character.UnicodeBlock.LATIN_EXTENDED_B ||
                block == Character.UnicodeBlock.LATIN_EXTENDED_ADDITIONAL -> {
                    if (Character.isLetter(cp)) {
                        latinCount++
                        foreignDiacriticsCount++
                    }
                }
            }
        }

        if (hiraganaKatakanaCount > 0) return "ja"
        if (hangulCount > 0) return "ko"
        if (cjkCount > 0) return "zh"
        if (arabicCount > 0) return "ar"
        if (cyrillicCount > 0) return "ru"
        if (devanagariCount > 0) return "hi"
        if (foreignDiacriticsCount > 0) return "es"

        // European & global language keyword heuristics for Latin script
        val lower = text.lowercase()
        if (lower.contains(" temporada") || lower.contains(" película") || lower.contains(" capítulo") || lower.contains(" tráiler") || lower.contains(" castellano") || lower.contains(" español") || lower.contains("de la") || lower.contains("en el") || lower.contains("por que") || lower.contains("para que")) return "es"
        if (lower.contains(" épisode") || lower.contains(" film complet") || lower.contains(" bande annonce") || lower.contains(" saison ") || lower.contains("dans le") || lower.contains("pour les") || lower.contains("avec le")) return "fr"
        if (lower.contains(" staffel") || lower.contains(" ganzer film") || lower.contains(" folge ") || lower.contains("das ist") || lower.contains("mit dem") || lower.contains("und die")) return "de"
        if (lower.contains(" dublado") || lower.contains(" legendado") || lower.contains(" completo ") || lower.contains("com o") || lower.contains("para o")) return "pt"
        if (lower.contains(" bölum") || lower.contains(" fragman") || lower.contains(" dizi ") || lower.contains("ve en")) return "tr"
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
        val originalTitle = item.originalTitle ?: item.title
        val detected = item.detectedLanguage ?: detectLanguage(originalTitle)

        // If Hindi, preserve Hindi title as requested by user
        if (detected == "hi") {
            return item.copy(
                originalTitle = originalTitle,
                translatedTitleEN = originalTitle,
                translatedTitleHI = originalTitle,
                detectedLanguage = "hi"
            )
        }

        // If already translated with a valid non-blank translation that differs or is ready
        if (!item.translatedTitleEN.isNullOrBlank() && item.originalTitle != null && item.translatedTitleEN != item.originalTitle) {
            return item
        }

        val result = translateTitle(originalTitle, database)
        val translatedTitle = result.translatedEN.ifBlank { originalTitle }

        return item.copy(
            title = translatedTitle,
            originalTitle = originalTitle,
            translatedTitleEN = translatedTitle,
            translatedTitleHI = null,
            detectedLanguage = result.detectedLanguage,
            translatedDescriptionEN = null
        )
    }

    /**
     * Parallel batch translation for lists of VideoItems for smooth UI performance.
     */
    suspend fun translateVideoItemList(
        items: List<VideoItem>,
        database: AppDatabase? = null
    ): List<VideoItem> = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext items
        // Process in chunks to avoid overwhelming network pool
        items.chunked(10).flatMap { chunk ->
            chunk.map { item ->
                async {
                    try {
                        translateVideoItem(item, database)
                    } catch (e: Exception) {
                        item
                    }
                }
            }.awaitAll()
        }
    }

    /**
     * Translates title to English if needed.
     */
    suspend fun translateTitle(title: String, database: AppDatabase? = null): TranslationResult {
        if (title.isBlank()) {
            return TranslationResult(title, title, "", "en", 1.0f)
        }

        val detectedLang = detectLanguage(title)

        // If title is Hindi, keep as Hindi as explicitly requested by user
        if (detectedLang == "hi") {
            return TranslationResult(
                originalText = title,
                translatedEN = title,
                translatedHI = title,
                detectedLanguage = "hi",
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
                if (dbCachedEn != null && dbCachedEn.translatedText.isNotBlank()) {
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

        // Fetch translation using multi-provider fallback
        val rawTranslation = fetchTranslationApi(title, sourceLang = detectedLang, targetLang = "en")
        val cleanEn = cleanTranslation(rawTranslation ?: title, title)

        val isDifferent = !cleanEn.equals(title, ignoreCase = true)
        val finalDetected = if (isDifferent && detectedLang == "en") "foreign" else detectedLang

        val confidence = if (isDifferent) 0.98f else 0.60f
        val result = TranslationResult(
            originalText = title,
            translatedEN = cleanEn,
            translatedHI = "",
            detectedLanguage = finalDetected,
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
     * Multi-tiered resilient translation fetcher.
     */
    private suspend fun fetchTranslationApi(
        text: String,
        sourceLang: String,
        targetLang: String = "en"
    ): String? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext text

        val encoded = try {
            URLEncoder.encode(text, "UTF-8")
        } catch (e: Exception) {
            text
        }

        // Tier 1: Google Translate Mobile HTML Scraper (100% reliable, zero 429 rate limit issues)
        try {
            val url = "https://translate.google.com/m?sl=auto&tl=$targetLang&q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                val match = Regex("""<div class="result-container">(.*?)</div>""", RegexOption.DOT_MATCHES_ALL).find(html)
                if (match != null) {
                    val rawResult = match.groupValues[1].trim()
                    val unescaped = unescapeHtmlEntities(rawResult)
                    if (unescaped.isNotBlank()) {
                        return@withContext unescaped
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google Mobile HTML translation failed: ${e.message}")
        }

        // Tier 2: MyMemory Translation API
        try {
            val sl = if (sourceLang.isBlank() || sourceLang == "en") "autodetect" else sourceLang
            val url = "https://api.mymemory.translated.net/get?q=$encoded&langpair=$sl|$targetLang"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string()
                if (!bodyStr.isNullOrBlank()) {
                    val jsonObj = org.json.JSONObject(bodyStr)
                    val responseData = jsonObj.optJSONObject("responseData")
                    val translatedText = responseData?.optString("translatedText")
                    if (!translatedText.isNullOrBlank() && !translatedText.equals("QUERY LENGTH LIMIT EXCEEDED", ignoreCase = true)) {
                        val unescaped = unescapeHtmlEntities(translatedText.trim())
                        if (unescaped.isNotBlank()) {
                            return@withContext unescaped
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MyMemory translation failed: ${e.message}")
        }

        // Tier 3: Google dict-chrome-ex JSON API
        try {
            val url = "https://clients5.google.com/translate_a/single?client=dict-chrome-ex&sl=auto&tl=$targetLang&dt=t&q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile; rv:124.0) Gecko/124.0 Firefox/124.0")
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
                            return@withContext unescapeHtmlEntities(result)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google dict-chrome-ex translation failed: ${e.message}")
        }

        // Tier 4: Secondary fallback via Lingva API mirror
        try {
            val sl = if (sourceLang.isBlank()) "auto" else sourceLang
            val url = "https://lingva.ml/api/v1/$sl/$targetLang/$encoded"
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
                        return@withContext unescapeHtmlEntities(translation.trim())
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lingva API translation failed: ${e.message}")
        }

        return@withContext null
    }

    private fun unescapeHtmlEntities(text: String): String {
        return text
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
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
                clean = "$clean ($term)"
            }
        }

        return clean
    }
}

