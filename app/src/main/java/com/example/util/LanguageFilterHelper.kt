package com.example.util

import com.example.model.VideoItem

/**
 * Universal Language and Metadata Filter Helper.
 * Strictly filters content so the user receives ONLY languages they understand:
 * - English (Latin)
 * - Hindi (Devanagari script: \u0900-\u097F)
 * - Chinese (Hanzi / CJK Unified Ideographs: \u4E00-\u9FFF, \u3400-\u4DBF)
 * - Japanese (Hiragana \u3040-\u309F, Katakana \u30A0-\u30FF, Kanji \u4E00-\u9FFF)
 *
 * Rejects and blocks all unwanted languages/scripts:
 * - Malayalam (\u0D00-\u0D7F)
 * - Tamil (\u0B80-\u0BFF)
 * - Telugu (\u0C00-\u0C7F)
 * - Kannada (\u0C80-\u0CFF)
 * - Bengali / Assamese (\u0980-\u09FF)
 * - Gujarati (\u0A80-\u0AFF)
 * - Gurmukhi / Punjabi (\u0A00-\u0A7F)
 * - Oriya / Odia (\u0B00-\u0B7F)
 * - Sinhala (\u0D80-\u0DFF)
 * - Arabic / Persian / Urdu (\u0600-\u06FF, \u0750-\u077F, \u08A0-\u08FF)
 * - Cyrillic / Russian / Ukrainian (\u0400-\u04FF)
 * - Thai (\u0E00-\u0E7F)
 * - Burmese / Myanmar (\u1000-\u109F)
 * - Hebrew (\u0590-\u05FF)
 * - Khmer (\u1780-\u17FF)
 */
object LanguageFilterHelper {

    enum class DetectedLanguage {
        ENGLISH,
        HINDI,
        JAPANESE,
        CHINESE,
        OTHER
    }

    // Disallowed character ranges (Malayalam, Tamil, Telugu, Kannada, Bengali, Arabic, Cyrillic, Thai, etc.)
    private val DISALLOWED_SCRIPT_RANGES = listOf(
        0x0D00..0x0D7F, // Malayalam (മലയാളം)
        0x0B80..0x0BFF, // Tamil (தமிழ்)
        0x0C00..0x0C7F, // Telugu (తెలుగు)
        0x0C80..0x0CFF, // Kannada (ಕನ್ನಡ)
        0x0980..0x09FF, // Bengali / Assamese (বাংলা)
        0x0A80..0x0AFF, // Gujarati (ગુજરાતી)
        0x0A00..0x0A7F, // Gurmukhi / Punjabi (ਪੰਜਾਬੀ)
        0x0B00..0x0B7F, // Oriya / Odia (ଓଡ଼ିଆ)
        0x0D80..0x0DFF, // Sinhala (සිංහල)
        0x0600..0x06FF, // Arabic / Urdu / Persian
        0x0750..0x077F, // Arabic Supplement
        0x08A0..0x08FF, // Arabic Extended-A
        0x0400..0x04FF, // Cyrillic (Russian/Ukrainian)
        0x0E00..0x0E7F, // Thai (ไทย)
        0x1000..0x109F, // Myanmar / Burmese
        0x0590..0x05FF, // Hebrew
        0x1780..0x17FF, // Khmer
        0x0EB0..0x0EFF, // Lao
        0x0F00..0x0FFF, // Tibetan
        0x1200..0x137F  // Ethiopic
    )

    // Disallowed regional keywords (e.g. Malayalam news channels, regional language tags)
    private val DISALLOWED_KEYWORDS = listOf(
        "malayalam", "asianet news", "asianetnews", "manorama news", "manoramanews",
        "mathrubhumi", "twentyfour news", "mediaone", "news18 kerala", "kairali",
        "kaumudy", "amrita tv", "mazhavil manorama", "surya tv", "zeekeralam",
        "tamil", "sun tv", "polimer news", "puthiyathalaimurai", "thanthi tv",
        "telugu", "tv9 telugu", "ntv telugu", "etv telugu", "sakshi tv",
        "kannada", "tv9 kannada", "public tv", "suvarna news", "newsfirst kannada",
        "bengali", "abp ananda", "24 ghanta", "news18 bangla",
        "marathi", "abp majha", "tv9 marathi", "zee 24 taas",
        "punjabi", "ptc news", "zee punjab",
        "gujarati", "abp asmita", "tv9 gujarati", "sandesh news",
        "urdu", "ary news", "geo news", "bol news", "samaa tv"
    )

    /**
     * Checks if a character belongs to a disallowed language script.
     */
    fun hasDisallowedScript(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        for (char in text) {
            val code = char.code
            for (range in DISALLOWED_SCRIPT_RANGES) {
                if (code in range) return true
            }
        }
        return false
    }

    /**
     * Checks if text contains disallowed language keywords.
     */
    fun containsDisallowedKeywords(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val lower = text.lowercase()
        for (kw in DISALLOWED_KEYWORDS) {
            if (lower.contains(kw)) return true
        }
        return false
    }

    /**
     * Check if a video item is in one of the allowed languages (English, Hindi, Chinese, Japanese).
     */
    fun isAllowedVideoItem(item: VideoItem): Boolean {
        // 1. Check title for disallowed scripts & keywords
        if (hasDisallowedScript(item.title) || hasDisallowedScript(item.originalTitle)) {
            return false
        }
        if (containsDisallowedKeywords(item.title) || containsDisallowedKeywords(item.originalTitle)) {
            return false
        }

        // 2. Check channel/uploader for disallowed scripts & keywords
        if (hasDisallowedScript(item.uploaderName) || containsDisallowedKeywords(item.uploaderName)) {
            return false
        }

        // 3. Check tags
        for (tag in item.tags) {
            if (hasDisallowedScript(tag) || containsDisallowedKeywords(tag)) {
                return false
            }
        }

        // 4. Check description
        if (hasDisallowedScript(item.description)) {
            return false
        }

        return true
    }

    /**
     * General metadata check.
     */
    fun isAllowed(title: String?, uploaderName: String? = null, providerId: String? = null): Boolean {
        if (hasDisallowedScript(title) || hasDisallowedScript(uploaderName)) return false
        if (containsDisallowedKeywords(title) || containsDisallowedKeywords(uploaderName)) return false
        return true
    }

    /**
     * Detects language using UniversalTranslator.
     */
    fun detectLanguage(title: String, uploaderName: String? = null, providerId: String? = null): DetectedLanguage {
        val code = UniversalTranslator.detectLanguage(title)
        return when (code) {
            "hi" -> DetectedLanguage.HINDI
            "ja" -> DetectedLanguage.JAPANESE
            "zh" -> DetectedLanguage.CHINESE
            "en" -> DetectedLanguage.ENGLISH
            else -> DetectedLanguage.OTHER
        }
    }
}
