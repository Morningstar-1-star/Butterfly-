package com.example.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * Real-time Media3 PCM AudioProcessor implementing the full DSP pipeline:
 * Decode -> Channel Downmix -> Loudness Normalize -> Dynamic Compressor -> Dialogue/Voice Stabilizer -> EQ -> Limiter -> Volume
 */
class AudioEnhancementProcessor : BaseAudioProcessor() {

    @Volatile
    var config: AudioEnhancementConfig = AudioEnhancementConfig()
        set(value) {
            field = value
            updateDsfParameters()
        }

    // Telemetry callback
    var onTelemetryUpdated: ((AudioTelemetryState) -> Unit)? = null

    // DSP State Variables
    private var sampleRate = 44100
    private var channelCount = 2
    private var isFloatInput = false

    // Voice Formant Biquad Peaking Filter State
    private var bqB0 = 1.0f
    private var bqB1 = 0.0f
    private var bqB2 = 0.0f
    private var bqA1 = 0.0f
    private var bqA2 = 0.0f

    // Bass / Treble Shelving Filters
    private var bassB0 = 1f; private var bassB1 = 0f; private var bassB2 = 0f; private var bassA1 = 0f; private var bassA2 = 0f
    private var trebB0 = 1f; private var trebB1 = 0f; private var trebB2 = 0f; private var trebA1 = 0f; private var trebA2 = 0f

    // Filter Delays per channel (up to 2 output channels)
    private val vX1 = FloatArray(2)
    private val vX2 = FloatArray(2)
    private val vY1 = FloatArray(2)
    private val vY2 = FloatArray(2)

    private val bassX1 = FloatArray(2)
    private val bassX2 = FloatArray(2)
    private val bassY1 = FloatArray(2)
    private val bassY2 = FloatArray(2)

    private val trebX1 = FloatArray(2)
    private val trebX2 = FloatArray(2)
    private val trebY1 = FloatArray(2)
    private val trebY2 = FloatArray(2)

    // Dynamic Compressor & Voice Envelope Follower
    private var compEnvelope = 0.0f
    private var voiceEnvelope = 0.0f
    private var loudnessIntegratedEnvelope = 0.0f
    private var smoothedVoiceGain = 1.0f
    private var smoothedDrcGain = 1.0f

    // Telemetry accumulators
    private var telemetrySampleCounter = 0
    private var sumSqIn = 0.0
    private var sumSqOut = 0.0

    init {
        updateDsfParameters()
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        isFloatInput = (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT)

        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }

        // Determine output channel count (always downmix to stereo if multichannel)
        val outputChannels = when {
            config.channelMode == ChannelDownmixMode.MONO -> 1
            channelCount >= 2 -> 2
            else -> 1
        }

        updateDsfParameters()

