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

    private val _engineVersion = MutableStateFlow<String?>("2024.12.13")
    val engineVersion: StateFlow<String?> = _engineVersion.asStateFlow()

    private val _latestRemoteVersion = MutableStateFlow<String?>("Up to date")
    val latestRemoteVersion: StateFlow<String?> = _latestRemoteVersion.asStateFlow()

    private val _isAutoUpdateEnabled = MutableStateFlow<Boolean>(true)
    val isAutoUpdateEnabled: StateFlow<Boolean> = _isAutoUpdateEnabled.asStateFlow()

    fun injectUpdatedPathIntoPython(context: Context) {
        try {
            val updatedBaseDir = File(context.filesDir, "yt_dlp_updated")
            val targetDir = File(updatedBaseDir, "yt_dlp")
            val updatedBinary = File(updatedBaseDir, "yt-dlp")

            // 1. Set PYTHONPATH in process environment so child Python processes load updated modules & plugins
            val pythonPaths = mutableListOf<String>()
            if (targetDir.exists() && targetDir.isDirectory) {
                pythonPaths.add(updatedBaseDir.absolutePath)
            }
            pythonPaths.add(context.filesDir.absolutePath)
            try {
                val existingPythonPath = android.system.Os.getenv("PYTHONPATH")
                val combined = (pythonPaths + listOfNotNull(existingPythonPath?.takeIf { it.isNotBlank() }))
                    .distinct()
                    .joinToString(":")
                android.system.Os.setenv("PYTHONPATH", combined, true)
            } catch (e: Throwable) {
                Log.d(TAG, "PYTHONPATH setenv notice: ${e.message}")
            }

            // 2. Discover any yt-dlp binary or yt_dlp package folder inside noBackupFilesDir & filesDir and sync
            val searchRoots = listOfNotNull(context.noBackupFilesDir, context.filesDir)
            for (root in searchRoots) {
                if (!root.exists()) continue
                root.walkTopDown().maxDepth(4).forEach { file ->
                    try {
                        // Sync extracted package directories
                        if (file.isDirectory && file.name == "yt_dlp" &&
                            file.canonicalPath != targetDir.canonicalPath &&
                            targetDir.exists()
                        ) {
                            val marker = File(file, ".ota_synced_${_engineVersion.value}")
                            if (!marker.exists()) {
                                targetDir.copyRecursively(file, overwrite = true)
                                marker.writeText("ok")
                                Log.i(TAG, "Synced OTA updated yt_dlp package to: ${file.absolutePath}")
                            }
                        }
                        // Sync standalone zipapp binary if present (used by youtubedl-android / yt-dlp-android)
                        if (file.isFile && file.name == "yt-dlp" &&
                            file.canonicalPath != updatedBinary.canonicalPath &&
                            updatedBinary.exists() && updatedBinary.length() > 100_000L
                        ) {
                            if (file.length() != updatedBinary.length()) {
                                updatedBinary.copyTo(file, overwrite = true)
                                file.setExecutable(true, false)
                                file.setReadable(true, false)
                                Log.i(TAG, "Replaced standalone yt-dlp zipapp binary at: ${file.absolutePath}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Notice syncing yt-dlp target ${file.absolutePath}: ${e.message}")
                    }
                }
            }

            // 3. Sync plugin extractors (like noodlemagazine.py) from assets into proper yt_dlp_plugins/extractor namespace
            try {
                val assetMgr = context.assets
                val pluginFiles = assetMgr.list("yt_plugins/yt_dlp_plugins/extractor") ?: emptyArray()
                if (pluginFiles.isNotEmpty()) {
                    val candidatePluginBases = listOf(
                        File(context.filesDir, "yt-dlp"),
                        updatedBaseDir,
                        context.filesDir
                    )
                    for (baseDir in candidatePluginBases) {
                        val pluginNamespaceDir = File(baseDir, "yt_dlp_plugins/extractor")
                        pluginNamespaceDir.mkdirs()
                        val rootInit = File(baseDir, "yt_dlp_plugins/__init__.py")
                        if (!rootInit.exists()) {
                            try { rootInit.writeText("# yt-dlp plugins namespace\n") } catch (_: Exception) {}
                        }
                        for (pName in pluginFiles) {
                            if (pName.endsWith(".py")) {
                                val destPy = File(pluginNamespaceDir, pName)
                                if (!destPy.exists()) {
                                    assetMgr.open("yt_plugins/yt_dlp_plugins/extractor/$pName").use { input ->
                                        FileOutputStream(destPy).use { out -> input.copyTo(out) }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.d(TAG, "Plugin extractor injection notice: ${e.message}")
            }
        } catch (e: Throwable) {
            Log.d(TAG, "OTA update path notice: ${e.message}")
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

            var latestVersion: String? = null
            var wheelUrl: String? = null
            val githubZipappUrl = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp"

            // 1. Query GitHub Releases first (official upstream source: github.com/yt-dlp/yt-dlp)
            try {
                val ghReq = Request.Builder()
                    .url("https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest")
                    .header("User-Agent", "Butterfly-App/1.0")
                    .header("Accept", "application/vnd.github+json")
                    .build()
                httpClient.newCall(ghReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            val ghJson = JSONObject(body)
                            latestVersion = ghJson.optString("tag_name").takeIf { it.isNotBlank() }
                            val assets = ghJson.optJSONArray("assets")
                            if (assets != null) {
                                for (i in 0 until assets.length()) {
                                    val assetObj = assets.getJSONObject(i)
                                    val dlUrl = assetObj.optString("browser_download_url")
                                    if (dlUrl.endsWith(".whl")) {
                                        wheelUrl = dlUrl
                                        break
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "GitHub API release check fallback to PyPI: ${e.message}")
            }

            // 2. Fallback / supplement with PyPI metadata for wheel URL if needed
            if (latestVersion == null || wheelUrl == null) {
                val req = Request.Builder()
                    .url("https://pypi.org/pypi/yt-dlp/json")
                    .header("User-Agent", "Butterfly-App/1.0")
                    .build()

                val responseStr = httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }

                if (!responseStr.isNullOrBlank()) {
                    val json = JSONObject(responseStr)
                    if (latestVersion == null) {
                        latestVersion = json.getJSONObject("info").getString("version")
                    }
                    val urlsArray = json.getJSONArray("urls")
                    for (i in 0 until urlsArray.length()) {
                        val uObj = urlsArray.getJSONObject(i)
                        val u = uObj.getString("url")
                        if (u.endsWith(".whl")) {
                            wheelUrl = u
                            break
                        }
                    }
                }
            }

            if (latestVersion == null || wheelUrl == null) {
                val errMsg = "Failed to fetch yt-dlp release metadata from GitHub/PyPI"
                _updateState.value = UpdateState.Error(errMsg)
                withContext(Dispatchers.Main) { onResult(false, errMsg) }
                return@withContext
            }

            val targetDir = File(context.filesDir, "yt_dlp_updated")
            val tempTargetDir = File(context.filesDir, "yt_dlp_updated_temp_${System.currentTimeMillis()}")
            if (tempTargetDir.exists()) tempTargetDir.deleteRecursively()
            tempTargetDir.mkdirs()

            // 3. Download standalone GitHub zipapp binary for direct binary replacement
            try {
                val binReq = Request.Builder()
                    .url(githubZipappUrl)
                    .header("User-Agent", "Butterfly-App/1.0")
                    .build()
                httpClient.newCall(binReq).execute().use { resp ->
                    if (resp.isSuccessful && resp.body != null) {
                        val binFile = File(tempTargetDir, "yt-dlp")
                        resp.body!!.byteStream().use { input ->
                            FileOutputStream(binFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        binFile.setExecutable(true, false)
                        Log.i(TAG, "Downloaded standalone yt-dlp zipapp from GitHub Releases (${binFile.length()} bytes)")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Standalone zipapp download notice (will use wheel): ${e.message}")
            }

            // 4. Download and unpack wheel package for PYTHONPATH module injection
            Log.i(TAG, "Downloading yt-dlp $latestVersion wheel from $wheelUrl")
            val tempWheelFile = File(context.cacheDir, "yt_dlp_latest.whl")
            val downloadReq = Request.Builder().url(wheelUrl!!).build()
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

            _engineVersion.value = latestVersion
            injectUpdatedPathIntoPython(context)

            val newVer = YtDlpResolver.getEngineVersion(context)
            _engineVersion.value = newVer
            _latestRemoteVersion.value = latestVersion
            val displayVer = "Android wrapper: 2.0.2 | yt-dlp engine: $newVer"
            _installedVersion.value = displayVer
            val succMsg = "Successfully updated yt-dlp engine to $newVer (GitHub release)"
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
            val ghReq = Request.Builder()
                .url("https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest")
                .header("User-Agent", "Butterfly-App/1.0")
                .header("Accept", "application/vnd.github+json")
                .build()

            httpClient.newCall(ghReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val ver = JSONObject(body).optString("tag_name")
                        if (ver.isNotBlank()) {
                            _latestRemoteVersion.value = ver
                            return@withContext ver
                        }
                    }
                }
            }

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
                    "yt-dlp is running the latest GitHub release ($localVer)"
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
