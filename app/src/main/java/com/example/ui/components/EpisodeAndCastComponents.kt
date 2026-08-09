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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
    onAddComment: (String) -> Unit,
    modifier: Modifier = Modifier,
    isTorrent: Boolean = false,
    isLoading: Boolean = false,
    totalReviewsCountText: String? = null
) {
    var newCommentText by remember { mutableStateOf("") }
    var commentList by remember(comments) { mutableStateOf(comments) }

    // Filter & Sort state matching concept art
    var activeSort by remember { mutableStateOf("TOP") } // TOP, NEWEST
    var starRatingFilter by remember { mutableStateOf("ALL") } // ALL, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 8_10, 6_7, 1_5
    var spoilerFilter by remember { mutableStateOf("ALL") } // ALL, HIDE_SPOILERS, SPOILERS_ONLY

    var starMenuExpanded by remember { mutableStateOf(false) }

    // State for revealed spoilers map (commentId -> Boolean)
    val revealedSpoilers = remember { mutableStateMapOf<String, Boolean>() }
    // State for liked/disliked comments
    val userLikes = remember { mutableStateMapOf<String, Int>() }
    val userDislikes = remember { mutableStateMapOf<String, Int>() }

    // Filter and sort comments list
    val filteredComments = remember(commentList, activeSort, starRatingFilter, spoilerFilter) {
        var list = commentList

        // Rating Filter
        when (starRatingFilter) {
            "10" -> list = list.filter { (it.rating ?: 7f) >= 9.5f }
            "9" -> list = list.filter { val r = it.rating ?: 7f; r >= 8.5f && r < 9.5f }
            "8" -> list = list.filter { val r = it.rating ?: 7f; r >= 7.5f && r < 8.5f }
            "7" -> list = list.filter { val r = it.rating ?: 7f; r >= 6.5f && r < 7.5f }
            "6" -> list = list.filter { val r = it.rating ?: 7f; r >= 5.5f && r < 6.5f }
            "5" -> list = list.filter { val r = it.rating ?: 7f; r >= 4.5f && r < 5.5f }
            "4" -> list = list.filter { val r = it.rating ?: 7f; r >= 3.5f && r < 4.5f }
            "3" -> list = list.filter { val r = it.rating ?: 7f; r >= 2.5f && r < 3.5f }
            "2" -> list = list.filter { val r = it.rating ?: 7f; r >= 1.5f && r < 2.5f }
            "1" -> list = list.filter { (it.rating ?: 7f) < 1.5f }
            "8_10" -> list = list.filter { (it.rating ?: 7f) >= 8f }
            "6_7" -> list = list.filter { val r = it.rating ?: 7f; r >= 6f && r < 8f }
            "1_5" -> list = list.filter { (it.rating ?: 7f) < 6f }
        }

        // Spoiler Filter
        when (spoilerFilter) {
            "HIDE_SPOILERS" -> list = list.filter { !it.isSpoiler }
            "SPOILERS_ONLY" -> list = list.filter { it.isSpoiler }
        }

        // Sort Order
        when (activeSort) {
            "NEWEST" -> list.reversed()
            else -> list.sortedByDescending { it.likeCount } // TOP
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {

        // Header Filter Tag Bar (Concept Art style: Top, Newest, Star Dropdown, Spoiler)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "Top" Tag
            Surface(
                selected = activeSort == "TOP",
                onClick = { activeSort = "TOP" },
                shape = RoundedCornerShape(8.dp),
                color = if (activeSort == "TOP") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Text(
                    text = "Top",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (activeSort == "TOP") MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            // "Newest" Tag
            Surface(
                selected = activeSort == "NEWEST",
                onClick = { activeSort = "NEWEST" },
                shape = RoundedCornerShape(8.dp),
                color = if (activeSort == "NEWEST") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Text(
                    text = "Newest",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (activeSort == "NEWEST") MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            // "Star ⭐️" Tag Dropdown Menu
            Box {
                val starLabel = when (starRatingFilter) {
                    "ALL" -> "Star ⭐️"
                    "8_10" -> "Star ⭐️ (8-10)"
                    "6_7" -> "Star ⭐️ (6-7)"
                    "1_5" -> "Star ⭐️ (1-5)"
                    else -> "Star ⭐️ ($starRatingFilter ★)"
                }
                Surface(
                    onClick = { starMenuExpanded = true },
                    shape = RoundedCornerShape(8.dp),
                    color = if (starRatingFilter != "ALL") Color(0xFFF5C518) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = starLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (starRatingFilter != "ALL") Color.Black else MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select Star Rating",
                            tint = if (starRatingFilter != "ALL") Color.Black else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = starMenuExpanded,
                    onDismissRequest = { starMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All Ratings", fontSize = 13.sp) },
                        onClick = { starRatingFilter = "ALL"; starMenuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("★ 8-10 Stars (High)", fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                        onClick = { starRatingFilter = "8_10"; starMenuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("★ 6-7 Stars (Mixed)", fontSize = 13.sp) },
                        onClick = { starRatingFilter = "6_7"; starMenuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("★ 1-5 Stars (Low)", fontSize = 13.sp) },
                        onClick = { starRatingFilter = "1_5"; starMenuExpanded = false }
                    )
                    HorizontalDivider()
                    for (stars in 10 downTo 1) {
                        DropdownMenuItem(
                            text = { Text("★ $stars Star${if (stars > 1) "s" else ""}", fontSize = 13.sp) },
                            onClick = { starRatingFilter = stars.toString(); starMenuExpanded = false }
                        )
                    }
                }
            }

            // "Spoiler" Tag
            Surface(
                selected = spoilerFilter != "ALL",
                onClick = {
                    spoilerFilter = when (spoilerFilter) {
                        "ALL" -> "SPOILERS_ONLY"
                        "SPOILERS_ONLY" -> "HIDE_SPOILERS"
                        else -> "ALL"
                    }
                },
                shape = RoundedCornerShape(8.dp),
                color = if (spoilerFilter != "ALL") Color(0xFFFF7043) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                val spoilerText = when (spoilerFilter) {
                    "SPOILERS_ONLY" -> "⚠️ Spoilers Only"
                    "HIDE_SPOILERS" -> "🙈 Hide Spoilers"
                    else -> "Spoiler ⚠️"
                }
                Text(
                    text = spoilerText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (spoilerFilter != "ALL") Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Add Comment Input Box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newCommentText,
                onValueChange = { newCommentText = it },
                placeholder = { Text("Add a comment...", fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (newCommentText.isNotBlank()) {
                        val created = VideoComment(
                            id = "c_${System.currentTimeMillis()}",
                            authorName = "You",
                            commentText = newCommentText,
                            timeAgo = "Just now",
                            likeCount = 1,
                            rating = 9.0f,
                            reviewTitle = newCommentText.take(40)
                        )
                        commentList = listOf(created) + commentList
                        onAddComment(newCommentText)
                        newCommentText = ""
                    }
                },
                enabled = newCommentText.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Post Comment",
                    tint = if (newCommentText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
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
                    Text("Loading real IMDb user reviews...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    text = if (isTorrent) "No IMDb reviews match the selected filter." else "No comments yet. Be the first to comment!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                filteredComments.forEachIndexed { index, comment ->
                    val isRevealed = revealedSpoilers[comment.id] == true
                    val extraLikes = userLikes[comment.id] ?: 0
                    val extraDislikes = userDislikes[comment.id] ?: 0

                    YouTubeStyleCommentItem(
                        comment = comment,
                        isTorrent = isTorrent,
                        isRevealed = isRevealed,
                        onToggleSpoiler = { revealedSpoilers[comment.id] = !isRevealed },
                        extraLikes = extraLikes,
                        extraDislikes = extraDislikes,
                        onLikeClick = {
                            userLikes[comment.id] = if (extraLikes > 0) 0 else 1
                        },
                        onDislikeClick = {
                            userDislikes[comment.id] = if (extraDislikes > 0) 0 else 1
                        }
                    )

                    if (index < filteredComments.size - 1) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun YouTubeStyleCommentItem(
    comment: VideoComment,
    isTorrent: Boolean,
    isRevealed: Boolean,
    onToggleSpoiler: () -> Unit,
    extraLikes: Int,
    extraDislikes: Int,
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // User Avatar
        if (!comment.authorAvatarUrl.isNullOrEmpty()) {
            AsyncImage(
                model = comment.authorAvatarUrl,
                contentDescription = comment.authorName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
            )
        } else {
            val initialName = comment.authorName.removePrefix("@").trim()
            val initial = if (initialName.isNotEmpty()) initialName.take(1).uppercase() else "U"
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (isTorrent) Color(0xFF1E88E5) else MaterialTheme.colorScheme.primary
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Top Line: @Username • Time Ago • Rating Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val displayName = if (comment.authorName.startsWith("@")) comment.authorName else "@${comment.authorName}"
                Text(
                    text = displayName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "• ${comment.timeAgo}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (comment.rating != null && comment.rating > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = Color(0xFFF5C518).copy(alpha = 0.18f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFF5C518),
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${comment.rating.toInt()}/10",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF5C518)
                            )
                        }
                    }
                }
            }

            // Review Title Headline (if present)
            if (!comment.reviewTitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = comment.reviewTitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Spoiler Tag Alert
            if (comment.isSpoiler) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.clickable { onToggleSpoiler() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isRevealed) "⚠️ Spoiler (Tap to hide)" else "⚠️ Spoiler Alert (Tap to reveal)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF7043)
                    )
                }
            }

            // Main Comment Text (Truncated to max 3 lines)
            if (!comment.isSpoiler || isRevealed) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = comment.commentText,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
                    maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )

                // Read More / Show Less Toggle
                val isLongText = comment.commentText.length > 100 || comment.commentText.contains("\n")
                if (isLongText) {
                    Text(
                        text = if (isExpanded) "Show less" else "... Read more",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable { isExpanded = !isExpanded }
                            .padding(top = 2.dp, bottom = 2.dp)
                    )
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleSpoiler() }
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "This review contains spoilers. Tap to show content.",
                        fontSize = 12.sp,
                        color = Color(0xFFFFAB91),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Reaction Bar: Like button, Dislike button, Reply icon, Reply count pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Like Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onLikeClick() }
                ) {
                    Icon(
                        imageVector = if (extraLikes > 0) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = "Like",
                        modifier = Modifier.size(15.dp),
                        tint = if (extraLikes > 0) Color(0xFFF5C518) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val totalLikes = comment.likeCount + extraLikes
                    if (totalLikes > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatLikeCount(totalLikes),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Dislike Button
                Icon(
                    imageVector = if (extraDislikes > 0) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                    contentDescription = "Dislike",
                    modifier = Modifier
                        .size(15.dp)
                        .clickable { onDislikeClick() },
                    tint = if (extraDislikes > 0) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Reply Icon
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = "Reply",
                    modifier = Modifier
                        .size(15.dp)
                        .clickable { },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Reply Count Pill (e.g. "55 replies >")
                val replyCount = Math.abs(comment.id.hashCode() % 45) + 1
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.clickable { }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$replyCount ${if (replyCount == 1) "reply" else "replies"}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
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
