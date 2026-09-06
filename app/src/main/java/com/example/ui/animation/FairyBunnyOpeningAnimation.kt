package com.example.ui.animation

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.ui.AppAccentColor
import com.example.ui.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * Handcrafted Fairy Butterfly Bunny Opening Animation.
 * Faithfully recreated to match the reference video:
 * 1. (0.0s - 0.75s) Rear View: White bunny seen from behind with vibrant blue butterfly wings fluttering gently.
 * 2. (0.75s - 1.4s) Looking Back & Wings Shift: Bunny smoothly turns its head back over its shoulder, revealing a cute dot eye while the butterfly wings naturally shift to the side.
 * 3. (1.4s - 1.9s) Scared / Startled: Bunny gets startled! Its eye turns into an 'x', ears perk straight up, shock marks radiate, and body recoils in panic.
 * 4. (1.9s - 2.5s) Rapid Flutter & Fly to Top: Wings flutter at high speed, bunny launches and flies rapidly off the top of the screen.
 * 5. (2.5s) Seamless App Open Transition.
 */
@Composable
fun FairyBunnyOpeningAnimation(
    themeMode: ThemeMode,
    accentColor: AppAccentColor,
    modifier: Modifier = Modifier,
    onAnimationFinished: () -> Unit
) {
    var isSkipped by remember { mutableStateOf(false) }

    // Master animation timeline (0ms to 2550f)
    val animClock = remember { Animatable(0f) }

    // Guarantee that onAnimationFinished is called and never hangs
    LaunchedEffect(Unit) {
        val animJob = launch {
            try {
                animClock.animateTo(
                    targetValue = 2550f,
                    animationSpec = tween(
                        durationMillis = 2500,
                        easing = LinearEasing
                    )
                )
            } catch (_: Exception) {}
        }
        val timeoutJob = launch {
            delay(2650L)
            if (!isSkipped) {
                isSkipped = true
                onAnimationFinished()
            }
        }
        animJob.join()
        timeoutJob.cancel()
        onAnimationFinished()
    }

    val progress = if (isSkipped) 2550f else animClock.value

    // If finished or skipped, immediately unmount to never block touches
    if (progress >= 2500f || isSkipped) {
        SideEffect {
            onAnimationFinished()
        }
        return
    }

    val isDark = themeMode == ThemeMode.AMOLED_DARK
    val density = LocalDensity.current

    // Background color: Clean white in Light Mode, deep sleek midnight in Dark Mode
    val bgColor = if (isDark) Color(0xFF090C15) else Color(0xFFFFFFFF)

    // Palette faithful to the reference video
    val lineInkColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D4ED8)
    val furColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFFFFFFFF)
    val wingBluePrimary = if (isDark) Color(0xFF2563EB) else Color(0xFF2563EB)
    val wingBlueDeep = if (isDark) Color(0xFF1D4ED8) else Color(0xFF1E40AF)
    val eyeColor = if (isDark) Color(0xFF93C5FD) else Color(0xFF1D4ED8)
    val shockLineColor = if (isDark) Color(0xFF93C5FD) else Color(0xFF2563EB)

    // Fade out overlay during final 150ms of transition
    val screenFadeAlpha = when {
        progress > 2350f -> 1f - ((progress - 2350f) / 150f).coerceIn(0f, 1f)
        else -> 1f
    }

    // --- Dynamic Physics & Timeline Variables ---

    // 1. Turn progress: 0f = facing away (rear), 1f = looking back (3/4 profile)
    val turnProgress = when {
        progress < 750f -> 0f
        progress < 1350f -> {
            val t = (progress - 750f) / 600f
            FastOutSlowInEasing.transform(t)
        }
        else -> 1f
    }

    // 2. Scared factor: 0f = calm, 1f = fully startled
    val scaredFactor = when {
        progress < 1400f -> 0f
        progress < 1550f -> {
            val t = (progress - 1400f) / 150f
            FastOutLinearInEasing.transform(t)
        }
        progress < 1900f -> 1f
        progress < 2050f -> {
            val t = 1f - ((progress - 1900f) / 150f)
            t.coerceIn(0f, 1f)
        }
        else -> 0f
    }

    // 3. Flight factor: 0f = on ground, 1f = flying away
    val flightProgress = when {
        progress < 1900f -> 0f
        else -> ((progress - 1900f) / 550f).coerceIn(0f, 1f)
    }

    // 4. Vertical displacement (offsetY)
    val verticalOffsetDp = when {
        // Phase 1: Subtle breathing float
        progress < 750f -> {
            val t = progress / 750f
            sin(t * Math.PI.toFloat() * 2f) * -3f
        }
        // Phase 2: Gentle motion while turning
        progress < 1400f -> {
            val t = (progress - 750f) / 650f
            sin(t * Math.PI.toFloat()) * -4f
        }
        // Phase 3: Startled jump & crouch down (anticipation)
        progress < 1900f -> {
            val t = (progress - 1400f) / 500f
            if (t < 0.25f) {
                // Quick shock jump
                -8f * sin(t / 0.25f * (Math.PI.toFloat() / 2f))
            } else {
                // Crouch down to anticipate takeoff
                val subT = (t - 0.25f) / 0.75f
                -8f + (14f * sin(subT * (Math.PI.toFloat() / 2f)))
            }
        }
        // Phase 4: Rocket straight up off screen!
        else -> {
            val t = flightProgress
            // Exponential upward launch
            6f - (950f * (t * t * 1.15f))
        }
    }

    // 5. Wing Flap Physics
    val wingFlapPhase = when {
        // Phase 1: Gentle flutter (2 cycles)
        progress < 750f -> {
            val t = progress / 750f
            sin(t * Math.PI.toFloat() * 4f)
        }
        // Phase 2: Slow glide / shift as body turns
        progress < 1400f -> {
            val t = (progress - 750f) / 650f
            sin(t * Math.PI.toFloat() * 2.5f) * 0.7f
        }
        // Phase 3: Startled spread wide + jitter
        progress < 1900f -> {
            val t = (progress - 1400f) / 500f
            val jitter = sin(t * Math.PI.toFloat() * 12f) * 0.12f
            0.85f + jitter
        }
        // Phase 4: Rapid high-frequency flight flutter!
        else -> {
            val t = (progress - 1900f) / 1000f
            sin(t * Math.PI.toFloat() * 32f) // ~16 flaps/sec
        }
    }

    // 6. Ear perk & shock wobble
    val earShockAngle = if (scaredFactor > 0.05f) {
        val t = (progress - 1400f).coerceAtLeast(0f)
        sin(t * 0.045f) * 7f * scaredFactor
    } else {
        0f
    }

    // 7. Shock mark scale (0 to 1 with elastic pop)
    val shockMarkScale = when {
        progress in 1420f..1880f -> {
            val t = ((progress - 1420f) / 120f).coerceIn(0f, 1f)
            if (t < 0.7f) {
                (t / 0.7f) * 1.2f
            } else {
                1.2f - (0.2f * ((t - 0.7f) / 0.3f))
            }
        }
        else -> 0f
    }

    // 8. Flight forward tilt
    val flightTiltDegrees = when {
        progress < 1900f -> 0f
        else -> {
            val t = flightProgress
            -10f * sin(t * Math.PI.toFloat() * 0.8f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor.copy(alpha = screenFadeAlpha))
            .alpha(screenFadeAlpha)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Tap to skip
                isSkipped = true
                onAnimationFinished()
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height
            val centerX = canvasW / 2f
            val centerY = canvasH / 2f + verticalOffsetDp.dp.toPx()

            // Character scale base: 260dp bounding box
            val baseScale = (minOf(canvasW, canvasH) / 360f).coerceIn(0.9f, 1.4f)

            // Flight wind lines (when taking off)
            if (flightProgress > 0.05f) {
                val windAlpha = (flightProgress * 1.4f).coerceIn(0f, 0.75f)
                val windColor = shockLineColor.copy(alpha = windAlpha)
                val strokeW = 2.5.dp.toPx()

                // 3 streamlined wind flutter trails underneath the bunny
                drawLine(
                    color = windColor,
                    start = Offset(centerX - 35.dp.toPx() * baseScale, centerY + 65.dp.toPx() * baseScale),
                    end = Offset(centerX - 35.dp.toPx() * baseScale, centerY + 130.dp.toPx() * baseScale),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = windColor,
                    start = Offset(centerX, centerY + 75.dp.toPx() * baseScale),
                    end = Offset(centerX, centerY + 160.dp.toPx() * baseScale),
                    strokeWidth = strokeW * 1.2f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = windColor,
                    start = Offset(centerX + 35.dp.toPx() * baseScale, centerY + 60.dp.toPx() * baseScale),
                    end = Offset(centerX + 35.dp.toPx() * baseScale, centerY + 120.dp.toPx() * baseScale),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
            }

            // Draw character with translation, scale, and flight tilt
            translate(left = centerX, top = centerY) {
                scale(scale = baseScale, pivot = Offset.Zero) {
                    rotate(degrees = flightTiltDegrees, pivot = Offset(0f, 10f)) {
                        drawFairyBunny(
                            turnProgress = turnProgress,
                            scaredFactor = scaredFactor,
                            shockMarkScale = shockMarkScale,
                            wingFlapPhase = wingFlapPhase,
                            earShockAngle = earShockAngle,
                            flightProgress = flightProgress,
                            furColor = furColor,
                            lineInkColor = lineInkColor,
                            wingBluePrimary = wingBluePrimary,
                            wingBlueDeep = wingBlueDeep,
                            eyeColor = eyeColor,
                            shockLineColor = shockLineColor,
                            density = density.density
                        )
                    }
                }
            }
        }
    }
}

