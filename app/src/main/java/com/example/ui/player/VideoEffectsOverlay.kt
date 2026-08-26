package com.example.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.effects.*

/**
 * Clean UI / HUD overlay for real-time video upscaling status & GPU telemetry.
 * All actual pixel and frame processing occurs in the Media3 GPU GlShaderProgram pipeline.
 */
@Composable
fun VideoEffectsOverlay(
    config: VideoEffectsConfig,
    modifier: Modifier = Modifier
) {
    val upscaleConfig by VideoEnhancementEngine.config.collectAsState()
    val upscaleTelemetry by VideoEnhancementEngine.telemetry.collectAsState()

    if (!upscaleConfig.showDebugHud) {
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Live On-Screen Telemetry HUD
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
