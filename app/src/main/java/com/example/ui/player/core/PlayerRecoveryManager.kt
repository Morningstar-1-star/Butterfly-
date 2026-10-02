package com.example.ui.player.core

import android.util.Log
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
        var httpStatus: Int? = null
        var httpMessage: String? = null
        var failedDataSpecUri: String? = null
        val causeList = mutableListOf<String>()

        var currentCause: Throwable? = error
        while (currentCause != null) {
            val causeClassName = currentCause.javaClass.simpleName
            val causeMsg = currentCause.message ?: ""
            causeList.add("$causeClassName: $causeMsg")

            if (currentCause is HttpDataSource.InvalidResponseCodeException) {
                httpStatus = currentCause.responseCode
                httpMessage = currentCause.responseMessage
                failedDataSpecUri = currentCause.dataSpec.uri.toString()
            }
            currentCause = currentCause.cause
        }

        when {
            httpStatus == 403 -> {
                val host = failedDataSpecUri?.let { runCatching { android.net.Uri.parse(it).host }.getOrNull() } ?: ""
                detailedError.append("[HTTP 403 Forbidden]: Stream authorization rejected")
                if (host.isNotBlank()) detailedError.append(" on $host")
                detailedError.append(" (Provider: '$activeProvider')")
            }
            httpStatus == 412 -> {
                detailedError.append("[HTTP 412 Precondition Failed]: CDN anti-hotlink check failed for '$activeProvider'")
            }
            httpStatus == 416 -> {
                detailedError.append("[HTTP 416 Range Not Satisfiable]: Requested byte range invalid on CDN")
            }
            httpStatus == 404 -> {
                detailedError.append("[HTTP 404 Not Found]: Stream file not found on remote server")
            }
            httpStatus != null && httpStatus in 500..599 -> {
                detailedError.append("[HTTP $httpStatus Server Error]: Remote server failure ($httpMessage)")
            }
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> {
                detailedError.append("[HARDWARE_DECODER_ERROR / $errorCodeName]: Media codec initialization failed")
            }
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                detailedError.append("[NETWORK_TIMEOUT / $errorCodeName]: Connection timed out while streaming")
            }
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> {
                detailedError.append("[PARSER_ERROR / $errorCodeName]: Media stream container format unsupported or malformed")
            }
            else -> {
                detailedError.append("[MEDIA3_ERROR / $errorCodeName]: ${error.message ?: "Playback failure"}")
            }
        }

        if (causeList.isNotEmpty()) {
            detailedError.append("\nCause Chain:\n - ").append(causeList.joinToString("\n - "))
        }

        Log.e("PlaybackDiagnostics", "Playback Exception Surface: errorCode=${error.errorCode}($errorCodeName), httpStatus=$httpStatus, failedUri=$failedDataSpecUri\nDiagnostic: $detailedError")

        playbackFailedListener?.invoke(httpStatus)
        return Pair(detailedError.toString(), httpStatus)
    }
}

