package com.example.recommendation

import com.example.model.VideoItem
import com.example.util.SmartTagExtractor
import java.util.Locale
import java.util.regex.Pattern

/**
 * High-Precision Video Knowledge & Semantic Intelligence Extractor.
 *
 * Extracts deep, multi-dimensional semantic metadata entirely under the hood:
 * 1. Detected Language & Script Analysis (Chinese, Japanese, Korean, Hindi, Spanish, French, German, Russian, Arabic, English)
 * 2. Primary & Secondary Categories synchronized with SmartTagExtractor (Trailer, Movie, Gameplay, Song, Anime, Tutorial, etc.)
 * 3. Granular Hashtags & Topic Keywords (#trailer, #gameplay, #ost, #lofi, #walkthrough, etc.)
 * 4. Content Tone & Format Tier (Quick Clip, Short, Episode, Feature Film, Live Stream)
 * 5. Creator / Performer / Studio entity extraction
 *
 * NOTE: Operates strictly for AI intelligence, recommendation ranking, and taste modeling.
 * Does NOT require or inject any visible UI tags/hashtags into video cards.
 */
object VideoKnowledgeExtractor {

    enum class DurationTier(val label: String) {
        LIVE_STREAM("Live Stream"),
        QUICK_CLIP("Quick Clip (< 3m)"),
        SHORT("Short (3-10m)"),
        STANDARD_EPISODE("Standard Episode (10-45m)"),
        FEATURE_FILM("Feature Film (45m+)")
    }

    data class LanguageMetadata(
        val code: String,
        val displayName: String,
        val flagEmoji: String,
        val confidence: Float
    )

    data class VideoKnowledge(
        val videoId: String,
        val primaryCategory: String,
        val categoryDisplayName: String,
        val detectedLanguage: LanguageMetadata,
        val hashtags: List<String>,
        val semanticKeywords: List<String>,
        val durationTier: DurationTier,
        val providerId: String,
        val isLiveStream: Boolean,
        val performerOrCreator: String,
        val contentTone: String
    )

    private val HASHTAG_PATTERN = Pattern.compile("#([\\p{L}0-9_\\-]+)")
    private val BRACKETED_TOPIC_PATTERN = Pattern.compile("[\\[\\(【「]([\\p{L}0-9_\\s\\-]{2,20})[\\]\\)】」]")

    private val CHINESE_DRAMA_KEYWORDS = listOf(
        "电视剧", "国产剧", "古装", "仙侠", "玄幻", "动漫", "国创", "第", "集", "预告", "花絮",
        "donghua", "cdrama", "c-drama", "chinese drama", "xianxia", "wuxia", "vip", "tencent video",
        "bilibili", "v.qq.com", "iqiyi", "youku", "manga", "manhua", "webtoon"
    )

    private val JAPANESE_KEYWORDS = listOf(
        "anime", "subbed", "english sub", "raw", "toei", "mappa", "ghibli", "kadokawa",
        "crunchyroll", "aniplex", "toho", "madhouse", "kyoto animation", "shonen", "seiyuu",
        "j-pop", "jpop", "otaku", "demonslayer", "jujutsu", "naruto", "one piece", "dragon ball",
        "frieren", "solo leveling", "attack on titan", "bleach", "supjav", "jav", "uncensored",
        "tokyo", "japan", "japanese"
    )

    private val HINDI_KEYWORDS = listOf(
        "hindi", "bollywood", "dubbed in hindi", "hindi dubbed", "t-series", "tseries",
        "zee music", "goldmines", "shemaroo", "starplus", "sab tv", "sony liv", "sonyliv", "voot",
        "hotstar", "aaj tak", "colors tv", "yash raj", "dharma", "bhansali", "south hindi",
        "filmy", "geet", "gaana", "b4u", "ultra movie", "desi", "hindi song", "hindi movie"
    )

    private val KOREAN_KEYWORDS = listOf(
        "k-drama", "kdrama", "korean drama", "k-pop", "kpop", "hangul", "tvn", "jtbc",
        "sbs", "kbs", "mbc", "bts", "blackpink", "newjeans", "stray kids", "seoul", "korean"
    )

    private val SPANISH_KEYWORDS = listOf(
        "cancion", "canción", "musica", "música", "pelicula", "película", "completa", "en español",
        "espanol", "capitulo", "capítulo", "temporada", "latino", "subtitulado", "resumen",
        "trailer oficial en español", "noticias", "musica latina", "reggaeton"
    )

    private val FRENCH_KEYWORDS = listOf(
        "français", "francais", "en français", "chanson", "musique", "film complet",
        "bande-annonce", "saison", "épisode", "vf", "vostfr"
    )

