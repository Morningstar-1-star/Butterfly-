package com.example.extractor

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Manages active yt-dlp core engine status and upstream release tracking.
 * Note: dev.ffmpegkit-maintained:yt-dlp-android packages the yt-dlp engine as an embedded asset.
 * Updates are provided through dependency releases.
 */
object YtDlpUpdateManager {
    private const val TAG = "YtDlpUpdateManager"

    sealed class UpdateState {
        object Idle : UpdateState()
        object Checking : UpdateState()
        data class Success(val message: String, val version: String) : UpdateState()
        data class Error(val errorMessage: String) : UpdateState()
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _installedVersion = MutableStateFlow<String?>("dev.ffmpegkit-maintained:yt-dlp-android:2.0.2")
    val installedVersion: StateFlow<String?> = _installedVersion.asStateFlow()

    private val _wrapperVersion = MutableStateFlow<String>("2.0.2")
    val wrapperVersion: StateFlow<String> = _wrapperVersion.asStateFlow()

    private val _engineVersion = MutableStateFlow<String?>("Checking...")
    val engineVersion: StateFlow<String?> = _engineVersion.asStateFlow()

    private val _latestRemoteVersion = MutableStateFlow<String?>("Checking...")
    val latestRemoteVersion: StateFlow<String?> = _latestRemoteVersion.asStateFlow()

    private val _isAutoUpdateEnabled = MutableStateFlow<Boolean>(false)
    val isAutoUpdateEnabled: StateFlow<Boolean> = _isAutoUpdateEnabled.asStateFlow()

    suspend fun refreshVersion(context: Context): String = withContext(Dispatchers.IO) {
        _updateState.value = UpdateState.Checking
        try {
            val engVer = YtDlpResolver.getEngineVersion(context)
            _engineVersion.value = engVer
            _wrapperVersion.value = "2.0.2"
            val displayVer = "Android wrapper: 2.0.2 | yt-dlp engine: $engVer"
            _installedVersion.value = displayVer
            _updateState.value = UpdateState.Success("yt-dlp core engine active", engVer)

            fetchLatestRemoteVersion()
            displayVer
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to get yt-dlp engine version: ${e.message}")
            val fallback = "Android wrapper: 2.0.2 | yt-dlp engine: 2024.12.13"
            _installedVersion.value = fallback
            _engineVersion.value = "2024.12.13"
            _updateState.value = UpdateState.Idle
            fallback
        }
    }

    suspend fun fetchLatestRemoteVersion(): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest")
                .header("User-Agent", "Butterfly-App/1.0")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val responseStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!responseStr.isNullOrBlank()) {
                val json = JSONObject(responseStr)
                val tagName = json.optString("tag_name", "").removePrefix("v")
                if (tagName.isNotBlank()) {
                    _latestRemoteVersion.value = tagName
                    return@withContext tagName
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch latest yt-dlp release: ${e.message}")
        }
        null
    }

    suspend fun checkForUpdates(context: Context, isManual: Boolean = false): Unit = withContext(Dispatchers.IO) {
        _updateState.value = UpdateState.Checking
        try {
            val localVer = YtDlpResolver.getEngineVersion(context)
            _engineVersion.value = localVer
            val remoteVer = fetchLatestRemoteVersion()

            val displayVer = "Android wrapper: 2.0.2 | yt-dlp engine: $localVer"
            _installedVersion.value = displayVer

            val statusMsg = if (remoteVer != null && remoteVer == localVer) {
                "yt-dlp is running the latest engine ($localVer)"
            } else {
                "yt-dlp engine $localVer is active"
            }
            _updateState.value = UpdateState.Success(statusMsg, localVer)
        } catch (e: Throwable) {
            Log.e(TAG, "Engine verification error: ${e.message}", e)
            _updateState.value = UpdateState.Error(e.message ?: "Failed to verify engine")
        }
    }

    fun setAutoUpdateEnabled(enabled: Boolean) {
        _isAutoUpdateEnabled.value = enabled
    }

    fun resetState() {
        _updateState.value = UpdateState.Idle
    }
}
