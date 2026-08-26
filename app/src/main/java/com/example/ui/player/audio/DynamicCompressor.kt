package com.example.ui.player.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

class DynamicCompressor {

    private var envelopeDb: Float = -96.0f

    fun processInPlace(
        samples: FloatArray,
        sampleCount: Int,
        channelCount: Int,
        sampleRate: Int,
        mode: DynamicRangeCompressionMode,
        customThresholdDb: Float,
        customRatio: Float,
        attackMs: Float,
        releaseMs: Float,
        kneeDb: Float,
        makeupGainDb: Float
    ) {
        if (mode == DynamicRangeCompressionMode.OFF && customRatio <= 1.0f) {
            return
        }

        val threshold = if (mode != DynamicRangeCompressionMode.OFF) mode.thresholdDb else customThresholdDb
        val ratio = if (mode != DynamicRangeCompressionMode.OFF) mode.ratio else customRatio
        val makeupGainLinear = 10.0f.pow((if (mode != DynamicRangeCompressionMode.OFF) (makeupGainDb + (abs(threshold) / 4f)) else makeupGainDb) / 20.0f)

        val attackAlpha = exp(-1.0f / (max(1.0f, attackMs) * 0.001f * sampleRate))
        val releaseAlpha = exp(-1.0f / (max(10.0f, releaseMs) * 0.001f * sampleRate))
        val halfKnee = kneeDb / 2.0f

        var i = 0
        while (i < sampleCount) {
            // Find max peak across channels for current frame
            var maxMag = 1e-6f
            for (ch in 0 until channelCount) {
                if (i + ch < sampleCount) {
                    val mag = abs(samples[i + ch])
                    if (mag > maxMag) maxMag = mag
                }
            }

            val sampleDb = 20.0f * log10(maxMag)

            // Envelope follower
            envelopeDb = if (sampleDb > envelopeDb) {
                attackAlpha * envelopeDb + (1.0f - attackAlpha) * sampleDb
            } else {
                releaseAlpha * envelopeDb + (1.0f - releaseAlpha) * sampleDb
            }

            // Calculate gain reduction in dB
            var gainDb = 0.0f
            if (kneeDb > 0.0f && envelopeDb > (threshold - halfKnee) && envelopeDb < (threshold + halfKnee)) {
                // Soft knee region
                val delta = envelopeDb - threshold + halfKnee
                gainDb = ((1.0f / ratio) - 1.0f) * (delta * delta) / (2.0f * kneeDb)
            } else if (envelopeDb >= threshold) {
                // Above threshold
                gainDb = ((1.0f / ratio) - 1.0f) * (envelopeDb - threshold)
            }

            val gainLinear = 10.0f.pow(gainDb / 20.0f) * makeupGainLinear

            // Apply compressed gain to frame channels
            for (ch in 0 until channelCount) {
                if (i + ch < sampleCount) {
                    samples[i + ch] *= gainLinear
                }
            }

            i += channelCount
        }
    }

    fun reset() {
        envelopeDb = -96.0f
    }
}
