package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.StreamData
import com.example.model.VideoItem
import com.example.ui.player.formatVideoTimestamp
import com.example.util.PreviewFrameResolver
import kotlinx.coroutines.launch

/**
 * Interactive Screenshot & Scene Timing Gallery with Arrow Dropdown (SpankBang & Universal Video Sources).
 * Allows users to click any screenshot timing of the video and have the player instantly seek and play
 * from that exact timestamp across SpankBang, YouTube, XNXX, Eporner, Xvideos, HellPorno, Bilibili, and all sources.
 */
@Composable
fun InteractiveTimelinePreviewStrip(
    currentPositionMs: Long,
    durationMs: Long,
    streamData: StreamData?,
    previewItem: VideoItem?,
    onSeekTo: (timestampMs: Long) -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = true
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    var isGridView by remember { mutableStateOf(false) }

    // 1. Resolve timeline frames from streamData or previewItem
    val rawFrames: List<String> = remember(streamData?.videoId, previewItem?.id, streamData?.thumbnailUrl, previewItem?.thumbnailUrl, streamData?.previewThumbnails, previewItem?.previewThumbnails) {
        val streamPreviews = streamData?.previewThumbnails?.takeIf { it.isNotEmpty() }
        val itemPreviews = previewItem?.previewThumbnails?.takeIf { it.isNotEmpty() }
        if (!streamPreviews.isNullOrEmpty()) {
            streamPreviews
        } else if (!itemPreviews.isNullOrEmpty()) {
            itemPreviews
        } else if (previewItem != null) {
            PreviewFrameResolver.resolvePreviewFrames(previewItem)
        } else if (streamData != null) {
            val dummyItem = VideoItem(
                id = streamData.videoId,
                title = streamData.title,
                uploaderName = streamData.channelName,
                thumbnailUrl = streamData.thumbnailUrl,
                providerId = streamData.providerId,
                previewThumbnails = streamData.previewThumbnails
            )
            PreviewFrameResolver.resolvePreviewFrames(dummyItem)
        } else {
            emptyList()
        }
    }

    val safeDuration = durationMs.coerceAtLeast(10_000L)

    // Calculate timestamp per frame index
    val timelineCards: List<TimelineFrameData> = remember(rawFrames, safeDuration, streamData?.chapters) {
        val chapters = streamData?.chapters ?: emptyList()
        if (rawFrames.size > 1) {
            rawFrames.mapIndexed { index, url ->
                val timeMs = ((index.toDouble() / (rawFrames.size - 1).coerceAtLeast(1)) * safeDuration).toLong()
                val matchedChapter = chapters.lastOrNull { timeMs >= it.startTimeMs }
                TimelineFrameData(
                    index = index,
                    imageUrl = url,
                    timestampMs = timeMs,
                    timeLabel = formatVideoTimestamp(timeMs),
                    chapterTitle = matchedChapter?.title
                )
            }
        } else if (chapters.size > 1) {
            // If chapters available, map each chapter to a timing card
            chapters.mapIndexed { index, ch ->
                TimelineFrameData(
                    index = index,
                    imageUrl = rawFrames.firstOrNull() ?: streamData?.thumbnailUrl ?: "",
                    timestampMs = ch.startTimeMs,
                    timeLabel = formatVideoTimestamp(ch.startTimeMs),
                    chapterTitle = ch.title
                )
            }
        } else if (rawFrames.size == 1 && safeDuration > 30_000L) {
            // Single thumbnail fallback: create interval timing cards across the video
            val baseThumb = rawFrames.first()
            val totalPoints = 12
            (0 until totalPoints).map { i ->
                val timeMs = ((i.toDouble() / (totalPoints - 1).coerceAtLeast(1)) * safeDuration).toLong()
                TimelineFrameData(
                    index = i,
                    imageUrl = baseThumb,
                    timestampMs = timeMs,
                    timeLabel = formatVideoTimestamp(timeMs)
                )
            }
        } else {
            emptyList()
        }
    }

    if (timelineCards.isEmpty()) return

    // Find active frame matching current playback time
    val activeIndex = remember(currentPositionMs, timelineCards) {
        timelineCards.indexOfLast { currentPositionMs >= it.timestampMs }.coerceAtLeast(0)
    }

    // Auto-scroll carousel to keep active frame in center
    LaunchedEffect(activeIndex, isExpanded, isGridView) {
        if (isExpanded && !isGridView && !listState.isScrollInProgress && activeIndex in timelineCards.indices) {
            val targetIndex = (activeIndex - 1).coerceAtLeast(0)
            listState.animateScrollToItem(targetIndex)
        }
    }

    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "arrowRotation"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            // 1. Arrow Dropdown Header Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Screenshots & Scene Timing",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            // Count Badge
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "${timelineCards.size}",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Tap any screenshot to jump & play from that timing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Grid / Carousel Toggle
                    if (isExpanded) {
                        IconButton(
                            onClick = { isGridView = !isGridView },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isGridView) Icons.Default.ViewCarousel else Icons.Default.GridView,
                                contentDescription = "Toggle Grid/Carousel",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Dropdown Arrow Button
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse screenshots" else "Expand screenshots",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(22.dp)
                                .rotate(arrowRotation)
                        )
                    }
                }
            }

            // 2. Expandable Content: Carousel Strip or Grid of Screenshot Timing Cards
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    if (isGridView) {
                        // Matrix Grid Layout
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 130.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            itemsIndexed(timelineCards, key = { idx, item -> "${item.timestampMs}_$idx" }) { index, item ->
                                ScreenshotTimingCard(
                                    item = item,
                                    isActive = index == activeIndex,
                                    onSeekTo = onSeekTo
                                )
                            }
                        }
                    } else {
                        // Horizontal Carousel Strip with Arrow Navigation
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left Arrow Button (<)
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        val currentFirst = listState.firstVisibleItemIndex
                                        val prevIndex = (currentFirst - 3).coerceAtLeast(0)
                                        listState.animateScrollToItem(prevIndex)
                                    }
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                                    contentDescription = "Previous screenshot timings",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp).offset(x = 2.dp)
                                )
                            }

                            // Horizontal Frame Carousel
                            LazyRow(
                                state = listState,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                            ) {
                                itemsIndexed(timelineCards, key = { idx, item -> "${item.timestampMs}_$idx" }) { index, item ->
                                    ScreenshotTimingCard(
                                        item = item,
                                        isActive = index == activeIndex,
                                        onSeekTo = onSeekTo
                                    )
                                }
                            }

                            // Right Arrow Button (>)
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        val currentFirst = listState.firstVisibleItemIndex
                                        val nextIndex = (currentFirst + 3).coerceAtMost(timelineCards.size - 1)
                                        listState.animateScrollToItem(nextIndex)
                                    }
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = "Next screenshot timings",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScreenshotTimingCard(
    item: TimelineFrameData,
    isActive: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val borderColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.20f),
        label = "frameBorder"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isActive) 2.5.dp else 1.dp,
        label = "frameBorderWidth"
    )

    Surface(
        modifier = modifier
            .width(140.dp)
            .height(88.dp)
            .clickable {
                onSeekTo(item.timestampMs)
            },
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF181818),
        border = BorderStroke(borderWidth, borderColor),
        shadowElevation = if (isActive) 6.dp else 2.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(item.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Scene at ${item.timeLabel}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Vignette gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.60f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.75f)
                            )
                        )
                    )
            )

            // Top-Left Exact Timestamp Badge (Click to play from here)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp)
                    .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                    .border(0.5.dp, if (isActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = item.timeLabel,
                    color = if (isActive) MaterialTheme.colorScheme.primary else Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Top-Right Scene Index
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "#${item.index + 1}",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Bottom-Left Chapter / Scene info
            if (!item.chapterTitle.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(5.dp)
                        .widthIn(max = 85.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = item.chapterTitle,
                        color = Color.White,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            // Active Playing indicator
            if (isActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "PLAYING",
                        color = Color.Black,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

data class TimelineFrameData(
    val index: Int,
    val imageUrl: String,
    val timestampMs: Long,
    val timeLabel: String,
    val chapterTitle: String? = null
)
