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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppAccentColor
import com.example.ui.ThemeMode
import kotlin.math.cos
import kotlin.math.sin

/**
 * Enchanting Fairy Butterfly Bunny App Opening Animation.
 *
 * Inspired by the whimsical video of the white bunny with vibrant cobalt butterfly wings.
 * Features:
 * - Fluid 3D wing flapping physics with depth perspective
 * - Expressive character nuances (springy ear physics, sweet eye blink, breathing hover)
 * - Distinctive aesthetics for Light Mode (celestial dawn sky, pristine ink-drawn bunny, royal cobalt wings)
 *   and Dark Mode (cosmic AMOLED nebula, moonlight fur with accent rim lighting, bioluminescent glowing wings)
 * - Fairy dust particle streams, lift-off acceleration, and cinematic zoom-through reveal
 * - Instant tap-to-skip support
 */
@Composable
fun FairyBunnyOpeningAnimation(
    themeMode: ThemeMode,
    accentColor: AppAccentColor,
    modifier: Modifier = Modifier,
    onAnimationFinished: () -> Unit
) {
    var isSkipped by remember { mutableStateOf(false) }

    // Master animation clock (0ms to 1600ms)
    val animClock = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        animClock.animateTo(
            targetValue = 1600f,
            animationSpec = tween(
                durationMillis = 1600,
                easing = LinearEasing
            )
        )
        onAnimationFinished()
    }

    val progress = animClock.value
    val isDark = themeMode == ThemeMode.AMOLED_DARK
    val density = LocalDensity.current

    // --- Phase 1: Entrance Bounce (0ms - 380ms) ---
    val entranceScale = when {
        progress < 280f -> {
            val t = (progress / 280f).coerceIn(0f, 1f)
            // Elastic spring overshoot from 0.5f to 1.08f to 1.0f
            0.5f + (0.58f * sin(t * Math.PI.toFloat() * 0.75f))
        }
        progress < 380f -> {
            val t = ((progress - 280f) / 100f).coerceIn(0f, 1f)
            1.08f - (0.08f * t)
        }
        else -> 1f
    }

    // --- Phase 2: Hover & Breathing Float (0ms - 1000ms) ---
    val hoverOffsetY = when {
        progress < 300f -> 0f
        progress < 1000f -> {
            val t = (progress - 300f) / 700f
            -8f * sin(t * Math.PI.toFloat() * 3f)
        }
        progress < 1250f -> {
            // Anticipation crouch down before leaping
            val t = (progress - 1000f) / 250f
            if (t < 0.35f) {
                // Dip down slightly
                6f * sin(t / 0.35f * (Math.PI.toFloat() / 2f))
            } else {
                // Rocket up!
                val jumpT = (t - 0.35f) / 0.65f
                6f - (55f * FastOutSlowInEasing.transform(jumpT))
            }
        }
        else -> {
            val t = ((progress - 1250f) / 350f).coerceIn(0f, 1f)
            -55f - (70f * t)
        }
    }

    // --- Phase 3: 3D Butterfly Wings Flap Angle (degrees) ---
    val flapAngleFront = when {
        // Flap 1 (Gentle flutter 360ms - 620ms)
        progress in 360f..620f -> {
            val t = (progress - 360f) / 260f
            if (t < 0.45f) {
                FastOutSlowInEasing.transform(t / 0.45f) * 48f
            } else {
                val subT = (t - 0.45f) / 0.55f
                48f * (1f - FastOutSlowInEasing.transform(subT)) - (8f * sin(subT * Math.PI.toFloat()))
            }
        }
        // Flap 2 (Deeper flutter 650ms - 950ms)
        progress in 650f..950f -> {
            val t = (progress - 650f) / 300f
            if (t < 0.45f) {
                FastOutSlowInEasing.transform(t / 0.45f) * 58f
            } else {
                val subT = (t - 0.45f) / 0.55f
                58f * (1f - FastOutSlowInEasing.transform(subT)) - (10f * sin(subT * Math.PI.toFloat()))
            }
        }
        // Rapid energetic takeoff flaps (1020ms - 1450ms)
        progress in 1020f..1450f -> {
            val t = (progress - 1020f) / 430f
            // Rapid cycles of flapping
            val cycle = (t * 4.5f) % 1f
            if (cycle < 0.45f) {
                (cycle / 0.45f) * 65f
            } else {
                65f * (1f - (cycle - 0.45f) / 0.55f)
            }
        }
        else -> 0f
    }

    // Back wing flaps with a slight phase lag for lifelike organic depth
    val flapAngleBack = when {
        progress in 380f..640f -> {
            val t = (progress - 380f) / 260f
            if (t < 0.45f) FastOutSlowInEasing.transform(t / 0.45f) * 44f
            else 44f * (1f - FastOutSlowInEasing.transform((t - 0.45f) / 0.55f))
        }
        progress in 670f..970f -> {
            val t = (progress - 670f) / 300f
            if (t < 0.45f) FastOutSlowInEasing.transform(t / 0.45f) * 54f
            else 54f * (1f - FastOutSlowInEasing.transform((t - 0.45f) / 0.55f))
        }
        progress in 1040f..1470f -> {
            val t = (progress - 1040f) / 430f
            val cycle = (t * 4.5f) % 1f
            if (cycle < 0.45f) (cycle / 0.45f) * 60f
            else 60f * (1f - (cycle - 0.45f) / 0.55f)
        }
        else -> 0f
    }

    // --- Phase 4: Bunny Ear Sway & Follow-through ---
    val earAngle = when {
        progress < 300f -> {
            val t = (progress / 300f).coerceIn(0f, 1f)
            -15f * (1f - t)
        }
        progress in 360f..650f -> {
            val t = (progress - 360f) / 290f
            6f * sin(t * Math.PI.toFloat() * 2f)
        }
        progress in 670f..980f -> {
            val t = (progress - 670f) / 310f
            7f * sin(t * Math.PI.toFloat() * 2f)
        }
        progress in 1020f..1450f -> {
            // Wind of flight sweeps ears back
            val t = ((progress - 1020f) / 430f).coerceIn(0f, 1f)
            -14f * t
        }
        else -> 0f
    }

    // --- Phase 5: Eye Blink (580ms - 720ms) ---
    val eyeBlink = when (progress) {
        in 580f..650f -> ((progress - 580f) / 70f).coerceIn(0f, 1f)
        in 650f..720f -> (1f - (progress - 650f) / 70f).coerceIn(0f, 1f)
        else -> 0f
    }

    // --- Phase 6: Flight Pitch & Tilt ---
    val bodyTiltZ = when {
        progress in 360f..950f -> {
            val t = (progress - 360f) / 590f
            -2.5f * sin(t * Math.PI.toFloat() * 2f)
        }
        progress in 1050f..1450f -> {
            // Leaning forward into joyful flight
            val t = ((progress - 1050f) / 250f).coerceIn(0f, 1f)
            -10f * t
        }
        else -> 0f
    }

    // --- Phase 7: Cinematic Zoom-Through (1250ms - 1600ms) ---
    val zoomScale = when {
        progress < 1250f -> 1f
        else -> {
            val t = ((progress - 1250f) / 350f).coerceIn(0f, 1f)
            val curve = CubicBezierEasing(0.35f, 0.0f, 0.15f, 1.0f).transform(t)
            1f + (22f * curve)
        }
    }

    // Overall splash overlay alpha (dissolves during zoom reveal)
    val splashAlpha = when {
        progress < 1320f -> 1f
        else -> {
            val t = ((progress - 1320f) / 280f).coerceIn(0f, 1f)
            1f - CubicBezierEasing(0.4f, 0f, 0.2f, 1f).transform(t)
        }
    }

    // Palette & Lighting Setup
    val activeGlow = remember(isDark, accentColor) {
        if (isDark) {
            if (accentColor == AppAccentColor.MONOCHROME) Color(0xFF38BDF8) else accentColor.color
        } else {
            Color(0xFF2563EB)
        }
    }

    val backgroundBrush = remember(isDark, activeGlow) {
        if (isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF070D1E), // Celestial midnight navy
                    Color(0xFF040711),
                    Color(0xFF010206)  // Pure deep AMOLED space
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFEFF6FF), // Soft morning porcelain sky
                    Color(0xFFDBEAFE), // Airy azure mist
                    Color(0xFFBFDBFE)  // Delicate pastel horizon
                )
            )
        }
    }

    // Wings Palette: Vibrant cobalt blue inspired directly by the user's video, elevated with gradient luster
    val wingBaseColor = remember(isDark) {
        if (isDark) Color(0xFF1D4ED8) else Color(0xFF1A56FF)
    }
    val wingHighlightColor = remember(isDark, activeGlow) {
        if (isDark) activeGlow else Color(0xFF60A5FA)
    }

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
                    isSkipped = true
                    onAnimationFinished()
                },
            contentAlignment = Alignment.Center
        ) {
            // Ambient Luminous Aura behind Bunny
            val auraPulse = 1f + 0.08f * sin((progress / 200f) * Math.PI.toFloat())
            val auraScale = when {
                progress < 300f -> (progress / 300f) * auraPulse
                progress < 1250f -> auraPulse
                else -> auraPulse + ((progress - 1250f) / 350f) * 3f
            }

            Box(
                modifier = Modifier
                    .size(310.dp)
                    .scale(auraScale)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                activeGlow.copy(alpha = if (isDark) 0.32f else 0.25f),
                                activeGlow.copy(alpha = if (isDark) 0.10f else 0.08f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )

            // Celestial Background Sparkles & Constellation Dust
            FairyConstellationDust(
                progress = progress / 1600f,
                isDark = isDark,
                accentGlow = activeGlow
            )

            // Active Fairy Dust particles emitted during wing flutters and takeoff
            if (progress in 400f..1450f) {
                FairyWingDustParticles(
                    progress = (progress - 400f) / 1050f,
                    accentColor = activeGlow,
                    isDark = isDark
                )
            }

            // --- Ground Shadow (dissolves as bunny leaps into the sky) ---
            val groundShadowAlpha = when {
                progress < 1000f -> if (isDark) 0.4f else 0.25f
                progress < 1300f -> {
                    val t = (progress - 1000f) / 300f
                    (1f - t) * (if (isDark) 0.4f else 0.25f)
                }
                else -> 0f
            }
            if (groundShadowAlpha > 0.01f && zoomScale < 2.5f) {
                Box(
                    modifier = Modifier
                        .offset(y = 88.dp)
                        .width(110.dp)
                        .height(20.dp)
                        .alpha(groundShadowAlpha)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    if (isDark) activeGlow.copy(alpha = 0.5f) else Color(0xFF0F172A).copy(alpha = 0.35f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )
            }

            // --- Main Character Rig (Bunny + 3D Butterfly Wings) ---
            val totalCharacterScale = entranceScale * zoomScale
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .graphicsLayer {
                        scaleX = totalCharacterScale
                        scaleY = totalCharacterScale
                        translationY = with(density) { hoverOffsetY.dp.toPx() }
                        rotationZ = bodyTiltZ
                        cameraDistance = 36f * density.density
                    },
                contentAlignment = Alignment.Center
            ) {
                // Soft ambient blur silhouette under character
                if (zoomScale < 2f && isDark) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(1.05f)
                            .blur(14.dp)
                            .alpha(0.35f)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawBunnySilhouette(color = activeGlow)
                        }
                    }
                }

                // 1. BACK WING (Layered behind bunny body in 3D)
                Box(
                    modifier = Modifier
                        .offset(x = 24.dp, y = (-12).dp)
                        .size(105.dp, 125.dp)
                        .graphicsLayer {
                            rotationY = flapAngleBack
                            transformOrigin = TransformOrigin(0.05f, 0.55f)
                            cameraDistance = 28f * density.density
                            alpha = 0.95f
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawButterflyWing(
                            baseColor = wingBaseColor.copy(alpha = 0.92f),
                            highlightColor = wingHighlightColor.copy(alpha = 0.85f),
                            isDark = isDark,
                            isBackWing = true
                        )
                    }
                }

                // 2. BUNNY CHARACTER BODY (Seated profile, facing forward-left)
                Canvas(
                    modifier = Modifier.size(175.dp)
                ) {
                    drawBunnyCharacter(
                        isDark = isDark,
                        accentColor = activeGlow,
                        earAngle = earAngle,
                        eyeBlink = eyeBlink
                    )
                }

                // 3. FRONT WING (Layered in front at the back attachment joint)
                Box(
                    modifier = Modifier
                        .offset(x = 32.dp, y = (-6).dp)
                        .size(118.dp, 140.dp)
                        .graphicsLayer {
                            rotationY = flapAngleFront
                            transformOrigin = TransformOrigin(0.05f, 0.55f)
                            cameraDistance = 28f * density.density
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawButterflyWing(
                            baseColor = wingBaseColor,
                            highlightColor = wingHighlightColor,
                            isDark = isDark,
                            isBackWing = false
                        )
                    }
                }
            }

            // --- Elegant Bottom Branding Header ---
            val brandAlpha = when {
                progress < 250f -> (progress / 250f).coerceIn(0f, 1f)
                progress < 1150f -> 1f
                else -> (1f - (progress - 1150f) / 200f).coerceIn(0f, 1f)
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
                        color = if (isDark) Color.White.copy(alpha = 0.95f) else Color(0xFF0F172A),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 5.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Fairy Bunny • Celestial Edition",
                        color = if (isDark) activeGlow.copy(alpha = 0.9f) else Color(0xFF1D4ED8).copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.2.sp
                    )
                }
            }
        }
    }
}

