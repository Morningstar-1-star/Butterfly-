package com.example.util

import androidx.compose.ui.graphics.Color
import com.example.model.*
import java.text.SimpleDateFormat
import java.util.*

object PersonalityBadgeEngine {

    private val GENRE_COLORS = mapOf(
        "Anime & Animation" to Color(0xFFFF4081),
        "Horror & Thriller" to Color(0xFFE53935),
        "Cinema & Movies" to Color(0xFFFFB300),
        "Action & Adventure" to Color(0xFFFF6D00),
        "Sci-Fi & Cyberpunk" to Color(0xFF00E5FF),
        "Fantasy & Magic" to Color(0xFFAB47BC),
        "Romance & Drama" to Color(0xFFEC407A),
        "Gaming & Esports" to Color(0xFF76FF03),
        "Tech & Science" to Color(0xFF29B6F6),
        "Music & Audio" to Color(0xFF8E24AA),
        "Adult & 18+" to Color(0xFFFF1744),
        "General & Other" to Color(0xFF78909C)
    )

    fun calculateProfile(
        watchHistory: List<VideoItem>,
        watchProgressMap: Map<String, Float>,
        watchLaterList: List<VideoItem>,
        likedVideoIds: Set<String>,
        playlists: List<UserPlaylist>
    ): PersonalityProfile {
        val totalHistoryCount = watchHistory.size
        var totalMinutes = 0L

        // Genre tracking maps
        val genreCounts = mutableMapOf<String, Int>()
        val genreDurations = mutableMapOf<String, Long>()

        for (video in watchHistory) {
            val progress = watchProgressMap[video.id] ?: 0.5f
            val durSecs = (video.durationSeconds ?: 600L).coerceIn(60L, 10800L)
            val watchedSecs = (durSecs * progress).toLong()
            val mins = watchedSecs / 60
            totalMinutes += mins

            val detectedGenres = detectGenresForVideo(video)
            for (g in detectedGenres) {
                genreCounts[g] = (genreCounts[g] ?: 0) + 1
                genreDurations[g] = (genreDurations[g] ?: 0L) + mins
            }
        }

        // Include liked videos and watch later into taste analysis
        for (video in watchLaterList) {
            val detectedGenres = detectGenresForVideo(video)
            for (g in detectedGenres) {
                genreCounts[g] = (genreCounts[g] ?: 0) + 1
            }
        }

        // If history is empty, populate gentle default beginner seed
        if (genreCounts.isEmpty()) {
            genreCounts["Anime & Animation"] = 2
            genreCounts["Cinema & Movies"] = 3
            genreCounts["Action & Adventure"] = 2
            genreCounts["Sci-Fi & Cyberpunk"] = 1
            totalMinutes = 45
        }

        val totalGenreHits = genreCounts.values.sum().coerceAtLeast(1)

        val stats = genreCounts.map { (genre, count) ->
            val percentage = (count.toFloat() / totalGenreHits) * 100f
            GenreConsumptionStat(
                genreName = genre,
                count = count,
                percentage = percentage,
                color = GENRE_COLORS[genre] ?: Color(0xFF00E5FF)
            )
        }.sortedByDescending { it.count }

        val dominantGenre = stats.firstOrNull()?.genreName ?: "Cinema & Movies"

        val (archetype, desc, emoji) = when (dominantGenre) {
            "Anime & Animation" -> Triple("Supreme Anime Otaku & Realm Wanderer", "Master of Japanese animation, manga adaptations, and fantasy worlds.", "🌸")
            "Horror & Thriller" -> Triple("Midnight Thriller & Horror Sleuth", "Fearless consumer of dark mysteries, suspense thrillers, and paranormal tales.", "🕯️")
            "Cinema & Movies" -> Triple("Grandmaster Cinephile & Film Critic", "Deep lover of feature cinema, directorial vision, and box office classics.", "🎬")
            "Action & Adventure" -> Triple("High-Octane Action Virtuoso", "Thrill-seeker drawn to intense combat, heroic epics, and wild adventures.", "⚡")
            "Sci-Fi & Cyberpunk" -> Triple("Cyberpunk & Cosmic Explorer", "Futuristic visionary exploring space exploration, AI, and neon dystopias.", "🚀")
            "Fantasy & Magic" -> Triple("Archmage of Myth & Fantasy", "Enchanted by legendary lore, mythical creatures, and ancient kingdoms.", "🔮")
            "Romance & Drama" -> Triple("Romantic Heart & Drama Connoisseur", "Moved by deep human connections, love stories, and emotional journeys.", "💖")
            "Gaming & Esports" -> Triple("Apex Esports Gamer & Strategist", "Master of pro walkthroughs, gaming streams, and virtual arenas.", "🎮")
            "Tech & Science" -> Triple("Tech Innovator & Science Prodigy", "Curious mind diving into cutting-edge tech, documentaries, and engineering.", "🔬")
            "Music & Audio" -> Triple("Harmonic Audiophile & Melody Seeker", "In tune with sonic landscapes, music videos, and rhythm masterclasses.", "🎵")
            "Adult & 18+" -> Triple("After-Hours Midnight Voyager", "Unfiltered explorer of mature, late-night adult entertainment.", "🔥")
            else -> Triple("Omniverse Omnivore & Cultural Sage", "Eclectic viewer with balanced tastes spanning all cinematic genres.", "🌌")
        }

        // Generate Genre Badges
        val allGenres = listOf(
            "Anime & Animation" to "Mastery over anime, animations, and serialized visual art",
            "Horror & Thriller" to "Bravery across psychological thrillers, horror, and suspense",
            "Cinema & Movies" to "Dedication to full-length movies, Hollywood, and global cinema",
            "Action & Adventure" to "Adrenaline-fueled appetite for blockbuster action and quests",
            "Sci-Fi & Cyberpunk" to "Fascination with futuristic science fiction and cyber realms",
            "Fantasy & Magic" to "Immersion into magical worlds, epic sagas, and mythologies",
            "Romance & Drama" to "Passion for heartwarming romances and compelling drama",
            "Gaming & Esports" to "Leveling up through gaming streams and tournament plays",
            "Tech & Science" to "Expanding knowledge through science, computing, and deep dives",
            "Music & Audio" to "Grooving with concerts, music videos, and soundscapes",
            "Adult & 18+" to "Late night adult viewing and mature collections"
        )

        val genreBadges = allGenres.map { (gName, gDesc) ->
            val count = genreCounts[gName] ?: 0
            val duration = genreDurations[gName] ?: (count * 15L)
            val xp = (count * 25) + (duration * 2).toInt()

            val tier = when {
                xp >= BadgeTier.DIAMOND.minXp -> BadgeTier.DIAMOND
                xp >= BadgeTier.PLATINUM.minXp -> BadgeTier.PLATINUM
                xp >= BadgeTier.GOLD.minXp -> BadgeTier.GOLD
                xp >= BadgeTier.SILVER.minXp -> BadgeTier.SILVER
                else -> BadgeTier.BRONZE
            }

            val nextTier = when (tier) {
                BadgeTier.BRONZE -> BadgeTier.SILVER
                BadgeTier.SILVER -> BadgeTier.GOLD
                BadgeTier.GOLD -> BadgeTier.PLATINUM
                BadgeTier.PLATINUM -> BadgeTier.DIAMOND
                BadgeTier.DIAMOND -> null
            }

            val progress = if (nextTier != null) {
                val currentBase = tier.minXp
                val target = nextTier.minXp
                ((xp - currentBase).toFloat() / (target - currentBase)).coerceIn(0f, 1f)
            } else 1f

            val needed = if (nextTier != null) (nextTier.minXp - xp).coerceAtLeast(0) else 0

            GenreBadge(
                id = gName.lowercase().replace("[^a-z]".toRegex(), "_"),
                name = gName,
                description = gDesc,
                iconName = gName,
                currentXp = xp,
                totalVideosWatched = count,
                totalMinutesWatched = duration,
                currentTier = tier,
                nextTier = nextTier,
                progressToNextTier = progress,
                xpNeededForNextTier = needed
            )
        }

        // Generate Milestone Achievements
        val totalPlaylistsCount = playlists.size
        val totalLikedCount = likedVideoIds.size
        val distinctGenresExplored = genreCounts.keys.size
        val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val todayStr = dateFormat.format(Date())

        val milestoneAchievements = listOf(
            MilestoneAchievement(
                id = "first_flight",
                title = "First Flight",
                description = "Watch your very first video or stream.",
                iconEmoji = "🎬",
                category = "Explorer",
                isUnlocked = totalHistoryCount >= 1,
                currentProgress = totalHistoryCount.coerceAtMost(1),
                maxProgress = 1,
                unlockedDate = if (totalHistoryCount >= 1) todayStr else null,
                rarity = "Common",
                xpReward = 50
            ),
            MilestoneAchievement(
                id = "genre_hopper",
                title = "Genre Hopper",
                description = "Explore videos across 4 different genres.",
                iconEmoji = "🧭",
                category = "Discovery",
                isUnlocked = distinctGenresExplored >= 4,
                currentProgress = distinctGenresExplored.coerceAtMost(4),
                maxProgress = 4,
                unlockedDate = if (distinctGenresExplored >= 4) todayStr else null,
                rarity = "Rare",
                xpReward = 150
            ),
            MilestoneAchievement(
                id = "marathon_watcher",
                title = "Marathon Binge",
                description = "Accumulate over 2 hours (120 mins) of watch time.",
                iconEmoji = "⏱️",
                category = "Dedication",
                isUnlocked = totalMinutes >= 120,
                currentProgress = totalMinutes.coerceAtMost(120).toInt(),
                maxProgress = 120,
                unlockedDate = if (totalMinutes >= 120) todayStr else null,
                rarity = "Epic",
                xpReward = 300
            ),
            MilestoneAchievement(
                id = "cast_sleuth",
                title = "Cast Sleuth",
                description = "Inspect top cast profiles and actor filmographies.",
                iconEmoji = "🎭",
                category = "Cinema",
                isUnlocked = true, // Unlocked as feature is ready
                currentProgress = 1,
                maxProgress = 1,
                unlockedDate = todayStr,
                rarity = "Common",
                xpReward = 75
            ),
            MilestoneAchievement(
                id = "master_curator",
                title = "Master Curator",
                description = "Create 2 or more custom playlists.",
                iconEmoji = "📁",
                category = "Library",
                isUnlocked = totalPlaylistsCount >= 2,
                currentProgress = totalPlaylistsCount.coerceAtMost(2),
                maxProgress = 2,
                unlockedDate = if (totalPlaylistsCount >= 2) todayStr else null,
                rarity = "Rare",
                xpReward = 200
            ),
            MilestoneAchievement(
                id = "taste_collector",
                title = "Taste Collector",
                description = "Like or bookmark at least 5 videos/movies.",
                iconEmoji = "❤️",
                category = "Community",
                isUnlocked = (totalLikedCount + watchLaterList.size) >= 5,
                currentProgress = (totalLikedCount + watchLaterList.size).coerceAtMost(5),
                maxProgress = 5,
                unlockedDate = if ((totalLikedCount + watchLaterList.size) >= 5) todayStr else null,
                rarity = "Rare",
                xpReward = 150
            ),
            MilestoneAchievement(
                id = "grandmaster_voyage",
                title = "Grandmaster Voyager",
                description = "Reach Diamond tier in any genre badge.",
                iconEmoji = "👑",
                category = "Mastery",
                isUnlocked = genreBadges.any { it.currentTier == BadgeTier.DIAMOND },
                currentProgress = if (genreBadges.any { it.currentTier == BadgeTier.DIAMOND }) 1 else 0,
                maxProgress = 1,
                unlockedDate = null,
                rarity = "Legendary",
                xpReward = 1000
            )
        )

        val totalXp = genreBadges.sumOf { it.currentXp } + milestoneAchievements.filter { it.isUnlocked }.sumOf { it.xpReward }
        val globalLevel = (totalXp / 150) + 1
        val unlockedBadgesCount = milestoneAchievements.count { it.isUnlocked } + genreBadges.count { it.currentTier.levelNumber >= 2 }

        return PersonalityProfile(
            dominantArchetype = archetype,
            archetypeDescription = desc,
            archetypeEmoji = emoji,
            totalWatchTimeMinutes = totalMinutes,
            totalVideosCompleted = totalHistoryCount,
            totalBadgesUnlocked = unlockedBadgesCount,
            totalXp = totalXp,
            globalLevel = globalLevel,
            genreDistribution = stats,
            genreBadges = genreBadges,
            milestoneAchievements = milestoneAchievements
        )
    }

