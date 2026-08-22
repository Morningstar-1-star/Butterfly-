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
        dislikedVideoIds: Set<String> = emptySet(),
        notInterestedVideoIds: Set<String> = emptySet(),
        playlists: List<UserPlaylist>,
        dailyStreak: Int = 1,
        longestStreak: Int = 1
    ): PersonalityProfile {
        val totalHistoryCount = watchHistory.size
        var totalMinutes = 0L

        // Genre tracking maps
        val genreCounts = mutableMapOf<String, Int>()
        val genreDurations = mutableMapOf<String, Long>()

        // Creator tracking maps
        val creatorCounts = mutableMapOf<String, Int>()
        val creatorDurations = mutableMapOf<String, Long>()
        val creatorThumbs = mutableMapOf<String, String?>()
        val creatorGenreVotes = mutableMapOf<String, MutableMap<String, Int>>()

        // Specific metrics
        var completionistCount = 0
        var marathonCount = 0
        var quickSkipCount = 0
        var lateNightVideoCount = 0
        var lateNightMinutes = 0L
        var livestreamCount = 0
        var livestreamMinutes = 0L
        var adultVideoCount = 0

        val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val todayStr = dateFormat.format(Date())

        for ((index, video) in watchHistory.withIndex()) {
            val progress = watchProgressMap[video.id] ?: 0.5f
            val durSecs = (video.durationSeconds ?: 600L).coerceIn(60L, 10800L)
            val watchedSecs = (durSecs * progress).toLong()
            val mins = (watchedSecs / 60).coerceAtLeast(1L)
            totalMinutes += mins

            val detectedGenres = detectGenresForVideo(video)
            for (g in detectedGenres) {
                genreCounts[g] = (genreCounts[g] ?: 0) + 1
                genreDurations[g] = (genreDurations[g] ?: 0L) + mins
            }

            // Creator aggregation
            val creatorName = video.uploaderName?.trim()?.ifBlank { "Unknown Creator" } ?: "Featured Creator"
            creatorCounts[creatorName] = (creatorCounts[creatorName] ?: 0) + 1
            creatorDurations[creatorName] = (creatorDurations[creatorName] ?: 0L) + mins
            if (!video.thumbnailUrl.isNullOrBlank() && !creatorThumbs.containsKey(creatorName)) {
                creatorThumbs[creatorName] = video.thumbnailUrl
            }
            val genMap = creatorGenreVotes.getOrPut(creatorName) { mutableMapOf() }
            for (g in detectedGenres) {
                genMap[g] = (genMap[g] ?: 0) + 1
            }

            // Completionist check (watched >= 85%)
            if (progress >= 0.85f) {
                completionistCount++
            }

            // Marathon check (video >= 45m and watched >= 40%)
            if (durSecs >= 2700L && progress >= 0.40f) {
                marathonCount++
            }

            // Quick skip check (progress <= 0.20f)
            if (progress <= 0.20f && durSecs >= 180L) {
                quickSkipCount++
            }

            // Livestream check
            val isStream = isLivestreamVideo(video)
            if (isStream) {
                livestreamCount++
                livestreamMinutes += mins
            }

            // Adult check
            if (isAdultVideo(video)) {
                adultVideoCount++
            }

            // Late night simulated distribution (or actual night timestamps)
            if (index % 3 == 0 || video.id.hashCode() % 4 == 0) {
                lateNightVideoCount++
                lateNightMinutes += mins
            }
        }

        // Include liked videos and watch later into taste analysis
        for (video in watchLaterList) {
            val detectedGenres = detectGenresForVideo(video)
            for (g in detectedGenres) {
                genreCounts[g] = (genreCounts[g] ?: 0) + 1
            }
            if (isAdultVideo(video)) {
                adultVideoCount++
            }
        }

        // Default beginner seed if history is empty
        if (genreCounts.isEmpty()) {
            genreCounts["Anime & Animation"] = 2
            genreCounts["Cinema & Movies"] = 3
            genreCounts["Action & Adventure"] = 2
            genreCounts["Sci-Fi & Cyberpunk"] = 1
            totalMinutes = 45
            creatorCounts["Cinema World"] = 2
            creatorCounts["Anime Legends"] = 1
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

        // Top Creators Ranking
        val topCreators = creatorCounts.entries.sortedByDescending { it.value }.take(5).map { (creator, count) ->
            val mins = creatorDurations[creator] ?: (count * 12L)
            val favGenre = creatorGenreVotes[creator]?.maxByOrNull { it.value }?.key ?: "Entertainment"
            val (stanRank, level) = when {
                count >= 10 || mins >= 120 -> "👑 Immortal Stan" to 5
                count >= 5 || mins >= 60 -> "💎 Platinum Superfan" to 4
                count >= 3 || mins >= 35 -> "🥇 Dedicated Loyalist" to 3
                count >= 2 || mins >= 15 -> "🥈 Devoted Regular" to 2
                else -> "🥉 Curious Observer" to 1
            }
            CreatorStat(
                creatorName = creator,
                videoCount = count,
                totalMinutes = mins,
                avatarUrl = creatorThumbs[creator],
                stanTitle = stanRank,
                stanLevel = level,
                favoriteGenre = favGenre
            )
        }

        // Circadian stat calculation
        val hourNow = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val morningPct = 20f
        val afternoonPct = 30f
        val eveningPct = 35f
        val nightPct = 15f
        val (dominantTime, dominantDesc) = when {
            hourNow in 0..5 -> "Creature of the Night 🦉" to "Peak viewing happens during midnight & the witching hour when the world sleeps."
            hourNow in 6..11 -> "Early Bird Cinephile 🌅" to "Starting the morning energized with visual storytelling and audio."
            hourNow in 12..17 -> "Midday Cruiser ☀️" to "Enjoying quick sessions and lunch break visual entertainment."
            else -> "Prime-Time Cinephile 🌆" to "Unwinding in the golden hours of the evening with immersive streams."
        }
        val circadianStat = CircadianStat(
            morningPercent = morningPct,
            afternoonPercent = afternoonPct,
            eveningPercent = eveningPct,
            nightPercent = nightPct,
            dominantTimeSlot = dominantTime,
            dominantTimeDesc = dominantDesc
        )

        // Positivity ratio
        val likesCount = likedVideoIds.size
        val dislikesCount = dislikedVideoIds.size
        val notIntCount = notInterestedVideoIds.size
        val totalInteractions = (likesCount + dislikesCount).coerceAtLeast(1)
        val positivityPercent = if (likesCount == 0 && dislikesCount == 0) 100 else ((likesCount.toFloat() / totalInteractions.toFloat()) * 100).toInt().coerceIn(0, 100)

        // Hall of Fame Badges
        val fameBadges = listOf(
            FameShameBadge(
                id = "fame_creator_stan",
                title = "Certified #1 Stan",
                subtitle = topCreators.firstOrNull()?.creatorName ?: "Top Creator",
                description = "Spent dedicated hours watching your most beloved channel.",
                iconEmoji = "🏆",
                isUnlocked = (topCreators.firstOrNull()?.videoCount ?: 0) >= 1,
                tier = "Gold",
                statText = "${topCreators.firstOrNull()?.videoCount ?: 0} videos • ${topCreators.firstOrNull()?.totalMinutes ?: 0}m",
                isShame = false,
                roastOrGloryQuote = "True loyalty never dies. The algorithm acknowledges your fandom.",
                xpReward = 200
            ),
            FameShameBadge(
                id = "fame_daily_streak",
                title = "Streak Champion",
                subtitle = "$dailyStreak-Day Daily Streak",
                description = "Returned to stream on consecutive days without breaking the chain.",
                iconEmoji = "🔥",
                isUnlocked = dailyStreak >= 1,
                tier = if (dailyStreak >= 7) "Legendary" else if (dailyStreak >= 3) "Platinum" else "Gold",
                statText = "$dailyStreak days active (Record: $longestStreak d)",
                isShame = false,
                roastOrGloryQuote = "Consistency is the mother of mastery. You never miss a beat.",
                xpReward = 150
            ),
            FameShameBadge(
                id = "fame_golden_heart",
                title = "The Golden Heart",
                subtitle = "$positivityPercent% Positivity Index",
                description = "Shower creators with thumbs up and pure positive karma.",
                iconEmoji = "💖",
                isUnlocked = likesCount >= 1 || positivityPercent >= 80,
                tier = "Platinum",
                statText = "$likesCount likes given • $positivityPercent% positivity",
                isShame = false,
                roastOrGloryQuote = "Your likes fuel the creative soul of independent creators.",
                xpReward = 120
            ),
            FameShameBadge(
                id = "fame_completionist",
                title = "Credits Connoisseur",
                subtitle = "$completionistCount Full Completions",
                description = "Stayed until the very last frame without skipping ahead.",
                iconEmoji = "🎬",
                isUnlocked = completionistCount >= 1,
                tier = "Gold",
                statText = "$completionistCount videos watched to 100%",
                isShame = false,
                roastOrGloryQuote = "A true viewer respects the editor's cuts and the director's pacing.",
                xpReward = 180
            ),
            FameShameBadge(
                id = "fame_livestream_veteran",
                title = "Live Broadcast Junkie",
                subtitle = "$livestreamCount Streams Watched",
                description = "Immersed in real-time broadcasts, premiers, and gaming streams.",
                iconEmoji = "🔴",
                isUnlocked = livestreamCount >= 1 || totalHistoryCount >= 2,
                tier = "Gold",
                statText = "$livestreamCount streams • ${livestreamMinutes}m live",
                isShame = false,
                roastOrGloryQuote = "There is nothing quite like experiencing the moment live as it happens.",
                xpReward = 160
            ),
            FameShameBadge(
                id = "fame_master_archivist",
                title = "Master Archivist",
                subtitle = "${playlists.size + watchLaterList.size} Saved Collections",
                description = "Crafted structured playlists and bookmarked cinema essentials.",
                iconEmoji = "📁",
                isUnlocked = playlists.isNotEmpty() || watchLaterList.isNotEmpty(),
                tier = "Silver",
                statText = "${playlists.size} playlists • ${watchLaterList.size} in Watch Later",
                isShame = false,
                roastOrGloryQuote = "The Library of Alexandria has nothing on your digital collection.",
                xpReward = 140
            )
        )

        val hallOfFame = HallOfFameData(
            topCreators = topCreators,
            totalLivestreamsWatched = livestreamCount,
            totalLivestreamMinutes = livestreamMinutes,
            likesGiven = likesCount,
            positivityScorePercent = positivityPercent,
            dailyStreakDays = dailyStreak,
            longestStreakDays = longestStreak,
            completionistCount = completionistCount,
            marathonsCompleted = marathonCount,
            fameBadges = fameBadges
        )

        // Hall of Shame Calculation
        val watchLaterHoard = watchLaterList.size
        val loopCount = if (totalHistoryCount >= 3) 2 else 1
        val shameScore = ((watchLaterHoard * 8) + (quickSkipCount * 12) + (lateNightVideoCount * 10) + (dislikesCount * 15) + (adultVideoCount * 14)).coerceIn(15, 100)

        val (shameTitle, shameRoast) = when {
            shameScore >= 75 -> "👹 Unhinged 3 AM Goblin Lord" to "Your Watch Later list is officially classified as a digital graveyard, and sleep is merely an abstract concept to you."
            shameScore >= 50 -> "🦇 Nocturnal Doomscroll Menace" to "Legend says you've skipped 10 videos in 2 minutes and added 50 more you'll statistically never watch."
            shameScore >= 25 -> "👀 Mildly Chaotic Consumer" to "You have good intentions, but that 1.5x playback speed and skipped intros betray your true chaos."
            else -> "😇 Suspiciously Well-Behaved Viewer" to "Almost too saintly. Are you sure you aren't secretly watching 4-hour icebergs at 4 AM?"
        }

        val shameBadges = listOf(
            FameShameBadge(
                id = "shame_3am_vampire",
                title = "3 AM Doomscroller",
                subtitle = "$lateNightVideoCount Late-Night Sessions",
                description = "Watching content when the rest of humanity is deep in REM sleep.",
                iconEmoji = "🌙",
                isUnlocked = lateNightVideoCount >= 1,
                tier = "Cursed",
                statText = "$lateNightVideoCount videos after midnight",
                isShame = true,
                roastOrGloryQuote = "Melatonin who? The algorithm knows you're awake right now.",
                xpReward = 100
            ),
            FameShameBadge(
                id = "shame_watch_later_graveyard",
                title = "Watch Later Graveyard",
                subtitle = "$watchLaterHoard Abandoned Videos",
                description = "Dumping videos into Watch Later with zero intention of ever watching them.",
                iconEmoji = "🪦",
                isUnlocked = watchLaterHoard >= 1,
                tier = "Cursed",
                statText = "$watchLaterHoard videos waiting in limbo",
                isShame = true,
                roastOrGloryQuote = "Those videos are turning into digital fossils in your library.",
                xpReward = 100
            ),
            FameShameBadge(
                id = "shame_goldfish_skips",
                title = "Goldfish Attention Span",
                subtitle = "$quickSkipCount Quick Discards",
                description = "Abandoned videos within the first 20 seconds because the hook wasn't instant.",
                iconEmoji = "💨",
                isUnlocked = quickSkipCount >= 1 || totalHistoryCount >= 2,
                tier = "Cursed",
                statText = "$quickSkipCount videos skipped early",
                isShame = true,
                roastOrGloryQuote = "TikTok has ruined your tolerance for 5-second intros.",
                xpReward = 100
            ),
            FameShameBadge(
                id = "shame_speed_demon",
                title = "1.5x / 2.0x Speed Demon",
                subtitle = "Normal Speed Is Too Slow",
                description = "Accelerating human speech because waiting in real-time is painful.",
                iconEmoji = "⚡",
                isUnlocked = true,
                tier = "Cursed",
                statText = "Consuming information at hypersonic velocities",
                isShame = true,
                roastOrGloryQuote = "Why listen to a podcast for an hour when you can finish it in 30 minutes?",
                xpReward = 100
            ),
            FameShameBadge(
                id = "shame_critical_hater",
                title = "The Skeptical Critic",
                subtitle = "${dislikesCount + notIntCount} Banished Videos",
                description = "Disliked or banished videos straight to the shadow realm.",
                iconEmoji = "💔",
                isUnlocked = (dislikesCount + notIntCount) >= 1,
                tier = "Cursed",
                statText = "$dislikesCount dislikes • $notIntCount not interested",
                isShame = true,
                roastOrGloryQuote = "Not everything deserves five stars. You are the harshest judge.",
                xpReward = 100
            ),
            FameShameBadge(
                id = "shame_loop_repeat",
                title = "Repeat Offender",
                subtitle = "$loopCount Obsessive Loops",
                description = "Playing the exact same song, trailer, or clip over and over again.",
                iconEmoji = "🔁",
                isUnlocked = totalHistoryCount >= 2,
                tier = "Cursed",
                statText = "$loopCount re-watched obsessions",
                isShame = true,
                roastOrGloryQuote = "If a song is a banger, listening to it 47 times in a row is totally rational.",
                xpReward = 100
            )
        )

        val hallOfShame = HallOfShameData(
            lateNightVideoCount = lateNightVideoCount,
            lateNightMinutes = lateNightMinutes,
            watchLaterHoardedCount = watchLaterHoard,
            quickSkipCount = quickSkipCount,
            dislikesGiven = dislikesCount,
            notInterestedCount = notIntCount,
            loopRewatchCount = loopCount,
            adultVideoCount = adultVideoCount,
            shameScorePercent = shameScore,
            shameRankTitle = shameTitle,
            roastQuote = shameRoast,
            shameBadges = shameBadges
        )

        // Generate Milestone Achievements
        val totalPlaylistsCount = playlists.size
        val totalLikedCount = likedVideoIds.size
        val distinctGenresExplored = genreCounts.keys.size

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

        val totalXp = genreBadges.sumOf { it.currentXp } +
                milestoneAchievements.filter { it.isUnlocked }.sumOf { it.xpReward } +
                fameBadges.filter { it.isUnlocked }.sumOf { it.xpReward } +
                shameBadges.filter { it.isUnlocked }.sumOf { it.xpReward }

        val globalLevel = (totalXp / 150) + 1
        val unlockedBadgesCount = milestoneAchievements.count { it.isUnlocked } +
                genreBadges.count { it.currentTier.levelNumber >= 2 } +
                fameBadges.count { it.isUnlocked } +
                shameBadges.count { it.isUnlocked }

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
            milestoneAchievements = milestoneAchievements,
            hallOfFame = hallOfFame,
            hallOfShame = hallOfShame,
            circadianStat = circadianStat
        )
    }

    private fun isLivestreamVideo(video: VideoItem): Boolean {
        val text = "${video.title ?: ""} ${video.uploaderName ?: ""}".lowercase()
        return text.contains("live") || text.contains("stream") || text.contains("broadcast") || text.contains("🔴") || (video.durationSeconds ?: 0L) >= 7200L
    }

    private fun isAdultVideo(video: VideoItem): Boolean {
        val text = "${video.title ?: ""} ${video.uploaderName ?: ""} ${video.description ?: ""}".lowercase()
        return video.providerId in listOf("eporner", "pornhub", "xvideos", "xhamster", "redtube", "youporn", "beeg", "4tube", "rule34video") ||
                text.contains("18+") || text.contains("nsfw") || text.contains("adult") || text.contains("porn")
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
        if (isAdultVideo(video)) {
            genres.add("Adult & 18+")
        }

        if (genres.isEmpty()) {
            genres.add("General & Other")
        }

        return genres
    }
}
