package com.example.plugin.manager

import android.content.Context
import com.example.plugin.bridge.HttpBridge
import com.example.plugin.runtime.PluginRuntime
import com.example.plugin.sdk.model.PluginManifest
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class VersionHistoryEntry(
    val pluginId: String,
    val version: String,
    val installedTimestamp: Long,
    val backupPath: String?
)

class ProviderUpdater(
    private val context: Context,
    private val pluginsDir: File,
    private val installer: ProviderInstaller
) {
    private val http = HttpBridge()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val manifestAdapter = moshi.adapter(PluginManifest::class.java)

    private val historyDir = File(context.filesDir, "butterfly_plugin_backups").apply { if (!exists()) mkdirs() }

    suspend fun checkPluginUpdate(runtime: PluginRuntime): UpdateInfo? = withContext(Dispatchers.IO) {
        val repoUrl = runtime.manifest.repository ?: return@withContext null
        try {
            val response = http.get(repoUrl)
            if (response.statusCode == 200) {
                val remoteManifest = manifestAdapter.fromJson(response.body)
                if (remoteManifest != null && isVersionGreater(remoteManifest.version, runtime.manifest.version)) {
                    return@withContext UpdateInfo(
                        pluginId = runtime.manifest.id,
                        currentVersion = runtime.manifest.version,
                        availableVersion = remoteManifest.version,
                        downloadUrl = "$repoUrl/download"
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    suspend fun performUpdate(pluginId: String, updateUrl: String): Boolean = withContext(Dispatchers.IO) {
        val pluginDir = File(pluginsDir, pluginId)
        if (pluginDir.exists()) {
            // Create backup before updating
            val backupTarget = File(historyDir, "${pluginId}_${System.currentTimeMillis()}.bak")
            pluginDir.copyRecursively(backupTarget, overwrite = true)
        }

        val result = installer.installFromUrl(updateUrl)
        return@withContext result.success
    }

    suspend fun rollback(pluginId: String): Boolean = withContext(Dispatchers.IO) {
        val backups = historyDir.listFiles { _, name -> name.startsWith("${pluginId}_") && name.endsWith(".bak") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

        if (backups.isEmpty()) return@withContext false

        val latestBackup = backups.first()
        val pluginDir = File(pluginsDir, pluginId)
        pluginDir.deleteRecursively()
        latestBackup.copyRecursively(pluginDir, overwrite = true)
        latestBackup.deleteRecursively()
        true
    }

    private fun isVersionGreater(v1: String, v2: String): Boolean {
        return try {
            val p1 = v1.split(".").map { it.toInt() }
            val p2 = v2.split(".").map { it.toInt() }
            for (i in 0 until maxOf(p1.size, p2.size)) {
                val n1 = p1.getOrElse(i) { 0 }
                val n2 = p2.getOrElse(i) { 0 }
                if (n1 > n2) return true
                if (n1 < n2) return false
            }
            false
        } catch (e: Exception) {
            v1 > v2
        }
    }
}
