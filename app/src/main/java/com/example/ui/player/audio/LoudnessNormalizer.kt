package com.example.ui.player.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

class LoudnessNormalizer {

    private var currentGainLinear: Float = 1.0f
    private var smoothedGainLinear: Float = 1.0f
    private var lastMeasuredGainDb: Float = 0.0f

    fun processInPlace(
        samples: FloatArray,
        sampleCount: Int,
        channelCount: Int,
        sampleRate: Int,
        targetLufs: Float,
        enabled: Boolean,
        voiceStabilizerEnabled: Boolean,
        maxBoostDb: Float,
        maxClampDb: Float
    ): Float {
        if (!enabled && !voiceStabilizerEnabled) {
            return 0.0f
        }

        if (sampleCount == 0) return 0.0f

        // 1. Calculate short-term RMS level across current buffer
        var sumSquare = 0.0
        for (i in 0 until sampleCount) {
            val sample = samples[i]
            sumSquare += sample * sample
        }
        val rms = max(1e-6, kotlin.math.sqrt(sumSquare / sampleCount))

        // 2. Convert RMS to approximate LUFS (full scale sine reference = -3.01 dBFS)
        val lufsMeasured = (20.0 * log10(rms) - 3.01).toFloat()

        // 3. Compute target gain difference to match target LUFS
        var targetGainDb = targetLufs - lufsMeasured

        // Clamp gain boost/cut limits
        val minGainDb = if (voiceStabilizerEnabled) maxClampDb else -18.0f
        val maxGainDb = if (voiceStabilizerEnabled) maxBoostDb else 18.0f
        targetGainDb = targetGainDb.coerceIn(minGainDb, maxGainDb)

        val targetGainLinear = 10.0f.pow(targetGainDb / 20.0f)

        // 4. Smooth gain transition (attack ~150ms, release ~300ms) to avoid audio pumping
        val timeSec = sampleCount.toFloat() / (sampleRate * channelCount).coerceAtLeast(1)
        val alpha = if (targetGainLinear < smoothedGainLinear) {
            // Attack (clamps loud bursts faster)
            kotlin.math.exp(-timeSec / 0.08f)
        } else {
            // Release (boosts quiet whispers smoothly)
            kotlin.math.exp(-timeSec / 0.25f)
        }

        smoothedGainLinear = alpha * smoothedGainLinear + (1.0f - alpha) * targetGainLinear
        lastMeasuredGainDb = (20.0 * log10(smoothedGainLinear.toDouble())).toFloat()

        // 5. Apply gain in-place to samples
        for (i in 0 until sampleCount) {
            samples[i] *= smoothedGainLinear
        }

        return lastMeasuredGainDb
    }

    fun reset() {
        currentGainLinear = 1.0f
        smoothedGainLinear = 1.0f
        lastMeasuredGainDb = 0.0f
    }
}
