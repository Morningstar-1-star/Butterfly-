package com.example.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.VideoHeatmap
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

/**
 * YouTube-authentic Precise Seek Bar.
 * - Anchored firmly to the bottom edge of the player.
 * - Never moves or jumps vertically when controls toggle.
 * - Smooth real-time left/right drag scrubbing.
 * - Vibrant YouTube Yellow active track and thumb.
 * - High-precision floating preview time & thumbnail bubble hovering above thumb.
 */
@Composable
fun YouTubePreciseSeekBar(
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    isControlsVisible: Boolean,
    onSeekStarted: () -> Unit,
    onSeekScrubbing: (scrubPositionMs: Long) -> Unit,
    onSeekFinished: (finalPositionMs: Long) -> Unit,
    modifier: Modifier = Modifier,
    onSlideUpForFineScrubbing: (() -> Unit)? = null,
    previewFrames: List<String> = emptyList(),
    fallbackThumbnailUrl: String? = null,
    segments: List<com.example.smartskip.SkipSegment> = emptyList(),
    chapters: List<com.example.extractor.chapters.VideoChapter> = emptyList(),
    heatmap: VideoHeatmap? = null,
    activeColor: Color = Color(0xFFFFD600),
    bufferedColor: Color = Color.White.copy(alpha = 0.50f),
    inactiveColor: Color = Color.White.copy(alpha = 0.25f),
    thumbColor: Color = Color(0xFFFFD600),
    isLandscape: Boolean = false
) {
    val context = LocalContext.current
    var isDragging by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }
    var trackWidthPx by remember { mutableFloatStateOf(1f) }
    var bubbleWidthPx by remember { mutableIntStateOf(0) }

    val safeDuration = durationMs.coerceAtLeast(1L)
    val displayPosition = if (isDragging) scrubPositionMs else currentPositionMs.coerceIn(0L, safeDuration)
    val progressFraction = (displayPosition.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val bufferedFraction = (bufferedPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val hasHeatmap = heatmap != null && heatmap.isNotEmpty

    // Animated bar thickness and thumb size for YouTube-authentic feel
    val animatedBarHeight by animateDpAsState(
        targetValue = if (isDragging) 4.5.dp else if (isControlsVisible) 3.5.dp else 2.5.dp,
        animationSpec = tween(durationMillis = 180),
        label = "barHeight"
    )

    val animatedThumbRadius by animateDpAsState(
        targetValue = if (isDragging) 6.5.dp else if (isControlsVisible) 4.5.dp else 0.dp,
        animationSpec = tween(durationMillis = 180),
        label = "thumbRadius"
    )

    val animatedThumbAlpha by animateFloatAsState(
        targetValue = if (isDragging || isControlsVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "thumbAlpha"
    )

    // Floating preview frame image corresponding to current scrub position
    val activePreviewUrl = remember(scrubPositionMs, previewFrames, fallbackThumbnailUrl) {
        if (previewFrames.size > 1) {
            val idx = ((scrubPositionMs.toDouble() / safeDuration) * (previewFrames.size - 1))
                .roundToInt()
                .coerceIn(0, previewFrames.size - 1)
            previewFrames[idx]
        } else {
            fallbackThumbnailUrl ?: previewFrames.firstOrNull()
        }
    }

    // Fixed height touch wrapper anchored to bottom
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .onSizeChanged { trackWidthPx = it.width.toFloat().coerceAtLeast(1f) },
        contentAlignment = Alignment.BottomCenter
    ) {
        // Floating YouTube Scrubbing Window with Thumbnail & Time (Floats smoothly above thumb)
        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.85f, animationSpec = tween(120)),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.85f, animationSpec = tween(120)),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            val thumbXPx = progressFraction * trackWidthPx
            val halfBubble = bubbleWidthPx / 2f
            val bubbleLeft = (thumbXPx - halfBubble).coerceIn(8f, (trackWidthPx - bubbleWidthPx - 8f).coerceAtLeast(8f))

            Box(
                modifier = Modifier
                    .offset { IntOffset(bubbleLeft.roundToInt(), -80) }
                    .onSizeChanged { bubbleWidthPx = it.width }
                    .shadow(12.dp, RoundedCornerShape(10.dp))
                    .background(Color(0xFA151515), RoundedCornerShape(10.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .padding(4.dp)
            ) {
                val currentChapter = remember(scrubPositionMs, chapters) {
                    chapters.lastOrNull { scrubPositionMs >= it.startTimeMs }
                }
                val isNearPeak = hasHeatmap && heatmap != null &&
                        kotlin.math.abs(progressFraction - heatmap.peakFraction) < 0.05f

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(IntrinsicSize.Min)
                ) {
                    // Preview Thumbnail Image (if available)
                    if (!activePreviewUrl.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .width(110.dp)
                                .height(62.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(activePreviewUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Scrub Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                    }

                    if (isNearPeak) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = "🔥", fontSize = 10.sp)
                            Text(
                                text = "Most Replayed",
                                color = Color(0xFFFFB300),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (currentChapter != null) {
                        Text(
                            text = currentChapter.title,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = "${formatVideoTimestamp(scrubPositionMs)} / ${formatVideoTimestamp(durationMs)}",
                        color = if (isNearPeak) Color(0xFFFFE082) else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // The Seekbar Canvas Track & Thumb with Touch & Drag Gestures
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
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
            contentAlignment = Alignment.BottomCenter
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // Baseline is locked exactly to the bottom of the canvas (canvasHeight - 4dp)
                // This guarantees ZERO vertical jump or shift when controls toggle
                val barHeightPx = animatedBarHeight.toPx()
                val thumbRadiusPx = animatedThumbRadius.toPx()
                val centerY = canvasHeight - 4.dp.toPx()
                val baselineY = centerY - (barHeightPx / 2f)
                val cornerRadius = CornerRadius(barHeightPx / 2f, barHeightPx / 2f)

                // 0. Heatmap Waveform (Most Replayed Graph) if available
                if (hasHeatmap && heatmap != null && heatmap.points.size >= 4 && isControlsVisible) {
                    val pts = heatmap.points
                    val n = pts.size
                    val maxWaveHeight = 16.dp.toPx()

                    val wavePath = Path()
                    val strokePath = Path()

                    val firstY = baselineY - (pts[0] * maxWaveHeight).coerceAtLeast(1.dp.toPx())
                    wavePath.moveTo(0f, baselineY)
                    wavePath.lineTo(0f, firstY)
                    strokePath.moveTo(0f, firstY)

                    for (i in 0 until n - 1) {
                        val x0 = (i.toFloat() / (n - 1).toFloat()) * canvasWidth
                        val y0 = baselineY - (pts[i] * maxWaveHeight).coerceAtLeast(1.dp.toPx())
                        val x1 = ((i + 1).toFloat() / (n - 1).toFloat()) * canvasWidth
                        val y1 = baselineY - (pts[i + 1] * maxWaveHeight).coerceAtLeast(1.dp.toPx())

                        val midX = (x0 + x1) / 2f
                        wavePath.cubicTo(midX, y0, midX, y1, x1, y1)
                        strokePath.cubicTo(midX, y0, midX, y1, x1, y1)
                    }

                    wavePath.lineTo(canvasWidth, baselineY)
                    wavePath.lineTo(0f, baselineY)
                    wavePath.close()

                    // Unplayed / base heatmap fill
                    drawPath(
                        path = wavePath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.40f),
                                Color.White.copy(alpha = 0.10f)
                            ),
                            startY = baselineY - maxWaveHeight,
                            endY = baselineY
                        )
                    )

                    // Played portion of heatmap highlighted in yellow
                    val activeW = canvasWidth * progressFraction
                    if (activeW > 0f) {
                        clipRect(left = 0f, top = 0f, right = activeW, bottom = canvasHeight) {
                            drawPath(
                                path = wavePath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        activeColor.copy(alpha = 0.65f),
                                        activeColor.copy(alpha = 0.20f)
                                    ),
                                    startY = baselineY - maxWaveHeight,
                                    endY = baselineY
                                )
                            )
                        }
                    }

                    // Top crisp line of heatmap
                    drawPath(
                        path = strokePath,
                        color = Color.White.copy(alpha = 0.75f),
                        style = Stroke(
                            width = 1.2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                // 1. Inactive background track (full width)
                drawRoundRect(
                    color = inactiveColor,
                    topLeft = Offset(0f, centerY - (barHeightPx / 2f)),
                    size = Size(canvasWidth, barHeightPx),
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
                            topLeft = Offset(segStartX, centerY - (barHeightPx / 2f)),
                            size = Size(segWidth, barHeightPx),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                        )
                    }
                }

                // 2. Buffered progress track
                if (bufferedFraction > 0f) {
                    val bufferedWidth = canvasWidth * bufferedFraction
                    drawRoundRect(
                        color = bufferedColor,
                        topLeft = Offset(0f, centerY - (barHeightPx / 2f)),
                        size = Size(bufferedWidth, barHeightPx),
                        cornerRadius = cornerRadius
                    )
                }

                // 3. Active / Played progress track (Bright YouTube Yellow)
                val activeWidth = canvasWidth * progressFraction
                if (activeWidth > 0f) {
                    drawRoundRect(
                        color = activeColor,
                        topLeft = Offset(0f, centerY - (barHeightPx / 2f)),
                        size = Size(activeWidth, barHeightPx),
                        cornerRadius = cornerRadius
                    )
                }

                // 3.5 YouTube Chapter Separator Gaps (Clean 2dp black notches dividing chapters)
                if (chapters.size > 1 && safeDuration > 0) {
                    val gapWidth = 2.dp.toPx()
                    for (ci in 1 until chapters.size) {
                        val ch = chapters[ci]
                        val frac = (ch.startTimeMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
                        val x = frac * canvasWidth
                        if (x > gapWidth && x < canvasWidth - gapWidth) {
                            drawRect(
                                color = Color.Black,
                                topLeft = Offset(x - gapWidth / 2f, centerY - (barHeightPx / 2f) - 1.dp.toPx()),
                                size = Size(gapWidth, barHeightPx + 2.dp.toPx())
                            )
                        }
                    }
                }

                // 4. Scrubber Thumb Circle (YouTube Yellow Dot) - smooth alpha and radius transition
                if (thumbRadiusPx > 0.5f && animatedThumbAlpha > 0.05f) {
                    val thumbX = activeWidth.coerceIn(thumbRadiusPx, canvasWidth - thumbRadiusPx)

                    // Outer subtle glow when dragging
                    if (isDragging) {
                        drawCircle(
                            color = activeColor.copy(alpha = 0.35f * animatedThumbAlpha),
                            radius = thumbRadiusPx + 4.dp.toPx(),
                            center = Offset(thumbX, centerY)
                        )
                    }

                    // Main yellow thumb circle
                    drawCircle(
                        color = thumbColor.copy(alpha = animatedThumbAlpha),
                        radius = thumbRadiusPx,
                        center = Offset(thumbX, centerY)
                    )

                    // Inner white center highlight dot
                    drawCircle(
                        color = Color.White.copy(alpha = animatedThumbAlpha),
                        radius = if (isDragging) 2.2.dp.toPx() else 1.2.dp.toPx(),
                        center = Offset(thumbX, centerY)
                    )
                }
            }
        }
    }
}
