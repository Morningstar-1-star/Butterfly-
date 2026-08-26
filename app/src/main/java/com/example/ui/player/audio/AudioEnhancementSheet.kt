package com.example.ui.player.audio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioEnhancementSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val config by AudioEnhancementEngine.config.collectAsState()
    val meters by AudioEnhancementEngine.meterState.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = Color(0xFF141419),
        contentColor = Color.White,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // SHEET HEADER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
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
                            uncheckedTrackColor = Color(0xFF333340)
                        )
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { AudioEnhancementEngine.resetToDefaults() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 1. LIVE DSP METERS & STATUS BOX
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E1E28),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E2E3E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                text = if (config.isEnabled) "DSP Pipeline: Active (Media3)" else "DSP Pipeline: Bypassed",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (config.isEnabled) Color(0xFF00E676) else Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "${meters.sampleRate} Hz • ${if (meters.channelCount == 1) "Mono" else if (meters.channelCount == 2) "Stereo" else "${meters.channelCount}.1 Surround"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.LightGray
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Input / Output Level Meters
                    MeterBarRow(label = "IN", levelDb = meters.inDb, barColor = Color(0xFF00E5FF))
                    Spacer(modifier = Modifier.height(6.dp))
                    MeterBarRow(label = "OUT", levelDb = meters.outDb, barColor = Color(0xFF7C4DFF))

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Voice Leveler Gain: ${if (meters.gainLevelerDb >= 0) "+" else ""}${String.format("%.1f", meters.gainLevelerDb)} dB",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF00E5FF),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. DSP PRESETS SELECTOR
            Text(
                text = "DSP Presets",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

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

            Spacer(modifier = Modifier.height(8.dp))

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

            Spacer(modifier = Modifier.height(16.dp))

            // 3. VOICE STABILIZER & LEVELER
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A24)),
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
                                text = "Keeps conversations steady; boosts quiet whispers and clamps down sudden shouts & explosions",
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
                                    text = "Whisper / Low Voice Boost Limit: +${config.whisperBoostLimitDb.roundToInt()} dB",
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
                                    text = "Loud Shout / Explosion Clamp Limit: ${config.explosionClampLimitDb.roundToInt()} dB",
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

            Spacer(modifier = Modifier.height(12.dp))

            // 4. LOUDNESS NORMALIZATION
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A24)),
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
                                text = "Matches consistent volume level across videos and providers",
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

            Spacer(modifier = Modifier.height(12.dp))

            // 5. DYNAMIC RANGE COMPRESSION (DRC)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A24)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Dynamic Range Compression",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Reduces extreme volume gaps between whispers and explosions",
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

            Spacer(modifier = Modifier.height(12.dp))

            // 6. DIALOGUE BOOST & SPEECH CLARITY
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A24)),
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
                        text = "Enhances vocal frequencies and extracts speech from background noise",
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

            Spacer(modifier = Modifier.height(12.dp))

            // 7. TONE EQUALIZER
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A24)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Tone Equalizer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Bass Gain: ${config.bassGainDb.roundToInt()} dB",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                    Slider(
                        value = config.bassGainDb,
                        onValueChange = { AudioEnhancementEngine.setBassGainDb(it) },
                        valueRange = -12f..12f
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Treble Gain: ${config.trebleGainDb.roundToInt()} dB",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                    Slider(
                        value = config.trebleGainDb,
                        onValueChange = { AudioEnhancementEngine.setTrebleGainDb(it) },
                        valueRange = -12f..12f
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 8. CHANNEL DOWNMIXING MODE
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A24)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Channel Downmixing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Surround to stereo matrixing using ITU-R BS.775 speech weights",
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

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MeterBarRow(
    label: String,
    levelDb: Float,
    barColor: Color
) {
    val fillFraction = ((levelDb + 60f) / 60f).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(targetValue = fillFraction, label = "meter")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(32.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF101018))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedFraction)
                    .background(barColor)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${levelDb.roundToInt()} dB",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.width(42.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun PresetCardButton(
    preset: AudioPreset,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selectedPreset: AudioPreset,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val selected = selectedPreset == preset
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color(0xFFB388FF).copy(alpha = 0.25f) else Color(0xFF1E1E28),
        border = androidx.compose.foundation.BorderStroke(
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
