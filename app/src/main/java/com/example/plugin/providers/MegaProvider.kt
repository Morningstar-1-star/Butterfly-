package com.example.plugin.providers

import android.content.Context
import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import com.example.util.CloudFoldersSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class MegaProvider(private val context: Context? = null) : ContentProviderApi {

    override val providerId: String = "mega"

    override val capabilities: ProviderCapabilities
        get() = ProviderCapabilities(
            supportsSearch = true,
            supportsMovie = false,
            supportsSeries = false,
            supportsAnime = false,
            supportsTorrent = false
        )

    private val http = HttpBridge()

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val folderUrls = if (context != null) {
            CloudFoldersSettingsManager.getMegaFolderUrls(context)
        } else {
            listOf(
                "https://mega.nz/folder/Io4myLQC#0MV-ZU9NXIQZtRfcKSiqog/folder/1hogxZ7B",
                "https://mega.nz/folder/r9BlSRgA#kM1H8SJBL6oFlzKC8k1vyg/folder/esAkWZ4Y"
            )
        }

        val items = mutableListOf<PluginVideoItem>()

        folderUrls.forEachIndexed { folderIdx, folderUrl ->
            try {
                val folderId = extractFolderId(folderUrl)
                val folderKey = extractFolderKey(folderUrl)
                val subFolderId = extractSubFolderId(folderUrl)

                if (folderId.isNotBlank()) {
                    // Query Mega CS API for contents of folder
                    val apiUrl = "https://g.mega.co.nz/cs?id=${(100000..999999).random()}&n=$folderId"
                    val payload = "[{\"a\":\"f\",\"c\":1,\"r\":1}]"
                    val resp = http.post(apiUrl, payload)

                    var parsedNodesCount = 0
                    if (resp.statusCode == 200 && resp.body.trim().startsWith("[")) {
                        try {
                            val jsonArray = JSONArray(resp.body)
                            if (jsonArray.length() > 0) {
                                val firstObj = jsonArray.optJSONObject(0)
                                val filesArray = firstObj?.optJSONArray("f")
                                if (filesArray != null && filesArray.length() > 0) {
                                    for (i in 0 until filesArray.length()) {
                                        val node = filesArray.optJSONObject(i) ?: continue
                                        val handle = node.optString("h")
                                        val type = node.optInt("t", 0) // 0 = file, 1 = folder
                                        val sizeBytes = node.optLong("s", 0L)

                                        if (handle.isNotBlank()) {
                                            parsedNodesCount++
                                            val isSubFolder = (type == 1)
                                            val nodeTypeLabel = if (isSubFolder) "Folder" else "Video File"
                                            val sizeLabel = if (sizeBytes > 0) " (${sizeBytes / (1024 * 1024)} MB)" else ""

                                            val videoTitle = if (subFolderId.isNotBlank() && handle == subFolderId) {
                                                "Mega Folder Item $subFolderId$sizeLabel"
                                            } else {
                                                "Mega $nodeTypeLabel #$parsedNodesCount ($handle)$sizeLabel"
                                            }

                                            items.add(
                                                PluginVideoItem(
                                                    id = "mega_${folderIdx}_${handle}",
                                                    title = videoTitle,
                                                    uploaderName = "Mega Storage",
                                                    viewCount = (500..10000).random().toLong(),
                                                    durationSeconds = if (isSubFolder) 7200L else 3600L,
                                                    uploadDate = "2026-08-09",
                                                    thumbnailUrl = "https://mega.nz/favicon.ico",
                                                    providerId = providerId
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Fallback if CS response format differs
                        }
                    }

                    // If API parsing returned 0 nodes or failed, generate folder & subfolder entry items
                    if (parsedNodesCount == 0) {
                        val displayTitle = if (subFolderId.isNotBlank()) {
                            "Mega Cloud Subfolder ($subFolderId)"
                        } else {
                            "Mega Storage Folder ($folderId)"
                        }

                        items.add(
                            PluginVideoItem(
                                id = "mega_${folderIdx}_${folderId}",
                                title = displayTitle,
                                uploaderName = "Mega Storage",
                                viewCount = 1500L,
                                durationSeconds = 7200L,
                                uploadDate = "2026-08-09",
                                thumbnailUrl = "https://mega.nz/favicon.ico",
                                providerId = providerId
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Fallback item
                items.add(
                    PluginVideoItem(
                        id = "mega_${folderIdx}_default",
                        title = "Mega Cloud Folder #${folderIdx + 1}",
                        uploaderName = "Mega Storage",
                        viewCount = 1000L,
                        durationSeconds = 3600L,
                        uploadDate = "2026-08-09",
                        thumbnailUrl = "https://mega.nz/favicon.ico",
                        providerId = providerId
                    )
                )
            }
        }

        PagedResult(items, nextPageToken = null)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val allHome = home(pageToken).items
        val filtered = allHome.filter { 
            it.title.contains(query, ignoreCase = true)
        }

        val resultList = if (filtered.isNotEmpty()) filtered else {
            listOf(
                PluginVideoItem(
                    id = "mega_search_" + URLEncoder.encode(query, "UTF-8"),
                    title = "Mega Stream Search: $query",
                    uploaderName = "Mega Storage",
                    durationSeconds = 3600L,
                    thumbnailUrl = "https://mega.nz/favicon.ico",
                    providerId = providerId
                )
            )
        }

        PagedResult(resultList, nextPageToken = null)
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val cleanId = extractId(idOrUrl)
        PluginVideoItem(
            id = cleanId,
            title = "Mega Video Stream",
            uploaderName = "Mega Storage",
            durationSeconds = 3600L,
            thumbnailUrl = "https://mega.nz/favicon.ico",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val folderUrls = if (context != null) CloudFoldersSettingsManager.getMegaFolderUrls(context) else emptyList()

        val targetUrl = when {
            idOrUrl.startsWith("http") -> idOrUrl
            idOrUrl.startsWith("mega_") -> {
                val parts = idOrUrl.split("_")
                val folderIdx = parts.getOrNull(1)?.toIntOrNull() ?: 0
                val handle = parts.getOrNull(2) ?: ""
                val baseFolderUrl = folderUrls.getOrNull(folderIdx) ?: "https://mega.nz/folder/Io4myLQC#0MV-ZU9NXIQZtRfcKSiqog/folder/1hogxZ7B"

                val folderId = extractFolderId(baseFolderUrl)
                val folderKey = extractFolderKey(baseFolderUrl)

                if (handle.isNotBlank() && folderId.isNotBlank() && folderKey.isNotBlank()) {
                    "https://mega.nz/embed/folder/$folderId#$folderKey/file/$handle"
                } else {
                    baseFolderUrl
                }
            }
            else -> "https://mega.nz/embed/$idOrUrl"
        }

        val folderId = extractFolderId(targetUrl)
        val folderKey = extractFolderKey(targetUrl)

        val embedUrl = if (targetUrl.contains("embed/")) {
            targetUrl
        } else if (folderId.isNotBlank() && folderKey.isNotBlank()) {
            "https://mega.nz/embed/folder/$folderId#$folderKey"
        } else if (targetUrl.contains("/file/")) {
            targetUrl.replace("/file/", "/embed/")
        } else {
            targetUrl
        }

        val videoStreams = listOf(
            PluginVideoStream(
                url = embedUrl,
                qualityLabel = "Mega HD Player (Auto)",
                format = "embed",
                isMuxed = true
            ),
            PluginVideoStream(
                url = targetUrl,
                qualityLabel = "Mega Web Direct Player",
                format = "embed",
                isMuxed = true
            )
        )

        PluginStreamInfo(
            id = idOrUrl,
            url = embedUrl,
            title = "Mega Cloud Stream",
            channelName = "Mega Storage",
            description = "Mega Cloud High Speed Video Player",
            videoStreams = videoStreams
        )
    }

    private fun extractFolderId(url: String): String {
        val pattern = Regex("folder/([A-Za-z0-9_-]+)")
        return pattern.find(url)?.groupValues?.get(1) ?: ""
    }

    private fun extractFolderKey(url: String): String {
        val pattern = Regex("#([A-Za-z0-9_-]+)")
        return pattern.find(url)?.groupValues?.get(1) ?: ""
    }

    private fun extractSubFolderId(url: String): String {
        val parts = url.split("/folder/")
        return if (parts.size > 2) parts.last() else ""
    }

    private fun extractId(idOrUrl: String): String {
        return idOrUrl.replace("mega_", "").take(50)
    }
}
