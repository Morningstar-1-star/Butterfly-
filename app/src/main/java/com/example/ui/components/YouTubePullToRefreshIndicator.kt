@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-fidelity YouTube Pull-To-Refresh Indicator.
 * - Dynamic drag response: arrow rotates and arc sweep angle expands as user pulls down.
 * - Refreshing state: Silky-smooth indeterminate breathing & rotating spinner.
 * - Exit animation: Elegant spring-based scale & fade dismissal.
 */
@Composable
fun YouTubePullToRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    // Distance fraction from 0.0 (idle) to 1.0 (threshold) and > 1.0 (overscroll)
    val distanceFraction = state.distanceFraction

    // Determine visibility and animation states
    val isVisible = isRefreshing || distanceFraction > 0.02f

    // Animated scale & alpha for entering, refreshing and dismissing
    val animatedScale by animateFloatAsState(
        targetValue = when {
            isRefreshing -> 1f
            isVisible -> (distanceFraction * 1.15f).coerceIn(0f, 1f)
            else -> 0f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "yt_refresh_scale"
    )

    val animatedAlpha by animateFloatAsState(
        targetValue = when {
            isRefreshing -> 1f
            isVisible -> (distanceFraction * 1.5f).coerceIn(0f, 1f)
            else -> 0f
        },
        animationSpec = tween(durationMillis = 150, easing = LinearEasing),
        label = "yt_refresh_alpha"
    )

    // Vertical translation physics (slingshot / rubberband)
    val restingOffsetDp = 48.dp
    val maxDragOffsetDp = 76.dp
    val currentTranslationY by animateDpAsState(
        targetValue = when {
            isRefreshing -> restingOffsetDp
            isVisible -> {
                val clamped = distanceFraction.coerceIn(0f, 1.4f)
                (restingOffsetDp * clamped).coerceAtMost(maxDragOffsetDp)
            }
            else -> 0.dp
        },
        animationSpec = if (isRefreshing) spring(stiffness = Spring.StiffnessLow) else spring(stiffness = Spring.StiffnessHigh),
        label = "yt_refresh_translation"
    )

    // Indeterminate infinite animations for refreshing state
    val infiniteTransition = rememberInfiniteTransition(label = "yt_spinner_loop")
    val spinningRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "yt_spin_rotation"
    )

    // Breathing arc sweep for indeterminate loading
    val breathingSweep by infiniteTransition.animateFloat(
        initialValue = 35f,
        targetValue = 270f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "yt_breathing_sweep"
    )

    if (animatedScale > 0.01f && animatedAlpha > 0.01f) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = topPadding + currentTranslationY),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .scale(animatedScale)
                    .alpha(animatedAlpha)
                    .shadow(elevation = 6.dp, shape = CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .size(24.dp)
                        .padding(2.dp)
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val center = Offset(canvasWidth / 2f, canvasHeight / 2f)
                    val arcRadius = (canvasWidth / 2f) - 2.dp.toPx()
                    val strokeWidthPx = 2.4.dp.toPx()

                    if (isRefreshing) {
                        // REFRESHING: Indeterminate animated rotating and breathing arc
                        rotate(degrees = spinningRotation, pivot = center) {
                            drawArc(
                                color = contentColor,
                                startAngle = -90f,
                                sweepAngle = breathingSweep,
                                useCenter = false,
                                topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                                size = Size(arcRadius * 2, arcRadius * 2),
                                style = Stroke(
                                    width = strokeWidthPx,
                                    cap = StrokeCap.Round
                                )
                            )
                        }
                    } else {
                        // DRAGGING / SWIPING: Direct user pull feedback with arrow
                        val progress = distanceFraction.coerceIn(0f, 1.2f)
                        val dragRotation = (progress * 360f * 1.15f) - 90f
                        val dragSweep = (progress * 280f).coerceIn(25f, 290f)

                        // Draw main circular arc
                        drawArc(
                            color = contentColor,
                            startAngle = dragRotation,
                            sweepAngle = dragSweep,
                            useCenter = false,
                            topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                            size = Size(arcRadius * 2, arcRadius * 2),
                            style = Stroke(
                                width = strokeWidthPx,
                                cap = StrokeCap.Round
                            )
                        )

                        // Draw Arrow Head at the end of the arc
                        val endAngleDeg = dragRotation + dragSweep
                        val endAngleRad = Math.toRadians(endAngleDeg.toDouble())
                        val arrowTipX = center.x + (arcRadius * cos(endAngleRad)).toFloat()
                        val arrowTipY = center.y + (arcRadius * sin(endAngleRad)).toFloat()

                        drawArrowHead(
                            tip = Offset(arrowTipX, arrowTipY),
                            angleDeg = endAngleDeg + 90f,
                            color = contentColor,
                            arrowSizePx = 6.dp.toPx()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Draws a sharp, balanced triangle arrow head for the drag indicator.
 */
private fun DrawScope.drawArrowHead(
    tip: Offset,
    angleDeg: Float,
    color: Color,
    arrowSizePx: Float
) {
    rotate(degrees = angleDeg, pivot = tip) {
        val path = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(tip.x - arrowSizePx * 0.75f, tip.y - arrowSizePx)
            lineTo(tip.x + arrowSizePx * 0.75f, tip.y - arrowSizePx)
            close()
        }
        drawPath(path = path, color = color)
    }
}
