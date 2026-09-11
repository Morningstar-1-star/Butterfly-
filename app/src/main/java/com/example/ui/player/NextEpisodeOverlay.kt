package com.example.ui.player

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.ripple
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * High-precision descriptor for an available next episode in a series.
 */
data class NextEpisodeData(
    val episodeId: String,
    val title: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
    val durationText: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val providerId: String? = null
)

/**
 * Modern, futuristic Glassmorphic Next Episode Autoplay Card & Button.
 *
 * Features:
 * - Fluid horizontal gradient progress countdown timer
 * - Smooth spring physics enter/exit animations
 * - Live thumbnail and episode title badge
 * - Instant "Play Now" tap action + "Dismiss / Watch Credits" cancel action
 * - Non-intrusive bottom-end positioning with elevation shadow
 */
@Composable
fun NextEpisodeOverlay(
    nextEpisode: NextEpisodeData?,
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isLandscape: Boolean,
    onPlayNext: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    countdownTotalSeconds: Int = 10,
    triggerRemainingMs: Long = 28000L
) {
    if (nextEpisode == null) return

    val remainingMs = (durationMs - currentPositionMs).coerceAtLeast(0L)
    val shouldTrigger = durationMs > 30000L && (remainingMs <= triggerRemainingMs || (currentPositionMs >= durationMs - 1000L && durationMs > 0L))

    var isDismissed by remember(nextEpisode.episodeId) { mutableStateOf(false) }
    var countdownRemaining by remember(nextEpisode.episodeId) { mutableIntStateOf(countdownTotalSeconds) }
    val progressAnim = remember(nextEpisode.episodeId) { Animatable(0f) }

    val isVisible = shouldTrigger && !isDismissed

    // Autoplay countdown timer coroutine
    LaunchedEffect(isVisible, isPlaying, nextEpisode.episodeId) {
        if (isVisible && isPlaying) {
            countdownRemaining = countdownTotalSeconds
            progressAnim.snapTo(0f)

            val startTime = System.currentTimeMillis()
            val totalDurationMs = countdownTotalSeconds * 1000L

            while (countdownRemaining > 0 && isVisible && !isDismissed) {
                delay(100)
                val elapsed = System.currentTimeMillis() - startTime
                val fraction = (elapsed.toFloat() / totalDurationMs).coerceIn(0f, 1f)
                progressAnim.snapTo(fraction)
                val remSec = ((totalDurationMs - elapsed) / 1000L).toInt().coerceAtLeast(0)
                countdownRemaining = remSec

                if (elapsed >= totalDurationMs) {
                    break
                }
            }

            if (isVisible && !isDismissed && countdownRemaining <= 0) {
                onPlayNext()
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
        ) { it + 80 } + fadeIn(animationSpec = tween(350, easing = FastOutSlowInEasing)) + scaleIn(
            animationSpec = spring(dampingRatio = 0.80f),
            initialScale = 0.85f
        ),
        exit = slideOutVertically(
            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium)
        ) { it + 80 } + fadeOut(animationSpec = tween(250)) + scaleOut(
            animationSpec = spring(dampingRatio = 0.85f),
            targetScale = 0.90f
        ),
        modifier = modifier
    ) {
        val glassBrush = Brush.verticalGradient(
            colors = listOf(
                Color(0xF0121724),
                Color(0xF80B0E17)
            )
        )

        val borderGradient = Brush.linearGradient(
            colors = listOf(
                Color(0xFF3880FF).copy(alpha = glowAlpha),
                Color(0xFF00E5FF).copy(alpha = glowAlpha * 0.9f),
                Color(0xFF7C3AED).copy(alpha = glowAlpha * 0.7f)
            )
        )

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Transparent,
            shadowElevation = 14.dp,
            modifier = Modifier
                .testTag("next_episode_card")
                .widthIn(max = if (isLandscape) 440.dp else 340.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(glassBrush)
                .border(1.5.dp, borderGradient, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                // Top Header Row: Badge & Dismiss "X"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Cyan Glowing Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF00E5FF).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = when {
                                    nextEpisode.seasonNumber != null && nextEpisode.episodeNumber != null ->
                                        "S${nextEpisode.seasonNumber} · E${nextEpisode.episodeNumber}"
                                    nextEpisode.episodeNumber != null -> "EPISODE ${nextEpisode.episodeNumber}"
                                    else -> "NEXT UP"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF00E5FF),
                                letterSpacing = 0.5.sp
                            )
                        }

                        Text(
                            text = "Auto-playing in ${countdownRemaining}s",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }

                    // Dismiss / Cancel Autoplay Button
                    IconButton(
                        onClick = {
                            isDismissed = true
                            onDismiss()
                        },
                        modifier = Modifier
                            .testTag("dismiss_next_episode_button")
                            .size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss Next Episode",
                            tint = Color.White.copy(alpha = 0.60f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Middle Content Row: Thumbnail + Title + Play Now CTA
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Optional Thumbnail Preview
                    if (!nextEpisode.thumbnailUrl.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .width(if (isLandscape) 84.dp else 68.dp)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .border(0.75.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = nextEpisode.thumbnailUrl,
                                contentDescription = nextEpisode.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    // Title and Series Metadata
                    Column(
                        modifier = Modifier
                            .weight(1f)
                    ) {
                        Text(
                            text = nextEpisode.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!nextEpisode.subtitle.isNullOrBlank()) {
                            Text(
                                text = nextEpisode.subtitle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color.White.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Interactive Fluid Progress Button (Play Now)
                    Box(
                        modifier = Modifier
                            .testTag("play_next_episode_button")
                            .height(38.dp)
                            .widthIn(min = 105.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(19.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true, color = Color.White)
                            ) {
                                onPlayNext()
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // Fluid Horizontal Filling Progress Bar
                        val progressFraction = progressAnim.value.coerceIn(0f, 1f)
                        if (progressFraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = progressFraction)
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0xFF2563EB),
                                                Color(0xFF06B6D4)
                                            )
                                        )
                                    )
                            )
                        }

                        // Button Label & Icon overlay
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Play Now",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Play Next",
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
