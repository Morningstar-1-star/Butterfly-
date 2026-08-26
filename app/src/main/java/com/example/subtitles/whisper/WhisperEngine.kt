package com.example.subtitles.whisper

import android.content.Context
import android.util.Log
import com.example.subtitles.SubtitleItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

sealed class WhisperEngineState {
    object Idle : WhisperEngineState()
    object ModelMissing : WhisperEngineState()
    data class DownloadingModel(val progressPercent: Int) : WhisperEngineState()
    object Ready : WhisperEngineState()
    object Transcribing : WhisperEngineState()
    data class Error(val message: String) : WhisperEngineState()
}

class WhisperEngine(private val context: Context) {

    companion object {
        private const val TAG = "WhisperEngine"
        private const val SAMPLE_RATE_16KHZ = 16000
    }

    private val modelManager = WhisperModelManager(context)
    private var activeContext: WhisperContext? = null
    private val isCancelled = AtomicBoolean(false)

    private val _state = MutableStateFlow<WhisperEngineState>(WhisperEngineState.Idle)
    val state: StateFlow<WhisperEngineState> = _state.asStateFlow()

    fun checkModelStatus(): Boolean {
        val available = modelManager.isModelDownloaded(WhisperModelType.TINY_EN)
        if (!available) {
            _state.value = WhisperEngineState.ModelMissing
        } else {
            _state.value = WhisperEngineState.Ready
        }
        return available
    }

    suspend fun prepareModel(onProgress: ((Int) -> Unit)? = null): Boolean {
        if (checkModelStatus()) return true

        _state.value = WhisperEngineState.DownloadingModel(0)
        val file = modelManager.downloadModel(WhisperModelType.TINY_EN) { downloaded, total ->
            if (total > 0) {
                val pct = ((downloaded * 100) / total).toInt()
                _state.value = WhisperEngineState.DownloadingModel(pct)
                onProgress?.invoke(pct)
            }
        }

        return if (file != null) {
            _state.value = WhisperEngineState.Ready
            true
        } else {
            _state.value = WhisperEngineState.Error("Failed to download GGML speech model")
            false
        }
    }

    suspend fun transcribeAudioPcm(
        pcmData: ShortArray,
        sampleRate: Int,
        channels: Int
    ): List<SubtitleItem> = withContext(Dispatchers.Default) {
        if (!checkModelStatus()) {
            Log.w(TAG, "Whisper model missing. Cannot transcribe audio.")
            return@withContext emptyList<SubtitleItem>()
        }

        val modelFile = modelManager.getModelFile(WhisperModelType.TINY_EN) ?: return@withContext emptyList<SubtitleItem>()
        isCancelled.set(false)
        _state.value = WhisperEngineState.Transcribing

        val samples16k = resampleTo16kHzMono(pcmData, sampleRate, channels)
        if (samples16k.isEmpty()) return@withContext emptyList<SubtitleItem>()

        try {
            if (activeContext == null) {
                activeContext = WhisperContext(modelFile.absolutePath)
            }

            val ctx = activeContext
            if (ctx != null && ctx.isInitialized) {
                val result = ctx.transcribe(samples16k, "en")
                if (result == 0) {
                    Log.i(TAG, "Speech transcription executed successfully across ${samples16k.size} samples")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during speech transcription: ${e.message}", e)
            _state.value = WhisperEngineState.Error(e.message ?: "Transcription error")
        } finally {
            _state.value = WhisperEngineState.Ready
        }

        emptyList<SubtitleItem>()
    }

    fun cancel() {
        isCancelled.set(true)
    }

    fun release() {
        activeContext?.close()
        activeContext = null
        _state.value = WhisperEngineState.Idle
    }

    private fun resampleTo16kHzMono(input: ShortArray, sourceSampleRate: Int, channels: Int): FloatArray {
        if (input.isEmpty()) return FloatArray(0)

        val monoLength = input.size / channels.coerceAtLeast(1)
        val monoPcm = FloatArray(monoLength)

        var idx = 0
        for (i in input.indices step channels.coerceAtLeast(1)) {
            var sum = 0f
            for (c in 0 until channels) {
                if (i + c < input.size) {
                    sum += input[i + c] / 32768f
                }
            }
            if (idx < monoLength) {
                monoPcm[idx++] = sum / channels
            }
        }

        if (sourceSampleRate == SAMPLE_RATE_16KHZ) {
            return monoPcm
        }

        val ratio = sourceSampleRate.toDouble() / SAMPLE_RATE_16KHZ.toDouble()
        val targetSize = (monoLength / ratio).toInt()
        val resampled = FloatArray(targetSize)

        for (i in 0 until targetSize) {
            val srcIndex = (i * ratio).toInt().coerceIn(0, monoLength - 1)
            resampled[i] = monoPcm[srcIndex]
        }

        return resampled
    }
}
