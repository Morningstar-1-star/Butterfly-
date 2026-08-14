package com.example.util

import com.example.model.VideoItem
import java.util.Locale

/**
 * Language detection and filtering engine.
 * Strictly enforces content visibility ONLY for:
 * 1. English
 * 2. Hindi (Devanagari script + Hinglish / Romanized Hindi)
 * 3. Japanese (Hiragana, Katakana, Kanji with Japanese context + Romaji)
 *
 * All other languages (e.g. Russian/Cyrillic, Arabic, Korean/Hangul, Thai, Spanish,
 * Portuguese, French, German, Turkish, Chinese-only, etc.) are blocked.
 */
object LanguageFilterHelper {

    enum class DetectedLanguage {
        ENGLISH,
        HINDI,
        JAPANESE,
        BLOCKED
    }

    // Common Hinglish / Romanized Hindi indicator words
    private val HINDI_ROMANIZED_WORDS = hashSetOf(
        "kare", "kaise", "hota", "dekhe", "batao", "kya", "hai", "bhi", "yeh", "woh",
        "gaana", "geet", "desh", "bharat", "namaste", "aaj", "kal", "pyar", "dil", "hindu",
        "bollywood", "kahani", "shiksha", "khabar", "samachar", "apna", "shuru", "karo",
        "achha", "bahut", "gaye", "wale", "wali", "shandar", "suno", "dekho", "gana",
        "mast", "hum", "tum", "mera", "meri", "tere", "teri", "kuch", "sabse", "zindagi",
        "dost", "dosti", "bhai", "behen", "didi", "pyaar", "ishq", "mohabbat", "khushi",
        "nach", "gana", "nacho", "dekhte", "raho", "sach", "jhooth", "kab", "kahan", "kaun",
        "kitna", "kitne", "accha", "bahut", "badhiya", "khatarnak", "naya", "purana", "sab",
        "log", "kahin", "aaya", "aayi", "gaya", "gayi", "karenge", "karta", "karti", "karte",
        "hoon", "hain", "raha", "rahi", "rahe", "thoda", "zyada", "sirf", "bataiye", "seekho",
        "sikhaye", "deshi", "videshi", "gaon", "shehar", "duniya", "chahiye", "milenge", "mila",
        "mile", "bhojpuri", "punjabi", "marathi", "gujarati", "desi", "bhabhi", "devar"
    )

    // Japanese Romaji & Culture keywords
    private val JAPANESE_ROMAJI_WORDS = hashSetOf(
        "anime", "manga", "tokyo", "japan", "japanese", "naruto", "shingeki", "kyojin", "shounen",
        "isekai", "kimetsu", "yaiba", "jujutsu", "kaisen", "boku", "watashi", "arigato", "sensei",
        "senpai", "vocaloid", "hatsune", "miku", "nintendo", "shibuya", "shinjuku", "sub", "dub",
        "op", "ed", "ost", "amv", "mad", "jpop", "jrock", "vtuber", "hololive", "nijisanji",
        "ghibli", "toei", "mappa", "ufotable", "aniplex", "kadokawa", "shueisha", "clannad",
        "gundam", "bleach", "pokemon", "persona", "genshin", "honkai", "kawaii", "desu", "neko",
        "maid", "samurai", "ninja", "katana", "chibi", "otaku", "cosplay", "akihabara", "kyoto",
        "osaka", "hokkaido", "genshin", "seiyuu", "onigiri", "ramen", "sushi", "zen", "tsundere",
        "yandere", "waifu", "husbando", "doujin", "hentai", "jav", "javinfo", "apijav", "fc2",
        "ssis", "ipx", "stars", "sone", "mide", "ssni", "juq", "dldss", "abw", "abp", "ipz",
        "snis", "ipx", "fsdss", "ebod", "pppd", "meyd", "dasd", "jul", "adn", "pred", "wanz"
    )

