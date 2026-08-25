package com.example.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton engine managing audio enhancement state, presets, and DSP pipeline lifecycle.
 */
object AudioEnhancementEngine {

    val processor = AudioEnhancementProcessor()

    private val _config = MutableStateFlow(AudioEnhancementConfig())
    val config: StateFlow<AudioEnhancementConfig> = _config.asStateFlow()

    private val _telemetry = MutableStateFlow(AudioTelemetryState())
    val telemetry: StateFlow<AudioTelemetryState> = _telemetry.asStateFlow()

    init {
        processor.config = _config.value
        processor.onTelemetryUpdated = { liveTelemetry ->
            _telemetry.value = liveTelemetry
        }
    }

    fun setEnabled(enabled: Boolean) {
        updateConfig { it.copy(isEnabled = enabled) }
    }

    fun toggleEnabled() {
        setEnabled(!_config.value.isEnabled)
    }

    fun applyPreset(preset: AudioPreset) {
        val newConfig = when (preset) {
            AudioPreset.CUSTOM -> _config.value.copy(preset = AudioPreset.CUSTOM)
            AudioPreset.VOICE_STABILIZER -> AudioEnhancementConfig(
                isEnabled = true,
                preset = AudioPreset.VOICE_STABILIZER,
                loudnessNormalization = true,
                targetLoudnessLufs = -16.0f,
                dynamicRangeMode = DynamicRangeMode.MEDIUM,
                voiceStabilizer = VoiceStabilizerConfig(
                    enabled = true,
                    minGainDb = -10f,
                    maxGainDb = 12f,
                    targetSpeechDb = -18f,
                    responseSpeed = 0.6f
                ),
                dialogueBoost = DialogueBoostMode.CLEAR,
                limiterEnabled = true,
                limiterCeilingDb = -0.5f,
                volumeBoostPercent = 100,
                bassGainDb = 0f,
                trebleGainDb = 1.5f,
                nightMode = false,
                headphoneMode = false
            )
            AudioPreset.NIGHT_MODE -> AudioEnhancementConfig(
                isEnabled = true,
                preset = AudioPreset.NIGHT_MODE,
                loudnessNormalization = true,
                targetLoudnessLufs = -20.0f,
                dynamicRangeMode = DynamicRangeMode.STRONG,
                voiceStabilizer = VoiceStabilizerConfig(
                    enabled = true,
                    minGainDb = -16f,
                    maxGainDb = 15f,
                    targetSpeechDb = -20f,
                    responseSpeed = 0.8f
                ),
                dialogueBoost = DialogueBoostMode.VOCAL_MAX,
                limiterEnabled = true,
                limiterCeilingDb = -1.5f,
                volumeBoostPercent = 100,
                bassGainDb = -4.0f,
                trebleGainDb = 0f,
                nightMode = true,
                headphoneMode = false
            )
            AudioPreset.HEADPHONE_MODE -> AudioEnhancementConfig(
                isEnabled = true,
                preset = AudioPreset.HEADPHONE_MODE,
                loudnessNormalization = true,
                targetLoudnessLufs = -18.0f,
                dynamicRangeMode = DynamicRangeMode.LOW,
                voiceStabilizer = VoiceStabilizerConfig(
                    enabled = true,
                    minGainDb = -8f,
                    maxGainDb = 8f,
                    targetSpeechDb = -18f,
                    responseSpeed = 0.5f
                ),
                dialogueBoost = DialogueBoostMode.CLEAR,
                limiterEnabled = true,
                limiterCeilingDb = -0.8f,
                volumeBoostPercent = 100,
                bassGainDb = 2.0f,
                trebleGainDb = 1.0f,
                nightMode = false,
                headphoneMode = true
            )
            AudioPreset.CINEMA_ACTION -> AudioEnhancementConfig(
                isEnabled = true,
                preset = AudioPreset.CINEMA_ACTION,
                loudnessNormalization = true,
                targetLoudnessLufs = -16.0f,
                dynamicRangeMode = DynamicRangeMode.STRONG,
                voiceStabilizer = VoiceStabilizerConfig(
                    enabled = true,
                    minGainDb = -14f,
                    maxGainDb = 10f,
                    targetSpeechDb = -18f,
                    responseSpeed = 0.7f
                ),
                dialogueBoost = DialogueBoostMode.CLEAR,
                limiterEnabled = true,
                limiterCeilingDb = -0.5f,
                volumeBoostPercent = 100,
                bassGainDb = 1.5f,
                trebleGainDb = 0f,
                nightMode = false,
                headphoneMode = false
            )
            AudioPreset.ANIME_ENHANCED -> AudioEnhancementConfig(
                isEnabled = true,
                preset = AudioPreset.ANIME_ENHANCED,
                loudnessNormalization = true,
                targetLoudnessLufs = -15.0f,
                dynamicRangeMode = DynamicRangeMode.MEDIUM,
                voiceStabilizer = VoiceStabilizerConfig(
                    enabled = true,
                    minGainDb = -12f,
                    maxGainDb = 14f,
                    targetSpeechDb = -17f,
                    responseSpeed = 0.65f
                ),
                dialogueBoost = DialogueBoostMode.VOCAL_MAX,
                limiterEnabled = true,
                limiterCeilingDb = -0.3f,
                volumeBoostPercent = 100,
                bassGainDb = 0f,
                trebleGainDb = 3.0f,
                nightMode = false,
                headphoneMode = false
            )
        }
        _config.value = newConfig
        processor.config = newConfig
    }

