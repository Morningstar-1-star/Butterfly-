package com.example.ui.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max

class AudioEnhancementProcessor : BaseAudioProcessor() {

    private val loudnessNormalizer = LoudnessNormalizer()
    private val dynamicCompressor = DynamicCompressor()
    private val dialogueEnhancer = DialogueEnhancer()
    private val audioEqualizer = AudioEqualizer()
    private val peakLimiter = PeakLimiter()

    // Reusable buffers to avoid allocations in queueInput
    private var floatBuffer = FloatArray(4096)
    private var downmixFloatBuffer = FloatArray(4096)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val config = AudioEnhancementEngine.config.value
        val outChannels = when {
            config.channelMode == ChannelMode.MONO -> 1
            config.channelMode == ChannelMode.STEREO && inputAudioFormat.channelCount > 2 -> 2
            config.channelMode == ChannelMode.DOWNMIX_5_1 && inputAudioFormat.channelCount >= 6 -> 2
            inputAudioFormat.channelCount >= 6 -> 2
            else -> inputAudioFormat.channelCount
        }

        return AudioProcessor.AudioFormat(
            inputAudioFormat.sampleRate,
            outChannels,
            inputAudioFormat.encoding
        )
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val config = AudioEnhancementEngine.config.value
        val inFormat = inputAudioFormat
        val outFormat = outputAudioFormat

        // Bypass processing if engine is completely disabled
        if (!config.isEnabled) {
            val outBuffer = replaceOutputBuffer(remaining)
            outBuffer.put(inputBuffer)
            outBuffer.flip()
            return
        }

        val inSampleRate = inFormat.sampleRate
        val inChannels = inFormat.channelCount
        val encoding = inFormat.encoding

        val is16BitPcm = encoding == C.ENCODING_PCM_16BIT
        val isFloatPcm = encoding == C.ENCODING_PCM_FLOAT

        if (!is16BitPcm && !isFloatPcm) {
            // Fallback passthrough for unhandled encodings
            val outBuffer = replaceOutputBuffer(remaining)
            outBuffer.put(inputBuffer)
            outBuffer.flip()
            return
        }

        val bytesPerSample = if (is16BitPcm) 2 else 4
        val totalSamples = remaining / bytesPerSample

        if (floatBuffer.size < totalSamples) {
            floatBuffer = FloatArray(totalSamples * 2)
        }

        // 1. Read input buffer into normalized float array (-1.0f to +1.0f)
        var maxInMag = 1e-6f
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        if (is16BitPcm) {
            for (i in 0 until totalSamples) {
                val sampleShort = inputBuffer.short
                val f = sampleShort / 32768.0f
                floatBuffer[i] = f
                val mag = abs(f)
                if (mag > maxInMag) maxInMag = mag
            }
        } else {
            for (i in 0 until totalSamples) {
                var f = inputBuffer.float
                if (f.isNaN() || f.isInfinite()) f = 0.0f
                floatBuffer[i] = f.coerceIn(-1.0f, 1.0f)
                val mag = abs(f)
                if (mag > maxInMag) maxInMag = mag
            }
        }

        val inDb = 20.0f * log10(maxInMag)

        // 2. Handle 5.1/7.1 Surround Downmixing if needed
        var currentSamples = floatBuffer
        var currentSampleCount = totalSamples
        var currentChannels = inChannels

        if (inChannels >= 6 && outFormat.channelCount == 2) {
            val frameCount = totalSamples / inChannels
            val reqSize = frameCount * 2
            if (downmixFloatBuffer.size < reqSize) {
                downmixFloatBuffer = FloatArray(reqSize * 2)
            }
            val boostDb = if (config.dialogueBoostMode != DialogueBoostMode.OFF) config.dialogueBoostMode.gainDb else 3.0f
            val downmixedCount = dialogueEnhancer.downmixSurroundToStereo(
                inSamples = floatBuffer,
                inSampleCount = totalSamples,
                inChannels = inChannels,
                outSamples = downmixFloatBuffer,
                boostDb = boostDb
            )
            currentSamples = downmixFloatBuffer
            currentSampleCount = downmixedCount
            currentChannels = 2
        } else if (config.channelMode == ChannelMode.MONO && inChannels == 2) {
            // Stereo to Mono downmixing
            val frameCount = totalSamples / 2
            for (f in 0 until frameCount) {
                val mono = (floatBuffer[f * 2] + floatBuffer[f * 2 + 1]) * 0.5f
                floatBuffer[f] = mono
            }
            currentSampleCount = frameCount
            currentChannels = 1
        }

