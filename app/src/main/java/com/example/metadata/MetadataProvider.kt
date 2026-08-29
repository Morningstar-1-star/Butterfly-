package com.example.metadata

/**
 * Universal canonical contract for JAV metadata providers (Javinizer-Go, JAVapi, JavBus, Javdex, AVM, OpenAver, MDCx, FSS).
 */
interface MetadataProvider {
    val id: String
    val name: String
    val classification: ProviderClassification get() = ProviderClassification.SCRAPER
    val isEnabled: Boolean get() = true
    val priority: Int get() = 50 // Higher = prioritized

    /**
     * Resolves complete metadata for a given JAV code.
     * Returns null if not found or if the provider is unreachable.
     */
    suspend fun getMetadata(javCode: String): JavMetadata?

    /**
     * Searches metadata entries matching a text query (actor name, series, or keyword).
     */
    suspend fun search(query: String): List<JavMetadata> = emptyList()

    /**
     * Performs a lightweight status/availability check for the provider.
     */
    suspend fun checkStatus(): Boolean = isEnabled
}