    // Distinctive foreign language words that must be BLOCKED
    private val FOREIGN_BLOCKED_WORDS = hashSetOf(
        // Spanish
        "película", "pelicula", "completa", "canción", "cancion", "capítulo", "capitulo",
        "español", "noticias", "resumen", "partido", "para", "como", "con", "por", "sobre",
        "nuevo", "nueva", "mundo", "todos", "temporada", "estreno", "habla", "usted", "ustedes",
        // Portuguese
        "não", "vídeo", "video", "música", "musica", "episódio", "episodio", "português",
        "portugues", "você", "voce", "hoje", "futebol", "completo", "novela", "filme",
        // French
        "bande", "annonce", "officielle", "sous-titres", "français", "francais", "résumé",
        "chanson", "avec", "dans", "pour", "cette", "monde", "histoire", "saison",
        // German
        "offizielles", "ganzer", "film", "deutsch", "nachrichten", "zusammenfassung",
        "folge", "staffel", "heute", "nicht", "spiel", "über", "unsere",
        // Turkish
        "türkçe", "turkce", "dublaj", "izle", "fragmanı", "fragmani", "özet", "ozet",
        "bölüm", "bolum", "yayın", "yayin", "canlı", "canli", "şarkı", "sarki", "dizi",
        // Indonesian / Malay
        "lagu", "terbaru", "bahasa", "terlengkap", "alur", "cerita", "film", "selengkapnya",
        // Italian
        "canzone", "italiano", "puntata", "episodio", "film", "completo", "notizie"
    )

    // Chinese-exclusive keywords (to distinguish from Japanese Kanji)
    private val CHINESE_EXCLUSIVE_WORDS = hashSetOf(
        "电影", "电视剧", "完整版", "國語", "国语", "普通話", "普通话", "免费", "中文", "中文字幕",
        "哔哩哔哩", "优酷", "腾讯", "爱奇艺", "预告", "解说", "大陆", "剧情", "粤语"
    )

    /**
     * Check if a video item is in an allowed language (English, Hindi, Japanese).
     */
    fun isAllowedVideoItem(item: VideoItem): Boolean {
        return isAllowed(
            title = item.title,
            uploaderName = item.uploaderName,
            providerId = item.providerId
        )
    }

    /**
     * Determine if given metadata is strictly in English, Hindi, or Japanese.
     */
    fun isAllowed(title: String?, uploaderName: String? = null, providerId: String? = null): Boolean {
        if (title.isNullOrBlank()) return true

        val detected = detectLanguage(title, uploaderName, providerId)
        return detected != DetectedLanguage.BLOCKED
    }

    /**
     * Detect specific language: ENGLISH, HINDI, JAPANESE, or BLOCKED.
     */
    fun detectLanguage(title: String, uploaderName: String? = null, providerId: String? = null): DetectedLanguage {
        val cleanTitle = title.trim()
        val cleanUploader = uploaderName?.trim() ?: ""
        val pid = providerId?.lowercase() ?: ""

        // 1. Japanese Provider Fast-Track (Anime / JAV / Japanese metadata)
        if (pid == "jikan_anime" || pid == "nyaa" || pid == "nyaa_si" || pid.contains("jav")) {
            return DetectedLanguage.JAPANESE
        }

        // 2. Unicode Block Scanning
        var hasDevanagari = false
        var hasJapaneseKana = false
        var hasCjkUnified = false
        var hasDisallowedScript = false

        for (cp in cleanTitle.codePoints()) {
            val block = Character.UnicodeBlock.of(cp)
            when (block) {
                Character.UnicodeBlock.DEVANAGARI,
                Character.UnicodeBlock.DEVANAGARI_EXTENDED,
                Character.UnicodeBlock.VEDIC_EXTENSIONS -> {
                    hasDevanagari = true
                }
                Character.UnicodeBlock.HIRAGANA,
                Character.UnicodeBlock.KATAKANA,
                Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS,
                Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS -> {
                    hasJapaneseKana = true
                }
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
                Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION -> {
                    hasCjkUnified = true
                }
                // Explicitly Blocked Alphabets / Scripts
                Character.UnicodeBlock.CYRILLIC,
                Character.UnicodeBlock.CYRILLIC_SUPPLEMENTARY,
                Character.UnicodeBlock.ARABIC,
                Character.UnicodeBlock.ARABIC_SUPPLEMENT,
                Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A,
                Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B,
                Character.UnicodeBlock.HANGUL_SYLLABLES,
                Character.UnicodeBlock.HANGUL_JAMO,
                Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO,
                Character.UnicodeBlock.THAI,
                Character.UnicodeBlock.HEBREW,
                Character.UnicodeBlock.GREEK,
                Character.UnicodeBlock.BENGALI,
                Character.UnicodeBlock.TAMIL,
                Character.UnicodeBlock.TELUGU,
                Character.UnicodeBlock.KANNADA,
                Character.UnicodeBlock.MALAYALAM,
                Character.UnicodeBlock.GUJARATI,
                Character.UnicodeBlock.GURMUKHI,
                Character.UnicodeBlock.ARMENIAN,
                Character.UnicodeBlock.GEORGIAN,
                Character.UnicodeBlock.MYANMAR,
                Character.UnicodeBlock.KHMER -> {
                    hasDisallowedScript = true
                }
                else -> {}
            }
        }

        // If disallowed foreign script is present, reject immediately
        if (hasDisallowedScript) {
            return DetectedLanguage.BLOCKED
        }

        // Devanagari detected -> Hindi
        if (hasDevanagari) {
            return DetectedLanguage.HINDI
        }

        // Japanese Kana detected -> Japanese
        if (hasJapaneseKana) {
            return DetectedLanguage.JAPANESE
        }

        // CJK Ideographs without kana:
        if (hasCjkUnified) {
            // Check if it has Chinese exclusive terms
            val lowerTitle = cleanTitle.lowercase(Locale.ROOT)
            if (CHINESE_EXCLUSIVE_WORDS.any { lowerTitle.contains(it) }) {
                return DetectedLanguage.BLOCKED
            }
            // Check if context/uploader is Japanese (anime, jpop, etc.)
            if (isJapaneseContext(cleanTitle, cleanUploader)) {
                return DetectedLanguage.JAPANESE
            }
            // Fallback for CJK ideographs without kana: block purely Chinese media
            return DetectedLanguage.BLOCKED
        }

        // 3. Latin Script Text Analysis:
        val words = cleanTitle.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{Nd}]+"))
            .filter { it.isNotBlank() }