/**
 * Draws the enchanting Butterfly Wing with graceful upper and lower lobes,
 * rich gradients, and delicate vein luminosity.
 */
private fun DrawScope.drawButterflyWing(
    baseColor: Color,
    highlightColor: Color,
    isDark: Boolean,
    isBackWing: Boolean
) {
    val w = size.width
    val h = size.height

    // Master Wing Path (Originating at x: 0.05*w, y: 0.55*h attachment point)
    val rootX = w * 0.05f
    val rootY = h * 0.52f

    val wingPath = Path().apply {
        moveTo(rootX, rootY)

        // --- Upper Lobe (Large sweeping top wing) ---
        cubicTo(
            w * 0.12f, h * 0.22f,
            w * 0.38f, h * 0.02f,
            w * 0.72f, h * 0.04f
        )
        cubicTo(
            w * 0.96f, h * 0.06f,
            w * 1.02f, h * 0.28f,
            w * 0.88f, h * 0.48f
        )
        // Mid-wing waist indentation
        cubicTo(
            w * 0.78f, h * 0.58f,
            w * 0.58f, h * 0.56f,
            w * 0.52f, h * 0.60f
        )

        // --- Lower Lobe (Rounded soft bottom wing) ---
        cubicTo(
            w * 0.62f, h * 0.68f,
            w * 0.86f, h * 0.76f,
            w * 0.78f, h * 0.92f
        )
        cubicTo(
            w * 0.70f, h * 1.02f,
            w * 0.42f, h * 0.98f,
            w * 0.26f, h * 0.82f
        )
        cubicTo(
            w * 0.14f, h * 0.72f,
            w * 0.06f, h * 0.62f,
            rootX, rootY
        )
        close()
    }

    // Main Wing Body Fill with Radial/Linear Gradient
    val fillBrush = Brush.radialGradient(
        colors = listOf(
            highlightColor,
            baseColor,
            if (isDark) Color(0xFF0F2B66) else Color(0xFF1E40AF)
        ),
        center = Offset(w * 0.35f, h * 0.40f),
        radius = w * 0.9f
    )
    drawPath(path = wingPath, brush = fillBrush)

    // Delicate translucent wing veins for high craftsmanship
    val veinColor = if (isDark) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.65f)
    val strokeWidth = if (isBackWing) 1.2f else 1.8f

    // Upper Lobe Veins
    val v1 = Path().apply {
        moveTo(rootX, rootY)
        cubicTo(w * 0.25f, h * 0.35f, w * 0.50f, h * 0.20f, w * 0.72f, h * 0.12f)
    }
    val v2 = Path().apply {
        moveTo(w * 0.35f, h * 0.30f)
        cubicTo(w * 0.55f, h * 0.32f, w * 0.75f, h * 0.30f, w * 0.88f, h * 0.36f)
    }
    val v3 = Path().apply {
        moveTo(w * 0.38f, h * 0.38f)
        cubicTo(w * 0.55f, h * 0.45f, w * 0.70f, h * 0.48f, w * 0.82f, h * 0.52f)
    }

    // Lower Lobe Veins
    val v4 = Path().apply {
        moveTo(rootX, rootY)
        cubicTo(w * 0.20f, h * 0.65f, w * 0.45f, h * 0.75f, w * 0.68f, h * 0.86f)
    }
    val v5 = Path().apply {
        moveTo(w * 0.28f, h * 0.68f)
        cubicTo(w * 0.35f, h * 0.82f, w * 0.45f, h * 0.90f, w * 0.52f, h * 0.94f)
    }

    drawPath(v1, color = veinColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
    drawPath(v2, color = veinColor.copy(alpha = veinColor.alpha * 0.75f), style = Stroke(width = strokeWidth * 0.8f, cap = StrokeCap.Round))
    drawPath(v3, color = veinColor.copy(alpha = veinColor.alpha * 0.75f), style = Stroke(width = strokeWidth * 0.8f, cap = StrokeCap.Round))
    drawPath(v4, color = veinColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
    drawPath(v5, color = veinColor.copy(alpha = veinColor.alpha * 0.75f), style = Stroke(width = strokeWidth * 0.8f, cap = StrokeCap.Round))

    // Glossy specular edge rim
    val rimColor = if (isDark) highlightColor.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.75f)
    drawPath(
        path = wingPath,
        color = rimColor,
        style = Stroke(width = if (isDark) 2.2f else 1.5f, cap = StrokeCap.Round)
    )
}

