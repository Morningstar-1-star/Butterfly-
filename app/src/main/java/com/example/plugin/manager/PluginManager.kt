package com.example.plugin.manager

import android.content.Context
import com.example.plugin.bridge.HttpBridge
import com.example.plugin.runtime.PluginRuntime
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.PluginManifest
import com.example.plugin.security.PluginSignatureVerifier
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

import com.example.plugin.providers.*

data class UpdateInfo(
    val pluginId: String,
    val currentVersion: String,
    val availableVersion: String,
    val downloadUrl: String
)

class PluginManager(private val context: Context) {

    private val pluginsDir = File(context.filesDir, "butterfly_plugins").apply { if (!exists()) mkdirs() }
    private val signatureVerifier = PluginSignatureVerifier()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val manifestAdapter = moshi.adapter(PluginManifest::class.java)

    private val _activePlugins = MutableStateFlow<Map<String, PluginRuntime>>(emptyMap())
    val activePlugins: StateFlow<Map<String, PluginRuntime>> = _activePlugins.asStateFlow()

    private val nativeProviders = mutableMapOf<String, ContentProviderApi>()
    val capabilityRegistry = com.example.plugin.registry.ProviderCapabilityRegistry()

    init {
        registerNativeProvider(UnifiedTorrentProvider())
        registerNativeProvider(TorrentioAggregatorProvider())
        registerNativeProvider(AutoEmbedProvider())
        registerNativeProvider(VegaMultiProvider())
        registerNativeProvider(MediaFusionProvider())
        registerNativeProvider(CometProvider())
        registerNativeProvider(ZileanProvider())
        registerNativeProvider(VidSrcProvider())
        registerNativeProvider(OrionProvider())
        registerNativeProvider(EasyDebridProvider())
        registerNativeProvider(JackettProwlarrProvider())
        registerNativeProvider(SubtitleProvider())
        registerNativeProvider(YouTubeProvider())
        registerNativeProvider(TmdbTorrentProvider())
        registerNativeProvider(ArchiveOrgProvider())
        registerNativeProvider(EpornerProvider())
        registerNativeProvider(ApiJavServerProvider())
        registerNativeProvider(ApiJavHentaiProvider())
        registerNativeProvider(ApiJavPornProvider())
        registerNativeProvider(JavInfoProvider())
        registerNativeProvider(YtsTorrentProvider())
        registerNativeProvider(JikanAnimeProvider())
        registerNativeProvider(EztvTorrentProvider())
        registerNativeProvider(TorrentApiMultiProvider())
        registerNativeProvider(NyaaAnimeProvider())
        registerNativeProvider(AdultSwimProvider())
        registerNativeProvider(HotstarProvider())
        registerNativeProvider(DailymotionProvider())
        registerNativeProvider(DirectMp4Provider())
        registerNativeProvider(DirectHlsProvider())
        registerNativeProvider(RssVideoProvider())
        registerNativeProvider(JsonProvider())
        registerNativeProvider(MegaProvider(context))
        registerNativeProvider(TelegramProvider(context))
        registerNativeProvider(VimeoProvider())
        registerNativeProvider(TwitchProvider())
        registerNativeProvider(BilibiliProvider())
        registerNativeProvider(TikTokProvider())
        registerNativeProvider(NineGagProvider())
        registerNativeProvider(NewgroundsProvider())
        registerNativeProvider(MySpaceProvider())
        registerNativeProvider(TumblrProvider())
        registerNativeProvider(BlueskyProvider())
        registerNativeProvider(WeiboProvider())
        registerNativeProvider(OkRuProvider())
        registerNativeProvider(RutubeProvider())
        registerNativeProvider(BigoProvider())
        registerNativeProvider(ViuProvider())
        registerNativeProvider(VkProvider())
        registerNativeProvider(InstagramProvider())
        com.example.plugin.providers.AdultProviderRegistry.providers.forEach { provider ->
            registerNativeProvider(provider)
        }
    }

    fun getAllAvailableProviders(): List<ContentProviderApi> {
        val list = mutableListOf<ContentProviderApi>()
        list.addAll(nativeProviders.values)
        _activePlugins.value.values.forEach { runtime ->
            runtime.createProviderApi()?.let { list.add(it) }
        }
        return list
    }