        if (words.isEmpty()) return DetectedLanguage.ENGLISH

        // Check for disallowed foreign vocabulary (Spanish, French, German, Turkish, etc.)
        var foreignScore = 0
        for (w in words) {
            if (FOREIGN_BLOCKED_WORDS.contains(w)) {
                foreignScore++
            }
        }
        if (foreignScore >= 2 || (words.size <= 4 && foreignScore >= 1)) {
            return DetectedLanguage.BLOCKED
        }

        // Check for Japanese Romaji vocabulary
        var japaneseScore = 0
        for (w in words) {
            if (JAPANESE_ROMAJI_WORDS.contains(w)) {
                japaneseScore++
            }
        }
        if (japaneseScore > 0 && isJapaneseContext(cleanTitle, cleanUploader)) {
            return DetectedLanguage.JAPANESE
        }

        // Check for Hinglish / Romanized Hindi vocabulary
        var hindiScore = 0
        for (w in words) {
            if (HINDI_ROMANIZED_WORDS.contains(w)) {
                hindiScore++
            }
        }
        if (hindiScore >= 2 || (words.size <= 5 && hindiScore >= 1)) {
            return DetectedLanguage.HINDI
        }

        if (japaneseScore >= 2) {
            return DetectedLanguage.JAPANESE
        }

        // Default Latin text is treated as English (international media/numbers/titles)
        return DetectedLanguage.ENGLISH
    }

    private fun isJapaneseContext(title: String, uploader: String): Boolean {
        val combined = "$title $uploader".lowercase(Locale.ROOT)
        val japaneseTokens = listOf(
            "anime", "manga", "j-pop", "jpop", "vocaloid", "hatsune miku", "toei", "mappa",
            "aniplex", "ufotable", "ghibli", "kadokawa", "shueisha", "vtuber", "hololive",
            "nijisanji", "japan", "tokyo", "ost", "amv", "op", "ed", "sub", "dub",
            "gundam", "naruto", "one piece", "bleach", "dragon ball", "jojos", "kimetsu",
            "jujutsu", "attack on titan", "shingeki", "chainsaw man", "spy x family",
            "bocchi", "frieren", "dungeon meshi", "my hero academia", "boku no hero",
            "demon slayer", "boruto", "fc2", "ssis", "ipx", "stars", "sone", "ssni"
        )
        return japaneseTokens.any { combined.contains(it) }
    }
}
