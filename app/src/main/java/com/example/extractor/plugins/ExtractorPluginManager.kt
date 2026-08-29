package com.example.extractor.plugins

import android.content.Context
import android.util.Log
import com.example.model.PlayableStreamOption
import com.example.model.StreamData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Extractor Plugin Definition Interface.
 * Plugins are independent extractors with isolated verification and execution.
 */
interface ExtractorPlugin {
    val id: String
    val name: String
    val version: String
    val isEnabled: Boolean
    fun canHandle(url: String): Boolean
    suspend fun extract(context: Context, url: String): StreamData?
}

/**
 * Universal Extractor Plugin Registry & Lifecycle Coordinator.
 * (Adapted from ClosedPort22/yt-dlp-plugins specifications)
 *
 * Coordinates specialized plugin extractors:
 * - HiAnime (pratikpatel8982/yt-dlp-hianime)
 * - AniWatch / Kaido (Tons-7/yt-dlp-aniwatchtv-kaido)
 * - Hanime (cynthia2006/hanime-plugin)
 * - Coomer (schmoaaaaah/yt-dlp-coomer)
 * - PMVHaven (Earthworm-Banana/yt-dlp-PMVHaven_com-plugin)
 * - PO-Token Engine (coletdjnz/yt-dlp-getpot-wpc)
 * - Remote Cipher (coletdjnz/yt-dlp-remote-cipher)
 */
object ExtractorPluginManager {

    private const val TAG = "ExtractorPluginManager"

    private val plugins = ConcurrentHashMap<String, ExtractorPlugin>()

    init {
        registerPlugin(HiAnimeExtractor())
        registerPlugin(AniWatchKaidoExtractor())
        registerPlugin(HanimeExtractor())
        registerPlugin(CoomerExtractor())
        registerPlugin(PMVHavenExtractor())
        PoTokenPlugin.initPlugin()
    }

    fun registerPlugin(plugin: ExtractorPlugin) {
        plugins[plugin.id] = plugin
    }

    fun register(plugin: ExtractorPlugin) {
        registerPlugin(plugin)
    }

    fun getPlugins(): List<ExtractorPlugin> = plugins.values.toList()

    fun findPluginForUrl(url: String): ExtractorPlugin? {
        return plugins.values.firstOrNull { it.isEnabled && it.canHandle(url) }
    }

    suspend fun tryExtractWithPlugin(context: Context, url: String): StreamData? = withContext(Dispatchers.IO) {
        val plugin = findPluginForUrl(url) ?: return@withContext null
        try {
            Log.i(TAG, "Attempting extraction via plugin ${plugin.name} for $url")
            plugin.extract(context, url)
        } catch (e: Exception) {
            Log.w(TAG, "Plugin ${plugin.name} failed: ${e.message}")
            null
        }
    }
}
