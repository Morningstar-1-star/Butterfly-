package com.example.util

import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Utility for parsing and decrypting Mega.nz URLs, folder node trees, and file attributes.
 */
object MegaCrypto {
    private const val TAG = "MegaCrypto"

    data class MegaNode(
        val handle: String,
        val parentHandle: String,
        val type: Int, // 0 = file, 1 = folder, 2 = root, 3 = inbox, 4 = trash
        val name: String,
        val size: Long,
        val keyBytes: ByteArray?,
        val ivBytes: ByteArray?,
        val rawKeyArray: IntArray?
    )

    data class MegaLinkInfo(
        val isFolder: Boolean,
        val folderId: String,
        val masterKey: ByteArray?,
        val fileHandle: String = "",
        val subFolderId: String = ""
    )

    /**
     * Parses a Mega URL into its components (folder id, master key, target subfolder or file).
     */
    fun parseMegaUrl(url: String): MegaLinkInfo? {
        try {
            val cleanUrl = url.trim()
            if (cleanUrl.contains("/folder/")) {
                // e.g. https://mega.nz/folder/Io4myLQC#0MV-ZU9NXIQZtRfcKSiqog/folder/1hogxZ7B
                // or https://mega.nz/folder/Io4myLQC#0MV-ZU9NXIQZtRfcKSiqog/file/xxx
                val parts = cleanUrl.split("/folder/")
                val mainPart = parts[1] // "Io4myLQC#0MV-ZU9NXIQZtRfcKSiqog..."
                val subFolder = if (parts.size > 2) parts[2].split("#")[0].split("/")[0] else ""

                val folderId = mainPart.substringBefore("#").substringBefore("/").substringBefore("?")
                val hashRemainder = if (mainPart.contains("#")) mainPart.substringAfter("#") else ""
                val masterKeyB64 = hashRemainder.substringBefore("/").substringBefore("?")

                val fileHandle = if (cleanUrl.contains("/file/")) {
                    cleanUrl.substringAfter("/file/").substringBefore("/").substringBefore("?")
                } else ""

                val masterKeyBytes = if (masterKeyB64.isNotBlank()) base64UrlDecode(masterKeyB64) else null

                return MegaLinkInfo(
                    isFolder = true,
                    folderId = folderId,
                    masterKey = masterKeyBytes,
                    fileHandle = fileHandle,
                    subFolderId = subFolder
                )
            } else if (cleanUrl.contains("/file/")) {
                // e.g. https://mega.nz/file/ABCDEF#1234567890
                val fileId = cleanUrl.substringAfter("/file/").substringBefore("#").substringBefore("/").substringBefore("?")
                val keyB64 = if (cleanUrl.contains("#")) cleanUrl.substringAfter("#").substringBefore("/").substringBefore("?") else ""
                val keyBytes = if (keyB64.isNotBlank()) base64UrlDecode(keyB64) else null

                return MegaLinkInfo(
                    isFolder = false,
                    folderId = fileId,
                    masterKey = keyBytes,
                    fileHandle = fileId
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Mega URL: $url", e)
        }
        return null
    }

    /**
     * Decodes Base64Url string (with '-' and '_') into raw byte array.
     */
    fun base64UrlDecode(input: String): ByteArray {
        var formatted = input.replace("-", "+").replace("_", "/")
        val pad = (4 - (formatted.length % 4)) % 4
        formatted += "=".repeat(pad)
        return try {
            java.util.Base64.getDecoder().decode(formatted)
        } catch (e: Exception) {
            android.util.Base64.decode(formatted, android.util.Base64.DEFAULT)
        }
    }

    /**
     * Encodes byte array into Base64Url string (without padding, using '-' and '_').
     */
    fun base64UrlEncode(input: ByteArray): String {
        return try {
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(input)
        } catch (e: Exception) {
            android.util.Base64.encodeToString(input, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE).trim().replace("=", "")
        }
    }

    /**
     * Decrypts AES-128 in ECB mode with no padding.
     */
    fun decryptAesEcb(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        val secretKey = SecretKeySpec(key, "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey)
        return cipher.doFinal(data)
    }

    /**
     * Decrypts AES-128 in CBC mode with zero IV and no padding.
     */
    fun decryptAesCbc(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        val secretKey = SecretKeySpec(key, "AES")
        val iv = IvParameterSpec(ByteArray(16))
        cipher.init(Cipher.DECRYPT_MODE, secretKey, iv)
        return cipher.doFinal(data)
    }

    /**
     * Decrypts a Mega folder node key (encrypted with the folder master key).
     * For files, k is 32 bytes (encrypted with folder master key); decrypted key consists of 16-byte AES key + 8-byte IV + 8-byte meta-MAC.
     * For folders, k is 16 bytes.
     */
    fun decryptNodeKey(encryptedKeyB64: String, masterKey: ByteArray): Pair<ByteArray, ByteArray>? {
        try {
            // String format may be "h:keyB64" or just "keyB64"
            val keyString = if (encryptedKeyB64.contains(":")) encryptedKeyB64.substringAfter(":") else encryptedKeyB64
            val encryptedBytes = base64UrlDecode(keyString)

            if (encryptedBytes.isEmpty() || encryptedBytes.size % 16 != 0) return null

            val decrypted = decryptAesEcb(encryptedBytes, masterKey)

            if (decrypted.size >= 32) {
                // File key: first 16 bytes XOR third 16 bytes? In Mega specs:
                // k_raw is 4 ints [k0, k1, k2, k3, k4, k5, k6, k7]
                // key = [k0 ^ k4, k1 ^ k5, k2 ^ k6, k3 ^ k7]
                // iv = [k4, k5, 0, 0] (64-bit IV + 64-bit initial counter)
                val intBuffer = ByteBuffer.wrap(decrypted).order(ByteOrder.BIG_ENDIAN)
                val ints = IntArray(decrypted.size / 4)
                for (i in ints.indices) {
                    ints[i] = intBuffer.int
                }

                if (ints.size >= 8) {
                    val keyInts = intArrayOf(
                        ints[0] xor ints[4],
                        ints[1] xor ints[5],
                        ints[2] xor ints[6],
                        ints[3] xor ints[7]
                    )

                    val realKeyBytes = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN).apply {
                        putInt(keyInts[0])
                        putInt(keyInts[1])
                        putInt(keyInts[2])
                        putInt(keyInts[3])
                    }.array()

                    val ivBytes = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN).apply {
                        putInt(ints[4])
                        putInt(ints[5])
                        putLong(0L) // Counter starts at 0
                    }.array()

                    return Pair(realKeyBytes, ivBytes)
                }
            } else if (decrypted.size == 16) {
                // Folder key
                return Pair(decrypted, ByteArray(16))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed decrypting node key: $encryptedKeyB64", e)
        }
        return null
    }

    /**
     * Decrypts the "a" attribute string (file name, metadata) of a Mega node.
     */
    fun decryptNodeAttributes(attrB64: String, key: ByteArray): String {
        try {
            val encData = base64UrlDecode(attrB64)
            if (encData.isEmpty() || encData.size % 16 != 0) return ""

            val decrypted = decryptAesCbc(encData, key)
            val jsonString = String(decrypted, Charsets.UTF_8).trim()

            // Mega attributes JSON starts with "MEGA{"
            val startIndex = jsonString.indexOf("MEGA{")
            if (startIndex != -1) {
                val jsonPart = jsonString.substring(startIndex + 4)
                val endIndex = jsonPart.lastIndexOf("}")
                if (endIndex != -1) {
                    val cleanJson = jsonPart.substring(0, endIndex + 1)
                    val obj = JSONObject(cleanJson)
                    return obj.optString("n", "")
                }
            } else if (jsonString.startsWith("{")) {
                val endIndex = jsonString.lastIndexOf("}")
                if (endIndex != -1) {
                    val obj = JSONObject(jsonString.substring(0, endIndex + 1))
                    return obj.optString("n", "")
                }
            }
        } catch (e: Exception) {
            // Attributes decryption failed
        }
        return ""
    }

    fun isVideoFile(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm") ||
                lower.endsWith(".avi") || lower.endsWith(".mov") || lower.endsWith(".ts") ||
                lower.endsWith(".m4v") || lower.endsWith(".flv") || lower.endsWith(".wmv") ||
                lower.endsWith(".3gp") || lower.endsWith(".mpeg") || lower.endsWith(".mpg")
    }
}
