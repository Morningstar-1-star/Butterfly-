package com.example.plugin.registry

import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.ProviderCapabilities

class ProviderCapabilityRegistry {

    private val registeredProviders = mutableMapOf<String, ContentProviderApi>()

    fun register(provider: ContentProviderApi) {
        registeredProviders[provider.providerId] = provider
    }

    fun unregister(providerId: String) {
        registeredProviders.remove(providerId)
    }

    fun getAllProviders(): List<ContentProviderApi> = registeredProviders.values.toList()

    fun getProvidersByCapability(predicate: (ProviderCapabilities) -> Boolean): List<ContentProviderApi> {
        return registeredProviders.values.filter { predicate(it.capabilities) }
    }

    fun getMovieProviders(): List<ContentProviderApi> = getProvidersByCapability { it.supportsMovie }
    
    fun getSeriesProviders(): List<ContentProviderApi> = getProvidersByCapability { it.supportsSeries }

    fun getAnimeProviders(): List<ContentProviderApi> = getProvidersByCapability { it.supportsAnime }

    fun getTorrentProviders(): List<ContentProviderApi> = getProvidersByCapability { it.supportsTorrent }

    fun getLiveProviders(): List<ContentProviderApi> = getProvidersByCapability { it.supportsLive }

    fun getSubtitleProviders(): List<ContentProviderApi> = getProvidersByCapability { it.supportsSubtitles }

    fun getSearchProviders(): List<ContentProviderApi> = getProvidersByCapability { it.supportsSearch }

    fun clear() {
        registeredProviders.clear()
    }
}
