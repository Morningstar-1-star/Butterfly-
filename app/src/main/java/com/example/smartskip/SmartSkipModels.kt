package com.example.smartskip

import androidx.compose.ui.graphics.Color

/**
 * 10 SponsorBlock / Smart Skip Categories corresponding to SponsorBlock specifications.
 */
enum class SkipCategory(
    val id: String,
    val displayName: String,
    val shortName: String,
    val description: String,
    val defaultBehavior: SkipBehavior,
    val color: Color
) {
    SPONSOR(
        id = "sponsor",
        displayName = "Sponsor",
        shortName = "Sponsor",
        description = "Paid promotion, paid referrals and direct advertisements. Not for self-promotion or free shout-outs to causes / creators / websites / products they like",
        defaultBehavior = SkipBehavior.AUTO_SKIP,
        color = Color(0xFF00E676) // Bright Green
    ),
    SELF_PROMO(
        id = "selfpromo",
        displayName = "Unpaid / Self Promotion",
        shortName = "Self Promo",
        description = "Similar to Sponsor except for unpaid / self promotion. Includes sections about merchandise, donations, or information about who they collaborated with",
        defaultBehavior = SkipBehavior.AUTO_SKIP,
        color = Color(0xFFFFD600) // Yellow
    ),
    INTERACTION(
        id = "interaction",
        displayName = "Interaction Reminder (Subscribe)",
        shortName = "Reminder",
        description = "A short reminder to like, subscribe or follow them in the middle of content. If it is long or about something specific, it should instead be under self promotion",
        defaultBehavior = SkipBehavior.AUTO_SKIP,
        color = Color(0xFFE040FB) // Magenta / Purple
    ),
    HIGHLIGHT(
        id = "poi_highlight",
        displayName = "Highlight",
        shortName = "Highlight",
        description = "The part of the video that most people are looking for",
        defaultBehavior = SkipBehavior.SHOW_BUTTON,
        color = Color(0xFFFF4081) // Pink / Hot Red
    ),
    INTRO(
        id = "intro",
        displayName = "Intermission / Intro Animation",
        shortName = "Intro",
        description = "An interval without actual content. Could be a pause, static frame, or repeating animation. Does not include transitions containing information",
        defaultBehavior = SkipBehavior.AUTO_SKIP,
        color = Color(0xFF00E5FF) // Cyan
    ),
    OUTRO(
        id = "outro",
        displayName = "Endcards / Credits",
        shortName = "Credits",
        description = "Credits or when the YouTube endcards appear. Not for conclusions with information",
        defaultBehavior = SkipBehavior.AUTO_SKIP,
        color = Color(0xFF2979FF) // Blue
    ),
    PREVIEW(
        id = "preview",
        displayName = "Preview / Recap",
        shortName = "Recap",
        description = "Collection of clips that show what is coming up or what happened in the video or in other videos of a series, where all information is repeated elsewhere",
        defaultBehavior = SkipBehavior.AUTO_SKIP,
        color = Color(0xFF00B0FF) // Light Blue
    ),
    HOOK(
        id = "hook",
        displayName = "Hook / Greetings",
        shortName = "Hook",
        description = "Narrated trailers for the upcoming video, greetings and goodbyes. Does not include sections that add additional content",
        defaultBehavior = SkipBehavior.AUTO_SKIP,
        color = Color(0xFF536DFE) // Slate / Indigo
    ),
    FILLER(
        id = "filler",
        displayName = "Tangent / Jokes",
        shortName = "Filler",
        description = "Tangential scenes or jokes that are not required to understand the main content of the video. Does not include sections providing context or background details",
        defaultBehavior = SkipBehavior.DONT_SKIP,
        color = Color(0xFF7C4DFF) // Purple
    ),
    MUSIC_OFFTOPIC(
        id = "music_offtopic",
        displayName = "Music: Non-Music Section",
        shortName = "Non-Music",
        description = "Only for use in music videos. Sections of music videos without music that are not already covered by another category",
        defaultBehavior = SkipBehavior.AUTO_SKIP,
        color = Color(0xFFFF6D00) // Orange
    );

    companion object {
        fun fromId(id: String?): SkipCategory {
            if (id == null) return SPONSOR
            val lower = id.lowercase()
            return values().firstOrNull {
                it.id.equals(lower, ignoreCase = true) ||
                it.name.equals(lower, ignoreCase = true) ||
                (lower == "intermission" && it == INTRO) ||
                (lower == "endcards" && it == OUTRO) ||
                (lower == "recap" && it == PREVIEW) ||
                (lower == "tangent" && it == FILLER) ||
                (lower == "op" && it == INTRO) ||
                (lower == "ed" && it == OUTRO) ||
                (lower == "mixed-op" && it == INTRO) ||
                (lower == "mixed-ed" && it == OUTRO) ||
                (lower == "credits" && it == OUTRO)
            } ?: SPONSOR
        }
    }
}

/**
 * Behavior choice for each category.
 */
enum class SkipBehavior(val label: String) {
    AUTO_SKIP("Skip automatically"),
    SHOW_BUTTON("Show button"),
    DONT_SKIP("Don't skip")
}

/**
 * High precision skip segment.
 */
data class SkipSegment(
    val category: SkipCategory,
    val startMs: Long,
    val endMs: Long,
    val label: String,
    val providerSource: String,
    val uuid: String = "",
    val actionType: SkipBehavior = SkipBehavior.AUTO_SKIP
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

/**
 * Supported Smart Skip sources.
 */
enum class SkipSource(val id: String, val displayName: String, val description: String) {
    YOUTUBE("youtube", "YouTube (SponsorBlock)", "Crowdsourced sponsor and segment skipping for YouTube"),
    BILIBILI("bilibili", "Bilibili (BilibiliSponsorBlock)", "Bilibili community segment skipping"),
    ANIME("anime", "Anime (AniSkip)", "Automated anime OP / ED / Recap skip markers"),
    MOVIES_TV("movies_tv", "Movies & TV (TheIntroDB)", "TV show and movie opening, recap, and credit markers");
}
