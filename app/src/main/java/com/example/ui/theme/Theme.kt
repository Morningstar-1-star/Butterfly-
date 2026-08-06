package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.ui.AppAccentColor
import com.example.ui.ThemeMode

@Composable
fun MyApplicationTheme(
    themeMode: ThemeMode = ThemeMode.AMOLED_DARK,
    accentColor: AppAccentColor = AppAccentColor.YELLOW,
    content: @Composable () -> Unit
) {
    val primaryColor = accentColor.color
    val onPrimaryColor = if (accentColor == AppAccentColor.YELLOW || accentColor == AppAccentColor.CYAN || accentColor == AppAccentColor.GREEN) Color.Black else Color.White

    val colorScheme = if (themeMode == ThemeMode.LIGHT) {
        lightColorScheme(
            primary = primaryColor,
            onPrimary = onPrimaryColor,
            secondary = primaryColor.copy(alpha = 0.85f),
            onSecondary = onPrimaryColor,
            background = Color(0xFFFFFFFF), // Crisp Pure White
            onBackground = Color(0xFF121212),
            surface = Color(0xFFF7F7FA),
            onSurface = Color(0xFF121212),
            surfaceVariant = Color(0xFFEBEBF0),
            onSurfaceVariant = Color(0xFF444446),
            outline = Color(0xFFCCCCCC),
            error = Color(0xFFD32F2F),
            onError = Color.White
        )
    } else {
        // AMOLED Pitch Black
        darkColorScheme(
            primary = primaryColor,
            onPrimary = onPrimaryColor,
            secondary = primaryColor.copy(alpha = 0.85f),
            onSecondary = onPrimaryColor,
            background = Color(0xFF000000), // Pure Pitch Black AMOLED
            onBackground = Color(0xFFF2F2F7),
            surface = Color(0xFF121212),
            onSurface = Color(0xFFF2F2F7),
            surfaceVariant = Color(0xFF1C1C1E),
            onSurfaceVariant = Color(0xFFC7C7CC),
            outline = Color(0xFF333336),
            error = Color(0xFFFF453A),
            onError = Color.Black
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

