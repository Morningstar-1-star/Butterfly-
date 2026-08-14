package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.sin

/**
 * Recognised calendar occasions for dynamic Butterfly doodles.
 */
enum class OccasionTheme(val greetingTitle: String) {
    INDEPENDENCE_DAY("Happy Independence Day! 🇮🇳"),
    REPUBLIC_DAY("Happy Republic Day! 🇮🇳"),
    DIWALI("Happy Diwali! 🪔✨"),
    HOLI("Happy Holi! 🎨"),
    CHRISTMAS("Merry Christmas! 🎄🎅"),
    NEW_YEAR("Happy New Year! 🎆✨"),
    VALENTINES("Season of Love! 💖"),
    HALLOWEEN("Spooky Nights! 🎃👻"),
    DEFAULT("")
}

object OccasionDetector {
    fun getCurrentOccasion(): OccasionTheme {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        val month = cal.get(Calendar.MONTH) + 1 // 1-12
        val day = cal.get(Calendar.DAY_OF_MONTH)

        return when {
            // Independence Day (India): Aug 14 - Aug 16
            month == 8 && (day in 14..16) -> OccasionTheme.INDEPENDENCE_DAY
            // Republic Day (India): Jan 25 - Jan 27
            month == 1 && (day in 25..27) -> OccasionTheme.REPUBLIC_DAY
            // Christmas: Dec 23 - Dec 26
            month == 12 && (day in 23..26) -> OccasionTheme.CHRISTMAS
            // New Year: Dec 31 & Jan 1 - Jan 2
            (month == 12 && day == 31) || (month == 1 && day in 1..2) -> OccasionTheme.NEW_YEAR
            // Valentine's: Feb 13 - Feb 15
            month == 2 && (day in 13..15) -> OccasionTheme.VALENTINES
            // Halloween: Oct 30 - Nov 1
            (month == 10 && day in 30..31) || (month == 11 && day == 1) -> OccasionTheme.HALLOWEEN
            // Holi season (approx March)
            month == 3 && (day in 24..26) -> OccasionTheme.HOLI
            // Diwali season (approx Oct/Nov)
            (month == 10 && day in 20..24) || (month == 11 && day in 10..14) -> OccasionTheme.DIWALI
            else -> OccasionTheme.DEFAULT
        }
    }
}

/**
 * Lightweight, zero-battery-overhead Canvas-rendered Butterfly Logo
 * that adapts dynamically to special occasions with pure math-based animations.
 */
