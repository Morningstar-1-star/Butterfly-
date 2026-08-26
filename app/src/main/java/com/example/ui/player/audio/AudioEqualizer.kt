package com.example.ui.player.audio

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class AudioEqualizer {

    private class BiquadFilter {
        var b0 = 1.0f
        var b1 = 0.0f
        var b2 = 0.0f
        var a1 = 0.0f
        var a2 = 0.0f

        var x1L = 0.0f
        var x2L = 0.0f
        var y1L = 0.0f
        var y2L = 0.0f

        var x1R = 0.0f
        var x2R = 0.0f
        var y1R = 0.0f
        var y2R = 0.0f

        fun reset() {
            x1L = 0f; x2L = 0f; y1L = 0f; y2L = 0f
            x1R = 0f; x2R = 0f; y1R = 0f; y2R = 0f
        }

        fun configureLowShelf(freq: Float, gainDb: Float, sampleRate: Int) {
            if (kotlin.math.abs(gainDb) < 0.1f) {
                b0 = 1.0f; b1 = 0f; b2 = 0f; a1 = 0f; a2 = 0f
                return
            }
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * Math.PI * freq / sampleRate
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / 2.0 * sqrt(2.0)

            val b0Un = a * ((a + 1.0) - (a - 1.0) * cosW0 + 2.0 * sqrt(a) * alpha)
            val b1Un = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0)
            val b2Un = a * ((a + 1.0) - (a - 1.0) * cosW0 - 2.0 * sqrt(a) * alpha)
            val a0Un = (a + 1.0) + (a - 1.0) * cosW0 + 2.0 * sqrt(a) * alpha
            val a1Un = -2.0 * ((a - 1.0) + (a + 1.0) * cosW0)
            val a2Un = (a + 1.0) + (a - 1.0) * cosW0 - 2.0 * sqrt(a) * alpha

            b0 = (b0Un / a0Un).toFloat()
            b1 = (b1Un / a0Un).toFloat()
            b2 = (b2Un / a0Un).toFloat()
            a1 = (a1Un / a0Un).toFloat()
            a2 = (a2Un / a0Un).toFloat()
        }

        fun configureHighShelf(freq: Float, gainDb: Float, sampleRate: Int) {
            if (kotlin.math.abs(gainDb) < 0.1f) {
                b0 = 1.0f; b1 = 0f; b2 = 0f; a1 = 0f; a2 = 0f
                return
            }
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * Math.PI * freq / sampleRate
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / 2.0 * sqrt(2.0)

            val b0Un = a * ((a + 1.0) + (a - 1.0) * cosW0 + 2.0 * sqrt(a) * alpha)
            val b1Un = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0)
            val b2Un = a * ((a + 1.0) + (a - 1.0) * cosW0 - 2.0 * sqrt(a) * alpha)
            val a0Un = (a + 1.0) - (a - 1.0) * cosW0 + 2.0 * sqrt(a) * alpha
            val a1Un = 2.0 * ((a - 1.0) - (a + 1.0) * cosW0)
            val a2Un = (a + 1.0) - (a - 1.0) * cosW0 - 2.0 * sqrt(a) * alpha

            b0 = (b0Un / a0Un).toFloat()
            b1 = (b1Un / a0Un).toFloat()
            b2 = (b2Un / a0Un).toFloat()
            a1 = (a1Un / a0Un).toFloat()
            a2 = (a2Un / a0Un).toFloat()
        }

        fun configurePeaking(freq: Float, gainDb: Float, q: Float, sampleRate: Int) {
            if (kotlin.math.abs(gainDb) < 0.1f) {
                b0 = 1.0f; b1 = 0f; b2 = 0f; a1 = 0f; a2 = 0f
                return
            }
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * Math.PI * freq / sampleRate
            val alpha = sin(w0) / (2.0 * q)
            val cosW0 = cos(w0)

            val b0Un = 1.0 + alpha * a
            val b1Un = -2.0 * cosW0
            val b2Un = 1.0 - alpha * a
            val a0Un = 1.0 + alpha / a
            val a1Un = -2.0 * cosW0
            val a2Un = 1.0 - alpha / a

            b0 = (b0Un / a0Un).toFloat()
            b1 = (b1Un / a0Un).toFloat()
            b2 = (b2Un / a0Un).toFloat()
            a1 = (a1Un / a0Un).toFloat()
            a2 = (a2Un / a0Un).toFloat()
        }

        fun processStereo(l: Float, r: Float): Pair<Float, Float> {
            if (b0 == 1.0f && b1 == 0f && b2 == 0f && a1 == 0f && a2 == 0f) {
                return Pair(l, r)
            }
            val outL = b0 * l + b1 * x1L + b2 * x2L - a1 * y1L - a2 * y2L
            x2L = x1L; x1L = l; y2L = y1L; y1L = outL

            val outR = b0 * r + b1 * x1R + b2 * x2R - a1 * y1R - a2 * y2R
            x2R = x1R; x1R = r; y2R = y1R; y1R = outR

            return Pair(outL, outR)
        }
    }

    private val bassShelf = BiquadFilter()
    private val trebleShelf = BiquadFilter()
    private val eqBands = Array(5) { BiquadFilter() }

    private val eqFreqs = floatArrayOf(60f, 230f, 910f, 3600f, 14000f)

    private var lastSampleRate = 0
    private var lastBassGain = Float.NaN
    private var lastTrebleGain = Float.NaN
    private var lastEqBands: FloatArray? = null

    private fun configureFiltersIfNeeded(
        bassGainDb: Float,
        trebleGainDb: Float,
        eq5BandsDb: FloatArray,
        sampleRate: Int
    ) {
        if (sampleRate <= 0) return
        val eqChanged = lastEqBands == null || !lastEqBands!!.contentEquals(eq5BandsDb)

        if (sampleRate != lastSampleRate || bassGainDb != lastBassGain || trebleGainDb != lastTrebleGain || eqChanged) {
            lastSampleRate = sampleRate
            lastBassGain = bassGainDb
            lastTrebleGain = trebleGainDb
            lastEqBands = eq5BandsDb.clone()

            bassShelf.configureLowShelf(120f, bassGainDb, sampleRate)
            trebleShelf.configureHighShelf(7000f, trebleGainDb, sampleRate)

            for (b in 0 until 5) {
                val gain = if (b < eq5BandsDb.size) eq5BandsDb[b] else 0f
                eqBands[b].configurePeaking(eqFreqs[b], gain, 1.4f, sampleRate)
            }
        }
    }

    fun processInPlace(
        samples: FloatArray,
        sampleCount: Int,
        channelCount: Int,
        sampleRate: Int,
        bassGainDb: Float,
        trebleGainDb: Float,
        eq5BandsDb: FloatArray
    ) {
        val hasEq = bassGainDb != 0f || trebleGainDb != 0f || eq5BandsDb.any { it != 0f }
        if (!hasEq) return

        configureFiltersIfNeeded(bassGainDb, trebleGainDb, eq5BandsDb, sampleRate)

        if (channelCount == 2) {
            var i = 0
            while (i < sampleCount - 1) {
                var l = samples[i]
                var r = samples[i + 1]

                val (l1, r1) = bassShelf.processStereo(l, r)
                val (l2, r2) = trebleShelf.processStereo(l1, r1)

                l = l2
                r = r2

                for (b in 0 until 5) {
                    val (lEq, rEq) = eqBands[b].processStereo(l, r)
                    l = lEq
                    r = rEq
                }

                samples[i] = l
                samples[i + 1] = r

                i += 2
            }
        } else if (channelCount == 1) {
            var i = 0
            while (i < sampleCount) {
                var s = samples[i]
                val (l1, _) = bassShelf.processStereo(s, s)
                val (l2, _) = trebleShelf.processStereo(l1, l1)
                s = l2
                for (b in 0 until 5) {
                    val (lEq, _) = eqBands[b].processStereo(s, s)
                    s = lEq
                }
                samples[i] = s
                i++
            }
        }
    }

    fun reset() {
        bassShelf.reset()
        trebleShelf.reset()
        eqBands.forEach { it.reset() }
        lastSampleRate = 0
    }
}
