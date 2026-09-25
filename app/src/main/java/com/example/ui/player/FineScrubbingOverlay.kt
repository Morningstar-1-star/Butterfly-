package com.example.ui.player

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import com.example.util.PreviewFrameResolver
import kotlinx.coroutines.launch

/**
 * YouTube-Authentic "Slide Up for Fine Scrubbing" Filmstrip Overlay.
 * Allows users to scrub frame-by-frame through the video timeline with high-precision thumbnails,
 * zoomed preview, and instant seek commit.
 */
@Composable
fun FineScrubbingOverlay(
    isVisible: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    streamData: StreamData?,
    previewItem: VideoItem?,
    onScrubPositionChange: (Long) -> Unit,
    onConfirmSeek: (Long) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
        modifier = modifier.fillMaxSize()
    ) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        var tempPositionMs by remember(currentPositionMs, isVisible) { mutableLongStateOf(currentPositionMs) }
        val safeDuration = durationMs.coerceAtLeast(10_000L)

        // Resolve frame thumbnails
        val rawFrames = remember(streamData, previewItem) {
            val direct = streamData?.previewThumbnails?.takeIf { it.isNotEmpty() }
                ?: previewItem?.previewThumbnails?.takeIf { it.isNotEmpty() }
            if (!direct.isNullOrEmpty()) {
                direct
            } else if (previewItem != null) {
                PreviewFrameResolver.resolvePreviewFrames(previewItem)
            } else if (streamData != null) {
                val dummy = VideoItem(
                    id = streamData.videoId,
                    title = streamData.title,
                    uploaderName = streamData.channelName,
                    thumbnailUrl = streamData.thumbnailUrl,
                    providerId = streamData.providerId,
                    previewThumbnails = streamData.previewThumbnails
                )
                PreviewFrameResolver.resolvePreviewFrames(dummy)
            } else emptyList()
        }

        val filmstripItems = remember(rawFrames, safeDuration) {
            val count = if (rawFrames.size > 1) rawFrames.size else 16
            (0 until count).map { i ->
                val time = ((i.toDouble() / (count - 1).coerceAtLeast(1)) * safeDuration).toLong()
                val img = if (rawFrames.isNotEmpty()) rawFrames[i % rawFrames.size] else streamData?.thumbnailUrl ?: ""
                FineFrameItem(
                    index = i,
                    timeMs = time,
                    imageUrl = img
                )
            }
        }

        // Selected preview frame
        val currentFrameImage = remember(tempPositionMs, filmstripItems) {
            val idx = filmstripItems.indexOfLast { tempPositionMs >= it.timeMs }.coerceAtLeast(0)
            filmstripItems.getOrNull(idx)?.imageUrl ?: streamData?.thumbnailUrl ?: ""
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        if (dragAmount.y > 20f) {
                            onCancel()
                        } else {
                            val fractionDelta = dragAmount.x / (size.width.toFloat().coerceAtLeast(1f))
                            val deltaMs = (fractionDelta * safeDuration * 0.4f).toLong()
                            tempPositionMs = (tempPositionMs + deltaMs).coerceIn(0L, safeDuration)
                            onScrubPositionChange(tempPositionMs)
                        }
                    }
                }
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header: Drag handle, dismiss arrow & Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel fine scrubbing",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Swipe down to close",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Fine Scrubbing",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = { onConfirmSeek(tempPositionMs) },
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Confirm seek",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Center Magnified Live Preview Card
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .height(115.dp)
                        .shadow(12.dp, RoundedCornerShape(12.dp))
                        .background(Color(0xFF1C1C1C), RoundedCornerShape(12.dp))
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(currentFrameImage)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Fine scrub frame",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Bottom Pill with Exact Timestamp
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp)
                            .background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(6.dp))
                            .border(0.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${formatVideoTimestamp(tempPositionMs)} / ${formatVideoTimestamp(durationMs)}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Horizontal Filmstrip Scrubber Track
                val listState = rememberLazyListState()
                val currentFraction = (tempPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

                LazyRow(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(filmstripItems) { _, item ->
                        val isNear = kotlin.math.abs(item.timeMs - tempPositionMs) < (safeDuration / filmstripItems.size)
                        Box(
                            modifier = Modifier
                                .width(54.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(6.dp))
                                .border(
                                    if (isNear) 2.dp else 0.5.dp,
                                    if (isNear) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f),
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    tempPositionMs = item.timeMs
                                    onScrubPositionChange(tempPositionMs)
                                }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(item.imageUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

data class FineFrameItem(
    val index: Int,
    val timeMs: Long,
    val imageUrl: String
)
