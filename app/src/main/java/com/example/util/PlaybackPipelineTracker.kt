package com.example.util

import android.util.Log

object PlaybackPipelineTracker {
    private const val TAG = "PlaybackPipeline"

    @Volatile
    private var extractionStartTimeMs: Long = 0L

    @Volatile
    private var currentVideoId: String = ""

    fun logExtractionStart(videoId: String, url: String) {
        extractionStartTimeMs = System.currentTimeMillis()
        currentVideoId = videoId
        Log.i(TAG, "[EXTRACTION_START] videoId=$videoId, url=$url (t=0ms)")
    }

    fun logNewpipeResult(progressiveCount: Int, adaptiveCount: Int) {
        val elapsed = if (extractionStartTimeMs > 0) System.currentTimeMillis() - extractionStartTimeMs else 0
        Log.i(TAG, "[NEWPIPE_RESULT] t=+${elapsed}ms (progressive=$progressiveCount, adaptive=$adaptiveCount)")
    }

    fun logFormatSelected(label: String, isMuxed: Boolean, format: String, urlSnippet: String) {
        val elapsed = if (extractionStartTimeMs > 0) System.currentTimeMillis() - extractionStartTimeMs else 0
        Log.i(TAG, "[FORMAT_SELECTED] t=+${elapsed}ms, quality='$label', isMuxed=$isMuxed, format=$format, url=$urlSnippet")
    }

    fun logPrepare(urlSnippet: String, headersCount: Int) {
        val elapsed = if (extractionStartTimeMs > 0) System.currentTimeMillis() - extractionStartTimeMs else 0
        Log.i(TAG, "[PREPARE] t=+${elapsed}ms, url=$urlSnippet, headersCount=$headersCount")
    }

    fun logFirstFrame(durationMs: Long) {
        val elapsed = if (extractionStartTimeMs > 0) System.currentTimeMillis() - extractionStartTimeMs else 0
        Log.i(TAG, "[FIRST_FRAME] t=+${elapsed}ms, First video frame rendered! Duration=${durationMs}ms (${formatDuration(durationMs)})")
    }

    fun logPlaybackError(
        errorCodeName: String,
        errorCode: Int,
        message: String?,
        causeName: String?,
        causeMessage: String?,
        httpStatus: Int?
    ) {
        val elapsed = if (extractionStartTimeMs > 0) System.currentTimeMillis() - extractionStartTimeMs else 0
        Log.e(TAG, "[PLAYBACK_ERROR] t=+${elapsed}ms, Media3 Error: [$errorCodeName / $errorCode], msg=$message, httpStatus=$httpStatus, cause=$causeName: $causeMessage")
    }

    private fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0) return "00:00"
        val totalSecs = durationMs / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return String.format("%02d:%02d", mins, secs)
    }
}
