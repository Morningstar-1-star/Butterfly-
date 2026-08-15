package com.example.util

import com.example.model.VideoItem
import java.util.regex.Pattern

data class VideoCategory(
    val id: String,
    val name: String,
    val emoji: String,
    val priority: Int = 100
)

object VideoCategoryClassifier {

    val CATEGORY_ALL = VideoCategory("all", "All", "🏷️", 0)
    val CATEGORY_MOVIES = VideoCategory("movies", "Movies & TV", "🎬", 1)
    val CATEGORY_GAMING = VideoCategory("gaming", "Gaming", "🎮", 2)
    val CATEGORY_MUSIC = VideoCategory("music", "Music", "🎵", 3)
    val CATEGORY_COMEDY = VideoCategory("comedy", "Comedy & Memes", "😂", 4)
    val CATEGORY_TECH = VideoCategory("tech", "Tech & Science", "💻", 5)
    val CATEGORY_LEARN = VideoCategory("learn", "Learn & How-To", "💡", 6)
    val CATEGORY_TRAVEL = VideoCategory("travel", "Travel & Adventure", "🌍", 7)
    val CATEGORY_PODCAST = VideoCategory("podcast", "Podcast & Talk", "🎙️", 8)
    val CATEGORY_NEWS = VideoCategory("news", "News & World", "📰", 9)
    val CATEGORY_ARCHIVE = VideoCategory("archive", "Archive & History", "🏛️", 10)
    val CATEGORY_ADULT = VideoCategory("adult", "18+ Adult", "🔞", 11)
    val CATEGORY_OTHER = VideoCategory("other", "General", "📌", 99)

