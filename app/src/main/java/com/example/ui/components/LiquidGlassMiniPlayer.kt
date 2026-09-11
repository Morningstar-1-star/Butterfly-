package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.StreamData
import com.example.ui.player.GlobalPlayerManager
import com.example.ui.player.PersistentPlayerHost
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * YouTube-Grade Floating Picture-in-Picture Mini Player with:
 * - Free 2D drag anywhere with smooth spring inertia.
 * - Big left (Play/Pause) and right (Close X) buttons matching YouTube's exact look.
 * - Auto-fading dark pill background layer behind buttons (fades out after 2.5-3 seconds).
 * - Swipe right, center, or left to smoothly dismiss/close video.
 * - Multi-touch pinch-to-resize & double tap size cycling.
 * - Signature YouTube red progress bar at the bottom edge.
 */
@Composable
fun LiquidGlassMiniPlayer(
    streamData: StreamData?,
    progressFraction: Float = 0f,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    onNext: () -> Unit = {},
    bottomBarPaddingDp: androidx.compose.ui.unit.Dp = 80.dp,
    statusBarPaddingDp: androidx.compose.ui.unit.Dp = 32.dp,
    modifier: Modifier = Modifier
) {
    if (streamData == null) return

    val videoAspectRatio by GlobalPlayerManager.videoAspectRatio.collectAsState()
    val activeAspectRatio = remember(videoAspectRatio) {
        if (videoAspectRatio in 0.4f..2.5f) videoAspectRatio else 16f / 9f
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val density = LocalDensity.current
        val coroutineScope = rememberCoroutineScope()
        val viewConfig = LocalViewConfiguration.current

        val parentWidthPx = with(density) { maxWidth.toPx() }
        val parentHeightPx = with(density) { maxHeight.toPx() }

        val isVertical = activeAspectRatio < 1.0f
        val compactWidthPx = with(density) { (if (isVertical) 140.dp else 210.dp).toPx() }
        val mediumWidthPx = with(density) { (if (isVertical) 185.dp else 275.dp).toPx() }
        val expandedWidthPx = with(density) { (if (isVertical) 220.dp else 340.dp).toPx().coerceAtMost(parentWidthPx - 16.dp.toPx()) }
        val minWidthPx = with(density) { (if (isVertical) 110.dp else 150.dp).toPx() }
        val maxWidthPx = (parentWidthPx - with(density) { 16.dp.toPx() }).coerceAtLeast(expandedWidthPx)

        val marginPx = with(density) { 10.dp.toPx() }
        val topMarginPx = with(density) { statusBarPaddingDp.toPx() + 8.dp.toPx() }
        val bottomMarginPx = with(density) { bottomBarPaddingDp.toPx() + 8.dp.toPx() }

        // Width animatable for smooth resize transitions & pinch gesture
        val animWidth = remember(streamData.videoId) { Animatable(compactWidthPx) }
        var sizeCycleState by remember(streamData.videoId) { mutableIntStateOf(0) } // 0: Compact, 1: Medium, 2: Large

        val defaultPlayerH = compactWidthPx / activeAspectRatio
        val initialRightX = (parentWidthPx - compactWidthPx - marginPx).coerceAtLeast(marginPx)
        val initialBottomY = (parentHeightPx - defaultPlayerH - bottomMarginPx).coerceAtLeast(topMarginPx)

        // Physics-driven spring animatables for position
        val animX = remember(streamData.videoId) { Animatable(initialRightX) }
        val animY = remember(streamData.videoId) { Animatable(initialBottomY) }

        // Tactile lift feedback when dragging
        val dragScale = remember { Animatable(1.0f) }

        // Entrance scale & alpha
        val enterScale = remember(streamData.videoId) { Animatable(0.92f) }
        val enterAlpha = remember(streamData.videoId) { Animatable(0f) }

        var isDismissing by remember(streamData.videoId) { mutableStateOf(false) }

        // Auto-fading dark pill background layer behind left/right buttons (fades after 2.8s)
        val buttonBgAlpha = remember(streamData.videoId) { Animatable(1.0f) }
        var fadeJob by remember { mutableStateOf<Job?>(null) }

        fun triggerButtonBgActive() {
            fadeJob?.cancel()
            fadeJob = coroutineScope.launch {
                buttonBgAlpha.snapTo(1.0f)
                delay(2800L)
                buttonBgAlpha.animateTo(
                    targetValue = 0.0f,
                    animationSpec = tween(450, easing = LinearOutSlowInEasing)
                )
            }
        }

        // Trigger button background fade timer when entering
        LaunchedEffect(streamData.videoId) {
            triggerButtonBgActive()
        }

        // Smooth Entrance animation
        LaunchedEffect(streamData.videoId) {
            launch {
                enterScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
            launch {
                enterAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(180, easing = LinearOutSlowInEasing)
                )
            }
        }

        // Clamp positions dynamically on screen size or aspect ratio changes
        LaunchedEffect(parentWidthPx, parentHeightPx, activeAspectRatio) {
            val currentW = animWidth.value
            val currentH = currentW / activeAspectRatio
            val maxX = (parentWidthPx - currentW - marginPx).coerceAtLeast(marginPx)
            val maxY = (parentHeightPx - currentH - bottomMarginPx).coerceAtLeast(topMarginPx)

            val clampedX = animX.value.coerceIn(marginPx, maxX)
            val clampedY = animY.value.coerceIn(topMarginPx, maxY)
            animX.snapTo(clampedX)
            animY.snapTo(clampedY)
        }

        val playerW = animWidth.value
        val playerH = playerW / activeAspectRatio

        val dismissWithAnimation: (directionX: Float, directionY: Float) -> Unit = { dirX, dirY ->
            if (!isDismissing) {
                isDismissing = true
                coroutineScope.launch {
                    val targetX = if (dirX > 0) parentWidthPx + 120f else if (dirX < 0) -playerW - 120f else animX.value
                    val targetY = if (dirY > 0) parentHeightPx + 120f else animY.value
                    launch {
                        if (dirX != 0f) {
                            animX.animateTo(
                                targetValue = targetX,
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                                initialVelocity = dirX
                            )
                        }
                    }
                    launch {
                        if (dirY != 0f) {
                            animY.animateTo(
                                targetValue = targetY,
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                                initialVelocity = dirY
                            )
                        }
                    }
                    launch {
                        enterAlpha.animateTo(0f, tween(140, easing = FastOutSlowInEasing))
                    }
                    delay(140L)
                    onClose()
                }
            }
        }

        Surface(
            modifier = Modifier
                .offset {
                    IntOffset(animX.value.roundToInt(), animY.value.roundToInt())
                }
                .graphicsLayer {
                    val combinedScale = enterScale.value * dragScale.value
                    scaleX = combinedScale
                    scaleY = combinedScale
                    alpha = enterAlpha.value
                }
                .size(
                    width = with(density) { playerW.toDp() },
                    height = with(density) { playerH.toDp() }
                )
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(14.dp),
                    spotColor = Color.Black.copy(alpha = 0.75f),
                    ambientColor = Color.Black.copy(alpha = 0.45f)
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.32f),
                            Color.White.copy(alpha = 0.10f),
                            Color.Black.copy(alpha = 0.50f)
                        )
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
                .clip(RoundedCornerShape(14.dp))
                .pointerInput(streamData.videoId, parentWidthPx, parentHeightPx, activeAspectRatio) {
                    val velocityTracker = VelocityTracker()
                    var lastTapTime = 0L

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        velocityTracker.resetTracking()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)

                        var isDragging = false
                        var isPinching = false
                        var totalPanX = 0f
                        var totalPanY = 0f

                        do {
                            val event = awaitPointerEvent()
                            val canceled = event.changes.any { it.isConsumed }
                            if (canceled) break

                            val pointerCount = event.changes.size

                            if (pointerCount >= 2) {
                                // Multi-finger pinch-to-zoom resize
                                isPinching = true
                                val zoom = event.calculateZoom()
                                if (zoom != 1f) {
                                    val newW = (animWidth.value * zoom).coerceIn(minWidthPx, maxWidthPx)
                                    coroutineScope.launch {
                                        animWidth.snapTo(newW)
                                    }
                                }
                                val pan = event.calculatePan()
                                totalPanX += pan.x
                                totalPanY += pan.y
                                val activeW = animWidth.value
                                val activeH = activeW / activeAspectRatio
                                val maxX = (parentWidthPx - activeW - marginPx).coerceAtLeast(marginPx)
                                val maxY = (parentHeightPx - activeH - bottomMarginPx).coerceAtLeast(topMarginPx)
                                coroutineScope.launch {
                                    animX.snapTo((animX.value + pan.x).coerceIn(marginPx, maxX))
                                    animY.snapTo((animY.value + pan.y).coerceIn(topMarginPx, maxY))
                                }
                                event.changes.forEach { it.consume() }
                            } else if (pointerCount == 1) {
                                val change = event.changes.first()
                                if (change.pressed) {
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    val pan = change.positionChange()
                                    totalPanX += pan.x
                                    totalPanY += pan.y

                                    val touchSlop = viewConfig.touchSlop
                                    if (!isDragging && hypot(totalPanX, totalPanY) > touchSlop) {
                                        isDragging = true
                                        // Subtle tactile lift feedback
                                        coroutineScope.launch {
                                            dragScale.animateTo(1.025f, tween(100, easing = LinearOutSlowInEasing))
                                        }
                                        triggerButtonBgActive()
                                    }

                                    if (isDragging) {
                                        coroutineScope.launch {
                                            animX.snapTo(animX.value + pan.x)
                                            animY.snapTo(animY.value + pan.y)
                                        }
                                        change.consume()
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        // Release / Finger Lift
                        if (isDragging) {
                            coroutineScope.launch {
                                dragScale.animateTo(1.0f, spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMediumLow))
                            }

                            val velocity = velocityTracker.calculateVelocity()
                            val vx = velocity.x
                            val vy = velocity.y

                            val activeW = animWidth.value
                            val activeH = activeW / activeAspectRatio

                            // Swipe to close: right, left, or downwards
                            val isDismissRight = (vx > 500f && abs(vx) > abs(vy) * 0.8f) || (animX.value > (parentWidthPx - activeW * 0.40f) && totalPanX > 30f)
                            val isDismissLeft = (vx < -500f && abs(vx) > abs(vy) * 0.8f) || (animX.value < (-activeW * 0.40f) && totalPanX < -30f)
                            val isDismissDown = (vy > 750f && vy > abs(vx) * 1.2f) || (animY.value > (parentHeightPx - activeH * 0.35f) && totalPanY > 50f)

                            when {
                                isDismissRight -> {
                                    dismissWithAnimation(vx.coerceAtLeast(600f), 0f)
                                }
                                isDismissLeft -> {
                                    dismissWithAnimation(vx.coerceAtMost(-600f), 0f)
                                }
                                isDismissDown -> {
                                    dismissWithAnimation(0f, vy.coerceAtLeast(800f))
                                }
                                else -> {
                                    // Smooth rubber bounce settling at the released position anywhere on screen
                                    val minX = marginPx
                                    val maxX = (parentWidthPx - activeW - marginPx).coerceAtLeast(marginPx)
                                    val minY = topMarginPx
                                    val maxY = (parentHeightPx - activeH - bottomMarginPx).coerceAtLeast(topMarginPx)

                                    val projectedX = animX.value + (vx * 0.10f)
                                    val projectedY = animY.value + (vy * 0.10f)

                                    val targetX = projectedX.coerceIn(minX, maxX)
                                    val targetY = projectedY.coerceIn(minY, maxY)

                                    coroutineScope.launch {
                                        launch {
                                            animX.animateTo(
                                                targetValue = targetX,
                                                animationSpec = spring(
                                                    dampingRatio = 0.78f,
                                                    stiffness = Spring.StiffnessMediumLow
                                                ),
                                                initialVelocity = vx
                                            )
                                        }
                                        launch {
                                            animY.animateTo(
                                                targetValue = targetY,
                                                animationSpec = spring(
                                                    dampingRatio = 0.78f,
                                                    stiffness = Spring.StiffnessMediumLow
                                                ),
                                                initialVelocity = vy
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (!isPinching) {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastTapTime < 280L) {
                                // Double-tap: smoothly cycle between Compact -> Medium -> Large sizes
                                sizeCycleState = (sizeCycleState + 1) % 3
                                val targetWidth = when (sizeCycleState) {
                                    0 -> compactWidthPx
                                    1 -> mediumWidthPx
                                    else -> expandedWidthPx
                                }
                                coroutineScope.launch {
                                    animWidth.animateTo(
                                        targetValue = targetWidth,
                                        animationSpec = spring(
                                            dampingRatio = 0.80f,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            } else {
                                // Single tap: reveal buttons background if faded, or expand to full player
                                triggerButtonBgActive()
                                onExpand()
                            }
                            lastTapTime = currentTime
                        }
                    }
                },
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF0A0A0A)
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. VIDEO SURFACE & FALLBACK THUMBNAIL
                if (!streamData.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = streamData.thumbnailUrl,
                        contentDescription = streamData.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                PersistentPlayerHost(
                    useController = false,
                    modifier = Modifier.fillMaxSize()
                )

                // 2. TOP DRAG HANDLE (Subtle YouTube-style pill indicator)
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .width(26.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.40f))
                        .align(Alignment.TopCenter)
                )

                // 3. PROMINENT UPPER YOUTUBE BUTTONS (POSITIONED UP ON MINI PLAYER)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp, start = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // LEFT: PROMINENT PLAY / PAUSE BUTTON (YouTube size ~40dp circle, 24dp icon)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.58f * buttonBgAlpha.value))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                triggerButtonBgActive()
                                onTogglePlay()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause video" else "Play video",
                            tint = Color.White,
                            modifier = Modifier
                                .size(24.dp)
                                .shadow(
                                    elevation = if (buttonBgAlpha.value < 0.2f) 4.dp else 0.dp,
                                    shape = CircleShape,
                                    spotColor = Color.Black
                                )
                        )
                    }

                    // RIGHT: PROMINENT CLOSE ('X') BUTTON (YouTube size ~40dp circle, 22dp icon)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.58f * buttonBgAlpha.value))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                dismissWithAnimation(400f, 0f)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close mini player",
                            tint = Color.White,
                            modifier = Modifier
                                .size(22.dp)
                                .shadow(
                                    elevation = if (buttonBgAlpha.value < 0.2f) 4.dp else 0.dp,
                                    shape = CircleShape,
                                    spotColor = Color.Black
                                )
                        )
                    }
                }

                // 4. YOUTUBE SIGNATURE RED PROGRESS BAR (Bottom Edge)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                            .background(Color(0xFFFF0000))
                    )
                }
            }
        }
    }
}
