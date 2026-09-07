package com.example.ui.animation

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppAccentColor
import com.example.ui.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

/**
 * MTV Moon Landing-inspired Opening Animation featuring the Astronaut on the lunar surface
 * planting a glowing neon flagpole with a waving psychedelic Butterfly Flag!
 * Synchronized with the authentic "I want my MTV" vocal, rock guitar & cosmic sting.
 */
@Composable
fun MtvMoonButterflyOpeningAnimation(
    themeMode: ThemeMode,
    accentColor: AppAccentColor,
    modifier: Modifier = Modifier,
    onAnimationFinished: () -> Unit
) {
    val context = LocalContext.current
    var isSkipped by remember { mutableStateOf(false) }

    // Master animation clock (0ms to 2400ms)
    val animClock = remember { Animatable(0f) }

    // Launch animation sequence & sound synthesis
    LaunchedEffect(Unit) {
        // Trigger authentic "I Want My MTV" vocal & rock sting
        MtvSoundSynthesizer.playMtvSting(context)

        val animJob = launch {
            try {
                animClock.animateTo(
                    targetValue = 2400f,
                    animationSpec = tween(
                        durationMillis = 2380,
                        easing = LinearEasing
                    )
                )
            } catch (_: Exception) {}
        }
        val timeoutJob = launch {
            delay(2500L)
            if (!isSkipped) {
                isSkipped = true
                MtvSoundSynthesizer.stop()
                onAnimationFinished()
            }
        }
        animJob.join()
        timeoutJob.cancel()
        MtvSoundSynthesizer.stop()
        onAnimationFinished()
    }

    DisposableEffect(Unit) {
        onDispose {
            MtvSoundSynthesizer.stop()
        }
    }

    val progress = if (isSkipped) 2400f else animClock.value

    // If finished or skipped, immediately unmount to never block touches
    if (progress >= 2380f || isSkipped) {
        SideEffect {
            MtvSoundSynthesizer.stop()
            onAnimationFinished()
        }
        return
    }

    val density = LocalDensity.current

    // Pre-generate stars for the cosmic background (deterministic seed)
    val stars = remember {
        val rand = Random(42)
        List(70) {
            StarSpec(
                xFrac = rand.nextFloat(),
                yFrac = rand.nextFloat() * 0.75f,
                radius = rand.nextFloat() * 1.8f + 0.6f,
                twinkleSpeed = rand.nextFloat() * 2f + 1f,
                baseAlpha = rand.nextFloat() * 0.5f + 0.35f,
                isCyan = rand.nextBoolean()
            )
        }
    }

    // Phase values:
    // 0ms - 420ms: Camera zoom toward lunar surface, astronaut plants glowing pole
    // 420ms - 1400ms: Full moon scene with waving psychedelic butterfly flag & "I want my MTV" vocal
    // 1400ms - 2050ms: Rock guitar power chord & Butterfly logo zooms forward with 3D MTV title
    // 2050ms - 2380ms: Light flash / portal fadeout
    val isTitlePhase = progress >= 1400f
    val titleProgress = if (isTitlePhase) ((progress - 1400f) / 650f).coerceIn(0f, 1f) else 0f
    val exitAlpha = if (progress >= 2050f) {
        1f - ((progress - 2050f) / 330f).coerceIn(0f, 1f)
    } else 1f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF030308))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isSkipped = true
                MtvSoundSynthesizer.stop()
                onAnimationFinished()
            }
            .alpha(exitAlpha)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            // 1. Cosmic Deep Space Background
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF020206),
                        Color(0xFF0A051A),
                        Color(0xFF0E0C22),
                        Color(0xFF050510)
                    )
                ),
                size = size
            )

            // Cosmic Nebula Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF7928CA).copy(alpha = 0.22f),
                        Color(0xFF00E5FF).copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(canvasW * 0.7f, canvasH * 0.3f),
                    radius = canvasW * 0.65f
                ),
                radius = canvasW * 0.65f,
                center = Offset(canvasW * 0.7f, canvasH * 0.3f)
            )

            // 2. Starfield
            for (star in stars) {
                val twinkle = (sin((progress / 1000f) * star.twinkleSpeed * Math.PI.toFloat() * 2f) * 0.4f + 0.6f).coerceIn(0.2f, 1f)
                val starColor = if (star.isCyan) Color(0xFF80DEEA) else Color.White
                drawCircle(
                    color = starColor.copy(alpha = star.baseAlpha * twinkle),
                    radius = star.radius,
                    center = Offset(star.xFrac * canvasW, star.yFrac * canvasH)
                )
            }

            // 3. Curved Lunar Surface (Moon Horizon)
            val moonCenterY = canvasH * 0.72f
            val moonPath = Path().apply {
                moveTo(0f, moonCenterY)
                quadraticBezierTo(
                    canvasW * 0.5f,
                    moonCenterY - 35f,
                    canvasW,
                    moonCenterY + 15f
                )
                lineTo(canvasW, canvasH)
                lineTo(0f, canvasH)
                close()
            }

            // Moon surface gradient
            drawPath(
                path = moonPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2A2D3E),
                        Color(0xFF1E202E),
                        Color(0xFF11131E),
                        Color(0xFF090A10)
                    ),
                    startY = moonCenterY - 35f,
                    endY = canvasH
                )
            )

            // Moon horizon glowing rim light
            drawPath(
                path = Path().apply {
                    moveTo(0f, moonCenterY)
                    quadraticBezierTo(
                        canvasW * 0.5f,
                        moonCenterY - 35f,
                        canvasW,
                        moonCenterY + 15f
                    )
                },
                color = Color(0xFF00E5FF).copy(alpha = 0.35f),
                style = Stroke(width = 3.5f)
            )

            // Moon Craters
            drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF151824), Color(0xFF33384D)),
                    start = Offset(canvasW * 0.22f, moonCenterY + 45f),
                    end = Offset(canvasW * 0.22f + 50f, moonCenterY + 75f)
                ),
                topLeft = Offset(canvasW * 0.18f, moonCenterY + 45f),
                size = Size(65f, 24f)
            )
            drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF151824), Color(0xFF33384D)),
                    start = Offset(canvasW * 0.72f, moonCenterY + 65f),
                    end = Offset(canvasW * 0.72f + 80f, moonCenterY + 95f)
                ),
                topLeft = Offset(canvasW * 0.68f, moonCenterY + 65f),
                size = Size(90f, 32f)
            )
            drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF12141F), Color(0xFF2A2D3C)),
                    start = Offset(canvasW * 0.45f, moonCenterY + 110f),
                    end = Offset(canvasW * 0.45f + 40f, moonCenterY + 130f)
                ),
                topLeft = Offset(canvasW * 0.43f, moonCenterY + 110f),
                size = Size(50f, 18f)
            )

            // 4. Astronaut & Waving Flag (Centerpiece of 0ms - 1450ms)
            val sceneAlpha = if (progress < 1350f) 1f else (1f - (progress - 1350f) / 300f).coerceIn(0f, 1f)
            if (sceneAlpha > 0.01f) {
                // Astronaut position
                val astroCenterX = canvasW * 0.42f
                val astroGroundY = moonCenterY + 20f

                // Flagpole strike / plant vibration at 420ms
                val plantOffset = if (progress < 420f) {
                    val t = (progress / 420f).coerceIn(0f, 1f)
                    (1f - t) * -60f
                } else {
                    val dt = (progress - 420f) / 200f
                    if (dt < 1f) sin(dt * Math.PI.toFloat() * 4f) * 4f * (1f - dt) else 0f
                }

                // Draw Astronaut
                drawAstronaut(
                    x = astroCenterX,
                    groundY = astroGroundY + plantOffset * 0.2f,
                    polePlantProgress = (progress / 420f).coerceIn(0f, 1f),
                    alpha = sceneAlpha
                )

                // Glowing Neon Flagpole
                val poleBaseX = astroCenterX + 42f
                val poleBaseY = astroGroundY + 12f
                val poleTopY = astroGroundY - 180f + plantOffset

                // Pole Outer Neon Glow
                drawLine(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFFFFD600), Color(0xFF00E5FF), Color(0xFFFF007A))
                    ),
                    start = Offset(poleBaseX, poleTopY - 10f),
                    end = Offset(poleBaseX, poleBaseY),
                    strokeWidth = 10f,
                    cap = StrokeCap.Round,
                    alpha = sceneAlpha * 0.45f
                )

                // Pole Core
                drawLine(
                    color = Color.White,
                    start = Offset(poleBaseX, poleTopY - 10f),
                    end = Offset(poleBaseX, poleBaseY),
                    strokeWidth = 3.5f,
                    cap = StrokeCap.Round,
                    alpha = sceneAlpha
                )

                // Golden sphere on top of flagpole
                drawCircle(
                    color = Color(0xFFFFD600),
                    radius = 6.5f,
                    center = Offset(poleBaseX, poleTopY - 10f),
                    alpha = sceneAlpha
                )

                // Ground Impact Sparks & Dust Burst
                if (progress >= 420f && progress < 1200f) {
                    val sparkProgress = (progress - 420f) / 780f
                    drawImpactSparks(
                        originX = poleBaseX,
                        originY = poleBaseY,
                        sparkProgress = sparkProgress,
                        alpha = sceneAlpha
                    )
                }

                // 5. The Waving Psychedelic Butterfly Flag!
                drawWavingButterflyFlag(
                    poleX = poleBaseX,
                    poleTopY = poleTopY,
                    progressMs = progress,
                    alpha = sceneAlpha
                )
            }

            // 6. Center Title & MTV 3D Logo Reveal Phase (1350ms - 2250ms)
            if (isTitlePhase) {
                drawMtvTitlePhase(
                    canvasW = canvasW,
                    canvasH = canvasH,
                    titleProgress = titleProgress
                )
            }
        }

        // Tap to Skip Pill (appears during first 1.8s)
        if (progress < 1800f) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 44.dp, end = 20.dp)
            ) {
                Text(
                    text = "Skip ❯",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * Draws the detailed lunar astronaut planting the flagpole.
 */
private fun DrawScope.drawAstronaut(
    x: Float,
    groundY: Float,
    polePlantProgress: Float,
    alpha: Float
) {
    val suitWhite = Color(0xFFE2E8F0)
    val suitShadow = Color(0xFF94A3B8)
    val visorGold = Color(0xFFFFB300)
    val visorGlow = Color(0xFFFFE082)
    val jointGray = Color(0xFF475569)

    // Body dimensions
    val hipY = groundY - 58f
    val chestY = groundY - 95f
    val headY = groundY - 122f

    // 1. Oxygen Life Support Backpack
    drawRoundRect(
        color = suitShadow,
        topLeft = Offset(x - 28f, headY + 12f),
        size = Size(20f, 52f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
        alpha = alpha
    )
    // Small telemetry LED lights on backpack
    drawCircle(color = Color(0xFF00E5FF), radius = 2f, center = Offset(x - 24f, headY + 22f), alpha = alpha)
    drawCircle(color = Color(0xFFFF0055), radius = 2f, center = Offset(x - 24f, headY + 30f), alpha = alpha)

    // 2. Legs & Lunar Boots
    // Left Leg
    val leftLegPath = Path().apply {
        moveTo(x - 8f, hipY)
        lineTo(x - 16f, groundY - 12f)
        lineTo(x - 24f, groundY)
        lineTo(x - 6f, groundY)
        lineTo(x - 2f, groundY - 14f)
        close()
    }
    drawPath(path = leftLegPath, color = suitShadow, alpha = alpha)

    // Right Leg (Forward braced against ground)
    val rightLegPath = Path().apply {
        moveTo(x + 6f, hipY)
        lineTo(x + 18f, groundY - 10f)
        lineTo(x + 28f, groundY)
        lineTo(x + 10f, groundY)
        lineTo(x + 8f, groundY - 12f)
        close()
    }
    drawPath(path = rightLegPath, color = suitWhite, alpha = alpha)

    // Knee joint rings
    drawLine(color = jointGray, start = Offset(x - 14f, groundY - 24f), end = Offset(x - 6f, groundY - 24f), strokeWidth = 3f, alpha = alpha)
    drawLine(color = jointGray, start = Offset(x + 10f, groundY - 22f), end = Offset(x + 20f, groundY - 22f), strokeWidth = 3f, alpha = alpha)

    // 3. Torso / Spacesuit Chest
    val torsoPath = Path().apply {
        moveTo(x - 14f, headY + 18f)
        lineTo(x + 16f, headY + 18f)
        lineTo(x + 12f, hipY)
        lineTo(x - 10f, hipY)
        close()
    }
    drawPath(path = torsoPath, color = suitWhite, alpha = alpha)

    // Chest control unit & mission patch
    drawRect(
        color = Color(0xFF334155),
        topLeft = Offset(x - 6f, chestY - 10f),
        size = Size(14f, 16f),
        alpha = alpha
    )
    drawRect(
        color = Color(0xFF00E5FF),
        topLeft = Offset(x - 4f, chestY - 7f),
        size = Size(10f, 4f),
        alpha = alpha
    )

    // 4. Helmet & Gold Mirrored Visor
    drawCircle(
        color = suitWhite,
        radius = 16f,
        center = Offset(x, headY),
        alpha = alpha
    )

    // Gold Reflective Visor (reflecting the cosmic stars and neon flag)
    drawOval(
        brush = Brush.linearGradient(
            colors = listOf(visorGlow, visorGold, Color(0xFFB45309)),
            start = Offset(x - 4f, headY - 8f),
            end = Offset(x + 14f, headY + 8f)
        ),
        topLeft = Offset(x - 2f, headY - 10f),
        size = Size(18f, 18f),
        alpha = alpha
    )
    // Visor specular glint
    drawCircle(
        color = Color.White.copy(alpha = 0.85f * alpha),
        radius = 2.5f,
        center = Offset(x + 4f, headY - 6f)
    )

    // 5. Left Arm (Extended in balancing motion)
    val leftArmPath = Path().apply {
        moveTo(x - 12f, headY + 24f)
        lineTo(x - 30f, headY + 38f)
        lineTo(x - 38f, headY + 50f)
    }
    drawPath(path = leftArmPath, color = suitShadow, style = Stroke(width = 9f, cap = StrokeCap.Round), alpha = alpha)
    drawCircle(color = suitWhite, radius = 5.5f, center = Offset(x - 38f, headY + 50f), alpha = alpha)

    // 6. Right Arm (Gripping the glowing flagpole firmly)
    val rightHandX = x + 42f
    val rightHandY = groundY - 105f
    val rightArmPath = Path().apply {
        moveTo(x + 12f, headY + 22f)
        lineTo(x + 28f, headY + 34f)
        lineTo(rightHandX, rightHandY)
    }
    drawPath(path = rightArmPath, color = suitWhite, style = Stroke(width = 9f, cap = StrokeCap.Round), alpha = alpha)
    // Astronaut glove gripping pole
    drawCircle(color = Color(0xFF475569), radius = 6.5f, center = Offset(rightHandX, rightHandY), alpha = alpha)
}

/**
 * Draws the waving psychedelic Butterfly Flag with fluid wave physics and morphing neon colors.
 */
private fun DrawScope.drawWavingButterflyFlag(
    poleX: Float,
    poleTopY: Float,
    progressMs: Float,
    alpha: Float
) {
    val flagWidth = 145f
    val flagHeight = 90f
    val flagStartX = poleX + 2f
    val flagStartY = poleTopY + 12f

    // Wave parameters
    val waveFreq = 0.042f
    val waveSpeed = progressMs * 0.012f
    val waveAmp = 7.5f

    // Draw flag cloth using vertical wave strips to create realistic rippling 3D cloth
    val numStrips = 28
    val stripWidth = flagWidth / numStrips

    // Color cycle for psychedelic morphing (hot magenta, sunny yellow, cyan, electric purple)
    val colorCycle = (progressMs * 0.0018f) % (2f * Math.PI.toFloat())
    val neonMagenta = Color(0xFFFF007A)
    val neonYellow = Color(0xFFFFD600)
    val neonCyan = Color(0xFF00E5FF)
    val neonPurple = Color(0xFF7928CA)

    for (i in 0 until numStrips) {
        val x0 = flagStartX + i * stripWidth
        val x1 = x0 + stripWidth
        val relX0 = i * stripWidth
        val relX1 = (i + 1) * stripWidth

        // Sine displacement along flag
        val yOffset0 = sin(waveSpeed - relX0 * waveFreq) * waveAmp * (relX0 / flagWidth)
        val yOffset1 = sin(waveSpeed - relX1 * waveFreq) * waveAmp * (relX1 / flagWidth)

        // Shading factor based on wave slope for 3D cloth sheen
        val slope = cos(waveSpeed - relX0 * waveFreq)
        val clothSheen = (0.75f + 0.25f * slope).coerceIn(0.5f, 1.15f)

        // Fluid morphing color for this strip
        val hueFrac = (relX0 / flagWidth + colorCycle / (2f * Math.PI.toFloat())) % 1f
        val stripColor = when {
            hueFrac < 0.25f -> lerpColor(neonMagenta, neonYellow, hueFrac / 0.25f)
            hueFrac < 0.50f -> lerpColor(neonYellow, neonCyan, (hueFrac - 0.25f) / 0.25f)
            hueFrac < 0.75f -> lerpColor(neonCyan, neonPurple, (hueFrac - 0.50f) / 0.25f)
            else -> lerpColor(neonPurple, neonMagenta, (hueFrac - 0.75f) / 0.25f)
        }

        val shadedColor = Color(
            red = (stripColor.red * clothSheen).coerceIn(0f, 1f),
            green = (stripColor.green * clothSheen).coerceIn(0f, 1f),
            blue = (stripColor.blue * clothSheen).coerceIn(0f, 1f),
            alpha = alpha
        )

        // Draw strip trapezoid
        val stripPath = Path().apply {
            moveTo(x0, flagStartY + yOffset0)
            lineTo(x1, flagStartY + yOffset1)
            lineTo(x1, flagStartY + flagHeight + yOffset1)
            lineTo(x0, flagStartY + flagHeight + yOffset0)
            close()
        }
        drawPath(path = stripPath, color = shadedColor)
    }

    // Glowing Neon Border around the flag
    val topBorderPath = Path()
    val bottomBorderPath = Path()
    for (i in 0..numStrips) {
        val relX = i * stripWidth
        val curX = flagStartX + relX
        val yOff = sin(waveSpeed - relX * waveFreq) * waveAmp * (relX / flagWidth)
        if (i == 0) {
            topBorderPath.moveTo(curX, flagStartY + yOff)
            bottomBorderPath.moveTo(curX, flagStartY + flagHeight + yOff)
        } else {
            topBorderPath.lineTo(curX, flagStartY + yOff)
            bottomBorderPath.lineTo(curX, flagStartY + flagHeight + yOff)
        }
    }
    drawPath(path = topBorderPath, color = Color.White.copy(alpha = 0.9f * alpha), style = Stroke(width = 2.5f))
    drawPath(path = bottomBorderPath, color = Color.White.copy(alpha = 0.9f * alpha), style = Stroke(width = 2.5f))

    // Vertical right edge of flag
    val lastYOff = sin(waveSpeed - flagWidth * waveFreq) * waveAmp
    drawLine(
        color = Color.White.copy(alpha = 0.9f * alpha),
        start = Offset(flagStartX + flagWidth, flagStartY + lastYOff),
        end = Offset(flagStartX + flagWidth, flagStartY + flagHeight + lastYOff),
        strokeWidth = 2.5f
    )

    // Draw the iconic Butterfly Logo directly on the flag!
    val flagMidX = flagStartX + flagWidth * 0.52f
    val flagMidY = flagStartY + flagHeight * 0.50f + sin(waveSpeed - flagWidth * 0.52f * waveFreq) * waveAmp * 0.52f
    drawButterflyLogoOnFlag(
        centerX = flagMidX,
        centerY = flagMidY,
        size = 54f,
        color = Color(0xFF0F172A),
        glowColor = Color.White,
        alpha = alpha
    )
}

/**
 * Draws the high-contrast Butterfly symbol directly mapped onto the flag.
 */
private fun DrawScope.drawButterflyLogoOnFlag(
    centerX: Float,
    centerY: Float,
    size: Float,
    color: Color,
    glowColor: Color,
    alpha: Float
) {
    val scale = size / 16f
    val halfW = size * 0.5f
    val halfH = size * 0.5f

    translate(left = centerX - halfW, top = centerY - halfH) {
        // Outer glowing silhouette
        val butterflyPath = Path().apply {
            moveTo(3.468f * scale, 1.948f * scale)
            cubicTo(5.303f * scale, 3.325f * scale, 7.276f * scale, 6.118f * scale, 8f * scale, 7.616f * scale)
            cubicTo(8.725f * scale, 6.118f * scale, 10.698f * scale, 3.325f * scale, 12.532f * scale, 1.948f * scale)
            cubicTo(13.855f * scale, 0.955f * scale, 16f * scale, 0.186f * scale, 16f * scale, 2.632f * scale)
            cubicTo(16f * scale, 3.121f * scale, 15.72f * scale, 6.737f * scale, 15.556f * scale, 7.324f * scale)
            cubicTo(14.984f * scale, 9.364f * scale, 12.903f * scale, 9.885f * scale, 11.052f * scale, 9.57f * scale)
            cubicTo(14.288f * scale, 10.121f * scale, 15.112f * scale, 11.945f * scale, 13.333f * scale, 13.77f * scale)
            cubicTo(9.957f * scale, 17.234f * scale, 8.481f * scale, 12.9f * scale, 8.103f * scale, 11.79f * scale)
            cubicTo(8.033f * scale, 11.586f * scale, 8f * scale, 11.49f * scale, 8f * scale, 11.572f * scale)
            cubicTo(8f * scale, 11.49f * scale, 7.967f * scale, 11.586f * scale, 7.897f * scale, 11.79f * scale)
            cubicTo(7.519f * scale, 12.9f * scale, 6.043f * scale, 17.234f * scale, 2.667f * scale, 13.77f * scale)
            cubicTo(0.888f * scale, 11.945f * scale, 1.712f * scale, 10.121f * scale, 4.948f * scale, 9.57f * scale)
            cubicTo(3.097f * scale, 9.885f * scale, 1.016f * scale, 9.364f * scale, 0.444f * scale, 7.324f * scale)
            cubicTo(0.28f * scale, 6.737f * scale, 0f * scale, 3.121f * scale, 0f * scale, 2.632f * scale)
            cubicTo(0f * scale, 0.186f * scale, 2.145f * scale, 0.955f * scale, 3.468f * scale, 1.948f * scale)
            close()
        }

        // White halo
        drawPath(
            path = butterflyPath,
            color = glowColor.copy(alpha = 0.95f * alpha),
            style = Stroke(width = 3f)
        )
        // Solid black/deep midnight fill inside flag
        drawPath(
            path = butterflyPath,
            color = color.copy(alpha = 0.92f * alpha)
        )
    }
}

/**
 * Draws lunar impact dust and sparks radiating from the flagpole planting point.
 */
private fun DrawScope.drawImpactSparks(
    originX: Float,
    originY: Float,
    sparkProgress: Float,
    alpha: Float
) {
    val sparkAlpha = (1f - sparkProgress).coerceIn(0f, 1f) * alpha
    if (sparkAlpha <= 0.01f) return

    val numSparks = 14
    for (i in 0 until numSparks) {
        val angle = (i.toFloat() / numSparks) * Math.PI.toFloat() * 1.4f + Math.PI.toFloat() * 0.8f
        val dist = sparkProgress * 65f * (0.6f + 0.4f * sin(i * 3.7f))
        val spX = originX + cos(angle) * dist
        val spY = originY + sin(angle) * dist * 0.45f - (sparkProgress * 15f)

        val sparkColor = if (i % 2 == 0) Color(0xFFFFD600) else Color(0xFF00E5FF)
        drawCircle(
            color = sparkColor.copy(alpha = sparkAlpha),
            radius = (3f * (1f - sparkProgress)).coerceAtLeast(0.8f),
            center = Offset(spX, spY)
        )
    }
}

/**
 * Draws the MTV-Style Title Reveal phase (1350ms - 2250ms)
 * Huge 3D Butterfly Emblem, retro cyber grid, and bold MTV-style typography:
 * "BUTTERFLY"
 * "ENTERTAINMENT STUDIOS"
 */
private fun DrawScope.drawMtvTitlePhase(
    canvasW: Float,
    canvasH: Float,
    titleProgress: Float
) {
    val eased = FastOutSlowInEasing.transform(titleProgress)
    val scale = (0.7f + 0.35f * eased)
    val centerX = canvasW * 0.5f
    val centerY = canvasH * 0.42f

    // Cyber Horizon Grid Lines (Synthwave / MTV 80s aesthetic)
    val gridY = canvasH * 0.76f
    for (i in 0..7) {
        val lineY = gridY + (i * i * 3.8f)
        if (lineY < canvasH) {
            val gridAlpha = (0.35f * (1f - i / 8f) * eased).coerceIn(0f, 1f)
            drawLine(
                color = Color(0xFF00E5FF).copy(alpha = gridAlpha),
                start = Offset(0f, lineY),
                end = Offset(canvasW, lineY),
                strokeWidth = 1.5f
            )
        }
    }

    // Centered Big Butterfly Emblem with 3D Chromatic Offset
    val butterflySize = 130f * scale
    val halfW = butterflySize * 0.5f
    val halfH = butterflySize * 0.5f
    val bScale = butterflySize / 16f

    // 1. Magenta 3D Drop Shadow
    translate(left = centerX - halfW + 6f, top = centerY - halfH + 6f) {
        val path = createButterflyPath(bScale)
        drawPath(path = path, color = Color(0xFFFF007A).copy(alpha = 0.8f * eased))
    }

    // 2. Cyan 3D Offset
    translate(left = centerX - halfW - 4f, top = centerY - halfH - 4f) {
        val path = createButterflyPath(bScale)
        drawPath(path = path, color = Color(0xFF00E5FF).copy(alpha = 0.8f * eased))
    }

    // 3. Bright Yellow & Gold Center Logo
    translate(left = centerX - halfW, top = centerY - halfH) {
        val path = createButterflyPath(bScale)
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFD600), Color(0xFFFF9100), Color(0xFFFF007A))
            )
        )
        drawPath(
            path = path,
            color = Color.White,
            style = Stroke(width = 2.5f)
        )
    }

    // Radial lens flare burst behind the emblem
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFD600).copy(alpha = 0.45f * eased),
                Color(0xFFFF007A).copy(alpha = 0.25f * eased),
                Color.Transparent
            ),
            center = Offset(centerX, centerY),
            radius = canvasW * 0.55f * scale
        ),
        radius = canvasW * 0.55f * scale,
        center = Offset(centerX, centerY)
    )

    // Stylized MTV Typography: "BUTTERFLY" and "ENTERTAINMENT STUDIOS"
    val textY = centerY + halfH + 32f

    // MTV 3D Block typography rendered via custom bold vector text
    drawMtvBlockText(
        text = "BUTTERFLY",
        centerX = centerX,
        topY = textY,
        scale = scale,
        eased = eased
    )

    drawMtvSubText(
        text = "ENTERTAINMENT  STUDIOS",
        centerX = centerX,
        topY = textY + 38f * scale,
        scale = scale,
        eased = eased
    )
}

