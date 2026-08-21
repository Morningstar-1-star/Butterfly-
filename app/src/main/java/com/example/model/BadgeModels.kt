package com.example.model

import androidx.compose.ui.graphics.Color

enum class BadgeTier(
    val title: String,
    val levelNumber: Int,
    val primaryColor: Color,
    val secondaryColor: Color,
    val minXp: Int,
    val iconEmoji: String
) {
    BRONZE("Bronze", 1, Color(0xFFCD7F32), Color(0xFF8C5320), 0, "🥉"),
    SILVER("Silver", 2, Color(0xFFC0C0C0), Color(0xFF7F8C8D), 100, "🥈"),
    GOLD("Gold", 3, Color(0xFFFFD700), Color(0xFFB8860B), 300, "🥇"),
    PLATINUM("Platinum", 4, Color(0xFF00E5FF), Color(0xFF0097A7), 700, "💎"),
    DIAMOND("Diamond", 5, Color(0xFFE040FB), Color(0xFF7B1FA2), 1500, "👑")
}

data class GenreBadge(
    val id: String,
    val name: String,
    val description: String,
    val iconName: String,
    val currentXp: Int,
    val totalVideosWatched: Int,
    val totalMinutesWatched: Long,
    val currentTier: BadgeTier,
    val nextTier: BadgeTier?,
    val progressToNextTier: Float, // 0f..1f
    val xpNeededForNextTier: Int
)

data class MilestoneAchievement(
    val id: String,
    val title: String,
    val description: String,
    val iconEmoji: String,
    val category: String,
    val isUnlocked: Boolean,
    val currentProgress: Int,
    val maxProgress: Int,
    val unlockedDate: String? = null,
    val rarity: String = "Common", // Common, Rare, Epic, Legendary
    val xpReward: Int = 50
)

data class PersonalityProfile(
    val dominantArchetype: String,
    val archetypeDescription: String,
    val archetypeEmoji: String,
    val totalWatchTimeMinutes: Long,
    val totalVideosCompleted: Int,
    val totalBadgesUnlocked: Int,
    val totalXp: Int,
    val globalLevel: Int,
    val genreDistribution: List<GenreConsumptionStat>,
    val genreBadges: List<GenreBadge>,
    val milestoneAchievements: List<MilestoneAchievement>
)

data class GenreConsumptionStat(
    val genreName: String,
    val count: Int,
    val percentage: Float, // 0f..100f
    val color: Color
)
