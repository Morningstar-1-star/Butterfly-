package com.example.ui.animation

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppAccentColor
import com.example.ui.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-performance, cinematic app opening animation featuring the 3D-flapping butterfly,
 * inspired by the iconic butterfly launch animation and elevated with organic physics,
 * luminous lighting, theme adaptation (Light / Dark Mode), and a seamless zoom-through reveal.
 */
@Composable
fun ButterflyOpeningAnimation(
    themeMode: ThemeMode,
    accentColor: AppAccentColor,
    modifier: Modifier = Modifier,
    onAnimationFinished: () -> Unit
) {
    var isSkipped by remember { mutableStateOf(false) }

    // Master animation clock (0ms to 1400ms)
    val animClock = remember { Animatable(0f) }

    // Launch animation sequence
    LaunchedEffect(Unit) {
        animClock.animateTo(
            targetValue = 1400f,
            animationSpec = tween(
                durationMillis = 1400,
                easing = LinearEasing
            )
        )
        onAnimationFinished()
    }

    val progress = animClock.value

    // Calculate animation phase values based on time progress:
    // 1. Initial Entrance & Spring Bounce (0 - 300ms)
    val entranceScale = when {
        progress < 250f -> {
            val t = (progress / 250f).coerceIn(0f, 1f)
            // Ease out elastic bounce: 0.6 -> 1.06 -> 1.0
            0.6f + (0.46f * sin(t * Math.PI.toFloat() * 0.75f))
        }
        progress < 350f -> {
            val t = ((progress - 250f) / 100f).coerceIn(0f, 1f)
            1.06f - (0.06f * t)
        }
        else -> 1f
    }

    // 2. 3D Wing Flap Angle (in degrees)
    // Flap 1: 300ms -> 650ms (Peak ~56deg at 450ms)
    // Flap 2: 680ms -> 1000ms (Stronger lift, peak ~64deg at 820ms)
    val flapAngle = when {
        progress in 300f..650f -> {
            val t = (progress - 300f) / 350f
            if (t < 0.45f) {
                // Forward flap
                val subT = t / 0.45f
                FastOutSlowInEasing.transform(subT) * 56f
            } else {
                // Return & slight backward overshoot
                val subT = (t - 0.45f) / 0.55f
                val eased = FastOutSlowInEasing.transform(subT)
                56f * (1f - eased) - (12f * sin(subT * Math.PI.toFloat()))
            }
        }
        progress in 680f..1000f -> {
            val t = (progress - 680f) / 320f
            if (t < 0.45f) {
                val subT = t / 0.45f
                FastOutSlowInEasing.transform(subT) * 64f
            } else {
                val subT = (t - 0.45f) / 0.55f
                val eased = FastOutSlowInEasing.transform(subT)
                64f * (1f - eased) - (16f * sin(subT * Math.PI.toFloat()))
            }
        }
        else -> 0f
    }

    // 3. Vertical Flight Lift (Translation Y)
    val flightOffsetY = when {
        progress < 300f -> 0f
        progress < 650f -> {
            val t = (progress - 300f) / 350f
            -18f * sin(t * Math.PI.toFloat() * 0.85f)
        }
        progress < 1000f -> {
            val t = (progress - 650f) / 350f
            -18f - (22f * sin(t * Math.PI.toFloat() * 0.9f))
        }
        else -> {
            val t = ((progress - 1000f) / 400f).coerceIn(0f, 1f)
            -40f * (1f - t)
        }
    }

    // 4. Slight living tilt (Z-rotation) during flight
    val bodyTiltZ = when {
        progress in 320f..650f -> {
            val t = (progress - 320f) / 330f
            -3f * sin(t * Math.PI.toFloat() * 2f)
        }
        progress in 700f..1000f -> {
            val t = (progress - 700f) / 300f
            2.5f * sin(t * Math.PI.toFloat() * 2f)
        }
        else -> 0f
    }

    // 5. Cinematic Zoom-Through (1000ms -> 1400ms)
    val zoomScale = when {
        progress < 1000f -> 1f
        else -> {
            val t = ((progress - 1000f) / 400f).coerceIn(0f, 1f)
            // Exponential zoom curve: 1.0 -> 24.0f
            val curve = CubicBezierEasing(0.42f, 0.0f, 0.2f, 1.0f).transform(t)
            1f + (23f * curve)
        }
    }

    // 6. Overall Splash Overlay Opacity (fades out during zoom-through)
    val splashAlpha = when {
        progress < 1120f -> 1f
        else -> {
            val t = ((progress - 1120f) / 280f).coerceIn(0f, 1f)
            1f - CubicBezierEasing(0.4f, 0f, 0.2f, 1f).transform(t)
        }
    }

    // Specular wing glint shimmer phase (950ms - 1100ms)
    val shimmerPhase = when {
        progress in 920f..1150f -> (progress - 920f) / 230f
        else -> -1f
    }

    // Theme Color Mapping
    val isDark = themeMode == ThemeMode.AMOLED_DARK
    val backgroundBrush = remember(isDark, accentColor) {
        if (isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0F172A), // Deep midnight slate
                    Color(0xFF080D1A),
                    Color(0xFF020408)  // AMOLED pure abyss
                )
            )
        } else {
            // Radiant sky gradient inspired by video clip
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF389DF6), // Vibrant cerulean sky
                    Color(0xFF1E88E5), // Vivid royal azure
                    Color(0xFF0D47A1)  // Rich deep cobalt
                )
            )
        }
    }

    val glowColor = remember(isDark, accentColor) {
        if (isDark) {
            if (accentColor == AppAccentColor.MONOCHROME) Color(0xFF00E5FF) else accentColor.color
        } else {
            Color(0xFF80D8FF)
        }
    }

    val butterflyColor = Color.White

    val density = LocalDensity.current

    if (splashAlpha > 0.01f && !isSkipped) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .alpha(splashAlpha)
                .background(backgroundBrush)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    // Tap to skip immediately
                    isSkipped = true
                    onAnimationFinished()
                },
            contentAlignment = Alignment.Center
        ) {
            // Subtle ambient radial glow behind butterfly
            val glowScale = when {
                progress < 300f -> progress / 300f
                progress < 1000f -> 1f + 0.15f * sin((progress - 300f) / 700f * Math.PI.toFloat() * 2f)
                else -> 1f + ((progress - 1000f) / 400f) * 2f
            }

            Box(
                modifier = Modifier
                    .size(280.dp)
                    .scale(glowScale)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                glowColor.copy(alpha = if (isDark) 0.28f else 0.35f),
                                glowColor.copy(alpha = if (isDark) 0.08f else 0.12f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )

            // Dynamic Flutter Dust / Stardust Particles
            if (progress in 650f..1150f) {
                ButterflyParticles(
                    progress = (progress - 650f) / 500f,
                    accentColor = glowColor,
                    isDark = isDark
                )
            }

            // Main Butterfly Container with 3D Flap & Transform
            val totalScale = entranceScale * zoomScale
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .graphicsLayer {
                        scaleX = totalScale
                        scaleY = totalScale
                        translationY = with(density) { flightOffsetY.dp.toPx() }
                        rotationZ = bodyTiltZ
                        cameraDistance = 32f * density.density
                    },
                contentAlignment = Alignment.Center
            ) {
                // Soft drop shadow / ambient elevation under butterfly (fades during zoom)
                if (zoomScale < 2f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(1.02f)
                            .blur(if (isDark) 12.dp else 8.dp)
                            .alpha(if (isDark) 0.45f else 0.25f)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawFullButterfly(
                                color = if (isDark) glowColor else Color(0xFF003C8F)
                            )
                        }
                    }
                }

                // 3D Flapping Wings Layer
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left Wing (Pivot at Right edge = x: 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .graphicsLayer {
                                rotationY = flapAngle
                                transformOrigin = TransformOrigin(1f, 0.55f)
                                cameraDistance = 24f * density.density
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawLeftWing(
                                color = butterflyColor,
                                shimmerPhase = shimmerPhase
                            )
                        }
                    }

                    // Right Wing (Pivot at Left edge = x: 0f)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .graphicsLayer {
                                rotationY = -flapAngle
                                transformOrigin = TransformOrigin(0f, 0.55f)
                                cameraDistance = 24f * density.density
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawRightWing(
                                color = butterflyColor,
                                shimmerPhase = shimmerPhase
                            )
                        }
                    }
                }

                // Central Spine Bridge (ensures zero hairline gaps at any rotation angle)
                Canvas(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .align(Alignment.Center)
                ) {
                    val bodyHeight = size.height * 0.32f
                    val topY = size.height * 0.46f
                    drawOval(
                        color = butterflyColor,
                        topLeft = Offset(0f, topY),
                        size = Size(size.width, bodyHeight)
                    )
                }
            }

            // Subtle elegant app branding indicator at bottom (fades out as butterfly launches)
            val brandAlpha = when {
                progress < 250f -> (progress / 250f).coerceIn(0f, 1f)
                progress < 950f -> 1f
                else -> (1f - (progress - 950f) / 150f).coerceIn(0f, 1f)
            }

            if (brandAlpha > 0.05f) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 54.dp)
                        .alpha(brandAlpha),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "BUTTERFLY",
                        color = if (isDark) Color.White.copy(alpha = 0.9f) else Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 5.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Next-Gen Video Player",
                        color = if (isDark) glowColor.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