    fun setLoudnessNormalization(enabled: Boolean) {
        updateConfig { it.copy(loudnessNormalization = enabled, preset = AudioPreset.CUSTOM) }
    }

    fun setTargetLoudness(targetLufs: Float) {
        updateConfig { it.copy(targetLoudnessLufs = targetLufs, preset = AudioPreset.CUSTOM) }
    }

    fun setDynamicRangeMode(mode: DynamicRangeMode) {
        updateConfig { it.copy(dynamicRangeMode = mode, preset = AudioPreset.CUSTOM) }
    }

    fun setDialogueBoost(mode: DialogueBoostMode) {
        updateConfig { it.copy(dialogueBoost = mode, preset = AudioPreset.CUSTOM) }
    }

    fun setVoiceStabilizerEnabled(enabled: Boolean) {
        updateConfig {
            it.copy(
                voiceStabilizer = it.voiceStabilizer.copy(enabled = enabled),
                preset = AudioPreset.CUSTOM
            )
        }
    }

    fun setVoiceStabilizerLimits(minGainDb: Float, maxGainDb: Float) {
        updateConfig {
            it.copy(
                voiceStabilizer = it.voiceStabilizer.copy(
                    minGainDb = minGainDb,
                    maxGainDb = maxGainDb
                ),
                preset = AudioPreset.CUSTOM
            )
        }
    }

    fun setVoiceStabilizerTarget(targetDb: Float) {
        updateConfig {
            it.copy(
                voiceStabilizer = it.voiceStabilizer.copy(targetSpeechDb = targetDb),
                preset = AudioPreset.CUSTOM
            )
        }
    }

    fun setLimiterEnabled(enabled: Boolean) {
        updateConfig { it.copy(limiterEnabled = enabled, preset = AudioPreset.CUSTOM) }
    }

    fun setVolumeBoost(percent: Int) {
        updateConfig { it.copy(volumeBoostPercent = percent.coerceIn(100, 300)) }
    }

    fun setBassGain(gainDb: Float) {
        updateConfig { it.copy(bassGainDb = gainDb.coerceIn(-12f, 12f), preset = AudioPreset.CUSTOM) }
    }

    fun setTrebleGain(gainDb: Float) {
        updateConfig { it.copy(trebleGainDb = gainDb.coerceIn(-12f, 12f), preset = AudioPreset.CUSTOM) }
    }

    fun setNightMode(enabled: Boolean) {
        if (enabled) {
            applyPreset(AudioPreset.NIGHT_MODE)
        } else {
            applyPreset(AudioPreset.VOICE_STABILIZER)
        }
    }

    fun setHeadphoneMode(enabled: Boolean) {
        if (enabled) {
            applyPreset(AudioPreset.HEADPHONE_MODE)
        } else {
            applyPreset(AudioPreset.VOICE_STABILIZER)
        }
    }

    fun setChannelMode(mode: ChannelDownmixMode) {
        updateConfig { it.copy(channelMode = mode) }
    }

    fun resetToDefaults() {
        applyPreset(AudioPreset.VOICE_STABILIZER)
    }

    fun updateConfig(block: (AudioEnhancementConfig) -> AudioEnhancementConfig) {
        val updated = block(_config.value)
        _config.value = updated
        processor.config = updated
    }
}
