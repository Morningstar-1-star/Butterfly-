package com.example.ui.player.audio

enum class AudioPreset(val displayName: String) {
    VOICE_STABILIZER("Voice Stabilizer"),
    NIGHT("Night Mode"),
    HEADPHONE("Headphone Pro"),
    CINEMA("Cinema Action"),
    ANIME_VOCAL("Anime Vocal Clear"),
    CUSTOM("Custom")
}

enum class DynamicRangeCompressionMode(
    val displayName: String,
    val ratio: Float,
    val thresholdDb: Float
) {
    OFF("Off", 1.0f, 0.0f),
    LOW("Low", 2.0f, -12.0f),
    MEDIUM("Medium", 4.0f, -18.0f),
    STRONG("Strong", 8.0f, -24.0f),
    NIGHT("Night Mode", 12.0f, -28.0f)
}

enum class DialogueBoostMode(val displayName: String, val gainDb: Float) {
    OFF("Off", 0f),
    SUBTLE("Subtle (+3dB)", 3f),
    CLEAR("Clear (+6dB)", 6f),
    VOCAL_MAX("Vocal Max (+9dB)", 9f)
}

enum class ChannelMode(val displayName: String) {
    AUTO("Auto (Match Source)"),
    STEREO("Stereo (2.0)"),
    MONO("Mono (Summed)"),
    DOWNMIX_5_1("5.1/7.1 to Stereo (ITU-R BS.775)")
}

data class AudioEnhancementConfig(
    val isEnabled: Boolean = true,
    val selectedPreset: AudioPreset = AudioPreset.VOICE_STABILIZER,
    val voiceStabilizerEnabled: Boolean = true,
    val whisperBoostLimitDb: Float = 12.0f,
    val explosionClampLimitDb: Float = -10.0f,
    val loudnessNormalizationEnabled: Boolean = true,
    val targetLufs: Float = -16.0f,
    val drcMode: DynamicRangeCompressionMode = DynamicRangeCompressionMode.MEDIUM,
    val drcThresholdDb: Float = -18.0f,
    val drcRatio: Float = 4.0f,
    val drcAttackMs: Float = 10.0f,
    val drcReleaseMs: Float = 100.0f,
    val drcKneeDb: Float = 6.0f,
    val drcMakeupGainDb: Float = 3.0f,
    val dialogueBoostMode: DialogueBoostMode = DialogueBoostMode.CLEAR,
    val dialogueBoostPercentage: Float = 50.0f,
    val limiterEnabled: Boolean = true,
    val limiterCeilingDb: Float = -1.0f,
    val bassGainDb: Float = 0.0f,
    val trebleGainDb: Float = 0.0f,
    val eq5BandsDb: FloatArray = floatArrayOf(0f, 0f, 0f, 0f, 0f),
    val channelMode: ChannelMode = ChannelMode.AUTO,
    val pitchCorrectionPreserved: Boolean = true
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioEnhancementConfig
        if (isEnabled != other.isEnabled) return false
        if (selectedPreset != other.selectedPreset) return false
        if (voiceStabilizerEnabled != other.voiceStabilizerEnabled) return false
        if (whisperBoostLimitDb != other.whisperBoostLimitDb) return false
        if (explosionClampLimitDb != other.explosionClampLimitDb) return false
        if (loudnessNormalizationEnabled != other.loudnessNormalizationEnabled) return false
        if (targetLufs != other.targetLufs) return false
        if (drcMode != other.drcMode) return false
        if (drcThresholdDb != other.drcThresholdDb) return false
        if (drcRatio != other.drcRatio) return false
        if (dialogueBoostMode != other.dialogueBoostMode) return false
        if (dialogueBoostPercentage != other.dialogueBoostPercentage) return false
        if (limiterEnabled != other.limiterEnabled) return false
        if (limiterCeilingDb != other.limiterCeilingDb) return false
        if (bassGainDb != other.bassGainDb) return false
        if (trebleGainDb != other.trebleGainDb) return false
        if (!eq5BandsDb.contentEquals(other.eq5BandsDb)) return false
        if (channelMode != other.channelMode) return false
        if (pitchCorrectionPreserved != other.pitchCorrectionPreserved) return false
        return true
    }

    override fun hashCode(): Int {
        var result = isEnabled.hashCode()
        result = 31 * result + selectedPreset.hashCode()
        result = 31 * result + voiceStabilizerEnabled.hashCode()
        result = 31 * result + whisperBoostLimitDb.hashCode()
        result = 31 * result + explosionClampLimitDb.hashCode()
        result = 31 * result + loudnessNormalizationEnabled.hashCode()
        result = 31 * result + targetLufs.hashCode()
        result = 31 * result + drcMode.hashCode()
        result = 31 * result + drcThresholdDb.hashCode()
        result = 31 * result + drcRatio.hashCode()
        result = 31 * result + dialogueBoostMode.hashCode()
        result = 31 * result + dialogueBoostPercentage.hashCode()
        result = 31 * result + limiterEnabled.hashCode()
        result = 31 * result + limiterCeilingDb.hashCode()
        result = 31 * result + bassGainDb.hashCode()
        result = 31 * result + trebleGainDb.hashCode()
        result = 31 * result + eq5BandsDb.contentHashCode()
        result = 31 * result + channelMode.hashCode()
        result = 31 * result + pitchCorrectionPreserved.hashCode()
        return result
    }
}

data class AudioMeterState(
    val inDb: Float = -60f,
    val outDb: Float = -60f,
    val gainLevelerDb: Float = 0f,
    val sampleRate: Int = 44100,
    val channelCount: Int = 2,
    val activeDsp: Boolean = true
)
