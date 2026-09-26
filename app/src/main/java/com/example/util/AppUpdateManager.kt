package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
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
import java.util.concurrent.TimeUnit

data class GithubReleaseInfo(
    val tagName: String,
    val versionName: String,
    val versionCode: Int,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkSize: Long,
    val publishedAt: String
)

sealed class UpdateCheckState {
    object Idle : UpdateCheckState()
    object Checking : UpdateCheckState()
    data class UpdateAvailable(val release: GithubReleaseInfo) : UpdateCheckState()
    data class UpToDate(val currentVersion: String) : UpdateCheckState()
    data class Downloading(val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : UpdateCheckState()
    data class ReadyToInstall(val apkFile: File, val release: GithubReleaseInfo) : UpdateCheckState()
    data class Error(val message: String) : UpdateCheckState()
}

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    private const val GITHUB_REPO = "Morningstar-1-star/Butterfly-"
    private const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    private val _updateState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateState: StateFlow<UpdateCheckState> = _updateState.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    val currentVersionName: String
        get() = BuildConfig.VERSION_NAME

    val currentVersionCode: Int
        get() = BuildConfig.VERSION_CODE

    suspend fun checkForUpdates(context: Context): UpdateCheckState = withContext(Dispatchers.IO) {
        _updateState.value = UpdateCheckState.Checking
        try {
            val req = Request.Builder()
                .url(RELEASES_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Butterfly-App-Updater")
                .build()

            val responseBody = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    if (resp.code == 404) {
                        val state = UpdateCheckState.UpToDate(currentVersionName)
                        _updateState.value = state
                        return@withContext state
                    }
                    throw Exception("GitHub API error: ${resp.code}")
                }
                resp.body?.string() ?: throw Exception("Empty response from GitHub")
            }

            val json = JSONObject(responseBody)
            val tagName = json.optString("tag_name", "").trim()
            val releaseTitle = json.optString("name", tagName).ifBlank { tagName }
            val releaseNotes = json.optString("body", "No release notes provided.").trim()
            val publishedAt = json.optString("published_at", "")

            // Parse asset APK
            val assets = json.optJSONArray("assets")
            var apkUrl = ""
            var apkSize = 0L

            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "").lowercase()
                    val downloadUrl = asset.optString("browser_download_url", "")
                    if (name.endsWith(".apk") || downloadUrl.endsWith(".apk")) {
                        apkUrl = downloadUrl
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            if (apkUrl.isBlank()) {
                val state = UpdateCheckState.UpToDate(currentVersionName)
                _updateState.value = state
                return@withContext state
            }

            // Extract numeric version from tag/title (e.g. "v1.6" -> "1.6", "6")
            val cleanVersionName = tagName.removePrefix("v").removePrefix("V").ifBlank { tagName }
            
            // Extract versionCode integer from release body, title, or tag
            var remoteVersionCode = extractVersionCode(json)
            if (remoteVersionCode <= 0) {
                // Infer from versionName if tag is e.g. "1.6" -> 6 or semver comparison
                remoteVersionCode = parseVersionNameToCode(cleanVersionName)
            }

            val isNewer = remoteVersionCode > currentVersionCode || isVersionNameNewer(cleanVersionName, currentVersionName)

            val releaseInfo = GithubReleaseInfo(
                tagName = tagName,
                versionName = cleanVersionName,
                versionCode = remoteVersionCode,
                releaseTitle = releaseTitle,
                releaseNotes = releaseNotes,
                apkDownloadUrl = apkUrl,
                apkSize = apkSize,
                publishedAt = publishedAt
            )

            val newState = if (isNewer) {
                UpdateCheckState.UpdateAvailable(releaseInfo)
            } else {
                UpdateCheckState.UpToDate(currentVersionName)
            }

            _updateState.value = newState
            return@withContext newState
        } catch (e: Exception) {
            Log.w(TAG, "Check update error: ${e.message}")
            val errorState = UpdateCheckState.Error("Unable to check updates: ${e.localizedMessage}")
            _updateState.value = errorState
            return@withContext errorState
        }
    }

    suspend fun downloadAndInstall(context: Context, release: GithubReleaseInfo) = withContext(Dispatchers.IO) {
        try {
            _updateState.value = UpdateCheckState.Downloading(0, 0L, release.apkSize)

            val req = Request.Builder()
                .url(release.apkDownloadUrl)
                .header("User-Agent", "Butterfly-App-Updater")
                .build()

            val response = httpClient.newCall(req).execute()
            if (!response.isSuccessful) {
                throw Exception("Failed to download update: HTTP ${response.code}")
            }

            val body = response.body ?: throw Exception("Empty download body")
            val totalBytes = if (body.contentLength() > 0) body.contentLength() else release.apkSize

            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, "butterfly_update_${release.versionName}.apk")
            if (apkFile.exists()) apkFile.delete()

            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalDownloaded = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead

                        val percent = if (totalBytes > 0) ((totalDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100) else 50
                        _updateState.value = UpdateCheckState.Downloading(percent, totalDownloaded, totalBytes)
                    }
                    output.flush()
                }
            }

            _updateState.value = UpdateCheckState.ReadyToInstall(apkFile, release)
            triggerApkInstallation(context, apkFile)
        } catch (e: Exception) {
            Log.e(TAG, "Download update error: ${e.message}", e)
            _updateState.value = UpdateCheckState.Error("Download failed: ${e.localizedMessage}")
        }
    }

    fun triggerApkInstallation(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists()) return

            val authority = "${context.packageName}.fileprovider"
            val contentUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Trigger install error: ${e.message}", e)
            _updateState.value = UpdateCheckState.Error("Could not launch package installer: ${e.localizedMessage}")
        }
    }

    fun resetState() {
        _updateState.value = UpdateCheckState.Idle
    }

    private fun extractVersionCode(json: JSONObject): Int {
        // Try parsing versionCode from release title or body if formatted like "versionCode=6" or "vc:6"
        val text = json.optString("body", "") + " " + json.optString("name", "") + " " + json.optString("tag_name", "")
        val vcMatch = Regex("""versionCode\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("""vc\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE).find(text)
        return vcMatch?.groupValues?.get(1)?.toIntOrNull() ?: -1
    }

    private fun parseVersionNameToCode(versionName: String): Int {
        val digits = versionName.replace(Regex("""[^0-9.]"""), "")
        val parts = digits.split(".").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            1 -> parts[0]
            2 -> parts[0] * 10 + parts[1]
            3 -> parts[0] * 100 + parts[1] * 10 + parts[2]
            else -> 0
        }
    }

    private fun isVersionNameNewer(remoteVer: String, currentVer: String): Boolean {
        val rParts = remoteVer.replace(Regex("""[^0-9.]"""), "").split(".").mapNotNull { it.toIntOrNull() }
        val cParts = currentVer.replace(Regex("""[^0-9.]"""), "").split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(rParts.size, cParts.size)

        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
