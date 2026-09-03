package com.example.cloudsocial.mega

import android.util.Base64
import android.util.Log
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
        private const val TAG = "MegaSourceResolver"
        private val MEGA_FILE_PATTERN = Pattern.compile("https?://mega\\.nz/file/([a-zA-Z0-9_-]+)#([a-zA-Z0-9_-]+)")
        private val MEGA_FOLDER_PATTERN = Pattern.compile("https?://mega\\.nz/folder/([a-zA-Z0-9_-]+)#([a-zA-Z0-9_-]+)")
        private val MEGA_OLD_FOLDER_PATTERN = Pattern.compile("https?://mega\\.nz/#F!([a-zA-Z0-9_-]+)!([a-zA-Z0-9_-]+)")
        private val MEGA_OLD_FILE_PATTERN = Pattern.compile("https?://mega\\.nz/#!([a-zA-Z0-9_-]+)!([a-zA-Z0-9_-]+)")

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

            val oldFileMatcher = MEGA_OLD_FILE_PATTERN.matcher(clean)
            if (oldFileMatcher.find()) {
                return MegaUrlInfo(
                    id = oldFileMatcher.group(1) ?: "",
                    key = oldFileMatcher.group(2) ?: "",
                    isFolder = false
                )
            }

            return null
        }

        fun base64UrlDecode(str: String): ByteArray {
            var s = str.replace("-", "+").replace("_", "/")
            while (s.length % 4 != 0) {
                s += "="
            }
            return Base64.decode(s, Base64.DEFAULT)
        }

        fun parseMasterKey(folderKey: String): ByteArray? {
            return try {
                val raw = base64UrlDecode(folderKey)
                when (raw.size) {
                    16 -> raw
                    32 -> {
                        val folded = ByteArray(16)
                        for (i in 0 until 16) {
                            folded[i] = (raw[i].toInt() xor raw[i + 16].toInt()).toByte()
                        }
                        folded
                    }
                    else -> if (raw.size >= 16) raw.copyOf(16) else null
                }
            } catch (e: Exception) {
                null
            }
        }

        fun decryptNodeKey(kStr: String, masterKey: ByteArray): ByteArray? {
            val keyBase64 = if (kStr.contains(":")) kStr.substringAfterLast(":") else kStr
            if (keyBase64.isBlank()) return null
            return try {
                val encKey = base64UrlDecode(keyBase64)
                if (encKey.isEmpty() || encKey.size % 16 != 0) return null
                val cipher = Cipher.getInstance("AES/ECB/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(masterKey, "AES"))
                val decKey = cipher.doFinal(encKey)
                if (decKey.size == 32) {
                    val key = ByteArray(16)
                    for (i in 0 until 16) {
                        key[i] = (decKey[i].toInt() xor decKey[i + 16].toInt()).toByte()
                    }
                    key
                } else if (decKey.size >= 16) {
                    decKey.copyOf(16)
                } else null
            } catch (e: Exception) {
                null
            }
        }

        fun decryptNodeAttributes(rawAttrs: String, nodeKey: ByteArray): String? {
            if (rawAttrs.isBlank()) return null
            return try {
                val encAttrs = base64UrlDecode(rawAttrs)
                if (encAttrs.isEmpty() || encAttrs.size % 16 != 0) return null
                val cipher = Cipher.getInstance("AES/CBC/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(nodeKey, "AES"), IvParameterSpec(ByteArray(16)))
                val decAttrs = cipher.doFinal(encAttrs)
                val text = String(decAttrs, Charsets.UTF_8).trimEnd('\u0000', ' ')
                if (text.startsWith("MEGA{")) {
                    val jsonStr = text.removePrefix("MEGA")
                    val obj = JSONObject(jsonStr)
                    obj.optString("n").takeIf { it.isNotBlank() }
                } else {
                    val nameMatch = Pattern.compile("\"n\"\\s*:\\s*\"([^\"]+)\"").matcher(text)
                    if (nameMatch.find()) {
                        nameMatch.group(1)
                    } else null
                }
            } catch (e: Exception) {
                null
            }
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
        val masterKey = parseMasterKey(key)

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
            connection.connectTimeout = 12000
            connection.readTimeout = 15000
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
                    if (nodeType == 0) { // Process file
                        val nodeId = fileObj.optString("h")
                        val size = fileObj.optLong("s", 0L)
                        val rawAttrs = fileObj.optString("a", "")
                        val rawKey = fileObj.optString("k", "")

                        var name: String? = null
                        if (masterKey != null && rawKey.isNotBlank()) {
                            val nodeKey = decryptNodeKey(rawKey, masterKey)
                            if (nodeKey != null) {
                                name = decryptNodeAttributes(rawAttrs, nodeKey)
                            }
                        }

                        val finalName = name?.ifBlank { null } ?: "MEGA Video $nodeId"
                        val ext = finalName.substringAfterLast(".", "").lowercase()

                        val isVideo = ext in listOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "ts", "flv", "wmv", "3gp")
                        val isImage = ext in listOf("jpg", "jpeg", "png", "webp", "gif")
                        val isAudio = ext in listOf("mp3", "flac", "m4a", "aac", "ogg", "wav", "opus")

                        val mime = if (isVideo) "video/mp4" else if (isImage) "image/jpeg" else if (isAudio) "audio/mpeg" else "video/mp4"
                        val cat = if (isVideo) "video" else if (isImage) "image" else if (isAudio) "audio" else "document"

                        val itemKey = "mega_${nodeId}"
                        val sourceUrl = "https://mega.nz/folder/$folderId#$key"

                        // Thumbnail fallback to a high-contrast poster placeholder
                        val thumbUrl = "https://mega.nz/favicon.ico"

                        mediaList.add(
                            CloudSocialMediaEntity(
                                id = itemKey,
                                sourceId = source.id,
                                type = "MEGA",
                                remoteId = nodeId,
                                parentId = folderId,
                                title = finalName,
                                caption = "MEGA Cloud • $finalName",
                                sourceUrl = sourceUrl,
                                directStreamUrl = null,
                                thumbnailUrl = thumbUrl,
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
            Log.e(TAG, "Error fetching MEGA folder $folderId: ${e.message}", e)
        }
        return mediaList
    }

    private fun fetchMegaFileMetadata(fileId: String, key: String, source: CloudSocialSourceEntity): CloudSocialMediaEntity? {
        try {
            val itemKey = "mega_${fileId}"
            val masterKey = parseMasterKey(key)
            var fileName = "MEGA File $fileId"
            var fileSize = 0L

            try {
                val requestUrl = "https://g.api.mega.co.nz/cs?id=${System.currentTimeMillis()}"
                val payload = JSONArray().put(JSONObject().apply {
                    put("a", "g")
                    put("p", fileId)
                }).toString()

                val connection = URL(requestUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.write(payload.toByteArray(Charsets.UTF_8))

                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(responseText)
                if (jsonArray.length() > 0) {
                    val obj = jsonArray.getJSONObject(0)
                    fileSize = obj.optLong("s", 0L)
                    val rawAttrs = obj.optString("at", "")
                    if (masterKey != null && rawAttrs.isNotBlank()) {
                        val decName = decryptNodeAttributes(rawAttrs, masterKey)
                        if (!decName.isNullOrBlank()) {
                            fileName = decName
                        }
                    }
                }
            } catch (_: Exception) {}

            return CloudSocialMediaEntity(
                id = itemKey,
                sourceId = source.id,
                type = "MEGA",
                remoteId = fileId,
                parentId = null,
                title = fileName,
                caption = "Direct MEGA Link • $fileName",
                sourceUrl = source.sourceUrl,
                directStreamUrl = null,
                thumbnailUrl = "https://mega.nz/favicon.ico",
                mimeType = "video/mp4",
                fileSize = fileSize,
                formattedSize = if (fileSize > 0) formatFileSize(fileSize) else "MEGA Video",
                durationMs = 0L,
                mediaCategory = "video",
                dateTimestamp = System.currentTimeMillis(),
                resolution = "HD"
            )
        } catch (e: Exception) {
            return null
        }
    }

    suspend fun resolveStreamUrl(mediaItem: CloudSocialMediaEntity): String = withContext(Dispatchers.IO) {
        val folderId = mediaItem.parentId
        val nodeId = mediaItem.remoteId

        // 1. Try MEGA API direct CDN download ticket
        try {
            val requestUrl = if (!folderId.isNullOrBlank()) {
                "https://g.api.mega.co.nz/cs?id=${System.currentTimeMillis()}&n=$folderId"
            } else {
                "https://g.api.mega.co.nz/cs?id=${System.currentTimeMillis()}"
            }

            val payloadObj = JSONObject().apply {
                put("a", "g")
                put("g", 1)
                if (!folderId.isNullOrBlank()) {
                    put("n", nodeId)
                } else {
                    put("p", nodeId)
                }
            }
            val payload = JSONArray().put(payloadObj).toString()

            val connection = URL(requestUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.write(payload.toByteArray(Charsets.UTF_8))

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(responseText)
            if (jsonArray.length() > 0) {
                val obj = jsonArray.getJSONObject(0)
                val directG = obj.optString("g")
                if (directG.isNotBlank() && directG.startsWith("http")) {
                    return@withContext directG
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed resolving direct MEGA stream for $nodeId: ${e.message}")
        }

        if (!mediaItem.directStreamUrl.isNullOrBlank() && mediaItem.directStreamUrl.startsWith("http") && !mediaItem.directStreamUrl.contains("/cs/download/")) {
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
