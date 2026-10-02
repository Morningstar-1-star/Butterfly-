@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Premium YouTube Pull-To-Refresh Indicator.
 * - Uses Material 3 PullToRefresh physics with silky smooth scaling and translation.
 * - Displays a perfectly proportioned, beautiful circular loading indicator.
 * - Prevents distortion, clipping, or broken circle artifacts on all device sizes and themes.
 */
@Composable
fun YouTubePullToRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.primary
) {
    val distanceFraction = state.distanceFraction
    val isVisible = isRefreshing || distanceFraction > 0.02f

    val animatedScale by animateFloatAsState(
        targetValue = when {
            isRefreshing -> 1f
            isVisible -> (distanceFraction * 1.1f).coerceIn(0f, 1f)
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
            isVisible -> (distanceFraction * 1.4f).coerceIn(0f, 1f)
            else -> 0f
        },
        animationSpec = tween(durationMillis = 150, easing = LinearEasing),
        label = "yt_refresh_alpha"
    )

    val restingOffsetDp = 48.dp
    val currentTranslationY by animateDpAsState(
        targetValue = when {
            isRefreshing -> restingOffsetDp
            isVisible -> {
                val clamped = distanceFraction.coerceIn(0f, 1.3f)
                (restingOffsetDp * clamped).coerceAtMost(72.dp)
            }
            else -> 0.dp
        },
        animationSpec = if (isRefreshing) spring(stiffness = Spring.StiffnessLow) else spring(stiffness = Spring.StiffnessHigh),
        label = "yt_refresh_translation"
    )

    if (animatedScale > 0.01f && animatedAlpha > 0.01f) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = topPadding + currentTranslationY),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                shape = CircleShape,
                color = containerColor,
                shadowElevation = 6.dp,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .size(40.dp)
                    .scale(animatedScale)
                    .alpha(animatedAlpha)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            color = contentColor,
                            strokeWidth = 2.8.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        // Smooth progress arc while dragging
                        CircularProgressIndicator(
                            progress = { distanceFraction.coerceIn(0f, 1f) },
                            color = contentColor,
                            trackColor = contentColor.copy(alpha = 0.15f),
                            strokeWidth = 2.8.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
