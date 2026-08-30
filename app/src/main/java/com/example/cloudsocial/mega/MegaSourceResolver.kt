package com.example.cloudsocial.mega

import android.util.Base64
import com.example.cloudsocial.db.CloudSocialMediaEntity
import com.example.cloudsocial.db.CloudSocialSourceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class MegaUrlInfo(
    val id: String,
    val key: String,
    val isFolder: Boolean
)

class MegaSourceResolver {

    companion object {
        private val MEGA_FILE_PATTERN = Pattern.compile("https?://mega\\.nz/file/([a-zA-Z0-9_-]+)#([a-zA-Z0-9_-]+)")
        private val MEGA_FOLDER_PATTERN = Pattern.compile("https?://mega\\.nz/folder/([a-zA-Z0-9_-]+)#([a-zA-Z0-9_-]+)")
        private val MEGA_OLD_FOLDER_PATTERN = Pattern.compile("https?://mega\\.nz/#F!([a-zA-Z0-9_-]+)!([a-zA-Z0-9_-]+)")

        fun parseUrl(input: String): MegaUrlInfo? {
            val clean = input.trim()

            val folderMatcher = MEGA_FOLDER_PATTERN.matcher(clean)
            if (folderMatcher.find()) {
                return MegaUrlInfo(
                    id = folderMatcher.group(1) ?: "",
                    key = folderMatcher.group(2) ?: "",
                    isFolder = true
                )
            }

            val oldFolderMatcher = MEGA_OLD_FOLDER_PATTERN.matcher(clean)
            if (oldFolderMatcher.find()) {
                return MegaUrlInfo(
                    id = oldFolderMatcher.group(1) ?: "",
                    key = oldFolderMatcher.group(2) ?: "",
                    isFolder = true
                )
            }

            val fileMatcher = MEGA_FILE_PATTERN.matcher(clean)
            if (fileMatcher.find()) {
                return MegaUrlInfo(
                    id = fileMatcher.group(1) ?: "",
                    key = fileMatcher.group(2) ?: "",
                    isFolder = false
                )
            }

            return null
        }
    }

    suspend fun scanSource(source: CloudSocialSourceEntity): List<CloudSocialMediaEntity> = withContext(Dispatchers.IO) {
        val parsed = parseUrl(source.sourceUrl) ?: return@withContext emptyList()
        val results = mutableListOf<CloudSocialMediaEntity>()

        if (parsed.isFolder) {
            val folderFiles = fetchMegaFolderNodes(parsed.id, parsed.key, source)
            results.addAll(folderFiles)
        } else {
            val fileItem = fetchMegaFileMetadata(parsed.id, parsed.key, source)
            if (fileItem != null) results.add(fileItem)
        }

        return@withContext results
    }

    private fun fetchMegaFolderNodes(folderId: String, key: String, source: CloudSocialSourceEntity): List<CloudSocialMediaEntity> {
        val mediaList = mutableListOf<CloudSocialMediaEntity>()
        try {
            val requestUrl = "https://g.api.mega.co.nz/cs?id=${System.currentTimeMillis()}&n=$folderId"
            val payload = JSONArray().put(JSONObject().apply {
                put("a", "f")
                put("c", 1)
                put("r", 1)
                put("ca", 1)
            }).toString()

            val connection = URL(requestUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.write(payload.toByteArray(Charsets.UTF_8))

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(responseText)
            if (jsonArray.length() > 0) {
                val obj = jsonArray.getJSONObject(0)
                val files = obj.optJSONArray("f") ?: JSONArray()
                for (i in 0 until files.length()) {
                    val fileObj = files.getJSONObject(i)
                    val nodeType = fileObj.optInt("t", 0) // 0 = file, 1 = folder
                    if (nodeType == 0) { // Only process video/media files
                        val nodeId = fileObj.optString("h")
                        val size = fileObj.optLong("s", 0L)
                        val rawAttrs = fileObj.optString("a", "")

                        val name = decryptNodeName(rawAttrs, key) ?: "MEGA Video $nodeId"
                        val ext = name.substringAfterLast(".", "").lowercase()

                        val isVideo = ext in listOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "ts")
                        val isImage = ext in listOf("jpg", "jpeg", "png", "webp", "gif")
                        val isAudio = ext in listOf("mp3", "flac", "m4a", "aac", "ogg")

                        val mime = if (isVideo) "video/mp4" else if (isImage) "image/jpeg" else if (isAudio) "audio/mpeg" else "video/mp4"
                        val cat = if (isVideo) "video" else if (isImage) "image" else if (isAudio) "audio" else "document"

                        val itemKey = "mega_${nodeId}"
                        val sourceUrl = "https://mega.nz/folder/$folderId#$key"

                        mediaList.add(
                            CloudSocialMediaEntity(
                                id = itemKey,
                                sourceId = source.id,
                                type = "MEGA",
                                remoteId = nodeId,
                                parentId = folderId,
                                title = name,
                                caption = "MEGA Folder File • $name",
                                sourceUrl = sourceUrl,
                                directStreamUrl = "https://g.api.mega.co.nz/cs/download/$nodeId", // Stream proxy placeholder
                                thumbnailUrl = null,
                                mimeType = mime,
                                fileSize = size,
                                formattedSize = formatFileSize(size),
                                durationMs = 0L,
                                mediaCategory = cat,
                                dateTimestamp = System.currentTimeMillis(),
                                resolution = "HD"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return mediaList
    }

    private fun fetchMegaFileMetadata(fileId: String, key: String, source: CloudSocialSourceEntity): CloudSocialMediaEntity? {
        try {
            val itemKey = "mega_${fileId}"
            return CloudSocialMediaEntity(
                id = itemKey,
                sourceId = source.id,
                type = "MEGA",
                remoteId = fileId,
                parentId = null,
                title = "MEGA File $fileId",
                caption = "Direct MEGA Link",
                sourceUrl = source.sourceUrl,
                directStreamUrl = "https://g.api.mega.co.nz/cs/download/$fileId",
                thumbnailUrl = null,
                mimeType = "video/mp4",
                fileSize = 0L,
                formattedSize = "MEGA File",
                durationMs = 0L,
                mediaCategory = "video",
                dateTimestamp = System.currentTimeMillis(),
                resolution = "HD"
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun decryptNodeName(rawAttrs: String, folderKey: String): String? {
        if (rawAttrs.isBlank()) return null
        try {
            // Simplified metadata string extraction from MEGA base64 attributes
            val decoded = String(Base64.decode(rawAttrs.replace("-", "+").replace("_", "/"), Base64.DEFAULT), Charsets.ISO_8859_1)
            val nameMatch = Pattern.compile("\"n\":\"([^\"]+)\"").matcher(decoded)
            if (nameMatch.find()) {
                return nameMatch.group(1)
            }
        } catch (_: Exception) {}
        return null
    }

    suspend fun resolveStreamUrl(mediaItem: CloudSocialMediaEntity): String = withContext(Dispatchers.IO) {
        if (!mediaItem.directStreamUrl.isNullOrBlank() && mediaItem.directStreamUrl.startsWith("http")) {
            return@withContext mediaItem.directStreamUrl
        }
        return@withContext mediaItem.sourceUrl
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "Unknown"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.0f KB", kb)
            else -> "$bytes B"
        }
    }
}
