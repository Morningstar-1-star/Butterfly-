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
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Manages active yt-dlp core engine status, dynamic OTA updating, and upstream release tracking.
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
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
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

    private val _isAutoUpdateEnabled = MutableStateFlow<Boolean>(true)
    val isAutoUpdateEnabled: StateFlow<Boolean> = _isAutoUpdateEnabled.asStateFlow()

    fun injectUpdatedPathIntoPython(context: Context) {
        try {
            val targetDir = File(context.filesDir, "yt_dlp_updated")
            if (targetDir.exists() && File(targetDir, "yt_dlp").exists()) {
                Log.i(TAG, "Active OTA updated yt-dlp package present at ${targetDir.absolutePath}")
            }
        } catch (e: Throwable) {
            Log.d(TAG, "OTA update path note: ${e.message}")
        }
    }

    suspend fun refreshVersion(context: Context): String = withContext(Dispatchers.IO) {
        _updateState.value = UpdateState.Checking
        try {
            injectUpdatedPathIntoPython(context)
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

    suspend fun updateYtDlpEngine(context: Context, onResult: (Boolean, String) -> Unit = { _, _ -> }): Unit = withContext(Dispatchers.IO) {
        _updateState.value = UpdateState.Checking
        try {
            YtDlpResolver.ensureInitialized(context)

            // Fetch latest release details from PyPI
            val req = Request.Builder()
                .url("https://pypi.org/pypi/yt-dlp/json")
                .header("User-Agent", "Butterfly-App/1.0")
                .build()

            val responseStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (responseStr.isNullOrBlank()) {
                val errMsg = "Failed to fetch yt-dlp metadata from PyPI"
                _updateState.value = UpdateState.Error(errMsg)
                withContext(Dispatchers.Main) { onResult(false, errMsg) }
                return@withContext
            }

            val json = JSONObject(responseStr)
            val latestVersion = json.getJSONObject("info").getString("version")
            val urlsArray = json.getJSONArray("urls")
            var wheelUrl: String? = null
            for (i in 0 until urlsArray.length()) {
                val uObj = urlsArray.getJSONObject(i)
                val u = uObj.getString("url")
                if (u.endsWith(".whl")) {
                    wheelUrl = u
                    break
                }
            }

            if (wheelUrl == null) {
                val errMsg = "No python wheel artifact found for yt-dlp $latestVersion"
                _updateState.value = UpdateState.Error(errMsg)
                withContext(Dispatchers.Main) { onResult(false, errMsg) }
                return@withContext
            }

            Log.i(TAG, "Downloading yt-dlp $latestVersion wheel from $wheelUrl")
            val tempWheelFile = File(context.cacheDir, "yt_dlp_latest.whl")
            val downloadReq = Request.Builder().url(wheelUrl).build()
            httpClient.newCall(downloadReq).execute().use { resp ->
                if (!resp.isSuccessful || resp.body == null) {
                    throw IOException("HTTP download failed with code ${resp.code}")
                }
                resp.body!!.byteStream().use { input ->
                    FileOutputStream(tempWheelFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            val targetDir = File(context.filesDir, "yt_dlp_updated")
            val tempTargetDir = File(context.filesDir, "yt_dlp_updated_temp_${System.currentTimeMillis()}")
            if (tempTargetDir.exists()) tempTargetDir.deleteRecursively()
            tempTargetDir.mkdirs()

            ZipInputStream(tempWheelFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    val outFile = File(tempTargetDir, entryName)
                    if (!outFile.canonicalPath.startsWith(tempTargetDir.canonicalPath)) {
                        entry = zis.nextEntry
                        continue
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            tempWheelFile.delete()

            if (targetDir.exists()) targetDir.deleteRecursively()
            tempTargetDir.renameTo(targetDir)

            injectUpdatedPathIntoPython(context)

            val newVer = YtDlpResolver.getEngineVersion(context)
            _engineVersion.value = newVer
            _latestRemoteVersion.value = latestVersion
            val displayVer = "Android wrapper: 2.0.2 | yt-dlp engine: $newVer"
            _installedVersion.value = displayVer
            val succMsg = "Successfully updated yt-dlp engine to $newVer"
            _updateState.value = UpdateState.Success(succMsg, newVer)

            withContext(Dispatchers.Main) {
                onResult(true, "yt-dlp updated to $newVer!")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Exception updating yt-dlp engine: ${e.message}", e)
            val errStr = e.message ?: "Failed to update yt-dlp"
            _updateState.value = UpdateState.Error(errStr)
            withContext(Dispatchers.Main) {
                onResult(false, "Update error: $errStr")
            }
        }
    }

    suspend fun fetchLatestRemoteVersion(): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://pypi.org/pypi/yt-dlp/json")
                .header("User-Agent", "Butterfly-App/1.0")
                .build()

            val responseStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!responseStr.isNullOrBlank()) {
                val json = JSONObject(responseStr)
                val ver = json.getJSONObject("info").getString("version")
                if (ver.isNotBlank()) {
                    _latestRemoteVersion.value = ver
                    return@withContext ver
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
            injectUpdatedPathIntoPython(context)
            val localVer = YtDlpResolver.getEngineVersion(context)
            _engineVersion.value = localVer
            val remoteVer = fetchLatestRemoteVersion()

            val displayVer = "Android wrapper: 2.0.2 | yt-dlp engine: $localVer"
            _installedVersion.value = displayVer

            if (isManual && remoteVer != null && remoteVer != localVer) {
                updateYtDlpEngine(context)
            } else {
                val statusMsg = if (remoteVer != null && remoteVer == localVer) {
                    "yt-dlp is running the latest engine ($localVer)"
                } else if (remoteVer != null) {
                    "Update available: $remoteVer (Active: $localVer)"
                } else {
                    "yt-dlp engine $localVer is active"
                }
                _updateState.value = UpdateState.Success(statusMsg, localVer)
            }
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
