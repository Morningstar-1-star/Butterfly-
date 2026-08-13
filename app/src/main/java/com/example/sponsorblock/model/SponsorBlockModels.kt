package com.example.sponsorblock.model

import androidx.compose.ui.graphics.Color

enum class SponsorBlockCategory(
    val key: String,
    val title: String,
    val description: String,
    val color: Color,
    val defaultAction: SponsorBlockAction
) {
    SPONSOR(
        key = "sponsor",
        title = "Sponsor",
        description = "Paid promotion, paid referrals and direct advertisements. Not for self-promotion or free shout-outs to causes / creators / websites / products they like",
        color = Color(0xFF00D400),
        defaultAction = SponsorBlockAction.AUTO_SKIP
    ),
    SELFPROMO(
        key = "selfpromo",
        title = "Unpaid / Self Promotion",
        description = "Similar to Sponsor except for unpaid / self promotion. Includes sections about merchandise, donations, or information about who they collaborated with",
        color = Color(0xFFFFD700),
        defaultAction = SponsorBlockAction.AUTO_SKIP
    ),
    INTERACTION(
        key = "interaction",
        title = "Interaction Reminder (Subscribe)",
        description = "A short reminder to like, subscribe or follow them in the middle of content. If it is long or about something specific, it should instead be under self promotion",
        color = Color(0xFFCC00FF),
        defaultAction = SponsorBlockAction.AUTO_SKIP
    ),
    HIGHLIGHT(
        key = "poi_highlight",
        title = "Highlight",
        description = "The part of the video that most people are looking for",
        color = Color(0xFFFF1493),
        defaultAction = SponsorBlockAction.MANUAL_SKIP
    ),
    INTRO(
        key = "intro",
        title = "Intermission / Intro Animation",
        description = "An interval without actual content. Could be a pause, static frame, or repeating animation. Does not include transitions containing information",
        color = Color(0xFF00E5FF),
        defaultAction = SponsorBlockAction.AUTO_SKIP
    ),
    OUTRO(
        key = "outro",
        title = "Endcards / Credits",
        description = "Credits or when the YouTube endcards appear. Not for conclusions with information",
        color = Color(0xFF0000FF),
        defaultAction = SponsorBlockAction.AUTO_SKIP
    ),
    PREVIEW(
        key = "preview",
        title = "Preview / Recap",
        description = "Collection of clips that show what is coming up or what happened in the video or in other videos of a series, where all information is repeated elsewhere",
        color = Color(0xFF00BFFF),
        defaultAction = SponsorBlockAction.AUTO_SKIP
    ),
    FILLER(
        key = "filler",
        title = "Hook / Greetings",
        description = "Narrated trailers for the upcoming video, greetings and goodbyes. Does not include sections that add additional content",
        color = Color(0xFF6A5ACD),
        defaultAction = SponsorBlockAction.AUTO_SKIP
    ),
    TANGENT(
        key = "tangent",
        title = "Tangent / Jokes",
        description = "Tangential scenes or jokes that are not required to understand the main content of the video. Does not include sections providing context or background details",
        color = Color(0xFF9932CC),
        defaultAction = SponsorBlockAction.MANUAL_SKIP
    ),
    ANIME_OP(
        key = "anime_op",
        title = "Anime Opening (OP)",
        description = "Anime opening theme song and title sequence (via AniSkip API)",
        color = Color(0xFFFF5722),
        defaultAction = SponsorBlockAction.AUTO_SKIP
    ),
    ANIME_ED(
        key = "anime_ed",
        title = "Anime Ending (ED)",
        description = "Anime ending theme song and credits sequence (via AniSkip API)",
        color = Color(0xFFE91E63),
        defaultAction = SponsorBlockAction.AUTO_SKIP
    ),
    ANIME_RECAP(
        key = "anime_recap",
        title = "Anime Recap",
        description = "Summary of previous anime episodes (via AniSkip API)",
        color = Color(0xFF00BCD4),
        defaultAction = SponsorBlockAction.AUTO_SKIP
    ),
    TV_INTRO(
        key = "tv_intro",
        title = "TV Series Intro / Theme",
        description = "TV series opening sequence or theme song (via TheIntroDB / Stream Chapters)",
        color = Color(0xFF3F51B5),
        defaultAction = SponsorBlockAction.AUTO_SKIP
    ),
    TV_CREDITS(
        key = "tv_credits",
        title = "TV Series Credits",
        description = "TV series end credits sequence (via TheIntroDB / Stream Chapters)",
        color = Color(0xFF9C27B0),
        defaultAction = SponsorBlockAction.AUTO_SKIP
    ),
    MOVIE_SONG(
        key = "movie_song",
        title = "Movie Song / Musical Scene",
        description = "Musical songs or musical dance sequences in movies (via Stream Chapters / SponsorBlock)",
        color = Color(0xFFFF9800),
        defaultAction = SponsorBlockAction.MANUAL_SKIP
    );

    companion object {
        fun fromKey(key: String): SponsorBlockCategory {
            val lower = key.lowercase().trim()
            return when {
                lower == "op" || lower == "anime_op" || lower == "opening" -> ANIME_OP
                lower == "ed" || lower == "anime_ed" || lower == "ending" -> ANIME_ED
                lower == "recap" || lower == "anime_recap" || lower == "mixed-op" || lower == "mixed-ed" -> ANIME_RECAP
                lower == "tv_intro" || lower == "introdb_intro" || lower == "tv_theme" -> TV_INTRO
                lower == "tv_credits" || lower == "introdb_outro" || lower == "credits" -> TV_CREDITS
                lower == "movie_song" || lower == "song" || lower == "musical" || lower == "music_offtopic" -> MOVIE_SONG
                lower == "poi_highlight" || lower == "highlight" -> HIGHLIGHT
                else -> values().firstOrNull { it.key.equals(lower, ignoreCase = true) } ?: SPONSOR
            }
        }
    }
}

enum class SponsorBlockAction(val title: String, val description: String) {
    AUTO_SKIP("Skip Automatically", "Automatically seek past segment"),
    MANUAL_SKIP("Show Skip Button", "Display interactive on-screen skip button"),
    DISABLE("Do Nothing", "Ignore segment");

    companion object {
        fun fromName(name: String): SponsorBlockAction {
            return values().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: AUTO_SKIP
        }
    }
}

data class SponsorSegment(
    val category: SponsorBlockCategory,
    val startTime: Double, // in seconds
    val endTime: Double,   // in seconds
    val uuid: String,
    val locked: Int = 0
) {
    val duration: Double get() = (endTime - startTime).coerceAtLeast(0.0)
}
