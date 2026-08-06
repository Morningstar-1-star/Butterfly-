package com.example.plugin.manager

import android.content.Context
import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.model.PluginManifest
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipFile

data class InstallationResult(
    val success: Boolean,
    val pluginId: String? = null,
    val errorMessage: String? = null
)

class ProviderInstaller(
    private val context: Context,
    private val pluginsDir: File
) {
    private val http = HttpBridge()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val manifestAdapter = moshi.adapter(PluginManifest::class.java)

    /**
     * Resolves and installs a provider from various input source formats:
     * - Short IDs: "butterfly-org", "github:owner/repo"
     * - GitHub repo/release/gist URLs
     * - GitLab / Codeberg URLs
     * - Direct ZIP / Manifest URLs
     */
    suspend fun installFromSourceString(sourceStr: String, expectedPublicKey: String? = null): InstallationResult = withContext(Dispatchers.IO) {
        val clean = sourceStr.trim()
        val downloadUrl = resolveSourceToDownloadUrl(clean)
            ?: return@withContext InstallationResult(false, errorMessage = "Could not resolve download URL for source: $clean")

        return@withContext installFromUrl(downloadUrl, expectedPublicKey)
    }

    /**
     * Resolves short codes or repository URLs into direct download links.
     */
    fun resolveSourceToDownloadUrl(sourceStr: String): String? {
        val s = sourceStr.trim()
        return when {
            s.equals("butterfly-org", ignoreCase = true) ->
                "https://raw.githubusercontent.com/butterfly-app/plugins/main/official_pack.zip"

            s.startsWith("github:", ignoreCase = true) -> {
                val repoPath = s.substringAfter("github:").trim()
                "https://github.com/$repoPath/releases/latest/download/plugin.zip"
            }

            s.startsWith("https://github.com/", ignoreCase = true) -> {
                if (s.endsWith(".zip") || s.contains("/releases/download/")) {
                    s
                } else if (s.contains("/gist.github.com/")) {
                    "$s/archive/main.zip"
                } else {
                    val path = s.removePrefix("https://github.com/").removeSuffix("/")
                    "https://github.com/$path/releases/latest/download/plugin.zip"
                }
            }

            s.startsWith("https://gitlab.com/", ignoreCase = true) || s.startsWith("https://codeberg.org/", ignoreCase = true) -> {
                if (s.endsWith(".zip")) s else "$s/-/archive/main/repository.zip"
            }

            s.startsWith("http://", ignoreCase = true) || s.startsWith("https://", ignoreCase = true) -> {
                s
            }

            else -> null
        }
    }

    suspend fun installFromUrl(url: String, expectedPublicKey: String? = null): InstallationResult = withContext(Dispatchers.IO) {
        try {
            val response = http.get(url)
            if (response.statusCode != 200) {
                return@withContext InstallationResult(false, errorMessage = "HTTP error ${response.statusCode} fetching from $url")
            }

            if (url.endsWith("manifest.json") || response.body.trim().startsWith("{")) {
                // Direct manifest installation
                val manifest = try {
                    manifestAdapter.fromJson(response.body)
                } catch (e: Exception) {
                    null
                }
                if (manifest != null) {
                    val pluginFolder = File(pluginsDir, manifest.id).apply { if (!exists()) mkdirs() }
                    File(pluginFolder, "manifest.json").writeText(response.body)
                    return@withContext InstallationResult(true, pluginId = manifest.id)
                }
            }

            // Otherwise treat response body as ZIP stream
            val stream = response.body.byteInputStream()
            return@withContext installFromZipStream(stream, expectedPublicKey)
        } catch (e: Exception) {
            return@withContext InstallationResult(false, errorMessage = e.localizedMessage ?: "Installation failed")
        }
    }

    suspend fun installFromZipStream(inputStream: InputStream, expectedPublicKey: String? = null): InstallationResult = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "temp_installer_${System.currentTimeMillis()}.zip")
        try {
            FileOutputStream(tempFile).use { output ->
                inputStream.copyTo(output)
            }

            var manifest: PluginManifest? = null
            ZipFile(tempFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.endsWith("manifest.json")) {
                        val json = zip.getInputStream(entry).bufferedReader().readText()
                        manifest = manifestAdapter.fromJson(json)
                        break
                    }
                }
            }

            val validManifest = manifest
                ?: return@withContext InstallationResult(false, errorMessage = "No valid manifest.json found in ZIP bundle")

            val targetDir = File(pluginsDir, validManifest.id).apply { if (!exists()) mkdirs() }

            ZipFile(tempFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val fileName = if (entry.name.contains("/")) entry.name.substringAfterLast("/") else entry.name
                    if (fileName.isEmpty()) continue

                    val file = File(targetDir, fileName)
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
            return@withContext InstallationResult(true, pluginId = validManifest.id)
        } catch (e: Exception) {
            tempFile.delete()
            return@withContext InstallationResult(false, errorMessage = e.localizedMessage ?: "ZIP Extraction Error")
        }
    }
}
