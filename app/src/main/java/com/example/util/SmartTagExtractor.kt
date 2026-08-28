package com.example.util

import com.example.model.VideoItem
import java.util.Locale

object SmartTagExtractor {

    data class TagInfo(
        val category: String,
        val displayName: String,
        val emoji: String,
        val priority: Int = 100
    )

    data class SmartTagChip(
        val key: String,
        val label: String,
        val emoji: String,
        val count: Int
    )

    /**
     * Extracts high-accuracy semantic and topical tags for a single VideoItem.
     * Uses strict pattern and semantic context matching — NO naive random word splitting.
     */
    fun extractTags(video: VideoItem): List<TagInfo> {
        val title = video.title
        val titleLower = title.lowercase(Locale.ROOT)
        val uploaderLower = video.uploaderName.lowercase(Locale.ROOT)
        val descriptionLower = (video.description ?: "").lowercase(Locale.ROOT)
        val fullText = "$titleLower $uploaderLower $descriptionLower ${video.tags.joinToString(" ").lowercase(Locale.ROOT)}"

        val detected = mutableListOf<TagInfo>()

        // 1. Trailers & Teasers
        if (titleLower.contains("trailer") || titleLower.contains("teaser") || titleLower.contains("first look") || titleLower.contains("pv ")) {
            when {
                titleLower.contains("anime") || titleLower.contains("pv ") ->
                    detected.add(TagInfo("anime_trailer", "Anime Trailer", "🎌", 10))
                titleLower.contains("gameplay") || titleLower.contains("game trailer") || titleLower.contains("launch trailer") ->
                    detected.add(TagInfo("gaming_trailer", "Gaming Trailer", "🎮", 10))
                titleLower.contains("movie") || titleLower.contains("official trailer") || titleLower.contains("extended") || titleLower.contains("(2024)") || titleLower.contains("(2025)") || titleLower.contains("(2026)") ->
                    detected.add(TagInfo("movie_trailer", "Movie Trailer", "🎬", 10))
                else ->
                    detected.add(TagInfo("trailer", "Trailer", "🎬", 12))
            }
        }

        // 2. Movies & Classic Cinema
        val yearPattern = Regex("\\((19\\d{2}|20\\d{2})\\)")
        val hasYear = yearPattern.containsMatchIn(title)
        val isArchive = uploaderLower.contains("archive") || fullText.contains("internet archive") || uploaderLower.contains("classic")
        
        if (titleLower.contains("full movie") || titleLower.contains("entire movie") || titleLower.contains("feature film") || titleLower.contains("cinema") || (hasYear && !titleLower.contains("trailer"))) {
            if (isArchive || (hasYear && title.contains("19"))) {
                detected.add(TagInfo("movie", "Movie", "🍿", 15))
                detected.add(TagInfo("classic_cinema", "Classic Cinema", "📽️", 18))
            } else {
                detected.add(TagInfo("movie", "Movie", "🍿", 15))
            }
        } else if (isArchive) {
            detected.add(TagInfo("movie", "Movie", "🍿", 20))
            detected.add(TagInfo("classic_cinema", "Classic Cinema", "📽️", 22))
        }

        // 3. Series & Shows
        if (titleLower.contains("episode") || titleLower.contains("season ") || titleLower.contains("series") || titleLower.contains("web series") || titleLower.contains(" ep ") || titleLower.contains("ep.")) {
            detected.add(TagInfo("series", "Series", "📺", 20))
        }

        // 3b. Action, Anime, Romance, Reality & Genres
        if (titleLower.contains("anime") || titleLower.contains("manga") || titleLower.contains("shonen") || titleLower.contains("isekai") || fullText.contains("subbed")) {
            detected.add(TagInfo("anime", "Anime", "🎌", 12))
        }
        if (titleLower.contains("action") || titleLower.contains("fight") || titleLower.contains("battle") || titleLower.contains("combat") || titleLower.contains("stunt") || titleLower.contains("chase")) {
            detected.add(TagInfo("action", "Action", "💥", 12))
        }
        if (titleLower.contains("romance") || titleLower.contains("romantic") || titleLower.contains("love story") || titleLower.contains("relationship") || titleLower.contains("dating")) {
            detected.add(TagInfo("romance", "Romance", "💕", 14))
        }
        if (titleLower.contains("reality") || titleLower.contains("bigg boss") || titleLower.contains("big boss") || titleLower.contains("vlog") || titleLower.contains("influencer") || titleLower.contains("drama")) {
            detected.add(TagInfo("reality", "Reality & Drama", "🎭", 14))
        }
        if (titleLower.contains("sci-fi") || titleLower.contains("scifi") || titleLower.contains("fantasy") || titleLower.contains("superhero") || titleLower.contains("marvel") || titleLower.contains("dc")) {
            detected.add(TagInfo("scifi", "Sci-Fi & Fantasy", "⚡", 14))
        }

        // 4. Video Essays & Deep Dives / Philosophy
        if (titleLower.contains("essay") || titleLower.contains("deep dive") || titleLower.contains("retrospective") || 
            titleLower.contains("why ") || titleLower.contains("sin ") || titleLower.contains("life") || titleLower.contains("monsters") || 
            titleLower.contains("philosophy") || titleLower.contains("psychology") || titleLower.contains("humanity") || 
            titleLower.contains("meaning") || titleLower.contains("morality") || titleLower.contains("tragedy")) {
            
            if (titleLower.contains("essay") || titleLower.contains("deep dive") || titleLower.contains("analysis")) {
                detected.add(TagInfo("video_essay", "Video Essay", "📖", 15))
            }
            if (titleLower.contains("philosophy") || titleLower.contains("sin") || titleLower.contains("life") || titleLower.contains("monsters") || titleLower.contains("meaning")) {
                detected.add(TagInfo("philosophy", "Philosophy", "🧠", 16))
            }
        }

        // 5. Gaming & Gameplay
        if (titleLower.contains("gameplay") || titleLower.contains("apex") || titleLower.contains("kills") || titleLower.contains("damage") ||
            titleLower.contains("gta") || titleLower.contains("minecraft") || titleLower.contains("roblox") || titleLower.contains("fortnite") ||
            titleLower.contains("cod ") || titleLower.contains("valorant") || titleLower.contains("ps5") || titleLower.contains("xbox") ||
            titleLower.contains("walkthrough") || titleLower.contains("esports") || uploaderLower.contains("apex") || uploaderLower.contains("gaming")) {
            
            detected.add(TagInfo("gaming", "Gaming", "🎮", 15))
            if (titleLower.contains("gameplay") || titleLower.contains("kills") || titleLower.contains("solo") || titleLower.contains("walkthrough")) {
                detected.add(TagInfo("gameplay", "Gameplay", "🕹️", 18))
            }
        }

        // 6. Podcasts & Talk Shows
        if (titleLower.contains("podcast") || titleLower.contains("joe rogan") || titleLower.contains("lex fridman") || 
            titleLower.contains("huberman") || titleLower.contains("interview") || uploaderLower.contains("podcast") || titleLower.contains("talk show")) {
            detected.add(TagInfo("podcast", "Podcast", "🎙️", 15))
        }

        // 7. Comedy, Stand-up & Funny
        if (titleLower.contains("stand up") || titleLower.contains("stand-up") || titleLower.contains("funny") || 
            titleLower.contains("parody") || titleLower.contains("comedy") || titleLower.contains("prank") || 
            titleLower.contains("roast") || titleLower.contains("sketch") || titleLower.contains("meme") || titleLower.contains("humor")) {
            detected.add(TagInfo("comedy", "Comedy", "🎭", 20))
        }

        // 8. Documentaries
        if (titleLower.contains("documentary") || titleLower.contains("docuseries") || titleLower.contains("untold story") || titleLower.contains("history of")) {
            detected.add(TagInfo("documentary", "Documentary", "🍿", 20))
        }

        // 9. Tech, AI & Coding
        if (titleLower.contains("unboxing") || titleLower.contains("iphone") || titleLower.contains("samsung") || titleLower.contains("pixel") ||
            titleLower.contains("review") && (fullText.contains("phone") || fullText.contains("gpu") || fullText.contains("laptop") || fullText.contains("macbook")) ||
            titleLower.contains("tech") || titleLower.contains("gadgets") || uploaderLower.contains("mkbhd") || uploaderLower.contains("boss")) {
            detected.add(TagInfo("tech", "Tech", "💻", 25))
        }
        if (titleLower.contains("ai ") || titleLower.contains("chatgpt") || titleLower.contains("gemini") || titleLower.contains("artificial intelligence") || titleLower.contains("coding") || titleLower.contains("programming")) {
            detected.add(TagInfo("ai", "AI & Tech", "🤖", 22))
        }

        // 10. News & Geopolitics
        if (titleLower.contains("news") || titleLower.contains("breaking") || uploaderLower.contains("news") || uploaderLower.contains("bbc") || uploaderLower.contains("cnn")) {
            detected.add(TagInfo("news", "News", "📰", 25))
        }
        if (titleLower.contains("geopolitics") || titleLower.contains("world affairs") || titleLower.contains("military") || titleLower.contains("defense") || titleLower.contains("war ")) {
            detected.add(TagInfo("world_affairs", "World Affairs", "🌐", 25))
        }

        // 11. Learning, Science & Education
        if (titleLower.contains("explained") || titleLower.contains("how to") || titleLower.contains("tutorial") || titleLower.contains("guide") || titleLower.contains("learn") || uploaderLower.contains("academy")) {
            detected.add(TagInfo("education", "Education", "📚", 25))
        }
        if (titleLower.contains("science") || titleLower.contains("physics") || titleLower.contains("space") || titleLower.contains("nasa") || titleLower.contains("quantum")) {
            detected.add(TagInfo("science", "Science", "🔬", 25))
        }

        // 12. Reaction & Reviews
        if (titleLower.contains("reaction") || titleLower.contains("reacts")) {
            detected.add(TagInfo("reaction", "Reaction", "😲", 25))
        } else if (titleLower.contains("review")) {
            detected.add(TagInfo("review", "Review", "⭐", 25))
        }

        // 13. Sports
        if (titleLower.contains("cricket") || titleLower.contains("football") || titleLower.contains("soccer") || titleLower.contains("nba") || titleLower.contains("match highlights")) {
            detected.add(TagInfo("sports", "Sports", "⚽", 25))
        }

        // 14. Music & Audio
        if (titleLower.contains("official music video") || titleLower.contains("song") || titleLower.contains("soundtrack") || titleLower.contains("ost") || titleLower.contains("lyrics") || titleLower.contains("live concert")) {
            detected.add(TagInfo("music", "Music", "🎵", 25))
        }

        // 15. Automotive & Food
        if (titleLower.contains("tesla") || titleLower.contains("supercar") || titleLower.contains("car review") || titleLower.contains("drive") || titleLower.contains("ev ")) {
            detected.add(TagInfo("auto", "Auto", "🚗", 30))
        }
        if (titleLower.contains("recipe") || titleLower.contains("cooking") || titleLower.contains("street food") || titleLower.contains("food")) {
            detected.add(TagInfo("food", "Food", "🍔", 30))
        }

        // Smart Fallback if still empty: ONLY pick standard meaningful categories, NEVER random title words!
        if (detected.isEmpty()) {
            when {
                titleLower.contains("how") || titleLower.contains("what") || titleLower.contains("why") ->
                    detected.add(TagInfo("discovery", "Deep Dive", "📚", 90))
                video.durationSeconds > 1800 ->
                    detected.add(TagInfo("feature", "Feature", "🎞️", 90))
                else ->
                    detected.add(TagInfo("video", "Watch", "📺", 100))
            }
        }

        // Sort by priority and limit to max 2 top tags per video for clean UI
        return detected.distinctBy { it.category }.sortedBy { it.priority }.take(2)
    }

