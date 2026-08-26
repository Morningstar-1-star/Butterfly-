package com.example.audio

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import com.example.util.AiCaptionEngine
import com.example.util.WhisperInferenceEngine
import java.nio.ByteBuffer

/**
 * Transparent, bit-perfect AudioProcessor that captures decoded audio PCM frames
 * for on-device Whisper AI captioning without altering audio volume or dynamics.
 */
class WhisperAudioProcessor : BaseAudioProcessor() {

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Transparent passthrough with identical audio format
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // Ingest PCM samples into Whisper inference engine if live captions are enabled
        if (AiCaptionEngine.captionState.value.isEnabled) {
            val pcmBytes = ByteArray(remaining)
            val pos = inputBuffer.position()
            inputBuffer.get(pcmBytes)
            inputBuffer.position(pos) // Restore position for ExoPlayer audio pipeline

            val sampleRate = inputAudioFormat.sampleRate
            val channelCount = inputAudioFormat.channelCount
            WhisperInferenceEngine.feedPcmData(pcmBytes, sampleRate, channelCount)
        }

        // Forward untouched PCM data directly to AudioTrack output buffer
        val outBuffer = replaceOutputBuffer(remaining)
        outBuffer.put(inputBuffer)
        outBuffer.flip()
    }
}
