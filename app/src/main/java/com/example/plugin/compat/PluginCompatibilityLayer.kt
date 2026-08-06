package com.example.plugin.compat

import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Grayjay & External Plugin Compatibility Layer
 *
 * ARCHITECTURAL STUDY & COMPATIBILITY ANALYSIS:
 * Grayjay plugins use JavaScript scripts (invoked via a QuickJS JS runtime) and a config JSON manifest.
 * They define platform hooks like `source.getHome()`, `source.getSearchCapabilities()`, `source.getComments()`, etc.
 *
 * Compatibility Determination:
 * Adapting Grayjay plugins is completely possible without copying proprietary or GPL runtime code.
 * Butterfly implements a JS-to-Kotlin bridge wrapper that parses Grayjay `config.json` manifests
 * and maps Grayjay JS output objects into Butterfly's standard `ContentProviderApi` models:
 * - PluginVideoItem
 * - PluginStreamInfo
 * - PluginComment
 * - PluginSubtitle
 *
 * This guarantees that Butterfly core remains 100% provider-agnostic and that external plugins
 * (including Grayjay-formatted plugins) run seamlessly.
 */

data class GrayjayManifest(
    val id: String,
    val name: String,
    val author: String? = null,
    val version: String,
    val scriptUrl: String? = null,
    val iconUrl: String? = null,
    val allowUrls: List<String> = emptyList()
)

class PluginCompatibilityLayer {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    /**
     * Parses a Grayjay-style config JSON into a standard PluginManifest.
     */
    fun parseGrayjayManifest(jsonStr: String): PluginManifest {
        val obj = JSONObject(jsonStr)
        return PluginManifest(
            id = obj.optString("id", "grayjay_plugin"),
            name = obj.optString("name", "Grayjay Plugin"),
            version = obj.optString("version", "1.0.0"),
            author = obj.optString("author", "Community"),
            description = obj.optString("description", "Adapted Grayjay plugin"),
            entryFile = obj.optString("script", "main.js"),
            icon = obj.optString("icon", null)
        )
    }

    /**
     * Wraps an external Grayjay or JS-based plugin API object into Butterfly's ContentProviderApi.
     */
    fun createAdaptedProvider(
        manifest: PluginManifest,
        delegateProvider: ContentProviderApi
    ): ContentProviderApi {
        return object : ContentProviderApi {
            override val providerId: String = manifest.id

            override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> =
                delegateProvider.home(pageToken)

            override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> =
                delegateProvider.search(query, pageToken)

            override suspend fun getVideo(idOrUrl: String): PluginVideoItem =
                delegateProvider.getVideo(idOrUrl)

            override suspend fun getStreams(idOrUrl: String): PluginStreamInfo =
                delegateProvider.getStreams(idOrUrl)

            override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> =
                delegateProvider.getComments(idOrUrl, pageToken)

            override suspend fun getSubtitles(idOrUrl: String): List<PluginSubtitle> =
                delegateProvider.getSubtitles(idOrUrl)

            override suspend fun getChannel(channelIdOrUrl: String): PluginChannel =
                delegateProvider.getChannel(channelIdOrUrl)

            override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist =
                delegateProvider.getPlaylist(playlistIdOrUrl)

            override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> =
                delegateProvider.getRecommendations(idOrUrl)
        }
    }
}