    suspend fun loadInstalledPlugins() = withContext(Dispatchers.IO) {
        val loaded = mutableMapOf<String, PluginRuntime>()
        val pluginFolders = pluginsDir.listFiles { file -> file.isDirectory } ?: emptyArray()

        for (folder in pluginFolders) {
            val manifestFile = File(folder, "manifest.json")
            if (manifestFile.exists()) {
                try {
                    val manifestJson = manifestFile.readText()
                    val manifest = manifestAdapter.fromJson(manifestJson)
                    if (manifest != null) {
                        val runtime = PluginRuntime(context, manifest, folder)
                        loaded[manifest.id] = runtime
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        _activePlugins.value = loaded
    }

    suspend fun installPluginFromZip(inputStream: InputStream, expectedPublicKey: String? = null): Boolean = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "temp_plugin_${System.currentTimeMillis()}.zip")
        try {
            FileOutputStream(tempFile).use { output ->
                inputStream.copyTo(output)
            }

            // Extract manifest.json
            var manifest: PluginManifest? = null
            java.util.zip.ZipFile(tempFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name == "manifest.json") {
                        val json = zip.getInputStream(entry).bufferedReader().readText()
                        manifest = manifestAdapter.fromJson(json)
                        break
                    }
                }
            }

            val validManifest = manifest ?: return@withContext false

            if (expectedPublicKey != null && !signatureVerifier.verifySignature(validManifest, expectedPublicKey)) {
                throw SecurityException("Plugin signature verification failed for ${validManifest.id}")
            }

            val targetDir = File(pluginsDir, validManifest.id).apply { if (!exists()) mkdirs() }

            // Extract all files
            java.util.zip.ZipFile(tempFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val file = File(targetDir, entry.name)
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(file).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }

            tempFile.delete()
            loadInstalledPlugins()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            tempFile.delete()
            false
        }
    }

    suspend fun checkForUpdates(): List<UpdateInfo> = withContext(Dispatchers.IO) {
        val updates = mutableListOf<UpdateInfo>()
        val http = HttpBridge()

        for ((id, runtime) in _activePlugins.value) {
            val repoUrl = runtime.manifest.repository ?: continue
            try {
                val resp = http.get(repoUrl)
                if (resp.statusCode == 200) {
                    val remoteManifest = manifestAdapter.fromJson(resp.body)
                    if (remoteManifest != null && isVersionGreater(remoteManifest.version, runtime.manifest.version)) {
                        updates.add(
                            UpdateInfo(
                                pluginId = id,
                                currentVersion = runtime.manifest.version,
                                availableVersion = remoteManifest.version,
                                downloadUrl = "$repoUrl/download"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        updates
    }

    suspend fun installPluginFromUrl(downloadUrl: String, expectedPublicKey: String? = null): Boolean = withContext(Dispatchers.IO) {
        val http = HttpBridge()
        return@withContext try {
            val response = http.get(downloadUrl)
            if (response.statusCode == 200) {
                val stream = response.body.byteInputStream()
                installPluginFromZip(stream, expectedPublicKey)
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun installPluginFromGithub(owner: String, repo: String, releaseTag: String = "latest", expectedPublicKey: String? = null): Boolean = withContext(Dispatchers.IO) {
        val downloadUrl = if (releaseTag == "latest") {
            "https://github.com/$owner/$repo/releases/latest/download/plugin.zip"
        } else {
            "https://github.com/$owner/$repo/releases/download/$releaseTag/plugin.zip"
        }
        return@withContext installPluginFromUrl(downloadUrl, expectedPublicKey)
    }

    fun registerNativeProvider(provider: ContentProviderApi) {
        nativeProviders[provider.providerId] = provider
        capabilityRegistry.register(provider)
    }

    fun getProvider(pluginId: String): ContentProviderApi? {
        return nativeProviders[pluginId] ?: _activePlugins.value[pluginId]?.createProviderApi()
    }

    private fun isVersionGreater(v1: String, v2: String): Boolean {
        return try {
            val parts1 = v1.split(".").map { it.toInt() }
            val parts2 = v2.split(".").map { it.toInt() }
            for (i in 0 until maxOf(parts1.size, parts2.size)) {
                val p1 = parts1.getOrElse(i) { 0 }
                val p2 = parts2.getOrElse(i) { 0 }
                if (p1 > p2) return true
                if (p1 < p2) return false
            }
            false
        } catch (e: Exception) {
            v1 > v2
        }
    }
}
