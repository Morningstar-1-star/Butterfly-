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
import com.example.model.CastMember
import com.example.model.EpisodeItem
import com.example.model.SeriesSeason
import com.example.model.VideoComment

@Composable
fun CastSection(
    castList: List<CastMember>,
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
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            castList.forEach { member ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    if (!member.avatarUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = member.avatarUrl,
                            contentDescription = member.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
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
                                maxLines = 1
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
        // Tab 1: SEASONS & EPISODES
        TabPill(
            label = "SEASONS & EPISODES",
            icon = Icons.Outlined.Tv,
            isSelected = selectedTab == PlayerTab.SEASONS_EPISODES,
            onClick = { onTabSelected(PlayerTab.SEASONS_EPISODES) }
        )

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

        // Tab 4: Comments
        TabPill(
            label = "Comments ($commentsCount)",
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
    modifier: Modifier = Modifier
) {
    var newCommentText by remember { mutableStateOf("") }
    var commentList by remember { mutableStateOf(comments) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Comment Input Box
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
                shape = RoundedCornerShape(24.dp),
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
                            likeCount = 1
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

        // List of Comments
        if (commentList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No comments yet. Be the first to comment!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                commentList.forEach { comment ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
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
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = comment.authorName.take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = comment.authorName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = comment.timeAgo,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = comment.commentText,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.ThumbUp,
                                    contentDescription = "Like",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${comment.likeCount}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = "Reply",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