    // Regex helpers with exact word boundary matching to eliminate false positives
    private fun containsWord(text: String, vararg words: String): Boolean {
        for (word in words) {
            val pattern = "\\b" + Pattern.quote(word.lowercase()) + "\\b"
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(text).find()) {
                return true
            }
        }
        return false
    }

    private fun containsPhrase(text: String, vararg phrases: String): Boolean {
        val lower = text.lowercase()
        for (phrase in phrases) {
            if (lower.contains(phrase.lowercase())) {
                return true
            }
        }
        return false
    }

    fun classify(video: VideoItem): List<VideoCategory> {
        val title = video.title.lowercase()
        val channel = (video.uploaderName ?: "").lowercase()
        val tagsStr = video.tags.joinToString(" ").lowercase()
        val combined = "$title $channel $tagsStr"
        val provider = (video.providerId ?: "").lowercase()

        val scoredCategories = mutableListOf<Pair<VideoCategory, Int>>()

        // 1. Adult Content
        if (provider.contains("eporner") || provider.contains("pornhub") || provider.contains("xvideos") ||
            containsWord(combined, "nsfw", "xxx", "hentai", "porn", "erotic", "18+")) {
            scoredCategories.add(CATEGORY_ADULT to 100)
        }

        // 2. Movies & TV / Trailers (Check FIRST before Tech to prevent false-positives on trailers)
        var movieScore = 0
        if (containsPhrase(combined, "official trailer", "teaser trailer", "teaser", "special look", "final trailer",
                "movie trailer", "season 1", "season 2", "season 3", "season 4", "season 5",
                "episode 1", "episode 2", "full movie", "official clip", "sneak peek",
                "box office", "star wars", "marvel studios", "marvel entertainment", "lucasfilm",
                "disney+", "netflix film", "warner bros", "sony pictures", "paramount pictures",
                "universal pictures", "20th century", "cinematic trailer", "short film", "feature film",
                "alexander korda")) {
            movieScore += 40
        }
        if (containsWord(combined, "trailer", "teaser", "movie", "film", "cinema", "s01", "s02", "s03", "e01", "e02", "e03",
                "season", "episode", "hollywood", "bollywood", "bluray", "remux", "web-dl", "1080p", "4k", "dvdrip")) {
            movieScore += 25
        }
        if (channel.contains("marvel") || channel.contains("star wars") || channel.contains("disney") ||
            channel.contains("warner") || channel.contains("sony pictures") || channel.contains("netflix") ||
            channel.contains("paramount") || channel.contains("universal") || channel.contains("hbo") ||
            channel.contains("a24") || channel.contains("mgm") || channel.contains("korda")) {
            movieScore += 35
        }
        if (movieScore > 0) scoredCategories.add(CATEGORY_MOVIES to movieScore)

        // 3. Gaming
        var gamingScore = 0
        if (containsPhrase(combined, "kingdom hearts", "gameplay", "walkthrough", "let's play", "playthrough",
                "boss fight", "speedrun", "esports", "d23", "game trailer", "release date reaction",
                "ps5 gameplay", "xbox series", "nintendo switch", "pc gameplay", "joe bart games",
                "thegamersjoint", "ign", "gamespot", "minecraft", "gta 5", "gta 6", "fortnite",
                "roblox", "pokemon", "zelda", "elden ring", "valorant", "call of duty", "league of legends")) {
            gamingScore += 45
        }
        if (containsWord(combined, "gameplay", "gaming", "gamer", "esports", "ps5", "ps4", "xbox", "nintendo",
                "switch", "steam", "rtx", "playthrough", "speedrun", "modded", "glitch", "arcade", "rpg", "fps")) {
            gamingScore += 25
        }
        if (channel.contains("game") || channel.contains("gaming") || channel.contains("gamersjoint") ||
            channel.contains("ign") || channel.contains("gamespot") || channel.contains("nintendo") ||
            channel.contains("playstation") || channel.contains("xbox")) {
            gamingScore += 30
        }
        if (gamingScore > 0) scoredCategories.add(CATEGORY_GAMING to gamingScore)

        // 4. Music & Audio
        var musicScore = 0
        if (containsPhrase(combined, "official music video", "official audio", "lyric video", "lyrics video",
                "full song", "soundtrack", "original soundtrack", "ost", "live performance",
                "acoustic version", "audio track", "remix", "cover song", "music video")) {
            musicScore += 45
        }
        if (containsWord(combined, "music", "song", "lyrics", "album", "vevo", "audio", "remix", "orchestra",
                "beats", "instrumental", "acoustic", "concert", "singing", "guitar", "piano", "vocals", "rap", "hiphop", "pop", "rock", "jazz")) {
            musicScore += 20
        }
        if (channel.endsWith("- topic") || channel.contains("vevo") || channel.contains("records") ||
            channel.contains("music") || channel.contains("t-series") || provider.contains("music")) {
            musicScore += 35
        }
        if (musicScore > 0) scoredCategories.add(CATEGORY_MUSIC to musicScore)

        // 5. Comedy & Memes / Animation
        var comedyScore = 0
        if (containsPhrase(combined, "skibidi toilet", "skibidi", "funny moments", "try not to laugh",
                "stand up comedy", "standup comedy", "parody", "bloopers", "meme compilation")) {
            comedyScore += 45
        }
        if (containsWord(combined, "skibidi", "funny", "comedy", "standup", "meme", "memes", "parody",
                "prank", "joke", "jokes", "hilarious", "humor", "animation", "animated", "cartoon", "anime")) {
            comedyScore += 25
        }
        if (channel.contains("skibidi") || channel.contains("smosh") || channel.contains("comedy") ||
            channel.contains("meme") || channel.contains("funny")) {
            comedyScore += 30
        }
        if (comedyScore > 0) scoredCategories.add(CATEGORY_COMEDY to comedyScore)

        // 6. Tech & Science (CRITICAL: Strict word-bounded matching so "ai" only matches standalone word!)
        var techScore = 0
        if (containsPhrase(combined, "artificial intelligence", "machine learning", "deep learning",
                "large language model", "generative ai", "apple vision pro", "macbook pro",
                "samsung galaxy", "iphone 16", "iphone 15", "linus tech tips", "marques brownlee",
                "mkbhd", "unboxing & review", "full review", "hands-on review", "hardware review")) {
            techScore += 40
        }
        if (containsWord(combined, "tech", "technology", "ai", "gpt", "chatgpt", "llm", "gemini",
                "smartphone", "processor", "gpu", "cpu", "chipset", "nvidia", "amd", "intel", "snapdragon",
                "coding", "programming", "python", "kotlin", "javascript", "linux", "robotics",
                "spacex", "nasa", "isro", "astronomy", "quantum", "gadget", "gadgets", "telecom", "cybersecurity")) {
            techScore += 25
        }
        if (channel.contains("mkbhd") || channel.contains("tech") || channel.contains("verge") ||
            channel.contains("linus") || channel.contains("dave2d") || channel.contains("engine")) {
            techScore += 30
        }
        if (techScore > 0) scoredCategories.add(CATEGORY_TECH to techScore)

        // 7. Travel, Nature & Adventure
        var travelScore = 0
        if (containsPhrase(combined, "travel vlog", "scenic 4k", "4k relaxation", "drone 4k",
                "national geographic", "nature documentary", "walking tour", "travel guide")) {
            travelScore += 40
        }
        if (containsWord(combined, "adventure", "travel", "vlog", "nature", "wildlife", "ocean",
                "mountain", "mountains", "hiking", "forest", "safari", "island", "scenic", "roadtrip")) {
            travelScore += 25
        }
        if (travelScore > 0) scoredCategories.add(CATEGORY_TRAVEL to travelScore)

        // 8. Learn & How-To
        var learnScore = 0
        if (containsPhrase(combined, "how to", "step by step", "crash course", "complete guide",
                "explained in 5 minutes", "for beginners", "tutorial for", "science experiment")) {
            learnScore += 40
        }
        if (containsWord(combined, "tutorial", "guide", "explained", "lecture", "course", "lesson",
                "education", "learn", "study", "exam", "upsc", "physics", "math", "chemistry", "biology")) {
            learnScore += 25
        }
        if (learnScore > 0) scoredCategories.add(CATEGORY_LEARN to learnScore)

        // 9. Podcasts & Talk
        var podcastScore = 0
        if (containsPhrase(combined, "full podcast", "podcast episode", "full interview", "talk show",
                "joe rogan", "lex fridman", "huberman lab", "deep dive discussion")) {
            podcastScore += 40
        }
        if (containsWord(combined, "podcast", "podcasts", "interview", "interviews", "roundtable", "q&a", "ama")) {
            podcastScore += 25
        }
        if (podcastScore > 0) scoredCategories.add(CATEGORY_PODCAST to podcastScore)

        // 10. News & World Geopolitics
        var newsScore = 0
        if (containsPhrase(combined, "breaking news", "world news", "foreign policy", "united nations",
                "press conference", "military conflict", "geopolitics", "defense analysis")) {
            newsScore += 40
        }
        if (containsWord(combined, "news", "breaking", "geopolitics", "diplomacy", "military", "elections",
                "parliament", "congress", "investigation", "journalism", "report", "bbc", "cnn", "reuters", "aljazeera", "wion")) {
            newsScore += 25
        }
        if (newsScore > 0) scoredCategories.add(CATEGORY_NEWS to newsScore)

        // 11. Archive & History
        var archiveScore = 0
        if (containsPhrase(combined, "public domain", "archive.org", "classic film", "historical footage",
                "silent film", "vintage cinema", "black and white movie")) {
            archiveScore += 40
        }
        if (containsWord(combined, "archive", "vintage", "antique", "history", "historical", "1920s", "1930s", "1940s", "1950s") ||
            provider.contains("archive")) {
            archiveScore += 25
        }
        if (archiveScore > 0) scoredCategories.add(CATEGORY_ARCHIVE to archiveScore)

        if (scoredCategories.isEmpty()) {
            return listOf(CATEGORY_OTHER)
        }

        // Return sorted by score (descending) and priority
        return scoredCategories
            .sortedWith(compareByDescending<Pair<VideoCategory, Int>> { it.second }.thenBy { it.first.priority })
            .map { it.first }
    }

    fun getAllCategoriesInList(videos: List<VideoItem>): List<Pair<VideoCategory, Int>> {
        val categoryCounts = mutableMapOf<VideoCategory, Int>()
        categoryCounts[CATEGORY_ALL] = videos.size

        videos.forEach { video ->
            val videoCats = classify(video)
            // Use top 2 categories for count aggregation
            videoCats.take(2).forEach { cat ->
                if (cat.id != CATEGORY_OTHER.id) {
                    categoryCounts[cat] = (categoryCounts[cat] ?: 0) + 1
                }
            }
        }

        return categoryCounts.entries
            .map { Pair(it.key, it.value) }
            .sortedBy { it.first.priority }
    }
}

