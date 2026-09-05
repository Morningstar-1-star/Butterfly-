package com.example.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.effects.*
import kotlin.random.Random

/**
 * Real-time UI / Visual overlay for video vignette, film grain, and GPU telemetry HUD.
 */
@Composable
fun VideoEffectsOverlay(
    config: VideoEffectsConfig,
    modifier: Modifier = Modifier
) {
    val upscaleConfig by VideoEnhancementEngine.config.collectAsState()
    val upscaleTelemetry by VideoEnhancementEngine.telemetry.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Dynamic Cinematic Vignette Filter Layer
        if (config.isEnabled && config.enhancement.vignette > 0f) {
            val vignetteStrength = (config.enhancement.vignette / 100f).coerceIn(0f, 1f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.maxDimension * 0.7f
                val brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Transparent,
                        Color.Black.copy(alpha = vignetteStrength * 0.45f),
                        Color.Black.copy(alpha = vignetteStrength * 0.90f)
                    ),
                    center = center,
                    radius = radius
                )
                drawRect(brush = brush)
            }
        }

        // 2. Dynamic Film Grain Filter Layer
        if (config.isEnabled && config.enhancement.filmGrain > 0f) {
            val grainAlpha = (config.enhancement.filmGrain / 100f * 0.35f).coerceIn(0f, 0.4f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                if (w > 0 && h > 0) {
                    val count = (w * h * 0.0006f * (config.enhancement.filmGrain / 50f)).toInt().coerceIn(100, 1500)
                    val rnd = Random(System.currentTimeMillis() / 150L) // subtle continuous jitter
                    for (i in 0 until count) {
                        val px = rnd.nextFloat() * w
                        val py = rnd.nextFloat() * h
                        val pAlpha = rnd.nextFloat() * grainAlpha
                        val isWhite = rnd.nextBoolean()
                        drawCircle(
                            color = if (isWhite) Color.White.copy(alpha = pAlpha) else Color.Black.copy(alpha = pAlpha),
                            radius = rnd.nextFloat() * 1.5f + 0.5f,
                            center = Offset(px, py)
                        )
                    }
                }
            }
        }

        // 3. Live On-Screen Telemetry HUD
        if (upscaleConfig.showDebugHud) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "⚡ GPU Shader HUD (Media3 GlEffect)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E5FF),
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Pipeline: ${upscaleTelemetry.activePipelineName}",
                        fontSize = 10.sp,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Res: ${upscaleTelemetry.inputResolution} → ${upscaleTelemetry.enhancedFramebufferResolution}",
                        fontSize = 10.sp,
                        color = Color.LightGray,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "FPS: ${String.format("%.1f", upscaleTelemetry.currentFps)} • Status: ${upscaleTelemetry.gpuSafetyState.displayName}",
                        fontSize = 10.sp,
                        color = Color(upscaleTelemetry.gpuSafetyState.badgeColorHex),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
