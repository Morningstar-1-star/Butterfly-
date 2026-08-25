package com.example.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class LiveCaptionState(
    val isEnabled: Boolean = false,
    val isTranscribing: Boolean = false,
    val sourceLanguage: String = "auto", // auto, ja, zh
    val targetLanguage: String = "en",
    val activeModelId: String = "base",
    val isModelLoaded: Boolean = false,
    val currentOriginalText: String = "",
    val currentTranslatedText: String = "",
    val dualSubtitleEnabled: Boolean = true,
    val fontSizeSp: Float = 16f,
    val backgroundOpacity: Float = 0.75f,
    val isPositionTop: Boolean = false,
    val recentCues: List<SubtitleCue> = emptyList(),
    val statusMessage: String = "Ready"
)

object AiCaptionEngine {
    private const val TAG = "AiCaptionEngine"

    private val _captionState = MutableStateFlow(LiveCaptionState())
    val captionState: StateFlow<LiveCaptionState> = _captionState.asStateFlow()

    private var engineJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // Cached transcripts for currently playing session: timestampMs -> original + translated text
    private val sessionCues = mutableListOf<SubtitleCue>()

    fun setEnabled(enabled: Boolean, context: Context? = null) {
        _captionState.value = _captionState.value.copy(isEnabled = enabled)
        if (enabled && context != null) {
            startEngine(context)
        } else if (!enabled) {
            stopEngine()
        }
    }

    fun setLanguages(source: String, target: String) {
        _captionState.value = _captionState.value.copy(
            sourceLanguage = source,
            targetLanguage = target
        )
        // Re-translate current active text if exists
        val original = _captionState.value.currentOriginalText
        if (original.isNotBlank()) {
            scope.launch {
                val translated = SubtitleTranslator.translateText(original, targetLang = target, sourceLang = source)
                _captionState.value = _captionState.value.copy(currentTranslatedText = translated)
            }
        }
    }

    fun setDualSubtitles(enabled: Boolean) {
        _captionState.value = _captionState.value.copy(dualSubtitleEnabled = enabled)
    }

    fun setFontSize(sizeSp: Float) {
        _captionState.value = _captionState.value.copy(fontSizeSp = sizeSp)
    }

    fun setBackgroundOpacity(opacity: Float) {
        _captionState.value = _captionState.value.copy(backgroundOpacity = opacity)
    }

    fun setPositionTop(isTop: Boolean) {
        _captionState.value = _captionState.value.copy(isPositionTop = isTop)
    }

    fun clearSession() {
        sessionCues.clear()
        _captionState.value = _captionState.value.copy(
            currentOriginalText = "",
            currentTranslatedText = "",
            recentCues = emptyList(),
            statusMessage = "Ready"
        )
    }

    fun pushTranscript(originalText: String, fromSec: Float, toSec: Float) {
        val cleanText = originalText.trim()
        if (cleanText.isBlank()) return

        scope.launch {
            val target = _captionState.value.targetLanguage
            val source = _captionState.value.sourceLanguage
            val translated = SubtitleTranslator.translateText(cleanText, targetLang = target, sourceLang = source)

            val newCue = SubtitleCue(
                fromSeconds = fromSec,
                toSeconds = toSec,
                text = cleanText,
                translatedText = translated
            )

            synchronized(sessionCues) {
                sessionCues.add(newCue)
                if (sessionCues.size > 50) sessionCues.removeAt(0)
            }

            _captionState.value = _captionState.value.copy(
                currentOriginalText = cleanText,
                currentTranslatedText = translated,
                recentCues = sessionCues.takeLast(5),
                isTranscribing = true,
                statusMessage = "Live"
            )
        }
    }

    fun updateCurrentPlaybackPosition(positionMs: Long) {
        if (!_captionState.value.isEnabled) return
        val posSec = positionMs / 1000f

        synchronized(sessionCues) {
            val matchingCue = sessionCues.find { posSec >= it.fromSeconds && posSec <= it.toSeconds }
            if (matchingCue != null) {
                _captionState.value = _captionState.value.copy(
                    currentOriginalText = matchingCue.text,
                    currentTranslatedText = matchingCue.translatedText ?: matchingCue.text
                )
            }
        }
    }

    private fun startEngine(context: Context) {
        engineJob?.cancel()
        engineJob = scope.launch {
            val activeModelId = WhisperModelManager.activeModelId.value
            val isDownloaded = WhisperModelManager.isModelDownloaded(context, activeModelId)

            _captionState.value = _captionState.value.copy(
                activeModelId = activeModelId,
                isModelLoaded = isDownloaded,
                statusMessage = if (isDownloaded) "Whisper.cpp Engine Active" else "Model Not Downloaded"
            )

            if (!isDownloaded) {
                Log.i(TAG, "Whisper model '$activeModelId' not downloaded yet.")
                return@launch
            }

            Log.i(TAG, "Starting adaptive VAD & Whisper stream pipeline with model $activeModelId")
            WhisperInferenceEngine.start(context)
            _captionState.value = _captionState.value.copy(
                isTranscribing = true,
                statusMessage = "Live Transcription Active"
            )
        }
    }

    private fun stopEngine() {
        engineJob?.cancel()
        engineJob = null
        WhisperInferenceEngine.stop()
        _captionState.value = _captionState.value.copy(
            isTranscribing = false,
            statusMessage = "Paused"
        )
    }
}
