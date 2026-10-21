package com.example.ui.ambient

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun rememberAmbientPalette(
    thumbnailUrl: String?,
    isDarkTheme: Boolean = isSystemInDarkTheme()
): AmbientPalette {
    val context = androidx.compose.ui.platform.LocalContext.current
    var palette by remember(thumbnailUrl, isDarkTheme) {
        mutableStateOf(AmbientColorExtractor.getDefaultPalette(isDarkTheme))
    }

    LaunchedEffect(thumbnailUrl, isDarkTheme) {
        if (!thumbnailUrl.isNullOrBlank()) {
            palette = AmbientColorExtractor.extractColors(context, thumbnailUrl, isDarkTheme)
        } else {
            palette = AmbientColorExtractor.getDefaultPalette(isDarkTheme)
        }
    }

    return palette
}

/**
 * YouTube-style Ambient Glow layer positioned around the video player.
 * Radiates dynamic subtle color bleeds matching the video content:
 * 1. Top glow: upward soft gradient into status bar
 * 2. Bottom glow: soft ambient diffusion behind the video title & details
 */
@Composable
fun AmbientPlayerGlow(
    palette: AmbientPalette,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme()
) {
    // Smoothly animate color transitions when video changes
    val animatedPrimary by animateColorAsState(
        targetValue = palette.primaryColor,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "ambientPrimary"
    )
    val animatedSecondary by animateColorAsState(
        targetValue = palette.secondaryColor,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "ambientSecondary"
    )
    val animatedTop by animateColorAsState(
        targetValue = palette.topColor,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "ambientTop"
    )

    // Smoothly fade in/out when toggling Ambient Mode
    val glowAlpha by animateFloatAsState(
        targetValue = if (isEnabled) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "ambientAlpha"
    )

    if (glowAlpha <= 0.01f) return

    val baseAlpha = if (isDarkTheme) 0.55f * glowAlpha else 0.25f * glowAlpha
    val secondaryAlpha = if (isDarkTheme) 0.35f * glowAlpha else 0.18f * glowAlpha
    val topAlpha = if (isDarkTheme) 0.38f * glowAlpha else 0.18f * glowAlpha

    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        val width = size.width
        val height = size.height

        // 1. TOP AMBIENT GLOW (Bleeding upward from video top edge)
        val topGlowHeight = (width * (9f / 16f) * 0.40f).coerceIn(45.dp.toPx(), 95.dp.toPx())
        val topBrush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                animatedTop.copy(alpha = topAlpha * 0.25f),
                animatedTop.copy(alpha = topAlpha * 0.65f),
                animatedTop.copy(alpha = topAlpha)
            ),
            startY = 0f,
            endY = topGlowHeight
        )
        drawRect(
            brush = topBrush,
            topLeft = Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(width, topGlowHeight)
        )

        // 2. BOTTOM AMBIENT GLOW (Diffusing below video bottom edge behind title & channel)
        val videoHeight = width * (9f / 16f)
        val bottomGlowStartY = videoHeight
        val bottomGlowHeight = (height - videoHeight).coerceAtLeast(180.dp.toPx()).coerceAtMost(360.dp.toPx())

        // Linear vertical fade
        val bottomLinearBrush = Brush.verticalGradient(
            colors = listOf(
                animatedPrimary.copy(alpha = baseAlpha),
                animatedPrimary.copy(alpha = baseAlpha * 0.70f),
                animatedSecondary.copy(alpha = secondaryAlpha * 0.50f),
                animatedSecondary.copy(alpha = secondaryAlpha * 0.20f),
                Color.Transparent
            ),
            startY = bottomGlowStartY,
            endY = bottomGlowStartY + bottomGlowHeight
        )
        drawRect(
            brush = bottomLinearBrush,
            topLeft = Offset(0f, bottomGlowStartY),
            size = androidx.compose.ui.geometry.Size(width, bottomGlowHeight)
        )

        // Radial bloom centered at lower center of video for rich ambient depth
        val radialRadius = width * 0.85f
        val radialBrush = Brush.radialGradient(
            colors = listOf(
                animatedPrimary.copy(alpha = baseAlpha * 0.75f),
                animatedSecondary.copy(alpha = secondaryAlpha * 0.35f),
                Color.Transparent
            ),
            center = Offset(width * 0.5f, bottomGlowStartY + 16.dp.toPx()),
            radius = radialRadius
        )
        drawRect(
            brush = radialBrush,
            topLeft = Offset(0f, bottomGlowStartY),
            size = androidx.compose.ui.geometry.Size(width, bottomGlowHeight)
        )
    }
}
