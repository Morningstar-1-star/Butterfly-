package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R

/**
 * Official Butterfly app logo using the Android VectorDrawable path.
 * Dynamically adopts the user's chosen secondaryAccentColor (yellow, monochrome, cyan, purple, etc.)
 */
@Composable
fun ThemedButterflyLogo(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    useTransparentBg: Boolean = false,
    solidColor: Color? = null
) {
    val activeAccentColor = solidColor ?: MaterialTheme.colorScheme.primary

    if (useTransparentBg) {
        Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_butterfly_silhouette),
                contentDescription = "Butterfly Logo",
                colorFilter = ColorFilter.tint(activeAccentColor),
                modifier = Modifier.size(size * 0.85f)
            )
        }
    } else {
        // High contrast inner butterfly silhouette
        val innerButterflyColor = if (activeAccentColor == Color.White) {
            Color.Black
        } else {
            Color.White
        }

        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(activeAccentColor),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_butterfly_silhouette),
                contentDescription = "Butterfly Logo",
                colorFilter = ColorFilter.tint(innerButterflyColor),
                modifier = Modifier.size(size * 0.65f)
            )
        }
    }
}