    private val GERMAN_KEYWORDS = listOf(
        "deutsch", "auf deutsch", "ganzer film", "folge", "staffel", "offizieller trailer", "deutsche"
    )

    private val LIVE_CAM_PROVIDERS = setOf(
        "stripchat", "chaturbate", "cam4", "cammodels", "camsoda", "bongacams"
    )

    private val ADULT_TUBE_PROVIDERS = setOf(
        "xnxx", "hellporno", "pornhub", "xvideos", "youporn", "xhamster", "rule34video",
        "hanime1", "redtube", "tube8", "coomer", "pmvhaven", "txxx"
    )

    private val JAV_PROVIDERS = setOf(
        "supjav", "sextb", "123av", "javtiful", "javguru", "missav"
    )

    /**
     * Extracts full knowledge profile for a given video item.
     */
    fun extractKnowledge(video: VideoItem): VideoKnowledge {
        val title = video.title ?: ""
        val uploader = video.uploaderName ?: ""
        val desc = video.description ?: ""
        val provider = (video.providerId ?: "").lowercase(Locale.ROOT).trim()

        val fullText = "$title $uploader $desc ${video.tags.joinToString(" ")}".lowercase(Locale.ROOT)

        // 1. Extract hashtags and bracketed tags
        val hashtags = extractHashtags(title, desc, video.tags)

        // 2. Language Detection
        val langMeta = detectLanguageDetailed(title, desc, uploader, video.tags, provider)

        // 3. Category & Content Tone Detection (Synchronized with SmartTagExtractor)
        val (primaryCat, catDisplayName, contentTone) = detectCategoryDetailed(video, fullText, provider)

        // 4. Duration & Live Stream Detection
        val isLive = provider in LIVE_CAM_PROVIDERS ||
                video.durationSeconds <= 0L ||
                fullText.contains("live stream") ||
                fullText.contains("🔴 live") ||
                fullText.contains("online now") ||
                fullText.contains("broadcast")

        val durationTier = when {
            isLive -> DurationTier.LIVE_STREAM
            video.durationSeconds in 1..179 -> DurationTier.QUICK_CLIP
            video.durationSeconds in 180..599 -> DurationTier.SHORT
            video.durationSeconds in 600..2699 -> DurationTier.STANDARD_EPISODE
            video.durationSeconds >= 2700 -> DurationTier.FEATURE_FILM
            else -> DurationTier.STANDARD_EPISODE
        }

        // 5. Semantic Keywords
        val semanticKeywords = extractSemanticTokens(video, hashtags)

        return VideoKnowledge(
            videoId = video.id,
            primaryCategory = primaryCat,
            categoryDisplayName = catDisplayName,
            detectedLanguage = langMeta,
            hashtags = hashtags,
            semanticKeywords = semanticKeywords,
            durationTier = durationTier,
            providerId = provider,
            isLiveStream = isLive,
            performerOrCreator = uploader.ifBlank { "Creator" },
            contentTone = contentTone
        )
    }

    /**
     * Extracts hashtags and topic tokens from text, descriptions, and tags.
     */
    fun extractHashtags(title: String, desc: String?, explicitTags: List<String>): List<String> {
        val result = mutableSetOf<String>()

        explicitTags.forEach { tag ->
            val cleaned = tag.trim().lowercase(Locale.ROOT)
            if (cleaned.isNotBlank()) {
                result.add(if (cleaned.startsWith("#")) cleaned else "#$cleaned")
            }
        }

        val combined = "$title ${desc ?: ""}"
        val matcher = HASHTAG_PATTERN.matcher(combined)
        while (matcher.find()) {
            val tag = matcher.group(1)?.lowercase(Locale.ROOT)
            if (!tag.isNullOrBlank() && tag.length in 2..30) {
                result.add("#$tag")
            }
        }

        // Also extract high-signal bracketed tokens [Trailer], [OST], [Gameplay], etc.
        val bracketMatcher = BRACKETED_TOPIC_PATTERN.matcher(combined)
        val bracketStopWords = setOf("hd", "4k", "1080p", "60fps", "full", "official", "video", "new", "free")
        while (bracketMatcher.find()) {
            val token = bracketMatcher.group(1)?.trim()?.lowercase(Locale.ROOT)
            if (!token.isNullOrBlank() && token.length in 2..20 && token !in bracketStopWords) {
                val tagFormatted = "#" + token.replace(Regex("[^a-zA-Z0-9_]"), "")
                if (tagFormatted.length >= 3) {
                    result.add(tagFormatted)
                }
            }
        }

        return result.toList().take(15)
    }

