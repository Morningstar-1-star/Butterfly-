package com.example.util

import com.example.model.VideoItem

data class VideoCategory(
    val id: String,
    val name: String,
    val emoji: String,
    val priority: Int = 100
)

object VideoCategoryClassifier {

    val CATEGORY_ALL = VideoCategory("all", "All", "🏷️", 0)
    val CATEGORY_MUSIC = VideoCategory("music", "Music", "🎵", 1)
    val CATEGORY_GEOPOLITICAL = VideoCategory("geopolitical", "World & Geo", "🌐", 2)
    val CATEGORY_TECH = VideoCategory("tech", "Tech & Science", "💻", 3)
    val CATEGORY_GAMING = VideoCategory("gaming", "Gaming", "🎮", 4)
    val CATEGORY_MOVIES = VideoCategory("movies", "Movies & TV", "🎬", 5)
    val CATEGORY_LEARN = VideoCategory("learn", "Learn & How-To", "💡", 6)
    val CATEGORY_PODCAST = VideoCategory("podcast", "Podcast & Talk", "🎙️", 7)
    val CATEGORY_NEWS = VideoCategory("news", "News & Info", "📰", 8)
    val CATEGORY_COMEDY = VideoCategory("comedy", "Comedy", "😂", 9)
    val CATEGORY_TORRENT = VideoCategory("torrent", "Torrent & Media", "🧲", 10)
    val CATEGORY_ARCHIVE = VideoCategory("archive", "Archive & History", "🏛️", 11)
    val CATEGORY_ADULT = VideoCategory("adult", "18+ Adult", "🔞", 12)
    val CATEGORY_OTHER = VideoCategory("other", "Other", "📌", 99)

    fun classify(video: VideoItem): List<VideoCategory> {
        val categories = mutableListOf<VideoCategory>()
        val text = "${video.title} ${video.uploaderName} ${video.tags.joinToString(" ")} ${video.providerId}".lowercase()
        val provider = video.providerId?.lowercase() ?: ""

        // 1. Adult Sites
        if (provider.contains("eporner") || provider.contains("pornhub") || provider.contains("xvideos") ||
            text.contains("18+") || text.contains("nsfw") || text.contains("xxx") || text.contains("hentai")) {
            categories.add(CATEGORY_ADULT)
        }

        // 2. Torrents / Movies
        if (provider.contains("torrent") || provider.contains("debrid") || text.contains(".mkv") ||
            text.contains("1080p") || text.contains("4k") || text.contains("bluray") || text.contains("x264") || text.contains("hevc")) {
            categories.add(CATEGORY_TORRENT)
        }

        // 3. Movies & TV
        if (text.contains("official trailer") || text.contains("teaser") || text.contains("full movie") ||
            text.contains("episode") || text.contains("season ") || text.contains("s01") || text.contains("e01") ||
            text.contains("cinematic") || text.contains("film") || text.contains("movie")) {
            categories.add(CATEGORY_MOVIES)
        }

        // 4. Music
        if (provider.contains("music") || text.contains("official music video") || text.contains("official audio") ||
            text.contains("lyric video") || text.contains("lyrics video") || text.contains("full song") ||
            text.contains("vevo") || text.contains("ost") || text.contains("soundtrack") || text.endsWith(" - topic") ||
            text.contains("remix") || text.contains("cover song")) {
            categories.add(CATEGORY_MUSIC)
        }

        // 5. Geopolitical & World
        if (text.contains("geopolitics") || text.contains("world news") || text.contains("foreign policy") ||
            text.contains("military") || text.contains("defense") || text.contains("diplomacy") || text.contains("china") ||
            text.contains("russia") || text.contains("ukraine") || text.contains("india") || text.contains("pakistan") ||
            text.contains("us navy") || text.contains("global powers") || text.contains("strategy")) {
            categories.add(CATEGORY_GEOPOLITICAL)
        }

        // 6. Tech & Science
        if (text.contains("tech") || text.contains("ai") || text.contains("artificial intelligence") ||
            text.contains("smartphone") || text.contains("review") || text.contains("unboxing") || text.contains("space") ||
            text.contains("nasa") || text.contains("isro") || text.contains("engineering") || text.contains("code") ||
            text.contains("python") || text.contains("gadget")) {
            categories.add(CATEGORY_TECH)
        }

        // 7. Gaming
        if (text.contains("gameplay") || text.contains("walkthrough") || text.contains("let's play") ||
            text.contains("esports") || text.contains("ps5") || text.contains("xbox") || text.contains("pc gaming") ||
            text.contains("rtx") || text.contains("steam") || text.contains("gaming")) {
            categories.add(CATEGORY_GAMING)
        }

        // 8. Learn & How-To
        if (text.contains("tutorial") || text.contains("how to") || text.contains("explained") ||
            text.contains("lecture") || text.contains("guide") || text.contains("course") || text.contains("math") ||
            text.contains("science") || text.contains("learn") || text.contains("upsc") || text.contains("exam")) {
            categories.add(CATEGORY_LEARN)
        }

        // 9. Podcast & Interviews
        if (text.contains("podcast") || text.contains("interview") || text.contains("episode") ||
            text.contains("talk show") || text.contains("discussion") || text.contains("qa") || text.contains("q&a")) {
            categories.add(CATEGORY_PODCAST)
        }

        // 10. News & Information
        if (text.contains("news") || text.contains("breaking") || text.contains("documentary") ||
            text.contains("report") || text.contains("journalism") || text.contains("analysis")) {
            categories.add(CATEGORY_NEWS)
        }

        // 11. Comedy
        if (text.contains("funny") || text.contains("comedy") || text.contains("standup") ||
            text.contains("meme") || text.contains("parody") || text.contains("reaction")) {
            categories.add(CATEGORY_COMEDY)
        }

        // 12. Archive.org
        if (provider.contains("archive") || text.contains("archive.org")) {
            categories.add(CATEGORY_ARCHIVE)
        }

        if (categories.isEmpty()) {
            categories.add(CATEGORY_OTHER)
        }

        return categories.sortedBy { it.priority }
    }

    fun getAllCategoriesInList(videos: List<VideoItem>): List<Pair<VideoCategory, Int>> {
        val categoryCounts = mutableMapOf<VideoCategory, Int>()
        categoryCounts[CATEGORY_ALL] = videos.size

        videos.forEach { video ->
            val videoCats = classify(video)
            videoCats.forEach { cat ->
                categoryCounts[cat] = (categoryCounts[cat] ?: 0) + 1
            }
        }

        return categoryCounts.entries
            .map { Pair(it.key, it.value) }
            .sortedBy { it.first.priority }
    }
}