        // Always output in 16-bit PCM for universal hardware DAC compatibility
        return AudioProcessor.AudioFormat(sampleRate, outputChannels, C.ENCODING_PCM_16BIT)
    }

    override fun onFlush() {
        super.onFlush()
        resetFilterState()
    }

    override fun onReset() {
        super.onReset()
        resetFilterState()
    }

    private fun resetFilterState() {
        for (i in 0 until 2) {
            vX1[i] = 0f; vX2[i] = 0f; vY1[i] = 0f; vY2[i] = 0f
            bassX1[i] = 0f; bassX2[i] = 0f; bassY1[i] = 0f; bassY2[i] = 0f
            trebX1[i] = 0f; trebX2[i] = 0f; trebY1[i] = 0f; trebY2[i] = 0f
        }
        compEnvelope = 0f
        voiceEnvelope = 0f
        loudnessIntegratedEnvelope = 0f
        smoothedVoiceGain = 1.0f
        smoothedDrcGain = 1.0f
        sumSqIn = 0.0
        sumSqOut = 0.0
        telemetrySampleCounter = 0
    }

    private fun updateDsfParameters() {
        val sr = if (sampleRate > 0) sampleRate.toFloat() else 44100f

        // 1. Dialogue Peak Filter around 1.5 kHz (speech intelligibility band)
        val dialogueBoostDb = when (config.dialogueBoost) {
            DialogueBoostMode.OFF -> 0f
            DialogueBoostMode.SUBTLE -> 3.0f
            DialogueBoostMode.CLEAR -> 6.0f
            DialogueBoostMode.VOCAL_MAX -> 9.0f
        }
        val effDialogueDb = if (config.nightMode) max(dialogueBoostDb, 5.0f) else dialogueBoostDb
        computePeakingBiquad(1500f, effDialogueDb, 1.4f, sr)

        // 2. Bass Low-Shelf Filter around 120 Hz
        computeLowShelfBiquad(120f, config.bassGainDb, 0.707f, sr)

        // 3. Treble High-Shelf Filter around 7.5 kHz
        computeHighShelfBiquad(7500f, config.trebleGainDb, 0.707f, sr)
    }

    private fun computePeakingBiquad(freq: Float, gainDb: Float, q: Float, sr: Float) {
        if (gainDb == 0f) {
            bqB0 = 1f; bqB1 = 0f; bqB2 = 0f; bqA1 = 0f; bqA2 = 0f
            return
        }
        val a = 10f.pow(gainDb / 40f)
        val w0 = 2f * PI.toFloat() * freq / sr
        val alpha = sin(w0) / (2f * q)
        val a0 = 1f + alpha / a
        bqB0 = (1f + alpha * a) / a0
        bqB1 = (-2f * cos(w0)) / a0
        bqB2 = (1f - alpha * a) / a0
        bqA1 = (-2f * cos(w0)) / a0
        bqA2 = (1f - alpha / a) / a0
    }

    private fun computeLowShelfBiquad(freq: Float, gainDb: Float, q: Float, sr: Float) {
        if (gainDb == 0f) {
            bassB0 = 1f; bassB1 = 0f; bassB2 = 0f; bassA1 = 0f; bassA2 = 0f
            return
        }
        val a = 10f.pow(gainDb / 40f)
        val w0 = 2f * PI.toFloat() * freq / sr
        val cosW = cos(w0)
        val sinW = sin(w0)
        val alpha = sinW / (2f * q)
        val aPlus1 = a + 1f
        val aMinus1 = a - 1f
        val sqrt2aAlpha = 2f * sqrt(a) * alpha
        val a0 = aPlus1 + aMinus1 * cosW + sqrt2aAlpha

        bassB0 = (a * (aPlus1 - aMinus1 * cosW + sqrt2aAlpha)) / a0
        bassB1 = (2f * a * (aMinus1 - aPlus1 * cosW)) / a0
        bassB2 = (a * (aPlus1 - aMinus1 * cosW - sqrt2aAlpha)) / a0
        bassA1 = (-2f * (aMinus1 + aPlus1 * cosW)) / a0
        bassA2 = (aPlus1 + aMinus1 * cosW - sqrt2aAlpha) / a0
    }

    private fun computeHighShelfBiquad(freq: Float, gainDb: Float, q: Float, sr: Float) {
        if (gainDb == 0f) {
            trebB0 = 1f; trebB1 = 0f; trebB2 = 0f; trebA1 = 0f; trebA2 = 0f
            return
        }
        val a = 10f.pow(gainDb / 40f)
        val w0 = 2f * PI.toFloat() * freq / sr
        val cosW = cos(w0)
        val sinW = sin(w0)
        val alpha = sinW / (2f * q)
        val aPlus1 = a + 1f
        val aMinus1 = a - 1f
        val sqrt2aAlpha = 2f * sqrt(a) * alpha
        val a0 = aPlus1 - aMinus1 * cosW + sqrt2aAlpha

        trebB0 = (a * (aPlus1 + aMinus1 * cosW + sqrt2aAlpha)) / a0
        trebB1 = (-2f * a * (aMinus1 + aPlus1 * cosW)) / a0
        trebB2 = (a * (aPlus1 + aMinus1 * cosW - sqrt2aAlpha)) / a0
        trebA1 = (2f * (aMinus1 - aPlus1 * cosW)) / a0
        trebA2 = (aPlus1 - aMinus1 * cosW - sqrt2aAlpha) / a0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val inChannels = channelCount
        val bytesPerSample = if (isFloatInput) 4 else 2
        val inFrameSize = inChannels * bytesPerSample
        val frameCount = inputBuffer.remaining() / inFrameSize

        if (frameCount <= 0) return

        val outChannels = outputAudioFormat.channelCount
        val outBytesPerSample = 2 // 16-bit PCM output
        val outBuffer = replaceOutputBuffer(frameCount * outChannels * outBytesPerSample)

        val isEnabled = config.isEnabled
        val masterVolumeScale = (config.volumeBoostPercent.toFloat() / 100f).coerceIn(0.1f, 3.0f)
        val limiterCeilingLinear = 10f.pow(config.limiterCeilingDb / 20f).coerceIn(0.7f, 0.99f)

        // DRC Settings
        val drcMode = if (config.nightMode) DynamicRangeMode.STRONG else config.dynamicRangeMode
        val drcThreshLinear = 10f.pow(drcMode.thresholdDb / 20f)
        val drcRatio = drcMode.ratio
        val drcAttackCoeff = 0.05f
        val drcReleaseCoeff = 0.001f

        // Voice Stabilizer / AGC parameters
        val voiceStab = config.voiceStabilizer
        val voiceStabEnabled = (voiceStab.enabled || config.preset == AudioPreset.VOICE_STABILIZER || config.nightMode) && isEnabled
        val targetSpeechLinear = 10f.pow(voiceStab.targetSpeechDb / 20f)
        val minGainLinear = 10f.pow(voiceStab.minGainDb / 20f) // e.g. -10dB -> 0.316
        val maxGainLinear = 10f.pow(voiceStab.maxGainDb / 20f) // e.g. +12dB -> 3.98

        // Loudness Normalizer Target
        val targetLufsLinear = 10f.pow((config.targetLoudnessLufs + 3f) / 20f)

        for (f in 0 until frameCount) {
            // Read Channels and convert to normalized Float [-1.0f..1.0f]
            var left = 0f
            var right = 0f
            var center = 0f
            var surroundLeft = 0f
            var surroundRight = 0f

            if (!isFloatInput) {
                if (inChannels == 1) {
                    val s = inputBuffer.short / 32768.0f
                    left = s; right = s; center = s
                } else if (inChannels == 2) {
                    left = inputBuffer.short / 32768.0f
                    right = inputBuffer.short / 32768.0f
                    center = (left + right) * 0.5f
                } else if (inChannels >= 6) {
                    // 5.1: [L, R, C, LFE, Ls, Rs]
                    left = inputBuffer.short / 32768.0f
                    right = inputBuffer.short / 32768.0f
                    center = inputBuffer.short / 32768.0f
                    val lfe = inputBuffer.short / 32768.0f
                    surroundLeft = inputBuffer.short / 32768.0f
                    surroundRight = inputBuffer.short / 32768.0f
                    // Skip remaining if 7.1
                    for (c in 6 until inChannels) {
                        inputBuffer.short
                    }
                } else {
                    left = inputBuffer.short / 32768.0f
                    right = inputBuffer.short / 32768.0f
                    for (c in 2 until inChannels) {
                        inputBuffer.short
                    }
                }
            } else {
                if (inChannels == 1) {
                    val s = inputBuffer.float
                    left = s; right = s; center = s
                } else if (inChannels == 2) {
                    left = inputBuffer.float
                    right = inputBuffer.float
                    center = (left + right) * 0.5f
                } else if (inChannels >= 6) {
                    left = inputBuffer.float
                    right = inputBuffer.float
                    center = inputBuffer.float
                    inputBuffer.float // LFE
                    surroundLeft = inputBuffer.float
                    surroundRight = inputBuffer.float
                    for (c in 6 until inChannels) {
                        inputBuffer.float
                    }
                } else {
                    left = inputBuffer.float
                    right = inputBuffer.float
                    for (c in 2 until inChannels) {
                        inputBuffer.float
                    }
                }
            }

            // Input Telemetry calculation
            sumSqIn += (left * left + right * right) * 0.5

            if (!isEnabled) {
                // Passthrough
                val finalL = (left * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
                val finalR = (right * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
                if (outChannels == 1) {
                    outBuffer.putShort(((finalL + finalR) / 2).toShort())
                } else {
                    outBuffer.putShort(finalL)
                    outBuffer.putShort(finalR)
                }
                continue
            }

            // 1. Channel Downmixing (ITU-R BS.775 with speech emphasis on Center channel)
            var procL: Float
            var procR: Float

            if (inChannels >= 6 && config.channelMode != ChannelDownmixMode.STEREO) {
                val centerWeight = if (config.dialogueBoost != DialogueBoostMode.OFF) 0.85f else 0.7071f
                procL = (left + center * centerWeight + surroundLeft * 0.5f) * 0.75f
                procR = (right + center * centerWeight + surroundRight * 0.5f) * 0.75f
            } else {
                procL = left
                procR = right
            }

            // 2. Mid/Side Speech Band Extraction & Dialogue Peaking Filter
            val mid = (procL + procR) * 0.5f
            val side = (procL - procR) * 0.5f

            // Apply vocal peak filter to Mid channel
            val filteredMid = applyBiquad(mid, 0)
            val enhancedMid = if (config.dialogueBoost != DialogueBoostMode.OFF || config.nightMode) {
                filteredMid
            } else {
                mid
            }

            procL = enhancedMid + side
            procR = enhancedMid - side

            // 3. Dynamic Range Compression (DRC) Envelope
            val peakMagnitude = max(abs(procL), abs(procR))
            if (peakMagnitude > compEnvelope) {
                compEnvelope += drcAttackCoeff * (peakMagnitude - compEnvelope)
            } else {
                compEnvelope += drcReleaseCoeff * (peakMagnitude - compEnvelope)
            }

            var drcGain = 1.0f
            if (drcMode != DynamicRangeMode.OFF && compEnvelope > drcThreshLinear) {
                val overDb = 20f * log10(compEnvelope / drcThreshLinear)
                val compressedDb = overDb / drcRatio
                val targetGainDb = -(overDb - compressedDb)
                drcGain = 10f.pow(targetGainDb / 20f)
            }
            smoothedDrcGain += 0.01f * (drcGain - smoothedDrcGain)

            procL *= smoothedDrcGain
            procR *= smoothedDrcGain

            // 4. Voice Stabilizer (Leveler / Clamp for loud spikes & boost for low voices)
            val speechEnergy = abs(enhancedMid)
            voiceEnvelope += if (speechEnergy > voiceEnvelope) 0.08f * (speechEnergy - voiceEnvelope) else 0.0008f * (speechEnergy - voiceEnvelope)

            if (voiceStabEnabled && voiceEnvelope > 0.001f) {
                val targetGain = (targetSpeechLinear / (voiceEnvelope + 0.005f)).coerceIn(minGainLinear, maxGainLinear)
                val voiceAlpha = (voiceStab.responseSpeed * 0.005f).coerceIn(0.0005f, 0.02f)
                smoothedVoiceGain += voiceAlpha * (targetGain - smoothedVoiceGain)

                procL *= smoothedVoiceGain
                procR *= smoothedVoiceGain
            }

            // 5. Loudness Normalization
            if (config.loudnessNormalization) {
                loudnessIntegratedEnvelope += 0.0002f * (max(abs(procL), abs(procR)) - loudnessIntegratedEnvelope)
                if (loudnessIntegratedEnvelope > 0.001f) {
                    val normGain = (targetLufsLinear / (loudnessIntegratedEnvelope + 0.01f)).coerceIn(0.5f, 2.5f)
                    procL *= normGain
                    procR *= normGain
                }
            }

            // 6. Tone Controls (Bass & Treble Shelving Filters)
            if (config.bassGainDb != 0f) {
                procL = applyBassBiquad(procL, 0)
                procR = applyBassBiquad(procR, 1)
            }
            if (config.trebleGainDb != 0f) {
                procL = applyTrebleBiquad(procL, 0)
                procR = applyTrebleBiquad(procR, 1)
            }

            // 7. Master Volume Boost
            procL *= masterVolumeScale
            procR *= masterVolumeScale

            // 8. True-Peak Limiter (Hard ceiling with smooth soft-knee saturation)
            if (config.limiterEnabled) {
                procL = softLimit(procL, limiterCeilingLinear)
                procR = softLimit(procR, limiterCeilingLinear)
            }

            // Telemetry Output Accumulator
            sumSqOut += (procL * procL + procR * procR) * 0.5

            // Convert to 16-bit PCM output
            val outL = (procL * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
            val outR = (procR * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()

            if (outChannels == 1) {
                outBuffer.putShort(((outL + outR) / 2).toShort())
            } else {
                outBuffer.putShort(outL)
                outBuffer.putShort(outR)
            }
        }

        // Periodic Telemetry Dispatch (~10 times per second)
        telemetrySampleCounter += frameCount
        if (telemetrySampleCounter >= (sampleRate / 10)) {
            val inRms = sqrt(sumSqIn / telemetrySampleCounter.toDouble()).toFloat().coerceAtLeast(1e-5f)
            val outRms = sqrt(sumSqOut / telemetrySampleCounter.toDouble()).toFloat().coerceAtLeast(1e-5f)
            val inDb = 20f * log10(inRms)
            val outDb = 20f * log10(outRms)
            val grDb = if (smoothedDrcGain < 1.0f) 20f * log10(smoothedDrcGain) else 0f
            val voiceGainDb = 20f * log10(smoothedVoiceGain)

            val telemetry = AudioTelemetryState(
                inputRmsDb = inDb.coerceIn(-60f, 0f),
                outputRmsDb = outDb.coerceIn(-60f, 0f),
                currentGainReductionDb = grDb,
                appliedVoiceGainDb = voiceGainDb,
                voiceActive = voiceEnvelope > 0.01f,
                channels = channelCount,
                sampleRate = sampleRate
            )
            onTelemetryUpdated?.invoke(telemetry)

            telemetrySampleCounter = 0
            sumSqIn = 0.0
            sumSqOut = 0.0
        }

        outBuffer.flip()
    }

    private fun applyBiquad(sample: Float, ch: Int): Float {
        val y = bqB0 * sample + bqB1 * vX1[ch] + bqB2 * vX2[ch] - bqA1 * vY1[ch] - bqA2 * vY2[ch]
        vX2[ch] = vX1[ch]
        vX1[ch] = sample
        vY2[ch] = vY1[ch]
        vY1[ch] = y
        return y
    }

    private fun applyBassBiquad(sample: Float, ch: Int): Float {
        val y = bassB0 * sample + bassB1 * bassX1[ch] + bassB2 * bassX2[ch] - bassA1 * bassY1[ch] - bassA2 * bassY2[ch]
        bassX2[ch] = bassX1[ch]
        bassX1[ch] = sample
        bassY2[ch] = bassY1[ch]
        bassY1[ch] = y
        return y
    }

    private fun applyTrebleBiquad(sample: Float, ch: Int): Float {
        val y = trebB0 * sample + trebB1 * trebX1[ch] + trebB2 * trebX2[ch] - trebA1 * trebY1[ch] - trebA2 * trebY2[ch]
        trebX2[ch] = trebX1[ch]
        trebX1[ch] = sample
        trebY2[ch] = trebY1[ch]
        trebY1[ch] = y
        return y
    }

    private fun softLimit(sample: Float, ceiling: Float): Float {
        val absX = abs(sample)
        if (absX <= ceiling * 0.85f) return sample
        // Fast cubic soft-saturation curve above 85% of ceiling
        val sign = if (sample >= 0f) 1.0f else -1.0f
        val threshold = ceiling * 0.85f
        val over = absX - threshold
        val range = ceiling - threshold
        val compressed = threshold + range * tanh(over / range)
        return sign * min(compressed, ceiling)
    }
}