    /**
     * Advanced Language Detection supporting Chinese, Japanese, Korean, Hindi, Spanish, French, German, Russian, Arabic, and English.
     */
    fun detectLanguageDetailed(
        title: String,
        desc: String?,
        uploader: String?,
        tags: List<String>?,
        providerId: String?
    ): LanguageMetadata {
        val prov = (providerId ?: "").lowercase(Locale.ROOT)
        val fullText = "$title ${uploader ?: ""} ${desc ?: ""} ${(tags ?: emptyList()).joinToString(" ")}".lowercase(Locale.ROOT)

        // 1. Chinese Detection (Hanzi Unicode block: \u4E00..\u9FFF, \u3400..\u4DBF)
        val hanziCount = title.count { it in '\u4E00'..'\u9FFF' || it in '\u3400'..'\u4DBF' } +
                (uploader ?: "").count { it in '\u4E00'..'\u9FFF' }
        val hasChineseProvider = prov in setOf("tencent", "v.qq.com", "bilibili")
        val hasChineseDramaKeywords = CHINESE_DRAMA_KEYWORDS.any { fullText.contains(it) }

        if (hanziCount >= 2 || (hasChineseProvider && hanziCount >= 1) || (hasChineseDramaKeywords && hanziCount >= 1)) {
            return LanguageMetadata("zh", "Chinese", "🇨🇳", 0.95f)
        }

        // 2. Japanese Detection (Hiragana \u3040..\u309F, Katakana \u30A0..\u30FF)
        val kanaCount = title.count { it in '\u3040'..'\u30FF' } + (uploader ?: "").count { it in '\u3040'..'\u30FF' }
        val isJavProvider = prov in JAV_PROVIDERS
        val hasJavCode = Regex("""[a-zA-Z]{3,5}-\d{3,5}""").containsMatchIn(title)
        val hasJapaneseKeywords = JAPANESE_KEYWORDS.any { fullText.contains(it) }

        if (kanaCount >= 2 || isJavProvider || (hasJavCode && (kanaCount >= 1 || prov.contains("jav"))) || (hasJapaneseKeywords && kanaCount >= 1)) {
            return LanguageMetadata("ja", "Japanese", "🇯🇵", 0.94f)
        }

        // 3. Korean Detection (Hangul: \uAC00..\uD7AF, \u1100..\u11FF)
        val hangulCount = title.count { it in '\uAC00'..'\uD7AF' || it in '\u1100'..'\u11FF' } +
                (uploader ?: "").count { it in '\uAC00'..'\uD7AF' }
        val hasKoreanKeywords = KOREAN_KEYWORDS.any { fullText.contains(it) }
        if (hangulCount >= 2 || (hasKoreanKeywords && hangulCount >= 1)) {
            return LanguageMetadata("ko", "Korean", "🇰🇷", 0.94f)
        }

        // 4. Hindi & Indic Detection (Devanagari: \u0900..\u097F)
        val devanagariCount = title.count { it in '\u0900'..'\u097F' } + (uploader ?: "").count { it in '\u0900'..'\u097F' }
        val hasHindiKeywords = HINDI_KEYWORDS.any { fullText.contains(it) }
        val isIndianOtt = prov in setOf("sonyliv", "hotstar", "zee5")

        if (devanagariCount >= 2 || hasHindiKeywords || (isIndianOtt && hasHindiKeywords)) {
            return LanguageMetadata("hi", "Hindi", "🇮🇳", 0.93f)
        }

        // 5. Arabic Detection (\u0600..\u06FF)
        val arabicCount = title.count { it in '\u0600'..'\u06FF' }
        if (arabicCount >= 3) {
            return LanguageMetadata("ar", "Arabic", "🇸🇦", 0.92f)
        }

        // 6. Russian / Cyrillic Detection (\u0400..\u04FF)
        val cyrillicCount = title.count { it in '\u0400'..'\u04FF' }
        if (cyrillicCount >= 3) {
            return LanguageMetadata("ru", "Russian", "🇷🇺", 0.92f)
        }

        // 7. Spanish Detection (accents & distinct vocabulary)
        val spanishSpecialCharCount = title.count { it in "áéíóúñÁÉÍÓÚÑ¿¡" }
        val hasSpanishKeywords = SPANISH_KEYWORDS.any { fullText.contains(it) }
        if (spanishSpecialCharCount >= 2 || hasSpanishKeywords) {
            return LanguageMetadata("es", "Spanish", "🇪🇸", 0.90f)
        }

        // 8. French Detection
        val frenchSpecialCharCount = title.count { it in "çàèùœéêëÇÀÈÙŒÉÊË" }
        val hasFrenchKeywords = FRENCH_KEYWORDS.any { fullText.contains(it) }
        if (frenchSpecialCharCount >= 2 || hasFrenchKeywords) {
            return LanguageMetadata("fr", "French", "🇫🇷", 0.90f)
        }

        // 9. German Detection
        val germanSpecialCharCount = title.count { it in "äöüßÄÖÜ" }
        val hasGermanKeywords = GERMAN_KEYWORDS.any { fullText.contains(it) }
        if (germanSpecialCharCount >= 2 || hasGermanKeywords) {
            return LanguageMetadata("de", "German", "🇩🇪", 0.90f)
        }

        // 10. English Default / Latin script
        val englishWordRegex = Regex("""\b(the|and|with|official|music|video|trailer|episode|full|stream|watch|show|season|hd|4k|gameplay|review|movie|song)\b""", RegexOption.IGNORE_CASE)
        if (englishWordRegex.containsMatchIn(fullText) || title.any { it in 'a'..'z' || it in 'A'..'Z' }) {
            return LanguageMetadata("en", "English", "🇺🇸", 0.85f)
        }

        return LanguageMetadata("other", "International", "🌐", 0.50f)
    }