/**
 * Creates the butterfly Path scaled by [bScale].
 */
private fun createButterflyPath(bScale: Float): Path {
    return Path().apply {
        moveTo(3.468f * bScale, 1.948f * bScale)
        cubicTo(5.303f * bScale, 3.325f * bScale, 7.276f * bScale, 6.118f * bScale, 8f * bScale, 7.616f * bScale)
        cubicTo(8.725f * bScale, 6.118f * bScale, 10.698f * bScale, 3.325f * bScale, 12.532f * bScale, 1.948f * bScale)
        cubicTo(13.855f * bScale, 0.955f * bScale, 16f * bScale, 0.186f * bScale, 16f * bScale, 2.632f * bScale)
        cubicTo(16f * bScale, 3.121f * bScale, 15.72f * bScale, 6.737f * bScale, 15.556f * bScale, 7.324f * bScale)
        cubicTo(14.984f * bScale, 9.364f * bScale, 12.903f * bScale, 9.885f * bScale, 11.052f * bScale, 9.57f * bScale)
        cubicTo(14.288f * bScale, 10.121f * bScale, 15.112f * bScale, 11.945f * bScale, 13.333f * bScale, 13.77f * bScale)
        cubicTo(9.957f * bScale, 17.234f * bScale, 8.481f * bScale, 12.9f * bScale, 8.103f * bScale, 11.79f * bScale)
        cubicTo(8.033f * bScale, 11.586f * bScale, 8f * bScale, 11.49f * bScale, 8f * bScale, 11.572f * bScale)
        cubicTo(8f * bScale, 11.49f * bScale, 7.967f * bScale, 11.586f * bScale, 7.897f * bScale, 11.79f * bScale)
        cubicTo(7.519f * bScale, 12.9f * bScale, 6.043f * bScale, 17.234f * bScale, 2.667f * bScale, 13.77f * bScale)
        cubicTo(0.888f * bScale, 11.945f * bScale, 1.712f * bScale, 10.121f * bScale, 4.948f * bScale, 9.57f * bScale)
        cubicTo(3.097f * bScale, 9.885f * bScale, 1.016f * bScale, 9.364f * bScale, 0.444f * bScale, 7.324f * bScale)
        cubicTo(0.28f * bScale, 6.737f * bScale, 0f * bScale, 3.121f * bScale, 0f * bScale, 2.632f * bScale)
        cubicTo(0f * bScale, 0.186f * bScale, 2.145f * bScale, 0.955f * bScale, 3.468f * bScale, 1.948f * bScale)
        close()
    }
}