    private fun detectGenresForVideo(video: VideoItem): List<String> {
        val text = "${video.title ?: ""} ${video.uploaderName ?: ""} ${video.description ?: ""}".lowercase()
        val genres = mutableListOf<String>()

        if (text.contains("anime") || text.contains("manga") || text.contains("shonen") || text.contains("jikan") || text.contains("anilist") || text.contains("naruto") || text.contains("one piece") || text.contains("jujutsu") || text.contains("animation") || text.contains("cartoon")) {
            genres.add("Anime & Animation")
        }
        if (text.contains("horror") || text.contains("scary") || text.contains("ghost") || text.contains("creepy") || text.contains("thriller") || text.contains("murder") || text.contains("mystery") || text.contains("suspense") || text.contains("dark")) {
            genres.add("Horror & Thriller")
        }
        if (text.contains("movie") || text.contains("film") || text.contains("cinema") || text.contains("trailer") || text.contains("hollywood") || text.contains("bollywood") || text.contains("tmdb") || text.contains("imdb")) {
            genres.add("Cinema & Movies")
        }
        if (text.contains("action") || text.contains("fight") || text.contains("martial") || text.contains("superhero") || text.contains("marvel") || text.contains("dc ") || text.contains("adventure") || text.contains("mission")) {
            genres.add("Action & Adventure")
        }
        if (text.contains("sci-fi") || text.contains("space") || text.contains("cyberpunk") || text.contains("alien") || text.contains("matrix") || text.contains("galaxy") || text.contains("future")) {
            genres.add("Sci-Fi & Cyberpunk")
        }
        if (text.contains("fantasy") || text.contains("magic") || text.contains("dragon") || text.contains("witch") || text.contains("wizard") || text.contains("lord of the rings") || text.contains("myth")) {
            genres.add("Fantasy & Magic")
        }
        if (text.contains("romance") || text.contains("love") || text.contains("drama") || text.contains("relationship") || text.contains("couple") || text.contains("k-drama") || text.contains("kdrama")) {
            genres.add("Romance & Drama")
        }
        if (text.contains("gaming") || text.contains("gameplay") || text.contains("walkthrough") || text.contains("speedrun") || text.contains("esports") || text.contains("gta") || text.contains("minecraft") || text.contains("fortnite")) {
            genres.add("Gaming & Esports")
        }
        if (text.contains("tech") || text.contains("science") || text.contains("documentary") || text.contains("gadget") || text.contains("review") || text.contains("coding") || text.contains("phone") || text.contains("computer")) {
            genres.add("Tech & Science")
        }
        if (text.contains("music") || text.contains("song") || text.contains("official audio") || text.contains("lyric") || text.contains("remix") || text.contains("album") || text.contains("live concert")) {
            genres.add("Music & Audio")
        }
        if (video.providerId in listOf("eporner", "pornhub", "xvideos", "xhamster", "redtube", "youporn", "beeg", "4tube", "rule34video") || text.contains("18+") || text.contains("nsfw") || text.contains("adult")) {
            genres.add("Adult & 18+")
        }

        if (genres.isEmpty()) {
            genres.add("General & Other")
        }

        return genres
    }
}
