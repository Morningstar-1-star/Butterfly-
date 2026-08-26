package com.example.ui.player

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.effects.*

/**
 * Collapsible section categories for Video Settings
 */
private enum class EffectSection(val title: String, val icon: ImageVector) {
    UPSCALER("Upscaler", Icons.Outlined.HighQuality),
    PRESETS("Presets", Icons.Outlined.AutoAwesome),
    BASIC("Basic", Icons.Outlined.Tune),
    COLOR("Color", Icons.Outlined.Palette),
    ENHANCEMENT("Filters", Icons.Outlined.FilterFrames)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEffectsSettingsSheet(
    onDismiss: () -> Unit
) {
    val config by VideoEffectsManager.currentConfig.collectAsState()
    val upscaleConfig by VideoEnhancementEngine.config.collectAsState()
    val upscaleTelemetry by VideoEnhancementEngine.telemetry.collectAsState()
    var selectedSection by remember { mutableStateOf(EffectSection.UPSCALER) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            // Header: Title, Power Switch, Reset All, Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Video settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    // Master Toggle
                    Switch(
                        checked = config.isEnabled,
                        onCheckedChange = { VideoEffectsManager.setEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF00E5FF),
                            uncheckedThumbColor = Color.LightGray,
                            uncheckedTrackColor = Color(0xFF2A2A38)
                        ),
                        modifier = Modifier.scale(0.8f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (config.hasActiveEffects()) {
                        TextButton(
                            onClick = { VideoEffectsManager.resetAll() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset All",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset", color = Color(0xFFFF5252), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Navigation Tabs: Presets | Basic | Color | Enhancement
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1B1B24))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                EffectSection.values().forEach { section ->
                    val isSelected = selectedSection == section
                    Surface(
                        onClick = { selectedSection = section },
                        shape = RoundedCornerShape(9.dp),
                        color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color.Transparent,
                        border = if (isSelected) BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)) else null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = section.icon,
                                contentDescription = null,
                                tint = if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = section.title,
                                color = if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when (selectedSection) {
                    EffectSection.UPSCALER -> {
                        VideoUpscalerSection(
                            config = upscaleConfig,
                            telemetry = upscaleTelemetry
                        )
                    }
                    EffectSection.PRESETS -> {
                        PresetsSection(
                            activePreset = config.selectedPreset,
                            onSelectPreset = { VideoEffectsManager.applyPreset(it) }
                        )
                    }
                    EffectSection.BASIC -> {
                        BasicControlsSection(
                            state = config.basic,
                            onReset = { VideoEffectsManager.resetBasic() },
                            onBrightnessChange = { v -> VideoEffectsManager.updateBasic { it.copy(brightness = v) } },
                            onContrastChange = { v -> VideoEffectsManager.updateBasic { it.copy(contrast = v) } },
                            onSaturationChange = { v -> VideoEffectsManager.updateBasic { it.copy(saturation = v) } },
                            onHueChange = { v -> VideoEffectsManager.updateBasic { it.copy(hue = v) } },
                            onGammaChange = { v -> VideoEffectsManager.updateBasic { it.copy(gamma = v) } },
                            onSharpnessChange = { v -> VideoEffectsManager.updateBasic { it.copy(sharpness = v) } }
                        )
                    }
                    EffectSection.COLOR -> {
                        ColorAdvancedSection(
                            state = config.color,
                            onReset = { VideoEffectsManager.resetColor() },
                            onExposureChange = { v -> VideoEffectsManager.updateColor { it.copy(exposure = v) } },
                            onTemperatureChange = { v -> VideoEffectsManager.updateColor { it.copy(temperature = v) } },
                            onTintChange = { v -> VideoEffectsManager.updateColor { it.copy(tint = v) } },
                            onHighlightsChange = { v -> VideoEffectsManager.updateColor { it.copy(highlights = v) } },
                            onShadowsChange = { v -> VideoEffectsManager.updateColor { it.copy(shadows = v) } },
                            onBlacksChange = { v -> VideoEffectsManager.updateColor { it.copy(blacks = v) } },
                            onWhitesChange = { v -> VideoEffectsManager.updateColor { it.copy(whites = v) } },
                            onVibranceChange = { v -> VideoEffectsManager.updateColor { it.copy(vibrance = v) } }
                        )
                    }
                    EffectSection.ENHANCEMENT -> {
                        EnhancementControlsSection(
                            state = config.enhancement,
                            onReset = { VideoEffectsManager.resetEnhancement() },
                            onDenoiseChange = { v -> VideoEffectsManager.updateEnhancement { it.copy(denoise = v) } },
                            onDebandChange = { d -> VideoEffectsManager.updateEnhancement { it.copy(deband = d) } },
                            onDeinterlaceToggle = { b -> VideoEffectsManager.updateEnhancement { it.copy(deinterlace = b) } },
                            onFilmGrainChange = { v -> VideoEffectsManager.updateEnhancement { it.copy(filmGrain = v) } },
                            onVignetteChange = { v -> VideoEffectsManager.updateEnhancement { it.copy(vignette = v) } },
                            onBlurChange = { v -> VideoEffectsManager.updateEnhancement { it.copy(blur = v) } }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 1. Presets Section: Reference Video Style Grid
 */
@Composable
private fun PresetsSection(
    activePreset: PresetFilter,
    onSelectPreset: (PresetFilter) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Atmospheric Presets",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.6f),
            letterSpacing = 1.sp
        )

        // Grid of preset chips in 3 columns
        val presets = PresetFilter.values()
        val rows = presets.toList().chunked(3)

        rows.forEach { rowPresets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowPresets.forEach { preset ->
                    val isSelected = activePreset == preset
                    Surface(
                        onClick = { onSelectPreset(preset) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color(0xFF1E1E28),
                        border = if (isSelected) BorderStroke(1.2.dp, Color(0xFF00E5FF)) else BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = preset.displayName,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                // Fill remainder of row if less than 3
                if (rowPresets.size < 3) {
                    repeat(3 - rowPresets.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF181822),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = activePreset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/**
 * 2. Basic Controls Section: Brightness, Contrast, Saturation, Gamma, Hue, Sharpness
 */
@Composable
private fun BasicControlsSection(
    state: BasicEffectsState,
    onReset: () -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onHueChange: (Float) -> Unit,
    onGammaChange: (Float) -> Unit,
    onSharpnessChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Basic Controls",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 13.sp
            )
            if (!state.isDefault()) {
                TextButton(
                    onClick = onReset,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Basic",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset Basic", color = Color(0xFF00E5FF), fontSize = 11.sp)
                }
            }
        }

        EffectSliderRow(
            label = "Brightness",
            value = state.brightness,
            range = -100f..100f,
            onValueChange = onBrightnessChange,
            onResetValue = { onBrightnessChange(0f) }
        )

        EffectSliderRow(
            label = "Contrast",
            value = state.contrast,
            range = -100f..100f,
            onValueChange = onContrastChange,
            onResetValue = { onContrastChange(0f) }
        )

        EffectSliderRow(
            label = "Saturation",
            value = state.saturation,
            range = -100f..100f,
            onValueChange = onSaturationChange,
            onResetValue = { onSaturationChange(0f) }
        )

        EffectSliderRow(
            label = "Gamma",
            value = state.gamma,
            range = -100f..100f,
            onValueChange = onGammaChange,
            onResetValue = { onGammaChange(0f) }
        )

        EffectSliderRow(
            label = "Hue",
            value = state.hue,
            range = -180f..180f,
            valueFormat = "%.0f°",
            onValueChange = onHueChange,
            onResetValue = { onHueChange(0f) }
        )

        EffectSliderRow(
            label = "Sharpness",
            value = state.sharpness,
            range = 0f..100f,
            isNotched = true,
            onValueChange = onSharpnessChange,
            onResetValue = { onSharpnessChange(0f) }
        )
    }
}

/**
 * 3. Color (Advanced) Section: Exposure, Temperature, Tint, Highlights, Shadows, Blacks, Whites, Vibrance
 */
@Composable
private fun ColorAdvancedSection(
    state: ColorAdvancedEffectsState,
    onReset: () -> Unit,
    onExposureChange: (Float) -> Unit,
    onTemperatureChange: (Float) -> Unit,
    onTintChange: (Float) -> Unit,
    onHighlightsChange: (Float) -> Unit,
    onShadowsChange: (Float) -> Unit,
    onBlacksChange: (Float) -> Unit,
    onWhitesChange: (Float) -> Unit,
    onVibranceChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Advanced Color Grading",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 13.sp
            )
            if (!state.isDefault()) {
                TextButton(
                    onClick = onReset,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Color",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset Color", color = Color(0xFF00E5FF), fontSize = 11.sp)
                }
            }
        }

        EffectSliderRow(
            label = "Exposure",
            value = state.exposure,
            range = -100f..100f,
            onValueChange = onExposureChange,
            onResetValue = { onExposureChange(0f) }
        )

        EffectSliderRow(
            label = "Temperature",
            value = state.temperature,
            range = -100f..100f,
            onValueChange = onTemperatureChange,
            onResetValue = { onTemperatureChange(0f) }
        )

        EffectSliderRow(
            label = "Tint",
            value = state.tint,
            range = -100f..100f,
            onValueChange = onTintChange,
            onResetValue = { onTintChange(0f) }
        )

        EffectSliderRow(
            label = "Highlights",
            value = state.highlights,
            range = -100f..100f,
            onValueChange = onHighlightsChange,
            onResetValue = { onHighlightsChange(0f) }
        )

        EffectSliderRow(
            label = "Shadows",
            value = state.shadows,
            range = -100f..100f,
            onValueChange = onShadowsChange,
            onResetValue = { onShadowsChange(0f) }
        )

        EffectSliderRow(
            label = "Blacks",
            value = state.blacks,
            range = -100f..100f,
            onValueChange = onBlacksChange,
            onResetValue = { onBlacksChange(0f) }
        )

        EffectSliderRow(
            label = "Whites",
            value = state.whites,
            range = -100f..100f,
            onValueChange = onWhitesChange,
            onResetValue = { onWhitesChange(0f) }
        )

        EffectSliderRow(
            label = "Vibrance",
            value = state.vibrance,
            range = -100f..100f,
            onValueChange = onVibranceChange,
            onResetValue = { onVibranceChange(0f) }
        )
    }
}

/**
 * 4. Enhancement Controls Section: Deband, Denoise, Deinterlace, Film Grain, Vignette, Blur
 */
@Composable
private fun EnhancementControlsSection(
    state: EnhancementEffectsState,
    onReset: () -> Unit,
    onDenoiseChange: (Float) -> Unit,
    onDebandChange: (DebandConfig) -> Unit,
    onDeinterlaceToggle: (Boolean) -> Unit,
    onFilmGrainChange: (Float) -> Unit,
    onVignetteChange: (Float) -> Unit,
    onBlurChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Enhancement & Post-Processing",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 13.sp
            )
            if (!state.isDefault()) {
                TextButton(
                    onClick = onReset,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Enhancement",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset Enhancement", color = Color(0xFF00E5FF), fontSize = 11.sp)
                }
            }
        }

        // Deband Collapsible Card (matching reference video 00:03-00:06)
        var debandExpanded by remember { mutableStateOf(state.deband.enabled) }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1B1B26),
            border = BorderStroke(1.dp, if (state.deband.enabled) Color(0xFF00E5FF).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { debandExpanded = !debandExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridOn,
                            contentDescription = null,
                            tint = if (state.deband.enabled) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Deband",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = state.deband.enabled,
                            onCheckedChange = {
                                onDebandChange(state.deband.copy(enabled = it))
                                if (it) debandExpanded = true
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF00E5FF),
                                uncheckedThumbColor = Color.LightGray,
                                uncheckedTrackColor = Color(0xFF2A2A38)
                            ),
                            modifier = Modifier.scale(0.75f)
                        )
                        Icon(
                            imageVector = if (debandExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                AnimatedVisibility(visible = debandExpanded) {
                    Column(
                        modifier = Modifier.padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        EffectSliderRow(
                            label = "Iterations",
                            value = state.deband.iterations.toFloat(),
                            range = 1f..4f,
                            valueFormat = "%.0f",
                            onValueChange = { onDebandChange(state.deband.copy(iterations = it.toInt())) },
                            onResetValue = { onDebandChange(state.deband.copy(iterations = 1)) }
                        )

                        EffectSliderRow(
                            label = "Threshold",
                            value = state.deband.threshold,
                            range = 0f..100f,
                            onValueChange = { onDebandChange(state.deband.copy(threshold = it)) },
                            onResetValue = { onDebandChange(state.deband.copy(threshold = 48f)) }
                        )

                        EffectSliderRow(
                            label = "Range",
                            value = state.deband.range,
                            range = 0f..64f,
                            onValueChange = { onDebandChange(state.deband.copy(range = it)) },
                            onResetValue = { onDebandChange(state.deband.copy(range = 16f)) }
                        )

                        EffectSliderRow(
                            label = "Grain",
                            value = state.deband.grain,
                            range = 0f..100f,
                            onValueChange = { onDebandChange(state.deband.copy(grain = it)) },
                            onResetValue = { onDebandChange(state.deband.copy(grain = 32f)) }
                        )
                    }
                }
            }
        }

        // Deinterlace Switch
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1B1B26),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Deinterlace Scanlines",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Reduces interlace comb artifacts",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }

                Switch(
                    checked = state.deinterlace,
                    onCheckedChange = onDeinterlaceToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF00E5FF),
                        uncheckedThumbColor = Color.LightGray,
                        uncheckedTrackColor = Color(0xFF2A2A38)
                    ),
                    modifier = Modifier.scale(0.8f)
                )
            }
        }

        EffectSliderRow(
            label = "Denoise",
            value = state.denoise,
            range = 0f..100f,
            onValueChange = onDenoiseChange,
            onResetValue = { onDenoiseChange(0f) }
        )

        EffectSliderRow(
            label = "Film Grain",
            value = state.filmGrain,
            range = 0f..100f,
            onValueChange = onFilmGrainChange,
            onResetValue = { onFilmGrainChange(0f) }
        )

        EffectSliderRow(
            label = "Vignette",
            value = state.vignette,
            range = 0f..100f,
            onValueChange = onVignetteChange,
            onResetValue = { onVignetteChange(0f) }
        )

        EffectSliderRow(
            label = "Blur / Soft Focus",
            value = state.blur,
            range = 0f..100f,
            onValueChange = onBlurChange,
            onResetValue = { onBlurChange(0f) }
        )
    }
}

