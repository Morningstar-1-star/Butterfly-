package com.example.subtitles.whisper

import android.util.Log

object WhisperJni {
    private const val TAG = "WhisperJni"
    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("whisper_jni")
            isNativeLoaded = true
            Log.i(TAG, "Successfully loaded whisper_jni native library")
        } catch (e: Throwable) {
            Log.w(TAG, "Native whisper_jni library not present, operating in safe fallback mode: ${e.message}")
            isNativeLoaded = false
        }
    }

    fun isAvailable(): Boolean = isNativeLoaded

    external fun initContext(modelPath: String): Long
    external fun freeContext(handle: Long)
    external fun fullTranscribe(handle: Long, samples: FloatArray, nSamples: Int, language: String): Int
}