/**
 * Draws the bold 3D "BUTTERFLY" typography reminiscent of the MTV block logo.
 */
private fun DrawScope.drawMtvBlockText(
    text: String,
    centerX: Float,
    topY: Float,
    scale: Float,
    eased: Float
) {
    val charWidth = 19f * scale
    val charHeight = 28f * scale
    val charSpacing = 3.5f * scale
    val totalWidth = text.length * charWidth + (text.length - 1) * charSpacing
    val startX = centerX - totalWidth * 0.5f

    // 1. Deep Magenta 3D extrusion offset
    val shadowOffset = 4.5f * scale
    drawLetters(
        text = text,
        startX = startX + shadowOffset,
        topY = topY + shadowOffset,
        charW = charWidth,
        charH = charHeight,
        charSpacing = charSpacing,
        color = Color(0xFFFF007A).copy(alpha = 0.85f * eased)
    )

    // 2. Cyan 3D layer
    drawLetters(
        text = text,
        startX = startX - 2.5f * scale,
        topY = topY - 2.5f * scale,
        charW = charWidth,
        charH = charHeight,
        charSpacing = charSpacing,
        color = Color(0xFF00E5FF).copy(alpha = 0.85f * eased)
    )

    // 3. Bright Yellow & White foreground
    drawLetters(
        text = text,
        startX = startX,
        topY = topY,
        charW = charWidth,
        charH = charHeight,
        charSpacing = charSpacing,
        color = Color(0xFFFFD600).copy(alpha = eased)
    )
}

