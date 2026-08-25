package com.example.ui.player

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioEffectsSettingsSheet(
    onDismiss: () -> Unit
) {
    val config by AudioEnhancementEngine.config.collectAsState()
    val telemetry by AudioEnhancementEngine.telemetry.collectAsState()
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF101018),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp)
                .navigationBarsPadding()
                .verticalScroll(scrollState)
        ) {
            // 1. Header: Title, Master Switch, Reset, Close
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
                            checkedTrackColor = Color(0xFF7C4DFF),
                            uncheckedThumbColor = Color.LightGray,
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
                        onClick = { AudioEnhancementEngine.resetToDefaults() },
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

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Real-time Audio DSP Telemetry & VU Meters
            Surface(
                color = Color(0xFF1B1B26),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF7C4DFF).copy(alpha = 0.3f)),
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
                                    .background(if (config.isEnabled) Color(0xFF00E676) else Color.Gray)
                            )
                            Text(
                                text = if (config.isEnabled) "DSP Pipeline: Active (Media3)" else "DSP Pipeline: Bypass",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (config.isEnabled) Color(0xFF00E676) else Color.Gray
                            )
                        }

                        Text(
                            text = "${telemetry.sampleRate} Hz • ${if (telemetry.channels >= 6) "5.1 Surround" else if (telemetry.channels == 1) "Mono" else "Stereo"}",
                            fontSize = 10.sp,
                            color = Color.LightGray.copy(alpha = 0.8f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Input & Output VU Bars
                    AudioLevelMeter(
                        label = "IN",
                        dbLevel = telemetry.inputRmsDb,
                        meterColor = Color(0xFF00E5FF)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    AudioLevelMeter(
                        label = "OUT",
                        dbLevel = telemetry.outputRmsDb,
                        meterColor = Color(0xFF7C4DFF)
                    )

                    if (telemetry.appliedVoiceGainDb != 0f || telemetry.currentGainReductionDb != 0f) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Voice Leveler Gain: ${if (telemetry.appliedVoiceGainDb > 0) "+" else ""}${String.format("%.1f", telemetry.appliedVoiceGainDb)} dB",
                                fontSize = 11.sp,
                                color = if (telemetry.appliedVoiceGainDb > 0) Color(0xFF00E676) else Color(0xFFFFB74D),
                                fontWeight = FontWeight.SemiBold
                            )
                            if (telemetry.currentGainReductionDb < 0f) {
                                Text(
                                    text = "DRC Limit: ${String.format("%.1f", telemetry.currentGainReductionDb)} dB",
                                    fontSize = 11.sp,
                                    color = Color(0xFFFF5252),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Audio Presets Quick Selector
            Text(
                text = "DSP Presets",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))

            val presets = listOf(
                AudioPreset.VOICE_STABILIZER to Icons.Outlined.RecordVoiceOver,
                AudioPreset.NIGHT_MODE to Icons.Outlined.Bedtime,
                AudioPreset.HEADPHONE_MODE to Icons.Outlined.Headphones,
                AudioPreset.CINEMA_ACTION to Icons.Outlined.Movie,
                AudioPreset.ANIME_ENHANCED to Icons.Outlined.AutoAwesome
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.take(3).forEach { (preset, icon) ->
                    val isSelected = config.preset == preset
                    Surface(
                        onClick = { AudioEnhancementEngine.applyPreset(preset) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Color(0xFF7C4DFF).copy(alpha = 0.25f) else Color(0xFF1C1C24),
                        border = if (isSelected) BorderStroke(1.dp, Color(0xFF7C4DFF)) else BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = preset.displayName,
                                tint = if (isSelected) Color(0xFFB388FF) else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = preset.displayName,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFFB388FF) else Color.White,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.drop(3).forEach { (preset, icon) ->
                    val isSelected = config.preset == preset
                    Surface(
                        onClick = { AudioEnhancementEngine.applyPreset(preset) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Color(0xFF7C4DFF).copy(alpha = 0.25f) else Color(0xFF1C1C24),
                        border = if (isSelected) BorderStroke(1.dp, Color(0xFF7C4DFF)) else BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = preset.displayName,
                                tint = if (isSelected) Color(0xFFB388FF) else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = preset.displayName,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFFB388FF) else Color.White,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 4. Voice Stabilizer & Leveler (User requested: bounds for low whispers and loud shouts)
            Surface(
                color = Color(0xFF1A1A24),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
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
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Keeps conversations steady: boosts quiet whispers and clamps down sudden shouts & explosions",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray.copy(alpha = 0.8f)
                            )
                        }
                        Switch(
                            checked = config.voiceStabilizer.enabled,
                            onCheckedChange = { AudioEnhancementEngine.setVoiceStabilizerEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF7C4DFF),
                                uncheckedThumbColor = Color.LightGray,
                                uncheckedTrackColor = Color(0xFF2A2A38)
                            )
                        )
                    }

                    if (config.voiceStabilizer.enabled) {
                        Spacer(modifier = Modifier.height(12.dp))

                        // Max Whisper Boost Slider
                        Text(
                            text = "Whisper / Low Voice Boost Limit: +${config.voiceStabilizer.maxGainDb.toInt()} dB",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF00E676)
                        )
                        Slider(
                            value = config.voiceStabilizer.maxGainDb,
                            onValueChange = {
                                AudioEnhancementEngine.setVoiceStabilizerLimits(
                                    minGainDb = config.voiceStabilizer.minGainDb,
                                    maxGainDb = it
                                )
                            },
                            valueRange = 0f..24f,
                            steps = 23,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00E676),
                                activeTrackColor = Color(0xFF00E676)
                            )
                        )

                        // Max Shout / Explosion Clamp Slider
                        Text(
                            text = "Loud Shout / Explosion Clamp Limit: ${config.voiceStabilizer.minGainDb.toInt()} dB",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFF5252)
                        )
                        Slider(
                            value = config.voiceStabilizer.minGainDb,
                            onValueChange = {
                                AudioEnhancementEngine.setVoiceStabilizerLimits(
                                    minGainDb = it,
                                    maxGainDb = config.voiceStabilizer.maxGainDb
                                )
                            },
                            valueRange = -24f..0f,
                            steps = 23,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFFF5252),
                                activeTrackColor = Color(0xFFFF5252)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Volume Normalization & Target Loudness
            Surface(
                color = Color(0xFF1A1A24),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
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
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Matches consistent volume level across videos and providers",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray.copy(alpha = 0.8f)
                            )
                        }
                        Switch(
                            checked = config.loudnessNormalization,
                            onCheckedChange = { AudioEnhancementEngine.setLoudnessNormalization(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF7C4DFF)
                            )
                        )
                    }

                    if (config.loudnessNormalization) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Target Loudness: ${config.targetLoudnessLufs.toInt()} LUFS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFB388FF)
                        )
                        Slider(
                            value = config.targetLoudnessLufs,
                            onValueChange = { AudioEnhancementEngine.setTargetLoudness(it) },
                            valueRange = -24f..-10f,
                            steps = 13,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFB388FF),
                                activeTrackColor = Color(0xFF7C4DFF)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 6. Dynamic Range Compression (DRC)
            Surface(
                color = Color(0xFF1A1A24),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Dynamic Range Compression",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Reduces extreme volume gaps between whispers and explosions",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DynamicRangeMode.values().forEach { mode ->
                            val isSelected = config.dynamicRangeMode == mode
                            Surface(
                                onClick = { AudioEnhancementEngine.setDynamicRangeMode(mode) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color(0xFF7C4DFF).copy(alpha = 0.3f) else Color(0xFF242432),
                                border = if (isSelected) BorderStroke(1.dp, Color(0xFF7C4DFF)) else null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode.displayName,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color(0xFFB388FF) else Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 7. Dialogue Boost & Vocal Intelligibility
            Surface(
                color = Color(0xFF1A1A24),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Dialogue Boost & Speech Clarity",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Enhances vocal frequencies and extracts speech from background noise",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DialogueBoostMode.values().forEach { mode ->
                            val isSelected = config.dialogueBoost == mode
                            Surface(
                                onClick = { AudioEnhancementEngine.setDialogueBoost(mode) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.3f) else Color(0xFF242432),
                                border = if (isSelected) BorderStroke(1.dp, Color(0xFF00E5FF)) else null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode.displayName,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color(0xFF00E5FF) else Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 8. Tone Controls (Bass & Treble)
            Surface(
                color = Color(0xFF1A1A24),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Tone Equalizer",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Bass Gain: ${if (config.bassGainDb > 0) "+" else ""}${config.bassGainDb.toInt()} dB",
                        fontSize = 12.sp,
                        color = Color.White
                    )
                    Slider(
                        value = config.bassGainDb,
                        onValueChange = { AudioEnhancementEngine.setBassGain(it) },
                        valueRange = -12f..12f,
                        steps = 23,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF7C4DFF), activeTrackColor = Color(0xFF7C4DFF))
                    )

                    Text(
                        text = "Treble Gain: ${if (config.trebleGainDb > 0) "+" else ""}${config.trebleGainDb.toInt()} dB",
                        fontSize = 12.sp,
                        color = Color.White
                    )
                    Slider(
                        value = config.trebleGainDb,
                        onValueChange = { AudioEnhancementEngine.setTrebleGain(it) },
                        valueRange = -12f..12f,
                        steps = 23,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 9. Channel Downmixing Mode
            Surface(
                color = Color(0xFF1A1A24),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Channel Downmixing",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Surround to stereo matrixing using ITU-R BS.775 speech weights",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    ChannelDownmixMode.values().forEach { mode ->
                        val isSelected = config.channelMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { AudioEnhancementEngine.setChannelMode(mode) }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = mode.displayName,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF7C4DFF) else Color.White
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color(0xFF7C4DFF),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioLevelMeter(
    label: String,
    dbLevel: Float,
    meterColor: Color
) {
    val fraction = ((dbLevel + 60f) / 60f).coerceIn(0f, 1f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.width(28.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(meterColor.copy(alpha = 0.6f), meterColor)
                        )
                    )
            )
        }
        Text(
            text = "${dbLevel.toInt()} dB",
            fontSize = 10.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = Color.LightGray,
            modifier = Modifier.width(42.dp)
        )
    }
}
