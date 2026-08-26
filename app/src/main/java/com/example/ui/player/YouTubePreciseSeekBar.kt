package com.example.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

fun formatVideoTimestamp(millis: Long): String {
    if (millis <= 0L) return "00:00"
    val totalSeconds = millis / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

@Composable
fun YouTubePreciseSeekBar(
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    onSeekStarted: () -> Unit,
    onSeekScrubbing: (scrubPositionMs: Long) -> Unit,
    onSeekFinished: (finalPositionMs: Long) -> Unit,
    modifier: Modifier = Modifier,
    segments: List<com.example.smartskip.SkipSegment> = emptyList(),
    activeColor: Color = Color(0xFFFF0033),
    bufferedColor: Color = Color.White.copy(alpha = 0.55f),
    inactiveColor: Color = Color.White.copy(alpha = 0.25f),
    thumbColor: Color = Color(0xFFFF0033),
    isLandscape: Boolean = false
) {
    var isDragging by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }
    var trackWidthPx by remember { mutableFloatStateOf(1f) }
    var bubbleWidthPx by remember { mutableIntStateOf(0) }

    val safeDuration = durationMs.coerceAtLeast(1L)
    val displayPosition = if (isDragging) scrubPositionMs else currentPositionMs.coerceIn(0L, safeDuration)
    val progressFraction = (displayPosition.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val bufferedFraction = (bufferedPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isLandscape) 26.dp else 44.dp)
            .onSizeChanged { trackWidthPx = it.width.toFloat().coerceAtLeast(1f) },
        contentAlignment = Alignment.BottomCenter
    ) {
        // Floating YouTube Scrubbing Time Bubble (Appears above finger/thumb)
        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            val thumbXPx = progressFraction * trackWidthPx
            val halfBubble = bubbleWidthPx / 2f
            val bubbleLeft = (thumbXPx - halfBubble).coerceIn(12f, (trackWidthPx - bubbleWidthPx - 12f).coerceAtLeast(12f))

            Box(
                modifier = Modifier
                    .offset { IntOffset(bubbleLeft.roundToInt(), 0) }
                    .onSizeChanged { bubbleWidthPx = it.width }
                    .background(Color(0xEE1A1A1A), RoundedCornerShape(8.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${formatVideoTimestamp(scrubPositionMs)} / ${formatVideoTimestamp(durationMs)}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // The Seekbar Canvas Track & Thumb with Touch Gestures
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(safeDuration) {
                    detectTapGestures(
                        onPress = { offset ->
                            isDragging = true
                            onSeekStarted()
                            val frac = (offset.x / size.width).coerceIn(0f, 1f)
                            scrubPositionMs = (frac * safeDuration).toLong()
                            onSeekScrubbing(scrubPositionMs)

                            val released = tryAwaitRelease()
                            if (released) {
                                onSeekFinished(scrubPositionMs)
                            }
                            isDragging = false
                        }
                    )
                }
                .pointerInput(safeDuration) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            onSeekStarted()
                            val frac = (offset.x / size.width).coerceIn(0f, 1f)
                            scrubPositionMs = (frac * safeDuration).toLong()
                            onSeekScrubbing(scrubPositionMs)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val frac = (change.position.x / size.width).coerceIn(0f, 1f)
                            scrubPositionMs = (frac * safeDuration).toLong()
                            onSeekScrubbing(scrubPositionMs)
                        },
                        onDragEnd = {
                            isDragging = false
                            onSeekFinished(scrubPositionMs)
                        },
                        onDragCancel = {
                            isDragging = false
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(if (isLandscape) 12.dp else 16.dp)) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val centerY = canvasHeight / 2f

                val barHeight = if (isLandscape) {
                    if (isDragging) 3.5.dp.toPx() else 2.dp.toPx()
                } else {
                    if (isDragging) 6.dp.toPx() else 3.5.dp.toPx()
                }
                val cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f)

                // 1. Inactive background track (full width)
                drawRoundRect(
                    color = inactiveColor,
                    topLeft = Offset(0f, centerY - (barHeight / 2f)),
                    size = Size(canvasWidth, barHeight),
                    cornerRadius = cornerRadius
                )

                // 1.5 Smart Skip / SponsorBlock Segments on Seekbar Track
                if (safeDuration > 0 && segments.isNotEmpty()) {
                    for (seg in segments) {
                        val segStartFrac = (seg.startMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
                        val segEndFrac = (seg.endMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
                        val segStartX = segStartFrac * canvasWidth
                        val segWidth = ((segEndFrac - segStartFrac) * canvasWidth).coerceAtLeast(3.dp.toPx())

                        drawRoundRect(
                            color = seg.category.color.copy(alpha = 0.9f),
                            topLeft = Offset(segStartX, centerY - (barHeight / 2f)),
                            size = Size(segWidth, barHeight),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                        )
                    }
                }

                // 2. Buffered progress track
                if (bufferedFraction > 0f) {
                    val bufferedWidth = canvasWidth * bufferedFraction
                    drawRoundRect(
                        color = bufferedColor,
                        topLeft = Offset(0f, centerY - (barHeight / 2f)),
                        size = Size(bufferedWidth, barHeight),
                        cornerRadius = cornerRadius
                    )
                }

                // 3. Active / Played progress track
                val activeWidth = canvasWidth * progressFraction
                if (activeWidth > 0f) {
                    drawRoundRect(
                        color = activeColor,
                        topLeft = Offset(0f, centerY - (barHeight / 2f)),
                        size = Size(activeWidth, barHeight),
                        cornerRadius = cornerRadius
                    )
                }

                // 4. Scrubber Thumb Circle
                val thumbRadius = if (isLandscape) {
                    if (isDragging) 5.dp.toPx() else 3.dp.toPx()
                } else {
                    if (isDragging) 7.5.dp.toPx() else 5.dp.toPx()
                }
                val thumbX = activeWidth.coerceIn(thumbRadius, canvasWidth - thumbRadius)

                // Outer subtle glow when dragging
                if (isDragging) {
                    drawCircle(
                        color = activeColor.copy(alpha = 0.35f),
                        radius = thumbRadius + 4.dp.toPx(),
                        center = Offset(thumbX, centerY)
                    )
                }

                // Main red thumb circle
                drawCircle(
                    color = thumbColor,
                    radius = thumbRadius,
                    center = Offset(thumbX, centerY)
                )

                // Inner white center dot
                drawCircle(
                    color = Color.White,
                    radius = if (isDragging) 2.5.dp.toPx() else 1.5.dp.toPx(),
                    center = Offset(thumbX, centerY)
                )
            }
        }
    }
}
