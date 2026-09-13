package com.example.ui.player.dynamicisland

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.player.GlobalPlayerManager

/**
 * Apple-grade Dynamic Island UI for Android:
 * Morphs seamlessly between a compact notch pill and an expanded media control card.
 */
@Composable
fun DynamicIslandContent(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpenFullApp: () -> Unit,
    onCloseIsland: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val streamData by GlobalPlayerManager.activeStreamData.collectAsState()
    val isPlaying by GlobalPlayerManager.isPlaying.collectAsState()
    val currentPosMs by GlobalPlayerManager.currentPositionMs.collectAsState()
    val durationMs by GlobalPlayerManager.durationMs.collectAsState()

    val progressFraction = remember(currentPosMs, durationMs) {
        if (durationMs > 0) (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    }

    val title = streamData?.title ?: "Butterfly Audio Playback"
    val channel = streamData?.channelName ?: "Butterfly Player"
    val thumbUrl = streamData?.thumbnailUrl

    // Deep obsidian black glass styling
    val islandCornerRadius = if (isExpanded) 28.dp else 22.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, start = 8.dp, end = 8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            modifier = Modifier
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                .widthIn(min = if (isExpanded) 330.dp else 160.dp, max = if (isExpanded) 370.dp else 220.dp)
                .shadow(
                    elevation = if (isExpanded) 18.dp else 8.dp,
                    shape = RoundedCornerShape(islandCornerRadius),
                    spotColor = Color.Black.copy(alpha = 0.8f),
                    ambientColor = Color.Black.copy(alpha = 0.5f)
                )
                .clip(RoundedCornerShape(islandCornerRadius))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = if (isExpanded) 0.22f else 0.15f),
                    shape = RoundedCornerShape(islandCornerRadius)
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (!isExpanded) {
                                onToggleExpand()
                            }
                        },
                        onLongPress = {
                            onOpenFullApp()
                        }
                    )
                },
            color = Color(0xF508080C),
            contentColor = Color.White
        ) {
            if (isExpanded) {
                ExpandedIslandView(
                    title = title,
                    channel = channel,
                    thumbnailUrl = thumbUrl,
                    isPlaying = isPlaying,
                    currentPosMs = currentPosMs,
                    durationMs = durationMs,
                    progressFraction = progressFraction,
                    onTogglePlay = { GlobalPlayerManager.togglePlayPause() },
                    onSeekBack = { GlobalPlayerManager.seekBackward(10000L) },
                    onSeekForward = { GlobalPlayerManager.seekForward(10000L) },
                    onSeekTo = { posMs -> GlobalPlayerManager.seekTo(posMs) },
                    onOpenApp = onOpenFullApp,
                    onCollapse = onToggleExpand,
                    onClose = onCloseIsland
                )
            } else {
                CollapsedPillView(
                    title = title,
                    thumbnailUrl = thumbUrl,
                    isPlaying = isPlaying,
                    currentPosMs = currentPosMs,
                    durationMs = durationMs,
                    onTap = onToggleExpand
                )
            }
        }
    }
}

/**
 * Compact Pill State (Sits naturally over/beside the camera notch)
 */
@Composable
private fun CollapsedPillView(
    title: String,
    thumbnailUrl: String?,
    isPlaying: Boolean,
    currentPosMs: Long,
    durationMs: Long,
    onTap: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(38.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Circular Thumbnail / Album Art
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E1E24)),
            contentAlignment = Alignment.Center
        ) {
            if (!thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Headphones,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Center: Animated Audio Equalizer Waves or Brief Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.5.dp),
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            EqualizerBar(targetHeight = if (isPlaying) 14.dp else 4.dp, animDelay = 0)
            EqualizerBar(targetHeight = if (isPlaying) 20.dp else 4.dp, animDelay = 150)
            EqualizerBar(targetHeight = if (isPlaying) 11.dp else 4.dp, animDelay = 300)
            EqualizerBar(targetHeight = if (isPlaying) 17.dp else 4.dp, animDelay = 450)
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right: Time Remaining or Play State
        val timeText = formatDuration(currentPosMs)
        Text(
            text = timeText,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFFFD54F),
            modifier = Modifier.padding(end = 4.dp)
        )
    }
}

/**
 * Animated Bouncing Equalizer Bars
 */
@Composable
private fun EqualizerBar(targetHeight: androidx.compose.ui.unit.Dp, animDelay: Int) {
    val infiniteTransition = rememberInfiniteTransition()
    val barHeightFraction by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400 + animDelay, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .width(2.5.dp)
            .height(targetHeight * barHeightFraction)
            .clip(RoundedCornerShape(2.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF00E5FF), Color(0xFF3F51B5))
                )
            )
    )
}

/**
 * Expanded Island State (Rich Apple-Inspired Media Center)
 */
@Composable
private fun ExpandedIslandView(
    title: String,
    channel: String,
    thumbnailUrl: String?,
    isPlaying: Boolean,
    currentPosMs: Long,
    durationMs: Long,
    progressFraction: Float,
    onTogglePlay: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onOpenApp: () -> Unit,
    onCollapse: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp)
    ) {
        // Top Header: Branding tag + Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE91E63)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
                Text(
                    text = "BUTTERFLY • AUDIO MODE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Open App Button
                IconButton(
                    onClick = onOpenApp,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInFull,
                        contentDescription = "Return to Butterfly Video Player",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Minimize / Collapse Button
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Collapse Island",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Center Card: Video Preview / Thumbnail + Title & Artist
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .clickable { onOpenApp() }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Live Video / Thumbnail Preview Box
            Box(
                modifier = Modifier
                    .size(width = 68.dp, height = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (!thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // Subtle video expand hint overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInFull,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = channel,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Progress Bar & Duration Labels
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressFraction)
                        .background(Color(0xFF00E5FF))
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(currentPosMs),
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
                val remainingMs = (durationMs - currentPosMs).coerceAtLeast(0L)
                Text(
                    text = if (durationMs > 0) "-${formatDuration(remainingMs)}" else "--:--",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Bottom Controls: Replay 10, Play/Pause, Forward 10
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Seek -10 sec
            IconButton(
                onClick = onSeekBack,
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Replay10,
                    contentDescription = "Rewind 10 seconds",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            // Play / Pause Circle Button
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { onTogglePlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            // Seek +10 sec
            IconButton(
                onClick = onSeekForward,
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Forward10,
                    contentDescription = "Forward 10 seconds",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
