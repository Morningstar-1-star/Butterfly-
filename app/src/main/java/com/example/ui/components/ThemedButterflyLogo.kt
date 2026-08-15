package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Clean, lightweight Canvas-rendered Butterfly Logo.
 */
@Composable
fun ThemedButterflyLogo(
    modifier: Modifier = Modifier,
    size: Dp = 34.dp
) {
    val backgroundBrush = Brush.linearGradient(
        listOf(Color(0xFF8E24AA), Color(0xFFE91E63))
    )

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

            // Original Butterfly Logo
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

