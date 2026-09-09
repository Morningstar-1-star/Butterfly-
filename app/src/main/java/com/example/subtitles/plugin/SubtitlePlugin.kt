package com.example.subtitles.plugin

import com.example.subtitles.SubtitleItem
import com.example.subtitles.SubtitleProvider
import com.example.subtitles.SubtitleSearchQuery

/**
 * Metadata descriptor for a subtitle provider plugin.
 */
data class SubtitlePluginInfo(
    val id: String,
    val name: String,
    val description: String,
    val author: String = "Butterfly Core",
    val version: String = "1.0.0",
    val requiresApiKey: Boolean = false,
    val apiKeyHelpUrl: String? = null,
    val isInstalled: Boolean = true,
    val isEnabled: Boolean = true,
    val supportsFps: Boolean = false,
    val supportsHi: Boolean = true,
    val supportedLanguages: List<String> = emptyList(), // Empty list implies all languages supported
    val websiteUrl: String? = null
)

/**
 * Lightweight Kotlin/HTTP Subtitle Provider Plugin contract.
 * Extends SubtitleProvider for seamless integration with the Butterfly player.
 */
interface SubtitlePlugin : SubtitleProvider {
    val info: SubtitlePluginInfo
    override val id: String get() = info.id
    override val name: String get() = info.name
    override val isEnabled: Boolean get() = info.isEnabled

    /**
     * Executes a lightweight health check/ping against the provider endpoint.
     * Returns Result.success with diagnostic message/latency, or Result.failure with error.
     */
    suspend fun testConnection(): Result<String>
}
