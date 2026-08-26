package com.example.ui.player.audio

object AudioPresets {

    fun getPresetConfig(preset: AudioPreset, currentConfig: AudioEnhancementConfig): AudioEnhancementConfig {
        return when (preset) {
            AudioPreset.VOICE_STABILIZER -> currentConfig.copy(
                selectedPreset = AudioPreset.VOICE_STABILIZER,
                voiceStabilizerEnabled = true,
                whisperBoostLimitDb = 12.0f,
                explosionClampLimitDb = -10.0f,
                loudnessNormalizationEnabled = true,
                targetLufs = -16.0f,
                drcMode = DynamicRangeCompressionMode.MEDIUM,
                drcThresholdDb = -18.0f,
                drcRatio = 4.0f,
                dialogueBoostMode = DialogueBoostMode.CLEAR,
                dialogueBoostPercentage = 60.0f,
                limiterEnabled = true,
                limiterCeilingDb = -1.0f,
                bassGainDb = 0.0f,
                trebleGainDb = 0.0f
            )

            AudioPreset.NIGHT -> currentConfig.copy(
                selectedPreset = AudioPreset.NIGHT,
                voiceStabilizerEnabled = true,
                whisperBoostLimitDb = 15.0f,
                explosionClampLimitDb = -14.0f,
                loudnessNormalizationEnabled = true,
                targetLufs = -18.0f,
                drcMode = DynamicRangeCompressionMode.STRONG,
                drcThresholdDb = -24.0f,
                drcRatio = 8.0f,
                dialogueBoostMode = DialogueBoostMode.CLEAR,
                dialogueBoostPercentage = 75.0f,
                limiterEnabled = true,
                limiterCeilingDb = -2.0f,
                bassGainDb = -2.0f,
                trebleGainDb = -1.0f
            )

            AudioPreset.HEADPHONE -> currentConfig.copy(
                selectedPreset = AudioPreset.HEADPHONE,
                voiceStabilizerEnabled = false,
                loudnessNormalizationEnabled = true,
                targetLufs = -16.0f,
                drcMode = DynamicRangeCompressionMode.LOW,
                drcThresholdDb = -14.0f,
                drcRatio = 2.5f,
                dialogueBoostMode = DialogueBoostMode.SUBTLE,
                dialogueBoostPercentage = 30.0f,
                limiterEnabled = true,
                limiterCeilingDb = -1.0f,
                bassGainDb = 2.0f,
                trebleGainDb = 1.0f
            )

            AudioPreset.CINEMA -> currentConfig.copy(
                selectedPreset = AudioPreset.CINEMA,
                voiceStabilizerEnabled = false,
                loudnessNormalizationEnabled = false,
                targetLufs = -14.0f,
                drcMode = DynamicRangeCompressionMode.OFF,
                drcThresholdDb = 0.0f,
                drcRatio = 1.0f,
                dialogueBoostMode = DialogueBoostMode.OFF,
                dialogueBoostPercentage = 0.0f,
                limiterEnabled = true,
                limiterCeilingDb = -0.5f,
                bassGainDb = 3.0f,
                trebleGainDb = 2.0f
            )

            AudioPreset.ANIME_VOCAL -> currentConfig.copy(
                selectedPreset = AudioPreset.ANIME_VOCAL,
                voiceStabilizerEnabled = false,
                loudnessNormalizationEnabled = true,
                targetLufs = -15.0f,
                drcMode = DynamicRangeCompressionMode.LOW,
                drcThresholdDb = -16.0f,
                drcRatio = 3.0f,
                dialogueBoostMode = DialogueBoostMode.VOCAL_MAX,
                dialogueBoostPercentage = 90.0f,
                limiterEnabled = true,
                limiterCeilingDb = -1.0f,
                bassGainDb = 0.0f,
                trebleGainDb = 3.0f
            )

            AudioPreset.CUSTOM -> currentConfig.copy(selectedPreset = AudioPreset.CUSTOM)
        }
    }
}
