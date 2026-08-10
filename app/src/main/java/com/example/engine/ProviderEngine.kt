package com.example.engine

import android.content.Context
import com.example.model.ProviderUiItem
import com.example.plugin.manager.ExtensionManager
import com.example.plugin.manager.PluginManager
import com.example.plugin.manager.ProviderHealthMonitor
import com.example.plugin.manager.ProviderHealthStatus
import com.example.plugin.manager.RepositoryManager
import com.example.plugin.sdk.api.ContentProviderApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProviderEngine(
    val context: Context,
    val pluginManager: PluginManager,
    val repositoryManager: RepositoryManager,
    val extensionManager: ExtensionManager
) {
    val healthMonitor = ProviderHealthMonitor()

    private val _enabledProviders = MutableStateFlow<List<ContentProviderApi>>(emptyList())
    val enabledProviders: StateFlow<List<ContentProviderApi>> = _enabledProviders.asStateFlow()

    private val _providerUiList = MutableStateFlow<List<ProviderUiItem>>(emptyList())
    val providerUiList: StateFlow<List<ProviderUiItem>> = _providerUiList.asStateFlow()

    init {
        refreshProviders()
    }

    fun refreshProviders() {
        val providers = pluginManager.capabilityRegistry.getAllProviders()
        _enabledProviders.value = providers

        val uiItems = providers.map { p ->
            val record = healthMonitor.getRecord(p.providerId)
            val isHealthy = record?.status != ProviderHealthStatus.OFFLINE
            val desc = if (isHealthy) "Active" else "Offline / Degraded"

            ProviderUiItem(
                id = p.providerId,
                name = p.providerId.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                description = desc,
                isEnabled = true,
                isDefault = false
            )
        }
        _providerUiList.value = uiItems
    }

    fun filterProvidersByCapability(
        requireTorrent: Boolean = false,
        requireSubtitles: Boolean = false
    ): List<ContentProviderApi> {
        return _enabledProviders.value.filter { p ->
            val caps = p.capabilities
            if (requireTorrent && !caps.supportsTorrent) return@filter false
            if (requireSubtitles && !caps.supportsSubtitles) return@filter false
            true
        }
    }
}
