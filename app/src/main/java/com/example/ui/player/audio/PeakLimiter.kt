package com.example.ui.player.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

class PeakLimiter {

    private var currentGain: Float = 1.0f

    fun processInPlace(
        samples: FloatArray,
        sampleCount: Int,
        channelCount: Int,
        sampleRate: Int,
        ceilingDb: Float,
        enabled: Boolean
    ) {
        val maxAmp = 10.0f.pow(ceilingDb / 20.0f)

        var i = 0
        while (i < sampleCount) {
            // Find max amplitude in current frame
            var maxMag = 0.0f
            for (ch in 0 until channelCount) {
                if (i + ch < sampleCount) {
                    val mag = abs(samples[i + ch])
                    if (mag > maxMag) maxMag = mag
                }
            }

            // Target gain to keep peak below ceiling
            val targetGain = if (maxMag > maxAmp) (maxAmp / maxMag) else 1.0f

            // Fast attack, smooth release
            val alpha = if (targetGain < currentGain) 0.05f else 0.999f
            currentGain = alpha * currentGain + (1.0f - alpha) * targetGain

            // Apply limiter gain and final safety hard-clamp
            for (ch in 0 until channelCount) {
                if (i + ch < sampleCount) {
                    val s = samples[i + ch] * currentGain
                    samples[i + ch] = s.coerceIn(-1.0f, 1.0f)
                }
            }

            i += channelCount
        }
    }

    fun reset() {
        currentGain = 1.0f
    }
}