@Composable
fun ThemedButterflyLogo(
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    occasion: OccasionTheme = remember { OccasionDetector.getCurrentOccasion() }
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ButterflyDoodleTransition")
    
    // Slow, ultra-efficient ambient phase for micro animations (6-8s loop)
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283185f, // 2*PI
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val backgroundBrush = remember(occasion) {
        when (occasion) {
            OccasionTheme.INDEPENDENCE_DAY, OccasionTheme.REPUBLIC_DAY -> {
                // Saffron, White, Green Tri-Color radial/linear blend
                Brush.linearGradient(
                    listOf(
                        Color(0xFFFF9933), // Saffron
                        Color(0xFF0D47A1), // Navy Navy / Ashoka Blue
                        Color(0xFF138808)  // India Green
                    )
                )
            }
            OccasionTheme.DIWALI -> {
                // Royal Gold & Crimson Glow
                Brush.radialGradient(
                    listOf(
                        Color(0xFFFFD700),
                        Color(0xFFFF6F00),
                        Color(0xFF880E4F)
                    )
                )
            }
            OccasionTheme.HOLI -> {
                // Vibrant Gulal Splash: Magenta, Cyan, Gold
                Brush.sweepGradient(
                    listOf(
                        Color(0xFFE91E63),
                        Color(0xFF00E5FF),
                        Color(0xFFFFEB3B),
                        Color(0xFF9C27B0),
                        Color(0xFFE91E63)
                    )
                )
            }
            OccasionTheme.CHRISTMAS -> {
                // Pine Green & Ruby Red
                Brush.linearGradient(
                    listOf(
                        Color(0xFF1B5E20),
                        Color(0xFFC62828),
                        Color(0xFFB71C1C)
                    )
                )
            }
            OccasionTheme.NEW_YEAR -> {
                // Midnight Obsidian, Neon Violet & Sparkling Gold
                Brush.radialGradient(
                    listOf(
                        Color(0xFFFFD54F),
                        Color(0xFF7C4DFF),
                        Color(0xFF1A102F)
                    )
                )
            }
            OccasionTheme.VALENTINES -> {
                // Rose-red & soft pastel pink
                Brush.linearGradient(
                    listOf(
                        Color(0xFFFF4081),
                        Color(0xFFE91E63),
                        Color(0xFFAD1457)
                    )
                )
            }
            OccasionTheme.HALLOWEEN -> {
                // Pumpkin orange & witch purple
                Brush.linearGradient(
                    listOf(
                        Color(0xFFFF6D00),
                        Color(0xFF4A148C),
                        Color(0xFF212121)
                    )
                )
            }
            OccasionTheme.DEFAULT -> {
                // Classic Butterfly Purple-Pink Gradient
                Brush.linearGradient(
                    listOf(Color(0xFF8E24AA), Color(0xFFE91E63))
                )
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundBrush),
        contentAlignment = Alignment.Center
    ) {
        val innerSize = size * 0.62f
        Canvas(modifier = Modifier.size(innerSize)) {
            val w = this.size.width
            val h = this.size.height

            when (occasion) {
                OccasionTheme.INDEPENDENCE_DAY, OccasionTheme.REPUBLIC_DAY -> {
                    // Top wings: Saffron, Bottom wings: India Green, Body/Center: Ashoka Chakra Navy
                    val saffron = Color(0xFFFF9933)
                    val green = Color(0xFF138808)
                    val navy = Color(0xFF000080)
                    val white = Color.White

                    // Left & Right Top Wings (Saffron)
                    drawCircle(color = saffron, radius = w * 0.28f, center = Offset(w * 0.30f, h * 0.35f))
                    drawCircle(color = saffron, radius = w * 0.28f, center = Offset(w * 0.70f, h * 0.35f))

                    // Left & Right Bottom Wings (Green)
                    drawCircle(color = green, radius = w * 0.20f, center = Offset(w * 0.36f, h * 0.68f))
                    drawCircle(color = green, radius = w * 0.20f, center = Offset(w * 0.64f, h * 0.68f))

                    // Body (White with navy border)
                    drawLine(
                        color = white,
                        start = Offset(w * 0.5f, h * 0.20f),
                        end = Offset(w * 0.5f, h * 0.82f),
                        strokeWidth = w * 0.10f
                    )
                    drawLine(
                        color = navy,
                        start = Offset(w * 0.5f, h * 0.20f),
                        end = Offset(w * 0.5f, h * 0.82f),
                        strokeWidth = w * 0.05f
                    )

                    // Antennae
                    drawLine(color = navy, start = Offset(w * 0.5f, h * 0.22f), end = Offset(w * 0.30f, h * 0.10f), strokeWidth = w * 0.05f)
                    drawLine(color = navy, start = Offset(w * 0.5f, h * 0.22f), end = Offset(w * 0.70f, h * 0.10f), strokeWidth = w * 0.05f)

                    // Micro Ashoka Center Glow Pulse
                    val sparkleRadius = (w * 0.06f) + (sin(phase) * w * 0.02f)
                    drawCircle(color = navy, radius = sparkleRadius, center = Offset(w * 0.5f, h * 0.48f))
                }

                OccasionTheme.DIWALI -> {
                    // Golden glowing wings with diya-flame flicker in center
                    val gold = Color(0xFFFFD700)
                    val amber = Color(0xFFFF8F00)
                    val flameGlow = Color(0xFFFFF176)

                    // Wings
                    drawCircle(color = gold, radius = w * 0.28f, center = Offset(w * 0.30f, h * 0.35f))
                    drawCircle(color = gold, radius = w * 0.28f, center = Offset(w * 0.70f, h * 0.35f))
                    drawCircle(color = amber, radius = w * 0.20f, center = Offset(w * 0.36f, h * 0.68f))
                    drawCircle(color = amber, radius = w * 0.20f, center = Offset(w * 0.64f, h * 0.68f))

                    // Body
                    drawLine(color = Color(0xFF5D4037), start = Offset(w * 0.5f, h * 0.20f), end = Offset(w * 0.5f, h * 0.82f), strokeWidth = w * 0.08f)
                    drawLine(color = Color(0xFF5D4037), start = Offset(w * 0.5f, h * 0.22f), end = Offset(w * 0.30f, h * 0.10f), strokeWidth = w * 0.05f)
                    drawLine(color = Color(0xFF5D4037), start = Offset(w * 0.5f, h * 0.22f), end = Offset(w * 0.70f, h * 0.10f), strokeWidth = w * 0.05f)

                    // Diya flame flicker
                    val flickerY = h * 0.46f + sin(phase * 3f) * (h * 0.03f)
                    drawCircle(color = flameGlow, radius = w * 0.08f + cos(phase * 2f) * w * 0.02f, center = Offset(w * 0.5f, flickerY))
                }

                OccasionTheme.CHRISTMAS -> {
                    val snowWhite = Color(0xFFFFFFFF)
                    val hollyGreen = Color(0xFF81C784)
                    val berryRed = Color(0xFFEF5350)

                    // Wings (Icy Snow & Holly)
                    drawCircle(color = snowWhite, radius = w * 0.28f, center = Offset(w * 0.30f, h * 0.35f))
                    drawCircle(color = snowWhite, radius = w * 0.28f, center = Offset(w * 0.70f, h * 0.35f))
                    drawCircle(color = hollyGreen, radius = w * 0.20f, center = Offset(w * 0.36f, h * 0.68f))
                    drawCircle(color = hollyGreen, radius = w * 0.20f, center = Offset(w * 0.64f, h * 0.68f))

                    // Body
                    drawLine(color = Color(0xFF1B5E20), start = Offset(w * 0.5f, h * 0.20f), end = Offset(w * 0.5f, h * 0.82f), strokeWidth = w * 0.08f)

                    // Santa Cap Hat on head
                    val capPath = Path().apply {
                        moveTo(w * 0.35f, h * 0.22f)
                        lineTo(w * 0.65f, h * 0.22f)
                        lineTo(w * 0.50f, h * 0.05f)
                        close()
                    }
                    drawPath(capPath, color = berryRed)
                    drawCircle(color = snowWhite, radius = w * 0.05f, center = Offset(w * 0.50f, h * 0.05f))
                }

                OccasionTheme.NEW_YEAR -> {
                    val gold = Color(0xFFFFD54F)
                    val starWhite = Color(0xFFFFFFFF)
                    val neonCyan = Color(0xFF00E5FF)

                    // Wings with fireworks sparkle hue
                    drawCircle(color = starWhite, radius = w * 0.28f, center = Offset(w * 0.30f, h * 0.35f))
                    drawCircle(color = starWhite, radius = w * 0.28f, center = Offset(w * 0.70f, h * 0.35f))
                    drawCircle(color = gold, radius = w * 0.20f, center = Offset(w * 0.36f, h * 0.68f))
                    drawCircle(color = gold, radius = w * 0.20f, center = Offset(w * 0.64f, h * 0.68f))

                    // Body
                    drawLine(color = Color(0xFF311B92), start = Offset(w * 0.5f, h * 0.20f), end = Offset(w * 0.5f, h * 0.82f), strokeWidth = w * 0.08f)
                    drawLine(color = neonCyan, start = Offset(w * 0.5f, h * 0.22f), end = Offset(w * 0.30f, h * 0.10f), strokeWidth = w * 0.05f)
                    drawLine(color = neonCyan, start = Offset(w * 0.5f, h * 0.22f), end = Offset(w * 0.70f, h * 0.10f), strokeWidth = w * 0.05f)

                    // Orbiting firecracker spark
                    val sparkX = w * 0.5f + cos(phase) * (w * 0.35f)
                    val sparkY = h * 0.45f + sin(phase) * (h * 0.30f)
                    drawCircle(color = gold, radius = w * 0.04f, center = Offset(sparkX, sparkY))
                }

                OccasionTheme.HOLI -> {
                    // Multi-hue powder splash
                    drawCircle(color = Color(0xFFFF4081), radius = w * 0.28f, center = Offset(w * 0.30f, h * 0.35f))
                    drawCircle(color = Color(0xFF00E5FF), radius = w * 0.28f, center = Offset(w * 0.70f, h * 0.35f))
                    drawCircle(color = Color(0xFFFFEB3B), radius = w * 0.20f, center = Offset(w * 0.36f, h * 0.68f))
                    drawCircle(color = Color(0xFF76FF03), radius = w * 0.20f, center = Offset(w * 0.64f, h * 0.68f))

                    drawLine(color = Color(0xFF311B92), start = Offset(w * 0.5f, h * 0.20f), end = Offset(w * 0.5f, h * 0.82f), strokeWidth = w * 0.08f)
                    drawLine(color = Color(0xFFE040FB), start = Offset(w * 0.5f, h * 0.22f), end = Offset(w * 0.30f, h * 0.10f), strokeWidth = w * 0.05f)
                    drawLine(color = Color(0xFFE040FB), start = Offset(w * 0.5f, h * 0.22f), end = Offset(w * 0.70f, h * 0.10f), strokeWidth = w * 0.05f)
                }

                else -> {
                    // Classic Standard Pure White & Purple Butterfly
                    val wingColor = Color.White
                    drawCircle(color = wingColor, radius = w * 0.28f, center = Offset(w * 0.30f, h * 0.35f))
                    drawCircle(color = wingColor, radius = w * 0.28f, center = Offset(w * 0.70f, h * 0.35f))
                    drawCircle(color = wingColor.copy(alpha = 0.85f), radius = w * 0.20f, center = Offset(w * 0.36f, h * 0.68f))
                    drawCircle(color = wingColor.copy(alpha = 0.85f), radius = w * 0.20f, center = Offset(w * 0.64f, h * 0.68f))

                    drawLine(color = Color(0xFF4A148C), start = Offset(w * 0.5f, h * 0.20f), end = Offset(w * 0.5f, h * 0.82f), strokeWidth = w * 0.08f)
                    drawLine(color = Color(0xFF4A148C), start = Offset(w * 0.5f, h * 0.22f), end = Offset(w * 0.30f, h * 0.10f), strokeWidth = w * 0.05f)
                    drawLine(color = Color(0xFF4A148C), start = Offset(w * 0.5f, h * 0.22f), end = Offset(w * 0.70f, h * 0.10f), strokeWidth = w * 0.05f)
                }
            }
        }
    }
}