    /**
     * Categorizes video into deep semantic categories synchronized with SmartTagExtractor.
     */
    private fun detectCategoryDetailed(
        video: VideoItem,
        fullText: String,
        provider: String
    ): Triple<String, String, String> {
        // Priority 1: Live Interactive Webcams
        if (provider in LIVE_CAM_PROVIDERS || fullText.contains("stripchat") || fullText.contains("chaturbate") || fullText.contains("webcam model")) {
            return Triple("live_cams_webcams", "🔴 Live Webcam", "live_interactive")
        }

        // Priority 2: Use SmartTagExtractor's precision categories (Trailer, Gameplay, Song, Movie, Anime, etc.)
        val topTags = SmartTagExtractor.extractTags(video, maxTags = 2)
        val primaryTag = topTags.firstOrNull()

        if (primaryTag != null) {
            val tone = when (primaryTag.category) {
                "trailer", "movie_trailer", "gameplay_trailer", "anime_trailer" -> "trailer"
                "movie", "short_film", "classic_cinema" -> "cinema_film"
                "gameplay", "gaming", "esports" -> "gaming"
                "song", "music", "lofi", "hip_hop", "acoustic", "soundtrack", "edm", "rock", "kpop" -> "music"
                "anime", "donghua" -> "anime"
                "tutorial", "guide", "tech", "coding", "ai" -> "educational"
                "podcast", "interview" -> "podcast"
                "documentary" -> "documentary"
                "comedy", "standup" -> "comedy"
                "nsfw_adult", "hentai", "pmv", "cam_model" -> "mature_adult"
                else -> "entertainment"
            }
            return Triple(primaryTag.category, "${primaryTag.emoji} ${primaryTag.displayName}", tone)
        }

        // Adult tube fallback
        if (provider in ADULT_TUBE_PROVIDERS || provider in JAV_PROVIDERS || fullText.contains("porn") || fullText.contains("xxx")) {
            return Triple("adult_hd_tube", "🔞 Adult", "mature_adult")
        }

        // Chinese drama fallback
        if (provider in setOf("tencent", "v.qq.com", "bilibili") || CHINESE_DRAMA_KEYWORDS.any { fullText.contains(it) }) {
            return Triple("chinese_drama_donghua", "🇨🇳 Chinese Drama", "series_drama")
        }

        return Triple("general_entertainment", "🎬 Entertainment", "entertainment")
    }

    /**
     * Extracts normalized semantic tokens with stop words stripped out.
     */
    private fun extractSemanticTokens(video: VideoItem, hashtags: List<String>): List<String> {
        val stopWords = setOf(
            "the", "and", "for", "with", "that", "this", "from", "video", "official", "full", "hd", "4k",
            "2024", "2025", "2026", "about", "are", "have", "more", "you", "your", "part", "episode",
            "watch", "stream", "clip", "online", "free"
        )

        val tokens = mutableSetOf<String>()
        hashtags.forEach { tokens.add(it.replace("#", "")) }

        val raw = "${video.title} ${video.uploaderName}"
            .split(Regex("[^\\p{L}0-9]+"))
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.length >= 3 && it !in stopWords }

        tokens.addAll(raw)
        return tokens.take(15)
    }
}
