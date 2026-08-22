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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
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

enum class BadgesSubTab(val label: String, val icon: ImageVector) {
    PERSONALITY_METER("Personality", Icons.Outlined.Psychology),
    HALL_OF_FAME("Hall of Fame", Icons.Outlined.EmojiEvents),
    HALL_OF_SHAME("Hall of Shame", Icons.Outlined.MoodBad),
    GENRE_BADGES("Genres", Icons.Outlined.MilitaryTech),
    ACHIEVEMENTS("Trophies", Icons.Outlined.WorkspacePremium)
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
    val dislikedVideoIds by viewModel.dislikedVideoIds.collectAsState()
    val notInterestedVideoIds by viewModel.notInterestedVideoIds.collectAsState()
    val playlists by viewModel.userPlaylists.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val appStreak by viewModel.appOpenStreak.collectAsState()
    val longestStreak by viewModel.longestAppStreak.collectAsState()

    val profile = remember(watchHistory, watchProgressMap, watchLaterList, likedVideoIds, dislikedVideoIds, notInterestedVideoIds, playlists, appStreak, longestStreak) {
        PersonalityBadgeEngine.calculateProfile(
            watchHistory = watchHistory,
            watchProgressMap = watchProgressMap,
            watchLaterList = watchLaterList,
            likedVideoIds = likedVideoIds,
            dislikedVideoIds = dislikedVideoIds,
            notInterestedVideoIds = notInterestedVideoIds,
            playlists = playlists,
            dailyStreak = appStreak,
            longestStreak = longestStreak
        )
    }

    var selectedTab by remember { mutableStateOf(BadgesSubTab.PERSONALITY_METER) }
    var selectedGenreBadgeForDetail by remember { mutableStateOf<GenreBadge?>(null) }
    var selectedAchievementForDetail by remember { mutableStateOf<MilestoneAchievement?>(null) }
    var selectedFameShameBadgeForDetail by remember { mutableStateOf<FameShameBadge?>(null) }

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