/**
 * Draws the sweet, adorable seated white bunny character, perfectly capturing
 * the charm of the video clip with expressive eyes, springy ears, and soft blush.
 */
private fun DrawScope.drawBunnyCharacter(
    isDark: Boolean,
    accentColor: Color,
    earAngle: Float,
    eyeBlink: Float
) {
    val w = size.width
    val h = size.height

    // Main bunny fur colors
    val furColor = Color.White
    val furShadow = if (isDark) Color(0xFF1E293B).copy(alpha = 0.3f) else Color(0xFFE2E8F0).copy(alpha = 0.6f)
    val outlineColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF334155)
    val earBlushColor = Color(0xFFF472B6).copy(alpha = if (isDark) 0.6f else 0.45f)
    val cheekBlushColor = Color(0xFFFB7185).copy(alpha = if (isDark) 0.55f else 0.45f)

    // --- 1. Fluffy White Tail (Bottom Right) ---
    val tailCenter = Offset(w * 0.74f, h * 0.74f)
    drawCircle(
        color = furShadow,
        radius = w * 0.11f,
        center = Offset(tailCenter.x + 1f, tailCenter.y + 2f)
    )
    drawCircle(
        color = furColor,
        radius = w * 0.11f,
        center = tailCenter
    )
    drawCircle(
        color = outlineColor,
        radius = w * 0.11f,
        center = tailCenter,
        style = Stroke(width = 2f)
    )

    // --- 2. Bunny Ears (with dynamic rotation & bounce) ---
    // Back Ear
    translate(left = w * 0.56f, top = h * 0.28f) {
        val backEarAngle = earAngle * 0.8f + 6f
        val backEarPath = Path().apply {
            moveTo(0f, 0f)
            cubicTo(w * 0.02f, -h * 0.18f, w * 0.08f, -h * 0.30f, w * 0.14f, -h * 0.32f)
            cubicTo(w * 0.18f, -h * 0.30f, w * 0.16f, -h * 0.16f, w * 0.10f, 0f)
            close()
        }
        drawPath(
            path = backEarPath,
            color = furColor
        )
        // Back ear inner blush
        val backEarInner = Path().apply {
            moveTo(w * 0.03f, -h * 0.05f)
            cubicTo(w * 0.05f, -h * 0.15f, w * 0.09f, -h * 0.25f, w * 0.13f, -h * 0.26f)
            cubicTo(w * 0.15f, -h * 0.24f, w * 0.13f, -h * 0.14f, w * 0.08f, -h * 0.05f)
            close()
        }
        drawPath(path = backEarInner, color = earBlushColor)
        drawPath(path = backEarPath, color = outlineColor, style = Stroke(width = 2f))
    }

    // Front Ear (Tall, expressive, springy)
    translate(left = w * 0.44f, top = h * 0.26f) {
        val frontEarPath = Path().apply {
            moveTo(0f, 0f)
            // Left edge curves upward
            cubicTo(-w * 0.04f, -h * 0.18f, -w * 0.02f, -h * 0.33f, w * 0.04f, -h * 0.36f)
            // Tip rounding
            cubicTo(w * 0.08f, -h * 0.36f, w * 0.10f, -h * 0.32f, w * 0.10f, -h * 0.22f)
            // Right edge curves back down
            cubicTo(w * 0.10f, -h * 0.12f, w * 0.08f, -h * 0.02f, w * 0.06f, 0f)
            close()
        }
        drawPath(path = frontEarPath, color = furColor)

        // Front ear inner blush
        val frontEarInner = Path().apply {
            moveTo(0f, -h * 0.06f)
            cubicTo(-w * 0.02f, -h * 0.17f, 0f, -h * 0.28f, w * 0.04f, -h * 0.30f)
            cubicTo(w * 0.06f, -h * 0.29f, w * 0.07f, -h * 0.20f, w * 0.06f, -h * 0.06f)
            close()
        }
        drawPath(path = frontEarInner, color = earBlushColor)
        drawPath(path = frontEarPath, color = outlineColor, style = Stroke(width = 2.2f))
    }

    // --- 3. Chubby Seated Body & Paws ---
    val bodyPath = Path().apply {
        // Start at neck / chest
        moveTo(w * 0.38f, h * 0.46f)
        // Chest & belly curve forward-down
        cubicTo(w * 0.28f, h * 0.54f, w * 0.26f, h * 0.68f, w * 0.30f, h * 0.78f)
        // Front little paws resting
        cubicTo(w * 0.32f, h * 0.82f, w * 0.42f, h * 0.82f, w * 0.46f, h * 0.78f)
        // Seated base & rear foot
        cubicTo(w * 0.54f, h * 0.82f, w * 0.68f, h * 0.82f, w * 0.74f, h * 0.76f)
        // Back / rump arching up
        cubicTo(w * 0.74f, h * 0.65f, w * 0.66f, h * 0.52f, w * 0.56f, h * 0.46f)
        close()
    }
    // Soft drop shadow under belly
    drawPath(path = bodyPath, color = furShadow)
    drawPath(path = bodyPath, color = furColor)
    drawPath(path = bodyPath, color = outlineColor, style = Stroke(width = 2.2f))

    // Little front paw toe divider
    drawLine(
        color = outlineColor,
        start = Offset(w * 0.38f, h * 0.76f),
        end = Offset(w * 0.38f, h * 0.80f),
        strokeWidth = 1.8f,
        cap = StrokeCap.Round
    )

    // --- 4. Round Head & Muzzle ---
    val headPath = Path().apply {
        // Chin
        moveTo(w * 0.36f, h * 0.46f)
        // Cute protruding muzzle / cheek
        cubicTo(w * 0.28f, h * 0.46f, w * 0.26f, h * 0.36f, w * 0.32f, h * 0.30f)
        // Forehead
        cubicTo(w * 0.38f, h * 0.24f, w * 0.52f, h * 0.24f, w * 0.58f, h * 0.30f)
        // Nape of neck / back of head
        cubicTo(w * 0.64f, h * 0.36f, w * 0.60f, h * 0.46f, w * 0.48f, h * 0.48f)
        close()
    }
    drawPath(path = headPath, color = furColor)
    drawPath(path = headPath, color = outlineColor, style = Stroke(width = 2.2f))

    // --- 5. Facial Details ---
    // Rosy Cheek Blush
    drawOval(
        color = cheekBlushColor,
        topLeft = Offset(w * 0.33f, h * 0.38f),
        size = Size(w * 0.08f, h * 0.045f)
    )

    // Tiny Cute Nose (soft rose dot at muzzle tip)
    drawCircle(
        color = Color(0xFFF43F5E),
        radius = w * 0.016f,
        center = Offset(w * 0.285f, h * 0.345f)
    )

    // Expressive Eye (Vibrant cobalt blue with white gleam, blinks happily)
    val eyeCenter = Offset(w * 0.38f, h * 0.33f)
    if (eyeBlink > 0.6f) {
        // Happy sleeping/smiling eyelid arc `^_^`
        val blinkArc = Path().apply {
            moveTo(eyeCenter.x - w * 0.024f, eyeCenter.y)
            quadraticBezierTo(eyeCenter.x, eyeCenter.y - h * 0.018f, eyeCenter.x + w * 0.024f, eyeCenter.y)
        }
        drawPath(path = blinkArc, color = Color(0xFF1D4ED8), style = Stroke(width = 2.4f, cap = StrokeCap.Round))
    } else {
        // Open bright round eye (just like the video!)
        val eyeRadius = w * 0.028f * (1f - eyeBlink * 0.7f)
        // Blue eye iris
        drawCircle(
            color = Color(0xFF1D4ED8),
            radius = eyeRadius,
            center = eyeCenter
        )
        // Sparkle white catchlight
        drawCircle(
            color = Color.White,
            radius = eyeRadius * 0.42f,
            center = Offset(eyeCenter.x - eyeRadius * 0.32f, eyeCenter.y - eyeRadius * 0.32f)
        )
    }

    // Whiskers (delicate whimsical lines)
    val whiskerColor = outlineColor.copy(alpha = 0.5f)
    drawLine(
        color = whiskerColor,
        start = Offset(w * 0.28f, h * 0.37f),
        end = Offset(w * 0.22f, h * 0.36f),
        strokeWidth = 1.4f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = whiskerColor,
        start = Offset(w * 0.28f, h * 0.39f),
        end = Offset(w * 0.22f, h * 0.40f),
        strokeWidth = 1.4f,
        cap = StrokeCap.Round
    )
}