/**
 * Procedural Flutter Dust Particles that scatter gracefully when the butterfly takes flight.
 */
@Composable
private fun ButterflyParticles(
    progress: Float,
    accentColor: Color,
    isDark: Boolean
) {
    Canvas(modifier = Modifier.size(240.dp)) {
        val count = 10
        val center = Offset(size.width / 2f, size.height / 2f - 20f)
        for (i in 0 until count) {
            val angle = (i * 36f + (progress * 70f)) * (Math.PI.toFloat() / 180f)
            val dist = 35f + (progress * 110f) * (0.8f + (i % 3) * 0.25f)
            val px = center.x + cos(angle) * dist
            val py = center.y + sin(angle) * (dist * 0.65f) - (progress * 30f)
            val pAlpha = (1f - progress).coerceIn(0f, 1f) * 0.85f
            val radius = (3.5f - (progress * 1.5f)).coerceAtLeast(1f)

            drawCircle(
                color = if (i % 2 == 0) Color.White.copy(alpha = pAlpha) else accentColor.copy(alpha = pAlpha),
                radius = radius,
                center = Offset(px, py)
            )
        }
    }
}

/**
 * Draws the mathematically precise Left Wing of the butterfly (x: 0..8, y: 0..16)
 */
private fun DrawScope.drawLeftWing(color: Color, shimmerPhase: Float) {
    val scaleX = size.width / 8f
    val scaleY = size.height / 16f

    val path = Path().apply {
        moveTo(8f * scaleX, 7.616f * scaleY)
        cubicTo(
            7.276f * scaleX, 6.118f * scaleY,
            5.303f * scaleX, 3.325f * scaleY,
            3.468f * scaleX, 1.948f * scaleY
        )
        cubicTo(
            2.145f * scaleX, 0.955f * scaleY,
            0f, 0.186f * scaleY,
            0f, 2.632f * scaleY
        )
        cubicTo(
            0f, 3.12f * scaleY,
            0.28f * scaleX, 6.737f * scaleY,
            0.444f * scaleX, 7.324f * scaleY
        )
        cubicTo(
            1.016f * scaleX, 9.365f * scaleY,
            3.097f * scaleX, 9.885f * scaleY,
            4.948f * scaleX, 9.57f * scaleY
        )
        cubicTo(
            1.713f * scaleX, 10.12f * scaleY,
            0.889f * scaleX, 11.945f * scaleY,
            2.667f * scaleX, 13.77f * scaleY
        )
        cubicTo(
            6.043f * scaleX, 17.234f * scaleY,
            7.519f * scaleX, 12.9f * scaleY,
            7.898f * scaleX, 11.79f * scaleY
        )
        cubicTo(
            7.967f * scaleX, 11.586f * scaleY,
            8f * scaleX, 11.491f * scaleY,
            8f * scaleX, 11.572f * scaleY
        )
        close()
    }

    drawPath(path = path, color = color)

    // Optional Specular shimmer sweep
    if (shimmerPhase in 0f..1f) {
        val sweepX = size.width * (shimmerPhase * 2f - 0.5f)
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.6f),
                    Color.Transparent
                ),
                start = Offset(sweepX - 25f, 0f),
                end = Offset(sweepX + 25f, size.height)
            )
        )
    }
}

