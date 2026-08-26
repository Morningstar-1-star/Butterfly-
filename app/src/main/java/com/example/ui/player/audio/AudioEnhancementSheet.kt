package com.example.ui.player.audio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

enum class AudioSheetTab(val title: String, val icon: ImageVector) {
    EQUALIZER("Equalizer", Icons.Default.GraphicEq),
    VOICE("Voice Clarity", Icons.Default.RecordVoiceOver),
    DYNAMICS("Dynamics & DRC", Icons.Default.Compress),
    PRESETS("DSP Presets", Icons.Default.Tune)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioEnhancementSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val config by AudioEnhancementEngine.config.collectAsState()
    val meters by AudioEnhancementEngine.meterState.collectAsState()
    var selectedTab by remember { mutableStateOf(AudioSheetTab.EQUALIZER) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = Color(0xFF13131A),
        contentColor = Color.White,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // SHEET HEADER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Audio Enhancement",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Switch(
                        checked = config.isEnabled,
                        onCheckedChange = { AudioEnhancementEngine.setEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFB388FF),
                            uncheckedTrackColor = Color(0xFF2A2A38)
                        ),
                        modifier = Modifier.scale(0.85f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = {
                            if (selectedTab == AudioSheetTab.EQUALIZER) {
                                AudioEnhancementEngine.resetEq()
                            } else {
                                AudioEnhancementEngine.resetToDefaults()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (selectedTab == AudioSheetTab.EQUALIZER) "Reset EQ" else "Reset All",
                            color = Color(0xFFFF5252),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // LIVE METERS BAR
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1B1B26),
                border = BorderStroke(1.dp, Color(0xFF2E2E3E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (config.isEnabled) Color(0xFF00E676) else Color.Gray)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (config.isEnabled) "DSP Engine: Active" else "DSP Engine: Bypassed",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (config.isEnabled) Color(0xFF00E676) else Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "${meters.sampleRate} Hz • ${if (meters.channelCount == 1) "Mono" else if (meters.channelCount == 2) "Stereo" else "${meters.channelCount}.1"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // TAB NAVIGATION
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1A1A24))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AudioSheetTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    Surface(
                        onClick = { selectedTab = tab },
                        shape = RoundedCornerShape(9.dp),
                        color = if (isSelected) Color(0xFFB388FF).copy(alpha = 0.22f) else Color.Transparent,
                        border = if (isSelected) BorderStroke(1.dp, Color(0xFFB388FF).copy(alpha = 0.55f)) else null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 7.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                tint = if (isSelected) Color(0xFFB388FF) else Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = tab.title,
                                color = if (isSelected) Color(0xFFB388FF) else Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // SCROLLABLE BODY
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when (selectedTab) {
                    AudioSheetTab.EQUALIZER -> {
                        EqualizerSection(config = config)
                    }
                    AudioSheetTab.VOICE -> {
                        VoiceClaritySection(config = config)
                    }
                    AudioSheetTab.DYNAMICS -> {
                        DynamicsSection(config = config, meters = meters)
                    }
                    AudioSheetTab.PRESETS -> {
                        DspPresetsSection(config = config)
                    }
                }
            }
        }
    }
}

/**
 * 1. EQUALIZER SECTION: Presets, Curve Visualizer, 10-Band EQ, Bass/Treble Boost, 3D Spatializer
 */
