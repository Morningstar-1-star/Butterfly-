package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.VideoItem
import com.example.util.ThumbnailOptimizer
import com.example.ui.animation.bounceClick

@Composable
fun LandscapeRelatedDrawer(
    isVisible: Boolean,
    videos: List<VideoItem>,
    currentVideoId: String?,
    currentChannelName: String? = null,
    onVideoClick: (VideoItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf("All") }

    // Derive active channel name from current video or list
    val channelName = remember(currentChannelName, videos) {
        currentChannelName?.takeIf { it.isNotBlank() }
            ?: videos.firstOrNull()?.uploaderName?.takeIf { it.isNotBlank() }
    }

    val filterChips = remember(channelName) {
        buildList {
            add("All")
            if (!channelName.isNullOrBlank()) {
                add("From $channelName")
            }
            add("Related")
            add("For you")
            add("Recently uploaded")
            add("Watched")
        }
    }

    val filteredVideos = remember(selectedFilter, videos, channelName) {
        when {
            selectedFilter == "All" -> videos
            selectedFilter.startsWith("From ") && !channelName.isNullOrBlank() -> {
                val matching = videos.filter { it.uploaderName.equals(channelName, ignoreCase = true) }
                if (matching.isNotEmpty()) matching else videos
            }
            selectedFilter == "Related" -> videos.take(20)
            selectedFilter == "For you" -> videos.shuffled(kotlin.random.Random(42))
            selectedFilter == "Recently uploaded" -> {
                val sorted = videos.sortedByDescending { it.uploadDate ?: "" }
                if (sorted.isNotEmpty()) sorted else videos
            }
            selectedFilter == "Watched" -> videos.reversed()
            else -> videos
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.68f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xF0121212),
                            Color(0xFA181818),
                            Color(0xFF0F0F0F)
                        )
                    ),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                )
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.12f),
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                )
                .pointerInput(Unit) {
                    var totalDragDown = 0f
                    detectVerticalDragGestures(
                        onDragStart = { totalDragDown = 0f },
                        onDragEnd = { totalDragDown = 0f },
                        onDragCancel = { totalDragDown = 0f },
                        onVerticalDrag = { change, dragAmount ->
                            totalDragDown += dragAmount
                            if (totalDragDown > 30f) {
                                change.consume()
                                totalDragDown = 0f
                                onDismiss()
                            }
                        }
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Drag Handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(42.dp)
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                )

                // Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "More videos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 17.sp
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.White.copy(alpha = 0.12f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close more videos",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // YouTube-Style Category Filter Chips
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(filterChips) { chipText ->
                        val isSelected = chipText == selectedFilter
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.10f),
                            modifier = Modifier
                                .clickable { selectedFilter = chipText }
                        ) {
                            Text(
                                text = chipText,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.Black else Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Horizontal Scrollable Related Videos Cards
                if (filteredVideos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No related videos found",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 6.dp)
                    ) {
                        items(filteredVideos, key = { (it.providerId ?: "") + "_" + it.id }) { video ->
                            LandscapeRelatedCard(
                                video = video,
                                isPlaying = video.id == currentVideoId,
                                onClick = {
                                    onVideoClick(video)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LandscapeRelatedCard(
    video: VideoItem,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val thumbRequest = ThumbnailOptimizer.buildThumbnailRequest(context, video.thumbnailUrl, crossfadeMillis = 100)

    Card(
        modifier = Modifier
            .width(210.dp)
            .fillMaxHeight()
            .bounceClick(scaleDown = 0.96f) { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) Color(0xFF2A2A2A) else Color(0xFF1E1E1E)
        ),
        border = if (isPlaying) {
            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // Thumbnail container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF111111))
            ) {
                if (thumbRequest != null) {
                    AsyncImage(
                        model = thumbRequest,
                        contentDescription = video.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (video.displayDuration.isNotBlank()) {
                    Text(
                        text = video.displayDuration,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = "PLAYING",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = video.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            val metadataLine = remember(video.uploaderName, video.formattedViews, video.viewCount, video.uploadDate) {
                val views = com.example.util.DateUtils.formatViews(video.viewCount, video.formattedViews)
                val timeAgo = com.example.util.DateUtils.formatRelativeTime(video.uploadDate)
                com.example.util.DateUtils.buildYouTubeMetadataLine(
                    channelName = video.uploaderName,
                    formattedViews = views,
                    timeAgo = timeAgo
                )
            }

            Text(
                text = metadataLine,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