            // TAB SELECTOR (ScrollableTabRow for the 5 sub-tabs)
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = Color.Transparent,
                edgePadding = 0.dp,
                divider = {}
            ) {
                BadgesSubTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (selectedTab == tab) {
                                        when (tab) {
                                            BadgesSubTab.HALL_OF_FAME -> Color(0xFFFFD700)
                                            BadgesSubTab.HALL_OF_SHAME -> Color(0xFFFF5252)
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tab.label,
                                    fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == tab) {
                                        when (tab) {
                                            BadgesSubTab.HALL_OF_FAME -> Color(0xFFFFD700)
                                            BadgesSubTab.HALL_OF_SHAME -> Color(0xFFFF5252)
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // TAB CONTENT
            when (selectedTab) {
                BadgesSubTab.PERSONALITY_METER -> {
                    PersonalityMeterContent(profile = profile)
                }
                BadgesSubTab.HALL_OF_FAME -> {
                    HallOfFameContent(
                        hallOfFame = profile.hallOfFame,
                        onBadgeClick = { selectedFameShameBadgeForDetail = it }
                    )
                }
                BadgesSubTab.HALL_OF_SHAME -> {
                    HallOfShameContent(
                        hallOfShame = profile.hallOfShame,
                        onBadgeClick = { selectedFameShameBadgeForDetail = it }
                    )
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

    // DETAIL DIALOG FOR FAME / SHAME BADGE
    selectedFameShameBadgeForDetail?.let { badge ->
        FameShameBadgeDetailDialog(
            badge = badge,
            onDismiss = { selectedFameShameBadgeForDetail = null }
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
                    title = "Badges & Trophies",
                    value = "${profile.totalBadgesUnlocked}",
                    subtitle = "Level ${profile.globalLevel} Explorer",
                    icon = Icons.Outlined.MilitaryTech,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // CIRCADIAN VIEWING RHYTHM (Time of Day Breakdown)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Circadian Viewing Rhythm",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = profile.circadianStat.dominantTimeDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Multi-color Circadian Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                    ) {
                        Box(modifier = Modifier.weight(profile.circadianStat.morningPercent.coerceAtLeast(1f)).fillMaxHeight().background(Color(0xFFFFB300)))
                        Box(modifier = Modifier.weight(profile.circadianStat.afternoonPercent.coerceAtLeast(1f)).fillMaxHeight().background(Color(0xFFFF7043)))
                        Box(modifier = Modifier.weight(profile.circadianStat.eveningPercent.coerceAtLeast(1f)).fillMaxHeight().background(Color(0xFFAB47BC)))
                        Box(modifier = Modifier.weight(profile.circadianStat.nightPercent.coerceAtLeast(1f)).fillMaxHeight().background(Color(0xFF1E88E5)))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        CircadianLegendItem(label = "Morning 🌅", percent = profile.circadianStat.morningPercent.toInt(), color = Color(0xFFFFB300))
                        CircadianLegendItem(label = "Midday ☀️", percent = profile.circadianStat.afternoonPercent.toInt(), color = Color(0xFFFF7043))
                        CircadianLegendItem(label = "Prime 🌆", percent = profile.circadianStat.eveningPercent.toInt(), color = Color(0xFFAB47BC))
                        CircadianLegendItem(label = "Night 🦉", percent = profile.circadianStat.nightPercent.toInt(), color = Color(0xFF1E88E5))
                    }
                }
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
fun CircadianLegendItem(label: String, percent: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * HALL OF FAME: Top Creators, Livestream records, Positivity index, Streaks & Glory Badges
 */
@Composable
fun HallOfFameContent(
    hallOfFame: HallOfFameData,
    onBadgeClick: (FameShameBadge) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // TOP CREATOR STAN HERO BANNER
        item {
            val topCreator = hallOfFame.topCreators.firstOrNull()
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E1B10)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "👑 HALL OF FAME",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "Top Creator Royalty",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFFD700).copy(alpha = 0.8f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF332A00))
                                .border(2.dp, Color(0xFFFFD700), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!topCreator?.avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = topCreator?.avatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text("⭐", fontSize = 24.sp)
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = topCreator?.creatorName ?: "Featured Creator",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${topCreator?.stanTitle ?: "Dedicated Viewer"} • ${topCreator?.videoCount ?: 0} videos (${topCreator?.totalMinutes ?: 0}m)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFFD700),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // FAME METRICS ROW: Positivity & Streak & Streams
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "Positive Energy",
                    value = "${hallOfFame.positivityScorePercent}%",
                    subtitle = "${hallOfFame.likesGiven} likes given",
                    icon = Icons.Outlined.ThumbUp,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Daily Streak",
                    value = "${hallOfFame.dailyStreakDays} Days",
                    subtitle = "Record: ${hallOfFame.longestStreakDays} d",
                    icon = Icons.Outlined.LocalFireDepartment,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // TOP CREATORS LEADERBOARD
        if (hallOfFame.topCreators.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Most Watched Creators",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        hallOfFame.topCreators.forEachIndexed { idx, creator ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#${idx + 1}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (idx == 0) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(28.dp)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = creator.creatorName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${creator.stanTitle} • ${creator.favoriteGenre}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text(
                                    text = "${creator.videoCount} vids (${creator.totalMinutes}m)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // HALL OF FAME TROPHY BADGES
        item {
            Text(
                text = "Glory Badges & Honors",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(hallOfFame.fameBadges, key = { it.id }) { badge ->
            FameShameBadgeCard(
                badge = badge,
                onClick = { onBadgeClick(badge) }
            )
        }
    }
}

/**
 * HALL OF SHAME: Guilty pleasures, 3 AM Doomscrolling, Watch Later Graveyard, Quick Skips & Roast
 */
@Composable
fun HallOfShameContent(
    hallOfShame: HallOfShameData,
    onBadgeClick: (FameShameBadge) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // ROAST & SHAME SCORE HERO BANNER
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF200F15)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "💀 HALL OF SHAME",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF5252),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "Guilty Pleasures & Chaos",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFF5252).copy(alpha = 0.8f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = hallOfShame.shameRankTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "\"${hallOfShame.roastQuote}\"",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = Color(0xFFFF8A80)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Shame Index Progress Bar
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Chaos & Shame Index",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "${hallOfShame.shameScorePercent}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF5252)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { hallOfShame.shameScorePercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFFFF5252),
                            trackColor = Color(0xFF3E1B24)
                        )
                    }
                }
            }
        }

        // GUILTY PLEASURE HABIT STATS
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "3 AM Vampire",
                    value = "${hallOfShame.lateNightVideoCount}",
                    subtitle = "${hallOfShame.lateNightMinutes}m after dark",
                    icon = Icons.Outlined.NightsStay,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Watch Later Hoard",
                    value = "${hallOfShame.watchLaterHoardedCount}",
                    subtitle = "Unwatched in graveyard",
                    icon = Icons.Outlined.Inventory2,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "Goldfish Skips",
                    value = "${hallOfShame.quickSkipCount}",
                    subtitle = "Skipped in <20 secs",
                    icon = Icons.Outlined.FastForward,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Skeptical Dislikes",
                    value = "${hallOfShame.dislikesGiven + hallOfShame.notInterestedCount}",
                    subtitle = "Banished videos",
                    icon = Icons.Outlined.ThumbDown,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // CURSED BADGES LIST
        item {
            Text(
                text = "Cursed Relics & Habits",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(hallOfShame.shameBadges, key = { it.id }) { badge ->
            FameShameBadgeCard(
                badge = badge,
                onClick = { onBadgeClick(badge) }
            )
        }
    }
}

@Composable
fun FameShameBadgeCard(
    badge: FameShameBadge,
    onClick: () -> Unit
) {
    val isUnlocked = badge.isUnlocked
    val isShame = badge.isShame
    val primaryTint = if (isShame) Color(0xFFFF5252) else Color(0xFFFFD700)

    val cardBg = if (isUnlocked) {
        if (isShame) Color(0xFF221118) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = if (isUnlocked) androidx.compose.foundation.BorderStroke(1.dp, primaryTint.copy(alpha = 0.3f)) else null
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
                        if (isUnlocked) primaryTint.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    .border(
                        1.5.dp,
                        if (isUnlocked) primaryTint else Color.Gray.copy(alpha = 0.3f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badge.iconEmoji,
                    fontSize = 22.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = badge.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isUnlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = badge.tier,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.Bold,
                        color = primaryTint
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = badge.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = primaryTint.copy(alpha = 0.9f)
                )

                Text(
                    text = badge.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isUnlocked) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Unlocked",
                    tint = primaryTint,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = "+${badge.xpReward} XP",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = primaryTint
                )
            }
        }
    }
}

@Composable
fun FameShameBadgeDetailDialog(
    badge: FameShameBadge,
    onDismiss: () -> Unit
) {
    val isShame = badge.isShame
    val primaryTint = if (isShame) Color(0xFFFF5252) else Color(0xFFFFD700)

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
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(primaryTint.copy(alpha = 0.15f))
                        .border(2.5.dp, primaryTint, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badge.iconEmoji,
                        fontSize = 34.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = badge.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "${badge.tier} • ${badge.subtitle}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = primaryTint
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = badge.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (!badge.roastOrGloryQuote.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = primaryTint.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "\"${badge.roastOrGloryQuote}\"",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = primaryTint,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Stat: ${badge.statText}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryTint),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isShame) "I Accept My Sins" else "Claim Glory",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
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
    icon: ImageVector,
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
