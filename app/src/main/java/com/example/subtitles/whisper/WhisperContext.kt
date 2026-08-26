package com.example.subtitles.whisper

import android.util.Log

class WhisperContext(val modelPath: String) : AutoCloseable {

    companion object {
        private const val TAG = "WhisperContext"
    }

    private var nativeHandle: Long = 0L

    init {
        if (WhisperJni.isAvailable()) {
            nativeHandle = WhisperJni.initContext(modelPath)
        } else {
            Log.w(TAG, "WhisperJni native binding unavailable")
        }
    }

    val isInitialized: Boolean get() = nativeHandle != 0L

    fun transcribe(samples16kHzMono: FloatArray, language: String = "auto"): Int {
        if (!isInitialized) return -1
        return WhisperJni.fullTranscribe(nativeHandle, samples16kHzMono, samples16kHzMono.size, language)
    }

    override fun close() {
        if (nativeHandle != 0L) {
            WhisperJni.freeContext(nativeHandle)
            nativeHandle = 0L
        }
    }
}