@Composable
private fun EqualizerSection(config: AudioEnhancementConfig) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Equalizer Presets Header & Chips
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Equalizer Presets",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.7f),
                letterSpacing = 1.sp
            )

            val presets = EqualizerPreset.values()
            val rows = presets.toList().chunked(4)

            rows.forEach { rowPresets ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowPresets.forEach { preset ->
                        val isSelected = config.eqPreset == preset
                        Surface(
                            onClick = { AudioEnhancementEngine.setEqPreset(preset) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFFB388FF).copy(alpha = 0.25f) else Color(0xFF1E1E28),
                            border = if (isSelected) BorderStroke(1.2.dp, Color(0xFFB388FF)) else BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            ) {
                                Text(
                                    text = preset.displayName,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFFB388FF) else Color.White,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (rowPresets.size < 4) {
                        repeat(4 - rowPresets.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Real-time EQ Frequency Response Curve Visualizer
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF181822)),
            border = BorderStroke(1.dp, Color(0xFF2A2A38)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Frequency Response Curve",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${config.eqPreset.displayName} Mode",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB388FF),
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                EqCurveCanvas(
                    eqBands = config.eq10BandsDb,
                    bassBoost = config.bassBoostDb + config.bassGainDb,
                    trebleBoost = config.trebleBoostDb + config.trebleGainDb,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(75.dp)
                )
            }
        }

        // 10-Band Graphic Sliders
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF181822)),
            border = BorderStroke(1.dp, Color(0xFF2A2A38)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "10-Band Graphic Equalizer",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                for (index in 0 until 10) {
                    val label = AudioEqualizer.EQ_10_LABELS[index]
                    val gain = config.eq10BandsDb.getOrElse(index) { 0f }
                    EqBandSliderRow(
                        frequencyLabel = label,
                        gainDb = gain,
                        onGainChange = { newGain ->
                            AudioEnhancementEngine.setEqBand(index, newGain)
                        }
                    )
                }
            }
        }

        // Acoustic Enhancers: Bass Boost, Treble Boost, 3D Surround Virtualizer
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF181822)),
            border = BorderStroke(1.dp, Color(0xFF2A2A38)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Acoustic Tuning & Spatializer",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Bass Boost
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Deep Bass Boost",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "+${config.bassBoostDb.roundToInt()} dB",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF)
                        )
                    }
                    Slider(
                        value = config.bassBoostDb,
                        onValueChange = { AudioEnhancementEngine.setBassBoostDb(it) },
                        valueRange = 0f..15f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color(0xFF2A2A38)
                        )
                    )
                }

                // Treble Boost
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFFB388FF),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Treble & Air Clarity",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "+${config.trebleBoostDb.roundToInt()} dB",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB388FF)
                        )
                    }
                    Slider(
                        value = config.trebleBoostDb,
                        onValueChange = { AudioEnhancementEngine.setTrebleBoostDb(it) },
                        valueRange = 0f..15f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFB388FF),
                            activeTrackColor = Color(0xFFB388FF),
                            inactiveTrackColor = Color(0xFF2A2A38)
                        )
                    )
                }

                // 3D Spatial Virtualizer
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SurroundSound,
                                contentDescription = null,
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "3D Surround Virtualizer",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "${config.virtualizerPercent.roundToInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E676)
                        )
                    }
                    Slider(
                        value = config.virtualizerPercent,
                        onValueChange = { AudioEnhancementEngine.setVirtualizerPercent(it) },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E676),
                            activeTrackColor = Color(0xFF00E676),
                            inactiveTrackColor = Color(0xFF2A2A38)
                        )
                    )
                }
            }
        }
    }
}

/**
 * Interactive Real-time EQ Curve Canvas
 */
@Composable
private fun EqCurveCanvas(
    eqBands: FloatArray,
    bassBoost: Float,
    trebleBoost: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val midY = height / 2f

        // Draw center reference 0dB line
        drawLine(
            color = Color.White.copy(alpha = 0.12f),
            start = Offset(0f, midY),
            end = Offset(width, midY),
            strokeWidth = 1.dp.toPx()
        )

        val totalPoints = 10
        val points = mutableListOf<Offset>()

        for (i in 0 until totalPoints) {
            val x = (i.toFloat() / (totalPoints - 1).toFloat()) * width
            var gain = eqBands.getOrElse(i) { 0f }
            if (i < 3) gain += bassBoost * (1f - i * 0.3f)
            if (i > 6) gain += trebleBoost * ((i - 6) * 0.33f)
            
            // Map -12dB..+12dB to height..0
            val normalized = (gain / 12f).coerceIn(-1.5f, 1.5f)
            val y = midY - (normalized * (height * 0.42f))
            points.add(Offset(x, y))
        }

        val path = Path()
        val fillPath = Path()

        if (points.isNotEmpty()) {
            path.moveTo(points[0].x, points[0].y)
            fillPath.moveTo(points[0].x, midY)
            fillPath.lineTo(points[0].x, points[0].y)

            for (i in 0 until points.size - 1) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val cx = (p0.x + p1.x) / 2f
                path.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                fillPath.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
            }

            fillPath.lineTo(points.last().x, midY)
            fillPath.close()

            // Draw subtle glowing fill
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFB388FF).copy(alpha = 0.35f),
                        Color(0xFF00E5FF).copy(alpha = 0.05f)
                    ),
                    startY = 0f,
                    endY = height
                )
            )

            // Draw main EQ response line
            drawPath(
                path = path,
                brush = Brush.horizontalGradient(
                    listOf(Color(0xFF00E5FF), Color(0xFFB388FF), Color(0xFFFF4081))
                ),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw frequency control dots
            points.forEach { pt ->
                drawCircle(
                    color = Color.White,
                    radius = 3.dp.toPx(),
                    center = pt
                )
                drawCircle(
                    color = Color(0xFFB388FF),
                    radius = 1.8.dp.toPx(),
                    center = pt
                )
            }
        }
    }
}

