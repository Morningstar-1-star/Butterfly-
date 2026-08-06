package com.example.plugin.runtime

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.plugin.bridge.HttpBridge
import com.example.plugin.bridge.LoggingBridge
import com.example.plugin.bridge.StorageBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import com.example.plugin.security.PluginPermission
import com.example.plugin.security.PluginSandboxPermissions
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PluginRuntime(
    private val context: Context,
    val manifest: PluginManifest,
    private val pluginDir: File
) {

    val logger = LoggingBridge(manifest.id)
    val storage = StorageBridge(context, manifest.id)
    val http = HttpBridge()

    private val permissions: PluginSandboxPermissions

    init {
        val granted = manifest.permissions.mapNotNull { PluginPermission.fromCode(it) }.toSet()
        permissions = PluginSandboxPermissions(granted)
    }

    /**
     * Instantiates an active ContentProviderApi interface for this plugin.
     */
    fun createProviderApi(): ContentProviderApi {
        return SandboxedContentProvider(this)
    }

    private class SandboxedContentProvider(
        private val runtime: PluginRuntime
    ) : ContentProviderApi {

        override val providerId: String = runtime.manifest.id

        private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

        override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
            runtime.logger.d("Executing home(pageToken=$pageToken)")
            // Returns structured result or delegates to JS runtime entry
            PagedResult(items = emptyList())
        }

        override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
            runtime.logger.d("Executing search(query=$query, pageToken=$pageToken)")
            PagedResult(items = emptyList())
        }

        override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
            runtime.logger.d("Executing getVideo(idOrUrl=$idOrUrl)")
            PluginVideoItem(
                id = idOrUrl,
                title = "Video Details",
                uploaderName = "Provider Content",
                providerId = providerId
            )
        }

        override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
            runtime.logger.d("Executing getStreams(idOrUrl=$idOrUrl)")
            PluginStreamInfo(
                id = idOrUrl,
                url = idOrUrl,
                title = "Stream Info",
                channelName = "Provider"
            )
        }

        override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> = withContext(Dispatchers.IO) {
            PagedResult(items = emptyList())
        }

        override suspend fun getSubtitles(idOrUrl: String): List<PluginSubtitle> = withContext(Dispatchers.IO) {
            emptyList()
        }

        override suspend fun getChannel(channelIdOrUrl: String): PluginChannel = withContext(Dispatchers.IO) {
            PluginChannel(id = channelIdOrUrl, name = "Channel")
        }

        override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist = withContext(Dispatchers.IO) {
            PluginPlaylist(id = playlistIdOrUrl, title = "Playlist", uploaderName = "Uploader")
        }

        override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> = withContext(Dispatchers.IO) {
            emptyList()
        }
    }
}
