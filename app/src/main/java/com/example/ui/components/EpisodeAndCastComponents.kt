package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import com.example.model.CastMember
import com.example.model.EpisodeItem
import com.example.model.SeriesSeason
import com.example.model.VideoComment

@Composable
fun CastSection(
    castList: List<CastMember>,
    onCastClick: ((CastMember) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (castList.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = "Cast",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            castList.forEach { member ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .clickable { onCastClick?.invoke(member) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    if (!member.avatarUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = member.avatarUrl,
                            contentDescription = member.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = member.name.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = member.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!member.role.isNullOrEmpty()) {
                            Text(
                                text = member.role,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

enum class PlayerTab {
    SEASONS_EPISODES,
    RELATED,
    RECOMMENDED,
    COMMENTS
}

@Composable
fun PlayerTabBar(
    selectedTab: PlayerTab,
    onTabSelected: (PlayerTab) -> Unit,
    showSeasonsTab: Boolean = true,
    commentsCount: Int = 14,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showSeasonsTab) {
            // Tab 1: SEASONS & EPISODES
            TabPill(
                label = "SEASONS & EPISODES",
                icon = Icons.Outlined.Tv,
                isSelected = selectedTab == PlayerTab.SEASONS_EPISODES,
                onClick = { onTabSelected(PlayerTab.SEASONS_EPISODES) }
            )
        }

        // Tab 2: Related
        TabPill(
            label = "Related",
            icon = null,
            isSelected = selectedTab == PlayerTab.RELATED,
            onClick = { onTabSelected(PlayerTab.RELATED) }
        )

        // Tab 3: Recommended
        TabPill(
            label = "Recommended",
            icon = null,
            isSelected = selectedTab == PlayerTab.RECOMMENDED,
            onClick = { onTabSelected(PlayerTab.RECOMMENDED) }
        )

        val formattedCount = remember(commentsCount) {
            when {
                commentsCount >= 1_000_000 -> "${String.format(java.util.Locale.US, "%.1f", commentsCount / 1_000_000.0)}M"
                commentsCount >= 1_000 -> "${String.format(java.util.Locale.US, "%.1f", commentsCount / 1_000.0)}K"
                else -> "$commentsCount"
            }
        }

        // Tab 4: Comments
        TabPill(
            label = "Comments ($formattedCount)",
            icon = Icons.Outlined.ChatBubbleOutline,
            isSelected = selectedTab == PlayerTab.COMMENTS,
            onClick = { onTabSelected(PlayerTab.COMMENTS) }
        )
    }
}

@Composable
private fun TabPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.height(38.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun SeasonsAndEpisodesView(
    seasons: List<SeriesSeason>,
    activeVideoId: String?,
    onEpisodeClick: (EpisodeItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSeasonIndex by remember { mutableIntStateOf(0) }
    val currentSeason = seasons.getOrNull(selectedSeasonIndex) ?: seasons.firstOrNull()

    Column(modifier = modifier.fillMaxWidth()) {
        // Season Selector Pills
        if (seasons.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                seasons.forEachIndexed { index, season ->
                    FilterChip(
                        selected = index == selectedSeasonIndex,
                        onClick = { selectedSeasonIndex = index },
                        label = { Text(season.seasonName, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }

        // Episodes List
        if (currentSeason != null && currentSeason.episodes.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                currentSeason.episodes.forEach { episode ->
                    val isCurrentPlaying = activeVideoId != null && episode.id == activeVideoId

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEpisodeClick(episode) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrentPlaying)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = if (isCurrentPlaying)
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Thumbnail with play badge
                            Box(
                                modifier = Modifier
                                    .width(110.dp)
                                    .height(68.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.DarkGray)
                            ) {
                                if (!episode.thumbnailUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = episode.thumbnailUrl,
                                        contentDescription = episode.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                // Duration Badge
                                Surface(
                                    color = Color.Black.copy(alpha = 0.8f),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                ) {
                                    Text(
                                        text = episode.durationText,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }

                                if (isCurrentPlaying) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.4f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Playing",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Details
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = if (isCurrentPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "S${episode.seasonNumber}:E${episode.episodeNumber}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCurrentPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (isCurrentPlaying) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "NOW PLAYING",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = episode.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = episode.viewsText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Icon(
                                imageVector = if (isCurrentPlaying) Icons.Default.VolumeUp else Icons.Default.PlayCircleOutline,
                                contentDescription = "Play",
                                tint = if (isCurrentPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CommentsSectionView(
    comments: List<VideoComment>,
    onAddComment: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    isTorrent: Boolean = false,
    isLoading: Boolean = false,
    totalReviewsCountText: String? = null
) {
    var commentList by remember(comments) { mutableStateOf(comments) }

    // Sort state: TOP or NEWEST
    var activeSort by remember { mutableStateOf("TOP") }

    // State for liked/disliked comments
    val userLikes = remember { mutableStateMapOf<String, Int>() }
    val userDislikes = remember { mutableStateMapOf<String, Int>() }

    // Sort comments list
    val filteredComments = remember(commentList, activeSort) {
        when (activeSort) {
            "NEWEST" -> commentList.reversed()
            else -> commentList.sortedByDescending { it.likeCount } // TOP
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {

        // Modern AMOLED Tab Selector Header (Only Top & Newest)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0A0C10),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // "Top" Tab
                    Surface(
                        onClick = { activeSort = "TOP" },
                        shape = RoundedCornerShape(20.dp),
                        color = if (activeSort == "TOP") Color(0xFFF5C518) else Color.Transparent
                    ) {
                        Text(
                            text = "Top Reviews",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (activeSort == "TOP") Color.Black else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                        )
                    }

                    // "Newest" Tab
                    Surface(
                        onClick = { activeSort = "NEWEST" },
                        shape = RoundedCornerShape(20.dp),
                        color = if (activeSort == "NEWEST") Color(0xFFF5C518) else Color.Transparent
                    ) {
                        Text(
                            text = "Newest",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (activeSort == "NEWEST") Color.Black else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        // Loading State
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color(0xFFF5C518))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Loading reviews...", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.6f))
                }
            }
        } else if (filteredComments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No user reviews available.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                filteredComments.forEach { comment ->
                    val extraLikes = userLikes[comment.id] ?: 0
                    val extraDislikes = userDislikes[comment.id] ?: 0

                    YouTubeStyleCommentItem(
                        comment = comment,
                        isTorrent = isTorrent,
                        isRevealed = true,
                        onToggleSpoiler = {},
                        extraLikes = extraLikes,
                        extraDislikes = extraDislikes,
                        onLikeClick = {
                            userLikes[comment.id] = if (extraLikes > 0) 0 else 1
                        },
                        onDislikeClick = {
                            userDislikes[comment.id] = if (extraDislikes > 0) 0 else 1
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun YouTubeStyleCommentItem(
    comment: VideoComment,
    isTorrent: Boolean = false,
    isRevealed: Boolean = true,
    onToggleSpoiler: () -> Unit = {},
    extraLikes: Int = 0,
    extraDislikes: Int = 0,
    onLikeClick: () -> Unit = {},
    onDislikeClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF090A0E) // Pure deep AMOLED black card container
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Avatar + Username + Time + Rating Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Avatar Picture or Initials
                if (!comment.authorAvatarUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = comment.authorAvatarUrl,
                        contentDescription = comment.authorName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                    )
                } else {
                    val initialName = comment.authorName.removePrefix("@").trim()
                    val initial = if (initialName.isNotEmpty()) initialName.take(1).uppercase() else "U"
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFE50914), Color(0xFFB20710))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val displayName = if (comment.authorName.startsWith("@")) comment.authorName else "@${comment.authorName}"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = displayName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        // Source Badge Pill (AniList / MyAnimeList / TMDB)
                        if (!comment.sourceBadge.isNullOrBlank()) {
                            val badgeBg = when (comment.sourceBadge) {
                                "AniList" -> Color(0xFF02A9FF)
                                "MyAnimeList", "MAL" -> Color(0xFF2E51A2)
                                else -> Color(0xFF01B4E4)
                            }
                            Surface(
                                color = badgeBg,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = comment.sourceBadge,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = comment.timeAgo,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.45f)
                    )
                }

                // Rating Pill on top right
                if ((comment.rating != null && comment.rating > 0) || !comment.ratingText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = Color(0xFFF5C518),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            val displayRatingStr = comment.ratingText
                                ?: "${comment.rating?.toInt() ?: 8}/10"
                            Text(
                                text = displayRatingStr,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }

            // Title headline
            val titleText = if (!comment.reviewTitle.isNullOrBlank()) {
                comment.reviewTitle
            } else {
                comment.commentText.take(70)
            }

            val hasBodyText = !comment.commentText.isNullOrBlank() &&
                (comment.reviewTitle.isNullOrBlank() || comment.commentText.trim() != comment.reviewTitle.trim())

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = titleText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                lineHeight = 18.sp,
                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )

            // Explained body text shown ONLY when expanded
            if (isExpanded && hasBodyText) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = comment.commentText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 19.sp,
                    color = Color.White.copy(alpha = 0.82f)
                )
            }

            // Toggle expand button
            if (hasBodyText) {
                Text(
                    text = if (isExpanded) "Show less" else "Read full review",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFF5C518),
                    modifier = Modifier
                        .clickable { isExpanded = !isExpanded }
                        .padding(top = 6.dp, bottom = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer Action Bar: Helpful / Unhelpful buttons + Open Review URL link
            val context = LocalContext.current
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Helpful / Like Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onLikeClick() }
                    ) {
                        Icon(
                            imageVector = if (extraLikes > 0) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = "Helpful",
                            modifier = Modifier.size(15.dp),
                            tint = if (extraLikes > 0) Color(0xFFF5C518) else Color.White.copy(alpha = 0.5f)
                        )
                        val totalLikes = comment.likeCount + extraLikes
                        if (totalLikes > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${formatLikeCount(totalLikes)} helpful",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (extraLikes > 0) Color(0xFFF5C518) else Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }

                    // Unhelpful / Dislike Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onDislikeClick() }
                    ) {
                        Icon(
                            imageVector = if (extraDislikes > 0) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                            contentDescription = "Unhelpful",
                            modifier = Modifier.size(15.dp),
                            tint = if (extraDislikes > 0) Color(0xFFFF5252) else Color.White.copy(alpha = 0.5f)
                        )
                    }
                }

                // Open Original Review Link Button
                if (!comment.reviewUrl.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(comment.reviewUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    ) {
                        Text(
                            text = "View on ${comment.sourceBadge ?: "Web"}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFF5C518)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Open review URL",
                            modifier = Modifier.size(12.dp),
                            tint = Color(0xFFF5C518)
                        )
                    }
                }
            }
        }
    }
}

private fun formatLikeCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", count / 1000000.0)
        count >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", count / 1000.0)
        else -> count.toString()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastFilmographySheet(
    castMember: CastMember,
    onDismiss: () -> Unit,
    onSelectFilmographyItem: (com.example.model.CastFilmographyItem) -> Unit
) {
    var filmography by remember { mutableStateOf<List<com.example.model.CastFilmographyItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(castMember.name, castMember.personId) {
        isLoading = true
        filmography = com.example.util.TMDBHelper.fetchFilmographyForPerson(castMember.name, castMember.personId)
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Header Info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!castMember.avatarUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = castMember.avatarUrl,
                        contentDescription = castMember.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = castMember.name.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = castMember.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!castMember.role.isNullOrEmpty()) {
                        Text(
                            text = "Role: ${castMember.role}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Filmography & Works",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading movies & TV series for ${castMember.name}...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (filmography.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No filmography credits found for this actor.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "Movies & TV Series (${filmography.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filmography) { item ->
                        Card(
                            modifier = Modifier
                                .width(130.dp)
                                .clickable {
                                    onSelectFilmographyItem(item)
                                    onDismiss()
                                },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .background(Color.DarkGray)
                                ) {
                                    if (!item.posterUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = item.posterUrl,
                                            contentDescription = item.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    // Rating pill
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.75f),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                    ) {
                                        Text(
                                            text = "★ ${String.format(java.util.Locale.US, "%.1f", item.voteAverage)}",
                                            color = Color(0xFFFFC107),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                    // Year pill
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(6.dp)
                                    ) {
                                        Text(
                                            text = item.releaseYear,
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = item.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (item.character.isNotBlank()) {
                                        Text(
                                            text = "as ${item.character}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
