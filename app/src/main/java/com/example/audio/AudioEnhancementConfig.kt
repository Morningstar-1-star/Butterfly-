package com.example.audio

/**
 * Presets for audio post-processing and voice leveling.
 */
enum class AudioPreset(val displayName: String, val description: String) {
    CUSTOM("Custom", "Customized DSP chain"),
    VOICE_STABILIZER("Voice Stabilizer", "Keeps dialogue steady; boosts whispers and clamps sudden loud spikes"),
    NIGHT_MODE("Night Mode", "Quiet listening: heavy dynamic compression and clear dialogue for late-night viewing"),
    HEADPHONE_MODE("Headphone Pro", "Balanced spatial peaks, reduced listening fatigue, and crisp vocal clarity"),
    CINEMA_ACTION("Cinema Action", "Tames loud explosions and gunshots while keeping conversations clear"),
    ANIME_ENHANCED("Anime Vocal Clear", "Brightens voice tracks, clarifies high frequencies, and evens out screams/whispers")
}

/**
 * Dynamic Range Compression (DRC) strength modes.
 */
enum class DynamicRangeMode(val displayName: String, val thresholdDb: Float, val ratio: Float) {
    OFF("Off", 0f, 1.0f),
    LOW("Low", -18f, 1.8f),
    MEDIUM("Medium", -24f, 3.2f),
    STRONG("Strong", -30f, 5.5f)
}

/**
 * Dialogue & Speech Intelligibility Boost levels.
 */
enum class DialogueBoostMode(val displayName: String, val gainDb: Float, val vocalQ: Float) {
    OFF("Off", 0f, 1.0f),
    SUBTLE("Subtle (+3dB)", 3.0f, 1.2f),
    CLEAR("Clear (+6dB)", 6.0f, 1.5f),
    VOCAL_MAX("Vocal Max (+9dB)", 9.0f, 1.8f)
}

/**
 * Channel downmixing and routing modes.
 */
enum class ChannelDownmixMode(val displayName: String) {
    AUTO("Auto (Match Source)"),
    STEREO("Stereo (2.0)"),
    MONO("Mono (Summed)"),
    SURROUND_TO_STEREO_ITU("5.1/7.1 to Stereo (ITU-R BS.775)")
}

/**
 * Voice Stabilizer (Adaptive Automatic Gain Control & Clamp Limits).
 * Prevents voices from getting too quiet (whispers) or too loud (screaming/explosions).
 *
 * @param enabled Whether voice stabilization is active.
 * @param minGainDb Maximum volume attenuation for loud spikes/shouts (e.g. -12dB).
 * @param maxGainDb Maximum volume amplification for low whispers (e.g. +14dB).
 * @param targetSpeechDb Target perceived speech loudness in dBFS (e.g. -18dBFS).
 * @param responseSpeed Attack/release speed (0.1 = smooth slow transition, 1.0 = rapid).
 */
data class VoiceStabilizerConfig(
    val enabled: Boolean = true,
    val minGainDb: Float = -10f,
    val maxGainDb: Float = 12f,
    val targetSpeechDb: Float = -18f,
    val responseSpeed: Float = 0.6f
)

/**
 * Comprehensive Audio Enhancement Configuration for Butterfly.
 */
data class AudioEnhancementConfig(
    val isEnabled: Boolean = true,
    val preset: AudioPreset = AudioPreset.VOICE_STABILIZER,
    val loudnessNormalization: Boolean = true,
    val targetLoudnessLufs: Float = -16.0f, // Configurable target: -24 to -12 LUFS
    val dynamicRangeMode: DynamicRangeMode = DynamicRangeMode.MEDIUM,
    val voiceStabilizer: VoiceStabilizerConfig = VoiceStabilizerConfig(),
    val dialogueBoost: DialogueBoostMode = DialogueBoostMode.CLEAR,
    val limiterEnabled: Boolean = true,
    val limiterCeilingDb: Float = -0.5f,
    val volumeBoostPercent: Int = 100, // 100% to 300%
    val bassGainDb: Float = 0f, // -12dB to +12dB
    val trebleGainDb: Float = 0f, // -12dB to +12dB
    val nightMode: Boolean = false,
    val headphoneMode: Boolean = false,
    val channelMode: ChannelDownmixMode = ChannelDownmixMode.AUTO
) {
    fun hasActiveProcessing(): Boolean {
        if (!isEnabled) return false
        return loudnessNormalization ||
                dynamicRangeMode != DynamicRangeMode.OFF ||
                voiceStabilizer.enabled ||
                dialogueBoost != DialogueBoostMode.OFF ||
                limiterEnabled ||
                volumeBoostPercent != 100 ||
                bassGainDb != 0f ||
                trebleGainDb != 0f ||
                nightMode ||
                headphoneMode
    }
}

/**
 * Live audio DSP telemetry metrics.
 */
data class AudioTelemetryState(
    val inputRmsDb: Float = -60f,
    val outputRmsDb: Float = -60f,
    val currentGainReductionDb: Float = 0f,
    val appliedVoiceGainDb: Float = 0f,
    val voiceActive: Boolean = false,
    val channels: Int = 2,
    val sampleRate: Int = 44100
)
