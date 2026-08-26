package com.example.ui.player.audio

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class DialogueEnhancer {

    private var b0 = 1.0f
    private var b1 = 0.0f
    private var b2 = 0.0f
    private var a1 = 0.0f
    private var a2 = 0.0f

    // State memory for biquad filter (Mid channel)
    private var x1 = 0.0f
    private var x2 = 0.0f
    private var y1 = 0.0f
    private var y2 = 0.0f

    private var lastSampleRate = 0

    private fun updateFilter(sampleRate: Int) {
        if (sampleRate == lastSampleRate || sampleRate <= 0) return
        lastSampleRate = sampleRate

        // 2nd Order Peaking Bandpass filter centered at 1200 Hz for human dialogue clarity
        val centerFreq = 1200.0
        val q = 0.85
        val w0 = 2.0 * Math.PI * centerFreq / sampleRate
        val alpha = sin(w0) / (2.0 * q)
        val cosW0 = cos(w0)

        val b0Unnorm = alpha
        val b1Unnorm = 0.0
        val b2Unnorm = -alpha
        val a0Unnorm = 1.0 + alpha
        val a1Unnorm = -2.0 * cosW0
        val a2Unnorm = 1.0 - alpha

        b0 = (b0Unnorm / a0Unnorm).toFloat()
        b1 = (b1Unnorm / a0Unnorm).toFloat()
        b2 = (b2Unnorm / a0Unnorm).toFloat()
        a1 = (a1Unnorm / a0Unnorm).toFloat()
        a2 = (a2Unnorm / a0Unnorm).toFloat()
    }

    fun processInPlace(
        samples: FloatArray,
        sampleCount: Int,
        channelCount: Int,
        sampleRate: Int,
        mode: DialogueBoostMode,
        boostPercentage: Float
    ) {
        if (mode == DialogueBoostMode.OFF && boostPercentage <= 0.0f) {
            return
        }

        updateFilter(sampleRate)

        val gainDb = if (mode != DialogueBoostMode.OFF) mode.gainDb else (boostPercentage / 100.0f * 9.0f)
        val boostLinear = (10.0f.pow(gainDb / 20.0f) - 1.0f)

        if (channelCount == 2) {
            var i = 0
            while (i < sampleCount - 1) {
                val left = samples[i]
                val right = samples[i + 1]

                val mid = (left + right) * 0.5f
                val side = (left - right) * 0.5f

                // Biquad filter on Mid channel to isolate vocal frequencies
                val filteredMid = b0 * mid + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
                x2 = x1
                x1 = mid
                y2 = y1
                y1 = filteredMid

                val enhancedMid = mid + filteredMid * boostLinear

                samples[i] = enhancedMid + side
                samples[i + 1] = enhancedMid - side

                i += 2
            }
        } else if (channelCount == 1) {
            var i = 0
            while (i < sampleCount) {
                val sample = samples[i]
                val filtered = b0 * sample + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
                x2 = x1
                x1 = sample
                y2 = y1
                y1 = filtered

                samples[i] = sample + filtered * boostLinear
                i++
            }
        }
    }

    /**
     * ITU-R BS.775 Downmix 5.1/7.1 Surround to Stereo with Center Channel Dialogue Boost
     */
    fun downmixSurroundToStereo(
        inSamples: FloatArray,
        inSampleCount: Int,
        inChannels: Int,
        outSamples: FloatArray,
        boostDb: Float
    ): Int {
        if (inChannels < 6) return 0

        val centerGain = 0.707f * 10.0f.pow(boostDb / 20.0f)
        val surroundGain = 0.707f

        var inIdx = 0
        var outIdx = 0
        val frameCount = inSampleCount / inChannels

        for (f in 0 until frameCount) {
            val l = inSamples[inIdx]
            val r = inSamples[inIdx + 1]
            val c = inSamples[inIdx + 2]
            // val lfe = inSamples[inIdx + 3]
            val ls = inSamples[inIdx + 4]
            val rs = inSamples[inIdx + 5]

            val leftStereo = l + c * centerGain + ls * surroundGain
            val rightStereo = r + c * centerGain + rs * surroundGain

            outSamples[outIdx] = leftStereo
            outSamples[outIdx + 1] = rightStereo

            inIdx += inChannels
            outIdx += 2
        }

        return frameCount * 2
    }

    fun reset() {
        x1 = 0.0f
        x2 = 0.0f
        y1 = 0.0f
        y2 = 0.0f
        lastSampleRate = 0
    }
}
