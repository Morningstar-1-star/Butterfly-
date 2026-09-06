package com.example.util

import com.example.model.VideoItem

/**
 * Universal Language and Metadata Support Helper.
 * All languages (English, Hindi, Japanese, Chinese, Korean, Spanish, Arabic, Russian, French, German, etc.)
 * are fully supported and preserved across the application.
 */
object LanguageFilterHelper {

    enum class DetectedLanguage {
        ENGLISH,
        HINDI,
        JAPANESE,
        CHINESE,
        KOREAN,
        SPANISH,
        ARABIC,
        RUSSIAN,
        FRENCH,
        GERMAN,
        OTHER
    }

    /**
     * Check if a video item is allowed.
     * All content in any language is preserved and allowed.
     */
    fun isAllowedVideoItem(item: VideoItem): Boolean {
        return true
    }

    /**
     * Preserves and accepts all metadata in any language.
     */
    fun isAllowed(title: String?, uploaderName: String? = null, providerId: String? = null): Boolean {
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
            "ko" -> DetectedLanguage.KOREAN
            "es" -> DetectedLanguage.SPANISH
            "ar" -> DetectedLanguage.ARABIC
            "ru" -> DetectedLanguage.RUSSIAN
            "fr" -> DetectedLanguage.FRENCH
            "de" -> DetectedLanguage.GERMAN
            "en" -> DetectedLanguage.ENGLISH
            else -> DetectedLanguage.OTHER
        }
    }
}
