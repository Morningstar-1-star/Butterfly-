package com.example.plugin.manager

import android.content.Context
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.PluginManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

data class ExtensionStatus(
    val pluginId: String,
    val manifest: PluginManifest,
    val isEnabled: Boolean,
    val isInstalled: Boolean,
    val updateAvailable: Boolean = false,
    val latestVersion: String? = null
)

class ExtensionManager(
    private val context: Context,
    val pluginManager: PluginManager,
    val repositoryManager: RepositoryManager
) {
    private val pluginsDir = File(context.filesDir, "butterfly_plugins").apply { if (!exists()) mkdirs() }
    val installer = ProviderInstaller(context, pluginsDir)
    val updater = ProviderUpdater(context, pluginsDir, installer)

    private val _extensionStatuses = MutableStateFlow<List<ExtensionStatus>>(emptyList())
    val extensionStatuses: StateFlow<List<ExtensionStatus>> = _extensionStatuses.asStateFlow()

    suspend fun refreshExtensions() = withContext(Dispatchers.IO) {
        pluginManager.loadInstalledPlugins()
        val activeRuntimes = pluginManager.activePlugins.value
        val updates = pluginManager.checkForUpdates()
        val updateMap = updates.associateBy { it.pluginId }

        val statuses = activeRuntimes.map { (id, runtime) ->
            val updateInfo = updateMap[id]
            ExtensionStatus(
                pluginId = id,
                manifest = runtime.manifest,
                isEnabled = true,
                isInstalled = true,
                updateAvailable = (updateInfo != null),
                latestVersion = updateInfo?.availableVersion
            )
        }
        _extensionStatuses.value = statuses
    }

    suspend fun installExtensionFromSource(sourceStr: String): InstallationResult = withContext(Dispatchers.IO) {
        val result = installer.installFromSourceString(sourceStr)
        if (result.success) {
            refreshExtensions()
        }
        result
    }

    suspend fun installExtensionFromZip(stream: InputStream): Boolean = withContext(Dispatchers.IO) {
        val success = pluginManager.installPluginFromZip(stream)
        if (success) {
            refreshExtensions()
        }
        success
    }

    suspend fun uninstallExtension(pluginId: String): Boolean = withContext(Dispatchers.IO) {
        val folder = File(pluginsDir, pluginId)
        if (folder.exists()) {
            val deleted = folder.deleteRecursively()
            refreshExtensions()
            return@withContext deleted
        }
        false
    }

    suspend fun updateExtension(pluginId: String): Boolean = withContext(Dispatchers.IO) {
        val runtime = pluginManager.activePlugins.value[pluginId] ?: return@withContext false
        val updateInfo = updater.checkPluginUpdate(runtime) ?: return@withContext false
        val success = updater.performUpdate(pluginId, updateInfo.downloadUrl)
        if (success) {
            refreshExtensions()
        }
        success
    }

    suspend fun rollbackExtension(pluginId: String): Boolean = withContext(Dispatchers.IO) {
        val success = updater.rollback(pluginId)
        if (success) {
            refreshExtensions()
        }
        success
    }
}