/**
 * Draws the mathematically precise Right Wing of the butterfly (x: 8..16, y: 0..16)
 */
private fun DrawScope.drawRightWing(color: Color, shimmerPhase: Float) {
    val scaleX = size.width / 8f
    val scaleY = size.height / 16f

    // Right wing coordinates in original SVG are 8..16; offset by -8 to fit inside 0..size.width
    val path = Path().apply {
        moveTo((8f - 8f) * scaleX, 7.616f * scaleY)
        cubicTo(
            (8.725f - 8f) * scaleX, 6.118f * scaleY,
            (10.698f - 8f) * scaleX, 3.325f * scaleY,
            (12.532f - 8f) * scaleX, 1.948f * scaleY
        )
        cubicTo(
            (13.855f - 8f) * scaleX, 0.955f * scaleY,
            (16f - 8f) * scaleX, 0.186f * scaleY,
            (16f - 8f) * scaleX, 2.632f * scaleY
        )
        cubicTo(
            (16f - 8f) * scaleX, 3.121f * scaleY,
            (15.72f - 8f) * scaleX, 6.737f * scaleY,
            (15.556f - 8f) * scaleX, 7.324f * scaleY
        )
        cubicTo(
            (14.984f - 8f) * scaleX, 9.364f * scaleY,
            (12.903f - 8f) * scaleX, 9.885f * scaleY,
            (11.052f - 8f) * scaleX, 9.57f * scaleY
        )
        cubicTo(
            (14.288f - 8f) * scaleX, 10.121f * scaleY,
            (15.112f - 8f) * scaleX, 11.945f * scaleY,
            (13.333f - 8f) * scaleX, 13.77f * scaleY
        )
        cubicTo(
            (9.957f - 8f) * scaleX, 17.234f * scaleY,
            (8.481f - 8f) * scaleX, 12.9f * scaleY,
            (8.103f - 8f) * scaleX, 11.79f * scaleY
        )
        cubicTo(
            (8.033f - 8f) * scaleX, 11.586f * scaleY,
            (8f - 8f) * scaleX, 11.49f * scaleY,
            (8f - 8f) * scaleX, 11.572f * scaleY
        )
        close()
    }

    drawPath(path = path, color = color)

    // Optional Specular shimmer sweep
    if (shimmerPhase in 0f..1f) {
        val sweepX = size.width * (shimmerPhase * 2f - 0.5f)
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.6f),
                    Color.Transparent
                ),
                start = Offset(sweepX - 25f, 0f),
                end = Offset(sweepX + 25f, size.height)
            )
        )
    }
}

