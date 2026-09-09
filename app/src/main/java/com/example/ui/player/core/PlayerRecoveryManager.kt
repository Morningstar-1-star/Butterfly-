package com.example.ui.player.core

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import java.util.Collections

/**
 * Manages player error diagnosis, failure URL tracking, and recovery decisions.
 */
class PlayerRecoveryManager {

    private val failedStreamUrls = Collections.synchronizedSet(mutableSetOf<String>())
    private var playbackFailedListener: ((Int?) -> Unit)? = null

    fun setPlaybackFailedListener(listener: ((Int?) -> Unit)?) {
        playbackFailedListener = listener
    }

    fun markStreamFailed(url: String?) {
        if (!url.isNullOrBlank()) {
            failedStreamUrls.add(url)
        }
    }

    fun isStreamFailed(url: String?): Boolean {
        return if (url.isNullOrBlank()) false else failedStreamUrls.contains(url)
    }

    fun clearFailedStreams() {
        failedStreamUrls.clear()
    }

    /**
     * Analyzes PlaybackException and returns a formatted diagnostic string plus HTTP status code if available.
     */
    fun diagnoseError(error: PlaybackException, activeProvider: String?): Pair<String, Int?> {
        val detailedError = StringBuilder()
        val errorCodeName = error.errorCodeName
        val rootCause = error.cause
        var httpStatus: Int? = null

        var currentCause: Throwable? = error
        while (currentCause != null) {
            if (currentCause is HttpDataSource.InvalidResponseCodeException) {
                httpStatus = currentCause.responseCode
                break
            }
            currentCause = currentCause.cause
        }

        when {
            httpStatus == 403 -> {
                detailedError.append("[HTTP 403 Forbidden]: Stream token or hotlink expired for provider '$activeProvider'")
            }
            httpStatus == 404 -> {
                detailedError.append("[HTTP 404 Not Found]: Stream file not found on remote server")
            }
            httpStatus != null && httpStatus >= 500 -> {
                detailedError.append("[HTTP $httpStatus Server Error]: Remote upstream server failure")
            }
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> {
                detailedError.append("[HARDWARE_DECODER_ERROR / $errorCodeName]: Media codec initialization failed")
            }
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                detailedError.append("[NETWORK_TIMEOUT / $errorCodeName]: Connection timed out while streaming")
            }
            else -> {
                detailedError.append("[MEDIA3_ERROR / $errorCodeName]: ${error.message ?: "Playback failure"}")
            }
        }

        if (rootCause != null && !detailedError.contains(rootCause.javaClass.simpleName)) {
            detailedError.append("\nCause: [${rootCause.javaClass.simpleName}] ${rootCause.message}")
        }
        if (httpStatus != null && !detailedError.contains("HTTP $httpStatus")) {
            detailedError.append(" (HTTP Status $httpStatus)")
        }

        playbackFailedListener?.invoke(httpStatus)
        return Pair(detailedError.toString(), httpStatus)
    }
}