/**
 * Procedural Fairy Stardust particles emitted from fluttering wings.
 */
@Composable
private fun FairyWingDustParticles(
    progress: Float,
    accentColor: Color,
    isDark: Boolean
) {
    Canvas(modifier = Modifier.size(260.dp)) {
        val count = 12
        val center = Offset(size.width * 0.65f, size.height * 0.45f)
        for (i in 0 until count) {
            val angle = (i * 30f + (progress * 90f)) * (Math.PI.toFloat() / 180f)
            val dist = 28f + (progress * 130f) * (0.75f + (i % 4) * 0.25f)
            val px = center.x + cos(angle) * dist
            val py = center.y + sin(angle) * (dist * 0.75f) - (progress * 35f)
            val pAlpha = (1f - progress).coerceIn(0f, 1f) * 0.9f
            val radius = (3.6f - (progress * 1.6f)).coerceAtLeast(1.2f)

            drawCircle(
                color = if (i % 2 == 0) Color.White.copy(alpha = pAlpha) else accentColor.copy(alpha = pAlpha),
                radius = radius,
                center = Offset(px, py)
            )
        }
    }
}

/**
 * Background twinkling stars and constellation dust for the celestial ambiance.
 */
@Composable
private fun FairyConstellationDust(
    progress: Float,
    isDark: Boolean,
    accentGlow: Color
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val starPositions = listOf(
            Offset(size.width * 0.18f, size.height * 0.22f),
            Offset(size.width * 0.82f, size.height * 0.28f),
            Offset(size.width * 0.25f, size.height * 0.68f),
            Offset(size.width * 0.75f, size.height * 0.62f),
            Offset(size.width * 0.12f, size.height * 0.45f),
            Offset(size.width * 0.88f, size.height * 0.42f),
            Offset(size.width * 0.50f, size.height * 0.15f)
        )

        starPositions.forEachIndexed { idx, pos ->
            val shimmer = (sin((progress * 12f) + idx * 1.3f) + 1f) / 2f
            val starAlpha = (if (isDark) 0.65f else 0.4f) * shimmer
            val starRadius = (2.2f + shimmer * 1.8f)

            drawCircle(
                color = if (idx % 2 == 0) Color.White.copy(alpha = starAlpha) else accentGlow.copy(alpha = starAlpha),
                radius = starRadius,
                center = pos
            )
        }
    }
}

/**
 * Ambient silhouette for dark-mode glow diffusion.
 */
private fun DrawScope.drawBunnySilhouette(color: Color) {
    drawCircle(
        color = color.copy(alpha = 0.45f),
        radius = size.width * 0.35f,
        center = Offset(size.width * 0.5f, size.height * 0.5f)
    )
}