/**
 * Draws the sub-headline "ENTERTAINMENT STUDIOS" in letterspaced golden neon.
 */
private fun DrawScope.drawMtvSubText(
    text: String,
    centerX: Float,
    topY: Float,
    scale: Float,
    eased: Float
) {
    val charWidth = 8f * scale
    val charHeight = 11f * scale
    val charSpacing = 2.5f * scale
    val totalWidth = text.length * charWidth + (text.length - 1) * charSpacing
    val startX = centerX - totalWidth * 0.5f

    drawLetters(
        text = text,
        startX = startX,
        topY = topY,
        charW = charWidth,
        charH = charHeight,
        charSpacing = charSpacing,
        color = Color(0xFFFFE082).copy(alpha = 0.9f * eased)
    )
}

/**
 * Geometric stroke-based character renderer for crisp, zero-dependency 3D MTV lettering.
 */
private fun DrawScope.drawLetters(
    text: String,
    startX: Float,
    topY: Float,
    charW: Float,
    charH: Float,
    charSpacing: Float,
    color: Color
) {
    val stroke = Stroke(width = (charW * 0.22f).coerceAtLeast(2.2f), cap = StrokeCap.Square)

    for (i in text.indices) {
        val c = text[i]
        if (c == ' ') continue
        val x0 = startX + i * (charW + charSpacing)
        val x1 = x0 + charW
        val xm = x0 + charW * 0.5f
        val y0 = topY
        val y1 = topY + charH
        val ym = topY + charH * 0.5f

        when (c) {
            'B' -> {
                drawLine(color, Offset(x0, y0), Offset(x0, y1), stroke.width, stroke.cap)
                drawLine(color, Offset(x0, y0), Offset(x1 - 3f, y0), stroke.width, stroke.cap)
                drawLine(color, Offset(x1 - 3f, y0), Offset(x1, y0 + 5f), stroke.width, stroke.cap)
                drawLine(color, Offset(x1, y0 + 5f), Offset(x1 - 3f, ym), stroke.width, stroke.cap)
                drawLine(color, Offset(x0, ym), Offset(x1 - 3f, ym), stroke.width, stroke.cap)
                drawLine(color, Offset(x1 - 3f, ym), Offset(x1, ym + 5f), stroke.width, stroke.cap)
                drawLine(color, Offset(x1, ym + 5f), Offset(x1 - 3f, y1), stroke.width, stroke.cap)
                drawLine(color, Offset(x0, y1), Offset(x1 - 3f, y1), stroke.width, stroke.cap)
            }
            'U' -> {
                drawLine(color, Offset(x0, y0), Offset(x0, y1 - 4f), stroke.width, stroke.cap)
                drawLine(color, Offset(x1, y0), Offset(x1, y1 - 4f), stroke.width, stroke.cap)
                drawLine(color, Offset(x0 + 4f, y1), Offset(x1 - 4f, y1), stroke.width, stroke.cap)
            }
            'T' -> {
                drawLine(color, Offset(x0, y0), Offset(x1, y0), stroke.width, stroke.cap)
                drawLine(color, Offset(xm, y0), Offset(xm, y1), stroke.width, stroke.cap)
            }
            'E' -> {
                drawLine(color, Offset(x0, y0), Offset(x0, y1), stroke.width, stroke.cap)
                drawLine(color, Offset(x0, y0), Offset(x1, y0), stroke.width, stroke.cap)
                drawLine(color, Offset(x0, ym), Offset(x1 - 4f, ym), stroke.width, stroke.cap)
                drawLine(color, Offset(x0, y1), Offset(x1, y1), stroke.width, stroke.cap)
            }
            'R' -> {
                drawLine(color, Offset(x0, y0), Offset(x0, y1), stroke.width, stroke.cap)
                drawLine(color, Offset(x0, y0), Offset(x1, y0), stroke.width, stroke.cap)
                drawLine(color, Offset(x1, y0), Offset(x1, ym), stroke.width, stroke.cap)
                drawLine(color, Offset(x0, ym), Offset(x1, ym), stroke.width, stroke.cap)
                drawLine(color, Offset(xm, ym), Offset(x1, y1), stroke.width, stroke.cap)
            }
            'F' -> {
                drawLine(color, Offset(x0, y0), Offset(x0, y1), stroke.width, stroke.cap)
                drawLine(color, Offset(x0, y0), Offset(x1, y0), stroke.width, stroke.cap)
                drawLine(color, Offset(x0, ym), Offset(x1 - 4f, ym), stroke.width, stroke.cap)
            }
            'L' -> {
                drawLine(color, Offset(x0, y0), Offset(x0, y1), stroke.width, stroke.cap)
                drawLine(color, Offset(x0, y1), Offset(x1, y1), stroke.width, stroke.cap)
            }
            'Y' -> {
                drawLine(color, Offset(x0, y0), Offset(xm, ym), stroke.width, stroke.cap)
                drawLine(color, Offset(x1, y0), Offset(xm, ym), stroke.width, stroke.cap)
                drawLine(color, Offset(xm, ym), Offset(xm, y1), stroke.width, stroke.cap)
            }
            'N' -> {
                drawLine(color, Offset(x0, y0), Offset(x0, y1), stroke.width, stroke.cap)
                drawLine(color, Offset(x0, y0), Offset(x1, y1), stroke.width, stroke.cap)
                drawLine(color, Offset(x1, y0), Offset(x1, y1), stroke.width, stroke.cap)
            }
            'A' -> {
                drawLine(color, Offset(xm, y0), Offset(x0, y1), stroke.width, stroke.cap)
                drawLine(color, Offset(xm, y0), Offset(x1, y1), stroke.width, stroke.cap)
                drawLine(color, Offset(x0 + 3f, ym + 2f), Offset(x1 - 3f, ym + 2f), stroke.width, stroke.cap)
            }
            'I' -> {
                drawLine(color, Offset(x0, y0), Offset(x1, y0), stroke.width, stroke.cap)
                drawLine(color, Offset(xm, y0), Offset(xm, y1), stroke.width, stroke.cap)
                drawLine(color, Offset(x0, y1), Offset(x1, y1), stroke.width, stroke.cap)
            }
            'M' -> {
                drawLine(color, Offset(x0, y0), Offset(x0, y1), stroke.width, stroke.cap)
                drawLine(color, Offset(x0, y0), Offset(xm, ym), stroke.width, stroke.cap)
                drawLine(color, Offset(xm, ym), Offset(x1, y0), stroke.width, stroke.cap)
                drawLine(color, Offset(x1, y0), Offset(x1, y1), stroke.width, stroke.cap)
            }
            'S' -> {
                drawLine(color, Offset(x0, y0 + 3f), Offset(x0 + 3f, y0), stroke.width, stroke.cap)
                drawLine(color, Offset(x0 + 3f, y0), Offset(x1, y0), stroke.width, stroke.cap)
                drawLine(color, Offset(x0, y0 + 3f), Offset(x1, ym), stroke.width, stroke.cap)
                drawLine(color, Offset(x1, ym), Offset(x1, y1 - 3f), stroke.width, stroke.cap)
                drawLine(color, Offset(x1, y1 - 3f), Offset(x0, y1), stroke.width, stroke.cap)
            }
            'D' -> {
                drawLine(color, Offset(x0, y0), Offset(x0, y1), stroke.width, stroke.cap)
                drawLine(color, Offset(x0, y0), Offset(x1 - 4f, y0), stroke.width, stroke.cap)
                drawLine(color, Offset(x1 - 4f, y0), Offset(x1, ym), stroke.width, stroke.cap)
                drawLine(color, Offset(x1, ym), Offset(x1 - 4f, y1), stroke.width, stroke.cap)
                drawLine(color, Offset(x0, y1), Offset(x1 - 4f, y1), stroke.width, stroke.cap)
            }
            'O' -> {
                drawLine(color, Offset(x0, y0), Offset(x1, y0), stroke.width, stroke.cap)
                drawLine(color, Offset(x0, y0), Offset(x0, y1), stroke.width, stroke.cap)
                drawLine(color, Offset(x1, y0), Offset(x1, y1), stroke.width, stroke.cap)
                drawLine(color, Offset(x0, y1), Offset(x1, y1), stroke.width, stroke.cap)
            }
            else -> {
                drawLine(color, Offset(x0, y0), Offset(x1, y1), stroke.width, stroke.cap)
            }
        }
    }
}

/**
 * Linearly interpolates two colors.
 */
private fun lerpColor(c1: Color, c2: Color, t: Float): Color {
    val clamped = t.coerceIn(0f, 1f)
    return Color(
        red = c1.red + (c2.red - c1.red) * clamped,
        green = c1.green + (c2.green - c1.green) * clamped,
        blue = c1.blue + (c2.blue - c1.blue) * clamped,
        alpha = c1.alpha + (c2.alpha - c1.alpha) * clamped
    )
}

/**
 * Precalculated cosmic star specification.
 */
private data class StarSpec(
    val xFrac: Float,
    val yFrac: Float,
    val radius: Float,
    val twinkleSpeed: Float,
    val baseAlpha: Float,
    val isCyan: Boolean
)
