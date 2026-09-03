package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * YouTube-Grade Floating PiP Mini Player with Inertia Sliding Physics & Fluid Touch Feedback.
 *
 * Features:
 * - Buttery smooth 60/120Hz gesture tracking with RenderThread hardware translation (zero layout relayout during drag).
 * - Momentum sliding with spring damping inertia and magnetic edge docking.
 * - Swipe-to-dismiss: smoothly flings off screen (left, right, or down) with velocity and fades out.
 * - Swipe-to-expand: flinging up smoothly expands into the full screen player.
 * - Double-tap to smoothly toggle between Compact (224dp) and Expanded (330dp) mode with spring physics.
 * - Multi-touch pinch-to-zoom resizing.
 * - YouTube signature thin red progress indicator at the bottom edge.
 * - Premium AMOLED frosted glass styling with multi-layer shadow, subtle highlight border, and top drag handle.
 * - Dedicated tactile quick-action pills: Expand, Close (X), and Play/Pause.
 * - Full controls overlay with auto-hide timer (3 seconds).
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
        val compactWidthPx = with(density) { (if (isVertical) 140.dp else 226.dp).toPx() }
        val expandedWidthPx = with(density) { (if (isVertical) 205.dp else 336.dp).toPx().coerceAtMost(parentWidthPx - 24.dp.toPx()) }
        val minWidthPx = with(density) { (if (isVertical) 120.dp else 160.dp).toPx() }
        val maxWidthPx = (parentWidthPx - with(density) { 16.dp.toPx() }).coerceAtLeast(expandedWidthPx)

        val marginPx = with(density) { 12.dp.toPx() }
        val topMarginPx = with(density) { statusBarPaddingDp.toPx() + 8.dp.toPx() }
        val bottomMarginPx = with(density) { bottomBarPaddingDp.toPx() + 10.dp.toPx() }

        // Width animation state for smooth double-tap toggle or pinch resize
        val animWidth = remember(streamData.videoId) { Animatable(compactWidthPx) }
        var isExpandedSize by remember(streamData.videoId) { mutableStateOf(false) }

        val defaultPlayerH = compactWidthPx / activeAspectRatio
        val initialRightX = (parentWidthPx - compactWidthPx - marginPx).coerceAtLeast(marginPx)
        val initialBottomY = (parentHeightPx - defaultPlayerH - bottomMarginPx).coerceAtLeast(topMarginPx)

        // Physics-driven spring animatables for position
        val animX = remember(streamData.videoId) { Animatable(initialRightX) }
        val animY = remember(streamData.videoId) { Animatable(initialBottomY) }

        // Subtle tactile elevation scaling when dragging
        val dragScale = remember { Animatable(1.0f) }

        // Entrance scale & alpha
        val enterScale = remember(streamData.videoId) { Animatable(0.92f) }
        val enterAlpha = remember(streamData.videoId) { Animatable(0f) }

        var isExpandingToFullscreen by remember(streamData.videoId) { mutableStateOf(false) }
        var isDismissing by remember(streamData.videoId) { mutableStateOf(false) }

        val performExpandToFullscreen: () -> Unit = {
            if (!isExpandingToFullscreen && !isDismissing) {
                isExpandingToFullscreen = true
                onExpand()
            }
        }

        // Overlay controls visibility and auto-hide timer
        var areControlsVisible by remember { mutableStateOf(false) }
        var hideControlsJob by remember { mutableStateOf<Job?>(null) }

        fun triggerControlsVisibility(visible: Boolean) {
            areControlsVisible = visible
            hideControlsJob?.cancel()
            if (visible) {
                hideControlsJob = coroutineScope.launch {
                    delay(3000L)
                    areControlsVisible = false
                }
            }
        }

        // Silky entrance animation
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
                    animationSpec = tween(200, easing = LinearOutSlowInEasing)
                )
            }
        }

        // Clamp positions dynamically if boundaries or orientation change
        LaunchedEffect(parentWidthPx, parentHeightPx, activeAspectRatio) {
            val currentW = animWidth.value
            val currentH = currentW / activeAspectRatio
            val rightX = (parentWidthPx - currentW - marginPx).coerceAtLeast(marginPx)
            val bottomY = (parentHeightPx - currentH - bottomMarginPx).coerceAtLeast(topMarginPx)

            val clampedX = animX.value.coerceIn(marginPx, rightX)
            val clampedY = animY.value.coerceIn(topMarginPx, bottomY)
            animX.snapTo(clampedX)
            animY.snapTo(clampedY)
        }

        val playerW = animWidth.value
        val playerH = playerW / activeAspectRatio

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
                    shape = RoundedCornerShape(16.dp),
                    spotColor = Color.Black.copy(alpha = 0.75f),
                    ambientColor = Color.Black.copy(alpha = 0.40f)
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.08f),
                            Color.Black.copy(alpha = 0.40f)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .clip(RoundedCornerShape(16.dp))
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
                                // Pinch-to-zoom resize
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
                                            dragScale.animateTo(1.025f, tween(120, easing = LinearOutSlowInEasing))
                                        }
                                    }

                                    if (isDragging) {
                                        coroutineScope.launch {
                                            // Allow elastic overdrag outside viewport for physical sensation
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
                            // Settle tactile lift back to 1.0f
                            coroutineScope.launch {
                                dragScale.animateTo(1.0f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
                            }

                            val velocity = velocityTracker.calculateVelocity()
                            val vx = velocity.x
                            val vy = velocity.y

                            val activeW = animWidth.value
                            val activeH = activeW / activeAspectRatio

                            // Check 1: Swipe-to-dismiss horizontally (YouTube Fling or Drag past threshold)
                            val isDismissRight = (vx > 1000f) || (animX.value > (parentWidthPx - activeW * 0.40f) && totalPanX > 35f)
                            val isDismissLeft = (vx < -1000f) || (animX.value < (-activeW * 0.40f) && totalPanX < -35f)

                            // Check 2: Swipe-to-dismiss downwards
                            val isDismissDown = (vy > 1200f && vy > abs(vx) * 1.2f) || (animY.value > (parentHeightPx - activeH * 0.40f) && totalPanY > 50f)

                            // Check 3: Swipe-to-expand upwards (Fling Up expands directly)
                            val isExpandUp = (vy < -900f && abs(vy) > abs(vx) * 1.2f) || (totalPanY < -60f && abs(totalPanY) > abs(totalPanX) * 1.2f)

                            when {
                                isExpandUp -> {
                                    performExpandToFullscreen()
                                }
                                isDismissRight -> {
                                    isDismissing = true
                                    coroutineScope.launch {
                                        launch {
                                            animX.animateTo(
                                                targetValue = parentWidthPx + 80f,
                                                animationSpec = spring(
                                                    dampingRatio = 0.85f,
                                                    stiffness = Spring.StiffnessMediumLow
                                                ),
                                                initialVelocity = vx
                                            )
                                        }
                                        launch {
                                            enterAlpha.animateTo(0f, tween(160, easing = FastOutSlowInEasing))
                                        }
                                        delay(160L)
                                        onClose()
                                    }
                                }
                                isDismissLeft -> {
                                    isDismissing = true
                                    coroutineScope.launch {
                                        launch {
                                            animX.animateTo(
                                                targetValue = -activeW - 80f,
                                                animationSpec = spring(
                                                    dampingRatio = 0.85f,
                                                    stiffness = Spring.StiffnessMediumLow
                                                ),
                                                initialVelocity = vx
                                            )
                                        }
                                        launch {
                                            enterAlpha.animateTo(0f, tween(160, easing = FastOutSlowInEasing))
                                        }
                                        delay(160L)
                                        onClose()
                                    }
                                }
                                isDismissDown -> {
                                    isDismissing = true
                                    coroutineScope.launch {
                                        launch {
                                            animY.animateTo(
                                                targetValue = parentHeightPx + 80f,
                                                animationSpec = spring(
                                                    dampingRatio = 0.85f,
                                                    stiffness = Spring.StiffnessMediumLow
                                                ),
                                                initialVelocity = vy
                                            )
                                        }
                                        launch {
                                            enterAlpha.animateTo(0f, tween(160, easing = FastOutSlowInEasing))
                                        }
                                        delay(160L)
                                        onClose()
                                    }
                                }
                                else -> {
                                    // Buttery smooth YouTube magnetic edge docking with momentum sliding inertia
                                    val minX = marginPx
                                    val maxX = (parentWidthPx - activeW - marginPx).coerceAtLeast(marginPx)
                                    val minY = topMarginPx
                                    val maxY = (parentHeightPx - activeH - bottomMarginPx).coerceAtLeast(topMarginPx)

                                    val projectedX = animX.value + (vx * 0.16f)
                                    val projectedY = animY.value + (vy * 0.16f)

                                    val targetX = when {
                                        vx > 450f -> maxX // Flung firmly towards right
                                        vx < -450f -> minX // Flung firmly towards left
                                        (projectedX + activeW / 2f) >= (parentWidthPx / 2f) -> maxX // Settle right
                                        else -> minX // Settle left
                                    }
                                    val targetY = projectedY.coerceIn(minY, maxY)

                                    coroutineScope.launch {
                                        launch {
                                            animX.animateTo(
                                                targetValue = targetX,
                                                animationSpec = spring(
                                                    dampingRatio = 0.82f,
                                                    stiffness = Spring.StiffnessMediumLow
                                                ),
                                                initialVelocity = vx
                                            )
                                        }
                                        launch {
                                            animY.animateTo(
                                                targetValue = targetY,
                                                animationSpec = spring(
                                                    dampingRatio = 0.82f,
                                                    stiffness = Spring.StiffnessMediumLow
                                                ),
                                                initialVelocity = vy
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (!isPinching) {
                            // Touch was a clean tap!
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastTapTime < 280L) {
                                // Double-tap: smoothly toggle between compact & expanded PiP width!
                                isExpandedSize = !isExpandedSize
                                val targetWidth = if (isExpandedSize) expandedWidthPx else compactWidthPx
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
                                // Single tap: Expand directly to full screen just like YouTube!
                                performExpandToFullscreen()
                            }
                            lastTapTime = currentTime
                        }
                    }
                },
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0F0F0F)
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

                // 2. TOP DRAG HANDLE AFFORDANCE (Subtle YouTube-style pill)
                Box(
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .width(28.dp)
                        .height(3.5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.40f))
                        .align(Alignment.TopCenter)
                )

                // 3. PERSISTENT QUICK ACTIONS (Always available when controls are idle)
                if (!areControlsVisible) {
                    // Top-Left: Quick Expand to Fullscreen
                    Surface(
                        onClick = { performExpandToFullscreen() },
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .size(28.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Expand to full screen",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Top-Right: Quick Dismiss (X)
                    Surface(
                        onClick = {
                            isDismissing = true
                            coroutineScope.launch {
                                launch { animY.animateTo(animY.value + 60f, tween(140)) }
                                launch { enterAlpha.animateTo(0f, tween(140)) }
                                delay(140)
                                onClose()
                            }
                        },
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(28.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close mini player",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Bottom-Right: Quick Play/Pause Pill
                    Surface(
                        onClick = { onTogglePlay() },
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 6.dp, bottom = 8.dp)
                            .size(28.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // 4. RICH FROSTED GLASS CONTROLS OVERLAY (When activated)
                AnimatedVisibility(
                    visible = areControlsVisible,
                    enter = fadeIn(animationSpec = tween(150, easing = LinearOutSlowInEasing)),
                    exit = fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                performExpandToFullscreen()
                            }
                    ) {
                        // Top Row: Title badge, Expand, Close
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .align(Alignment.TopCenter),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = {
                                    triggerControlsVisibility(false)
                                    performExpandToFullscreen()
                                },
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.65f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInFull,
                                        contentDescription = "Expand to full screen",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Clean Title Badge
                            Text(
                                text = streamData.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = Color.White.copy(alpha = 0.90f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )

                            Surface(
                                onClick = {
                                    triggerControlsVisibility(false)
                                    onClose()
                                },
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.65f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close mini player",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // Center Controls: Rewind 10s | Play/Pause | Forward 10s
                        Row(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Rewind 10s
                            Surface(
                                onClick = {
                                    triggerControlsVisibility(true)
                                    GlobalPlayerManager.seekBackward(10000L)
                                },
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.65f),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FastRewind,
                                        contentDescription = "Rewind 10 seconds",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Center: Prominent Play/Pause
                            Surface(
                                onClick = {
                                    triggerControlsVisibility(true)
                                    onTogglePlay()
                                },
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.95f),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color.Black,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            // Forward 10s
                            Surface(
                                onClick = {
                                    triggerControlsVisibility(true)
                                    GlobalPlayerManager.seekForward(10000L)
                                },
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.65f),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FastForward,
                                        contentDescription = "Forward 10 seconds",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. YOUTUBE SIGNATURE RED PROGRESS BAR (Bottom Edge)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color.White.copy(alpha = 0.20f))
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