/**
 * Individual EQ band slider row with dB readout and center notch
 */
@Composable
private fun EqBandSliderRow(
    frequencyLabel: String,
    gainDb: Float,
    onGainChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label on left
        Text(
            text = frequencyLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.width(46.dp)
        )

        // Center Slider
        Slider(
            value = gainDb,
            onValueChange = { onGainChange((it * 10f).roundToInt() / 10f) },
            valueRange = -12f..12f,
            colors = SliderDefaults.colors(
                thumbColor = if (gainDb != 0f) Color(0xFFB388FF) else Color.White,
                activeTrackColor = Color(0xFFB388FF),
                inactiveTrackColor = Color(0xFF2A2A38)
            ),
            modifier = Modifier.weight(1f)
        )

        // Value on right
        Text(
            text = "${if (gainDb > 0) "+" else ""}${String.format("%.1f", gainDb)} dB",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (gainDb > 0) Color(0xFF00E5FF) else if (gainDb < 0) Color(0xFFFF5252) else Color.Gray,
            modifier = Modifier.width(52.dp),
            textAlign = TextAlign.End
        )
    }
}

/**
 * 2. VOICE & CLARITY SECTION
 */
@Composable
private fun VoiceClaritySection(config: AudioEnhancementConfig) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Voice Stabilizer & Leveler
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF181822)),
            border = BorderStroke(1.dp, Color(0xFF2A2A38)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Voice Stabilizer & Leveler",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Maintains smooth dialogue volume; boosts faint whispers & clamps harsh sudden shouts",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = config.voiceStabilizerEnabled,
                        onCheckedChange = { AudioEnhancementEngine.setVoiceStabilizerEnabled(it) }
                    )
                }

                AnimatedVisibility(visible = config.voiceStabilizerEnabled) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Whisper Boost: +${config.whisperBoostLimitDb.roundToInt()} dB",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF00E676),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = config.whisperBoostLimitDb,
                            onValueChange = { AudioEnhancementEngine.setWhisperBoostLimitDb(it) },
                            valueRange = 0f..18f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00E676),
                                activeTrackColor = Color(0xFF00E676)
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Explosion / Shout Clamp: ${config.explosionClampLimitDb.roundToInt()} dB",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFF5252),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = config.explosionClampLimitDb,
                            onValueChange = { AudioEnhancementEngine.setExplosionClampLimitDb(it) },
                            valueRange = -18f..0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFFF5252),
                                activeTrackColor = Color(0xFFFF5252)
                            )
                        )
                    }
                }
            }
        }

        // Dialogue Boost & Speech Clarity
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF181822)),
            border = BorderStroke(1.dp, Color(0xFF2A2A38)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Dialogue Boost & Speech Clarity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Highlights vocal center frequencies for crystal clear dialogue over loud background music",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DialogueBoostMode.values().forEach { mode ->
                        val selected = config.dialogueBoostMode == mode
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) Color(0xFF00E5FF) else Color(0xFF252533),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { AudioEnhancementEngine.setDialogueBoostMode(mode) }
                        ) {
                            Text(
                                text = mode.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 3. DYNAMICS & DRC SECTION
 */
@Composable
private fun DynamicsSection(config: AudioEnhancementConfig, meters: AudioMeterState) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Loudness Normalization
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF181822)),
            border = BorderStroke(1.dp, Color(0xFF2A2A38)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Loudness Normalization",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Maintains consistent volume across videos & different providers",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = config.loudnessNormalizationEnabled,
                        onCheckedChange = { AudioEnhancementEngine.setLoudnessNormalizationEnabled(it) }
                    )
                }

                AnimatedVisibility(visible = config.loudnessNormalizationEnabled) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Target Loudness: ${config.targetLufs.roundToInt()} LUFS",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFB388FF),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = config.targetLufs,
                            onValueChange = { AudioEnhancementEngine.setTargetLufs(it) },
                            valueRange = -24f..-12f,
                            steps = 11,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFB388FF),
                                activeTrackColor = Color(0xFFB388FF)
                            )
                        )
                    }
                }
            }
        }

        // Dynamic Range Compression (DRC)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF181822)),
            border = BorderStroke(1.dp, Color(0xFF2A2A38)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Dynamic Range Compression (DRC)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Narrows volume extremes for comfortable listening in noisy environments",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DynamicRangeCompressionMode.values().take(4).forEach { mode ->
                        val selected = config.drcMode == mode
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) Color(0xFFB388FF) else Color(0xFF252533),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { AudioEnhancementEngine.setDrcMode(mode) }
                        ) {
                            Text(
                                text = mode.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }

        // Channel Downmixing Mode
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF181822)),
            border = BorderStroke(1.dp, Color(0xFF2A2A38)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Channel Matrix & Downmixing",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "ITU-R BS.775 5.1/7.1 surround downmixing matrix preserving speech center channel",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))

                ChannelMode.values().forEach { mode ->
                    val selected = config.channelMode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { AudioEnhancementEngine.setChannelMode(mode) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = mode.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) Color(0xFF00E5FF) else Color.White,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 4. DSP PRESETS SECTION
 */
@Composable
private fun DspPresetsSection(config: AudioEnhancementConfig) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Engine Audio Presets",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.7f),
            letterSpacing = 1.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetCardButton(
                preset = AudioPreset.VOICE_STABILIZER,
                icon = Icons.Default.GraphicEq,
                selectedPreset = config.selectedPreset,
                modifier = Modifier.weight(1f)
            ) { AudioEnhancementEngine.setPreset(AudioPreset.VOICE_STABILIZER) }

            PresetCardButton(
                preset = AudioPreset.NIGHT,
                icon = Icons.Default.NightsStay,
                selectedPreset = config.selectedPreset,
                modifier = Modifier.weight(1f)
            ) { AudioEnhancementEngine.setPreset(AudioPreset.NIGHT) }

            PresetCardButton(
                preset = AudioPreset.HEADPHONE,
                icon = Icons.Default.Headphones,
                selectedPreset = config.selectedPreset,
                modifier = Modifier.weight(1f)
            ) { AudioEnhancementEngine.setPreset(AudioPreset.HEADPHONE) }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetCardButton(
                preset = AudioPreset.CINEMA,
                icon = Icons.Default.Movie,
                selectedPreset = config.selectedPreset,
                modifier = Modifier.weight(1f)
            ) { AudioEnhancementEngine.setPreset(AudioPreset.CINEMA) }

            PresetCardButton(
                preset = AudioPreset.ANIME_VOCAL,
                icon = Icons.Default.AutoAwesome,
                selectedPreset = config.selectedPreset,
                modifier = Modifier.weight(1f)
            ) { AudioEnhancementEngine.setPreset(AudioPreset.ANIME_VOCAL) }
        }
    }
}

@Composable
private fun PresetCardButton(
    preset: AudioPreset,
    icon: ImageVector,
    selectedPreset: AudioPreset,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val selected = selectedPreset == preset
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color(0xFFB388FF).copy(alpha = 0.25f) else Color(0xFF1E1E28),
        border = BorderStroke(
            1.dp,
            if (selected) Color(0xFFB388FF) else Color(0xFF2A2A3A)
        ),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = preset.displayName,
                tint = if (selected) Color(0xFFB388FF) else Color.White,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = preset.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) Color(0xFFB388FF) else Color.White,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                fontSize = 11.sp
            )
        }
    }
}