/**
 * Draws the complete Bunny Character and Butterfly Wings based on exact animation timeline state.
 */
private fun DrawScope.drawFairyBunny(
    turnProgress: Float,
    scaredFactor: Float,
    shockMarkScale: Float,
    wingFlapPhase: Float,
    earShockAngle: Float,
    flightProgress: Float,
    furColor: Color,
    lineInkColor: Color,
    wingBluePrimary: Color,
    wingBlueDeep: Color,
    eyeColor: Color,
    shockLineColor: Color,
    density: Float
) {
    val mainStrokeWidth = 3.2f * density

    // Anticipation squash & stretch
    val squashX = if (flightProgress > 0.05f) {
        0.92f + (flightProgress * 0.08f)
    } else if (scaredFactor > 0.1f) {
        1.06f
    } else {
        1.0f
    }
    val squashY = if (flightProgress > 0.05f) {
        1.10f
    } else if (scaredFactor > 0.1f) {
        0.92f
    } else {
        1.0f
    }

    scale(scaleX = squashX, scaleY = squashY, pivot = Offset(0f, 40f)) {

        // --- 1. LEFT BUTTERFLY WING (Seen only during rear view, hides as bunny turns) ---
        if (turnProgress < 0.85f) {
            val leftWingAlpha = (1f - (turnProgress / 0.85f)).coerceIn(0f, 1f)
            val leftWingScaleX = (-0.75f * (0.55f + 0.45f * wingFlapPhase))

            translate(left = -8f, top = -12f) {
                scale(scaleX = leftWingScaleX, scaleY = 0.82f, pivot = Offset.Zero) {
                    rotate(degrees = -15f + (wingFlapPhase * 10f), pivot = Offset.Zero) {
                        drawButterflyWingPath(
                            primaryColor = wingBluePrimary.copy(alpha = leftWingAlpha),
                            deepColor = wingBlueDeep.copy(alpha = leftWingAlpha),
                            strokeColor = lineInkColor.copy(alpha = leftWingAlpha),
                            strokeWidth = mainStrokeWidth
                        )
                    }
                }
            }
        }

        // --- 2. BUNNY BODY AND EARS ---
        // Interpolate between Pose 1 (Rear View) and Pose 2/3 (Turned Profile View)
        if (turnProgress < 0.5f) {
            // POSE 1: Rear View (Facing Away)
            drawRearBunny(
                furColor = furColor,
                lineColor = lineInkColor,
                strokeWidth = mainStrokeWidth,
                earShockAngle = earShockAngle
            )
        } else {
            // POSE 2 & 3: Turned Profile View (Looking Back at User)
            drawProfileBunny(
                furColor = furColor,
                lineColor = lineInkColor,
                eyeColor = eyeColor,
                strokeWidth = mainStrokeWidth,
                scaredFactor = scaredFactor,
                earShockAngle = earShockAngle,
                flightProgress = flightProgress
            )
        }

        // --- 3. RIGHT BUTTERFLY WING (Main Prominent Wing) ---
        // As the bunny turns, the wing shifts from back-center to the right side!
        val wingAttachmentX = lerp(5f, 22f, turnProgress)
        val wingAttachmentY = lerp(-12f, -10f, turnProgress)
        val wingAngleBase = lerp(12f, 24f, turnProgress)

        // Flapping scale along horizontal axis (3D foreshortening effect)
        val rightWingFlapScale = 0.35f + (0.65f * ((wingFlapPhase + 1f) / 2f))

        translate(left = wingAttachmentX, top = wingAttachmentY) {
            scale(scaleX = rightWingFlapScale, scaleY = 1.0f, pivot = Offset.Zero) {
                rotate(degrees = wingAngleBase + (wingFlapPhase * 12f), pivot = Offset.Zero) {
                    drawButterflyWingPath(
                        primaryColor = wingBluePrimary,
                        deepColor = wingBlueDeep,
                        strokeColor = lineInkColor,
                        strokeWidth = mainStrokeWidth
                    )
                }
            }
        }

        // --- 4. SHOCK MARKS & SWEAT DROP (Phase 3: Scared) ---
        if (shockMarkScale > 0.05f) {
            drawShockElements(
                scale = shockMarkScale,
                strokeColor = shockLineColor,
                strokeWidth = mainStrokeWidth * 0.9f
            )
        }
    }
}

