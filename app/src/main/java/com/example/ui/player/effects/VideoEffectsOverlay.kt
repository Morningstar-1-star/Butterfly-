package com.example.ui.player.effects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color

@Composable
fun VideoEffectsOverlay(
    config: VideoEffectsConfig,
    modifier: Modifier = Modifier
) {
    if (config.brightness == 0.0f &&
        config.contrast == 1.0f &&
        config.saturation == 1.0f &&
        config.amoledBlackLevel == 0.0f &&
        config.nightShieldWarmth == 0.0f
    ) {
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalDim = (config.amoledBlackLevel * 0.7f) + (if (config.brightness < 0f) -config.brightness * 0.8f else 0f)
            if (totalDim > 0.01f) {
                drawRect(
                    color = Color.Black.copy(alpha = totalDim.coerceIn(0f, 0.75f)),
                    size = size
                )
            }

            // Brightness boost (White overlay with Screen blend)
            if (config.brightness > 0.01f) {
                drawRect(
                    color = Color.White.copy(alpha = (config.brightness * 0.6f).coerceIn(0f, 0.4f)),
                    size = size,
                    blendMode = BlendMode.Screen
                )
            }

            // Contrast enhancement (Soft overlay blend)
            if (config.contrast > 1.05f) {
                val contrastAlpha = ((config.contrast - 1.0f) * 0.35f).coerceIn(0f, 0.3f)
                drawRect(
                    color = Color.Black.copy(alpha = contrastAlpha),
                    size = size,
                    blendMode = BlendMode.Overlay
                )
            }

            // Night Warm Shield (Orange amber tint for eye comfort)
            if (config.nightShieldWarmth > 0.01f) {
                drawRect(
                    color = Color(0xFFFF9800).copy(alpha = (config.nightShieldWarmth * 0.45f).coerceIn(0f, 0.35f)),
                    size = size,
                    blendMode = BlendMode.Color
                )
            }
        }
    }
}
