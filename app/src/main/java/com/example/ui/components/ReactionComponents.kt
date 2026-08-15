package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.VideoCameraFront
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
import com.example.model.VideoItem
import com.example.util.ReactionGroup
import com.example.util.ReactionType

import com.example.ui.animation.bounceClick

@Composable
fun ReactionsView(
    reactionGroups: List<ReactionGroup>,
    isLoading: Boolean,
    onPlayVideo: (VideoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf("ALL") }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Sub-filter Pills Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReactionFilterChip(
                label = "All Reactions (${reactionGroups.size})",
                isSelected = selectedFilter == "ALL",
                onClick = { selectedFilter = "ALL" }
            )
            ReactionFilterChip(
                label = "🎬 Trailer Reacts",
                isSelected = selectedFilter == "TRAILER",
                onClick = { selectedFilter = "TRAILER" }
            )
            ReactionFilterChip(
                label = "🍿 Full & Uncut",
                isSelected = selectedFilter == "UNCUT",
                onClick = { selectedFilter = "UNCUT" }
            )
            ReactionFilterChip(
                label = "🧩 Multi-Part Sets",
                isSelected = selectedFilter == "PARTS",
                onClick = { selectedFilter = "PARTS" }
            )
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Text(
                        text = "Searching reaction channels & multi-part clips...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (reactionGroups.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VideoCameraFront,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "No Reaction Videos Found Yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "We search top YouTube reaction channels for trailer & movie reactions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val filteredList = remember(reactionGroups, selectedFilter) {
                when (selectedFilter) {
                    "TRAILER" -> reactionGroups.filter { it.reactionType == ReactionType.TRAILER_REACTION }
                    "UNCUT" -> reactionGroups.filter { it.reactionType == ReactionType.UNCUT_FULL }
                    "PARTS" -> reactionGroups.filter { it.allParts.size > 1 || it.reactionType == ReactionType.MULTI_PART }
                    else -> reactionGroups
                }
            }

            filteredList.forEach { group ->
                ReactionGroupCard(
                    group = group,
                    onPlayVideo = onPlayVideo
                )
            }
        }
    }
}

@Composable
private fun ReactionFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .height(32.dp)
            .bounceClick(scaleDown = 0.92f)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ReactionGroupCard(
    group: ReactionGroup,
    onPlayVideo: (VideoItem) -> Unit
) {
    var activePartIndex by remember { mutableStateOf(0) }
    val currentVideo = group.allParts.getOrElse(activePartIndex) { group.mainItem }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Video Thumbnail Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
                    .clickable { onPlayVideo(currentVideo) }
            ) {
                if (!currentVideo.thumbnailUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = currentVideo.thumbnailUrl,
                        contentDescription = currentVideo.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Gradient Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.3f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f)
                                )
                            )
                        )
                )

                // Reaction Type Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (group.reactionType) {
                        ReactionType.TRAILER_REACTION -> Color(0xFFE91E63)
                        ReactionType.UNCUT_FULL -> Color(0xFF9C27B0)
                        ReactionType.MULTI_PART -> Color(0xFFFF9800)
                        else -> MaterialTheme.colorScheme.primary
                    },
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = when (group.reactionType) {
                                ReactionType.TRAILER_REACTION -> Icons.Outlined.Movie
                                ReactionType.UNCUT_FULL -> Icons.Default.AutoAwesome
                                else -> Icons.Outlined.VideoCameraFront
                            },
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = group.partLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Duration Badge
                if (currentVideo.displayDuration.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.8f),
                        contentColor = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = currentVideo.displayDuration,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Center Play Icon
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Reaction",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Reaction Title & Channel Name
            Text(
                text = currentVideo.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    modifier = Modifier.size(20.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Mood,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Text(
                    text = group.channelName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (currentVideo.formattedViews.isNotBlank()) {
                    Text(text = "•", fontSize = 12.sp, color = Color.Gray)
                    Text(
                        text = currentVideo.formattedViews,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Multi-Part Selector (If channel cropped and uploaded reaction in multiple parts)
            if (group.allParts.size > 1) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🧩 Reaction Parts (${group.allParts.size}):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9800)
                    )

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        group.allParts.forEachIndexed { index, partVideo ->
                            val isSel = (index == activePartIndex)
                            Surface(
                                onClick = {
                                    activePartIndex = index
                                    onPlayVideo(partVideo)
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSel) Color(0xFFFF9800) else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSel) Color.Black else MaterialTheme.colorScheme.onSurface
                            ) {
                                Text(
                                    text = "Part ${index + 1}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