/**
 * Draws the unified full butterfly silhouette (x: 0..16, y: 0..16)
 */
private fun DrawScope.drawFullButterfly(color: Color) {
    val scaleX = size.width / 16f
    val scaleY = size.height / 16f

    val path = Path().apply {
        moveTo(3.468f * scaleX, 1.948f * scaleY)
        cubicTo(
            5.303f * scaleX, 3.325f * scaleY,
            7.276f * scaleX, 6.118f * scaleY,
            8f * scaleX, 7.616f * scaleY
        )
        cubicTo(
            8.725f * scaleX, 6.118f * scaleY,
            10.698f * scaleX, 3.325f * scaleY,
            12.532f * scaleX, 1.948f * scaleY
        )
        cubicTo(
            13.855f * scaleX, 0.955f * scaleY,
            16f * scaleX, 0.186f * scaleY,
            16f * scaleX, 2.632f * scaleY
        )
        cubicTo(
            16f * scaleX, 3.121f * scaleY,
            15.72f * scaleX, 6.737f * scaleY,
            15.556f * scaleX, 7.324f * scaleY
        )
        cubicTo(
            14.984f * scaleX, 9.364f * scaleY,
            12.903f * scaleX, 9.885f * scaleY,
            11.052f * scaleX, 9.57f * scaleY
        )
        cubicTo(
            14.288f * scaleX, 10.121f * scaleY,
            15.112f * scaleX, 11.945f * scaleY,
            13.333f * scaleX, 13.77f * scaleY
        )
        cubicTo(
            9.957f * scaleX, 17.234f * scaleY,
            8.481f * scaleX, 12.9f * scaleY,
            8.103f * scaleX, 11.79f * scaleY
        )
        cubicTo(
            8.033f * scaleX, 11.586f * scaleY,
            8f * scaleX, 11.49f * scaleY,
            8f * scaleX, 11.572f * scaleY
        )
        cubicTo(
            8f * scaleX, 11.491f * scaleY,
            7.967f * scaleX, 11.586f * scaleY,
            7.898f * scaleX, 11.79f * scaleY
        )
        cubicTo(
            7.519f * scaleX, 12.9f * scaleY,
            6.043f * scaleX, 17.234f * scaleY,
            2.667f * scaleX, 13.77f * scaleY
        )
        cubicTo(
            0.889f * scaleX, 11.945f * scaleY,
            1.713f * scaleX, 10.12f * scaleY,
            4.948f * scaleX, 9.57f * scaleY
        )
        cubicTo(
            3.097f * scaleX, 9.885f * scaleY,
            1.016f * scaleX, 9.365f * scaleY,
            0.444f * scaleX, 7.324f * scaleY
        )
        cubicTo(
            0.28f * scaleX, 6.737f * scaleY,
            0f, 3.12f * scaleY,
            0f, 2.632f * scaleY
        )
        cubicTo(
            0f, 0.186f * scaleY,
            2.145f * scaleX, 0.955f * scaleY,
            3.468f * scaleX, 1.948f * scaleY
        )
        close()
    }

    drawPath(path = path, color = color)
}