/**
 * Reusable Slider Component matching reference video aesthetic
 */
@Composable
private fun EffectSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueFormat: String = "%.0f",
    isNotched: Boolean = false,
    onValueChange: (Float) -> Unit,
    onResetValue: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column: Label with value directly below
        Column(
            modifier = Modifier
                .width(95.dp)
                .clickable { onResetValue() }
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = String.format(java.util.Locale.US, valueFormat, value),
                style = MaterialTheme.typography.bodySmall,
                color = if (value != 0f && value != range.start) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }

        // Right Column: Slider with Center Reference Tick Line
        Box(
            modifier = Modifier
                .weight(1f)
                .height(34.dp),
            contentAlignment = Alignment.Center
        ) {
            if (range.start < 0f && range.endInclusive > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(2.dp)
                        .height(12.dp)
                        .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(1.dp))
                )
            }

            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                steps = if (isNotched) 10 else 0,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF00E5FF),
                    inactiveTrackColor = Color(0xFF2A2A38),
                    activeTickColor = Color(0xFF00E5FF),
                    inactiveTickColor = Color.White.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
)

/**
 * Real-time GPU Upscaler & Neural Enhancement Section
 */
@Composable
private fun VideoUpscalerSection(
    config: VideoEnhancementConfig,
    telemetry: VideoEnhancementTelemetry
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Active Pipeline Status Card
        Surface(
            color = Color(0xFF1B1B26),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f)),
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
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(telemetry.gpuSafetyState.badgeColorHex))
                        )
                        Text(
                            text = telemetry.activePipelineName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF)
                        )
                    }

                    Text(
                        text = telemetry.gpuSafetyState.displayName,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(telemetry.gpuSafetyState.badgeColorHex)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Source: ${telemetry.inputResolution} → ${telemetry.upscaledResolution}",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                    Text(
                        text = "Passes: ${telemetry.activePassesCount} GLSL",
                        fontSize = 11.sp,
                        color = Color.LightGray.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Enhancement Presets: OFF / AUTO / QUALITY / PERFORMANCE / ANIME / LIVE ACTION
        Text(
            text = "Enhancement Presets",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.6f),
            letterSpacing = 1.sp
        )

        val presets = listOf(
            VideoEnhancementPreset.AUTO to Icons.Outlined.AutoFixHigh,
            VideoEnhancementPreset.QUALITY to Icons.Outlined.HighQuality,
            VideoEnhancementPreset.PERFORMANCE to Icons.Outlined.Speed,
            VideoEnhancementPreset.ANIME to Icons.Outlined.Brush,
            VideoEnhancementPreset.LIVE_ACTION to Icons.Outlined.Movie,
            VideoEnhancementPreset.OFF to Icons.Outlined.Block
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            presets.take(3).forEach { (preset, icon) ->
                val isSelected = config.preset == preset
                Surface(
                    onClick = { VideoEnhancementEngine.setPreset(preset) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color(0xFF1E1E28),
                    border = if (isSelected) BorderStroke(1.dp, Color(0xFF00E5FF)) else BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = preset.displayName,
                            tint = if (isSelected) Color(0xFF00E5FF) else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = preset.displayName,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color(0xFF00E5FF) else Color.White
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            presets.drop(3).forEach { (preset, icon) ->
                val isSelected = config.preset == preset
                Surface(
                    onClick = { VideoEnhancementEngine.setPreset(preset) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color(0xFF1E1E28),
                    border = if (isSelected) BorderStroke(1.dp, Color(0xFF00E5FF)) else BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = preset.displayName,
                            tint = if (isSelected) Color(0xFF00E5FF) else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = preset.displayName,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color(0xFF00E5FF) else Color.White
                        )
                    }
                }
            }
        }

        // Neural Scaler Engine Picker
        Text(
            text = "Upscaler Engine",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.6f),
            letterSpacing = 1.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            UpscalerEngine.values().take(3).forEach { engine ->
                val isSelected = config.upscalerEngine == engine
                Surface(
                    onClick = { VideoEnhancementEngine.setUpscalerEngine(engine) },
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color(0xFF1E1E28),
                    border = if (isSelected) BorderStroke(1.dp, Color(0xFF00E5FF)) else null,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 7.dp, horizontal = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = engine.shortTag,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color(0xFF00E5FF) else Color.White
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            UpscalerEngine.values().drop(3).forEach { engine ->
                val isSelected = config.upscalerEngine == engine
                Surface(
                    onClick = { VideoEnhancementEngine.setUpscalerEngine(engine) },
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color(0xFF1E1E28),
                    border = if (isSelected) BorderStroke(1.dp, Color(0xFF00E5FF)) else null,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 7.dp, horizontal = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = engine.shortTag,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color(0xFF00E5FF) else Color.White
                        )
                    }
                }
            }
        }

        // Sliders: Sharpen, Deband, Denoise
        EffectSliderRow(
            label = "Perceptual Sharpening",
            value = config.sharpen,
            range = 0f..100f,
            onValueChange = { VideoEnhancementEngine.setSharpen(it) },
            onResetValue = { VideoEnhancementEngine.setSharpen(35f) }
        )

        EffectSliderRow(
            label = "Gradient Debanding",
            value = config.deband,
            range = 0f..100f,
            onValueChange = { VideoEnhancementEngine.setDeband(it) },
            onResetValue = { VideoEnhancementEngine.setDeband(25f) }
        )

        EffectSliderRow(
            label = "Temporal Denoising",
            value = config.denoise,
            range = 0f..100f,
            onValueChange = { VideoEnhancementEngine.setDenoise(it) },
            onResetValue = { VideoEnhancementEngine.setDenoise(15f) }
        )

        // Features Toggles
        Surface(
            color = Color(0xFF181822),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Chroma Reconstruction (CfL)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                    Switch(
                        checked = config.chromaReconstructionCfL,
                        onCheckedChange = { VideoEnhancementEngine.setChromaReconstruction(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF00E5FF)
                        ),
                        modifier = Modifier.scale(0.75f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SSim Anti-Ringing Clamping",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                    Switch(
                        checked = config.antiRinging,
                        onCheckedChange = { VideoEnhancementEngine.setAntiRinging(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF00E5FF)
                        ),
                        modifier = Modifier.scale(0.75f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Auto GPU Safety Throttling",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                    Switch(
                        checked = config.autoGpuSafety,
                        onCheckedChange = { VideoEnhancementEngine.setAutoGpuSafety(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF00E5FF)
                        ),
                        modifier = Modifier.scale(0.75f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Live On-Screen Telemetry HUD",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                    Switch(
                        checked = config.showDebugHud,
                        onCheckedChange = { VideoEnhancementEngine.toggleDebugHud() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF00E5FF)
                        ),
                        modifier = Modifier.scale(0.75f)
                    )
                }
            }
        }
    }
}

