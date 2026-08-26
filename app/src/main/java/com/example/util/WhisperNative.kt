package com.example.util

import android.util.Log

/**
 * JNI bridge for on-device whisper.cpp speech recognition inference.
 * Directly interfaces with native GGML / Whisper context.
 */
object WhisperNative {
    private const val TAG = "WhisperNative"
    private var isLoaded = false

    init {
        try {
            System.loadLibrary("whisper")
            isLoaded = true
            Log.i(TAG, "Native whisper library loaded successfully.")
        } catch (e: Throwable) {
            isLoaded = false
            Log.w(TAG, "Native whisper library load deferred: ${e.message}")
        }
    }

    fun isAvailable(): Boolean = isLoaded

    external fun initContext(modelPath: String): Long
    external fun freeContext(contextPtr: Long)
    external fun abortInference(contextPtr: Long)
    external fun fullTranscribe(
        contextPtr: Long,
        audioData: FloatArray,
        nThreads: Int,
        language: String,
        translate: Boolean
    ): Int
    external fun getTextSegmentCount(contextPtr: Long): Int
    external fun getTextSegment(contextPtr: Long, index: Int): String
    external fun getTextSegmentT0(contextPtr: Long, index: Int): Long
    external fun getTextSegmentT1(contextPtr: Long, index: Int): Long

    data class TimestampedSegment(
        val text: String,
        val startMs: Long,
        val endMs: Long
    )

    fun getSegments(contextPtr: Long): List<TimestampedSegment> {
        if (contextPtr == 0L || !isLoaded) return emptyList()
        val count = getTextSegmentCount(contextPtr)
        val list = mutableListOf<TimestampedSegment>()
        for (i in 0 until count) {
            val text = getTextSegment(contextPtr, i)
            val t0 = getTextSegmentT0(contextPtr, i)
            val t1 = getTextSegmentT1(contextPtr, i)
            list.add(TimestampedSegment(text, t0, t1))
        }
        return list
    }

    /**
     * High level helper to perform transcription on 16kHz mono float audio.
     */
    fun transcribe(
        contextPtr: Long,
        samples: FloatArray,
        language: String = "auto",
        translate: Boolean = false,
        nThreads: Int = 4
    ): String {
        if (contextPtr == 0L || !isLoaded) return ""
        try {
            val result = fullTranscribe(contextPtr, samples, nThreads, language, translate)
            if (result != 0) {
                Log.w(TAG, "Whisper transcription returned code $result")
                return ""
            }
            val count = getTextSegmentCount(contextPtr)
            val sb = StringBuilder()
            for (i in 0 until count) {
                sb.append(getTextSegment(contextPtr, i)).append(" ")
            }
            return sb.toString().trim()
        } catch (e: Exception) {
            Log.e(TAG, "Inference error: ${e.message}")
            return ""
        }
    }
}
