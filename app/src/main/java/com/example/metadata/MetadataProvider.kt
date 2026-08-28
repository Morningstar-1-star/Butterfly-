package com.example.metadata

/**
 * Universal interface for JAV metadata providers (Javinizer, Javdex, AVM, OpenAver, MDCx, FSS).
 */
interface MetadataProvider {
    val id: String
    val name: String
    val isEnabled: Boolean get() = true
    val priority: Int get() = 50 // Higher = prioritized

    /**
     * Resolves complete metadata for a given JAV code.
     */
    suspend fun getMetadata(javCode: String): JavMetadata?

    /**
     * Searches metadata entries matching a text query (actor name, series, or keyword).
     */
    suspend fun search(query: String): List<JavMetadata> = emptyList()
}