/**
 * Draws the Bunny Seated from Behind (Pose 1: Rear View).
 */
private fun DrawScope.drawRearBunny(
    furColor: Color,
    lineColor: Color,
    strokeWidth: Float,
    earShockAngle: Float
) {
    // 1. Ears from behind
    // Left Ear
    rotate(degrees = -6f + earShockAngle, pivot = Offset(-14f, -50f)) {
        val leftEarPath = Path().apply {
            moveTo(-18f, -48f)
            cubicTo(-26f, -75f, -22f, -102f, -14f, -106f)
            cubicTo(-6f, -102f, -8f, -75f, -8f, -52f)
            close()
        }
        drawPath(leftEarPath, furColor, style = Fill)
        drawPath(leftEarPath, lineColor, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    // Right Ear
    rotate(degrees = 8f - earShockAngle, pivot = Offset(10f, -48f)) {
        val rightEarPath = Path().apply {
            moveTo(-2f, -52f)
            cubicTo(4f, -75f, 10f, -100f, 18f, -104f)
            cubicTo(26f, -100f, 22f, -75f, 14f, -46f)
            close()
        }
        drawPath(rightEarPath, furColor, style = Fill)
        drawPath(rightEarPath, lineColor, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    // 2. Head and chubby seated body from behind
    val bodyPath = Path().apply {
        moveTo(-32f, 44f) // Left bottom paw
        cubicTo(-42f, 20f, -38f, -10f, -26f, -36f) // Left back
        cubicTo(-20f, -56f, 10f, -56f, 18f, -36f) // Head curve top
        cubicTo(32f, -15f, 36f, 15f, 32f, 44f) // Right flank
        cubicTo(20f, 54f, -18f, 54f, -32f, 44f) // Bottom curve
        close()
    }
    drawPath(bodyPath, furColor, style = Fill)
    drawPath(bodyPath, lineColor, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // 3. Cute fluffy bunny tail on bottom right
    val tailPath = Path().apply {
        moveTo(24f, 32f)
        cubicTo(42f, 28f, 44f, 48f, 26f, 46f)
    }
    drawPath(tailPath, furColor, style = Fill)
    drawPath(tailPath, lineColor, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // Subtle spine posture line
    val backFold = Path().apply {
        moveTo(0f, -10f)
        cubicTo(4f, 8f, 2f, 26f, -2f, 40f)
    }
    drawPath(backFold, lineColor.copy(alpha = 0.45f), style = Stroke(strokeWidth * 0.7f, cap = StrokeCap.Round))
}

/**
 * Draws the Bunny Looking Back in Profile (Pose 2: Looking Back & Pose 3: Scared).
 * Matches frames 00:01 and 00:02 with precision.
 */
private fun DrawScope.drawProfileBunny(
    furColor: Color,
    lineColor: Color,
    eyeColor: Color,
    strokeWidth: Float,
    scaredFactor: Float,
    earShockAngle: Float,
    flightProgress: Float
) {
    // 1. Ears in Profile View
    // Back Ear (Right Ear)
    rotate(degrees = 12f + earShockAngle * 1.2f, pivot = Offset(4f, -44f)) {
        val backEarPath = Path().apply {
            moveTo(-4f, -46f)
            cubicTo(4f, -72f, 12f, -94f, 20f, -96f)
            cubicTo(26f, -93f, 22f, -70f, 14f, -40f)
            close()
        }
        drawPath(backEarPath, furColor, style = Fill)
        drawPath(backEarPath, lineColor, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    // Front Ear (Left Ear)
    rotate(degrees = -4f - earShockAngle, pivot = Offset(-18f, -46f)) {
        val frontEarPath = Path().apply {
            moveTo(-20f, -44f)
            cubicTo(-28f, -70f, -26f, -96f, -18f, -100f)
            cubicTo(-10f, -96f, -10f, -70f, -8f, -48f)
            close()
        }
        drawPath(frontEarPath, furColor, style = Fill)
        drawPath(frontEarPath, lineColor, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    // 2. Bunny Chubby Body Profile (Head turned over shoulder, seated pose)
    val profileBodyPath = Path().apply {
        // Head dome & forehead
        moveTo(-10f, -48f)
        cubicTo(-24f, -44f, -36f, -34f, -40f, -22f) // Forehead down to nose
        cubicTo(-44f, -14f, -42f, -4f, -36f, 4f) // Rounded snout & chubby cheek
        cubicTo(-32f, 14f, -30f, 24f, -32f, 36f) // Neck to front chest

        // Front paw fold
        cubicTo(-34f, 44f, -20f, 46f, -16f, 42f)

        // Belly to hind leg
        cubicTo(-2f, 48f, 18f, 48f, 26f, 42f)

        // Hind paw & flank curve
        cubicTo(36f, 34f, 34f, 16f, 26f, -4f) // Back curve
        cubicTo(22f, -20f, 16f, -34f, 6f, -44f) // Shoulder to back of neck
        cubicTo(0f, -48f, -6f, -48f, -10f, -48f)
        close()
    }
    drawPath(profileBodyPath, furColor, style = Fill)
    drawPath(profileBodyPath, lineColor, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // Front paw resting fold detail line
    val pawFold = Path().apply {
        moveTo(-26f, 32f)
        cubicTo(-24f, 40f, -16f, 42f, -12f, 38f)
    }
    drawPath(pawFold, lineColor, style = Stroke(strokeWidth * 0.85f, cap = StrokeCap.Round))

    // Hind thigh cute contour line
    val thighFold = Path().apply {
        moveTo(22f, 38f)
        cubicTo(18f, 24f, 8f, 22f, 4f, 26f)
    }
    drawPath(thighFold, lineColor.copy(alpha = 0.5f), style = Stroke(strokeWidth * 0.75f, cap = StrokeCap.Round))

    // 3. EYE EXPRESSION:
    // If scaredFactor > 0.45f -> startled 'X' eye!
    // Else -> cute round blue dot eye '•' looking back!
    val eyeCenterX = -24f
    val eyeCenterY = -18f

    if (scaredFactor > 0.45f) {
        // Startled / Scared 'X' Eye (just like Frame 00:02!)
        val xRadius = 6.5f
        val xStroke = strokeWidth * 1.1f

        // Diagonal 1 (\)
        drawLine(
            color = eyeColor,
            start = Offset(eyeCenterX - xRadius, eyeCenterY - xRadius),
            end = Offset(eyeCenterX + xRadius, eyeCenterY + xRadius),
            strokeWidth = xStroke,
            cap = StrokeCap.Round
        )
        // Diagonal 2 (/)
        drawLine(
            color = eyeColor,
            start = Offset(eyeCenterX - xRadius, eyeCenterY + xRadius),
            end = Offset(eyeCenterX + xRadius, eyeCenterY - xRadius),
            strokeWidth = xStroke,
            cap = StrokeCap.Round
        )
    } else {
        // Sweet curious round dot eye
        drawCircle(
            color = eyeColor,
            radius = 5.2f,
            center = Offset(eyeCenterX, eyeCenterY)
        )
        // Tiny cute white reflection spark
        drawCircle(
            color = Color.White,
            radius = 1.6f,
            center = Offset(eyeCenterX - 1.6f, eyeCenterY - 1.6f)
        )
    }
}

/**
 * Draws the Butterfly Wing Path with authentic double-lobe geometry.
 * Rich royal blue fill with crisp outline and subtle wing vein detail.
 */
private fun DrawScope.drawButterflyWingPath(
    primaryColor: Color,
    deepColor: Color,
    strokeColor: Color,
    strokeWidth: Float
) {
    val wingPath = Path().apply {
        // Start at wing hinge / root
        moveTo(0f, 0f)

        // Upper Lobe: sweeps up and out into a majestic fan petal
        cubicTo(16f, -32f, 46f, -62f, 72f, -62f)
        cubicTo(88f, -62f, 96f, -48f, 92f, -32f)
        cubicTo(88f, -18f, 74f, -4f, 52f, 2f) // Waist notch between upper and lower lobes

        // Lower Lobe: rounded teardrop petal
        cubicTo(68f, 16f, 74f, 36f, 62f, 48f)
        cubicTo(50f, 56f, 32f, 52f, 18f, 36f)
        cubicTo(8f, 24f, 2f, 12f, 0f, 0f) // Back to root
        close()
    }

    // 1. Fill solid rich royal blue
    drawPath(wingPath, primaryColor, style = Fill)

    // 2. Clean outer stroke
    drawPath(
        wingPath,
        strokeColor,
        style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // 3. Delicate inner wing vein lines (adding subtle organic artistry)
    val veinPath = Path().apply {
        moveTo(4f, -2f)
        cubicTo(30f, -22f, 56f, -38f, 76f, -42f)
        moveTo(4f, 2f)
        cubicTo(26f, 14f, 44f, 26f, 54f, 32f)
    }
    drawPath(
        veinPath,
        deepColor.copy(alpha = 0.55f),
        style = Stroke(strokeWidth * 0.65f, cap = StrokeCap.Round)
    )
}

/**
 * Draws the Comic Shock Marks and Sweat Drop (Phase 3: Scared).
 * Radiating tick marks near the top-left of the head.
 */
private fun DrawScope.drawShockElements(
    scale: Float,
    strokeColor: Color,
    strokeWidth: Float
) {
    scale(scale = scale, pivot = Offset(-38f, -40f)) {
        // 3 Radiating surprise tick marks above head (\ | /)
        // 1. Left diagonal mark
        drawLine(
            color = strokeColor,
            start = Offset(-40f, -38f),
            end = Offset(-54f, -50f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        // 2. Center upright mark
        drawLine(
            color = strokeColor,
            start = Offset(-30f, -46f),
            end = Offset(-38f, -64f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        // 3. Right diagonal mark
        drawLine(
            color = strokeColor,
            start = Offset(-20f, -52f),
            end = Offset(-22f, -70f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Cute little sweat drop near temple
        val sweatPath = Path().apply {
            moveTo(6f, -26f)
            cubicTo(3f, -21f, 1f, -17f, 4f, -14f)
            cubicTo(7f, -11f, 11f, -13f, 11f, -16f)
            cubicTo(11f, -19f, 8f, -23f, 6f, -26f)
            close()
        }
        drawPath(sweatPath, strokeColor, style = Fill)
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction.coerceIn(0f, 1f)
}