    /**
     * Builds smart category chip items with counts for a playlist / watch later collection
     */
    fun buildSmartTagChips(videos: List<VideoItem>): List<SmartTagChip> {
        val tagCountMap = mutableMapOf<String, Pair<TagInfo, Int>>()

        videos.forEach { video ->
            val tags = extractTags(video)
            tags.forEach { tagInfo ->
                val current = tagCountMap[tagInfo.category]
                if (current == null) {
                    tagCountMap[tagInfo.category] = Pair(tagInfo, 1)
                } else {
                    tagCountMap[tagInfo.category] = Pair(current.first, current.second + 1)
                }
            }
        }

        val result = mutableListOf<SmartTagChip>()
        // Always include "All" at the start
        result.add(SmartTagChip(key = "all", label = "All", emoji = "•", count = videos.size))

        // Sort tags by frequency (descending) and then priority
        val sortedTags = tagCountMap.values
            .sortedWith(compareByDescending<Pair<TagInfo, Int>> { it.second }.thenBy { it.first.priority })

        for ((tagInfo, count) in sortedTags) {
            result.add(
                SmartTagChip(
                    key = tagInfo.category,
                    label = tagInfo.displayName,
                    emoji = tagInfo.emoji,
                    count = count
                )
            )
        }

        return result
    }

    /**
     * Matches a video against a smart tag filter key
     */
    fun matchesTag(video: VideoItem, tagKey: String): Boolean {
        if (tagKey == "all" || tagKey.isBlank()) return true
        val tags = extractTags(video)
        return tags.any { it.category.equals(tagKey, ignoreCase = true) || it.displayName.equals(tagKey, ignoreCase = true) }
    }
}

