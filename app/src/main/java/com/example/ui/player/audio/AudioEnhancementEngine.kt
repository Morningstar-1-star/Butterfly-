package com.example.ui.player.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AudioEnhancementEngine {

    private val _config = MutableStateFlow(AudioEnhancementConfig())
    val config: StateFlow<AudioEnhancementConfig> = _config.asStateFlow()

    private val _meterState = MutableStateFlow(AudioMeterState())
    val meterState: StateFlow<AudioMeterState> = _meterState.asStateFlow()

    val processorInstance by lazy { AudioEnhancementProcessor() }

    fun getAudioProcessor(): AudioEnhancementProcessor {
        return processorInstance
    }

    fun setEnabled(enabled: Boolean) {
        _config.value = _config.value.copy(isEnabled = enabled)
    }

    fun setPreset(preset: AudioPreset) {
        val newConfig = AudioPresets.getPresetConfig(preset, _config.value)
        _config.value = newConfig
    }

    fun updateConfig(update: (AudioEnhancementConfig) -> AudioEnhancementConfig) {
        _config.value = update(_config.value).copy(selectedPreset = AudioPreset.CUSTOM)
    }

    fun setVoiceStabilizerEnabled(enabled: Boolean) {
        _config.value = _config.value.copy(voiceStabilizerEnabled = enabled)
    }

    fun setWhisperBoostLimitDb(db: Float) {
        _config.value = _config.value.copy(whisperBoostLimitDb = db)
    }

    fun setExplosionClampLimitDb(db: Float) {
        _config.value = _config.value.copy(explosionClampLimitDb = db)
    }

    fun setLoudnessNormalizationEnabled(enabled: Boolean) {
        _config.value = _config.value.copy(loudnessNormalizationEnabled = enabled)
    }

    fun setTargetLufs(lufs: Float) {
        _config.value = _config.value.copy(targetLufs = lufs)
    }

    fun setDrcMode(mode: DynamicRangeCompressionMode) {
        _config.value = _config.value.copy(drcMode = mode)
    }

    fun setDialogueBoostMode(mode: DialogueBoostMode) {
        _config.value = _config.value.copy(dialogueBoostMode = mode)
    }

    fun setDialogueBoostPercentage(pct: Float) {
        _config.value = _config.value.copy(dialogueBoostPercentage = pct)
    }

    fun setBassGainDb(db: Float) {
        _config.value = _config.value.copy(bassGainDb = db)
    }

    fun setTrebleGainDb(db: Float) {
        _config.value = _config.value.copy(trebleGainDb = db)
    }

    fun setChannelMode(mode: ChannelMode) {
        _config.value = _config.value.copy(channelMode = mode)
    }

    fun resetToDefaults() {
        _config.value = AudioPresets.getPresetConfig(AudioPreset.VOICE_STABILIZER, AudioEnhancementConfig())
    }

    fun updateMeters(
        inDb: Float,
        outDb: Float,
        gainLevelerDb: Float,
        sampleRate: Int,
        channelCount: Int,
        activeDsp: Boolean
    ) {
        _meterState.value = AudioMeterState(
            inDb = inDb,
            outDb = outDb,
            gainLevelerDb = gainLevelerDb,
            sampleRate = sampleRate,
            channelCount = channelCount,
            activeDsp = activeDsp
        )
    }
}
