package com.example.extractor

import android.content.Context
import android.util.Log
import dev.ffmpegkit_maintained.ytdlp.YtDlp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

object YtDlpUpdateManager {
    private const val TAG = "YtDlpUpdateManager"

    sealed class UpdateState {
        object Idle : UpdateState()
        object Checking : UpdateState()
        data class BundledInfo(val version: String, val libraryVersion: String = "2.0.2") : UpdateState()
        data class Error(val errorMessage: String, val throwable: Throwable? = null) : UpdateState()
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _installedVersion = MutableStateFlow<String?>("Checking...")
    val installedVersion: StateFlow<String?> = _installedVersion.asStateFlow()

    private val _engineStatus = MutableStateFlow<String>("yt-dlp-android 2.0.2 (Bundled AAR)")
    val engineStatus: StateFlow<String> = _engineStatus.asStateFlow()

    suspend fun refreshVersion(context: Context): String? = withContext(Dispatchers.IO) {
        _updateState.value = UpdateState.Checking
        try {
            YtDlpResolver.ensureInitialized(context)
            val displayVer = "2.0.2 (Bundled AAR)"
            _installedVersion.value = displayVer
            _updateState.value = UpdateState.BundledInfo(displayVer, "2.0.2")
            displayVer
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to get yt-dlp version: ${e.message}")
            _installedVersion.value = "yt-dlp 2.0.2"
            _updateState.value = UpdateState.Error(e.message ?: "Unknown error", e)
            null
        }
    }

    fun resetState() {
        _updateState.value = UpdateState.Idle
    }
}
