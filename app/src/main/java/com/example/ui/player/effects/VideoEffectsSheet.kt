package com.example.ui.player.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VideoEffectsContent(
    effectsManager: VideoEffectsManager,
    modifier: Modifier = Modifier
) {
    val config by effectsManager.config.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Presets Header & Horizontal Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Picture Presets",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(
                onClick = { effectsManager.resetToDefaults() },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reset", fontSize = 12.sp)
            }
        }

        val presetsScrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(presetsScrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VideoPreset.values().forEach { preset ->
                val isSelected = config.activePreset == preset
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier.clickable { effectsManager.applyPreset(preset) }
                ) {
                    Text(
                        text = preset.displayName,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // Custom Sliders
        // 1. Brightness
        EffectSliderItem(
            icon = Icons.Default.Brightness6,
            title = "Brightness",
            valueText = "${if (config.brightness >= 0) "+" else ""}${(config.brightness * 100).toInt()}%",
            value = config.brightness,
            valueRange = -0.4f..0.4f,
            onValueChange = { effectsManager.setBrightness(it) }
        )

        // 2. Contrast
        EffectSliderItem(
            icon = Icons.Default.Contrast,
            title = "Contrast",
            valueText = "${(config.contrast * 100).toInt()}%",
            value = config.contrast,
            valueRange = 0.6f..1.6f,
            onValueChange = { effectsManager.setContrast(it) }
        )

        // 3. Saturation / Color Vibrancy
        EffectSliderItem(
            icon = Icons.Default.Palette,
            title = "Saturation / Vibrancy",
            valueText = "${(config.saturation * 100).toInt()}%",
            value = config.saturation,
            valueRange = 0.0f..2.0f,
            onValueChange = { effectsManager.setSaturation(it) }
        )

        // 4. AMOLED Deep Black (Gamma Black Enhancement)
        EffectSliderItem(
            icon = Icons.Default.DarkMode,
            title = "AMOLED Deep Black",
            valueText = "${(config.amoledBlackLevel * 200).toInt()}%",
            value = config.amoledBlackLevel,
            valueRange = 0.0f..0.5f,
            onValueChange = { effectsManager.setAmoledBlackLevel(it) }
        )

        // 5. Night Warm Shield (Blue light filter)
        EffectSliderItem(
            icon = Icons.Default.WbIncandescent,
            title = "Night Warm Shield",
            valueText = if (config.nightShieldWarmth <= 0.01f) "Off" else "${(config.nightShieldWarmth * 166).toInt()}%",
            value = config.nightShieldWarmth,
            valueRange = 0.0f..0.6f,
            onValueChange = { effectsManager.setNightShieldWarmth(it) }
        )
    }
}

@Composable
private fun EffectSliderItem(
    icon: ImageVector,
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            )
        }
    }
}
