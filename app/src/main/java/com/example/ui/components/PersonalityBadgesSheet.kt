package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.model.*
import com.example.ui.MainViewModel
import com.example.util.PersonalityBadgeEngine

enum class BadgesSubTab {
    PERSONALITY_METER,
    GENRE_BADGES,
    ACHIEVEMENTS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalityBadgesSheet(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val watchHistory by viewModel.watchHistory.collectAsState()
    val watchProgressMap by viewModel.watchProgressMap.collectAsState()
    val watchLaterList by viewModel.watchLaterList.collectAsState()
    val likedVideoIds by viewModel.likedVideoIds.collectAsState()
    val playlists by viewModel.userPlaylists.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()

    val profile = remember(watchHistory, watchProgressMap, watchLaterList, likedVideoIds, playlists) {
        PersonalityBadgeEngine.calculateProfile(
            watchHistory = watchHistory,
            watchProgressMap = watchProgressMap,
            watchLaterList = watchLaterList,
            likedVideoIds = likedVideoIds,
            playlists = playlists
        )
    }

    var selectedTab by remember { mutableStateOf(BadgesSubTab.PERSONALITY_METER) }
    var selectedGenreBadgeForDetail by remember { mutableStateOf<GenreBadge?>(null) }
    var selectedAchievementForDetail by remember { mutableStateOf<MilestoneAchievement?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            ) {
                BottomSheetDefaults.DragHandle()
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 16.dp)
        ) {
            // HEADER: Profile Avatar, Dominant Archetype & Global Stats
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF1E293B),
                                Color(0xFF0F172A)
                            )
                        )
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (!userProfile.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = userProfile.avatarUrl,
                            contentDescription = "Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = profile.archetypeEmoji,
                            fontSize = 28.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Level ${profile.globalLevel}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700),
                            modifier = Modifier
                                .background(Color(0xFF332A00), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${profile.totalXp} Total XP",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = profile.dominantArchetype,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = profile.archetypeDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // TAB SELECTOR (Personality Meter, Genre Badges, Achievements)
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == BadgesSubTab.PERSONALITY_METER,
                    onClick = { selectedTab = BadgesSubTab.PERSONALITY_METER },
                    text = {
                        Text(
                            text = "Personality Meter",
                            fontWeight = if (selectedTab == BadgesSubTab.PERSONALITY_METER) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTab == BadgesSubTab.GENRE_BADGES,
                    onClick = { selectedTab = BadgesSubTab.GENRE_BADGES },
                    text = {
                        Text(
                            text = "Genre Badges",
                            fontWeight = if (selectedTab == BadgesSubTab.GENRE_BADGES) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTab == BadgesSubTab.ACHIEVEMENTS,
                    onClick = { selectedTab = BadgesSubTab.ACHIEVEMENTS },
                    text = {
                        Text(
                            text = "Achievements",
                            fontWeight = if (selectedTab == BadgesSubTab.ACHIEVEMENTS) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // TAB CONTENT
            when (selectedTab) {
                BadgesSubTab.PERSONALITY_METER -> {
                    PersonalityMeterContent(profile = profile)
                }
                BadgesSubTab.GENRE_BADGES -> {
                    GenreBadgesContent(
                        badges = profile.genreBadges,
                        onBadgeClick = { selectedGenreBadgeForDetail = it }
                    )
                }
                BadgesSubTab.ACHIEVEMENTS -> {
                    AchievementsContent(
                        achievements = profile.milestoneAchievements,
                        onAchievementClick = { selectedAchievementForDetail = it }
                    )
                }
            }
        }
    }

    // DETAIL DIALOG FOR GENRE BADGE
    selectedGenreBadgeForDetail?.let { badge ->
        BadgeDetailDialog(
            badge = badge,
            onDismiss = { selectedGenreBadgeForDetail = null }
        )
    }

    // DETAIL DIALOG FOR ACHIEVEMENT
    selectedAchievementForDetail?.let { achievement ->
        AchievementDetailDialog(
            achievement = achievement,
            onDismiss = { selectedAchievementForDetail = null }
        )
    }
}

@Composable
fun PersonalityMeterContent(profile: PersonalityProfile) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Quick Stats Summary Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "Watch Time",
                    value = "${profile.totalWatchTimeMinutes / 60}h ${profile.totalWatchTimeMinutes % 60}m",
                    subtitle = "${profile.totalVideosCompleted} videos watched",
                    icon = Icons.Outlined.Timer,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Badges Unlocked",
                    value = "${profile.totalBadgesUnlocked}",
                    subtitle = "Level ${profile.globalLevel} Explorer",
                    icon = Icons.Outlined.MilitaryTech,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // TASTE DISTRIBUTION BREAKDOWN
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Content Consumption Spectrum",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Real-time breakdown of your media tastes and viewing habits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Multi-color segmented bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                    ) {
                        profile.genreDistribution.forEach { stat ->
                            if (stat.percentage > 0.5f) {
                                Box(
                                    modifier = Modifier
                                        .weight(stat.percentage.coerceAtLeast(0.01f))
                                        .fillMaxHeight()
                                        .background(stat.color)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // List of percentages per genre
                    profile.genreDistribution.forEach { stat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(stat.color)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stat.genreName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${String.format("%.1f", stat.percentage)}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GenreBadgesContent(
    badges: List<GenreBadge>,
    onBadgeClick: (GenreBadge) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(badges, key = { it.id }) { badge ->
            GenreBadgeCard(
                badge = badge,
                onClick = { onBadgeClick(badge) }
            )
        }
    }
}

@Composable
fun GenreBadgeCard(
    badge: GenreBadge,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(badge.currentTier.primaryColor.copy(alpha = 0.2f))
                    .border(2.dp, badge.currentTier.primaryColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badge.currentTier.iconEmoji,
                    fontSize = 24.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = badge.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${badge.currentTier.title} • Level ${badge.currentTier.levelNumber}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = badge.currentTier.primaryColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            // XP Progress Bar
            LinearProgressIndicator(
                progress = { badge.progressToNextTier },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = badge.currentTier.primaryColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (badge.nextTier != null) "${badge.currentXp} / ${badge.nextTier.minXp} XP" else "Max Level Reached",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AchievementsContent(
    achievements: List<MilestoneAchievement>,
    onAchievementClick: (MilestoneAchievement) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(achievements, key = { it.id }) { achievement ->
            AchievementListItem(
                achievement = achievement,
                onClick = { onAchievementClick(achievement) }
            )
        }
    }
}

@Composable
fun AchievementListItem(
    achievement: MilestoneAchievement,
    onClick: () -> Unit
) {
    val isUnlocked = achievement.isUnlocked
    val cardBg = if (isUnlocked) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isUnlocked) Color(0xFFFFD700).copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    .border(
                        1.5.dp,
                        if (isUnlocked) Color(0xFFFFD700) else Color.Gray.copy(alpha = 0.3f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = achievement.iconEmoji,
                    fontSize = 22.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = achievement.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isUnlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = achievement.rarity,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.Bold,
                        color = when (achievement.rarity) {
                            "Legendary" -> Color(0xFFFF9100)
                            "Epic" -> Color(0xFFE040FB)
                            "Rare" -> Color(0xFF00E5FF)
                            else -> Color(0xFF8BC34A)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!isUnlocked && achievement.maxProgress > 1) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { achievement.currentProgress.toFloat() / achievement.maxProgress },
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isUnlocked) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Unlocked",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = "+${achievement.xpReward} XP",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BadgeDetailDialog(
    badge: GenreBadge,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(badge.currentTier.primaryColor.copy(alpha = 0.2f))
                        .border(3.dp, badge.currentTier.primaryColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badge.currentTier.iconEmoji,
                        fontSize = 36.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = badge.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "${badge.currentTier.title} Badge (Tier ${badge.currentTier.levelNumber})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = badge.currentTier.primaryColor
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = badge.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${badge.totalVideosWatched}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Videos Watched",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${badge.totalMinutesWatched}m",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Watch Duration",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun AchievementDetailDialog(
    achievement: MilestoneAchievement,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFD700).copy(alpha = 0.15f))
                        .border(2.dp, Color(0xFFFFD700), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = achievement.iconEmoji,
                        fontSize = 32.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = achievement.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "${achievement.rarity} • ${achievement.category}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD700)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (achievement.isUnlocked && achievement.unlockedDate != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Unlocked on ${achievement.unlockedDate}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Awesome")
                }
            }
        }
    }
}