        // 3. Loudness Normalization & Voice Leveling
        val levelerGainDb = loudnessNormalizer.processInPlace(
            samples = currentSamples,
            sampleCount = currentSampleCount,
            channelCount = currentChannels,
            sampleRate = inSampleRate,
            targetLufs = config.targetLufs,
            enabled = config.loudnessNormalizationEnabled,
            voiceStabilizerEnabled = config.voiceStabilizerEnabled,
            maxBoostDb = config.whisperBoostLimitDb,
            maxClampDb = config.explosionClampLimitDb
        )

        // 4. Dynamic Range Compression (DRC)
        dynamicCompressor.processInPlace(
            samples = currentSamples,
            sampleCount = currentSampleCount,
            channelCount = currentChannels,
            sampleRate = inSampleRate,
            mode = config.drcMode,
            customThresholdDb = config.drcThresholdDb,
            customRatio = config.drcRatio,
            attackMs = config.drcAttackMs,
            releaseMs = config.drcReleaseMs,
            kneeDb = config.drcKneeDb,
            makeupGainDb = config.drcMakeupGainDb
        )

        // 5. Dialogue Boost & Speech Clarity
        if (inChannels < 6) {
            dialogueEnhancer.processInPlace(
                samples = currentSamples,
                sampleCount = currentSampleCount,
                channelCount = currentChannels,
                sampleRate = inSampleRate,
                mode = config.dialogueBoostMode,
                boostPercentage = config.dialogueBoostPercentage
            )
        }

        // 6. Tone, Bass Boost & 10-Band Graphic Equalizer + 3D Virtualizer
        audioEqualizer.processInPlace(
            samples = currentSamples,
            sampleCount = currentSampleCount,
            channelCount = currentChannels,
            sampleRate = inSampleRate,
            bassGainDb = config.bassGainDb + config.bassBoostDb,
            trebleGainDb = config.trebleGainDb + config.trebleBoostDb,
            eq10BandsDb = config.eq10BandsDb,
            virtualizerPercent = config.virtualizerPercent
        )

        // 7. Peak Limiter
        peakLimiter.processInPlace(
            samples = currentSamples,
            sampleCount = currentSampleCount,
            channelCount = currentChannels,
            sampleRate = inSampleRate,
            ceilingDb = config.limiterCeilingDb,
            enabled = config.limiterEnabled
        )

        // Calculate output peak meter dB
        var maxOutMag = 1e-6f
        for (i in 0 until currentSampleCount) {
            val mag = abs(currentSamples[i])
            if (mag > maxOutMag) maxOutMag = mag
        }
        val outDb = 20.0f * log10(maxOutMag)

        // Update live meter stats for UI
        AudioEnhancementEngine.updateMeters(
            inDb = inDb.coerceIn(-60f, 0f),
            outDb = outDb.coerceIn(-60f, 0f),
            gainLevelerDb = levelerGainDb,
            sampleRate = inSampleRate,
            channelCount = currentChannels,
            activeDsp = true
        )

        // 8. Write processed samples to output ByteBuffer
        val outputByteCount = currentSampleCount * bytesPerSample
        val outBuffer = replaceOutputBuffer(outputByteCount)
        outBuffer.order(ByteOrder.LITTLE_ENDIAN)

        if (is16BitPcm) {
            for (i in 0 until currentSampleCount) {
                val clamped = currentSamples[i].coerceIn(-1.0f, 1.0f)
                val s = (clamped * 32767.0f).toInt().toShort()
                outBuffer.putShort(s)
            }
        } else {
            for (i in 0 until currentSampleCount) {
                val clamped = currentSamples[i].coerceIn(-1.0f, 1.0f)
                outBuffer.putFloat(clamped)
            }
        }

        outBuffer.flip()
    }

    override fun onReset() {
        loudnessNormalizer.reset()
        dynamicCompressor.reset()
        dialogueEnhancer.reset()
        audioEqualizer.reset()
        peakLimiter.reset()
    }
}
