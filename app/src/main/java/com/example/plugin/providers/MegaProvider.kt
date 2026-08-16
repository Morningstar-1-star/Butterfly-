package com.example.plugin.providers

import android.content.Context
import android.util.Log
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import com.example.torrent.MegaStreamServer
import com.example.util.CloudFoldersSettingsManager
import com.example.util.MegaCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class MegaProvider(private val context: Context? = null) : ContentProviderApi {

    override val providerId: String = "mega"

    override val capabilities: ProviderCapabilities
        get() = ProviderCapabilities(
            supportsSearch = true,
            supportsMovie = true,
            supportsSeries = true,
            supportsAnime = false,
            supportsTorrent = false
        )

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "MegaProvider"
        // In-memory cache of decrypted mega items across user session
        val cachedNodes = ConcurrentHashMap<String, CachedMegaItem>()

        data class CachedMegaItem(
            val handle: String,
            val folderId: String,
            val fileName: String,
            val sizeBytes: Long,
            val keyBytes: ByteArray,
            val ivBytes: ByteArray,
            val rawDownloadUrl: String? = null
        )
    }

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val folderUrls = if (context != null) {
            CloudFoldersSettingsManager.getMegaFolderUrls(context)
        } else {
            emptyList()
        }

        val allItems = mutableListOf<PluginVideoItem>()

        if (folderUrls.isEmpty()) {
            return@withContext PagedResult(emptyList(), nextPageToken = null)
        }

        folderUrls.forEachIndexed { folderIdx, folderUrl ->
            try {
                val parsedLink = MegaCrypto.parseMegaUrl(folderUrl)
                if (parsedLink != null && parsedLink.folderId.isNotBlank()) {
                    val nodes = fetchAndDecryptFolder(parsedLink)
                    if (nodes.isNotEmpty()) {
                        nodes.forEach { nodeItem ->
                            allItems.add(nodeItem)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing Mega folder URL: $folderUrl", e)
            }
        }

        PagedResult(allItems, nextPageToken = null)
    }

    private suspend fun fetchAndDecryptFolder(linkInfo: MegaCrypto.MegaLinkInfo): List<PluginVideoItem> = withContext(Dispatchers.IO) {
        val resultList = mutableListOf<PluginVideoItem>()
        val masterKey = linkInfo.masterKey ?: return@withContext emptyList()

        try {
            // Start local stream server if context is available
            context?.let { MegaStreamServer.start(it) }

            val apiUrl = "https://g.mega.co.nz/cs?id=${(100000..999999).random()}&n=${linkInfo.folderId}"
            val payload = "[{\"a\":\"f\",\"c\":1,\"r\":1}]"
            val mediaType = "application/json".toMediaTypeOrNull()
            val requestBody = payload.toRequestBody(mediaType)

            val req = Request.Builder()
                .url(apiUrl)
                .post(requestBody)
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""

            if (resp.isSuccessful && body.startsWith("[")) {
                val jsonArray = JSONArray(body)
                if (jsonArray.length() > 0) {
                    val firstObj = jsonArray.optJSONObject(0)
                    val filesArray = firstObj?.optJSONArray("f")
                    if (filesArray != null && filesArray.length() > 0) {
                        // Map of handle to decrypted node
                        val nodeMap = mutableMapOf<String, MegaCrypto.MegaNode>()

                        for (i in 0 until filesArray.length()) {
                            val nodeJson = filesArray.optJSONObject(i) ?: continue
                            val handle = nodeJson.optString("h")
                            val parent = nodeJson.optString("p")
                            val type = nodeJson.optInt("t", 0) // 0 = file, 1 = folder
                            val size = nodeJson.optLong("s", 0L)
                            val attrB64 = nodeJson.optString("a")
                            val keyStr = nodeJson.optString("k")

                            if (handle.isNotBlank() && keyStr.isNotBlank()) {
                                val keyPair = MegaCrypto.decryptNodeKey(keyStr, masterKey)
                                if (keyPair != null) {
                                    val (keyBytes, ivBytes) = keyPair
                                    val fileName = if (attrB64.isNotBlank()) {
                                        MegaCrypto.decryptNodeAttributes(attrB64, keyBytes)
                                    } else ""

                                    val finalName = if (fileName.isNotBlank()) fileName else if (type == 1) "Folder $handle" else "File $handle"

                                    val node = MegaCrypto.MegaNode(
                                        handle = handle,
                                        parentHandle = parent,
                                        type = type,
                                        name = finalName,
                                        size = size,
                                        keyBytes = keyBytes,
                                        ivBytes = ivBytes,
                                        rawKeyArray = null
                                    )
                                    nodeMap[handle] = node

                                    // If it's a file, cache it for on-the-fly streaming
                                    if (type == 0) {
                                        cachedNodes[handle] = CachedMegaItem(
                                            handle = handle,
                                            folderId = linkInfo.folderId,
                                            fileName = finalName,
                                            sizeBytes = size,
                                            keyBytes = keyBytes,
                                            ivBytes = ivBytes
                                        )
                                    }
                                }
                            }
                        }

                        // Filter items: if subfolder specified in link, include nodes in that subfolder tree
                        val targetParent = linkInfo.subFolderId.ifBlank { "" }

                        nodeMap.values.forEach { node ->
                            if (node.type == 0) { // Video or Media file
                                val isInSubfolder = if (targetParent.isNotBlank()) {
                                    isDescendantOf(node, targetParent, nodeMap)
                                } else true

                                if (isInSubfolder) {
                                    val sizeMb = if (node.size > 0) node.size / (1024 * 1024) else 0L
                                    val sizeLabel = if (sizeMb > 0) "${sizeMb}MB" else ""

                                    resultList.add(
                                        PluginVideoItem(
                                            id = "mega_${linkInfo.folderId}_${node.handle}",
                                            title = node.name,
                                            uploaderName = "Mega Storage • $sizeLabel",
                                            viewCount = (100..5000).random().toLong(),
                                            durationSeconds = 0L,
                                            uploadDate = "2026-08-16",
                                            thumbnailUrl = "https://mega.nz/favicon.ico",
                                            providerId = providerId
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query Mega folder API: ${linkInfo.folderId}", e)
        }

        return@withContext resultList
    }

    private fun isDescendantOf(
        node: MegaCrypto.MegaNode,
        targetParentHandle: String,
        allNodes: Map<String, MegaCrypto.MegaNode>
    ): Boolean {
        var currentParent = node.parentHandle
        var depth = 0
        while (currentParent.isNotBlank() && depth < 20) {
            if (currentParent == targetParentHandle) return true
            val parentNode = allNodes[currentParent] ?: break
            currentParent = parentNode.parentHandle
            depth++
        }
        return false
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
                    title = "Mega Search: $query",
                    uploaderName = "Mega Storage",
                    durationSeconds = 0L,
                    thumbnailUrl = "https://mega.nz/favicon.ico",
                    providerId = providerId
                )
            )
        }

        PagedResult(resultList, nextPageToken = null)
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val cleanId = idOrUrl.replace("mega_", "")
        val parts = cleanId.split("_")
        val handle = if (parts.size > 1) parts[1] else parts[0]

        val cached = cachedNodes[handle]
        val title = cached?.fileName ?: "Mega Video Stream"
        val sizeMb = if ((cached?.sizeBytes ?: 0) > 0) cached!!.sizeBytes / (1024 * 1024) else 0L

        PluginVideoItem(
            id = idOrUrl,
            title = title,
            uploaderName = "Mega Storage" + if (sizeMb > 0) " • ${sizeMb}MB" else "",
            durationSeconds = 0L,
            thumbnailUrl = "https://mega.nz/favicon.ico",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        context?.let { MegaStreamServer.start(it) }

        val parts = idOrUrl.replace("mega_", "").split("_")
        val folderId = if (parts.size > 1) parts[0] else ""
        val handle = if (parts.size > 1) parts[1] else parts[0]

        var cached = cachedNodes[handle]

        // If not cached yet (e.g. user pasted direct link or opened fresh), fetch folder nodes
        if (cached == null && folderId.isNotBlank()) {
            val folderUrls = if (context != null) CloudFoldersSettingsManager.getMegaFolderUrls(context) else emptyList()
            val matchUrl = folderUrls.find { it.contains(folderId) }
            if (matchUrl != null) {
                val parsedLink = MegaCrypto.parseMegaUrl(matchUrl)
                if (parsedLink != null) {
                    fetchAndDecryptFolder(parsedLink)
                    cached = cachedNodes[handle]
                }
            }
        }

        val videoStreams = mutableListOf<PluginVideoStream>()

        if (cached != null) {
            // Register with local MegaStreamServer for native on-the-fly streaming to ExoPlayer
            val streamUrl = MegaStreamServer.registerStream(
                handle = cached.handle,
                folderId = cached.folderId,
                fileName = cached.fileName,
                sizeBytes = cached.sizeBytes,
                keyBytes = cached.keyBytes,
                ivBytes = cached.ivBytes
            )

            videoStreams.add(
                PluginVideoStream(
                    url = streamUrl,
                    qualityLabel = "Mega Native Stream (${cached.fileName.substringAfterLast('.', "Direct").uppercase()})",
                    format = "mp4",
                    isMuxed = true
                )
            )
        } else {
            // Fallback web embed stream
            val embedUrl = if (folderId.isNotBlank() && handle.isNotBlank()) {
                "https://mega.nz/embed/folder/$folderId/file/$handle"
            } else {
                "https://mega.nz/embed/$idOrUrl"
            }

            videoStreams.add(
                PluginVideoStream(
                    url = embedUrl,
                    qualityLabel = "Mega Web Stream",
                    format = "embed",
                    isMuxed = true
                )
            )
        }

        PluginStreamInfo(
            id = idOrUrl,
            url = videoStreams.firstOrNull()?.url ?: "",
            title = cached?.fileName ?: "Mega Video Stream",
            channelName = "Mega Storage",
            description = "Mega Cloud High Speed Video Player",
            videoStreams = videoStreams
        )
    }
}
